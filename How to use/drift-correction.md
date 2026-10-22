# Drift Correction

*Plugins ▸ Toxo Toolkit ▸ Drift Correction* — Java plugin (`Drift_Correction.jar`).

Removes the drift of a movie by following an object that does not move relative to the coverslip
(stuck bead, debris, landmark), then crops the movie to the area present in every frame.

## Use

1. Open the movie and go to a frame where the fixed object is sharp (reference frame).
2. Outline the object tightly (or click on it with the point tool) and run the plugin.
3. Optional calibration on random frames, then tracking, then review and *Apply the correction*.

## How it works

- **Detection**: in each frame the object is searched around its predicted position, either by
  *correlation* with its appearance in the reference frame (insensitive to photobleaching, sub-pixel),
  or by *thresholding* (as in Count and Colocalise: background, blur, auto threshold × factor, area
  filter; position = centre of the object).
- **Continuity first**: the drift is slow, so the position is predicted from the last reliable ones.
  A look-alike elsewhere is only accepted if the whole image confirms a stage jump.
- **Two thresholds** on the score: ≥ *Doubtful* = reliable; between *Lost* and *Doubtful* = doubtful;
  < *Lost* = not found. Doubtful and lost frames are interpolated (or measured on the whole image when
  that is more reliable).
- **Correction**: each frame is shifted and cropped to the area common to all frames.

## Parameters

Macro key in brackets: `run("Drift Correction", "key=value ...")`.

| Parameter | Default | Role |
|---|---|---|
| Mode (`mode`) | Semi-automatic | Automatic / Semi-automatic (asks when in doubt) / Manual (one click per frame). |
| Detection (`detection`) | Similarity (correlation) | Or Thresholding (segmented object). |
| Object (`object`) | Bright on dark background | Or dark on bright (transmitted light). |
| Channel, Plane (`channel`, `plane`) | displayed | Channel and Z plane (or max projection) used for tracking. All channels are corrected. |
| Radius (`radius`) | 20 px | Largest move of the object between two frames (widened automatically up to 4×). |
| Doubtful if score < (`doubtful`) | 0.80 | Below: doubtful detection. |
| Lost if score < (`lost`) | 0.50 | Below: object not found. |
| Smoothing (`smoothing`) | 1.0 px | Gaussian blur before detection (0 = none). |
| Background (`background`) | 0 | Rolling-ball radius, px (0 = none). |
| Thresholding method, Threshold factor (`thresholding`, `threshold`) | Otsu, 1.00 | Thresholding only. Factor > 1 = stricter. |
| Minimum / Maximum area (`minimum`, `maximum`) | 0.5 / 2.0 × object | Thresholding only: objects outside this range are ignored. |
| Box size (`box`) | 31 px | Template size for a point selection. |
| Click refinement (`click`) | 4 px | Clicks snap onto the object within this distance (0 = raw clicks). |
| Manual step (`manual`) | 1 | Manual mode: frames skipped between clicks (interpolated). |
| Registration (`registration`) | Whole pixels | Whole pixels = raw values kept (best for quantification); or sub-pixel bilinear / bicubic. |
| Calibrate first (`calibrate`), Number of frames (`number`) | yes, 9 | Calibration before tracking (not in macros). |
| Review (`review`) | yes | Check and correct the tracking before applying. |

## Calibration

The settings are tried on N frames picked at random (one per section of the movie). A montage shows the
object found in each (green reliable, orange doubtful, red not found, cyan = second best candidate, red
tint = thresholded pixels). The panel gives the scores, **suggested thresholds**, and warns when another
object looks almost as similar. *Redraw* applies new settings, *Accept* starts the tracking.

## Semi-automatic mode and review

At a doubtful frame: click on the object, or *Detection is correct*, *Unreadable frame (interpolate)*,
*Wait for the object to return* (no more questions until it is found again), *Stop asking*.
In the review: click to correct a frame, jump between doubtful frames, *Re-track from here*.

## Outputs

- Corrected movie `<title>_driftcorr` (same channels, bit depth, calibration).
- Table `Drift - <title>`: per frame, position, dX/dY (px and µm), score, visible %, source
  (auto, click, interpolated, lost...), note.
- Drift plot and summary in the Log.
