package toxo.cac;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.measure.Measurements;
import ij.plugin.filter.ThresholdToSelection;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;

import java.awt.Color;
import java.awt.Font;
import java.awt.Rectangle;
import java.util.List;

/**
 * The QC display and the drawing on the QC panels, as the macro functions of the same names do them (qcCopy,
 * qcRange, drawROIs, labelROIs, drawTop...), each on the image given. The Calibrate preview uses them too, so that
 * the preview and the QC stacks show the very same view.
 */
final class QC {
    private QC() {}

    // ================================================================== display
    /** Display copy of a channel: duplicated, in the colour lut ("" keeps its own), with the range qcRange gives. */
    static ImagePlus qcCopy(ImagePlus src, String newTitle, double gain, String lut) {
        ImagePlus d = Detect.dup(src, newTitle);
        if (lut != null && !lut.isEmpty()) Cmd.applyLut(d, lut);
        qcRange(d, gain);
        return d;
    }

    /**
     * Display range adapted to the image: from its background (the most frequent value) to the brightness of its
     * objects (the value 99.5 % of the pixels above its Triangle threshold stay under), then brightened by gain.
     * Without such objects, ImageJ's automatic contrast (0.35 % saturated).
     */
    static void qcRange(ImagePlus imp, double gain) {
        Cmd.resetMinAndMax(imp);
        int bd = imp.getBitDepth();
        if (bd == 24) return;
        double mn = imp.getDisplayRangeMin(), mx = imp.getDisplayRangeMax();
        if (mx <= mn) return;
        double h0 = 0, hs = 1;
        int qa = (int) Math.round(mn), qb = (int) Math.round(mx);
        int[] hc;
        imp.deleteRoi();
        if (bd == 8) hc = Detect.hist(imp, 256).hc;
        else if (bd == 16) hc = Detect.hist(imp, 65536).hc;
        else {
            hc = Detect.hist(imp, 4096).hc;
            h0 = mn;
            hs = (mx - mn) / 4096;
            qa = 0;
            qb = 4095;
        }
        if (qa < 0) qa = 0;
        if (qb > hc.length - 1) qb = hc.length - 1;
        int iMode = qa;
        for (int q = qa + 1; q <= qb; q++) if (hc[q] > hc[iMode]) iMode = q;
        double lo = h0 + iMode * hs;
        Detect.setAutoThreshold(imp, "Triangle dark");
        double tLo = imp.getProcessor().getMinThreshold();
        if (tLo == ImageProcessor.NO_THRESHOLD) tLo = -1;
        else tLo = imp.getCalibration().getCValue(tLo);
        Detect.resetThreshold(imp);
        int iT = (int) Math.floor((tLo - h0) / hs);
        if (iT <= iMode) iT = iMode + 1;
        double hi = lo;
        if (iT <= qb) {
            double nObj = 0;
            for (int q = iT; q <= qb; q++) nObj += hc[q];
            if (nObj > 0) {
                double want = 0.995 * nObj;
                double acc = 0;
                int iHi = qb;
                for (int q = iT; q <= qb; q++) {
                    acc += hc[q];
                    if (acc >= want) {
                        iHi = q;
                        break;
                    }
                }
                hi = h0 + (iHi + 1) * hs;
            }
        }
        if (hi <= lo) {
            Cmd.enhanceContrast(imp, 0.35);
            lo = imp.getDisplayRangeMin();
            hi = imp.getDisplayRangeMax();
            if (hi <= lo) return;
        }
        if (gain > 0 && gain != 1) hi = lo + (hi - lo) / gain;
        Cmd.setMinAndMax(imp, lo, hi);
    }

    // ================================================================== drawing on an RGB panel
    static Color hex(String h) {
        return new Color(Integer.parseInt(h.substring(0, 2), 16), Integer.parseInt(h.substring(2, 4), 16),
                Integer.parseInt(h.substring(4, 6), 16));
    }

    /** The outlines of the ROIs idx (indices into rois) drawn on the panel, line width lw. */
    static void drawROIs(ImagePlus panel, List<Roi> rois, int[] idx, Color c, int lw) {
        if (idx.length == 0) return;
        ImageProcessor ip = panel.getProcessor();
        for (int i : idx) {
            Roi r = (Roi) rois.get(i).clone();
            r.setImage(panel);
            ip.resetRoi();
            ip.setLineWidth(lw);
            ip.setColor(c);
            r.drawPixels(ip);
        }
        ip.setLineWidth(1);
        panel.deleteRoi();
    }

    static void drawROIs(ImagePlus panel, List<Roi> rois, int[] idx, String hexColour, int lw) {
        drawROIs(panel, rois, idx, hex(hexColour), lw);
    }

    /** Antialiased text: on a Mac, or from 14 points (setFont of the macro language). */
    static boolean antialias(int size) {
        return IJ.isMacOSX() || size >= 14;
    }

    private static void text(ImageProcessor ip, String s, double x, double y) {
        ip.drawString(s, (int) (x + 0.5), (int) (y + 0.5));
    }

    private static void fontOn(ImageProcessor ip, int size) {
        ip.setFont(new Font("SansSerif", Font.BOLD, size));
        ip.setJustification(ImageProcessor.LEFT_JUSTIFY);
        ip.setAntialiasedText(antialias(size));
    }

    /** Numbers 1..N next to each ROI idx (labelROIs), or numOf[index] when given (labelROIsNum). */
    static void labelROIs(ImagePlus panel, List<Roi> rois, int[] idx, int[] numOf, Color c, int fontSize, int dy) {
        int nLab = idx.length;
        if (nLab == 0) return;
        int imgW = panel.getWidth(), imgH = panel.getHeight();
        double dx = Math.max(4, Math.round(fontSize / 4.0));
        double[] lx = new double[nLab], ly = new double[nLab];
        for (int q = 0; q < nLab; q++) {
            Rectangle b = rois.get(idx[q]).getBounds();
            double px = b.x + b.width + dx;
            double py = b.y + b.height / 2.0 + dy;
            if (px > imgW - fontSize * 1.6) px = b.x - Math.round(fontSize * 1.3);
            px = Math.min(Math.max(px, 2), imgW - Math.round(fontSize * 1.5));
            py = Math.min(Math.max(py, fontSize + 2), imgH - 2);
            lx[q] = px;
            ly[q] = py;
        }
        panel.deleteRoi();
        ImageProcessor ip = panel.getProcessor();
        ip.resetRoi();
        fontOn(ip, fontSize);
        double halo = Math.max(1, Math.round(fontSize / 12.0));
        ip.setColor(new Color(0, 0, 0));
        for (int q = 0; q < nLab; q++) {
            String t = numOf == null ? "" + (q + 1) : "" + numOf[idx[q]];
            text(ip, t, lx[q] - halo, ly[q]);
            text(ip, t, lx[q] + halo, ly[q]);
            text(ip, t, lx[q], ly[q] - halo);
            text(ip, t, lx[q], ly[q] + halo);
        }
        ip.setColor(c);
        for (int q = 0; q < nLab; q++) {
            String t = numOf == null ? "" + (q + 1) : "" + numOf[idx[q]];
            text(ip, t, lx[q], ly[q]);
        }
    }

    static void labelROIs(ImagePlus panel, List<Roi> rois, int[] idx, String hexColour, int fontSize, int dy) {
        labelROIs(panel, rois, idx, null, hex(hexColour), fontSize, dy);
    }

    /** Circles round the marker objects whose class in cl is cls (drawObjects). */
    static void drawObjects(ImagePlus panel, double[] oX, double[] oY, double[] oR, int[] cl, int cls, Color c, int lw) {
        ImageProcessor ip = panel.getProcessor();
        ip.resetRoi();
        ip.setColor(c);
        ip.setLineWidth(lw);
        for (int q = 0; q < oX.length; q++) {
            if (cl[q] != cls) continue;
            double r = oR[q] + lw + 1;
            ip.drawOval((int) (oX[q] - r), (int) (oY[q] - r), (int) (2 * r), (int) (2 * r));
        }
        ip.setLineWidth(1);
    }

    /** A dot at the basal end of each oriented parasite (drawBasalEnds). */
    static void drawBasalEnds(ImagePlus panel, Detect d, int nPar, int rad, Color c) {
        ImageProcessor ip = panel.getProcessor();
        ip.resetRoi();
        ip.setColor(c);
        for (int p = 0; p < nPar; p++) {
            if (d.pBas[p] == 1) ip.fillOval((int) (d.aX1[p] - rad), (int) (d.aY1[p] - rad), 2 * rad, 2 * rad);
            if (d.pBas[p] == 2) ip.fillOval((int) (d.aX2[p] - rad), (int) (d.aY2[p] - rad), 2 * rad, 2 * rad);
        }
    }

    /** The outline of the objects of the mask (objects 255) drawn on the panel (drawMaskOutline). */
    static boolean drawMask(ImagePlus mask, ImagePlus panel, String hexColour, int lw) {
        if (mask == null) return false;
        mask.deleteRoi();
        Detect.setThreshold(mask, 255, 255);
        Roi sel = ThresholdToSelection.run(mask);
        Detect.resetThreshold(mask);
        mask.deleteRoi();
        if (sel == null) return false;
        ImageProcessor ip = panel.getProcessor();
        ip.resetRoi();
        ip.setLineWidth(lw);
        ip.setColor(hex(hexColour));
        sel.setImage(panel);
        sel.drawPixels(ip);
        ip.setLineWidth(1);
        panel.deleteRoi();
        return true;
    }

    /** A line of text at the top of the panel, line ln (1 = top), with a black halo (drawTop). */
    static void drawTop(ImagePlus panel, String txt, int ln, String hexColour, int fs) {
        if (txt == null || txt.isEmpty()) return;
        panel.deleteRoi();
        ImageProcessor ip = panel.getProcessor();
        ip.resetRoi();
        fontOn(ip, fs);
        double halo = Math.max(1, Math.round(fs / 14.0));
        double px = 6;
        double py = 4 + fs + (ln - 1) * Math.round(fs * 1.6);
        ip.setColor(new Color(0, 0, 0));
        text(ip, txt, px - halo, py);
        text(ip, txt, px + halo, py);
        text(ip, txt, px, py - halo);
        text(ip, txt, px, py + halo);
        ip.setColor(hex(hexColour));
        text(ip, txt, px, py);
    }

    /** The counts at the bottom of the panel (drawRecap). */
    static void drawRecap(ImagePlus panel, String txt, Color c, int fs) {
        bottomLine(panel, txt, c, fs, 0);
    }

    static void drawRecap(ImagePlus panel, String txt, String hexColour, int fs) {
        bottomLine(panel, txt, hex(hexColour), fs, 0);
    }

    /** A warning line just above the recap (drawNote). */
    static void drawNote(ImagePlus panel, String txt, Color c, int fs) {
        bottomLine(panel, txt, c, fs, Math.round(fs * 1.6));
    }

    private static void bottomLine(ImagePlus panel, String txt, Color c, int fs, double up) {
        panel.deleteRoi();
        ImageProcessor ip = panel.getProcessor();
        ip.resetRoi();
        fontOn(ip, fs);
        double halo = Math.max(1, Math.round(fs / 14.0));
        double px = 20;
        double py = panel.getHeight() - Math.round(fs * 0.7) - up;
        ip.setColor(new Color(0, 0, 0));
        text(ip, txt, px - halo, py);
        text(ip, txt, px + halo, py);
        text(ip, txt, px, py - halo);
        text(ip, txt, px, py + halo);
        ip.setColor(c);
        text(ip, txt, px, py);
    }

    static int[] seq(int n) {
        int[] a = new int[n];
        for (int i = 0; i < n; i++) a[i] = i;
        return a;
    }

    @SuppressWarnings("unused")
    private static double mean(ImagePlus imp) {
        ImageStatistics st = imp.getStatistics(Measurements.MEAN);
        return st.mean;
    }
}
