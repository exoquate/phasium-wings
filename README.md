# Phasium Wings

Kosmetik-Mod für den Minecraft-Server **Phasium** (mc.phasium.cc), Fabric 1.21.1.
Dies ist der vollständige Quellcode der Version **1.3.1**, genau der Stand, der auf dem Server läuft
(die daraus gebaute Datei ist byte-genau identisch mit der veröffentlichten `phasium-wings-1.3.1.jar`).

> **Hinweis:** Der Code dieser Mod wurde mit Hilfe einer KI (Claude von Anthropic) geschrieben. Ich (Exoquate) habe
> vorgegeben, was die Mod können soll, und alles im Spiel getestet. Der Code liegt offen, damit jeder nachprüfen kann,
> was die Mod auf dem eigenen PC macht.

## Was die Mod macht

- Zeichnet Kosmetik an Spielern und NPCs: Flügel, Haare, Hüte, Gürtel, Jacken und Rüstungs-Skins, inklusive Federn
  und Funkeln als Partikel.
- Welche Kosmetik jemand trägt, kommt vom Server. Der Server schickt die Definitionen (JSON) und die Bilder (PNG) über
  die normale Minecraft-Verbindung, der Client zeichnet sie nur.
- Bringt das „Phasium Knöpfe“-Resourcepack mit (Design von Knöpfen und Menüs), es ist in der Mod eingebaut.
- Spielerbefehl `/fluegeleffekte an|aus`: Federn und Funkeln für sich selbst abschalten
  (gespeichert in `config/phasium_wings_client.txt`).

## Was die Mod auf deinem PC tut (alles, was über das Zeichnen hinausgeht)

### 1. Internetzugriff: das Phasium Live-Paket (`client/LivePack.java`)
Einmal pro Spielstart:
1. Lädt `https://mc.phasium.cc/phasium-live.sha1` (eine 40-stellige Prüfsumme).
2. Nur wenn sich die Prüfsumme geändert hat: lädt `https://mc.phasium.cc/phasium-live.zip` herunter und prüft die
   Prüfsumme. Stimmt sie nicht, wird die Datei verworfen.
3. Speichert sie als `config/phasium/phasium-live.zip` und lädt sie als **Resourcepack** (nur Bilder, Modelle, Texte;
   ein Resourcepack kann keinen Programmcode enthalten).

Keine weiteren Verbindungen, es werden keine Daten von dir gesendet (nur die normale HTTPS-Anfrage).
Ist der Server nicht erreichbar, wird die zuletzt geladene Version benutzt.

### 2. Dateizugriff: Profil-Übernahme (`client/ProfileMigration.java`)
Bei einem neuen Modpack-Profil gingen früher Karte, Wegpunkte und Tastenbelegung verloren. Deshalb läuft **einmal pro
Profil** beim allerersten Start:
1. Sucht im selben Launcher-Ordner nach anderen Profilen, die ebenfalls eine Phasium-Mod enthalten
   (`phasium-wings*` oder `phasium-assets*` im `mods`-Ordner), und nimmt das zuletzt gespielte.
2. **Kopiert** von dort (liest nur, verschiebt oder löscht nichts im alten Profil):
   - die Xaero-Karten- und Wegpunkt-Ordner, die im neuen Profil noch fehlen,
   - beim ersten Start die Einstellungen aus `options.txt` (außer der Resourcepack-Liste) und die Xaero-Minimap-Einstellungen.
3. Schreibt `config/phasium/profil-uebernahme.txt`, damit das nie wieder passiert, und notiert dort, was übernommen wurde.

Andere Profile ohne Phasium-Mod werden nicht angefasst.

### Nur auf dem Server (läuft nie auf deinem PC)
- Speicherung der getragenen Kosmetik, Befehle `/fluegelwahl`, `/kosmetikaus`, `/fluegelnpc`, `/fluegelreload`.
- `/npcskin` (nur Admins): holt den Skin eines Minecraft-Accounts über die offizielle Mojang-API für Server-NPCs.

## Selbst bauen

Voraussetzung: JDK 21 oder neuer.

```
gradle build
```

Die fertige Datei liegt danach in `build/libs/`. Wer prüfen will, ob sie der veröffentlichten Datei entspricht,
vergleicht die SHA-1-Prüfsumme: `4f57255546530e296eea04d8d87a7b050a28a68c` (phasium-wings-1.3.1.jar).
Je nach Gradle- und Java-Version kann die gebaute Datei leicht abweichen (Zeitstempel/Reihenfolge im Archiv),
der Inhalt der Klassen ist derselbe.

## Dateien

| Pfad | Inhalt |
|---|---|
| `src/main/java/cc/phasium/wings/` | Server-Teil: Kosmetik speichern, Befehle, Netzwerk-Pakete |
| `src/main/java/cc/phasium/wings/client/` | Client-Teil: Zeichnen, Partikel, Live-Paket, Profil-Übernahme |
| `src/main/java/cc/phasium/wings/mixin/client/` | Zwei kleine Eingriffe: Live-Paket als Resourcepack anmelden, Rüstung ausblenden, wenn ein Rüstungs-Skin getragen wird |
| `src/main/resources/` | Modelle, Bilder und Partikel der eingebauten Flügel |
| `libs/Phasium-Knoepfe.zip` | Das eingebaute Knöpfe-Resourcepack |
| `libs/fabric-permissions-api-0.3.1.jar` | Fremde Bibliothek von lucko (MIT-Lizenz), nur zum Kompilieren der Rechte-Abfragen |

## Lizenz

Alle Rechte vorbehalten. Der Code ist **nur zur Einsicht** veröffentlicht: lesen, prüfen und zum Vergleich selbst
bauen ist erlaubt, kopieren, verändern oder weiterverwenden nicht. Details in `LICENSE`.
