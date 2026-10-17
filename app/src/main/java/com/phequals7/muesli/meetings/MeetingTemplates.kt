package com.phequals7.muesli.meetings

/**
 * Clinitex meeting note templates (Lot 2). [instructions] is sent as
 * `templateInstructions` to the n8n summary webhook; the section headings
 * it lists are the headings Claude writes in the meeting notes.
 *
 * Ids stay stable: they are stored in RecordingSession.templateId and
 * unknown ids (old Muesli templates) fall back to [GENERAL].
 *
 * [forMeetings] = false keeps a template out of the meeting pickers
 * (Réunions tab, default template in Réglages): [NOTE_VOCALE] is used only,
 * and always, by voice notes.
 *
 * The RDV commercial sections are a proposal pending business validation.
 */
enum class MeetingTemplate(
    val id: String,
    val label: String,
    val detail: String,
    val instructions: String,
    val forMeetings: Boolean = true,
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
    );

    companion object {
        fun fromId(id: String?): MeetingTemplate = entries.firstOrNull { it.id == id } ?: GENERAL

        /** Templates offered for meetings (pickers), in display order. */
        val meetingTemplates: List<MeetingTemplate> get() = entries.filter { it.forMeetings }

        /** Like [fromId] but never returns a voice-note-only template (falls back to [GENERAL]). */
        fun meetingFromId(id: String?): MeetingTemplate = fromId(id).takeIf { it.forMeetings } ?: GENERAL
    }
}
