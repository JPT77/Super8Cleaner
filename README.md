# Super-8 Debug Pipeline (Java / Gradle / Eclipse)

Java-Portierung der Super-8 OpenCV-Pipeline. Nutzt **JavaCV** (bündelt OpenCV + FFmpeg
als native Bibliotheken), daher ist **keine** manuelle OpenCV/FFmpeg-Installation nötig.

## Funktionen
1. **Inhaltsprüfung** (`isValidFrame`) **vor** jeder Crop-Erkennung
2. Vertikale Suche nur in den **oberen 20 %** bzw. **unteren 10 %**
3. Globaler Crop (horizontal **und** vertikal) über **Median**
4. CSV-Ausgabe → `output/analysis.csv` (inkl. `scene_id`)
5. **Szenenerkennung** mit fortlaufender `scene_id` pro Frame
6. **Szenenexport** als einzelne Dateien (`output/scenes/scene_000.mkv` …)
7. **FFV1 (lossless)** im `.mkv`-Container statt `mp4v`
8. **Debug-Preview** `output/debug_preview.mkv` mit Overlay (Crop-Rahmen, Szene, Schnitt)
9. **Weißabgleich pro Szene** mit **begrenzter Korrektur** (statt ungebremstem Gray-World)

## Voraussetzungen
- JDK 17+
- Gradle (oder der mitgelieferte Wrapper `./gradlew`)

## Bauen & Ausführen (Kommandozeile)
```bash
# Standard: input.mp4 -> output/
./gradlew run

# Eigene Pfade
./gradlew run --args="meinfilm.mp4 ergebnis"

# Nur die Native-Libs der aktuellen Plattform laden (kleinerer Download!)
./gradlew run -Djavacpp.platform=linux-x86_64   --args="input.mp4 output"
# weitere Werte: windows-x86_64, macosx-x86_64, macosx-arm64, linux-arm64
```
> Beim ersten Lauf lädt Gradle die JavaCV-Abhängigkeiten. Ohne `-Djavacpp.platform`
> werden die nativen Bibliotheken **aller** Plattformen geladen (mehrere hundert MB).

## Eclipse-Import
1. **File → Import… → Gradle → Existing Gradle Project**
2. Projektordner `super8-debug-java` wählen → Finish (Buildship lädt die Abhängigkeiten).

Alternativ klassische Eclipse-Metadaten erzeugen:
```bash
./gradlew eclipse
```
Danach **File → Import… → General → Existing Projects into Workspace**.

## Konfiguration
Pfade per Argument (`args[0]`=Video, `args[1]`=Ausgabeordner) oder Umgebungsvariablen
`SUPER8_INPUT` / `SUPER8_OUTPUT`. Parameter (Schwellwerte, Crop-Anteile, max. WB-Gain)
in `Config.java`.

## Projektstruktur
```
super8-debug-java/
├── build.gradle
├── settings.gradle
├── gradlew / gradlew.bat / gradle/wrapper/...
└── src/main/java/com/super8/debug/
    ├── Main.java            # Einstiegspunkt
    ├── Config.java          # konfigurierbare Pfade & Parameter
    ├── ImageOps.java        # OpenCV-Bildoperationen
    ├── FrameRecord.java     # CSV-Zeilenmodell
    └── Super8Pipeline.java  # Pipeline (Laden, Analyse, CSV, Preview, Szenenexport)
```
