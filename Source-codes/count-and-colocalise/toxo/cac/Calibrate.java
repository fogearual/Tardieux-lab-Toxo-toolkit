package toxo.cac;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.NonBlockingGenericDialog;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.gui.TextRoi;
import ij.gui.WaitForUserDialog;
import ij.io.OpenDialog;
import ij.measure.Calibration;
import ij.plugin.ChannelSplitter;
import ij.plugin.RGBStackMerge;
import ij.plugin.filter.ThresholdToSelection;
import ij.process.Blitter;

import java.awt.Font;
import java.awt.Rectangle;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Calibrate, as in the macro version: one object class, one or several images. A preview stack shows, for each
 * image, the raw channel, then the same view with what is counted drawn over it (an overlay); a panel holds every
 * setting of that class. OK redraws the preview with the values of the panel, until "Keep" sends them back to the
 * detection dialog (or "Discard" restores the ones it had). Only the steps a changed setting affects are redone.
 */
final class Calibrate {

    static final String[] SRC_MODES = {"One image I choose", "One image picked at random",
            "One random image from each condition"};
    static final String ACT_REDRAW = "Redraw the preview with these values";
    static final String ACT_KEEP = "Keep these values and go back";
    static final String ACT_DISCARD = "Discard the changes and go back";
    static final String ACT_LOOK = "Redraw, then let me zoom and scroll the preview";

    final Plugin P;
    final Settings S;
    final String[] roleCols;

    /** One calibration image: its channels, as projected and calibrated in the batch. */
    static final class Img {
        ImagePlus blue, green, red, pole;
        String lab;
        double pxV;
        String pxU = "um", pxS = "";
        final Slot[] slot = {new Slot(), new Slot(), new Slot()};   // nuclei, parasites, marker
    }

    /** Cache of the slow steps of one class on one image: the corrected channel, its mask, and the thresholds. */
    static final class Slot {
        String keyP = "", keyM = "";
        ImagePlus pre, bin;
        double thr, thrA, medT, maxT;
        int hit;
        String note = "";
    }

    String calSrc = "";
    final List<Img> imgs = new ArrayList<Img>();
    int calSkip, calMem;
    String calChosenLab = "";
    int nv = 2;
    ImagePlus prev;
    String viewGains = "", lastKey = "", summary = "";
    // counts of the image just drawn, and the totals of a redraw
    double cntA, cntB, cntC, cntD, cntE;
    int capHits, spikeHits;
    double[] poleT = new double[6];
    double objT;
    String imgLine = "";
    int slN;
    String action = "";
    // settings before calibrating, for "Discard"
    double[] bak;
    String[] bakS;

    Calibrate(Plugin p, String[] roleCols) {
        this.P = p;
        this.S = p.S;
        this.roleCols = roleCols;
    }

    /** 1 calibrated, 0 nothing to calibrate on, -1 the whole tool is cancelled. */
    int calibrate(String what, String src) {
        calSrc = src;
        save();
        // the random choices draw from the list of images to process
        if (!src.equals(SRC_MODES[0])) {
            int nFound = P.collectImages();
            if (nFound == 0) {
                IJ.showMessage("Calibrate", "No image to pick from in this folder.");
                return 0;
            }
        }
        if (!load(what, src)) {
            closeTemp();
            return 0;
        }
        prev = preview(what);
        lastKey = "";
        redraw(what);

        boolean done = false;
        while (!done) {
            if (!dialog(what)) action = ACT_DISCARD;
            if (action.equals(ACT_KEEP)) {
                done = true;
            } else if (action.equals(ACT_DISCARD)) {
                restore();
                done = true;
            } else {
                update(what);
                if (action.equals(ACT_LOOK))
                    new WaitForUserDialog("Zoom and scroll through the preview freely.\n"
                            + "Click OK to come back to the settings.").show();
            }
        }

        // The preview stays open, showing what the settings now in force count.
        update(what);
        closeTemp();
        prev.setSlice(2);
        return 1;
    }

    // ================================================================== the images
    /** Opens the image(s) to calibrate on. True if at least one image is usable. */
    boolean load(String what, String src) {
        imgs.clear();
        calSkip = 0;
        calMem = 0;
        if (src.equals(SRC_MODES[0])) {
            Img im = S.tiffMode ? openChosenTiff(what) : openChosen(what);
            if (im != null) keep(im, calChosenLab);
            return !imgs.isEmpty();
        }
        int[] picks = pickIndices(src);
        int nvL = what.equals("double") ? 3 : 2;
        double calNeed = 0;
        for (int j = 0; j < picks.length; j++) {
            int i = picks[j];
            IJ.showStatus("Calibrate: opening image " + (j + 1) + " / " + picks.length);
            IJ.showProgress(j + 1, picks.length);
            Input.Entry e = P.entries.get(i);
            Img im = S.tiffMode ? openTiffEntry(e, what) : openFile(e, what);
            if (im != null) {
                String lab = e.cond;
                if (!e.manip.equals("-")) lab = e.manip + " / " + lab;
                String nmI = new File(e.path).getName();
                if (S.tiffMode) nmI = e.tBase;
                else if (e.seriesTotal > 1) nmI = nmI + " (position " + e.series + ")";
                lab = lab + " / " + nmI;
                // The preview holds, per image, nvL RGB views and the overlay: about 5 x nvL bytes per pixel; the
                // corrected channel and mask kept to redraw faster, 3 more (6 for the double labelling). Images that
                // would not fit in memory are left out (the first one is always kept).
                ImagePlus ch = what.equals("nuclei") ? im.blue : what.equals("parasites") ? im.green : im.red;
                double keptB = what.equals("double") ? 6 : 3;
                double need = (double) ch.getWidth() * ch.getHeight() * (5 * nvL + keptB);
                System.gc();
                double usedM = IJ.currentMemory(), maxM = IJ.maxMemory();
                if (!imgs.isEmpty() && usedM + calNeed + need > 0.8 * maxM) {
                    calMem = picks.length - j;
                    break;
                }
                calNeed += need;
                keep(im, lab);
            } else calSkip++;
        }
        IJ.showProgress(1.0);
        if (imgs.isEmpty()) {
            IJ.showMessage("Calibrate", "None of the images picked could be opened with the channels\n"
                    + "needed. Check the channel numbers in the previous dialogs\n"
                    + "(and the Log, for files that could not be opened).");
            return false;
        }
        return true;
    }

    void keep(Img im, String lab) {
        im.lab = lab;
        imgs.add(im);
    }

    /** Indices into the image list: one at random, or one at random from each experiment x condition. */
    int[] pickIndices(String src) {
        int n = P.entries.size();
        if (src.equals(SRC_MODES[1])) {
            int r = (int) Math.floor(Math.random() * n);
            if (r >= n) r = n - 1;
            return new int[]{r};
        }
        List<String> keys = new ArrayList<String>();
        for (Input.Entry e : P.entries) {
            String key = e.manip + "|" + e.cond;
            if (!keys.contains(key)) keys.add(key);
        }
        int[] out = new int[keys.size()];
        for (int u = 0; u < keys.size(); u++) {
            List<Integer> cand = new ArrayList<Integer>();
            for (int i = 0; i < n; i++) {
                Input.Entry e = P.entries.get(i);
                if ((e.manip + "|" + e.cond).equals(keys.get(u))) cand.add(i);
            }
            int r = (int) Math.floor(Math.random() * cand.size());
            if (r >= cand.size()) r = cand.size() - 1;
            out[u] = cand.get(r);
        }
        return out;
    }

    /** The image picked by hand (multi-channel file). */
    Img openChosen(String what) {
        Plugin.hook("calfile");
        OpenDialog od = new OpenDialog("Calibrate " + what + ": choose one image", null);
        if (od.getFileName() == null) return null;
        String p = od.getDirectory() + od.getFileName();
        calChosenLab = new File(p).getName();
        Input.Entry e = new Input.Entry();
        e.path = p;
        return openFile(e, what);
    }

    /** Opens entry e and keeps the channels this calibration uses, projected and calibrated as in the batch. */
    Img openFile(Input.Entry e, String what) {
        Input.Opened o = Input.openFile(S, e);
        for (String l : o.log) IJ.log(l);
        if (o.imp == null) {
            IJ.log("Calibrate: " + new File(e.path).getName() + " could not be opened: " + o.err);
            if (calSrc.equals(SRC_MODES[0])) IJ.showMessage("Calibrate", "This image could not be opened:\n" + o.err);
            return null;
        }
        // as in the batch: first time point, maximum projection of the Z planes
        ImagePlus proj = Input.countView(o.imp);
        Img im = new Img();
        pixel(im, proj);
        int uB = S.chBLUE, uG = S.chGREEN, uR = S.chRED;
        if (S.autoDetectChannels) {
            int[] dc = Input.rolesFromCols(Input.colours(proj), roleCols, S.dcSkip);
            boolean okDc = true;
            if (S.chBLUE > 0 && dc[0] == 0) okDc = false;
            if (S.chGREEN > 0 && dc[1] == 0) okDc = false;
            if (S.chRED > 0 && dc[2] == 0) okDc = false;
            if (okDc) {
                uB = dc[0];
                uG = dc[1];
                uR = dc[2];
            }
        }
        int pnc = proj.getNChannels();
        if (pnc > 1) {
            ImagePlus[] ch = ChannelSplitter.split(proj);
            boolean[] taken = new boolean[pnc + 1];
            // only the channels this calibration uses are kept (each channel once, in this order)
            if (what.equals("nuclei")) im.blue = take(ch, taken, uB);
            if (!what.equals("nuclei")) im.green = take(ch, taken, uG);
            if (what.equals("double")) im.red = take(ch, taken, uR);
            // the references of the poles, when the option is on
            if (what.equals("double") && S.poleNeedMk && S.chPOLE > 0) im.pole = take(ch, taken, S.chPOLE);
            if (what.equals("double") && S.poleNeedNuc) im.blue = take(ch, taken, uB);
        } else {
            // a single-channel file is taken as the channel being calibrated
            if (what.equals("nuclei")) im.blue = proj;
            if (what.equals("parasites")) im.green = proj;
            if (what.equals("double")) im.red = proj;
        }
        return has(im, what) ? im : null;
    }

    static ImagePlus take(ImagePlus[] ch, boolean[] taken, int c) {
        if (c < 1 || c > ch.length || taken[c]) return null;
        taken[c] = true;
        return ch[c - 1];
    }

    /** The pixel size of the image, set as in the batch (said once in the Log when the first dialog's is not used). */
    void pixel(Img im, ImagePlus imp) {
        Input.Px px = Input.applyPixelSize(S, imp);
        if (!P.calibShown && Input.pixelNote(S, px)) {
            IJ.log(Input.pixelNoteText(S));
            P.calibShown = true;
        }
        im.pxV = px.w;
        im.pxU = "um";
        im.pxS = px.source;
    }

    /** One TIFF per channel, picked by hand: the file gives the coverslip (W) and the field (P); every channel
     *  file of that field in the same folder is used. */
    Img openChosenTiff(String what) {
        Plugin.hook("calfile");
        OpenDialog od = new OpenDialog("Calibrate " + what + ": choose one TIFF of the field (any channel)", null);
        if (od.getFileName() == null) return null;
        String dir = od.getDirectory();
        if (!dir.endsWith(File.separator) && !dir.endsWith("/")) dir = dir + File.separator;
        String nm = od.getFileName();
        calChosenLab = nm;
        String stem = Input.stripExt(nm);
        int wPos = stem.lastIndexOf("--W");
        int pPos = -1;
        if (wPos >= 0) pPos = stem.indexOf("--P", wPos);
        if (wPos < 0 || pPos < 0) {
            IJ.showMessage("Calibrate", "This file name holds no --W...--P... fields:\n" + nm);
            return null;
        }
        String wNum = Input.digitsAt(stem, wPos + 3);
        String pNum = Input.digitsAt(stem, pPos + 3);
        calChosenLab = "W" + wNum + "_P" + pNum;
        String jB = "", jG = "", jR = "", jP = "";
        for (String f : Input.fileList(dir)) {
            String lc = f.toLowerCase();
            if (!lc.endsWith(".tif") && !lc.endsWith(".tiff")) continue;
            String st = Input.stripExt(f);
            int w2 = st.lastIndexOf("--W");
            if (w2 < 0) continue;
            int p2 = st.indexOf("--P", w2);
            if (p2 < 0) continue;
            if (!Input.digitsAt(st, w2 + 3).equals(wNum)) continue;
            if (!Input.digitsAt(st, p2 + 3).equals(pNum)) continue;
            String role = Input.chanRole(S, st);
            String full = dir + f;
            if (role.equals("BLUE")) jB = Input.addPath(jB, full);
            if (role.equals("GREEN")) jG = Input.addPath(jG, full);
            if (role.equals("RED")) jR = Input.addPath(jR, full);
            if (role.equals("POLE")) jP = Input.addPath(jP, full);
        }
        Input.Entry e = new Input.Entry();
        e.tBlue = jB;
        e.tGreen = jG;
        e.tRed = jR;
        e.tPole = jP;
        return openTiffEntry(e, what);
    }

    /** One TIFF per channel: the channel files of one acquisition. */
    Img openTiffEntry(Input.Entry e, String what) {
        Img im = new Img();
        if (what.equals("nuclei")) im.blue = tiff(im, e.tBlue, "CAL_BLUE");
        if (!what.equals("nuclei")) im.green = tiff(im, e.tGreen, "CAL_GREEN");
        if (what.equals("double")) im.red = tiff(im, e.tRed, "CAL_RED");
        if (what.equals("double") && S.poleNeedNuc) im.blue = tiff(im, e.tBlue, "CAL_BLUE");
        if (what.equals("double") && S.poleNeedMk) im.pole = tiff(im, e.tPole, "CAL_POLE");
        return has(im, what) ? im : null;
    }

    ImagePlus tiff(Img im, String paths, String title) {
        ImagePlus imp = Input.openTiffChannel(paths, title);
        if (imp != null) pixel(im, imp);
        return imp;
    }

    /** True if the channels needed are there. Says which one is missing when a single image was asked for. */
    boolean has(Img im, String what) {
        String miss = "";
        if (what.equals("nuclei") && im.blue == null) miss = "nuclear stain";
        if (!what.equals("nuclei") && im.green == null) miss = "parasite label";
        if (what.equals("double") && im.red == null) miss = "second marker";
        if (miss.isEmpty()) return true;
        if (calSrc.equals(SRC_MODES[0]))
            IJ.showMessage("Calibrate", "The " + miss + " channel was not found in this image.\n"
                    + "Check the channel numbers in the previous dialogs, or pick another image.");
        return false;
    }

    // ================================================================== the preview
    /** The preview stack, built once: for each image, the raw channel in its colour, then the view(s) the counted
     *  objects are drawn on. Images of different sizes sit in the top-left corner of the largest. */
    ImagePlus preview(String what) {
        nv = what.equals("double") ? 3 : 2;
        int mw = 0, mh = 0;
        for (Img im : imgs) {
            ImagePlus b = base(im, what);
            mw = Math.max(mw, b.getWidth());
            mh = Math.max(mh, b.getHeight());
        }
        ImagePlus p = IJ.createImage("CAL_prev", "RGB black", mw, mh, imgs.size() * nv);
        // calibrated as the (first) image it shows
        if (!imgs.isEmpty()) {
            Calibration cal = p.getCalibration();
            double v = imgs.get(0).pxV;
            cal.pixelWidth = v;
            cal.pixelHeight = v;
            cal.pixelDepth = v;
            cal.setUnit("micron");
        }
        views(p, what);
        p.setSlice(2);
        p.setTitle("Calibration QC - " + what);
        p.show();
        return p;
    }

    static ImagePlus base(Img im, String what) {
        if (what.equals("nuclei")) return im.blue;
        if (what.equals("parasites")) return im.green;
        return im.red;
    }

    /** The views of every calibration image, drawn into the preview with the brightness in force. */
    void views(ImagePlus p, String what) {
        for (int k = 1; k <= imgs.size(); k++) {
            Img im = imgs.get(k - 1);
            int s0 = (k - 1) * nv;
            String lab = im.lab;
            if (what.equals("nuclei")) {
                ImagePlus v = view(im.blue, "Blue", S.qcGainBlue);
                put(p, v, s0 + 1, lab + "  -  raw nuclear stain");
                put(p, v, s0 + 2, lab + "  -  counted nuclei");
            }
            if (what.equals("parasites")) {
                ImagePlus v = view(im.green, "Green", S.qcGainGreen);
                put(p, v, s0 + 1, lab + "  -  raw parasite label");
                put(p, v, s0 + 2, lab + "  -  counted parasites");
            }
            if (what.equals("double")) {
                ImagePlus v = view(im.red, "Red", S.qcGainRed);
                put(p, v, s0 + 1, lab + "  -  raw marker");
                put(p, v, s0 + 2, lab + "  -  marker alone, double-labelled drawn");
                ImagePlus m = mergeView(im.red, im.green);
                put(p, m, s0 + 3, lab + "  -  marker + parasites, double-labelled drawn");
            }
        }
        viewGains = gainKey();
    }

    /** Display copy of one channel, in the given colour and brightness, as RGB. */
    static ImagePlus view(ImagePlus src, String lut, double gain) {
        ImagePlus d = QC.qcCopy(src, "CAL_v", gain, lut);
        Cmd.toRGB(d);
        return d;
    }

    /** Red + green display merge, as RGB, each with its brightness (Merge Channels without "create"). */
    ImagePlus mergeView(ImagePlus redT, ImagePlus greenT) {
        ImagePlus mr = Detect.dup(redT, "CAL_mr");
        QC.qcRange(mr, S.qcGainRed);
        ImagePlus mg = Detect.dup(greenT, "CAL_mg");
        QC.qcRange(mg, S.qcGainGreen);
        ImageStack rgb = new RGBStackMerge().mergeStacks(mr.getWidth(), mr.getHeight(), mr.getStackSize(), mr.getStack(),
                mg.getStack(), null, false);
        ImagePlus out = new ImagePlus("RGB", rgb);
        out.setCalibration(mr.getCalibration());
        return out;
    }

    /** Copies the view into slice sl of the preview (top-left corner) and labels that slice. */
    static void put(ImagePlus p, ImagePlus view, int sl, String lab) {
        ImageStack st = p.getStack();
        st.getProcessor(sl).copyBits(view.getProcessor(), 0, 0, Blitter.COPY);
        st.setSliceLabel(lab, sl);
    }

    /** New brightness: the views are redrawn in the preview already open, without counting anything again. */
    void refreshViews(String what) {
        views(prev, what);
        prev.updateAndDraw();
    }

    /** Brings the preview up to date with the settings in force, redoing only what changed. */
    void update(String what) {
        if (prev == null || WindowManager.getImage(prev.getID()) == null) {
            // the preview was closed meanwhile: built again
            prev = preview(what);
            lastKey = "";
        } else if (!gainKey().equals(viewGains)) refreshViews(what);
        if (!detKey(what).equals(lastKey)) redraw(what);
    }

    String gainKey() {
        return Macro.n2s(S.qcGainBlue) + "|" + Macro.n2s(S.qcGainGreen) + "|" + Macro.n2s(S.qcGainRed);
    }

    /** Every setting the counting of this class depends on, as one string. */
    String detKey(String what) {
        String kn = Macro.d2s(S.nucleiThresholdFactor, 6) + "|" + Macro.d2s(S.nucleiMinArea, 6) + "|" + Macro.n2s(S.nucleiBgRolling)
                + "|" + Macro.d2s(S.nucleiBlurSigma, 6) + "|" + S.nucleiCirc + "|" + S.nucleiThresholdMethod + "|"
                + S.nucleiEdgeMode + "|" + S.nucleiWatershed + "|" + Macro.d2s(S.nucleiWsTol, 6) + "|" + S.splitBigNuclei + "|"
                + Macro.d2s(S.nucleiDoubletFactor, 6) + "|" + Macro.n2s(S.nucleiMaxSplit) + "|" + Macro.d2s(S.nucleiFragFactor, 6)
                + "|" + Macro.d2s(S.nucleiCapF, 6);
        String kp = Macro.d2s(S.greenThresholdFactor, 6) + "|" + Macro.d2s(S.greenMinArea, 6) + "|" + Macro.d2s(S.greenMaxArea, 6)
                + "|" + Macro.n2s(S.greenBgRolling) + "|" + Macro.d2s(S.greenBlurSigma, 6) + "|" + S.greenCirc + "|"
                + S.greenThresholdMethod + "|" + S.greenWatershed + "|" + Macro.d2s(S.greenWsTol, 6) + "|"
                + Macro.d2s(S.greenCapF, 6) + "|" + S.splitBigParasites + "|" + Macro.d2s(S.greenDoubletFactor, 6) + "|"
                + Macro.n2s(S.greenMaxSplit);
        String kr = Macro.d2s(S.redThresholdFactor, 6) + "|" + Macro.d2s(S.minOverlapPercent, 6) + "|" + Macro.n2s(S.redBgRolling)
                + "|" + Macro.d2s(S.redBlurSigma, 6) + "|" + S.redThresholdMethod + "|" + Macro.d2s(S.redCapF, 6);
        String ko = S.dblMode + "|" + Macro.d2s(S.objMinArea, 6) + "|" + Macro.d2s(S.objMaxArea, 6) + "|" + Macro.d2s(S.objTol, 6)
                + "|" + Macro.n2s(S.objMinN) + "|" + S.objWatershed + "|" + Macro.d2s(S.encMinArea, 6) + "|"
                + Macro.d2s(S.encMaxArea, 6) + "|" + S.posMode + "|" + Macro.d2s(S.centrePct, 6) + "|" + Macro.d2s(S.endPct, 6) + "|"
                + Macro.d2s(S.pnMinArea, 6) + "|" + Macro.d2s(S.pnMaxArea, 6) + "|" + Macro.d2s(S.pmThresholdFactor, 6) + "|"
                + Macro.d2s(S.pmMinArea, 6) + "|" + Macro.d2s(S.pmMaxArea, 6) + "|" + Macro.d2s(S.poleDeadPct, 6);
        if (what.equals("nuclei")) return kn;
        if (what.equals("parasites")) return kp;
        return kp + "|" + kr + "|" + ko + "|" + kn;
    }

    /** The binary mask of class cls (0 nuclei, 1 parasites, 2 marker) for image im, made with the settings in
     *  force; each step is redone only when one of its settings changed. The thresholds it was made with are
     *  restored into d with it. */
    ImagePlus maskOf(int cls, Img im, Detect d) {
        Slot s = im.slot[cls];
        ImagePlus src;
        String kp, km;
        if (cls == 0) {
            src = im.blue;
            kp = Macro.n2s(S.nucleiBgRolling) + "|" + Macro.d2s(S.nucleiBlurSigma, 6);
            km = kp + "|" + S.nucleiThresholdMethod + "|" + Macro.d2s(S.nucleiThresholdFactor, 6) + "|" + S.nucleiWatershed + "|"
                    + Macro.d2s(S.nucleiWsTol, 6) + "|" + Macro.d2s(S.nucleiCapF, 6);
        } else if (cls == 1) {
            src = im.green;
            kp = Macro.n2s(S.greenBgRolling) + "|" + Macro.d2s(S.greenBlurSigma, 6);
            km = kp + "|" + S.greenThresholdMethod + "|" + Macro.d2s(S.greenThresholdFactor, 6) + "|" + S.greenWatershed + "|"
                    + Macro.d2s(S.greenWsTol, 6) + "|" + Macro.d2s(S.greenCapF, 6);
        } else {
            src = im.red;
            kp = Macro.n2s(S.redBgRolling) + "|" + Macro.d2s(S.redBlurSigma, 6);
            km = kp + "|" + S.redThresholdMethod + "|" + Macro.d2s(S.redThresholdFactor, 6) + "|" + Macro.d2s(S.redCapF, 6);
        }
        String[] nm = {"n", "p", "m"};
        if (!s.keyP.equals(kp) || s.pre == null) {
            String preT = "CAL_" + nm[cls] + "pre";
            if (cls == 0) s.pre = d.nucleiPre(src, preT);
            if (cls == 1) s.pre = d.parasitePre(src, preT);
            if (cls == 2) s.pre = d.markerPre(src, preT);
            s.keyP = kp;
            s.keyM = "";
        }
        if (!s.keyM.equals(km) || s.bin == null) {
            s.pre.deleteRoi();
            ImagePlus bin = Detect.dup(s.pre, "CAL_" + nm[cls] + "bin");
            if (cls == 0) {
                d.nucleiMask(bin);
                s.thrA = d.nucThrAuto;
                s.thr = d.nucThr;
            }
            if (cls == 1) {
                d.parasiteMask(bin);
                s.thrA = d.parThrAuto;
                s.thr = d.parThr;
            }
            if (cls == 2) {
                d.markerMask(bin);
                s.thrA = d.dblThrAuto;
                s.thr = d.dblThr;
            }
            s.bin = bin;
            s.hit = d.capHit + 2 * d.spikeHit;
            s.note = d.thrNote;
            s.medT = d.thrMed;
            s.maxT = d.thrMax;
            s.keyM = km;
        }
        // what the threshold left out of its calculation, as if just made
        d.capHit = s.hit % 2;
        d.spikeHit = s.hit / 2;
        d.thrNote = s.note;
        d.thrMed = s.medT;
        d.thrMax = s.maxT;
        if (cls == 0) {
            d.nucThrAuto = s.thrA;
            d.nucThr = s.thr;
        }
        if (cls == 1) {
            d.parThrAuto = s.thrA;
            d.parThr = s.thr;
        }
        if (cls == 2) {
            d.dblThrAuto = s.thrA;
            d.dblThr = s.thr;
        }
        return s.bin;
    }

    /** Runs the detection on every calibration image with the settings in force and redraws the overlay of the
     *  preview. The counts go to the summary, shown in the panel. */
    void redraw(String what) {
        prev.deleteRoi();
        int iw = prev.getWidth();
        int fs = (int) Math.max(10, Math.round(iw / 90.0));
        int lw = (int) Math.max(1, Math.round(iw / 500.0));
        Overlay ov = new Overlay();
        double tA = 0, tB = 0, tC = 0, tD = 0, tE = 0;
        // fragments are only mentioned when the rule is on
        boolean frOn = S.nucleiFragFactor > 0;
        capHits = 0;
        spikeHits = 0;
        poleT = new double[6];
        objT = 0;
        StringBuilder lines = new StringBuilder();
        int calN = imgs.size();
        Ovl o = new Ovl(ov, prev);
        for (int k = 1; k <= calN; k++) {
            IJ.showStatus("Calibrate: counting image " + k + " / " + calN);
            IJ.showProgress(k, calN);
            int sl = (k - 1) * nv + 2;
            Detect d = new Detect(S);
            Img im = imgs.get(k - 1);
            if (what.equals("nuclei")) redrawNuclei(im, sl, fs, lw, d, o);
            if (what.equals("parasites")) redrawParasites(im, sl, fs, lw, d, o);
            if (what.equals("double")) redrawDouble(im, sl, fs, lw, d, o);
            tA += cntA;
            tB += cntB;
            tC += cntC;
            tD += cntD;
            tE += cntE;
            if (calN > 1) {
                String ln = "";
                if (what.equals("nuclei")) {
                    ln = Macro.n2s(cntA) + " cells (" + Macro.n2s(cntB) + " nuclei, " + Macro.n2s(cntC) + " clusters";
                    if (frOn) ln = ln + ", " + Macro.n2s(cntE) + " fragments";
                    ln = ln + ")";
                }
                if (what.equals("parasites")) {
                    if (S.splitBigParasites)
                        ln = Macro.n2s(cntA) + " parasites (" + Macro.n2s(cntB) + " objects, " + Macro.n2s(cntD)
                                + " clusters; threshold " + Macro.n2s(cntC) + ")";
                    else ln = Macro.n2s(cntA) + " parasites (threshold " + Macro.n2s(cntC) + ")";
                }
                if (what.equals("double"))
                    ln = Macro.n2s(cntA) + " / " + Macro.n2s(cntB) + " double-labelled (marker threshold " + Macro.n2s(cntC) + ")"
                            + imgLine;
                // the panel stays short: four images there, all of them in the Log
                if (k <= 4) lines.append("\n   ").append(im.lab).append(":  ").append(ln);
                IJ.log("Calibrate " + what + "   " + im.lab + ":  " + ln);
            }
        }
        if (calN > 4) lines.append("\n   ... and ").append(calN - 4).append(" more image(s): see the Log");

        if (what.equals("nuclei")) {
            summary = "COUNTED: " + Macro.n2s(tA) + " host cells from " + Macro.n2s(tB) + " nuclei (" + Macro.n2s(tC) + " clusters";
            if (frOn) summary = summary + ", " + Macro.n2s(tE) + " fragments";
            summary = summary + ")";
            if (tD > 0) summary = summary + ", " + Macro.n2s(tD) + " at the border left out";
        }
        if (what.equals("parasites")) {
            summary = "COUNTED: " + Macro.n2s(tA) + " parasites";
            if (S.splitBigParasites) summary = summary + " from " + Macro.n2s(tB) + " objects (" + Macro.n2s(tD) + " clusters)";
        }
        if (what.equals("double")) {
            if (S.dblMode == 0)
                summary = "COUNTED: " + Macro.n2s(tA) + " double-labelled of " + Macro.n2s(tB) + " parasites (overlap >= "
                        + Macro.n2s(S.minOverlapPercent) + " %)";
            if (S.dblMode == 1) {
                String pm = Settings.POS_MODES[S.posMode];
                summary = "COUNTED: " + Macro.n2s(tA) + " double-labelled of " + Macro.n2s(tB) + " parasites (at least "
                        + Macro.n2s(S.objMinN) + " marker object(s) of " + Macro.n2s(S.objMinArea) + " to " + Macro.n2s(S.objMaxArea)
                        + " um2, " + Macro.n2s(objT) + " counted: " + pm + ")";
            }
            if (S.dblMode == 2)
                summary = "COUNTED: " + Macro.n2s(tA) + " double-labelled of " + Macro.n2s(tB) + " parasites (overlap >= "
                        + Macro.n2s(S.minOverlapPercent) + " % with a marker object of " + Macro.n2s(S.encMinArea) + " to "
                        + Macro.n2s(S.encMaxArea) + " um2)";
            if (S.dblPolesUsed && S.dblMode != 2) {
                double pSum = poleT[1] + poleT[2] + poleT[3];
                summary = summary + "\nPOLES: marker at the basal pole " + Macro.n2s(poleT[1]) + ", apical " + Macro.n2s(poleT[2])
                        + ", indeterminable " + Macro.n2s(poleT[3]) + " (sum " + Macro.n2s(pSum) + "), not at a pole "
                        + Macro.n2s(poleT[4]) + "; " + Macro.n2s(poleT[5]) + " parasite object(s) oriented";
            }
        }
        if (calN > 1) summary = summary + " on " + calN + " images:" + lines;
        else summary = summary + "  (" + imgs.get(0).lab + ")";
        if (calSkip > 0) summary = summary + "\n" + calSkip + " image(s) picked lacked a channel and were left out";
        if (calMem > 0) summary = summary + "\n" + calMem + " more image(s) not shown: not enough memory";
        summary = summary + "\n" + brightLine(what);
        summary = summary + "\n" + pixelLine(what);

        prev.deleteRoi();
        prev.setOverlay(ov);
        prev.setHideOverlay(false);
        IJ.showStatus("");
        IJ.showProgress(1.0);
        // what the preview now shows, so that an OK without change redraws nothing
        lastKey = detKey(what);
    }

    /** Pixel size of the calibration images, and what the area limits are in pixels with it. */
    String pixelLine(String what) {
        boolean same = true;
        // (0.11 read from a file can come back as 0.10999999: not a difference)
        double pv = imgs.get(0).pxV;
        for (int k = 1; k < imgs.size(); k++) if (Math.abs(imgs.get(k).pxV - pv) > 0.001 * Math.abs(pv)) same = false;
        String s = "Pixel size " + Macro.d2s(pv, 4) + " " + imgs.get(0).pxU + " (" + imgs.get(0).pxS + ")";
        if (!same) s = s + " for the first image; it varies between images";
        if (pv <= 0) return s;
        if (what.equals("nuclei"))
            s = s + "\nMinimum area " + Macro.n2s(S.nucleiMinArea) + " um2 = " + Macro.n2s(Math.round(S.nucleiMinArea / (pv * pv)))
                    + " pixels";
        if (what.equals("parasites"))
            s = s + "\nArea " + Macro.n2s(S.greenMinArea) + " to " + Macro.n2s(S.greenMaxArea) + " um2 = "
                    + Macro.n2s(Math.round(S.greenMinArea / (pv * pv))) + " to " + Macro.n2s(Math.round(S.greenMaxArea / (pv * pv)))
                    + " pixels";
        if (what.equals("double") && S.dblMode == 1)
            s = s + "\nMarker objects " + Macro.n2s(S.objMinArea) + " to " + Macro.n2s(S.objMaxArea) + " um2 = "
                    + Macro.n2s(Math.round(S.objMinArea / (pv * pv))) + " to " + Macro.n2s(Math.round(S.objMaxArea / (pv * pv)))
                    + " pixels; " + Macro.n2s(S.objTol) + " um = " + Macro.d2s(S.objTol / pv, 1) + " pixels";
        return s;
    }

    /** The bright cap on the images drawn: how many images it acted on (and a saturated peak, when one was left out). */
    String brightLine(String what) {
        double capF = S.nucleiCapF;
        if (what.equals("parasites")) capF = S.greenCapF;
        if (what.equals("double")) capF = S.redCapF;
        String s;
        if (capF > 0) s = "Bright cap " + Macro.n2s(capF) + " x the median brightness: acted on " + capHits + " of " + imgs.size()
                + " image(s)";
        else s = "Bright cap off";
        if (spikeHits > 0) s = s + "; a saturated peak was left out on " + spikeHits + " image(s)";
        return s;
    }

    /** What the threshold of the mask just made left out of its calculation, added to the counts of the panel. */
    void thrInfo(Detect d) {
        if (d.capHit == 1) capHits++;
        if (d.spikeHit == 1) spikeHits++;
    }

    /** Nuclei of image im, drawn on slice sl. */
    void redrawNuclei(Img im, int sl, int fs, int lw, Detect d, Ovl o) {
        // the mask from the cache (same steps as segmentNuclei), then its objects
        ImagePlus bin = maskOf(0, im, d);
        thrInfo(d);
        String noteN = d.thrNote;
        bin.deleteRoi();
        ImagePlus mask = Detect.dup(bin, "CAL_mask");
        d.nucleiObjects(mask);
        int nEdge = 0;
        List<Roi> edgeOut = new ArrayList<Roi>();
        if (S.nucleiEdgeMode > 0) nEdge = d.removeBorderNuclei(mask, S.nucleiEdgeMode, edgeOut);
        int nObj = d.rm.size();
        double nCells = d.countCells(im.blue);
        int[] clus = d.cellClusterIdx, frag = d.cellFragIdx;
        int[] allIdx = QC.seq(nObj);
        // what the filters rejected near their limits, underneath the rest: orange by size, yellow by shape;
        // nothing of what the border took
        ImagePlus[] rej = d.rejectMasks(mask, nObj, nEdge > 0 ? edgeOut : null, S.nucleiMinArea, Double.POSITIVE_INFINITY);
        o.mask(rej[0], "ff9900", lw, sl, 0);
        o.mask(rej[1], "ffff00", lw, sl, 0);
        if (nEdge > 0) o.rois(edgeOut, QC.seq(edgeOut.size()), "a0a0a0", lw, sl);
        o.rois(d.rm, allIdx, "00ffff", lw, sl);
        o.rois(d.rm, clus, "ff00ff", lw, sl);
        o.rois(d.rm, frag, "00ff00", lw, sl);
        o.labels(d.rm, allIdx, "00ffff", fs, sl);

        o.text(im.lab, 6, 4, "ffffff", fs, sl);
        // legend on two lines, so that it fits on narrow images too
        o.text(Run.legNuclei(S, 1), 6, 4 + Math.round(fs * 1.6), "ffffff", fs, sl);
        o.text(Run.legNuclei(S, 2), 6, 4 + Math.round(fs * 3.2), "ffffff", fs, sl);
        double yb = im.blue.getHeight() - fs * 2;
        String cntT = "Cells = " + Macro.n2s(nCells) + "   (" + nObj + " nuclei, " + clus.length + " clusters";
        if (S.nucleiFragFactor > 0) cntT = cntT + ", " + frag.length + " fragments";
        if (S.nucleiEdgeMode > 0) cntT = cntT + ", " + nEdge + " cut by the border left out";
        cntT = cntT + ")";
        // the clusters and fragments are sized against this median
        if (d.cellMedian > 0) cntT = cntT + "   median area " + Macro.d2s(d.cellMedian, 1) + " um2";
        o.text(cntT, 6, yb, "00ffff", fs, sl);
        if (!noteN.isEmpty()) o.text("Threshold: " + noteN, 6, yb - Math.round(fs * 1.6), "ffff00", fs, sl);

        cntA = nCells;
        cntB = nObj;
        cntC = clus.length;
        cntD = nEdge;
        cntE = frag.length;
    }

    /** Parasites of image im, drawn on slice sl. */
    void redrawParasites(Img im, int sl, int fs, int lw, Detect d, Ovl o) {
        ImagePlus bin = maskOf(1, im, d);
        thrInfo(d);
        String noteP = d.thrNote;
        bin.deleteRoi();
        ImagePlus mask = Detect.dup(bin, "CAL_mask");
        int nObj = d.parasiteObjects(mask);
        double nPar = d.countParasites(im.green);
        int[] clus = d.parClusterIdx;
        int[] allIdx = QC.seq(nObj);
        ImagePlus[] rej = d.rejectMasks(mask, nObj, null, S.greenMinArea, S.greenMaxArea);
        o.mask(rej[0], "ff9900", lw, sl, 0);
        o.mask(rej[1], "ffff00", lw, sl, 0);
        o.rois(d.rm, allIdx, "ff00ff", lw, sl);
        o.rois(d.rm, clus, "ffffff", lw, sl);
        o.labels(d.rm, allIdx, "ff00ff", fs, sl);

        o.text(im.lab, 6, 4, "ffffff", fs, sl);
        o.text(Run.legParasites(S, 1), 6, 4 + Math.round(fs * 1.6), "ffffff", fs, sl);
        o.text(Run.legParasites(S, 2), 6, 4 + Math.round(fs * 3.2), "ffffff", fs, sl);
        double yb = im.green.getHeight() - fs * 2;
        String cntT = "Parasites = " + Macro.n2s(nPar);
        if (S.splitBigParasites) {
            cntT = cntT + "   (" + nObj + " objects, " + clus.length + " clusters";
            if (d.parMedian > 0) cntT = cntT + ", median area " + Macro.d2s(d.parMedian, 1) + " um2";
            cntT = cntT + ")";
        }
        cntT = cntT + "   (threshold " + Macro.n2s(d.parThr) + " = automatic " + Macro.nfmt(d.parThrAuto) + " x "
                + Macro.n2s(S.greenThresholdFactor) + ")";
        o.text(cntT, 6, yb, "ff00ff", fs, sl);
        if (!noteP.isEmpty()) o.text("Threshold: " + noteP, 6, yb - Math.round(fs * 1.6), "ffff00", fs, sl);

        cntA = nPar;
        cntB = nObj;
        cntC = d.parThr;
        cntD = clus.length;
        cntE = 0;
    }

    /** Double labelling of image im, drawn on slices sl and sl + 1. */
    void redrawDouble(Img im, int sl, int fs, int lw, Detect d, Ovl o) {
        // parasites detected with the parasite settings in force; a cluster counts for the parasites it holds
        ImagePlus binP = maskOf(1, im, d);
        binP.deleteRoi();
        ImagePlus pmask = Detect.dup(binP, "CAL_pmask");
        int nObj = d.parasiteObjects(pmask);
        double nPar = d.countParasites(im.green);
        ImagePlus binM = maskOf(2, im, d);
        thrInfo(d);
        String noteM = d.thrNote;
        // the thresholds of the marker mask, before the poles' own detections
        double thrM = d.dblThr;
        binM.deleteRoi();
        ImagePlus mask = Detect.dup(binM, "CAL_mask");
        // the same scoring as the batch: overlap, objects, or enclosing objects, then the poles
        ImagePlus refB = S.poleNeedNuc ? im.blue : null;
        ImagePlus refP = S.poleNeedMk ? im.pole : null;
        List<Roi> parRois = new ArrayList<Roi>(d.rm);
        double nDbl = d.scoreDoubleAll(mask, nObj, mask, refB, refP);
        int[] dIdx = d.dblIdx;
        List<Integer> othL = new ArrayList<Integer>();
        boolean[] isD = new boolean[nObj];
        for (int z : dIdx) isD[z] = true;
        for (int q = 0; q < nObj; q++) if (!isD[q]) othL.add(q);
        int[] oth = Detect.toInt(othL);
        // the marker objects by kind: 2 counted, 1 on a parasite elsewhere, 0 on none
        int[] oKind = new int[d.oX.length];
        for (int q = 0; q < d.oX.length; q++) {
            if (d.oOwn[q] >= 0) oKind[q] = 1;
            if (d.oUse[q] == 1) oKind[q] = 2;
        }
        ImagePlus enc = null;
        if (S.dblMode == 2) enc = d.enclosingMask(mask);
        // specks of the marker are not drawn: under half the smallest parasite (overlap), under the smallest
        // object (objects), under half the smallest object (enclosing objects)
        double minSp = d.mkSpeck();
        boolean polesOn = S.dblPolesUsed && S.dblMode != 2;
        double yb = im.red.getHeight() - fs * 2;
        for (int v = sl; v <= sl + 1; v++) {
            o.mask(mask, "ff9900", lw, v, minSp);
            if (enc != null) o.mask(enc, "ffff00", lw, v, 0);
            // the merge view (second one) shows the poles, when asked for
            if (polesOn && v == sl + 1) {
                List<Integer> cB = new ArrayList<Integer>(), cA = new ArrayList<Integer>(), cI = new ArrayList<Integer>(),
                        cM = new ArrayList<Integer>();
                for (int q = 0; q < nObj; q++) {
                    if (d.pCls[q] == 1) cB.add(q);
                    if (d.pCls[q] == 2) cA.add(q);
                    if (d.pCls[q] == 3) cI.add(q);
                    if (d.pCls[q] == 4) cM.add(q);
                }
                o.rois(parRois, oth, "808080", lw, v);
                o.rois(parRois, Detect.toInt(cM), "ff9600", lw, v);
                o.rois(parRois, Detect.toInt(cI), "ffffff", lw, v);
                o.rois(parRois, Detect.toInt(cA), "00ffff", lw, v);
                o.rois(parRois, Detect.toInt(cB), "ff00ff", lw, v);
                if (S.dblMode == 1) {
                    o.objects(d, d.oPole, 4, "ff9600", lw, v);
                    o.objects(d, d.oPole, 3, "ffffff", lw, v);
                    o.objects(d, d.oPole, 2, "00ffff", lw, v);
                    o.objects(d, d.oPole, 1, "ff00ff", lw, v);
                }
                o.basalEnds(d, nObj, Math.max(3, lw * 2), "ffff00", v);
                o.labels(parRois, dIdx, "ffffff", fs, v);
                o.text(im.lab, 6, 4, "ffffff", fs, v);
                o.text(Run.legPoles(1, S), 6, 4 + Math.round(fs * 1.6), "ffffff", fs, v);
                o.text(Run.legPoles(2, S), 6, 4 + Math.round(fs * 3.2), "ffffff", fs, v);
                double pSum = d.poleN[1] + d.poleN[2] + d.poleN[3];
                String cntP = "Poles: basal " + Macro.n2s(d.poleN[1]) + ", apical " + Macro.n2s(d.poleN[2]) + ", indeterminable "
                        + Macro.n2s(d.poleN[3]) + " (sum " + Macro.n2s(pSum) + " of " + Macro.n2s(nDbl) + "), not at a pole "
                        + Macro.n2s(d.poleN[4]) + "   -   " + Macro.n2s(d.poleOri) + " of " + nObj + " parasite object(s) oriented";
                o.text(cntP, 6, yb, "ffffff", fs, v);
                if (!d.poleNote.isEmpty()) o.text("Not oriented: " + d.poleNote, 6, yb - Math.round(fs * 1.6), "ffff00", fs, v);
            } else {
                o.rois(parRois, oth, "00ffff", lw, v);
                o.rois(parRois, dIdx, "ffffff", lw, v);
                if (S.dblMode == 1) {
                    o.objects(d, oKind, 0, "808080", lw, v);
                    o.objects(d, oKind, 1, "ffff00", lw, v);
                    o.objects(d, oKind, 2, "ffffff", lw, v);
                }
                o.labels(parRois, dIdx, "ffffff", fs, v);
                o.text(im.lab, 6, 4, "ffffff", fs, v);
                o.text(Run.legDouble(S, 1), 6, 4 + Math.round(fs * 1.6), "ffffff", fs, v);
                String lg = Run.legDouble(S, 2);
                if (!lg.isEmpty()) o.text(lg, 6, 4 + Math.round(fs * 3.2), "ffffff", fs, v);
                String cntD = "Double-labelled = " + Macro.n2s(nDbl) + " / " + Macro.n2s(nPar);
                if (S.dblMode == 0)
                    cntD = cntD + "   (20 / 35 / 55 % overlap: " + Macro.n2s(d.dblN20) + " / " + Macro.n2s(d.dblN35) + " / "
                            + Macro.n2s(d.dblN55) + ")";
                if (S.dblMode == 1)
                    cntD = cntD + "   (objects: " + Macro.n2s(d.objUsed) + " counted, " + Macro.n2s(d.objOn) + " on a parasite, "
                            + Macro.n2s(d.objN) + " found)";
                if (S.dblMode == 2) cntD = cntD + "   (" + Macro.n2s(d.objN) + " marker objects of the size given)";
                o.text(cntD, 6, yb, "ffffff", fs, v);
                if (!noteM.isEmpty()) o.text("Marker threshold: " + noteM, 6, yb - Math.round(fs * 1.6), "ffff00", fs, v);
            }
        }

        cntA = nDbl;
        cntB = nPar;
        cntC = thrM;
        cntD = 0;
        cntE = 0;
        // the poles, for the panel: summed over the images
        poleT[1] += d.poleN[1];
        poleT[2] += d.poleN[2];
        poleT[3] += d.poleN[3];
        poleT[4] += d.poleN[4];
        poleT[5] += d.poleOri;
        objT += d.objUsed;
        imgLine = "";
        if (polesOn)
            imgLine = ", poles basal / apical / indet. " + Macro.n2s(d.poleN[1]) + " / " + Macro.n2s(d.poleN[2]) + " / "
                    + Macro.n2s(d.poleN[3]);
    }

    // ================================================================== the overlay
    /** The overlay of the preview, built as the macro version builds it (Overlay.addSelection of each outline,
     *  number and line of text, on its slice). */
    static final class Ovl {
        final Overlay ov;
        final ImagePlus prev;

        Ovl(Overlay ov, ImagePlus prev) {
            this.ov = ov;
            this.prev = prev;
        }

        /** Overlay.addSelection(colHex, width[, fill]) then Overlay.setPosition(sl), in that order. */
        void add(Roi r, String colHex, double width, int sl) {
            add(r, colHex, width, null, sl);
        }

        void add(Roi r, String colHex, double width, String fillHex, int sl) {
            r.setFillColor(null);
            r.setStrokeColor(QC.hex(colHex));
            if (!Double.isNaN(width)) r.setStrokeWidth(width);
            if (fillHex != null) r.setFillColor(QC.hex(fillHex));
            ov.add(r);
            r.setPosition(sl);
        }

        /** Everything above the threshold in a mask, outlined on slice sl - but for the specks smaller than minA
         *  (um2). Named as the ROI Manager names a selection it is given. */
        void mask(ImagePlus mask, String colHex, double width, int sl, double minA) {
            if (mask == null) return;
            mask.deleteRoi();
            ImagePlus src = mask;
            if (minA > 0) {
                // the particles of at least minA, as a mask of their own
                Particles.Result r = Particles.analyze(mask, Macro.n2s(minA) + "-Infinity", null, false, true);
                if (r.mask == null) return;
                src = r.mask;
            }
            // In both masks the objects are 255, whatever the LUT
            src.deleteRoi();
            Detect.setThreshold(src, 255, 255);
            Roi sel = ThresholdToSelection.run(src);
            Detect.resetThreshold(src);
            if (sel == null) return;
            sel.setName(managerLabel(src, sel));
            add((Roi) sel.clone(), colHex, width, sl);
        }

        /** ROIs idx of the list, outlined on slice sl. */
        void rois(List<Roi> list, int[] idx, String colHex, double width, int sl) {
            for (int i : idx) add((Roi) list.get(i).clone(), colHex, width, sl);
        }

        /** Numbers 1..N next to the ROIs idx of the list, on slice sl. */
        void labels(List<Roi> list, int[] idx, String colHex, int fs, int sl) {
            int iw = prev.getWidth(), ih = prev.getHeight();
            for (int q = 0; q < idx.length; q++) {
                Rectangle b = list.get(idx[q]).getBounds();
                double tx = b.x + b.width + 2;
                if (tx > iw - fs * 2) tx = b.x - fs * 2;
                if (tx < 0) tx = 0;
                double ty = b.y + b.height / 2.0 - fs / 2.0;
                if (ty < 0) ty = 0;
                if (ty > ih - fs) ty = ih - fs;
                text("" + (q + 1), tx, ty, colHex, fs, sl);
            }
        }

        /** A line of text on slice sl (setFont("SansSerif", fs, "bold") and makeText of the macro language). */
        void text(String txt, double x, double y, String colHex, int fs, int sl) {
            TextRoi t = new TextRoi((int) x, (int) y, txt, new Font("SansSerif", Font.BOLD, fs));
            t.setAntiAlias(QC.antialias(fs));
            add(t, colHex, Double.NaN, sl);
        }

        /** Circles round the marker objects of class cls in cl, on slice sl. */
        void objects(Detect d, int[] cl, int cls, String colHex, double width, int sl) {
            for (int q = 0; q < d.oX.length; q++) {
                if (cl[q] != cls) continue;
                double r = d.oR[q] + width + 1;
                add(new OvalRoi(d.oX[q] - r, d.oY[q] - r, 2 * r, 2 * r), colHex, width, sl);
            }
        }

        /** A dot at the basal end of each oriented parasite, on slice sl. */
        void basalEnds(Detect d, int nPar, double rad, String colHex, int sl) {
            for (int p = 0; p < nPar; p++) {
                if (d.pBas[p] == 0) continue;
                double cx = d.aX1[p], cy = d.aY1[p];
                if (d.pBas[p] == 2) {
                    cx = d.aX2[p];
                    cy = d.aY2[p];
                }
                add(new OvalRoi(cx - rad, cy - rad, 2 * rad, 2 * rad), colHex, 1, colHex, sl);
            }
        }

        /** RoiManager.getLabel(imp, roi, -1): the centre of the selection, "yyyy-xxxx". */
        static String managerLabel(ImagePlus imp, Roi roi) {
            Rectangle r = roi.getBounds();
            int xc = r.x + r.width / 2;
            int yc = r.y + r.height / 2;
            if (xc < 0) xc = 0;
            if (yc < 0) yc = 0;
            int digits = 4;
            String xs = "" + xc;
            if (xs.length() > digits) digits = xs.length();
            String ys = "" + yc;
            if (ys.length() > digits) digits = ys.length();
            if (digits == 4 && imp != null && (imp.getStackSize() >= 10000 || imp.getHeight() >= 10000)) digits = 5;
            xs = "000000" + xc;
            ys = "000000" + yc;
            String label = ys.substring(ys.length() - digits) + "-" + xs.substring(xs.length() - digits);
            if (imp != null && imp.getStackSize() > 1) {
                String zs = "000000" + imp.getCurrentSlice();
                label = zs.substring(zs.length() - digits) + "-" + label;
            }
            return label;
        }
    }

    // ================================================================== the panel
    /** Slider when its value lies on its steps, numeric field otherwise; every second one on the row before. */
    void slider(GenericDialog gd, String label, double lo, double hi, double val, double step) {
        if (slN % 2 == 1) Plugin.sameRow(gd);
        slN++;
        if (val < lo) lo = val;
        if (val > hi) hi = val;
        double steps = (val - lo) / step;
        boolean onGrid = Math.abs(steps - Math.round(steps)) < 0.001;
        boolean stepSliders = Plugin.versionAtLeast("1.52a");
        if (stepSliders && onGrid) gd.addSlider(label, lo, hi, val, step);
        else if (onGrid && step >= 1 && val == Math.round(val) && lo == Math.round(lo) && hi == Math.round(hi))
            gd.addSlider(label, lo, hi, val);
        else gd.addNumericField(label, val, 2, 6, "");
    }

    /** The panel: every setting of the class being calibrated, and what to do at OK. False: cancelled. */
    boolean dialog(String what) {
        String t = "Calibrate " + what;
        if (what.equals("double")) t = "Calibrate double labelling";
        boolean nonBlocking = Plugin.versionAtLeast("1.52o");
        Plugin.hook("calibrate");
        GenericDialog gd = nonBlocking ? new NonBlockingGenericDialog(t) : new GenericDialog(t);
        if (Plugin.versionAtLeast("1.52a")) {
            // at the right of the screen, the preview on its left
            int dx = IJ.getScreenSize().width - 980;
            if (dx < 0) dx = 0;
            gd.setLocation(dx, 30);
        }
        gd.addMessage(summary);
        gd.addMessage("Move the sliders, then OK (or Enter) to redraw. Brightness: display only.");
        // two sliders a row, so that the panel fits a laptop screen
        slN = 0;
        if (what.equals("nuclei")) {
            slider(gd, "Threshold factor", 0.2, 3, S.nucleiThresholdFactor, 0.05);
            slider(gd, "Bright cap (x median, 0 = off)", 0, 20, S.nucleiCapF, 0.5);
            slider(gd, "Minimum area (um2)", 0, 500, S.nucleiMinArea, 1);
            slider(gd, "Background radius (px, 0 = none)", 0, 300, S.nucleiBgRolling, 1);
            slider(gd, "Blur sigma (px, 0 = none)", 0, 5, S.nucleiBlurSigma, 0.1);
            slider(gd, "Minimum circularity", 0, 1, Settings.circMin(S.nucleiCirc), 0.05);
            slider(gd, "Watershed tolerance (px)", 0.5, 20, S.nucleiWsTol, 0.5);
            slider(gd, "Cluster above (x median)", 1.1, 4, S.nucleiDoubletFactor, 0.05);
            slider(gd, "Cluster: at most (cells)", 2, 10, S.nucleiMaxSplit, 1);
            slider(gd, "No cell under (x median, 0 = off)", 0, 0.9, S.nucleiFragFactor, 0.05);
            slider(gd, "Brightness (x)", 0.25, 10, S.qcGainBlue, 0.25);
            gd.addChoice("Threshold method", Settings.THR_METHODS, S.nucleiThresholdMethod);
            Plugin.sameRow(gd);
            gd.addChoice("Border", Settings.EDGE_MODES, Settings.EDGE_MODES[S.nucleiEdgeMode]);
            gd.addCheckbox("Split touching nuclei (watershed)", S.nucleiWatershed);
            Plugin.sameRow(gd);
            gd.addCheckbox("Overlapping nuclei count as several cells", S.splitBigNuclei);
        }
        if (what.equals("parasites")) {
            slider(gd, "Threshold factor", 0.5, 10, S.greenThresholdFactor, 0.05);
            slider(gd, "Bright cap (x median, 0 = off)", 0, 20, S.greenCapF, 0.5);
            slider(gd, "Minimum area (um2)", 0, 100, S.greenMinArea, 0.25);
            slider(gd, "Maximum area (um2)", 1, 300, S.greenMaxArea, 0.5);
            slider(gd, "Background radius (px, 0 = none)", 0, 300, S.greenBgRolling, 1);
            slider(gd, "Blur sigma (px, 0 = none)", 0, 5, S.greenBlurSigma, 0.1);
            slider(gd, "Minimum circularity", 0, 1, Settings.circMin(S.greenCirc), 0.05);
            slider(gd, "Watershed tolerance (px)", 0.5, 10, S.greenWsTol, 0.5);
            slider(gd, "Cluster above (x median)", 1.1, 4, S.greenDoubletFactor, 0.05);
            slider(gd, "Cluster: at most (parasites)", 2, 20, S.greenMaxSplit, 1);
            slider(gd, "Brightness (x)", 0.25, 10, S.qcGainGreen, 0.25);
            gd.addChoice("Threshold method", Settings.THR_METHODS, S.greenThresholdMethod);
            gd.addCheckbox("Split touching parasites (watershed)", S.greenWatershed);
            Plugin.sameRow(gd);
            gd.addCheckbox("Clumps count as several", S.splitBigParasites);
        }
        if (what.equals("double")) {
            slider(gd, "Marker threshold factor", 0.5, 10, S.redThresholdFactor, 0.05);
            slider(gd, "Bright cap (x median, 0 = off)", 0, 20, S.redCapF, 0.5);
            if (S.dblMode != 1) slider(gd, "Minimum overlap (%)", 0, 100, S.minOverlapPercent, 1);
            slider(gd, "Background radius (px, 0 = none)", 0, 300, S.redBgRolling, 1);
            slider(gd, "Blur sigma (px, 0 = none)", 0, 5, S.redBlurSigma, 0.1);
            slider(gd, "Marker brightness (x)", 0.25, 10, S.qcGainRed, 0.25);
            slider(gd, "Parasite brightness (x)", 0.25, 10, S.qcGainGreen, 0.25);
            if (S.dblMode == 1) {
                slider(gd, "Object: minimum area (um2)", 0, 20, S.objMinArea, 0.05);
                slider(gd, "Object: maximum area (um2)", 0.1, 200, S.objMaxArea, 0.1);
                slider(gd, "Centre at most outside (um)", 0, 3, S.objTol, 0.1);
                slider(gd, "Objects per parasite (at least)", 1, 20, S.objMinN, 1);
            }
            if (S.dblMode == 2) {
                slider(gd, "Object: minimum area (um2)", 0, 1000, S.encMinArea, 1);
                slider(gd, "Object: maximum area (um2)", 0, 20000, S.encMaxArea, 10);
            }
            if (S.dblMode == 1 || S.dblPolesUsed) {
                slider(gd, "Centre: central % of the length", 1, 100, S.centrePct, 1);
                slider(gd, "Extremity, pole: last % at each end", 1, 50, S.endPct, 1);
            }
            if (S.dblPolesUsed) {
                if (S.poleNeedNuc) {
                    slider(gd, "Parasite nucleus: min. area (um2)", 0, 20, S.pnMinArea, 0.1);
                    slider(gd, "Parasite nucleus: max. area (um2)", 0.5, 100, S.pnMaxArea, 0.5);
                }
                if (S.poleNeedMk) {
                    slider(gd, "Pole marker threshold factor", 0.5, 10, S.pmThresholdFactor, 0.05);
                    slider(gd, "Pole marker: min. area (um2)", 0, 10, S.pmMinArea, 0.05);
                    slider(gd, "Pole marker: max. area (um2)", 0.1, 50, S.pmMaxArea, 0.1);
                }
                slider(gd, "No orientation within (% from centre)", 0, 45, S.poleDeadPct, 1);
            }
            gd.addChoice("Threshold method", Settings.THR_METHODS, S.redThresholdMethod);
            if (S.dblMode == 1) {
                Plugin.sameRow(gd);
                gd.addChoice("Objects counted", Settings.POS_MODES, Settings.POS_MODES[S.posMode]);
                gd.addCheckbox("Split touching marker objects (watershed)", S.objWatershed);
            }
        }
        List<String> acts = new ArrayList<String>();
        acts.add(ACT_REDRAW);
        acts.add(ACT_KEEP);
        acts.add(ACT_DISCARD);
        if (!nonBlocking) acts.add(ACT_LOOK);
        gd.addChoice("At OK", acts.toArray(new String[0]), acts.get(0));
        gd.showDialog();
        if (gd.wasCanceled()) return false;

        if (what.equals("nuclei")) {
            S.nucleiThresholdFactor = gd.getNextNumber();
            S.nucleiCapF = gd.getNextNumber();
            S.nucleiMinArea = gd.getNextNumber();
            S.nucleiBgRolling = gd.getNextNumber();
            S.nucleiBlurSigma = gd.getNextNumber();
            double cMin = gd.getNextNumber();
            S.nucleiWsTol = gd.getNextNumber();
            S.nucleiDoubletFactor = gd.getNextNumber();
            S.nucleiMaxSplit = gd.getNextNumber();
            S.nucleiFragFactor = gd.getNextNumber();
            S.qcGainBlue = gd.getNextNumber();
            S.nucleiThresholdMethod = gd.getNextChoice();
            String ec = gd.getNextChoice();
            S.nucleiWatershed = gd.getNextBoolean();
            S.splitBigNuclei = gd.getNextBoolean();
            S.nucleiEdgeMode = 0;
            if (ec.equals(Settings.EDGE_MODES[1])) S.nucleiEdgeMode = 1;
            if (ec.equals(Settings.EDGE_MODES[2])) S.nucleiEdgeMode = 2;
            S.nucleiCirc = Settings.circStr(cMin);
            S.nucleiWsTol = Settings.wsTolOk(S.nucleiWsTol);
            if (Double.isNaN(S.nucleiFragFactor) || S.nucleiFragFactor < 0) S.nucleiFragFactor = 0;
            if (S.nucleiFragFactor > 0.9) S.nucleiFragFactor = 0.9;
            S.qcGainBlue = Settings.gainOk(S.qcGainBlue);
            S.nucleiCapF = Settings.capOk(S.nucleiCapF);
        }
        if (what.equals("parasites")) {
            S.greenThresholdFactor = gd.getNextNumber();
            S.greenCapF = gd.getNextNumber();
            S.greenMinArea = gd.getNextNumber();
            S.greenMaxArea = gd.getNextNumber();
            S.greenBgRolling = gd.getNextNumber();
            S.greenBlurSigma = gd.getNextNumber();
            double cMin = gd.getNextNumber();
            S.greenWsTol = gd.getNextNumber();
            S.greenDoubletFactor = gd.getNextNumber();
            S.greenMaxSplit = gd.getNextNumber();
            S.qcGainGreen = gd.getNextNumber();
            S.greenThresholdMethod = gd.getNextChoice();
            S.greenWatershed = gd.getNextBoolean();
            S.splitBigParasites = gd.getNextBoolean();
            S.greenCirc = Settings.circStr(cMin);
            S.greenWsTol = Settings.wsTolOk(S.greenWsTol);
            S.qcGainGreen = Settings.gainOk(S.qcGainGreen);
            S.greenCapF = Settings.capOk(S.greenCapF);
            S.parClusterOk();
        }
        if (what.equals("double")) {
            S.redThresholdFactor = gd.getNextNumber();
            S.redCapF = gd.getNextNumber();
            if (S.dblMode != 1) S.minOverlapPercent = gd.getNextNumber();
            S.redBgRolling = gd.getNextNumber();
            S.redBlurSigma = gd.getNextNumber();
            S.qcGainRed = gd.getNextNumber();
            S.qcGainGreen = gd.getNextNumber();
            if (S.dblMode == 1) {
                S.objMinArea = gd.getNextNumber();
                S.objMaxArea = gd.getNextNumber();
                S.objTol = gd.getNextNumber();
                S.objMinN = gd.getNextNumber();
            }
            if (S.dblMode == 2) {
                S.encMinArea = gd.getNextNumber();
                S.encMaxArea = gd.getNextNumber();
            }
            if (S.dblMode == 1 || S.dblPolesUsed) {
                S.centrePct = gd.getNextNumber();
                S.endPct = gd.getNextNumber();
            }
            if (S.dblPolesUsed) {
                if (S.poleNeedNuc) {
                    S.pnMinArea = gd.getNextNumber();
                    S.pnMaxArea = gd.getNextNumber();
                }
                if (S.poleNeedMk) {
                    S.pmThresholdFactor = gd.getNextNumber();
                    S.pmMinArea = gd.getNextNumber();
                    S.pmMaxArea = gd.getNextNumber();
                }
                S.poleDeadPct = gd.getNextNumber();
            }
            S.redThresholdMethod = gd.getNextChoice();
            if (S.dblMode == 1) {
                String pc = gd.getNextChoice();
                for (int q = 0; q < Settings.POS_MODES.length; q++) if (pc.equals(Settings.POS_MODES[q])) S.posMode = q;
                S.objWatershed = gd.getNextBoolean();
            }
            S.objSettingsOk();
            S.qcGainRed = Settings.gainOk(S.qcGainRed);
            S.qcGainGreen = Settings.gainOk(S.qcGainGreen);
            S.redCapF = Settings.capOk(S.redCapF);
        }
        action = gd.getNextChoice();
        return true;
    }

    // ================================================================== Discard
    /** Settings as they were before calibrating, for "Discard". */
    void save() {
        bak = new double[]{S.nucleiThresholdFactor, S.nucleiMinArea, S.nucleiBgRolling, S.nucleiBlurSigma, b(S.nucleiWatershed),
                b(S.splitBigNuclei), S.nucleiDoubletFactor, S.nucleiMaxSplit, S.nucleiEdgeMode, S.greenThresholdFactor,
                S.greenMinArea, S.greenMaxArea, S.greenBgRolling, S.greenBlurSigma, b(S.greenWatershed), S.redThresholdFactor,
                S.minOverlapPercent, S.redBgRolling, S.redBlurSigma, S.nucleiWsTol, S.greenWsTol, S.nucleiFragFactor,
                S.qcGainBlue, S.qcGainGreen, S.qcGainRed, S.nucleiCapF, S.greenCapF, S.redCapF, b(S.splitBigParasites),
                S.greenDoubletFactor, S.greenMaxSplit, S.objMinArea, S.objMaxArea, S.objTol, S.objMinN, b(S.objWatershed),
                S.encMinArea, S.encMaxArea, S.posMode, S.centrePct, S.endPct, S.pnMinArea, S.pnMaxArea, S.pmThresholdFactor,
                S.pmMinArea, S.pmMaxArea, S.poleDeadPct};
        bakS = new String[]{S.nucleiCirc, S.nucleiThresholdMethod, S.greenCirc, S.greenThresholdMethod, S.redThresholdMethod};
    }

    static double b(boolean v) {
        return v ? 1 : 0;
    }

    void restore() {
        int i = 0;
        S.nucleiThresholdFactor = bak[i++];
        S.nucleiMinArea = bak[i++];
        S.nucleiBgRolling = bak[i++];
        S.nucleiBlurSigma = bak[i++];
        S.nucleiWatershed = bak[i++] != 0;
        S.splitBigNuclei = bak[i++] != 0;
        S.nucleiDoubletFactor = bak[i++];
        S.nucleiMaxSplit = bak[i++];
        S.nucleiEdgeMode = (int) bak[i++];
        S.greenThresholdFactor = bak[i++];
        S.greenMinArea = bak[i++];
        S.greenMaxArea = bak[i++];
        S.greenBgRolling = bak[i++];
        S.greenBlurSigma = bak[i++];
        S.greenWatershed = bak[i++] != 0;
        S.redThresholdFactor = bak[i++];
        S.minOverlapPercent = bak[i++];
        S.redBgRolling = bak[i++];
        S.redBlurSigma = bak[i++];
        S.nucleiWsTol = bak[i++];
        S.greenWsTol = bak[i++];
        S.nucleiFragFactor = bak[i++];
        S.qcGainBlue = bak[i++];
        S.qcGainGreen = bak[i++];
        S.qcGainRed = bak[i++];
        S.nucleiCapF = bak[i++];
        S.greenCapF = bak[i++];
        S.redCapF = bak[i++];
        S.splitBigParasites = bak[i++] != 0;
        S.greenDoubletFactor = bak[i++];
        S.greenMaxSplit = bak[i++];
        S.objMinArea = bak[i++];
        S.objMaxArea = bak[i++];
        S.objTol = bak[i++];
        S.objMinN = bak[i++];
        S.objWatershed = bak[i++] != 0;
        S.encMinArea = bak[i++];
        S.encMaxArea = bak[i++];
        S.posMode = (int) bak[i++];
        S.centrePct = bak[i++];
        S.endPct = bak[i++];
        S.pnMinArea = bak[i++];
        S.pnMaxArea = bak[i++];
        S.pmThresholdFactor = bak[i++];
        S.pmMinArea = bak[i++];
        S.pmMaxArea = bak[i++];
        S.poleDeadPct = bak[i];
        S.nucleiCirc = bakS[0];
        S.nucleiThresholdMethod = bakS[1];
        S.greenCirc = bakS[2];
        S.greenThresholdMethod = bakS[3];
        S.redThresholdMethod = bakS[4];
    }

    /** Every image the calibration made is let go, except the preview. */
    void closeTemp() {
        for (Img im : imgs)
            for (Slot s : im.slot) {
                s.pre = null;
                s.bin = null;
            }
        imgs.clear();
    }
}
