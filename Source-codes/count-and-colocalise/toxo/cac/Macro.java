package toxo.cac;

import ij.util.Tools;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Number formatting and parsing exactly as the ImageJ macro language does them, so that every value the macro
 * version wrote into a command string ("size=7.25-62.5", "rolling=80") or printed in the Log is the very value and
 * text used here. IJ.d2s shares its DecimalFormat objects between threads, which can garble a number when images
 * are processed at once: the formats here are the same, one set per thread.
 */
public final class Macro {
    private Macro() {}

    private static final ThreadLocal<DecimalFormat[]> DF = new ThreadLocal<DecimalFormat[]>() {
        @Override
        protected DecimalFormat[] initialValue() {
            DecimalFormatSymbols dfs = new DecimalFormatSymbols(Locale.US);
            DecimalFormat[] df = new DecimalFormat[10];
            String p = "0";
            for (int i = 0; i < 10; i++) {
                df[i] = new DecimalFormat(i == 0 ? "0" : p, dfs);
                p = i == 0 ? "0.0" : p + "0";
            }
            df[0].setRoundingMode(RoundingMode.HALF_UP);
            return df;
        }
    };

    private static final ThreadLocal<DecimalFormat[]> SF = new ThreadLocal<DecimalFormat[]>() {
        @Override
        protected DecimalFormat[] initialValue() {
            DecimalFormatSymbols dfs = new DecimalFormatSymbols(Locale.US);
            DecimalFormat[] sf = new DecimalFormat[10];
            String p = "0.0";
            for (int i = 1; i < 10; i++) {
                sf[i] = new DecimalFormat(p + "E0", dfs);
                p = p + "0";
            }
            return sf;
        }
    };

    /** IJ.d2s(n, decimalPlaces), thread-safe. */
    public static String d2s(double n, int decimalPlaces) {
        if (Double.isNaN(n) || Double.isInfinite(n)) return "" + n;
        if (n == Float.MAX_VALUE) return "3.4e38";
        if (decimalPlaces < 0) {
            decimalPlaces = -decimalPlaces;
            if (decimalPlaces > 9) decimalPlaces = 9;
            if (decimalPlaces < 1) decimalPlaces = 1;
            return SF.get()[decimalPlaces].format(n);
        }
        if (decimalPlaces > 9) decimalPlaces = 9;
        return DF.get()[decimalPlaces].format(n);
    }

    /** IJ.d2s(x, significantDigits, maxDigits), thread-safe. */
    public static String d2s(double x, int significantDigits, int maxDigits) {
        double log10 = Math.log10(Math.abs(x));
        double roundErrorAtMax = 0.223 * Math.pow(10, -maxDigits);
        int magnitude = (int) Math.ceil(log10 + roundErrorAtMax);
        int decimals = x == 0 ? 0 : maxDigits - magnitude;
        if (decimals < 0 || magnitude < significantDigits + 1 - maxDigits)
            return d2s(x, -significantDigits);
        if (decimals > significantDigits)
            decimals = Math.max(significantDigits, decimals - maxDigits + significantDigits);
        return d2s(x, decimals);
    }

    /** A number joined to a string in a macro ("" + x): whole numbers without decimals, others with 4
     *  significant decimals, trailing zeros removed. */
    public static String n2s(double x) {
        if ((int) x == x) return d2s(x, 0);
        String str = d2s(x, 4, 9);
        while (str.endsWith("0") && str.contains(".") && !str.contains("E")) str = str.substring(0, str.length() - 1);
        if (str.endsWith(".")) str = str.substring(0, str.length() - 1);
        return str;
    }

    /** The value a command receives when the macro wrote x into its option string. */
    public static double asOption(double x) {
        return Tools.parseDouble(n2s(x));
    }

    /** Number as text, 2 decimals, dot separator (num2 of the macro). */
    public static String num2(double v) {
        return d2s(v, 2).replace(",", ".");
    }

    /** parseFloat of the macro language: NaN when not a number. */
    public static double parseFloat(String s) {
        if (s == null) return Double.NaN;
        s = s.trim();
        if (s.indexOf(' ') != -1) s = s.substring(0, s.indexOf(' '));
        return Tools.parseDouble(s);
    }

    /** parseInt of the macro language (base 10): rounded, NaN when not a number. */
    public static double parseInt(String s) {
        double n = parseFloat(s);
        if (!Double.isNaN(n)) n = Math.round(n);
        return n;
    }

    /** A threshold as text: whole numbers as they are, others with 2 decimals below 100, none above. */
    public static String nfmt(double v) {
        if (v == Math.round(v)) return d2s(v, 0);
        if (Math.abs(v) >= 100) return d2s(v, 0);
        return d2s(v, 2);
    }

    /** round() of the macro language. */
    public static double round(double v) {
        return Math.round(v);
    }

    /** maxOf / minOf on numbers as the macro does them. */
    public static double maxOf(double a, double b) { return Math.max(a, b); }

    public static double minOf(double a, double b) { return Math.min(a, b); }
}
