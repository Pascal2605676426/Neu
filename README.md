# CityBuild (Paper 26.2, Java 25)

## Bauen
Voraussetzung: JDK 25 und Maven.

    mvn clean package

Die fertige Datei liegt danach in `target/CityBuild.jar` und kommt in den `plugins`-Ordner deines Paper-Servers.
Falls Maven die `paper-api`-Version `[26.2.build,26.3.build)` nicht aufloest, trage in der `pom.xml` die genaue Version
von https://docs.papermc.io/paper/dev/project-setup/ bzw. der Paper-Downloadseite fuer 26.2 ein.

## Befehle
| Befehl | Funktion |
|---|---|
| /money [Spieler], /pay, /baltop | Geld, Ueberweisen, Rangliste |
| /eco give/take/set | Admin-Economy (citybuild.eco) |
| /plot claim, unclaim, info, trust, untrust, list | Grundstuecke (1 Chunk = 16x16) |
| /sethome, /home, /delhome, /homes | Homes |
| /spawn, /setspawn | Spawn |
| /warp, /warps, /setwarp, /delwarp | Warps |
| /sell hand, all, list | Items verkaufen |
| /citybuild reload | Config neu laden |

## Wichtige Permissions
- `citybuild.admin` - Spawn/Warps setzen, Reload
- `citybuild.eco` - /eco
- `citybuild.bypass` - Schutz ignorieren (Teammitglieder)
- `citybuild.plots.unlimited` - unbegrenzt Grundstuecke
- `citybuild.homes.<Zahl>` - mehr Homes (z.B. citybuild.homes.10)
- `citybuild.tpdelay.bypass` - kein Teleport-Delay

## Anpassen
Alles Wichtige steht in `plugins/CityBuild/config.yml`: Preise, Limits, Welten mit Schutz, Verkaufspreise, Teleport-Wartezeit.
Daten werden in `balances.yml`, `plots.yml`, `homes.yml` und `warps.yml` gespeichert.
