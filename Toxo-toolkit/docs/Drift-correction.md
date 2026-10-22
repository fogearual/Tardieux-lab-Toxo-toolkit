# Drift Correction

*Plugins ▸ Toxo Toolkit ▸ Drift Correction* — Java plugin `Drift_Correction.jar`
(source: [`src/drift-correction/Drift_Correction.java`](../src/drift-correction/Drift_Correction.java)).

Removes the drift of a movie by following an object that does **not** move relative to the coverslip
(a stuck bead, a piece of debris, a landmark), then crops every frame so that the object stays in place.
The corrected movie keeps only the area present in every frame.

## Quick start

1. Open the movie (any number of channels, Z planes and frames; a plain stack of slices is taken as
   time points).
2. Go to a frame where the fixed object is sharp: it becomes the **reference frame**.
3. Outline the object tightly (oval, rectangle, polygon, freehand, wand) or click on it with the point
   tool, then run the plugin. The options dialog is non-blocking: you can still change the selection
   or the frame while it is open.
4. Set the options, OK. With *Calibrate first* ticked, a calibration montage opens first (see below).
5. Depending on the mode, the plugin tracks alone, asks you about doubtful frames, or asks for a click
   on every frame. With *Review* ticked, check the result, correct it if needed, then *Apply the correction*.

## What the code does, step by step

1. **Template.** The pixels inside the selection, in the reference frame, form the template. The
   outline is a **mask**: only the object's own pixels are compared, so what moves around it does not
   disturb the detection. With a point selection, a square of *Box size* pixels centred on the point is
   used. The frames are pre-processed (rolling-ball background subtraction, then Gaussian smoothing)
   before any comparison, the template included.
2. **Detection in each frame**, in a window of *Radius* pixels around the predicted position:
   - *Similarity (correlation)*: masked normalised cross-correlation (NCC) between the template and
     each position of the window. NCC ignores brightness and contrast changes, so photobleaching does
     not matter. Every frame is compared with the **reference** template (never with the previous
     frame), so errors do not accumulate. The best peaks are refined with a *robust* NCC: pixels that
     do not fit the template (another object passing over it) are left out, and the fraction of pixels
     kept is the object's **visible fraction**. The position is refined to sub-pixel accuracy by fitting
     a parabola to the correlation peak.
   - *Thresholding (segmented object)*: as in Count and Colocalise. The threshold of each frame is
     computed on the whole pre-processed image (automatic method × factor), the window is segmented
     into objects (8-connected), objects whose area is outside [minimum, maximum] × the reference area
     are dropped, and the position is the intensity-weighted centre of the object. The score is the
     **size similarity** with the reference object: min(area, reference area) / max(area, reference area).
3. **Continuity first.** The fixed object only moves with the drift, which is slow and smooth. The next
   position is predicted from the median speed between the last reliable positions (one or two wrong
   positions do not change it). A candidate close to the prediction is preferred; a candidate far
   away (another bead, a parasite) is only accepted if its similarity is complete **and** the whole
   image moved in the same way (a real stage jump), which another fixed bead cannot fake.
   If the object is not found within *Radius*, the window is widened automatically (up to 4 × *Radius*).
4. **Decision with two thresholds** (on the score: robust NCC, or size similarity):
   - score ≥ *Doubtful* → **reliable** detection;
   - *Lost* ≤ score < *Doubtful* → **doubtful** detection (asked in semi-automatic mode);
   - score < *Lost*, or no candidate → **object not found** (lost).
   A candidate with a good score but less than 80 % of its pixels visible is treated as hidden (lost):
   a partly hidden object gives a biased position.
5. **After tracking** (automatic and semi-automatic modes):
   - a few "reliable" frames isolated between doubtful or lost frames (≤ 5) are flagged doubtful;
   - a short run of doubtful frames (≤ 20 frames, with reliable frames on both sides) is interpolated:
     a wrong measurement is worse than an interpolation; confirmed stage jumps are kept;
   - lost frames are interpolated linearly, **or** measured on the whole field (the shift of the whole
     image) when that measurement proves more reliable: the whole-field shift must first recover the
     known displacement between the reliable frames around the loss, and its error on the neighbouring
     reliable frames must be smaller than the expected error of the interpolation (from the curvature
     of the drift).
6. **Registration and crop.** Each frame is shifted by its drift and cropped to the area common to all
   frames (all channels and Z planes of a time point get the same shift).

## Options dialog

The *macro key* is what to write in a macro (`run("Drift Correction", "key=value ...")`). Values
between square brackets when they contain spaces. Checkboxes: write the key to tick, omit it to untick.

| Field | Macro key | Default | What it does |
|---|---|---|---|
| **Mode** | `mode` | Semi-automatic | *Automatic*: no intervention. *Semi-automatic (asks when in doubt)*: tracking stops at each doubtful or lost frame and asks you (see below). *Manual (click on every frame)*: you click on the object on every frame, or every *Manual step* frames. Semi-automatic and manual need the movie to be displayed (not in batch mode). |
| **Detection** | `detection` | Similarity (correlation) | How the object is found in each frame: *Similarity (correlation)* or *Thresholding (segmented object)* (see above). Correlation is best for a textured object or one touching others; thresholding for an object whose appearance changes (focus) but that stays well separated. |
| **Object** | `object` | Bright on dark background | Polarity: *Bright on dark background* (fluorescence) or *Dark on bright background* (absorbing object in transmitted light). Used by the background subtraction (light-background option) and by thresholding (the image is inverted for a dark object). |
| **Channel** | `channel` | the displayed channel | Channel used for tracking (multi-channel images only). The correction is applied to all channels. |
| **Plane** | `plane` | Displayed Z plane | For Z stacks: track on the *Displayed Z plane*, or on the *Max projection in Z* of each time point (useful when the object changes plane). |
| **Radius** | `radius` | 20 px | Largest expected move of the object **between two consecutive frames** (not the total drift). The search window is ± *Radius* around the prediction; it is widened automatically up to 4 × *Radius* when nothing is found. Too large: slower, and more chance to meet a look-alike. |
| **Doubtful if score <** | `doubtful` | 0.80 | Below this score a detection is doubtful (between 0 and 1). The calibration suggests a value. |
| **Lost if score <** | `lost` | 0.50 | Below this score the object is considered not found (must be ≤ *Doubtful*). |
| **Smoothing (sigma)** | `smoothing` | 1.0 px | Gaussian blur applied before detection (0 = none). Reduces the effect of camera noise; keep it below the size of the object's details. |
| **Background (rolling ball radius)** | `background` | 0 (none) | Rolling-ball background subtraction before detection, radius in pixels (0 = none). Use a radius larger than the object (about 2–3 × its radius) when the background is uneven. |
| **Thresholding method** | `thresholding` | Otsu | Automatic threshold method of ImageJ (Default, Huang, Intermodes, IsoData, Li, MaxEntropy, Mean, MinError, Minimum, Moments, Otsu, Percentile, RenyiEntropy, Shanbhag, Triangle, Yen). *Thresholding detection only.* |
| **Threshold factor** | `threshold` | 1.00 | Multiplies the automatic threshold, measured from the median of the image: threshold = median + factor × (automatic − median). > 1: stricter (smaller objects), < 1: more permissive. *Thresholding only.* |
| **Minimum area** / **Maximum area** | `minimum` / `maximum` | 0.5 / 2.0 × object | Objects whose area is outside [minimum, maximum] × the area of the reference object are ignored. An object merging with a neighbour (area too large) is thus not taken for the fixed object. *Thresholding only.* |
| **Box size (point selection)** | `box` | 31 px | Side of the square template used when the object is selected with a single point. |
| **Click refinement** | `click` | 4 px | Each click (semi-automatic, manual, review) is moved onto the object found within this distance, if the object is recognised there with a score ≥ *Doubtful* and fully visible. Otherwise the click is kept as is (minus your usual click offset, learnt from the refined clicks). 0 = raw clicks. |
| **Manual step** | `manual` | 1 frame | In manual mode, the movie moves on by this many frames after each click; the frames in between are interpolated. |
| **Registration** | `registration` | Whole pixels (raw values) | *Whole pixels*: each frame is cropped at the rounded drift, without interpolation: pixel values stay raw (best for quantification; residual error ≤ 0.5 px). *Sub-pixel bilinear* / *Sub-pixel bicubic*: the exact drift is applied by interpolation (slight smoothing; bicubic keeps details better). |
| **Calibrate first on frames picked at random** | `calibrate` | ticked | Opens the calibration before tracking (interactive use only; never in a macro). |
| **Number of frames** | `number` | 9 | Number of frames of the calibration. |
| **Review the tracking before applying** | `review` | ticked | Shows the tracking for checking and correction before the corrected movie is made. |

The last values used are remembered (keys `drift_correction.*` of the ImageJ preferences); in a macro,
only the values given are used, the others take the defaults above.

## Calibration

Before tracking, the settings are tried on *Number of frames* frames picked at random, **one in each
section of the movie** (so that the start, the middle and the end are all represented). For each frame:

1. the global drift since the reference frame is estimated on the whole image (downsampled
   correlation over ± 3 × *Radius*, refined to sub-pixel accuracy when the peak is sharp);
2. the object is searched around its reference position shifted by that drift; as during tracking,
   only a candidate close to the expected position (within max(4 px, 0.3 × object size)) is taken for
   the object; the best other candidate is kept apart.

**Montage** (one tile per frame, centred on the object found, enlarged): outline in green (reliable),
orange (doubtful) or red (not found; the red circle shows where the object was expected); the second
best candidate in cyan; with thresholding, the pixels above the threshold are tinted red. Each tile
shows the frame number, the score, the status, and "2nd: score at distance".

**Panel**: a summary (how many frames are reliable / doubtful / not found; median and range of the
object's score; best score of another object), **suggested thresholds**, and warnings:

- *another object looks almost as similar*: on some frames, an object other than yours scores within
  0.1 of it. Tracking then relies on continuity; if it jumps, outline a more distinctive object or try
  thresholding.
- *the object is not where the global drift expects it*: on more than half of the frames, the object
  is not found where expected while a similar object exists elsewhere. Either many things move in the
  image (the global drift is then unreliable and the test pessimistic), or the outlined object is not
  a fixed one.

Suggested thresholds: *Doubtful* = median score − max(0.05, 3 × robust SD) of the object's scores
(bounded to 0.30–0.97). *Lost* (correlation) = best score of another object + 0.05, so that a
look-alike is never taken as "found"; (thresholding) = *Doubtful* − 0.30; always at least 0.05 below
*Doubtful*.

Fields of the panel: the same detection fields as above, plus *New random frames* (new draw) and
*Use the suggested thresholds*. **Redraw** applies the settings to the montage; **Accept and start
tracking** keeps them (they are remembered) and starts; **Cancel** stops the plugin.

## Semi-automatic mode

At each doubtful or lost frame the movie stops on that frame, with the outline found (orange) or the
expected position (red), the reason, and these choices:

- **click on the object**: gives its position (refined, see *Click refinement*); you can also click on
  another frame to correct it;
- **Detection is correct** / **Keep this position**: the position shown is validated;
- **Unreadable frame (interpolate)**: no position, the frame is interpolated;
- **Wait for the object to return**: no more questions until the object is found again (for an object
  passing over the fixed one: one answer instead of one per frame);
- **Stop asking**: the rest of the movie is tracked automatically;
- **Cancel**.

## Manual mode

Click on the object on the frame shown; the movie moves on by *Manual step* frames. A grey outline shows
where the object is expected (interpolated). **« Back** goes back one step, **Finish** stops pointing
(the rest is interpolated). Zoom with the + and − keys (clicks are used for pointing).

## Review

The movie is shown with the tracked outline on every frame and its trajectory; a plot shows dX and dY
(px) against the frame, with doubtful frames (circles) and clicks (squares) marked.

Outline colours: green reliable, orange doubtful, red / pink lost (interpolated / estimated on the whole
field), cyan click, grey interpolated, magenta reference.

- click on the object to correct the displayed frame (the plot and the interpolation are updated);
- **« Previous doubtful** / **Next doubtful »**: jump between the frames to check;
- **Re-track from here**: runs the automatic tracking again from the displayed frame, up to the next
  frame you corrected by hand;
- **Apply the correction** / **Cancel**.

## Outputs

- **Corrected movie** `<title>_driftcorr`: same channels, slices, frames, bit depth, calibration, LUTs
  and display range, cropped to the area common to all frames.
- **Table** `Drift - <title>`, one row per frame:

  | Column | Meaning |
  |---|---|
  | Frame | time point |
  | X object (px), Y object (px) | position of the object (centre) |
  | dX (px), dY (px) | drift relative to the reference frame |
  | dX (unit), dY (unit) | the same in calibrated units (if the image is calibrated) |
  | Score | robust NCC, or size similarity (thresholding) |
  | Visible (%) | fraction of the object's pixels that match it (correlation) |
  | Source | reference, auto, auto doubtful, auto validated, refined click, raw click, interpolated, lost (interpolated), lost (whole field) |
  | Note | why a frame is doubtful or lost |

- **Plot** `Drift - <title>`: dX and dY against the frame.
- **Log**: mode, reference frame and position, detection settings, drift range, area kept,
  registration, number of positions of each kind, frames lost and frames to check.

## Tips

- Choose an object present in every frame, never crossed by anything, away from the borders, and
  outline only it.
- If tracking jumps to another object: lower *Radius*, raise *Lost*, outline a more distinctive object,
  or switch to thresholding with a tight area range.
- If many frames are doubtful although the object is clearly there: lower *Doubtful* (the calibration
  shows the usual score), or add smoothing / background subtraction.
- For quantification, keep *Whole pixels (raw values)*.
