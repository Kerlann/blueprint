package fr.blueprint.client.screen;

import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * Quand une valeur saisie ou glissée <b>part vers le serveur</b>.
 *
 * <p>Deux gestes s'étalent dans le temps, et tous deux partaient à chaque étape.
 *
 * <ul>
 *   <li>Un <b>champ de saisie</b> : taper « Jean-Baptiste » coûtait treize paquets, pour
 *       un nom qui n'intéresse personne avant d'être complet.</li>
 *   <li>Un <b>curseur</b> : traverser une plage de 16 à 90 par pas de 1 en coûtait
 *       soixante-quatorze, en une seconde. Le serveur limite les interactions d'écran à
 *       quarante par dix secondes — un joueur qui règle simplement l'âge de son
 *       personnage <b>crevait le quota</b> et se faisait ignorer.</li>
 * </ul>
 *
 * <p>La règle est la même, et elle tient en trois lignes :
 *
 * <ul>
 *   <li>Le geste ordinaire <b>retient</b> sa valeur pendant qu'il dure.</li>
 *   <li>Un élément {@code live} envoie tout de suite — une recherche qui filtre pendant
 *       qu'on tape, une jauge que le graphe doit suivre en continu.</li>
 *   <li>La valeur part quand le geste <b>finit</b> : perte du focus pour un champ,
 *       relâchement de la souris pour un curseur, fermeture de l'écran pour les deux.</li>
 * </ul>
 *
 * <p>C'est la fin du geste qui rend le report sûr. Cliquer sur « Valider » relâche le
 * champ, donc le texte part <b>avant</b> le clic ; les deux paquets empruntent le même
 * canal dans l'ordre, et le graphe trouve la valeur en place quand il traite le bouton.
 *
 * <p>Et l'écran ne perd rien en réactivité : la poignée du curseur et le chiffre à côté
 * sont dessinés <b>chez le client</b>, qui connaît la valeur avant tout le monde. Ce qui
 * attendait n'était pas l'affichage, c'était le graphe.
 *
 * <p>La règle est ici plutôt que dans l'écran parce qu'elle est <b>vérifiable sans lancer
 * le jeu</b> : une valeur perdue ou envoyée deux fois se remarque très mal en jouant, et
 * pas du tout en relisant.
 */
public final class ValueDrafts {

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
