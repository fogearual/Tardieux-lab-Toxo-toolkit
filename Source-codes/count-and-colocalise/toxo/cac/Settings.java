package toxo.cac;

import ij.IJ;
import ij.Prefs;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Every setting of Count and Colocalise, with the defaults validated on the invasion assay, and the settings files
 * ("key=value" per line) read and written exactly as by the macro version 23, so that files of either version can
 * start a run of the other.
 */
public final class Settings implements Cloneable {

    public static final String VERSION = "24";
    public static final String SET_FILE = "Count_and_Colocalise_settings.txt";
    public static final String OLD_SET_FILE = "Parasite_Quantification_Tool_settings.txt";

    public static final String[] THR_METHODS = {"Default", "Huang", "Intermodes", "IsoData", "Li", "MaxEntropy",
            "Mean", "MinError", "Minimum", "Moments", "Otsu", "Percentile", "RenyiEntropy", "Shanbhag", "Triangle", "Yen"};
    public static final String[] TASK_MODES = {"Count objects (QC stacks, results)",
            "Make images (Z projections, merges, TIFF)", "Both: count objects and make images"};
    public static final String[] IN_MODES = {"A folder (experiments and/or conditions in subfolders)",
            "A single folder of images", "A single image file"};
    public static final String[] IN_KEYS = {"AUTO", "FOLDER", "FILE"};
    public static final String[] OPEN_WAYS = {"Automatic: ImageJ for .nd and .tif, Bio-Formats for the rest",
            "Bio-Formats Importer for every file", "Bio-Formats simple opener for every file",
            "ImageJ only (TIFF; .nd read without Bio-Formats)"};
    public static final String[] OPEN_KEYS = {"auto", "importer", "ext", "imagej"};
    public static final String[] MEASURE_MODES = {"Single-labelled parasites (parasites minus double-labelled)",
            "All parasites", "Double-labelled parasites", "Double-labelled parasites, % of all parasites"};
    public static final String[] EDGE_MODES = {"Count them all", "Leave out nuclei touching the border",
            "Leave out nuclei more than half outside"};
    public static final String[] LOW_MODES = {"Left out of the counts", "Counted only if the target needs it"};
    public static final String[] DBL_MODES = {"Overlap of the parasite with the marker",
            "Marker objects inside the parasite", "The parasite inside a marker object"};
    public static final String[] POS_MODES = {"Anywhere on the parasite", "In its centre", "At an extremity"};
    public static final String[] POLE_REFS = {"The parasite nucleus (nuclear stain)", "A pole marker",
            "The nucleus and a pole marker"};
    public static final String[] POLE_SIDES = {"The basal pole", "The apical pole (e.g. extruded conoid)"};

    // ------------------------------------------------------------------ task, input, outputs
    public int taskMode = 0;
    public boolean doCount, doQC, doWorkbook, doHistExp, doHistPooled;
    public boolean doHyper, doProject, doMergeStack, doMergeProj;
    public String inputMode = "AUTO";
    public boolean tiffMode = false;
    public String extList = "czi nd";
    public boolean splitSeries = false;
    public String openMethod = "auto";
    public boolean useBatch = true;
    public boolean pixelForce = false;
    public double pixelSize = 0.11;
    public boolean pickOutDir = false;
    public int parallel = 0;                      // images processed at once, 0 = automatic (Java version)

    // ------------------------------------------------------------------ channels
    public int chBLUE = 4, chGREEN = 3, chRED = 2, chDIC = 1, chPOLE = 0;
    public String kwBlue = "DAPI", kwGreen = "FITC", kwRed = "Texas", dataSub = "data", kwPole = "";
    public boolean autoDetectChannels = false;
    public boolean countNuclei, countGreen, countDouble;
    public int measureMode = 0;
    public boolean mergeBlue = true, mergeGreen = true, mergeRed = true, mergeDIC = false;
    public String chColSet = "", chMrgSet = "";

    // ------------------------------------------------------------------ detection
    public double nucleiThresholdFactor = 1.0, nucleiCapF = 5, nucleiMinArea = 105;
    public boolean splitBigNuclei = true;
    public double nucleiDoubletFactor = 1.7, nucleiMaxSplit = 4, nucleiFragFactor = 0;
    public int nucleiEdgeMode = 0;
    public double nucleiBgRolling = 80, nucleiBlurSigma = 1.5;
    public String nucleiThresholdMethod = "Triangle";
    public boolean nucleiWatershed = true;
    public double nucleiWsTol = 0.5;
    public String nucleiCirc = "0.20-1.00";
    public final String nucleiMaxArea = "Infinity";
    public final double nucleiMultiply = 2.5;

    public double greenThresholdFactor = 4.25, greenCapF = 5, greenMinArea = 7.25, greenMaxArea = 62.5;
    public boolean greenWatershed = true;
    public double greenWsTol = 0.5;
    public boolean splitBigParasites = false;
    public double greenDoubletFactor = 1.7, greenMaxSplit = 8;
    public double greenBgRolling = 30, greenBlurSigma = 0.8;
    public String greenThresholdMethod = "Triangle";
    public String greenCirc = "0.10-1.00";
    public final double greenMultiply = 2.0;

    public double redThresholdFactor = 2.8, redCapF = 5, minOverlapPercent = 92;
    public double redBgRolling = 30, redBlurSigma = 0.8;
    public String redThresholdMethod = "Triangle";
    public final double redMultiply = 2.0;
    public final double enhanceSaturated = 0.10;

    // double labelling by objects, places, poles
    public int dblMode = 0;
    public double objMinArea = 0.1, objMaxArea = 10, objTol = 0.5, objMinN = 1;
    public boolean objWatershed = true;
    public double encMinArea = 20, encMaxArea = 5000;
    public int posMode = 0;
    public double centrePct = 30, endPct = 20;
    public boolean doPoles = false;
    public int poleRef = 0, poleSide = 0;
    public double pnMinArea = 0.8, pnMaxArea = 15, poleDeadPct = 5;
    public double pmThresholdFactor = 2.0, pmMinArea = 0.05, pmMaxArea = 5;
    public final double pmCapF = 5, pmMultiply = 2.0, pmBgRolling = 30, pmBlurSigma = 0.8;
    public final String pmThresholdMethod = "Triangle";

    // stop early, images with few objects
    public double stopTarget = 0;
    public String stopOn = "cells";
    public double minCellsImg = 0, minParImg = 0;
    public int lowMode = 0;

    // QC display and panels
    public double qcGainBlue = 1, qcGainGreen = 1, qcGainRed = 1;
    public boolean qcPanN = true, qcPanP = true, qcPanR = true, qcPanRG = true, qcPanPol = true, qcPanAll = true,
            qcRaw = false;
    public final double nearFrac = 0.5;

    // channel colours / merges of the images made (one entry per channel of the table)
    public String[] chCols = new String[0];
    public int[] chMrg = new int[0];

    // ------------------------------------------------------------------ derived, set before a run
    public boolean dblPolesUsed, poleNeedNuc, poleNeedMk;
    public int dcSkip;
    public int measureUse;
    public boolean normalizeToCells;
    public String measureLabel = "", measureNote = "";

    @Override
    public Settings clone() {
        try {
            Settings s = (Settings) super.clone();
            s.chCols = chCols.clone();
            s.chMrg = chMrg.clone();
            return s;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    // ================================================================== settings files
    private final List<String> setKeys = new ArrayList<String>();
    private final List<String> setVals = new ArrayList<String>();

    /** Reads a settings file into the key / value lists. Returns how many settings. */
    public int setRead(String path) {
        setKeys.clear();
        setVals.clear();
        if (path == null || path.isEmpty() || !new File(path).exists()) return 0;
        String txt;
        try {
            txt = new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return 0;
        }
        for (String ln0 : txt.split("\n", -1)) {
            String ln = ln0.replace("\r", "");
            if (ln.startsWith("#")) continue;
            int e = ln.indexOf('=');
            if (e <= 0) continue;
            setKeys.add(ln.substring(0, e));
            setVals.add(ln.substring(e + 1));
        }
        return setKeys.size();
    }

    public int setCount() { return setKeys.size(); }

    String sStr(String key, String def) {
        for (int q = 0; q < setKeys.size(); q++) if (setKeys.get(q).equals(key)) return setVals.get(q);
        return def;
    }

    double sNum(String key, double def) {
        String s = sStr(key, "");
        if (s.isEmpty()) return def;
        s = s.replace(",", ".");
        double v = Macro.parseFloat(s);
        if (Double.isNaN(v)) return def;
        return v;
    }

    boolean sBool(String key, boolean def) {
        return sNum(key, def ? 1 : 0) != 0;
    }

    /** The first dialog: what to do, input, pixel size. */
    public int applyGeneral() {
        if (setKeys.isEmpty()) return 0;
        doCount = sBool("doCount", doCount);
        doQC = sBool("doQC", doQC);
        doWorkbook = sBool("doWorkbook", doWorkbook);
        doHistExp = sBool("doHistExp", doHistExp);
        doHistPooled = sBool("doHistPooled", doHistPooled);
        doHyper = sBool("doHyper", doHyper);
        doProject = sBool("doProject", doProject);
        doMergeStack = sBool("doMergeStack", doMergeStack);
        doMergeProj = sBool("doMergeProj", doMergeProj);
        inputMode = sStr("inputMode", inputMode);
        tiffMode = sBool("tiffMode", tiffMode);
        extList = sStr("extList", extList);
        splitSeries = sBool("splitSeries", splitSeries);
        openMethod = sStr("openMethod", openMethod);
        useBatch = sBool("useBatch", useBatch);
        pixelForce = sBool("pixelForce", pixelForce);
        pixelSize = sNum("pixelSize", pixelSize);
        pickOutDir = sBool("pickOutDir", pickOutDir);
        parallel = (int) Math.round(sNum("parallel", parallel));
        double tm = sNum("taskMode", -1);
        if (tm < 0) {
            boolean imgO = doHyper || doProject || doMergeStack || doMergeProj;
            tm = 0;
            if (imgO && doCount) tm = 2;
            if (imgO && !doCount) tm = 1;
        }
        taskMode = (int) Math.round(tm);
        if (taskMode < 0 || taskMode > 2) taskMode = 0;
        if (indexOf(IN_KEYS, inputMode) < 0) inputMode = "AUTO";
        if (indexOf(OPEN_KEYS, openMethod) < 0) openMethod = "auto";
        if (pixelSize <= 0) pixelSize = 0.11;
        if (parallel < 0) parallel = 0;
        return 1;
    }

    /** Everything else: channels, objects, detection, Calibrate, QC display. */
    public int applyDetection() {
        if (setKeys.isEmpty()) return 0;
        chBLUE = (int) sNum("chBLUE", chBLUE);
        chGREEN = (int) sNum("chGREEN", chGREEN);
        chRED = (int) sNum("chRED", chRED);
        chDIC = (int) sNum("chDIC", chDIC);
        double[] chRaw = {sNum("chBLUE", chBLUE), sNum("chGREEN", chGREEN), sNum("chRED", chRED), sNum("chDIC", chDIC),
                sNum("chPOLE", chPOLE)};
        kwBlue = sStr("kwBlue", kwBlue);
        kwGreen = sStr("kwGreen", kwGreen);
        kwRed = sStr("kwRed", kwRed);
        dataSub = sStr("dataSub", dataSub);
        autoDetectChannels = sBool("autoDetectChannels", autoDetectChannels);
        countNuclei = sBool("countNuclei", countNuclei);
        countGreen = sBool("countGreen", countGreen);
        countDouble = sBool("countDouble", countDouble);
        double mm = sNum("measureMode", measureMode);
        mergeBlue = sBool("mergeBlue", mergeBlue);
        mergeGreen = sBool("mergeGreen", mergeGreen);
        mergeRed = sBool("mergeRed", mergeRed);
        mergeDIC = sBool("mergeDIC", mergeDIC);
        chColSet = sStr("chColours", "");
        chMrgSet = sStr("chMerges", "");

        nucleiThresholdFactor = sNum("nucleiThresholdFactor", nucleiThresholdFactor);
        nucleiCapF = sNum("nucleiCapF", nucleiCapF);
        nucleiMinArea = sNum("nucleiMinArea", nucleiMinArea);
        splitBigNuclei = sBool("splitBigNuclei", splitBigNuclei);
        nucleiDoubletFactor = sNum("nucleiDoubletFactor", nucleiDoubletFactor);
        nucleiMaxSplit = sNum("nucleiMaxSplit", nucleiMaxSplit);
        nucleiFragFactor = sNum("nucleiFragFactor", nucleiFragFactor);
        double em = sNum("nucleiEdgeMode", nucleiEdgeMode);
        nucleiBgRolling = sNum("nucleiBgRolling", nucleiBgRolling);
        nucleiBlurSigma = sNum("nucleiBlurSigma", nucleiBlurSigma);
        nucleiThresholdMethod = sStr("nucleiThresholdMethod", nucleiThresholdMethod);
        nucleiWatershed = sBool("nucleiWatershed", nucleiWatershed);
        nucleiWsTol = sNum("nucleiWsTol", nucleiWsTol);
        nucleiCirc = sStr("nucleiCirc", nucleiCirc);

        greenThresholdFactor = sNum("greenThresholdFactor", greenThresholdFactor);
        greenCapF = sNum("greenCapF", greenCapF);
        greenMinArea = sNum("greenMinArea", greenMinArea);
        greenMaxArea = sNum("greenMaxArea", greenMaxArea);
        greenWatershed = sBool("greenWatershed", greenWatershed);
        greenWsTol = sNum("greenWsTol", greenWsTol);
        splitBigParasites = sBool("splitBigParasites", splitBigParasites);
        greenDoubletFactor = sNum("greenDoubletFactor", greenDoubletFactor);
        greenMaxSplit = sNum("greenMaxSplit", greenMaxSplit);
        greenBgRolling = sNum("greenBgRolling", greenBgRolling);
        greenBlurSigma = sNum("greenBlurSigma", greenBlurSigma);
        greenThresholdMethod = sStr("greenThresholdMethod", greenThresholdMethod);
        greenCirc = sStr("greenCirc", greenCirc);

        redThresholdFactor = sNum("redThresholdFactor", redThresholdFactor);
        redCapF = sNum("redCapF", redCapF);
        minOverlapPercent = sNum("minOverlapPercent", minOverlapPercent);
        redBgRolling = sNum("redBgRolling", redBgRolling);
        redBlurSigma = sNum("redBlurSigma", redBlurSigma);
        redThresholdMethod = sStr("redThresholdMethod", redThresholdMethod);

        double dm = sNum("dblMode", dblMode);
        objMinArea = sNum("objMinArea", objMinArea);
        objMaxArea = sNum("objMaxArea", objMaxArea);
        objTol = sNum("objTol", objTol);
        objMinN = sNum("objMinN", objMinN);
        objWatershed = sBool("objWatershed", objWatershed);
        encMinArea = sNum("encMinArea", encMinArea);
        encMaxArea = sNum("encMaxArea", encMaxArea);
        double pm = sNum("posMode", posMode);
        centrePct = sNum("centrePct", centrePct);
        endPct = sNum("endPct", endPct);
        doPoles = sBool("doPoles", doPoles);
        double pr = sNum("poleRef", poleRef);
        double ps = sNum("poleSide", poleSide);
        kwPole = sStr("kwPole", kwPole);
        pnMinArea = sNum("pnMinArea", pnMinArea);
        pnMaxArea = sNum("pnMaxArea", pnMaxArea);
        poleDeadPct = sNum("poleDeadPct", poleDeadPct);
        pmThresholdFactor = sNum("pmThresholdFactor", pmThresholdFactor);
        pmMinArea = sNum("pmMinArea", pmMinArea);
        pmMaxArea = sNum("pmMaxArea", pmMaxArea);

        stopTarget = sNum("stopTarget", stopTarget);
        stopOn = sStr("stopOn", stopOn);
        minCellsImg = sNum("minCellsImg", minCellsImg);
        minParImg = sNum("minParImg", minParImg);
        double lm = sNum("lowMode", lowMode);
        qcGainBlue = sNum("qcGainBlue", qcGainBlue);
        qcGainGreen = sNum("qcGainGreen", qcGainGreen);
        qcGainRed = sNum("qcGainRed", qcGainRed);
        qcPanN = sBool("qcPanN", qcPanN);
        qcPanP = sBool("qcPanP", qcPanP);
        qcPanR = sBool("qcPanR", qcPanR);
        qcPanRG = sBool("qcPanRG", qcPanRG);
        qcPanPol = sBool("qcPanPol", qcPanPol);
        qcPanAll = sBool("qcPanAll", qcPanAll);
        qcRaw = sBool("qcRaw", qcRaw);

        // values a dialog would reject: back to the defaults
        chBLUE = chanOk(chRaw[0]);
        chGREEN = chanOk(chRaw[1]);
        chRED = chanOk(chRaw[2]);
        chDIC = chanOk(chRaw[3]);
        chPOLE = chanOk(chRaw[4]);
        dblMode = Double.isNaN(dm) || dm < 0 || dm > 2 ? 0 : (int) Math.round(dm);
        posMode = Double.isNaN(pm) || pm < 0 || pm > 2 ? 0 : (int) Math.round(pm);
        poleRef = Double.isNaN(pr) || pr < 0 || pr > 2 ? 0 : (int) Math.round(pr);
        poleSide = Double.isNaN(ps) || ps < 0 || ps > 1 ? 0 : (int) Math.round(ps);
        objSettingsOk();
        measureMode = (int) Math.round(mm);
        if (measureMode < 0 || measureMode >= MEASURE_MODES.length) measureMode = 0;
        nucleiEdgeMode = (int) Math.round(em);
        if (nucleiEdgeMode < 0 || nucleiEdgeMode > 2) nucleiEdgeMode = 0;
        lowMode = (int) Math.round(lm);
        if (lowMode < 0 || lowMode > 1) lowMode = 0;
        if (!stopOn.equals("parasites")) stopOn = "cells";
        if (indexOf(THR_METHODS, nucleiThresholdMethod) < 0) nucleiThresholdMethod = "Triangle";
        if (indexOf(THR_METHODS, greenThresholdMethod) < 0) greenThresholdMethod = "Triangle";
        if (indexOf(THR_METHODS, redThresholdMethod) < 0) redThresholdMethod = "Triangle";
        nucleiCirc = circStr(circMin(nucleiCirc));
        greenCirc = circStr(circMin(greenCirc));
        nucleiWsTol = wsTolOk(nucleiWsTol);
        greenWsTol = wsTolOk(greenWsTol);
        qcGainBlue = gainOk(qcGainBlue);
        qcGainGreen = gainOk(qcGainGreen);
        qcGainRed = gainOk(qcGainRed);
        nucleiCapF = capOk(nucleiCapF);
        greenCapF = capOk(greenCapF);
        redCapF = capOk(redCapF);
        parClusterOk();
        if (nucleiBgRolling < 0) nucleiBgRolling = 0;
        if (greenBgRolling < 0) greenBgRolling = 0;
        if (redBgRolling < 0) redBgRolling = 0;
        if (nucleiBlurSigma < 0) nucleiBlurSigma = 0;
        if (greenBlurSigma < 0) greenBlurSigma = 0;
        if (redBlurSigma < 0) redBlurSigma = 0;
        if (nucleiFragFactor < 0) nucleiFragFactor = 0;
        if (nucleiFragFactor > 0.9) nucleiFragFactor = 0.9;
        if (nucleiMaxSplit < 2) nucleiMaxSplit = 2;
        if (nucleiDoubletFactor < 1.1) nucleiDoubletFactor = 1.1;
        if (stopTarget < 0) stopTarget = 0;
        if (minCellsImg < 0) minCellsImg = 0;
        if (minParImg < 0) minParImg = 0;
        return 1;
    }

    /** Every setting of the run, for the next run or a later one (same keys and format as the macro). */
    public String settingsText() {
        StringBuilder s = new StringBuilder();
        s.append("# Count and Colocalise v").append(VERSION).append(" - settings of a run\n");
        s.append("# Read back by the first dialog: 'Detection settings to start from'.\n");
        s.append("taskMode=").append(taskMode).append('\n');
        kv(s, "doCount", b(doCount)); kv(s, "doQC", b(doQC)); kv(s, "doWorkbook", b(doWorkbook));
        kv(s, "doHistExp", b(doHistExp)); kv(s, "doHistPooled", b(doHistPooled));
        kv(s, "doHyper", b(doHyper)); kv(s, "doProject", b(doProject));
        kv(s, "doMergeStack", b(doMergeStack)); kv(s, "doMergeProj", b(doMergeProj));
        kv(s, "inputMode", inputMode); kv(s, "tiffMode", b(tiffMode)); kv(s, "extList", extList);
        kv(s, "splitSeries", b(splitSeries)); kv(s, "openMethod", openMethod);
        kv(s, "useBatch", b(useBatch)); kv(s, "pixelForce", b(pixelForce));
        kv(s, "pixelSize", Macro.d2s(pixelSize, 6)); kv(s, "pickOutDir", b(pickOutDir));
        kv(s, "parallel", "" + parallel);
        kv(s, "chBLUE", "" + chBLUE); kv(s, "chGREEN", "" + chGREEN); kv(s, "chRED", "" + chRED); kv(s, "chDIC", "" + chDIC);
        kv(s, "kwBlue", kwBlue); kv(s, "kwGreen", kwGreen); kv(s, "kwRed", kwRed); kv(s, "dataSub", dataSub);
        kv(s, "autoDetectChannels", b(autoDetectChannels));
        kv(s, "countNuclei", b(countNuclei)); kv(s, "countGreen", b(countGreen)); kv(s, "countDouble", b(countDouble));
        kv(s, "measureMode", "" + measureMode);
        StringBuilder cc = new StringBuilder(), cm = new StringBuilder();
        for (int i = 0; i < chCols.length; i++) cc.append(i > 0 ? "," : "").append(chCols[i]);
        for (int i = 0; i < chMrg.length; i++) cm.append(i > 0 ? "," : "").append(chMrg[i]);
        kv(s, "chColours", cc.toString()); kv(s, "chMerges", cm.toString());
        kv(s, "nucleiThresholdFactor", d4(nucleiThresholdFactor)); kv(s, "nucleiCapF", d4(nucleiCapF));
        kv(s, "nucleiMinArea", d4(nucleiMinArea)); kv(s, "splitBigNuclei", b(splitBigNuclei));
        kv(s, "nucleiDoubletFactor", d4(nucleiDoubletFactor)); kv(s, "nucleiMaxSplit", Macro.n2s(nucleiMaxSplit));
        kv(s, "nucleiFragFactor", d4(nucleiFragFactor)); kv(s, "nucleiEdgeMode", "" + nucleiEdgeMode);
        kv(s, "nucleiBgRolling", d4(nucleiBgRolling)); kv(s, "nucleiBlurSigma", d4(nucleiBlurSigma));
        kv(s, "nucleiThresholdMethod", nucleiThresholdMethod); kv(s, "nucleiWatershed", b(nucleiWatershed));
        kv(s, "nucleiWsTol", d4(nucleiWsTol)); kv(s, "nucleiCirc", nucleiCirc);
        kv(s, "greenThresholdFactor", d4(greenThresholdFactor)); kv(s, "greenCapF", d4(greenCapF));
        kv(s, "greenMinArea", d4(greenMinArea)); kv(s, "greenMaxArea", d4(greenMaxArea));
        kv(s, "greenWatershed", b(greenWatershed)); kv(s, "greenWsTol", d4(greenWsTol));
        kv(s, "splitBigParasites", b(splitBigParasites)); kv(s, "greenDoubletFactor", d4(greenDoubletFactor));
        kv(s, "greenMaxSplit", Macro.n2s(greenMaxSplit)); kv(s, "greenBgRolling", d4(greenBgRolling));
        kv(s, "greenBlurSigma", d4(greenBlurSigma)); kv(s, "greenThresholdMethod", greenThresholdMethod);
        kv(s, "greenCirc", greenCirc);
        kv(s, "redThresholdFactor", d4(redThresholdFactor)); kv(s, "redCapF", d4(redCapF));
        kv(s, "minOverlapPercent", d4(minOverlapPercent)); kv(s, "redBgRolling", d4(redBgRolling));
        kv(s, "redBlurSigma", d4(redBlurSigma)); kv(s, "redThresholdMethod", redThresholdMethod);
        kv(s, "dblMode", "" + dblMode); kv(s, "objMinArea", d4(objMinArea)); kv(s, "objMaxArea", d4(objMaxArea));
        kv(s, "objTol", d4(objTol)); kv(s, "objMinN", Macro.n2s(objMinN)); kv(s, "objWatershed", b(objWatershed));
        kv(s, "encMinArea", d4(encMinArea)); kv(s, "encMaxArea", d4(encMaxArea)); kv(s, "posMode", "" + posMode);
        kv(s, "centrePct", d4(centrePct)); kv(s, "endPct", d4(endPct));
        kv(s, "doPoles", b(doPoles)); kv(s, "poleRef", "" + poleRef); kv(s, "poleSide", "" + poleSide);
        kv(s, "chPOLE", "" + chPOLE); kv(s, "kwPole", kwPole); kv(s, "pnMinArea", d4(pnMinArea));
        kv(s, "pnMaxArea", d4(pnMaxArea)); kv(s, "poleDeadPct", d4(poleDeadPct));
        kv(s, "pmThresholdFactor", d4(pmThresholdFactor)); kv(s, "pmMinArea", d4(pmMinArea)); kv(s, "pmMaxArea", d4(pmMaxArea));
        kv(s, "stopTarget", Macro.n2s(stopTarget)); kv(s, "stopOn", stopOn);
        kv(s, "minCellsImg", Macro.n2s(minCellsImg)); kv(s, "minParImg", Macro.n2s(minParImg)); kv(s, "lowMode", "" + lowMode);
        kv(s, "qcGainBlue", d4(qcGainBlue)); kv(s, "qcGainGreen", d4(qcGainGreen)); kv(s, "qcGainRed", d4(qcGainRed));
        kv(s, "qcPanN", b(qcPanN)); kv(s, "qcPanP", b(qcPanP)); kv(s, "qcPanR", b(qcPanR)); kv(s, "qcPanRG", b(qcPanRG));
        kv(s, "qcPanPol", b(qcPanPol)); kv(s, "qcPanAll", b(qcPanAll)); kv(s, "qcRaw", b(qcRaw));
        return s.toString();
    }

    public boolean saveSettings(String path) {
        try {
            Files.write(new File(path).toPath(), settingsText().getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void kv(StringBuilder s, String k, String v) { s.append(k).append('=').append(v).append('\n'); }

    private static String b(boolean v) { return v ? "1" : "0"; }

    private static String d4(double v) { return Macro.d2s(v, 4); }

    /** Settings file in the ImageJ preferences folder ("" when there is no such folder). */
    public static String lastSetPath() {
        String d = Prefs.getPrefsDir();
        if (d == null || d.isEmpty()) return "";
        File f = new File(d);
        if (!f.isDirectory()) f.mkdirs();
        if (!f.isDirectory()) return "";
        if (!d.endsWith(File.separator)) d = d + File.separator;
        return d + SET_FILE;
    }

    // ================================================================== values as used
    public static int chanOk(double v) {
        if (Double.isNaN(v) || v < 0) return 0;
        return (int) Math.round(v);
    }

    /** Bright cap as used: 0 (off) when not a positive number, at least 1.5 x the median otherwise. */
    public static double capOk(double v) {
        if (Double.isNaN(v)) return 0;
        if (v <= 0) return 0;
        if (v < 1.5) return 1.5;
        return v;
    }

    /** "0.20-1.00" -> 0.2 */
    public static double circMin(String s) {
        int d = s.indexOf('-');
        if (d < 0) return 0;
        double v = Macro.parseFloat(s.substring(0, d));
        if (Double.isNaN(v)) return 0;
        return v;
    }

    /** 0.2 -> "0.20-1.00" */
    public static String circStr(double v) {
        if (v < 0) v = 0;
        if (v > 1) v = 1;
        return Macro.num2(v) + "-1.00";
    }

    public static double wsTolOk(double v) {
        if (Double.isNaN(v)) return 0.5;
        if (v <= 0.5) return 0.5;
        if (v < 1) return 1;
        if (v > 100) return 100;
        return v;
    }

    public static double gainOk(double v) {
        if (Double.isNaN(v)) return 1;
        if (v <= 0) return 1;
        if (v < 0.05) return 0.05;
        if (v > 50) return 50;
        return v;
    }

    public void parClusterOk() {
        if (Double.isNaN(greenDoubletFactor) || greenDoubletFactor < 1.1) greenDoubletFactor = 1.1;
        if (Double.isNaN(greenMaxSplit) || greenMaxSplit < 2) greenMaxSplit = 2;
        greenMaxSplit = Math.round(greenMaxSplit);
        if (greenMaxSplit > 50) greenMaxSplit = 50;
    }

    public void objSettingsOk() {
        if (dblMode < 0 || dblMode > 2) dblMode = 0;
        if (Double.isNaN(objMinArea) || objMinArea < 0) objMinArea = 0;
        if (Double.isNaN(objMaxArea) || objMaxArea < 0) objMaxArea = 10;
        if (objMaxArea < objMinArea) { double sw = objMinArea; objMinArea = objMaxArea; objMaxArea = sw; }
        if (Double.isNaN(objTol) || objTol < 0) objTol = 0;
        if (objTol > 10) objTol = 10;
        if (Double.isNaN(objMinN) || objMinN < 1) objMinN = 1;
        objMinN = Math.round(objMinN);
        if (Double.isNaN(encMinArea) || encMinArea < 0) encMinArea = 0;
        if (Double.isNaN(encMaxArea) || encMaxArea < 0) encMaxArea = 5000;
        if (encMaxArea < encMinArea) { double sw = encMinArea; encMinArea = encMaxArea; encMaxArea = sw; }
        if (posMode < 0 || posMode > 2) posMode = 0;
        if (Double.isNaN(centrePct) || centrePct < 1) centrePct = 1;
        if (centrePct > 100) centrePct = 100;
        if (Double.isNaN(endPct) || endPct < 1) endPct = 1;
        if (endPct > 50) endPct = 50;
        if (poleRef < 0 || poleRef > 2) poleRef = 0;
        if (poleSide < 0 || poleSide > 1) poleSide = 0;
        if (Double.isNaN(pnMinArea) || pnMinArea < 0) pnMinArea = 0;
        if (Double.isNaN(pnMaxArea) || pnMaxArea < 0) pnMaxArea = 15;
        if (pnMaxArea < pnMinArea) { double sw = pnMinArea; pnMinArea = pnMaxArea; pnMaxArea = sw; }
        if (Double.isNaN(pmMinArea) || pmMinArea < 0) pmMinArea = 0;
        if (Double.isNaN(pmMaxArea) || pmMaxArea < 0) pmMaxArea = 5;
        if (pmMaxArea < pmMinArea) { double sw = pmMinArea; pmMinArea = pmMaxArea; pmMaxArea = sw; }
        if (Double.isNaN(pmThresholdFactor) || pmThresholdFactor <= 0) pmThresholdFactor = 2;
        if (Double.isNaN(poleDeadPct) || poleDeadPct < 0) poleDeadPct = 0;
        if (poleDeadPct > 45) poleDeadPct = 45;
    }

    // ================================================================== reported measure
    /** The measure in force (measureUse), its label, and whether it is per 100 host cells. */
    public int setMeasure() {
        measureNote = "";
        measureUse = measureMode;
        if (!countDouble && measureUse != 1) {
            if (measureUse != 0)
                measureNote = "NOTE: double labelling is not counted, so the measure is all parasites, not '"
                        + MEASURE_MODES[measureMode] + "'.";
            measureUse = 1;
        }
        if (!countGreen) {
            measureUse = 4;
            measureNote = "";
        }
        normalizeToCells = countNuclei && measureUse <= 2;
        if (measureUse == 0) measureLabel = "Single-labelled parasites";
        if (measureUse == 1) measureLabel = "Parasites";
        if (measureUse == 2) measureLabel = "Double-labelled parasites";
        if (measureUse <= 2) measureLabel = measureLabel + (normalizeToCells ? " / 100 cells" : " (count)");
        if (measureUse == 3) measureLabel = "Double-labelled, % of parasites";
        if (measureUse == 4) measureLabel = "Host cells (count)";
        return measureUse;
    }

    public double measImg(double cells, double par, double dbl) {
        if (measureUse == 3) return par > 0 ? dbl * 100.0 / par : 0;
        if (measureUse == 4) return cells;
        double num = par;
        if (measureUse == 0) num = par - dbl;
        if (measureUse == 2) num = dbl;
        if (normalizeToCells) return cells > 0 ? num * 100.0 / cells : 0;
        return num;
    }

    public double measBar(double cells, double par, double dbl, double nImg) {
        double v = measImg(cells, par, dbl);
        if (normalizeToCells || measureUse == 3) return v;
        return nImg > 0 ? v / nImg : 0;
    }

    /** The pole options in force (from the dialogs). */
    public void derive() {
        dblPolesUsed = doCount && countDouble && doPoles && dblMode != 2;
        poleNeedNuc = dblPolesUsed && (poleRef == 0 || poleRef == 2);
        poleNeedMk = dblPolesUsed && (poleRef == 1 || poleRef == 2);
        dcSkip = poleNeedMk && !tiffMode ? chPOLE : 0;
    }

    public static int indexOf(String[] a, String v) {
        for (int i = 0; i < a.length; i++) if (a[i].equals(v)) return i;
        return -1;
    }

    static String lower(String s) { return s.toLowerCase(Locale.US); }

    public static void log(String s) { IJ.log(s); }
}
