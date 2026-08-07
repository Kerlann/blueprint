package fr.blueprint.client.screen;

import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * Quand le texte d'un champ de saisie <b>part vers le serveur</b>.
 *
 * <p>Il partait à chaque frappe. Taper « Jean-Baptiste » coûtait treize paquets, treize
 * exécutions du graphe et treize écritures de variable, pour un nom qui n'intéresse
 * personne avant d'être complet ; sur un serveur peuplé où chacun remplit un formulaire à
 * la connexion, cela se compte en milliers d'allers pour rien.
 *
 * <p>La règle tient en trois lignes, et elle est ici plutôt que dans l'écran parce qu'elle
 * est <b>vérifiable sans lancer le jeu</b> : un brouillon perdu ou envoyé deux fois se
 * remarque très mal en jouant, et pas du tout en relisant.
 *
 * <ul>
 *   <li>Un champ ordinaire <b>retient</b> ce qu'on tape.</li>
 *   <li>Un champ {@code live} envoie tout de suite — une recherche qui filtre pendant
 *       qu'on tape n'a pas d'autre choix.</li>
 *   <li>Le brouillon part à la <b>perte du focus</b> : {@code Entrée}, {@code Tab},
 *       {@code Échap}, un clic ailleurs, la fermeture de l'écran.</li>
 * </ul>
 *
 * <p>C'est la perte du focus qui rend le report sûr. Cliquer sur « Valider » relâche le
 * champ, donc le texte part <b>avant</b> le clic ; les deux paquets empruntent le même
 * canal dans l'ordre, et le graphe trouve la valeur en place quand il traite le bouton.
 */
public final class InputDrafts {

    private final Set<String> pending = new HashSet<>();

    /**
     * Le joueur a tapé.
     *
     * @param live le champ rapporte-t-il à chaque frappe ?
     * @return vrai s'il faut envoyer maintenant.
     */
    public boolean typed(String element, boolean live) {
        if (live) {
            pending.remove(element);
            return true;
        }
        pending.add(element);
        return false;
    }

    /**
     * Le champ perd le focus, ou l'écran se ferme.
     *
     * @return vrai s'il reste un brouillon à envoyer. Faux si rien n'a été tapé depuis le
     *         dernier envoi — sans quoi chaque clic dans le vide referait partir le même
     *         texte, et le report différé aurait remplacé un paquet par frappe par un
     *         paquet par clic.
     */
    public boolean flush(@Nullable String element) {
        return element != null && pending.remove(element);
    }

    /** Le texte est parti par un autre chemin ({@code Entrée}) : plus rien n'attend. */
    public void sent(String element) {
        pending.remove(element);
    }

    /** Un brouillon attend-il pour ce champ ? */
    public boolean waiting(String element) {
        return pending.contains(element);
    }
}
