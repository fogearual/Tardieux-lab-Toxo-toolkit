# Toxo Toolkit

Four Fiji / ImageJ tools for fluorescence and live imaging, grouped in one menu:

```
Plugins ▸ Toxo Toolkit ▸ Count and Colocalise
                       ▸ Drift Correction
                       ▸ Object Velocity
                       ▸ Smart Time Stamper
```

| Tool | What it does | Type | Documentation |
|---|---|---|---|
| **Count and Colocalise** | Counting of host nuclei, parasites and double-labelled parasites on whole folders of images (CZI, ND, LIF, ND2, TIFF...), with annotated QC stacks, CSV files, an Excel workbook and histograms; Can also make Z projections, merges and TIFF copies of any files for easier automated handling of whole folders. | Macro (`.ijm`) | [How to use/count-and-colocalise.md](How to use/count-and-colocalise.md) |
| **Drift Correction** | Removes the drift of a movie by following a fixed object you outline (bead, debris, landmark), then crops the movie to the area present in every frame. Automatic, semi-automatic (recommended) or manual tracking, calibration on random frames, review before applying. | Java plugin (`.jar`) | [How to use/drift-correction.md](How to use/drift-correction.md) |
| **Object Velocity** | Speed of objects you point by hand on a movie (beads, parasites...): one track per object, speed along the track, straight-line speed, step speeds, straightness. | Macro (`.ijm`) | [How to use/object-velocity.md](How to use/object-velocity.md) |
| **Smart Time Stamper** | Writes the real time on every frame of a movie from each frame's original number, so that a substack of non-consecutive frames gets the right times. | Macro (`.ijm`) | [How to use/smart-time-stamper.md](How to use/smart-time-stamper.md) |

## Installation

1. Download this repository (green **Code** button ▸ *Download ZIP*) and unzip it.
2. Copy the folder **`Toxo Toolkit`** (with its four files) into the `plugins` folder of Fiji:
   `Fiji.app/plugins/Toxo Toolkit/`.
   On macOS, `Fiji.app` is a package: right-click it ▸ *Show Package Contents*.
3. Restart Fiji. The tools appear under *Plugins ▸ Toxo Toolkit*.

- **The folder name must contain a space, not an underscore**

Requirements: Fiji (or ImageJ 1.53b or later; 1.52 for Count and Colocalise) with Java 8 or later.
Count and Colocalise needs Bio-Formats (included in Fiji) for the files it does not open itself.

## Repository layout

```
Toxo Toolkit/                    the folder to copy into Fiji.app/plugins/
    Count_and_Colocalise.ijm
    Drift_Correction.jar
    Object_Velocity.ijm
    Smart_Time_Stamper.ijm
Source-codes/            source code of Drift_Correction.jar
    Drift_Correction.java
    plugins.config
How to use/                            one page per tool: every parameter, every output, what the code does
```

The macros are their own source code: open them in Fiji (*File ▸ Open*) or any text editor. Each one
starts with a commented header that sums up what it does.

## Settings that are remembered

Every tool remembers its last settings between runs (ImageJ preferences). Count and Colocalise also
writes the settings of each run to `Settings.txt` next to its results, and can start again from any of
them.
