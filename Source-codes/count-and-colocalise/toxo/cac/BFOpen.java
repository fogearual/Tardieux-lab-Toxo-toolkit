package toxo.cac;

import ij.ImagePlus;

import java.util.List;

/**
 * Everything that goes through Bio-Formats, in one class: it is loaded only when a file needs Bio-Formats, so
 * that .nd and TIFF files never depend on it (as in the macro version).
 */
final class BFOpen {
    private BFOpen() {}

    /** Bio-Formats is not always safe to call from several threads at once: one file at a time. */
    static final Object LOCK = new Object();

    /** A "series_N" key of an option string. */
    private static final java.util.regex.Pattern SERIES_KEY =
            java.util.regex.Pattern.compile("(?:^|\\s)series_(\\d+)(?=\\s|=|$)");

    /** Number of series of a file (Ext.getSeriesCount), 1 when it cannot be read. */
    static int seriesCount(String path, List<String> warn) {
        synchronized (LOCK) {
            try {
                loci.formats.ImageReader r = new loci.formats.ImageReader();
                try {
                    r.setId(path);
                    return r.getSeriesCount();
                } finally {
                    r.close();
                }
            } catch (Throwable e) {
                if (warn != null) warn.add("Bio-Formats could not read the series of " + path + ": " + e);
                return 1;
            }
        }
    }

    /** Ext.openImagePlus(path): the Bio-Formats simple opener, default options, first series. */
    static ImagePlus simple(String path, Input.Opened o) {
        synchronized (LOCK) {
            try {
                loci.plugins.in.ImporterOptions options = new loci.plugins.in.ImporterOptions();
                options.setId(path);
                options.setSeriesOn(0, true);
                ImagePlus[] imps = loci.plugins.BF.openImagePlus(options);
                if (imps == null || imps.length == 0) {
                    o.err = "Bio-Formats could not open it";
                    return null;
                }
                return imps[0];
            } catch (Throwable e) {
                o.err = "Bio-Formats could not open it (" + e.getMessage() + ")";
                return null;
            }
        }
    }

    /** run("Bio-Formats Importer", options): the options read as the importer reads those of a macro. */
    static ImagePlus importer(String optionString, Input.Opened o) {
        synchronized (LOCK) {
            try {
                // as the importer reads the options of a macro: its defaults, then the options given
                loci.plugins.in.ImporterOptions options = new loci.plugins.in.ImporterOptions();
                options.parseArg(optionString);
                options.checkObsoleteOptions();
                // the series asked for (series_N): in a macro, the series dialog of the importer turns every
                // series off, then on those named
                java.util.regex.Matcher m = SERIES_KEY.matcher(optionString);
                boolean cleared = false;
                while (m.find()) {
                    if (!cleared) options.clearSeries();
                    cleared = true;
                    options.setSeriesOn(Integer.parseInt(m.group(1)) - 1, true);
                }
                ImagePlus[] imps = loci.plugins.BF.openImagePlus(options);
                if (imps == null || imps.length == 0) {
                    o.err = "Bio-Formats could not open it";
                    return null;
                }
                return imps[0];
            } catch (Throwable e) {
                o.err = "Bio-Formats could not open it (" + e.getMessage() + ")";
                return null;
            }
        }
    }

    /** Ext.getSizeC / Z / T of an entry (the Bio-Formats simple opener: sizes only). Null if unreadable. */
    static int[] sizes(String path, Input.Entry e) {
        synchronized (LOCK) {
            try {
                loci.formats.ImageReader r = new loci.formats.ImageReader();
                try {
                    r.setId(path);
                    if (e.seriesTotal > 1) r.setSeries(e.series - 1);
                    return new int[]{r.getSizeC(), r.getSizeZ(), r.getSizeT()};
                } finally {
                    r.close();
                }
            } catch (Throwable ex) {
                return null;
            }
        }
    }

    /** The importer, as a virtual stack and without autoscale: the colours from the metadata, at once ("Default"
     *  would leave them out: red, green, blue in channel order). */
    static ImagePlus virtual(String path, Input.Entry e) {
        String o = "open=[" + path + "] color_mode=Composite rois_import=[ROI manager] view=Hyperstack "
                + "stack_order=XYCZT use_virtual_stack";
        if (e.seriesTotal > 1) o = o + " series_" + e.series;
        Input.Opened op = new Input.Opened();
        return importer(o, op);
    }
}
