package com.phequals7.muesli.meetings

/**
 * Clinitex meeting note templates (Lot 2). [instructions] is sent as
 * `templateInstructions` to the n8n summary webhook; the section headings
 * it lists are the headings Claude writes in the meeting notes.
 *
 * Ids stay stable: they are stored in RecordingSession.templateId and
 * unknown ids (old Muesli templates) fall back to [GENERAL].
 *
 * The RDV commercial sections are a proposal pending business validation.
 */
enum class MeetingTemplate(
    val id: String,
    val label: String,
    val detail: String,
    val instructions: String,
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
    );

    companion object {
        fun fromId(id: String?): MeetingTemplate = entries.firstOrNull { it.id == id } ?: GENERAL
    }
}
