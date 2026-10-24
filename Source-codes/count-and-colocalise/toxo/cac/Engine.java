package toxo.cac;

import ij.IJ;
import ij.ImagePlus;
import ij.plugin.FileInfoVirtualStack;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Entry points shared by the plugin and the tests: the channels of the first image (what the Channels dialog
 * shows) and a run from a settings file.
 */
public final class Engine {
    private Engine() {}

    /** The channels of the first image, read from its metadata (firstImageInfo of the macro). */
    public static final class FirstInfo {
        public int nc, nz = 1, nt = 1;
        public String[] cols = new String[0], names = new String[0];
        public int sugB, sugG, sugR, sugD;
        public String text = "";
        public boolean ok;
    }

    public static FirstInfo firstImageInfo(Settings S, List<Input.Entry> list) {
        FirstInfo f = new FirstInfo();
        if (list.isEmpty()) return f;
        Input.Entry e = list.get(0);
        String p = e.path;
        String nm = new File(p).getName();
        if (e.seriesTotal > 1) nm = nm + " (position / series 1 of " + e.seriesTotal + ")";
        String lc = p.toLowerCase();
        String[] cols;
        String[] names = new String[0];
        int nc, nz = 1, nt = 1;
        if (lc.endsWith(".nd") && (S.openMethod.equals("auto") || S.openMethod.equals("imagej"))) {
            Nd nd = new Nd();
            if (!nd.parse(p)) {
                f.text = "First image: " + nm + "\n   could not be read: " + nd.err;
                return f;
            }
            nc = nd.waves.length;
            nz = nd.nZ;
            nt = nd.nT;
            int pos = e.series;
            StringBuilder miss = new StringBuilder();
            String stem = Input.stripExt(new File(p).getName());
            for (int w = 1; w <= nc; w++)
                if (nd.file(p, w, pos, 1).isEmpty()) miss.append(' ').append(stem).append("_w").append(w).append(nd.waves[w - 1]);
            if (miss.length() > 0) {
                f.text = "First image: " + nm + "\n   its image files were not found next to it:" + miss;
                return f;
            }
            cols = new String[nc];
            for (int c = 0; c < nc; c++) cols[c] = Input.lutWord(Input.lutForName(nd.waves[c]));
            names = Nd.copyOf(nd.waves);
        } else {
            ImagePlus imp = null;
            String err = "";
            boolean isTif = Input.isTifName(lc);
            if (S.openMethod.equals("imagej") || (S.openMethod.equals("auto") && isTif)) {
                if (isTif) {
                    try {
                        imp = FileInfoVirtualStack.openVirtual(Input.fwd(p));
                    } catch (Throwable ex) {
                        imp = null;
                    }
                }
                if (imp == null) imp = IJ.openImage(Input.fwd(p));
                if (imp == null) err = "ImageJ could not open it";
            } else if (S.openMethod.equals("ext")) {
                int[] czt = BFOpen.sizes(Input.fwd(p), e);
                if (czt == null) err = "Bio-Formats could not open it";
                else {
                    nc = czt[0];
                    nz = czt[1];
                    nt = czt[2];
                    cols = new String[nc];
                    for (int c = 0; c < nc; c++) cols[c] = "?";
                    return finish(f, nm, cols, names, nc, nz, nt, S);
                }
            } else {
                imp = BFOpen.virtual(Input.fwd(p), e);
                if (imp == null) err = "Bio-Formats could not open it";
            }
            if (imp == null) {
                f.text = "First image: " + nm + "\n   could not be opened: " + err;
                return f;
            }
            nc = imp.getNChannels();
            nz = imp.getNSlices();
            nt = imp.getNFrames();
            if (imp.getBitDepth() == 24) {
                nc = 3;
                cols = new String[]{"red", "green", "blue"};
            } else cols = Input.colours(imp);
            imp.close();
        }
        return finish(f, nm, cols, names, nc, nz, nt, S);
    }

    private static FirstInfo finish(FirstInfo f, String nm, String[] cols, String[] names, int nc, int nz, int nt, Settings S) {
        f.nc = nc;
        f.nz = nz;
        f.nt = nt;
        f.cols = cols;
        f.names = new String[nc];
        for (int c = 0; c < nc; c++) f.names[c] = names.length > c ? names[c] : "";
        int[] sug = Input.sugFromCols(cols, S.dcSkip);
        f.sugB = sug[0];
        f.sugG = sug[1];
        f.sugR = sug[2];
        f.sugD = sug[3];
        String planes = nz + " Z plane" + (nz > 1 ? "s" : "");
        if (nt > 1) planes = planes + ", " + nt + " time points";
        f.text = "First image: " + nm + "  -  " + nc + " channel(s), " + planes;
        if (f.sugB > 0 || f.sugG > 0 || f.sugR > 0) f.text = f.text + "\n   What each channel is was suggested from its colour: check it.";
        f.ok = true;
        return f;
    }

    /** The colour of each role (nuclei, parasites, marker, DIC) in the first image, for the per-file option. */
    public static String[] roleCols(Settings S, String[] firstCols) {
        String[] rc = {"", "", "", ""};
        int[] roleNum = {S.chBLUE, S.chGREEN, S.chRED, S.chDIC};
        for (int q = 0; q < 4; q++) {
            if (roleNum[q] > 0 && roleNum[q] <= firstCols.length) rc[q] = firstCols[roleNum[q] - 1];
            if (rc[q].equals("?") || rc[q].equals("other")) rc[q] = "";
        }
        return rc;
    }

    /** chCols / chMrg from the settings ("file,blue,green" / "1,0,1"). */
    public static void channelTable(Settings S) {
        String[] c = S.chColSet.isEmpty() ? new String[0] : S.chColSet.split(",", -1);
        String[] m = S.chMrgSet.isEmpty() ? new String[0] : S.chMrgSet.split(",", -1);
        S.chCols = c;
        S.chMrg = new int[m.length];
        for (int i = 0; i < m.length; i++) {
            double v = Macro.parseFloat(m[i]);
            S.chMrg[i] = Double.isNaN(v) ? 0 : (int) Math.round(v);
        }
    }

    /** A whole run from a settings file, no dialog (tests, macros). Returns a summary line. */
    public static String runHeadless(String settingsPath, String inputFolder, String outputFolder, int parallel) {
        Settings S = new Settings();
        S.setRead(settingsPath);
        S.applyGeneral();
        S.applyDetection();
        if (parallel >= 0) S.parallel = parallel;
        channelTable(S);
        S.derive();
        String root = inputFolder;
        if (!root.endsWith(File.separator) && !root.endsWith("/")) root = root + File.separator;
        String inFile = "";
        if (S.inputMode.equals("FILE")) {
            inFile = inputFolder;
            root = new File(inputFolder).getParent() + File.separator;
        }
        List<String> warn = new ArrayList<String>();
        List<Input.Entry> list = Input.collect(S, root, inFile, warn);
        Run r = new Run(S, list, root);
        r.echo = false;
        if (outputFolder != null && !outputFolder.isEmpty()) {
            r.userOutDir = outputFolder;
            if (!S.tiffMode) r.droppedOut = Input.dropUnder(list, outputFolder, root);
        }
        // image outputs rebuild a view from one multi-channel file: none with one TIFF per channel (the choices
        // stay in the settings, as in the macro version)
        if (S.tiffMode && (S.doHyper || S.doProject || S.doMergeStack || S.doMergeProj)) r.skippedImgOut = true;
        if (!S.tiffMode && !list.isEmpty()) {
            FirstInfo fi = firstImageInfo(S, list);
            r.firstCols = fi.cols;
            r.roleCols = roleCols(S, fi.cols);
        }
        r.run();
        return list.size() + " image(s), " + r.summaryNames.size() + " row(s), " + r.threadsUsed + " thread(s)";
    }
}
