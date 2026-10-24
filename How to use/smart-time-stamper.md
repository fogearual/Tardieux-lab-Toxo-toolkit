# Smart Time Stamper

*Plugins ▸ Toxo Toolkit ▸ Smart Time Stamper* — Java plugin (`Smart_Time_Stamper.jar`).

Writes the real time on each frame from its **original frame number** in the movie:
time = (original frame − frame of time zero) × interval. ImageJ's own Time Stamper counts the frames of
the stack, which is wrong on a substack of non-consecutive frames.

The original numbers are read from the slice labels kept by *Make Substack* (Bio-Formats `t:45/200`,
AVI times, `frame 45`, numbers in file names) or from the *Substack (...)* title. If none is found, a
window asks for the number of each image.

## Placing the time live

With **Adjust live** ticked, a panel stays open after the dialog and the movie shows the result at once:

- **drag** the time with the mouse on the image (on any frame);
- **arrow keys**: 1 px (Shift: 10 px), with the image or the panel selected;
- change **font size, bold, colour, background**, or snap back to a corner;
- scroll the movie to check every frame, then **OK**.
- with the SVG montage chosen, a **Montage preview** window shows the whole montage, updated live, and the
  panel also sets the **columns, border width and border colour**.

The dragged position is remembered for the next run (as *Custom*).

## Scale bar

**Scale bar** adds a bar of a given length, in the unit of the image (*Image ▸ Properties*), on the
**first image**, the **last image** or **every image**, with or without its length written above it
(`10 µm`). It has its own thickness, text size, colour and box, and is placed like the time: a corner, or
*Custom* (left, top), or **dragged live** on the image (on a frame that shows it). The arrow keys move what
was dragged last, or what *Arrows move* says. The panel also changes its length, thickness, text, colour
and box at once. It is drawn in the overlay, burned into the RGB copy, and written as vectors in the SVG:
a `scale bar` layer with a rectangle and a real text, both editable in Illustrator. On an uncalibrated
image the length is in pixels.

## Vector file for Illustrator

**Vector file (SVG)** writes, next to the image, either one SVG per time point (folder
`<image>_time_svg/`) or one montage of all time points. Illustrator and Inkscape open them with two
layers: `images` (the frame as displayed, embedded) and `time stamps` (real text, editable: font, size,
colour, content), plus `scale bar` when the bar is shown. Untick *Include the image* to get the time stamps only.

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
| Position (`position`) | Top left | Corners, centres, at the selection, or Custom. |
| X, Y (`x`, `y`) | — | Custom position: top left corner of the text, in pixels. |
| Font size, Bold, Colour, Background | 5 % of image, yes, white, none | Text appearance. |
| Scale bar (`scale`) | None | None, First image, Last image or Every image. |
| Place of the bar (`place`) | Bottom right | Corners, or Custom (left, top below). |
| Length of the bar (`length`) | about 20 % of the width | In the unit of the image. |
| Thickness of the bar (`thickness`) | 1.2 % of the image | Pixels. |
| Show the length above the bar (`show`) | yes | Untick for a bar without text. |
| Size of the bar text (`size`) | 4 % of the image | Pixels. |
| Bar colour (`bar`), Box behind the bar (`box`) | White, none | Like the time. |
| Left, Top of the bar (`left`, `top`) | — | Custom place: top left corner of the bar with its text, in pixels. |
| Adjust live | yes | Live placement panel (not in macros). |
| Write the time (`write`) | Overlay | Overlay (pixels unchanged), new RGB copy with the time burned in, or nothing (SVG only). |
| Vector file (`vector`) | None | One SVG per time point, or one SVG montage. |
| Include the image in the SVG (`include`) | yes | Untick for the time stamps alone. |
| Columns (`columns`) | 0 (auto) | Montage only. |
| Width of the borders (`width`) | 2 % of the image | Montage only: px between and around the frames (0 = none). |
| Border colour (`border`) | White | Montage only: white, black, grey or transparent. |
| List the times in a table (`list`) | yes | Table *Time stamps*. |

Running it again replaces its previous time stamps.
