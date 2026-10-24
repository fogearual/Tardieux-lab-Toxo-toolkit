package toxo.cac;

import ij.ImagePlus;
import ij.Prefs;
import ij.gui.Roi;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.filter.ParticleAnalyzer;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.util.Tools;

import java.util.ArrayList;
import java.util.List;

/**
 * "Analyze Particles" as the macro runs it ("size=a-b circularity=c-d show=Nothing add", "show=Masks",
 * "exclude"), with ImageJ's own ParticleAnalyzer: the outlines go to a list of this image instead of the ROI
 * Manager, which is shared by every image, so that several images can be analysed at once.
 */
public final class Particles {
    private Particles() {}

    /** Outlines of the particles, in scan order, and the mask of "show=Masks" (null otherwise). */
    public static final class Result {
        public final List<Roi> rois = new ArrayList<Roi>();
        public ImagePlus mask;
    }

    private static final class PA extends ParticleAnalyzer {
        final List<Roi> out;

        PA(int options, ResultsTable rt, double min, double max, double cmin, double cmax, List<Roi> out) {
            super(options, Measurements.AREA, rt, min, max, cmin, cmax);
            this.out = out;
        }

        ImagePlus imp;

        @Override
        protected void saveResults(ImageStatistics stats, Roi roi) {
            super.saveResults(stats, roi);
            if (out != null) {
                // named as the ROI Manager names the particles "Analyze Particles ... add" gives it
                roi.setName(label(imp, roi, out.size() + 1));
                out.add((Roi) roi.clone());
            }
        }
    }

    /** RoiManager.getLabel(imp, roi, n) for a particle n: its number, then the y of its centre. */
    static String label(ImagePlus imp, Roi roi, int n) {
        java.awt.Rectangle r = roi.getBounds();
        int xc = r.y + r.height / 2;
        int yc = n;
        if (xc < 0) xc = 0;
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

    /**
     * Analyze Particles on imp as by the macro command.
     * @param size   "a-b" in calibrated units (as the macro wrote it, "Infinity" allowed)
     * @param circ   "c-d", or null for 0.00-1.00
     * @param masks  "show=Masks" (else "show=Nothing add")
     */
    public static Result analyze(ImagePlus imp, String size, String circ, boolean exclude, boolean masks) {
        Result r = new Result();
        double unitSquared = imp.getCalibration().pixelWidth * imp.getCalibration().pixelHeight;
        String[] mm = Tools.split(size, " -");
        double mins = mm.length >= 1 ? Tools.parseDouble(mm[0]) : 0.0;
        double maxs = mm.length == 2 ? Tools.parseDouble(mm[1]) : Double.NaN;
        double minSize = Double.isNaN(mins) ? 0.0 : mins / unitSquared;
        double maxSize = Double.isNaN(maxs) ? Double.POSITIVE_INFINITY : maxs / unitSquared;
        if (minSize < 0.0) minSize = 0.0;
        if (maxSize < minSize) maxSize = Double.POSITIVE_INFINITY;
        double minc = 0.0, maxc = 1.0;
        if (circ != null) {
            String[] cc = Tools.split(circ, " -");
            double a = cc.length >= 1 ? Tools.parseDouble(cc[0]) : 0.0;
            double b = cc.length == 2 ? Tools.parseDouble(cc[1]) : Double.NaN;
            minc = Double.isNaN(a) ? 0.0 : a;
            maxc = Double.isNaN(b) ? 1.0 : b;
            if (minc < 0.0) minc = 0.0;
            if (minc > maxc && maxc == 1.0) minc = 0.0;
            if (minc > maxc) minc = maxc;
            if (maxc < minc) maxc = minc;
            if (minc == 1.0 && maxc == 1.0) minc = 0.0;
        }
        int options = masks ? ParticleAnalyzer.SHOW_MASKS : ParticleAnalyzer.SHOW_NONE;
        if (exclude) options |= ParticleAnalyzer.EXCLUDE_EDGE_PARTICLES;
        ResultsTable rt = new ResultsTable();
        PA pa = new PA(options, rt, minSize, maxSize, minc, maxc, masks ? null : r.rois);
        pa.imp = imp;
        pa.setHideOutputImage(true);
        imp.deleteRoi();
        ImageProcessor ip = imp.getProcessor();
        // A binary image without a threshold: the analyzer takes the level the macro version gets (255, or 0
        // for a white background), but would also write "threshold not set" to the Log, which it does not do
        // inside a macro. The same level, set as an invisible threshold, finds the same particles silently.
        boolean setHere = false;
        if (ip.getMinThreshold() == ImageProcessor.NO_THRESHOLD && ip instanceof ByteProcessor) {
            double level = 255;
            if (!Prefs.blackBackground && !imp.isInvertedLut()) level = 0;
            ip.setThreshold(level, level, ImageProcessor.NO_LUT_UPDATE);
            setHere = true;
        }
        try {
            pa.analyze(imp, ip);
        } finally {
            if (setHere) ip.resetThreshold();
        }
        if (masks) r.mask = pa.getOutputImage();
        return r;
    }
}
