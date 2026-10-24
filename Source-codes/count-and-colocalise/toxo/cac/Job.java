package toxo.cac;

import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Roi;
import ij.io.FileSaver;
import ij.io.RoiEncoder;
import ij.measure.Calibration;
import ij.plugin.ChannelSplitter;

import java.awt.Color;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * One image of the batch, processed as the loop of the macro version processes it: opening, first time point and
 * Z projection, pixel size, channels, nuclei, parasites, double labelling and poles, the QC stack, the ROI files,
 * and the lines of the Log. Everything an image needs is its own, so several images run at once; the Log lines
 * and the counts are handed back, and committed in the order of the list.
 */
final class Job {

    /** What one image gives. */
    static final class Result {
        int index;
        final List<String> log = new ArrayList<String>();
        /** Position in log where the pixel-size note goes, when this image would print it (-1: never). */
        int pixelNoteAt = -1;
        boolean row;                 // a row of results (else: skipped, missing, error)
        String manip, cond, base;
        double cells, green, coloc, obj, ori, bas, api, ind, mid;
        int use = 1;
        String why = "";
        String perPar = "";
        boolean hasPerPar;
        final List<File> files = new ArrayList<File>();   // written, removed again if the image is not counted
        long bytes;
        Input.Px px;                 // the pixel size applied last by this image (null: none)
    }

    final Run R;
    final Settings S;

    Job(Run run) {
        this.R = run;
        this.S = run.S;
    }

    /** Processes entry i (0-based). */
    Result process(int i) {
        Result res = new Result();
        res.index = i;
        Input.Entry e = R.entries.get(i);
        String manip = e.manip, cond = e.cond;
        String base = e.base(S.tiffMode);
        int n = R.entries.size();
        res.manip = manip;
        res.cond = cond;
        res.base = base;
        List<String> log = res.log;
        String sep = File.separator;
        String stackFolder, dataFolder;
        if (manip.equals("-")) {
            stackFolder = R.stacksRoot + cond + sep;
            dataFolder = R.dataRoot + cond + sep;
        } else {
            mkdir(R.stacksRoot + manip + sep);
            mkdir(R.dataRoot + manip + sep);
            stackFolder = R.stacksRoot + manip + sep + cond + sep;
            dataFolder = R.dataRoot + manip + sep + cond + sep;
        }
        mkdir(stackFolder);
        mkdir(dataFolder);

        log.add("[" + (i + 1) + "/" + n + "] " + manip + " / " + cond + " / " + base);

        // the channels of this image, under the roles they play
        ImagePlus blue = null, green = null, red = null, pole = null;
        int nCh = 0;
        Input.Px px = null;
        int uBLUE = S.chBLUE, uGREEN = S.chGREEN, uRED = S.chRED, uDIC = S.chDIC;
        String chFrom = "the channels given in the Channels dialog";

        if (S.tiffMode) {
            if (S.countNuclei || S.poleNeedNuc) {
                blue = Input.openTiffChannel(e.tBlue, base + "_BLUE");
                if (blue != null) px = pixel(blue, res);
            }
            if (S.countGreen) {
                green = Input.openTiffChannel(e.tGreen, base + "_GREEN");
                if (green != null) px = pixel(green, res);
            }
            if (S.countDouble) {
                red = Input.openTiffChannel(e.tRed, base + "_RED");
                if (red != null) px = pixel(red, res);
            }
            if (S.poleNeedMk) {
                pole = Input.openTiffChannel(e.tPole, base + "_POLE");
                if (pole != null) px = pixel(pole, res);
            }
        } else {
            if (!new File(e.path).exists()) {
                log.add("   MISSING FILE -> image skipped");
                return res;
            }
            Input.Opened o = Input.openFile(S, e);
            log.addAll(o.log);
            if (o.imp == null) {
                log.add("   ERROR: " + o.err + " -> image skipped");
                return res;
            }
            ImagePlus original = o.imp;
            res.bytes = (long) original.getSizeInBytes();
            int opC = original.getNChannels(), opZ = original.getNSlices(), opT = original.getNFrames();
            log.add("   opened: " + opC + " channel(s), " + opZ + " Z plane(s), " + opT + " time point(s)");
            if (opT > 1) log.add("   NOTE: a time series: only its first time point is counted");
            ImagePlus projected = Input.countView(original);
            px = pixel(projected, res);
            nCh = projected.getNChannels();
            if (S.autoDetectChannels) {
                String[] cols = Input.colours(projected);
                int[] dc = Input.rolesFromCols(cols, R.roleCols, S.dcSkip);
                boolean okDet = true;
                if (S.countNuclei && dc[0] == 0) okDet = false;
                if (S.countGreen && dc[1] == 0) okDet = false;
                if (S.countDouble && dc[2] == 0) okDet = false;
                if (okDet) {
                    uBLUE = dc[0];
                    uGREEN = dc[1];
                    uRED = dc[2];
                    uDIC = dc[3];
                    chFrom = "found by their colour in this file";
                    log.add("   channels found by their colour: nuclear stain " + uBLUE + ", parasite label " + uGREEN
                            + ", second marker " + uRED + ", DIC " + uDIC);
                } else {
                    log.add("   channels could not be told from the colours"
                            + " -> using the channel numbers given in the dialog");
                }
            }
            if (nCh > 1) {
                ImagePlus[] ch = ChannelSplitter.split(projected);
                boolean[] taken = new boolean[ch.length + 1];
                // the pole marker first: always the channel number given
                if (S.poleNeedMk && S.chPOLE > 0 && S.chPOLE <= ch.length) {
                    pole = ch[S.chPOLE - 1];
                    taken[S.chPOLE] = true;
                }
                if (uDIC > 0 && uDIC <= ch.length && !taken[uDIC]) taken[uDIC] = true;
                if (uRED > 0 && uRED <= ch.length && !taken[uRED]) {
                    red = ch[uRED - 1];
                    taken[uRED] = true;
                }
                if (uGREEN > 0 && uGREEN <= ch.length && !taken[uGREEN]) {
                    green = ch[uGREEN - 1];
                    taken[uGREEN] = true;
                }
                if (uBLUE > 0 && uBLUE <= ch.length && !taken[uBLUE]) {
                    blue = ch[uBLUE - 1];
                    taken[uBLUE] = true;
                }
            } else {
                if (uBLUE == 1) blue = projected;
                else if (uGREEN == 1) green = projected;
                else if (uRED == 1) red = projected;
            }
            if (blue != null) blue.setTitle(base + "_BLUE");
            if (green != null) green.setTitle(base + "_GREEN");
            if (red != null) red.setTitle(base + "_RED");
            if (pole != null) pole.setTitle(base + "_POLE");
        }

        if (px != null) {
            log.add("   pixel size " + Macro.d2s(px.w, 4) + " um (" + px.source + ")");
            res.px = px;
        } else log.add(Run.PX_PLACEHOLDER);   // no channel opened: the pixel size last applied, at commit

        // the channels this count needs
        String miss = "";
        if (S.countNuclei && blue == null) miss = miss + "  nuclear stain";
        if (S.countGreen && green == null) miss = miss + "  parasite label";
        if (S.countDouble && red == null) miss = miss + "  second marker";
        if (!miss.isEmpty()) {
            if (S.tiffMode) log.add("   ERROR: no file for" + miss + " -> image skipped");
            else log.add("   ERROR: channel missing:" + miss + "  (the file has " + nCh + " channel(s): check the Channels dialog)"
                    + " -> image skipped");
            return res;
        }
        if (!S.countNuclei && !S.poleNeedNuc) blue = null;
        if (!S.countGreen) green = null;
        if (!S.countDouble) red = null;
        double pxUsed = px.w, pxUsedH = px.h;
        String pxSource = px.source;

        // annotation sizes, scaled to the image (as in the Calibrate preview)
        int imgW = S.countNuclei ? blue.getWidth() : green.getWidth();
        int labelFS = (int) Math.max(10, Math.round(imgW / 90.0 * R.labelScale));
        int recapFS = (int) Math.max(10, Math.round(imgW / 90.0 * R.recapScale));
        int lineW = (int) Math.max(2, Math.round(imgW / 500.0 * R.strokeScale));

        double nucleiCount = 0, greenCount = 0, colocCount = 0;
        int nucleiROIs = 0, nEdgeOut = 0, parObj = 0;
        int[] doubletIdx = new int[0], fragIdx = new int[0], parClus = new int[0], colocIndices = new int[0];
        double[] poleN = new double[5];
        double poleOri = 0, objUsed = 0;
        StringBuilder thrInfo = new StringBuilder();
        List<ImagePlus> panels = new ArrayList<ImagePlus>();
        List<String> plabs = new ArrayList<String>();
        File nucleiZip = new File(dataFolder + base + "_nuclei_ROIs.zip");
        File edgeZip = new File(dataFolder + base + "_nuclei_border_excluded_ROIs.zip");
        List<Roi> nucleiRois = new ArrayList<Roi>();
        List<Roi> edgeOut = new ArrayList<Roi>();
        Detect d = new Detect(S);

        // ================================================================== nuclei
        if (S.countNuclei) {
            String sn = Detect.satCheck(blue);
            if (!sn.isEmpty()) log.add("   nuclear stain: " + sn);
            ImagePlus nMask = d.segmentNuclei(blue, "Nucleus_mask_" + base);
            log.addAll(d.log);
            d.log.clear();
            if (!d.thrNote.isEmpty()) log.add("   nuclei threshold: " + d.thrNote);
            thrInfo.append("nuclei threshold ").append(Macro.nfmt(d.nucThr));
            if (!d.thrNote.isEmpty()) thrInfo.append(" (").append(d.thrNote).append(")");
            thrInfo.append("\n");

            if (S.nucleiEdgeMode > 0) {
                nEdgeOut = d.removeBorderNuclei(nMask, S.nucleiEdgeMode, edgeOut);
                if (nEdgeOut > 0) {
                    saveRois(edgeOut, edgeZip);
                    res.files.add(edgeZip);
                    log.add("   nuclei at the border, not counted: " + nEdgeOut);
                }
            }
            nucleiROIs = d.rm.size();
            nucleiCount = d.countCells(blue);
            doubletIdx = d.cellClusterIdx;
            fragIdx = d.cellFragIdx;
            if (d.cellMedian > 0) {
                String fragTxt = "";
                if (S.nucleiFragFactor > 0) fragTxt = ", " + fragIdx.length + " fragments";
                log.add("   nuclei " + nucleiROIs + " objects, median " + Macro.d2s(d.cellMedian, 1) + " um2, "
                        + doubletIdx.length + " clusters" + fragTxt + " -> " + Macro.n2s(nucleiCount) + " cells");
            } else log.add("   nuclei = " + Macro.n2s(nucleiCount));
            nucleiRois.addAll(d.rm);
            if (nucleiROIs > 0) {
                saveRois(nucleiRois, nucleiZip);
                res.files.add(nucleiZip);
            }
            if (nucleiROIs == 0 && nucleiZip.exists()) nucleiZip.delete();
            if (nEdgeOut == 0 && edgeZip.exists()) edgeZip.delete();

            ImagePlus[] rejN = null;
            if (S.doQC && S.qcPanN) rejN = d.rejectMasks(nMask, nucleiROIs, nEdgeOut > 0 ? edgeOut : null, S.nucleiMinArea,
                    Double.POSITIVE_INFINITY);

            if (S.doQC && S.qcRaw) {
                ImagePlus dup = QC.qcCopy(blue, "BLUE_rdup_" + base, S.qcGainBlue, "Blue");
                ImagePlus raw = Cmd.flattenClean(dup, base + "_BLUE_raw");
                QC.drawTop(raw, base + "  -  nuclear stain", 1, "ffffff", recapFS);
                panels.add(raw);
                plabs.add("BLUE - raw");
            }
            if (S.doQC && S.qcPanN) {
                int[] nucleiIdx = QC.seq(nucleiROIs);
                ImagePlus dup = QC.qcCopy(blue, "BLUE_dup_" + base, S.qcGainBlue, "Blue");
                ImagePlus qc = Cmd.flattenClean(dup, base + "_BLUE_QC");
                QC.drawMask(rejN[0], qc, R.colRejS, lineW);
                QC.drawMask(rejN[1], qc, R.colRejC, lineW);
                if (nEdgeOut > 0) QC.drawROIs(qc, edgeOut, QC.seq(edgeOut.size()), R.colEdge, lineW);
                QC.drawROIs(qc, nucleiRois, nucleiIdx, R.colNuc, lineW);
                QC.drawROIs(qc, nucleiRois, doubletIdx, R.colNClu, lineW);
                QC.drawROIs(qc, nucleiRois, fragIdx, R.colNFrag, lineW);
                QC.labelROIs(qc, nucleiRois, nucleiIdx, R.colNuc, labelFS, 0);
                QC.drawTop(qc, base, 1, "ffffff", recapFS);
                QC.drawTop(qc, R.legNuclei(1), 2, "ffffff", recapFS);
                QC.drawTop(qc, R.legNuclei(2), 3, "ffffff", recapFS);
                String recapB = "Cells = " + Macro.n2s(nucleiCount) + "   (" + nucleiROIs + " nuclei, " + doubletIdx.length
                        + " clusters";
                if (S.nucleiFragFactor > 0) recapB = recapB + ", " + fragIdx.length + " fragments";
                if (S.nucleiEdgeMode > 0) recapB = recapB + ", " + nEdgeOut + " cut by the border left out";
                recapB = recapB + ")";
                QC.drawRecap(qc, recapB, R.colNuc, recapFS);
                panels.add(qc);
                plabs.add("BLUE - nuclei");
            }
        }

        // ================================================================== parasites
        List<Roi> parRois = new ArrayList<Roi>();
        if (S.countGreen) {
            String sn = Detect.satCheck(green);
            if (!sn.isEmpty()) log.add("   parasite label: " + sn);
            int[] nOut = new int[1];
            ImagePlus pMask = d.segmentParasites(green, "Parasite_mask_" + base, nOut);
            log.addAll(d.log);
            d.log.clear();
            parObj = nOut[0];
            String thrG = "   green threshold " + Macro.n2s(d.parThr) + " (auto " + Macro.nfmt(d.parThrAuto) + " x "
                    + Macro.n2s(S.greenThresholdFactor) + ")";
            if (!d.thrNote.isEmpty()) thrG = thrG + "; " + d.thrNote;
            log.add(thrG);
            thrInfo.append("parasite threshold ").append(Macro.n2s(d.parThr)).append(" (automatic ")
                    .append(Macro.nfmt(d.parThrAuto)).append(" x ").append(Macro.n2s(S.greenThresholdFactor));
            if (!d.thrNote.isEmpty()) thrInfo.append("; ").append(d.thrNote);
            thrInfo.append(")\n");
            greenCount = d.countParasites(green);
            parClus = d.parClusterIdx;
            if (d.parMedian > 0)
                log.add("   green parasites " + parObj + " objects, median " + Macro.d2s(d.parMedian, 1) + " um2, "
                        + parClus.length + " clusters -> " + Macro.n2s(greenCount) + " parasites");
            else log.add("   green parasites = " + Macro.n2s(greenCount));
            parRois.addAll(d.rm);

            ImagePlus[] rejP = null;
            if (S.doQC && S.qcPanP) rejP = d.rejectMasks(pMask, parObj, null, S.greenMinArea, S.greenMaxArea);

            if (S.doQC && S.qcRaw) {
                ImagePlus dup = QC.qcCopy(green, "GREEN_rdup_" + base, S.qcGainGreen, "Green");
                ImagePlus raw = Cmd.flattenClean(dup, base + "_GREEN_raw");
                QC.drawTop(raw, base + "  -  parasite label", 1, "ffffff", recapFS);
                panels.add(raw);
                plabs.add("GREEN - raw");
            }
            if (S.doQC && S.qcPanP) {
                int[] greenIdx = QC.seq(parObj);
                ImagePlus dup = QC.qcCopy(green, "GREEN_dup_" + base, S.qcGainGreen, "Green");
                ImagePlus qc = Cmd.flattenClean(dup, base + "_GREEN_QC");
                QC.drawMask(rejP[0], qc, R.colRejS, lineW);
                QC.drawMask(rejP[1], qc, R.colRejC, lineW);
                QC.drawROIs(qc, parRois, greenIdx, R.colPar, lineW);
                QC.drawROIs(qc, parRois, parClus, R.colPClu, lineW);
                QC.labelROIs(qc, parRois, greenIdx, R.colPar, labelFS, 0);
                QC.drawTop(qc, base, 1, "ffffff", recapFS);
                QC.drawTop(qc, R.legParasites(1), 2, "ffffff", recapFS);
                QC.drawTop(qc, R.legParasites(2), 3, "ffffff", recapFS);
                String recapG = "Parasites = " + Macro.n2s(greenCount);
                if (S.splitBigParasites) recapG = recapG + "   (" + parObj + " objects, " + parClus.length + " clusters)";
                QC.drawRecap(qc, recapG, R.colPar, recapFS);
                panels.add(qc);
                plabs.add("GREEN - parasites");
            }
        }

        // ================================================================== double labelling, poles
        if (S.countDouble) {
            String sn = Detect.satCheck(red);
            if (!sn.isEmpty()) log.add("   second marker: " + sn);
            ImagePlus rMask = d.markerPre(red, "Red_mask_" + base);
            d.markerMask(rMask);
            String thrR = "   red threshold " + Macro.n2s(d.dblThr) + " (auto " + Macro.nfmt(d.dblThrAuto) + " x "
                    + Macro.n2s(S.redThresholdFactor) + ")";
            if (!d.thrNote.isEmpty()) thrR = thrR + "; " + d.thrNote;
            log.add(thrR);
            thrInfo.append("marker threshold ").append(Macro.n2s(d.dblThr)).append(" (automatic ")
                    .append(Macro.nfmt(d.dblThrAuto)).append(" x ").append(Macro.n2s(S.redThresholdFactor));
            if (!d.thrNote.isEmpty()) thrInfo.append("; ").append(d.thrNote);
            thrInfo.append(")\n");
            ImagePlus refB = S.poleNeedNuc ? blue : null;
            ImagePlus refP = S.poleNeedMk ? pole : null;
            if (S.poleNeedNuc && refB == null)
                log.add("   NOTE: no nuclear stain: the parasites cannot be oriented by their nucleus");
            if (S.poleNeedMk && refP == null)
                log.add("   NOTE: no pole marker channel: the parasites cannot be oriented by it");
            // the parasites are rm 0 to parObj-1, as the green ROIs stay loaded in the macro
            d.rm.clear();
            d.rm.addAll(parRois);
            colocCount = d.scoreDoubleAll(rMask, parObj, rMask, refB, refP);
            log.addAll(d.log);
            d.log.clear();
            colocIndices = d.dblIdx;
            if (S.dblMode == 0) {
                log.add("   double positives = " + Macro.n2s(colocCount) + " / " + Macro.n2s(greenCount) + "   (>=20% : "
                        + Macro.n2s(d.dblN20) + ", >=35% : " + Macro.n2s(d.dblN35) + ", >=55% : " + Macro.n2s(d.dblN55) + ")");
            } else if (S.dblMode == 1) {
                log.add("   marker objects " + Macro.n2s(d.objN) + ", " + Macro.n2s(d.objOn) + " on a parasite, "
                        + Macro.n2s(d.objUsed) + " counted (" + Settings.POS_MODES[S.posMode] + ") -> double positives = "
                        + Macro.n2s(colocCount) + " / " + Macro.n2s(greenCount));
            } else {
                log.add("   marker objects " + Macro.n2s(d.objN) + " of " + Macro.n2s(S.encMinArea) + " to "
                        + Macro.n2s(S.encMaxArea) + " um2 -> parasites inside one = " + Macro.n2s(colocCount) + " / "
                        + Macro.n2s(greenCount));
            }
            poleN = d.poleN;
            poleOri = d.poleOri;
            objUsed = d.objUsed;
            if (S.dblPolesUsed && S.dblMode != 2) {
                String oriL = "   poles: " + Macro.n2s(poleOri) + " of " + parObj + " parasite object(s) oriented";
                if (!d.poleNote.isEmpty()) oriL = oriL + " (not oriented: " + d.poleNote + ")";
                log.add(oriL);
                double pSum = poleN[1] + poleN[2] + poleN[3];
                String poleTxt = "marker at the basal pole " + Macro.n2s(poleN[1]) + ", apical pole " + Macro.n2s(poleN[2])
                        + ", pole indeterminable " + Macro.n2s(poleN[3]) + " (sum " + Macro.n2s(pSum) + " of "
                        + Macro.n2s(colocCount) + " double-labelled), not at a pole " + Macro.n2s(poleN[4]);
                log.add("   " + poleTxt);
                thrInfo.append("Poles: ").append(poleTxt).append("; ").append(Macro.n2s(poleOri)).append(" of ")
                        .append(parObj).append(" parasite object(s) oriented\n");
            }
            int[] oKind = new int[d.oX.length];
            for (int k = 0; k < d.oX.length; k++) {
                if (d.oOwn[k] >= 0) oKind[k] = 1;
                if (d.oUse[k] == 1) oKind[k] = 2;
            }
            boolean qcD = S.doQC && (S.qcPanR || S.qcPanRG);
            ImagePlus mkA = null;
            if (qcD) mkA = Detect.apMask(rMask, "size=" + Macro.n2s(d.mkSpeck()) + "-Infinity", "MkArea_" + base);
            ImagePlus encQC = null;
            if (qcD && S.dblMode == 2) encQC = d.enclosingMask(rMask);

            if (S.dblMode == 1 || (S.dblPolesUsed && S.dblMode != 2)) {
                res.perPar = R.parasiteLines(d, manip, cond, base, parObj);
                res.hasPerPar = true;
            }

            List<Integer> oth = new ArrayList<Integer>();
            boolean[] isDbl = new boolean[parObj];
            for (int z : colocIndices) isDbl[z] = true;
            for (int z = 0; z < parObj; z++) if (!isDbl[z]) oth.add(z);
            int[] othIdx = Detect.toInt(oth);
            String recapR = "Double-labelled = " + Macro.n2s(colocCount) + " / " + Macro.n2s(greenCount);
            if (S.dblMode == 0)
                recapR = recapR + "   (20 / 35 / 55 % overlap: " + Macro.n2s(d.dblN20) + " / " + Macro.n2s(d.dblN35) + " / "
                        + Macro.n2s(d.dblN55) + ")";
            if (S.dblMode == 1)
                recapR = recapR + "   (objects: " + Macro.n2s(d.objUsed) + " counted, " + Macro.n2s(d.objOn)
                        + " on a parasite, " + Macro.n2s(d.objN) + " found)";
            if (S.dblMode == 2) recapR = recapR + "   (" + Macro.n2s(d.objN) + " marker objects of the size given)";

            if (S.doQC && S.qcRaw) {
                ImagePlus dup = QC.qcCopy(red, "RED_rdup_" + base, S.qcGainRed, "Red");
                ImagePlus raw = Cmd.flattenClean(dup, base + "_RED_raw");
                QC.drawTop(raw, base + "  -  marker", 1, "ffffff", recapFS);
                panels.add(raw);
                plabs.add("RED - raw");
            }
            for (int pv = 0; pv < 2; pv++) {
                if (pv == 0 && (!S.doQC || !S.qcPanR)) continue;
                if (pv == 1 && (!S.doQC || !S.qcPanRG)) continue;
                ImagePlus dPan;
                if (pv == 0) {
                    ImagePlus dup = QC.qcCopy(red, "RED_dup_" + base, S.qcGainRed, "Red");
                    dPan = Cmd.flattenClean(dup, base + "_RED_QC");
                } else {
                    ImagePlus rd = QC.qcCopy(red, base + "_RED_disp", S.qcGainRed, "");
                    ImagePlus gd = QC.qcCopy(green, base + "_GREEN_disp", S.qcGainGreen, "");
                    ImagePlus m = Cmd.mergeCreateKeep(new ImagePlus[]{rd, gd});
                    dPan = Cmd.flattenClean(Cmd.rgbOfMerge(m), base + "_Merge_RG_QC");
                }
                QC.drawMask(mkA, dPan, R.colMk, lineW);
                if (S.dblMode == 2) QC.drawMask(encQC, dPan, "ffff00", lineW);
                QC.drawROIs(dPan, parRois, othIdx, R.colOth, lineW);
                QC.drawROIs(dPan, parRois, colocIndices, R.colDbl, lineW);
                if (S.dblMode == 1) {
                    int lw2 = (int) Math.max(1, Math.round(lineW / 2.0));
                    QC.drawObjects(dPan, d.oX, d.oY, d.oR, oKind, 0, new Color(130, 130, 130), lw2);
                    QC.drawObjects(dPan, d.oX, d.oY, d.oR, oKind, 1, new Color(255, 255, 0), lw2);
                    QC.drawObjects(dPan, d.oX, d.oY, d.oR, oKind, 2, new Color(255, 255, 255), lw2);
                }
                QC.labelROIs(dPan, parRois, colocIndices, R.colDbl, labelFS, 0);
                QC.drawTop(dPan, base, 1, "ffffff", recapFS);
                QC.drawTop(dPan, R.legDouble(1), 2, "ffffff", recapFS);
                QC.drawTop(dPan, R.legDouble(2), 3, "ffffff", recapFS);
                QC.drawRecap(dPan, recapR, R.colDbl, recapFS);
                panels.add(dPan);
                plabs.add(pv == 0 ? "RED - double-labelled" : "MERGE RED+GREEN");
            }

            if (S.doQC && S.qcPanPol && S.dblPolesUsed && S.dblMode != 2) {
                ImagePlus rd = QC.qcCopy(red, base + "_RED_disp3", S.qcGainRed, "");
                ImagePlus gd = QC.qcCopy(green, base + "_GREEN_disp3", S.qcGainGreen, "");
                ImagePlus bd = refB != null ? QC.qcCopy(blue, base + "_BLUE_disp3", S.qcGainBlue, "") : null;
                ImagePlus m = Cmd.mergeCreateKeep(new ImagePlus[]{rd, gd, bd});
                ImagePlus polesQC = Cmd.flattenClean(Cmd.rgbOfMerge(m), base + "_Poles_QC");
                List<Integer> othP = new ArrayList<Integer>(), basP = new ArrayList<Integer>(), apiP = new ArrayList<Integer>(),
                        indP = new ArrayList<Integer>(), midP = new ArrayList<Integer>();
                for (int q = 0; q < parObj; q++) {
                    if (d.pCls[q] == 0) othP.add(q);
                    if (d.pCls[q] == 1) basP.add(q);
                    if (d.pCls[q] == 2) apiP.add(q);
                    if (d.pCls[q] == 3) indP.add(q);
                    if (d.pCls[q] == 4) midP.add(q);
                }
                int lw2 = (int) Math.max(1, Math.round(lineW / 2.0));
                QC.drawROIs(polesQC, parRois, Detect.toInt(othP), new Color(130, 130, 130), lw2);
                QC.drawROIs(polesQC, parRois, Detect.toInt(midP), new Color(255, 150, 0), lineW);
                QC.drawROIs(polesQC, parRois, Detect.toInt(indP), new Color(255, 255, 255), lineW);
                QC.drawROIs(polesQC, parRois, Detect.toInt(apiP), new Color(0, 255, 255), lineW);
                QC.drawROIs(polesQC, parRois, Detect.toInt(basP), new Color(255, 0, 255), lineW);
                if (S.dblMode == 1) {
                    QC.drawObjects(polesQC, d.oX, d.oY, d.oR, d.oPole, 4, new Color(255, 150, 0), lw2);
                    QC.drawObjects(polesQC, d.oX, d.oY, d.oR, d.oPole, 3, new Color(255, 255, 255), lw2);
                    QC.drawObjects(polesQC, d.oX, d.oY, d.oR, d.oPole, 2, new Color(0, 255, 255), lw2);
                    QC.drawObjects(polesQC, d.oX, d.oY, d.oR, d.oPole, 1, new Color(255, 0, 255), lw2);
                }
                QC.drawBasalEnds(polesQC, d, parObj, Math.max(3, lineW * 2), new Color(255, 255, 0));
                int[] dblNum = new int[parObj];
                for (int z = 0; z < colocIndices.length; z++) dblNum[colocIndices[z]] = z + 1;
                QC.labelROIs(polesQC, parRois, Detect.toInt(basP), dblNum, new Color(255, 0, 255), labelFS, 0);
                QC.labelROIs(polesQC, parRois, Detect.toInt(apiP), dblNum, new Color(0, 255, 255), labelFS, 0);
                QC.labelROIs(polesQC, parRois, Detect.toInt(indP), dblNum, new Color(255, 255, 255), labelFS, 0);
                QC.labelROIs(polesQC, parRois, Detect.toInt(midP), dblNum, new Color(255, 150, 0), labelFS, 0);
                QC.drawTop(polesQC, base, 1, "ffffff", recapFS);
                QC.drawTop(polesQC, R.legPoles(1), 2, "ffffff", recapFS);
                QC.drawTop(polesQC, R.legPoles(2), 3, "ffffff", recapFS);
                double pSum = poleN[1] + poleN[2] + poleN[3];
                String recapP = "Poles: basal " + Macro.n2s(poleN[1]) + ", apical " + Macro.n2s(poleN[2]) + ", indeterminable "
                        + Macro.n2s(poleN[3]) + " (sum " + Macro.n2s(pSum) + " of " + Macro.n2s(colocCount) + "), not at a pole "
                        + Macro.n2s(poleN[4]) + "   -   " + Macro.n2s(poleOri) + " of " + parObj + " parasite object(s) oriented";
                QC.drawRecap(polesQC, recapP, "ffffff", recapFS);
                panels.add(polesQC);
                plabs.add("POLES - marker at the basal (magenta) / apical (cyan) pole");
            }
        }

        // ================================================================== too few objects
        String lowWhy = "";
        if (S.countNuclei && S.minCellsImg > 0 && nucleiCount < S.minCellsImg)
            lowWhy = "fewer than " + Macro.n2s(S.minCellsImg) + " host cells";
        if (S.countGreen && S.minParImg > 0 && greenCount < S.minParImg) {
            if (!lowWhy.isEmpty()) lowWhy = lowWhy + " and ";
            lowWhy = lowWhy + "fewer than " + Macro.n2s(S.minParImg) + " parasites";
        }
        if (!lowWhy.isEmpty()) {
            String lowNote = "left out of the counts";
            if (S.lowMode == 1 && S.stopTarget > 0)
                lowNote = "set aside, counted only if its condition needs it to reach the target";
            log.add("   " + lowWhy + ": " + lowNote);
        }

        if (S.doQC) {
            if (S.qcPanAll && R.nClasses >= 2) {
                // the nuclei follow the parasite objects in the ROI Manager of the macro
                List<Roi> all = new ArrayList<Roi>(parRois);
                int nucleiOffset = parObj;
                all.addAll(nucleiRois);
                int nucleiInMerge = all.size() - nucleiOffset;
                int[] nucleiMergeIdx = new int[nucleiInMerge];
                for (int q = 0; q < nucleiInMerge; q++) nucleiMergeIdx[q] = nucleiOffset + q;
                int[] greenIdx = QC.seq(parObj);
                ImagePlus rd = S.countDouble ? QC.qcCopy(red, base + "_RED_disp2", S.qcGainRed, "") : null;
                ImagePlus gd = QC.qcCopy(green, base + "_GREEN_disp2", S.qcGainGreen, "");
                ImagePlus bd = QC.qcCopy(blue, base + "_BLUE_disp2", S.qcGainBlue, "");
                ImagePlus m = Cmd.mergeCreateKeep(new ImagePlus[]{rd, gd, bd});
                ImagePlus full = Cmd.flattenClean(Cmd.rgbOfMerge(m), base + "_Merge_Full_QC");
                if (nEdgeOut > 0) QC.drawROIs(full, edgeOut, QC.seq(edgeOut.size()), R.colEdge, lineW);
                QC.drawROIs(full, all, nucleiMergeIdx, R.colNuc, lineW);
                QC.drawROIs(full, all, greenIdx, R.colPar, lineW);
                QC.drawROIs(full, all, colocIndices, R.colDbl, lineW);
                QC.labelROIs(full, all, nucleiMergeIdx, R.colNuc, labelFS, 0);
                QC.labelROIs(full, all, greenIdx, R.colPar, labelFS, 0);
                QC.labelROIs(full, all, colocIndices, R.colDbl, labelFS, labelFS + 2);
                QC.drawTop(full, base, 1, "ffffff", recapFS);
                String lg = "cyan: nuclei   magenta: parasites";
                if (S.countDouble) lg = lg + "   white: double-labelled";
                if (S.nucleiEdgeMode > 0) lg = lg + "   grey: nuclei cut by the border, left out";
                QC.drawTop(full, lg, 2, "ffffff", recapFS);
                String recapM = "Cells = " + Macro.n2s(nucleiCount) + "   Parasites = " + Macro.n2s(greenCount);
                if (S.countDouble) recapM = recapM + "   Double-labelled = " + Macro.n2s(colocCount);
                QC.drawRecap(full, recapM, "ffffff", recapFS);
                panels.add(full);
                plabs.add("MERGE FULL");
            }
            if (!lowWhy.isEmpty() && !panels.isEmpty()) {
                String noteT = "UNDER THE MINIMUM (" + lowWhy + "): left out of the counts";
                if (S.lowMode == 1 && S.stopTarget > 0)
                    noteT = "UNDER THE MINIMUM (" + lowWhy + "): set aside for the target, see the Log";
                QC.drawNote(panels.get(panels.size() - 1), noteT, new Color(255, 170, 0), recapFS);
            }
            if (!panels.isEmpty()) {
                String stackTitle = base + "_QC_Stack";
                ImagePlus st;
                if (panels.size() == 1) {
                    st = panels.get(0);
                    st.setTitle(stackTitle);
                } else st = Cmd.concat(panels.toArray(new ImagePlus[0]), stackTitle);
                ImageStack stack = st.getStack();
                for (int q = 0; q < plabs.size(); q++) stack.setSliceLabel(plabs.get(q), q + 1);
                st.setSlice(1);
                Calibration cal = st.getCalibration();
                cal.pixelWidth = pxUsed;
                cal.pixelHeight = pxUsedH;
                cal.pixelDepth = pxUsed;
                cal.setUnit("micron");
                StringBuilder qi = new StringBuilder();
                qi.append("Count and Colocalise v").append(Settings.VERSION).append(" - QC stack\n");
                qi.append("Source: ").append(e.path).append("\n");
                if (e.seriesTotal > 1)
                    qi.append("Series / position: ").append(e.series).append(" of ").append(e.seriesTotal).append("\n");
                qi.append("Experiment: ").append(manip).append("   Condition: ").append(cond).append("   Image: ").append(base)
                        .append("\n");
                qi.append("Pixel size: ").append(Macro.d2s(pxUsed, 4)).append(" um (").append(pxSource).append(")\n");
                if (!S.tiffMode) {
                    String chL = "";
                    if (S.countNuclei) chL = chL + ", nuclear stain " + uBLUE;
                    if (!S.countNuclei && S.poleNeedNuc) chL = chL + ", nuclear stain " + uBLUE + " (poles)";
                    if (S.countGreen) chL = chL + ", parasite label " + uGREEN;
                    if (S.countDouble) chL = chL + ", second marker " + uRED;
                    if (S.poleNeedMk) chL = chL + ", pole marker " + S.chPOLE;
                    qi.append("Channels: ").append(chL.substring(2)).append("  (").append(chFrom).append(")\n");
                }
                String cntL = "";
                if (S.countNuclei) cntL = cntL + ", " + Macro.n2s(nucleiCount) + " host cells (" + nucleiROIs + " nuclei)";
                if (S.countGreen) cntL = cntL + ", " + Macro.n2s(greenCount) + " parasites (" + parObj + " objects)";
                if (S.countDouble) cntL = cntL + ", " + Macro.n2s(colocCount) + " double-labelled";
                qi.append("Counted: ").append(cntL.substring(2)).append("\n");
                if (S.countDouble) {
                    qi.append("Double labelling: ").append(Settings.DBL_MODES[S.dblMode]);
                    if (S.dblMode == 1)
                        qi.append(" (").append(Macro.n2s(objUsed)).append(" counted of ").append(Macro.n2s(d.objN))
                                .append(" objects)");
                    if (S.dblMode == 2) qi.append(" (").append(Macro.n2s(d.objN)).append(" objects)");
                    qi.append("\n");
                }
                if (!lowWhy.isEmpty()) qi.append("Under the minimum: ").append(lowWhy).append("\n");
                qi.append(thrInfo);
                qi.append("Slices:");
                for (int q = 0; q < plabs.size(); q++) qi.append(" ").append(q + 1).append(" = ").append(plabs.get(q)).append(";");
                qi.append("\n").append(R.setLog);
                st.setProperty("Info", qi.toString());
                File out = new File(stackFolder + stackTitle + ".tif");
                if (st.getStackSize() > 1) new FileSaver(st).saveAsTiffStack(out.getPath());
                else new FileSaver(st).saveAsTiff(out.getPath());
                res.files.add(out);
            }
        }

        res.row = true;
        res.cells = nucleiCount;
        res.green = greenCount;
        res.coloc = colocCount;
        res.obj = objUsed;
        res.ori = poleOri;
        res.bas = poleN[1];
        res.api = poleN[2];
        res.ind = poleN[3];
        res.mid = poleN[4];
        res.use = lowWhy.isEmpty() ? 1 : 0;
        res.why = lowWhy;
        return res;
    }

    /** Pixel size applied to imp; the place of the note in the Log is remembered (the first such image of the
     *  run prints it). */
    private Input.Px pixel(ImagePlus imp, Result res) {
        Input.Px px = Input.applyPixelSize(S, imp);
        if (res.pixelNoteAt < 0 && Input.pixelNote(S, px)) res.pixelNoteAt = res.log.size();
        return px;
    }

    static void mkdir(String d) {
        new File(d).mkdirs();
    }

    /** roiManager("Save", path) of these ROIs: each under its name, in order. */
    static void saveRois(List<Roi> rois, File path) {
        DataOutputStream out = null;
        try {
            ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(path)));
            out = new DataOutputStream(new BufferedOutputStream(zos));
            RoiEncoder re = new RoiEncoder(out);
            Set<String> used = new HashSet<String>();
            int k = 0;
            for (Roi roi : rois) {
                k++;
                String label = roi.getName();
                if (label == null) label = "roi" + k;
                if (!label.endsWith(".roi")) label += ".roi";
                if (used.contains(label)) {
                    String stem = label.substring(0, label.length() - 4);
                    int s = 1;
                    while (used.contains(stem + "-" + s + ".roi")) s++;
                    label = stem + "-" + s + ".roi";
                }
                used.add(label);
                zos.putNextEntry(new ZipEntry(label));
                re.write(roi);
                out.flush();
            }
            out.close();
        } catch (IOException ex) {
            ij.IJ.log("Count and Colocalise: could not save " + path + ": " + ex);
        } finally {
            if (out != null) try {
                out.close();
            } catch (IOException ignored) {
                // nothing
            }
        }
    }
}
