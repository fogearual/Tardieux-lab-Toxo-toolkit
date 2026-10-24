package toxo.cac;

import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import ij.plugin.HyperStackConverter;
import ij.process.ImageProcessor;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * MetaMorph .nd, read without Bio-Formats, as the macro version does it: the .nd is a text file describing the
 * acquisition (wavelengths, stage positions, time points, Z series); the images are the TIFF / STK files next to
 * it, <nd name>_w<n><wavelength>_s<position>_t<time point>.TIF, each part present only when the acquisition has
 * it. One hyperstack per position, channels in the order of the wavelengths.
 */
final class Nd {

    String[] waves = new String[0];      // wavelength names, in order
    int[] waveZ = new int[0];            // 1 when that wavelength has Z planes
    String[] stages = new String[0];     // stage position names
    int nPos = 1, nT = 1, nZ = 1;
    double zStep = 0;
    boolean stage, time, wave;
    String err = "";

    // folder listing kept for file(), per folder
    private String dirKey = "";
    private String[] dirLst = new String[0];

    /** Reads the .nd p. Returns false with the reason in err. */
    boolean parse(String p) {
        waves = new String[0];
        waveZ = new int[0];
        stages = new String[0];
        nPos = 1;
        nT = 1;
        nZ = 1;
        zStep = 0;
        stage = false;
        time = false;
        wave = false;
        File f = new File(p);
        if (!f.exists()) {
            err = "file not found: " + p;
            return false;
        }
        String txt;
        try {
            txt = new String(Files.readAllBytes(f.toPath()), StandardCharsets.ISO_8859_1);
        } catch (Exception e) {
            err = "file not found: " + p;
            return false;
        }
        String[] lines = Input.splitLines(txt);
        double nW = 0, nS = 0, nTT = 0;
        boolean doZ = true;
        List<Double> wI = new ArrayList<Double>(), zI = new ArrayList<Double>(), sI = new ArrayList<Double>();
        List<String> wN = new ArrayList<String>(), sN = new ArrayList<String>();
        List<Integer> zV = new ArrayList<Integer>();
        double ndNZ = 1, ndZStep = 0;
        int nKeys = 0;
        for (String l0 : lines) {
            String ln = l0.replace("\r", "");
            if (!ln.startsWith("\"")) continue;
            int q2 = ln.indexOf('"', 1);
            if (q2 < 0) continue;
            String key = ln.substring(1, q2);
            String rest = ln.substring(q2 + 1);
            int cm = rest.indexOf(',');
            if (cm < 0) continue;
            String val = Input.trimS(rest.substring(cm + 1));
            if (val.length() >= 2 && val.startsWith("\"") && val.endsWith("\"")) val = val.substring(1, val.length() - 1);
            String uv = val.toUpperCase(Locale.US);
            nKeys++;
            if (key.equals("DoTimelapse")) {
                if (uv.equals("TRUE")) time = true;
            } else if (key.equals("NTimePoints")) nTT = Macro.parseInt(val);
            else if (key.equals("DoStage")) {
                if (uv.equals("TRUE")) stage = true;
            } else if (key.equals("NStagePositions")) nS = Macro.parseInt(val);
            else if (key.equals("DoWave")) {
                if (uv.equals("TRUE")) wave = true;
            } else if (key.equals("NWavelengths")) nW = Macro.parseInt(val);
            else if (key.equals("DoZSeries")) {
                if (uv.equals("FALSE")) doZ = false;
            } else if (key.equals("NZSteps")) ndNZ = Macro.parseInt(val);
            else if (key.equals("ZStepSize")) ndZStep = Macro.parseFloat(val);
            else if (key.startsWith("WaveName")) {
                double k = Macro.parseInt(key.substring(8));
                if (!Double.isNaN(k)) {
                    wI.add(k);
                    wN.add(val);
                }
            } else if (key.startsWith("WaveDoZ")) {
                double k = Macro.parseInt(key.substring(7));
                int zz = uv.equals("FALSE") ? 0 : 1;
                if (!Double.isNaN(k)) {
                    zI.add(k);
                    zV.add(zz);
                }
            } else if (key.startsWith("Stage")) {
                double k = Macro.parseInt(key.substring(5));
                if (!Double.isNaN(k)) {
                    sI.add(k);
                    sN.add(val);
                }
            }
        }
        if (nKeys == 0) {
            err = "this .nd file holds no MetaMorph settings: " + p;
            return false;
        }
        if (Double.isNaN(nW) || nW < 1) nW = 1;
        for (double k : wI) if (k > nW) nW = k;
        if (Double.isNaN(ndNZ) || ndNZ < 1 || !doZ) ndNZ = 1;
        if (Double.isNaN(ndZStep) || ndZStep < 0) ndZStep = 0;
        nZ = (int) ndNZ;
        zStep = ndZStep;
        if (!Double.isNaN(nTT) && nTT > 1) nT = (int) nTT;
        if (stage && !Double.isNaN(nS) && nS > 1) nPos = (int) nS;
        if (wI.size() > 0) wave = true;
        int nw = (int) nW;
        waves = new String[nw];
        waveZ = new int[nw];
        for (int q = 0; q < nw; q++) {
            waves[q] = "w" + (q + 1);
            waveZ[q] = nZ == 1 ? 0 : 1;
        }
        for (int q = 0; q < wI.size(); q++) {
            double k = wI.get(q);
            if (k >= 1 && k <= nw) waves[(int) k - 1] = wN.get(q);
        }
        for (int q = 0; q < zI.size(); q++) {
            double k = zI.get(q);
            if (k >= 1 && k <= nw && nZ > 1) waveZ[(int) k - 1] = zV.get(q);
        }
        stages = new String[nPos];
        for (int q = 0; q < nPos; q++) stages[q] = "position " + (q + 1);
        for (int q = 0; q < sI.size(); q++) {
            double k = sI.get(q);
            if (k >= 1 && k <= nPos) stages[(int) k - 1] = sN.get(q);
        }
        return true;
    }

    /** The image file of wavelength w, position s, time point t (1-based) of the .nd p, "" if none. */
    String file(String p, int w, int s, int t) {
        String dir = new File(p).getParent();
        dir = (dir == null ? "" : dir) + File.separator;
        if (!dir.equals(dirKey)) {
            dirLst = Input.fileList(dir);
            Input.sortCI(dirLst);
            dirKey = dir;
        }
        String stem = Input.stripExt(new File(p).getName());
        String base = stem.toLowerCase(Locale.US);
        int nW = waves.length;
        String best = "";
        for (String nm : dirLst) {
            String lc = nm.toLowerCase(Locale.US);
            if (!(lc.endsWith(".tif") || lc.endsWith(".tiff") || lc.endsWith(".stk"))) continue;
            if (lc.contains("_thumb")) continue;
            if (!lc.startsWith(base)) continue;
            String st = Input.stripExt(lc);
            String tk = "_t" + t;
            if (st.endsWith(tk)) st = st.substring(0, st.length() - tk.length());
            else if (nT > 1) continue;
            String sk = "_s" + s;
            if (st.endsWith(sk)) st = st.substring(0, st.length() - sk.length());
            else if (nPos > 1) continue;
            String wk = base + "_w" + w;
            if (st.startsWith(wk)) {
                String nx = st.substring(wk.length());
                if (nW >= 10 && nx.length() > 0 && "0123456789".indexOf(nx.charAt(0)) >= 0) continue;
            } else {
                if (nW > 1) continue;
                if (!st.equals(base)) continue;
            }
            if (best.isEmpty()) best = dir + nm;
        }
        return best;
    }

    /**
     * Opens position s of the .nd p as one hyperstack (channels in the order of the wavelengths, Z planes,
     * time points), titled after the file and position. Returns null with the reason in err; the notes of the
     * reading go to log.
     */
    ImagePlus open(String p, int s, List<String> log) {
        if (!parse(p)) return null;
        int nW = waves.length;
        String pName = new File(p).getName();
        if (nW > 7) {
            err = "more than 7 wavelengths in " + pName;
            return null;
        }
        String base = Input.stripExt(pName);
        String title = base;
        if (nPos > 1) title = base + "_s" + s;
        List<ImagePlus> parts = new ArrayList<ImagePlus>();
        int nTimes = 0, zMax = 1, w0 = 0, h0 = 0, bdMax = 0;
        String info0 = "";
        boolean stopT = false;
        for (int t = 1; t <= nT; t++) {
            if (stopT) continue;
            String[] found = new String[nW];
            boolean allF = true;
            for (int w = 1; w <= nW; w++) {
                String f = file(p, w, s, t);
                found[w - 1] = f;
                if (f.isEmpty()) allF = false;
            }
            if (!allF) {
                if (t == 1) {
                    StringBuilder miss = new StringBuilder();
                    for (int w = 1; w <= nW; w++) {
                        if (!found[w - 1].isEmpty()) continue;
                        String expT = base + "_w" + w + waves[w - 1];
                        if (nPos > 1) expT = expT + "_s" + s;
                        if (nT > 1) expT = expT + "_t" + t;
                        miss.append(' ').append(expT).append(".TIF");
                    }
                    err = "image file(s) of " + pName + " not found next to it:" + miss;
                    return null;
                }
                log.add("   NOTE: " + pName + ": time point " + t + " incomplete, read up to time point " + (t - 1));
                stopT = true;
                continue;
            }
            for (int w = 1; w <= nW; w++) {
                ImagePlus imp = IJ.openImage(Input.fwd(found[w - 1]));
                if (imp == null) {
                    err = "ImageJ could not open " + found[w - 1];
                    return null;
                }
                if (imp.getBitDepth() == 24) Cmd.convert(imp, 8);
                if (imp.getNChannels() > 1 || imp.getNFrames() > 1) imp = hyperstackToStack(imp);
                imp.setTitle("ND_" + t + "_" + w);
                if (t == 1 && w == 1) {
                    w0 = imp.getWidth();
                    h0 = imp.getHeight();
                    Object inf = imp.getProperty("Info");
                    info0 = inf instanceof String ? (String) inf : "";
                }
                if (imp.getWidth() != w0 || imp.getHeight() != h0) {
                    err = "the images of " + pName + " differ in size";
                    return null;
                }
                if (imp.getStackSize() > zMax) zMax = imp.getStackSize();
                if (imp.getBitDepth() > bdMax) bdMax = imp.getBitDepth();
                parts.add(imp);
            }
            nTimes++;
        }
        // same depth for all (the deepest), same number of planes (the most)
        for (int q = 0; q < parts.size(); q++) {
            ImagePlus one = parts.get(q);
            if (one.getBitDepth() != bdMax) {
                if (bdMax == 16) Cmd.convert(one, 16);
                if (bdMax == 32) Cmd.convert(one, 32);
            }
            if (one.getStackSize() < zMax) {
                if (one.getStackSize() > 1) {
                    err = "the wavelengths of " + pName + " do not have the same Z planes";
                    return null;
                }
                // one plane: repeated over the Z planes of the others ("Add stack" on a black stack)
                ImagePlus rep = Cmd.newBlack(one.getTitle() + "_rep", bdMax, w0, h0, zMax);
                ImageStack rs = rep.getStack();
                ImageProcessor src = one.getProcessor();
                for (int k = 1; k <= zMax; k++) rs.getProcessor(k).copyBits(src, 0, 0, ij.process.Blitter.ADD);
                rep.setTitle(one.getTitle());
                parts.set(q, rep);
            }
        }
        ImagePlus imp;
        if (parts.size() == 1) {
            imp = parts.get(0);
            imp.setTitle(title);
        } else {
            imp = Cmd.concat(parts.toArray(new ImagePlus[0]), title);
            imp = HyperStackConverter.toHyperStack(imp, nW, zMax, nTimes, "xyzct", "Composite");
            imp.setTitle(title);
        }
        double um = mmCalib(info0);
        if (um > 0) {
            double zs = zStep;
            if (zs <= 0) zs = um;
            Calibration cal = imp.getCalibration();
            cal.pixelWidth = um;
            cal.pixelHeight = um;
            cal.pixelDepth = zs;
            cal.setUnit("micron");
        }
        for (int w = 1; w <= nW; w++) {
            if (nW > 1) imp.setC(w);
            Cmd.applyLut(imp, Input.lutForName(waves[w - 1]));
            Cmd.resetMinAndMax(imp);
        }
        if (nW > 1) imp.setC(1);
        imp.setProperty("Info", "MetaMorph .nd read by Count and Colocalise\n" + "Source: " + p + "\nPosition: " + s
                + " of " + nPos + "\n" + info0);
        return imp;
    }

    /** run("Hyperstack to Stack") on an image read from one file. */
    private static ImagePlus hyperstackToStack(ImagePlus imp) {
        if (!(imp.isHyperStack() || imp.isComposite())) return imp;
        ImagePlus imp2 = imp;
        if (imp.isComposite()) {
            ImageStack stack = imp.getStack();
            imp2 = imp.createImagePlus();
            imp2.setStack(imp.getTitle(), stack);
            int[] dim = imp.getDimensions();
            imp2.setDimensions(dim[2], dim[3], dim[4]);
            ImageProcessor ip2 = imp2.getProcessor();
            ip2.setColorModel(ip2.getDefaultColorModel());
        }
        imp2.setOpenAsHyperStack(false);
        if (imp != imp2) {
            Calibration cal = imp2.getCalibration();
            if (cal.frameInterval > 0) imp2.setDimensions(1, 1, imp2.getStackSize());
            else imp2.setDimensions(1, imp2.getStackSize(), 1);
        }
        return imp2;
    }

    /** Pixel size in um from MetaMorph metadata (spatial-calibration-x / -units), 0 when absent or off. */
    static double mmCalib(String info) {
        if (info == null) return 0;
        int i = info.indexOf("spatial-calibration-x");
        if (i < 0) return 0;
        int j = info.indexOf("value=", i);
        if (j < 0 || j - i > 120) return 0;
        int a = info.indexOf('"', j);
        int b = a < 0 ? -1 : info.indexOf('"', a + 1);
        if (a < 0 || b < 0) return 0;
        double v = Macro.parseFloat(info.substring(a + 1, b));
        if (Double.isNaN(v) || v <= 0) return 0;
        int k = info.indexOf("spatial-calibration-state");
        if (k >= 0) {
            int j2 = info.indexOf("value=", k);
            if (j2 >= 0 && j2 - k < 120) {
                String st = info.substring(j2, Math.min(j2 + 14, info.length())).toLowerCase(Locale.US);
                if (st.contains("off") || st.contains("false")) return 0;
            }
        }
        double f = 1;
        k = info.indexOf("spatial-calibration-units");
        if (k >= 0) {
            int j2 = info.indexOf("value=", k);
            if (j2 >= 0 && j2 - k < 120) {
                int a2 = info.indexOf('"', j2);
                int b2 = a2 < 0 ? -1 : info.indexOf('"', a2 + 1);
                if (a2 >= 0 && b2 > a2) f = Input.umPerUnit(info.substring(a2 + 1, b2));
            }
        }
        if (f == 0) return 0;
        return v * f;
    }

    static String[] copyOf(String[] a) {
        return Arrays.copyOf(a, a.length);
    }

    @SuppressWarnings("unused")
    private static boolean isComposite(ImagePlus imp) {
        return imp instanceof CompositeImage;
    }
}
