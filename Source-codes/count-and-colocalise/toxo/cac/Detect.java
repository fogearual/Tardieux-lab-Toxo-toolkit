package toxo.cac;

import ij.IJ;
import ij.ImagePlus;
import ij.LookUpTable;
import ij.Prefs;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.gui.Wand;
import ij.measure.Calibration;
import ij.measure.Measurements;
import ij.plugin.ContrastEnhancer;
import ij.plugin.Duplicator;
import ij.plugin.filter.BackgroundSubtracter;
import ij.plugin.filter.Binary;
import ij.plugin.filter.EDM;
import ij.plugin.filter.GaussianBlur;
import ij.plugin.filter.MaximumFinder;
import ij.plugin.filter.ThresholdToSelection;
import ij.process.Blitter;
import ij.process.ByteProcessor;
import ij.process.FloatPolygon;
import ij.process.FloatProcessor;
import ij.process.ImageConverter;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.process.ShortProcessor;

import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The detection functions of Count and Colocalise, shared by the batch and by Calibrate, transcribed from the
 * macro version 23 one by one: same steps, same order, the same ImageJ classes each macro command runs, so that
 * the same image gives the same objects. One instance per image: nothing is shared between images, which can
 * then be processed at the same time. The "ROI Manager" of an image is the list rm.
 */
public final class Detect implements Measurements {

    final Settings S;
    /** Lines for the Log, in the order the macro printed them (some functions print). */
    final List<String> log = new ArrayList<String>();

    // results of the last automatic threshold (autoLow)
    double autoHi, capVal, capN, spikeN, thrMed, thrMax, medMax;
    int capHit, spikeHit;
    double capObj;
    String thrNote = "";
    double nucThr, nucThrAuto, parThr, parThrAuto, dblThr, dblThrAuto;

    // results of the counting functions
    int[] cellClusterIdx = new int[0], cellFragIdx = new int[0];
    double cellMedian;
    int[] parClusterIdx = new int[0];
    double[] parWeight = new double[0];
    double parMedian;
    int[] dblIdx = new int[0];
    double dblN20, dblN35, dblN55;

    // objects, places, poles
    double[] fX = new double[0], fY = new double[0], fA = new double[0];
    double fe1x, fe1y, fe2x, fe2y;
    double[] aX1 = new double[0], aY1 = new double[0], aX2 = new double[0], aY2 = new double[0],
            aLen = new double[0], aArea = new double[0];
    double[] oX = new double[0], oY = new double[0], oR = new double[0], oT = new double[0];
    int[] oOwn = new int[0], oUse = new int[0], oPole = new int[0];
    double[] pObj = new double[0], pCnt = new double[0], pPlace = new double[0];
    int[] pBas = new int[0], pCls = new int[0];
    double[] pNuc = new double[0], pMk = new double[0];
    String[] pWhy = new String[0];
    double[] poleN = new double[5];
    double poleOri;
    String poleNote = "";
    double objN, objOn, objUsed;

    /** The ROI Manager of this image: outlines in the order the macro's ROI Manager held them. */
    public final List<Roi> rm = new ArrayList<Roi>();

    public Detect(Settings s) {
        this.S = s;
    }

    // ================================================================== macro primitives
    /** run("Duplicate...") of a single-plane image, as Duplicator does it. */
    static ImagePlus dup(ImagePlus imp, String title) {
        imp.deleteRoi();
        ImagePlus d = new Duplicator().crop(imp);
        d.setTitle(title);
        d.setProp("UniqueName", "true");
        return d;
    }

    /** getRawStatistics(nPixels, mean, min, max) of the whole image. */
    static ImageStatistics raw(ImagePlus imp) {
        ImageProcessor ip = imp.getProcessor();
        ip.setRoi(imp.getRoi());
        return ImageStatistics.getStatistics(ip, AREA + MEAN + MIN_MAX, null);
    }

    /** getHistogram(values, counts, nBins[, histMin, histMax]) of the macro language. */
    static final class Hist {
        double[] hv;
        int[] hc;
    }

    static Hist hist(ImagePlus imp, int nBins) {
        return hist(imp, nBins, 0, 0, false);
    }

    static Hist hist(ImagePlus imp, int nBins, double histMin, double histMax, boolean setMinMax) {
        Hist h = new Hist();
        int bitDepth = imp.getBitDepth();
        if (nBins == 65536 && bitDepth == 16) {
            ImageProcessor ip = imp.getProcessor();
            Roi roi = imp.getRoi();
            if (roi != null) ip.setRoi(roi);
            h.hc = ip.getHistogram();
            return h;
        }
        ImageStatistics stats;
        boolean custom8Bit = false;
        ImagePlus imp2 = imp;
        if ((bitDepth == 8 || bitDepth == 24) && nBins != 256) {
            ImageProcessor ip = imp.getProcessor().convertToShort(false);
            imp2 = imp.createImagePlus();
            imp2.setProcessor(ip);
            stats = imp2.getStatistics(AREA + MEAN + MODE + MIN_MAX, nBins, 0, 256);
            custom8Bit = true;
        } else if (setMinMax)
            stats = imp.getStatistics(AREA + MEAN + MODE + MIN_MAX, nBins, histMin, histMax);
        else
            stats = imp.getStatistics(AREA + MEAN + MODE + MIN_MAX, nBins);
        Calibration cal = imp2.getCalibration();
        double[] array = new double[nBins];
        double value = cal.getCValue(stats.histMin);
        double inc = 1.0;
        if (bitDepth == 16 || bitDepth == 32 || cal.calibrated() || custom8Bit)
            inc = (cal.getCValue(stats.histMax) - cal.getCValue(stats.histMin)) / stats.nBins;
        for (int i = 0; i < nBins; i++) {
            array[i] = value;
            value += inc;
        }
        h.hv = array;
        h.hc = stats.histogram;
        return h;
    }

    /** setThreshold(lower, upper) of the macro language. */
    static void setThreshold(ImagePlus imp, double lower, double upper) {
        imp.getProcessor().setThreshold(lower, upper, ImageProcessor.RED_LUT);
    }

    /** setAutoThreshold(method) of the macro language. */
    static void setAutoThreshold(ImagePlus imp, String method) {
        ImageProcessor ip = imp.getProcessor();
        ip.setRoi(imp.getRoi());
        ip.setAutoThreshold(method);
    }

    static void resetThreshold(ImagePlus imp) {
        imp.getProcessor().resetThreshold();
    }

    /** changeValues(v1, v2, v3) of the macro language, on the whole image. */
    static void changeValues(ImagePlus imp, double a, double b, double c) {
        ImageProcessor ip = imp.getProcessor();
        ip.resetRoi();
        boolean isFloat = imp.getType() == ImagePlus.GRAY32;
        int w = imp.getWidth(), h = imp.getHeight();
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                double v = isFloat ? ip.getPixelValue(x, y) : ip.getPixel(x, y) & 0xffffff;
                boolean replace = v >= a && v <= b;
                if (Double.isNaN(a) && Double.isNaN(b) && Double.isNaN(v)) replace = true;
                if (replace) {
                    if (isFloat) ip.putPixelValue(x, y, c);
                    else ip.putPixel(x, y, (int) c);
                }
            }
    }

    /** run("32-bit"). */
    static void to32(ImagePlus imp) {
        imp.deleteRoi();
        if (imp.isThreshold()) imp.getProcessor().resetThreshold();
        new ImageConverter(imp).convertToGray32();
    }

    /** run("Convert to Mask") on a single thresholded image, as Thresholder does it. */
    static void convertToMask(ImagePlus imp) {
        imp.deleteRoi();
        ImageProcessor ip = imp.getProcessor();
        ip.resetBinaryThreshold();
        int type = imp.getType();
        if (type == ImagePlus.GRAY16 || type == ImagePlus.GRAY32) {
            int size = imp.getWidth() * imp.getHeight();
            float t1 = (float) ip.getMinThreshold();
            float t2 = (float) ip.getMaxThreshold();
            if (t1 == ImageProcessor.NO_THRESHOLD) throw new IllegalStateException("no threshold");
            ByteProcessor ip2 = new ByteProcessor(imp.getWidth(), imp.getHeight());
            for (int j = 0; j < size; j++) {
                float value = ip.getf(j);
                ip2.set(j, value >= t1 && value <= t2 ? 255 : 0);
            }
            Calibration cal = imp.getCalibration();
            String label = imp.getStack().getSliceLabel(1);
            ij.ImageStack st = new ij.ImageStack(imp.getWidth(), imp.getHeight());
            st.addSlice(label, ip2);
            imp.setStack(null, st);
            ij.ImageStack stack = imp.getStack();
            stack.setColorModel(LookUpTable.createGrayscaleColorModel(!Prefs.blackBackground));
            imp.setStack(null, stack);
            imp.setCalibration(cal);
            imp.getProcessor().setThreshold(255, 255, ImageProcessor.NO_LUT_UPDATE);
            return;
        }
        // 8-bit: the "convert to mask" path of Thresholder.applyThreshold
        double minThreshold = ip.getMinThreshold();
        double maxThreshold = ip.getMaxThreshold();
        if (minThreshold == ImageProcessor.NO_THRESHOLD) throw new IllegalStateException("no threshold");
        int fcolor = 255, bcolor = 0;
        if (ip.isColorLut()) ip.setColorModel(ip.getDefaultColorModel());
        ip.resetThreshold();
        int[] lut = new int[256];
        for (int i = 0; i < 256; i++) lut[i] = i >= minThreshold && i <= maxThreshold ? fcolor : bcolor;
        ip.applyTable(lut);
        boolean invertedLut = imp.isInvertedLut();
        if ((invertedLut && Prefs.blackBackground) || (!invertedLut && !Prefs.blackBackground)) ip.invertLut();
        imp.getProcessor().setThreshold(fcolor, fcolor, ImageProcessor.NO_LUT_UPDATE);
    }

    /** run("8-bit") on a mask: only its (binary) threshold is removed. */
    static void eightBitMask(ImagePlus imp) {
        imp.deleteRoi();
        if (imp.isThreshold()) imp.getProcessor().resetThreshold();
    }

    /** run("Fill Holes"). */
    static void fillHoles(ImagePlus imp) {
        Binary b = new Binary();
        b.setup("fill", imp);
        ImageProcessor ip = imp.getProcessor();
        ip.resetRoi();
        b.run(ip);
    }

    /** run("Watershed"). */
    static void watershed(ImagePlus imp) {
        EDM e = new EDM();
        e.setup("watershed", imp);
        e.run(imp.getProcessor());
    }

    /** run("Create Selection") on a thresholded image: the selection is set (null if no pixel). */
    static Roi createSelection(ImagePlus imp) {
        Roi r = ThresholdToSelection.run(imp);
        if (r != null) imp.setRoi(r);
        else imp.deleteRoi();
        return r;
    }

    /** The outline idx of the list, as the selection of imp (roiManager("Select", idx)). */
    static Roi select(ImagePlus imp, Roi roi) {
        Roi r = (Roi) roi.clone();
        imp.setRoi(r);
        return imp.getRoi();
    }

    /** getValue("Area") of the selection of imp (the area does not depend on the other measurements the
     *  macro function computes). */
    static double area(ImagePlus imp) {
        return imp.getStatistics(AREA + MEAN).area;
    }

    /** getValue("Mean") of the selection of imp. */
    static double mean(ImagePlus imp) {
        return imp.getStatistics(AREA + MEAN).mean;
    }

    /** List.setMeasurements() then X, Y, Area / XM, YM of the selection of imp. */
    static ImageStatistics measure(ImagePlus imp) {
        return imp.getStatistics(ALL_STATS + SLICE);
    }

    /** Whether this ImageJ enlarges selections with RoiEnlarger.enlarge255 (1.54 and later). */
    static final boolean HAS_ENLARGE255;

    static {
        boolean h;
        try {
            ij.plugin.RoiEnlarger.class.getMethod("enlarge255", Roi.class, double.class);
            h = true;
        } catch (Throwable e) {
            h = false;
        }
        HAS_ENLARGE255 = h;
    }

    /** run("Enlarge...", "enlarge=n pixel") on the selection of imp, as this ImageJ does it. */
    static Roi enlarge(ImagePlus imp, int n) {
        Roi roi = imp.getRoi();
        if (roi == null || roi.isLine()) return roi;
        Roi roi2 = HAS_ENLARGE255 && Math.abs(n) < 256 ? enlarge255(roi, n) : ij.plugin.RoiEnlarger.enlarge(roi, n);
        if (roi2 != null) imp.setRoi(roi2);
        return imp.getRoi();
    }

    /** RoiEnlarger.enlarge255, without the switch of Prefs.blackBackground it makes for no purpose (its EDM
     *  ignores it), which would disturb the images processed at the same time. */
    static Roi enlarge255(Roi roi, double pixels) {
        if (pixels == 0) return roi;
        int type = roi.getType();
        int n = (int) Math.round(pixels);
        if (type == Roi.RECTANGLE || type == Roi.OVAL) return ij.plugin.RoiEnlarger.enlarge(roi, n);
        if (n < 0) return shrink255(roi, -n);
        Rectangle bounds = roi.getBounds();
        int width = bounds.width + 2 * n + 2;
        int height = bounds.height + 2 * n + 2;
        ImageProcessor ip = new ByteProcessor(width, height);
        ip.invert();
        roi.setLocation(n + 1, n + 1);
        ip.setColor(0);
        ip.fill(roi);
        ip.setThreshold(0, 0, ImageProcessor.NO_LUT_UPDATE);
        Roi roi2 = new ThresholdToSelection().convert(ip);
        Rectangle bounds2 = roi2.getBounds();
        int xoffset = bounds2.x - (n + 1);
        int yoffset = bounds2.y - (n + 1);
        roi.setLocation(bounds.x, bounds.y);
        new EDM().toEDM(ip);
        ip.setThreshold(0, n, ImageProcessor.NO_LUT_UPDATE);
        roi2 = new ThresholdToSelection().convert(ip);
        if (roi2 == null) return roi;
        roi2.copyAttributes(roi);
        roi2.setLocation(bounds.x - n + xoffset, bounds.y - n + yoffset);
        if (roi.getStroke() != null) roi2.setStroke(roi.getStroke());
        return roi2;
    }

    private static Roi shrink255(Roi roi, int n) {
        Rectangle bounds = roi.getBounds();
        int width = bounds.width + 2;
        int height = bounds.height + 2;
        ImageProcessor ip = new ByteProcessor(width, height);
        roi.setLocation(1, 1);
        ip.setColor(255);
        ip.fill(roi);
        roi.setLocation(bounds.x, bounds.y);
        new EDM().toEDM(ip);
        ip.setThreshold(n + 1, 255, ImageProcessor.NO_LUT_UPDATE);
        Roi roi2 = new ThresholdToSelection().convert(ip);
        if (roi2 == null) return roi;
        Rectangle bounds2 = roi2.getBounds();
        if (bounds2.width <= 0 && bounds2.height <= 0) return roi;
        roi2.copyAttributes(roi);
        roi2.setLocation(bounds.x + bounds2.x - 1, bounds.y + bounds2.y - 1);
        return roi2;
    }

    /** getSelectionBounds(x, y, w, h): the integer bounds, the whole image without a selection. */
    static Rectangle bounds(ImagePlus imp, Roi roi) {
        if (roi == null) return new Rectangle(0, 0, imp.getWidth(), imp.getHeight());
        return roi.getBounds();
    }

    /** run("Analyze Particles...", "size=a-b circularity=c show=Nothing add"): the outlines found. */
    static List<Roi> particles(ImagePlus imp, double aMin, String aMax, String circ, boolean exclude) {
        return Particles.analyze(imp, Macro.n2s(aMin) + "-" + aMax, circ, exclude, false).rois;
    }

    static List<Roi> particles(ImagePlus imp, double aMin, double aMax, String circ, boolean exclude) {
        return particles(imp, aMin, Macro.n2s(aMax), circ, exclude);
    }

    static ImagePlus newByte(String title, int w, int h) {
        return new ImagePlus(title, new ByteProcessor(w, h));
    }

    // ================================================================== corrected channel
    /** Corrected copy of a channel: contrast (display only), multiplied, background subtracted, blurred. */
    ImagePlus prepChannel(ImagePlus src, String dstTitle, double mult, double rolling, double sigma) {
        ImagePlus d = dup(src, dstTitle);
        new ContrastEnhancer().stretchHistogram(d, S.enhanceSaturated);
        int bd = d.getBitDepth();
        if (mult > 1 && (bd == 8 || bd == 16)) {
            ImageStatistics st = raw(d);
            double lim = bd == 16 ? 65535 : 255;
            if (st.max * mult > lim) {
                double cutV = Math.floor(lim / mult) + 1;
                double nC = countFrom(d, cutV);
                if (nC > 0.01 * st.pixelCount) to32(d);
            }
        }
        d.getProcessor().multiply(Macro.asOption(mult));
        if (rolling > 0) {
            ImageProcessor ip = d.getProcessor();
            ip.resetRoi();
            new BackgroundSubtracter().rollingBallBackground(ip, Macro.asOption(rolling), false, false, false, true, true);
            ip.resetMinAndMax();
            if (d.getBitDepth() == 32) {
                d.getProcessor().min(0);
                d.getProcessor().resetMinAndMax();
            }
        }
        if (sigma > 0) {
            ImageProcessor ip = d.getProcessor();
            ip.resetRoi();
            double acc = ip instanceof ByteProcessor ? 0.002 : 0.0002;
            double sg = Macro.asOption(sigma);
            new GaussianBlur().blurGaussian(ip, sg, sg, acc);
        }
        return d;
    }

    /** Number of pixels at or above the value v (8- or 16-bit image). */
    static double countFrom(ImagePlus imp, double v) {
        int nb = imp.getBitDepth() == 16 ? 65536 : 256;
        Hist h = hist(imp, nb);
        long b0 = Math.round(v);
        if (b0 < 0) b0 = 0;
        double n = 0;
        for (int b = (int) b0; b < nb; b++) n += h.hc[b];
        return n;
    }

    /** Median of the pixels at or above lo (NaN left out); the brightest goes to medMax. */
    double medAbove(ImagePlus imp, double lo) {
        imp.deleteRoi();
        ImageStatistics st = raw(imp);
        double maxV = st.max;
        medMax = maxV;
        if (Double.isNaN(maxV) || maxV < lo) return 0;
        if (imp.getBitDepth() == 32) {
            int nb = 1024;
            if (maxV <= lo) return lo;
            double bw = (maxV - lo) / nb;
            Hist h = hist(imp, nb, lo, maxV + bw, true);
            double tot = 0;
            for (int b = 0; b < nb; b++) tot += h.hc[b];
            if (tot == 0) return 0;
            double acc = 0;
            int b = 0;
            while (b < nb - 1 && acc + h.hc[b] < tot / 2) {
                acc += h.hc[b];
                b++;
            }
            return h.hv[b] + (h.hv[1] - h.hv[0]) / 2;
        }
        int nb = imp.getBitDepth() == 16 ? 65536 : 256;
        Hist h = hist(imp, nb);
        double b0 = -Math.floor(-lo);
        if (b0 < 0) b0 = 0;
        double tot = 0;
        for (int b = (int) b0; b < nb; b++) tot += h.hc[b];
        if (tot == 0) return 0;
        double acc = 0;
        int b = (int) b0;
        while (b < nb - 1 && acc + h.hc[b] < tot / 2) {
            acc += h.hc[b];
            b++;
        }
        return b;
    }

    /** 32-bit working copy for the threshold calculation. */
    static ImagePlus thrWork(ImagePlus src) {
        ImagePlus w = dup(src, src.getTitle() + "_thr");
        if (w.getBitDepth() != 32) to32(w);
        return w;
    }

    /** Selects the pixels that are not NaN. */
    static void selIncluded(ImagePlus imp) {
        imp.deleteRoi();
        setThreshold(imp, -1e30, 1e30);
        createSelection(imp);
        resetThreshold(imp);
    }

    /** Lower edge of a saturated peak in the 256-bin histogram, or 1e30 when there is none. */
    static double spikeFrom(ImagePlus imp) {
        Hist h = hist(imp, 256);
        int fnz = -1, lnz = -1, m = 0;
        for (int b = 0; b < 256; b++) {
            if (h.hc[b] > 0) {
                if (fnz < 0) fnz = b;
                lnz = b;
            }
            if (h.hc[b] > h.hc[m]) m = b;
        }
        if (fnz < 0) return 1e30;
        if (lnz - fnz < 8) return 1e30;
        if (m < fnz + 0.75 * (lnz - fnz)) return 1e30;
        int s = m;
        while (s > fnz && (double) h.hc[s - 1] * 4 >= h.hc[m]) s--;
        return h.hv[s];
    }

    /** The bright objects of the working copy (holding a spot above cap of at least minPx pixels) set to
     *  NaN whole. Returns the image to go on with and, in [0], how many objects (-1: all pixels above cap). */
    static ImagePlus dropBright(ImagePlus w, double cap, double lo, double minPx, double[] nOut) {
        w.deleteRoi();
        double nIn0 = raw(w).pixelCount;
        Calibration cal = w.getCalibration();
        double pw = cal.pixelWidth, ph = cal.pixelHeight;
        ImagePlus w2 = dup(w, w.getTitle() + "2");
        setThreshold(w2, cap, 1e30);
        // Find Maxima..., "noise=0 output=[Point Selection] above"
        int[] xs = new int[0], ys = new int[0];
        Polygon pts = maxima(w2, 0.0);
        if (pts != null) {
            xs = Arrays.copyOf(pts.xpoints, pts.npoints);
            ys = Arrays.copyOf(pts.ypoints, pts.npoints);
        }
        int nP = xs.length;
        w2.deleteRoi();
        resetThreshold(w2);
        double nObj = -1;
        if (nP <= 500) {
            nObj = 0;
            ImageProcessor ip = w2.getProcessor();
            for (int q = 0; q < nP; q++) {
                double v = ip.getPixelValue(xs[q], ys[q]);
                if (Double.isNaN(v)) continue;
                // the bright spot: a hot pixel, or an object brighter than the cap?
                setThreshold(w2, cap, 1e30);
                doWand(w2, xs[q], ys[q]);
                double aSel = statsOf(w2, AREA + MEAN + MIN_MAX, true).area;
                w2.deleteRoi();
                if (aSel / (pw * ph) >= minPx) {
                    // the whole object, down to the threshold
                    setThreshold(w2, lo, 1e30);
                    Roi r2 = doWand(w2, xs[q], ys[q]);
                    setInside(w2, r2, Double.NaN);
                    w2.deleteRoi();
                    nObj = nObj + 1;
                }
            }
            resetThreshold(w2);
            double nIn1 = raw(w2).pixelCount;
            if (nIn0 - nIn1 > 0.25 * w2.getWidth() * w2.getHeight()) nObj = -1;
        }
        if (nObj < 0) {
            changeValues(w, cap, 1e30, Double.NaN);
            nOut[0] = -1;
            return w;
        }
        w2.setTitle(w.getTitle());
        nOut[0] = nObj;
        return w2;
    }

    /** getStatistics(area, mean, ...) of the macro language (calibrated: area) on the current selection. */
    static ImageStatistics statsOf(ImagePlus imp, int params, boolean calibrated) {
        Calibration cal = calibrated ? imp.getCalibration() : null;
        ImageProcessor ip = imp.getProcessor();
        ip.setRoi(imp.getRoi());
        return ImageStatistics.getStatistics(ip, params, cal);
    }

    /** doWand(x, y) on a thresholded image (legacy mode); the selection is set and returned. */
    static Roi doWand(ImagePlus imp, int x, int y) {
        ImageProcessor ip = imp.getProcessor();
        if (imp.getType() == ImagePlus.GRAY32 && Double.isNaN(ip.getPixelValue(x, y))) return imp.getRoi();
        Wand w = new Wand(ip);
        if (!ip.isThreshold()) w.autoOutline(x, y, 0.0, Wand.LEGACY_MODE);
        else w.autoOutline(x, y, ip.getMinThreshold(), ip.getMaxThreshold(), Wand.LEGACY_MODE);
        if (w.npoints > 0) {
            Roi roi = new PolygonRoi(w.xpoints, w.ypoints, w.npoints, Roi.TRACED_ROI);
            imp.deleteRoi();
            imp.setRoi(roi);
        }
        return imp.getRoi();
    }

    /** run("Set...", "value=v") on the selection: the pixels inside its mask (all, without a selection). */
    static void setInside(ImagePlus imp, Roi roi, double v) {
        ImageProcessor ip = imp.getProcessor();
        if (roi == null) ip.resetRoi();
        else ip.setRoi(roi);
        Rectangle r = ip.getRoi();
        ImageProcessor mask = ip.getMask();
        for (int y = 0; y < r.height; y++)
            for (int x = 0; x < r.width; x++) {
                if (mask != null && mask.get(x, y) == 0) continue;
                ip.putPixelValue(r.x + x, r.y + y, v);
            }
        ip.resetRoi();
    }

    /** Find Maxima ("noise=tol output=[Point Selection] above") of a thresholded image: the points, or null. */
    static Polygon maxima(ImagePlus imp, double tol) {
        ImageProcessor ip = imp.getProcessor();
        ip.resetBinaryThreshold();
        boolean thresholded = ip.isThreshold();
        boolean invertedLut = imp.isInvertedLut();
        double threshold = thresholded ? ip.getMinThreshold() : ImageProcessor.NO_THRESHOLD;
        ImageProcessor ipF = ip;
        if (invertedLut) {
            threshold = ImageProcessor.NO_THRESHOLD;
            ipF = ip.duplicate();
            ipF.invert();
        }
        imp.deleteRoi();
        MaximumFinder mf = new MaximumFinder();
        mf.setup("", imp);
        boolean isEDM = imp.getBitDepth() == 32 && imp.getTitle().startsWith("EDM of ");
        mf.findMaxima(ipF, tol, false, threshold, MaximumFinder.POINT_SELECTION, false, isEDM);
        Roi r = imp.getRoi();
        if (r == null || r.getType() != Roi.POINT) return null;
        FloatPolygon fp = r.getFloatPolygon();
        Polygon p = new Polygon();
        for (int i = 0; i < fp.npoints; i++) p.addPoint((int) fp.xpoints[i], (int) fp.ypoints[i]);
        imp.deleteRoi();
        return p;
    }

    // ================================================================== automatic threshold
    /**
     * Lower bound of the automatic threshold of the corrected channel t, bright objects on a dark background,
     * a saturated peak and the objects above the bright cap left out of the calculation (see the macro).
     */
    double autoLow(ImagePlus t, String method, double capF, double sig) {
        t.deleteRoi();
        setAutoThreshold(t, method + " dark");
        double lo = t.getProcessor().getMinThreshold(), hi = t.getProcessor().getMaxThreshold();
        autoHi = hi;
        capHit = 0;
        capVal = 0;
        capObj = 0;
        capN = 0;
        spikeHit = 0;
        spikeN = 0;
        thrNote = "";
        double nAll = (double) t.getWidth() * t.getHeight();
        double minPx = Math.round(Math.PI * (2 * sig + 1) * (2 * sig + 1));
        thrMed = 0;
        thrMax = 0;
        if (capF > 0) {
            thrMed = medAbove(t, lo);
            thrMax = medMax;
        }
        ImagePlus w = null;
        boolean done = false;
        for (int it = 0; it < 4; it++) {
            if (done) continue;
            ImagePlus cur = w == null ? t : w;
            cur.deleteRoi();
            boolean changed = false;
            double sp = spikeFrom(cur);
            if (sp < 1e30) {
                if (w == null) w = thrWork(t);
                w.deleteRoi();
                double nA = raw(w).pixelCount;
                changeValues(w, sp, 1e30, Double.NaN);
                double nB = raw(w).pixelCount;
                spikeN = spikeN + nA - nB;
                spikeHit = 1;
                changed = true;
            } else if (capF > 0) {
                double med = thrMed, mxI = thrMax;
                if (w != null) {
                    med = medAbove(w, lo);
                    mxI = medMax;
                }
                if (med > 0 && mxI >= capF * med) {
                    if (w == null) w = thrWork(t);
                    w.deleteRoi();
                    double nA = raw(w).pixelCount;
                    double[] nbo = new double[1];
                    w = dropBright(w, capF * med, lo, minPx, nbo);
                    double nb = nbo[0];
                    double nB = raw(w).pixelCount;
                    if (nb != 0) {
                        capN = capN + nA - nB;
                        if (capHit == 0) capVal = capF * med;
                        capHit = 1;
                        if (nb < 0) capObj = -1;
                        if (nb > 0 && capObj >= 0) capObj = capObj + nb;
                        changed = true;
                    }
                }
            }
            if (!changed) {
                done = true;
            } else {
                selIncluded(w);
                setAutoThreshold(w, method + " dark");
                lo = w.getProcessor().getMinThreshold();
                resetThreshold(w);
                w.deleteRoi();
            }
        }
        if (spikeHit == 1)
            thrNote = "a peak of " + Macro.n2s(spikeN) + " saturated pixels (" + IJ.d2s(100.0 * spikeN / nAll, 1)
                    + " % of the image) left out of the threshold calculation";
        if (capHit == 1) {
            if (!thrNote.isEmpty()) thrNote = thrNote + "; ";
            String cn = Macro.n2s(capN) + " pixels";
            if (capObj > 0) cn = Macro.n2s(capObj) + " object(s)";
            thrNote = thrNote + cn + " brighter than " + Macro.n2s(capF) + " x the median (" + Macro.n2s(Math.round(capVal))
                    + ") left out of the threshold calculation";
        }
        t.deleteRoi();
        return lo;
    }

    // ================================================================== nuclei
    ImagePlus segmentNuclei(ImagePlus src, String maskTitle) {
        ImagePlus m = nucleiPre(src, maskTitle);
        nucleiMask(m);
        nucleiObjects(m);
        return m;
    }

    ImagePlus nucleiPre(ImagePlus src, String dstTitle) {
        return prepChannel(src, dstTitle, S.nucleiMultiply, S.nucleiBgRolling, S.nucleiBlurSigma);
    }

    void nucleiMask(ImagePlus mask) {
        double nuLo = autoLow(mask, S.nucleiThresholdMethod, S.nucleiCapF, S.nucleiBlurSigma);
        nucThrAuto = nuLo;
        nucThr = nuLo * S.nucleiThresholdFactor;
        setThreshold(mask, nuLo * S.nucleiThresholdFactor, 1e30);
        convertToMask(mask);
        eightBitMask(mask);
        fillHoles(mask);
        if (S.nucleiWatershed) watershedMask(mask, S.nucleiWsTol);
    }

    void nucleiObjects(ImagePlus mask) {
        rm.clear();
        rm.addAll(particles(mask, S.nucleiMinArea, S.nucleiMaxArea, S.nucleiCirc, false));
    }

    /** Host cells from the nuclei in rm, sized against the median of the field. */
    double countCells(ImagePlus areaImp) {
        int nR = rm.size();
        cellClusterIdx = new int[0];
        cellFragIdx = new int[0];
        cellMedian = 0;
        boolean useMed = S.splitBigNuclei || S.nucleiFragFactor > 0;
        if (!useMed || nR <= 3) return nR;
        double[] nAreas = new double[nR];
        for (int n = 0; n < nR; n++) {
            select(areaImp, rm.get(n));
            nAreas[n] = area(areaImp);
        }
        areaImp.deleteRoi();
        double[] sortedA = nAreas.clone();
        Arrays.sort(sortedA);
        double medA = sortedA[nR / 2];
        double totalCells = 0;
        List<Integer> clu = new ArrayList<Integer>(), frg = new ArrayList<Integer>();
        for (int n = 0; n < nR; n++) {
            double est = 1;
            if (S.splitBigNuclei && medA > 0 && nAreas[n] >= S.nucleiDoubletFactor * medA) {
                est = Math.round(nAreas[n] / medA);
                if (est < 2) est = 2;
                if (est > S.nucleiMaxSplit) est = S.nucleiMaxSplit;
                clu.add(n);
            } else if (S.nucleiFragFactor > 0 && medA > 0 && nAreas[n] < S.nucleiFragFactor * medA) {
                est = 0;
                frg.add(n);
            }
            totalCells = totalCells + est;
        }
        cellClusterIdx = toInt(clu);
        cellFragIdx = toInt(frg);
        cellMedian = medA;
        return totalCells;
    }

    // ================================================================== parasites
    ImagePlus segmentParasites(ImagePlus src, String maskTitle, int[] nOut) {
        ImagePlus m = parasitePre(src, maskTitle);
        parasiteMask(m);
        nOut[0] = parasiteObjects(m);
        return m;
    }

    ImagePlus parasitePre(ImagePlus src, String dstTitle) {
        return prepChannel(src, dstTitle, S.greenMultiply, S.greenBgRolling, S.greenBlurSigma);
    }

    void parasiteMask(ImagePlus mask) {
        mask.deleteRoi();
        double grLo = autoLow(mask, S.greenThresholdMethod, S.greenCapF, S.greenBlurSigma);
        double grHi = autoHi;
        parThrAuto = grLo;
        grLo = Math.round(grLo * S.greenThresholdFactor);
        parThr = grLo;
        setThreshold(mask, grLo, grHi);
        convertToMask(mask);
        eightBitMask(mask);
        fillHoles(mask);
        if (S.greenWatershed) watershedMask(mask, S.greenWsTol);
    }

    int parasiteObjects(ImagePlus mask) {
        rm.clear();
        rm.addAll(particles(mask, S.greenMinArea, S.greenMaxArea, S.greenCirc, false));
        return rm.size();
    }

    /** Parasites from the objects in rm, sized against the median of the field (clumps option). */
    double countParasites(ImagePlus areaImp) {
        int nR = rm.size();
        parClusterIdx = new int[0];
        parWeight = new double[nR];
        Arrays.fill(parWeight, 1);
        parMedian = 0;
        if (!S.splitBigParasites || nR < 3) return nR;
        double[] pAreas = new double[nR];
        for (int n = 0; n < nR; n++) {
            select(areaImp, rm.get(n));
            pAreas[n] = area(areaImp);
        }
        areaImp.deleteRoi();
        double[] sortedP = pAreas.clone();
        Arrays.sort(sortedP);
        double medP = sortedP[nR / 2];
        double totalP = 0;
        List<Integer> clu = new ArrayList<Integer>();
        for (int n = 0; n < nR; n++) {
            double est = 1;
            if (medP > 0 && pAreas[n] >= S.greenDoubletFactor * medP) {
                est = Math.round(pAreas[n] / medP);
                if (est < 2) est = 2;
                if (est > S.greenMaxSplit) est = S.greenMaxSplit;
                clu.add(n);
            }
            parWeight[n] = est;
            totalP = totalP + est;
        }
        parClusterIdx = toInt(clu);
        parMedian = medP;
        return totalP;
    }

    // ================================================================== marker
    ImagePlus markerPre(ImagePlus src, String dstTitle) {
        return prepChannel(src, dstTitle, S.redMultiply, S.redBgRolling, S.redBlurSigma);
    }

    void markerMask(ImagePlus mask) {
        double redLo = autoLow(mask, S.redThresholdMethod, S.redCapF, S.redBlurSigma);
        double redHi = autoHi;
        dblThrAuto = redLo;
        redLo = Math.round(redLo * S.redThresholdFactor);
        dblThr = redLo;
        setThreshold(mask, redLo, redHi);
        convertToMask(mask);
        eightBitMask(mask);
        fillHoles(mask);
    }

    /** Overlap of each parasite (rm 0 to nPar-1) with the marker mask, in parasites (clusters weighted). */
    double markerScore(ImagePlus mask, int nPar) {
        List<Integer> idx = new ArrayList<Integer>();
        dblN20 = 0;
        dblN35 = 0;
        dblN55 = 0;
        double nDbl = 0;
        boolean useW = parWeight.length == nPar;
        for (int r = 0; r < nPar; r++) {
            double w = 1;
            if (useW) w = parWeight[r];
            select(mask, rm.get(r));
            double coveragePercent = (mean(mask) / 255) * 100;
            if (coveragePercent >= 20) dblN20 = dblN20 + w;
            if (coveragePercent >= 35) dblN35 = dblN35 + w;
            if (coveragePercent >= 55) dblN55 = dblN55 + w;
            if (coveragePercent >= S.minOverlapPercent) {
                idx.add(r);
                nDbl = nDbl + w;
            }
        }
        mask.deleteRoi();
        dblIdx = toInt(idx);
        return nDbl;
    }

    // ================================================================== marker objects, places, poles
    double scoreDoubleAll(ImagePlus maskT, int nPar, ImagePlus imgT, ImagePlus blueT, ImagePlus poleT) {
        dblN20 = 0;
        dblN35 = 0;
        dblN55 = 0;
        objN = 0;
        objOn = 0;
        objUsed = 0;
        oX = new double[0];
        oY = new double[0];
        oR = new double[0];
        oOwn = new int[0];
        oT = new double[0];
        oUse = new int[0];
        oPole = new int[0];
        pObj = new double[nPar];
        pCnt = new double[nPar];
        pBas = new int[nPar];
        pWhy = new String[nPar];
        Arrays.fill(pWhy, "0");
        pNuc = new double[nPar];
        pMk = new double[nPar];
        if (S.dblMode == 1 || S.dblPolesUsed) parasiteAxes(nPar, imgT);
        double nDbl;
        if (S.dblMode == 0) {
            nDbl = markerScore(maskT, nPar);
        } else if (S.dblMode == 1) {
            nDbl = objectsInParasites(maskT, nPar, imgT);
        } else {
            objN = findObjects(maskT, S.encMinArea, S.encMaxArea, false);
            ImagePlus encT = enclosingMask(maskT);
            nDbl = markerScore(encT, nPar);
        }
        pCls = new int[nPar];
        pPlace = new double[nPar];
        Arrays.fill(pPlace, -1);
        poleN = new double[5];
        poleOri = 0;
        poleNote = "";
        if (S.dblPolesUsed && S.dblMode != 2) {
            orientParasites(nPar, imgT, blueT, poleT);
            classifyPoles(maskT, nPar, imgT);
        }
        return nDbl;
    }

    /** Objects of the binary mask (objects 255) with an area of aMin to aMax um2: centres (pixels), areas. */
    int findObjects(ImagePlus maskT, double aMin, double aMax, boolean ws) {
        maskT.deleteRoi();
        ImagePlus tmp = dup(maskT, "CAC_objects");
        if (ws) watershed(tmp);
        Calibration cal = tmp.getCalibration();
        double pw0 = cal.pixelWidth, ph0 = cal.pixelHeight;
        List<Roi> objs = particles(tmp, aMin, aMax, "0.00-1.00", false);
        int nO = objs.size();
        fX = new double[nO];
        fY = new double[nO];
        fA = new double[nO];
        for (int q = 0; q < nO; q++) {
            select(tmp, objs.get(q));
            ImageStatistics st = measure(tmp);
            fX[q] = st.xCentroid / pw0;
            fY[q] = st.yCentroid / ph0;
            fA[q] = st.area;
        }
        return nO;
    }

    /** Mask (objects 255) of the marker objects of encMinArea to encMaxArea um2. */
    ImagePlus enclosingMask(ImagePlus maskT) {
        maskT.deleteRoi();
        Calibration cal = maskT.getCalibration();
        double pw1 = cal.pixelWidth, ph1 = cal.pixelHeight;
        String pu = cal.getUnits();
        Particles.Result r = Particles.analyze(maskT, Macro.n2s(S.encMinArea) + "-" + Macro.n2s(S.encMaxArea),
                "0.00-1.00", false, true);
        ImagePlus enc = r.mask;
        if (enc == null) {
            enc = dup(maskT, "CAC_enclosing");
            ImageProcessor ip = enc.getProcessor();
            ip.resetRoi();
            ip.setValue(0);
            ip.fill();
        } else enc.setTitle("CAC_enclosing");
        Calibration c2 = enc.getCalibration().copy();
        c2.pixelWidth = pw1;
        c2.pixelHeight = ph1;
        c2.pixelDepth = 1;
        c2.setUnit(pu);
        enc.setCalibration(c2);
        return enc;
    }

    /** Long axis of each parasite: its ends (pixels), its length (um) and area (um2). */
    void parasiteAxes(int nPar, ImagePlus imgT) {
        aX1 = new double[nPar];
        aY1 = new double[nPar];
        aX2 = new double[nPar];
        aY2 = new double[nPar];
        aLen = new double[nPar];
        aArea = new double[nPar];
        double pw0 = imgT.getCalibration().pixelWidth;
        for (int p = 0; p < nPar; p++) {
            Roi r = select(imgT, rm.get(p));
            double lp = feretEnds(imgT, r);
            aX1[p] = fe1x;
            aY1[p] = fe1y;
            aX2[p] = fe2x;
            aY2[p] = fe2y;
            aLen[p] = lp * pw0;
            aArea[p] = area(imgT);
        }
        imgT.deleteRoi();
    }

    /** Ends of the long axis (Feret diameter) of the selection, in pixels. Returns its length in pixels. */
    double feretEnds(ImagePlus imp, Roi roi) {
        double[] fv = roi.getFeretValues();
        fe1x = fv[3];
        fe1y = fv[4];
        FloatPolygon fp = roi.getFloatPolygon();
        double bd = -1;
        fe2x = fe1x;
        fe2y = fe1y;
        for (int q = 0; q < fp.npoints; q++) {
            double dx = (double) fp.xpoints[q] - fe1x;
            double dy = (double) fp.ypoints[q] - fe1y;
            double dd = dx * dx + dy * dy;
            if (dd > bd) {
                bd = dd;
                fe2x = fp.xpoints[q];
                fe2y = fp.ypoints[q];
            }
        }
        return Math.sqrt(bd);
    }

    double placeOn(int p, double x, double y) {
        double vx = aX2[p] - aX1[p];
        double vy = aY2[p] - aY1[p];
        double l2 = vx * vx + vy * vy;
        if (l2 <= 0) return 0.5;
        return ((x - aX1[p]) * vx + (y - aY1[p]) * vy) / l2;
    }

    boolean placeCounted(double t) {
        double d = Math.abs(t - 0.5) * 2;
        if (S.posMode == 1) return d <= S.centrePct / 100;
        if (S.posMode == 2) return d >= 1 - S.endPct / 100;
        return true;
    }

    int poleAt(int p, double t) {
        double d = Math.abs(t - 0.5) * 2;
        if (d < 1 - S.endPct / 100) return 4;
        if (pBas[p] == 0) return 3;
        int endN = 1;
        if (t > 0.5) endN = 2;
        if (endN == pBas[p]) return 1;
        return 2;
    }

    /** For each point, the parasite holding it (or within tolPx pixels of it); -1 when none. */
    int[] ownerOf(double[] xs, double[] ys, int nPar, double tolPx, ImagePlus imgT) {
        int n = xs.length;
        int[] own = new int[n];
        Arrays.fill(own, -1);
        if (n == 0 || nPar == 0) return own;
        imgT.deleteRoi();
        long tl = Math.round(tolPx);
        for (int pass = 0; pass < 2; pass++) {
            if (pass == 1 && tl < 1) continue;
            for (int p = 0; p < nPar; p++) {
                Roi roi = select(imgT, rm.get(p));
                if (pass == 1) roi = enlarge(imgT, (int) tl);
                Rectangle b = bounds(imgT, roi);
                for (int k = 0; k < n; k++) {
                    if (own[k] >= 0) continue;
                    if (xs[k] < b.x || ys[k] < b.y || xs[k] >= b.x + b.width || ys[k] >= b.y + b.height) continue;
                    if (roi != null && roi.contains((int) Math.floor(xs[k]), (int) Math.floor(ys[k]))) own[k] = p;
                }
            }
        }
        imgT.deleteRoi();
        return own;
    }

    /** Marker objects inside the parasites (dblMode 1). Returns the double-labelled count. */
    double objectsInParasites(ImagePlus maskT, int nPar, ImagePlus imgT) {
        int nO = findObjects(maskT, S.objMinArea, S.objMaxArea, S.objWatershed);
        double pw0 = maskT.getCalibration().pixelWidth;
        oX = fX.clone();
        oY = fY.clone();
        oR = new double[nO];
        oT = new double[nO];
        oUse = new int[nO];
        oPole = new int[nO];
        for (int k = 0; k < nO; k++) oR[k] = Math.sqrt(fA[k] / Math.PI) / pw0;
        oOwn = ownerOf(oX, oY, nPar, S.objTol / pw0, imgT);
        objN = nO;
        boolean useW = parWeight.length == nPar;
        for (int k = 0; k < nO; k++) {
            int p = oOwn[k];
            oT[k] = -1;
            if (p < 0) continue;
            objOn = objOn + 1;
            pObj[p] = pObj[p] + 1;
            double t = placeOn(p, oX[k], oY[k]);
            oT[k] = t;
            if (placeCounted(t)) {
                oUse[k] = 1;
                pCnt[p] = pCnt[p] + 1;
                objUsed = objUsed + 1;
            }
        }
        List<Integer> idx = new ArrayList<Integer>();
        double nDbl = 0;
        for (int p = 0; p < nPar; p++) {
            if (pCnt[p] < S.objMinN || pCnt[p] == 0) continue;
            double w = 1;
            if (useW) w = parWeight[p];
            idx.add(p);
            nDbl = nDbl + w;
        }
        dblIdx = toInt(idx);
        return nDbl;
    }

    /** Nuclei of the parasites in the nuclear stain: objects of pnMinArea to pnMaxArea um2. */
    int parasiteNuclei(ImagePlus srcT) {
        ImagePlus t = prepChannel(srcT, "CAC_pnuclei", S.nucleiMultiply, S.nucleiBgRolling, S.nucleiBlurSigma);
        double lo = autoLow(t, S.nucleiThresholdMethod, S.nucleiCapF, S.nucleiBlurSigma);
        setThreshold(t, lo * S.nucleiThresholdFactor, 1e30);
        convertToMask(t);
        eightBitMask(t);
        fillHoles(t);
        return findObjects(t, S.pnMinArea, S.pnMaxArea, false);
    }

    /** Objects of the pole marker. */
    int poleMarkerObjects(ImagePlus srcT) {
        ImagePlus t = prepChannel(srcT, "CAC_polemarker", S.pmMultiply, S.pmBgRolling, S.pmBlurSigma);
        double lo = autoLow(t, S.pmThresholdMethod, S.pmCapF, S.pmBlurSigma);
        double hi = autoHi;
        setThreshold(t, lo * S.pmThresholdFactor, hi);
        convertToMask(t);
        eightBitMask(t);
        fillHoles(t);
        return findObjects(t, S.pmMinArea, S.pmMaxArea, false);
    }

    /** Basal end of each parasite, from its nucleus and / or the pole marker. Needs parasiteAxes. */
    double orientParasites(int nPar, ImagePlus imgT, ImagePlus blueT, ImagePlus poleT) {
        pBas = new int[nPar];
        pWhy = new String[nPar];
        Arrays.fill(pWhy, "0");
        pNuc = new double[nPar];
        pMk = new double[nPar];
        double dead = S.poleDeadPct / 100;
        int[] rN = new int[nPar], rM = new int[nPar];
        String[] sN = new String[nPar], sM = new String[nPar];
        Arrays.fill(sN, "0");
        Arrays.fill(sM, "0");
        double pw0 = imgT.getCalibration().pixelWidth;
        if (S.poleNeedNuc) {
            if (blueT != null) {
                int nn = parasiteNuclei(blueT);
                double[] nX = fX.clone(), nY = fY.clone();
                int[] ownN = ownerOf(nX, nY, nPar, 0, imgT);
                double[] tN = new double[nPar];
                for (int k = 0; k < nn; k++) {
                    int p = ownN[k];
                    if (p < 0) continue;
                    pNuc[p] = pNuc[p] + 1;
                    tN[p] = placeOn(p, nX[k], nY[k]);
                }
                for (int p = 0; p < nPar; p++) {
                    sN[p] = "";
                    if (pNuc[p] == 0) sN[p] = "no nucleus";
                    else if (pNuc[p] > 1) sN[p] = "several nuclei";
                    else if (Math.abs(tN[p] - 0.5) < dead) sN[p] = "nucleus at the centre";
                    else {
                        rN[p] = 1;
                        if (tN[p] > 0.5) rN[p] = 2;
                    }
                }
            } else {
                for (int p = 0; p < nPar; p++) sN[p] = "no nuclear stain";
            }
        }
        if (S.poleNeedMk) {
            if (poleT != null) {
                int nm = poleMarkerObjects(poleT);
                double[] mX = fX.clone(), mY = fY.clone();
                int[] ownM = ownerOf(mX, mY, nPar, S.objTol / pw0, imgT);
                double[] e1 = new double[nPar], e2 = new double[nPar];
                for (int k = 0; k < nm; k++) {
                    int p = ownM[k];
                    if (p < 0) continue;
                    pMk[p] = pMk[p] + 1;
                    double t = placeOn(p, mX[k], mY[k]);
                    if (t < 0.5 - dead) e1[p] = e1[p] + 1;
                    if (t > 0.5 + dead) e2[p] = e2[p] + 1;
                }
                for (int p = 0; p < nPar; p++) {
                    sM[p] = "";
                    int mEnd = 0;
                    if (pMk[p] == 0) sM[p] = "no pole marker";
                    else if (e1[p] > 0 && e2[p] > 0) sM[p] = "pole marker at both ends";
                    else if (e1[p] == 0 && e2[p] == 0) sM[p] = "pole marker at the centre";
                    else if (e1[p] > 0) mEnd = 1;
                    else mEnd = 2;
                    if (mEnd > 0) {
                        rM[p] = mEnd;
                        if (S.poleSide == 1) rM[p] = 3 - mEnd;
                    }
                }
            } else {
                for (int p = 0; p < nPar; p++) sM[p] = "no pole marker channel";
            }
        }
        for (int p = 0; p < nPar; p++) {
            pBas[p] = 0;
            pWhy[p] = "";
            if (S.poleNeedNuc && S.poleNeedMk) {
                if (rN[p] > 0 && rM[p] > 0) {
                    if (rN[p] == rM[p]) pBas[p] = rN[p];
                    else pWhy[p] = "nucleus and pole marker disagree";
                } else if (rN[p] > 0) {
                    pBas[p] = rN[p];
                } else if (rM[p] > 0) {
                    pBas[p] = rM[p];
                } else {
                    pWhy[p] = sN[p] + " / " + sM[p];
                }
            } else if (S.poleNeedNuc) {
                pBas[p] = rN[p];
                pWhy[p] = sN[p];
            } else {
                pBas[p] = rM[p];
                pWhy[p] = sM[p];
            }
            if (pBas[p] > 0) poleOri = poleOri + 1;
        }
        List<String> whyL = new ArrayList<String>();
        List<Integer> whyC = new ArrayList<Integer>();
        for (int p = 0; p < nPar; p++) {
            if (pBas[p] > 0) continue;
            int f = whyL.indexOf(pWhy[p]);
            if (f < 0) {
                whyL.add(pWhy[p]);
                whyC.add(1);
            } else whyC.set(f, whyC.get(f) + 1);
        }
        StringBuilder pn = new StringBuilder();
        for (int q = 0; q < whyL.size(); q++) {
            if (q > 0) pn.append(", ");
            pn.append(whyL.get(q)).append(' ').append(whyC.get(q));
        }
        poleNote = pn.toString();
        imgT.deleteRoi();
        return poleOri;
    }

    /** Pole of the marker on each double-labelled parasite; weighted counts in poleN[1..4]. */
    void classifyPoles(ImagePlus maskT, int nPar, ImagePlus imgT) {
        boolean useW = parWeight.length == nPar;
        Calibration cal = maskT.getCalibration();
        double pw0 = cal.pixelWidth, ph0 = cal.pixelHeight;
        for (int z = 0; z < dblIdx.length; z++) {
            int p = dblIdx[z];
            double w = 1;
            if (useW) w = parWeight[p];
            int cls = 4;
            if (S.dblMode == 1) {
                double nB = 0, nA = 0, nI = 0, sT = 0, nT = 0;
                for (int k = 0; k < oX.length; k++) {
                    if (oOwn[k] != p || oUse[k] != 1) continue;
                    int c = poleAt(p, oT[k]);
                    oPole[k] = c;
                    if (c == 1) nB = nB + 1;
                    if (c == 2) nA = nA + 1;
                    if (c == 3) nI = nI + 1;
                    sT = sT + oT[k];
                    nT = nT + 1;
                }
                if (nT > 0) pPlace[p] = sT / nT;
                if (pBas[p] == 0) {
                    if (nI > 0) cls = 3;
                } else {
                    if (nB > nA) cls = 1;
                    else if (nA > nB) cls = 2;
                    else if (nA > 0) cls = 3;
                }
            } else {
                select(maskT, rm.get(p));
                ImageStatistics st = measure(maskT);
                double xm = st.xCenterOfMass / pw0;
                double ym = st.yCenterOfMass / ph0;
                if (Double.isNaN(xm) || Double.isNaN(ym)) {
                    cls = 4;
                } else {
                    double t = placeOn(p, xm, ym);
                    pPlace[p] = t;
                    cls = poleAt(p, t);
                }
            }
            pCls[p] = cls;
            poleN[cls] = poleN[cls] + w;
        }
        maskT.deleteRoi();
    }

    // ================================================================== watershed with a tolerance
    /** Watershed of a binary mask, in place, with a tolerance (px). Returns true when the tolerance applied. */
    boolean watershedMask(ImagePlus mask, double tol) {
        mask.deleteRoi();
        if (tol <= 0.5) {
            watershed(mask);
            return false;
        }
        if (tol > 100) tol = 100;
        String edmT = mask.getTitle() + "_edm";
        ImagePlus edm = dup(mask, edmT);
        // with the default EDM output ("Overwrite") the copy itself becomes the map, the other settings of
        // Process > Binary > Options put it in a new image, renamed as the copy
        edm = distanceMap(edm);
        edm.setTitle(edmT);
        ImageStatistics st = raw(edm);
        if (st.max <= 0) return false;
        double lift = Math.floor(tol) + 1;
        edm.getProcessor().add(Macro.asOption(lift));
        changeValues(edm, lift, lift, 0);
        st = raw(edm);
        setThreshold(edm, lift + 1, st.max);
        boolean light = edm.isInvertedLut();
        ByteProcessor seg = segmented(edm, Macro.asOption(tol), light);
        if (seg == null) {
            log.add("   watershed tolerance could not be applied: standard watershed used");
            watershed(mask);
            return false;
        }
        mask.getProcessor().copyBits(seg, 0, 0, Blitter.AND);
        return true;
    }

    /** run("Distance Map") with the EDM output of Process > Binary > Options; returns the map. */
    static ImagePlus distanceMap(ImagePlus imp) {
        ImageProcessor ip = imp.getProcessor();
        ip.resetRoi();
        boolean invertedLut = imp.isInvertedLut();
        boolean background255 = (invertedLut && Prefs.blackBackground) || (!invertedLut && !Prefs.blackBackground);
        int backgroundValue = background255 ? (byte) 255 : 0;
        EDM e = new EDM();
        FloatProcessor floatEdm = e.makeFloatEDM(ip, backgroundValue, false);
        int out = EDM.getOutputType();
        String t = "EDM of " + imp.getShortTitle();
        ImageProcessor outIp;
        switch (out) {
            case EDM.FLOAT:
                outIp = floatEdm;
                break;
            case EDM.SHORT:
                floatEdm.setMinAndMax(0., 65535.);
                outIp = floatEdm.convertToShort(true);
                break;
            case EDM.BYTE:
                floatEdm.setMinAndMax(0., 255.);
                outIp = floatEdm.convertToByte(true);
                break;
            default:
                ip.setPixels(0, floatEdm);
                if (floatEdm.getMax() > 255.) ip.resetMinAndMax();
                ip.resetBinaryThreshold();
                return imp;
        }
        ImagePlus outImp = new ImagePlus(t, outIp);
        if (!Prefs.blackBackground) outIp.invertLut();
        outIp.resetMinAndMax();
        return outImp;
    }

    /** Find Maxima "noise=tol output=[Segmented Particles] above [light]": the segmented image, or null. */
    static ByteProcessor segmented(ImagePlus imp, double tol, boolean light) {
        ImageProcessor ip = imp.getProcessor();
        ip.resetBinaryThreshold();
        boolean thresholded = ip.isThreshold();
        boolean invertedLut = imp.isInvertedLut();
        double threshold = thresholded ? ip.getMinThreshold() : ImageProcessor.NO_THRESHOLD;
        ImageProcessor ipF = ip;
        if ((invertedLut && !light) || (!invertedLut && light)) {
            threshold = ImageProcessor.NO_THRESHOLD;
            ipF = ip.duplicate();
            ipF.invert();
        }
        imp.deleteRoi();
        MaximumFinder mf = new MaximumFinder();
        mf.setup("", imp);
        boolean isEDM = imp.getBitDepth() == 32 && imp.getTitle().startsWith("EDM of ");
        ByteProcessor out = mf.findMaxima(ipF, tol, false, threshold, MaximumFinder.SEGMENTED, false, isEDM);
        if (out == null) return null;
        if (!Prefs.blackBackground) out.invertLut();
        return out;
    }

    // ================================================================== nuclei at the image border
    /**
     * Leaves out the nuclei cut by the image border (mode 1 touching, 2 more than half outside). On entry rm
     * holds the nuclei of the filters; on return, the nuclei counted, in scan order. Those left out are put in
     * edgeOut. Returns how many were left out.
     */
    int removeBorderNuclei(ImagePlus mask, int mode, List<Roi> edgeOut) {
        mask.deleteRoi();
        int iw = mask.getWidth(), ih = mask.getHeight();
        Calibration bcal = mask.getCalibration();
        double floorA = S.nucleiMinArea / 4;
        ImagePlus eAll = apMask(mask, "size=" + Macro.n2s(floorA) + "-Infinity", "CAC_e_all");
        ImagePlus eIn = apMask(mask, "size=" + Macro.n2s(floorA) + "-Infinity exclude", "CAC_e_in");
        ImagePlus edge = dup(eAll, "CAC_edge");
        edge.getProcessor().copyBits(eIn.getProcessor(), 0, 0, Blitter.SUBTRACT);
        int nR = rm.size();
        edge.deleteRoi();
        setThreshold(edge, 255, 255);
        List<Roi> eR = particles(edge, 0, "Infinity", null, false);
        resetThreshold(edge);
        rm.addAll(eR);
        int nE = eR.size();
        if (nE <= 0) {
            return 0;
        }
        int[] out = new int[nE];
        int nOut = 0;
        ImagePlus test = newByte("Border_test", iw, ih);
        ImageProcessor tp = test.getProcessor();
        for (int k = 0; k < nE; k++) {
            Roi roi = select(test, rm.get(nR + k));
            Rectangle rb = bounds(test, roi);
            int bx = rb.x, by = rb.y, bw = rb.width, bh = rb.height;
            int o = 1;
            if (mode == 2) {
                tp.setValue(255);
                tp.fill(roi);
                o = 0;
                if (bx <= 0 && centreOutside(test, bx, by, bw, bh, "left", iw, ih)) o = 1;
                if (bx + bw >= iw && centreOutside(test, bx, by, bw, bh, "right", iw, ih)) o = 1;
                if (by <= 0 && centreOutside(test, bx, by, bw, bh, "top", iw, ih)) o = 1;
                if (by + bh >= ih && centreOutside(test, bx, by, bw, bh, "bottom", iw, ih)) o = 1;
                test.deleteRoi();
                tp.resetRoi();
                tp.setValue(0);
                tp.setRoi(bx, by, bw, bh);
                tp.fill();
                tp.resetRoi();
            }
            out[k] = o;
            nOut = nOut + o;
        }
        ImagePlus cnt = apMask(mask, "size=" + Macro.n2s(S.nucleiMinArea) + "-" + S.nucleiMaxArea + " circularity="
                + S.nucleiCirc, "CAC_cnt");
        cnt.getProcessor().copyBits(edge.getProcessor(), 0, 0, Blitter.SUBTRACT);
        if (nOut < nE) {
            ImagePlus inb = newByte("CAC_inb", iw, ih);
            Calibration ic = inb.getCalibration().copy();
            ic.pixelWidth = bcal.pixelWidth;
            ic.pixelHeight = bcal.pixelHeight;
            ic.pixelDepth = 1;
            ic.setUnit(bcal.getUnits());
            inb.setCalibration(ic);
            for (int k = 0; k < nE; k++) {
                if (out[k] == 1) continue;
                Roi roi = select(inb, rm.get(nR + k));
                inb.getProcessor().setValue(255);
                inb.getProcessor().fill(roi);
            }
            inb.deleteRoi();
            ImagePlus inok = apMask(inb, "size=" + Macro.n2s(S.nucleiMinArea / 2) + "-" + S.nucleiMaxArea
                    + " circularity=" + S.nucleiCirc, "CAC_inok");
            cnt.getProcessor().copyBits(inok.getProcessor(), 0, 0, Blitter.MAX);
        }
        if (nOut > 0) {
            for (int k = 0; k < nE; k++) if (out[k] == 1) edgeOut.add(rm.get(nR + k));
        }
        rm.clear();
        cnt.deleteRoi();
        setThreshold(cnt, 255, 255);
        rm.addAll(particles(cnt, 0, "Infinity", null, false));
        resetThreshold(cnt);
        mask.deleteRoi();
        return nOut;
    }

    /** Analyze Particles on the mask src (objects 255): the particles kept by opts, as a new mask. */
    static ImagePlus apMask(ImagePlus src, String opts, String dstTitle) {
        src.deleteRoi();
        int sw = src.getWidth(), sh = src.getHeight();
        Calibration scal = src.getCalibration();
        setThreshold(src, 255, 255);
        String size = value(opts, "size");
        String circ = value(opts, "circularity");
        boolean exclude = (" " + opts + " ").contains(" exclude ");
        Particles.Result r = Particles.analyze(src, size, circ, exclude, true);
        resetThreshold(src);
        ImagePlus t = r.mask;
        if (t == null) {
            ImagePlus e = newByte(dstTitle, sw, sh);
            Calibration c = e.getCalibration().copy();
            c.pixelWidth = scal.pixelWidth;
            c.pixelHeight = scal.pixelHeight;
            c.pixelDepth = 1;
            c.setUnit(scal.getUnits());
            e.setCalibration(c);
            return e;
        }
        t.setTitle(dstTitle);
        return t;
    }

    private static String value(String opts, String key) {
        for (String p : opts.split(" ")) if (p.startsWith(key + "=")) return p.substring(key.length() + 1);
        return null;
    }

    /** The particles neither counted nor left out at the border, as two masks: rejected by size, by shape.
     *  The objects counted are rm 0 to nC-1. */
    ImagePlus[] rejectMasks(ImagePlus mask, int nC, List<Roi> edgeOut, double minA, double maxA) {
        mask.deleteRoi();
        ImagePlus rem = dup(mask, "CAC_rem");
        ImageProcessor ip = rem.getProcessor();
        for (int k = 0; k < nC; k++) {
            Roi r = select(rem, rm.get(k));
            ip.setValue(0);
            ip.fill(r);
        }
        rem.deleteRoi();
        if (edgeOut != null && !edgeOut.isEmpty()) {
            for (Roi e : edgeOut) {
                Roi r = select(rem, e);
                ip.setValue(0);
                ip.fill(r);
            }
            rem.deleteRoi();
        }
        double lo = S.nearFrac * minA;
        ImagePlus outS = apMask(rem, "size=" + Macro.n2s(lo) + "-Infinity", "RejS");
        ImagePlus outC = apMask(rem, "size=" + Macro.n2s(minA) + "-" + (maxA == Double.POSITIVE_INFINITY ? "Infinity"
                : Macro.n2s(maxA)), "RejC");
        outS.getProcessor().copyBits(outC.getProcessor(), 0, 0, Blitter.SUBTRACT);
        return new ImagePlus[]{outS, outC};
    }

    /** True if the centre of the nucleus drawn on the test image lies beyond the given border. */
    static boolean centreOutside(ImagePlus test, int bx, int by, int bw, int bh, String side, int iw, int ih) {
        ImageProcessor tp = test.getProcessor();
        int nd = bw;
        if (side.equals("top") || side.equals("bottom")) nd = bh;
        if (nd < 3) return true;
        double[] dep = new double[nd], w2 = new double[nd];
        int nOk = 0;
        for (int q = 0; q < nd; q++) {
            double wq;
            if (side.equals("left") || side.equals("right")) {
                int xq = bx + q;
                if (side.equals("right")) xq = bx + bw - 1 - q;
                if (tp.getPixel(xq, 0) > 0 || tp.getPixel(xq, ih - 1) > 0) continue;
                test.setRoi(new Roi(xq, by, 1, bh));
                double mn = statsOf(test, AREA + MEAN + MIN_MAX, true).mean;
                wq = mn * bh / 255;
            } else {
                int yq = by + q;
                if (side.equals("bottom")) yq = by + bh - 1 - q;
                if (tp.getPixel(0, yq) > 0 || tp.getPixel(iw - 1, yq) > 0) continue;
                test.setRoi(new Roi(bx, yq, bw, 1));
                double mn = statsOf(test, AREA + MEAN + MIN_MAX, true).mean;
                wq = mn * bw / 255;
            }
            dep[nOk] = q + 0.5;
            w2[nOk] = wq * wq;
            nOk = nOk + 1;
        }
        test.deleteRoi();
        if (nOk < 3) return true;
        nd = nOk;
        double mu = 0;
        for (int q = 0; q < nd; q++) mu = mu + dep[q];
        mu = mu / nd;
        double s2 = 0, s3 = 0, s4 = 0, sy = 0, suy = 0, su2y = 0;
        for (int q = 0; q < nd; q++) {
            double u = dep[q] - mu;
            s2 = s2 + u * u;
            s3 = s3 + u * u * u;
            s4 = s4 + u * u * u * u;
            sy = sy + w2[q];
            suy = suy + u * w2[q];
            su2y = su2y + u * u * w2[q];
        }
        double det = nd * (s2 * s4 - s3 * s3) - s2 * s2 * s2;
        if (det == 0) return true;
        double pb = (nd * (suy * s4 - s3 * su2y) + sy * s3 * s2 - s2 * s2 * suy) / det;
        double pc = (nd * (s2 * su2y - suy * s3) - sy * s2 * s2) / det;
        if (pc < 0) {
            double centre = mu - pb / (2 * pc);
            return centre < 0;
        }
        double slope = pb + 2 * pc * (0.5 - mu);
        return slope <= 0;
    }

    // ================================================================== saturation
    /** Saturation of a raw channel, in words, "" when there is none to speak of. */
    static String satCheck(ImagePlus t) {
        t.deleteRoi();
        int bd = t.getBitDepth();
        if (bd != 8 && bd != 16) return "";
        ImageStatistics st = raw(t);
        double maxV = st.max, minV = st.min, nPx = st.pixelCount;
        if (maxV <= minV) return "";
        double nS = countFrom(t, maxV);
        double pc = 100.0 * nS / nPx;
        boolean top = maxV == 255 || maxV == 4095 || maxV == 16383 || maxV == 65535;
        if (pc < 1 && (!top || pc < 0.05)) return "";
        return IJ.d2s(pc, 2) + " % of the pixels saturated (at " + Macro.n2s(maxV) + "): lower the exposure if you can";
    }

    /** Smallest marker-positive area drawn (um2), Calibrate and QC stacks. */
    double mkSpeck() {
        if (S.dblMode == 1) return S.objMinArea;
        if (S.dblMode == 2) return S.encMinArea * S.nearFrac;
        return S.greenMinArea * S.nearFrac;
    }

    static int[] toInt(List<Integer> l) {
        int[] a = new int[l.size()];
        for (int i = 0; i < a.length; i++) a[i] = l.get(i);
        return a;
    }
}
