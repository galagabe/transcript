package fr.clinitex.transcription.meetings

/**
 * Clinitex meeting note templates (Lot 2). [instructions] is sent as
 * `templateInstructions` to the n8n summary webhook; the section headings
 * it lists are the headings Claude writes in the meeting notes.
 *
 * Ids stay stable: they are stored in RecordingSession.templateId and
 * unknown ids (old Muesli templates) fall back to [GENERAL].
 *
 * [forMeetings] = false keeps a template out of the meeting pickers
 * (Réunions tab, default template in Réglages); [forVoiceNotes] lists the
 * templates offered in the Vocaux tab: [NOTE_VOCALE] (default) and [METRE].
 *
 * The RDV commercial sections are a proposal pending business validation.
 * [METRE] (requested after a talk with a Pas-de-Calais salesperson, 10/2026):
 * the salesperson dictates the premises alone, as a voice note (moved from
 * the Réunions tab on 22/10/2026); the notes hold the premises table, with
 * the columns of the Clinitex costing tool and frequency codes (Q1…Q5,
 * MENS, TRIM), and a sanitary equipment table for the supplier, both
 * exported as CSV by [MetreCsv].
 */
enum class MeetingTemplate(
    val id: String,
    val label: String,
    val detail: String,
    val instructions: String,
    val forMeetings: Boolean = true,
    val forVoiceNotes: Boolean = false,
) {
    GENERAL(
        "general", "Réunion générale", "Synthèse, décisions et actions à mener.",
        """Suis exactement ce plan de compte rendu :

## Synthèse
Deux ou trois phrases résumant l'objet et l'issue de la réunion.

## Points clés
- Les principaux sujets abordés

## Décisions
- Les décisions prises

## Actions à mener
- [ ] Tâche — responsable et échéance s'ils sont cités

## Citations marquantes
- Déclarations importantes, uniquement si pertinent"""
    ),
    RDV_COMMERCIAL(
        "rdvCommercial", "RDV commercial", "Besoin du client, prestations, objections et suite à donner.",
        """Suis exactement ce plan de compte rendu :

## Synthèse du rendez-vous
Deux ou trois phrases : qui a été rencontré, pour quel site, et l'issue du rendez-vous.

## Contexte du client
- Site(s) concerné(s), type de locaux et interlocuteurs présents avec leur rôle

## Prestations actuelles
- Prestataire en place, fréquences de passage, points de satisfaction et d'insatisfaction

## Besoins et attentes
- Ce que le client souhaite obtenir ou changer

## Objections et points de vigilance
- Freins, inquiétudes ou conditions exprimés par le client

## Éléments pour le chiffrage
- Surfaces, fréquences, horaires, contraintes d'accès, budget ou délais, uniquement s'ils ont été mentionnés

## Prochaines étapes
- [ ] Action — responsable et échéance s'ils sont cités"""
    ),
    METRE(
        "metre", "Métré", "Locaux dictés (type, revêtement, surface, fréquence), exportés en CSV pour l'outil de calcul, et équipements sanitaires à prévoir.",
        """Ce texte est un métré dicté par un commercial seul : il énumère les locaux d'un site avec leurs caractéristiques. N'invente rien et ne calcule rien.

## Locaux
Un tableau Markdown, avec exactement ces colonnes et dans cet ordre :
| Fréquence | Batiment | Etage | Type de Local | Revêtement | Prestations | Surface |
- Une ligne par local dicté. Si plusieurs locaux identiques sont dictés ensemble (ex. « trois bureaux de 12 m² »), une ligne par local.
- Type de Local : uniquement l'un de ces types, recopié exactement (majuscules, sans accent) : ABORDS, ACCUEIL, AMPHITHEATRE, ASCENSEURS, BIBLIOTHEQUE, BOXS DE RECEPTION, BUREAUX, CIRCULATIONS, COURS D'ECOLE, DOUCHES, ESCALIERS, HALL D'ENTREE, KIOSQUE, LOCAL ARCHIVES, LOCAL CE, LOCAL CONTAINERS, LOCAL TECHNIQUES, OPENSPACE, PARKINGS, PISCINE, POSTE DE GARDE, REFECTOIRE, SALLE DE CLASSE, SALLE DE COURS, SALLE DE FORMATION, SALLE DE PAUSE, SALLE DE REUNION, SALLE DE SPORT, SALLE DES PROF, SALLE INFORMATIQUE SERVEURS, SANITAIRES, SHOWROOM, ENTREPOT, SURFACE DE VENTE, TERRASSES, VESTIAIRES, VITRERIE. Prends le type le plus proche quand le rapprochement est évident (un couloir = CIRCULATIONS, des WC ou toilettes = SANITAIRES, une cantine = REFECTOIRE, un gymnase = SALLE DE SPORT, un local poubelles = LOCAL CONTAINERS). Recopie toujours dans Prestations le local tel que le commercial l'a décrit (ex. « bureau accueil » : Type de Local = BUREAUX, Prestations = Bureau accueil ; « couloir » : CIRCULATIONS et Couloir ; « sanitaires » : SANITAIRES et Sanitaires). Un local qui ne correspond à aucun type, même approchant : écris son nom tel que dicté dans Type de Local, sans le recopier dans Prestations.
- Revêtement : le revêtement de sol tel que dicté (carrelage, moquette, thermoplastique, parquet bois, ciment…).
- Fréquence : le code de la fréquence de passage dictée. Q1 = 1 fois par semaine, Q2 = 2 fois par semaine, Q3 = 3 fois par semaine, Q4 = 4 fois par semaine, Q5 = 5 fois par semaine, tous les jours ou une fois par jour, MENS = 1 fois par mois, TRIM = 1 fois par trimestre. Toute autre fréquence : telle que dictée, en forme courte. Si une fréquence est dictée pour un groupe de locaux, reporte-la sur chaque ligne concernée.
- Surface : le nombre seul, en m², avec une virgule décimale (ex. 12,5).
- Batiment, Etage, Prestations : seulement s'ils sont dictés. Prestations commence par le local tel que décrit, puis les prestations dictées, séparés par une virgule. Prestations ne contient jamais les équipements sanitaires (ils vont dans le tableau suivant).
- Toute information non dictée : laisse la cellule vide. N'écris jamais « Rien de noté », « non précisé » ni de tiret dans le tableau.

## Équipements sanitaires
Un second tableau Markdown, avec exactement ces colonnes et dans cet ordre :
| Batiment | Etage | Type de Local | Équipement | Quantité |
- Une ligne par équipement ou consommable sanitaire dicté et par local : distributeur d'essuie-mains, distributeur de savon, distributeur de papier toilette, désodorisant, et tout autre équipement ou consommable sanitaire dicté.
- Batiment, Etage, Type de Local : ceux du local où l'équipement est dicté, écrits comme dans le tableau des locaux.
- Quantité : le nombre seul, seulement s'il est dicté ; sinon cellule vide.
- Si aucun équipement sanitaire n'est dicté, écris « Rien de noté » à la place du tableau.

## Remarques
- Les autres informations utiles dictées (accès, horaires, contraintes, matériel). Si rien d'autre n'est dicté, écris « Rien de noté ».""",
        forMeetings = false,
        forVoiceNotes = true,
    ),
    REUNION_INTERNE(
        "reunionInterne", "Réunion interne", "Points abordés, décisions, actions et points ouverts.",
        """Suis exactement ce plan de compte rendu :

## Synthèse
Deux ou trois phrases résumant l'objet et l'issue de la réunion.

## Points abordés
- Les sujets traités, dans l'ordre de la réunion

## Décisions
- Les décisions prises

## Actions à mener
- [ ] Tâche — responsable et échéance s'ils sont cités

## Points ouverts
- Questions restées sans réponse ou sujets à reprendre"""
    ),
    NOTE_VOCALE(
        "noteVocale", "Note vocale", "Message dicté remis au propre, avec les actions à faire.",
        "Mets au propre ce message dicté, sans rien inventer ni ajouter.\n\n" +
            "## Note\n" +
            "Le message reformulé en phrases claires et correctes, en gardant le sens et le ton. " +
            "Supprime les hésitations et répétitions.\n\n" +
            "## À faire\n" +
            "- [ ] Chaque action, demande ou consigne citée, avec le responsable et l'échéance s'ils sont mentionnés\n" +
            "Si aucune action n'est citée, écris « Rien de noté ».",
        forMeetings = false,
        forVoiceNotes = true,
    );

    companion object {
        fun fromId(id: String?): MeetingTemplate = entries.firstOrNull { it.id == id } ?: GENERAL

        /** Templates offered for meetings (pickers), in display order. */
        val meetingTemplates: List<MeetingTemplate> get() = entries.filter { it.forMeetings }

        /** Templates offered in the Vocaux tab, [NOTE_VOCALE] first. */
        val voiceNoteTemplates: List<MeetingTemplate> get() = entries.filter { it.forVoiceNotes }.sortedBy { it != NOTE_VOCALE }

        /** Like [fromId] but never returns a voice-note-only template (falls back to [GENERAL]). */
        fun meetingFromId(id: String?): MeetingTemplate = fromId(id).takeIf { it.forMeetings } ?: GENERAL
    }
}
