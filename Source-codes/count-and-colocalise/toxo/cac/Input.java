package toxo.cac;

import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.measure.Calibration;
import ij.plugin.CompositeConverter;
import ij.process.LUT;

import java.awt.image.IndexColorModel;
import java.io.BufferedReader;
import java.io.File;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The images to process and how each is opened, as the macro version does it: the folder scan (experiments and
 * conditions), files holding several series, one TIFF per channel, the pixel size, the channel colours.
 */
final class Input {
    private Input() {}

    /** One image to process: a file (one series of it), or one acquisition of the one-TIFF-per-channel layout. */
    static final class Entry {
        String path = "", manip = "-", cond = "-";
        int series = 1, seriesTotal = 1;
        String seriesName = "";
        // one TIFF per channel
        String tBlue = "", tGreen = "", tRed = "", tPole = "", tBase = "", tKey = "", tExp = "", tCov = "";

        /** Name of the image in the outputs. */
        String base(boolean tiffMode) {
            if (tiffMode) return tBase;
            String b = stripExt(new File(path).getName());
            if (seriesTotal > 1) b = b + "_s" + series;
            return b;
        }
    }

    // ================================================================== small helpers of the macro language
    /** getFileList(dir): names, folders with "/", hidden files left out, sorted. */
    static String[] fileList(String dir) {
        File f = new File(dir);
        if (!f.exists() || !f.isDirectory()) return new String[0];
        String[] list = f.list();
        if (list == null) return new String[0];
        if (!IJ.isWindows()) Arrays.sort(list);
        List<String> out = new ArrayList<String>();
        for (String s : list) {
            if (s.startsWith(".") || s.equals("Thumbs.db")) continue;
            if (new File(dir, s).isDirectory()) s = s + "/";
            out.add(s);
        }
        return out.toArray(new String[0]);
    }

    /** Array.sort of strings: case-insensitive, stable. */
    static void sortCI(String[] a) {
        Arrays.sort(a, String.CASE_INSENSITIVE_ORDER);
    }

    static void sortCI(List<String> a) {
        java.util.Collections.sort(a, String.CASE_INSENSITIVE_ORDER);
    }

    /** split(txt, "\n"): the lines, empty ones kept. */
    static String[] splitLines(String str) {
        List<String> v = new ArrayList<String>();
        try {
            BufferedReader br = new BufferedReader(new StringReader(str));
            String line;
            while ((line = br.readLine()) != null) v.add(line);
            br.close();
        } catch (Exception e) {
            // nothing
        }
        return v.toArray(new String[0]);
    }

    /** split(s, delims): tokens, empty ones left out. */
    static String[] split(String s, String delims) {
        java.util.StringTokenizer t = new java.util.StringTokenizer(s, delims);
        String[] out = new String[t.countTokens()];
        for (int i = 0; i < out.length; i++) out[i] = t.nextToken();
        return out;
    }

    /** File name without its extension. */
    static String stripExt(String name) {
        int d = name.lastIndexOf('.');
        if (d > 0) return name.substring(0, d);
        return name;
    }

    /** cleanName of the macro: the last part of a path, without the separators at its end. */
    static String cleanName(String s) {
        while (s.endsWith("/") || s.endsWith("\\")) s = s.substring(0, s.length() - 1);
        return new File(s).getName();
    }

    /** Forward slashes. */
    static String fwd(String p) {
        return p.replace("\\", "/");
    }

    /** Text without the spaces and tabs at its ends. */
    static String trimS(String s) {
        while (s.startsWith(" ") || s.startsWith("\t")) s = s.substring(1);
        while (s.endsWith(" ") || s.endsWith("\t")) s = s.substring(0, s.length() - 1);
        return s;
    }

    static String lc(String s) {
        return s.toLowerCase(Locale.US);
    }

    // ================================================================== file types
    /** The file types of the first dialog as extensions (".czi", ".nd"); TIF also takes TIFF. */
    static String[] parseExts(String txt) {
        String t = lc(txt).replace(",", " ").replace(";", " ");
        List<String> ext = new ArrayList<String>();
        for (String e0 : split(t, " ")) {
            String e = e0;
            while (e.startsWith("*")) e = e.substring(1);
            while (e.startsWith(".")) e = e.substring(1);
            if (e.isEmpty()) continue;
            if (!ext.contains("." + e)) ext.add("." + e);
            if (e.equals("tif") && !ext.contains(".tiff")) ext.add(".tiff");
        }
        return ext.toArray(new String[0]);
    }

    static boolean isImageFile(String name, String[] exts) {
        String l = lc(name);
        for (String e : exts) if (l.endsWith(e)) return true;
        return false;
    }

    static String fmtText(Settings S, String[] exts) {
        if (S.tiffMode) return "TIFF (" + S.kwBlue + " / " + S.kwGreen + " / " + S.kwRed + ")";
        StringBuilder s = new StringBuilder();
        for (int q = 0; q < exts.length; q++) {
            if (q > 0) s.append(" / ");
            s.append(exts[q]);
        }
        return s.toString();
    }

    // ================================================================== the image list
    /** The images under rootFolder (or the single file inFile), as collectImages of the macro. */
    static List<Entry> collect(Settings S, String rootFolder, String inFile, List<String> warn) {
        String[] exts = parseExts(S.extList);
        List<Entry> base = new ArrayList<Entry>();
        if (S.tiffMode) {
            for (Entry e : scanTiffRoot(S, rootFolder)) {
                e.manip = e.tExp;
                e.cond = e.tCov;
                e.path = firstPath(e.tBlue, e.tGreen, e.tRed);
                base.add(e);
            }
        } else if (S.inputMode.equals("FILE")) {
            Entry e = new Entry();
            e.manip = "-";
            e.cond = cleanName(rootFolder);
            e.path = inFile;
            base.add(e);
        } else if (S.inputMode.equals("FOLDER")) {
            String cond1 = cleanName(rootFolder);
            String[] fl = fileList(rootFolder);
            sortCI(fl);
            List<String> ndB = ndBases(fl);
            for (String f : fl) {
                if (isImageFile(f, exts) && !ndOwned(f, ndB)) {
                    Entry e = new Entry();
                    e.manip = "-";
                    e.cond = cond1;
                    e.path = rootFolder + f;
                    base.add(e);
                }
            }
        } else {
            scanDir(rootFolder, "", "", exts, base);
        }

        // one entry per position of a .nd; other files holding several series when asked (Bio-Formats)
        List<Entry> out = new ArrayList<Entry>();
        boolean anyBF = false;
        if (!base.isEmpty() && S.splitSeries && !S.tiffMode) {
            for (Entry e : base) {
                String l = lc(e.path);
                boolean isNd = l.endsWith(".nd");
                boolean isTf = isTifName(l);
                if (S.openMethod.equals("imagej")) isTf = true;
                if (!isNd && !isTf) anyBF = true;
                if (isNd && (S.openMethod.equals("importer") || S.openMethod.equals("ext"))) anyBF = true;
            }
        }
        for (Entry e : base) {
            int ns = 1;
            String[] names = {""};
            String l = lc(e.path);
            boolean nativeNd = l.endsWith(".nd") && (S.openMethod.equals("auto") || S.openMethod.equals("imagej"));
            if (!S.tiffMode && nativeNd) {
                Nd nd = new Nd();
                if (nd.parse(e.path)) {
                    ns = nd.nPos;
                    names = Nd.copyOf(nd.stages);
                }
            } else if (anyBF) {
                boolean isTf = isTifName(l);
                if (S.openMethod.equals("imagej")) isTf = true;
                if (!isTf) {
                    int n = BFOpen.seriesCount(fwd(e.path), warn);
                    ns = n < 1 ? 1 : n;
                }
            }
            for (int s = 1; s <= ns; s++) {
                Entry x = copy(e);
                x.series = s;
                x.seriesTotal = ns;
                x.seriesName = names.length >= s ? names[s - 1] : "";
                out.add(x);
            }
        }
        return out;
    }

    static boolean isTifName(String lower) {
        boolean t = lower.endsWith(".tif") || lower.endsWith(".tiff");
        if (lower.contains(".ome.tif")) t = false;
        return t;
    }

    private static Entry copy(Entry e) {
        Entry x = new Entry();
        x.path = e.path;
        x.manip = e.manip;
        x.cond = e.cond;
        x.tBlue = e.tBlue;
        x.tGreen = e.tGreen;
        x.tRed = e.tRed;
        x.tPole = e.tPole;
        x.tBase = e.tBase;
        x.tKey = e.tKey;
        x.tExp = e.tExp;
        x.tCov = e.tCov;
        return x;
    }

    /** Recursive scan: the last two folders above a file give the experiment and the condition. */
    static void scanDir(String dir, String lvl1, String lvl2, String[] exts, List<Entry> acc) {
        String[] lst = fileList(dir);
        sortCI(lst);
        List<String> ndB = ndBases(lst);
        for (String nm : lst) {
            String full = dir + nm;
            if (new File(full).isDirectory()) {
                String cn = cleanName(nm);
                if (cn.equals("Results")) continue;
                if (cn.equals("Validation")) continue;
                if (lvl1.isEmpty()) scanDir(full, cn, "", exts, acc);
                else if (lvl2.isEmpty()) scanDir(full, lvl1, cn, exts, acc);
                else scanDir(full, lvl2, cn, exts, acc);
            } else {
                if (!isImageFile(nm, exts)) continue;
                if (ndOwned(nm, ndB)) continue;
                String mm, cc;
                if (lvl2.isEmpty()) {
                    mm = "-";
                    cc = lvl1;
                } else {
                    mm = lvl1;
                    cc = lvl2;
                }
                if (cc.isEmpty()) cc = "-";
                Entry e = new Entry();
                e.manip = mm;
                e.cond = cc;
                e.path = full;
                acc.add(e);
            }
        }
    }

    /** Lower-case names, without extension, of the .nd files of a folder listing. */
    static List<String> ndBases(String[] lst) {
        List<String> out = new ArrayList<String>();
        for (String s : lst) {
            String l = lc(s);
            if (l.endsWith(".nd")) out.add(l.substring(0, l.length() - 3));
        }
        return out;
    }

    /** A TIFF / STK plane file of one of the .nd files of its folder (named <nd name>_...). */
    static boolean ndOwned(String nm, List<String> ndB) {
        String l = lc(nm);
        if (!(l.endsWith(".tif") || l.endsWith(".tiff") || l.endsWith(".stk"))) return false;
        for (String b : ndB) if (l.startsWith(b + "_")) return true;
        return false;
    }

    /** What an earlier run wrote into the output folder dir, left out of the list. Returns how many. An output
     *  folder inside the input folder: everything under it. The input folder itself (or a folder holding it): only
     *  the folders this tool writes there - otherwise every image would be left out. */
    static int dropUnder(List<Entry> list, String dir, String rootFolder) {
        String d = fwd(dir);
        if (!d.endsWith("/")) d = d + "/";
        d = lc(d);
        String r = lc(fwd(rootFolder));
        if (!r.endsWith("/")) r = r + "/";
        String[] pre = {d};
        if (r.startsWith(d))
            pre = new String[]{d + "stacks_", d + "data/", d + "hyperstacks/", d + "projections/", d + "merges/",
                    d + "projection merges/"};
        int n0 = list.size();
        for (int i = list.size() - 1; i >= 0; i--) {
            String pq = lc(fwd(list.get(i).path));
            for (String p : pre)
                if (pq.startsWith(p)) {
                    list.remove(i);
                    break;
                }
        }
        return n0 - list.size();
    }

    // ================================================================== one TIFF per channel
    /** <root>/<experiment>/<data>/<anything>--W00003--P00020--Z00000--T00000--<channel>.tif, grouped by
     *  experiment x coverslip (W) x field (P). */
    static List<Entry> scanTiffRoot(Settings S, String root) {
        List<Entry> acq = new ArrayList<Entry>();
        String[] exps = fileList(root);
        sortCI(exps);
        for (String ex : exps) {
            if (!new File(root + ex).isDirectory()) continue;
            String expName = cleanName(ex);
            if (expName.equals("Results")) continue;
            String expDir = root + ex;
            String dDir = findSub(expDir, S.dataSub);
            if (dDir.isEmpty()) dDir = expDir;
            String[] fl = fileList(dDir);
            sortCI(fl);
            for (String nm : fl) {
                String l = lc(nm);
                if (!(l.endsWith(".tif") || l.endsWith(".tiff"))) continue;
                String stem = stripExt(nm);
                int wPos = stem.lastIndexOf("--W");
                if (wPos < 0) continue;
                String wNum = digitsAt(stem, wPos + 3);
                int pPos = stem.indexOf("--P", wPos);
                if (pPos < 0) continue;
                String pNum = digitsAt(stem, pPos + 3);
                if (wNum.isEmpty() || pNum.isEmpty()) continue;
                String role = chanRole(S, stem);
                if (role.isEmpty()) continue;
                String key = expName + "|" + wNum + "|" + pNum;
                Entry e = null;
                for (int k = acq.size() - 1; k >= 0 && e == null; k--) if (acq.get(k).tKey.equals(key)) e = acq.get(k);
                if (e == null) {
                    e = new Entry();
                    e.tKey = key;
                    e.tExp = expName;
                    e.tCov = "Coverslip " + Macro.n2s(Macro.parseInt(wNum));
                    e.tBase = "W" + wNum + "_P" + pNum;
                    acq.add(e);
                }
                String full = dDir + nm;
                if (role.equals("BLUE")) e.tBlue = addPath(e.tBlue, full);
                if (role.equals("GREEN")) e.tGreen = addPath(e.tGreen, full);
                if (role.equals("RED")) e.tRed = addPath(e.tRed, full);
                if (role.equals("POLE")) e.tPole = addPath(e.tPole, full);
            }
        }
        return acq;
    }

    /** Subfolder of dir whose name matches, ignoring case; "" if none. */
    static String findSub(String dir, String name) {
        String want = lc(name);
        for (String s : fileList(dir)) {
            if (!new File(dir + s).isDirectory()) continue;
            if (lc(cleanName(s)).equals(want)) return dir + s;
        }
        return "";
    }

    static String digitsAt(String s, int pos) {
        StringBuilder out = new StringBuilder();
        for (int q = pos; q < s.length(); q++) {
            char c = s.charAt(q);
            if (c >= '0' && c <= '9') out.append(c);
            else break;
        }
        return out.toString();
    }

    /** Channel of a file, from the part after its last "--". */
    static String chanRole(Settings S, String stem) {
        int li = stem.lastIndexOf("--");
        if (li < 0) return "";
        String chn = lc(stem.substring(li + 2));
        if (S.kwBlue.length() > 0 && chn.contains(lc(S.kwBlue))) return "BLUE";
        if (S.kwGreen.length() > 0 && chn.contains(lc(S.kwGreen))) return "GREEN";
        if (S.kwRed.length() > 0 && chn.contains(lc(S.kwRed))) return "RED";
        if (S.poleNeedMk && S.kwPole.length() > 0 && chn.contains(lc(S.kwPole))) return "POLE";
        return "";
    }

    static String addPath(String joined, String p) {
        return joined.isEmpty() ? p : joined + "|" + p;
    }

    static String firstPath(String a, String b, String c) {
        if (!a.isEmpty()) return split(a, "|")[0];
        if (!b.isEmpty()) return split(b, "|")[0];
        if (!c.isEmpty()) return split(c, "|")[0];
        return "";
    }

    /** One channel of an acquisition: its file(s) opened, several Z files max-projected. Null if no file. */
    static ImagePlus openTiffChannel(String joined, String outTitle) {
        if (joined.isEmpty()) return null;
        String[] ps = split(joined, "|");
        ImagePlus raw;
        if (ps.length == 1) {
            raw = IJ.openImage(fwd(ps[0]));
            if (raw == null) return null;
        } else {
            ImagePlus[] zs = new ImagePlus[ps.length];
            for (int q = 0; q < ps.length; q++) {
                zs[q] = IJ.openImage(fwd(ps[q]));
                if (zs[q] == null) return null;
                zs[q].setTitle(outTitle + "_z" + q);
            }
            raw = Cmd.concat(zs, outTitle + "_raw");
        }
        raw.setTitle(outTitle + "_raw");
        if (raw.getBitDepth() == 24) Cmd.convert(raw, 8);
        ImagePlus out;
        if (raw.getStackSize() > 1) out = Cmd.maxProject(raw, false);
        else out = raw;
        out.setTitle(outTitle);
        return out;
    }

    // ================================================================== opening a file
    /** The result of opening one entry: the image, or the reason it could not be opened. */
    static final class Opened {
        ImagePlus imp;
        String err = "";
        final List<String> log = new ArrayList<String>();
        String[] chNames = new String[0];
    }

    /**
     * Opens entry e as the macro does (openFile): .nd read here, TIFF with ImageJ, the rest with Bio-Formats,
     * or as the first dialog says. An RGB image becomes three channels, red, green and blue.
     */
    static Opened openFile(Settings S, Entry e) {
        Opened o = new Opened();
        String p = fwd(e.path);
        String l = lc(p);
        boolean isND = l.endsWith(".nd");
        boolean isTif = isTifName(l);
        if (isND && (S.openMethod.equals("auto") || S.openMethod.equals("imagej"))) {
            Nd nd = new Nd();
            o.imp = nd.open(p, e.series, o.log);
            if (o.imp == null) o.err = nd.err;
            else o.chNames = Nd.copyOf(nd.waves);
            return o;
        }
        if (S.openMethod.equals("imagej") || (S.openMethod.equals("auto") && isTif)) {
            o.imp = IJ.openImage(p);
            if (o.imp == null) o.err = "ImageJ could not open it";
            rgbChannels(o);
            return o;
        }
        if (S.openMethod.equals("ext")) {
            o.imp = BFOpen.simple(p, o);
            if (o.imp != null && e.seriesTotal > 1 && e.series > 1)
                o.log.add("   NOTE: the Bio-Formats simple opener reads the first series only; "
                        + "choose the Importer to analyse series " + e.series);
            rgbChannels(o);
            return o;
        }
        // "Composite": each channel in the colour its file gives it (the "Default" colour mode of Bio-Formats would
        // give red, green, blue in channel order, whatever the file says)
        String opt = importOptions(p, e, "Composite");
        o.log.add("   opening: " + opt);
        o.imp = BFOpen.importer(opt, o);
        rgbChannels(o);
        return o;
    }

    static String importOptions(String p, Entry e, String mode) {
        String o = "open=[" + fwd(p) + "] autoscale color_mode=" + mode
                + " rois_import=[ROI manager] view=Hyperstack stack_order=XYCZT";
        if (e.seriesTotal > 1) o = o + " series_" + e.series;
        return o;
    }

    /** An RGB image becomes three channels, red, green and blue (rgbChannels of the macro). */
    static void rgbChannels(Opened o) {
        if (o.imp == null || o.imp.getBitDepth() != 24) return;
        int slice = o.imp.getCurrentSlice();
        ImagePlus c = CompositeConverter.makeComposite(o.imp);
        if (c == null) return;
        // as run("Make Composite") leaves it in batch mode: shown (reset), at the slice of the RGB image
        if (c instanceof CompositeImage) ((CompositeImage) c).reset();
        if (o.imp.getNSlices() * o.imp.getNFrames() > 1) c.setSlice(slice);
        // no "Red / Green / Blue" slice labels: a merge of three of these channels would be taken for an RGB
        // stack, and shown with its raw values instead of the display range of each channel. Channel by
        // channel, as the macro version does it (Stack.setChannel: the view follows).
        if (c.getStackSize() == 3) {
            for (int k = 1; k <= 3; k++) {
                c.setPosition(k, c.getSlice(), c.getFrame());
                c.getStack().setSliceLabel(null, c.getCurrentSlice());
            }
            c.setPosition(1, c.getSlice(), c.getFrame());
        }
        o.imp = c;
    }

    // ================================================================== the view counted
    /** First time point, Z planes merged by maximum projection; a single plane as it is. */
    static ImagePlus countView(ImagePlus src) {
        ImagePlus cur = src;
        int vz = src.getNSlices(), vt = src.getNFrames();
        if (vt > 1) cur = Cmd.firstTimePoint(src, src.getTitle() + "_t1");
        if (vz > 1) cur = Cmd.maxProject(cur, false);
        return cur;
    }

    // ================================================================== pixel size
    /** The pixel size applied to an image (applyPixelSize of the macro). */
    static final class Px {
        double w, h;
        String source = "";
        boolean fromFile;
    }

    /** Sets the pixel size of imp, in um, as the macro does (the first dialog's value when the image has none,
     *  or always when forced). */
    static Px applyPixelSize(Settings S, ImagePlus imp) {
        Calibration cal = imp.getCalibration();
        double vw = cal.pixelWidth, vh = cal.pixelHeight, vd = cal.pixelDepth;
        String vu = cal.getUnits();
        double f = umPerUnit(vu);
        boolean useFile = false;
        String why = "";
        if (S.pixelForce) {
            why = "forced in the first dialog; the image said " + Macro.n2s(vw) + " " + vu;
        } else if (f == 0) {
            String lu = lc(vu);
            if (lu.equals("pixel") || lu.equals("pixels") || lu.equals("") || lu.equals(" "))
                why = "the image has none: value of the first dialog";
            else
                why = "the image said " + Macro.n2s(vw) + " " + vu + ", not a length in um: value of the first dialog";
        } else if (vw * f < 0.005 || vw * f > 100 || vh * f < 0.005 || vh * f > 100) {
            why = "the image said " + Macro.n2s(vw) + " " + vu + ", out of the range of a microscope: "
                    + "value of the first dialog";
        } else {
            useFile = true;
        }
        Px px = new Px();
        double pw, ph, pd;
        if (useFile) {
            pw = vw * f;
            ph = vh * f;
            pd = vd * f;
            if (pd <= 0) pd = pw;
            px.source = "read from the image";
            if (f != 1) px.source = "read from the image, " + Macro.n2s(vw) + " " + vu + " converted to um";
        } else {
            pw = S.pixelSize;
            ph = S.pixelSize;
            pd = vd * f;
            if (f == 0 || pd <= 0) pd = S.pixelSize;
            px.source = why;
        }
        cal.pixelWidth = pw;
        cal.pixelHeight = ph;
        cal.pixelDepth = pd;
        cal.setUnit("micron");
        px.w = pw;
        px.h = ph;
        px.fromFile = useFile;
        return px;
    }

    /** The note on the pixel size of the first dialog, when an image has its own (said once per run). */
    static boolean pixelNote(Settings S, Px px) {
        return px.fromFile && Math.abs(S.pixelSize - px.w) > 0.01 * S.pixelSize;
    }

    static String pixelNoteText(Settings S) {
        return "   NOTE: the pixel size of the first dialog (" + Macro.n2s(S.pixelSize)
                + " um) is not used, because the image has its own. To "
                + "impose it, choose 'The value below, whatever the image says'.";
    }

    /** Factor turning a length unit into um; 0 when the unit is no length. */
    static double umPerUnit(String u) {
        String l = lc(u);
        while (l.startsWith(" ")) l = l.substring(1);
        while (l.endsWith(" ")) l = l.substring(0, l.length() - 1);
        if (l.equals("um") || l.equals("micron") || l.equals("microns") || l.equals("micrometer")
                || l.equals("micrometers") || l.equals("micrometre") || l.equals("micrometres")) return 1;
        if (l.equals("nm") || l.equals("nanometer") || l.equals("nanometers") || l.equals("nanometre")) return 0.001;
        if (l.equals("mm") || l.equals("millimeter") || l.equals("millimeters") || l.equals("millimetre")) return 1000;
        if (l.equals("cm") || l.equals("centimeter") || l.equals("centimeters") || l.equals("centimetre")) return 10000;
        if (l.equals("m") || l.equals("meter") || l.equals("meters") || l.equals("metre")) return 1000000;
        if (l.equals("km")) return 1000000000;
        if (l.length() == 2 && l.endsWith("m")) return 1;
        if (l.contains("micro")) return 1;
        return 0;
    }

    // ================================================================== channel colours
    /** Colour of the LUT of channel c (1-based) of imp, in words (lutColour of the macro). */
    static String lutColour(ImagePlus imp, int c) {
        IndexColorModel cm;
        if (imp.isComposite()) {
            LUT lut = ((CompositeImage) imp).getChannelLut(c);
            cm = lut;
        } else {
            java.awt.image.ColorModel m = imp.getProcessor().getColorModel();
            if (!(m instanceof IndexColorModel)) return "other";
            cm = (IndexColorModel) m;
        }
        int last = cm.getMapSize() - 1;
        int rr = cm.getRed(last), gg = cm.getGreen(last), bb = cm.getBlue(last);
        if (rr == gg && gg == bb) return "grey";
        if (bb > rr && bb > gg) return "blue";
        if (gg > rr && gg > bb) return "green";
        if (rr > gg && rr > bb) return "red";
        if (gg == bb && gg > rr) return "cyan";
        if (rr == bb && rr > gg) return "magenta";
        if (rr == gg && rr > bb) return "yellow";
        return "other";
    }

    /** The colour word of every channel of imp. */
    static String[] colours(ImagePlus imp) {
        int n = imp.getNChannels();
        String[] cols = new String[n];
        for (int c = 1; c <= n; c++) cols[c - 1] = lutColour(imp, c);
        return cols;
    }

    /** Channel roles from the colours of the first image: [blue, green, red, dic] (sugFromCols). */
    static int[] sugFromCols(String[] cols, int dcSkip) {
        int dDIC = 0, dRED = 0, dGREEN = 0, dBLUE = 0;
        for (int c = 1; c <= cols.length; c++) {
            if (c == dcSkip) continue;
            String cw = cols[c - 1];
            if (cw.equals("grey")) {
                if (dDIC == 0) dDIC = c;
            } else if (cw.equals("blue")) {
                if (dBLUE == 0) dBLUE = c;
            } else if (cw.equals("green")) {
                if (dGREEN == 0) dGREEN = c;
            } else if (cw.equals("red")) {
                if (dRED == 0) dRED = c;
            } else if (cw.equals("cyan")) {
                if (dBLUE == 0) dBLUE = c;
                else if (dGREEN == 0) dGREEN = c;
            } else if (cw.equals("magenta")) {
                if (dRED == 0) dRED = c;
                else if (dBLUE == 0) dBLUE = c;
            } else if (cw.equals("yellow")) {
                if (dRED == 0) dRED = c;
                else if (dGREEN == 0) dGREEN = c;
            }
        }
        return new int[]{dBLUE, dGREEN, dRED, dDIC};
    }

    /** Channels of the roles in one file: the channel with the colour each role has in the first file
     *  (roleCols: nuclei, parasites, marker, DIC). Returns [blue, green, red, dic]. */
    static int[] rolesFromCols(String[] cols, String[] roleCols, int dcSkip) {
        int dDIC = 0, dRED = 0, dGREEN = 0, dBLUE = 0;
        for (int c = 1; c <= cols.length; c++) {
            if (c == dcSkip) continue;
            String cw = cols[c - 1];
            if (cw.equals(roleCols[0]) && dBLUE == 0) dBLUE = c;
            else if (cw.equals(roleCols[1]) && dGREEN == 0) dGREEN = c;
            else if (cw.equals(roleCols[2]) && dRED == 0) dRED = c;
            else if (cw.equals(roleCols[3]) && dDIC == 0) dDIC = c;
        }
        return new int[]{dBLUE, dGREEN, dRED, dDIC};
    }

    /** LUT for a channel named after its fluorophore, filter or wavelength. */
    static String lutForName(String nm) {
        String l = lc(nm);
        String[] far = {"cy5", "647", "640", "633", "660", "far red", "far-red", "alexa 680", "a680"};
        for (String s : far) if (l.contains(s)) return "Magenta";
        String[] red = {"txred", "tx red", "tx-red", "texas", "tritc", "mcherry", "rfp", "cy3", "546", "555", "561",
                "568", "594", "dsred", "tomato", "red"};
        for (String s : red) if (l.contains(s)) return "Red";
        String[] grn = {"gfp", "fitc", "488", "green", "yfp", "cy2", "alexa 488", "a488"};
        for (String s : grn) if (l.contains(s)) return "Green";
        String[] blu = {"dapi", "hoechst", "405", "blue", "bfp", "350", "uv"};
        for (String s : blu) if (l.contains(s)) return "Blue";
        return "Grays";
    }

    /** Colour word of a LUT name. */
    static String lutWord(String lt) {
        if (lt.equals("Blue")) return "blue";
        if (lt.equals("Green")) return "green";
        if (lt.equals("Red")) return "red";
        if (lt.equals("Magenta")) return "magenta";
        if (lt.equals("Cyan")) return "cyan";
        if (lt.equals("Yellow")) return "yellow";
        return "grey";
    }
}
