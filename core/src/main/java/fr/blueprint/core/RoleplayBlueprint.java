package fr.blueprint.core;

import fr.blueprint.api.node.Permission;
import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.event.StandardEvents;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.BlueprintMeta;
import fr.blueprint.core.graph.EditOperation;
import fr.blueprint.core.graph.GraphLoader;
import fr.blueprint.core.graph.Link;
import fr.blueprint.core.graph.NodeTypeLookup;
import fr.blueprint.core.graph.VarScope;
import fr.blueprint.core.graph.Variable;
import fr.blueprint.core.graph.Vec2d;
import fr.blueprint.core.graph.screen.Anchor;
import fr.blueprint.core.graph.screen.ClientValue;
import fr.blueprint.core.graph.screen.ElementBinding;
import fr.blueprint.core.graph.screen.ElementKind;
import fr.blueprint.core.graph.screen.ElementOptions;
import fr.blueprint.core.graph.screen.Extent;
import fr.blueprint.core.graph.screen.LayoutSpec;
import fr.blueprint.core.graph.screen.Screen;
import fr.blueprint.core.graph.screen.ScreenElement;
import fr.blueprint.core.graph.screen.ScreenText;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * <b>Un serveur de jeu de rôle</b> : création de personnage à la connexion, et une fiche
 * permanente à l'écran.
 *
 * <p>Deux écrans et un graphe. À la connexion, le joueur qui n'a pas de personnage reçoit
 * un formulaire — prénom, nom, âge, sexe, métier ; celui qui en a un reçoit directement sa
 * fiche. Les champs sont enregistrés <b>chez le joueur</b> et survivent au redémarrage du
 * serveur.
 *
 * <p>Lancement en jeu : <code>/blueprint rp</code>. Le blueprint s'active et travaille à la
 * connexion suivante. <code>/bpc rp</code> rouvre le formulaire pour se corriger.
 *
 * <h2>Ce que cet exemple montre, et qu'aucun autre ne montrait</h2>
 *
 * <p><b>Le partage du travail entre le client et le serveur.</b> La fiche affiche quatre
 * choses ; deux viennent du serveur et deux ne viennent de nulle part.
 *
 * <ul>
 *   <li>Le <b>nom</b> et le <b>métier</b> sont des variables : seul le serveur les
 *       connaît, il les pousse quand elles changent — c'est-à-dire une fois, à la
 *       création.</li>
 *   <li>La <b>vie</b> est une {@link ClientValue} : le client l'affiche déjà dans ses
 *       propres cœurs. Aucune variable ne la porte, aucun tick ne la relit, aucun paquet
 *       ne la transporte, et elle bouge à l'image plutôt qu'au prochain rafraîchissement.</li>
 * </ul>
 *
 * <p>La version naïve de cette fiche coûterait, à cinquante joueurs, mille lectures et
 * jusqu'à mille paquets par seconde — pour redire à chacun ce qu'il voit déjà. Celle-ci ne
 * coûte rien après la création.
 *
 * <h2>La portée des variables</h2>
 *
 * <p>Tout est en <b>{@code PLAYER}</b>, et ce n'est pas un détail : en {@code GRAPH}, le
 * deuxième joueur à créer son personnage effacerait le prénom du premier, et chacun verrait
 * dans sa fiche l'identité du dernier arrivé. C'est exactement le défaut que la portée
 * joueur a été réparée pour empêcher.
 */
public final class RoleplayBlueprint {

    public static final Identifier ID = Identifier.fromNamespaceAndPath("blueprint", "rp");

    /** Nom à taper : {@code /bpc rp} — rouvre le formulaire. */
    public static final String COMMAND = "rp";

    /** Le formulaire modal, ouvert à la connexion d'un joueur sans personnage. */
    public static final String CREATION = "creation";

    /** La fiche permanente, affichée par-dessus le jeu. */
    public static final String FICHE = "fiche";

    /** Les métiers proposés. Modifiables dans l'éditeur, sans toucher au code. */
    private static final String METIERS = "Sans-emploi,Fermier,Forgeron,Marchand,Garde,Médecin";

    /** Homme ou femme, comme demandé. La liste se modifie au même endroit que les métiers. */
    private static final String SEXES = "Homme,Femme";

    private RoleplayBlueprint() {
    }

    // --------------------------------------------------------------- le formulaire

    private static Screen creation() {
        List<ScreenElement> elements = new ArrayList<>();

        elements.add(ScreenElement.of("cadre", ElementKind.PANEL, 0, 0, 220, 160)
                .withAnchor(Anchor.CENTER)
                .withLayout(LayoutSpec.column(5).withCross(LayoutSpec.Cross.STRETCH)));

        elements.add(ScreenElement.of("titre", ElementKind.LABEL, 0, 0, 210, 12)
                .withParent("cadre")
                .resized(Extent.fill(), Extent.of(12))
                .withText(ScreenText.literal("Qui êtes-vous ?")));

        // Deux champs plutôt qu'un « nom complet » : le graphe a besoin des deux
        // séparément pour la fiche, et découper une chaîne à l'espace se casse au premier
        // « Jean-Baptiste de La Fontaine ».
        elements.add(champ(elements, "prenom", "Prénom"));
        elements.add(champ(elements, "nom", "Nom de famille"));

        // L'âge au curseur et non au clavier : un champ numérique accepte « 700 » et
        // oblige le graphe à le refuser ensuite. Un curseur borné ne peut pas produire de
        // valeur invalide, donc il n'y a rien à valider.
        elements.add(ScreenElement.of("age", ElementKind.SLIDER, 0, 0, 210, 12)
                .withParent("cadre")
                .resized(Extent.fill(), Extent.of(12))
                .withOptions(ElementOptions.slider(16, 90, 1).withPlaceholder(" ans"))
                .withTooltip(ScreenText.literal("Glissez : l'âge de votre personnage")));

        elements.add(liste("sexe", "Choisir un sexe…"));
        elements.add(liste("metier", "Choisir un métier…"));

        // Le retour d'erreur a sa place à lui, sous les champs. Le mettre dans le titre
        // ferait disparaître la question, et un formulaire qui perd son intitulé quand il
        // se plaint est un formulaire qu'on ne sait plus remplir.
        elements.add(ScreenElement.of("erreur", ElementKind.LABEL, 0, 0, 210, 10)
                .withParent("cadre")
                .resized(Extent.fill(), Extent.of(10))
                .withText(ScreenText.literal("")));

        elements.add(ScreenElement.of("valider", ElementKind.BUTTON, 0, 0, 210, 16)
                .withParent("cadre")
                .resized(Extent.fill(), Extent.of(16))
                .withText(ScreenText.literal("Créer mon personnage")));

        return new Screen(CREATION, false, elements);
    }

    private static ScreenElement champ(List<ScreenElement> into, String name, String invite) {
        return ScreenElement.of(name, ElementKind.INPUT, 0, 0, 210, 12)
                .withParent("cadre")
                .resized(Extent.fill(), Extent.of(12))
                .withOptions(ElementOptions.input(invite, 24,
                        ElementOptions.InputFilter.TEXT));
    }

    private static ScreenElement liste(String name, String invite) {
        return ScreenElement.of(name, ElementKind.DROPDOWN, 0, 0, 210, 12)
                .withParent("cadre")
                .resized(Extent.fill(), Extent.of(12))
                .withText(ScreenText.literal(invite))
                .withOptions(ElementOptions.list(13));
    }

    // ------------------------------------------------------------------- la fiche

    private static Screen fiche() {
        List<ScreenElement> elements = new ArrayList<>();

        // En haut à gauche, hors de la barre d'action et des cœurs : un HUD qui recouvre
        // l'interface du jeu se fait masquer par le joueur, et n'aura servi à rien.
        elements.add(ScreenElement.of("bandeau", ElementKind.PANEL, 4, 4, 120, 46)
                .withAnchor(Anchor.TOP_LEFT)
                .withLayout(LayoutSpec.column(2).withCross(LayoutSpec.Cross.STRETCH)));

        // Deux étiquettes plutôt qu'une variable « identité » calculée : une valeur dérivée
        // se périme dès qu'on change le prénom sans repasser par le nœud qui la recompose,
        // et rien ne le signale. Deux liaisons sur les deux sources ne peuvent pas mentir.
        elements.add(ScreenElement.of("prenom", ElementKind.LABEL, 0, 0, 116, 10)
                .withParent("bandeau")
                .resized(Extent.fill(), Extent.of(10))
                .withBinding(ElementBinding.text("prenom", "%s")));

        elements.add(ScreenElement.of("nom", ElementKind.LABEL, 0, 0, 116, 10)
                .withParent("bandeau")
                .resized(Extent.fill(), Extent.of(10))
                .withBinding(ElementBinding.text("nom", "%s")));

        elements.add(ScreenElement.of("metier", ElementKind.LABEL, 0, 0, 116, 10)
                .withParent("bandeau")
                .resized(Extent.fill(), Extent.of(10))
                .withBinding(ElementBinding.text("metier", "Métier : %s")));

        // LA ligne à retenir de tout ce fichier. Ces deux éléments ne coûtent RIEN au
        // serveur : ni variable, ni tick, ni paquet. Le client possède déjà la valeur, il
        // la peint, et elle bouge à l'image.
        elements.add(ScreenElement.of("vie", ElementKind.PROGRESS, 0, 0, 116, 5)
                .withParent("bandeau")
                .resized(Extent.fill(), Extent.of(5))
                .withBinding(ElementBinding.clientProgress(ClientValue.HEALTH, 0, 20)));

        elements.add(ScreenElement.of("vie_texte", ElementKind.LABEL, 0, 0, 116, 10)
                .withParent("bandeau")
                .resized(Extent.fill(), Extent.of(10))
                .withBinding(ElementBinding.client(ClientValue.HEALTH,
                        ElementBinding.Target.TEXT, "%s / 20 PV")));

        return new Screen(FICHE, true, elements);
    }

    // ------------------------------------------------------------------ le graphe

    public static Blueprint build(NodeTypeLookup lookup) {
        Blueprint bp = new Blueprint(ID, new BlueprintMeta(
                "Blueprint", "Serveur RP : création de personnage et fiche permanente",
                "1.0.0", Permission.GAMEPLAY));

        // Toutes en PLAYER : c'est une identité, elle appartient à quelqu'un. En GRAPH,
        // le deuxième joueur à se créer effacerait le premier.
        variable(bp, "prenom", PinTypes.STRING, LiteralValue.of(PinTypes.STRING, ""));
        variable(bp, "nom", PinTypes.STRING, LiteralValue.of(PinTypes.STRING, ""));
        variable(bp, "age", PinTypes.DOUBLE, LiteralValue.of(PinTypes.DOUBLE, 25.0));
        variable(bp, "sexe", PinTypes.STRING, LiteralValue.of(PinTypes.STRING, ""));
        variable(bp, "metier", PinTypes.STRING,
                LiteralValue.of(PinTypes.STRING, "Sans-emploi"));
        // Le drapeau qui décide de tout : formulaire ou fiche, à la connexion.
        variable(bp, "cree", PinTypes.BOOL, LiteralValue.of(PinTypes.BOOL, false));

        GraphLoader.addScreen(bp, creation());
        GraphLoader.addScreen(bp, fiche());

        connexion(bp, lookup);
        reouvrir(bp, lookup);
        saisie(bp, lookup, "prenom", 0);
        saisie(bp, lookup, "nom", 400);
        curseurAge(bp, lookup);
        choix(bp, lookup, "sexe", 1200);
        choix(bp, lookup, "metier", 1600);
        valider(bp, lookup);
        return bp;
    }

    /**
     * À la connexion : le formulaire si le personnage n'existe pas, la fiche sinon.
     *
     * <p>Les deux listes déroulantes reçoivent leurs choix <b>ici</b>, au moment
     * d'ouvrir : un {@code gui/set_lines} ne peut pas viser un écran fermé, et les poser
     * une fois pour toutes au démarrage du serveur les poserait chez personne.
     */
    private static void connexion(Blueprint bp, NodeTypeLookup lookup) {
        UUID join = add(bp, lookup, "join", StandardEvents.PLAYER_JOIN.id(), -900, 0);

        UUID lu = add(bp, lookup, "join-lu", node("var/get"), -900, 160);
        literal(bp, lookup, lu, "var", LiteralValue.of(PinTypes.STRING, "cree"));

        UUID test = add(bp, lookup, "join-test", node("flow/branch"), -640, 0);
        link(bp, lookup, join, "exec_out", test, "exec_in");
        link(bp, lookup, lu, "value", test, "condition");

        // Déjà créé : la fiche, tout de suite.
        UUID fiche = add(bp, lookup, "join-fiche", node("hud/show"), -380, -160);
        literal(bp, lookup, fiche, "screen", LiteralValue.of(PinTypes.STRING, FICHE));
        link(bp, lookup, test, "true", fiche, "exec_in");
        link(bp, lookup, join, "player", fiche, "player");
        rafraichir(bp, lookup, "join-fiche", fiche, join, -120, -160);

        // Pas encore : le formulaire.
        ouvrirFormulaire(bp, lookup, "join", test, "false", join, -380, 160);
    }

    /** {@code /bpc rp} : rouvrir le formulaire pour se corriger. */
    private static void reouvrir(Blueprint bp, NodeTypeLookup lookup) {
        UUID commande = add(bp, lookup, "cmd", StandardEvents.COMMAND.id(), -900, 700);
        literal(bp, lookup, commande, "name", LiteralValue.of(PinTypes.STRING, COMMAND));
        ouvrirFormulaire(bp, lookup, "cmd", commande, "exec_out", commande, -640, 700);
    }

    /**
     * Ouvrir le formulaire et le remplir : les deux listes déroulantes, puis un
     * rafraîchissement pour que les liaisons partent.
     */
    private static void ouvrirFormulaire(Blueprint bp, NodeTypeLookup lookup, String seed,
                                         UUID after, String afterPin, UUID playerSource,
                                         double x, double y) {
        UUID open = add(bp, lookup, seed + "-open", node("gui/open"), x, y);
        literal(bp, lookup, open, "screen", LiteralValue.of(PinTypes.STRING, CREATION));
        link(bp, lookup, after, afterPin, open, "exec_in");
        link(bp, lookup, playerSource, "player", open, "player");

        UUID dernier = open;
        dernier = remplir(bp, lookup, seed, "sexe", SEXES, dernier, playerSource, x + 260, y);
        dernier = remplir(bp, lookup, seed, "metier", METIERS, dernier, playerSource,
                x + 520, y);
        rafraichir(bp, lookup, seed + "-form", dernier, playerSource, x + 780, y);
    }

    /** Les choix d'une liste déroulante, découpés d'une chaîne éditable d'un seul champ. */
    private static UUID remplir(Blueprint bp, NodeTypeLookup lookup, String seed,
                                String element, String choix, UUID after, UUID playerSource,
                                double x, double y) {
        UUID decoupe = add(bp, lookup, seed + '-' + element + "-choix", node("string/split"),
                x, y + 180);
        literal(bp, lookup, decoupe, "text", LiteralValue.of(PinTypes.STRING, choix));
        literal(bp, lookup, decoupe, "separator", LiteralValue.of(PinTypes.STRING, ","));

        UUID pose = add(bp, lookup, seed + '-' + element + "-pose", node("gui/set_lines"),
                x, y);
        literal(bp, lookup, pose, "screen", LiteralValue.of(PinTypes.STRING, CREATION));
        literal(bp, lookup, pose, "element", LiteralValue.of(PinTypes.STRING, element));
        link(bp, lookup, decoupe, "parts", pose, "lines");
        link(bp, lookup, playerSource, "player", pose, "player");
        link(bp, lookup, after, "exec_out", pose, "exec_in");
        return pose;
    }

    /**
     * Un champ de saisie enregistré à <b>chaque frappe</b>.
     *
     * <p>Sans {@code submitted} à dessein, contrairement à la vitrine : un formulaire se
     * valide au bouton, pas à la touche Entrée de chaque champ. Écrire à chaque frappe
     * coûte une écriture de variable — pas un paquet, il est déjà parti — et garantit
     * qu'au moment du clic, ce que le joueur voit est ce que le serveur a.
     */
    private static void saisie(Blueprint bp, NodeTypeLookup lookup, String element, double y) {
        UUID evt = add(bp, lookup, element + "-evt",
                StandardEvents.GUI_INPUT_CHANGED.id(), -900, 1000 + y);
        literal(bp, lookup, evt, "element", LiteralValue.of(PinTypes.STRING, element));

        UUID ecrire = add(bp, lookup, element + "-set", node("var/set"), -600, 1000 + y);
        literal(bp, lookup, ecrire, "var", LiteralValue.of(PinTypes.STRING, element));
        link(bp, lookup, evt, "text", ecrire, "value");
        link(bp, lookup, evt, "exec_out", ecrire, "exec_in");
    }

    /** Le curseur d'âge : la valeur arrive déjà bornée, il n'y a rien à vérifier. */
    private static void curseurAge(Blueprint bp, NodeTypeLookup lookup) {
        UUID evt = add(bp, lookup, "age-evt",
                StandardEvents.GUI_VALUE_CHANGED.id(), -900, 1800);
        literal(bp, lookup, evt, "element", LiteralValue.of(PinTypes.STRING, "age"));

        UUID ecrire = add(bp, lookup, "age-set", node("var/set"), -600, 1800);
        literal(bp, lookup, ecrire, "var", LiteralValue.of(PinTypes.STRING, "age"));
        link(bp, lookup, evt, "value", ecrire, "value");
        link(bp, lookup, evt, "exec_out", ecrire, "exec_in");
    }

    /**
     * Un choix de liste déroulante : c'est la <b>ligne</b> qu'on garde, pas son indice.
     *
     * <p>Un indice se périme dès qu'on ajoute un métier au milieu de la liste : les
     * personnages existants changeraient de métier sans que personne n'ait rien touché.
     */
    private static void choix(Blueprint bp, NodeTypeLookup lookup, String element, double y) {
        UUID evt = add(bp, lookup, element + "-evt",
                StandardEvents.GUI_LIST_CLICKED.id(), -900, 1000 + y);
        literal(bp, lookup, evt, "element", LiteralValue.of(PinTypes.STRING, element));

        UUID ecrire = add(bp, lookup, element + "-set", node("var/set"), -600, 1000 + y);
        literal(bp, lookup, ecrire, "var", LiteralValue.of(PinTypes.STRING, element));
        link(bp, lookup, evt, "line", ecrire, "value");
        link(bp, lookup, evt, "exec_out", ecrire, "exec_in");
    }

    /**
     * Le bouton « Créer » : vérifier, puis fermer et afficher la fiche.
     *
     * <p>La vérification est <b>côté serveur</b>, et c'est le seul endroit où elle peut
     * être. Un client modifié peut envoyer n'importe quel contenu de champ ; griser le
     * bouton tant que les champs sont vides serait un confort, jamais une garantie.
     */
    private static void valider(Blueprint bp, NodeTypeLookup lookup) {
        UUID clic = add(bp, lookup, "valider-evt",
                StandardEvents.GUI_ELEMENT_CLICKED.id(), -900, 3000);
        literal(bp, lookup, clic, "element", LiteralValue.of(PinTypes.STRING, "valider"));

        UUID complet = complet(bp, lookup);

        UUID test = add(bp, lookup, "valider-test", node("flow/branch"), -600, 3000);
        link(bp, lookup, clic, "exec_out", test, "exec_in");
        link(bp, lookup, complet, "result", test, "condition");

        // Complet : on marque, on ferme, on affiche.
        UUID marquer = add(bp, lookup, "valider-marque", node("var/set"), -340, 2840);
        literal(bp, lookup, marquer, "var", LiteralValue.of(PinTypes.STRING, "cree"));
        literal(bp, lookup, marquer, "value", LiteralValue.of(PinTypes.BOOL, true));
        link(bp, lookup, test, "true", marquer, "exec_in");

        UUID fermer = add(bp, lookup, "valider-ferme", node("gui/close"), -80, 2840);
        link(bp, lookup, marquer, "exec_out", fermer, "exec_in");
        link(bp, lookup, clic, "player", fermer, "player");

        UUID fiche = add(bp, lookup, "valider-fiche", node("hud/show"), 180, 2840);
        literal(bp, lookup, fiche, "screen", LiteralValue.of(PinTypes.STRING, FICHE));
        link(bp, lookup, fermer, "exec_out", fiche, "exec_in");
        link(bp, lookup, clic, "player", fiche, "player");
        rafraichir(bp, lookup, "valider", fiche, clic, 440, 2840);

        // Incomplet : on le dit dans l'écran, pas dans le chat — le joueur regarde le
        // formulaire, c'est là que la réponse doit apparaître.
        UUID dire = add(bp, lookup, "valider-erreur", node("gui/set_text"), -340, 3160);
        literal(bp, lookup, dire, "screen", LiteralValue.of(PinTypes.STRING, CREATION));
        literal(bp, lookup, dire, "element", LiteralValue.of(PinTypes.STRING, "erreur"));
        literal(bp, lookup, dire, "text", LiteralValue.of(PinTypes.STRING,
                "Prénom, nom et sexe sont obligatoires."));
        link(bp, lookup, test, "false", dire, "exec_in");
        link(bp, lookup, clic, "player", dire, "player");
    }

    /**
     * « Les trois champs obligatoires sont-ils remplis ? », en nœuds purs.
     *
     * <p>Par {@code logic/not_equals} contre la chaîne vide plutôt que par une longueur
     * comparée à zéro : c'est un nœud de moins par champ, et la lecture du graphe dit
     * exactement ce qu'on veut savoir.
     */
    private static UUID complet(Blueprint bp, NodeTypeLookup lookup) {
        UUID a = rempli(bp, lookup, "prenom", -1500, 3000);
        UUID b = rempli(bp, lookup, "nom", -1500, 3160);
        UUID c = rempli(bp, lookup, "sexe", -1500, 3320);

        UUID et1 = add(bp, lookup, "valider-et1", node("logic/and"), -1100, 3080);
        link(bp, lookup, a, "result", et1, "a");
        link(bp, lookup, b, "result", et1, "b");

        UUID et2 = add(bp, lookup, "valider-et2", node("logic/and"), -880, 3160);
        link(bp, lookup, et1, "result", et2, "a");
        link(bp, lookup, c, "result", et2, "b");
        return et2;
    }

    private static UUID rempli(Blueprint bp, NodeTypeLookup lookup, String variable,
                               double x, double y) {
        UUID lu = add(bp, lookup, "valider-lu-" + variable, node("var/get"), x, y);
        literal(bp, lookup, lu, "var", LiteralValue.of(PinTypes.STRING, variable));

        UUID vide = add(bp, lookup, "valider-vide-" + variable, node("logic/not_equals"),
                x + 220, y);
        link(bp, lookup, lu, "value", vide, "a");
        literal(bp, lookup, vide, "b", LiteralValue.of(PinTypes.STRING, ""));
        return vide;
    }

    // ------------------------------------------------------------------ outillage

    /**
     * Le {@code gui/refresh} qui suit une écriture.
     *
     * <p>Il ne fait partir que les liaisons de <b>variables</b>. Celles de source client —
     * la vie — ne l'attendent pas et ne l'attendront jamais : c'est tout l'intérêt.
     */
    private static void rafraichir(Blueprint bp, NodeTypeLookup lookup, String seed,
                                   UUID after, UUID playerSource, double x, double y) {
        UUID refresh = add(bp, lookup, seed + "-refresh", node("gui/refresh"), x, y);
        literal(bp, lookup, refresh, "screen", LiteralValue.of(PinTypes.STRING, FICHE));
        link(bp, lookup, playerSource, "player", refresh, "player");
        link(bp, lookup, after, "exec_out", refresh, "exec_in");
    }

    private static void variable(Blueprint bp, String name, fr.blueprint.api.pin.PinType type,
                                 LiteralValue defaut) {
        GraphLoader.addVariable(bp, new Variable(name, type, defaut, VarScope.PLAYER, true));
    }

    private static Identifier node(String path) {
        return Identifier.fromNamespaceAndPath("blueprint", path);
    }

    private static UUID add(Blueprint bp, NodeTypeLookup lookup, String seed,
                            Identifier type, double x, double y) {
        UUID uuid = UUID.nameUUIDFromBytes(("rp-" + seed)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        apply(bp, lookup, new EditOperation.AddNode(uuid, type, new Vec2d(x, y)));
        return uuid;
    }

    private static void literal(Blueprint bp, NodeTypeLookup lookup, UUID target,
                                String pin, LiteralValue value) {
        apply(bp, lookup, new EditOperation.SetLiteral(target, pin, value));
    }

    private static void link(Blueprint bp, NodeTypeLookup lookup, UUID from, String fromPin,
                             UUID to, String toPin) {
        apply(bp, lookup, new EditOperation.AddLink(new Link(from, fromPin, to, toPin)));
    }

    private static void apply(Blueprint bp, NodeTypeLookup lookup, EditOperation op) {
        EditOperation.Result result = op.apply(bp, lookup);
        if (!result.applied()) {
            throw new IllegalStateException("Blueprint RP incohérent : " + result.refusal());
        }
    }
}
