# Team-Regeln – SoPra Projekt 1, Gruppe 07

> Vorschlag, gemeinsam im Team abstimmen und anpassen. Wenn ihr euch geeinigt habt, haltet ihr euch daran.

## Definition of Ready (DoR)

Ein Issue darf im Sprint Planning nur dann ins **Sprint Backlog**, wenn:

1. das Ziel klar ist („Als … möchte ich … damit …“ bzw. bei Aufgaben das Ergebnis),
2. die Akzeptanzkriterien prüfbar sind (bei Service-Methoden: Vorbedingungen, Fehlerfälle und Verhalten ausgefüllt),
3. es geschätzt ist (Label `SP::x`) und höchstens 8 SP hat, sonst wird es aufgeteilt,
4. Abhängigkeiten verknüpft sind (`/relate #x`) und offene Regelfragen geklärt oder notiert sind.

## Definition of Done (DoD)

Die DoD wird im **Merge Request** abgehakt (MR-Template), nicht im Issue. Ein Issue ist erst **fertig**, wenn alles davon erfüllt ist:

1. Alle Akzeptanzkriterien des Issues sind erfüllt.
2. Unit-Tests sind geschrieben (bei Service-Methoden: Erfolgsfall **und** alle Fehlerfälle) und laufen grün.
3. Alle öffentlichen Klassen und Methoden haben KDoc, es gibt keine neuen detekt-Warnungen.
4. Die CI-Pipeline ist grün.
5. Mindestens eine **andere** Person hat den Merge Request reviewt und freigegeben.
6. Der Code ist in `main` gemergt und das Issue ist geschlossen.

„Fast fertig“ heißt nicht fertig. Unfertiges geht am Sprintende zurück ins Backlog.

## Ablauf eines Issues auf dem Board

| Spalte | Bedeutung | Wer schiebt? |
|---|---|---|
| **Backlog** (Open) | Idee/Anforderung, noch nicht für diesen Sprint eingeplant | – |
| **Sprint Backlog** | Im Sprint Planning ausgewählt, hat SP-Schätzung und Milestone | Team im Planning |
| **In Work** | Jemand arbeitet daran. **Assignee setzen!** | Bearbeiter*in |
| **In Review** | Merge Request ist offen, Reviewer*in ist eingetragen | Bearbeiter*in |
| **Closed** | Merge Request gemergt (`Closes #x` schließt automatisch) | GitLab |

Regel: Pro Person höchstens **2 Issues gleichzeitig** in *In Work*.

## Branches & Commits

- `main` ist geschützt. Dort landet nur, was per Merge Request reviewt wurde.
- Ein Branch pro Issue: `<issue-nr>-<kurzer-name>`, z. B. `14-publizieren`. Am einfachsten über den Button **„Create merge request“** im Issue, der legt Branch und MR automatisch an.
- Commit-Nachrichten im Imperativ und mit Issue-Nummer, z. B. `#14 Punkteberechnung beim Publizieren ergänzt`.
- **Früh und oft committen und pushen.** Die Bewertung ist individuell, und eure Beiträge müssen in GitLab sichtbar sein.

## Reviews

- Wer einen MR öffnet, trägt eine Reviewer*in ein. Reviews möglichst innerhalb von 24 h.
- Reviewt wird nach der Checkliste im MR-Template. Kommentare sind Vorschläge, Diskussionen bleiben sachlich.
- Wer den MR geöffnet hat, merged erst nach dem Approve.

## Architektur-Regeln (aus der Vorlesung)

- **Entity**: hält nur Daten, höchstens triviale lokale Methoden wie `nextFaculty()` oder `addResearch()`.
- **Service**: enthält die gesamte Spiellogik und ist **stateless**. Jede Service-Methode arbeitet in drei Schritten:
  1. Eingaben und Spielzustand prüfen. Bei Verstoß eine Exception werfen (`IllegalStateException` / `IllegalArgumentException`).
  2. Logik ausführen.
  3. Refreshes auslösen (`onAllRefreshables { … }`).
- **GUI**: ruft nur Service-Methoden auf und ändert **nie** direkt Entities.
- Ein komplettes Spiel muss sich allein über die Service-Schicht im Testcode durchspielen lassen.

## Scrum im Team

- **Scrum Master** rotiert pro Sprint. Sprint 1: Ilia Orlov, Sprint 2: ________
- **Daily** 2–3× pro Woche, max. 15 min (Was habe ich geschafft? Was mache ich als Nächstes? Was blockiert mich?):
  Dienstag und Donnertsag um ____ Uhr (Ort/Discord: Universität und Discord-Gruppe)
- **Story Points**: Fibonacci 1, 2, 3, 5, 8, 13, im Planning Poker geschätzt, als Label `SP::x`. Issues über 8 SP werden aufgeteilt.
- **Sprintziel** steht in der Beschreibung des Milestones (`P1 Sprint 1`, `P1 Sprint 2`).
- **Retro-Maßnahmen** (1–3 pro Sprint) werden als Issues mit Label `orga` angelegt und im nächsten Sprint nachgehalten.
- **Sprint Report**: jede*r selbst, **Vortag der Retro bis 18:00 Uhr** in Moodle. Wer zu spät abgibt, bekommt 0 Punkte auf der Score Card.
- **Peer Feedback**: **Tag der Retro bis 23:59 Uhr** an die Tutorin / den Tutor.
