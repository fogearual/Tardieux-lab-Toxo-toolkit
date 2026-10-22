# Toxo Toolkit

Four Fiji / ImageJ tools for fluorescence and live imaging of *Toxoplasma gondii* (and of any other
sample with the same needs), grouped in one menu:

```
Plugins ▸ Toxo Toolkit ▸ Count and Colocalise
                       ▸ Drift Correction
                       ▸ Object Velocity
                       ▸ Smart Time Stamper
```

| Tool | What it does | Type | Documentation |
|---|---|---|---|
| **Count and Colocalise** | Batch counting of host nuclei, parasites and double-labelled parasites on whole folders of images (CZI, ND, LIF, ND2, TIFF...), with annotated QC stacks, CSV files, an Excel workbook and histograms; also makes Z projections, merges and TIFF copies. | Macro (`.ijm`) | [docs/count-and-colocalise.md](docs/count-and-colocalise.md) |
| **Drift Correction** | Removes the drift of a movie by following a fixed object you outline (bead, debris, landmark), then crops the movie to the area present in every frame. Automatic, semi-automatic or manual tracking, calibration on random frames, review before applying. | Java plugin (`.jar`) | [docs/drift-correction.md](docs/drift-correction.md) |
| **Object Velocity** | Speed of objects you point by hand on a movie (beads, parasites...): one track per object, speed along the track, straight-line speed, step speeds, straightness. | Macro (`.ijm`) | [docs/object-velocity.md](docs/object-velocity.md) |
| **Smart Time Stamper** | Writes the real time on every frame of a movie from each frame's original number, so that a substack of non-consecutive frames gets the right times. | Macro (`.ijm`) | [docs/smart-time-stamper.md](docs/smart-time-stamper.md) |

## Installation

1. Download this repository (green **Code** button ▸ *Download ZIP*) and unzip it.
2. Copy the folder **`Toxo Toolkit`** (with its four files) into the `plugins` folder of Fiji:
   `Fiji.app/plugins/Toxo Toolkit/`.
   On macOS, `Fiji.app` is a package: right-click it ▸ *Show Package Contents*.
3. Restart Fiji (or *Help ▸ Refresh Menus*). The tools appear under *Plugins ▸ Toxo Toolkit*.

Two details matter:

- **The folder name must contain a space, not an underscore** (`Toxo Toolkit`, not `Toxo_Toolkit`).
  ImageJ keeps underscores in folder names, while the Java plugin declares its menu as
  `Toxo Toolkit`: with an underscore, the menu would be split in two.
- **Keep the file names.** For the macros, the file name gives the menu command (underscores become
  spaces). For the plugin, the class name `Drift_Correction` must stay unique: remove any older copy of
  the drift plugin (for example `Drift_Objet_Fixe.jar`) from the `plugins` folder.

Requirements: Fiji (or ImageJ 1.53b or later; 1.52 for Count and Colocalise) with Java 8 or later.
Count and Colocalise needs Bio-Formats (included in Fiji) for the files it does not open itself.

## Repository layout

```
Toxo Toolkit/                    the folder to copy into Fiji.app/plugins/
    Count_and_Colocalise.ijm
    Drift_Correction.jar
    Object_Velocity.ijm
    Smart_Time_Stamper.ijm
src/drift-correction/            source code of Drift_Correction.jar
    Drift_Correction.java
    plugins.config
docs/                            one page per tool: every parameter, every output, what the code does
```

The macros are their own source code: open them in Fiji (*File ▸ Open*) or any text editor. Each one
starts with a commented header that sums up what it does.

## Building the Drift Correction plugin from source

The `.jar` in `Toxo Toolkit/` is ready to use. To rebuild it, with a JDK (8 or later) and the
`ij.jar` of your Fiji (`Fiji.app/jars/ij-1.xx.jar`):

```bash
cd src/drift-correction
mkdir -p build
javac --release 8 -encoding UTF-8 -cp /path/to/ij.jar -d build Drift_Correction.java
cp plugins.config Drift_Correction.java build/
cd build && jar cf ../Drift_Correction.jar plugins.config Drift_Correction*.class Drift_Correction.java
```

`plugins.config` puts the command in the menu:
`Plugins>Toxo Toolkit, "Drift Correction", Drift_Correction`.

## Settings that are remembered

Every tool remembers its last settings between runs (ImageJ preferences). Count and Colocalise also
writes the settings of each run to `Settings.txt` next to its results, and can start again from any of
them.

## Using the tools in macros

All four tools can be called with `run()`. Options are given as `key=value`, where the key is the first
word of the field label in lower case (see each documentation page). Examples:

```javascript
run("Drift Correction", "mode=Automatic detection=[Similarity (correlation)] radius=20 doubtful=0.8 lost=0.5 registration=[Whole pixels (raw values)]");
run("Object Velocity", "original=1-60 frame=[0.87 s] pixel=0.11");
```
