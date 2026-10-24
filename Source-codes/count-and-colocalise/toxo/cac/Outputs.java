package toxo.cac;

import ij.IJ;
import ij.ImagePlus;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;

import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The result files of a run, as the macro version writes them: Raw data.csv, one CSV per condition,
 * Parasites.csv, the workbook (SpreadsheetML .xls, live formulas), the histograms, the last lines of the Log and
 * Log.txt.
 */
final class Outputs {

    final Run R;
    final Settings S;
    int nRows, nM, nC;
    boolean singleManip;
    final List<String> manipList = new ArrayList<String>();
    final List<String> condsAll = new ArrayList<String>();
    double[] aggCells, aggGreen, aggColoc, aggN, aggAll;
    boolean hasControl;
    int[] gOf;
    int gCount;
    int[] ordStart, ordIdx;

    private Outputs(Run r) {
        this.R = r;
        this.S = r.S;
    }

    static void write(Run r) {
        Outputs o = new Outputs(r);
        o.assemble();
        o.files();
    }

    // ================================================================== result assembly
    void assemble() {
        nRows = R.summaryNames.size();
        for (int t = 0; t < nRows; t++) if (!manipList.contains(R.summaryManip.get(t))) manipList.add(R.summaryManip.get(t));
        Input.sortCI(manipList);
        nM = manipList.size();
        singleManip = nM == 1 && manipList.get(0).equals("-");
        for (int t = 0; t < nRows; t++) if (!condsAll.contains(R.summaryCond.get(t))) condsAll.add(R.summaryCond.get(t));
        nC = condsAll.size();
        hasControl = false;
        for (String c : condsAll) if (isControlName(c)) hasControl = true;
        aggCells = new double[nM * nC];
        aggGreen = new double[nM * nC];
        aggColoc = new double[nM * nC];
        aggN = new double[nM * nC];
        aggAll = new double[nM * nC];
        for (int t = 0; t < nRows; t++) {
            int mi = manipList.indexOf(R.summaryManip.get(t));
            int ci = condsAll.indexOf(R.summaryCond.get(t));
            if (mi < 0 || ci < 0) continue;
            if (isExcluded(R.summaryManip.get(t), R.summaryCond.get(t))) continue;
            int k = mi * nC + ci;
            aggAll[k]++;
            if (R.summaryUse.get(t) == 0) continue;
            aggCells[k] += R.summaryCells.get(t);
            aggGreen[k] += R.summaryGreen.get(t);
            aggColoc[k] += R.summaryColoc.get(t);
            aggN[k]++;
        }
        // display order: controls first, then increasing concentration
        double[] sk = new double[nC];
        for (int c = 0; c < nC; c++) {
            double num = firstNumber(condsAll.get(c));
            if (num < 0) num = 99999;
            sk[c] = isControlName(condsAll.get(c)) ? num : 1000000000 + num;
        }
        for (int a = 0; a < nC - 1; a++) {
            int mn = a;
            for (int b = a + 1; b < nC; b++) if (sk[b] < sk[mn]) mn = b;
            if (mn != a) {
                String sw = condsAll.get(a);
                condsAll.set(a, condsAll.get(mn));
                condsAll.set(mn, sw);
                double sv = sk[a];
                sk[a] = sk[mn];
                sk[mn] = sv;
                for (int m = 0; m < nM; m++) {
                    int i1 = m * nC + a, i2 = m * nC + mn;
                    swap(aggCells, i1, i2);
                    swap(aggGreen, i1, i2);
                    swap(aggColoc, i1, i2);
                    swap(aggN, i1, i2);
                    swap(aggAll, i1, i2);
                }
            }
        }
        gOf = new int[nC];
        gCount = 0;
        for (int c = 0; c < nC; c++) {
            if (c == 0) {
                gOf[0] = 0;
                gCount = 1;
            } else if (sameGroup(condsAll.get(c - 1), condsAll.get(c))) gOf[c] = gOf[c - 1];
            else {
                gOf[c] = gCount;
                gCount++;
            }
        }
        ordStart = new int[nM * nC];
        ordIdx = new int[nRows];
        int pos = 0;
        for (int m = 0; m < nM; m++)
            for (int c = 0; c < nC; c++) {
                ordStart[m * nC + c] = pos;
                for (int t = 0; t < nRows; t++) {
                    if (!R.summaryManip.get(t).equals(manipList.get(m))) continue;
                    if (!R.summaryCond.get(t).equals(condsAll.get(c))) continue;
                    if (R.summaryUse.get(t) == 0) continue;
                    ordIdx[pos++] = t;
                }
            }
    }

    private static void swap(double[] a, int i, int j) {
        double v = a[i];
        a[i] = a[j];
        a[j] = v;
    }

    boolean isExcluded(String manip, String cond) {
        for (String x : R.exclList) if (x.equals(cond) || x.equals(manip + "/" + cond)) return true;
        return false;
    }

    static boolean isControlName(String name) {
        String l = name.toLowerCase(Locale.US);
        return l.contains("control") || l.contains("controle") || l.contains("ctrl") || l.contains("temoin");
    }

    /** First number contained in a string, -1 if none (a comma counts as a decimal point). */
    static double firstNumber(String s) {
        StringBuilder buf = new StringBuilder();
        boolean started = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                buf.append(c);
                started = true;
            } else if (started && (c == '.' || c == ',')) buf.append('.');
            else if (started) break;
        }
        String b = buf.toString();
        if (b.isEmpty()) return -1;
        if (b.endsWith(".")) b = b.substring(0, b.length() - 1);
        return Macro.parseFloat(b);
    }

    static boolean sameGroup(String a, String b) {
        boolean ca = isControlName(a), cb = isControlName(b);
        if (ca && cb) return true;
        if (ca != cb) return false;
        double na = firstNumber(a), nb = firstNumber(b);
        if (na < 0 || nb < 0) return false;
        return na == nb;
    }

    String groupLabel(int g) {
        String lab = "";
        boolean started = false;
        for (int c = 0; c < nC; c++) {
            if (gOf[c] != g) continue;
            if (!started) {
                lab = condsAll.get(c);
                started = true;
            } else lab = commonPrefix(lab, condsAll.get(c));
        }
        lab = trimEnd(lab);
        if (lab.length() < 2) {
            for (int c = 0; c < nC; c++) if (gOf[c] == g) return condsAll.get(c);
        }
        return lab;
    }

    static String commonPrefix(String a, String b) {
        int n = Math.min(a.length(), b.length());
        int k = 0;
        for (int q = 0; q < n; q++) {
            if (a.charAt(q) == b.charAt(q)) k = q + 1;
            else break;
        }
        return a.substring(0, k);
    }

    static String trimEnd(String s) {
        while (s.length() > 0) {
            char c = s.charAt(s.length() - 1);
            if (c == ' ' || c == '_' || c == '-') s = s.substring(0, s.length() - 1);
            else return s;
        }
        return s;
    }

    // ================================================================== files
    void files() {
        String sep = File.separator;
        String csvEnd = R.lowOn ? ",Counted\n" : "\n";
        String xHead = csvExtraHead();
        StringBuilder csv = new StringBuilder("Experiment,Condition,Image,Host_cells,Parasites,Double_labelled,Single_labelled,Measure"
                + xHead + csvEnd);
        for (int m = 0; m < nM; m++)
            for (int c = 0; c < nC; c++)
                for (int t = 0; t < nRows; t++) {
                    if (!R.summaryManip.get(t).equals(manipList.get(m))) continue;
                    if (!R.summaryCond.get(t).equals(condsAll.get(c))) continue;
                    double pv = S.measImg(R.summaryCells.get(t), R.summaryGreen.get(t), R.summaryColoc.get(t));
                    csv.append(Run.csvQ(R.summaryManip.get(t))).append(',').append(Run.csvQ(R.summaryCond.get(t))).append(',')
                            .append(Run.csvQ(R.summaryNames.get(t))).append(',').append(csvCounts(t)).append(',')
                            .append(Macro.num2(pv)).append(csvExtra(t));
                    if (R.lowOn) csv.append(',').append(usedTxt(t));
                    csv.append('\n');
                }
        save(csv.toString(), R.outputRoot + "Raw data.csv");

        for (int m = 0; m < nM; m++)
            for (int c = 0; c < nC; c++) {
                if (aggAll[m * nC + c] == 0) continue;
                StringBuilder sub = new StringBuilder("Image,Host_cells,Parasites,Double_labelled,Single_labelled,Measure" + xHead
                        + csvEnd);
                for (int t = 0; t < nRows; t++) {
                    if (!R.summaryManip.get(t).equals(manipList.get(m))) continue;
                    if (!R.summaryCond.get(t).equals(condsAll.get(c))) continue;
                    double pv = S.measImg(R.summaryCells.get(t), R.summaryGreen.get(t), R.summaryColoc.get(t));
                    sub.append(Run.csvQ(R.summaryNames.get(t))).append(',').append(csvCounts(t)).append(',')
                            .append(Macro.num2(pv)).append(csvExtra(t));
                    if (R.lowOn) sub.append(',').append(usedTxt(t));
                    sub.append('\n');
                }
                String subDir;
                if (manipList.get(m).equals("-")) subDir = R.dataRoot + condsAll.get(c) + sep;
                else subDir = R.dataRoot + manipList.get(m) + sep + condsAll.get(c) + sep;
                new File(subDir).mkdirs();
                save(sub.toString(), subDir + "Raw data - " + condsAll.get(c) + ".csv");
            }

        if (R.perPar.size() > 0) {
            String pHead = "Experiment,Condition,Image,Parasite,Counts_as,Area_um2,Length_um,Double_labelled,"
                    + "Double_labelled_number";
            if (S.dblMode == 1) pHead = pHead + ",Marker_objects_on_it,Marker_objects_counted";
            if (S.dblPolesUsed && S.dblMode != 2)
                pHead = pHead + ",Oriented,Nuclei_inside,Pole_marker_objects,Pole_of_the_marker,"
                        + "Marker_place_pct_from_basal_end_if_oriented";
            StringBuilder p = new StringBuilder(pHead).append('\n');
            for (String s : R.perPar) p.append(s);
            save(p.toString(), R.outputRoot + "Parasites.csv");
        }

        if (S.doWorkbook) Workbook.write(this, R.outputRoot + "Final results " + R.rootName + ".xls");

        if (S.doHistExp && nC > 0) {
            if (S.tiffMode) {
                for (int m = 0; m < nM; m++) Histograms.drawOne(this, R.outputRoot + "Histogram " + manipList.get(m) + ".png", m);
            } else Histograms.drawAll(this, R.outputRoot + "Histogram " + R.rootName + ".png");
        }
        if (S.doHistPooled && nC > 0 && !singleManip && nM > 1) {
            if (S.tiffMode)
                R.log("Pooled histogram skipped: coverslips of different slides are "
                        + "not the same condition, so they cannot be pooled.");
            else Histograms.drawPooled(this, R.outputRoot + "Histogram pooled " + R.rootName + ".png");
        }

        if (R.lowOn) {
            int nCounted = 0;
            for (int u : R.summaryUse) if (u > 0) nCounted++;
            R.log("Done. " + nRows + " image(s), " + nCounted + " counted and " + (nRows - nCounted) + " left out with "
                    + R.lowRule + ", " + nM + " experiment(s), " + nC + " condition(s).");
        } else R.log("Done. " + nRows + " image(s), " + nM + " experiment(s), " + nC + " condition(s).");
        if (R.exclList.size() > 0) R.log(R.exclList.size() + " condition(s) excluded from the calculations.");
        R.log("Reported measure: " + S.measureLabel);
        if (!S.measureNote.isEmpty()) R.log(S.measureNote);
        R.log("Output: " + R.outputRoot);
        R.writeLog();
    }

    static void save(String txt, String path) {
        try {
            Files.write(new File(path).toPath(), txt.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            IJ.log("Count and Colocalise: could not write " + path + ": " + e);
        }
    }

    String csvCounts(int t) {
        String a = "", b = "", c = "", d = "";
        if (S.countNuclei) a = Macro.n2s(R.summaryCells.get(t));
        if (S.countGreen) {
            b = Macro.n2s(R.summaryGreen.get(t));
            d = Macro.n2s(R.summaryGreen.get(t) - R.summaryColoc.get(t));
        }
        if (S.countDouble) c = Macro.n2s(R.summaryColoc.get(t));
        return a + "," + b + "," + c + "," + d;
    }

    String csvExtraHead() {
        String s = "";
        if (S.countDouble && S.dblMode == 1) s = s + ",Marker_objects_counted";
        if (S.dblPolesUsed && S.dblMode != 2)
            s = s + ",Oriented_parasite_objects,Basal_pole,Apical_pole,Pole_indeterminable,"
                    + "Pole_sum,Not_at_a_pole,Basal_pole_pct,Apical_pole_pct,Pole_indeterminable_pct," + "Pole_sum_pct";
        return s;
    }

    String csvExtra(int t) {
        StringBuilder s = new StringBuilder();
        if (S.countDouble && S.dblMode == 1) s.append(',').append(Macro.n2s(R.summaryObj.get(t)));
        if (S.dblPolesUsed && S.dblMode != 2) {
            double d = R.summaryColoc.get(t), nb = R.summaryBas.get(t), na = R.summaryApi.get(t), ni = R.summaryInd.get(t),
                    nm = R.summaryMid.get(t);
            double ns = nb + na + ni;
            s.append(',').append(Macro.n2s(R.summaryOri.get(t))).append(',').append(Macro.n2s(nb)).append(',')
                    .append(Macro.n2s(na)).append(',').append(Macro.n2s(ni)).append(',').append(Macro.n2s(ns)).append(',')
                    .append(Macro.n2s(nm)).append(',').append(pctTxt(nb, d)).append(',').append(pctTxt(na, d)).append(',')
                    .append(pctTxt(ni, d)).append(',').append(pctTxt(ns, d));
        }
        return s.toString();
    }

    static String pctTxt(double n, double d) {
        if (d == 0) return "";
        return Macro.num2(100 * n / d);
    }

    String usedTxt(int t) {
        int u = R.summaryUse.get(t);
        if (u == 1) return "yes";
        if (u == 2) return "yes - " + R.summaryWhy.get(t) + " but needed for the target";
        return "no - " + R.summaryWhy.get(t);
    }

    // ================================================================== histograms
    static final class Histograms {
        final Outputs O;
        final List<String> pName = new ArrayList<String>();
        final List<Double> pPct = new ArrayList<Double>(), pSD = new ArrayList<Double>(), pCells = new ArrayList<Double>(),
                pPar = new ArrayList<Double>(), pNf = new ArrayList<Double>(), pNm = new ArrayList<Double>();
        int[] pStart, pCount, pGlob;
        String[] pTitle;

        Histograms(Outputs o, int nPan) {
            O = o;
            pStart = new int[nPan];
            pCount = new int[nPan];
            pGlob = new int[nPan];
            pTitle = new String[nPan];
            for (int i = 0; i < nPan; i++) pTitle[i] = "0";
        }

        void fillPanel(int pIdx, int m) {
            Run R = O.R;
            Settings S = O.S;
            int nCh2 = O.nC;
            pStart[pIdx] = pName.size();
            pGlob[pIdx] = 0;
            if (!O.singleManip) pTitle[pIdx] = "EXP " + (m + 1) + "  -  " + O.manipList.get(m);
            else pTitle[pIdx] = O.manipList.get(m);
            int cnt = 0;
            for (int c = 0; c < nCh2; c++) {
                int kk = m * nCh2 + c;
                if (O.aggN[kk] == 0) continue;
                double cc = O.aggCells[kk], pp = O.aggGreen[kk], dd = O.aggColoc[kk], nn = O.aggN[kk];
                List<Double> vals = new ArrayList<Double>();
                for (int t = 0; t < O.nRows; t++) {
                    if (!R.summaryManip.get(t).equals(O.manipList.get(m))) continue;
                    if (!R.summaryCond.get(t).equals(O.condsAll.get(c))) continue;
                    if (R.summaryUse.get(t) == 0) continue;
                    if (S.normalizeToCells && R.summaryCells.get(t) <= 0) continue;
                    if (S.measureUse == 3 && R.summaryGreen.get(t) <= 0) continue;
                    vals.add(S.measImg(R.summaryCells.get(t), R.summaryGreen.get(t), R.summaryColoc.get(t)));
                }
                double pct = S.measBar(cc, pp, dd, nn);
                double sd = 0;
                if (vals.size() > 1) sd = arrayStd(vals);
                pName.add(O.condsAll.get(c));
                pPct.add(pct);
                pSD.add(sd);
                pCells.add(cc);
                pPar.add(pp);
                pNf.add(nn);
                pNm.add(0.0);
                cnt++;
            }
            pCount[pIdx] = cnt;
        }

        /** Array.getStatistics(a, min, max, mean, std): the sample standard deviation. */
        static double arrayStd(List<Double> a) {
            int n = a.size();
            double sum = 0, sum2 = 0;
            for (double v : a) {
                sum += v;
                sum2 += v * v;
            }
            double stdDev = (n * sum2 - sum * sum) / n;
            return Math.sqrt(stdDev / (n - 1.0));
        }

        static void drawAll(Outputs o, String path) {
            int nMh = o.nM;
            if (nMh == 0 || o.nC == 0) return;
            Histograms h = new Histograms(o, nMh);
            for (int m = 0; m < nMh; m++) h.fillPanel(m, m);
            h.render(path);
        }

        static void drawOne(Outputs o, String path, int m) {
            if (o.nC == 0) return;
            Histograms h = new Histograms(o, 1);
            h.fillPanel(0, m);
            if (h.pCount[0] == 0) return;
            h.render(path);
        }

        static void drawPooled(Outputs o, String path) {
            int nMh = o.nM;
            if (nMh == 0 || o.gCount == 0) return;
            Settings S = o.S;
            Histograms h = new Histograms(o, 1);
            h.pStart[0] = 0;
            h.pGlob[0] = 1;
            h.pTitle[0] = "ALL EXPERIMENTS  (n = " + nMh + ")";
            int cnt = 0;
            for (int g = 0; g < o.gCount; g++) {
                double cc = 0, pp = 0, dd = 0, nn = 0, nmc = 0;
                List<Double> mv = new ArrayList<Double>();
                for (int mm = 0; mm < nMh; mm++) {
                    double xc = 0, xp = 0, xd = 0, xn = 0;
                    for (int c = 0; c < o.nC; c++) {
                        if (o.gOf[c] != g) continue;
                        int kk = mm * o.nC + c;
                        xc += o.aggCells[kk];
                        xp += o.aggGreen[kk];
                        xd += o.aggColoc[kk];
                        xn += o.aggN[kk];
                    }
                    if (xn == 0) continue;
                    nmc++;
                    cc += xc;
                    pp += xp;
                    dd += xd;
                    nn += xn;
                    boolean okV = true;
                    if (S.normalizeToCells && xc <= 0) okV = false;
                    if (S.measureUse == 3 && xp <= 0) okV = false;
                    if (okV) mv.add(S.measBar(xc, xp, xd, xn));
                }
                if (nmc == 0) continue;
                double pct = S.measBar(cc, pp, dd, nn);
                double sd = 0;
                if (mv.size() > 1) sd = arrayStd(mv);
                h.pName.add(o.groupLabel(g));
                h.pPct.add(pct);
                h.pSD.add(sd);
                h.pCells.add(cc);
                h.pPar.add(pp);
                h.pNf.add(nn);
                h.pNm.add(nmc);
                cnt++;
            }
            h.pCount[0] = cnt;
            h.render(path);
        }

        private static void font(ImageProcessor ip, int size, boolean bold) {
            ip.setFont(new Font("SansSerif", bold ? Font.BOLD : Font.PLAIN, size));
            ip.setAntialiasedText(QC.antialias(size));
        }

        private static void text(ImageProcessor ip, String s, double x, double y) {
            ip.drawString(s, (int) (x + 0.5), (int) (y + 0.5));
        }

        private static void line(ImageProcessor ip, double x1, double y1, double x2, double y2) {
            ip.drawLine((int) Math.round(x1), (int) Math.round(y1), (int) Math.round(x2), (int) Math.round(y2));
        }

        void render(String outPath) {
            Settings S = O.S;
            int nPan = pStart.length;
            int nBars = pName.size();
            if (nBars == 0) return;
            double maxV = 0;
            for (int u = 0; u < nBars; u++) {
                double top = pPct.get(u) + pSD.get(u);
                if (top > maxV) maxV = top;
            }
            if (maxV <= 0) maxV = 1;
            maxV = maxV * 1.15;
            double step = 1;
            int mant = 1;
            while (maxV / step > 5) {
                if (mant == 1) {
                    mant = 2;
                    step = step * 2;
                } else if (mant == 2) {
                    mant = 5;
                    step = (step / 2) * 5;
                } else {
                    mant = 1;
                    step = step * 2;
                }
            }
            int nTicks = 0;
            while (nTicks * step < maxV) nTicks++;
            maxV = nTicks * step;
            int slot = 118, axisW = 62, padR = 22, gap = 40, mt = 46, mb = 150, ph = 380;
            double totalW = 0;
            for (int m = 0; m < nPan; m++) {
                double pw = axisW + slot * pCount[m] + padR;
                if (pCount[m] == 0) pw = 0;
                totalW += pw;
                if (m < nPan - 1) totalW += gap;
            }
            double W = totalW + 40;
            if (W < 700) W = 700;
            int H = mt + ph + mb;
            ColorProcessor ip = new ColorProcessor((int) W, H);
            ip.setColor(Color.white);
            ip.fill();
            ip.setColor(new Color(0, 0, 0));
            font(ip, 12, true);
            ip.setJustification(ImageProcessor.LEFT_JUSTIFY);
            String sdTxt = pGlob[0] == 1 ? "between experiments" : "field to field";
            text(ip, "Bars: " + S.measureLabel + "   -   error bars: SD " + sdTxt, 20, H - 14);
            ip.setJustification(ImageProcessor.CENTER_JUSTIFY);
            double xCur = 20;
            for (int m = 0; m < nPan; m++) {
                if (pCount[m] == 0) continue;
                double plotL = xCur + axisW;
                double plotW = slot * pCount[m];
                double yTop = mt, yBot = mt + ph;
                ip.setColor(new Color(0, 0, 0));
                font(ip, 14, true);
                ip.setJustification(ImageProcessor.CENTER_JUSTIFY);
                text(ip, pTitle[m], plotL + plotW / 2, mt - 22);
                ip.setLineWidth(2);
                line(ip, plotL, yTop, plotL, yBot);
                line(ip, plotL, yBot, plotL + plotW, yBot);
                ip.setLineWidth(1);
                font(ip, 12, false);
                ip.setJustification(ImageProcessor.RIGHT_JUSTIFY);
                for (int g = 0; g <= nTicks; g++) {
                    double yv = step * g;
                    double yy = yBot - Math.round(ph * g / (double) nTicks);
                    ip.setColor(new Color(205, 205, 205));
                    line(ip, plotL + 1, yy, plotL + plotW, yy);
                    ip.setColor(new Color(0, 0, 0));
                    line(ip, plotL - 6, yy, plotL, yy);
                    text(ip, Macro.n2s(yv), plotL - 11, yy + 5);
                }
                ip.setJustification(ImageProcessor.CENTER_JUSTIFY);
                double bw = Math.round(slot * 0.52);
                for (int q = 0; q < pCount[m]; q++) {
                    int idx = pStart[m] + q;
                    double bx = Math.round(plotL + slot * q + (slot - bw) / 2);
                    double bh = Math.round(ph * pPct.get(idx) / maxV);
                    if (pGlob[m] == 1) ip.setColor(new Color(105, 105, 105));
                    else ip.setColor(new Color(150, 150, 150));
                    ip.setRoi((int) bx, (int) (yBot - bh), (int) bw, (int) bh);
                    ip.fill();
                    ip.resetRoi();
                    ip.setColor(new Color(70, 70, 70));
                    ip.setLineWidth(1);
                    ip.drawRect((int) bx, (int) (yBot - bh), (int) bw, (int) bh);
                    double cx = bx + Math.round(bw / 2);
                    double yVal;
                    if (pSD.get(idx) > 0) {
                        double hiV = pPct.get(idx) + pSD.get(idx);
                        double loV = pPct.get(idx) - pSD.get(idx);
                        if (loV < 0) loV = 0;
                        double yHi = yBot - Math.round(ph * hiV / maxV);
                        double yLo = yBot - Math.round(ph * loV / maxV);
                        ip.setColor(new Color(40, 40, 40));
                        ip.setLineWidth(2);
                        line(ip, cx, yHi, cx, yLo);
                        line(ip, cx - 8, yHi, cx + 8, yHi);
                        line(ip, cx - 8, yLo, cx + 8, yLo);
                        yVal = yHi - 8;
                    } else yVal = yBot - bh - 8;
                    ip.setColor(new Color(0, 0, 0));
                    font(ip, 13, true);
                    text(ip, Macro.d2s(pPct.get(idx), 2), cx, yVal);
                    font(ip, 12, true);
                    String[] wl = wrapLabel(ip, pName.get(idx), slot - 10);
                    text(ip, wl[0], cx, yBot + 22);
                    if (!wl[1].isEmpty()) text(ip, wl[1], cx, yBot + 38);
                    font(ip, 11, false);
                    ip.setColor(new Color(60, 60, 60));
                    text(ip, "SD = " + Macro.d2s(pSD.get(idx), 2), cx, yBot + 60);
                    ip.setColor(new Color(105, 105, 105));
                    if (pGlob[m] == 1) {
                        text(ip, "n = " + Macro.n2s(pNm.get(idx)) + " exp.", cx, yBot + 78);
                        if (S.normalizeToCells) text(ip, Macro.n2s(pCells.get(idx)) + " cells", cx, yBot + 94);
                        else if (S.measureUse == 3) text(ip, Macro.n2s(pPar.get(idx)) + " parasites", cx, yBot + 94);
                        else text(ip, Macro.n2s(pNf.get(idx)) + " fields", cx, yBot + 94);
                    } else {
                        if (S.normalizeToCells) {
                            text(ip, "n = " + Macro.n2s(pCells.get(idx)) + " cells", cx, yBot + 78);
                            text(ip, "over " + Macro.n2s(pNf.get(idx)) + " fields", cx, yBot + 94);
                        } else if (S.measureUse == 3) {
                            text(ip, "n = " + Macro.n2s(pPar.get(idx)) + " parasites", cx, yBot + 78);
                            text(ip, "over " + Macro.n2s(pNf.get(idx)) + " fields", cx, yBot + 94);
                        } else {
                            text(ip, "mean per field", cx, yBot + 78);
                            text(ip, "n = " + Macro.n2s(pNf.get(idx)) + " fields", cx, yBot + 94);
                        }
                    }
                }
                xCur = plotL + plotW + padR + gap;
            }
            ImagePlus imp = new ImagePlus("Histogram", ip);
            IJ.saveAs(imp, "png", outPath);
        }

        /** A label on two lines when wider than maxW (the current font). */
        static String[] wrapLabel(ImageProcessor ip, String txt, int maxW) {
            String[] out = {txt, ""};
            if (ip.getStringWidth(txt) <= maxW) return out;
            String[] parts = Input.split(txt, " ");
            if (parts.length < 2) return out;
            int k = 1;
            for (int q = 1; q < parts.length; q++) {
                StringBuilder cand = new StringBuilder();
                for (int z = 0; z < q; z++) {
                    if (z > 0) cand.append(' ');
                    cand.append(parts[z]);
                }
                if (ip.getStringWidth(cand.toString()) <= maxW) k = q;
            }
            StringBuilder a = new StringBuilder(), b = new StringBuilder();
            for (int z = 0; z < parts.length; z++) {
                if (z < k) {
                    if (z > 0) a.append(' ');
                    a.append(parts[z]);
                } else {
                    if (z > k) b.append(' ');
                    b.append(parts[z]);
                }
            }
            out[0] = a.toString();
            out[1] = b.toString();
            return out;
        }
    }
}
