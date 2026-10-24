package toxo.cac;

import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.io.FileSaver;
import ij.plugin.ChannelSplitter;
import ij.plugin.RGBStackMerge;
import ij.process.LUT;

import java.awt.image.IndexColorModel;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * The image outputs, as the macro version makes them: hyperstack TIFF, maximum Z projection, composite merge of
 * the chosen channels keeping all Z planes, composite merge of the projection; each channel in the colour chosen
 * for it, at its own bit depth.
 */
final class ImageOutputs {
    private ImageOutputs() {}

    static void run(Run R) {
        Settings S = R.S;
        boolean doMerge = S.doMergeStack || S.doMergeProj;
        R.log("Channel colours in the images made: " + coloursText(R));
        if (doMerge) R.log("Channels in the merges: " + mergeText(S));
        int n = R.entries.size();
        for (int i = 0; i < n; i++) {
            Input.Entry e = R.entries.get(i);
            String p = e.path;
            String bn = Input.stripExt(new File(p).getName());
            if (e.seriesTotal > 1) bn = bn + "_s" + e.series;
            if (!new File(p).exists()) {
                R.log("   MISSING FILE, skipped: " + p);
                continue;
            }
            Input.Opened o = Input.openFile(S, e);
            for (String l : o.log) R.log(l);
            if (o.imp == null) {
                R.log("[" + (i + 1) + "/" + n + "] " + bn + "   ERROR: " + o.err + " -> skipped");
                continue;
            }
            ImagePlus src = o.imp;
            Input.Px px = Input.applyPixelSize(S, src);
            if (!R.calibShown && Input.pixelNote(S, px)) {
                R.log(Input.pixelNoteText(S));
                R.calibShown = true;
            }
            R.lastPx = px.w;
            R.lastPxH = px.h;
            R.lastPxSource = px.source;
            R.log("[" + (i + 1) + "/" + n + "] " + bn + "   pixel size " + Macro.d2s(px.w, 4) + " um (" + px.source + ")");
            int cnz = src.getNSlices(), cnt = src.getNFrames();
            if (cnt > 1) R.log("   time series: " + cnt + " frames, " + cnz + " Z plane(s)");
            applyColours(S, src);
            if (S.doHyper) save(src, outSub(R, "Hyperstacks", e.manip, e.cond) + bn + ".tif");
            if (S.doProject && cnz == 1 && cnt > 1)
                R.log("   no Z planes to project: this file is a time series (" + cnt + " frames). Nothing projected.");
            if (S.doProject && cnz > 1) {
                ImagePlus pj = Cmd.maxProject(src, cnt > 1);
                save(pj, outSub(R, "Projections", e.manip, e.cond) + bn + "_MAX.tif");
            }
            if (S.doMergeStack) {
                ImagePlus mrg = buildMerge(S, src, bn + "_s");
                if (mrg != null) save(mrg, outSub(R, "Merges", e.manip, e.cond) + bn + "_merge.tif");
            }
            if (S.doMergeProj) {
                ImagePlus pT = cnz > 1 ? Cmd.maxProject(src, cnt > 1) : Cmd.dupAll(src, bn + "_p1");
                ImagePlus mrg = buildMerge(S, pT, bn + "_p");
                if (mrg != null) save(mrg, outSub(R, "Projection merges", e.manip, e.cond) + bn + "_MAX_merge.tif");
            }
            IJ.showProgress(i + 1, n);
            if (R.stopAsked()) {
                R.log("STOPPED with Esc after " + (i + 1) + " of " + n + " file(s).");
                break;
            }
        }
        R.log("");
        R.log("Image outputs written in " + R.outputRoot);
    }

    static void save(ImagePlus imp, String path) {
        new FileSaver(imp).saveAsTiff(path);
    }

    /** <output>/<kind>/<experiment>/<condition>/, created as needed. */
    static String outSub(Run R, String kind, String manip, String cond) {
        String d = R.outputRoot + kind + File.separator;
        new File(d).mkdirs();
        if (!manip.equals("-")) {
            d = d + manip + File.separator;
            new File(d).mkdirs();
        }
        d = d + cond + File.separator;
        new File(d).mkdirs();
        return d;
    }

    /** The colours chosen for the images made (channels beyond the table keep theirs). */
    static void applyColours(Settings S, ImagePlus imp) {
        if (imp.getBitDepth() == 24) return;
        int anc = imp.getNChannels();
        for (int c = 1; c <= anc; c++) {
            String cw = S.chCols.length >= c ? S.chCols[c - 1] : "file";
            String lc = lutCmd(cw);
            if (lc.isEmpty()) continue;
            if (anc > 1) imp.setC(c);
            double lo = imp.getDisplayRangeMin(), hi = imp.getDisplayRangeMax();
            Cmd.applyLut(imp, lc);
            Cmd.setMinAndMax(imp, lo, hi);
        }
        if (anc > 1) {
            imp.setC(1);
            if (imp.isComposite()) ((CompositeImage) imp).setMode(IJ.COLOR);
        }
    }

    static String lutCmd(String cw) {
        if (cw.equals("grey")) return "Grays";
        if (cw.equals("blue")) return "Blue";
        if (cw.equals("green")) return "Green";
        if (cw.equals("red")) return "Red";
        if (cw.equals("cyan")) return "Cyan";
        if (cw.equals("magenta")) return "Magenta";
        if (cw.equals("yellow")) return "Yellow";
        return "";
    }

    /** Composite merge of the channels ticked, every Z plane kept, each channel in its colour; null if none. */
    static ImagePlus buildMerge(Settings S, ImagePlus src, String bn) {
        ImagePlus dup = Cmd.dupAll(src, bn + "_dup");
        int mc = dup.getNChannels();
        List<Integer> sel = new ArrayList<Integer>();
        for (int c = 1; c <= mc; c++) {
            boolean on = S.chMrg.length >= c && S.chMrg[c - 1] == 1;
            if (on && sel.size() < 7) sel.add(c);
        }
        if (sel.isEmpty()) return null;
        List<LUT> luts = new ArrayList<LUT>();
        double[] los = new double[sel.size()], his = new double[sel.size()];
        for (int k = 0; k < sel.size(); k++) {
            if (mc > 1) dup.setC(sel.get(k));
            luts.add(lutOf(dup));
            los[k] = dup.getDisplayRangeMin();
            his[k] = dup.getDisplayRangeMax();
        }
        ImagePlus[] ch = mc > 1 ? ChannelSplitter.split(dup) : new ImagePlus[]{dup};
        if (sel.size() == 1) {
            ImagePlus one = ch[sel.get(0) - 1];
            one.setTitle(bn + "_merge");
            return one;
        }
        ImagePlus[] slots = new ImagePlus[sel.size()];
        for (int k = 0; k < sel.size(); k++) slots[k] = ch[sel.get(k) - 1];
        ImagePlus m = RGBStackMerge.mergeChannels(slots, false);
        if (m == null) return null;
        m.setCalibration(slots[0].getCalibration());
        m.setTitle(bn + "_merge");
        if (m instanceof CompositeImage) {
            CompositeImage ci = (CompositeImage) m;
            for (int k = 0; k < sel.size(); k++) {
                ci.setC(k + 1);
                LUT l = luts.get(k);
                ci.setChannelLut(l);
                Cmd.setMinAndMax(ci, los[k], his[k]);
            }
            ci.setC(1);
            ci.setMode(IJ.COMPOSITE);
        }
        return m;
    }

    static LUT lutOf(ImagePlus imp) {
        if (imp.isComposite()) return ((CompositeImage) imp).getChannelLut();
        IndexColorModel cm = (IndexColorModel) imp.getProcessor().getColorModel();
        return new LUT(cm, imp.getDisplayRangeMin(), imp.getDisplayRangeMax());
    }

    static String coloursText(Run R) {
        Settings S = R.S;
        StringBuilder out = new StringBuilder();
        for (int c = 1; c <= S.chCols.length; c++) {
            String cw = S.chCols[c - 1];
            if (cw.equals("file")) {
                cw = "as in the file";
                if (R.firstCols.length >= c) cw = cw + " (" + R.firstCols[c - 1] + ")";
            }
            if (out.length() > 0) out.append(", ");
            out.append(c).append(' ').append(cw);
        }
        return out.length() == 0 ? "as in the files" : out.toString();
    }

    static String mergeText(Settings S) {
        StringBuilder out = new StringBuilder();
        for (int c = 1; c <= S.chMrg.length; c++)
            if (S.chMrg[c - 1] == 1) {
                if (out.length() > 0) out.append(", ");
                out.append(c);
            }
        return out.length() == 0 ? "none (no merge made)" : out.toString();
    }
}
