# Smart Time Stamper

*Plugins ▸ Toxo Toolkit ▸ Smart Time Stamper* — macro `Smart_Time_Stamper.ijm` (ImageJ 1.53b or later).

Writes the time on every frame of a movie from the frame's **original number** in the movie, not from
its rank in the stack. On a stack made of frames that are not consecutive (for example *Make Substack*
with `3,15,27,40,...`), each frame gets its real time:

time = (original frame − frame of time zero) × frame interval.

ImageJ's own *Time Stamper* counts the frames of the new stack (0, 1 × interval, 2 × interval...), which
is wrong as soon as frames were left out.

## Where the original frame numbers come from

*Make Substack* keeps the slice labels of the movie; they are read in this order:

1. Bio-Formats labels (czi, nd2, lif, ome.tif...): `t:45/200 - movie` → frame 45;
2. AVI frames opened by ImageJ, labelled with frame number × interval: `39.15 s` with 0.87 s → frame 45;
3. a frame number in the label: `frame 45`, `T045`, `time000045`, `_t45` (numbers starting at 0 are
   shifted by one);
4. a label that is only a number (`45`), then the last number of the label when it changes from frame
   to frame (image sequences: `movie-0045`);
5. the title written by *Make Substack*, `Substack (3,15,27)`, when it is not cut short.

Labels that only count the slices of this stack are not used: `t:1/16` to `t:16/16` (a saved substack
opened again with Bio-Formats, which rewrites the labels; *File ▸ Open* keeps them), or 1, 2, 3... in a
row.

**When nothing is found**, a first window asks for the original frame number of each image, one box per
image (up to 60 images). A *Make Substack* title cut short (`Substack (3,15,27,40, ... 199)`) fills in
the first boxes and the last one. A tick box *Consecutive frames from box 1* fills them all. With more
than 60 images, the frames are taken as consecutive and the main dialog says so in orange.

## Main dialog

| Field | Macro key | Default | What it does |
|---|---|---|---|
| **Original frames** | `original` | read from the image | Original frame number of each time point, in order. Lists and ranges: `3,15,27,40-43`, `1-199-12` (every 12th frame). |
| **Frame interval** | `frame` | from *Image ▸ Properties* | Interval of the original movie: a number and a unit (`0.87 s`, `870 ms`, `min`, `h`). Worked out from the frame rate if there is no interval. |
| **Time zero at original frame** | `time` | 1 | The original frame shown as time 0 (for example the frame where a drug was added); earlier frames get negative times. |
| **Display as** | `display` | seconds | `seconds (38.3 s)`, `minutes (0.64 min)`, `hours (1.25 h)`, `milliseconds (870 ms)`, `mm:ss (00:38)`, `hh:mm:ss (00:00:38)`. |
| **Decimals** | `decimals` | auto | auto = just enough to tell two consecutive frames of the movie apart; or 0 to 3. |
| **Text before the time** | `text` | empty | For example `t =`. |
| **Add the original frame number** | `add` | unticked | Appends `(frame 45)` to the time. |
| **Position** | `position` | Top left | Top left / centre / right, Bottom left / centre / right, or *At the selection* (top-left corner of the selection drawn before starting; offered only when there is one). |
| **Font size** | `font` | 5 % of the image size | In pixels. The size is remembered as a fraction of the image, so it suits images of any size. |
| **Bold** | `bold` | ticked | Bold text. |
| **Colour** | `colour` | White | White, Yellow, Cyan, Magenta, Green, Red, Orange, Grey, Black. |
| **Background** | `background` | None | None, Dark see-through box, Black box, White box (behind the text). |
| **Write the time** | `write` | Overlay | *Overlay (pixels unchanged)*: the time is an overlay; *Image ▸ Overlay ▸ Remove Overlay* takes it away, *File ▸ Save As ▸ AVI* writes it into the movie. *New RGB copy (time burned in)*: a new RGB image `<title>_time` with the time burned in, as the image is displayed (brightness, colours; channels merged), for montages and figures. |
| **List the times in a table** | `list` | ticked | Table *Time stamps*: time point, original frame, time (s), text written, slice label. |

Running the macro again replaces the time stamps it wrote before; other overlay items are kept. The
settings are remembered between runs.

## Macro example

```javascript
run("Smart Time Stamper", "original=1-199-12 frame=[0.87 s] time=1 display=[mm:ss (00:38)] decimals=auto position=[Top left] font=24 bold colour=White background=None write=[Overlay (pixels unchanged)]");
```
