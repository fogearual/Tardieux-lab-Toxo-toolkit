# Count and Colocalise

*Plugins ▸ Toxo Toolkit ▸ Count and Colocalise* — Java plugin `Count_and_Colocalise.jar`, version 24
(formerly the macro *Parasite Quantification Tool*). Needs Fiji with Bio-Formats.

Batch analysis of folders of fluorescence images: counts **host nuclei**, **parasites** and
**double-labelled parasites**, writes QC stacks, CSV files, a workbook and histograms; can also make
**Z projections, merges and TIFF copies**. Everything is set in dialogs (each with a *Help* button) and
remembered for the next run.

## Input

`<folder>/<experiment>/<condition>/images` (or one experiment, one folder, one file). Any file type
(czi, nd, lif, nd2, tif...); MetaMorph `.nd` is read without Bio-Formats. Z stacks are max-projected;
multi-series files can be split. A "one TIFF per channel" layout (slides / coverslips) is also supported.

## Dialogs and main parameters

**1. What to do** — count objects, make images, or both; start from the last run's settings, the
validated defaults, or any earlier `Settings.txt`.

**2. Input and outputs** — folder / file, file types, how to open them, **Parallel** (images analysed at
once; 0 = automatic, from the processors and the memory; lower it if Fiji runs short of memory), **pixel
size** (from the image or forced; all areas are in µm²), outputs (QC stacks, workbook, histograms;
hyperstack, projection, merges).

**3. What to count** — nuclei, parasites, double-labelled; the **reported measure** (single-labelled,
all, double-labelled parasites — per 100 host cells when nuclei are counted — or % double-labelled);
how double labelling is scored:
- *overlap*: ≥ minimum overlap (92 %) of the parasite is marker-positive;
- *marker objects inside the parasite* (beads...): at least N objects of a given size on it;
- *parasite inside a marker object* (vacuole, cell).
Option **poles**: is the marker at the basal or apical pole (orientation from the parasite nucleus
and/or a pole marker).

**4. Channels** — role of each channel (nuclear stain, parasites, marker, pole marker, DIC), its colour
in the images made, and whether it goes into the merges. Suggested from the channel colours.

**5. Detection settings** (validated defaults):

| | Nuclei | Parasites | Marker |
|---|---|---|---|
| Threshold (method × factor) | Triangle × 1.0 | Triangle × 4.25 | Triangle × 2.8 |
| Area | ≥ 105 µm² | 7.25–62.5 µm² | overlap ≥ 92 % |
| Background / blur | 80 px / 1.5 px | 30 px / 0.8 px | 30 px / 0.8 px |
| Watershed | yes | yes | — |

Other settings in this dialog:
- **Bright cap** (5 × median): a very bright object (debris, dead cell) is left out of the threshold
  calculation so it cannot raise the threshold.
- **Clusters**: a nucleus (or clump of parasites) much larger than the median counts as several;
  optional **fragment** rule for small pieces.
- **Nuclei at the border**: count all, leave out those touching it, or those more than half outside.
- **Stop early**: stop each condition when a target number of cells / parasites is reached.
- **Minimum per image**: images with too few objects are left out (or used only if needed).

**Calibrate** — tune the settings of nuclei, parasites or double labelling on chosen or random images,
with a live preview and sliders, before running.

## Outputs

In `Results/` inside the input folder:

- `Raw data.csv` (one row per image) and one CSV per condition; `Parasites.csv` (per parasite, when
  objects / poles are used);
- results workbook `.xls` (summary per experiment, % of control when a condition name contains
  "control" or "ctrl");
- histograms per experiment and pooled;
- `Stacks_<name>/…/<image>_QC_Stack.tif`: annotated QC stacks (settings and counts in their Info);
- `Hyperstacks/`, `Projections/`, `Merges/`, `Projection merges/`: images made;
- `Settings.txt` and `Log.txt`.

Every option is explained under *Help* in each dialog (and in the header of the macro version).

## Java and macro versions

The plugin runs the very steps of the macro version 23.1 (`src/count-and-colocalise/macro/`), image by
image, with the same ImageJ functions: same counts, same QC stacks, same files - checked file by file on
33 test cases (all options, Calibrate included). It is faster because several images are analysed at
once: on 200 images, 2.6 times faster with 2 processors (1.5 times on one); the gain grows with the number
of processors. *Esc* stops a run (the images already done keep their QC stacks and ROI files; no result
table is written). The two versions read each other's `Settings.txt`.
