# Smart Time Stamper

*Plugins ▸ Toxo Toolkit ▸ Smart Time Stamper* — macro `Smart_Time_Stamper.ijm`.

Writes the real time on each frame from its **original frame number** in the movie:
time = (original frame − frame of time zero) × interval. ImageJ's own Time Stamper counts the frames of
the stack, which is wrong on a substack of non-consecutive frames.

The original numbers are read from the slice labels kept by *Make Substack* (Bio-Formats `t:45/200`,
AVI times, `frame 45`, numbers in file names) or from the *Substack (...)* title. If none is found, a
window asks for the number of each image.

## Parameters

| Parameter | Default | Role |
|---|---|---|
| Original frames (`original`) | read from the image | Lists and ranges: `3,15,27,40-43`, `1-199-12`. |
| Frame interval (`frame`) | Image ▸ Properties | With unit (`0.87 s`, `870 ms`). |
| Time zero at original frame (`time`) | 1 | Earlier frames get negative times. |
| Display as (`display`) | seconds | s, min, h, ms, mm:ss, hh:mm:ss. |
| Decimals (`decimals`) | auto | auto = enough to tell two frames apart. |
| Text before the time (`text`) | — | e.g. `t =`. |
| Add the original frame number (`add`) | no | Appends `(frame 45)`. |
| Position (`position`) | Top left | Corners, centres, or at the selection. |
| Font size, Bold, Colour, Background | 5 % of image, yes, white, none | Text appearance. |
| Write the time (`write`) | Overlay | Overlay (pixels unchanged) or new RGB copy with the time burned in. |
| List the times in a table (`list`) | yes | Table *Time stamps*. |

Running it again replaces its previous time stamps.
