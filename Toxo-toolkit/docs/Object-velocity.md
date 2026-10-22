# Object Velocity

*Plugins ▸ Toxo Toolkit ▸ Object Velocity* — macro `Object_Velocity.ijm` (ImageJ 1.53b or later).

Measures the speed of objects that you point by hand on a movie (beads, gliding parasites, particles...).
For each object you click its position once on each frame where you follow it; the macro keeps the
track in the ROI Manager, draws it on the image and measures it.

## How to use

1. Open the movie, or a substack of frames picked from it (*Image ▸ Stacks ▸ Tools ▸ Make Substack*).
2. Run the macro and check, in the dialog, the original frame numbers, the frame interval and the
   pixel size (see below). OK.
3. For each object: with the Multi-point tool (selected for you), click the object once on each frame
   where you follow it (next frame: the **>** key or the slider; **Alt+click** removes a wrong point),
   then click OK in the small window. The track is checked, named `object 1`, `object 2`... and added
   to the ROI Manager.
4. When all objects are done, click OK **without any point**: the tracks are measured.

Tracks already in the ROI Manager are measured too, and new objects are numbered after them: to go on
later, open the saved `.zip` (it fills the ROI Manager) and run the macro again. Tracks named `bead N`
(saved by the former version of this tool, *Bead Speeds*) are read the same way.

## What the code does

- **Time of each frame.** time = (original frame − 1) × frame interval. The original frame numbers are
  read from the slice labels that *Make Substack* keeps, as in Smart Time Stamper (Bio-Formats labels
  `t:45/200`, AVI frame times, `frame 45`, numbers in file names...), or from a *Make Substack* title, so
  that a stack of non-consecutive frames gets the right times. Without them, the frames are taken as
  consecutive. Labels that only number the frames of the stack itself (`t:1/16` to `t:16/16`, or
  1, 2, 3...) are not used.
- **One track** = a multi-point selection with one point per frame. A track is refused (with the reason)
  if it has fewer than 2 points, two points on the same frame, points not attached to frames, or
  original frames not in increasing order. The points are sorted by time.
- **Measures of a track** (positions converted to µm with the pixel size; the vertical pixel size is
  used for y if it differs):
  - **Path** = sum of the distances between consecutive points.
  - **Displacement** = distance between the first and the last point.
  - **Duration** = time between the first and the last point.
  - **Speed** = path / duration. This is the mean of the step speeds, each weighted by its duration, so
    it stays correct when frames are unevenly spaced; with evenly spaced frames it equals the plain
    mean of the step speeds.
  - **Straight-line speed** = displacement / duration (less sensitive to pointing errors, lower when the
    path curves).
  - **Mean step speed** and **SD of the step speeds** (each step: distance / time between its two points).
  - **Straightness** = displacement / path (1 = straight path).
- **Pointing correction.** At a zoom of 150 % or less, ImageJ rounds the first point of a multi-point
  selection to the pixel while the next ones are kept at x.5 / y.5; the macro puts the first point back
  on the same grid (otherwise the first step would be off by half a pixel in x and y).

## Dialog

| Field | Macro key | Default | What it does |
|---|---|---|---|
| **Original frames** | `original` | read from the image | The frame number, in the original movie, of each time point of this image, in order. Lists and ranges: `3,15,27,40-43` (40 to 43), `1-199-12` (every 12th frame). The message above says where the numbers come from (green: read from the image; orange: not found, check them). |
| **Frame interval** | `frame` | from *Image ▸ Properties* | Time between two consecutive frames of the **original** movie: a number and a unit (`0.87 s`, `870 ms`, `2 min`, `1 h`; without a unit, the unit shown). Worked out from the frame rate if there is no interval. |
| **Pixel size** | `pixel` | from *Image ▸ Properties* | Pixel width in µm (nm and mm calibrations are converted). |
| **Speed in** | `speed` | µm/s | Unit of the speeds: µm/s or µm/min. |
| **Point new objects now** | `point` | ticked | Point new tracks before measuring. Untick to only measure the tracks already in the ROI Manager. |
| **Draw the tracks on the image** | `draw` | ticked | Draws each track on the overlay: on each frame of a track, the whole path, a circle on the object of that frame, and its number, in a colour per object. Earlier drawings of the macro are replaced. |
| **Save the tracks (.zip) and the tables (.csv)** | `save` | ticked | Saves next to the image (or in a folder asked for, if the image has no folder): `<image>_object_tracks.zip`, `<image>_object_speeds.csv`, `<image>_object_steps.csv`. Existing files are never overwritten (`_2`, `_3`... added). |

The choices are remembered between runs.

## Outputs

**Table "Object speeds"** (one row per object). Rows of other images are kept, so several movies of a
condition can be gathered in one table; running the macro again on an image replaces its rows (the
table starts again if the speed unit changes).

| Column | Meaning |
|---|---|
| Image | image title |
| Object | object number |
| Points | number of points of the track |
| First frame, Last frame | original frame numbers of the first and last point |
| Duration (s) | time between the first and last point |
| Path (um) | distance along the track |
| Displacement (um) | straight-line distance from the first to the last point |
| Speed (um/s or um/min) | path / duration |
| Straight-line speed | displacement / duration |
| Mean step speed, SD step speeds | mean and SD of the speeds of the steps |
| Straightness | displacement / path |

**Table "Object steps"** (one row per step): Image, Object, Step, From frame, To frame, dt (s),
Distance (um), Speed, x1, y1, x2, y2 (px).

**Log**: number of objects, where the frame numbers came from, interval and pixel size, tracks not
measured and why, and the mean, SD, SEM, median, min and max of the speeds (and of all the images of
the table, when it holds several).

## Macro example

```javascript
// measure the tracks already in the ROI Manager, frames 1 to 60, 0.87 s per frame, 0.11 um per pixel
run("Object Velocity", "original=1-60 frame=[0.87 s] pixel=0.11 draw");
```
