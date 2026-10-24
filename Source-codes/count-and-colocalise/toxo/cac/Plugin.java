package toxo.cac;

import ij.IJ;
import ij.gui.GenericDialog;
import ij.io.OpenDialog;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * The dialogs of Count and Colocalise, then the run: what to do, the input and outputs, what to count, the
 * channels, the marker objects and poles, the detection settings (with Calibrate). The same dialogs as the macro
 * version, with the same fields, in the same order, and the same names in a macro or the Recorder; only "Batch
 * mode" (meaningless here: no image is ever displayed) became the number of images processed at once.
 */
public final class Plugin {

    /** Tests only: called with the name of each dialog just before it is shown (it can set its macro options). */
    public interface Hook {
        void before(String dialog);
    }

    public static volatile Hook hook;

    static void hook(String name) {
        Hook h = hook;
        if (h != null) h.before(name);
    }

    static final String TITLE = "Count and Colocalise";

    final Settings S = new Settings();

    // the input (pickInput / collectImages of the macro)
    String rootFolder = "", inFile = "";
    boolean inputPicked = false, imagesCollected = false;
    List<Input.Entry> entries = new ArrayList<Input.Entry>();
    String userOutDir = "";
    int droppedOut = 0;
    boolean skippedImgOut = false;
    boolean doConvert = false, doMerge = false;
    boolean calibShown = false;        // the note on the pixel size of the first dialog, said once

    // the first image (firstImageInfo)
    String firstInfo = "";
    int firstB, firstG, firstR, firstD, firstNC;
    String[] firstCols = new String[0], firstNames = new String[0];

    /** The whole tool: dialogs, then the run. */
    public static void runTool() {
        new Plugin().run();
    }

    void run() {
        // ---------------- remembered settings ----------------
        String prefsDir = ij.Prefs.getPrefsDir();
        String lastSetPath = "";
        if (prefsDir != null && !prefsDir.isEmpty() && !new File(prefsDir).isDirectory()) new File(prefsDir).mkdirs();
        if (prefsDir != null && !prefsDir.isEmpty() && new File(prefsDir).isDirectory()) {
            if (!prefsDir.endsWith(File.separator)) prefsDir = prefsDir + File.separator;
            lastSetPath = prefsDir + Settings.SET_FILE;
        }
        int nSet = 0;
        String readSetPath = lastSetPath;
        if (!lastSetPath.isEmpty() && !new File(lastSetPath).exists() && new File(prefsDir + Settings.OLD_SET_FILE).exists())
            readSetPath = prefsDir + Settings.OLD_SET_FILE;
        if (!readSetPath.isEmpty()) nSet = S.setRead(readSetPath);
        S.applyGeneral();

        // ---------------- dialog 1: what to do ----------------
        String[] setSrcModes = {"Those of my last run", "The validated defaults",
                "A settings file I choose (Settings.txt of a run)"};
        String setSrcPick = setSrcModes[0];
        if (nSet == 0) setSrcPick = setSrcModes[1];
        if (S.taskMode < 0 || S.taskMode > 2) S.taskMode = 0;

        hook("d1");
        GenericDialog gd = new GenericDialog(TITLE + "  v" + Settings.VERSION);
        gd.addMessage("What do you want to do?");
        gd.addChoice("I want to:", Settings.TASK_MODES, Settings.TASK_MODES[S.taskMode]);
        gd.addChoice("Detection settings to start from:", setSrcModes, setSrcPick);
        gd.addHelp("<html><h3>Count and Colocalise v" + Settings.VERSION + "</h3>"
                + "<p><b>Count objects</b>: nuclei (host cells), parasites and double-labelled parasites, "
                + "chosen in the next dialogs, with QC stacks showing on every image what was counted, "
                + "CSV files, a workbook and histograms.</p>"
                + "<p><b>Make images</b>: hyperstack TIFF, maximum Z projection and composite merges of "
                + "each file - nothing is counted.</p>"
                + "<p><b>Both</b>: the two, in one run.</p>"
                + "<p><b>Settings</b>: every run remembers its settings for the next one, and writes "
                + "Settings.txt next to its results; the detection settings of any of them can be the "
                + "starting point of a run.</p>"
                + "<p>Several images are analysed at once (second dialog): the results are those of a run "
                + "image after image.</p></html>");
        gd.showDialog();
        if (gd.wasCanceled()) return;
        String tChoice = gd.getNextChoice();
        for (int q = 0; q < Settings.TASK_MODES.length; q++) if (tChoice.equals(Settings.TASK_MODES[q])) S.taskMode = q;
        String setSrc = gd.getNextChoice();
        boolean doCountT = S.taskMode == 0 || S.taskMode == 2;
        boolean doImgT = S.taskMode == 1 || S.taskMode == 2;

        // detection settings: last run (already read), validated defaults, or a file
        if (setSrc.equals(setSrcModes[2])) {
            hook("setfile");
            OpenDialog od = new OpenDialog("Choose a settings file (Settings.txt in the results of a run)", null);
            if (od.getFileName() == null) return;
            String sp = od.getDirectory() + od.getFileName();
            int nL = S.setRead(sp);
            if (nL == 0) {
                exit("No settings found in:\n" + sp);
                return;
            }
            S.applyDetection();
        } else if (setSrc.equals(setSrcModes[0])) {
            if (nSet > 0) S.applyDetection();
        }

        // ---------------- dialog 2: input and outputs ----------------
        String[] layModes = {"Multi-channel files, of the types below", "One TIFF per channel (slides / coverslips)"};
        String[] pixWays = {"From the image, the value given if it has none", "The value given, whatever the image says"};
        String inPick = Settings.IN_MODES[0];
        for (int q = 0; q < Settings.IN_KEYS.length; q++) if (Settings.IN_KEYS[q].equals(S.inputMode)) inPick = Settings.IN_MODES[q];
        String openPick = Settings.OPEN_WAYS[0];
        for (int q = 0; q < Settings.OPEN_KEYS.length; q++)
            if (Settings.OPEN_KEYS[q].equals(S.openMethod)) openPick = Settings.OPEN_WAYS[q];
        String layPick = S.tiffMode ? layModes[1] : layModes[0];
        String pixPick = S.pixelForce ? pixWays[1] : pixWays[0];
        String[] cbL = {"Annotated QC stacks", "Results workbook (.xls)", "Histogram per experiment", "Pooled histogram"};
        boolean[] cbV = {S.doQC, S.doWorkbook, S.doHistExp, S.doHistPooled};
        String[] ioL = {"Hyperstack TIFF (all channels, all Z)", "Maximum Z projection", "Composite merge, all Z planes",
                "Composite merge of the projection"};
        boolean[] ioV = {S.doHyper, S.doProject, S.doMergeStack, S.doMergeProj};

        hook("d2");
        gd = new GenericDialog("Input and outputs");
        gd.addMessage("INPUT   (file types: their extensions, e.g.  czi nd lif nd2 lsm oib tif)");
        gd.addChoice("Analyse:", Settings.IN_MODES, inPick);
        if (doCountT) gd.addChoice("Images:", layModes, layPick);
        gd.addStringField("File types:", S.extList, 20);
        gd.addCheckbox("A file holding several positions or series: analyse each one (.nd: always)", S.splitSeries);
        gd.addChoice("Open the files with:", Settings.OPEN_WAYS, openPick);
        gd.addNumericField("Parallel:", S.parallel, 0, 3, "images analysed at once (0 = automatic)");
        gd.addMessage("PIXEL SIZE  (every area setting is in um2)");
        gd.addChoice("Pixel size:", pixWays, pixPick);
        sameRow(gd);
        gd.addNumericField("value", S.pixelSize, 4, 8, "um");
        if (doCountT) {
            gd.addMessage("COUNTING OUTPUTS  (the counts always go to CSV files)");
            gd.addCheckboxGroup(1, 4, cbL, cbV);
        }
        if (doImgT) {
            gd.addMessage("IMAGES TO MAKE  (from each file)");
            gd.addCheckboxGroup(2, 2, ioL, ioV);
        }
        gd.addCheckbox("Choose the output folder myself (otherwise: Results, inside the input folder)", S.pickOutDir);
        gd.addHelp("<html><h3>Input and outputs</h3>"
                + "<p><b>Input</b>: a folder of experiments / conditions / images, a single folder, or "
                + "a single file. File types are given by their extensions (czi nd lif nd2 tif ...). "
                + "MetaMorph .nd files are read by the tool itself, without Bio-Formats; TIFF files by "
                + "ImageJ; the other formats by Bio-Formats. A file holding several positions gives one "
                + "image per position.</p>"
                + "<p><b>Parallel</b>: how many images are analysed at once. 0 chooses from the "
                + "processors and the memory (the first image tells how much one takes); lower it if "
                + "ImageJ runs short of memory, 1 analyses them one after the other. The results are the "
                + "same whatever the number.</p>"
                + "<p><b>One TIFF per channel</b>: slide scanner layout, for counting only.</p>"
                + "<p><b>Pixel size</b>: read from each image and converted to um. An image with none, "
                + "calibrated in inches (a DPI tag) or out of the range of a microscope gets the value "
                + "given here. Every area setting is in um2.</p>"
                + "<p><b>QC stacks</b>: one per image, showing what was counted; their panels are chosen "
                + "in the next dialog.</p></html>");
        gd.showDialog();
        if (gd.wasCanceled()) return;

        String inChoice = gd.getNextChoice();
        String layChoice = layModes[0];
        if (doCountT) layChoice = gd.getNextChoice();
        S.extList = gd.getNextString();
        S.splitSeries = gd.getNextBoolean();
        String openChoice = gd.getNextChoice();
        double par = gd.getNextNumber();
        String pixChoice = gd.getNextChoice();
        S.pixelSize = gd.getNextNumber();
        if (doCountT) {
            S.doQC = gd.getNextBoolean();
            S.doWorkbook = gd.getNextBoolean();
            S.doHistExp = gd.getNextBoolean();
            S.doHistPooled = gd.getNextBoolean();
        }
        if (doImgT) {
            S.doHyper = gd.getNextBoolean();
            S.doProject = gd.getNextBoolean();
            S.doMergeStack = gd.getNextBoolean();
            S.doMergeProj = gd.getNextBoolean();
        }
        S.pickOutDir = gd.getNextBoolean();
        S.parallel = Double.isNaN(par) || par < 0 ? 0 : (int) Math.round(par);

        S.doCount = doCountT;
        if (!doImgT) {
            S.doHyper = false;
            S.doProject = false;
            S.doMergeStack = false;
            S.doMergeProj = false;
        }
        S.inputMode = "AUTO";
        for (int q = 0; q < Settings.IN_MODES.length; q++) if (inChoice.equals(Settings.IN_MODES[q])) S.inputMode = Settings.IN_KEYS[q];
        S.openMethod = "auto";
        for (int q = 0; q < Settings.OPEN_WAYS.length; q++)
            if (openChoice.equals(Settings.OPEN_WAYS[q])) S.openMethod = Settings.OPEN_KEYS[q];
        S.tiffMode = layChoice.equals(layModes[1]);
        S.pixelForce = pixChoice.equals(pixWays[1]);
        if (Double.isNaN(S.pixelSize) || S.pixelSize <= 0) {
            exit("The pixel size must be greater than 0.");
            return;
        }
        int nExt = Input.parseExts(S.extList).length;
        if (!S.tiffMode && nExt == 0) {
            exit("Give at least one file type to analyse, for example:  czi nd");
            return;
        }

        doMerge = S.doMergeStack || S.doMergeProj;
        doConvert = S.doHyper || S.doProject || S.doMergeStack || S.doMergeProj;
        // Image outputs rebuild a view from ONE multi-channel file; with one TIFF per channel they would open a
        // single channel only, so they are off here.
        if (S.tiffMode && doConvert) {
            doConvert = false;
            doMerge = false;
            skippedImgOut = true;
        }
        if (!S.doCount && !doConvert) {
            exit("Nothing to make: tick at least one image in 'Images to make'.");
            return;
        }
        if (!S.doCount) {
            S.doWorkbook = false;
            S.doHistExp = false;
            S.doHistPooled = false;
            S.doQC = false;
        }

        // ---------------- the images ----------------
        boolean showCh = S.doCount || doConvert;
        if (!pickInput()) return;
        if (!S.tiffMode) {
            int nFound = collectImages();
            if (nFound == 0) {
                noImageExit();
                return;
            }
        }
        userOutDir = "";
        if (S.pickOutDir) {
            hook("output");
            String d = IJ.getDirectory("Select the output folder");
            if (d == null) return;
            userOutDir = d;
        }
        // an output folder inside the input folder: what an earlier run wrote there is no image to count
        if (!S.tiffMode && userOutDir.length() > 0) {
            droppedOut = Input.dropUnder(entries, userOutDir, rootFolder);
            if (entries.isEmpty()) {
                noImageExit();
                return;
            }
        }
        if (!S.tiffMode && showCh) firstImageInfo();

        // ---------------- dialog 3: channels and objects ----------------
        // Channel numbers suggested by the colours of the first image, when it has them; a number then taken by
        // another role is cleared.
        if (!S.tiffMode && (firstB > 0 || firstG > 0 || firstR > 0)) {
            int[] sugg = {firstB, firstG, firstR, firstD};
            int[] cur = {S.chBLUE, S.chGREEN, S.chRED, S.chDIC};
            for (int q = 0; q < 4; q++) if (sugg[q] > 0) cur[q] = sugg[q];
            for (int q = 0; q < 4; q++) {
                if (sugg[q] > 0) continue;
                for (int r = 0; r < 4; r++) if (r != q && sugg[r] > 0 && cur[q] == sugg[r]) cur[q] = 0;
                if (cur[q] > firstNC) cur[q] = 0;
            }
            S.chBLUE = cur[0];
            S.chGREEN = cur[1];
            S.chRED = cur[2];
            S.chDIC = cur[3];
        }

        String[] roleNames = {"Nuclear stain", "Parasite label", "Second marker", "Pole marker", "Transmission / DIC",
                "Not used"};
        String[] colNames = {"as in the file", "grey", "blue", "green", "red", "cyan", "magenta", "yellow"};
        int nChRows = firstNC;
        if (nChRows == 0)
            nChRows = Math.max(4, Math.max(Math.max(S.chBLUE, S.chGREEN), Math.max(Math.max(S.chRED, S.chDIC), S.chPOLE)));
        if (nChRows > 12) nChRows = 12;
        int[] rowRole = new int[nChRows];
        String[] rowCol = new String[nChRows];
        int[] rowMrg = new int[nChRows];
        String[] remC = S.chColSet.isEmpty() ? new String[0] : S.chColSet.split(",", -1);
        String[] remM = S.chMrgSet.isEmpty() ? new String[0] : S.chMrgSet.split(",", -1);
        for (int k = 1; k <= nChRows; k++) {
            int r = 5;
            if (S.chPOLE == k) r = 3;
            if (S.chDIC == k) r = 4;
            if (S.chRED == k) r = 2;
            if (S.chGREEN == k) r = 1;
            if (S.chBLUE == k) r = 0;
            rowRole[k - 1] = r;
            // colour: the one remembered, else the file's - or, for a channel the file shows in grey, the usual
            // colour of what it is
            String fc = "?";
            if (firstCols.length >= k) fc = firstCols[k - 1];
            String cv = "";
            if (remC.length >= k) cv = Input.trimS(remC[k - 1]);
            if (colKeyIdx(cv) < 0) {
                cv = "file";
                if (fc.equals("grey") || fc.equals("?") || fc.equals("other")) cv = roleColour(r);
            }
            rowCol[k - 1] = cv;
            int mv = -1;
            if (remM.length >= k) {
                double tm = Macro.parseInt(Input.trimS(remM[k - 1]));
                mv = Double.isNaN(tm) ? -1 : (int) tm;
            }
            if (mv != 0 && mv != 1) {
                mv = 0;
                if (r == 0 && S.mergeBlue) mv = 1;
                if (r == 1 && S.mergeGreen) mv = 1;
                if (r == 2 && S.mergeRed) mv = 1;
                if (r == 4 && S.mergeDIC) mv = 1;
            }
            rowMrg[k - 1] = mv;
        }

        // ---- what to count (before the channels, which then know what is needed) ----
        if (S.doCount) {
            hook("what");
            gd = new GenericDialog("What to count");
            gd.addMessage("WHAT TO COUNT");
            gd.addCheckboxGroup(1, 3, new String[]{"Nuclei (host cells)", "Parasites", "Double-labelled parasites"},
                    new boolean[]{S.countNuclei, S.countGreen, S.countDouble});
            gd.addChoice("Reported measure:", Settings.MEASURE_MODES, Settings.MEASURE_MODES[S.measureMode]);
            gd.addChoice("Double labelling:", Settings.DBL_MODES, Settings.DBL_MODES[S.dblMode]);
            gd.addCheckbox("Poles: basal or apical pole of the marker (option)", S.doPoles);
            if (S.doQC) {
                gd.addMessage("QC STACK  (one per image: the panels it holds)");
                gd.addCheckboxGroup(2, 4, new String[]{"Nuclei", "Parasites", "Marker", "Marker + parasites", "Poles",
                                "All channels", "Each channel also raw", ""},
                        new boolean[]{S.qcPanN, S.qcPanP, S.qcPanR, S.qcPanRG, S.qcPanPol, S.qcPanAll, S.qcRaw, false});
            }
            gd.addHelp("<html><h3>What to count</h3>"
                    + "<p><b>Reported measure</b>: with nuclei counted, it is given per 100 host cells; "
                    + "without them, as a count per image.</p>"
                    + "<p><b>Double labelling</b> is scored on the parasites: by the overlap of each parasite "
                    + "with the marker, by marker objects inside it (beads, a labelled structure), or by the "
                    + "parasite lying inside a marker object (a vacuole, a cell). <b>Poles</b>: whether the "
                    + "marker sits at the basal or the apical pole (see the documentation).</p>"
                    + "<p><b>QC stack</b>: the panels each stack holds - the nuclei, the parasites, the "
                    + "marker, marker + parasites, the poles (pole option), all channels (nuclei and parasites "
                    + "counted) - drawn as in Calibrate; 'each channel also raw' adds the channel without "
                    + "outlines before its panel. A panel for something not counted is left out.</p>"
                    + "<p>The channels of each object come next.</p></html>");
            gd.showDialog();
            if (gd.wasCanceled()) return;
            S.countNuclei = gd.getNextBoolean();
            S.countGreen = gd.getNextBoolean();
            S.countDouble = gd.getNextBoolean();
            String mChoice = gd.getNextChoice();
            for (int q = 0; q < Settings.MEASURE_MODES.length; q++) if (mChoice.equals(Settings.MEASURE_MODES[q])) S.measureMode = q;
            String dChoice = gd.getNextChoice();
            for (int q = 0; q < Settings.DBL_MODES.length; q++) if (dChoice.equals(Settings.DBL_MODES[q])) S.dblMode = q;
            S.doPoles = gd.getNextBoolean();
            if (S.doQC) {
                S.qcPanN = gd.getNextBoolean();
                S.qcPanP = gd.getNextBoolean();
                S.qcPanR = gd.getNextBoolean();
                S.qcPanRG = gd.getNextBoolean();
                S.qcPanPol = gd.getNextBoolean();
                S.qcPanAll = gd.getNextBoolean();
                S.qcRaw = gd.getNextBoolean();
            }
            if (!S.countNuclei && !S.countGreen) {
                exit("Tick at least one object class to count.");
                return;
            }
            if (S.countDouble && !S.countGreen) {
                exit("Colocalisation is scored on the parasite objects, so parasites have to be counted as well.");
                return;
            }
        }

        // ---- the channels ----
        if (showCh) {
            // shown again, holding what was chosen, until the channels make sense
            String chErr = "x";
            while (!chErr.isEmpty()) {
                hook("channels");
                gd = new GenericDialog("Channels");
                if (!firstInfo.isEmpty()) gd.addMessage(firstInfo);
                if (S.tiffMode) {
                    gd.addMessage("CHANNEL FILES  (a word that the file name of each channel contains)");
                    gd.addStringField("Nuclear stain", S.kwBlue, 12);
                    sameRow(gd);
                    gd.addStringField("Parasite label", S.kwGreen, 12);
                    gd.addStringField("Second marker", S.kwRed, 12);
                    sameRow(gd);
                    gd.addStringField("Subfolder of the TIFFs", S.dataSub, 12);
                    gd.addStringField("Pole marker (optional)", S.kwPole, 12);
                } else {
                    String hd = "";
                    if (S.doCount) hd = "what each one is";
                    if (doConvert) {
                        if (!hd.isEmpty()) hd = hd + ", ";
                        hd = hd + "its colour in the images made";
                    }
                    if (doMerge) hd = hd + ", in the merges or not";
                    gd.addMessage("CHANNELS  (" + hd + ")");
                    for (int k = 1; k <= nChRows; k++) {
                        String lab = rowLabel(k);
                        if (S.doCount) {
                            gd.addChoice(lab, roleNames, roleNames[rowRole[k - 1]]);
                            if (doConvert) {
                                sameRow(gd);
                                gd.addChoice("colour", colNames, colNames[colKeyIdx(rowCol[k - 1])]);
                            }
                        } else {
                            gd.addChoice(lab + "   colour", colNames, colNames[colKeyIdx(rowCol[k - 1])]);
                        }
                        if (doMerge) {
                            sameRow(gd);
                            gd.addCheckbox("in the merges", rowMrg[k - 1] == 1);
                        }
                    }
                    // (a group spans the whole width: a single long tick box would widen the columns)
                    if (S.doCount)
                        gd.addCheckboxGroup(1, 1, new String[]{"Channels in another order in some files: find each one by its "
                                + "colour in the first file"}, new boolean[]{S.autoDetectChannels});
                }
                gd.addHelp("<html><h3>Channels</h3>"
                        + "<p>One row per channel of the first file, with the colour and the name its file gives "
                        + "it. Say what each channel is (a channel not used for counting: Not used), the colour it "
                        + "gets in the images made (hyperstack, projection, merges; 'as in the file' keeps the "
                        + "colour of the file) and whether it goes into the merges. The hyperstack and the "
                        + "projection show the channels one at a time, the merges show them together.</p>"
                        + "<p>The same channel numbers serve for every file. When the order changes from file to "
                        + "file, tick the last box: each channel is then found in every file by the colour it has "
                        + "in the first one (the channels used need different colours there).</p>"
                        + "<p><b>One TIFF per channel</b>: a word that the file name of each channel contains.</p></html>");
                gd.showDialog();
                if (gd.wasCanceled()) return;

                if (S.tiffMode) {
                    S.kwBlue = gd.getNextString();
                    S.kwGreen = gd.getNextString();
                    S.kwRed = gd.getNextString();
                    S.dataSub = gd.getNextString();
                    S.kwPole = gd.getNextString();
                } else {
                    for (int k = 1; k <= nChRows; k++) {
                        if (S.doCount) {
                            String rc = gd.getNextChoice();
                            for (int q = 0; q < roleNames.length; q++) if (rc.equals(roleNames[q])) rowRole[k - 1] = q;
                        }
                        if (doConvert) {
                            String cc = gd.getNextChoice();
                            for (int q = 0; q < colNames.length; q++) if (cc.equals(colNames[q])) rowCol[k - 1] = colKey(q);
                        }
                    }
                    if (doMerge) for (int k = 1; k <= nChRows; k++) rowMrg[k - 1] = gd.getNextBoolean() ? 1 : 0;
                    if (S.doCount) S.autoDetectChannels = gd.getNextBoolean();
                }

                // what each channel is -> the channel numbers; a role on two channels, or missing for what is
                // counted, is asked again
                chErr = "";
                if (!S.tiffMode && S.doCount) {
                    int[] nUse = new int[5], firstK = new int[5];
                    StringBuilder dupTxt = new StringBuilder();
                    for (int k = 1; k <= nChRows; k++) {
                        int r = rowRole[k - 1];
                        if (r < 5) {
                            nUse[r]++;
                            if (nUse[r] == 1) firstK[r] = k;
                            if (nUse[r] == 2)
                                dupTxt.append("\n   - ").append(roleNames[r]).append(": channels ").append(firstK[r]).append(" and ")
                                        .append(k);
                        }
                    }
                    if (dupTxt.length() > 0) chErr = chErr + "\nOne channel only for each:" + dupTxt;
                    S.chBLUE = firstK[0];
                    S.chGREEN = firstK[1];
                    S.chRED = firstK[2];
                    S.chPOLE = firstK[3];
                    S.chDIC = firstK[4];
                    String chMiss = "";
                    if (S.countNuclei && S.chBLUE == 0) chMiss = chMiss + "\n   - the nuclear stain";
                    if (S.countGreen && S.chGREEN == 0) chMiss = chMiss + "\n   - the parasite label";
                    if (S.countDouble && S.chRED == 0) chMiss = chMiss + "\n   - the second marker";
                    if (!chMiss.isEmpty()) chErr = chErr + "\nSay which channel is:" + chMiss;
                    // fewer channels than objects to count: nothing to choose here
                    int nNeed = (S.countNuclei ? 1 : 0) + (S.countGreen ? 1 : 0) + (S.countDouble ? 1 : 0);
                    if (!chMiss.isEmpty() && nChRows < nNeed) {
                        exit("The first image has " + nChRows + " channel(s) only, too few for what is to be counted:" + chMiss
                                + "\nCount fewer objects ('What to count'), or choose files with more channels.");
                        return;
                    }
                    // found by colour in every file: the channels used need a colour of their own in the first file
                    if (S.autoDetectChannels && chErr.isEmpty()) {
                        String cTxt = colourClash(firstK);
                        if (!cTxt.isEmpty())
                            chErr = chErr + "\nTo find the channels of each file by their colour, the ones used "
                                    + "need different colours in the first file:" + cTxt
                                    + "\n(or untick the last box: the same numbers for every file)";
                    }
                }
                if (!chErr.isEmpty()) {
                    hook("message");
                    IJ.showMessage("Channels", "Please correct:" + chErr);
                }
            }
        }
        S.chCols = rowCol.clone();
        S.chMrg = rowMrg.clone();
        S.chBLUE = Settings.chanOk(S.chBLUE);
        S.chGREEN = Settings.chanOk(S.chGREEN);
        S.chRED = Settings.chanOk(S.chRED);
        S.chDIC = Settings.chanOk(S.chDIC);
        S.chPOLE = Settings.chanOk(S.chPOLE);
        S.kwPole = Input.trimS(S.kwPole);
        // per-file detection: the colour of each role in the first file
        String[] roleCols = Engine.roleCols(S, firstCols);

        // ---------------- dialog 3b: marker objects and poles ----------------
        S.derive();
        if (S.doCount && S.countDouble && (S.dblMode > 0 || S.doPoles)) {
            hook("objects");
            gd = new GenericDialog("Double labelling: objects and poles");
            if (S.dblMode == 1) {
                gd.addMessage("MARKER OBJECTS INSIDE THE PARASITES  (beads, a labelled structure...)");
                gd.addNumericField("Object area from", S.objMinArea, 2, 6, "um2");
                sameRow(gd);
                gd.addNumericField("to", S.objMaxArea, 2, 6, "um2");
                gd.addNumericField("Its centre at most", S.objTol, 2, 6, "um outside the parasite (beads sit on its surface)");
                gd.addNumericField("At least", S.objMinN, 0, 4, "counted object(s) per double-labelled parasite");
                gd.addCheckbox("Split touching objects (watershed)", S.objWatershed);
                gd.addChoice("Objects counted:", Settings.POS_MODES, Settings.POS_MODES[S.posMode]);
            }
            if (S.dblMode == 2) {
                gd.addMessage("MARKER OBJECTS AROUND THE PARASITES  (a vacuole, a cell...)");
                gd.addNumericField("Object area from", S.encMinArea, 1, 8, "um2");
                sameRow(gd);
                gd.addNumericField("to", S.encMaxArea, 1, 8, "um2");
                gd.addMessage("A parasite is inside one when the minimum overlap of the next dialog\n"
                        + "(a % of its area) lies in it.");
                if (S.doPoles)
                    gd.addMessage("The pole option does not apply here (the parasite is inside the\n"
                            + "object): it is left off for this run.");
            }
            if (S.dblMode == 1 || S.dblPolesUsed) {
                gd.addMessage("PLACES ALONG THE PARASITE  (on its long axis, tip to tip)");
                gd.addNumericField("Centre: the central", S.centrePct, 0, 4, "% of its length");
                sameRow(gd);
                gd.addNumericField("extremity (pole): the last", S.endPct, 0, 4, "% at each end");
            }
            if (S.dblPolesUsed) {
                gd.addMessage("POLES  (which end of each parasite is basal)");
                gd.addChoice("Oriented by:", Settings.POLE_REFS, Settings.POLE_REFS[S.poleRef]);
                gd.addChoice("The pole marker marks:", Settings.POLE_SIDES, Settings.POLE_SIDES[S.poleSide]);
                gd.addNumericField("Nucleus of a parasite from", S.pnMinArea, 2, 6, "um2");
                sameRow(gd);
                gd.addNumericField("to", S.pnMaxArea, 2, 6, "um2");
                gd.addNumericField("Pole marker: threshold factor", S.pmThresholdFactor, 2, 6, "");
                gd.addNumericField("Pole marker object from", S.pmMinArea, 2, 6, "um2");
                sameRow(gd);
                gd.addNumericField("to", S.pmMaxArea, 2, 6, "um2");
                gd.addNumericField("No orientation when the reference lies within", S.poleDeadPct, 0, 4,
                        "% of the length from the centre");
            }
            gd.addHelp("<html><h3>Marker objects and poles</h3>"
                    + "<p><b>Marker objects inside the parasite</b>: the marker is cut into objects of the "
                    + "size given; each belongs to the parasite holding its centre - or within the distance "
                    + "given of its outline. A parasite holding at least the number of objects given, at the "
                    + "place asked, is double-labelled.</p>"
                    + "<p><b>The parasite inside a marker object</b>: the marker objects of the size given "
                    + "(a vacuole, a cell) hold a parasite when the minimum overlap of its area lies in them.</p>"
                    + "<p><b>Places</b> are read on the long axis of the parasite, tip to tip: the centre is the "
                    + "central part of its length, an extremity the last part at each end.</p>"
                    + "<p><b>Poles</b>: the end of each parasite nearest its nucleus is basal (the nucleus of a "
                    + "tachyzoite lies in its basal half); a pole marker gives the basal end, or the apical one "
                    + "(an extruded conoid labelled in fluorescence). With both, one is enough, and if they "
                    + "disagree the parasite is not oriented. The marker of a double-labelled parasite at an "
                    + "extremity is then at the basal pole, the apical pole, or - parasite not oriented - "
                    + "indeterminable; the three and their sum are given per image and per condition.</p></html>");
            gd.showDialog();
            if (gd.wasCanceled()) return;
            if (S.dblMode == 1) {
                S.objMinArea = gd.getNextNumber();
                S.objMaxArea = gd.getNextNumber();
                S.objTol = gd.getNextNumber();
                S.objMinN = gd.getNextNumber();
                S.objWatershed = gd.getNextBoolean();
                String pc = gd.getNextChoice();
                for (int q = 0; q < Settings.POS_MODES.length; q++) if (pc.equals(Settings.POS_MODES[q])) S.posMode = q;
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
                String rc = gd.getNextChoice();
                for (int q = 0; q < Settings.POLE_REFS.length; q++) if (rc.equals(Settings.POLE_REFS[q])) S.poleRef = q;
                String sc = gd.getNextChoice();
                for (int q = 0; q < Settings.POLE_SIDES.length; q++) if (sc.equals(Settings.POLE_SIDES[q])) S.poleSide = q;
                S.pnMinArea = gd.getNextNumber();
                S.pnMaxArea = gd.getNextNumber();
                S.pmThresholdFactor = gd.getNextNumber();
                S.pmMinArea = gd.getNextNumber();
                S.pmMaxArea = gd.getNextNumber();
                S.poleDeadPct = gd.getNextNumber();
            }
            S.objSettingsOk();
        }
        S.derive();
        if (S.poleNeedMk) {
            if (S.tiffMode && S.kwPole.isEmpty()) {
                exit("The parasites are to be oriented by a pole marker: give the word its\n"
                        + "files contain ('Channels'), or orient them by their nucleus.");
                return;
            }
            if (!S.tiffMode && S.chPOLE == 0) {
                exit("The parasites are to be oriented by a pole marker: choose 'Pole marker' for\n"
                        + "its channel ('Channels'), or orient them by their nucleus.");
                return;
            }
            if (!S.tiffMode && !S.autoDetectChannels) {
                if (S.chPOLE == S.chBLUE || S.chPOLE == S.chGREEN || S.chPOLE == S.chRED) {
                    exit("The pole marker cannot share its channel (" + S.chPOLE + ") with the nuclear\n"
                            + "stain, the parasite label or the second marker.");
                    return;
                }
            }
        }
        if (S.poleNeedNuc && !S.tiffMode && !S.autoDetectChannels && S.chBLUE == 0) {
            exit("The parasites are to be oriented by their nucleus: choose 'Nuclear stain' for\n"
                    + "its channel ('Channels').");
            return;
        }

        if (S.tiffMode) {
            int nFound = collectImages();
            if (nFound == 0) {
                noImageExit();
                return;
            }
        }
        // Reported measure: per 100 host cells with nuclei, a count per image without; double labelling measures
        // need double labelling counted.
        S.setMeasure();

        // ---------------- dialog 4: detection settings ----------------
        if (S.doCount) {
            if (!detectionDialog(roleCols)) return;
        }

        // The settings of this run are those the next one starts from.
        if (!lastSetPath.isEmpty()) S.saveSettings(lastSetPath);

        // ---------------- the run ----------------
        Run r = new Run(S, entries, rootFolder);
        r.userOutDir = userOutDir;
        r.droppedOut = droppedOut;
        r.skippedImgOut = skippedImgOut;
        r.firstCols = firstCols;
        r.roleCols = roleCols;
        r.run();
        IJ.showStatus("Count and Colocalise: done in " + IJ.d2s(r.elapsedMs / 1000.0, 1) + " s");
    }

    // ================================================================== dialog 4: detection settings
    /** The detection dialog, shown again after each calibration with the values it returned. False: cancelled. */
    boolean detectionDialog(String[] roleCols) {
        String stopWhatTxt = S.stopOn.equals("parasites") ? "parasites" : "host cells";
        // objects the stop target can be counted in: only those being counted
        List<String> sw = new ArrayList<String>();
        if (S.countNuclei) sw.add("host cells");
        if (S.countGreen) sw.add("parasites");
        String[] stopWhat = sw.toArray(new String[0]);
        if (!sw.contains(stopWhatTxt)) stopWhatTxt = stopWhat[0];

        String calRun = "Count with the settings below";
        String calNuc = "Calibrate nuclei first";
        String calPar = "Calibrate parasites first";
        String calDbl = "Calibrate double labelling first";
        String calSrcPick = Calibrate.SRC_MODES[0];
        List<String> cm = new ArrayList<String>();
        cm.add(calRun);
        if (S.countNuclei) cm.add(calNuc);
        if (S.countGreen) cm.add(calPar);
        if (S.countDouble) cm.add(calDbl);
        String[] calModes = cm.toArray(new String[0]);

        // where each class is read from, for the block titles
        String srcB = "channel " + S.chBLUE;
        String srcG = "channel " + S.chGREEN;
        String srcR = "marker: channel " + S.chRED;
        if (S.autoDetectChannels) {
            srcB = "blue channel";
            srcG = "green channel";
            srcR = "marker: red channel";
        }
        if (S.tiffMode) {
            srcB = "files '" + S.kwBlue + "'";
            srcG = "files '" + S.kwGreen + "'";
            srcR = "marker: files '" + S.kwRed + "'";
        }

        String calPick = "";
        while (!calPick.equals(calRun)) {
            // preprocessing in use, shown under each block (Calibrate changes it)
            String wsN = "off";
            if (S.nucleiWatershed) wsN = "" + Macro.n2s(S.nucleiWsTol) + " px";
            double cN = Settings.circMin(S.nucleiCirc);
            double cG = Settings.circMin(S.greenCirc);
            String prepN = "      in Calibrate: " + S.nucleiThresholdMethod + " threshold, background " + Macro.n2s(S.nucleiBgRolling)
                    + " px, blur " + Macro.n2s(S.nucleiBlurSigma) + " px, circularity " + Macro.n2s(cN) + ", watershed " + wsN;
            String prepG = "      in Calibrate: " + S.greenThresholdMethod + " threshold, background " + Macro.n2s(S.greenBgRolling)
                    + " px, blur " + Macro.n2s(S.greenBlurSigma) + " px, circularity " + Macro.n2s(cG) + ", watershed tolerance "
                    + Macro.n2s(S.greenWsTol) + " px";
            String prepR = "      in Calibrate: " + S.redThresholdMethod + " threshold, background " + Macro.n2s(S.redBgRolling)
                    + " px, blur " + Macro.n2s(S.redBlurSigma) + " px";

            // Kept compact - a laptop screen must still show its OK button: short fields share a row, menus have a
            // row of their own, explanations under Help.
            hook("detection");
            GenericDialog gd = new GenericDialog("Detection settings");
            gd.addChoice("Next:", calModes, calRun);
            gd.addChoice("Calibrate on:", Calibrate.SRC_MODES, calSrcPick);
            if (S.countNuclei) {
                gd.addMessage("NUCLEI  (" + srcB + ")   areas in um2");
                gd.addNumericField("Threshold factor", S.nucleiThresholdFactor, 2, 6, "");
                sameRow(gd);
                gd.addNumericField("bright cap", S.nucleiCapF, 1, 4, "x median");
                sameRow(gd);
                gd.addNumericField("Minimum area", S.nucleiMinArea, 1, 6, "");
                gd.addCheckbox("Overlapping nuclei count as several cells:", S.splitBigNuclei);
                sameRow(gd);
                gd.addNumericField("above", S.nucleiDoubletFactor, 2, 4, "x median");
                sameRow(gd);
                gd.addNumericField("at most", S.nucleiMaxSplit, 0, 4, "cells");
                gd.addNumericField("No cell under", S.nucleiFragFactor, 2, 4, "x median (0 = off)");
                gd.addChoice("Nuclei cut by the border:", Settings.EDGE_MODES, Settings.EDGE_MODES[S.nucleiEdgeMode]);
                gd.addMessage(prepN);
            }
            if (S.countGreen) {
                gd.addMessage("PARASITES  (" + srcG + ")   areas in um2");
                gd.addNumericField("Threshold factor", S.greenThresholdFactor, 2, 6, "");
                sameRow(gd);
                gd.addNumericField("bright cap", S.greenCapF, 1, 4, "x median");
                sameRow(gd);
                gd.addNumericField("Area from", S.greenMinArea, 2, 6, "");
                sameRow(gd);
                gd.addNumericField("to", S.greenMaxArea, 2, 6, "");
                gd.addCheckbox("Split touching parasites (watershed)", S.greenWatershed);
                gd.addCheckbox("Clumps count as several parasites:", S.splitBigParasites);
                sameRow(gd);
                gd.addNumericField("above", S.greenDoubletFactor, 2, 4, "x median");
                sameRow(gd);
                gd.addNumericField("at most", S.greenMaxSplit, 0, 4, "parasites");
                gd.addMessage(prepG);
            }
            if (S.countDouble) {
                String dHow = "by overlap";
                if (S.dblMode == 1) dHow = "by marker objects inside the parasite";
                if (S.dblMode == 2) dHow = "the parasite inside a marker object";
                gd.addMessage("DOUBLE LABELLING  (" + srcR + "), " + dHow);
                gd.addNumericField("Marker threshold factor", S.redThresholdFactor, 2, 6, "");
                sameRow(gd);
                gd.addNumericField("bright cap", S.redCapF, 1, 4, "x median");
                if (S.dblMode != 1) {
                    sameRow(gd);
                    gd.addNumericField("Minimum overlap", S.minOverlapPercent, 0, 6, "%");
                }
                gd.addMessage(prepR);
            }
            gd.addMessage("STOP EARLY, MINIMUM PER IMAGE  (optional, 0 = off; see Help)");
            gd.addNumericField("Target per condition", S.stopTarget, 0, 6, "");
            sameRow(gd);
            gd.addChoice("in", stopWhat, stopWhatTxt);
            String labP = "Minimum per image: parasites";
            if (S.countNuclei) gd.addNumericField("Minimum per image: host cells", S.minCellsImg, 0, 6, "");
            if (S.countGreen) {
                // next to the host cells
                if (S.countNuclei) {
                    sameRow(gd);
                    labP = "parasites";
                }
                gd.addNumericField(labP, S.minParImg, 0, 6, "");
            }
            gd.addChoice("An image under the minimum is", Settings.LOW_MODES, Settings.LOW_MODES[S.lowMode]);
            gd.addHelp("<html><h3>Detection settings</h3>"
                    + "<p><b>Threshold factor</b>: the automatic threshold of each image times this factor "
                    + "(above 1: stricter, fewer and smaller objects).</p>"
                    + "<p><b>Bright cap</b>: in each image, an object brighter than this x the median "
                    + "brightness of what is detected (debris, a dead cell) is left out of the threshold "
                    + "calculation, so that it cannot raise the threshold above the ordinary objects. It "
                    + "is still detected. Hot pixels are not concerned. 0 = off.</p>"
                    + "<p><b>Clusters</b>: an object larger than the given multiple of the median area "
                    + "of its image counts as several (area / median).</p>"
                    + "<p><b>Calibrate</b>: pick it in the first menu, then OK: the counted objects are "
                    + "drawn on one or several images, with a slider for every setting, before the run - "
                    + "the settings listed under each block too.</p>"
                    + "<p><b>Double labelling</b>: by overlap, a parasite is double-labelled when at least the "
                    + "minimum overlap of its area is positive in the mask of the marker; by marker objects, "
                    + "when it holds them (size, place and number: the dialog before, and Calibrate); inside "
                    + "a marker object, when that fraction of its area lies in one.</p>"
                    + "<p><b>Stop early</b>: each condition is counted image by image until the target is "
                    + "reached; the image reaching it counts in full. <b>Minimum per image</b>: an image "
                    + "under it is left out, or kept for a condition that does not reach the target "
                    + "without it. See the documentation.</p></html>");
            gd.showDialog();
            if (gd.wasCanceled()) return false;

            calPick = gd.getNextChoice();
            calSrcPick = gd.getNextChoice();
            if (S.countNuclei) {
                S.nucleiThresholdFactor = gd.getNextNumber();
                S.nucleiCapF = gd.getNextNumber();
                S.nucleiMinArea = gd.getNextNumber();
                S.splitBigNuclei = gd.getNextBoolean();
                S.nucleiDoubletFactor = gd.getNextNumber();
                S.nucleiMaxSplit = gd.getNextNumber();
                S.nucleiFragFactor = gd.getNextNumber();
                String edgeChoice = gd.getNextChoice();
                // 0 = off. Kept below 1: at 1, every nucleus under the median - half of them - would count for nothing.
                if (Double.isNaN(S.nucleiFragFactor) || S.nucleiFragFactor < 0) S.nucleiFragFactor = 0;
                if (S.nucleiFragFactor > 0.9) S.nucleiFragFactor = 0.9;
                S.nucleiEdgeMode = 0;
                if (edgeChoice.equals(Settings.EDGE_MODES[1])) S.nucleiEdgeMode = 1;
                if (edgeChoice.equals(Settings.EDGE_MODES[2])) S.nucleiEdgeMode = 2;
                S.nucleiCapF = Settings.capOk(S.nucleiCapF);
            }
            if (S.countGreen) {
                S.greenThresholdFactor = gd.getNextNumber();
                S.greenCapF = gd.getNextNumber();
                S.greenMinArea = gd.getNextNumber();
                S.greenMaxArea = gd.getNextNumber();
                S.greenWatershed = gd.getNextBoolean();
                S.splitBigParasites = gd.getNextBoolean();
                S.greenDoubletFactor = gd.getNextNumber();
                S.greenMaxSplit = gd.getNextNumber();
                S.parClusterOk();
                S.greenCapF = Settings.capOk(S.greenCapF);
            }
            if (S.countDouble) {
                S.redThresholdFactor = gd.getNextNumber();
                S.redCapF = gd.getNextNumber();
                if (S.dblMode != 1) S.minOverlapPercent = gd.getNextNumber();
                S.redCapF = Settings.capOk(S.redCapF);
            }
            S.stopTarget = gd.getNextNumber();
            stopWhatTxt = gd.getNextChoice();
            if (S.countNuclei) S.minCellsImg = gd.getNextNumber();
            if (S.countGreen) S.minParImg = gd.getNextNumber();
            String lowChoice = gd.getNextChoice();
            if (Double.isNaN(S.stopTarget) || S.stopTarget < 0) S.stopTarget = 0;
            S.stopOn = stopWhatTxt.equals("parasites") ? "parasites" : "cells";
            // whole numbers of objects; 0 = no minimum
            if (Double.isNaN(S.minCellsImg) || S.minCellsImg < 0) S.minCellsImg = 0;
            if (Double.isNaN(S.minParImg) || S.minParImg < 0) S.minParImg = 0;
            S.minCellsImg = Macro.round(S.minCellsImg);
            S.minParImg = Macro.round(S.minParImg);
            S.lowMode = lowChoice.equals(Settings.LOW_MODES[1]) ? 1 : 0;

            String what = null;
            if (calPick.equals(calNuc)) what = "nuclei";
            if (calPick.equals(calPar)) what = "parasites";
            if (calPick.equals(calDbl)) what = "double";
            if (what != null) {
                Calibrate cal = new Calibrate(this, roleCols);
                if (cal.calibrate(what, calSrcPick) < 0) return false;
            }
        }
        return true;
    }

    // ================================================================== the images
    /** Asks for the input once: the folder of experiments, a single folder, or a single file. */
    boolean pickInput() {
        if (inputPicked) return true;
        String sp = File.separator;
        inFile = "";
        hook("input");
        if (S.tiffMode) {
            rootFolder = IJ.getDirectory("Select the folder containing the experiments");
        } else if (S.inputMode.equals("FILE")) {
            OpenDialog od = new OpenDialog("Select an image file", null);
            if (od.getFileName() == null) return false;
            inFile = od.getDirectory() + od.getFileName();
            rootFolder = new File(inFile).getParent() + sp;
        } else if (S.inputMode.equals("FOLDER")) {
            rootFolder = IJ.getDirectory("Select the folder containing the images");
        } else {
            rootFolder = IJ.getDirectory("Select the folder containing the experiments");
        }
        if (rootFolder == null) return false;
        if (!rootFolder.endsWith(sp) && !rootFolder.endsWith("/")) rootFolder = rootFolder + sp;
        inputPicked = true;
        return true;
    }

    /** The images to process (done once). Returns how many entries were found. */
    int collectImages() {
        if (imagesCollected) return entries.size();
        if (!pickInput()) return 0;
        List<String> warn = new ArrayList<String>();
        entries = Input.collect(S, rootFolder, inFile, warn);
        for (String w : warn) IJ.log(w);
        imagesCollected = true;
        return entries.size();
    }

    /** No image found: the folder contents go to the Log, and the tool stops. */
    void noImageExit() {
        IJ.log("Contents of " + rootFolder + " :");
        for (String f : Input.fileList(rootFolder)) IJ.log("   " + f);
        String ft = Input.fmtText(S, Input.parseExts(S.extList));
        exit("No " + ft + " file found under " + rootFolder + "\nSee the Log window for the folder contents. The file types are set\n"
                + "in the first dialog.");
    }

    /** The channels of the first image (its metadata), for the Channels dialog. */
    void firstImageInfo() {
        firstInfo = "";
        firstB = firstG = firstR = firstD = 0;
        firstNC = 0;
        if (entries.isEmpty()) return;
        Engine.FirstInfo f = Engine.firstImageInfo(S, entries);
        firstInfo = f.text;
        if (!f.ok) return;
        firstNC = f.nc;
        firstCols = f.cols.clone();
        firstNames = f.names.clone();
        firstB = f.sugB;
        firstG = f.sugG;
        firstR = f.sugR;
        firstD = f.sugD;
    }

    // ================================================================== channel table
    /** Label of row k of the channel table: the colour and the name its file gives to that channel. */
    String rowLabel(int k) {
        String d = "?";
        if (firstCols.length >= k) d = firstCols[k - 1];
        if (firstNames.length >= k && !firstNames[k - 1].isEmpty()) d = d + ", " + firstNames[k - 1];
        return "Channel " + k + "  (" + d + ")";
    }

    static final String[] COL_KEYS = {"file", "grey", "blue", "green", "red", "cyan", "magenta", "yellow"};

    static String colKey(int q) {
        return COL_KEYS[q];
    }

    static int colKeyIdx(String cv) {
        for (int q = 0; q < COL_KEYS.length; q++) if (COL_KEYS[q].equals(cv)) return q;
        return -1;
    }

    /** The usual colour of what a channel is (index in the roles of the table), for a channel shown in grey. */
    static String roleColour(int r) {
        if (r == 0) return "blue";
        if (r == 1) return "green";
        if (r == 2) return "red";
        if (r == 3) return "magenta";
        if (r == 4) return "grey";
        return "file";
    }

    /** For the per-file option: the roles in use whose colour in the first image is unknown or shared. */
    String colourClash(int[] fk) {
        String[] nm = {"nuclear stain", "parasite label", "second marker", "", "transmission / DIC"};
        StringBuilder out = new StringBuilder();
        for (int a = 0; a < 5; a++) {
            if (a == 3 || fk[a] == 0) continue;
            String ca = "?";
            if (fk[a] <= firstCols.length) ca = firstCols[fk[a] - 1];
            if (ca.equals("?") || ca.equals("other")) {
                out.append("\n   - ").append(nm[a]).append(" (channel ").append(fk[a]).append("): colour unknown");
                continue;
            }
            for (int b = a + 1; b < 5; b++) {
                if (b == 3 || fk[b] == 0) continue;
                String cb = "?";
                if (fk[b] <= firstCols.length) cb = firstCols[fk[b] - 1];
                if (cb.equals(ca)) out.append("\n   - ").append(nm[a]).append(" and ").append(nm[b]).append(": both ").append(ca);
            }
        }
        return out.toString();
    }

    // ================================================================== small helpers
    /** Is this ImageJ at least version want ("1.52a")? Fiji can report "2.x.y/1.54f": the part after the "/". */
    static boolean versionAtLeast(String want) {
        String v = IJ.getVersion();
        int sl = v.lastIndexOf('/');
        if (sl >= 0) v = v.substring(sl + 1);
        int n = want.length();
        if (v.length() < n) return false;
        for (int q = 0; q < n; q++) {
            char a = v.charAt(q), b = want.charAt(q);
            if (a > b) return true;
            if (a < b) return false;
        }
        return true;
    }

    /** The next field on the row of the one before (ImageJ 1.51r and later). */
    static void sameRow(GenericDialog gd) {
        if (versionAtLeast("1.51r")) gd.addToSameRow();
    }

    /** exit(message) of the macro: the message, and the tool stops (the caller returns). */
    static void exit(String msg) {
        hook("message");
        IJ.showMessage(TITLE, msg);
    }
}
