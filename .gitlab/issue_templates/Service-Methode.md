<!--
  TEMPLATE: Service-Methode – eine Spielaktion bzw. Spiellogik in der Service-Schicht
  Titel:  [Service] Name (x AP)      z. B.  [Service] Literatur nutzen (2 AP)
  Story Points als Label SP::x setzen, Abhängigkeiten per Quick Action:  /relate #12
  Die Checkboxen unter „Verhalten“ sind die Akzeptanzkriterien. Jede Box = mindestens ein Testfall.
  Die DoD (Review, CI, KDoc) steht im Merge-Request-Template und wird nicht hier abgehakt.
-->

## Ziel

**Als** Spieler*in **möchte ich** …, **damit** …

## Regel (Aufgabenstellung)

- 

## Schnittstelle

`PlayerActionService.methodName(param: Typ): Unit`

| Parameter | Typ | gültige Werte |
|---|---|---|
| | | |

## Vorbedingungen → Fehlerfall

| Vorbedingung | sonst |
|---|---|
| Ein Spiel läuft | `IllegalStateException` |
| Aktive Fakultät hat ≥ x AP | `IllegalStateException` |
| | `IllegalArgumentException` |

## Verhalten (Akzeptanzkriterien)

- [ ] <!-- Nachbedingung 1, z. B. „Karte liegt oben auf dem passenden Forschungsstapel“ -->
- [ ] AP der aktiven Fakultät um x reduziert
- [ ] Jeder Fehlerfall aus der Tabelle wirft die Exception und lässt den Spielzustand unverändert
- [ ] `refreshAfter…()` wird ausgelöst (Test-Refreshable)
- [ ] Log-Zeile, z. B. „…“

## Offen / geklärt

<!-- Regel-Sonderfälle: Frage notieren, Antwort der Tutorin / des Tutors mit Datum dazuschreiben -->

- 

/label ~"User Story" ~service
