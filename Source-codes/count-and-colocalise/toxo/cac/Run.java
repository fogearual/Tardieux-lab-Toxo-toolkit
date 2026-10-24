package toxo.cac;

import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A run of Count and Colocalise once the dialogs are answered: the image outputs, the counting of every image
 * (several at once, the results committed in the order of the list, so that the Log, the stop target and every
 * file are those of a run image after image), and the result files. The very steps of the macro version.
 */
public final class Run {

    final Settings S;
    final List<Input.Entry> entries;
    final String rootFolder;
    String userOutDir = "";
    int droppedOut = 0;
    boolean skippedImgOut = false;

    String outputRoot, stacksRoot, dataRoot, rootName;
    String[] roleCols = {"", "", "", ""};
    String[] firstCols = new String[0];
    String setLog = "";
    int nClasses;
    final double labelScale = 1.0, recapScale = 1.0, strokeScale = 1.0;

    // outline colours (Calibrate and QC stacks alike)
    final String colNuc = "00ffff", colNClu = "ff00ff", colNFrag = "00ff00", colEdge = "a0a0a0";
    final String colPar = "ff00ff", colPClu = "ffffff", colDbl = "ffffff", colOth = "00ffff";
    final String colRejS = "ff9900", colRejC = "ffff00", colMk = "ff9900";

    static final String PX_PLACEHOLDER = "\u0001PX";

    // the pixel size last applied (for the Log of an image that opened no channel)
    double lastPx = 0, lastPxH = 0;
    String lastPxSource = "";
    boolean calibShown = false;

    // the Log of the run, as the Log window shows it
    final List<String> logLines = new ArrayList<String>();
    boolean echo = true;

    // results, one row per image counted (the summary arrays of the macro)
    final List<String> summaryManip = new ArrayList<String>(), summaryCond = new ArrayList<String>(),
            summaryNames = new ArrayList<String>(), summaryWhy = new ArrayList<String>();
    final List<Double> summaryCells = new ArrayList<Double>(), summaryGreen = new ArrayList<Double>(),
            summaryColoc = new ArrayList<Double>(), summaryObj = new ArrayList<Double>(),
            summaryOri = new ArrayList<Double>(), summaryBas = new ArrayList<Double>(), summaryApi = new ArrayList<Double>(),
            summaryInd = new ArrayList<Double>(), summaryMid = new ArrayList<Double>();
    final List<Integer> summaryUse = new ArrayList<Integer>(), summaryKey = new ArrayList<Integer>();
    final List<String> perPar = new ArrayList<String>();
    final List<String> exclList = new ArrayList<String>();

    // stop early
    final List<String> stopKeys = new ArrayList<String>(), stopLab = new ArrayList<String>();
    final List<Double> stopDone = new ArrayList<Double>();
    final List<Integer> stopImgs = new ArrayList<Integer>(), stopSkip = new ArrayList<Integer>();
    String stopWhatTxt = "host cells";
    String lowRule = "";
    boolean lowOn = false;

    // threads
    int threadsUsed = 1;
    long elapsedMs;
    volatile boolean cancelled = false;

    public Run(Settings s, List<Input.Entry> entries, String rootFolder) {
        this.S = s;
        this.entries = entries;
        this.rootFolder = rootFolder;
    }

    // ================================================================== the Log
    /** print(s) of the macro language: the Log window's lines, as IJ.log makes them. */
    void log(String s) {
        if (s == null) return;
        if (echo) IJ.log(s);
        String t = s;
        if (t.endsWith("\n")) {
            if (t.equals("\n\n")) t = "\n \n ";
            else if (t.endsWith("\n\n")) t = t.substring(0, t.length() - 2) + "\n \n ";
            else t = t + " ";
        }
        if (t.length() == 1 && t.equals("\n")) t = "";
        String[] lines = t.split("\n");
        if (lines.length == 0) logLines.add("");
        for (String l : lines) logLines.add(l);
    }

    String logText() {
        StringBuilder b = new StringBuilder();
        for (String l : logLines) b.append(l).append('\n');
        return b.toString();
    }

    // ================================================================== legends, Parasites.csv
    String legNuclei(int ln) {
        return legNuclei(S, ln);
    }

    String legParasites(int ln) {
        return legParasites(S, ln);
    }

    String legDouble(int ln) {
        return legDouble(S, ln);
    }

    String legPoles(int ln) {
        return legPoles(ln, S);
    }

    /** The legend of the nuclei panels (Calibrate and QC stacks), line ln (1, 2). */
    static String legNuclei(Settings S, int ln) {
        if (ln == 1) {
            String t = "cyan: counted   magenta: cluster (several cells)";
            if (S.nucleiFragFactor > 0) t = t + "   green: fragment (no cell)";
            return t;
        }
        String t = "orange: rejected by size   yellow: rejected by shape";
        if (S.nucleiEdgeMode > 0) t = "grey: cut by the border, left out   " + t;
        return t;
    }

    /** The legend of the parasite panels, line ln (1, 2). */
    static String legParasites(Settings S, int ln) {
        if (ln == 1) {
            String t = "magenta: counted";
            if (S.splitBigParasites) t = t + "   white: cluster (several parasites)";
            return t;
        }
        return "orange: rejected by size   yellow: rejected by shape";
    }

    static String legDouble(Settings S, int ln) {
        if (ln == 1) {
            String t = "white: double-labelled   cyan: other parasites   orange: marker-positive area";
            if (S.dblMode == 2) t = t + "   yellow: marker objects";
            return t;
        }
        if (S.dblMode == 1) return "objects (circles): white counted, yellow on a parasite elsewhere, grey on none";
        return "";
    }

    static String legPoles(int ln, Settings S) {
        if (ln == 1) return "marker at the basal pole: magenta   apical: cyan   indeterminable: white";
        return "not at a pole: orange   other parasites: grey   yellow dot: basal end";
    }

    /** A name as a CSV field: quoted when it holds a comma, a quote or a line break. */
    static String csvQ(String s) {
        if (s.indexOf(',') < 0 && s.indexOf('"') < 0 && s.indexOf('\n') < 0) return s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    /** The lines of Parasites.csv of the image just scored: one per parasite object. */
    String parasiteLines(Detect d, String manip, String cond, String base, int nPar) {
        StringBuilder s = new StringBuilder();
        boolean useW = d.parWeight.length == nPar;
        int[] dN = new int[nPar];
        for (int z = 0; z < d.dblIdx.length; z++) dN[d.dblIdx[z]] = z + 1;
        String[] clsTxt = {"", "basal pole", "apical pole", "indeterminable", "not at a pole"};
        for (int p = 0; p < nPar; p++) {
            double w = useW ? d.parWeight[p] : 1;
            StringBuilder ln = new StringBuilder();
            ln.append(csvQ(manip)).append(',').append(csvQ(cond)).append(',').append(csvQ(base)).append(',').append(p + 1)
                    .append(',').append(Macro.n2s(w)).append(',').append(Macro.num2(d.aArea[p])).append(',')
                    .append(Macro.num2(d.aLen[p])).append(',');
            if (dN[p] > 0) ln.append("yes,").append(dN[p]);
            else ln.append("no,");
            if (S.dblMode == 1) ln.append(',').append(Macro.n2s(d.pObj[p])).append(',').append(Macro.n2s(d.pCnt[p]));
            if (S.dblPolesUsed && S.dblMode != 2) {
                String oriT = "yes";
                if (d.pBas[p] == 0) oriT = "no - " + d.pWhy[p].replace(",", ";");
                String plc = "";
                if (d.pPlace[p] >= 0) plc = Macro.num2(100 * d.pPlace[p]);
                if (d.pPlace[p] >= 0 && d.pBas[p] == 2) plc = Macro.num2(100 * (1 - d.pPlace[p]));
                ln.append(',').append(oriT).append(',').append(Macro.n2s(d.pNuc[p])).append(',').append(Macro.n2s(d.pMk[p]))
                        .append(',').append(clsTxt[d.pCls[p]]).append(',').append(plc);
            }
            s.append(ln).append('\n');
        }
        return s.toString();
    }

    // ================================================================== the run
    /** The whole run, after the dialogs. */
    public void run() {
        long t0 = System.currentTimeMillis();
        if (echo) IJ.resetEscape();
        String sep = File.separator;
        S.setMeasure();
        stopWhatTxt = S.stopOn.equals("parasites") ? "parasites" : "host cells";
        calibShown = false;
        if (echo) IJ.log("\\Clear");
        log("Count and Colocalise v" + Settings.VERSION);
        if (S.tiffMode)
            log("One TIFF per channel: " + entries.size() + " acquisition(s) found " + "(experiment x coverslip x field)");
        if (skippedImgOut)
            log("Image outputs (hyperstack, projection, merges) are not available "
                    + "with one TIFF per channel; they were skipped.");
        rootName = Input.cleanName(rootFolder);
        outputRoot = rootFolder + "Results" + sep;
        if (userOutDir != null && userOutDir.length() > 0) {
            outputRoot = userOutDir;
            if (!outputRoot.endsWith(sep)) outputRoot = outputRoot + sep;
        }
        stacksRoot = outputRoot + "Stacks_" + rootName + sep;
        dataRoot = outputRoot + "Data" + sep;
        new File(outputRoot).mkdirs();
        if (S.doCount) {
            new File(stacksRoot).mkdirs();
            new File(dataRoot).mkdirs();
        }
        S.saveSettings(outputRoot + "Settings.txt");
        log(entries.size() + " image(s) to process, output in " + outputRoot);
        if (droppedOut > 0) log(droppedOut + " file(s) inside the output folder left out (written by an earlier run)");
        for (String a : exclList) log("excluded from calculations: " + a);

        boolean doConvert = (S.doHyper || S.doProject || S.doMergeStack || S.doMergeProj) && !S.tiffMode;
        if (doConvert) {
            ImageOutputs.run(this);
            if (cancelled || !S.doCount) {
                writeLog();
                elapsedMs = System.currentTimeMillis() - t0;
                return;
            }
        }

        setLog = settingsLog();
        log(setLog);
        if (!S.measureNote.isEmpty()) log(S.measureNote);
        if (S.stopTarget > 0)
            log("Stop early: each condition stops at " + Macro.n2s(S.stopTarget) + " " + stopWhatTxt
                    + " (the image reaching it is counted in full)");
        lowRule = "";
        if (S.countNuclei && S.minCellsImg > 0) lowRule = "fewer than " + Macro.n2s(S.minCellsImg) + " host cells";
        if (S.countGreen && S.minParImg > 0) {
            if (!lowRule.isEmpty()) lowRule = lowRule + " or ";
            lowRule = lowRule + "fewer than " + Macro.n2s(S.minParImg) + " parasites";
        }
        lowOn = !lowRule.isEmpty();
        if (lowOn) {
            String lowTxt = "left out of the counts";
            if (S.lowMode == 1 && S.stopTarget > 0)
                lowTxt = "counted only if their condition does not reach the target without them";
            if (S.lowMode == 1 && S.stopTarget == 0) lowTxt = "left out of the counts (there is no stop target to reach)";
            log("Images with " + lowRule + ": " + lowTxt);
        }
        log("");
        nClasses = (S.countNuclei ? 1 : 0) + (S.countGreen ? 1 : 0);

        processAll();
        if (cancelled) {
            // stopped with Esc, as the macro version stops: what each image wrote stays, no result table
            log("STOPPED with Esc after " + committed + " of " + entries.size() + " image(s): their QC stacks and ROI files "
                    + "are written, the result tables are not.");
            writeLog();
            elapsedMs = System.currentTimeMillis() - t0;
            return;
        }

        afterLoop();
        Outputs.write(this);
        elapsedMs = System.currentTimeMillis() - t0;
    }

    /** Every setting in force, for the Log and the Info of the QC stacks. */
    String settingsLog() {
        StringBuilder s = new StringBuilder("Settings\n");
        if (S.countNuclei) {
            String wsN = "off";
            if (S.nucleiWatershed) wsN = "on (tolerance " + Macro.n2s(S.nucleiWsTol) + " px)";
            String clN = "no";
            if (S.splitBigNuclei)
                clN = "above " + Macro.n2s(S.nucleiDoubletFactor) + " x the median, at most " + Macro.n2s(S.nucleiMaxSplit)
                        + " cells";
            String frN = "no";
            if (S.nucleiFragFactor > 0) frN = "below " + Macro.n2s(S.nucleiFragFactor) + " x the median, no cell";
            String capN = "off";
            if (S.nucleiCapF > 0) capN = Macro.n2s(S.nucleiCapF) + " x the median brightness";
            String sN = "   nuclei: channel " + S.chBLUE + ", " + S.nucleiThresholdMethod + " threshold x "
                    + Macro.n2s(S.nucleiThresholdFactor) + ", bright cap " + capN + ", area >= " + Macro.n2s(S.nucleiMinArea)
                    + " um2, circularity " + S.nucleiCirc + ", background " + Macro.n2s(S.nucleiBgRolling) + " px, blur "
                    + Macro.n2s(S.nucleiBlurSigma) + " px, watershed " + wsN + ", clusters " + clN + ", fragments " + frN;
            if (S.tiffMode) sN = sN.replace("channel " + S.chBLUE, "files '" + S.kwBlue + "'");
            s.append(sN).append("\n");
            s.append("   nuclei at the image border: ").append(Settings.EDGE_MODES[S.nucleiEdgeMode]).append("\n");
        }
        if (S.countGreen) {
            String wsG = "off";
            if (S.greenWatershed) wsG = "on (tolerance " + Macro.n2s(S.greenWsTol) + " px)";
            String capG = "off";
            if (S.greenCapF > 0) capG = Macro.n2s(S.greenCapF) + " x the median brightness";
            String sG = "   parasites: channel " + S.chGREEN + ", " + S.greenThresholdMethod + " threshold x "
                    + Macro.n2s(S.greenThresholdFactor) + ", bright cap " + capG + ", area " + Macro.n2s(S.greenMinArea) + " to "
                    + Macro.n2s(S.greenMaxArea) + " um2, circularity " + S.greenCirc + ", background "
                    + Macro.n2s(S.greenBgRolling) + " px, blur " + Macro.n2s(S.greenBlurSigma) + " px, watershed " + wsG;
            if (S.splitBigParasites)
                sG = sG + ", clusters above " + Macro.n2s(S.greenDoubletFactor) + " x the median, at most "
                        + Macro.n2s(S.greenMaxSplit) + " parasites";
            if (S.tiffMode) sG = sG.replace("channel " + S.chGREEN, "files '" + S.kwGreen + "'");
            s.append(sG).append("\n");
        }
        if (S.countDouble) {
            String capR = "off";
            if (S.redCapF > 0) capR = Macro.n2s(S.redCapF) + " x the median brightness";
            String sR = "   double labelling: channel " + S.chRED + ", " + S.redThresholdMethod + " threshold x "
                    + Macro.n2s(S.redThresholdFactor) + ", bright cap " + capR;
            if (S.dblMode != 1) sR = sR + ", overlap >= " + Macro.n2s(S.minOverlapPercent) + " %";
            sR = sR + ", background " + Macro.n2s(S.redBgRolling) + " px, blur " + Macro.n2s(S.redBlurSigma) + " px";
            if (S.tiffMode) sR = sR.replace("channel " + S.chRED, "files '" + S.kwRed + "'");
            s.append(sR).append("\n");
            String sM = "   scored as: " + Settings.DBL_MODES[S.dblMode];
            if (S.dblMode == 1) {
                String wsO = S.objWatershed ? "yes" : "no";
                sM = sM + " - objects of " + Macro.n2s(S.objMinArea) + " to " + Macro.n2s(S.objMaxArea) + " um2 (split: " + wsO
                        + "), centre at most " + Macro.n2s(S.objTol) + " um outside the parasite, at least "
                        + Macro.n2s(S.objMinN) + " per parasite, counted: " + Settings.POS_MODES[S.posMode];
            }
            if (S.dblMode == 2)
                sM = sM + " - objects of " + Macro.n2s(S.encMinArea) + " to " + Macro.n2s(S.encMaxArea) + " um2";
            if (S.dblMode == 1 || S.dblPolesUsed)
                sM = sM + "\n   places along the parasite: centre = central " + Macro.n2s(S.centrePct)
                        + " % of its length, extremity and pole = last " + Macro.n2s(S.endPct) + " % at each end";
            s.append(sM).append("\n");
            if (S.dblPolesUsed) {
                String pr = Settings.POLE_REFS[S.poleRef].toLowerCase();
                String sP = "   poles: oriented by " + pr;
                if (S.poleNeedMk) {
                    String ps = Settings.POLE_SIDES[S.poleSide].toLowerCase();
                    sP = sP + "; pole marker channel " + S.chPOLE + " (marks " + ps + "), threshold x "
                            + Macro.n2s(S.pmThresholdFactor) + ", objects " + Macro.n2s(S.pmMinArea) + " to "
                            + Macro.n2s(S.pmMaxArea) + " um2";
                    if (S.tiffMode) sP = sP.replace("channel " + S.chPOLE, "files '" + S.kwPole + "'");
                }
                if (S.poleNeedNuc)
                    sP = sP + "; parasite nucleus " + Macro.n2s(S.pnMinArea) + " to " + Macro.n2s(S.pnMaxArea) + " um2";
                sP = sP + "; no orientation within " + Macro.n2s(S.poleDeadPct) + " % of the length from the centre";
                s.append(sP).append("\n");
            }
        }
        if (S.autoDetectChannels && !S.tiffMode)
            s.append("   channels found in each file by the colour they have in the first file (the numbers above if that fails)\n");
        String pxMode = "from the image, " + Macro.n2s(S.pixelSize) + " um if it has none";
        if (S.pixelForce) pxMode = Macro.n2s(S.pixelSize) + " um on every image";
        s.append("   pixel size: ").append(pxMode).append("\n");
        s.append("   reported measure: ").append(S.measureLabel).append("\n");
        if (S.doQC) {
            s.append("   QC display: range adapted to each image, x ").append(Macro.n2s(S.qcGainBlue)).append(" (nuclei), x ")
                    .append(Macro.n2s(S.qcGainGreen)).append(" (parasites), x ").append(Macro.n2s(S.qcGainRed))
                    .append(" (marker); display only\n");
            String qp = "";
            if (S.countNuclei && S.qcPanN) qp = qp + ", nuclei";
            if (S.countGreen && S.qcPanP) qp = qp + ", parasites";
            if (S.countDouble && S.qcPanR) qp = qp + ", marker";
            if (S.countDouble && S.qcPanRG) qp = qp + ", marker + parasites";
            if (S.dblPolesUsed && S.qcPanPol) qp = qp + ", poles";
            if (S.countNuclei && S.countGreen && S.qcPanAll) qp = qp + ", all channels";
            if (S.qcRaw) qp = qp + "; each channel also raw";
            if (qp.isEmpty()) qp = ", none (no QC stack)";
            s.append("   QC stack panels: ").append(qp.substring(2)).append("\n");
        }
        return s.toString();
    }

    // ================================================================== the images, several at once
    /** Images processed at once: as set, or from the processors and the memory. */
    int threadsFor(Job.Result first) {
        int cores = Math.max(1, Runtime.getRuntime().availableProcessors());
        if (ImagePlus.getStaticGlobalCalibration() != null) return 1;   // one calibration shared by every image
        if (S.parallel > 0) return Math.max(1, Math.min(S.parallel, 64));
        long max = Runtime.getRuntime().maxMemory();
        long per = first != null && first.bytes > 0 ? first.bytes * 6 + (64L << 20) : 512L << 20;
        long budget = (long) (max * 0.6);
        int byMem = (int) Math.max(1, budget / per);
        return Math.max(1, Math.min(cores, byMem));
    }

    private final Map<Integer, Job.Result> done = new HashMap<Integer, Job.Result>();
    private final Object doneLock = new Object();

    void processAll() {
        int n = entries.size();
        if (n == 0) return;
        Job job = new Job(this);
        // the first image alone: what an image takes in memory decides how many run at once
        Job.Result first = safeProcess(job, 0);
        commit(first);
        IJ.showProgress(1, n);
        if (n == 1) return;
        if (stopAsked()) return;
        threadsUsed = threadsFor(first);
        final AtomicInteger next = new AtomicInteger(1);
        final Semaphore window = new Semaphore(threadsUsed * 2);
        List<Thread> workers = new ArrayList<Thread>();
        for (int t = 0; t < threadsUsed; t++) {
            Thread th = new Thread(new Runnable() {
                public void run() {
                    Job jb = new Job(Run.this);
                    while (!cancelled) {
                        try {
                            window.acquire();
                        } catch (InterruptedException e) {
                            return;
                        }
                        int i = next.getAndIncrement();
                        if (i >= entries.size()) {
                            window.release();
                            return;
                        }
                        Job.Result r;
                        if (targetReachedFor(i)) {
                            r = new Job.Result();
                            r.index = i;
                        } else r = safeProcess(jb, i);
                        synchronized (doneLock) {
                            done.put(i, r);
                            doneLock.notifyAll();
                        }
                    }
                }
            }, "Count and Colocalise " + (t + 1));
            th.setDaemon(true);
            th.setPriority(Thread.NORM_PRIORITY - 1);
            workers.add(th);
            th.start();
        }
        for (int i = 1; i < n; i++) {
            Job.Result r;
            synchronized (doneLock) {
                while (!done.containsKey(i)) {
                    try {
                        doneLock.wait(200);
                    } catch (InterruptedException e) {
                        cancelled = true;
                        return;
                    }
                    if (cancelled || stopAsked()) return;
                }
                r = done.remove(i);
            }
            commit(r);
            window.release();
            IJ.showProgress(i + 1, n);
            IJ.showStatus("Count and Colocalise: " + (i + 1) + " / " + n + " image(s)   (Esc stops)");
            if (stopAsked()) return;
        }
        IJ.showProgress(1.0);
    }

    private Job.Result safeProcess(Job job, int i) {
        try {
            return job.process(i);
        } catch (Throwable ex) {
            Job.Result r = new Job.Result();
            r.index = i;
            Input.Entry e = entries.get(i);
            r.manip = e.manip;
            r.cond = e.cond;
            r.base = e.base(S.tiffMode);
            r.log.add("[" + (i + 1) + "/" + entries.size() + "] " + e.manip + " / " + e.cond + " / " + r.base);
            r.log.add("   ERROR: " + ex + " -> image skipped");
            if (ex instanceof OutOfMemoryError) r.log.add("   (not enough memory: lower 'Images processed at once')");
            return r;
        }
    }

    /** True when the condition of image i has already reached the stop target (it is then not processed). */
    private boolean targetReachedFor(int i) {
        if (S.stopTarget <= 0) return false;
        Input.Entry e = entries.get(i);
        String key = e.manip + "|" + e.cond;
        synchronized (stopKeys) {
            int k = stopKeys.indexOf(key);
            return k >= 0 && stopDone.get(k) >= S.stopTarget;
        }
    }

    /** Esc pressed (interactive runs): the run stops after the images being processed. */
    boolean stopAsked() {
        if (echo && IJ.escapePressed()) cancelled = true;
        return cancelled;
    }

    int committed = 0;

    /** The result of image i, in list order: the stop target, the Log, the rows. */
    void commit(Job.Result r) {
        committed++;
        int i = r.index;
        Input.Entry e = entries.get(i);
        int n = entries.size();
        String manip = e.manip, cond = e.cond, base = e.base(S.tiffMode);
        int sKey = -1;
        if (S.stopTarget > 0) {
            String key2 = manip + "|" + cond;
            synchronized (stopKeys) {
                sKey = stopKeys.indexOf(key2);
                if (sKey < 0) {
                    stopKeys.add(key2);
                    stopLab.add(manip.equals("-") ? cond : manip + " / " + cond);
                    stopDone.add(0.0);
                    stopImgs.add(0);
                    stopSkip.add(0);
                    sKey = stopKeys.size() - 1;
                }
                if (stopDone.get(sKey) >= S.stopTarget) {
                    stopSkip.set(sKey, stopSkip.get(sKey) + 1);
                    log("[" + (i + 1) + "/" + n + "] " + manip + " / " + cond + " / " + base
                            + "   not counted: target already reached");
                    for (File f : r.files) f.delete();
                    return;
                }
            }
        }
        for (int q = 0; q < r.log.size(); q++) {
            if (q == r.pixelNoteAt && !calibShown) {
                log(Input.pixelNoteText(S));
                calibShown = true;
            }
            String ln = r.log.get(q);
            if (ln.equals(PX_PLACEHOLDER)) ln = "   pixel size " + Macro.d2s(lastPx, 4) + " um (" + lastPxSource + ")";
            log(ln);
        }
        if (r.px != null) {
            lastPx = r.px.w;
            lastPxH = r.px.h;
            lastPxSource = r.px.source;
        }
        if (r.pixelNoteAt == r.log.size() && !calibShown) {
            log(Input.pixelNoteText(S));
            calibShown = true;
        }
        if (!r.row) return;
        summaryManip.add(r.manip);
        summaryCond.add(r.cond);
        summaryNames.add(r.base);
        summaryCells.add(r.cells);
        summaryGreen.add(r.green);
        summaryColoc.add(r.coloc);
        summaryObj.add(r.obj);
        summaryOri.add(r.ori);
        summaryBas.add(r.bas);
        summaryApi.add(r.api);
        summaryInd.add(r.ind);
        summaryMid.add(r.mid);
        summaryUse.add(r.use);
        summaryWhy.add(r.why);
        summaryKey.add(sKey);
        if (r.hasPerPar) perPar.add(r.perPar);
        if (sKey >= 0 && r.use == 1) {
            synchronized (stopKeys) {
                double add = S.stopOn.equals("parasites") ? r.green : r.cells;
                stopDone.set(sKey, stopDone.get(sKey) + add);
                stopImgs.set(sKey, stopImgs.get(sKey) + 1);
                if (stopDone.get(sKey) >= S.stopTarget)
                    log("   target reached for " + stopLab.get(sKey) + ": " + Macro.n2s(stopDone.get(sKey)) + " " + stopWhatTxt
                            + " in " + stopImgs.get(sKey) + " image(s)");
            }
        }
    }

    // ================================================================== after the loop
    void afterLoop() {
        // images under the minimum, set aside: back where the target needs them
        if (lowOn && S.lowMode == 1 && S.stopTarget > 0) {
            for (int sq = 0; sq < stopKeys.size(); sq++) {
                if (stopDone.get(sq) >= S.stopTarget) continue;
                List<Integer> cand = new ArrayList<Integer>();
                List<Double> cv = new ArrayList<Double>();
                for (int t = 0; t < summaryUse.size(); t++) {
                    if (summaryKey.get(t) != sq) continue;
                    if (summaryUse.get(t) != 0) continue;
                    cand.add(t);
                    cv.add(S.stopOn.equals("parasites") ? summaryGreen.get(t) : summaryCells.get(t));
                }
                for (int a = 0; a < cand.size() - 1; a++) {
                    int best = a;
                    for (int b = a + 1; b < cand.size(); b++) {
                        if (cv.get(b) > cv.get(best)) best = b;
                        else if (cv.get(b).doubleValue() == cv.get(best).doubleValue() && cand.get(b) < cand.get(best)) best = b;
                    }
                    if (best != a) {
                        int sw = cand.get(a);
                        cand.set(a, cand.get(best));
                        cand.set(best, sw);
                        double sv = cv.get(a);
                        cv.set(a, cv.get(best));
                        cv.set(best, sv);
                    }
                }
                for (int q = 0; q < cand.size(); q++) {
                    if (stopDone.get(sq) >= S.stopTarget) continue;
                    summaryUse.set(cand.get(q), 2);
                    stopDone.set(sq, stopDone.get(sq) + cv.get(q));
                    stopImgs.set(sq, stopImgs.get(sq) + 1);
                }
            }
        }
        if (S.stopTarget > 0) {
            log("");
            log("Stop early, target " + Macro.n2s(S.stopTarget) + " " + stopWhatTxt + " per condition:");
            for (int sq = 0; sq < stopKeys.size(); sq++) {
                int nLowU = 0, nLowO = 0;
                for (int t = 0; t < summaryUse.size(); t++) {
                    if (summaryKey.get(t) != sq) continue;
                    if (summaryUse.get(t) == 2) nLowU++;
                    if (summaryUse.get(t) == 0) nLowO++;
                }
                String sLine = "   " + stopLab.get(sq) + ": " + Macro.n2s(stopDone.get(sq)) + " " + stopWhatTxt + " in "
                        + stopImgs.get(sq) + " image(s)";
                if (nLowU > 0) sLine = sLine + " (" + nLowU + " of them under the minimum, needed for the target)";
                if (stopSkip.get(sq) > 0) sLine = sLine + ", " + stopSkip.get(sq) + " image(s) left out";
                if (nLowO > 0) sLine = sLine + ", " + nLowO + " under the minimum left out";
                if (stopDone.get(sq) < S.stopTarget) sLine = sLine + "   TARGET NOT REACHED - not enough images";
                log(sLine);
            }
        }
        if (lowOn) {
            int nLowOut = 0, nLowUsed = 0;
            for (int u : summaryUse) {
                if (u == 0) nLowOut++;
                if (u == 2) nLowUsed++;
            }
            log("");
            String lowHead = "Images with " + lowRule + ": " + nLowOut + " left out";
            if (S.lowMode == 1 && S.stopTarget > 0) lowHead = lowHead + ", " + nLowUsed + " counted to reach the target";
            log(lowHead);
            for (int t = 0; t < summaryUse.size(); t++) {
                if (summaryUse.get(t) == 1) continue;
                String labT = summaryCond.get(t) + " / " + summaryNames.get(t);
                if (!summaryManip.get(t).equals("-")) labT = summaryManip.get(t) + " / " + labT;
                String fate = " - left out";
                if (summaryUse.get(t) == 2) fate = " - counted, needed for the target";
                log("   " + labT + ": " + summaryWhy.get(t) + fate);
            }
        }
    }

    void writeLog() {
        try {
            Files.write(new File(outputRoot + "Log.txt").toPath(), logText().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            IJ.log("Count and Colocalise: could not write the Log: " + ex);
        }
    }

    static boolean blackBackground() {
        return Prefs.blackBackground;
    }
}
