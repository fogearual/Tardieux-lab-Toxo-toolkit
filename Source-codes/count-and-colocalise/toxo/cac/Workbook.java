package toxo.cac;

/**
 * The results workbook (SpreadsheetML .xls that Excel opens), as the macro version writes it: a summary per
 * experiment on top, pointing by live formulas at the TOTAL rows of the per-image detail below (experiments side
 * by side), the images left out, and the poles on a sheet of their own.
 */
final class Workbook {
    private Workbook() {}

    /** A name as XML text: & < > escaped, the line breaks the workbook writes itself (&#10;) kept. */
    static String xmlEsc(String s) {
        s = s.replace("&", "&amp;");
        s = s.replace("&amp;#10;", "&#10;");
        s = s.replace("<", "&lt;");
        s = s.replace(">", "&gt;");
        return s;
    }

    static String hCell(String s) {
        return "<Cell ss:StyleID=\"h\"><Data ss:Type=\"String\">" + xmlEsc(s) + "</Data></Cell>";
    }

    static String sCell(String s) {
        return "<Cell ss:StyleID=\"c\"><Data ss:Type=\"String\">" + xmlEsc(s) + "</Data></Cell>";
    }

    static String nCell(double v) {
        return "<Cell ss:StyleID=\"c\"><Data ss:Type=\"Number\">" + Macro.n2s(v) + "</Data></Cell>";
    }

    static String tCell(String s) {
        return "<Cell ss:StyleID=\"t\"><Data ss:Type=\"String\">" + xmlEsc(s) + "</Data></Cell>";
    }

    static String mCell(String s, int mergeDown) {
        return "<Cell ss:StyleID=\"t\" ss:MergeDown=\"" + mergeDown + "\"><Data ss:Type=\"String\">" + xmlEsc(s)
                + "</Data></Cell>";
    }

    static String sCellIdx(String s, int idx) {
        return "<Cell ss:Index=\"" + idx + "\" ss:StyleID=\"c\"><Data ss:Type=\"String\">" + xmlEsc(s) + "</Data></Cell>";
    }

    static String tCellIdx(String s, int idx) {
        return "<Cell ss:Index=\"" + idx + "\" ss:StyleID=\"t\"><Data ss:Type=\"String\">" + xmlEsc(s) + "</Data></Cell>";
    }

    static String fCell(String st, String f) {
        return "<Cell ss:StyleID=\"" + st + "\" ss:Formula=\"" + f + "\"><Data ss:Type=\"Number\"></Data></Cell>";
    }

    static String xmlHead(int nCols) {
        String b1 = "<Borders>" + "<Border ss:Position=\"Bottom\" ss:LineStyle=\"Continuous\" ss:Weight=\"1\"/>"
                + "<Border ss:Position=\"Left\" ss:LineStyle=\"Continuous\" ss:Weight=\"1\"/>"
                + "<Border ss:Position=\"Right\" ss:LineStyle=\"Continuous\" ss:Weight=\"1\"/>"
                + "<Border ss:Position=\"Top\" ss:LineStyle=\"Continuous\" ss:Weight=\"1\"/></Borders>";
        String b2 = "<Borders>" + "<Border ss:Position=\"Bottom\" ss:LineStyle=\"Continuous\" ss:Weight=\"2\"/>"
                + "<Border ss:Position=\"Left\" ss:LineStyle=\"Continuous\" ss:Weight=\"2\"/>"
                + "<Border ss:Position=\"Right\" ss:LineStyle=\"Continuous\" ss:Weight=\"2\"/>"
                + "<Border ss:Position=\"Top\" ss:LineStyle=\"Continuous\" ss:Weight=\"2\"/></Borders>";
        String al = "<Alignment ss:Horizontal=\"Center\" ss:Vertical=\"Center\" ss:WrapText=\"1\"/>";
        StringBuilder h = new StringBuilder();
        h.append("<?xml version=\"1.0\"?>\n<?mso-application progid=\"Excel.Sheet\"?>\n")
                .append("<Workbook xmlns=\"urn:schemas-microsoft-com:office:spreadsheet\"")
                .append(" xmlns:ss=\"urn:schemas-microsoft-com:office:spreadsheet\">\n<Styles>\n")
                .append("<Style ss:ID=\"h\">").append(al).append(b1).append("<Font ss:Bold=\"1\"/>")
                .append("<Interior ss:Color=\"#E8E8E8\" ss:Pattern=\"Solid\"/></Style>\n")
                .append("<Style ss:ID=\"hm\">").append(al).append(b2).append("<Font ss:Bold=\"1\" ss:Size=\"12\"/>")
                .append("<Interior ss:Color=\"#C6D9F0\" ss:Pattern=\"Solid\"/></Style>\n")
                .append("<Style ss:ID=\"c\">").append(al).append(b1).append("</Style>\n")
                .append("<Style ss:ID=\"p\">").append(al).append(b1).append("<NumberFormat ss:Format=\"0.00\"/></Style>\n")
                .append("<Style ss:ID=\"na\">").append(al).append(b1)
                .append("<Interior ss:Color=\"#D9D9D9\" ss:Pattern=\"Solid\"/></Style>\n")
                .append("<Style ss:ID=\"t\">").append(al).append(b2).append("<Font ss:Bold=\"1\"/>")
                .append("<Interior ss:Color=\"#DCE6F1\" ss:Pattern=\"Solid\"/></Style>\n")
                .append("<Style ss:ID=\"tp\">").append(al).append(b2).append("<Font ss:Bold=\"1\"/>")
                .append("<Interior ss:Color=\"#DCE6F1\" ss:Pattern=\"Solid\"/>")
                .append("<NumberFormat ss:Format=\"0.00\"/></Style>\n</Styles>\n")
                .append("<Worksheet ss:Name=\"Results\">\n<Table>\n");
        for (int q = 0; q < nCols; q++) {
            int w = 80;
            int r8 = q % 8;
            if (r8 == 0) w = 135;
            if (r8 == 1) w = 130;
            if (r8 == 5) w = 110;
            if (r8 == 6) w = 125;
            if (r8 == 7) w = 95;
            h.append("<Column ss:Width=\"").append(w).append("\"/>");
        }
        return h.append("\n").toString();
    }

    static String measureFormula(Settings S) {
        if (S.measureUse == 3) return "=IF(RC[-3]=0,0,RC[-2]/RC[-3]*100)";
        if (S.measureUse == 4) return "=RC[-4]";
        String col = "RC[-1]";
        if (S.measureUse == 1) col = "RC[-3]";
        if (S.measureUse == 2) col = "RC[-2]";
        if (S.normalizeToCells) return "=IF(RC[-4]=0,0," + col + "/RC[-4]*100)";
        return "=" + col;
    }

    static void write(Outputs O, String outPath) {
        Settings S = O.S;
        Run R = O.R;
        String mf = measureFormula(S);
        String hdC = S.countNuclei ? "Host cells" : "Host cells (not counted)";
        String hdP = "Parasites", hdS = "Single-labelled";
        if (!S.countGreen) {
            hdP = "Parasites (not counted)";
            hdS = "Single-labelled (not counted)";
        }
        String hdD = S.countDouble ? "Double-labelled" : "Double-labelled (not counted)";
        int nMw = O.nM, nCw = O.nC, BW = 8, gCount = O.gCount;
        int[] sRowOf = new int[nMw * nCw], grpRowOf = new int[nMw * gCount], gFirst = new int[nMw * gCount],
                gLast = new int[nMw * gCount], ctrlS = new int[nMw], nPresM = new int[nMw];
        int sr = 1;
        for (int m = 0; m < nMw; m++) {
            int tot = 0;
            for (int g = 0; g < gCount; g++) {
                int k2 = 0;
                boolean isCtrlG = false;
                for (int c = 0; c < nCw; c++) {
                    if (O.gOf[c] != g) continue;
                    if (O.aggN[m * nCw + c] == 0) continue;
                    if (Outputs.isControlName(O.condsAll.get(c))) isCtrlG = true;
                    sr++;
                    sRowOf[m * nCw + c] = sr;
                    if (k2 == 0) gFirst[m * gCount + g] = sr;
                    gLast[m * gCount + g] = sr;
                    k2++;
                }
                if (k2 == 0) continue;
                tot += k2;
                if (k2 > 1) {
                    sr++;
                    grpRowOf[m * gCount + g] = sr;
                    tot++;
                } else grpRowOf[m * gCount + g] = 0;
                if (isCtrlG) {
                    if (k2 > 1) ctrlS[m] = grpRowOf[m * gCount + g];
                    else ctrlS[m] = gLast[m * gCount + g];
                }
            }
            nPresM[m] = tot;
            sr++;
        }
        sr++;
        int summaryHeight = sr;
        int[] maxK = new int[nCw], totRel = new int[nCw];
        for (int c = 0; c < nCw; c++) {
            int mk = 0;
            for (int m = 0; m < nMw; m++) if (O.aggN[m * nCw + c] > mk) mk = (int) O.aggN[m * nCw + c];
            maxK[c] = mk;
        }
        int rr = 0;
        if (!O.singleManip) rr++;
        rr++;
        for (int c = 0; c < nCw; c++) {
            if (maxK[c] == 0) continue;
            rr += maxK[c];
            rr++;
            totRel[c] = rr;
            rr++;
        }
        int nCols = nMw * BW;
        if (nCols < 8) nCols = 8;
        StringBuilder x = new StringBuilder(xmlHead(nCols));
        x.append("<Row ss:Height=\"34\">").append(hCell("Experiment")).append(hCell("Condition")).append(hCell(hdC))
                .append(hCell(hdP)).append(hCell(hdD)).append(hCell(hdS)).append(hCell(S.measureLabel));
        if (O.hasControl) x.append(hCell("% of control"));
        x.append("</Row>\n");
        for (int m = 0; m < nMw; m++) {
            int nPres = nPresM[m];
            if (nPres == 0) {
                x.append("<Row></Row>\n");
                continue;
            }
            String labM = O.manipList.get(m);
            if (!O.singleManip) labM = "EXP " + (m + 1) + "&#10;" + O.manipList.get(m);
            boolean first = true;
            int baseD = m * BW + 1;
            for (int g = 0; g < gCount; g++) {
                int k2 = 0;
                for (int c = 0; c < nCw; c++) {
                    if (O.gOf[c] != g) continue;
                    if (O.aggN[m * nCw + c] == 0) continue;
                    int tr = summaryHeight + totRel[c];
                    x.append(sumRowRef(O, first, labM, nPres - 1, O.condsAll.get(c), baseD, tr, ctrlS[m]));
                    first = false;
                    k2++;
                }
                if (k2 > 1) {
                    String gl = O.groupLabel(g) + " (pooled)";
                    x.append(sumRowGroup(O, first, labM, nPres - 1, gl, gFirst[m * gCount + g], gLast[m * gCount + g], ctrlS[m]));
                    first = false;
                }
            }
            x.append("<Row ss:Height=\"10\"></Row>\n");
        }
        x.append("<Row></Row>\n");
        if (!O.singleManip) {
            x.append("<Row ss:Height=\"30\">");
            for (int m = 0; m < nMw; m++)
                x.append("<Cell ss:Index=\"").append(m * BW + 1).append("\" ss:StyleID=\"hm\"")
                        .append(" ss:MergeAcross=\"6\"><Data ss:Type=\"String\">").append("EXP ").append(m + 1).append("  -  ")
                        .append(xmlEsc(O.manipList.get(m))).append("</Data></Cell>");
            x.append("</Row>\n");
        }
        x.append("<Row ss:Height=\"34\">");
        for (int m = 0; m < nMw; m++)
            x.append("<Cell ss:Index=\"").append(m * BW + 1).append("\" ss:StyleID=\"h\">")
                    .append("<Data ss:Type=\"String\">Condition</Data></Cell>").append(hCell("Image")).append(hCell(hdC))
                    .append(hCell(hdP)).append(hCell(hdD)).append(hCell(hdS)).append(hCell(S.measureLabel));
        x.append("</Row>\n");
        for (int c = 0; c < nCw; c++) {
            if (maxK[c] == 0) continue;
            for (int j = 0; j < maxK[c]; j++) {
                x.append("<Row>");
                for (int m = 0; m < nMw; m++) {
                    int baseD = m * BW + 1;
                    int kk = (int) O.aggN[m * nCw + c];
                    if (kk == 0) {
                        x.append("<Cell ss:Index=\"").append(baseD).append("\" ss:StyleID=\"na\"></Cell>");
                        for (int z = 0; z < 6; z++) x.append("<Cell ss:StyleID=\"na\"></Cell>");
                    } else if (j < kk) {
                        int t = O.ordIdx[O.ordStart[m * nCw + c] + j];
                        String nmT = R.summaryNames.get(t);
                        if (R.summaryUse.get(t) == 2) nmT = nmT + " (*)";
                        if (j == 0) {
                            String lab = O.condsAll.get(c);
                            if (!O.singleManip) lab = lab + "&#10;EXP " + (m + 1);
                            x.append("<Cell ss:Index=\"").append(baseD).append("\" ss:StyleID=\"t\"").append(" ss:MergeDown=\"")
                                    .append(maxK[c]).append("\"><Data ss:Type=\"String\">").append(xmlEsc(lab))
                                    .append("</Data></Cell>").append(sCell(nmT));
                        } else x.append(sCellIdx(nmT, baseD + 1));
                        x.append(nCell(R.summaryCells.get(t))).append(nCell(R.summaryGreen.get(t)))
                                .append(nCell(R.summaryColoc.get(t))).append(fCell("c", "=RC[-2]-RC[-1]")).append(fCell("p", mf));
                    } else {
                        x.append("<Cell ss:Index=\"").append(baseD + 1).append("\" ss:StyleID=\"c\"></Cell>");
                        for (int z = 0; z < 5; z++) x.append("<Cell ss:StyleID=\"c\"></Cell>");
                    }
                }
                x.append("</Row>\n");
            }
            x.append("<Row ss:Height=\"28\">");
            for (int m = 0; m < nMw; m++) {
                int baseD = m * BW + 1;
                int kk = (int) O.aggN[m * nCw + c];
                if (kk == 0) {
                    x.append("<Cell ss:Index=\"").append(baseD).append("\" ss:StyleID=\"na\"></Cell>");
                    for (int z = 0; z < 6; z++) x.append("<Cell ss:StyleID=\"na\"></Cell>");
                } else {
                    String sum = "=SUM(R[-" + maxK[c] + "]C:R[-1]C)";
                    x.append(tCellIdx("TOTAL", baseD + 1)).append(fCell("t", sum)).append(fCell("t", sum)).append(fCell("t", sum))
                            .append(fCell("t", "=RC[-2]-RC[-1]")).append(fCell("tp", mf));
                }
            }
            x.append("</Row>\n");
            x.append("<Row ss:Height=\"8\"></Row>\n");
        }
        int nOut = 0, nUsed = 0;
        for (int u : R.summaryUse) {
            if (u == 0) nOut++;
            if (u == 2) nUsed++;
        }
        if (nUsed > 0)
            x.append("<Row><Cell ss:MergeAcross=\"6\"><Data ss:Type=\"String\">(*) ").append(R.lowRule)
                    .append(": counted only because its condition needed it to reach the target").append("</Data></Cell></Row>\n");
        if (nOut > 0) {
            x.append("<Row></Row>\n");
            x.append("<Row ss:Height=\"30\"><Cell ss:StyleID=\"hm\" ss:MergeAcross=\"6\">")
                    .append("<Data ss:Type=\"String\">IMAGES LEFT OUT OF THE COUNTS  -  ").append(R.lowRule)
                    .append("</Data></Cell></Row>\n");
            x.append("<Row ss:Height=\"34\">").append(hCell("Experiment")).append(hCell("Condition")).append(hCell("Image"))
                    .append(hCell(hdC)).append(hCell(hdP)).append(hCell(hdD)).append(hCell("Why")).append("</Row>\n");
            for (int t = 0; t < R.summaryUse.size(); t++) {
                if (R.summaryUse.get(t) != 0) continue;
                x.append("<Row>").append(sCell(R.summaryManip.get(t))).append(sCell(R.summaryCond.get(t)))
                        .append(sCell(R.summaryNames.get(t))).append(nCell(R.summaryCells.get(t)))
                        .append(nCell(R.summaryGreen.get(t))).append(nCell(R.summaryColoc.get(t))).append(sCell(R.summaryWhy.get(t)))
                        .append("</Row>\n");
            }
        }
        x.append("</Table>\n</Worksheet>\n");
        if (S.dblPolesUsed && S.dblMode != 2) x.append(polesSheet(O));
        x.append("</Workbook>\n");
        Outputs.save(x.toString(), outPath);
    }

    static String polesSheet(Outputs O) {
        Run R = O.R;
        int nMw = O.nM, nCw = O.nC;
        StringBuilder x = new StringBuilder("<Worksheet ss:Name=\"Poles\">\n<Table>\n");
        int[] wd = {135, 130, 130, 95, 80, 80, 100, 75, 85, 80, 80, 100, 75, 100};
        for (int w : wd) x.append("<Column ss:Width=\"").append(w).append("\"/>");
        x.append("\n<Row ss:Height=\"46\">").append(hCell("Experiment")).append(hCell("Condition")).append(hCell("Image"))
                .append(hCell("Double-labelled")).append(hCell("Marker at the basal pole")).append(hCell("Marker at the apical pole"))
                .append(hCell("Pole indeterminable")).append(hCell("Sum")).append(hCell("Not at a pole")).append(hCell("% basal"))
                .append(hCell("% apical")).append(hCell("% indeterminable")).append(hCell("% sum"))
                .append(hCell("Parasite objects oriented")).append("</Row>\n");
        String fS = "=RC5+RC6+RC7", fPb = "=IF(RC4=0,0,RC5/RC4*100)", fPa = "=IF(RC4=0,0,RC6/RC4*100)",
                fPi = "=IF(RC4=0,0,RC7/RC4*100)", fPs = "=IF(RC4=0,0,RC8/RC4*100)";
        for (int m = 0; m < nMw; m++)
            for (int c = 0; c < nCw; c++) {
                int k = m * nCw + c;
                int kk = (int) O.aggN[k];
                if (kk == 0) continue;
                for (int j = 0; j < kk; j++) {
                    int t = O.ordIdx[O.ordStart[k] + j];
                    String nmT = R.summaryNames.get(t);
                    if (R.summaryUse.get(t) == 2) nmT = nmT + " (*)";
                    x.append("<Row>").append(sCell(O.manipList.get(m))).append(sCell(O.condsAll.get(c))).append(sCell(nmT))
                            .append(nCell(R.summaryColoc.get(t))).append(nCell(R.summaryBas.get(t))).append(nCell(R.summaryApi.get(t)))
                            .append(nCell(R.summaryInd.get(t))).append(fCell("c", fS)).append(nCell(R.summaryMid.get(t)))
                            .append(fCell("p", fPb)).append(fCell("p", fPa)).append(fCell("p", fPi)).append(fCell("p", fPs))
                            .append(nCell(R.summaryOri.get(t))).append("</Row>\n");
                }
                String fT = "=SUM(R[-" + kk + "]C:R[-1]C)";
                x.append("<Row ss:Height=\"26\">").append(tCell(O.manipList.get(m))).append(tCell(O.condsAll.get(c)))
                        .append(tCell("TOTAL")).append(fCell("t", fT)).append(fCell("t", fT)).append(fCell("t", fT))
                        .append(fCell("t", fT)).append(fCell("t", fS)).append(fCell("t", fT)).append(fCell("tp", fPb))
                        .append(fCell("tp", fPa)).append(fCell("tp", fPi)).append(fCell("tp", fPs)).append(fCell("t", fT))
                        .append("</Row>\n");
                x.append("<Row ss:Height=\"8\"></Row>\n");
            }
        x.append("</Table>\n</Worksheet>\n");
        return x.toString();
    }

    static String sumRowRef(Outputs O, boolean isFirst, String labM, int mergeN, String condLabel, int baseD, int tr,
                            int ctrlRow) {
        String mf = measureFormula(O.S);
        StringBuilder r = new StringBuilder("<Row ss:Height=\"26\">");
        if (isFirst) r.append(mCell(labM, mergeN)).append(tCell(condLabel));
        else r.append(tCellIdx(condLabel, 2));
        r.append(fCell("t", "=R" + tr + "C" + (baseD + 2))).append(fCell("t", "=R" + tr + "C" + (baseD + 3)))
                .append(fCell("t", "=R" + tr + "C" + (baseD + 4))).append(fCell("t", "=RC[-2]-RC[-1]")).append(fCell("tp", mf));
        if (O.hasControl) {
            if (ctrlRow > 0) r.append(fCell("tp", "=IF(R" + ctrlRow + "C7=0,0,RC[-1]/R" + ctrlRow + "C7*100)"));
            else r.append("<Cell ss:StyleID=\"na\"></Cell>");
        }
        return r.append("</Row>\n").toString();
    }

    static String sumRowGroup(Outputs O, boolean isFirst, String labM, int mergeN, String grpLab, int r1, int r2,
                              int ctrlRow) {
        String mf = measureFormula(O.S);
        StringBuilder r = new StringBuilder("<Row ss:Height=\"26\">");
        if (isFirst) r.append(mCell(labM, mergeN)).append(tCell(grpLab));
        else r.append(tCellIdx(grpLab, 2));
        r.append(fCell("t", "=SUM(R" + r1 + "C3:R" + r2 + "C3)")).append(fCell("t", "=SUM(R" + r1 + "C4:R" + r2 + "C4)"))
                .append(fCell("t", "=SUM(R" + r1 + "C5:R" + r2 + "C5)")).append(fCell("t", "=RC[-2]-RC[-1]"))
                .append(fCell("tp", mf));
        if (O.hasControl) {
            if (ctrlRow > 0) r.append(fCell("tp", "=IF(R" + ctrlRow + "C7=0,0,RC[-1]/R" + ctrlRow + "C7*100)"));
            else r.append("<Cell ss:StyleID=\"na\"></Cell>");
        }
        return r.append("</Row>\n").toString();
    }
}
