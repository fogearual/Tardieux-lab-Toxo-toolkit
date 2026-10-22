/*
 * Drift_Correction - Fiji / ImageJ 1.x plugin (Toxo Toolkit)
 *
 * Corrects the drift of a movie by following a FIXED object chosen by the user, then crops the movie
 * to the area present in every frame.
 *
 *  - Object selection: an outline (oval, rectangle, freehand...) or a single point. The outline is used
 *    as a MASK: only the pixels of the object are used to recognise it.
 *  - Detection: normalised cross-correlation (insensitive to photobleaching and background), always
 *    against the appearance of the object in the reference frame (no accumulated error), sub-pixel
 *    position; or thresholding (background subtraction, smoothing, automatic threshold x factor, area
 *    filter), the position being the centre of the segmented object.
 *  - Identity of the object: continuity first (the fixed object only moves with the drift, which is slow
 *    and smooth; its speed is estimated robustly). A similar object elsewhere is only accepted if the
 *    whole field confirms a stage jump; an object passing over the fixed object makes it "lost" instead
 *    of being followed. Lost frames are interpolated, or measured on the whole field when that is more
 *    reliable.
 *  - Two thresholds: score below "doubtful" = doubtful detection, below "lost" = object not found.
 *  - Calibration: the settings are tried first on frames picked at random, with a preview montage.
 *  - Modes: automatic / semi-automatic (asks for a click when a detection is doubtful) / manual (one
 *    click per frame or every N frames, refined on the object, skipped frames interpolated).
 *  - Final review: movie with the tracked outline, drift plot, correction by clicking, re-tracking
 *    from a frame.
 *  - Registration: per-frame crop (raw pixel values kept) or sub-pixel (bilinear / bicubic). The
 *    corrected movie only keeps the area common to all frames.
 *
 * Installation: copy Drift_Correction.jar into Fiji.app/plugins/Toxo Toolkit/ and restart Fiji.
 * Menu: Plugins > Toxo Toolkit > Drift Correction
 */

import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Macro;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.ImageCanvas;
import ij.gui.ImageWindow;
import ij.gui.NonBlockingGenericDialog;
import ij.gui.Overlay;
import ij.gui.Plot;
import ij.gui.PlotWindow;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.gui.Toolbar;
import ij.macro.Interpreter;
import ij.measure.Calibration;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.plugin.filter.BackgroundSubtracter;
import ij.plugin.filter.GaussianBlur;
import ij.process.AutoThresholder;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.FloatPolygon;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.EventQueue;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

public class Drift_Correction implements PlugIn {

    static final String TITLE = "Drift Correction";

    // Where each position comes from
    static final int NONE = 0, REF = 1, AUTO = 2, DOUBT = 3, VALIDATED = 4, CLICK = 5, CLICK_RAW = 6, INTERP = 7, LOST = 8,
            FIELD = 9;
    static final String[] SRC_NAME = {"none", "reference", "auto", "auto doubtful", "auto validated",
            "refined click", "raw click", "interpolated", "lost (interpolated)", "lost (whole field)"};
    static final Color ORANGE = new Color(255, 140, 0);
    static final Color[] SRC_COLOR = {Color.gray, Color.magenta, new Color(0, 230, 0), ORANGE, Color.yellow,
            Color.cyan, Color.cyan, new Color(175, 175, 175), new Color(230, 0, 0), new Color(255, 90, 200)};

    static final String[] MODES = {"Automatic", "Semi-automatic (asks when in doubt)",
            "Manual (click on every frame)"};
    static final int M_AUTO = 0, M_SEMI = 1, M_MANUAL = 2;
    static final String[] PLANES = {"Displayed Z plane", "Max projection in Z"};
    static final String[] REGISTRATION = {"Whole pixels (raw values)", "Sub-pixel bilinear",
            "Sub-pixel bicubic"};

    static final int SPARSE_LIMIT = 2500;   // above: coarse search on a sparse template, then fine search
    static final String[] DETECTIONS = {"Similarity (correlation)", "Thresholding (segmented object)"};
    static final int D_CORREL = 0, D_THRESHOLD = 1;
    static final String[] POLARITIES = {"Bright on dark background", "Dark on bright background"};
    static final String[] THR_METHODS = AutoThresholder.getMethods();
    static final int HISTORY = 25;
    static final double MIN_VISIBLE = 0.8;   // below: the object is considered partly hidden
    static final double ROBUST_K = 6;   // residuals > 6 robust SDs = pixels of another object
    static final String PREFS = "drift_correction.";

    static final String NOTES =
            "Radius: largest move of the object between 2 frames (widened automatically if needed).\n"
          + "Score < \"doubtful\": doubtful detection (asked in semi-auto); score < \"lost\": object not found.\n"
          + "Thresholding (as in Count and Colocalise): background, smoothing, auto threshold x factor,\n"
          + "min/max area; its score is the size similarity with the reference object (1 = same area).\n"
          + "Calibrate: try the settings on frames picked at random, with a preview, before tracking.";

    static final String HELP = "<html><h3>Drift Correction</h3>"
          + "<p><b>Fixed object</b>: choose an object that does not move relative to the coverslip (stuck "
          + "bead, debris, landmark), well contrasted and away from the borders. Go to a frame where it is "
          + "sharp, then outline it closely, without including anything that moves.</p>"
          + "<p><b>Detection</b>. <i>Similarity (correlation)</i>: the object is found by normalised "
          + "correlation with its appearance in the reference frame (insensitive to photobleaching); the score "
          + "is the similarity (1 = identical). <i>Thresholding</i>, as in Count and Colocalise: background "
          + "subtraction (rolling ball), smoothing, automatic threshold (method of your choice) multiplied by "
          + "a factor, then the objects whose area lies between the minimum and the maximum (in multiples of "
          + "the area of the reference object); the position is the centre of the object and the score the "
          + "size similarity. Thresholding copes better with an object that changes appearance (focus), "
          + "correlation with an object touching others. Background and smoothing apply to both methods.</p>"
          + "<p><b>Thresholds</b>: score at least \"doubtful\": reliable detection; between \"lost\" and "
          + "\"doubtful\": doubtful (semi-automatic mode asks you; in automatic mode a short run of doubtful "
          + "frames is interpolated); below \"lost\": object not found, interpolated position. Tracking "
          + "favours continuity: a similar object elsewhere is only taken if the whole image confirms a "
          + "stage jump.</p>"
          + "<p><b>Calibration</b>: before tracking, the settings are tried on N frames picked at random (one "
          + "per section of the movie). The montage shows for each the object found (green reliable, orange "
          + "doubtful, red not found), the second best candidate (cyan) and, with thresholding, the pixels "
          + "kept. The panel sums up the scores and suggests thresholds. \"Redraw\" applies the changed "
          + "settings, \"Accept\" starts the tracking with them.</p>"
          + "<p><b>Modes</b>: <i>automatic</i>, no intervention; <i>semi-automatic</i>: the plugin stops when "
          + "a detection is doubtful or the object lost, and you click on the object (\"Wait for the object "
          + "to return\" avoids being asked on every frame of a passage); <i>manual</i>: you click on the "
          + "object on every frame (or every N frames), each click being refined on the object when it is "
          + "well recognised.</p>"
          + "<p><b>Review</b>: before applying, the movie is shown with the tracked outline (green = "
          + "reliable, orange = doubtful, red / pink = object lost, cyan = click, grey = interpolated) and "
          + "the drift plot. A click on the object corrects a frame; \"Re-track from here\" runs the tracking "
          + "again from the displayed frame.</p>"
          + "<p><b>Registration</b>: \"Whole pixels\" crops each frame without interpolation (raw values "
          + "kept). Sub-pixel interpolates (slight smoothing). The corrected movie only keeps the area "
          + "present in every frame.</p>"
          + "<p>The \"Drift\" table gives, for each frame, the position of the object, the drift (px and "
          + "calibrated unit), the score, the visible fraction of the object and where each position comes "
          + "from. In a macro (automatic mode, no calibration): select the object, then "
          + "run(\"Drift Correction\", \"mode=Automatic doubtful=0.8 lost=0.5 ...\").</p></html>";

    // ---------------------------------------------------------------- image
    ImagePlus imp;
    ImageStack stack;
    int W, H, nC, nZ, nT;   // nT: number of time points (frames, or slices when the image has no frames)
    boolean timeIsZ;

    // ---------------------------------------------------------------- settings
    int mode = M_SEMI, trackC = 1, trackZ = 1, radius = 20, boxSize = 31, refineR = 4, step = 1, registration = 0;
    double sigma = 1.0;
    boolean zProj = false, review = true;
    int detMethod = D_CORREL, nCalib = 9;
    double doubtful = 0.80, lost = 0.50, bgRadius = 0, thrFactor = 1.0, areaMin = 0.5, areaMax = 2.0;
    String thrMethod = "Otsu";
    boolean dark = false, calibrateFirst = true;
    double refArea = Double.NaN, lastLevel = Double.NaN;   // thresholding: area of the reference object, last threshold
    String modelError, lastCalSummary;
    CalRes[] lastCalRes;

    // ---------------------------------------------------------------- model
    Roi userRoi, shape;
    int refT, bx, by, bw, bh;
    double offX, offY, shapeDx, shapeDy;
    int[] mi, mj, smi, smj;
    double[] tz, stz;
    double tss, stss;
    boolean useSparse;

    // ---------------------------------------------------------------- results
    double[] px, py, score;
    int[] src;
    String[] why;
    double[] visibleFrac;
    boolean[] isJump, isReacq;
    final ArrayDeque<Double> history = new ArrayDeque<Double>();   // scores of the recent reliable detections
    final ArrayDeque<Double> steps = new ArrayDeque<Double>();     // distances to the prediction (px)
    final ArrayDeque<Double> clickBiasX = new ArrayDeque<Double>(), clickBiasY = new ArrayDeque<Double>();
    boolean stopAsking, waitReturn;
    String trackingText = TRACKING_TEXT, trackingCancel = "Cancel";   // panel shown while tracking
    Session session;
    PlotWindow plotWin;
    int progressDone;

    // cache of the last frame read (tracking image, not smoothed)
    int cachedT = -1;
    float[] cached;

    // =====================================================================================
    public void run(String arg) {
        if (EventQueue.isDispatchThread()) {   // never block the user interface
            final String a = arg;
            new Thread(new Runnable() { public void run() { Drift_Correction.this.run(a); } }, TITLE).start();
            return;
        }
        imp = IJ.getImage();
        stack = imp.getStack();
        W = imp.getWidth();
        H = imp.getHeight();
        nC = imp.getNChannels();
        int nSl = imp.getNSlices(), nFr = imp.getNFrames();
        if (nFr > 1) { nT = nFr; nZ = nSl; timeIsZ = false; }
        else if (nSl > 1) { nT = nSl; nZ = 1; timeIsZ = true; }
        else { IJ.error(TITLE, "A movie is needed: this image has only one time point."); return; }
        boolean visible = imp.getWindow() != null && !Interpreter.isBatchMode();

        Roi roi = showDialog();
        if (roi == null) return;
        if (!visible && mode != M_AUTO) {
            IJ.error(TITLE, "The semi-automatic and manual modes need the movie to be displayed.");
            return;
        }
        if (!visible) review = false;
        refT = currentTime();
        trackZ = timeIsZ ? 1 : imp.getZ();
        userRoi = (Roi) roi.clone();
        boolean macroRun = Macro.getOptions() != null;
        if (!buildModel(false)) return;
        if (timeIsZ)
            IJ.log(TITLE + ": the image has no \"frames\" dimension, its slices are taken as time points.");

        if (visible && calibrateFirst && !macroRun && !calibrationPhase()) {
            IJ.showStatus(TITLE + ": cancelled");
            return;
        }

        px = new double[nT + 1];
        py = new double[nT + 1];
        score = new double[nT + 1];
        src = new int[nT + 1];
        why = new String[nT + 1];
        visibleFrac = new double[nT + 1];
        isJump = new boolean[nT + 1];
        isReacq = new boolean[nT + 1];
        Arrays.fill(visibleFrac, Double.NaN);
        Arrays.fill(score, Double.NaN);
        IJ.resetEscape();

        boolean ok = false;
        long t0 = System.currentTimeMillis();
        try {
            if (visible && (mode != M_AUTO || review)) session = new Session();
            if (mode == M_MANUAL) ok = manualPhase();
            else ok = autoPhase(mode == M_SEMI);
            if (ok && !interpolate()) {
                IJ.error(TITLE, "No position of the object was obtained.");
                ok = false;
            }
            if (ok && review) ok = reviewPhase();
        } finally {
            if (session != null) {
                session.close();
                session = null;
            }
            IJ.showProgress(1.0);
            IJ.showStatus("");
        }
        if (!ok) {
            IJ.showStatus(TITLE + ": cancelled");
            return;
        }
        ImagePlus res = apply();
        if (res == null) return;
        report(res, (System.currentTimeMillis() - t0) / 1000.0);
        lastResult = res;
        res.show();
        if (res.isHyperStack() || res.getStackSize() > 1) {
            if (timeIsZ) res.setPosition(imp.getC(), refT, 1);
            else res.setPosition(imp.getC(), Math.min(trackZ, res.getNSlices()), refT);
        }
    }

    // ===================================================================== dialogue
    Roi showDialog() {
        boolean macro = Macro.getOptions() != null;
        if (!macro) loadPrefs();
        trackC = imp.getC();
        if (!macro && imp.getWindow() != null) {
            int tool = Toolbar.getToolId();
            boolean areaTool = tool == Toolbar.RECTANGLE || tool == Toolbar.OVAL || tool == Toolbar.POLYGON
                    || tool == Toolbar.FREEROI || tool == Toolbar.POINT || tool == Toolbar.WAND;
            if (!areaTool) IJ.setTool(Toolbar.OVAL);
        }
        String[] chans = new String[nC];
        for (int c = 0; c < nC; c++) chans[c] = "" + (c + 1);
        while (true) {
            // in a macro (options given) a plain dialog is enough and also works "headless"
            GenericDialog gd = macro ? new GenericDialog(TITLE) : new NonBlockingGenericDialog(TITLE);
            gd.addMessage("1. Go to a frame where the fixed object is sharp (reference frame).\n"
                    + "2. Outline the object (oval, rectangle, freehand...) or click on it (point tool).\n"
                    + "3. Set the options, then OK.");
            gd.addChoice("Mode", MODES, MODES[mode]);
            gd.addChoice("Detection", DETECTIONS, DETECTIONS[detMethod]);
            gd.addToSameRow();
            gd.addChoice("Object", POLARITIES, POLARITIES[dark ? 1 : 0]);
            if (nC > 1) gd.addChoice("Channel", chans, chans[Math.max(1, Math.min(trackC, nC)) - 1]);
            if (nZ > 1) {
                if (nC > 1) gd.addToSameRow();
                gd.addChoice("Plane", PLANES, PLANES[zProj ? 1 : 0]);
            }
            gd.addNumericField("Radius", radius, 0, 5, "px (search)");
            gd.addNumericField("Doubtful if score <", doubtful, 2, 5, "");
            gd.addToSameRow();
            gd.addNumericField("Lost if score <", lost, 2, 5, "");
            gd.addNumericField("Smoothing (sigma)", sigma, 1, 5, "px");
            gd.addToSameRow();
            gd.addNumericField("Background (rolling ball radius)", bgRadius, 0, 5, "px, 0 = none");
            gd.addChoice("Thresholding method", THR_METHODS, thrMethod);
            gd.addToSameRow();
            gd.addNumericField("Threshold factor", thrFactor, 2, 5, "");
            gd.addNumericField("Minimum area", areaMin, 2, 5, "x object");
            gd.addToSameRow();
            gd.addNumericField("Maximum area", areaMax, 2, 5, "x object");
            gd.addNumericField("Box size (point selection)", boxSize, 0, 5, "px");
            gd.addToSameRow();
            gd.addNumericField("Click refinement", refineR, 0, 5, "px");
            gd.addNumericField("Manual step", step, 0, 5, "frame(s)");
            gd.addToSameRow();
            gd.addChoice("Registration", REGISTRATION, REGISTRATION[registration]);
            gd.addCheckbox("Calibrate first on frames picked at random", calibrateFirst);
            gd.addToSameRow();
            gd.addNumericField("Number of frames", nCalib, 0, 4, "");
            gd.addCheckbox("Review the tracking before applying", review);
            gd.addMessage(NOTES, null, Color.darkGray);
            gd.addHelp(HELP);
            gd.showDialog();
            if (gd.wasCanceled()) return null;
            mode = gd.getNextChoiceIndex();
            detMethod = gd.getNextChoiceIndex();
            dark = gd.getNextChoiceIndex() == 1;
            if (nC > 1) trackC = gd.getNextChoiceIndex() + 1;
            if (nZ > 1) zProj = gd.getNextChoiceIndex() == 1;
            radius = (int) Math.round(gd.getNextNumber());
            doubtful = gd.getNextNumber();
            lost = gd.getNextNumber();
            sigma = gd.getNextNumber();
            bgRadius = gd.getNextNumber();
            thrMethod = THR_METHODS[gd.getNextChoiceIndex()];
            thrFactor = gd.getNextNumber();
            areaMin = gd.getNextNumber();
            areaMax = gd.getNextNumber();
            boxSize = (int) Math.round(gd.getNextNumber());
            refineR = (int) Math.round(gd.getNextNumber());
            step = (int) Math.round(gd.getNextNumber());
            registration = gd.getNextChoiceIndex();
            calibrateFirst = gd.getNextBoolean();
            nCalib = (int) Math.round(gd.getNextNumber());
            review = gd.getNextBoolean();
            if (gd.invalidNumber()) {
                IJ.error(TITLE, "One of the numbers is not valid.");
                if (macro) return null;
                continue;
            }
            sanitize();
            Roi roi = imp.getRoi();
            if (usable(roi)) {
                if (!macro) savePrefs();
                return roi;
            }
            IJ.error(TITLE, "No object selected on \"" + imp.getTitle() + "\":\n"
                    + "outline the fixed object, or click on it with the point tool, then OK.");
            if (macro || imp.getWindow() == null) return null;
        }
    }

    static boolean usable(Roi r) {
        if (r == null) return false;
        if (r.getType() == Roi.POINT) return true;
        if (!r.isArea()) return false;
        Rectangle b = r.getBounds();
        return b.width >= 3 && b.height >= 3;
    }

    void sanitize() {
        radius = clamp(radius, 1, Math.max(W, H));
        doubtful = Math.max(0, Math.min(1, doubtful));
        lost = Math.max(0, Math.min(doubtful, lost));
        sigma = Math.max(0, Math.min(10, sigma));
        bgRadius = Math.max(0, Math.min(500, bgRadius));
        thrFactor = Math.max(0.05, Math.min(20, thrFactor));
        areaMin = Math.max(0, areaMin);
        areaMax = Math.max(Math.max(areaMin, 0.01), areaMax);
        boxSize = clamp(boxSize, 5, Math.min(W, H));
        refineR = clamp(refineR, 0, 100);
        step = clamp(step, 1, nT);
        trackC = clamp(trackC, 1, nC);
        nCalib = clamp(nCalib, 1, 100);
    }

    void loadPrefs() {
        mode = clamp((int) Prefs.get(PREFS + "mode", M_SEMI), 0, 2);
        radius = (int) Prefs.get(PREFS + "radius", 20);
        sigma = Prefs.get(PREFS + "sigma", 1.0);
        boxSize = (int) Prefs.get(PREFS + "box", 31);
        refineR = (int) Prefs.get(PREFS + "refine", 4);
        step = (int) Prefs.get(PREFS + "step", 1);
        registration = clamp((int) Prefs.get(PREFS + "registration", 0), 0, 2);
        review = Prefs.get(PREFS + "review", true);
        zProj = Prefs.get(PREFS + "zproj", false);
        detMethod = clamp((int) Prefs.get(PREFS + "det", D_CORREL), 0, 1);
        dark = Prefs.get(PREFS + "dark", false);
        doubtful = Prefs.get(PREFS + "doubtful", 0.80);
        lost = Prefs.get(PREFS + "lost", 0.50);
        bgRadius = Prefs.get(PREFS + "bg", 0);
        thrMethod = Prefs.get(PREFS + "thr", "Otsu");
        if (!Arrays.asList(THR_METHODS).contains(thrMethod)) thrMethod = "Otsu";
        thrFactor = Prefs.get(PREFS + "facteur", 1.0);
        areaMin = Prefs.get(PREFS + "amin", 0.5);
        areaMax = Prefs.get(PREFS + "amax", 2.0);
        calibrateFirst = Prefs.get(PREFS + "calibrateFirst", true);
        nCalib = (int) Prefs.get(PREFS + "ncalib", 9);
    }

    void savePrefs() {
        Prefs.set(PREFS + "mode", mode);
        Prefs.set(PREFS + "radius", radius);
        Prefs.set(PREFS + "sigma", sigma);
        Prefs.set(PREFS + "box", boxSize);
        Prefs.set(PREFS + "refine", refineR);
        Prefs.set(PREFS + "step", step);
        Prefs.set(PREFS + "registration", registration);
        Prefs.set(PREFS + "review", review);
        Prefs.set(PREFS + "zproj", zProj);
        Prefs.set(PREFS + "det", detMethod);
        Prefs.set(PREFS + "dark", dark);
        Prefs.set(PREFS + "doubtful", doubtful);
        Prefs.set(PREFS + "lost", lost);
        Prefs.set(PREFS + "bg", bgRadius);
        Prefs.set(PREFS + "thr", thrMethod);
        Prefs.set(PREFS + "facteur", thrFactor);
        Prefs.set(PREFS + "amin", areaMin);
        Prefs.set(PREFS + "amax", areaMax);
        Prefs.set(PREFS + "calibrateFirst", calibrateFirst);
        Prefs.set(PREFS + "ncalib", nCalib);
    }

    // ===================================================================== modele
    /** Model of the object (+ reference object for thresholding). quiet: errors go to modelError, no window. */
    boolean buildModel(boolean quiet) {
        levelCache.clear();
        return buildTemplate(userRoi, quiet) && (detMethod != D_THRESHOLD || initBlobRef(quiet));
    }

    boolean fail(String msg, boolean quiet) {
        modelError = msg;
        if (!quiet) IJ.error(TITLE, msg);
        return false;
    }

    boolean buildTemplate(Roi roi, boolean quiet) {
        Rectangle rb;
        ImageProcessor mask = null;
        boolean point = roi.getType() == Roi.POINT;
        if (point) {
            FloatPolygon fp = roi.getFloatPolygon();
            int cx = (int) Math.floor(fp.xpoints[0]), cy = (int) Math.floor(fp.ypoints[0]);
            rb = new Rectangle(cx - boxSize / 2, cy - boxSize / 2, boxSize, boxSize);
        } else {
            rb = roi.getBounds();
            mask = roi.getMask();
            if (mask != null && (mask.getWidth() != rb.width || mask.getHeight() != rb.height)) mask = null;
        }
        Rectangle b = rb.intersection(new Rectangle(0, 0, W, H));
        if (b.width < 3 || b.height < 3) {
            return fail("The selection is outside the image.", quiet);
        }
        bx = b.x; by = b.y; bw = b.width; bh = b.height;
        shape = point ? new Roi(bx, by, bw, bh) : (Roi) roi.clone();
        shapeDx = shape.getXBase() - bx;
        shapeDy = shape.getYBase() - by;
        offX = bw / 2.0;
        offY = bh / 2.0;

        float[] tpl = region(refT, bx, by, bw, bh);
        int n = 0;
        int[] ii = new int[bw * bh], jj = new int[bw * bh];
        for (int j = 0; j < bh; j++)
            for (int i = 0; i < bw; i++)
                if (mask == null || mask.get(i + bx - rb.x, j + by - rb.y) != 0) { ii[n] = i; jj[n] = j; n++; }
        if (n < 9) {
            return fail("The selection is too small (fewer than 9 pixels).", quiet);
        }
        mi = Arrays.copyOf(ii, n);
        mj = Arrays.copyOf(jj, n);
        objectCenter(tpl);
        tz = new double[n];
        tss = zeroMean(tpl, mi, mj, tz);
        double energy = 0;
        for (int k = 0; k < n; k++) { double v = tpl[mj[k] * bw + mi[k]]; energy += v * v; }
        if (!(tss > 1e-9 * energy) || tss <= 0) {
            return fail("The selected area is uniform: choose a contrasted object.", quiet);
        }
        int s = n > SPARSE_LIMIT ? Math.max(2, (int) Math.round(Math.sqrt(n / 1200.0))) : 1;
        useSparse = s > 1;
        if (useSparse) {
            int m = 0;
            for (int k = 0; k < n; k++) if (mi[k] % s == 0 && mj[k] % s == 0) { ii[m] = mi[k]; jj[m] = mj[k]; m++; }
            smi = Arrays.copyOf(ii, m);
            smj = Arrays.copyOf(jj, m);
            stz = new double[m];
            stss = zeroMean(tpl, smi, smj, stz);
            if (!(stss > 0)) useSparse = false;
        }
        if (!quiet && (bw > W / 2 || bh > H / 2))
            IJ.log(TITLE + ": the outlined area is large (" + bw + "x" + bh
                    + " px); outlining only the fixed object makes tracking faster and safer.");
        return true;
    }

    /**
     * Centre of the object in the template (where the user will click): the most contrasted point (bright or
     * dark) of the mask, refined by a local centroid (5 x 5).
     */
    void objectCenter(float[] tpl) {
        int n = mi.length;
        double[] v = new double[n];
        for (int k = 0; k < n; k++) v[k] = tpl[mj[k] * bw + mi[k]];
        double[] sv = v.clone();
        Arrays.sort(sv);
        double med = sv[n / 2];
        int best = 0;
        for (int k = 1; k < n; k++) if (Math.abs(v[k] - med) > Math.abs(v[best] - med)) best = k;
        boolean[] inMask = new boolean[bw * bh];
        for (int k = 0; k < n; k++) inMask[mj[k] * bw + mi[k]] = true;
        double sw = 0, sx = 0, sy = 0;
        for (int dy = -2; dy <= 2; dy++)
            for (int dx = -2; dx <= 2; dx++) {
                int x = mi[best] + dx, y = mj[best] + dy;
                if (x < 0 || y < 0 || x >= bw || y >= bh || !inMask[y * bw + x]) continue;
                double w = (tpl[y * bw + x] - med) * (tpl[y * bw + x] - med);
                sw += w; sx += w * (x + 0.5); sy += w * (y + 0.5);
            }
        if (sw > 0) { offX = sx / sw; offY = sy / sw; }
    }

    double zeroMean(float[] tpl, int[] ii, int[] jj, double[] out) {
        int n = ii.length;
        double mean = 0;
        for (int k = 0; k < n; k++) mean += tpl[jj[k] * bw + ii[k]];
        mean /= n;
        double ss = 0;
        for (int k = 0; k < n; k++) {
            out[k] = tpl[jj[k] * bw + ii[k]] - mean;
            ss += out[k] * out[k];
        }
        return ss;
    }

    // ===================================================================== image access
    int index(int c, int z, int t) {
        return timeIsZ ? imp.getStackIndex(c, t, 1) : imp.getStackIndex(c, z, t);
    }

    int currentTime() {
        int[] p = imp.convertIndexToPosition(imp.getCurrentSlice());
        return timeIsZ ? p[1] : p[2];
    }

    void show(int t) {
        if (timeIsZ) imp.setPosition(imp.getC(), t, imp.getT());
        else imp.setPosition(imp.getC(), imp.getZ(), t);
    }

    /** Tracking image (chosen channel, Z plane or max projection) of time point t, as float, not smoothed. */
    float[] full(int t) {
        if (t == cachedT) return cached;
        float[] out;
        if (zProj && nZ > 1) {
            out = toFloat(stack.getProcessor(index(trackC, 1, t)));
            for (int z = 2; z <= nZ; z++) {
                float[] f = toFloat(stack.getProcessor(index(trackC, z, t)));
                for (int k = 0; k < out.length; k++) if (f[k] > out[k]) out[k] = f[k];
            }
        } else
            out = toFloat(stack.getProcessor(index(trackC, trackZ, t)));
        cachedT = t;
        cached = out;
        return out;
    }

    static float[] toFloat(ImageProcessor ip) {
        if (ip instanceof FloatProcessor) {
            float[] f = ((float[]) ip.getPixels()).clone();
            for (int k = 0; k < f.length; k++) if (Float.isNaN(f[k]) || Float.isInfinite(f[k])) f[k] = 0f;
            return f;
        }
        return (float[]) ip.convertToFloat().getPixels();
    }

    /**
     * Region (x0, y0, w, h) of the tracking image of time point t, pre-processed as for detection: background
     * subtraction (rolling ball, if radius > 0), then Gaussian smoothing (sigma). Must lie inside the image.
     */
    float[] region(int t, int x0, int y0, int w, int h) {
        float[] f = full(t);
        float[] r = new float[w * h];
        if (sigma <= 0 && bgRadius <= 0) {
            for (int j = 0; j < h; j++) System.arraycopy(f, (y0 + j) * W + x0, r, j * w, w);
            return r;
        }
        int m = sigma > 0 ? (int) Math.ceil(3 * sigma) + 1 : 0;
        if (bgRadius > 0) m = Math.max(m, (int) Math.ceil(2 * bgRadius) + 1);
        int ex0 = Math.max(0, x0 - m), ey0 = Math.max(0, y0 - m);
        int ex1 = Math.min(W, x0 + w + m), ey1 = Math.min(H, y0 + h + m);
        int ew = ex1 - ex0, eh = ey1 - ey0;
        float[] e = new float[ew * eh];
        for (int j = 0; j < eh; j++) System.arraycopy(f, (ey0 + j) * W + ex0, e, j * ew, ew);
        FloatProcessor fp = new FloatProcessor(ew, eh, e, null);
        if (bgRadius > 0)
            new BackgroundSubtracter().rollingBallBackground(fp, bgRadius, false, dark, false, false, true);
        if (sigma > 0) {
            GaussianBlur gb = new GaussianBlur();
            gb.showProgress(false);
            gb.blurGaussian(fp, sigma, sigma, 0.002);
        }
        e = (float[]) fp.getPixels();
        for (int j = 0; j < h; j++) System.arraycopy(e, (y0 - ey0 + j) * ew + (x0 - ex0), r, j * w, w);
        return r;
    }

    // ===================================================================== correlation
    final class Region {
        final int x0, y0, w, h;
        final float[] d;
        final int[] offs, soffs;

        Region(int t, int x0, int y0, int w, int h) {
            this.x0 = x0; this.y0 = y0; this.w = w; this.h = h;
            d = region(t, x0, y0, w, h);
            offs = offsets(mi, mj, w);
            soffs = useSparse ? offsets(smi, smj, w) : null;
        }
    }

    static int[] offsets(int[] ii, int[] jj, int w) {
        int[] o = new int[ii.length];
        for (int k = 0; k < o.length; k++) o[k] = jj[k] * w + ii[k];
        return o;
    }

    /** Normalised (masked) correlation of the template placed with its corner at (x, y). */
    double ncc(Region r, int x, int y, boolean sparse) {
        final int[] o = sparse ? r.soffs : r.offs;
        final double[] t = sparse ? stz : tz;
        final double ts = sparse ? stss : tss;
        final float[] d = r.d;
        final int n = o.length;
        final int base = (y - r.y0) * r.w + (x - r.x0);
        double s1 = 0, s2 = 0, st = 0;
        for (int k = 0; k < n; k++) {
            double v = d[base + o[k]];
            s1 += v;
            s2 += v * v;
            st += t[k] * v;
        }
        double var = s2 - s1 * s1 / n;
        if (!(var > 1e-12 * s2) || var <= 0) return 0;
        return st / Math.sqrt(ts * var);
    }

    // ------------------------------------------------------------------ candidats
    static final class Cand {
        double x, y, score, std, dist;   // score: robust NCC (tolerates partial occlusion); std: classic NCC
        double kept = 1;                 // fraction of the object's pixels that match it (1 = fully visible)
        int ix, iy;
        boolean border, raw;
    }

    /** Correlation (full template) over a window of positions, with a cache; used to climb to a peak. */
    final class Scorer {
        final Region reg;
        final int x0, x1, y0, y1, nx;
        final double[] map;   // optional: map already computed with the full template
        final HashMap<Integer, Double> cache = new HashMap<Integer, Double>();

        Scorer(Region reg, int x0, int x1, int y0, int y1, double[] map) {
            this.reg = reg; this.x0 = x0; this.x1 = x1; this.y0 = y0; this.y1 = y1;
            this.nx = x1 - x0 + 1;
            this.map = map;
        }

        double get(int x, int y) {
            if (x < x0 || x > x1 || y < y0 || y > y1) return Double.NaN;
            int key = (y - y0) * nx + (x - x0);
            if (map != null) return map[key];
            Double v = cache.get(key);
            if (v == null) { v = ncc(reg, x, y, false); cache.put(key, v); }
            return v;
        }

        int[] climb(int x, int y) {
            double cur = get(x, y);
            for (int it = 0; it < 500; it++) {
                int bxx = x, byy = y;
                double best = cur;
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0) continue;
                        double v = get(x + dx, y + dy);
                        if (v > best) { best = v; bxx = x + dx; byy = y + dy; }
                    }
                if (bxx == x && byy == y) break;
                x = bxx; y = byy; cur = best;
            }
            return new int[]{x, y};
        }
    }

    static double parabola(double a, double b, double c) {
        if (Double.isNaN(a) || Double.isNaN(c)) return 0;
        double den = a - 2 * b + c;
        if (den >= 0) return 0;
        double d = 0.5 * (a - c) / den;
        return Math.max(-0.5, Math.min(0.5, d));
    }

    /** NCC computed only on the template pixels flagged in keep. */
    double nccSubset(Region r, int x, int y, boolean[] keep) {
        final int[] o = r.offs;
        final int base = (y - r.y0) * r.w + (x - r.x0);
        double s1 = 0, s2 = 0, st = 0, sT = 0, sTT = 0;
        int m = 0;
        for (int k = 0; k < o.length; k++) {
            if (!keep[k]) continue;
            double v = r.d[base + o[k]], tt = tz[k];
            s1 += v; s2 += v * v; st += tt * v; sT += tt; sTT += tt * tt; m++;
        }
        if (m < 3) return 0;
        double cov = st - sT * s1 / m, varT = sTT - sT * sT / m, varI = s2 - s1 * s1 / m;
        if (!(varT > 0) || !(varI > 1e-12 * s2)) return 0;
        return cov / Math.sqrt(varT * varI);
    }

    /**
     * Robust NCC at (x, y): fits I = a.T + b, drops the pixels whose residual exceeds ROBUST_K robust SDs
     * (what does not look like the object: a parasite passing over it...), then computes the NCC on the
     * remaining pixels. The mask of the kept pixels is written into keep.
     */
    double robustNcc(Region r, int x, int y, boolean[] keep) {
        final int[] o = r.offs;
        final int n = o.length, base = (y - r.y0) * r.w + (x - r.x0);
        double[] v = new double[n];
        double sI = 0, sTI = 0;
        for (int k = 0; k < n; k++) { v[k] = r.d[base + o[k]]; sI += v[k]; sTI += tz[k] * v[k]; }
        double mean = sI / n, a = sTI / tss;
        if (!(a > 0)) { Arrays.fill(keep, true); return nccSubset(r, x, y, keep); }
        double[] res = new double[n];
        for (int k = 0; k < n; k++) res[k] = Math.abs(v[k] - mean - a * tz[k]);
        double[] s = res.clone();
        Arrays.sort(s);
        double lim = ROBUST_K * 1.4826 * s[n / 2];   // >= median: at least half of the pixels are always kept
        for (int k = 0; k < n; k++) keep[k] = res[k] <= lim;
        return nccSubset(r, x, y, keep);
    }

    /** Refines a peak: classic NCC, then NCC restricted to the reliable pixels (robust to partial occlusion). */
    Cand refinePeak(Region reg, Scorer sc, int sx, int sy) {
        int[] p = sc.climb(sx, sy);
        boolean[] keep = new boolean[mi.length];
        double s = robustNcc(reg, p[0], p[1], keep);
        for (int it = 0; it < 3; it++) {
            int[] q = climbSubset(reg, sc, p[0], p[1], keep);
            if (q[0] == p[0] && q[1] == p[1]) break;
            p = q;
            s = robustNcc(reg, p[0], p[1], keep);
        }
        Cand c = new Cand();
        c.ix = p[0];
        c.iy = p[1];
        c.score = s;
        c.std = ncc(reg, p[0], p[1], false);
        int nk = 0;
        for (boolean b : keep) if (b) nk++;
        c.kept = nk / (double) keep.length;
        if (c.kept >= 0.97) {                       // clean object: sub-pixel position on all of its pixels
            p = sc.climb(p[0], p[1]);
            c.ix = p[0];
            c.iy = p[1];
            Arrays.fill(keep, true);
            s = c.std = ncc(reg, p[0], p[1], false);
        }
        c.x = p[0] + parabola(subsetAt(reg, sc, p[0] - 1, p[1], keep), s, subsetAt(reg, sc, p[0] + 1, p[1], keep));
        c.y = p[1] + parabola(subsetAt(reg, sc, p[0], p[1] - 1, keep), s, subsetAt(reg, sc, p[0], p[1] + 1, keep));
        return c;
    }

    double subsetAt(Region reg, Scorer sc, int x, int y, boolean[] keep) {
        if (x < sc.x0 || x > sc.x1 || y < sc.y0 || y > sc.y1) return Double.NaN;
        return nccSubset(reg, x, y, keep);
    }

    int[] climbSubset(Region reg, Scorer sc, int x, int y, boolean[] keep) {
        double cur = subsetAt(reg, sc, x, y, keep);
        for (int it = 0; it < 20; it++) {
            int bxx = x, byy = y;
            double best = cur;
            for (int dy = -1; dy <= 1; dy++)
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dy == 0) continue;
                    double v = subsetAt(reg, sc, x + dx, y + dy, keep);
                    if (v > best) { best = v; bxx = x + dx; byy = y + dy; }
                }
            if (bxx == x && byy == y) break;
            x = bxx; y = byy; cur = best;
        }
        return new int[]{x, y};
    }

    /**
     * Correlation peaks (candidates) at time point t, in the window of radius r around the predicted position
     * (template corner). Sorted by decreasing score. Empty list if the window is outside the image.
     */
    List<Cand> candidates(int t, double predX, double predY, int r) {
        List<Cand> out = new ArrayList<Cand>();
        int cx = (int) Math.round(predX), cy = (int) Math.round(predY);
        final int xmax = W - bw, ymax = H - bh;
        // window widened by 1 px so that the sub-pixel position of a peak on its edge can be computed
        final int x0 = Math.max(0, cx - r - 1), x1 = Math.min(xmax, cx + r + 1);
        final int y0 = Math.max(0, cy - r - 1), y1 = Math.min(ymax, cy + r + 1);
        if (x0 > x1 || y0 > y1) return out;
        final Region reg = new Region(t, x0, y0, x1 - x0 + bw, y1 - y0 + bh);
        final int nx = x1 - x0 + 1, ny = y1 - y0 + 1;
        final boolean sparse = useSparse && nx * ny > 100;
        final double[] map = new double[nx * ny];
        IntStream.range(0, ny).parallel().forEach(j -> {
            for (int i = 0; i < nx; i++) map[j * nx + i] = ncc(reg, x0 + i, y0 + j, sparse);
        });
        Scorer sc = new Scorer(reg, x0, x1, y0, y1, sparse ? null : map);
        List<Integer> seeds = new ArrayList<Integer>();
        for (int k : topLocalMaxima(map, nx, ny, 6)) seeds.add(k);
        int sx = Math.max(x0, Math.min(x1, cx)), sy = Math.max(y0, Math.min(y1, cy));
        seeds.add((sy - y0) * nx + (sx - x0));               // the peak closest to the prediction
        for (int k : seeds) {
            Cand c = refinePeak(reg, sc, x0 + k % nx, y0 + k / nx);
            c.border = (c.ix <= cx - r && cx - r > 0) || (c.ix >= cx + r && cx + r < xmax)
                    || (c.iy <= cy - r && cy - r > 0) || (c.iy >= cy + r && cy + r < ymax);
            c.dist = Math.hypot(c.x - predX, c.y - predY);
            boolean dup = false;
            for (int q = 0; q < out.size(); q++) {
                Cand o = out.get(q);
                if (Math.hypot(o.x - c.x, o.y - c.y) < 1.5) {
                    dup = true;
                    if (c.score > o.score) out.set(q, c);
                    break;
                }
            }
            if (!dup) out.add(c);
        }
        out.sort((a, b) -> Double.compare(b.score, a.score));
        return out;
    }

    static int[] topLocalMaxima(double[] map, int nx, int ny, int k) {
        List<Integer> peaks = new ArrayList<Integer>();
        for (int j = 0; j < ny; j++)
            for (int i = 0; i < nx; i++) {
                double v = map[j * nx + i];
                boolean max = true;
                for (int dy = -1; dy <= 1 && max; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int x = i + dx, y = j + dy;
                        if ((dx == 0 && dy == 0) || x < 0 || y < 0 || x >= nx || y >= ny) continue;
                        if (map[y * nx + x] > v) { max = false; break; }
                    }
                if (max) peaks.add(j * nx + i);
            }
        if (peaks.isEmpty()) peaks.add(0);
        final double[] m = map;
        peaks.sort((a, b) -> Double.compare(m[b], m[a]));
        int n = Math.min(k, peaks.size());
        int[] out = new int[n];
        for (int q = 0; q < n; q++) out[q] = peaks.get(q);
        return out;
    }

    // ===================================================================== seuillage
    static final class Blob {
        double x, y, area;
        boolean border;
    }

    final HashMap<Integer, Double> levelCache = new HashMap<Integer, Double>();

    /**
     * Threshold of frame t, computed as in Count and Colocalise on the whole pre-processed image (background,
     * smoothing): automatic threshold (chosen method), then the factor applied relative to the median. On the
     * whole image, an object passing close to the fixed object hardly changes the threshold.
     */
    double frameLevel(int t) {
        Double c = levelCache.get(t);
        if (c != null) return c;
        float[] d = region(t, 0, 0, W, H);
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (int k = 0; k < d.length; k++) {
            double v = dark ? -d[k] : d[k];
            lo = Math.min(lo, v);
            hi = Math.max(hi, v);
        }
        double level = Double.NaN;
        if (hi > lo) {
            int[] hist = new int[256];
            double bin = (hi - lo) / 256.0;
            for (int k = 0; k < d.length; k++) hist[Math.min(255, (int) (((dark ? -d[k] : d[k]) - lo) / bin))]++;
            int tb;
            try {
                tb = new AutoThresholder().getThreshold(AutoThresholder.Method.valueOf(thrMethod), hist);
            } catch (Exception e) {
                tb = new AutoThresholder().getThreshold(AutoThresholder.Method.Otsu, hist);
            }
            int cum = 0, mb = 0;
            while (mb < 255 && (cum += hist[mb]) < d.length / 2) mb++;
            double med = lo + (mb + 0.5) * bin;
            level = med + thrFactor * (lo + (tb + 1) * bin - med);
        }
        levelCache.put(t, level);
        return level;
    }

    /**
     * Objects (8-connected components above the frame threshold) in the window (x0, y0, w, h) of time point t,
     * with their area and centre (weighted by the intensity above the threshold). The threshold used is kept
     * in lastLevel (for the calibration display).
     */
    List<Blob> blobs(int t, int x0, int y0, int w, int h) {
        List<Blob> out = new ArrayList<Blob>();
        double level = frameLevel(t);
        lastLevel = level;
        if (Double.isNaN(level)) return out;
        float[] d = region(t, x0, y0, w, h);
        int n = d.length;
        if (dark) for (int k = 0; k < n; k++) d[k] = -d[k];
        byte[] st = new byte[n];          // 0 background, 1 object pixel to visit, 2 visited
        for (int k = 0; k < n; k++) if (d[k] > level) st[k] = 1;
        int[] queue = new int[n];
        for (int k0 = 0; k0 < n; k0++) {
            if (st[k0] != 1) continue;
            int head = 0, tail = 0, area = 0;
            queue[tail++] = k0;
            st[k0] = 2;
            double sw = 0, sx = 0, sy = 0;
            boolean border = false;
            while (head < tail) {
                int k = queue[head++], x = k % w, y = k / w;
                double wt = d[k] - level;
                sw += wt;
                sx += wt * (x + 0.5);
                sy += wt * (y + 0.5);
                area++;
                if ((x == 0 && x0 > 0) || (y == 0 && y0 > 0) || (x == w - 1 && x0 + w < W) || (y == h - 1 && y0 + h < H))
                    border = true;               // cut by the window (not by the image border)
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int xx = x + dx, yy = y + dy;
                        if (xx < 0 || yy < 0 || xx >= w || yy >= h) continue;
                        int kk = yy * w + xx;
                        if (st[kk] == 1) { st[kk] = 2; queue[tail++] = kk; }
                    }
            }
            Blob b = new Blob();
            b.area = area;
            b.x = x0 + sx / sw;
            b.y = y0 + sy / sw;
            b.border = border;
            out.add(b);
        }
        return out;
    }

    /** Thresholding window around the expected centre (cx, cy) for a search radius r. */
    int[] blobWindow(double cx, double cy, int r) {
        int half = r + Math.max(bw, bh);
        int x0 = Math.max(0, (int) Math.floor(cx) - half), y0 = Math.max(0, (int) Math.floor(cy) - half);
        int x1 = Math.min(W, (int) Math.floor(cx) + half + 1), y1 = Math.min(H, (int) Math.floor(cy) + half + 1);
        return new int[]{x0, y0, x1 - x0, y1 - y0};
    }

    /** Thresholding candidates: objects of compatible area; score = size similarity with the reference object. */
    List<Cand> blobCandidates(int t, double predX, double predY, int r) {
        List<Cand> out = new ArrayList<Cand>();
        int[] wd = blobWindow(predX + offX, predY + offY, r);
        if (wd[2] < 4 || wd[3] < 4) return out;
        for (Blob b : blobs(t, wd[0], wd[1], wd[2], wd[3])) {
            if (b.area < areaMin * refArea || b.area > areaMax * refArea) continue;
            Cand c = new Cand();
            c.x = b.x - offX;
            c.y = b.y - offY;
            c.ix = (int) Math.round(c.x);
            c.iy = (int) Math.round(c.y);
            c.score = c.std = Math.min(b.area, refArea) / Math.max(b.area, refArea);
            c.kept = 1;
            c.border = b.border;
            c.dist = Math.hypot(c.x - predX, c.y - predY);
            out.add(c);
        }
        out.sort((a, b) -> Double.compare(b.score - 0.02 * b.dist, a.score - 0.02 * a.dist));
        return out;
    }

    /**
     * Thresholding: finds, in the reference frame, the object chosen by the user (the one whose centre is
     * closest to the centre of the selection); its area is the reference and its centre the position.
     */
    boolean initBlobRef(boolean quiet) {
        double cx = bx + bw / 2.0, cy = by + bh / 2.0;
        int[] wd = blobWindow(cx, cy, radius);
        Blob best = null;
        double bd = Double.MAX_VALUE;
        for (Blob b : blobs(refT, wd[0], wd[1], wd[2], wd[3])) {
            boolean inside = b.x >= bx - 2 && b.x <= bx + bw + 2 && b.y >= by - 2 && b.y <= by + bh + 2;
            double dd = Math.hypot(b.x - cx, b.y - cy);
            if (inside && b.area >= 3 && dd < bd) { bd = dd; best = b; }
        }
        if (best == null)
            return fail("Thresholding does not find the object in the reference frame.\n"
                    + "Try another thresholding method, a smaller threshold factor,\n"
                    + "or \"Object: dark\" if the object is darker than the background.", quiet);
        refArea = best.area;
        offX = best.x - bx;            // with thresholding, the tracked position is the centre of the segmented object
        offY = best.y - by;
        return true;
    }

    /** Candidates from the chosen detection method (template corner, score, distance to the prediction). */
    List<Cand> findCandidates(int t, double predX, double predY, int r) {
        return detMethod == D_THRESHOLD ? blobCandidates(t, predX, predY, r) : candidates(t, predX, predY, r);
    }

    /**
     * Position (template corner) matching a click; refined on the object if refinement > 0 and the object is
     * well recognised there. Otherwise (object hidden...) the click is kept as is: the user is right.
     */
    Cand refineClick(int t, double cx, double cy) {
        double ex = cx - offX, ey = cy - offY;
        if (refineR > 0) {
            Cand c = null;
            for (Cand k : findCandidates(t, ex, ey, refineR))
                if (k.score >= doubtful && k.kept >= MIN_VISIBLE && k.dist <= refineR + 1 && (c == null || k.score > c.score))
                    c = k;
            if (c != null) {
                double typ = typicalScore();      // clicks on a well visible object update the usual score
                if (Double.isNaN(typ) || c.std >= typ - Math.max(0.06, 3 * scoreSpread())) push(history, c.std);
                clickBiasX.addLast(ex - c.x);     // the user's clicking habit (mean offset)
                clickBiasY.addLast(ey - c.y);
                while (clickBiasX.size() > 10) { clickBiasX.removeFirst(); clickBiasY.removeFirst(); }
                return c;
            }
        }
        if (clickBiasX.size() >= 3) {             // raw click: the usual click offset is removed
            ex -= median(clickBiasX, 3);
            ey -= median(clickBiasY, 3);
        }
        Cand m = new Cand();
        m.x = ex; m.y = ey;
        m.ix = (int) Math.round(ex); m.iy = (int) Math.round(ey);
        m.score = Double.NaN;
        m.raw = true;
        return m;
    }

    double typicalScore() { return median(history, 5); }

    double typicalStep() { return median(steps, 3); }

    /** Robust spread (1.4826 x MAD) of the recent scores, capped at 0.05. */
    double scoreSpread() {
        double med = typicalScore();
        if (Double.isNaN(med)) return 0;
        ArrayDeque<Double> dev = new ArrayDeque<Double>();
        for (double v : history) dev.add(Math.abs(v - med));
        return Math.min(0.05, 1.4826 * median(dev, 1));
    }

    /**
     * Initial usual score: similarity of the object with itself in the few frames around the reference frame
     * (before the tracking meets any difficulty).
     */
    void seedTypicalScore() {
        List<Double> sc = new ArrayList<Double>();
        for (int k = 1; k <= 4; k++)
            for (int t : new int[]{refT + k, refT - k}) {
                if (t < 1 || t > nT) continue;
                List<Cand> cs = findCandidates(t, bx, by, radius);
                if (!cs.isEmpty() && cs.get(0).std >= lost) sc.add(cs.get(0).std);
            }
        if (sc.size() < 3) return;
        double med = Predictor.med(sc);
        for (double v : sc) if (v >= med - 0.1) push(history, v);
    }

    static double median(ArrayDeque<Double> d, int min) {
        if (d.size() < min) return Double.NaN;
        Double[] h = d.toArray(new Double[0]);
        Arrays.sort(h);
        return h[h.length / 2];
    }

    static void push(ArrayDeque<Double> d, double v) {
        if (Double.isNaN(v)) return;
        d.addLast(v);
        while (d.size() > HISTORY) d.removeFirst();
    }

    static boolean trusted(int s) {
        return s == REF || s == VALIDATED || s == CLICK || s == CLICK_RAW;
    }

    void set(int t, double x, double y, double s, int source) {
        px[t] = x; py[t] = y; score[t] = s; src[t] = source;
    }

    // ------------------------------------------------------------------ decision
    static final class Decision {
        double x, y, score, std = Double.NaN, dist, kept = Double.NaN;
        int src;
        String why;
        boolean jump;      // position far from the prediction, confirmed by the whole field
        boolean reacq;     // ... after several frames without a reliable position (object found again)

        Decision(Cand c, double score, int src, String why) {
            this.x = c.x; this.y = c.y; this.score = score; this.std = c.std; this.dist = c.dist; this.src = src; this.why = why;
            this.kept = c.kept;
        }

        Decision(double x, double y, double score, int src, String why) {
            this.x = x; this.y = y; this.score = score; this.dist = 0; this.src = src; this.why = why;
        }
    }

    /**
     * Choice of the position at time point t. Continuity first: the fixed object only moves with the drift,
     * which is slow and smooth.
     *  - Close to the predicted position, a partial similarity is tolerated (robust score: a parasite may
     *    hide part of the object).
     *  - Far from the prediction, a "jump" is only accepted if the similarity is complete (classic score as
     *    good as usual) AND the whole field moved the same way (a real stage jump). Another identical bead,
     *    also fixed, fails this test.
     *  - Otherwise the object is declared lost for this frame (interpolated position).
     * lastGood: last frame with a reliable position (reference for the whole-field test),
     * gap: number of frames since that frame. Thresholds: score >= doubtful: reliable; between lost and
     * doubtful: doubtful; below lost: not found.
     */
    Decision decide(int t, double predX, double predY, int lastGood, int gap) {
        double typ = typicalScore();
        // a jump (object far from the prediction) needs a similarity at least as good as usual
        double strong = Double.isNaN(typ) ? doubtful : Math.max(doubtful, 0.85 * typ);
        double ts = typicalStep();
        // "near" zone: usual distance to the prediction, widened when the object has not been seen for a while
        double dNear = Double.isNaN(ts) ? radius : Math.max(2.5, 4 * ts) + 0.25 * Math.max(0, gap - 1);
        Cand weak = null, closest = null, masked = null, refused = null;
        int rMax = Math.max(radius, Math.min(4 * radius, Math.max(W, H)));
        for (int r = radius; ; r = Math.min(2 * r, rMax)) {
            List<Cand> cs = findCandidates(t, predX, predY, r);
            Cand near = null, jump = null;
            for (Cand c : cs) {
                if (c.dist <= dNear) {
                    if (closest == null || c.score > closest.score) closest = c;
                    if (c.score >= doubtful && c.kept < MIN_VISIBLE) {
                        if (masked == null || c.score > masked.score) masked = c;
                    } else if (c.score >= doubtful) {
                        if (near == null || c.score - 0.02 * c.dist > near.score - 0.02 * near.dist) near = c;
                    } else if (c.score >= lost && (weak == null || c.score > weak.score)) weak = c;
                } else if (c.std >= strong && !c.border && (jump == null || c.std > jump.std)) jump = c;
            }
            if (near != null) {
                if (jump != null && jump.std > near.score + 0.15 && fieldConfirmsJump(lastGood, t, jump, predX, predY))
                    return jumpDecision(jump, gap);
                return new Decision(near, near.score, AUTO, null);
            }
            if (jump != null) {
                if (fieldConfirmsJump(lastGood, t, jump, predX, predY)) return jumpDecision(jump, gap);
                refused = jump;
            }
            if (r >= rMax) break;
        }
        String other = refused == null ? "" : "; a similar object " + f1(refused.dist)
                + " px away was not taken (the rest of the image does not confirm a jump)";
        if (masked != null)
            return new Decision(masked, masked.score, LOST, "object partly hidden ("
                    + Math.round(100 * masked.kept) + " % visible): interpolated position");
        if (weak != null)
            return new Decision(weak, weak.score, DOUBT, "score " + f2(weak.score) + " below the doubtful threshold "
                    + f2(doubtful) + other);
        return new Decision(predX, predY, closest == null ? Double.NaN : closest.score, LOST,
                "object not found (" + (closest == null ? "no candidate" : "best score " + f2(closest.score))
                        + " below the lost threshold " + f2(lost) + ")" + other);
    }

    Decision jumpDecision(Cand c, int gap) { return jumpDecision(c, gap, "confirmed by the whole field"); }

    Decision jumpDecision(Cand c, int gap, String how) {
        String w = gap > 2 ? "object found again after " + (gap - 1) + " unreliable frame(s), " + f1(c.dist)
                + " px from the expected position (" + how + ")"
                : "jump of " + f1(c.dist) + " px (" + how + ")";
        Decision d = new Decision(c, c.std, DOUBT, w + " (score " + f2(c.std) + ")");
        d.jump = true;
        d.reacq = gap > 2;
        return d;
    }

    /**
     * Whole-field test: between frame a (known position) and frame b, did the image as a whole move like the
     * proposed jump (towards candidate c) rather than like the prediction?
     */
    boolean fieldConfirmsJump(int a, int b, Cand c, double predX, double predY) {
        double jdx = c.x - px[a], jdy = c.y - py[a], pdx = predX - px[a], pdy = predY - py[a];
        int m = (int) Math.ceil(Math.max(Math.max(Math.abs(jdx), Math.abs(jdy)), Math.max(Math.abs(pdx), Math.abs(pdy)))) + 4;
        int x0 = m, x1 = W - m, y0 = m, y1 = H - m;
        if (x1 - x0 < 24 || y1 - y0 < 24) return false;
        float[] A = full(a), B = full(b);
        int s = Math.max(1, (int) Math.sqrt((double) (x1 - x0) * (y1 - y0) / 40000.0));
        double sj = bestShift(A, B, x0, x1, y0, y1, s, jdx, jdy);
        double sp = bestShift(A, B, x0, x1, y0, y1, s, pdx, pdy);
        return sj > sp + 0.05 && sj > 0.2;
    }

    double bestShift(float[] A, float[] B, int x0, int x1, int y0, int y1, int s, double dx, double dy) {
        int cx = (int) Math.round(dx), cy = (int) Math.round(dy);
        double best = -2;
        for (int ddy = -2; ddy <= 2; ddy++)
            for (int ddx = -2; ddx <= 2; ddx++)
                best = Math.max(best, nccShift(A, B, x0, x1, y0, y1, s, cx + ddx, cy + ddy));
        return best;
    }

    double nccShift(float[] A, float[] B, int x0, int x1, int y0, int y1, int s, int sx, int sy) {
        double sa = 0, sb = 0, saa = 0, sbb = 0, sab = 0;
        int n = 0;
        for (int y = y0; y < y1; y += s) {
            int ra = y * W, rb = (y + sy) * W + sx;
            for (int x = x0; x < x1; x += s) {
                double a = A[ra + x], b = B[rb + x];
                sa += a; sb += b; saa += a * a; sbb += b * b; sab += a * b; n++;
            }
        }
        double va = saa - sa * sa / n, vb = sbb - sb * sb / n;
        if (!(va > 0) || !(vb > 0)) return 0;
        return (sab - sa * sb / n) / Math.sqrt(va * vb);
    }

    // ===================================================================== automatic tracking
    boolean autoPhase(boolean semi) {
        set(refT, bx, by, 1.0, REF);
        progressDone = 1;
        seedTypicalScore();
        if (session != null) {
            session.phase(TRACKING_TEXT, "cancel", "Cancel");
            show(refT);
            updateOverlay();
        }
        if (!trackRange(refT, nT, +1, semi, false)) return false;
        if (!trackRange(refT, 1, -1, semi, false)) return false;
        flagIsolated();
        settleDoubtful();
        fillFromField();
        return true;
    }

    /**
     * Lost frames: position interpolated linearly, or measured on the whole field (still background) when that
     * measurement is more reliable. The field must first recover the known displacement of the object between
     * the reliable frames around the loss, then on the neighbouring reliable frames; its error on these frames
     * is compared with the expected error of the interpolation (curvature of the drift).
     */
    void fillFromField() {
        interpolate();
        int t = 1;
        while (t <= nT) {
            if (src[t] != LOST) { t++; continue; }
            int a = t;
            while (t <= nT && src[t] == LOST) t++;
            int b = t - 1, L = a - 1, R = b + 1;
            if (L < 1 || R > nT) continue;
            double curv = curvature(L, R);
            double eInterp = Double.isNaN(curv) ? 0.5 : 0.125 * curv * (R - L) * (R - L);
            double[] lr = fieldShift(L, R, px[R] - px[L], py[R] - py[L]);
            if (lr == null || Math.hypot(lr[0] - (px[R] - px[L]), lr[1] - (py[R] - py[L])) > 1.0) continue;
            List<Double> ev = new ArrayList<Double>();
            for (int dk = 1; dk <= 12 && ev.size() < 6; dk++)
                for (int v : new int[]{L - dk, R + dk}) {
                    if (v < 1 || v > nT || !(src[v] == AUTO || trusted(src[v]))) continue;
                    double[] sh = fieldShift(L, v, px[v] - px[L], py[v] - py[L]);
                    if (sh != null) ev.add(Math.hypot(sh[0] - (px[v] - px[L]), sh[1] - (py[v] - py[L])));
                }
            if (ev.size() < 2) continue;
            double eField = Predictor.med(ev);
            if (eField > 0.8 || eField >= eInterp) continue;
            for (int k = a; k <= b; k++) {
                double[] sh = fieldShift(L, k, px[k] - px[L], py[k] - py[L]);
                if (sh == null) continue;
                set(k, px[L] + sh[0], py[L] + sh[1], score[k], FIELD);
                why[k] = (why[k] == null ? "" : why[k].replace(": interpolated position", "") + ": ")
                        + "position estimated on the whole field";
            }
        }
    }

    /** Curvature of the drift (px/frame^2) around a loss [L, R], from the neighbouring reliable positions. */
    double curvature(int L, int R) {
        int w = Math.max(5, R - L);
        List<double[]> pts = new ArrayList<double[]>();
        int left = 0, right = 0;
        for (int t = Math.max(1, L - w); t <= Math.min(nT, R + w); t++) {
            if (t > L && t < R) continue;
            if (!(src[t] == AUTO || trusted(src[t])) || (isJump[t] && !trusted(src[t]))) continue;
            pts.add(new double[]{t - 0.5 * (L + R), px[t], py[t]});
            if (t <= L) left++; else right++;
        }
        if (pts.size() < 5 || left < 2 || right < 2) return Double.NaN;
        return Math.max(Math.abs(2 * quadCoef(pts, 1)), Math.abs(2 * quadCoef(pts, 2)));
    }

    /** Coefficient of the u^2 term of a least-squares quadratic fit of p[col] against p[0]. */
    static double quadCoef(List<double[]> pts, int col) {
        double s0 = 0, s1 = 0, s2 = 0, s3 = 0, s4 = 0, y0 = 0, y1 = 0, y2 = 0;
        for (double[] p : pts) {
            double u = p[0], u2 = u * u, v = p[col];
            s0 += 1; s1 += u; s2 += u2; s3 += u2 * u; s4 += u2 * u2;
            y0 += v; y1 += u * v; y2 += u2 * v;
        }
        double[][] m = {{s0, s1, s2, y0}, {s1, s2, s3, y1}, {s2, s3, s4, y2}};
        for (int i = 0; i < 3; i++) {                      // Gaussian elimination
            int piv = i;
            for (int r = i + 1; r < 3; r++) if (Math.abs(m[r][i]) > Math.abs(m[piv][i])) piv = r;
            double[] tmp = m[i]; m[i] = m[piv]; m[piv] = tmp;
            if (Math.abs(m[i][i]) < 1e-12) return 0;
            for (int r = 0; r < 3; r++) {
                if (r == i) continue;
                double f = m[r][i] / m[i][i];
                for (int c = i; c < 4; c++) m[r][c] -= f * m[i][c];
            }
        }
        return m[2][3] / m[2][2];
    }

    /**
     * Whole-field shift between frames a and b, searched within +-4 px around (gx, gy), sub-pixel.
     * null if the peak is not sharp.
     */
    double[] fieldShift(int a, int b, double gx, double gy) {
        int cx = (int) Math.round(gx), cy = (int) Math.round(gy), R = 4;
        int m = Math.max(Math.abs(cx), Math.abs(cy)) + R + 2;
        int x0 = m, x1 = W - m, y0 = m, y1 = H - m;
        if (x1 - x0 < 24 || y1 - y0 < 24) return null;
        float[] A = full(a), B = full(b);
        int s = Math.max(1, (int) Math.sqrt((double) (x1 - x0) * (y1 - y0) / 40000.0));
        int n = 2 * R + 1;
        double[] g = new double[n * n];
        int bi = 0;
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                g[j * n + i] = nccShift(A, B, x0, x1, y0, y1, s, cx - R + i, cy - R + j);
                if (g[j * n + i] > g[bi]) bi = j * n + i;
            }
        int ix = bi % n, iy = bi / n;
        if (ix == 0 || iy == 0 || ix == n - 1 || iy == n - 1) return null;   // peak on the edge: not reliable
        double ring = -2;
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++)
                if (Math.max(Math.abs(i - ix), Math.abs(j - iy)) >= 3) ring = Math.max(ring, g[j * n + i]);
        double best = g[bi];
        if (best < 0.3 || best - ring < 0.02) return null;
        double sx = parabola(g[bi - 1], best, g[bi + 1]), sy = parabola(g[bi - n], best, g[bi + n]);
        return new double[]{cx - R + ix + sx, cy - R + iy + sy, best};
    }

    /** Unreliable frame: doubtful (except a confirmed stage jump), lost, or object found again after a loss. */
    boolean shaky(int t) {
        return unknown(src[t]) || (src[t] == DOUBT && (!isJump[t] || isReacq[t]));
    }

    /**
     * During a short disturbance (hidden object, low score), a wrong measurement is worse than an
     * interpolation: the doubtful frames of a run of at most 20 frames, bounded by reliable positions, are
     * interpolated (they stay flagged). Confirmed jumps are kept.
     */
    void settleDoubtful() {
        int t = 1;
        while (t <= nT) {
            if (!(src[t] == DOUBT && !isJump[t]) && !unknown(src[t])) { t++; continue; }
            int a = t;
            while (t <= nT && ((src[t] == DOUBT && !isJump[t]) || unknown(src[t]))) t++;
            int b = t - 1;
            boolean bounded = a > 1 && b < nT;
            if (bounded && b - a + 1 <= 20)
                for (int k = a; k <= b; k++)
                    if (src[k] == DOUBT) {
                        src[k] = LOST;
                        why[k] = why[k] + ": interpolated position";
                    }
        }
    }

    /**
     * A few "auto" frames stuck between doubtful or lost frames (while an object passes over the fixed object)
     * are not reliable: they are flagged doubtful for the review.
     */
    void flagIsolated() {
        int t = 1;
        while (t <= nT) {
            if (src[t] != AUTO) { t++; continue; }
            int a = t;
            while (t <= nT && src[t] == AUTO) t++;
            int b = t - 1;                               // run [a, b] of auto frames
            boolean left = a > 1 && shaky(a - 1), right = b < nT && shaky(b + 1);
            if (left && right && b - a + 1 <= 5)
                for (int k = a; k <= b; k++) {
                    src[k] = DOUBT;
                    why[k] = "isolated detection among doubtful frames (object probably disturbed)";
                }
        }
    }

    static final String TRACKING_TEXT = "<b>Automatic tracking…</b><br>The outline follows the fixed object from frame to frame.";

    /**
     * Prediction of the object position: last reliable position + drift speed, estimated as the median of the
     * speeds between the last reliable positions ("anchors"). One or two wrong positions (an object passing
     * over it) do not move the prediction.
     */
    static final class Predictor {
        final ArrayDeque<double[]> anchors = new ArrayDeque<double[]>();   // {t, x, y}
        final double vmax;

        Predictor(double vmax) { this.vmax = vmax; }

        void add(int t, double x, double y) {
            anchors.addLast(new double[]{t, x, y});
            while (anchors.size() > 6) anchors.removeFirst();
        }

        void reset(int t, double x, double y) {
            anchors.clear();
            add(t, x, y);
        }

        double[] last() { return anchors.peekLast(); }

        double[] predict(int t) {
            double[] l = anchors.peekLast();
            if (anchors.size() < 2) return new double[]{l[1], l[2]};
            List<Double> vx = new ArrayList<Double>(), vy = new ArrayList<Double>();
            double[] prev = null;
            for (double[] a : anchors) {
                if (prev != null) {
                    double dt = Math.abs(a[0] - prev[0]);
                    vx.add((a[1] - prev[1]) / dt);
                    vy.add((a[2] - prev[2]) / dt);
                }
                prev = a;
            }
            int k = (int) Math.abs(t - l[0]);
            return new double[]{l[1] + clampD(med(vx), vmax) * k, l[2] + clampD(med(vy), vmax) * k};
        }

        static double med(List<Double> v) {
            Double[] a = v.toArray(new Double[0]);
            Arrays.sort(a);
            int n = a.length;
            return n % 2 == 1 ? a[n / 2] : 0.5 * (a[n / 2 - 1] + a[n / 2]);
        }
    }

    /** Tracks the object from frame from (known position) to frame to. Returns false if cancelled. */
    boolean trackRange(int from, int to, int dir, boolean interactive, boolean stopAtTrusted) {
        return trackRange(from, to, dir, interactive, stopAtTrusted, false);
    }

    /**
     * seed: the prediction also starts from the reliable positions just before from (since the last jump),
     * so that the drift speed is known from the first frame on (used when re-tracking from a frame).
     */
    boolean trackRange(int from, int to, int dir, boolean interactive, boolean stopAtTrusted, boolean seed) {
        Predictor pr = new Predictor(Math.max(1, radius / 2.0));
        if (seed) {
            ArrayDeque<Integer> prev = new ArrayDeque<Integer>();
            for (int t = from - dir; t >= 1 && t <= nT && Math.abs(t - from) <= 30 && prev.size() < 5; t -= dir) {
                if (isJump[from] && !trusted(src[from])) break;
                boolean ok = trusted(src[t]) || (src[t] == AUTO && !isJump[t]);
                if (ok) prev.addFirst(t);
                if (isJump[t] || src[t] == REF) break;      // nothing before a jump is used
            }
            for (int t : prev) pr.add(t, px[t], py[t]);
        }
        pr.add(from, px[from], py[from]);
        for (int t = from + dir; dir > 0 ? t <= to : t >= to; t += dir) {
            if (cancelled()) return false;
            progressDone++;
            IJ.showProgress(Math.min(progressDone, nT), nT);
            if (trusted(src[t])) {
                if (stopAtTrusted) break;
                pr.add(t, px[t], py[t]);
                continue;
            }
            double[] p = pr.predict(t);
            int lastGood = (int) pr.last()[0];
            Decision d = decide(t, p[0], p[1], lastGood, Math.abs(t - lastGood));
            set(t, d.x, d.y, d.score, d.src);
            why[t] = d.why;
            visibleFrac[t] = d.kept;
            isJump[t] = d.jump;
            isReacq[t] = d.reacq;
            if (d.src == AUTO) waitReturn = false;
            if (d.src != AUTO && interactive && !stopAsking && !waitReturn && session != null) {
                if (!ask(t)) return false;
            }
            if (src[t] == AUTO) {
                // usual score: only normal detections go into it (not those disturbed by a neighbour),
                // while following a slow decrease (photobleaching, focus)
                double typ = typicalScore();
                if (Double.isNaN(typ) || d.std >= typ - Math.max(0.06, 3 * scoreSpread())) push(history, d.std);
            }
            if (trusted(src[t])) {
                if (d.jump) pr.reset(t, px[t], py[t]);
                else pr.add(t, px[t], py[t]);
            } else if (src[t] == AUTO && reliable(d, Math.abs(t - lastGood))) {   // only clean detections guide the rest
                pr.add(t, px[t], py[t]);
                push(steps, d.dist);
            } else if (src[t] == DOUBT && d.jump) {        // confirmed jump: start again from the new position
                pr.reset(t, px[t], py[t]);
            }
        }
        return true;
    }

    /** Clean detection: score in the usual range, object fully visible, where it was expected. */
    boolean reliable(Decision d, int gap) {
        double typ = typicalScore(), ts = typicalStep();
        if (d.kept < 0.9) return false;
        if (!Double.isNaN(typ) && d.score < typ - Math.max(0.04, 2.5 * scoreSpread())) return false;
        return Double.isNaN(ts) || d.dist <= Math.max(1.0, 3 * ts) + 0.25 * Math.max(0, gap - 1);
    }

    static double clampD(double v, double m) { return Math.max(-m, Math.min(m, v)); }

    boolean cancelled() {
        if (session != null) return session.cancelRequested();
        if (IJ.escapePressed()) {
            IJ.resetEscape();
            return true;
        }
        return false;
    }

    /** Semi-automatic mode: asks the user to decide for frame t. */
    boolean ask(int t) {
        show(t);
        updateOverlay();
        boolean lost = src[t] == LOST;
        String box = lost ? "The <font color='#e00000'><b>red</b></font> outline shows where the object is expected."
                : "The <font color='#ff8c00'><b>orange</b></font> outline shows the position found.";
        session.phase("<b>Frame " + t + " / " + nT + ": " + (lost ? "object not found" : "doubtful detection")
                        + "</b><br>" + (why[t] == null ? "" : why[t]) + "<br><br>" + box + "<br>"
                        + "<b>Click on the fixed object</b> to give its position, or:",
                "ok", lost ? "Keep this position" : "Detection is correct", "skip", "Unreadable frame (interpolate)",
                "wait", "Wait for the object to return", "noask", "Stop asking", "cancel", "Cancel");
        session.clear();
        session.status(" ");
        boolean result = true;
        while (true) {
            Ev e = session.next();
            if (e.id.equals("cancel")) { result = false; break; }
            if (e.id.equals("click")) {
                Cand c = refineClick(e.frame, e.x, e.y);
                set(e.frame, c.x, c.y, c.score, c.raw ? CLICK_RAW : CLICK);
                why[e.frame] = null;
                isJump[e.frame] = isReacq[e.frame] = false;
                if (e.frame == t) break;
                updateOverlay();        // correction of another frame: keep asking for t
                show(t);
                continue;
            }
            if (e.id.equals("ok")) { src[t] = VALIDATED; why[t] = null; break; }
            if (e.id.equals("skip")) { set(t, px[t], py[t], Double.NaN, NONE); why[t] = "unreadable frame"; break; }
            if (e.id.equals("noask")) { stopAsking = true; break; }
            if (e.id.equals("wait")) { waitReturn = true; break; }    // stop asking until the object is found again
        }
        if (result) {
            session.phase(trackingText, "cancel", trackingCancel);
            updateOverlay();
        }
        return result;
    }

    // ===================================================================== mode manuel
    boolean manualPhase() {
        if (refineR > 0) set(refT, bx, by, 1.0, REF);   // the reference frame is already known
        int cur = (src[1] == REF) ? nextTarget(1) : 1;
        if (cur > nT) return true;
        session.phase("<b>Manual pointing</b><br>Click on the fixed object: the movie moves on by " + step
                        + " frame(s) after each click. The grey outline shows where the object is expected; "
                        + "the frames not pointed will be interpolated.<br>"
                        + "(To zoom, use the + and - keys: clicks are used for pointing.)",
                "back", "« Back", "finish", "Finish", "cancel", "Cancel");
        session.clear();
        refreshManual(cur);
        while (true) {
            Ev e = session.next();
            if (e.id.equals("cancel")) return false;
            if (e.id.equals("finish")) break;
            if (e.id.equals("back")) {
                cur = Math.max(1, Math.min(cur, nT) - step);
                if (cur == refT && src[refT] == REF && cur > 1) cur = Math.max(1, cur - step);
                refreshManual(cur);
                continue;
            }
            if (e.id.equals("click")) {
                Cand c = refineClick(e.frame, e.x, e.y);
                set(e.frame, c.x, c.y, c.score, c.raw ? CLICK_RAW : CLICK);
                why[e.frame] = null;
                isJump[e.frame] = isReacq[e.frame] = false;
                cur = nextTarget(e.frame);
                if (cur > nT) break;
                refreshManual(cur);
            }
        }
        return true;
    }

    int nextTarget(int f) {
        int n = f + step;
        if (n > nT) n = f < nT ? nT : nT + 1;
        if (n <= nT && n == refT && src[refT] == REF) return nextTarget(n);
        return n;
    }

    void refreshManual(int cur) {
        int clicked = 0;
        for (int t = 1; t <= nT; t++) if (src[t] == CLICK || src[t] == CLICK_RAW) clicked++;
        interpolate();
        show(cur);
        updateOverlay();
        session.status("Frame " + cur + " / " + nT + " — " + clicked + " frame(s) pointed");
    }

    // ===================================================================== interpolation
    static boolean unknown(int s) { return s == NONE || s == INTERP || s == LOST; }   // FIELD: measured (whole field)

    /** Fills the frames without a reliable position (NONE / INTERP / LOST) by linear interpolation. */
    boolean interpolate() {
        int[] known = new int[nT];
        int n = 0;
        for (int t = 1; t <= nT; t++) if (!unknown(src[t])) known[n++] = t;
        if (n == 0) return false;
        int k = 0;
        for (int t = 1; t <= nT; t++) {
            if (!unknown(src[t])) continue;
            while (k < n && known[k] < t) k++;
            int a = k > 0 ? known[k - 1] : -1, b = k < n ? known[k] : -1;
            double x, y;
            if (a < 0) { x = px[b]; y = py[b]; }
            else if (b < 0) { x = px[a]; y = py[a]; }
            else {
                double w = (t - a) / (double) (b - a);
                x = px[a] + w * (px[b] - px[a]);
                y = py[a] + w * (py[b] - py[a]);
            }
            set(t, x, y, src[t] == LOST ? score[t] : Double.NaN, src[t] == LOST ? LOST : INTERP);
        }
        return true;
    }

    // ===================================================================== verification
    static final String REVIEW_TEXT = "<b>Review of the tracking</b><br>"
            + "Go through the movie: the outline must stay on the fixed object.<br>"
            + "&bull; Wrong outline &rarr; <b>click on the object</b> to correct this frame.<br>"
            + "&bull; \"Re-track from here\" erases the tracking after the displayed frame and tracks again "
            + "from it; the frames before are kept. Correct the displayed frame first if needed.<br>"
            + "Outline: <font color='#00c000'><b>green</b></font> reliable, "
            + "<font color='#ff8c00'><b>orange</b></font> doubtful, "
            + "<font color='#e00000'><b>red</b></font> / <font color='#ff5ac8'><b>pink</b></font> object lost "
            + "(position interpolated / estimated on the whole field), "
            + "<font color='#00a0c0'><b>cyan</b></font> click, "
            + "<font color='#909090'><b>grey</b></font> interpolated, "
            + "<font color='#d000d0'><b>magenta</b></font> reference.";

    boolean reviewPhase() {
        interpolate();
        updateOverlay();
        int first = nextDoubt(0, +1);
        reviewPanel();
        updatePlot();
        show(first > 0 ? first : refT);
        session.clear();
        final int[] shown = {-1};
        session.idle = new Runnable() {
            public void run() {
                int f = currentTime();
                if (f != shown[0]) { shown[0] = f; reviewStatus(f); }
            }
        };
        reviewStatus(currentTime());
        shown[0] = currentTime();
        try {
            while (true) {
                Ev e = session.next();
                if (e.id.equals("cancel")) return false;
                if (e.id.equals("apply")) return true;
                if (e.id.equals("click")) {
                    Cand c = refineClick(e.frame, e.x, e.y);
                    set(e.frame, c.x, c.y, c.score, c.raw ? CLICK_RAW : CLICK);
                    why[e.frame] = null;
                    isJump[e.frame] = isReacq[e.frame] = false;
                    interpolate();
                    updateOverlay();
                    updatePlot();
                    reviewStatus(e.frame);
                } else if (e.id.equals("prevd") || e.id.equals("nextd")) {
                    int f = nextDoubt(currentTime(), e.id.equals("nextd") ? +1 : -1);
                    if (f > 0) show(f);
                    else session.status("No other doubtful frame in this direction.");
                } else if (e.id.equals("retrack")) {
                    int f = currentTime();
                    if (f >= nT) { session.status("Last frame: nothing to re-track."); continue; }
                    if (!(trusted(src[f]) || src[f] == AUTO)) {         // unreliable starting point
                        f = pickStart(f);
                        reviewPanel();
                        if (f < 1) { reviewStatus(currentTime()); shown[0] = currentTime(); continue; }
                        if (f >= nT) { session.status("Last frame: nothing to re-track."); continue; }
                    }
                    retrackFrom(f);
                    reviewPanel();
                    show(f);
                    updateOverlay();
                    updatePlot();
                    session.clear();
                    int doubts = 0;
                    for (int t = f + 1; t <= nT; t++) if (src[t] == DOUBT || src[t] == LOST || src[t] == FIELD) doubts++;
                    session.status("Re-tracked from frame " + f + " to " + nT + ": "
                            + (doubts == 0 ? "no doubtful frame." : doubts + " doubtful frame(s) after frame " + f + "."));
                    shown[0] = currentTime();
                    continue;
                }
                shown[0] = currentTime();
            }
        } finally {
            session.idle = null;
        }
    }

    void reviewPanel() {
        session.phase(REVIEW_TEXT,
                "prevd", "« Previous doubtful", "nextd", "Next doubtful »",
                "retrack", "Re-track from here", "apply", "Apply the correction",
                "cancel", "Cancel");
    }

    /**
     * The displayed frame has no reliable position: the user clicks on the object to restart from it.
     * Returns the frame to restart from, or -1.
     */
    int pickStart(int f) {
        session.phase("<b>Re-track from frame " + f + "</b><br>The position on this frame is not reliable ("
                        + SRC_NAME[src[f]] + "). <b>Click on the fixed object</b> to restart the tracking from it.",
                "keep", "Use the current position", "back", "Back");
        session.clear();
        while (true) {
            Ev e = session.next();
            if (e.id.equals("cancel") || e.id.equals("back")) return -1;
            if (e.id.equals("keep")) {
                src[f] = VALIDATED; why[f] = null;
                isJump[f] = isReacq[f] = false;
                return f;
            }
            if (e.id.equals("click")) {
                Cand c = refineClick(e.frame, e.x, e.y);
                set(e.frame, c.x, c.y, c.score, c.raw ? CLICK_RAW : CLICK);
                why[e.frame] = null;
                isJump[e.frame] = isReacq[e.frame] = false;
                return e.frame;
            }
        }
    }

    /**
     * Erases everything after frame f (clicks included, the reference frame excepted) and tracks again from f,
     * in the chosen mode (semi-automatic: asks when in doubt). "Stop" keeps what was done; the rest is
     * interpolated.
     */
    void retrackFrom(int f) {
        for (int t = f + 1; t <= nT; t++) {
            if (src[t] == REF) continue;
            set(t, px[t], py[t], Double.NaN, NONE);
            why[t] = null;
            isJump[t] = isReacq[t] = false;
        }
        stopAsking = waitReturn = false;
        trackingText = "<b>Re-tracking from frame " + f + "…</b><br>The frames after it are tracked again; "
                + "the frames before are kept.";
        trackingCancel = "Stop";
        session.phase(trackingText, "cancel", trackingCancel);
        session.clear();
        show(f);
        updateOverlay();
        progressDone = f;
        Runnable idle = session.idle;
        session.idle = null;                     // the review status must not overwrite the questions
        try {
            trackRange(f, nT, +1, mode == M_SEMI, false, true);
        } finally {
            session.idle = idle;
            trackingText = TRACKING_TEXT;
            trackingCancel = "Cancel";
        }
        IJ.showProgress(1.0);
        flagIsolated();
        settleDoubtful();
        fillFromField();
        interpolate();
    }

    int nextDoubt(int from, int dir) {
        for (int t = from + dir; t >= 1 && t <= nT; t += dir) if (src[t] == DOUBT || src[t] == LOST || src[t] == FIELD) return t;
        return -1;
    }

    void reviewStatus(int f) {
        if (f < 1 || f > nT) return;
        int doubts = 0;
        for (int t = 1; t <= nT; t++) if (src[t] == DOUBT || src[t] == LOST || src[t] == FIELD) doubts++;
        String s = "Frame " + f + " / " + nT + " — " + SRC_NAME[src[f]]
                + (why[f] != null ? ": " + why[f] : Double.isNaN(score[f]) ? "" : " (score " + f2(score[f]) + ")")
                + " — dX " + f1(px[f] - px[refT]) + ", dY " + f1(py[f] - py[refT]) + " px"
                + " — " + (doubts == 0 ? "no doubtful frame" : doubts + " doubtful frame(s)");
        session.status(s);
    }

    // ===================================================================== affichage
    void updateOverlay() {
        Overlay ov = new Overlay();
        int n = 0;
        float[] xs = new float[nT], ys = new float[nT];
        for (int t = 1; t <= nT; t++) {
            if (src[t] == NONE) continue;
            xs[n] = (float) (px[t] + offX);
            ys[n] = (float) (py[t] + offY);
            n++;
        }
        if (n > 1) {
            PolygonRoi traj = new PolygonRoi(Arrays.copyOf(xs, n), Arrays.copyOf(ys, n), n, Roi.POLYLINE);
            traj.setStrokeColor(new Color(255, 255, 0, 110));
            ov.add(traj);
        }
        boolean hyper = imp.isHyperStack() || nC > 1;
        for (int t = 1; t <= nT; t++) {
            if (src[t] == NONE) continue;
            Roi r = (Roi) shape.clone();
            r.setLocation(px[t] + shapeDx, py[t] + shapeDy);
            r.setStrokeColor(SRC_COLOR[src[t]]);
            r.setStrokeWidth(src[t] == DOUBT || src[t] == LOST || src[t] == FIELD ? 2 : 1);
            if (hyper) {
                if (timeIsZ) r.setPosition(0, t, 0); else r.setPosition(0, 0, t);
                ov.add(r);
            } else if (stack.getSize() == nT) {
                r.setPosition(t);
                ov.add(r);
            } else {
                for (int z = 1; z <= nZ; z++) {
                    Roi r2 = (Roi) r.clone();
                    r2.setPosition(index(1, z, t));
                    ov.add(r2);
                }
            }
        }
        imp.setOverlay(ov);
    }

    Plot makePlot() {
        double[] f = new double[nT], dx = new double[nT], dy = new double[nT];
        List<double[]> doubt = new ArrayList<double[]>(), click = new ArrayList<double[]>();
        for (int t = 1; t <= nT; t++) {
            f[t - 1] = t;
            dx[t - 1] = px[t] - px[refT];
            dy[t - 1] = py[t] - py[refT];
            if (src[t] == DOUBT || src[t] == LOST || src[t] == FIELD) { doubt.add(new double[]{t, dx[t - 1]}); doubt.add(new double[]{t, dy[t - 1]}); }
            if (src[t] == CLICK || src[t] == CLICK_RAW) { click.add(new double[]{t, dx[t - 1]}); click.add(new double[]{t, dy[t - 1]}); }
        }
        Plot p = new Plot("Drift - " + imp.getTitle(), "Frame", "Drift (px)");
        p.setLineWidth(1.5f);
        p.setColor(new Color(210, 40, 40));
        p.addPoints(f, dx, Plot.LINE);
        p.setColor(new Color(40, 80, 210));
        p.addPoints(f, dy, Plot.LINE);
        StringBuilder legend = new StringBuilder("dX\tdY");
        if (!doubt.isEmpty()) {
            p.setColor(ORANGE);
            p.addPoints(col(doubt, 0), col(doubt, 1), Plot.CIRCLE);
            legend.append("\tdoubtful");
        }
        if (!click.isEmpty()) {
            p.setColor(new Color(0, 160, 200));
            p.addPoints(col(click, 0), col(click, 1), Plot.BOX);
            legend.append("\tclick");
        }
        double lo = 0, hi = 0;
        for (int k = 0; k < nT; k++) {
            lo = Math.min(lo, Math.min(dx[k], dy[k]));
            hi = Math.max(hi, Math.max(dx[k], dy[k]));
        }
        double m = Math.max(1, 0.08 * (hi - lo));
        p.setLimits(1, Math.max(2, nT), lo - m, hi + m);
        p.setColor(Color.black);
        p.setLegend(legend.toString(), Plot.AUTO_POSITION);
        return p;
    }

    static double[] col(List<double[]> l, int c) {
        double[] r = new double[l.size()];
        for (int i = 0; i < r.length; i++) r[i] = l.get(i)[c];
        return r;
    }

    void updatePlot() {
        if (Interpreter.isBatchMode()) return;
        Plot p = makePlot();
        if (plotWin != null && plotWin.isVisible() && plotWin.getImagePlus() != null
                && plotWin.getImagePlus().getWindow() != null) {
            plotWin.drawPlot(p);
            return;
        }
        plotWin = p.show();
        if (plotWin != null && session != null && session.dlg != null && imp.getWindow() != null) {
            // below the image if there is room, otherwise below the panel; hiding neither of them
            Rectangle iw = imp.getWindow().getBounds(), d = session.dlg.getBounds();
            Dimension scr = Toolkit.getDefaultToolkit().getScreenSize();
            int pw = plotWin.getWidth(), ph = plotWin.getHeight();
            int x, y;
            if (iw.y + iw.height + 10 + ph <= scr.height) { x = iw.x; y = iw.y + iw.height + 10; }
            else { x = d.x; y = d.y + d.height + 10; }
            x = Math.max(0, Math.min(x, scr.width - pw));
            y = Math.max(0, Math.min(y, scr.height - ph));
            plotWin.setLocation(x, y);
        }
        ImageWindow iw = imp.getWindow();
        if (iw != null) iw.toFront();
        if (session != null) session.toFront();
    }

    // ===================================================================== calibration
    static final class CalRes {
        int t, status;            // 0 reliable, 1 doubtful, 2 not found, 3 hidden
        Cand best, second;        // object kept, best other candidate (a distinct object)
        double level = Double.NaN, secDist = Double.NaN, predX, predY;   // expected position (global drift)
    }

    static final String[] CAL_STATUS = {"reliable", "doubtful", "not found", "hidden"};
    static final Color[] CAL_COLOR = {new Color(0, 220, 0), ORANGE, new Color(235, 30, 30), ORANGE};

    String detectionText() {
        String pre = (bgRadius > 0 ? ", background " + IJ.d2s(bgRadius, 0) + " px" : "")
                + (sigma > 0 ? ", smoothing " + IJ.d2s(sigma, 1) : "");
        if (detMethod == D_CORREL) return "similarity (correlation)" + pre;
        return "thresholding " + thrMethod + " x " + IJ.d2s(thrFactor, 2) + ", object " + (dark ? "dark" : "bright")
                + ", area " + IJ.d2s(areaMin, 2) + " to " + IJ.d2s(areaMax, 2) + " x " + Math.round(refArea) + " px" + pre;
    }

    /** N frames picked at random, one per section of the movie (start, middle and end all represented), reference excluded. */
    int[] pickFrames(Random rnd) {
        List<Integer> pool = new ArrayList<Integer>();
        for (int t = 1; t <= nT; t++) if (t != refT) pool.add(t);
        int n = Math.min(nCalib, pool.size());
        int[] out = new int[n];
        for (int k = 0; k < n; k++) {
            int a = k * pool.size() / n, b = (k + 1) * pool.size() / n;
            out[k] = pool.get(a + rnd.nextInt(Math.max(1, b - a)));
        }
        return out;
    }

    /**
     * Approximate global shift of frame t relative to the reference frame, measured on the whole image
     * downsampled (within +- maxShift px), then refined at full resolution if the peak is sharp.
     */
    double[] globalShift(int t, int maxShift) {
        int f = Math.max(1, (int) Math.round(Math.max(W, H) / 200.0)), w = W / f, h = H / f;
        float[] A = down(full(refT), W, f, w, h), B = down(full(t), W, f, w, h);
        int S = Math.max(2, (int) Math.ceil(maxShift / (double) f)), m = S + 1;
        if (w - 2 * m < 16 || h - 2 * m < 16) return new double[]{0, 0};
        int bsx = 0, bsy = 0;
        double best = -2;
        for (int sy = -S; sy <= S; sy++)
            for (int sx = -S; sx <= S; sx++) {
                double v = nccShiftW(A, B, w, m, w - m, m, h - m, sx, sy);
                if (v > best) { best = v; bsx = sx; bsy = sy; }
            }
        double[] fine = fieldShift(refT, t, bsx * f, bsy * f);
        return fine != null ? new double[]{fine[0], fine[1]} : new double[]{bsx * f, bsy * f};
    }

    /** Block average f x f of an image of width aw (w x h whole blocks). */
    static float[] down(float[] a, int aw, int f, int w, int h) {
        if (f == 1) return a.clone();
        float[] o = new float[w * h];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                double sum = 0;
                for (int j = 0; j < f; j++) {
                    int r = (y * f + j) * aw + x * f;
                    for (int i = 0; i < f; i++) sum += a[r + i];
                }
                o[y * w + x] = (float) (sum / (f * f));
            }
        return o;
    }

    static double nccShiftW(float[] A, float[] B, int w, int x0, int x1, int y0, int y1, int sx, int sy) {
        double sa = 0, sb = 0, saa = 0, sbb = 0, sab = 0;
        int n = 0;
        for (int y = y0; y < y1; y++) {
            int ra = y * w, rb = (y + sy) * w + sx;
            for (int x = x0; x < x1; x++) {
                double a = A[ra + x], b = B[rb + x];
                sa += a; sb += b; saa += a * a; sbb += b * b; sab += a * b; n++;
            }
        }
        double va = saa - sa * sa / n, vb = sbb - sb * sb / n;
        if (!(va > 0) || !(vb > 0)) return 0;
        return (sab - sa * sb / n) / Math.sqrt(va * vb);
    }

    /**
     * Detection on each picked frame: without continuity, the expected position of the object is its reference
     * position shifted by the global drift, estimated on the whole image. As during tracking, only a candidate
     * close to this position is taken for the object; the best other object is kept apart.
     */
    CalRes[] detectCal(int[] frames) {
        int calR = Math.min(Math.max(3 * radius, 30), Math.max(W, H));
        double sep = 0.5 * Math.max(bw, bh), tol = Math.max(4, 0.3 * Math.max(bw, bh));
        CalRes[] res = new CalRes[frames.length];
        for (int i = 0; i < frames.length; i++) {
            IJ.showProgress(i, frames.length);
            CalRes r = new CalRes();
            r.t = frames[i];
            double[] g = globalShift(r.t, calR);
            r.predX = bx + g[0];
            r.predY = by + g[1];
            List<Cand> cs = findCandidates(r.t, r.predX, r.predY, calR);
            r.level = lastLevel;
            for (Cand c : cs)
                if (c.dist <= tol && (r.best == null || c.score - 0.01 * c.dist > r.best.score - 0.01 * r.best.dist)) r.best = c;
            double ox = r.best != null ? r.best.x : r.predX, oy = r.best != null ? r.best.y : r.predY;
            for (Cand c : cs)
                if (Math.hypot(c.x - ox, c.y - oy) > sep && (r.second == null || c.std > r.second.std)) r.second = c;
            if (r.second != null) r.secDist = Math.hypot(r.second.x - ox, r.second.y - oy);
            if (r.best == null || r.best.score < lost) r.status = 2;
            else if (r.best.kept < MIN_VISIBLE) r.status = 3;
            else r.status = r.best.score >= doubtful ? 0 : 1;
            res[i] = r;
        }
        IJ.showProgress(1.0);
        return res;
    }

    /** Suggested thresholds from the calibration: {doubtful, lost, ambiguous frames, distance}, or null. */
    double[] suggest(CalRes[] res) {
        List<Double> sc = new ArrayList<Double>();
        double sec = 0, near = Double.MAX_VALUE;
        int ambig = 0;
        for (CalRes r : res) {
            if (r.best != null && r.best.kept >= MIN_VISIBLE && r.best.score >= 0.2) sc.add(r.best.score);
            if (r.second != null) {
                sec = Math.max(sec, r.second.std);
                if (r.best != null && r.best.score - r.second.std < 0.1) { ambig++; near = Math.min(near, r.secDist); }
            }
        }
        if (sc.size() < 3) return null;
        double med = Predictor.med(sc);
        List<Double> dev = new ArrayList<Double>();
        for (double v : sc) dev.add(Math.abs(v - med));
        double mad = 1.4826 * Predictor.med(dev);
        double d = Math.max(0.3, Math.min(0.97, med - Math.max(0.05, 3 * mad)));
        // with correlation, "lost" above the other objects; with thresholding identical objects have the same score
        double p = detMethod == D_THRESHOLD ? d - 0.3 : Math.max(0.3, sec + 0.05);
        p = Math.max(0.2, Math.min(d - 0.05, p));
        return new double[]{Math.round(100 * d) / 100.0, Math.round(100 * p) / 100.0, detMethod == D_THRESHOLD ? 0 : ambig, near};
    }

    String calSummary(CalRes[] res, double[] sg) {
        int[] n = new int[4];
        List<Double> sc = new ArrayList<Double>();
        double mn = 2, mx = -1, sec = -1;
        for (CalRes r : res) {
            n[r.status]++;
            if (r.best != null) { sc.add(r.best.score); mn = Math.min(mn, r.best.score); mx = Math.max(mx, r.best.score); }
            if (r.second != null) sec = Math.max(sec, r.second.std);
        }
        StringBuilder b = new StringBuilder();
        b.append(res.length).append(" frames picked at random (one per section of the movie): ").append(n[0])
                .append(" reliable, ").append(n[1] + n[3]).append(" doubtful, ").append(n[2]).append(" not found.\n");
        if (!sc.isEmpty()) {
            b.append("Score of the object: median ").append(f2(Predictor.med(sc))).append(" (").append(f2(mn))
                    .append(" to ").append(f2(mx)).append(")");
            if (detMethod == D_CORREL && sec >= 0) b.append("; best other object: ").append(f2(sec));
            b.append(".\n");
        }
        if (sg != null) b.append("Suggested thresholds: doubtful if score < ").append(f2(sg[0])).append(", lost if < ")
                .append(f2(sg[1])).append(".\n");
        else b.append("Too few frames where the object is found to suggest thresholds.\n");
        if (sg != null && sg[2] > 0)
            b.append("Warning: on ").append((int) sg[2]).append(" frame(s), another object looks almost as similar (")
                    .append(Math.round(sg[3])).append(" px away or more). Tracking then relies on continuity;\n")
                    .append("if it jumps, outline a more distinctive object or try thresholding.\n");
        if (detMethod == D_THRESHOLD)
            b.append("Thresholding: reference object of ").append(Math.round(refArea))
                    .append(" px; score = size similarity (1 = same area).\n");
        int far = 0;
        for (CalRes r : res) if (r.status == 2 && r.second != null && r.second.std >= doubtful) far++;
        if (far * 2 >= res.length)
            b.append("On ").append(far).append(" frames, the object is not where the global drift expects it, but a similar ")
                    .append("object exists further away:\nif many things move in the image this test is pessimistic; ")
                    .append("otherwise, check the outlined object.\n");
        b.append("Montage: green = reliable, orange = doubtful, red = not found (circle: expected position), cyan = 2nd candidate");
        if (detMethod == D_THRESHOLD) b.append(",\nred tint = pixels kept by the threshold");
        return b.append(".").toString();
    }

    /**
     * Calibration (as in Count and Colocalise): the settings are tried on frames picked at random; a montage
     * shows the result; you adjust, redraw, then accept. false if cancelled.
     */
    boolean calibrationPhase() {
        Random rnd = new Random();
        int[] frames = pickFrames(rnd);
        ImagePlus prev = null;
        try {
            while (true) {
                boolean ok = buildModel(true);
                CalRes[] res = ok ? detectCal(frames) : null;
                lastCalRes = res;
                double[] sg = ok ? suggest(res) : null;
                lastCalSummary = ok ? calSummary(res, sg) : "Problem: " + modelError;
                prev = drawCal(prev, frames, res);
                NonBlockingGenericDialog gd = new NonBlockingGenericDialog("Calibration - " + TITLE);
                if (prev != null && prev.getWindow() != null) {      // next to the montage, without hiding it
                    Rectangle mb = prev.getWindow().getBounds();
                    Dimension scr = Toolkit.getDefaultToolkit().getScreenSize();
                    gd.setLocation(Math.max(0, Math.min(mb.x + mb.width + 10, scr.width - 660)), mb.y);
                }
                gd.addMessage(lastCalSummary);
                gd.addChoice("Detection", DETECTIONS, DETECTIONS[detMethod]);
                gd.addToSameRow();
                gd.addChoice("Object", POLARITIES, POLARITIES[dark ? 1 : 0]);
                gd.addSlider("Doubtful if score <", 0, 1, doubtful, 0.01);
                gd.addSlider("Lost if score <", 0, 1, lost, 0.01);
                gd.addNumericField("Smoothing (sigma)", sigma, 1, 5, "px");
                gd.addToSameRow();
                gd.addNumericField("Background (rolling ball radius)", bgRadius, 0, 5, "px, 0 = none");
                gd.addChoice("Thresholding method", THR_METHODS, thrMethod);
                gd.addToSameRow();
                gd.addNumericField("Threshold factor", thrFactor, 2, 5, "");
                gd.addNumericField("Minimum area", areaMin, 2, 5, "x object");
                gd.addToSameRow();
                gd.addNumericField("Maximum area", areaMax, 2, 5, "x object");
                gd.addNumericField("Radius", radius, 0, 5, "px (search)");
                gd.addToSameRow();
                gd.addNumericField("Number of frames", nCalib, 0, 4, "");
                gd.addCheckbox("New random frames", false);
                if (sg != null) {
                    gd.addToSameRow();
                    gd.addCheckbox("Use the suggested thresholds (" + f2(sg[0]) + " / " + f2(sg[1]) + ")", false);
                }
                gd.addMessage("\"Redraw\" applies the settings to the montage; \"Accept\" keeps them and starts the tracking.",
                        null, Color.darkGray);
                gd.enableYesNoCancel("Redraw", "Accept and start tracking");
                gd.addHelp(HELP);
                gd.showDialog();
                if (gd.wasCanceled()) return false;
                boolean validate = !gd.wasOKed();
                int oldN = nCalib;
                detMethod = gd.getNextChoiceIndex();
                dark = gd.getNextChoiceIndex() == 1;
                doubtful = gd.getNextNumber();
                lost = gd.getNextNumber();
                sigma = gd.getNextNumber();
                bgRadius = gd.getNextNumber();
                thrMethod = THR_METHODS[gd.getNextChoiceIndex()];
                thrFactor = gd.getNextNumber();
                areaMin = gd.getNextNumber();
                areaMax = gd.getNextNumber();
                radius = (int) Math.round(gd.getNextNumber());
                nCalib = (int) Math.round(gd.getNextNumber());
                boolean redraw = gd.getNextBoolean();
                if (sg != null && gd.getNextBoolean()) { doubtful = sg[0]; lost = sg[1]; }
                if (gd.invalidNumber()) IJ.error(TITLE, "One of the numbers is not valid.");
                sanitize();
                if (redraw || nCalib != oldN) frames = pickFrames(rnd);
                if (validate && buildModel(false)) {
                    savePrefs();
                    return true;
                }
            }
        } finally {
            if (prev != null) {
                prev.changes = false;
                prev.close();
            }
            IJ.showProgress(1.0);
        }
    }

    /** Calibration montage: one tile per picked frame, centred on the object found. */
    ImagePlus drawCal(ImagePlus prev, int[] frames, CalRes[] res) {
        int n = frames.length;
        int tile = Math.min(Math.min(W, H), Math.max(48, Math.min(160, 4 * Math.max(bw, bh))));
        int cols = (int) Math.ceil(Math.sqrt(n)), rows = (n + cols - 1) / cols, gap = 6;
        Dimension scr = Toolkit.getDefaultToolkit().getScreenSize();
        int fitW = (int) (0.5 * scr.width) / cols - gap, fitH = (int) (0.8 * scr.height) / rows - gap;
        int k = Math.max(1, Math.min(4, Math.min(200, Math.min(fitW, fitH)) / tile)), ts = tile * k;
        ColorProcessor mont = new ColorProcessor(cols * (ts + gap) + gap, rows * (ts + gap) + gap);
        mont.setColor(new Color(45, 45, 45));
        mont.fill();
        for (int i = 0; i < n; i++) {
            CalRes r = res == null ? null : res[i];
            Cand c = r == null ? null : r.best;
            double cx = (c != null ? c.x : r != null ? r.predX : bx) + offX, cy = (c != null ? c.y : r != null ? r.predY : by) + offY;
            int tx0 = clamp((int) Math.round(cx - tile / 2.0), 0, W - tile), ty0 = clamp((int) Math.round(cy - tile / 2.0), 0, H - tile);
            mont.insert(calTile(frames[i], r, tx0, ty0, tile, k), gap + (i % cols) * (ts + gap), gap + (i / cols) * (ts + gap));
        }
        if (prev != null && prev.getWindow() != null && prev.getWidth() == mont.getWidth() && prev.getHeight() == mont.getHeight()) {
            prev.setProcessor(mont);
            prev.updateAndDraw();
            return prev;
        }
        if (prev != null) {
            prev.changes = false;
            prev.close();
        }
        ImagePlus p = new ImagePlus("Calibration - " + imp.getTitle(), mont);
        p.show();
        if (p.getWindow() != null) p.getWindow().setLocation(10, 80);
        return p;
    }

    ColorProcessor calTile(int t, CalRes r, int tx0, int ty0, int tile, int k) {
        float[] d = region(t, tx0, ty0, tile, tile);
        float[] s = d.clone();
        Arrays.sort(s);
        double lo = s[(int) (0.01 * (s.length - 1))], hi = s[(int) (0.995 * (s.length - 1))];
        if (!(hi > lo)) hi = lo + 1;
        boolean mask = detMethod == D_THRESHOLD && r != null && !Double.isNaN(r.level);
        int ts = tile * k;
        ColorProcessor cp = new ColorProcessor(ts, ts);
        int[] px = (int[]) cp.getPixels();
        for (int y = 0; y < tile; y++)
            for (int x = 0; x < tile; x++) {
                double v = d[y * tile + x];
                int g = (int) Math.max(0, Math.min(255, Math.round(255 * (v - lo) / (hi - lo))));
                int rgb = (g << 16) | (g << 8) | g;
                if (mask && (dark ? -v : v) > r.level) rgb = (((g + 255) / 2) << 16) | ((g / 2) << 8) | (g / 2);
                for (int j = 0; j < k; j++) {
                    int o = (y * k + j) * ts + x * k;
                    Arrays.fill(px, o, o + k, rgb);
                }
            }
        Color col = r == null ? Color.gray : CAL_COLOR[r.status];
        if (r != null && r.second != null) drawShape(cp, r.second, tx0, ty0, k, Color.cyan, 1);
        if (r != null && r.best != null) drawShape(cp, r.best, tx0, ty0, k, col, 2);
        if (r != null && r.best == null) {             // object absent: where it was expected
            Cand e = new Cand();
            e.x = r.predX;
            e.y = r.predY;
            drawShape(cp, e, tx0, ty0, k, col, 1);
        }
        cp.setColor(col);
        cp.setLineWidth(2);
        cp.drawRect(0, 0, ts, ts);
        cp.setLineWidth(1);
        cp.setFont(new Font("SansSerif", Font.BOLD, 11));
        cp.setAntialiasedText(true);
        label(cp, "t=" + t + (r == null || r.best == null ? "" : "  " + f2(r.best.score))
                + (r == null ? "" : "  " + CAL_STATUS[r.status]), 3, 3);
        if (r != null && r.second != null) label(cp, "2nd: " + f2(r.second.std) + " at " + Math.round(r.secDist) + " px", 3, 19);
        return cp;
    }

    /** Outline of the object (shape of the selection) for candidate c, in a tile (origin tx0, ty0, zoom k). */
    void drawShape(ImageProcessor ip, Cand c, int tx0, int ty0, int k, Color col, int lw) {
        FloatPolygon fp = shape.getFloatPolygon();
        double ox = c.x + shapeDx - shape.getXBase(), oy = c.y + shapeDy - shape.getYBase();
        float[] xs = new float[fp.npoints], ys = new float[fp.npoints];
        for (int i = 0; i < fp.npoints; i++) {
            xs[i] = (float) ((fp.xpoints[i] + ox - tx0) * k);
            ys[i] = (float) ((fp.ypoints[i] + oy - ty0) * k);
        }
        ip.setColor(col);
        ip.setLineWidth(lw);
        new PolygonRoi(xs, ys, fp.npoints, Roi.POLYGON).drawPixels(ip);
        ip.setLineWidth(1);
    }

    static void label(ImageProcessor ip, String s, int x, int y) {
        ip.setColor(Color.black);
        ip.fillRect(x, y, ip.getStringWidth(s) + 6, 15);
        ip.setColor(Color.white);
        ip.drawString(s, x + 3, y + 14);
    }

    // ===================================================================== application
    ImagePlus apply() {
        boolean integer = registration == 0, cubic = registration == 2;
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        double[] ux = new double[nT + 1], uy = new double[nT + 1];
        for (int t = 1; t <= nT; t++) {
            ux[t] = integer ? Math.round(px[t]) : px[t];
            uy[t] = integer ? Math.round(py[t]) : py[t];
            minX = Math.min(minX, ux[t]); maxX = Math.max(maxX, ux[t]);
            minY = Math.min(minY, uy[t]); maxY = Math.max(maxY, uy[t]);
        }
        int w2 = (int) Math.floor(W - (maxX - minX) + 1e-6);
        int h2 = (int) Math.floor(H - (maxY - minY) + 1e-6);
        if (w2 < 1 || h2 < 1) {
            IJ.error(TITLE, "The drift is larger than the image: no area is common to all frames.");
            return null;
        }
        int N = stack.getSize();
        int bytes = imp.getBitDepth() == 24 ? 4 : Math.max(1, imp.getBitDepth() / 8);
        long need = (long) w2 * h2 * N * bytes;
        long max = IJ.maxMemory();
        if (max > 0 && need > 0.9 * (max - IJ.currentMemory())) {
            IJ.error(TITLE, "Not enough memory to create the corrected movie (" + (need >> 20) + " MB).\n"
                    + "Increase the memory (Edit > Options > Memory & Threads) or close other images.");
            return null;
        }
        ImageStack out = new ImageStack(w2, h2);
        for (int n = 1; n <= N; n++) {
            int[] pos = imp.convertIndexToPosition(n);
            int t = timeIsZ ? pos[1] : pos[2];
            ImageProcessor ip = stack.getProcessor(n);
            double ox = ux[t] - minX, oy = uy[t] - minY;
            ImageProcessor ip2;
            if (integer) {
                ip.setRoi((int) Math.round(ox), (int) Math.round(oy), w2, h2);
                ip2 = ip.crop();
                ip.resetRoi();
            } else ip2 = shiftCrop(ip, ox, oy, w2, h2, cubic);
            out.addSlice(stack.getSliceLabel(n), ip2);
            IJ.showProgress(n, N);
            IJ.showStatus("Drift correction: " + n + "/" + N);
        }
        String base = imp.getTitle();
        int dot = base.lastIndexOf('.');
        if (dot > 0 && base.length() - dot <= 5) base = base.substring(0, dot);
        ImagePlus res = new ImagePlus(WindowManager.makeUniqueName(base + "_driftcorr"), out);
        res.setDimensions(nC, imp.getNSlices(), imp.getNFrames());
        Calibration cal = imp.getCalibration().copy();
        res.setCalibration(cal);
        Object info = imp.getProperty("Info");
        if (info != null) res.setProperty("Info", info);
        if (imp.isHyperStack() || nC > 1) res.setOpenAsHyperStack(true);
        if (imp.isComposite() && nC > 1) {
            CompositeImage ci = new CompositeImage(res, ((CompositeImage) imp).getMode());
            ci.copyLuts(imp);
            res = ci;
        } else if (imp.getBitDepth() != 24) {
            res.getProcessor().setLut(imp.getProcessor().getLut());
            res.setDisplayRange(imp.getDisplayRangeMin(), imp.getDisplayRangeMax());
        }
        res.setProperty("drift.crop", w2 + "x" + h2);
        lastCrop = new int[]{w2, h2};
        return res;
    }

    int[] lastCrop;
    ImagePlus lastResult;

    static ImageProcessor shiftCrop(ImageProcessor ip, double ox, double oy, int w2, int h2, boolean cubic) {
        int w = ip.getWidth(), h = ip.getHeight();
        if (ip instanceof ColorProcessor) {
            ColorProcessor cp = (ColorProcessor) ip, out = new ColorProcessor(w2, h2);
            for (int ch = 0; ch < 3; ch++) {
                float[] f = (float[]) cp.toFloat(ch, null).getPixels();
                out.setPixels(ch, new FloatProcessor(w2, h2, shiftFloat(f, w, h, ox, oy, w2, h2, cubic), null));
            }
            return out;
        }
        float[] f = ip instanceof FloatProcessor ? (float[]) ip.getPixels() : (float[]) ip.convertToFloat().getPixels();
        float[] r = shiftFloat(f, w, h, ox, oy, w2, h2, cubic);
        if (ip instanceof FloatProcessor) return new FloatProcessor(w2, h2, r, null);
        if (ip instanceof ShortProcessor) {
            short[] s = new short[r.length];
            for (int k = 0; k < r.length; k++) s[k] = (short) Math.max(0, Math.min(65535, Math.round(r[k])));
            return new ShortProcessor(w2, h2, s, null);
        }
        byte[] b = new byte[r.length];
        for (int k = 0; k < r.length; k++) b[k] = (byte) Math.max(0, Math.min(255, Math.round(r[k])));
        return new ByteProcessor(w2, h2, b, null);
    }

    /** Samples s at (u + ox, v + oy); separable bilinear or bicubic (Keys) interpolation. */
    static float[] shiftFloat(final float[] s, final int w, final int h, double ox, double oy,
                              final int w2, final int h2, boolean cubic) {
        final int ix = (int) Math.floor(ox + 1e-9), iy = (int) Math.floor(oy + 1e-9);
        double fx = ox - ix, fy = oy - iy;
        if (fx < 1e-6) fx = 0;
        if (fy < 1e-6) fy = 0;
        final double[] wx = weights(fx, cubic), wy = weights(fy, cubic);
        final int o0 = cubic ? -1 : 0, nk = wx.length;
        final int rows0 = iy + o0, nrows = h2 + nk - 1;
        final float[] tmp = new float[nrows * w2];
        IntStream.range(0, nrows).parallel().forEach(j -> {
            int yy = Math.max(0, Math.min(h - 1, rows0 + j));
            int base = yy * w;
            for (int u = 0; u < w2; u++) {
                double acc = 0;
                for (int a = 0; a < nk; a++) {
                    int xx = Math.max(0, Math.min(w - 1, u + ix + o0 + a));
                    acc += wx[a] * s[base + xx];
                }
                tmp[j * w2 + u] = (float) acc;
            }
        });
        final float[] r = new float[w2 * h2];
        IntStream.range(0, h2).parallel().forEach(v -> {
            for (int u = 0; u < w2; u++) {
                double acc = 0;
                for (int b = 0; b < nk; b++) acc += wy[b] * tmp[(v + b) * w2 + u];
                r[v * w2 + u] = (float) acc;
            }
        });
        return r;
    }

    static double[] weights(double f, boolean cubic) {
        if (!cubic) return new double[]{1 - f, f};
        return new double[]{keys(1 + f), keys(f), keys(1 - f), keys(2 - f)};
    }

    static double keys(double x) {
        final double a = -0.5;
        x = Math.abs(x);
        if (x <= 1) return ((a + 2) * x - (a + 3)) * x * x + 1;
        if (x < 2) return ((a * x - 5 * a) * x + 8 * a) * x - 4 * a;
        return 0;
    }

    // ===================================================================== bilan
    void report(ImagePlus res, double seconds) {
        Calibration cal = imp.getCalibration();
        boolean scaled = cal.scaled();
        String unit = cal.getUnit();
        ResultsTable rt = new ResultsTable();
        int doubts = 0, clicks = 0, interp = 0, valid = 0, autos = 0;
        List<Integer> doubtList = new ArrayList<Integer>(), lostList = new ArrayList<Integer>();
        double dxMin = 0, dxMax = 0, dyMin = 0, dyMax = 0;
        for (int t = 1; t <= nT; t++) {
            double dx = px[t] - px[refT], dy = py[t] - py[refT];
            dxMin = Math.min(dxMin, dx); dxMax = Math.max(dxMax, dx);
            dyMin = Math.min(dyMin, dy); dyMax = Math.max(dyMax, dy);
            rt.incrementCounter();
            rt.addValue("Frame", t);
            rt.addValue("X object (px)", px[t] + offX);
            rt.addValue("Y object (px)", py[t] + offY);
            rt.addValue("dX (px)", dx);
            rt.addValue("dY (px)", dy);
            if (scaled) {
                rt.addValue("dX (" + unit + ")", dx * cal.pixelWidth);
                rt.addValue("dY (" + unit + ")", dy * cal.pixelHeight);
            }
            rt.addValue("Score", score[t]);
            rt.addValue("Visible (%)", Double.isNaN(visibleFrac[t]) ? Double.NaN : Math.round(100 * visibleFrac[t]));
            rt.addValue("Source", SRC_NAME[src[t]]);
            rt.addValue("Note", why[t] == null ? "" : why[t]);
            if (src[t] == DOUBT) { doubts++; doubtList.add(t); }
            if (src[t] == CLICK || src[t] == CLICK_RAW) clicks++;
            if (src[t] == INTERP) interp++;
            if (src[t] == LOST || src[t] == FIELD) lostList.add(t);
            if (src[t] == VALIDATED) valid++;
            if (src[t] == AUTO) autos++;
        }
        rt.setPrecision(3);
        rt.show("Drift - " + imp.getTitle());
        lastTable = rt;

        IJ.log("=== " + TITLE + ": " + imp.getTitle() + " ===");
        IJ.log("Mode: " + MODES[mode] + "; reference: frame " + refT + ", object at ("
                + f1(px[refT] + offX) + ", " + f1(py[refT] + offY) + ") px, channel " + trackC
                + (nZ > 1 ? (zProj ? ", max Z projection" : ", plane z=" + trackZ) : ""));
        IJ.log("Detection: " + detectionText() + "; doubtful if score < " + f2(doubtful) + ", lost if < " + f2(lost));
        IJ.log("Drift relative to frame " + refT + ": X from " + f1(dxMin) + " to " + f1(dxMax)
                + " px, Y from " + f1(dyMin) + " to " + f1(dyMax) + " px"
                + (scaled ? "  (pixel = " + IJ.d2s(cal.pixelWidth, 4) + " " + unit + ")" : ""));
        IJ.log("Area kept: " + lastCrop[0] + " x " + lastCrop[1] + " px (of " + W + " x " + H
                + "); registration: " + REGISTRATION[registration]);
        StringBuilder sb = new StringBuilder();
        sb.append("Positions: ").append(autos).append(" reliable auto");
        if (valid > 0) sb.append(", ").append(valid).append(" validated");
        if (clicks > 0) sb.append(", ").append(clicks).append(" pointed by hand");
        if (interp > 0) sb.append(", ").append(interp).append(" interpolated");
        sb.append(" (").append(IJ.d2s(seconds, 1)).append(" s)");
        IJ.log(sb.toString());
        if (!lostList.isEmpty())
            IJ.log("Object lost or disturbed (position interpolated or estimated on the whole field): " + lostList.size()
                    + " frame(s): " + ranges(lostList));
        if (doubts > 0)
            IJ.log("To check: " + doubts + " frame(s) (stage jump or doubtful detection): " + ranges(doubtList)
                    + " (see the table, column Note).");
        if (!Interpreter.isBatchMode()) updatePlot();
    }

    ResultsTable lastTable;

    static String ranges(List<Integer> l) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l.size(); ) {
            int a = l.get(i), j = i;
            while (j + 1 < l.size() && l.get(j + 1) == l.get(j) + 1) j++;
            if (sb.length() > 0) sb.append(", ");
            sb.append(a);
            if (j > i) sb.append("-").append(l.get(j));
            i = j + 1;
        }
        return sb.toString();
    }

    static String f1(double v) { return String.format(Locale.US, "%.1f", v); }
    static String f2(double v) { return String.format(Locale.US, "%.2f", v); }
    static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    // ===================================================================== interface
    static final class Ev {
        final String id;
        final int frame;
        final double x, y;
        Ev(String id, int frame, double x, double y) { this.id = id; this.frame = frame; this.x = x; this.y = y; }
    }

    static void onEDT(Runnable r) {
        if (SwingUtilities.isEventDispatchThread()) { r.run(); return; }
        try { SwingUtilities.invokeAndWait(r); }
        catch (Exception e) { IJ.log(TITLE + " : " + e); }
    }

    /** Non-modal control panel + listening to the clicks on the image. */
    final class Session {
        JDialog dlg;
        JLabel text, statusLabel;
        JPanel buttons;
        final LinkedBlockingQueue<Ev> q = new LinkedBlockingQueue<Ev>();
        final ImageCanvas canvas;
        final MouseAdapter mouse;
        final int savedTool;
        final Overlay savedOverlay;
        final boolean savedAutoMeasure, savedAddToManager, savedAddToOverlay;
        volatile Runnable idle;

        Session() {
            canvas = imp.getCanvas();
            savedTool = Toolbar.getToolId();
            savedOverlay = imp.getOverlay();
            savedAutoMeasure = Prefs.pointAutoMeasure;
            savedAddToManager = Prefs.pointAddToManager;
            savedAddToOverlay = Prefs.pointAddToOverlay;
            Prefs.pointAutoMeasure = false;
            Prefs.pointAddToManager = false;
            Prefs.pointAddToOverlay = false;
            onEDT(new Runnable() { public void run() { build(); } });
            mouse = new MouseAdapter() {
                int sx, sy;
                boolean down;

                public void mousePressed(MouseEvent e) { sx = e.getX(); sy = e.getY(); down = true; }

                public void mouseReleased(MouseEvent e) {
                    if (!down) return;
                    down = false;
                    if (Math.abs(e.getX() - sx) > 4 || Math.abs(e.getY() - sy) > 4) return;   // glisser
                    if (e.getButton() != MouseEvent.BUTTON1 || e.isPopupTrigger() || e.isControlDown()
                            || e.isAltDown() || e.isMetaDown() || IJ.spaceBarDown()) return;
                    int tool = Toolbar.getToolId();
                    if (tool == Toolbar.MAGNIFIER || tool == Toolbar.HAND) return;
                    // centre of the clicked screen pixel (offScreenXD gives its left / top edge)
                    double half = 0.5 / canvas.getMagnification();
                    double x = canvas.offScreenXD(e.getX()) + half, y = canvas.offScreenYD(e.getY()) + half;
                    q.offer(new Ev("click", currentTime(), x, y));
                    EventQueue.invokeLater(new Runnable() { public void run() { imp.deleteRoi(); } });
                }
            };
            if (canvas != null) canvas.addMouseListener(mouse);
            imp.deleteRoi();
            IJ.setTool(Toolbar.POINT);
        }

        void build() {
            Window owner = imp.getWindow();
            dlg = new JDialog(owner, TITLE, Dialog.ModalityType.MODELESS);
            dlg.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            dlg.addWindowListener(new WindowAdapter() {
                public void windowClosing(WindowEvent e) { q.offer(new Ev("cancel", 0, 0, 0)); }
            });
            JPanel p = new JPanel(new BorderLayout(8, 8));
            p.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
            text = new JLabel();
            statusLabel = new JLabel(" ");
            statusLabel.setForeground(new Color(0, 80, 160));
            buttons = new JPanel(new GridLayout(0, 2, 6, 6));
            p.add(text, BorderLayout.NORTH);
            p.add(statusLabel, BorderLayout.CENTER);
            p.add(buttons, BorderLayout.SOUTH);
            dlg.setContentPane(p);
            dlg.pack();
            place();
            dlg.setVisible(true);
        }

        /** Screen (monitor) holding the image. */
        Rectangle screen() {
            Window owner = imp.getWindow();
            if (owner != null && owner.getGraphicsConfiguration() != null)
                return owner.getGraphicsConfiguration().getBounds();
            Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
            return new Rectangle(0, 0, d.width, d.height);
        }

        /** Right of the image if possible, otherwise left, always fully on screen. */
        void place() {
            Window owner = imp.getWindow();
            Rectangle scr = screen();
            int w = dlg.getWidth(), h = dlg.getHeight();
            int x = scr.x + 20, y = scr.y + 60;
            if (owner != null) {
                Rectangle o = owner.getBounds();
                x = o.x + o.width + 8;
                y = o.y;
                if (x + w > scr.x + scr.width) x = o.x - w - 8;
            }
            x = Math.max(scr.x, Math.min(x, scr.x + scr.width - w));
            y = Math.max(scr.y, Math.min(y, scr.y + scr.height - h));
            dlg.setLocation(x, y);
        }

        /** After a change of size: the panel must not go off the screen. */
        void keepOnScreen() {
            Rectangle scr = screen(), b = dlg.getBounds();
            if (b.x + b.width > scr.x + scr.width || b.y + b.height > scr.y + scr.height || b.x < scr.x || b.y < scr.y)
                place();
        }

        void phase(final String html, final String... ids) {
            onEDT(new Runnable() {
                public void run() {
                    text.setText("<html><body style='width:330px'>" + html + "</body></html>");
                    buttons.removeAll();
                    for (int i = 0; i + 1 < ids.length; i += 2) {
                        final String id = ids[i];
                        JButton b = new JButton(ids[i + 1]);
                        b.setActionCommand(id);
                        b.addActionListener(new ActionListener() {
                            public void actionPerformed(ActionEvent e) { q.offer(new Ev(id, 0, 0, 0)); }
                        });
                        buttons.add(b);
                    }
                    dlg.pack();
                    keepOnScreen();
                    dlg.validate();
                    dlg.repaint();
                }
            });
        }

        void status(final String s) {
            final String html = "<html><body style='width:330px'>"
                    + s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") + "</body></html>";
            EventQueue.invokeLater(new Runnable() {
                public void run() {
                    if (statusLabel == null) return;
                    statusLabel.setText(html);
                    dlg.pack();
                    keepOnScreen();
                }
            });
        }

        void toFront() {
            EventQueue.invokeLater(new Runnable() { public void run() { if (dlg != null) dlg.toFront(); } });
        }

        void clear() { q.clear(); }

        /** Waits for an action (click or button). Returns "cancel" if the image or the panel is closed. */
        Ev next() {
            while (true) {
                Ev e;
                try { e = q.poll(200, TimeUnit.MILLISECONDS); }
                catch (InterruptedException ex) { return new Ev("cancel", 0, 0, 0); }
                if (e != null) return e;
                if (imp.getWindow() == null) return new Ev("cancel", 0, 0, 0);
                Runnable r = idle;
                if (r != null) r.run();
            }
        }

        /** During tracking: true if "Cancel" was clicked (clicks on the image are ignored). */
        boolean cancelRequested() {
            if (imp.getWindow() == null) return true;
            Ev e;
            boolean c = false;
            while ((e = q.poll()) != null) if (e.id.equals("cancel")) c = true;
            if (IJ.escapePressed()) { IJ.resetEscape(); c = true; }
            return c;
        }

        void close() {
            if (canvas != null) canvas.removeMouseListener(mouse);
            onEDT(new Runnable() { public void run() { if (dlg != null) dlg.dispose(); } });
            Prefs.pointAutoMeasure = savedAutoMeasure;
            Prefs.pointAddToManager = savedAddToManager;
            Prefs.pointAddToOverlay = savedAddToOverlay;
            if (imp.getWindow() != null) {
                imp.setOverlay(savedOverlay);
                if (userRoi != null) imp.setRoi((Roi) userRoi.clone());
            }
            IJ.setTool(savedTool);
        }
    }
}
