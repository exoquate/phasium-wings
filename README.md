# Phasium Wings

🇬🇧 [English version below](#english) · 🇩🇪 Deutsch

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

---

<a id="english"></a>
# Phasium Wings (English)

Cosmetic mod for the Minecraft server **Phasium** (mc.phasium.cc), Fabric 1.21.1.
This is the complete source code of version **1.3.1**, exactly what runs on the server
(the file built from it is byte-identical to the published `phasium-wings-1.3.1.jar`).

> **Note:** The code of this mod was written with the help of an AI (Claude by Anthropic). I (Exoquate) decided what
> the mod should do and tested everything in game. The code is public so that anyone can check what the mod does on
> their own PC.

## What the mod does

- Draws cosmetics on players and NPCs: wings, hair, hats, belts, jackets and armour skins, including feathers and
  sparkles as particles.
- Which cosmetics someone wears comes from the server. The server sends the definitions (JSON) and the images (PNG)
  over the normal Minecraft connection; the client only draws them.
- Contains the built-in "Phasium Knöpfe" resource pack (the look of buttons and menus).
- Player command `/fluegeleffekte an|aus`: turn feathers and sparkles off for yourself
  (stored in `config/phasium_wings_client.txt`).

## What the mod does on your PC (everything beyond drawing)

### 1. Internet access: the Phasium live pack (`client/LivePack.java`)
Once per game start:
1. Downloads `https://mc.phasium.cc/phasium-live.sha1` (a 40 character checksum).
2. Only if the checksum changed: downloads `https://mc.phasium.cc/phasium-live.zip` and checks the checksum.
   If it does not match, the file is discarded.
3. Stores it as `config/phasium/phasium-live.zip` and loads it as a **resource pack** (images, models and texts only;
   a resource pack cannot contain program code).

No other connections, no data about you is sent (only the normal HTTPS request).
If the server cannot be reached, the last downloaded version is used.

### 2. File access: profile migration (`client/ProfileMigration.java`)
With a new modpack profile, players used to lose their map, waypoints and key bindings. So **once per profile**,
on the very first start:
1. Looks in the same launcher folder for other profiles that also contain a Phasium mod
   (`phasium-wings*` or `phasium-assets*` in the `mods` folder) and takes the most recently played one.
2. **Copies** from there (it only reads; nothing is moved or deleted in the old profile):
   - the Xaero map and waypoint folders that the new profile does not have yet,
   - on the first start, the settings from `options.txt` (except the resource pack list) and the Xaero minimap settings.
3. Writes `config/phasium/profil-uebernahme.txt` so it never runs again, noting what was copied.

Other profiles without a Phasium mod are not touched.

### Server only (never runs on your PC)
- Storing worn cosmetics, commands `/fluegelwahl`, `/kosmetikaus`, `/fluegelnpc`, `/fluegelreload`.
- `/npcskin` (admins only): fetches the skin of a Minecraft account from the official Mojang API for server NPCs.

## Building it yourself

Requirement: JDK 21 or newer.

```
gradle build
```

The file ends up in `build/libs/`. To check that it matches the published file, compare the SHA-1 checksum:
`4f57255546530e296eea04d8d87a7b050a28a68c` (phasium-wings-1.3.1.jar).
Depending on the Gradle and Java version, the built file can differ slightly (timestamps/order inside the archive);
the content of the classes is the same.

## Files

| Path | Content |
|---|---|
| `src/main/java/cc/phasium/wings/` | Server part: storing cosmetics, commands, network packets |
| `src/main/java/cc/phasium/wings/client/` | Client part: drawing, particles, live pack, profile migration |
| `src/main/java/cc/phasium/wings/mixin/client/` | Two small hooks: register the live pack as a resource pack, hide armour while an armour skin is worn |
| `src/main/resources/` | Models, images and particles of the built-in wings |
| `libs/Phasium-Knoepfe.zip` | The built-in buttons resource pack |
| `libs/fabric-permissions-api-0.3.1.jar` | Third-party library by lucko (MIT licence), only needed to compile the permission checks |

## Licence

All rights reserved. The code is published **for inspection only**: reading, reviewing and building it yourself for
comparison is allowed; copying, modifying or reusing it is not. Details in `LICENSE`.
