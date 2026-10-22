# Object Velocity

*Plugins ▸ Toxo Toolkit ▸ Object Velocity* — macro `Object_Velocity.ijm`.

Speed of objects you point by hand on a movie (beads, parasites...).

## Use

1. Open the movie (or a substack of it), run the macro, check frames, interval and pixel size.
2. For each object: click it once on each frame where you follow it (Multi-point tool; next frame:
   `>` key; Alt+click removes a point), then OK. When all are done: OK without any point.

Tracks are kept in the ROI Manager (`object 1`, `object 2`...). Tracks already there are measured too:
to continue later, open the saved `.zip` and run again.

## Measures

- **Speed** = distance along the track / time between first and last point (correct even when frames
  are unevenly spaced).
- **Straight-line speed** = first-to-last distance / time.
- **Step speeds** (mean, SD) and **straightness** = straight-line distance / path (1 = straight).

Times use the **original frame numbers** read from the slice labels (as in Smart Time Stamper), so a
substack of non-consecutive frames gets the right times.

## Parameters

| Parameter | Default | Role |
|---|---|---|
| Original frames (`original`) | read from the image | Frame number of each time point in the original movie (`3,15,27,40-43`). |
| Frame interval (`frame`) | Image ▸ Properties | Interval of the original movie, with unit (`0.87 s`, `870 ms`). |
| Pixel size (`pixel`) | Image ▸ Properties | µm per pixel. |
| Speed in (`speed`) | µm/s | Or µm/min. |
| Point new objects now (`point`) | yes | Untick to only measure tracks already in the ROI Manager. |
| Draw the tracks (`draw`) | yes | Tracks drawn on the overlay. |
| Save (`save`) | yes | Saves `<image>_object_tracks.zip`, `_object_speeds.csv`, `_object_steps.csv` next to the image (never overwrites). |

## Outputs

- Table **Object speeds**: one row per object (path, displacement, duration, speeds, straightness).
  Rows of other images are kept, so several movies can be pooled.
- Table **Object steps**: one row per step.
- Mean, SD, SEM, median of the speeds in the Log.
