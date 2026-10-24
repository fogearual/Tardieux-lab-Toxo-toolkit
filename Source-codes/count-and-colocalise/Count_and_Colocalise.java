import ij.plugin.PlugIn;

/**
 * Count and Colocalise (Toxo Toolkit): batch counting of host nuclei, parasites and double-labelled parasites,
 * with QC stacks, CSV files, a workbook and histograms; also hyperstacks, projections and merges of each file.
 * The menu command; everything else is in the package toxo.cac.
 */
public class Count_and_Colocalise implements PlugIn {
    public void run(String arg) {
        toxo.cac.Plugin.runTool();
    }
}
