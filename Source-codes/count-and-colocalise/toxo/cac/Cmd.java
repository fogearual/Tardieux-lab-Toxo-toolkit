package toxo.cac;

import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.LookUpTable;
import ij.measure.Calibration;
import ij.plugin.ContrastEnhancer;
import ij.plugin.Duplicator;
import ij.plugin.LutLoader;
import ij.plugin.RGBStackMerge;
import ij.plugin.ZProjector;
import ij.process.ColorProcessor;
import ij.process.ImageConverter;
import ij.process.ImageProcessor;
import ij.process.LUT;
import ij.process.StackConverter;

import java.awt.image.IndexColorModel;
import java.util.Properties;

/**
 * The ImageJ commands the macro version runs with run("..."), each done the way that command does it, on the
 * image given rather than on the image in front: several images can then be processed at once.
 */
final class Cmd {
    private Cmd() {}

    /** run("Duplicate...", "title=[t] duplicate"): the whole image, stack or hyperstack. */
    static ImagePlus dupAll(ImagePlus imp, String title) {
        Roi0.clear(imp);
        ImagePlus imp2;
        if (imp.getStackSize() == 1) imp2 = new Duplicator().crop(imp);
        else if (imp.isHyperStack() || imp.isComposite()) {
            imp2 = new Duplicator().run(imp, 1, imp.getNChannels(), 1, imp.getNSlices(), 1, imp.getNFrames());
            imp2.setPosition(imp.getC(), imp.getZ(), imp.getT());
        } else imp2 = new Duplicator().run(imp);
        imp2.setTitle(title);
        imp2.setProp("UniqueName", "true");
        return imp2;
    }

    /** run("Duplicate...", "title=[t] duplicate frames=1") / "duplicate range=1-1 frames=1": time point 1. */
    static ImagePlus firstTimePoint(ImagePlus imp, String title) {
        Roi0.clear(imp);
        int c = imp.getNChannels(), z = imp.getNSlices();
        ImagePlus imp2;
        if (c * z > 1) {
            imp2 = new Duplicator().run(imp, 1, c, 1, z, 1, 1);
            imp2.setPosition(imp.getC(), imp.getZ(), 1);
        } else imp2 = new Duplicator().run(imp, 1, 1);
        imp2.setTitle(title);
        imp2.setProp("UniqueName", "true");
        return imp2;
    }

    /** run("Z Project...", "projection=[Max Intensity]" [+ " all"]). */
    static ImagePlus maxProject(ImagePlus imp, boolean allFrames) {
        return ZProjector.run(imp, allFrames ? "max all" : "max");
    }

    /** run("32-bit") / run("16-bit") / run("8-bit") on an image or stack, as the Converter does it. */
    static void convert(ImagePlus imp, int bitDepth) {
        Roi0.clear(imp);
        if (imp.isThreshold()) imp.getProcessor().resetThreshold();
        if (imp.getBitDepth() == bitDepth) return;
        if (imp.getStackSize() > 1) {
            StackConverter sc = new StackConverter(imp);
            if (bitDepth == 8) sc.convertToGray8();
            else if (bitDepth == 16) sc.convertToGray16();
            else if (bitDepth == 32) new ImageConverter(imp).convertToGray32();
        } else {
            ImageConverter ic = new ImageConverter(imp);
            if (bitDepth == 8) ic.convertToGray8();
            else if (bitDepth == 16) ic.convertToGray16();
            else if (bitDepth == 32) ic.convertToGray32();
        }
    }

    /** run("RGB Color") on a single image (not a composite): through its LUT and display range. */
    static void toRGB(ImagePlus imp) {
        imp.setProp(LUT.nameKey, null);
        Roi0.clear(imp);
        if (imp.isThreshold()) imp.getProcessor().resetThreshold();
        if (imp.getBitDepth() == 24) return;
        if (imp.isComposite()) {
            ImagePlus rgb = compositeToRGB((CompositeImage) imp);
            imp.setImage(rgb);
            return;
        }
        new ImageConverter(imp).convertToRGB();
    }

    /** run("RGB Color") on a merge (Merge Channels ... create): the composite as displayed, or - a 3-slice 8-bit
     *  stack labelled Red / Green / Blue, which ImageJ takes for an RGB stack - its raw values. */
    static ImagePlus rgbOfMerge(ImagePlus m) {
        m.setProp(LUT.nameKey, null);
        ImageStack st = m.getStack();
        if (m.isComposite() && !st.isRGB() && !st.isHSB() && !st.isLab()) return compositeToRGB((CompositeImage) m);
        if (st.isRGB()) {
            int w = m.getWidth(), h = m.getHeight();
            ColorProcessor cp = new ColorProcessor(w, h);
            cp.setRGB((byte[]) st.getPixels(1), (byte[]) st.getPixels(2), (byte[]) st.getPixels(3));
            if (m.isInvertedLut()) cp.invert();
            ImagePlus out = new ImagePlus(m.getTitle(), cp);
            out.setCalibration(m.getCalibration());
            // (the same image in ImageJ: its Info and properties stay)
            out.setProperty("Info", m.getProperty("Info"));
            out.setProperties(m.getPropertiesAsArray());
            return out;
        }
        ImagePlus c = dupAll(m, m.getTitle());
        new StackConverter(c).convertToRGB();
        return c;
    }

    /** "RGB Color" of a single-plane composite: the composite as displayed. */
    static ImagePlus compositeToRGB(CompositeImage imp) {
        ImagePlus imp2 = imp.createImagePlus();
        imp.updateImage();
        imp2.setProcessor(imp.getTitle() + " (RGB)", new ColorProcessor(imp.getImage()));
        return imp2;
    }

    /** flattenClean of the macro: an RGB copy, nothing drawn over it. */
    static ImagePlus flattenClean(ImagePlus imp, String title) {
        Roi0.clear(imp);
        imp.setOverlay(null);
        if (imp.getBitDepth() != 24) toRGB(imp);
        ImageProcessor ip = imp.getProcessor().duplicate();
        ImagePlus out = new ImagePlus(title, ip);
        out.setCalibration(imp.getCalibration());
        // as ImagePlus.flatten: the Info and the properties of the image flattened
        out.setProperty("Info", imp.getProperty("Info"));
        out.setProperties(imp.getPropertiesAsArray());
        return out;
    }

    /** run("Merge Channels...", "c1=[a] c2=[b] ... create keep"): the slots in order, null = none. */
    static ImagePlus mergeCreateKeep(ImagePlus[] slots) {
        ImagePlus imp2 = RGBStackMerge.mergeChannels(slots, true);
        if (imp2 == null) return null;
        for (ImagePlus s : slots)
            if (s != null) {
                imp2.setCalibration(s.getCalibration());
                break;
            }
        return imp2;
    }

    /** run(lutName) (Grays, Blue, Green, Red, Cyan, Magenta, Yellow) on the current channel. */
    static void applyLut(ImagePlus imp, String lutName) {
        IndexColorModel cm = LutLoader.getLut(lutName);
        if (cm == null) return;
        if (imp.getProcessor().getNChannels() != 1) return;
        if (imp.isComposite() && ((CompositeImage) imp).getMode() == IJ.GRAYSCALE) {
            CompositeImage cimp = (CompositeImage) imp;
            cimp.setMode(IJ.COLOR);
            int saveC = cimp.getChannel();
            for (int c = 1; c <= cimp.getNChannels(); c++) {
                cimp.setC(c);
                cimp.setChannelColorModel(cm);
            }
            imp.setC(saveC);
        } else {
            ImageProcessor ip = imp.getChannelProcessor();
            if (imp.isComposite()) ((CompositeImage) imp).setChannelColorModel(cm);
            else {
                ip.setColorModel(cm);
                if (imp.getWindow() == null) imp.setProcessor(ip);
            }
            if (imp.getStackSize() > 1) imp.getStack().setColorModel(cm);
        }
        // the name of the LUT, as LutLoader keeps it (none for Grays)
        String nm = lutName.toLowerCase();
        imp.setProp(LUT.nameKey, nm.equals("grays") ? null : nm);
    }

    /** resetMinAndMax() of the macro language. */
    static void resetMinAndMax(ImagePlus imp) {
        imp.resetDisplayRange();
    }

    /** setMinAndMax(lo, hi) of the macro language. */
    static void setMinAndMax(ImagePlus imp, double lo, double hi) {
        Calibration cal = imp.getCalibration();
        imp.setDisplayRange(cal.getRawValue(lo), cal.getRawValue(hi));
    }

    /** run("Enhance Contrast", "saturated=s") on a single image. */
    static void enhanceContrast(ImagePlus imp, double saturated) {
        imp.trimProcessor();
        new ContrastEnhancer().stretchHistogram(imp, saturated);
    }

    /** run("Concatenate...", "title=[t] image1=[a] image2=[b] ...") of single-channel images: one plain stack,
     *  the images in order (Concatenator, "Open as 4D image" off). */
    static ImagePlus concat(ImagePlus[] images, String title) {
        int maxW = 0, maxH = 0;
        for (ImagePlus im : images) {
            maxW = Math.max(maxW, im.getWidth());
            maxH = Math.max(maxH, im.getHeight());
        }
        ImagePlus first = images[0];
        Calibration cal = first.getCalibration();
        int dataType = first.getType();
        ImageStack st = new ImageStack(maxW, maxH, first.getProcessor().getColorModel());
        double min = first.getProcessor().getMin(), max = first.getProcessor().getMax();
        String[] titles = new String[images.length];
        Properties[] props = new Properties[images.length];
        int count = 0;
        for (int i = 0; i < images.length; i++) {
            ImagePlus im = images[i];
            if (im.getType() != dataType) {
                IJ.log("Omitting " + im.getTitle() + " - image type not matched");
                continue;
            }
            ImageStack s1 = im.getStack();
            for (int k = 1; k <= s1.getSize(); k++) {
                ImageProcessor ip = s1.getProcessor(k);
                String label = s1.getSliceLabel(k);
                ImageProcessor ip2 = ip;
                if (ip.getWidth() != maxW || ip.getHeight() != maxH) {
                    ip2 = ip.createProcessor(maxW, maxH);
                    ip2.insert(ip, (maxW - ip.getWidth()) / 2, (maxH - ip.getHeight()) / 2);
                }
                st.addSlice(label, ip2);
            }
            props[count] = im.getProperties();
            titles[count] = im.getTitle();
            count++;
        }
        ImagePlus imp = new ImagePlus(title, st);
        imp.setCalibration(cal);
        imp.setProperty("Number of Stacks", Integer.valueOf(count));
        imp.setProperty("Stacks Properties", props);
        imp.setProperty("Image Titles", titles);
        imp.getProcessor().setMinAndMax(min, max);
        imp.setProperty("Info", first.getProperty("Info"));
        return imp;
    }

    /** A new black image ("8-bit black", "16-bit black", "32-bit black"), n slices. */
    static ImagePlus newBlack(String title, int bitDepth, int w, int h, int n) {
        ImageStack st = new ImageStack(w, h);
        for (int i = 0; i < n; i++) {
            ImageProcessor ip;
            if (bitDepth == 8) ip = new ij.process.ByteProcessor(w, h);
            else if (bitDepth == 16) ip = new ij.process.ShortProcessor(w, h);
            else ip = new ij.process.FloatProcessor(w, h);
            st.addSlice(null, ip);
        }
        ImagePlus imp = new ImagePlus(title, st);
        if (bitDepth == 8) {
            imp.getProcessor().setColorModel(LookUpTable.createGrayscaleColorModel(false));
        }
        return imp;
    }

    /** The selection of an image removed (run("Select None")). */
    static final class Roi0 {
        static void clear(ImagePlus imp) {
            imp.deleteRoi();
        }
    }
}
