/*
 * Smart Time Stamper - ImageJ / Fiji plugin (Toxo Toolkit), version 2 (October 2026), ImageJ 1.53b or later.
 *
 * WHAT IT DOES
 * Writes the time on every frame of a movie from the frame's ORIGINAL number in the movie, not from its rank in
 * the stack:  time = (original frame - frame of time zero) x frame interval.
 * On a stack made of frames that are not consecutive (Make Substack with 3,15,27,40,...), each frame gets its real
 * time. ImageJ's own Time Stamper counts the frames of the new stack, which is wrong as soon as frames were left out.
 *
 * WHERE THE ORIGINAL FRAME NUMBERS COME FROM (slice labels kept by Make Substack, in this order)
 *   1. Bio-Formats labels "t:45/200 - movie"; 2. AVI frames labelled with their time ("39.15 s" with 0.87 s);
 *   3. a frame number in the label ("frame 45", "T045", "_t45"); 4. a label that is only a number, then the last
 *   number of the label (image sequences); 5. the title written by Make Substack, "Substack (3,15,27)".
 *   Labels that only count the slices of this stack ("t:1/16" to "t:16/16", or 1, 2, 3...) are not used.
 *   When nothing is found, a window asks for the original frame number of each image.
 *
 * POSITION
 *   Corners / centres, the selection drawn before starting, or a custom position (X, Y). With "Adjust live", a
 *   panel stays open: drag the time with the mouse on the image (on any frame), nudge it with the arrow keys, change
 *   size, colour and background: the movie updates at once. OK keeps it.
 *
 * OUTPUT
 *   - overlay on the image (pixels unchanged), or a new RGB copy with the time burned in;
 *   - optional vector file (SVG) for Illustrator / Inkscape: the frame as an embedded image (layer "images") and
 *     the time as real, editable text (layer "time stamps"); one file per time point or one montage;
 *   - table "Time stamps".
 * Running it again replaces the time stamps it wrote before; other overlay items are kept.
 */

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Macro;
import ij.Prefs;
import ij.WindowManager;
import ij.CompositeImage;
import ij.gui.GenericDialog;
import ij.gui.ImageCanvas;
import ij.gui.ImageWindow;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.gui.TextRoi;
import ij.io.DirectoryChooser;
import ij.io.FileInfo;
import ij.measure.Calibration;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.process.ColorProcessor;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Smart_Time_Stamper implements PlugIn {

    static final String TITLE = "Smart Time Stamper";
    static final String NAME = "Time stamp";            // name of the overlay items written by this plugin
    static final String PREFS = "sts.";                  // same keys as the macro version 1

    static final String[] UNITS = {"ms", "s", "min", "h"};
    static final double[] UNIT_SEC = {0.001, 1, 60, 3600};
    static final String[] FORMATS = {"seconds (38.3 s)", "minutes (0.64 min)", "hours (1.25 h)",
            "milliseconds (870 ms)", "mm:ss (00:38)", "hh:mm:ss (00:00:38)"};
    static final String[] DECIMALS = {"auto", "0", "1", "2", "3"};
    static final String[] ANCHORS = {"Top left", "Top centre", "Top right", "Bottom left", "Bottom centre", "Bottom right"};
    static final String AT_SEL = "At the selection", CUSTOM = "Custom (X, Y below)";
    static final int P_SEL = 6, P_CUSTOM = 7;
    static final String[] COLOURS = {"White", "Yellow", "Cyan", "Magenta", "Green", "Red", "Orange", "Grey", "Black"};
    static final String[] COLOUR_HEX = {"#ffffff", "#ffff00", "#00ffff", "#ff00ff", "#00ff00", "#ff0000", "#ff9900",
            "#a0a0a0", "#000000"};
    static final String[] BACKGROUNDS = {"None", "Dark see-through box", "Black box", "White box"};
    static final Color[] BG_COLOUR = {null, new Color(0, 0, 0, 0x99), new Color(0, 0, 0), new Color(255, 255, 255)};
    static final String[] OUTPUTS = {"Overlay (pixels unchanged)", "New RGB copy (time burned in)",
            "Nothing (vector file only)"};
    static final String[] VECTORS = {"None", "One SVG per time point", "One SVG montage of all time points"};
    static final String[] BORDERS = {"White", "Black", "Grey", "Transparent"};
    static final Color[] BORDER_COLOUR = {Color.white, Color.black, new Color(128, 128, 128), null};

    // ------------------------------------------------------------------ image
    ImagePlus imp;
    String title;
    int W, H, nC, nZ, nF, nT, nSl;
    char tDim;
    Roi sel;
    Rectangle selB;
    String[] labels;
    boolean selfLab;
    String parseErr = "";
    boolean macro;

    // ------------------------------------------------------------------ settings
    int[] fr;
    double ivS, zero = 1;
    int fmtI, dec;
    String decC, prefix;
    boolean addFr, bold = true, tableOn = true, live = true, include = true;
    int posMode;
    double cx, cy;                 // custom position: top left corner of the text box (px)
    int fsize, colI, bgI, outI, vecI, columns, border, borderI;
    String[] texts;
    double[] times;

    // live adjustment
    Overlay base, original;

    public void run(String arg) {
        imp = WindowManager.getCurrentImage();
        if (imp == null) { IJ.error(TITLE, "Open the movie (or the stack made from it) first."); return; }
        macro = Macro.getOptions() != null;
        title = imp.getTitle();
        W = imp.getWidth(); H = imp.getHeight();
        nC = imp.getNChannels(); nZ = imp.getNSlices(); nF = imp.getNFrames(); nSl = imp.getStackSize();
        if (nF > 1) { tDim = 't'; nT = nF; }
        else if (nZ > 1) { tDim = 'z'; nT = nZ; }
        else { tDim = '-'; nT = 1; }
        sel = imp.getRoi();
        selB = sel != null ? sel.getBounds() : null;

        // ---------------------------------------------------------- frame interval
        Calibration cal = imp.getCalibration();
        double iv = cal.frameInterval;
        int ui = unitIndex(cal.getTimeUnit());
        String ivNote = "";
        boolean ivWarn = false;
        if (ui == -2) { iv = iv / 1000; ui = 0; }
        if (!(iv > 0)) {
            if (cal.fps > 0) {
                iv = 1 / cal.fps; ui = 1;
                ivNote = "Frame interval worked out from the frame rate (" + IJ.d2s(cal.fps, 3) + " frames per second).";
            } else {
                iv = 1; ui = 1;
                ivNote = "No frame interval in Image > Properties: type it here.";
                ivWarn = true;
            }
        } else if (ui < 0) {
            ui = 1;
            ivNote = "Time unit \"" + cal.getTimeUnit() + "\" not recognised: taken as seconds.";
            ivWarn = true;
        }

        // ---------------------------------------------------------- original frame numbers
        labels = new String[nT];
        boolean anyLabel = false;
        ImageStack stack = imp.getStack();
        for (int k = 1; k <= nT; k++) {
            String lab;
            if (nSl > 1) lab = stack.getSliceLabel(stackIdx(k));
            else {
                lab = (String) imp.getProperty("Label");
                if (lab == null || lab.isEmpty()) {
                    String l2 = stack.getSliceLabel(1);
                    lab = l2 != null && l2.indexOf('\n') < 0 ? l2 : "";
                }
            }
            lab = firstLine(lab);
            labels[k - 1] = lab;
            if (!lab.isEmpty()) anyLabel = true;
        }
        double ivS0 = iv * UNIT_SEC[ui];
        int[] f = new int[0];
        int src = 0;
        int maxP = nT == 1 ? 4 : 5;
        if (anyLabel)
            for (int p = 1; p <= maxP && f.length == 0 && !selfLab; p++) {
                f = tryPattern(labels, p, ivS0);
                if (f.length > 0) src = p;
            }
        if (f.length == 0 && title.startsWith("Substack (") && title.endsWith(")") && !title.contains("...")) {
            int[] t2 = parseFrames(title.substring(10, title.length() - 1));
            if (parseErr.isEmpty() && t2.length == nT) { f = t2; src = 6; }
        }
        if (f.length == 0) {
            f = new int[nT];
            for (int i = 0; i < nT; i++) f[i] = i + 1;
            src = 0;
        }
        String frStr = compactFrames(f);
        String lab1 = labels[0].length() > 32 ? labels[0].substring(0, 32) + "..." : labels[0];
        String srcMsg;
        Color srcCol = new Color(0, 100, 0), warn = new Color(192, 80, 0);
        if (src == 1) srcMsg = "Original frame numbers read from the slice labels (slice 1: \"" + lab1 + "\").";
        else if (src == 2) srcMsg = "Original frame numbers worked out from the frame times in the slice labels (slice 1: \"" + lab1 + "\").";
        else if (src >= 3 && src <= 5) srcMsg = "Original frame numbers read from the numbers in the slice labels (slice 1: \"" + lab1 + "\").";
        else if (src == 6) srcMsg = "Original frame numbers read from the title (list given to Make Substack).";
        else if (selfLab) {
            srcMsg = "The slice labels number the frames of this stack itself (t:1/" + nT + " to t:" + nT + "/" + nT
                    + "): right if it is the\nwhole movie. If frames were left out, type their numbers in the original movie.";
            srcCol = warn;
        } else {
            srcMsg = "No original frame numbers found" + (anyLabel ? " in the slice labels (slice 1: \"" + lab1 + "\")" : "")
                    + ":\nthe frames are taken as consecutive. Type the right numbers if frames were left out.";
            srcCol = warn;
        }
        String shortTitle = title.length() > 40 ? title.substring(0, 40) + "..." : title;
        String dimNote = tDim == 't' ? nT + " frames" : tDim == 'z' ? nT + " slices, each taken as one time point" : "one image";
        if (nC > 1) dimNote += ", " + nC + " channels";

        if (src == 0 && nT <= 60 && !macro) {
            int[] typed = askFrames(shortTitle);
            if (typed == null) return;
            f = typed;
            frStr = compactFrames(f);
            srcMsg = "Original frame numbers as typed for each image (they can still be changed here).";
            srcCol = new Color(0, 100, 0);
        }

        // ---------------------------------------------------------- dialog
        List<String> pos = new ArrayList<String>();
        for (String a : ANCHORS) pos.add(a);
        if (sel != null) pos.add(AT_SEL);
        pos.add(CUSTOM);
        String[] posList = pos.toArray(new String[0]);
        String fmtC = inList(FORMATS, pget("format", FORMATS[0]));
        decC = inList(DECIMALS, pget("decimals", "auto"));
        prefix = pget("prefix", "");
        addFr = pget("addframe", "0").equals("1");
        String posC = inList(posList, pget("position", "Top left"));
        if (sel != null) posC = AT_SEL;
        double frac = parseD(pget("fontfrac", "0.05"), 0.05);
        if (!(frac > 0 && frac <= 1)) frac = 0.05;
        fsize = Math.max(10, (int) Math.round(Math.min(W, H) * frac));
        int fsize0 = fsize;
        String colC = inList(COLOURS, pget("colour", "White"));
        String bgC = inList(BACKGROUNDS, pget("background", "None"));
        bold = pget("bold", "1").equals("1");
        String outC = inList(OUTPUTS, pget("output", OUTPUTS[0]));
        tableOn = pget("table", "1").equals("1");
        live = pget("live", "1").equals("1");
        String vecC = inList(VECTORS, pget("vector", VECTORS[0]));
        include = pget("include", "1").equals("1");
        cx = Math.round(parseD(pget("customx", "0.05"), 0.05) * W);
        cy = Math.round(parseD(pget("customy", "0.05"), 0.05) * H);
        columns = (int) parseD(pget("columns", "0"), 0);
        border = (int) parseD(pget("border", "-1"), -1);
        if (border < 0) border = Math.max(2, (int) Math.round(0.02 * Math.min(W, H)));
        String borderC = inList(BORDERS, pget("bordercolour", "White"));
        String ivStr = IJ.d2s(iv, decimalsOf(iv)) + " " + UNITS[ui];

        while (true) {
            GenericDialog gd = new GenericDialog(TITLE);
            gd.addMessage(shortTitle + ":  " + dimNote);
            gd.addMessage(srcMsg, null, srcCol);
            gd.addStringField("Original frames", frStr, 44);
            gd.addStringField("Frame interval", ivStr, 10);
            if (!ivNote.isEmpty()) gd.addMessage(ivNote, null, ivWarn ? warn : new Color(0, 100, 0));
            gd.addNumericField("Time zero at original frame", zero, 0, 6, "(earlier frames: negative times)");
            gd.addChoice("Display as", FORMATS, fmtC);
            gd.addChoice("Decimals", DECIMALS, decC);
            gd.addStringField("Text before the time", prefix, 10);
            gd.addCheckbox("Add the original frame number", addFr);
            gd.addChoice("Position", posList, posC);
            gd.addNumericField("X", cx, 0, 6, "px  (custom position: left edge of the text)");
            gd.addNumericField("Y", cy, 0, 6, "px  (custom position: top of the text)");
            gd.addNumericField("Font size", fsize, 0, 4, "pixels");
            gd.addCheckbox("Bold", bold);
            gd.addChoice("Colour", COLOURS, colC);
            gd.addChoice("Background", BACKGROUNDS, bgC);
            if (imp.getWindow() != null && !macro)
                gd.addCheckbox("Adjust live (drag the time on the image)", live);
            gd.addChoice("Write the time", OUTPUTS, outC);
            gd.addChoice("Vector file (SVG, editable in Illustrator)", VECTORS, vecC);
            gd.addCheckbox("Include the image in the SVG", include);
            gd.addNumericField("Columns (montage, 0 = auto)", columns, 0, 4, "");
            gd.addNumericField("Width of the borders (montage)", border, 0, 4, "px  (between and around the frames)");
            gd.addChoice("Border colour (montage)", BORDERS, borderC);
            gd.addCheckbox("List the times in a table", tableOn);
            gd.addHelp(HELP);
            gd.showDialog();
            if (gd.wasCanceled()) return;
            frStr = gd.getNextString();
            ivStr = gd.getNextString();
            prefix = gd.getNextString();
            zero = gd.getNextNumber();
            cx = gd.getNextNumber();
            cy = gd.getNextNumber();
            fsize = (int) Math.round(gd.getNextNumber());
            fmtC = gd.getNextChoice();
            decC = gd.getNextChoice();
            posC = gd.getNextChoice();
            colC = gd.getNextChoice();
            bgC = gd.getNextChoice();
            outC = gd.getNextChoice();
            vecC = gd.getNextChoice();
            addFr = gd.getNextBoolean();
            bold = gd.getNextBoolean();
            if (imp.getWindow() != null && !macro) live = gd.getNextBoolean();
            include = gd.getNextBoolean();
            tableOn = gd.getNextBoolean();
            columns = (int) Math.round(gd.getNextNumber());
            border = (int) Math.round(gd.getNextNumber());
            borderC = gd.getNextChoice();
            ivS = parseInterval(ivStr, ui);
            String err = "";
            int[] frN = parseFrames(frStr);
            if (!parseErr.isEmpty()) err = "Original frames: " + parseErr + ".";
            else if (frN.length != nT)
                err = "Original frames: " + frN.length + " numbers given, but the image has " + nT + " time points.";
            if (err.isEmpty() && !(ivS > 0)) err = "Frame interval: type a number above 0 and a unit, for example 0.87 s or 870 ms.";
            if (err.isEmpty() && Double.isNaN(zero)) err = "Type the original frame of time zero (1 = first frame of the movie).";
            if (err.isEmpty() && !(border >= 0)) err = "The width of the borders must be 0 or more pixels.";
            if (err.isEmpty() && !(fsize >= 4)) err = "The font size must be at least 4 pixels.";
            if (err.isEmpty() && posC.equals(CUSTOM) && (Double.isNaN(cx) || Double.isNaN(cy)))
                err = "Custom position: type X and Y in pixels.";
            if (err.isEmpty() && indexIn(OUTPUTS, outC) == 2 && indexIn(VECTORS, vecC) == 0)
                err = "Nothing would be written: choose an overlay, an RGB copy or a vector file.";
            if (err.isEmpty()) { fr = frN; break; }
            IJ.error(TITLE, err);
            if (macro) return;
        }
        fmtI = indexIn(FORMATS, fmtC);
        colI = indexIn(COLOURS, colC);
        bgI = indexIn(BACKGROUNDS, bgC);
        outI = indexIn(OUTPUTS, outC);
        vecI = indexIn(VECTORS, vecC);
        borderI = indexIn(BORDERS, borderC);
        posMode = posC.equals(AT_SEL) ? P_SEL : posC.equals(CUSTOM) ? P_CUSTOM : indexIn(ANCHORS, posC);
        dec = decC.equals("auto") ? autoDec(fmtI, ivS) : Integer.parseInt(decC);

        // ---------------------------------------------------------- texts
        times = new double[nT];
        texts = new String[nT];
        String pre = prefix;
        if (!pre.isEmpty() && !pre.endsWith(" ")) pre = pre + " ";
        for (int k = 0; k < nT; k++) {
            times[k] = (fr[k] - zero) * ivS;
            texts[k] = pre + fmtTime(times[k], fmtI, dec) + (addFr ? "  (frame " + fr[k] + ")" : "");
        }

        // ---------------------------------------------------------- live adjustment, then outputs
        original = imp.getOverlay() != null ? imp.getOverlay().duplicate() : null;
        base = withoutStamps(imp.getOverlay());
        if (live && imp.getWindow() != null && !macro) {
            if (!liveAdjust()) {
                imp.setOverlay(original);
                if (sel != null) imp.setRoi(sel);
                return;
            }
        }
        if (imp.getWindow() != null && sel != null && imp.getRoi() == null) imp.setRoi(sel);
        savePrefs(fsize0);

        if (outI == 0) {
            imp.setOverlay(stampOverlay());
        } else {
            imp.setOverlay(original);
            if (outI == 1) {
                ImagePlus rgb = rgbCopy();
                if (rgb != null) rgb.show();
            }
        }
        if (vecI > 0) exportSvg();
        if (tableOn) table();
    }

    static final String HELP = "<html><b>Smart Time Stamper</b><br>"
            + "Writes on each frame its real time: (original frame - frame of time zero) x frame interval.<br><br>"
            + "<b>Original frames</b>: the frame number in the movie of each time point of this image, in order. They are "
            + "read from the slice labels that Make Substack keeps (Bio-Formats \"t:45/200\", AVI times, \"frame 45\"...) "
            + "or from the title given by Make Substack. Check them. Lists and ranges: 3,15,27,40-43 (40 to 43), "
            + "1-199-12 (every 12th frame).<br>"
            + "<b>Frame interval</b>: from Image &gt; Properties; a number and a unit (ms, s, min, h), for example "
            + "0.87 s or 870 ms.<br>"
            + "<b>Time zero</b>: the original frame shown as time 0; earlier frames get negative times.<br>"
            + "<b>Decimals</b>: auto = enough to tell two consecutive frames of the movie apart.<br>"
            + "<b>Position</b>: a corner or centre, the selection drawn before starting, or Custom: X, Y = top left "
            + "corner of the text, in pixels.<br>"
            + "<b>Adjust live</b>: a panel stays open; drag the time with the mouse on the image (on any frame), "
            + "nudge it with the arrow keys (Shift: 10 px), change size, colour and background; the movie updates at "
            + "once. OK keeps the result.<br>"
            + "<b>Overlay</b>: the pixels are not changed (Image &gt; Overlay &gt; Remove Overlay takes it away). "
            + "<b>RGB copy</b>: the time is burned into a new RGB image, as the image is displayed.<br>"
            + "<b>Vector file</b>: SVG files that Illustrator or Inkscape open with the time as real, editable text "
            + "(layer \"time stamps\") over the frame (layer \"images\", as displayed). One file per time point, or one "
            + "montage of all time points (Columns: 0 = about square). Untick \"Include the image\" to get the time "
            + "stamps only. The files go next to the image (a folder is asked otherwise).<br>"
            + "<b>Table</b>: every time point with its original frame, its time in seconds and the text written.</html>";

    // ================================================================== geometry and drawing
    Font font() { return new Font("SansSerif", bold ? Font.BOLD : Font.PLAIN, fsize); }

    static final BufferedImage FG = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);

    FontMetrics metrics() {
        Graphics2D g = FG.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        FontMetrics m = g.getFontMetrics(font());
        g.dispose();
        return m;
    }

    /** Box of the text of time point k (0-based): {x, y, width, height, baseline}, as ImageJ's TextRoi draws it. */
    double[] box(int k, FontMetrics m) {
        double w = m.stringWidth(texts[k]) + 2, h = m.getHeight() + 2;
        int mg = Math.max(2, (int) Math.round(fsize * 0.4));
        double x, y;
        if (posMode == P_SEL && selB != null) { x = selB.x; y = selB.y; }
        else if (posMode == P_CUSTOM || posMode == P_SEL) { x = cx; y = cy; }
        else {
            int c3 = posMode % 3;
            x = c3 == 0 ? mg : c3 == 1 ? Math.round((W - w) / 2) : W - mg - w;
            y = posMode < 3 ? mg : H - mg - h;
        }
        x = Math.round(x); y = Math.round(y);
        return new double[]{x, y, w, h, y + m.getHeight() - m.getDescent()};
    }

    Color colour() { return Color.decode(COLOUR_HEX[colI]); }

    /** The base overlay plus one text item per time point. */
    Overlay stampOverlay() {
        Overlay ov = base != null ? base.duplicate() : new Overlay();
        FontMetrics m = metrics();
        Font fnt = font();
        boolean hyper = imp.isHyperStack() || nC > 1;
        for (int k = 1; k <= nT; k++) {
            double[] b = box(k - 1, m);
            TextRoi r = new TextRoi(b[0], b[1], texts[k - 1], fnt);
            r.setAntiAlias(true);
            r.setStrokeColor(colour());
            if (BG_COLOUR[bgI] != null) r.setFillColor(BG_COLOUR[bgI]);
            r.setName(NAME);
            if (nSl == 1 || nT == 1) ov.add(r);
            else if (hyper) {
                if (tDim == 't') r.setPosition(0, 0, k); else r.setPosition(0, k, 0);
                ov.add(r);
            } else if (nSl == nT) {
                r.setPosition(k);
                ov.add(r);
            } else {
                int per = nSl / nT;
                for (int j = 1; j <= per; j++) {
                    Roi r2 = (Roi) r.clone();
                    r2.setPosition((k - 1) * per + j);
                    ov.add(r2);
                }
            }
        }
        return ov;
    }

    static Overlay withoutStamps(Overlay o) {
        Overlay ov = new Overlay();
        if (o == null) return ov;
        for (int i = 0; i < o.size(); i++) if (!NAME.equals(o.get(i).getName())) ov.add(o.get(i));
        ov.drawLabels(o.getDrawLabels());
        return ov;
    }

    /** Draws the time stamp of time point k (0-based) at offset (ox, oy). */
    void drawStamp(Graphics2D g, int k, double ox, double oy) {
        FontMetrics m = metrics();
        double[] b = box(k, m);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (BG_COLOUR[bgI] != null) {
            g.setColor(BG_COLOUR[bgI]);
            g.fillRect((int) (ox + b[0]), (int) (oy + b[1]), (int) Math.round(b[2]), (int) Math.round(b[3]));
        }
        g.setColor(colour());
        g.setFont(font());
        g.drawString(texts[k], (int) (ox + b[0]), (int) (oy + b[4]));
    }

    /** The image as displayed (channels, LUTs, brightness), without overlay, for time point k and slice z. */
    BufferedImage rendered(int k, int z) {
        int c0 = imp.getChannel(), z0 = imp.getSlice(), t0 = imp.getFrame();
        if (tDim == 't') imp.setPositionWithoutUpdate(c0, z, k);
        else if (tDim == 'z') imp.setPositionWithoutUpdate(c0, k, t0);
        imp.updateImage();
        Image img = imp.getImage();
        BufferedImage bi = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = bi.createGraphics();
        g.drawImage(img, 0, 0, null);
        g.dispose();
        imp.setPositionWithoutUpdate(c0, z0, t0);
        imp.updateImage();
        return bi;
    }

    // ================================================================== outputs
    ImagePlus rgbCopy() {
        int nz = tDim == 't' ? nZ : 1;
        ImageStack out = new ImageStack(W, H);
        for (int k = 1; k <= nT; k++)
            for (int z = 1; z <= nz; z++) {
                IJ.showProgress((k - 1) * nz + z, nT * nz);
                BufferedImage bi = rendered(k, z);
                Graphics2D g = bi.createGraphics();
                drawStamp(g, k - 1, 0, 0);
                g.dispose();
                String lab = labels[k - 1];
                out.addSlice(lab == null || lab.isEmpty() ? null : lab, new ColorProcessor(bi));
            }
        IJ.showProgress(1.0);
        imp.updateAndDraw();
        ImagePlus r = new ImagePlus(baseName() + "_time", out);
        r.setDimensions(1, nz, nT);
        if (nz > 1 && nT > 1) r.setOpenAsHyperStack(true);
        r.setCalibration(imp.getCalibration().copy());
        return r;
    }

    String baseName() {
        String b = title;
        int dot = b.lastIndexOf('.');
        if (dot > 0 && b.length() - dot <= 5) b = b.substring(0, dot);
        return b;
    }

    void exportSvg() {
        String dir = null;
        FileInfo fi = imp.getOriginalFileInfo();
        if (fi != null && fi.directory != null && !fi.directory.isEmpty() && new File(fi.directory).isDirectory())
            dir = fi.directory;
        if (dir == null || (macro && Macro.getValue(Macro.getOptions(), "folder", null) != null)) {
            DirectoryChooser.setDefaultDirectory(fi != null ? fi.directory : null);
            dir = new DirectoryChooser("Folder for the SVG files").getDirectory();
            if (dir == null) return;
        }
        String bn = baseName().replaceAll("[\\\\/:*?\"<>|]", "_");
        int z = tDim == 't' ? imp.getSlice() : 1;
        List<File> written = new ArrayList<File>();
        try {
            if (vecI == 1) {
                File folder = unique(new File(dir, bn + "_time_svg"));
                if (!folder.mkdirs()) throw new java.io.IOException("cannot create " + folder);
                int pad = Math.max(3, String.valueOf(nT).length());
                for (int k = 1; k <= nT; k++) {
                    IJ.showProgress(k, nT);
                    StringBuilder img = new StringBuilder(), txt = new StringBuilder();
                    cell(k - 1, z, 0, 0, img, txt);
                    File fl = new File(folder, bn + "_t" + pad(k, pad) + ".svg");
                    writeSvg(fl, W, H, img, txt, null);
                    written.add(fl);
                }
            } else {
                int cols = cols(), rows = (int) Math.ceil(nT / (double) cols), b = border;
                StringBuilder img = new StringBuilder(), txt = new StringBuilder();
                for (int k = 0; k < nT; k++) {
                    IJ.showProgress(k + 1, nT);
                    cell(k, z, b + (k % cols) * (W + b), b + (k / cols) * (H + b), img, txt);
                }
                File fl = unique(new File(dir, bn + "_time_montage.svg"));
                writeSvg(fl, cols * W + (cols + 1) * b, rows * H + (rows + 1) * b, img, txt,
                        b > 0 ? BORDER_COLOUR[borderI] : null);
                written.add(fl);
            }
        } catch (Exception e) {
            IJ.error(TITLE, "The SVG file could not be written:\n" + e.getMessage());
            return;
        } finally {
            IJ.showProgress(1.0);
        }
        IJ.showStatus(TITLE + ": " + written.size() + " SVG file(s) written");
        IJ.log(TITLE + ": " + (written.size() == 1 ? written.get(0).getPath()
                : written.size() + " SVG files in " + written.get(0).getParent()));
    }

    /** SVG pieces of time point k (0-based) placed at (ox, oy): the image and the time stamp. */
    void cell(int k, int z, int ox, int oy, StringBuilder img, StringBuilder txt) throws java.io.IOException {
        if (include) {
            BufferedImage bi = rendered(k + 1, z);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            ImageIO.write(bi, "png", bo);
            img.append("    <image id=\"frame_").append(k + 1).append("\" x=\"").append(ox).append("\" y=\"").append(oy)
                    .append("\" width=\"").append(W).append("\" height=\"").append(H)
                    .append("\" preserveAspectRatio=\"none\" xlink:href=\"data:image/png;base64,")
                    .append(Base64.getEncoder().encodeToString(bo.toByteArray())).append("\"/>\n");
        }
        FontMetrics m = metrics();
        double[] b = box(k, m);
        txt.append("    <g id=\"time_").append(k + 1).append("\">\n");
        Color bg = BG_COLOUR[bgI];
        if (bg != null)
            txt.append("      <rect x=\"").append(num(ox + b[0])).append("\" y=\"").append(num(oy + b[1]))
                    .append("\" width=\"").append(num(b[2])).append("\" height=\"").append(num(b[3]))
                    .append("\" fill=\"").append(hex(bg)).append("\"")
                    .append(bg.getAlpha() < 255 ? " fill-opacity=\"" + num(bg.getAlpha() / 255.0) + "\"" : "")
                    .append("/>\n");
        txt.append("      <text x=\"").append(num(ox + b[0])).append("\" y=\"").append(num(oy + b[4]))
                .append("\" font-family=\"Arial, Helvetica, sans-serif\" font-size=\"").append(fsize)
                .append("\"").append(bold ? " font-weight=\"bold\"" : "")
                .append(" fill=\"").append(COLOUR_HEX[colI]).append("\" xml:space=\"preserve\">")
                .append(xml(texts[k])).append("</text>\n    </g>\n");
    }

    int cols() { return columns > 0 ? Math.min(columns, nT) : (int) Math.ceil(Math.sqrt(nT)); }

    static void writeSvg(File f, int w, int h, StringBuilder img, StringBuilder txt, Color borders) throws java.io.IOException {
        Writer o = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8);
        try {
            o.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            o.write("<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" version=\"1.1\" "
                    + "width=\"" + w + "px\" height=\"" + h + "px\" viewBox=\"0 0 " + w + " " + h + "\">\n");
            if (borders != null)
                o.write("  <g id=\"borders\">\n    <rect x=\"0\" y=\"0\" width=\"" + w + "\" height=\"" + h + "\" fill=\""
                        + hex(borders) + "\"/>\n  </g>\n");
            if (img.length() > 0) { o.write("  <g id=\"images\">\n"); o.write(img.toString()); o.write("  </g>\n"); }
            o.write("  <g id=\"time stamps\">\n");
            o.write(txt.toString());
            o.write("  </g>\n</svg>\n");
        } finally {
            o.close();
        }
    }

    static File unique(File f) {
        if (!f.exists()) return f;
        String n = f.getName(), ext = "";
        int dot = n.lastIndexOf('.');
        if (dot > 0 && f.getName().endsWith(".svg")) { ext = n.substring(dot); n = n.substring(0, dot); }
        for (int i = 2; ; i++) {
            File g = new File(f.getParentFile(), n + "_" + i + ext);
            if (!g.exists()) return g;
        }
    }

    static String pad(int v, int n) {
        String s = String.valueOf(v);
        while (s.length() < n) s = "0" + s;
        return s;
    }

    static String num(double v) {
        if (v == Math.rint(v)) return String.valueOf((long) v);
        return String.format(Locale.US, "%.3f", v);
    }

    static String hex(Color c) { return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue()); }

    static String xml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    void table() {
        ResultsTable rt = new ResultsTable();
        for (int k = 0; k < nT; k++) {
            rt.incrementCounter();
            rt.addValue("Time point", k + 1);
            rt.addValue("Original frame", fr[k]);
            rt.addValue("Time (s)", times[k]);
            rt.addValue("Text", texts[k]);
            rt.addValue("Slice label", labels[k]);
        }
        rt.show("Time stamps");
    }

    void savePrefs(int fsize0) {
        pset("format", FORMATS[fmtI]);
        pset("decimals", decC);
        pset("prefix", prefix);
        pset("addframe", addFr ? "1" : "0");
        if (posMode < 6) pset("position", ANCHORS[posMode]);
        else if (posMode == P_CUSTOM) {
            pset("position", CUSTOM);
            pset("customx", String.format(Locale.US, "%.5f", cx / W));
            pset("customy", String.format(Locale.US, "%.5f", cy / H));
        }
        if (fsize != fsize0) pset("fontfrac", String.format(Locale.US, "%.5f", fsize / (double) Math.min(W, H)));
        pset("colour", COLOURS[colI]);
        pset("background", BACKGROUNDS[bgI]);
        pset("bold", bold ? "1" : "0");
        pset("output", OUTPUTS[outI]);
        pset("vector", VECTORS[vecI]);
        pset("columns", String.valueOf(columns));
        pset("border", String.valueOf(border));
        pset("bordercolour", BORDERS[borderI]);
        pset("include", include ? "1" : "0");
        pset("table", tableOn ? "1" : "0");
        if (!macro && imp.getWindow() != null) pset("live", live ? "1" : "0");
    }

    static String pget(String k, String d) { return Prefs.get(PREFS + k, d); }

    static void pset(String k, String v) { Prefs.set(PREFS + k, v); }

    // ================================================================== live adjustment
    JDialog dlg;
    JLabel posLabel;
    JComboBox<String> snap;
    volatile boolean accepted;
    final CountDownLatch done = new CountDownLatch(1);
    boolean updatingSnap;
    // live preview of the montage
    BufferedImage[] frames;
    ImagePlus preview;
    volatile boolean previewDirty;

    /** Montage of all time points with their time stamps, as the SVG montage will look. */
    BufferedImage montage() {
        int cols = cols(), rows = (int) Math.ceil(nT / (double) cols), b = border;
        BufferedImage m = new BufferedImage(cols * W + (cols + 1) * b, rows * H + (rows + 1) * b, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = m.createGraphics();
        Color bc = BORDER_COLOUR[borderI];
        g.setColor(bc != null ? bc : new Color(230, 230, 230));        // transparent: light grey in the preview
        g.fillRect(0, 0, m.getWidth(), m.getHeight());
        for (int k = 0; k < nT; k++) {
            int ox = b + (k % cols) * (W + b), oy = b + (k / cols) * (H + b);
            if (include) g.drawImage(frames[k], ox, oy, null);
            else { g.setColor(new Color(60, 60, 60)); g.fillRect(ox, oy, W, H); }
            drawStamp(g, k, ox, oy);
        }
        g.dispose();
        return m;
    }

    void startPreview() {
        int z = tDim == 't' ? imp.getSlice() : 1;
        frames = new BufferedImage[nT];
        for (int k = 0; k < nT; k++) {
            IJ.showProgress(k + 1, nT);
            frames[k] = rendered(k + 1, z);
        }
        IJ.showProgress(1.0);
        preview = new ImagePlus("Montage preview", new ColorProcessor(montage()));
        preview.show();
        Thread t = new Thread(new Runnable() {
            public void run() {
                while (done.getCount() > 0) {
                    try { Thread.sleep(60); } catch (InterruptedException e) { return; }
                    if (!previewDirty || preview == null || preview.getWindow() == null) continue;
                    previewDirty = false;
                    preview.setProcessor(new ColorProcessor(montage()));
                    preview.updateAndDraw();
                }
            }
        }, "STS montage preview");
        t.setDaemon(true);
        t.start();
    }

    void closePreview() {
        if (preview != null) { preview.changes = false; preview.close(); preview = null; }
        frames = null;
    }

    /** Non-blocking panel: drag the time on the image, nudge, size, colour; the movie updates at once. */
    boolean liveAdjust() {
        final ImageWindow win = imp.getWindow();
        final ImageCanvas canvas = win.getCanvas();
        imp.deleteRoi();
        imp.setOverlay(stampOverlay());
        if (vecI == 2) startPreview();
        win.toFront();

        final MouseAdapter drag = new MouseAdapter() {
            double offX, offY;
            boolean on;

            public void mousePressed(MouseEvent e) {
                if (e.getButton() != MouseEvent.BUTTON1 || e.isPopupTrigger()) return;
                double x = canvas.offScreenXD(e.getX()), y = canvas.offScreenYD(e.getY());
                int k = currentK();
                double[] b = box(k, metrics());
                if (x >= b[0] && x <= b[0] + b[2] && y >= b[1] && y <= b[1] + b[3]) { offX = x - b[0]; offY = y - b[1]; }
                else { offX = b[2] / 2; offY = b[3] / 2; }
                on = true;
                moveTo(x - offX, y - offY);
            }

            public void mouseDragged(MouseEvent e) {
                if (!on) return;
                moveTo(canvas.offScreenXD(e.getX()) - offX, canvas.offScreenYD(e.getY()) - offY);
            }

            public void mouseReleased(MouseEvent e) { on = false; }
        };
        final KeyEventDispatcher keys = new KeyEventDispatcher() {
            public boolean dispatchKeyEvent(KeyEvent e) {
                if (e.getID() != KeyEvent.KEY_PRESSED) return false;
                int c = e.getKeyCode();
                if (c != KeyEvent.VK_LEFT && c != KeyEvent.VK_RIGHT && c != KeyEvent.VK_UP && c != KeyEvent.VK_DOWN) return false;
                Window w = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
                Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
                boolean onImage = w == win;
                boolean onPanel = w == dlg && !(owner instanceof JTextField) && !(owner instanceof JComboBox)
                        && !(owner instanceof JSpinner) && !(SwingUtilities.getAncestorOfClass(JSpinner.class, owner) != null);
                if (!onImage && !onPanel) return false;
                int s = e.isShiftDown() ? 10 : 1;
                nudge(c == KeyEvent.VK_LEFT ? -s : c == KeyEvent.VK_RIGHT ? s : 0, c == KeyEvent.VK_UP ? -s : c == KeyEvent.VK_DOWN ? s : 0);
                return true;
            }
        };

        onEDT(new Runnable() { public void run() { buildPanel(win); } });
        canvas.removeMouseListener(canvas);
        canvas.removeMouseMotionListener(canvas);
        canvas.addMouseListener(drag);
        canvas.addMouseMotionListener(drag);
        canvas.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(keys);
        try {
            while (true) {
                try { if (done.await(200, TimeUnit.MILLISECONDS)) break; }
                catch (InterruptedException ex) { accepted = false; break; }
                if (imp.getWindow() == null) { accepted = false; break; }
            }
        } finally {
            KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(keys);
            canvas.removeMouseListener(drag);
            canvas.removeMouseMotionListener(drag);
            canvas.addMouseListener(canvas);
            canvas.addMouseMotionListener(canvas);
            canvas.setCursor(Cursor.getDefaultCursor());
            onEDT(new Runnable() { public void run() { if (dlg != null) dlg.dispose(); } });
            closePreview();
        }
        return accepted;
    }

    int currentK() {
        int k = tDim == 't' ? imp.getFrame() : tDim == 'z' ? imp.getSlice() : 1;
        return Math.max(0, Math.min(nT - 1, k - 1));
    }

    /** The text box (of the displayed time point) goes to (x, y): custom position, kept inside the image. */
    void moveTo(double x, double y) {
        double[] b = box(currentK(), metrics());
        cx = Math.round(Math.max(0, Math.min(W - b[2], x)));
        cy = Math.round(Math.max(0, Math.min(H - b[3], y)));
        posMode = P_CUSTOM;
        refresh();
    }

    void nudge(int dx, int dy) {
        double[] b = box(currentK(), metrics());
        moveTo(b[0] + dx, b[1] + dy);
    }

    void refresh() {
        imp.setOverlay(stampOverlay());
        previewDirty = true;
        final double[] b = box(currentK(), metrics());
        EventQueue.invokeLater(new Runnable() {
            public void run() {
                if (posLabel == null) return;
                posLabel.setText("Position: X " + (int) b[0] + ", Y " + (int) b[1] + " px (top left corner of the text)");
                updatingSnap = true;
                snap.setSelectedItem(posMode < 6 ? ANCHORS[posMode] : posMode == P_SEL ? AT_SEL : "Custom (dragged)");
                updatingSnap = false;
            }
        });
    }

    void buildPanel(Window owner) {
        dlg = new JDialog(owner, TITLE + " - position", Dialog.ModalityType.MODELESS);
        dlg.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        dlg.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) { accepted = false; done.countDown(); }
        });
        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        p.add(new JLabel("<html><body style='width:300px'><b>Place the time stamp</b><br>"
                + "&bull; <b>Drag</b> it with the mouse on the image (on any frame).<br>"
                + "&bull; <b>Arrow keys</b>: 1 px (Shift: 10 px), image or this panel selected.<br>"
                + "&bull; Scroll the movie to check it on every frame; the changes show at once.</body></html>"),
                BorderLayout.NORTH);

        JPanel g = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 3, 3, 3);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;

        List<String> sn = new ArrayList<String>();
        for (String a : ANCHORS) sn.add(a);
        if (selB != null) sn.add(AT_SEL);
        sn.add("Custom (dragged)");
        snap = new JComboBox<String>(sn.toArray(new String[0]));
        snap.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (updatingSnap) return;
                String s = (String) snap.getSelectedItem();
                int i = indexIn(ANCHORS, s);
                if (ANCHORS[i].equals(s)) posMode = i;
                else if (AT_SEL.equals(s)) posMode = P_SEL;
                else posMode = P_CUSTOM;
                refresh();
            }
        });
        final JSpinner size = new JSpinner(new SpinnerNumberModel(fsize, 4, 2000, 1));
        size.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent e) {
                fsize = ((Number) size.getValue()).intValue();
                refresh();
            }
        });
        final JCheckBox bd = new JCheckBox("Bold", bold);
        bd.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { bold = bd.isSelected(); refresh(); }
        });
        final JComboBox<String> col = new JComboBox<String>(COLOURS);
        col.setSelectedIndex(colI);
        col.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { colI = col.getSelectedIndex(); refresh(); }
        });
        final JComboBox<String> bg = new JComboBox<String>(BACKGROUNDS);
        bg.setSelectedIndex(bgI);
        bg.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { bgI = bg.getSelectedIndex(); refresh(); }
        });
        int row = 0;
        row = addRow(g, c, row, "Snap to", snap);
        row = addRow(g, c, row, "Font size (px)", size);
        row = addRow(g, c, row, "", bd);
        row = addRow(g, c, row, "Colour", col);
        row = addRow(g, c, row, "Background", bg);
        JPanel arrows = new JPanel(new GridLayout(1, 4, 4, 4));
        String[][] ar = {{"←", "-1", "0"}, {"→", "1", "0"}, {"↑", "0", "-1"}, {"↓", "0", "1"}};
        for (final String[] a : ar) {
            JButton b = new JButton(a[0]);
            b.setFocusable(false);
            b.setToolTipText("Move by 1 px (Shift: 10 px)");
            b.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    int s = (e.getModifiers() & ActionEvent.SHIFT_MASK) != 0 ? 10 : 1;
                    nudge(s * Integer.parseInt(a[1]), s * Integer.parseInt(a[2]));
                }
            });
            arrows.add(b);
        }
        row = addRow(g, c, row, "Move", arrows);
        if (vecI == 2) {
            final JSpinner cl = new JSpinner(new SpinnerNumberModel(cols(), 1, nT, 1));
            cl.addChangeListener(new javax.swing.event.ChangeListener() {
                public void stateChanged(javax.swing.event.ChangeEvent e) {
                    columns = ((Number) cl.getValue()).intValue();
                    refresh();
                }
            });
            final JSpinner bw = new JSpinner(new SpinnerNumberModel(border, 0, 2000, 1));
            bw.addChangeListener(new javax.swing.event.ChangeListener() {
                public void stateChanged(javax.swing.event.ChangeEvent e) {
                    border = ((Number) bw.getValue()).intValue();
                    refresh();
                }
            });
            final JComboBox<String> bcol = new JComboBox<String>(BORDERS);
            bcol.setSelectedIndex(borderI);
            bcol.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) { borderI = bcol.getSelectedIndex(); refresh(); }
            });
            JLabel mt = new JLabel("<html><b>Montage</b> (window \"Montage preview\")</html>");
            c.gridx = 0; c.gridy = row++; c.gridwidth = 2;
            g.add(mt, c);
            row = addRow(g, c, row, "Columns", cl);
            row = addRow(g, c, row, "Border width (px)", bw);
            row = addRow(g, c, row, "Border colour", bcol);
        }
        posLabel = new JLabel(" ");
        posLabel.setForeground(new Color(0, 80, 160));
        c.gridx = 0; c.gridy = row; c.gridwidth = 2;
        g.add(posLabel, c);
        p.add(g, BorderLayout.CENTER);

        JPanel btn = new JPanel(new GridLayout(1, 2, 6, 6));
        JButton ok = new JButton("OK"), cancel = new JButton("Cancel");
        ok.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { accepted = true; done.countDown(); }
        });
        cancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { accepted = false; done.countDown(); }
        });
        btn.add(ok);
        btn.add(cancel);
        p.add(btn, BorderLayout.SOUTH);
        dlg.setContentPane(p);
        dlg.getRootPane().setDefaultButton(ok);
        dlg.pack();
        Rectangle o = owner.getBounds();
        Rectangle scr = owner.getGraphicsConfiguration() != null ? owner.getGraphicsConfiguration().getBounds()
                : new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        int x = o.x + o.width + 8, y = o.y;
        if (x + dlg.getWidth() > scr.x + scr.width) x = o.x - dlg.getWidth() - 8;
        x = Math.max(scr.x, Math.min(x, scr.x + scr.width - dlg.getWidth()));
        y = Math.max(scr.y, Math.min(y, scr.y + scr.height - dlg.getHeight()));
        dlg.setLocation(x, y);
        dlg.setVisible(true);
        refresh();
    }

    static int addRow(JPanel g, GridBagConstraints c, int row, String label, Component comp) {
        c.gridwidth = 1;
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        g.add(new JLabel(label), c);
        c.gridx = 1; c.weightx = 1;
        g.add(comp, c);
        return row + 1;
    }

    static void onEDT(Runnable r) {
        if (SwingUtilities.isEventDispatchThread()) { r.run(); return; }
        try { SwingUtilities.invokeAndWait(r); }
        catch (Exception e) { IJ.log(TITLE + ": " + e); }
    }

    // ================================================================== frames typed one by one
    int[] askFrames(String shortTitle) {
        String[] vals = new String[nT];
        for (int i = 0; i < nT; i++) vals[i] = "";
        String hint = "";
        if (title.startsWith("Substack (") && title.endsWith(")") && title.contains(", ... ")) {
            String inner = title.substring(10, title.length() - 1);
            int dd = inner.indexOf(", ... ");
            String[] first = inner.substring(0, dd).split(",");
            String last = inner.substring(dd + 6).trim();
            boolean okT = last.matches("\\d+");
            for (String s : first) if (!s.trim().matches("\\d+")) okT = false;
            if (okT && first.length < nT) {
                for (int i = 0; i < first.length; i++) vals[i] = first[i].trim();
                vals[nT - 1] = last;
                hint = "Boxes 1 to " + first.length + " and " + nT + " filled in from the title (list given to Make Substack).";
            }
        }
        boolean cons = false;
        while (true) {
            GenericDialog gd = new GenericDialog(TITLE + " - original frames");
            gd.addMessage(selfLab ? "The slice labels of \"" + shortTitle + "\" only number its own images (t:1/" + nT
                    + " to t:" + nT + "/" + nT + ")." : "The original frame numbers could not be read from \"" + shortTitle + "\".");
            gd.addMessage("For each image of this stack (1 to " + nT + "), type its frame number in the original movie:");
            if (!hint.isEmpty()) gd.addMessage(hint, null, new Color(0, 100, 0));
            int ncol = nT > 16 ? 6 : 4;
            Panel pnl = new Panel(new GridLayout(0, ncol * 2, 4, 2));
            TextField[] tf = new TextField[nT];
            for (int i = 0; i < nT; i++) {
                pnl.add(new Label(String.valueOf(i + 1), Label.RIGHT));
                tf[i] = new TextField(vals[i], 5);
                pnl.add(tf[i]);
            }
            gd.addPanel(pnl);
            if (nT > 1) gd.addCheckbox("Consecutive frames from box 1 (1 if empty): the other boxes are not needed", cons);
            gd.showDialog();
            if (gd.wasCanceled()) return null;
            for (int i = 0; i < nT; i++) vals[i] = tf[i].getText().trim();
            if (nT > 1) cons = gd.getNextBoolean();
            int[] out = new int[nT];
            StringBuilder bad = new StringBuilder();
            if (cons) {
                int st0 = vals[0].matches("\\d+") ? Integer.parseInt(vals[0]) : 1;
                for (int i = 0; i < nT; i++) out[i] = st0 + i;
            } else
                for (int i = 0; i < nT; i++) {
                    if (vals[i].matches("\\d+")) out[i] = Integer.parseInt(vals[i]);
                    else bad.append(bad.length() > 0 ? ", " : "").append(i + 1);
                }
            if (bad.length() == 0) return out;
            IJ.showMessage(TITLE, "Type a frame number (a whole number) in box " + bad + ",\nor tick \"Consecutive frames\".");
        }
    }

    // ================================================================== frame numbers and times
    int stackIdx(int k) {
        if (tDim == 't') return (k - 1) * nC * nZ + 1;
        if (tDim == 'z') return (k - 1) * nC + 1;
        return 1;
    }

    static String firstLine(String s) {
        if (s == null) return "";
        int i = s.indexOf('\n');
        if (i >= 0) s = s.substring(0, i);
        return s.trim();
    }

    /** 0 ms, 1 s, 2 min, 3 h, -2 microseconds, -1 unknown. */
    static int unitIndex(String u) {
        u = u == null ? "" : u.trim().toLowerCase(Locale.US);
        if (u.equals("us") || u.equals("usec") || u.equals("µs") || u.equals("μs") || u.startsWith("microsec")) return -2;
        if (u.equals("ms") || u.equals("msec") || u.startsWith("millisec")) return 0;
        if (u.equals("s") || u.equals("sec") || u.equals("secs") || u.startsWith("second")) return 1;
        if (u.equals("min") || u.equals("mins") || u.startsWith("minute")) return 2;
        if (u.equals("h") || u.equals("hr") || u.equals("hrs") || u.startsWith("hour")) return 3;
        return -1;
    }

    static double unitFactor(String u) {
        if (u.equals("ms")) return 0.001;
        if (u.equals("min")) return 60;
        if (u.equals("h")) return 3600;
        return 1;
    }

    /** "0.87 s", "870 ms", "0.87" (unit defU) -> seconds; -1 if unreadable. */
    static double parseInterval(String s, int defU) {
        s = s.trim().toLowerCase(Locale.US);
        Matcher m = Pattern.compile("(\\d+(?:\\.\\d*)?|\\.\\d+)\\s*([a-zµμ]*)").matcher(s);
        if (!m.matches()) return -1;
        double v = Double.parseDouble(m.group(1));
        String u = m.group(2);
        int k = u.isEmpty() ? defU : unitIndex(u);
        if (k == -2) return v / 1e6;
        if (k == 0) return v / 1000;
        if (k == 1) return v;
        if (k == 2) return v * 60;
        if (k == 3) return v * 3600;
        return -1;
    }

    /** Original frame numbers from the slice labels with pattern p; empty when the pattern does not fit every label. */
    int[] tryPattern(String[] labs, int p, double ivS) {
        int n = labs.length;
        int[] v = new int[n];
        boolean own = true;
        for (int i = 0; i < n; i++) {
            String s = labs[i];
            if (s.isEmpty()) return new int[0];
            Matcher m;
            if (p == 1) {
                m = Pattern.compile("(?i).*(?:^|[^a-z0-9])t[:=](\\d+)/(\\d+).*").matcher(s);
                if (!m.matches()) return new int[0];
                v[i] = Integer.parseInt(m.group(1));
                if (v[i] != i + 1 || Integer.parseInt(m.group(2)) != n) own = false;
            } else if (p == 2) {
                m = Pattern.compile("(?i)\\s*(-?\\d+(?:\\.\\d+)?)\\s*(ms|s|sec|min|h)\\s*").matcher(s);
                if (!(ivS > 0) || !m.matches()) return new int[0];
                double tv = Double.parseDouble(m.group(1)) * unitFactor(m.group(2).toLowerCase(Locale.US));
                double fv = tv / ivS;
                if (Math.abs(fv - Math.round(fv)) > 0.26) return new int[0];
                v[i] = (int) Math.round(fv);
            } else if (p == 3) {
                m = Pattern.compile("(?i).*?(?:^|[^a-z0-9])(?:frame|timepoint|time|tp|t)[ _:=#-]{0,3}(\\d+)(?![\\d.]).*").matcher(s);
                if (!m.matches()) return new int[0];
                v[i] = Integer.parseInt(m.group(1));
            } else if (p == 4) {
                m = Pattern.compile("\\s*(\\d+)\\s*").matcher(s);
                if (!m.matches()) return new int[0];
                v[i] = Integer.parseInt(m.group(1));
            } else {
                s = s.replaceAll("\\.[A-Za-z][A-Za-z0-9]{1,4}$", "");
                m = Pattern.compile("(?:.*\\D)?(\\d+)\\D*").matcher(s);
                if (!m.matches()) return new int[0];
                try { v[i] = Integer.parseInt(m.group(1)); }
                catch (NumberFormatException e) { return new int[0]; }
            }
        }
        if (p == 1 && own && n > 1) { selfLab = true; return new int[0]; }
        if (p == 2) {
            boolean z0 = false;
            for (int x : v) if (x <= 0) z0 = true;
            if (z0) for (int i = 0; i < n; i++) v[i]++;
            for (int x : v) if (x < 1) return new int[0];
        }
        if (p >= 3) {
            boolean z0 = false;
            for (int x : v) if (x == 0) z0 = true;
            if (z0) for (int i = 0; i < n; i++) v[i]++;
        }
        if (p >= 3 && n > 1) {
            boolean seq = true, same = true;
            for (int i = 0; i < n; i++) if (v[i] != i + 1) seq = false;
            for (int i = 1; i < n; i++) if (v[i] != v[0]) same = false;
            if (seq || same) return new int[0];
        }
        return v;
    }

    /** "3,15,27,40-43,1-199-12" -> frame numbers; sets parseErr when it cannot be read. */
    int[] parseFrames(String s) {
        parseErr = "";
        s = s.replaceAll("[;\\s]+", ",").replaceAll(",+", ",").replaceAll("^,|,$", "");
        if (s.isEmpty()) { parseErr = "no frame number given"; return new int[0]; }
        List<Integer> out = new ArrayList<Integer>();
        for (String q : s.split(",")) {
            if (q.matches("\\d+")) out.add(Integer.parseInt(q));
            else if (q.matches("\\d+-\\d+(-\\d+)?")) {
                String[] r = q.split("-");
                int a = Integer.parseInt(r[0]), b = Integer.parseInt(r[1]), st = r.length > 2 ? Integer.parseInt(r[2]) : 1;
                if (st < 1) { parseErr = "the step of \"" + q + "\" must be 1 or more"; return new int[0]; }
                int m = Math.abs(b - a) / st + 1;
                for (int x = 0; x < m; x++) out.add(b >= a ? a + x * st : a - x * st);
            } else {
                parseErr = "\"" + q + "\" is not a frame number or a range (like 40-43)";
                return new int[0];
            }
        }
        int[] r = new int[out.size()];
        for (int i = 0; i < r.length; i++) r[i] = out.get(i);
        return r;
    }

    static String compactFrames(int[] a) {
        String s = compactList(a, false);
        return s.length() > 200 ? compactList(a, true) : s;
    }

    static String compactList(int[] a, boolean steps) {
        StringBuilder s = new StringBuilder();
        int i = 0, n = a.length;
        while (i < n) {
            int j = i;
            while (j + 1 < n && a[j + 1] == a[j] + 1) j++;
            String item;
            if (j - i >= 2) { item = a[i] + "-" + a[j]; i = j + 1; }
            else {
                j = i;
                int st = 0;
                if (steps && i + 1 < n) {
                    st = a[i + 1] - a[i];
                    if (st > 1) while (j + 1 < n && a[j + 1] - a[j] == st) j++;
                }
                if (st > 1 && j - i >= 5) { item = a[i] + "-" + a[j] + "-" + st; i = j + 1; }
                else { item = String.valueOf(a[i]); i++; }
            }
            if (s.length() > 0) s.append(',');
            s.append(item);
        }
        return s.toString();
    }

    /** Decimals that tell two consecutive frames apart in the chosen format. */
    static int autoDec(int f, double ivs) {
        double v = f == 1 ? ivs / 60 : f == 2 ? ivs / 3600 : f == 3 ? ivs * 1000 : ivs;
        int d = 0;
        while (d < 3 && v * Math.pow(10, d) < 0.9999) d++;
        return d;
    }

    static String fmtTime(double ts, int f, int d) {
        if (f <= 3) {
            double v = f == 1 ? ts / 60 : f == 2 ? ts / 3600 : f == 3 ? ts * 1000 : ts;
            String u = f == 1 ? " min" : f == 2 ? " h" : f == 3 ? " ms" : " s";
            String s = IJ.d2s(v, d);
            if (Double.parseDouble(s) == 0) s = IJ.d2s(0, d);
            return s + u;
        }
        double q = Math.pow(10, d);
        long n = Math.round(Math.abs(ts) * q);
        String sg = ts < 0 && n > 0 ? "-" : "";
        long sec = (long) Math.floor(n / q);
        long fra = n - Math.round(sec * q);
        String s;
        if (f == 4) {
            long m = sec / 60;
            s = pad2(m) + ":" + pad2(sec - 60 * m);
        } else {
            long hh = sec / 3600, m = (sec - 3600 * hh) / 60;
            s = pad2(hh) + ":" + pad2(m) + ":" + pad2(sec - 3600 * hh - 60 * m);
        }
        if (d > 0) s = s + "." + pad((int) fra, d);
        return sg + s;
    }

    static String pad2(long v) { return v < 10 ? "0" + v : String.valueOf(v); }

    static int decimalsOf(double v) {
        for (int d = 0; d < 6; d++) {
            double q = Math.pow(10, d);
            if (Math.abs(v - Math.round(v * q) / q) < 1e-9 * Math.max(1, Math.abs(v))) return d;
        }
        return 6;
    }

    static int indexIn(String[] a, String v) {
        for (int i = 0; i < a.length; i++) if (a[i].equals(v)) return i;
        return 0;
    }

    static String inList(String[] a, String v) {
        for (String s : a) if (s.equals(v)) return v;
        return a[0];
    }

    static double parseD(String s, double d) {
        try { return Double.parseDouble(s); } catch (Exception e) { return d; }
    }
}
