# Count and Colocalise

*Plugins ▸ Toxo Toolkit ▸ Count and Colocalise* — macro `Count_and_Colocalise.ijm`, version 23
(Fiji, ImageJ 1.52 or later, with Bio-Formats). Formerly named *Parasite Quantification Tool*.

Batch analysis of fluorescence images of infected host cells. On whole folders of images it can:

- **count** host nuclei, parasites and double-labelled parasites (any of them), and score where a
  second marker lies on the parasites (overlap, marker objects such as beads, poles);
- write **annotated QC stacks**, **CSV files**, an **Excel workbook** and **histograms**;
- **make images**: hyperstack TIFF, maximum Z projection, composite merges.

Nothing has to be edited in the code: everything is set in a sequence of dialogs, each showing only
what the task needs, and each with a *Help* button. Every setting is remembered for the next run, and
written to `Settings.txt` next to the results.

## Input

**Folder layout**

```
<folder>/<experiment>/<condition>/*      several experiments
<folder>/<condition>/*                   a single experiment
```

A single folder of images, or a single file, are also accepted. A condition whose name contains
"control", "controle", "ctrl" or "temoin" (any case) is a control: it is used for the "% of control"
column of the workbook.

**File types**: any, given by their extensions (`czi nd lif nd2 lsm oib tif`...). How they are opened:

- *Automatic* (default): MetaMorph `.nd` read by the macro itself, TIFF with ImageJ, everything else
  with the Bio-Formats Importer;
- or every file with the Bio-Formats Importer, the Bio-Formats simple opener, or ImageJ only.

Z stacks are max-projected before counting; a single plane is used as it is. Of a time series only the
first time point is counted (the image outputs keep them all). A file holding several series (stage
positions of a `.nd`, scenes of a CZI, series of a LIF...) gives one entry per series, `<file>_s1`,
`<file>_s2`...: always for a `.nd`, for other files when ticked.

**MetaMorph `.nd`** (read without Bio-Formats): the `.nd` describes the acquisition and the images are
the TIFF / STK files next to it (`<nd name>_w<n><wavelength>_s<position>_t<time point>.TIF`). They are
found from the `.nd` and put together as one image per position, channels in the order of the
wavelengths; a wavelength acquired without Z (transmitted light) is repeated over the Z planes of the
others. Each channel gets a colour from its wavelength name (DAPI blue, GFP green, TxRed red...). The
pixel size comes from the MetaMorph metadata of the TIFF files.

**One TIFF per channel** (slides / coverslips):
`<folder>/<experiment>/data/<name>--W00003--P00020--Z00000--T00000--DAPI.tif`. Each experiment folder is
a slide, each W number a coverslip (the condition), each P number a field; channels are recognised from
the end of the file name (DAPI, FITC, Texas Red by default). Several Z files of a field are
max-projected. With this layout: one histogram per slide, no pooled histogram, no image outputs.

## Dialog 1 — what to do

| Field | Default | What it does |
|---|---|---|
| **I want to** | Count objects | *Count objects (QC stacks, results)*, *Make images (Z projections, merges, TIFF)*, or *Both*. |
| **Detection settings to start from** | Those of my last run | *Those of my last run* (remembered file in the ImageJ preferences folder), *The validated defaults* (validated on the invasion assay, listed below), or *A settings file I choose* (the `Settings.txt` of any earlier run). |

## Dialog 2 — input and outputs

| Field | Default | What it does |
|---|---|---|
| **Analyse** | A folder | *A folder (experiments and/or conditions in subfolders)*, *A single folder of images*, *A single image file*. |
| **Images** | Multi-channel files | *Multi-channel files, of the types below* or *One TIFF per channel (slides / coverslips)* (counting only). |
| **File types** | `czi nd` | Extensions of the files to analyse, separated by spaces. |
| **A file holding several positions or series: analyse each one** | unticked | One entry per series (always for `.nd`). Reads the files with Bio-Formats. |
| **Open the files with** | Automatic | See *Input*. |
| **Batch mode** | ticked | Faster (images not displayed). Untick it if files fail to open (Bio-Formats sometimes conflicts with it). |
| **Pixel size** + **value** | From the image; 0.11 µm | *From the image, the value given if it has none*, or *The value given, whatever the image says*. Every area setting is in µm², so the pixel size decides what is counted. Calibrations in nm, mm, cm or m are converted; a calibration that is not a length (inches from a DPI tag, a blank unit) or out of the range of a microscope (0.005–100 µm) is not used, and the Log says why. |
| **Counting outputs** | — | *Annotated QC stacks*, *Results workbook (.xls)*, *Histogram per experiment*, *Pooled histogram*. The counts always go to CSV files. |
| **Images to make** | — | *Hyperstack TIFF (all channels, all Z)*, *Maximum Z projection*, *Composite merge, all Z planes*, *Composite merge of the projection*. Merges stay multi-channel at the original bit depth (not flattened to RGB), each channel in the colour chosen for it. |
| **Choose the output folder myself** | unticked | Otherwise the results go to `Results/` inside the input folder. |

## Dialog 3 — what to count (counting only)

| Field | Default | What it does |
|---|---|---|
| **Nuclei (host cells)**, **Parasites**, **Double-labelled parasites** | — | What is counted. Only the channels of what is counted are needed. |
| **Reported measure** | Single-labelled parasites | *Single-labelled parasites (parasites minus double-labelled)* — the intracellular parasites of a red/green invasion assay; *All parasites*; *Double-labelled parasites*; *Double-labelled parasites, % of all parasites*. The first three are given per 100 host cells when nuclei are counted, as a count per image otherwise. Without double labelling counted the measure is all parasites; with no parasites counted, the host cells. The workbook formulas, the CSV "Measure" column and the histograms follow it. |
| **Double labelling** | Overlap | How a parasite is scored double-labelled: *Overlap of the parasite with the marker* (at least the minimum overlap of its area is positive in the marker mask); *Marker objects inside the parasite* (the marker is cut into objects of a given size — beads, a labelled structure — each belonging to the parasite holding its centre, or within a given distance of its outline; a parasite holding at least N counted objects is double-labelled); *The parasite inside a marker object* (a vacuole, a cell: at least the minimum overlap of its area lies in a marker object of the given size). |
| **Poles** | unticked | Also classes each double-labelled parasite by where its marker lies: basal pole, apical pole, pole indeterminable, not at a pole (see *Poles*). |
| **QC stack panels** | all but raw | Panels of each QC stack: *Nuclei*, *Parasites*, *Marker*, *Marker + parasites*, *Poles*, *All channels*, and *Each channel also raw* (without outlines). |

## Dialog 4 — channels

One row per channel of the first image, with the colour and the name its file gives it (read from its
metadata without opening it in full):

- **what it is**: nuclear stain, parasite label, second marker, pole marker, transmission / DIC, or not
  used. Suggested from its colour (blue = nuclear stain, green = parasites, red = second marker, grey =
  DIC): check it. One channel for each role.
- **its colour** in the images made: as in the file, or grey, blue, green, red, cyan, magenta, yellow. A
  channel shown in grey by its file gets the usual colour of its role.
- **in the merges**: whether it goes into the merges (7 channels at most).
- **Channels in another order in some files: find each one by its colour in the first file**: each file
  is then sorted out on its own (the channels used need different colours in the first file).

With one TIFF per channel, the fields are the file-name words of each channel (*Nuclear stain* `DAPI`,
*Parasite label* `FITC`, *Second marker* `Texas`, *Pole marker*), and the *Subfolder of the TIFFs* (`data`).

## Double labelling: objects and poles (when used)

| Field | Default | What it does |
|---|---|---|
| **Object area from … to** (marker objects inside the parasite) | 0.1 – 10 µm² | Size of the marker objects (beads...). |
| **Its centre at most … µm outside the parasite** | 0.5 µm | An object whose centre lies this far outside the parasite outline still belongs to it (beads sit on the surface, just outside the parasite label). |
| **At least … counted object(s) per double-labelled parasite** | 1 | |
| **Split touching objects (watershed)** | ticked | |
| **Objects counted** | Anywhere | *Anywhere on the parasite*, *In its centre*, *At an extremity*. |
| **Object area from … to** (parasite inside a marker object) | 20 – 5000 µm² | Size of the enclosing objects (vacuole, cell). |
| **Centre: the central … % of its length** | 30 % | Places along a parasite are read on its long axis, tip to tip (Feret diameter). |
| **Extremity (pole): the last … % at each end** | 20 % | |
| **Oriented by** | The parasite nucleus | *The parasite nucleus (nuclear stain)*: the end nearest the parasite's nucleus is basal (the nucleus of a tachyzoite lies in its basal half); *A pole marker*; *The nucleus and a pole marker* (one is enough; if they disagree the parasite is not oriented). |
| **The pole marker marks** | The basal pole | *The basal pole* or *The apical pole (e.g. extruded conoid)*. |
| **Nucleus of a parasite from … to** | 0.8 – 15 µm² | Size of parasite nuclei in the nuclear stain (host nuclei and the apicoplast are told apart by size). |
| **Pole marker: threshold factor**, **object from … to** | 2.0; 0.05 – 5 µm² | Detection of the pole marker. |
| **No orientation when the reference lies within … % of the centre** | 5 % | A reference near the centre of the parasite tells nothing. |

## Dialog 5 — detection settings

| Field | Default | What it does |
|---|---|---|
| **Next** | Count with the settings below | Or *Calibrate nuclei / parasites / double labelling first* (see *Calibrate*). |
| **Calibrate on** | One image I choose | *One image I choose*, *One image picked at random*, *One random image from each condition* (random picks come from the folder to analyse). |
| **Nuclei: Threshold factor** | 1.0 | Multiplies the lower bound of the automatic threshold (higher = stricter). |
| **Nuclei: bright cap** | 5 × median | See *Bright cap*. 0 = off. |
| **Nuclei: Minimum area** | 105 µm² | Smaller objects are not nuclei. |
| **Overlapping nuclei count as several cells**, **above**, **at most** | ticked; 1.7 × median; 4 cells | A nucleus clearly larger than the median nucleus of its field is a cluster: counted as area / median cells (rounded, at most the number given). |
| **No cell under** | 0 × median (off) | A nucleus smaller than this × the median is a fragment (usually a piece cut off by the watershed) and counts as no cell. |
| **Nuclei cut by the border** | Count them all | *Count them all*; *Leave out nuclei touching the border*; *Leave out nuclei more than half outside* (the hidden part is inferred from the visible one: a parabola fitted to the squared widths parallel to the border gives the depth of the centre). Left-out nuclei are drawn grey and saved as `<image>_nuclei_border_excluded_ROIs.zip`. |
| **Parasites: Threshold factor** | 4.25 | As for nuclei. |
| **Parasites: bright cap** | 5 × median | |
| **Parasites: Area from … to** | 7.25 – 62.5 µm² | Size range of a parasite. |
| **Split touching parasites (watershed)** | ticked | |
| **Clumps count as several parasites**, **above**, **at most** | unticked; 1.7 × median; 8 | Same rule as for nuclei clusters (needs at least 3 parasite objects in the field). A clump larger than the maximum area is left out: raise the maximum area to keep it. |
| **Marker threshold factor** | 2.8 | Second marker. |
| **Marker bright cap** | 5 × median | |
| **Minimum overlap** | 92 % | Overlap scoring: share of the parasite's area that must be marker-positive. |
| **Target per condition** + **in** | 0 (off) | *Stop early*: each condition of each experiment is counted image by image until this number of host cells (or parasites) is reached; the image that reaches it is counted in full, the remaining images are left out of the counts and listed in the Log. |
| **Minimum per image: host cells / parasites** | 0 (off) | An image with fewer objects is either *Left out of the counts*, or *Counted only if the target needs it* (richest first). It is still processed and keeps its QC stack. Use a minimum of parasites with care: a treatment that lowers infection gives fields with few parasites, and leaving them out biases the result. |

Settings not shown in this dialog are set in *Calibrate* (and listed under each block): threshold
method, background radius, blur, circularity, watershed tolerance.

## Detection, step by step

For each channel counted (nuclei, parasites, marker):

1. **Pre-processing** (on a copy): multiplication (nuclei 2.5, parasites 2.0, marker 2.0; a channel
   where more than 1 % of the pixels would clip is processed in 32-bit), rolling-ball **background
   subtraction** (radius 80 px nuclei, 30 px parasites and marker), **Gaussian blur** (σ 1.5 px nuclei,
   0.8 px parasites and marker). Multiplication and contrast enhancement do not change detection: the
   automatic threshold is computed on the rescaled histogram.
2. **Automatic threshold** (method *Triangle* by default), with two protections: the **bright cap** (in
   each image, an object holding a pixel brighter than cap × the median brightness of what the
   threshold detects is left out of the threshold calculation — it is still detected — so one debris
   or dead cell cannot raise the threshold above every ordinary object; hot pixels are not concerned),
   and **saturated peaks** (a saturated area larger than the background would be taken for the
   background: such a peak at the top of the histogram is left out of the calculation). The lower bound
   is then multiplied by the **threshold factor**.
3. **Mask**, then optional **watershed** to split touching objects. The watershed cuts an object
   between two peaks of its distance map when both rise more than the **tolerance** above the pass
   between them (0.5 px = ImageJ's default; higher = only deeper notches are cut). It never merges or
   loses objects.
4. **Analyze Particles** with the area range and the **circularity** range (nuclei 0.20–1.00, parasites
   0.10–1.00).
5. **Clusters and fragments** against the median area of the field (see the table above).
6. **Double labelling** and **poles** on the parasites.

Each channel counted is also checked for saturation: when more than a trace of its pixels sits at the
top of the camera range, the Log gives the share saturated.

## Calibrate

Choose *Calibrate nuclei / parasites / double labelling first* in *Next*, and where the images come from.
A QC stack shows, for each image, the raw channel in its colour, then the same view with what is counted
drawn over it, next to a panel with a slider for every setting of that class:

- nuclei: threshold factor, bright cap, minimum area, background radius, blur sigma, minimum
  circularity, watershed tolerance, cluster above / at most, no cell under, brightness, threshold
  method, border rule, watershed, clusters;
- parasites: threshold factor, bright cap, minimum and maximum area, background radius, blur sigma,
  minimum circularity, watershed tolerance, clump above / at most, brightness, threshold method,
  watershed, clumps;
- double labelling: marker threshold factor, bright cap, minimum overlap, background radius, blur
  sigma, marker and parasite brightness, the object, enclosing-object and pole settings, threshold
  method, objects counted, watershed of the marker objects.

At OK: *Redraw the preview with these values*, *Keep these values and go back*, *Discard the changes and
go back*, or *Redraw, then let me zoom and scroll the preview*. The batch and the preview run the very
same functions; only what a change affects is recomputed. **Brightness** sliders change the display of
the preview and of the QC stacks only, never the counts.

Outline colours (Calibrate and QC stacks): nuclei cyan, cluster magenta, fragment green, cut by the
border grey; parasites magenta, clump white; double-labelled white, other parasites cyan,
marker-positive area orange; rejected by size orange, by shape yellow. Poles: magenta basal, cyan
apical, white indeterminable, orange not at a pole, yellow dot the basal end.

## Poles

Which end of each parasite is basal is found from the end nearest its nucleus, and / or from a pole
marker in a channel of its own (basal, or apical such as an extruded conoid). Each double-labelled
parasite is then classed by where its marker lies (its counted objects — the pole holding more of
them — or the centre of its marker-positive part): **basal pole**, **apical pole**, **pole
indeterminable** (at an end of a parasite that could not be oriented), **not at a pole**. Counts and %
of the double-labelled parasites are given per image (Log, CSV) and per experiment × condition
(workbook, sheet "Poles").

## Outputs

In `Results/` inside the input folder (or the folder chosen):

| File / folder | Content |
|---|---|
| `Raw data.csv` | One row per image: experiment, condition, image, host cells, parasites, double-labelled, single-labelled, measure, and whether the image was counted. |
| `Raw data - <condition>.csv` | The same, per condition. |
| `Parasites.csv` | One row per parasite (double labelling by objects / poles): its objects, place of the marker, pole class. |
| Results workbook (`.xls`) | Per-experiment summary and per-image detail; "% of control" column when a control condition exists; sheet "Poles" when used. A class not counted keeps its column at 0, headed "(not counted)". |
| `Histogram <experiment>.png`, `Histogram pooled <name>.png` | Measure per condition, per experiment and pooled across experiments. |
| `Stacks_<name>/<experiment>/<condition>/<image>_QC_Stack.tif` | Annotated QC stack of each image, calibrated; its *Info* (*Image ▸ Show Info*) holds the source file, pixel size, channels, counts, thresholds and every setting. |
| `Data/` | Per-image data (for example the nuclei left out at the border, as ROI sets). |
| `Hyperstacks/`, `Projections/`, `Merges/`, `Projection merges/` (each with `<experiment>/<condition>/`) | Images made: `<image>.tif` (hyperstack), `<image>_MAX.tif` (maximum projection), `<image>_merge.tif` (merge of all planes), `<image>_MAX_merge.tif` (merge of the projection). |
| `Settings.txt` | Every setting of the run; can be loaded again in dialog 1. |
| `Log.txt` | The Log of the run: for each file, the channels, planes and time points it opened with, its pixel size, counts, thresholds, what the bright cap did, saturation, images left out. |

## Validated defaults (summary)

| Class | Threshold | Factor | Bright cap | Area | Background / blur | Circularity | Watershed |
|---|---|---|---|---|---|---|---|
| Nuclei | Triangle | 1.0 | 5 | ≥ 105 µm² | 80 px / 1.5 px | 0.20–1.00 | yes, tolerance 0.5 |
| Parasites | Triangle | 4.25 | 5 | 7.25–62.5 µm² | 30 px / 0.8 px | 0.10–1.00 | yes, tolerance 0.5 |
| Marker | Triangle | 2.8 | 5 | overlap ≥ 92 % | 30 px / 0.8 px | — | — |

Changing them changes what is counted: note the settings you use (they are in `Settings.txt` and in the
Info of every QC stack).
