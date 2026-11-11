<!--
  MERGE-REQUEST-TEMPLATE – hier wird die Definition of Done geprüft.
  Titel:  #<Issue> Kurzbeschreibung      z. B.  #14 Publizieren implementiert
  „Closes #x“ schließt das Issue beim Merge automatisch (→ Spalte Closed).
-->

Closes #

## Was wurde geändert?

- 

## Wie testen?

<!-- Welche Tests decken es ab? Bei GUI: welche Klicks zeigen das Feature? -->

- 

## Definition of Done (Autor*in)

- [ ] Alle Akzeptanzkriterien des Issues abgehakt
- [ ] Tests für Erfolgs- und Fehlerfälle, `./gradlew build` lokal grün
- [ ] KDoc für alle öffentlichen Klassen/Methoden, keine neuen detekt-Warnungen
- [ ] Architektur eingehalten: GUI ändert keine Entities, Services sind stateless
- [ ] Reviewer*in eingetragen, Issue auf **In Review**

## Review (Reviewer*in)

- [ ] Spielregel mit der Aufgabenstellung verglichen
- [ ] Tests sinnvoll und vollständig (auch Fehlerfälle)
- [ ] Code verständlich; Anmerkungen als Kommentare hinterlassen
- [ ] CI-Pipeline grün → Approve
