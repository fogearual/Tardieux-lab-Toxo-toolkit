// ======================================================================
//  Count and Colocalise  -  version 23.1
// ======================================================================
//
//  INSTALLATION
//  Copy this file into the folder  Fiji.app/plugins/Toxo Toolkit/  (create
//  it if needed) and restart Fiji (or run Help > Refresh Menus). It then
//  appears under  Plugins > Toxo Toolkit > Count and Colocalise : the folder
//  name gives the submenu (keep its space, not an underscore) and the file
//  name the command (underscores become spaces), so keep both names.
//
//  WHAT IT DOES
//  The dialogs open in turn, each showing only what the task needs:
//    1. what to do - count objects, make images (Z projections, merges,
//       TIFF), or both - and the detection settings to start from
//    2. input and outputs: which files (types, how to open them), the pixel
//       size, and what to write (QC stacks, workbook, histograms; the images
//       to make)
//    3. what to count (counting only): the objects, the measure reported,
//       how double labelling is scored (by overlap, or by marker objects)
//       with the pole option, and the panels of the QC stacks
//    4. the channels - one row per channel of the first image, with the
//       colour and name its file gives it, read from its metadata without
//       opening it in full: what each channel is (counting), its colour in
//       the images made, and whether it goes into the merges. Marker objects
//       and poles then have a short dialog of their own.
//    5. the detection settings of the objects actually counted, the rule
//       for nuclei at the image border, an optional stop target and minimum
//       per image, and Calibrate - tune the settings on images while
//       seeing the result
//  Every dialog fits a laptop screen; the explanations are under Help.
//  Nothing has to be edited in the code. Every setting is remembered for
//  the next run (a file in the ImageJ preferences folder), and written to
//  Settings.txt next to the results: the first dialog can start again from
//  the settings of the last run, from the validated defaults, or from the
//  Settings.txt of any earlier run.
//
//  TASKS
//    - image outputs: hyperstack TIFF, maximum Z projection,
//      composite merge of the chosen channels keeping all Z planes, and
//      composite merge of the maximum projection, each channel in the colour
//      chosen for it. The hyperstack and the projection show the channels
//      one at a time (C slider), the merges show them together. Merges stay
//      multi-channel at the original bit depth, not flattened to RGB.
//    - count nuclei, parasites, and double-labelled parasites - any of them:
//      only the channels of what is counted are needed
//    - annotated QC stacks, one per image, calibrated, drawn exactly as the
//      Calibrate preview (same brightness, same outline colours, same
//      legends); their panels are chosen in "What to count": nuclei,
//      parasites, marker, marker + parasites, poles, all channels, and each
//      channel also raw. In their Info (Image > Show Info): the source file,
//      the pixel size and where it came from, the channels used, the counts,
//      the thresholds and every setting
//    - results workbook with per-experiment summary and per-image detail
//    - histogram per experiment, and pooled histogram across experiments
//  Any combination can be run; the conversion alone needs no counting.
//
//  INPUT LAYOUT
//    <folder>/<experiment>/<condition>/*          several experiments
//    <folder>/<condition>/*                       a single experiment
//  a single folder of images, or a single file, are also accepted.
//  File types: any, given by their extension in the first dialog
//  (czi nd lif nd2 lsm oib tif ...). How they are opened:
//    - Automatic (default): MetaMorph .nd read by this tool itself, TIFF
//      with ImageJ, everything else with the Bio-Formats Importer
//    - or every file with the Bio-Formats Importer, the Bio-Formats simple
//      opener, or ImageJ only.
//  Z stacks are max-projected; a file with a single plane is used as it
//  is. Of a time series, only the first time point is counted (the image
//  outputs keep them all). The Log gives, for each file, the channels, Z
//  planes and time points it opened with.
//  A file holding several series (stage positions of a .nd, scenes of a
//  CZI, series of a LIF...) gives one entry per series, named
//  <file>_s1, <file>_s2 and so on: always for a .nd, for other files when
//  ticked in the first dialog (this reads them with Bio-Formats).
//
//  METAMORPH .nd  (read without Bio-Formats)
//    The .nd is a text file describing the acquisition; the images are the
//    TIFF / STK files next to it,
//        <nd name>_w<n><wavelength>_s<position>_t<time point>.TIF
//    each part present only when the acquisition has it. They are found
//    from the .nd (wavelengths, positions, time points, Z), whatever the
//    case of the names or the extension, and put together as one image per
//    position, channels in the order of the wavelengths. A wavelength
//    acquired without Z (transmitted light) is repeated over the Z planes
//    of the others. Each channel gets a colour after its wavelength name
//    (DAPI blue, GFP green, TxRed red...), so the channels can be found by
//    colour. The pixel size comes from the MetaMorph metadata of the TIFF
//    files when they have some (the .nd gives the Z step); a file that is
//    missing is named in the Log, and that position skipped.
//    These TIFF / STK files are never taken as images of their own.
//
//  CHANNELS  ("Channels")
//    One row per channel of the first image, with the colour and the name
//    its file gives it:
//      - what it is: nuclear stain, parasite label, second marker, pole
//        marker, transmission / DIC, or not used. Suggested from its colour
//        (blue = nuclear stain, green = parasites, red = second marker,
//        grey = DIC): check it. One channel for each.
//      - its colour in the images made (hyperstack, projection, merges):
//        as in the file, or grey, blue, green, red, cyan, magenta, yellow.
//        A channel its file shows in grey gets the usual colour of what it
//        is (nuclear stain blue, parasites green, marker red).
//      - whether it goes into the merges (7 channels at most).
//    The same channel numbers serve for every file. When the order changes
//    from file to file, tick "Channels in another order in some files: find
//    each one by its colour in the first file": each file is then sorted out
//    on its own (the channels used need different colours in the first
//    file; a file where one is not found uses the numbers of the table).
//
//  ONE TIFF PER CHANNEL (slides / coverslips), chosen in the first dialog:
//    <folder>/<experiment>/data/<name>--W00003--P00020--Z00000--T00000--DAPI.tif
//  The text before the W field is ignored. Each experiment folder is a
//  slide, each W number a coverslip (the condition), each P number a field.
//  Channels are recognised from the end of the file name (DAPI, FITC,
//  Texas Red by default, editable in "Channels"; a pole marker, when
//  the pole option uses one, by the word given for it). Several Z files
//  for one field are max-projected. With this layout: one histogram per
//  slide, no pooled histogram, and no image outputs.
//
//  REPORTED MEASURE  ("What to count")
//    - single-labelled parasites (parasites minus double-labelled: the
//      intracellular parasites of a red/green invasion assay)
//    - all parasites
//    - double-labelled parasites
//    - double-labelled parasites, as % of all parasites
//  The first three are per 100 host cells when nuclei are counted, a count
//  per image otherwise. Without double labelling counted, the measure is
//  all parasites; with no parasites counted, the host cells. The workbook
//  formulas, the CSV "Measure" column and the histograms follow it.
//
//  DOUBLE LABELLING  ("What to count", then "Double labelling: objects and
//  poles")  - three ways to score it:
//    - overlap (as always): at least the minimum overlap of the parasite's
//      area is positive in the mask of the marker;
//    - marker objects inside the parasite: the marker is cut into objects
//      of the size given (beads, a labelled structure); each belongs to the
//      parasite holding its centre, or within the distance given of its
//      outline (beads sit on the surface, just outside the parasite label).
//      Objects may be counted anywhere, only in the centre of the parasite,
//      or only at an extremity. A parasite holding at least the number of
//      counted objects given is double-labelled. The Log, the QC (circles)
//      and Parasites.csv give the objects of each parasite;
//    - the parasite inside a marker object (a vacuole, a cell): at least
//      the minimum overlap of its area lies in an object of the size given.
//  Places along a parasite are read on its long axis, tip to tip (its
//  Feret diameter): the centre is the central part of its length (30 % by
//  default), an extremity the last part at each end (20 %).
//
//  POLES  (option, "What to count")
//    Which end of each parasite is basal: the end nearest its nucleus (the
//    nucleus of a tachyzoite lies in its basal half; nuclei of a parasite's
//    size are found in the nuclear stain, host nuclei and the apicoplast
//    are told apart by size), and / or a pole marker in a channel of its
//    own - basal, or apical such as an extruded conoid labelled in
//    fluorescence (seen in DIC only, it cannot be found automatically). A
//    reference near the centre of the parasite tells nothing; with two
//    references one is enough, and if they disagree the parasite is not
//    oriented. Each double-labelled parasite is then classed by where its
//    marker lies (its counted objects - the pole holding more of them - or
//    the centre of its marker-positive part):
//      basal pole / apical pole / pole indeterminable (at an end of a
//      parasite that could not be oriented) / not at a pole.
//    The first three and their sum are given per image (Log, CSV) and per
//    experiment x condition (workbook, sheet "Poles"), as counts and as %
//    of the double-labelled parasites; Parasites.csv gives each parasite,
//    and the QC stack a POLES panel (yellow dot = basal end). Calibrate
//    double labelling shows and sets all of it.
//
//  WORKBOOK: the "% of control" column appears only when at least one
//  condition is a control; otherwise it is left out. A class that was not
//  counted keeps its column, at 0, headed "(not counted)"; in the CSV files
//  its cells are empty.
//
//  NUCLEI AT THE IMAGE BORDER  (detection settings)
//    - count them all (default)
//    - leave out every nucleus touching the border
//    - leave out nuclei with more than half of their area outside the
//      image. The hidden part is inferred from the visible one: the width
//      of an elliptical nucleus, measured parallel to the border, peaks at
//      its centre, so a parabola fitted to the squared widths gives the
//      depth of the centre. A centre beyond the border means more than half
//      of the nucleus is outside. A nucleus mostly inside is counted when
//      its visible part passes the filters with half the minimum area (at
//      most half of it is missing).
//    Every object cut by the border is judged, also one the size or shape
//    filters reject because it is cut (a cut nucleus is smaller than a
//    whole one); specks under a quarter of the minimum area are not.
//    Nuclei left out are drawn in grey only (never orange) in Calibrate and
//    the QC, and saved in the Data folder as
//    <image>_nuclei_border_excluded_ROIs.zip.
//
//  CLUSTERS AND FRAGMENTS OF NUCLEI  (detection settings)
//    Each nucleus is sized against the median nucleus area of its field.
//    Clearly larger: a cluster, counted as several cells (magenta in the
//    QC). Clearly smaller, when the fragment factor is above 0: a fragment,
//    counted as no cell (green) - usually the small piece a watershed cut
//    off a nucleus, which would otherwise make one cell count as two.
//    Every nucleus under the limit counts for nothing, cut or not - a
//    small piece of a nucleus cut by the image border as well. Objects
//    under the minimum area are not counted anyway, so the rule only acts
//    when the limit (factor x median) is above the minimum area; Calibrate
//    shows the median of each image. Fragment factor 0 (default) = off.
//
//  CLUSTERS OF PARASITES  (detection settings)
//    The same rule for parasites, when ticked: an object clearly larger than
//    the median parasite area of its field counts as several parasites
//    (area / median, rounded, at most the number given), outlined in white
//    in the QC. Meant for clumps the watershed cannot split. A clump larger
//    than the maximum area is left out like any other object, so raise the
//    maximum area to keep them. With fewer than 3 parasite objects in a
//    field there is no median to trust, and nothing is split. A clump that
//    is double-labelled counts as that many double-labelled parasites. Off
//    by default.
//
//  WATERSHED TOLERANCE  (Calibrate)
//    The watershed splits touching objects: it cuts an object between two
//    peaks of its distance map (each pixel's distance to the object edge).
//    ImageJ cuts as soon as both peaks rise more than 0.5 px above the pass
//    between them - tolerance 0.5, the default. A higher tolerance, in px,
//    only cuts at deeper notches: objects cut for nothing stay whole.
//    Nuclei and parasites each have their own. It never merges two
//    separate objects, and never loses one.
//
//  BRIGHT CAP  (detection settings and Calibrate)
//    One very bright object - debris, a dead cell, a dye crystal - stretches
//    the histogram the automatic threshold is computed on, and the threshold
//    can then rise above every ordinary object. The cap adapts to each
//    image, as the cluster rule does: in each image, an object holding a
//    pixel brighter than (factor) x the median brightness of what the
//    threshold detects is left out of the threshold calculation, whole -
//    the threshold is then the one the image would have without it. It is
//    left out of the CALCULATION only: the bright object is still detected.
//    Nothing is left out of an ordinary image, whose threshold is the
//    method's own. Hot pixels of the camera are not objects: a bright spot
//    no larger than what the blur makes of a single pixel is left in, as it
//    always was (the settings were validated with them). Default 5 x the
//    median; 0 = off. Nuclei, parasites and marker each have their own. The
//    Log says, image by image, when the cap acted, and so does Calibrate.
//
//  SATURATED IMAGES
//    - Multiplying a channel (a step of the preprocessing) clipped its
//      brightest values at the top of its bit depth, turning bright nuclei
//      into flat saturated plateaus. A channel where more than 1 % of the
//      pixels would clip is now processed in 32-bit, which keeps every
//      value (a few clipping hot pixels change nothing that matters).
//    - A saturated area larger than the background (a field covered with
//      saturated nuclei) makes the most frequent value of the image the
//      saturated one; the threshold methods then take it for the
//      background and keep only the saturated core of each nucleus. Such a
//      peak at the top of the histogram is left out of the threshold
//      calculation (said in the Log).
//    - Each channel counted is checked: when more than a trace of its
//      pixels sits at the top of the camera range, the Log gives the share
//      saturated. Lower the exposure if you can: a saturated nucleus has
//      no detail left for the watershed.
//
//  CALIBRATE  (detection settings, first two menus)
//    Pick "Calibrate nuclei / parasites / double labelling", choose where
//    the images come from - one you pick, one picked at random, or one at
//    random from each condition (the random picks come from the folder to
//    analyse, which is then asked for once) - then OK. A QC stack shows,
//    for each image, the raw channel in its colour, then the same view with
//    what is counted drawn over it (double labelling: the marker alone, and
//    marker + parasites), next to a panel with a slider for every setting
//    of that object class. OK or Enter redraws the counted objects with the
//    new values; "Keep" brings them back to the detection settings,
//    "Discard" restores the old ones.
//    The batch and the preview run the very same detection functions.
//    Only what a change affects is redone: the slow steps (background,
//    threshold, watershed) are kept for each image and rerun only when one
//    of their own settings changes, so a size, shape, cluster, fragment or
//    border setting redraws almost at once, even on several images.
//    From ImageJ 1.52o the panel stays open while you zoom and scroll; with
//    older versions pick "let me zoom and scroll" in the panel instead.
//    Settings not shown in the detection dialog (threshold method,
//    background radius, blur, circularity, watershed and its tolerance)
//    are listed under each block there, and every setting used is written
//    in the Log. What is above the threshold but not counted is shown only
//    when it comes close to being counted: orange, rejected by size - at
//    least half the minimum area, or above the maximum; yellow, rejected by
//    shape (circularity). Smaller specks are not drawn. Nuclei cut by the
//    border and left out are grey. The panel holds two sliders a row; the
//    counts of each image beyond the fourth are in the Log.
//
//  OUTLINE COLOURS  (Calibrate and QC stacks alike; never the colour of
//  the channel they are drawn on)
//    nuclei cyan, cluster magenta, fragment green, cut by the border grey;
//    parasites magenta, cluster white; double-labelled white, the other
//    parasites cyan, marker-positive area orange; rejected by size orange,
//    by shape yellow. Poles: magenta basal, cyan apical, white
//    indeterminable, orange not at a pole, yellow dot the basal end.
//
//  QC BRIGHTNESS  (Calibrate)
//    Each channel is shown with a display range adapted to each image: from
//    its background (the most frequent value) to the brightness of its
//    objects (the value 99.5 % of the pixels above its automatic threshold
//    stay under) - neither washed out nor saturated, whatever the exposure.
//    Times a brightness, set with the "Brightness" slider of the
//    calibration panel: 2 = twice as bright. The preview and the QC stacks
//    of the run use the very same view. Display only: the counts never
//    depend on it. Nuclei, parasites and marker each have their own.
//
//  STOP EARLY  (detection settings)
//    Each condition of each experiment is counted image by image until a
//    target number of host cells or parasites is reached. The image that
//    reaches it is counted in full; the remaining images of that condition
//    are left out of the counts and listed in the Log. 0 = count all.
//    The Log is saved as Log.txt in the output folder.
//
//  IMAGES WITH FEW OBJECTS  (detection settings)
//    An image with fewer host cells, or fewer parasites, than the minimum
//    given is either left out of the counts, or set aside and counted only
//    when its condition does not reach the stop target without it - the
//    richest first, until the target is reached. It is still processed and
//    keeps its QC stack; the workbook lists the images left out in a block
//    of their own, and the CSV files say for each image whether it was
//    counted. Without a stop target there is nothing to reach, and such
//    images are left out. A minimum of parasites needs care: a treatment
//    that lowers infection gives fields with few parasites, and leaving them
//    out biases the result. 0 = no minimum (default).
//
//  PIXEL SIZE  (first dialog)
//    Every area setting is in um^2, so the pixel size decides what is
//    counted. Either the calibration stored in the image is used, and the
//    value given in the dialog only for images that have none, or that
//    value is forced on every image whatever it says. A calibration in
//    another length unit (nm, mm, cm, m) is converted to um. One that is no
//    length - inches from a DPI tag, a blank unit - or out of the range of a
//    microscope (0.005 to 100 um) is not used: the value of the dialog is,
//    and the Log says why. The pixel size of every image is in the Log, and
//    in the Info of its QC stack.
//
//  Requires Fiji (ImageJ 1.52 or later) with Bio-Formats - Bio-Formats only
//  for the files it opens: .nd and TIFF need ImageJ alone.
// ======================================================================


// ======================================================================
//  USER INTERFACE
// ======================================================================
// Everything below is filled in from the dialogs. Nothing has to be edited
// in the code. The defaults are the settings validated on the invasion
// assay; changing them changes what is counted, so note what you used.
// ======================================================================

// ----------------------------------------------------------------------
// Globals. In the ImageJ macro language a function only sees its own
// parameters and variables declared with "var", so anything the functions
// below read has to be declared here, BEFORE the dialogs fill it in.
// ----------------------------------------------------------------------
var summaryManip = newArray(0);
var summaryCond  = newArray(0);
var summaryNames = newArray(0);
var summaryCells = newArray(0);
var summaryGreen = newArray(0);
var summaryColoc = newArray(0);
var summaryObj   = newArray(0);    // marker objects counted (objects mode)
var summaryOri   = newArray(0);    // parasite objects oriented (pole option)
var summaryBas   = newArray(0);    // double-labelled with the marker at the basal pole,
var summaryApi   = newArray(0);    //   the apical pole, an end of a parasite not
var summaryInd   = newArray(0);    //   oriented (indeterminable), not at a pole
var summaryMid   = newArray(0);
var summaryUse   = newArray(0);    // 1 counted, 0 left out (too few objects),
                                   // 2 counted only to reach the stop target
var summaryWhy   = newArray(0);    // why an image was under the minimum

var manipList = newArray(0);   // experiments, alphabetical
var condsAll  = newArray(0);   // conditions, final display order
var aggCells  = newArray(0);   // per experiment x condition, index m*nC+c
var aggGreen  = newArray(0);
var aggColoc  = newArray(0);
var aggN      = newArray(0);
var aggAll    = newArray(0);       // images, counted or not

var gOf    = newArray(0);      // group index of each condition
var gCount = 0;

var ordStart = newArray(0);    // image indices grouped by experiment x condition
var ordIdx   = newArray(0);

var mAcc = newArray(0);        // recursive scan
var cAcc = newArray(0);
var pAcc = newArray(0);

var dcDIC = 0;                 // detected channels
var infoC = 0;                 // sizes read by the Bio-Formats simple opener (openInfo)
var infoZ = 1;
var infoT = 1;
var dcRED = 0;
var dcGREEN = 0;
var dcBLUE = 0;

var singleManip = 0;           // 1 when no experiment level was found
var exclList = newArray(0);    // excluded conditions
var extList  = "czi nd";       // file types analysed, set in dialog 1
var extArr   = newArray(".czi", ".nd");
var tiffMode = false;          // one TIFF per channel, slides / coverslips
var tBlue  = newArray(0);      // per acquisition: channel files, "|"-joined
var tGreen = newArray(0);
var tRed   = newArray(0);
var tBase  = newArray(0);      // per acquisition: short unique name W.._P..
var tKey   = newArray(0);      // per acquisition: experiment|W|P
var tExp   = newArray(0);      // per acquisition: experiment folder
var tCov   = newArray(0);      // per acquisition: "Coverslip n"
var kwBlue  = "DAPI";          // file-name keywords identifying each channel
var kwGreen = "FITC";
var kwRed   = "Texas";
var dataSub = "data";          // subfolder of each experiment holding the TIFFs
var pixelSize  = 0.11;         // um/pixel given in the dialog
var pixelForce = false;        // true: used even when the image is calibrated
var calibShown = false;
var hasControl = false;        // at least one control condition exists
var skippedImgOut = false;     // image outputs dropped in TIFF mode
var bfColorMode = "Default";   // "" to let Bio-Formats decide
var useBatch = true;           // Bio-Formats sometimes conflicts with it
var openMethod = "auto";       // auto | importer | ext | imagej
var openWays = newArray("Automatic: ImageJ for .nd and .tif, Bio-Formats for the rest",
                        "Bio-Formats Importer for every file",
                        "Bio-Formats simple opener for every file",
                        "ImageJ only (TIFF; .nd read without Bio-Formats)");
var openKeys = newArray("auto", "importer", "ext", "imagej");
var splitSeries = false;       // files holding several series: one entry each
var serList = newArray(0);     // series index of each entry, 1-based
var serTot  = newArray(0);     // how many series the file holds
var serName = newArray(0);     // name of each series ("" when unknown)
var openErr = "";              // why the last file could not be opened
var droppedOut = 0;            // files left out: in the output folder (dropUnder)

var outputRoot = "";
var rootName = "";
var normalizeToCells = false;
var measureLabel = "";
// Reported measure. The first three are per 100 host cells when nuclei are
// counted, a count per image otherwise; the fourth is a ratio of its own.
var measureMode  = 0;
var measureUse   = 0;                  // the one in force (setMeasure), 4 = host cells
var measureNote  = "";
var measureModes = newArray("Single-labelled parasites (parasites minus double-labelled)",
                            "All parasites",
                            "Double-labelled parasites",
                            "Double-labelled parasites, % of all parasites");
var countNuclei = false;
var countGreen  = false;
var countDouble = false;
var chDIC = 1;
var chBLUE = 4;
var chGREEN = 3;
var chRED = 2;
var mergeDIC = false;
var mergeBlue = true;
var mergeGreen = true;
var mergeRed = true;
var autoDetectChannels = false;
var firstInfo = "";            // channels of the first image, for "Channels"
var firstB = 0;                // channel numbers its colours suggest
var firstG = 0;
var firstR = 0;
var firstD = 0;
var firstNC = 0;                       // its number of channels
var lastChNames = newArray(0);         // channel names of the file just opened (.nd)
var firstCols  = newArray(0);          // colour word of each channel of the first image
var firstNames = newArray(0);          // and its name ("" when the file gives none)
var chCols   = newArray(0);            // colour of each channel in the images made ("file": as opened)
var chMrg    = newArray(0);            // 1: the channel goes into the merges
var chColSet = "";                     // the same, remembered (settings), comma-separated
var chMrgSet = "";
var roleCols = newArray("", "", "", ""); // colour of each role (nuclei, parasites, marker, DIC) in the first image
var progVersion = "23.1";

// what to do (first dialog), remembered from one run to the next
var taskMode     = 0;                  // 0 count objects, 1 make images, 2 both
var taskModes    = newArray("Count objects (QC stacks, results)",
                            "Make images (Z projections, merges, TIFF)",
                            "Both: count objects and make images");
var doCount      = false;
var doQC         = false;
var doWorkbook   = false;
var doHistExp    = false;
var doHistPooled = false;
var doHyper      = false;
var doProject    = false;
var doMergeStack = false;
var doMergeProj  = false;
var doMerge      = false;
var doConvert    = false;
var pickOutDir   = false;
var inputPicked  = false;              // input asked for already (pickInput)
var inFile       = "";                 // the single file, when that is the input
// stop early (detection dialog)
var stopTarget   = 0;
var stopOn       = "cells";

// ----------------------------------------------------------------------
// Detection settings. Defaults validated on the invasion assay; the
// detection dialog and Calibrate change them. Declared here because the
// detection functions - shared by the batch and by Calibrate - read them.
// ----------------------------------------------------------------------
var nucleiThresholdFactor = 1.0;
var nucleiMinArea         = 105;       // um2
var splitBigNuclei        = true;
var nucleiDoubletFactor   = 1.7;
var nucleiMaxSplit        = 4;
var nucleiFragFactor      = 0;         // below this x the median: no cell (0 = off)
var nucleiEdgeMode        = 0;         // 0 all, 1 touching out, 2 half-out out
var edgeModes = newArray("Count them all",
                         "Leave out nuclei touching the border",
                         "Leave out nuclei more than half outside");
var greenThresholdFactor  = 4.25;
var greenMinArea          = 7.25;      // um2
var greenMaxArea          = 62.5;      // um2
var greenWatershed        = true;
var greenWsTol            = 0.5;       // watershed tolerance, px (0.5 = ImageJ's)
var redThresholdFactor    = 2.8;
var minOverlapPercent     = 92;
var splitBigParasites     = false;     // clumps counted as several parasites
var greenDoubletFactor    = 1.7;
var greenMaxSplit         = 8;
// Bright cap, adapted to each image: an object brighter than this x the
// median brightness of what the threshold detects in that image is left out
// of the automatic threshold calculation (it is still detected). 0 = off.
var nucleiCapF            = 5;
var greenCapF             = 5;
var redCapF               = 5;
// Images with few objects: under these counts an image is left out, or
// (lowMode 1) counted only if its condition needs it to reach the stop
// target. 0 = no minimum.
var minCellsImg           = 0;
var minParImg             = 0;
var lowMode               = 0;
var lowModes = newArray("Left out of the counts",
                        "Counted only if the target needs it");
var lowRule = "";                      // the minimum in words, "" when off

// Preprocessing - change only if you know why; Calibrate shows the effect.
// Multiply and Enhance Contrast do not change detection: the automatic
// threshold is recomputed on the rescaled histogram. Background radius and
// blur are in pixels; 0 skips the step.
var nucleiMultiply        = 2.5;
var nucleiBgRolling       = 80;
var nucleiBlurSigma       = 1.5;
var nucleiThresholdMethod = "Triangle";
var nucleiWatershed       = true;
var nucleiWsTol           = 0.5;       // watershed tolerance, px (0.5 = ImageJ's)
var nucleiMaxArea         = "Infinity";
var nucleiCirc            = "0.20-1.00";
var greenMultiply         = 2.0;
var greenBgRolling        = 30;
var greenBlurSigma        = 0.8;
var greenThresholdMethod  = "Triangle";
var greenCirc             = "0.10-1.00";
var redMultiply           = 2.0;
var redBgRolling          = 30;
var redBlurSigma          = 0.8;
var redThresholdMethod    = "Triangle";
var enhanceSaturated      = 0.10;

// QC display, per channel: a display range adapted to each image (from its
// background to the brightness of its objects, see qcRange) x this
// brightness. Set in Calibrate; used for the preview and for the QC stacks.
// Display only - the counts never depend on it.
var qcGainBlue            = 1;
var qcGainGreen           = 1;
var qcGainRed             = 1;
// panels of the QC stack ("What to count")
var qcPanN   = true;                   // nuclei
var qcPanP   = true;                   // parasites
var qcPanR   = true;                   // marker
var qcPanRG  = true;                   // marker + parasites
var qcPanPol = true;                   // poles (pole option)
var qcPanAll = true;                   // all channels (nuclei and parasites counted)
var qcRaw    = false;                  // each channel also without outlines
// Outline colours, the same in Calibrate and in the QC stacks; never the
// colour of the channel an outline is drawn on.
var colNuc   = "00ffff";               // nuclei counted
var colNClu  = "ff00ff";               //   cluster (several cells)
var colNFrag = "00ff00";               //   fragment (no cell)
var colEdge  = "a0a0a0";               //   at the border, left out
var colPar   = "ff00ff";               // parasites counted
var colPClu  = "ffffff";               //   cluster (several parasites)
var colDbl   = "ffffff";               // double-labelled parasites
var colOth   = "00ffff";               //   the other parasites
var colRejS  = "ff9900";               // rejected by size (close to the limits)
var colRejC  = "ffff00";               // rejected by shape (circularity)
var colMk    = "ff9900";               // marker-positive area
var nearFrac = 0.5;                    // an object under the minimum area is shown from this x it

// results of the detection functions
var cellClusterIdx = newArray(0);      // nuclei counted as several cells
var cellFragIdx = newArray(0);         // nuclei counted as no cell (fragments)
var cellMedian = 0;                    // median nucleus area, um2
var parClusterIdx = newArray(0);       // parasite objects counted as several
var parWeight = newArray(0);           // parasites each object counts for
var parMedian = 0;                     // median parasite area, um2 (0: not used)
var autoHi = 0;                        // upper bound of the last automatic threshold
// what the last automatic threshold left out of its calculation (autoLow)
var capHit = 0;                        // 1: objects above the bright cap left out
var capVal = 0;                        //    the cap, in corrected-channel units
var capObj = 0;                        //    how many objects (-1: pixels only)
var capN = 0;                          //    how many pixels
var spikeHit = 0;                      // 1: a saturated peak left out
var spikeN = 0;                        //    how many pixels
var thrMed = 0;                        // median brightness of what it detected
var thrMax = 0;                        // brightest pixel of the corrected channel
var medMax = 0;                        // brightest pixel, as found by medAbove
var thrNote = "";                      // all of this in words, "" if nothing
var satNote = "";                      // saturation of the raw channel, in words
var nucThr = 0;                        // nuclei threshold used
var nucThrAuto = 0;                    // automatic threshold it came from
var parThr = 0;                        // parasite threshold used
var parThrAuto = 0;                    // automatic threshold it came from
var dblIdx = newArray(0);              // double-labelled parasites
var dblThr = 0;
var dblThrAuto = 0;
var dblN20 = 0;
var dblN35 = 0;
var dblN55 = 0;

// ----------------------------------------------------------------------
// Double labelling by objects, places along the parasite, poles (second
// dialog, then "Double labelling: objects and poles").
// ----------------------------------------------------------------------
var dblMode  = 0;                      // 0 overlap, 1 objects in the parasite, 2 parasite in an object
var dblModes = newArray("Overlap of the parasite with the marker",
                        "Marker objects inside the parasite",
                        "The parasite inside a marker object");
var objMinArea   = 0.1;                // um2, marker objects inside the parasites
var objMaxArea   = 10;
var objTol       = 0.5;                // um: centre this far outside the outline still counts
var objMinN      = 1;                  // counted objects that make a parasite double-labelled
var objWatershed = true;
var encMinArea   = 20;                 // um2, marker objects around the parasites
var encMaxArea   = 5000;
var posMode  = 0;                      // objects counted: 0 anywhere, 1 centre, 2 an extremity
var posModes = newArray("Anywhere on the parasite", "In its centre", "At an extremity");
var centrePct = 30;                    // centre = the central centrePct % of the length
var endPct    = 20;                    // extremity, and pole = the last endPct % at each end
var doPoles   = false;
var poleRef   = 0;                     // 0 nucleus, 1 pole marker, 2 both
var poleRefs  = newArray("The parasite nucleus (nuclear stain)", "A pole marker",
                         "The nucleus and a pole marker");
var poleSide  = 0;                     // the pole marker marks 0 the basal, 1 the apical pole
var poleSides = newArray("The basal pole", "The apical pole (e.g. extruded conoid)");
var chPOLE    = 0;                     // channel of the pole marker (0 = none)
var kwPole    = "";                    // its file-name word, one TIFF per channel
var tPole     = newArray(0);           // per acquisition: pole marker files, "|"-joined
var pnMinArea = 0.8;                   // um2, nucleus of a parasite
var pnMaxArea = 15;
var poleDeadPct = 5;                   // reference closer than this % of the length to the centre: no orientation
var pmThresholdFactor = 2.0;           // pole marker
var pmMinArea  = 0.05;                 // um2
var pmMaxArea  = 5;
var pmCapF     = 5;
var pmMultiply = 2.0;
var pmBgRolling = 30;
var pmBlurSigma = 0.8;
var pmThresholdMethod = "Triangle";
var dcSkip = 0;                        // channel detectChannels leaves alone (the pole marker)
// results, for the image (or calibration image) just scored
var fX = newArray(0);                  // findObjects: centres (pixels) and areas (um2)
var fY = newArray(0);
var fA = newArray(0);
var fe1x = 0;                          // feretEnds: ends of the long axis, pixels
var fe1y = 0;
var fe2x = 0;
var fe2y = 0;
var aX1 = newArray(0);                 // per parasite: ends of its long axis (pixels),
var aY1 = newArray(0);                 //   length (um) and area (um2)
var aX2 = newArray(0);
var aY2 = newArray(0);
var aLen = newArray(0);
var aArea = newArray(0);
var oX = newArray(0);                  // per marker object: centre and radius (pixels),
var oY = newArray(0);                  //   parasite (-1 none), place t along it, counted,
var oR = newArray(0);                  //   pole (1 basal, 2 apical, 3 end of a parasite
var oOwn = newArray(0);                //   not oriented, 4 not at an end)
var oT = newArray(0);
var oUse = newArray(0);
var oPole = newArray(0);
var pObj = newArray(0);                // per parasite: objects on it, counted
var pCnt = newArray(0);
var pBas = newArray(0);                // per parasite: basal end (1 or 2, 0 not found) and why
var pWhy = newArray(0);
var pNuc = newArray(0);                //   nuclei inside, pole marker objects on it
var pMk  = newArray(0);
var pCls = newArray(0);                // per parasite: 0 not double-labelled, 1 marker at the
                                       //   basal pole, 2 apical, 3 indeterminable, 4 not at a pole
var pPlace   = newArray(0);                //   place of the marker along it (0 to 1, -1 none)
var poleN = newArray(0);               // weighted counts of pCls 1 to 4, and oriented objects
var poleOri = 0;
var poleNote = "";                     // orientation in words, for the Log
var objN = 0;                          // marker objects found, on parasites, counted
var objOn = 0;
var objUsed = 0;
var dblPolesUsed = false;              // the pole option is in force for this run
var poleNeedNuc = false;               // it reads the nuclear stain
var poleNeedMk  = false;               // it reads the pole marker
var perPar = newArray(0);              // Parasites.csv, one string per image

// pixel size actually applied to the last image, for the Log
var pxUsed = 0;                        // um, width ...
var pxUsedH = 0;                       // ... and height
var pxUnit = "";
var pxSource = "";

// Calibrate
var stepSliders = false;               // ImageJ 1.52a+: decimal sliders
var nonBlocking = false;               // ImageJ 1.52o+: panel left open
var calPrevID = 0;                     // preview stack
var calDrawID = 0;                     // image the overlay is built on
var calSummary = "";                   // counts shown in the panel
var calAction = "";
var calActRedraw  = "Redraw the preview with these values";
var calActKeep    = "Keep these values and go back";
var calActDiscard = "Discard the changes and go back";
var calActLook    = "Redraw, then let me zoom and scroll the preview";
var calBak = newArray(0);              // settings before calibrating
var calBakS = newArray(0);
var calSrc = "";                       // where the calibration images come from
var calSrcModes = newArray("One image I choose",
                           "One image picked at random",
                           "One random image from each condition");
var calChosenLab = "";
var calN = 0;                          // calibration images in use
var calNV = 2;                         // preview slices per image
var calLab  = newArray(0);             // their labels
var calPxV  = newArray(0);             // their pixel size ...
var calPxU  = newArray(0);             // ... unit
var calPxS  = newArray(0);             // ... and where it came from
var calSkip = 0;                       // picked but lacking a channel
var calMem  = 0;                       // picked but left out for memory
var calCntA = 0;                       // counts of the image just redrawn
var calCntB = 0;
var calCntC = 0;
var calCntD = 0;
var calCntE = 0;
var calSlots = 0;                      // cache of the slow detection steps:
var calKeyP  = newArray(0);            //   settings of each image's corrected
var calKeyM  = newArray(0);            //   channel and of its mask, and the
var calThr   = newArray(0);            //   thresholds that mask was made with
var calThrA  = newArray(0);
var calHit   = newArray(0);            //   and what the threshold left out
var calNote  = newArray(0);
var calMedT  = newArray(0);
var calMaxT  = newArray(0);
var calPoleT = newArray(6);            // double labelling: poles summed over the images
var calObjT  = 0;                      //   marker objects counted
var calImgLine = "";                   //   poles of the image just drawn, for its line
var calCapHits = 0;                    // images where the bright cap acted
var calSpikeHits = 0;                  // images where a saturated peak was left out
var calViewGains = "";                 // brightness the preview was drawn with
var calLastKey   = "";                 // settings its counted objects show
var calSlN       = 0;                  // sliders added to the panel so far

// image list, built once (collectImages)
var imagesCollected = false;
var imgPaths = newArray(0);
var imgMans  = newArray(0);
var imgConds = newArray(0);
var rootFolder = "";
var inputMode = "AUTO";
var inModes = newArray("A folder (experiments and/or conditions in subfolders)",
                       "A single folder of images",
                       "A single image file");
var inKeys = newArray("AUTO", "FOLDER", "FILE");

// MetaMorph .nd, read without Bio-Formats (ndParse)
var ndWaves  = newArray(0);            // wavelength names, in order
var ndWaveZ  = newArray(0);            // 1 when that wavelength has Z planes
var ndStages = newArray(0);            // stage position names
var ndNPos   = 1;                      // positions (series) in the file
var ndNT     = 1;                      // time points
var ndNZ     = 1;                      // Z planes
var ndZStep  = 0;                      // um between Z planes, 0 if not given
var ndStage  = false;                  // file names carry _s<position>
var ndTime   = false;                  // file names carry _t<time point>
var ndWave   = false;                  // file names carry _w<wavelength>
var ndDirKey = "";                     // folder listing kept for ndFile
var ndDirLst = newArray(0);

// remembered settings (settings file): keys and values as read
var setKeys = newArray(0);
var setVals = newArray(0);
var setFileName = "Count_and_Colocalise_settings.txt";
// settings saved under the former name of this tool (Parasite Quantification Tool) are still read
var oldSetFileName = "Parasite_Quantification_Tool_settings.txt";

var thrMethods = newArray("Default", "Huang", "Intermodes", "IsoData", "Li",
                          "MaxEntropy", "Mean", "MinError", "Minimum", "Moments",
                          "Otsu", "Percentile", "RenyiEntropy", "Shanbhag",
                          "Triangle", "Yen");


// ---------------- remembered settings ----------------
// The dialogs open with the settings of the last run, kept in a file of the
// ImageJ preferences folder. The first dialog can start from the validated
// defaults instead, or from the settings file of an earlier run (each run
// writes one, Settings.txt, next to its results).
prefsDir = call("ij.Prefs.getPrefsDir");
lastSetPath = "";
// ImageJ creates that folder only when it first saves its own preferences,
// on quitting: on a fresh installation it may not be there yet
if (lengthOf(prefsDir) > 0 && !File.isDirectory(prefsDir)) File.makeDirectory(prefsDir);
if (lengthOf(prefsDir) > 0 && File.isDirectory(prefsDir)) {
    if (!endsWith(prefsDir, File.separator)) prefsDir = prefsDir + File.separator;
    lastSetPath = prefsDir + setFileName;
}
nSet = 0;
readSetPath = lastSetPath;
if (lastSetPath != "" && !File.exists(lastSetPath) && File.exists(prefsDir + oldSetFileName))
    readSetPath = prefsDir + oldSetFileName;
if (readSetPath != "") nSet = setRead(readSetPath);
okG = applyGeneral();

// ---------------- dialog 1: what to do ----------------
// First, and alone: what the run is for. The next dialogs then show only
// what that task needs.
setSrcModes = newArray("Those of my last run", "The validated defaults",
                       "A settings file I choose (Settings.txt of a run)");
setSrcPick = setSrcModes[0];
if (nSet == 0) setSrcPick = setSrcModes[1];
if (isNaN(taskMode) || taskMode < 0 || taskMode > 2) taskMode = 0;
taskMode = round(taskMode);

Dialog.create("Count and Colocalise  v" + progVersion);
Dialog.addMessage("What do you want to do?");
Dialog.addChoice("I want to:", taskModes, taskModes[taskMode]);
Dialog.addChoice("Detection settings to start from:", setSrcModes, setSrcPick);
Dialog.addHelp("<html><h3>Count and Colocalise v" + progVersion + "</h3>" +
    "<p><b>Count objects</b>: nuclei (host cells), parasites and double-labelled parasites, " +
    "chosen in the next dialogs, with QC stacks showing on every image what was counted, " +
    "CSV files, a workbook and histograms.</p>" +
    "<p><b>Make images</b>: hyperstack TIFF, maximum Z projection and composite merges of " +
    "each file - nothing is counted.</p>" +
    "<p><b>Both</b>: the two, in one run.</p>" +
    "<p><b>Settings</b>: every run remembers its settings for the next one, and writes " +
    "Settings.txt next to its results; the detection settings of any of them can be the " +
    "starting point of a run.</p>" +
    "<p>Everything is explained at the top of the macro file.</p></html>");
Dialog.show();
tChoice = Dialog.getChoice();
for (q = 0; q < taskModes.length; q++) if (tChoice == taskModes[q]) taskMode = q;
setSrc  = Dialog.getChoice();
doCountT = false;
doImgT   = false;
if (taskMode == 0 || taskMode == 2) doCountT = true;
if (taskMode == 1 || taskMode == 2) doImgT = true;

// detection settings: last run (already read), validated defaults, or a file
if (setSrc == setSrcModes[2]) {
    sp = File.openDialog("Choose a settings file (Settings.txt in the results of a run)");
    nL = setRead(sp);
    if (nL == 0) exit("No settings found in:\n" + sp);
    okD = applyDetection();
} else if (setSrc == setSrcModes[0]) {
    if (nSet > 0) okD = applyDetection();
}

// ---------------- dialog 2: input and outputs ----------------
layModes = newArray("Multi-channel files, of the types below",
                    "One TIFF per channel (slides / coverslips)");
pixWays = newArray("From the image, the value given if it has none",
                   "The value given, whatever the image says");
inPick = inModes[0];
for (q = 0; q < inKeys.length; q++) if (inKeys[q] == inputMode) inPick = inModes[q];
openPick = openWays[0];
for (q = 0; q < openKeys.length; q++) if (openKeys[q] == openMethod) openPick = openWays[q];
layPick = layModes[0];
if (tiffMode) layPick = layModes[1];
pixPick = pixWays[0];
if (pixelForce) pixPick = pixWays[1];
// checkboxes in grids: the dialog stays short
cbL = newArray("Annotated QC stacks", "Results workbook (.xls)", "Histogram per experiment",
               "Pooled histogram");
cbV = newArray(doQC, doWorkbook, doHistExp, doHistPooled);
ioL = newArray("Hyperstack TIFF (all channels, all Z)", "Maximum Z projection",
               "Composite merge, all Z planes", "Composite merge of the projection");
ioV = newArray(doHyper, doProject, doMergeStack, doMergeProj);

Dialog.create("Input and outputs");
Dialog.addMessage("INPUT   (file types: their extensions, e.g.  czi nd lif nd2 lsm oib tif)");
Dialog.addChoice("Analyse:", inModes, inPick);
// one TIFF per channel is for counting only (no image of a whole file)
if (doCountT) Dialog.addChoice("Images:", layModes, layPick);
Dialog.addString("File types:", extList, 20);
Dialog.addCheckbox("A file holding several positions or series: analyse each one (.nd: always)", splitSeries);
Dialog.addChoice("Open the files with:", openWays, openPick);
Dialog.addCheckbox("Batch mode: faster, untick it if files fail to open", useBatch);
Dialog.addMessage("PIXEL SIZE  (every area setting is in um2)");
Dialog.addChoice("Pixel size:", pixWays, pixPick);
sr = sameRow();
Dialog.addNumber("value", pixelSize, 4, 8, "um");
if (doCountT) {
    Dialog.addMessage("COUNTING OUTPUTS  (the counts always go to CSV files)");
    Dialog.addCheckboxGroup(1, 4, cbL, cbV);
}
if (doImgT) {
    Dialog.addMessage("IMAGES TO MAKE  (from each file)");
    Dialog.addCheckboxGroup(2, 2, ioL, ioV);
}
Dialog.addCheckbox("Choose the output folder myself (otherwise: Results, inside the input folder)", pickOutDir);
Dialog.addHelp("<html><h3>Input and outputs</h3>" +
    "<p><b>Input</b>: a folder of experiments / conditions / images, a single folder, or " +
    "a single file. File types are given by their extensions (czi nd lif nd2 tif ...). " +
    "MetaMorph .nd files are read by the tool itself, without Bio-Formats; TIFF files by " +
    "ImageJ; the other formats by Bio-Formats. A file holding several positions gives one " +
    "image per position.</p>" +
    "<p><b>One TIFF per channel</b>: slide scanner layout, for counting only.</p>" +
    "<p><b>Pixel size</b>: read from each image and converted to um. An image with none, " +
    "calibrated in inches (a DPI tag) or out of the range of a microscope gets the value " +
    "given here. Every area setting is in um2.</p>" +
    "<p><b>QC stacks</b>: one per image, showing what was counted; their panels are chosen " +
    "in the next dialog.</p></html>");
Dialog.show();

inChoice  = Dialog.getChoice();
layChoice = layModes[0];
if (doCountT) layChoice = Dialog.getChoice();
extList     = Dialog.getString();
splitSeries = Dialog.getCheckbox();
openChoice  = Dialog.getChoice();
useBatch    = Dialog.getCheckbox();
pixChoice   = Dialog.getChoice();
pixelSize   = Dialog.getNumber();
if (doCountT) {
    doQC         = Dialog.getCheckbox();
    doWorkbook   = Dialog.getCheckbox();
    doHistExp    = Dialog.getCheckbox();
    doHistPooled = Dialog.getCheckbox();
}
if (doImgT) {
    doHyper      = Dialog.getCheckbox();
    doProject    = Dialog.getCheckbox();
    doMergeStack = Dialog.getCheckbox();
    doMergeProj  = Dialog.getCheckbox();
}
pickOutDir = Dialog.getCheckbox();

doCount = doCountT;
if (doImgT == false) {
    doHyper      = false;
    doProject    = false;
    doMergeStack = false;
    doMergeProj  = false;
}
inputMode = "AUTO";
for (q = 0; q < inModes.length; q++) if (inChoice == inModes[q]) inputMode = inKeys[q];
openMethod = "auto";
for (q = 0; q < openWays.length; q++) if (openChoice == openWays[q]) openMethod = openKeys[q];
tiffMode = false;
if (layChoice == layModes[1]) tiffMode = true;
pixelForce = false;
if (pixChoice == pixWays[1]) pixelForce = true;
if (isNaN(pixelSize) || pixelSize <= 0) exit("The pixel size must be greater than 0.");
nExt = parseExts(extList);
if (tiffMode == false && nExt == 0)
    exit("Give at least one file type to analyse, for example:  czi nd");

doMerge = false;
if (doMergeStack || doMergeProj) doMerge = true;
doConvert = false;
if (doHyper || doProject || doMergeStack || doMergeProj) doConvert = true;
// Image outputs rebuild a view from ONE multi-channel file; with one TIFF
// per channel they would open a single channel only, so they are off here.
if (tiffMode && doConvert) {
    doConvert = false;
    doMerge = false;
    skippedImgOut = true;       // reported in the Log once it is cleared
}
if (doCount == false && doConvert == false)
    exit("Nothing to make: tick at least one image in 'Images to make'.");
if (doCount == false) {
    doWorkbook = false;
    doHistExp = false;
    doHistPooled = false;
    doQC = false;
}

// ---------------- the images ----------------
// Asked for now, so that the next dialog can show the channels of the first
// image - read from its metadata, the file is not opened in full. With one
// TIFF per channel the files are sorted by the words of the next dialog, so
// they are listed after it. (The channels are asked for counting and for
// the colours of the images made.)
showCh = false;
if (doCount || doConvert) showCh = true;
okP = pickInput();
if (tiffMode == false) {
    nFound = collectImages();
    if (nFound == 0) noImageExit();
}
userOutDir = "";
if (pickOutDir) userOutDir = getDirectory("Select the output folder");
// an output folder inside the input folder (or the input folder itself):
// what an earlier run wrote there (QC stacks, merges...) is no image to count
if (tiffMode == false && lengthOf(userOutDir) > 0) {
    nKept = dropUnder(userOutDir);
    if (nKept == 0) noImageExit();
}
if (tiffMode == false && showCh) okF = firstImageInfo();

// ---------------- dialog 3: channels and objects ----------------
// Channel numbers suggested by the colours of the first image, when it has
// them; a number then taken by another role is cleared.
if (tiffMode == false && (firstB > 0 || firstG > 0 || firstR > 0)) {
    sugg = newArray(firstB, firstG, firstR, firstD);
    cur  = newArray(chBLUE, chGREEN, chRED, chDIC);
    for (q = 0; q < 4; q++) {
        if (sugg[q] > 0) cur[q] = sugg[q];
    }
    for (q = 0; q < 4; q++) {
        if (sugg[q] > 0) continue;
        for (r = 0; r < 4; r++) {
            if (r != q && sugg[r] > 0 && cur[q] == sugg[r]) cur[q] = 0;
        }
        if (cur[q] > firstNC) cur[q] = 0;
    }
    chBLUE = cur[0];
    chGREEN = cur[1];
    chRED = cur[2];
    chDIC = cur[3];
}

// The channels of the files: one row per channel of the first image (4 when
// it could not be read, more when a role was given a higher number), with
// what the channel is (counting), its colour in the images made, and whether
// it goes into the merges.
roleNames = newArray("Nuclear stain", "Parasite label", "Second marker", "Pole marker",
                     "Transmission / DIC", "Not used");
colNames  = newArray("as in the file", "grey", "blue", "green", "red", "cyan", "magenta",
                     "yellow");
nChRows = firstNC;
if (nChRows == 0) nChRows = maxOf(4, maxOf(maxOf(chBLUE, chGREEN), maxOf(maxOf(chRED, chDIC), chPOLE)));
if (nChRows > 12) nChRows = 12;
rowRole = newArray(nChRows);
rowCol  = newArray(nChRows);
rowMrg  = newArray(nChRows);
remC = newArray(0);
if (chColSet != "") remC = split(chColSet, ",");
remM = newArray(0);
if (chMrgSet != "") remM = split(chMrgSet, ",");
for (k = 1; k <= nChRows; k++) {
    r = 5;
    if (chPOLE == k)  r = 3;
    if (chDIC == k)   r = 4;
    if (chRED == k)   r = 2;
    if (chGREEN == k) r = 1;
    if (chBLUE == k)  r = 0;
    rowRole[k - 1] = r;
    // colour: the one remembered, else the file's - or, for a channel the
    // file shows in grey, the usual colour of what it is
    fc = "?";
    if (firstCols.length >= k) fc = firstCols[k - 1];
    cv = "";
    if (remC.length >= k) cv = trimS(remC[k - 1]);   // (assigned: a function giving text)
    if (colKeyIdx(cv) < 0) {
        cv = "file";
        if (fc == "grey" || fc == "?" || fc == "other") cv = roleColour(r);
    }
    rowCol[k - 1] = cv;
    mv = -1;
    if (remM.length >= k) {
        tm = trimS(remM[k - 1]);
        mv = parseInt(tm);
    }
    if (mv != 0 && mv != 1) {
        mv = 0;
        if (r == 0 && mergeBlue)  mv = 1;
        if (r == 1 && mergeGreen) mv = 1;
        if (r == 2 && mergeRed)   mv = 1;
        if (r == 4 && mergeDIC)   mv = 1;
    }
    rowMrg[k - 1] = mv;
}

// ---- what to count (before the channels, which then know what is needed) ----
if (doCount) {
    Dialog.create("What to count");
    Dialog.addMessage("WHAT TO COUNT");
    Dialog.addCheckboxGroup(1, 3, newArray("Nuclei (host cells)", "Parasites", "Double-labelled parasites"),
                            newArray(countNuclei, countGreen, countDouble));
    Dialog.addChoice("Reported measure:", measureModes, measureModes[measureMode]);
    Dialog.addChoice("Double labelling:", dblModes, dblModes[dblMode]);
    Dialog.addCheckbox("Poles: basal or apical pole of the marker (option)", doPoles);
    if (doQC) {
        Dialog.addMessage("QC STACK  (one per image: the panels it holds)");
        Dialog.addCheckboxGroup(2, 4, newArray("Nuclei", "Parasites", "Marker", "Marker + parasites",
                                               "Poles", "All channels", "Each channel also raw", ""),
                                newArray(qcPanN, qcPanP, qcPanR, qcPanRG, qcPanPol, qcPanAll, qcRaw, false));
    }
    Dialog.addHelp("<html><h3>What to count</h3>" +
        "<p><b>Reported measure</b>: with nuclei counted, it is given per 100 host cells; " +
        "without them, as a count per image.</p>" +
        "<p><b>Double labelling</b> is scored on the parasites: by the overlap of each parasite " +
        "with the marker, by marker objects inside it (beads, a labelled structure), or by the " +
        "parasite lying inside a marker object (a vacuole, a cell). <b>Poles</b>: whether the " +
        "marker sits at the basal or the apical pole (see the top of the macro file).</p>" +
        "<p><b>QC stack</b>: the panels each stack holds - the nuclei, the parasites, the " +
        "marker, marker + parasites, the poles (pole option), all channels (nuclei and parasites " +
        "counted) - drawn as in Calibrate; 'each channel also raw' adds the channel without " +
        "outlines before its panel. A panel for something not counted is left out.</p>" +
        "<p>The channels of each object come next.</p></html>");
    Dialog.show();
    countNuclei = Dialog.getCheckbox();
    countGreen  = Dialog.getCheckbox();
    countDouble = Dialog.getCheckbox();
    mChoice     = Dialog.getChoice();
    for (q = 0; q < measureModes.length; q++) if (mChoice == measureModes[q]) measureMode = q;
    dChoice     = Dialog.getChoice();
    for (q = 0; q < dblModes.length; q++) if (dChoice == dblModes[q]) dblMode = q;
    doPoles     = Dialog.getCheckbox();
    if (doQC) {
        qcPanN   = Dialog.getCheckbox();
        qcPanP   = Dialog.getCheckbox();
        qcPanR   = Dialog.getCheckbox();
        qcPanRG  = Dialog.getCheckbox();
        qcPanPol = Dialog.getCheckbox();
        qcPanAll = Dialog.getCheckbox();
        qcRaw    = Dialog.getCheckbox();
    }
    if (countNuclei == false && countGreen == false)
        exit("Tick at least one object class to count.");
    if (countDouble && countGreen == false)
        exit("Colocalisation is scored on the parasite objects, so parasites " +
             "have to be counted as well.");
}

// ---- the channels ----
if (showCh) {
    // shown again, holding what was chosen, until the channels make sense
    chErr = "x";
    while (chErr != "") {
        Dialog.create("Channels");
        if (firstInfo != "") Dialog.addMessage(firstInfo);
        if (tiffMode) {
            Dialog.addMessage("CHANNEL FILES  (a word that the file name of each channel contains)");
            Dialog.addString("Nuclear stain", kwBlue, 12);
            sr = sameRow();
            Dialog.addString("Parasite label", kwGreen, 12);
            Dialog.addString("Second marker", kwRed, 12);
            sr = sameRow();
            Dialog.addString("Subfolder of the TIFFs", dataSub, 12);
            Dialog.addString("Pole marker (optional)", kwPole, 12);
        } else {
            hd = "";
            if (doCount) hd = "what each one is";
            if (doConvert) {
                if (hd != "") hd = hd + ", ";
                hd = hd + "its colour in the images made";
            }
            if (doMerge) hd = hd + ", in the merges or not";
            Dialog.addMessage("CHANNELS  (" + hd + ")");
            for (k = 1; k <= nChRows; k++) {
                lab = rowLabel(k);
                if (doCount) {
                    Dialog.addChoice(lab, roleNames, roleNames[rowRole[k - 1]]);
                    if (doConvert) {
                        sr = sameRow();
                        Dialog.addChoice("colour", colNames, colNames[colKeyIdx(rowCol[k - 1])]);
                    }
                } else {
                    Dialog.addChoice(lab + "   colour", colNames, colNames[colKeyIdx(rowCol[k - 1])]);
                }
                if (doMerge) {
                    sr = sameRow();
                    Dialog.addCheckbox("in the merges", rowMrg[k - 1]);
                }
            }
            // (a group spans the whole width: a single long tick box would widen the columns)
            if (doCount) {
                adS = newArray(1);
                adS[0] = autoDetectChannels;
                Dialog.addCheckboxGroup(1, 1, newArray("Channels in another order in some files: find each one by its colour in the first file"), adS);
            }
        }
        Dialog.addHelp("<html><h3>Channels</h3>" +
            "<p>One row per channel of the first file, with the colour and the name its file gives " +
            "it. Say what each channel is (a channel not used for counting: Not used), the colour it " +
            "gets in the images made (hyperstack, projection, merges; 'as in the file' keeps the " +
            "colour of the file) and whether it goes into the merges. The hyperstack and the " +
            "projection show the channels one at a time, the merges show them together.</p>" +
            "<p>The same channel numbers serve for every file. When the order changes from file to " +
            "file, tick the last box: each channel is then found in every file by the colour it has " +
            "in the first one (the channels used need different colours there).</p>" +
            "<p><b>One TIFF per channel</b>: a word that the file name of each channel contains.</p></html>");
        Dialog.show();

        if (tiffMode) {
            kwBlue  = Dialog.getString();
            kwGreen = Dialog.getString();
            kwRed   = Dialog.getString();
            dataSub = Dialog.getString();
            kwPole  = Dialog.getString();
        } else {
            for (k = 1; k <= nChRows; k++) {
                if (doCount) {
                    rc = Dialog.getChoice();
                    for (q = 0; q < roleNames.length; q++) if (rc == roleNames[q]) rowRole[k - 1] = q;
                }
                if (doConvert) {
                    cc = Dialog.getChoice();
                    for (q = 0; q < colNames.length; q++) {
                        if (cc == colNames[q]) {
                            ck = colKey(q);
                            rowCol[k - 1] = ck;
                        }
                    }
                }
            }
            if (doMerge) {
                for (k = 1; k <= nChRows; k++) rowMrg[k - 1] = Dialog.getCheckbox();
            }
            if (doCount) autoDetectChannels = Dialog.getCheckbox();
        }

        // what each channel is -> the channel numbers; a role on two channels,
        // or missing for what is counted, is asked again
        chErr = "";
        if (tiffMode == false && doCount) {
            nUse = newArray(5);
            firstK = newArray(5);
            dupTxt = "";
            for (k = 1; k <= nChRows; k++) {
                r = rowRole[k - 1];
                if (r < 5) {
                    nUse[r] = nUse[r] + 1;
                    if (nUse[r] == 1) firstK[r] = k;
                    if (nUse[r] == 2)
                        dupTxt = dupTxt + "\n   - " + roleNames[r] + ": channels " + firstK[r] + " and " + k;
                }
            }
            if (dupTxt != "") chErr = chErr + "\nOne channel only for each:" + dupTxt;
            chBLUE  = firstK[0];
            chGREEN = firstK[1];
            chRED   = firstK[2];
            chPOLE  = firstK[3];
            chDIC   = firstK[4];
            chMiss = "";
            if (countNuclei && chBLUE == 0) chMiss = chMiss + "\n   - the nuclear stain";
            if (countGreen && chGREEN == 0) chMiss = chMiss + "\n   - the parasite label";
            if (countDouble && chRED == 0)  chMiss = chMiss + "\n   - the second marker";
            if (chMiss != "") chErr = chErr + "\nSay which channel is:" + chMiss;
            // fewer channels than objects to count: nothing to choose here
            nNeed = 0;
            if (countNuclei) nNeed = nNeed + 1;
            if (countGreen)  nNeed = nNeed + 1;
            if (countDouble) nNeed = nNeed + 1;
            if (chMiss != "" && nChRows < nNeed)
                exit("The first image has " + nChRows + " channel(s) only, too few for what is to be counted:" +
                     chMiss + "\nCount fewer objects ('What to count'), or choose files with more channels.");
            // found by colour in every file: the channels used need a colour
            // of their own in the first file
            if (autoDetectChannels && chErr == "") {
                cTxt = colourClash(firstK);
                if (cTxt != "") chErr = chErr + "\nTo find the channels of each file by their colour, the ones used " +
                                        "need different colours in the first file:" + cTxt +
                                        "\n(or untick the last box: the same numbers for every file)";
            }
        }
        if (chErr != "") showMessage("Channels", "Please correct:" + chErr);
    }
}
chCols = Array.copy(rowCol);
chMrg  = Array.copy(rowMrg);
// whole channel numbers, 0 = absent
chBLUE  = chanOk(chBLUE);
chGREEN = chanOk(chGREEN);
chRED   = chanOk(chRED);
chDIC   = chanOk(chDIC);
chPOLE  = chanOk(chPOLE);
kwPole  = trimS(kwPole);
// per-file detection: the colour of each role in the first file
roleCols = newArray("", "", "", "");
roleNum = newArray(chBLUE, chGREEN, chRED, chDIC);
for (q = 0; q < 4; q++) {
    if (roleNum[q] > 0 && roleNum[q] <= firstCols.length) roleCols[q] = firstCols[roleNum[q] - 1];
    if (roleCols[q] == "?" || roleCols[q] == "other") roleCols[q] = "";
}


// ---------------- dialog 3b: marker objects and poles ----------------
// Only when the double labelling is scored on objects, or the poles are
// asked for.
dblPolesUsed = false;
if (doCount && countDouble && doPoles && dblMode != 2) dblPolesUsed = true;
if (doCount && countDouble && (dblMode > 0 || doPoles)) {
    Dialog.create("Double labelling: objects and poles");
    if (dblMode == 1) {
        Dialog.addMessage("MARKER OBJECTS INSIDE THE PARASITES  (beads, a labelled structure...)");
        Dialog.addNumber("Object area from", objMinArea, 2, 6, "um2");
        sr = sameRow();
        Dialog.addNumber("to", objMaxArea, 2, 6, "um2");
        Dialog.addNumber("Its centre at most", objTol, 2, 6,
                         "um outside the parasite (beads sit on its surface)");
        Dialog.addNumber("At least", objMinN, 0, 4, "counted object(s) per double-labelled parasite");
        Dialog.addCheckbox("Split touching objects (watershed)", objWatershed);
        Dialog.addChoice("Objects counted:", posModes, posModes[posMode]);
    }
    if (dblMode == 2) {
        Dialog.addMessage("MARKER OBJECTS AROUND THE PARASITES  (a vacuole, a cell...)");
        Dialog.addNumber("Object area from", encMinArea, 1, 8, "um2");
        sr = sameRow();
        Dialog.addNumber("to", encMaxArea, 1, 8, "um2");
        Dialog.addMessage("A parasite is inside one when the minimum overlap of the next dialog\n" +
                          "(a % of its area) lies in it.");
        if (doPoles)
            Dialog.addMessage("The pole option does not apply here (the parasite is inside the\n" +
                              "object): it is left off for this run.");
    }
    if (dblMode == 1 || dblPolesUsed) {
        Dialog.addMessage("PLACES ALONG THE PARASITE  (on its long axis, tip to tip)");
        Dialog.addNumber("Centre: the central", centrePct, 0, 4, "% of its length");
        sr = sameRow();
        Dialog.addNumber("extremity (pole): the last", endPct, 0, 4, "% at each end");
    }
    if (dblPolesUsed) {
        Dialog.addMessage("POLES  (which end of each parasite is basal)");
        Dialog.addChoice("Oriented by:", poleRefs, poleRefs[poleRef]);
        Dialog.addChoice("The pole marker marks:", poleSides, poleSides[poleSide]);
        Dialog.addNumber("Nucleus of a parasite from", pnMinArea, 2, 6, "um2");
        sr = sameRow();
        Dialog.addNumber("to", pnMaxArea, 2, 6, "um2");
        Dialog.addNumber("Pole marker: threshold factor", pmThresholdFactor, 2, 6, "");
        Dialog.addNumber("Pole marker object from", pmMinArea, 2, 6, "um2");
        sr = sameRow();
        Dialog.addNumber("to", pmMaxArea, 2, 6, "um2");
        Dialog.addNumber("No orientation when the reference lies within", poleDeadPct, 0, 4,
                         "% of the length from the centre");
    }
    Dialog.addHelp("<html><h3>Marker objects and poles</h3>" +
        "<p><b>Marker objects inside the parasite</b>: the marker is cut into objects of the " +
        "size given; each belongs to the parasite holding its centre - or within the distance " +
        "given of its outline. A parasite holding at least the number of objects given, at the " +
        "place asked, is double-labelled.</p>" +
        "<p><b>The parasite inside a marker object</b>: the marker objects of the size given " +
        "(a vacuole, a cell) hold a parasite when the minimum overlap of its area lies in them.</p>" +
        "<p><b>Places</b> are read on the long axis of the parasite, tip to tip: the centre is the " +
        "central part of its length, an extremity the last part at each end.</p>" +
        "<p><b>Poles</b>: the end of each parasite nearest its nucleus is basal (the nucleus of a " +
        "tachyzoite lies in its basal half); a pole marker gives the basal end, or the apical one " +
        "(an extruded conoid labelled in fluorescence). With both, one is enough, and if they " +
        "disagree the parasite is not oriented. The marker of a double-labelled parasite at an " +
        "extremity is then at the basal pole, the apical pole, or - parasite not oriented - " +
        "indeterminable; the three and their sum are given per image and per condition.</p></html>");
    Dialog.show();
    if (dblMode == 1) {
        objMinArea   = Dialog.getNumber();
        objMaxArea   = Dialog.getNumber();
        objTol       = Dialog.getNumber();
        objMinN      = Dialog.getNumber();
        objWatershed = Dialog.getCheckbox();
        pc           = Dialog.getChoice();
        for (q = 0; q < posModes.length; q++) if (pc == posModes[q]) posMode = q;
    }
    if (dblMode == 2) {
        encMinArea = Dialog.getNumber();
        encMaxArea = Dialog.getNumber();
    }
    if (dblMode == 1 || dblPolesUsed) {
        centrePct = Dialog.getNumber();
        endPct    = Dialog.getNumber();
    }
    if (dblPolesUsed) {
        rc = Dialog.getChoice();
        for (q = 0; q < poleRefs.length; q++) if (rc == poleRefs[q]) poleRef = q;
        sc = Dialog.getChoice();
        for (q = 0; q < poleSides.length; q++) if (sc == poleSides[q]) poleSide = q;
        pnMinArea         = Dialog.getNumber();
        pnMaxArea         = Dialog.getNumber();
        pmThresholdFactor = Dialog.getNumber();
        pmMinArea         = Dialog.getNumber();
        pmMaxArea         = Dialog.getNumber();
        poleDeadPct       = Dialog.getNumber();
    }
    okV = objSettingsOk();
}
poleNeedNuc = false;
poleNeedMk  = false;
if (dblPolesUsed && (poleRef == 0 || poleRef == 2)) poleNeedNuc = true;
if (dblPolesUsed && (poleRef == 1 || poleRef == 2)) poleNeedMk = true;
if (poleNeedMk) {
    if (tiffMode && kwPole == "")
        exit("The parasites are to be oriented by a pole marker: give the word its\n" +
             "files contain ('Channels'), or orient them by their nucleus.");
    if (tiffMode == false && chPOLE == 0)
        exit("The parasites are to be oriented by a pole marker: choose 'Pole marker' for\n" +
             "its channel ('Channels'), or orient them by their nucleus.");
    if (tiffMode == false && autoDetectChannels == false) {
        if (chPOLE == chBLUE || chPOLE == chGREEN || chPOLE == chRED)
            exit("The pole marker cannot share its channel (" + chPOLE + ") with the nuclear\n" +
                 "stain, the parasite label or the second marker.");
    }
}
if (poleNeedNuc && tiffMode == false && autoDetectChannels == false && chBLUE == 0)
    exit("The parasites are to be oriented by their nucleus: choose 'Nuclear stain' for\n" +
         "its channel ('Channels').");
// the pole marker is never taken for a colour
dcSkip = 0;
if (poleNeedMk && tiffMode == false) dcSkip = chPOLE;

if (tiffMode) {
    nFound = collectImages();
    if (nFound == 0) noImageExit();
}
// Reported measure: per 100 host cells with nuclei, a count per image
// without; double labelling measures need double labelling counted.
okM = setMeasure();

// ---------------- dialog 4: detection settings ----------------
// Shown again after each calibration, holding the values it returned. The
// first menu stands for the "Calibrate" button: a macro dialog cannot hold
// extra buttons, so the choice made there is carried out at OK.

// Parts of the calibration panel that depend on the ImageJ version
stepSliders = versionAtLeast("1.52a");
nonBlocking = versionAtLeast("1.52o");

stopWhatTxt = "host cells";
if (stopOn == "parasites") stopWhatTxt = "parasites";

if (doCount) {
    // objects the stop target can be counted in: only those being counted
    stopWhat = newArray(0);
    if (countNuclei) stopWhat = Array.concat(stopWhat, "host cells");
    if (countGreen)  stopWhat = Array.concat(stopWhat, "parasites");
    okW = 0;
    for (q = 0; q < stopWhat.length; q++) if (stopWhat[q] == stopWhatTxt) okW = 1;
    if (okW == 0) stopWhatTxt = stopWhat[0];

    calRun = "Count with the settings below";
    calNuc = "Calibrate nuclei first";
    calPar = "Calibrate parasites first";
    calDbl = "Calibrate double labelling first";
    calSrcPick = calSrcModes[0];
    calModes = newArray(calRun);
    if (countNuclei) calModes = Array.concat(calModes, calNuc);
    if (countGreen)  calModes = Array.concat(calModes, calPar);
    if (countDouble) calModes = Array.concat(calModes, calDbl);

    // where each class is read from, for the block titles
    srcB = "channel " + chBLUE;
    srcG = "channel " + chGREEN;
    srcR = "marker: channel " + chRED;
    if (autoDetectChannels) {
        srcB = "blue channel";
        srcG = "green channel";
        srcR = "marker: red channel";
    }
    if (tiffMode) {
        srcB = "files '" + kwBlue + "'";
        srcG = "files '" + kwGreen + "'";
        srcR = "marker: files '" + kwRed + "'";
    }

    calPick = "";
    while (calPick != calRun) {
        // preprocessing in use, shown under each block (Calibrate changes it)
        wsN = "off";
        if (nucleiWatershed) wsN = "" + nucleiWsTol + " px";
        cN = circMin(nucleiCirc);
        cG = circMin(greenCirc);
        prepN = "      in Calibrate: " + nucleiThresholdMethod + " threshold, background " +
                nucleiBgRolling + " px, blur " + nucleiBlurSigma + " px, circularity " + cN +
                ", watershed " + wsN;
        prepG = "      in Calibrate: " + greenThresholdMethod + " threshold, background " +
                greenBgRolling + " px, blur " + greenBlurSigma + " px, circularity " + cG +
                ", watershed tolerance " + greenWsTol + " px";
        prepR = "      in Calibrate: " + redThresholdMethod + " threshold, background " +
                redBgRolling + " px, blur " + redBlurSigma + " px";

        // Kept compact - a laptop screen must still show its OK button:
        // short fields share a row, menus have a row of their own (in a
        // shared row they would widen every row), explanations under Help.
        Dialog.create("Detection settings");
        Dialog.addChoice("Next:", calModes, calRun);
        Dialog.addChoice("Calibrate on:", calSrcModes, calSrcPick);

        if (countNuclei) {
            Dialog.addMessage("NUCLEI  (" + srcB + ")   areas in um2");
            Dialog.addNumber("Threshold factor", nucleiThresholdFactor, 2, 6, "");
            sr = sameRow();
            Dialog.addNumber("bright cap", nucleiCapF, 1, 4, "x median");
            sr = sameRow();
            Dialog.addNumber("Minimum area", nucleiMinArea, 1, 6, "");
            Dialog.addCheckbox("Overlapping nuclei count as several cells:", splitBigNuclei);
            sr = sameRow();
            Dialog.addNumber("above", nucleiDoubletFactor, 2, 4, "x median");
            sr = sameRow();
            Dialog.addNumber("at most", nucleiMaxSplit, 0, 4, "cells");
            Dialog.addNumber("No cell under", nucleiFragFactor, 2, 4, "x median (0 = off)");
            Dialog.addChoice("Nuclei cut by the border:", edgeModes, edgeModes[nucleiEdgeMode]);
            Dialog.addMessage(prepN);
        }
        if (countGreen) {
            Dialog.addMessage("PARASITES  (" + srcG + ")   areas in um2");
            Dialog.addNumber("Threshold factor", greenThresholdFactor, 2, 6, "");
            sr = sameRow();
            Dialog.addNumber("bright cap", greenCapF, 1, 4, "x median");
            sr = sameRow();
            Dialog.addNumber("Area from", greenMinArea, 2, 6, "");
            sr = sameRow();
            Dialog.addNumber("to", greenMaxArea, 2, 6, "");
            Dialog.addCheckbox("Split touching parasites (watershed)", greenWatershed);
            Dialog.addCheckbox("Clumps count as several parasites:", splitBigParasites);
            sr = sameRow();
            Dialog.addNumber("above", greenDoubletFactor, 2, 4, "x median");
            sr = sameRow();
            Dialog.addNumber("at most", greenMaxSplit, 0, 4, "parasites");
            Dialog.addMessage(prepG);
        }
        if (countDouble) {
            dHow = "by overlap";
            if (dblMode == 1) dHow = "by marker objects inside the parasite";
            if (dblMode == 2) dHow = "the parasite inside a marker object";
            Dialog.addMessage("DOUBLE LABELLING  (" + srcR + "), " + dHow);
            Dialog.addNumber("Marker threshold factor", redThresholdFactor, 2, 6, "");
            sr = sameRow();
            Dialog.addNumber("bright cap", redCapF, 1, 4, "x median");
            if (dblMode != 1) {
                sr = sameRow();
                Dialog.addNumber("Minimum overlap", minOverlapPercent, 0, 6, "%");
            }
            Dialog.addMessage(prepR);
        }
        Dialog.addMessage("STOP EARLY, MINIMUM PER IMAGE  (optional, 0 = off; see Help)");
        Dialog.addNumber("Target per condition", stopTarget, 0, 6, "");
        sr = sameRow();
        Dialog.addChoice("in", stopWhat, stopWhatTxt);
        labP = "Minimum per image: parasites";
        if (countNuclei) Dialog.addNumber("Minimum per image: host cells", minCellsImg, 0, 6, "");
        if (countGreen) {
            // next to the host cells
            if (countNuclei) {
                sr = sameRow();
                labP = "parasites";
            }
            Dialog.addNumber(labP, minParImg, 0, 6, "");
        }
        Dialog.addChoice("An image under the minimum is", lowModes, lowModes[lowMode]);
        Dialog.addHelp("<html><h3>Detection settings</h3>" +
            "<p><b>Threshold factor</b>: the automatic threshold of each image times this factor " +
            "(above 1: stricter, fewer and smaller objects).</p>" +
            "<p><b>Bright cap</b>: in each image, an object brighter than this x the median " +
            "brightness of what is detected (debris, a dead cell) is left out of the threshold " +
            "calculation, so that it cannot raise the threshold above the ordinary objects. It " +
            "is still detected. Hot pixels are not concerned. 0 = off.</p>" +
            "<p><b>Clusters</b>: an object larger than the given multiple of the median area " +
            "of its image counts as several (area / median).</p>" +
            "<p><b>Calibrate</b>: pick it in the first menu, then OK: the counted objects are " +
            "drawn on one or several images, with a slider for every setting, before the run - " +
            "the settings listed under each block too.</p>" +
            "<p><b>Double labelling</b>: by overlap, a parasite is double-labelled when at least the " +
            "minimum overlap of its area is positive in the mask of the marker; by marker objects, " +
            "when it holds them (size, place and number: the dialog before, and Calibrate); inside " +
            "a marker object, when that fraction of its area lies in one.</p>" +
            "<p><b>Stop early</b>: each condition is counted image by image until the target is " +
            "reached; the image reaching it counts in full. <b>Minimum per image</b>: an image " +
            "under it is left out, or kept for a condition that does not reach the target " +
            "without it. See the top of the macro file.</p></html>");
        Dialog.show();

        calPick    = Dialog.getChoice();
        calSrcPick = Dialog.getChoice();
        if (countNuclei) {
            nucleiThresholdFactor = Dialog.getNumber();
            nucleiCapF            = Dialog.getNumber();
            nucleiMinArea         = Dialog.getNumber();
            splitBigNuclei        = Dialog.getCheckbox();
            nucleiDoubletFactor   = Dialog.getNumber();
            nucleiMaxSplit        = Dialog.getNumber();
            nucleiFragFactor      = Dialog.getNumber();
            edgeChoice            = Dialog.getChoice();
            // 0 = off. Kept below 1: at 1, every nucleus under the median -
            // half of them - would count for nothing.
            if (isNaN(nucleiFragFactor) || nucleiFragFactor < 0) nucleiFragFactor = 0;
            if (nucleiFragFactor > 0.9) nucleiFragFactor = 0.9;
            nucleiEdgeMode = 0;
            if (edgeChoice == edgeModes[1]) nucleiEdgeMode = 1;
            if (edgeChoice == edgeModes[2]) nucleiEdgeMode = 2;
            cv = capOk(nucleiCapF);
            nucleiCapF = cv;
        }
        if (countGreen) {
            greenThresholdFactor = Dialog.getNumber();
            greenCapF            = Dialog.getNumber();
            greenMinArea         = Dialog.getNumber();
            greenMaxArea         = Dialog.getNumber();
            greenWatershed       = Dialog.getCheckbox();
            splitBigParasites    = Dialog.getCheckbox();
            greenDoubletFactor   = Dialog.getNumber();
            greenMaxSplit        = Dialog.getNumber();
            ok = parClusterOk();
            cv = capOk(greenCapF);
            greenCapF = cv;
        }
        if (countDouble) {
            redThresholdFactor = Dialog.getNumber();
            redCapF            = Dialog.getNumber();
            if (dblMode != 1) minOverlapPercent = Dialog.getNumber();
            cv = capOk(redCapF);
            redCapF = cv;
        }
        stopTarget  = Dialog.getNumber();
        stopWhatTxt = Dialog.getChoice();
        if (countNuclei) minCellsImg = Dialog.getNumber();
        if (countGreen)  minParImg   = Dialog.getNumber();
        lowChoice = Dialog.getChoice();
        if (isNaN(stopTarget) || stopTarget < 0) stopTarget = 0;
        stopOn = "cells";
        if (stopWhatTxt == "parasites") stopOn = "parasites";
        // whole numbers of objects; 0 = no minimum
        if (isNaN(minCellsImg) || minCellsImg < 0) minCellsImg = 0;
        if (isNaN(minParImg) || minParImg < 0) minParImg = 0;
        minCellsImg = round(minCellsImg);
        minParImg   = round(minParImg);
        lowMode = 0;
        if (lowChoice == lowModes[1]) lowMode = 1;

        if (calPick == calNuc) okCal = calibrate("nuclei", calSrcPick);
        if (calPick == calPar) okCal = calibrate("parasites", calSrcPick);
        if (calPick == calDbl) okCal = calibrate("double", calSrcPick);
    }
}

// The settings of this run are those the next one starts from.
if (lastSetPath != "") okS = saveSettings(lastSetPath);


// ======================================================================
//  DISPLAY  -  QC images only, never a measurement
// ======================================================================
// (brightness of each channel: qcGainBlue / qcGainGreen / qcGainRed, set
// in Calibrate)
labelScale   = 1.0;
recapScale   = 1.0;
strokeScale  = 1.0;
keepWindowsOpen = false;


// ======================================================================
//  CONDITIONS LEFT OUT OF THE CALCULATIONS
// ======================================================================
// "<experiment folder>/<condition folder>", or just the condition folder
// when there is no experiment level. Excluded images are still processed
// and still produce a QC stack; they are marked in the workbook.
excludedConditions = newArray();


// ======================================================================
//  PATHS
// ======================================================================
sep = File.separator;

manList  = imgMans;
condList = imgConds;
pathList = imgPaths;

// Calibrate may have written to the Log; the run starts on a clean one,
// and reports the pixel size again at its first image.
calibShown = false;
print("\\Clear");
print("Count and Colocalise v" + progVersion);
if (tiffMode)
    print("One TIFF per channel: " + tKey.length + " acquisition(s) found " +
          "(experiment x coverslip x field)");
if (skippedImgOut)
    print("Image outputs (hyperstack, projection, merges) are not available " +
          "with one TIFF per channel; they were skipped.");

rootName   = cleanName(rootFolder);
outputRoot = rootFolder + "Results" + sep;
if (lengthOf(userOutDir) > 0) {
    outputRoot = userOutDir;
    if (!endsWith(outputRoot, sep)) outputRoot = outputRoot + sep;
}
stacksRoot = outputRoot + "Stacks_" + rootName + sep;
dataRoot   = outputRoot + "Data" + sep;
File.makeDirectory(outputRoot);
if (doCount) {
    File.makeDirectory(stacksRoot);
    File.makeDirectory(dataRoot);
}
// the settings of the run, next to its results: the first dialog can start
// a later run from them
okS = saveSettings(outputRoot + "Settings.txt");

exclList = excludedConditions;
print(pathList.length + " image(s) to process, output in " + outputRoot);
if (droppedOut > 0)
    print(droppedOut + " file(s) inside the output folder left out (written by an earlier run)");
for (a = 0; a < exclList.length; a++) print("excluded from calculations: " + exclList[a]);


// ======================================================================
//  IMAGE OUTPUTS
// ======================================================================
// Hyperstack, maximum projection and composite merges of the chosen
// channels, in the colours chosen in "Channels". The hyperstack
// and the projection show the channels one at a time (not merged).
// These are independent of the counting and can be produced on their own.
if (doConvert) {
    cTxt = coloursText();
    print("Channel colours in the images made: " + cTxt);
    if (doMerge) {
        mTxt = mergeText();
        print("Channels in the merges: " + mTxt);
    }
    if (useBatch) setBatchMode(true);
    for (i = 0; i < pathList.length; i++) {

        p = pathList[i];
        bn = File.getName(p);
        bn = stripExt(bn);
        // several series in one file: each saved under a name of its own
        if (serTot[i] > 1) bn = bn + "_s" + serList[i];

        if (!File.exists(p)) {
            print("   MISSING FILE, skipped: " + p);
            continue;
        }
        idsKeep = imageIDs();
        okOpen = openFile(p, i);
        if (okOpen == 0) {
            print("[" + (i + 1) + "/" + pathList.length + "] " + bn + "   ERROR: " + openErr +
                  " -> skipped");
            nc = closeNewImages(idsKeep);
            continue;
        }
        srcT = getTitle();
        okPx = applyPixelSize();
        print("[" + (i + 1) + "/" + pathList.length + "] " + bn + "   pixel size " +
              d2s(pxUsed, 4) + " um (" + pxSource + ")");
        getDimensions(cw, ch, cnc, cnz, cnt);
        if (cnt > 1) print("   time series: " + cnt + " frames, " + cnz + " Z plane(s)");
        // the colours chosen, channels one at a time (hyperstack, projection)
        okC = applyColours(srcT);

        if (doHyper) {
            selectWindow(srcT);
            sub = outSub("Hyperstacks", manList[i], condList[i]);
            saveAs("Tiff", sub + bn + ".tif");
            rename(srcT);
        }

        if (doProject && cnz == 1 && cnt > 1)
            print("   no Z planes to project: this file is a time series (" +
                  cnt + " frames). Nothing projected.");
        // ("all": every time point of a time series is projected)
        zpo = "projection=[Max Intensity]";
        if (cnt > 1) zpo = zpo + " all";
        if (doProject && cnz > 1) {
            selectWindow(srcT);
            run("Z Project...", zpo);
            projT = getTitle();
            sub = outSub("Projections", manList[i], condList[i]);
            saveAs("Tiff", sub + bn + "_MAX.tif");
            close();
        }

        // merge keeping every Z plane
        if (doMergeStack) {
            selectWindow(srcT);
            mrg = buildMerge(bn + "_s", cnz);
            if (mrg != "") {
                selectWindow(mrg);
                sub = outSub("Merges", manList[i], condList[i]);
                saveAs("Tiff", sub + bn + "_merge.tif");
                close();
            }
        }

        // merge of the maximum projection
        if (doMergeProj) {
            selectWindow(srcT);
            if (cnz > 1) {
                run("Z Project...", zpo);
                pT = getTitle();
            } else {
                run("Duplicate...", "title=[" + bn + "_p1] duplicate");
                pT = bn + "_p1";
            }
            selectWindow(pT);
            mrg = buildMerge(bn + "_p", 1);
            if (mrg != "") {
                selectWindow(mrg);
                sub = outSub("Projection merges", manList[i], condList[i]);
                saveAs("Tiff", sub + bn + "_MAX_merge.tif");
                close();
            }
            if (isOpen(pT)) { selectWindow(pT); close(); }
        }

        // only what this file opened is closed: images open before the run stay
        nc = closeNewImages(idsKeep);
        showProgress(i + 1, pathList.length);
    }
    if (useBatch) setBatchMode(false);
    print("");
    print("Image outputs written in " + outputRoot);
    if (doCount == false) {
        logTxt = getInfo("log");
        File.saveString(logTxt, outputRoot + "Log.txt");
        exit();
    }
}


// ======================================================================
//  PROCESSING
// ======================================================================
// Every setting in force, for the Methods and to reproduce the run. Kept in
// setLog as well: it goes into the Info of every QC stack.
setLog = "Settings\n";
if (countNuclei) {
    wsN = "off";
    if (nucleiWatershed) wsN = "on (tolerance " + nucleiWsTol + " px)";
    clN = "no";
    if (splitBigNuclei) clN = "above " + nucleiDoubletFactor + " x the median, at most " +
                              nucleiMaxSplit + " cells";
    frN = "no";
    if (nucleiFragFactor > 0) frN = "below " + nucleiFragFactor + " x the median, no cell";
    capN = "off";
    if (nucleiCapF > 0) capN = "" + nucleiCapF + " x the median brightness";
    sN = "   nuclei: channel " + chBLUE + ", " + nucleiThresholdMethod + " threshold x " +
         nucleiThresholdFactor + ", bright cap " + capN + ", area >= " + nucleiMinArea +
         " um2, circularity " + nucleiCirc + ", background " + nucleiBgRolling + " px, blur " +
         nucleiBlurSigma + " px, watershed " + wsN + ", clusters " + clN + ", fragments " + frN;
    if (tiffMode) sN = replace(sN, "channel " + chBLUE, "files '" + kwBlue + "'");
    setLog = setLog + sN + "\n";
    setLog = setLog + "   nuclei at the image border: " + edgeModes[nucleiEdgeMode] + "\n";
}
if (countGreen) {
    wsG = "off";
    if (greenWatershed) wsG = "on (tolerance " + greenWsTol + " px)";
    capG = "off";
    if (greenCapF > 0) capG = "" + greenCapF + " x the median brightness";
    sG = "   parasites: channel " + chGREEN + ", " + greenThresholdMethod + " threshold x " +
         greenThresholdFactor + ", bright cap " + capG + ", area " + greenMinArea + " to " +
         greenMaxArea + " um2, circularity " + greenCirc + ", background " + greenBgRolling +
         " px, blur " + greenBlurSigma + " px, watershed " + wsG;
    if (splitBigParasites)
        sG = sG + ", clusters above " + greenDoubletFactor + " x the median, at most " +
             greenMaxSplit + " parasites";
    if (tiffMode) sG = replace(sG, "channel " + chGREEN, "files '" + kwGreen + "'");
    setLog = setLog + sG + "\n";
}
if (countDouble) {
    capR = "off";
    if (redCapF > 0) capR = "" + redCapF + " x the median brightness";
    sR = "   double labelling: channel " + chRED + ", " + redThresholdMethod +
         " threshold x " + redThresholdFactor + ", bright cap " + capR;
    if (dblMode != 1) sR = sR + ", overlap >= " + minOverlapPercent + " %";
    sR = sR + ", background " + redBgRolling + " px, blur " + redBlurSigma + " px";
    if (tiffMode) sR = replace(sR, "channel " + chRED, "files '" + kwRed + "'");
    setLog = setLog + sR + "\n";
    dm = dblModes[dblMode];
    sM = "   scored as: " + dm;
    if (dblMode == 1) {
        wsO = "no";
        if (objWatershed) wsO = "yes";
        pm = posModes[posMode];
        sM = sM + " - objects of " + objMinArea + " to " + objMaxArea + " um2 (split: " + wsO +
             "), centre at most " + objTol + " um outside the parasite, at least " + objMinN +
             " per parasite, counted: " + pm;
    }
    if (dblMode == 2)
        sM = sM + " - objects of " + encMinArea + " to " + encMaxArea + " um2";
    if (dblMode == 1 || dblPolesUsed)
        sM = sM + "\n   places along the parasite: centre = central " + centrePct +
             " % of its length, extremity and pole = last " + endPct + " % at each end";
    setLog = setLog + sM + "\n";
    if (dblPolesUsed) {
        pr = toLowerCase(poleRefs[poleRef]);
        sP = "   poles: oriented by " + pr;
        if (poleNeedMk) {
            ps = toLowerCase(poleSides[poleSide]);
            sP = sP + "; pole marker channel " + chPOLE + " (marks " + ps + "), threshold x " +
                 pmThresholdFactor + ", objects " + pmMinArea + " to " + pmMaxArea + " um2";
            if (tiffMode) sP = replace(sP, "channel " + chPOLE, "files '" + kwPole + "'");
        }
        if (poleNeedNuc)
            sP = sP + "; parasite nucleus " + pnMinArea + " to " + pnMaxArea + " um2";
        sP = sP + "; no orientation within " + poleDeadPct + " % of the length from the centre";
        setLog = setLog + sP + "\n";
    }
}
if (autoDetectChannels && tiffMode == false)
    setLog = setLog + "   channels found in each file by the colour they have in the first file (the numbers above if that fails)\n";
pxMode = "from the image, " + pixelSize + " um if it has none";
if (pixelForce) pxMode = "" + pixelSize + " um on every image";
setLog = setLog + "   pixel size: " + pxMode + "\n";
setLog = setLog + "   reported measure: " + measureLabel + "\n";
if (doQC) {
    setLog = setLog + "   QC display: range adapted to each image, x " + qcGainBlue + " (nuclei), x " +
             qcGainGreen + " (parasites), x " + qcGainRed + " (marker); display only\n";
    qp = "";
    if (countNuclei && qcPanN) qp = qp + ", nuclei";
    if (countGreen && qcPanP)  qp = qp + ", parasites";
    if (countDouble && qcPanR) qp = qp + ", marker";
    if (countDouble && qcPanRG) qp = qp + ", marker + parasites";
    if (dblPolesUsed && qcPanPol) qp = qp + ", poles";
    if (countNuclei && countGreen && qcPanAll) qp = qp + ", all channels";
    if (qcRaw) qp = qp + "; each channel also raw";
    if (qp == "") qp = ", none (no QC stack)";
    setLog = setLog + "   QC stack panels: " + substring(qp, 2) + "\n";
}
print(setLog);
if (measureNote != "") print(measureNote);
if (stopTarget > 0)
    print("Stop early: each condition stops at " + stopTarget + " " + stopWhatTxt +
          " (the image reaching it is counted in full)");
// Images with few objects: the rule, in words, for the Log and the workbook
lowRule = "";
if (countNuclei && minCellsImg > 0) lowRule = "fewer than " + minCellsImg + " host cells";
if (countGreen && minParImg > 0) {
    if (lowRule != "") lowRule = lowRule + " or ";
    lowRule = lowRule + "fewer than " + minParImg + " parasites";
}
lowOn = false;
if (lowRule != "") lowOn = true;
if (lowOn) {
    lowTxt = "left out of the counts";
    if (lowMode == 1 && stopTarget > 0)
        lowTxt = "counted only if their condition does not reach the target without them";
    if (lowMode == 1 && stopTarget == 0)
        lowTxt = "left out of the counts (there is no stop target to reach)";
    print("Images with " + lowRule + ": " + lowTxt);
}
print("");

// object classes counted, for the QC panels
nClasses = 0;
if (countNuclei) nClasses = nClasses + 1;
if (countGreen)  nClasses = nClasses + 1;

// Stop early: running totals, one entry per experiment x condition
stopKeys = newArray(0);
stopLab  = newArray(0);
stopDone = newArray(0);
stopImgs = newArray(0);
stopSkip = newArray(0);
// stop-early entry of each image counted below (-1 without a target), to
// find the images set aside of a condition that falls short
summaryKey = newArray(0);

if (useBatch) setBatchMode(true);

for (i = 0; i < pathList.length; i++) {
    // "Batch mode" unticked: each file is opened outside batch mode (some
    // files fail to open in it), and processed in it - the QC panels and
    // the masks need no window, which would only slow everything down
    if (useBatch == false && is("Batch Mode")) setBatchMode(false);

    path  = pathList[i];
    manip = manList[i];
    cond  = condList[i];
    base  = File.getName(path);
    base  = stripExt(base);
    // several series in one file: keep them apart by name
    if (serTot[i] > 1) base = base + "_s" + serList[i];
    // one TIFF per channel: the file name also holds the channel and the
    // free text, so the acquisition gets a short name of its own
    if (tiffMode) base = tBase[i];

    // ---- Stop early: skip the image if its condition has its target ----
    sKey = -1;
    if (stopTarget > 0) {
        key2 = manip + "|" + cond;
        for (sq = 0; sq < stopKeys.length; sq++) {
            if (stopKeys[sq] == key2) sKey = sq;
        }
        if (sKey < 0) {
            stopKeys = Array.concat(stopKeys, key2);
            if (manip == "-") lab2 = cond;
            else              lab2 = manip + " / " + cond;
            stopLab  = Array.concat(stopLab, lab2);
            stopDone = Array.concat(stopDone, 0);
            stopImgs = Array.concat(stopImgs, 0);
            stopSkip = Array.concat(stopSkip, 0);
            sKey = stopKeys.length - 1;
        }
        if (stopDone[sKey] >= stopTarget) {
            stopSkip[sKey] = stopSkip[sKey] + 1;
            print("[" + (i+1) + "/" + pathList.length + "] " + manip + " / " + cond +
                  " / " + base + "   not counted: target already reached");
            continue;
        }
    }

    if (manip == "-") {
        stackFolder = stacksRoot + cond + sep;
        dataFolder  = dataRoot + cond + sep;
    } else {
        File.makeDirectory(stacksRoot + manip + sep);
        File.makeDirectory(dataRoot + manip + sep);
        stackFolder = stacksRoot + manip + sep + cond + sep;
        dataFolder  = dataRoot + manip + sep + cond + sep;
    }
    File.makeDirectory(stackFolder);
    File.makeDirectory(dataFolder);

    print("[" + (i+1) + "/" + pathList.length + "] " + manip + " / " + cond + " / " + base);

    // images open before this one: never closed by it
    idsKeep = imageIDs();
    roiManager("Reset");
    run("Clear Results");

    blueName  = base + "_BLUE";
    greenName = base + "_GREEN";
    redName   = base + "_RED";
    dicName   = base + "_DIC";
    poleName  = base + "_POLE";
    nCh = 0;

    if (tiffMode) {
        // ---- One TIFF per channel: each file opened under its final name,
        // so everything downstream is the same as for a multi-channel file.
        // Only the channels needed are opened.
        if (countNuclei || poleNeedNuc) okB = openTiffChannel(tBlue[i],  blueName);
        if (countGreen)  okG = openTiffChannel(tGreen[i], greenName);
        if (countDouble) okR = openTiffChannel(tRed[i],   redName);
        if (poleNeedMk)  okPm = openTiffChannel(tPole[i], poleName);
    } else {
        // ---- Import, max Z projection, channel split ----
        if (!File.exists(path)) {
            print("   MISSING FILE -> image skipped");
            continue;
        }
        okOpen = openFile(path, i);
        if (okOpen == 0) {
            print("   ERROR: " + openErr + " -> image skipped");
            nc = closeNewImages(idsKeep);
            continue;
        }
        original = getTitle();
        getDimensions(opW, opH, opC, opZ, opT);
        print("   opened: " + opC + " channel(s), " + opZ + " Z plane(s), " + opT +
              " time point(s)");
        if (opT > 1) print("   NOTE: a time series: only its first time point is counted");
        // first time point, maximum projection of the Z planes
        projected = countView(original);

        selectWindow(projected);
        // before Split Channels: the calibration follows into each channel,
        // and from there into the masks the counts are measured on
        okPx = applyPixelSize();
        getDimensions(dimW, dimH, nCh, nSl, nFr);

        // ---- Which channel is which? ----
        // With the per-file option, each role is the channel having the colour
        // that role has in the first file (colours read from the LUT of each
        // channel: the CZI metadata, or the wavelength names of a .nd). If this
        // fails, the channel numbers of the dialog are used.
        uDIC = chDIC; uRED = chRED; uGREEN = chGREEN; uBLUE = chBLUE;
        chFrom = "the channels given in the Channels dialog";
        if (autoDetectChannels) {
            detectChannels(projected);
            okDet = 1;
            if (countNuclei && dcBLUE == 0)  okDet = 0;
            if (countGreen  && dcGREEN == 0) okDet = 0;
            if (countDouble && dcRED == 0)   okDet = 0;
            if (okDet == 1) {
                uDIC = dcDIC; uRED = dcRED; uGREEN = dcGREEN; uBLUE = dcBLUE;
                chFrom = "found by their colour in this file";
                print("   channels found by their colour: nuclear stain " + uBLUE + ", parasite label " +
                      uGREEN + ", second marker " + uRED + ", DIC " + uDIC);
            } else {
                print("   channels could not be told from the colours" +
                      " -> using the channel numbers given in the dialog");
            }
        }

        if (nCh > 1) {
            run("Split Channels");
            // Name from C1-/C2-/...: do NOT use getList("image.titles"), which
            // also lists windows left open by previously processed files.
            // The pole marker first: it is always the channel number given
            // (never read from the colours, which it would confuse).
            if (poleNeedMk && chPOLE > 0 && isOpen("C" + chPOLE + "-" + projected)) { selectWindow("C" + chPOLE + "-" + projected); rename(poleName); }
            if (uDIC   > 0 && isOpen("C" + uDIC   + "-" + projected)) { selectWindow("C" + uDIC   + "-" + projected); rename(dicName);   }
            if (uRED   > 0 && isOpen("C" + uRED   + "-" + projected)) { selectWindow("C" + uRED   + "-" + projected); rename(redName);   }
            if (uGREEN > 0 && isOpen("C" + uGREEN + "-" + projected)) { selectWindow("C" + uGREEN + "-" + projected); rename(greenName); }
            if (uBLUE  > 0 && isOpen("C" + uBLUE  + "-" + projected)) { selectWindow("C" + uBLUE  + "-" + projected); rename(blueName);  }
        } else {
            // a single channel: channel 1, for whichever role it was given
            selectWindow(projected);
            if (uBLUE == 1)       rename(blueName);
            else if (uGREEN == 1) rename(greenName);
            else if (uRED == 1)   rename(redName);
        }
    }

    // the pixel size this image is measured with, image by image
    print("   pixel size " + d2s(pxUsed, 4) + " um (" + pxSource + ")");
    if (useBatch == false) setBatchMode(true);

    // ---- the channels this count needs ----
    miss = "";
    if (countNuclei && !isOpen(blueName))  miss = miss + "  nuclear stain";
    if (countGreen  && !isOpen(greenName)) miss = miss + "  parasite label";
    if (countDouble && !isOpen(redName))   miss = miss + "  second marker";
    if (miss != "") {
        if (tiffMode)
            print("   ERROR: no file for" + miss + " -> image skipped");
        else
            print("   ERROR: channel missing:" + miss + "  (the file has " + nCh +
                  " channel(s): check the Channels dialog)" +
                  " -> image skipped");
        nc = closeNewImages(idsKeep);
        continue;
    }
    // channels not counted are not needed any more (the nuclear stain still
    // is when it orients the parasites)
    if (isOpen(dicName)) { selectWindow(dicName); close(); }
    if (countNuclei == false && poleNeedNuc == false && isOpen(blueName)) { selectWindow(blueName); close(); }
    if (countGreen  == false && isOpen(greenName)) { selectWindow(greenName); close(); }
    if (countDouble == false && isOpen(redName))   { selectWindow(redName);   close(); }

    // ---- Annotation sizes, scaled to the image ----
    if (countNuclei) selectWindow(blueName);
    else             selectWindow(greenName);
    imgW = getWidth();
    // as in the Calibrate preview
    labelFS = maxOf(10, round(imgW / 90 * labelScale));
    recapFS = maxOf(10, round(imgW / 90 * recapScale));
    lineW   = maxOf(2, round(imgW / 500 * strokeScale));

    // what this image gives, 0 for a class not counted
    nucleiCount = 0;
    nucleiROIs  = 0;
    doubletIdx  = newArray(0);
    fragIdx     = newArray(0);
    nEdgeOut    = 0;
    greenCount  = 0;
    parObj      = 0;
    parClus     = newArray(0);
    colocCount  = 0;
    colocIndices = newArray(0);
    poleN   = newArray(5);           // pole of the marker: [1] basal ... [4] not at a pole
    poleOri = 0;
    objN    = 0;
    objOn   = 0;
    objUsed = 0;
    thrInfo = "";                    // thresholds, for the Info of the QC stack
    panels  = newArray(0);           // the QC panels made, and their labels
    plabs   = newArray(0);
    nucleiZip = dataFolder + base + "_nuclei_ROIs.zip";
    edgeZip = dataFolder + base + "_nuclei_border_excluded_ROIs.zip";

    // ==================================================================
    //  NUCLEI
    // ==================================================================
    if (countNuclei) {
        sn = satCheck(blueName);
        if (sn != "") print("   nuclear stain: " + sn);
        // same function as Calibrate, so the panel shows what is counted here
        nucleusMaskTitle = "Nucleus_mask_" + base;
        segmentNuclei(blueName, nucleusMaskTitle);
        if (thrNote != "") print("   nuclei threshold: " + thrNote);
        thrInfo = thrInfo + "nuclei threshold " + nfmt(nucThr);
        if (thrNote != "") thrInfo = thrInfo + " (" + thrNote + ")";
        thrInfo = thrInfo + "\n";

        // Nuclei cut by the image border, left out here when the dialog asks
        // for it - before anything else sees them, so the cluster median below
        // is computed on the nuclei actually counted. They are saved to a file
        // of their own and drawn in grey in the QC.
        if (nucleiEdgeMode > 0) {
            nEdgeOut = removeBorderNuclei(nucleusMaskTitle, nucleiEdgeMode, edgeZip);
            if (nEdgeOut > 0) print("   nuclei at the border, not counted: " + nEdgeOut);
        }

        // Clusters: an object clearly larger than the field median counts as
        // several overlapping cells. Fragments, clearly smaller, as none.
        nucleiROIs  = roiManager("count");
        nucleiCount = countCells(blueName);
        doubletIdx  = cellClusterIdx;
        fragIdx     = cellFragIdx;
        if (cellMedian > 0) {
            fragTxt = "";
            if (nucleiFragFactor > 0) fragTxt = ", " + fragIdx.length + " fragments";
            print("   nuclei " + nucleiROIs + " objects, median " + d2s(cellMedian, 1) +
                  " um2, " + doubletIdx.length + " clusters" + fragTxt + " -> " +
                  nucleiCount + " cells");
        } else
            print("   nuclei = " + nucleiCount);

        if (nucleiROIs > 0) {
            roiManager("Deselect");
            roiManager("Save", nucleiZip);
        }
        // a file left by an earlier run must not pass for this one
        if (nucleiROIs == 0 && File.exists(nucleiZip)) dl = File.delete(nucleiZip);
        if (nEdgeOut == 0 && File.exists(edgeZip)) dl = File.delete(edgeZip);

        // what the filters rejected near their limits, for the QC panel
        // (the mask is closed just after)
        rejNS = "";
        rejNC = "";
        if (doQC && qcPanN) {
            rejNS = "RejS_N_" + base;
            rejNC = "RejC_N_" + base;
            zE = "";
            if (nEdgeOut > 0) zE = edgeZip;
            okR = rejectMasks(nucleusMaskTitle, nucleiROIs, zE, nucleiMinArea, nucleiMaxArea,
                              rejNS, rejNC);
        }
        selectWindow(nucleusMaskTitle);
        close();

        // ---- Panels BLUE: the nuclear stain, raw, and the nuclei as in
        // Calibrate ----
        if (doQC && qcRaw) {
            qcCopy(blueName, "BLUE_rdup_" + base, qcGainBlue, "Blue");
            rawB = base + "_BLUE_raw";
            flattenClean(rawB);
            okT = drawTop(base + "  -  nuclear stain", 1, "ffffff", recapFS);
            selectWindow("BLUE_rdup_" + base);
            close();
            panels = Array.concat(panels, rawB);
            plabs  = Array.concat(plabs, "BLUE - raw");
        }
        if (doQC && qcPanN) {
            nucleiIdx = makeSeq(nucleiROIs);
            blueDup = "BLUE_dup_" + base;
            qcCopy(blueName, blueDup, qcGainBlue, "Blue");
            blueQC = base + "_BLUE_QC";
            flattenClean(blueQC);
            // rejected near the limits, underneath: orange by size, yellow by shape
            okE = drawMaskH(rejNS, blueQC, colRejS, lineW, nucleiROIs);
            okE = drawMaskH(rejNC, blueQC, colRejC, lineW, nucleiROIs);
            selectWindow(rejNS);
            close();
            selectWindow(rejNC);
            close();
            selectWindow(blueQC);
            // nuclei left out at the border: grey outline, no number, drawn
            // first so that the counted ones stay on top. Appended after the
            // counted nuclei, whose indices are unchanged.
            if (nEdgeOut > 0 && File.exists(edgeZip)) {
                edgeOff = roiManager("count");
                roiManager("Open", edgeZip);
                nEdgeB = roiManager("count") - edgeOff;
                if (nEdgeB > 0) {
                    edgeIdx = newArray(nEdgeB);
                    for (n = 0; n < nEdgeB; n++) edgeIdx[n] = edgeOff + n;
                    drawROIsH(edgeIdx, colEdge, lineW);
                }
            }
            drawROIsH(nucleiIdx, colNuc, lineW);
            drawROIsH(doubletIdx, colNClu, lineW);     // clusters: check by eye
            drawROIsH(fragIdx, colNFrag, lineW);       // fragments: counted as no cell
            labelROIsH(nucleiIdx, colNuc, labelFS, 0);
            okT = drawTop(base, 1, "ffffff", recapFS);
            lg = legNuclei(1);
            okT = drawTop(lg, 2, "ffffff", recapFS);
            lg = legNuclei(2);
            okT = drawTop(lg, 3, "ffffff", recapFS);
            recapB = "Cells = " + nucleiCount + "   (" + nucleiROIs + " nuclei, " +
                     doubletIdx.length + " clusters";
            if (nucleiFragFactor > 0) recapB = recapB + ", " + fragIdx.length + " fragments";
            if (nucleiEdgeMode > 0) recapB = recapB + ", " + nEdgeOut + " cut by the border left out";
            recapB = recapB + ")";
            drawRecapH(recapB, colNuc, recapFS);
            if (isOpen(blueDup)) { selectWindow(blueDup); close(); }
            panels = Array.concat(panels, blueQC);
            plabs  = Array.concat(plabs, "BLUE - nuclei");
        }
    }

    // ==================================================================
    //  GREEN PARASITES
    // ==================================================================
    if (countGreen) {
        sn = satCheck(greenName);
        if (sn != "") print("   parasite label: " + sn);
        parasiteMaskTitle = "Parasite_mask_" + base;
        parObj = segmentParasites(greenName, parasiteMaskTitle);
        thrG = "   green threshold " + parThr + " (auto " + nfmt(parThrAuto) + " x " +
               greenThresholdFactor + ")";
        if (thrNote != "") thrG = thrG + "; " + thrNote;
        print(thrG);
        thrInfo = thrInfo + "parasite threshold " + parThr + " (automatic " + nfmt(parThrAuto) +
                  " x " + greenThresholdFactor;
        if (thrNote != "") thrInfo = thrInfo + "; " + thrNote;
        thrInfo = thrInfo + ")\n";
        // Clusters (option): an object clearly larger than the field median
        // counts as several parasites. greenCount is the number of parasites,
        // parObj the number of objects - the ROI Manager items 0 to parObj-1.
        greenCount = countParasites(greenName);
        parClus    = parClusterIdx;
        if (parMedian > 0)
            print("   green parasites " + parObj + " objects, median " + d2s(parMedian, 1) +
                  " um2, " + parClus.length + " clusters -> " + greenCount + " parasites");
        else
            print("   green parasites = " + greenCount);

        rejPS = "";
        rejPC = "";
        if (doQC && qcPanP) {
            rejPS = "RejS_P_" + base;
            rejPC = "RejC_P_" + base;
            okR = rejectMasks(parasiteMaskTitle, parObj, "", greenMinArea, greenMaxArea, rejPS, rejPC);
        }
        selectWindow(parasiteMaskTitle);
        close();

        // ---- Panels GREEN: the parasite label, raw, and the parasites as
        // in Calibrate ----
        if (doQC && qcRaw) {
            qcCopy(greenName, "GREEN_rdup_" + base, qcGainGreen, "Green");
            rawG = base + "_GREEN_raw";
            flattenClean(rawG);
            okT = drawTop(base + "  -  parasite label", 1, "ffffff", recapFS);
            selectWindow("GREEN_rdup_" + base);
            close();
            panels = Array.concat(panels, rawG);
            plabs  = Array.concat(plabs, "GREEN - raw");
        }
        if (doQC && qcPanP) {
            greenIdx = makeSeq(parObj);
            greenDup = "GREEN_dup_" + base;
            qcCopy(greenName, greenDup, qcGainGreen, "Green");
            greenQC = base + "_GREEN_QC";
            flattenClean(greenQC);
            okE = drawMaskH(rejPS, greenQC, colRejS, lineW, parObj);
            okE = drawMaskH(rejPC, greenQC, colRejC, lineW, parObj);
            selectWindow(rejPS);
            close();
            selectWindow(rejPC);
            close();
            selectWindow(greenQC);
            drawROIsH(greenIdx, colPar, lineW);
            drawROIsH(parClus, colPClu, lineW);       // clusters: check by eye
            labelROIsH(greenIdx, colPar, labelFS, 0);
            okT = drawTop(base, 1, "ffffff", recapFS);
            lg = legParasites(1);
            okT = drawTop(lg, 2, "ffffff", recapFS);
            lg = legParasites(2);
            okT = drawTop(lg, 3, "ffffff", recapFS);
            recapG = "Parasites = " + greenCount;
            if (splitBigParasites)
                recapG = recapG + "   (" + parObj + " objects, " + parClus.length + " clusters)";
            drawRecapH(recapG, colPar, recapFS);
            if (isOpen(greenDup)) { selectWindow(greenDup); close(); }
            panels = Array.concat(panels, greenQC);
            plabs  = Array.concat(plabs, "GREEN - parasites");
        }
    }

    // ==================================================================
    //  RED / GREEN COLOCALISATION
    //  Green ROIs stay loaded: indices 0 to parObj-1. A cluster that is
    //  double-labelled counts for the parasites it holds.
    // ==================================================================
    if (countDouble) {
        sn = satCheck(redName);
        if (sn != "") print("   second marker: " + sn);
        redMaskTitle = "Red_mask_" + base;
        markerPre(redName, redMaskTitle);
        markerMask(redMaskTitle);
        thrR = "   red threshold " + dblThr + " (auto " + nfmt(dblThrAuto) + " x " +
               redThresholdFactor + ")";
        if (thrNote != "") thrR = thrR + "; " + thrNote;
        print(thrR);
        thrInfo = thrInfo + "marker threshold " + dblThr + " (automatic " + nfmt(dblThrAuto) +
                  " x " + redThresholdFactor;
        if (thrNote != "") thrInfo = thrInfo + "; " + thrNote;
        thrInfo = thrInfo + ")\n";
        // the references of the poles, when the option is on and they are there
        refB = "";
        if (poleNeedNuc && isOpen(blueName)) refB = blueName;
        refP = "";
        if (poleNeedMk && isOpen(poleName)) refP = poleName;
        if (poleNeedNuc && refB == "") print("   NOTE: no nuclear stain: the parasites cannot be oriented by their nucleus");
        if (poleNeedMk && refP == "") print("   NOTE: no pole marker channel: the parasites cannot be oriented by it");
        // double labelling as asked (overlap, objects in the parasite, or
        // the parasite in an object), then the pole of the marker
        colocCount   = scoreDoubleAll(redMaskTitle, parObj, redMaskTitle, refB, refP);
        colocIndices = dblIdx;
        if (dblMode == 0) {
            // If the three counters are close, the overlap threshold hardly
            // matters; if they diverge, many objects are borderline.
            print("   double positives = " + colocCount + " / " + greenCount +
                  "   (>=20% : " + dblN20 + ", >=35% : " + dblN35 + ", >=55% : " + dblN55 + ")");
        } else if (dblMode == 1) {
            print("   marker objects " + objN + ", " + objOn + " on a parasite, " + objUsed +
                  " counted (" + posModes[posMode] + ") -> double positives = " + colocCount +
                  " / " + greenCount);
        } else {
            print("   marker objects " + objN + " of " + encMinArea + " to " + encMaxArea +
                  " um2 -> parasites inside one = " + colocCount + " / " + greenCount);
        }
        poleTxt = "";
        if (dblPolesUsed && dblMode != 2) {
            oriL = "   poles: " + poleOri + " of " + parObj + " parasite object(s) oriented";
            if (poleNote != "") oriL = oriL + " (not oriented: " + poleNote + ")";
            print(oriL);
            pSum = poleN[1] + poleN[2] + poleN[3];
            poleTxt = "marker at the basal pole " + poleN[1] + ", apical pole " + poleN[2] +
                      ", pole indeterminable " + poleN[3] + " (sum " + pSum + " of " + colocCount +
                      " double-labelled), not at a pole " + poleN[4];
            print("   " + poleTxt);
            thrInfo = thrInfo + "Poles: " + poleTxt + "; " + poleOri + " of " + parObj +
                      " parasite object(s) oriented\n";
        }
        // the marker objects, by kind, for the QC: 2 counted, 1 on a
        // parasite elsewhere, 0 on no parasite
        oKind = newArray(oX.length);
        for (k = 0; k < oX.length; k++) {
            if (oOwn[k] >= 0) oKind[k] = 1;
            if (oUse[k] == 1) oKind[k] = 2;
        }
        // for the QC panels: the marker-positive area without its specks,
        // and the objects around the parasites (enclosing mode)
        qcD = false;
        if (doQC && (qcPanR || qcPanRG)) qcD = true;
        mkA = "";
        if (qcD) {
            mkA = "MkArea_" + base;
            mSp = mkSpeck();
            okA = apMask(redMaskTitle, "size=" + mSp + "-Infinity", mkA);
        }
        encQC = "";
        if (qcD && dblMode == 2) encQC = enclosingMask(redMaskTitle);
        if (isOpen(redMaskTitle)) { selectWindow(redMaskTitle); close(); }

        // this image's line(s) of Parasites.csv
        if (dblMode == 1 || (dblPolesUsed && dblMode != 2)) {
            pl = parasiteLines(manip, cond, base, parObj);
            perPar = Array.concat(perPar, pl);
        }

        // the parasites not double-labelled
        othIdx = newArray(0);
        isDbl = newArray(parObj);
        for (z = 0; z < colocIndices.length; z++) isDbl[colocIndices[z]] = 1;
        for (z = 0; z < parObj; z++) {
            if (isDbl[z] == 0) othIdx = Array.concat(othIdx, z);
        }
        recapR = "Double-labelled = " + colocCount + " / " + greenCount;
        if (dblMode == 0)
            recapR = recapR + "   (20 / 35 / 55 % overlap: " + dblN20 + " / " + dblN35 + " / " +
                     dblN55 + ")";
        if (dblMode == 1)
            recapR = recapR + "   (objects: " + objUsed + " counted, " + objOn + " on a parasite, " +
                     objN + " found)";
        if (dblMode == 2)
            recapR = recapR + "   (" + objN + " marker objects of the size given)";

        if (doQC && qcRaw) {
            qcCopy(redName, "RED_rdup_" + base, qcGainRed, "Red");
            rawR = base + "_RED_raw";
            flattenClean(rawR);
            okT = drawTop(base + "  -  marker", 1, "ffffff", recapFS);
            selectWindow("RED_rdup_" + base);
            close();
            panels = Array.concat(panels, rawR);
            plabs  = Array.concat(plabs, "RED - raw");
        }
        // ---- Panels RED (the marker) and MERGE RED+GREEN, drawn the same
        // way, as in Calibrate ----
        for (pv = 0; pv < 2; pv++) {
            if (pv == 0 && (doQC == false || qcPanR == false)) continue;
            if (pv == 1 && (doQC == false || qcPanRG == false)) continue;
            if (pv == 0) {
                redDup = "RED_dup_" + base;
                qcCopy(redName, redDup, qcGainRed, "Red");
                dPan = base + "_RED_QC";
                flattenClean(dPan);
                if (isOpen(redDup)) { selectWindow(redDup); close(); }
            } else {
                redDisp   = base + "_RED_disp";
                greenDisp = base + "_GREEN_disp";
                qcCopy(redName,   redDisp,   qcGainRed, "");
                qcCopy(greenName, greenDisp, qcGainGreen, "");
                mergeRG = base + "_Merge_RG";
                run("Merge Channels...",
                    "c1=[" + redDisp + "] c2=[" + greenDisp + "] create keep");
                rename(mergeRG);
                run("RGB Color");
                dPan = base + "_Merge_RG_QC";
                flattenClean(dPan);
                if (isOpen(mergeRG)) { selectWindow(mergeRG); close(); }
            }
            okE = drawMaskH(mkA, dPan, colMk, lineW, parObj);
            if (dblMode == 2) okE = drawMaskH(encQC, dPan, "ffff00", lineW, parObj);
            selectWindow(dPan);
            drawROIsH(othIdx, colOth, lineW);
            drawROIsH(colocIndices, colDbl, lineW);
            if (dblMode == 1) {
                selectWindow(dPan);
                drawObjects(oKind, 0, 130, 130, 130, maxOf(1, round(lineW / 2)));
                drawObjects(oKind, 1, 255, 255, 0, maxOf(1, round(lineW / 2)));
                drawObjects(oKind, 2, 255, 255, 255, maxOf(1, round(lineW / 2)));
            }
            labelROIsH(colocIndices, colDbl, labelFS, 0);
            okT = drawTop(base, 1, "ffffff", recapFS);
            lg = legDouble(1);
            okT = drawTop(lg, 2, "ffffff", recapFS);
            lg = legDouble(2);
            okT = drawTop(lg, 3, "ffffff", recapFS);
            drawRecapH(recapR, colDbl, recapFS);
            panels = Array.concat(panels, dPan);
            if (pv == 0) plabs = Array.concat(plabs, "RED - double-labelled");
            else         plabs = Array.concat(plabs, "MERGE RED+GREEN");
        }
        if (mkA != "") {
            if (isOpen(mkA)) { selectWindow(mkA); close(); }
        }
        if (encQC != "") {
            if (isOpen(encQC)) { selectWindow(encQC); close(); }
        }

        // ---- Panel POLES: each double-labelled parasite in the colour
        // of the pole of its marker; a yellow dot at the basal end of
        // every parasite that could be oriented ----
        if (doQC && qcPanPol && dblPolesUsed && dblMode != 2) {
            redDisp3   = base + "_RED_disp3";
            greenDisp3 = base + "_GREEN_disp3";
            qcCopy(redName,   redDisp3,   qcGainRed, "");
            qcCopy(greenName, greenDisp3, qcGainGreen, "");
            mcP = "c1=[" + redDisp3 + "] c2=[" + greenDisp3 + "] ";
            if (refB != "") {
                blueDisp3 = base + "_BLUE_disp3";
                qcCopy(blueName, blueDisp3, qcGainBlue, "");
                mcP = mcP + "c3=[" + blueDisp3 + "] ";
            }
            mergeP = base + "_Merge_Poles";
            run("Merge Channels...", mcP + "create keep");
            rename(mergeP);
            run("RGB Color");
            polesQC = base + "_Poles_QC";
            flattenClean(polesQC);

            // the others thin and grey, then each class in its colour
            othP = newArray(0);
            basP = newArray(0);
            apiP = newArray(0);
            indP = newArray(0);
            midP = newArray(0);
            for (q = 0; q < parObj; q++) {
                if (pCls[q] == 0) othP = Array.concat(othP, q);
                if (pCls[q] == 1) basP = Array.concat(basP, q);
                if (pCls[q] == 2) apiP = Array.concat(apiP, q);
                if (pCls[q] == 3) indP = Array.concat(indP, q);
                if (pCls[q] == 4) midP = Array.concat(midP, q);
            }
            drawROIs(othP, 130, 130, 130, maxOf(1, round(lineW / 2)));
            drawROIs(midP, 255, 150, 0, lineW);
            drawROIs(indP, 255, 255, 255, lineW);
            drawROIs(apiP, 0, 255, 255, lineW);
            drawROIs(basP, 255, 0, 255, lineW);
            if (dblMode == 1) {
                selectWindow(polesQC);
                // counted objects of double-labelled parasites, by pole
                drawObjects(oPole, 4, 255, 150, 0, maxOf(1, round(lineW / 2)));
                drawObjects(oPole, 3, 255, 255, 255, maxOf(1, round(lineW / 2)));
                drawObjects(oPole, 2, 0, 255, 255, maxOf(1, round(lineW / 2)));
                drawObjects(oPole, 1, 255, 0, 255, maxOf(1, round(lineW / 2)));
            }
            selectWindow(polesQC);
            drawBasalEnds(parObj, maxOf(3, lineW * 2), 255, 255, 0);
            // numbered as on the RED panel
            dblNum = newArray(parObj);
            for (z = 0; z < colocIndices.length; z++) dblNum[colocIndices[z]] = z + 1;
            labelROIsNum(basP, dblNum, 255, 0, 255, labelFS, 0);
            labelROIsNum(apiP, dblNum, 0, 255, 255, labelFS, 0);
            labelROIsNum(indP, dblNum, 255, 255, 255, labelFS, 0);
            labelROIsNum(midP, dblNum, 255, 150, 0, labelFS, 0);
            okT = drawTop(base, 1, "ffffff", recapFS);
            lg = legPoles(1);
            okT = drawTop(lg, 2, "ffffff", recapFS);
            lg = legPoles(2);
            okT = drawTop(lg, 3, "ffffff", recapFS);
            pSum = poleN[1] + poleN[2] + poleN[3];
            recapP = "Poles: basal " + poleN[1] + ", apical " + poleN[2] + ", indeterminable " +
                     poleN[3] + " (sum " + pSum + " of " + colocCount + "), not at a pole " +
                     poleN[4] + "   -   " + poleOri + " of " + parObj + " parasite object(s) oriented";
            drawRecapH(recapP, "ffffff", recapFS);
            if (isOpen(mergeP)) { selectWindow(mergeP); close(); }
            panels = Array.concat(panels, polesQC);
            plabs  = Array.concat(plabs, "POLES - marker at the basal (magenta) / apical (cyan) pole");
        }
    }

    // ---- Too few objects: left out, or set aside for the stop target ----
    lowWhy = "";
    if (countNuclei && minCellsImg > 0 && nucleiCount < minCellsImg)
        lowWhy = "fewer than " + minCellsImg + " host cells";
    if (countGreen && minParImg > 0 && greenCount < minParImg) {
        if (lowWhy != "") lowWhy = lowWhy + " and ";
        lowWhy = lowWhy + "fewer than " + minParImg + " parasites";
    }
    lowNote = "";
    if (lowWhy != "") {
        lowNote = "left out of the counts";
        if (lowMode == 1 && stopTarget > 0)
            lowNote = "set aside, counted only if its condition needs it to reach the target";
        print("   " + lowWhy + ": " + lowNote);
    }

    if (doQC) {
        // ---- Panel MERGE FULL, all channels, when nuclei and parasites are
        // counted ----
        if (qcPanAll && nClasses >= 2) {
            // the nuclei follow the parasite objects in the ROI Manager
            nucleiOffset = parObj;
            if (nucleiROIs > 0 && File.exists(nucleiZip)) roiManager("Open", nucleiZip);
            nucleiInMerge = roiManager("count") - nucleiOffset;
            if (nucleiInMerge < 0) nucleiInMerge = 0;
            nucleiMergeIdx = newArray(nucleiInMerge);
            for (n = 0; n < nucleiInMerge; n++) nucleiMergeIdx[n] = nucleiOffset + n;
            greenIdx = makeSeq(parObj);

            mcmd = "";
            if (countDouble) {
                redDisp2 = base + "_RED_disp2";
                qcCopy(redName, redDisp2, qcGainRed, "");
                mcmd = mcmd + "c1=[" + redDisp2 + "] ";
            }
            greenDisp2 = base + "_GREEN_disp2";
            qcCopy(greenName, greenDisp2, qcGainGreen, "");
            mcmd = mcmd + "c2=[" + greenDisp2 + "] ";
            blueDisp2 = base + "_BLUE_disp2";
            qcCopy(blueName, blueDisp2, qcGainBlue, "");
            mcmd = mcmd + "c3=[" + blueDisp2 + "] ";
            fullMerge = base + "_Merge_Full";
            run("Merge Channels...", mcmd + "create keep");
            rename(fullMerge);
            run("RGB Color");
            fullMergeQC = base + "_Merge_Full_QC";
            flattenClean(fullMergeQC);

            // nuclei left out at the border, grey and underneath, as in the blue
            // panel. Appended last, so the indices above are unchanged.
            if (nEdgeOut > 0 && File.exists(edgeZip)) {
                edgeOff = roiManager("count");
                roiManager("Open", edgeZip);
                nEdgeM = roiManager("count") - edgeOff;
                if (nEdgeM > 0) {
                    edgeMIdx = newArray(nEdgeM);
                    for (n = 0; n < nEdgeM; n++) edgeMIdx[n] = edgeOff + n;
                    drawROIsH(edgeMIdx, colEdge, lineW);
                }
            }

            drawROIsH(nucleiMergeIdx, colNuc, lineW);
            drawROIsH(greenIdx, colPar, lineW);
            drawROIsH(colocIndices, colDbl, lineW);    // double-labelled drawn over the others

            labelROIsH(nucleiMergeIdx, colNuc, labelFS, 0);
            labelROIsH(greenIdx, colPar, labelFS, 0);
            labelROIsH(colocIndices, colDbl, labelFS, labelFS + 2);

            okT = drawTop(base, 1, "ffffff", recapFS);
            lg = "cyan: nuclei   magenta: parasites";
            if (countDouble) lg = lg + "   white: double-labelled";
            if (nucleiEdgeMode > 0) lg = lg + "   grey: nuclei cut by the border, left out";
            okT = drawTop(lg, 2, "ffffff", recapFS);
            recapM = "Cells = " + nucleiCount + "   Parasites = " + greenCount;
            if (countDouble) recapM = recapM + "   Double-labelled = " + colocCount;
            drawRecapH(recapM, "ffffff", recapFS);
            panels = Array.concat(panels, fullMergeQC);
            plabs  = Array.concat(plabs, "MERGE FULL");
        }

        // the note goes on the last panel
        if (lowWhy != "" && panels.length > 0) {
            selectWindow(panels[panels.length - 1]);
            noteT = "UNDER THE MINIMUM (" + lowWhy + "): left out of the counts";
            if (lowMode == 1 && stopTarget > 0)
                noteT = "UNDER THE MINIMUM (" + lowWhy + "): set aside for the target, see the Log";
            drawNote(noteT, 255, 170, 0, recapFS);
        }

        // ---- Stack assembly and saving (no panel picked: no stack) ----
        if (panels.length > 0) {
            stackTitle = base + "_QC_Stack";
            if (panels.length == 1) {
                selectWindow(panels[0]);
                rename(stackTitle);
            } else {
                cc = "title=[" + stackTitle + "]";
                for (q = 0; q < panels.length; q++)
                    cc = cc + " image" + (q + 1) + "=[" + panels[q] + "]";
                run("Concatenate...", cc);
            }
            selectWindow(stackTitle);
            for (q = 0; q < plabs.length; q++) {
                setSlice(q + 1);
                setMetadata("Label", plabs[q]);
            }
            setSlice(1);
            // The calibration of the image analysed. Concatenate drops it as
            // soon as two panels differ in any way, and the Z spacing of the
            // source means nothing for panels.
            setVoxelSize(pxUsed, pxUsedH, pxUsed, "micron");
            qi = "Count and Colocalise v" + progVersion + " - QC stack\n";
            qi = qi + "Source: " + path + "\n";
            if (serTot[i] > 1) qi = qi + "Series / position: " + serList[i] + " of " + serTot[i] + "\n";
            qi = qi + "Experiment: " + manip + "   Condition: " + cond + "   Image: " + base + "\n";
            qi = qi + "Pixel size: " + d2s(pxUsed, 4) + " um (" + pxSource + ")\n";
            if (tiffMode == false) {
                chL = "";
                if (countNuclei) chL = chL + ", nuclear stain " + uBLUE;
                if (countNuclei == false && poleNeedNuc) chL = chL + ", nuclear stain " + uBLUE + " (poles)";
                if (countGreen)  chL = chL + ", parasite label " + uGREEN;
                if (countDouble) chL = chL + ", second marker " + uRED;
                if (poleNeedMk)  chL = chL + ", pole marker " + chPOLE;
                qi = qi + "Channels: " + substring(chL, 2) + "  (" + chFrom + ")\n";
            }
            cntL = "";
            if (countNuclei) cntL = cntL + ", " + nucleiCount + " host cells (" + nucleiROIs + " nuclei)";
            if (countGreen)  cntL = cntL + ", " + greenCount + " parasites (" + parObj + " objects)";
            if (countDouble) cntL = cntL + ", " + colocCount + " double-labelled";
            qi = qi + "Counted: " + substring(cntL, 2) + "\n";
            if (countDouble) {
                dm = dblModes[dblMode];
                qi = qi + "Double labelling: " + dm;
                if (dblMode == 1) qi = qi + " (" + objUsed + " counted of " + objN + " objects)";
                if (dblMode == 2) qi = qi + " (" + objN + " objects)";
                qi = qi + "\n";
            }
            if (lowWhy != "") qi = qi + "Under the minimum: " + lowWhy + "\n";
            qi = qi + thrInfo;
            qi = qi + "Slices:";
            for (q = 0; q < plabs.length; q++) qi = qi + " " + (q + 1) + " = " + plabs[q] + ";";
            qi = qi + "\n" + setLog;
            setMetadata("Info", qi);
            saveAs("Tiff", stackFolder + stackTitle + ".tif");
            if (keepWindowsOpen) {
                // kept: no longer one of the images this one is closed with
                idsKeep = Array.concat(idsKeep, getImageID());
                if (useBatch) {
                    setBatchMode("show");
                    setBatchMode(true);
                }
            }
        }
    }

    // everything this image opened, and only that
    nc = closeNewImages(idsKeep);
    roiManager("Reset");
    run("Clear Results");

    summaryManip = Array.concat(summaryManip, manip);
    summaryCond  = Array.concat(summaryCond,  cond);
    summaryNames = Array.concat(summaryNames, base);
    summaryCells = Array.concat(summaryCells, nucleiCount);
    summaryGreen = Array.concat(summaryGreen, greenCount);
    summaryColoc = Array.concat(summaryColoc, colocCount);
    summaryObj   = Array.concat(summaryObj, objUsed);
    summaryOri   = Array.concat(summaryOri, poleOri);
    summaryBas   = Array.concat(summaryBas, poleN[1]);
    summaryApi   = Array.concat(summaryApi, poleN[2]);
    summaryInd   = Array.concat(summaryInd, poleN[3]);
    summaryMid   = Array.concat(summaryMid, poleN[4]);
    // under the minimum: not counted for now (see below the loop)
    useImg = 1;
    if (lowWhy != "") useImg = 0;
    summaryUse = Array.concat(summaryUse, useImg);
    summaryWhy = Array.concat(summaryWhy, lowWhy);
    summaryKey = Array.concat(summaryKey, sKey);

    // ---- Stop early: add this image to its condition's total ----
    // (an image under the minimum does not count towards the target)
    if (sKey >= 0 && useImg == 1) {
        if (stopOn == "parasites") stopDone[sKey] = stopDone[sKey] + greenCount;
        else                       stopDone[sKey] = stopDone[sKey] + nucleiCount;
        stopImgs[sKey] = stopImgs[sKey] + 1;
        if (stopDone[sKey] >= stopTarget)
            print("   target reached for " + stopLab[sKey] + ": " + stopDone[sKey] +
                  " " + stopWhatTxt + " in " + stopImgs[sKey] + " image(s)");
    }
}

// ---- Images under the minimum, set aside: back where the target needs them ----
// A condition that falls short of the stop target gets its images set
// aside back, the richest first, until the target is reached.
if (lowOn && lowMode == 1 && stopTarget > 0) {
    for (sq = 0; sq < stopKeys.length; sq++) {
        if (stopDone[sq] >= stopTarget) continue;
        cand = newArray(0);
        cv   = newArray(0);
        for (t = 0; t < summaryUse.length; t++) {
            if (summaryKey[t] != sq) continue;
            if (summaryUse[t] != 0) continue;
            cand = Array.concat(cand, t);
            if (stopOn == "parasites") cv = Array.concat(cv, summaryGreen[t]);
            else                       cv = Array.concat(cv, summaryCells[t]);
        }
        // richest first; equal counts in the order the images were taken
        for (a = 0; a < cand.length - 1; a++) {
            best = a;
            for (b = a + 1; b < cand.length; b++) {
                if (cv[b] > cv[best]) best = b;
                else if (cv[b] == cv[best] && cand[b] < cand[best]) best = b;
            }
            if (best != a) {
                sw = cand[a]; cand[a] = cand[best]; cand[best] = sw;
                sv = cv[a];   cv[a]   = cv[best];   cv[best]   = sv;
            }
        }
        for (q = 0; q < cand.length; q++) {
            if (stopDone[sq] >= stopTarget) continue;
            summaryUse[cand[q]] = 2;
            stopDone[sq] = stopDone[sq] + cv[q];
            stopImgs[sq] = stopImgs[sq] + 1;
        }
    }
}

// ---- Stop early: what each condition got ----
if (stopTarget > 0) {
    print("");
    print("Stop early, target " + stopTarget + " " + stopWhatTxt + " per condition:");
    for (sq = 0; sq < stopKeys.length; sq++) {
        nLowU = 0;
        nLowO = 0;
        for (t = 0; t < summaryUse.length; t++) {
            if (summaryKey[t] != sq) continue;
            if (summaryUse[t] == 2) nLowU = nLowU + 1;
            if (summaryUse[t] == 0) nLowO = nLowO + 1;
        }
        sLine = "   " + stopLab[sq] + ": " + stopDone[sq] + " " + stopWhatTxt +
                " in " + stopImgs[sq] + " image(s)";
        if (nLowU > 0)
            sLine = sLine + " (" + nLowU + " of them under the minimum, needed for the target)";
        if (stopSkip[sq] > 0)
            sLine = sLine + ", " + stopSkip[sq] + " image(s) left out";
        if (nLowO > 0)
            sLine = sLine + ", " + nLowO + " under the minimum left out";
        if (stopDone[sq] < stopTarget)
            sLine = sLine + "   TARGET NOT REACHED - not enough images";
        print(sLine);
    }
}

// ---- Images under the minimum: what became of each ----
if (lowOn) {
    nLowOut  = 0;
    nLowUsed = 0;
    for (t = 0; t < summaryUse.length; t++) {
        if (summaryUse[t] == 0) nLowOut  = nLowOut + 1;
        if (summaryUse[t] == 2) nLowUsed = nLowUsed + 1;
    }
    print("");
    lowHead = "Images with " + lowRule + ": " + nLowOut + " left out";
    if (lowMode == 1 && stopTarget > 0)
        lowHead = lowHead + ", " + nLowUsed + " counted to reach the target";
    print(lowHead);
    for (t = 0; t < summaryUse.length; t++) {
        if (summaryUse[t] == 1) continue;
        labT = "" + summaryCond[t] + " / " + summaryNames[t];
        if (summaryManip[t] != "-") labT = "" + summaryManip[t] + " / " + labT;
        fate = " - left out";
        if (summaryUse[t] == 2) fate = " - counted, needed for the target";
        print("   " + labT + ": " + summaryWhy[t] + fate);
    }
}

if (is("Batch Mode")) setBatchMode(false);


// ======================================================================
//  RESULT ASSEMBLY
// ======================================================================
nRows = summaryNames.length;

// ---- experiments, alphabetical ----
for (t = 0; t < nRows; t++) {
    known = 0;
    for (u = 0; u < manipList.length; u++) if (manipList[u] == summaryManip[t]) known = 1;
    if (known == 0) manipList = Array.concat(manipList, summaryManip[t]);
}
Array.sort(manipList);
nM = manipList.length;
singleManip = 0;
if (nM == 1) { if (manipList[0] == "-") singleManip = 1; }

// ---- union of conditions ----
for (t = 0; t < nRows; t++) {
    known = 0;
    for (u = 0; u < condsAll.length; u++) if (condsAll[u] == summaryCond[t]) known = 1;
    if (known == 0) condsAll = Array.concat(condsAll, summaryCond[t]);
}
nC = condsAll.length;

// The "% of control" column only makes sense when a control exists.
hasControl = false;
for (c = 0; c < nC; c++) {
    if (isControlName(condsAll[c]) == 1) hasControl = true;
}

// ---- per experiment x condition totals ----
aggCells = newArray(nM * nC);
aggGreen = newArray(nM * nC);
aggColoc = newArray(nM * nC);
aggN     = newArray(nM * nC);
aggAll   = newArray(nM * nC);
for (t = 0; t < nRows; t++) {
    mi = -1;
    for (u = 0; u < nM; u++) if (manipList[u] == summaryManip[t]) mi = u;
    ci = -1;
    for (u = 0; u < nC; u++) if (condsAll[u] == summaryCond[t]) ci = u;
    if (mi < 0) continue;
    if (ci < 0) continue;
    if (isExcluded(summaryManip[t], summaryCond[t]) == 1) continue;
    k = mi * nC + ci;
    aggAll[k] = aggAll[k] + 1;
    // under the minimum and not needed for the target: not counted
    if (summaryUse[t] == 0) continue;
    aggCells[k] = aggCells[k] + summaryCells[t];
    aggGreen[k] = aggGreen[k] + summaryGreen[t];
    aggColoc[k] = aggColoc[k] + summaryColoc[t];
    aggN[k]     = aggN[k] + 1;
}

// ---- display order of the conditions ----
// Controls first, then increasing concentration. Whether a condition is
// present in every experiment does NOT enter the sort: the detail tables
// are aligned with grey cells, which must not disturb the dose order.
sk = newArray(nC);
for (c = 0; c < nC; c++) {
    num = firstNumber(condsAll[c]);
    if (num < 0) num = 99999;
    if (isControlName(condsAll[c]) == 1) sk[c] = num;
    else                                 sk[c] = 1000000000 + num;
}
for (a = 0; a < nC - 1; a++) {
    mn = a;
    for (b = a + 1; b < nC; b++) if (sk[b] < sk[mn]) mn = b;
    if (mn != a) {
        sw = condsAll[a]; condsAll[a] = condsAll[mn]; condsAll[mn] = sw;
        sv = sk[a];       sk[a]       = sk[mn];       sk[mn]       = sv;
        for (m = 0; m < nM; m++) {
            i1 = m * nC + a;
            i2 = m * nC + mn;
            v1 = aggCells[i1]; aggCells[i1] = aggCells[i2]; aggCells[i2] = v1;
            v2 = aggGreen[i1]; aggGreen[i1] = aggGreen[i2]; aggGreen[i2] = v2;
            v3 = aggColoc[i1]; aggColoc[i1] = aggColoc[i2]; aggColoc[i2] = v3;
            v4 = aggN[i1];     aggN[i1]     = aggN[i2];     aggN[i2]     = v4;
            v5 = aggAll[i1];   aggAll[i1]   = aggAll[i2];   aggAll[i2]   = v5;
        }
    }
}

// ---- condition groups ----
// Same concentration = same group (controls form a group of their own).
// Once sorted, group members are contiguous, so a pooled row can sum them.
gOf = newArray(nC);
gCount = 0;
for (c = 0; c < nC; c++) {
    if (c == 0) {
        gOf[0] = 0;
        gCount = 1;
    } else {
        if (sameGroup(condsAll[c - 1], condsAll[c]) == 1) {
            gOf[c] = gOf[c - 1];
        } else {
            gOf[c] = gCount;
            gCount = gCount + 1;
        }
    }
}


// ---- image index, grouped by experiment then condition ----
// Avoids rescanning the whole table for every cell written.
ordStart = newArray(nM * nC);
ordIdx   = newArray(nRows);
pos = 0;
for (m = 0; m < nM; m++) {
    for (c = 0; c < nC; c++) {
        ordStart[m * nC + c] = pos;
        for (t = 0; t < nRows; t++) {
            if (summaryManip[t] != manipList[m]) continue;
            if (summaryCond[t]  != condsAll[c])  continue;
            if (summaryUse[t] == 0) continue;     // only the images counted
            ordIdx[pos] = t;
            pos = pos + 1;
        }
    }
}


// ======================================================================
//  OUTPUT FILES
// ======================================================================
// With a minimum per image, a last column says whether each image was
// counted; without one, the files are as they always were. A class that was
// not counted has empty cells. Measure: see measureLabel (also in the Log).
csvEnd = "\n";
if (lowOn) csvEnd = ",Counted\n";
// marker objects and poles, when used: columns of their own after the measure
xHead = csvExtraHead();
csv = "Experiment,Condition,Image,Host_cells,Parasites,Double_labelled,Single_labelled,Measure" +
      xHead + csvEnd;
for (m = 0; m < nM; m++) {
    for (c = 0; c < nC; c++) {
        for (t = 0; t < nRows; t++) {
            if (summaryManip[t] != manipList[m]) continue;
            if (summaryCond[t]  != condsAll[c])  continue;
            pv = measImg(summaryCells[t], summaryGreen[t], summaryColoc[t]);
            cnT = csvCounts(t);
            xT = csvExtra(t);
            csv = csv + csvQ(summaryManip[t]) + "," + csvQ(summaryCond[t]) + "," +
                  csvQ(summaryNames[t]) + "," + cnT + "," + num2(pv) + xT;
            if (lowOn) {
                ut = usedTxt(t);
                csv = csv + "," + ut;
            }
            csv = csv + "\n";
        }
    }
}
File.saveString(csv, outputRoot + "Raw data.csv");

// one CSV per condition, next to its ROIs
for (m = 0; m < nM; m++) {
    for (c = 0; c < nC; c++) {
        if (aggAll[m * nC + c] == 0) continue;
        sub = "Image,Host_cells,Parasites,Double_labelled,Single_labelled,Measure" + xHead + csvEnd;
        for (t = 0; t < nRows; t++) {
            if (summaryManip[t] != manipList[m]) continue;
            if (summaryCond[t]  != condsAll[c])  continue;
            pv = measImg(summaryCells[t], summaryGreen[t], summaryColoc[t]);
            cnT = csvCounts(t);
            xT = csvExtra(t);
            sub = sub + csvQ(summaryNames[t]) + "," + cnT + "," + num2(pv) + xT;
            if (lowOn) {
                ut = usedTxt(t);
                sub = sub + "," + ut;
            }
            sub = sub + "\n";
        }
        // as in the main loop: no experiment level, the condition folder at once
        if (manipList[m] == "-") subDir = dataRoot + condsAll[c] + sep;
        else                     subDir = dataRoot + manipList[m] + sep + condsAll[c] + sep;
        File.saveString(sub, subDir + "Raw data - " + condsAll[c] + ".csv");
    }
}

// one line per parasite object, with the marker objects and the poles
if (perPar.length > 0) {
    pHead = "Experiment,Condition,Image,Parasite,Counts_as,Area_um2,Length_um,Double_labelled," +
            "Double_labelled_number";
    if (dblMode == 1) pHead = pHead + ",Marker_objects_on_it,Marker_objects_counted";
    if (dblPolesUsed && dblMode != 2)
        pHead = pHead + ",Oriented,Nuclei_inside,Pole_marker_objects,Pole_of_the_marker," +
                "Marker_place_pct_from_basal_end_if_oriented";
    pTxt = pHead + "\n";
    for (q = 0; q < perPar.length; q++) pTxt = pTxt + perPar[q];
    File.saveString(pTxt, outputRoot + "Parasites.csv");
}

if (doWorkbook)
    writeWorkbook(outputRoot + "Final results " + rootName + ".xls");

if (doHistExp && nC > 0) {
    if (tiffMode) {
        // one histogram per slide, its coverslips as the bars
        for (m = 0; m < nM; m++) {
            hn = outputRoot + "Histogram " + manipList[m] + ".png";
            drawHistogramOne(hn, m);
        }
    } else {
        drawHistogram(outputRoot + "Histogram " + rootName + ".png");
    }
}
if (doHistPooled && nC > 0 && singleManip == 0 && nM > 1) {
    // Pooling puts together conditions of the same number across
    // experiments. For slides that would merge coverslip 3 of one slide
    // with coverslip 3 of another, which are not the same condition.
    if (tiffMode)
        print("Pooled histogram skipped: coverslips of different slides are " +
              "not the same condition, so they cannot be pooled.");
    else
        drawGlobalHistogram(outputRoot + "Histogram pooled " + rootName + ".png");
}

if (lowOn) {
    nCounted = 0;
    for (t = 0; t < nRows; t++) if (summaryUse[t] > 0) nCounted = nCounted + 1;
    print("Done. " + nRows + " image(s), " + nCounted + " counted and " + (nRows - nCounted) +
          " left out with " + lowRule + ", " + nM + " experiment(s), " + nC + " condition(s).");
} else
    print("Done. " + nRows + " image(s), " + nM + " experiment(s), " + nC + " condition(s).");
if (exclList.length > 0)
    print(exclList.length + " condition(s) excluded from the calculations.");
print("Reported measure: " + measureLabel);
if (measureNote != "") print(measureNote);
print("Output: " + outputRoot);

// The Log records the options used and, with a stop target, which images
// were left out: kept next to the results.
logTxt = getInfo("log");
File.saveString(logTxt, outputRoot + "Log.txt");


// ======================================================================
//  FUNCTIONS - image outputs
// ======================================================================

// <output>/<kind>/<experiment>/<condition>/, created as needed.
function outSub(kind, manip, cond) {
    d = outputRoot + kind + File.separator;
    File.makeDirectory(d);
    if (manip != "-") {
        d = d + manip + File.separator;
        File.makeDirectory(d);
    }
    d = d + cond + File.separator;
    File.makeDirectory(d);
    return d;
}

// Composite merge of the channels ticked in the dialog. The image stays
// multi-channel at its original bit depth - no RGB flattening - so the
// values remain quantitative and each channel can still be turned on and
// off in Fiji. Every Z plane is kept. Returns "" if no channel was found.
function buildMerge(bn, nz) {
    run("Duplicate...", "title=[" + bn + "_dup] duplicate");
    dupT = bn + "_dup";
    selectWindow(dupT);
    getDimensions(mw, mh, mc, mz, mt);
    // the channels ticked for the merges, in their order (Merge Channels
    // takes 7 at most)
    sel = newArray(0);
    for (c = 1; c <= mc; c++) {
        on = 0;
        if (chMrg.length >= c) on = chMrg[c - 1];
        if (on == 1 && sel.length < 7) sel = Array.concat(sel, c);
    }
    if (sel.length == 0) return "";
    // the colour (LUT) and display range of each, kept through the merge
    lutR = newArray(0);
    lutG = newArray(0);
    lutB = newArray(0);
    los = newArray(sel.length);
    his = newArray(sel.length);
    for (k = 0; k < sel.length; k++) {
        if (mc > 1) Stack.setChannel(sel[k]);
        getLut(rr, gg, bb);
        lutR = Array.concat(lutR, rr);
        lutG = Array.concat(lutG, gg);
        lutB = Array.concat(lutB, bb);
        getMinAndMax(lo, hi);
        los[k] = lo;
        his[k] = hi;
    }
    if (mc > 1) run("Split Channels");
    outT = bn + "_merge";
    if (sel.length == 1) {
        // one channel: it is the image itself
        tt = chanTitle(dupT, sel[0], mc);
        if (tt == "") return "";
        selectWindow(tt);
        rename(outT);
        return outT;
    }
    opt = "";
    for (k = 0; k < sel.length; k++) {
        tt = chanTitle(dupT, sel[k], mc);
        if (tt == "") return "";
        opt = opt + "c" + (k + 1) + "=[" + tt + "] ";
    }
    // "create" builds a composite; without it Merge Channels would flatten
    // the result to RGB and the values would be lost.
    run("Merge Channels...", opt + "create");
    rename(outT);
    selectWindow(outT);
    for (k = 0; k < sel.length; k++) {
        Stack.setChannel(k + 1);
        setLut(Array.slice(lutR, 256 * k, 256 * k + 256), Array.slice(lutG, 256 * k, 256 * k + 256),
               Array.slice(lutB, 256 * k, 256 * k + 256));
        setMinAndMax(los[k], his[k]);
    }
    Stack.setChannel(1);
    Stack.setDisplayMode("composite");
    return outT;
}

// Title of one channel after Split Channels, "" if it is not open.
// A single-channel image is not split, so it keeps its own title - for
// channel 1 only: it is not copied into every colour of the merge.
function chanTitle(dupT, ch, nChan) {
    if (nChan <= 1) {
        if (ch == 1 && isOpen(dupT)) return dupT;
        return "";
    }
    t = "C" + ch + "-" + dupT;
    if (isOpen(t)) return t;
    return "";
}

function closeAllWindows() {
    lst = getList("image.titles");
    for (w = lst.length - 1; w >= 0; w--) {
        selectWindow(lst[w]);
        close();
    }
}

// IDs of the images open now.
function imageIDs() {
    n = nImages;
    ids = newArray(n);
    for (q = 1; q <= n; q++) {
        selectImage(q);
        ids[q - 1] = getImageID();
    }
    return ids;
}

// Closes every image open now that is not in keep (a list from imageIDs):
// what one file opened, and nothing the user had open. Returns how many.
function closeNewImages(keep) {
    ids = imageIDs();
    nc = 0;
    for (q = ids.length - 1; q >= 0; q--) {
        old = 0;
        for (r = 0; r < keep.length; r++) if (keep[r] == ids[q]) old = 1;
        if (old == 0) {
            selectImage(ids[q]);
            close();
            nc = nc + 1;
        }
    }
    return nc;
}

// The image src as it is counted, in front: the first time point of a time
// series (a projection would merge its time points, or keep only the one
// on display, depending on its Z planes), its Z planes merged by maximum
// projection. A single plane is used as it is: Z Project on a multichannel
// image with one plane would merge all its channels into one. src is
// closed when a new image is made. Returns the title of the result.
function countView(src) {
    selectWindow(src);
    getDimensions(vw, vh, vc, vz, vt);
    cur = src;
    if (vt > 1) {
        t1 = src + "_t1";
        // "frames" for a hyperstack, "range" for a plain stack of time points
        if (vc * vz > 1) run("Duplicate...", "title=[" + t1 + "] duplicate frames=1");
        else             run("Duplicate...", "title=[" + t1 + "] duplicate range=1-1 frames=1");
        selectWindow(src);
        close();
        selectWindow(t1);
        cur = t1;
    }
    if (vz > 1) {
        selectWindow(cur);
        run("Z Project...", "projection=[Max Intensity]");
        pj = getTitle();
        selectWindow(cur);
        close();
        cur = pj;
    }
    selectWindow(cur);
    return cur;
}

// A threshold as text: whole numbers as they are, others with 2 decimals
// below 100 (the thresholds of a 32-bit corrected channel), none above.
function nfmt(v) {
    if (v == round(v)) return d2s(v, 0);
    if (abs(v) >= 100) return d2s(v, 0);
    return d2s(v, 2);
}

// A channel number as used: a whole number, 0 (absent) when not one.
function chanOk(v) {
    if (isNaN(v) || v < 0) return 0;
    return round(v);
}

// Channels of the first image, for the "Channels" dialog: their number and
// colour (with the wavelength name for a .nd), and the channel numbers those
// colours suggest (firstB / firstG / firstR / firstD). Read from the
// metadata, so that a large file answers at once: a .nd from its own text
// (the wavelength names give the colours, as when it is opened), a TIFF or
// any other file opened as a virtual stack - only the plane on display is
// read - then closed.
function firstImageInfo() {
    firstInfo = "";
    firstB = 0;
    firstG = 0;
    firstR = 0;
    firstD = 0;
    firstNC = 0;
    if (imgPaths.length == 0) return 0;
    p = imgPaths[0];
    nm = File.getName(p);
    if (serTot[0] > 1) nm = nm + " (position / series 1 of " + serTot[0] + ")";
    lc = toLowerCase(p);
    cols = newArray(0);
    names = newArray(0);
    nc = 0;
    nz = 1;
    nt = 1;
    if (endsWith(lc, ".nd") && (openMethod == "auto" || openMethod == "imagej")) {
        okN = ndParse(p);
        if (okN == 0) {
            firstInfo = "First image: " + nm + "\n   could not be read: " + openErr;
            return 0;
        }
        nc = ndWaves.length;
        nz = ndNZ;
        nt = ndNT;
        // the image files of the first position, time point 1, are there?
        pos = 1;
        if (serList.length > 0) pos = serList[0];
        miss = "";
        stem = stripExt(File.getName(p));
        for (w = 1; w <= nc; w++) {
            f = ndFile(p, w, pos, 1);
            if (f == "") {
                // (a number followed by an array element would be added to it)
                expT = stem + "_w" + w;
                expT = expT + ndWaves[w - 1];
                miss = miss + " " + expT;
            }
        }
        if (miss != "") {
            firstInfo = "First image: " + nm + "\n   its image files were not found next to it:" + miss;
            return 0;
        }
        cols = newArray(nc);
        for (c = 0; c < nc; c++) {
            lt = lutForName(ndWaves[c]);
            cw = lutWord(lt);
            cols[c] = cw;
        }
        names = Array.copy(ndWaves);
    } else {
        wasB = is("Batch Mode");
        if (useBatch) setBatchMode(true);
        idsB = imageIDs();
        okO = openInfo(p, 0);
        if (okO == 0) {
            nc0 = closeNewImages(idsB);
            if (useBatch && wasB == false) setBatchMode(false);
            firstInfo = "First image: " + nm + "\n   could not be opened: " + openErr;
            return 0;
        }
        if (okO == 2) {
            // the Bio-Formats simple opener: sizes only, no colours
            nc = infoC;
            nz = infoZ;
            nt = infoT;
            cols = newArray(nc);
            for (c = 0; c < nc; c++) cols[c] = "?";
        } else {
            t = getTitle();
            getDimensions(w0, h0, nc, nz, nt);
            if (bitDepth() == 24) {
                // RGB: opened as its red, green and blue channels (rgbChannels)
                nc = 3;
                cols = newArray("red", "green", "blue");
            } else {
                cols = newArray(nc);
                for (c = 1; c <= nc; c++) {
                    selectWindow(t);
                    if (nc > 1) Stack.setChannel(c);
                    cw = lutColour();
                    cols[c - 1] = cw;
                }
            }
        }
        nc2 = closeNewImages(idsB);
        if (useBatch && wasB == false) setBatchMode(false);
    }
    firstNC = nc;
    firstCols = Array.copy(cols);
    firstNames = newArray(nc);
    for (c = 0; c < nc; c++) {
        firstNames[c] = "";
        if (names.length > c) firstNames[c] = names[c];
    }
    okS = sugFromCols(cols);
    firstB = dcBLUE;
    firstG = dcGREEN;
    firstR = dcRED;
    firstD = dcDIC;
    planes = "" + nz + " Z plane";
    if (nz > 1) planes = planes + "s";
    if (nt > 1) planes = planes + ", " + nt + " time points";
    firstInfo = "First image: " + nm + "  -  " + nc + " channel(s), " + planes;
    if (firstB > 0 || firstG > 0 || firstR > 0)
        firstInfo = firstInfo + "\n   What each channel is was suggested from its colour: check it.";
    return 1;
}

// Opens file p (entry idx) as a virtual stack, for its metadata only: in
// front, returns 1. Through the Bio-Formats simple opener nothing is opened:
// its sizes go to infoC / infoZ / infoT, and it returns 2. Returns 0 with
// the reason in openErr if nothing could be opened.
function openInfo(p, idx) {
    p = fwd(p);
    openErr = "";
    lc = toLowerCase(p);
    isTif = 0;
    if (endsWith(lc, ".tif") || endsWith(lc, ".tiff")) isTif = 1;
    if (indexOf(lc, ".ome.tif") >= 0) isTif = 0;
    n0 = nImages;
    if (openMethod == "imagej" || (openMethod == "auto" && isTif == 1)) {
        if (isTif == 1) run("TIFF Virtual Stack...", "open=[" + p + "]");
        else            open(p);
        if (nImages > n0) return 1;
        // not a TIFF the virtual stack opener reads: opened in full
        open(p);
        if (nImages > n0) return 1;
        openErr = "ImageJ could not open it";
        return 0;
    }
    if (openMethod == "ext") {
        run("Bio-Formats Macro Extensions");
        Ext.setId(p);
        if (serTot.length > idx) {
            if (serTot[idx] > 1) Ext.setSeries(serList[idx] - 1);
        }
        Ext.getSizeC(infoC);
        Ext.getSizeZ(infoZ);
        Ext.getSizeT(infoT);
        Ext.close();
        return 2;
    }
    // Bio-Formats Importer, as a virtual stack and without autoscale (which
    // would read every plane): the colours come from the metadata. (In its
    // "Default" colour mode Bio-Formats leaves them out: every file would
    // read red, green, blue in channel order.)
    o = "open=[" + p + "] color_mode=Composite rois_import=[ROI manager] view=Hyperstack " +
        "stack_order=XYCZT use_virtual_stack";
    if (serTot.length > idx) {
        if (serTot[idx] > 1) o = o + " series_" + serList[idx];
    }
    run("Bio-Formats Importer", o);
    if (nImages > n0) return 1;
    openErr = "Bio-Formats could not open it";
    return 0;
}

// Colour word of a LUT name (lutForName).
function lutWord(lt) {
    if (lt == "Blue")    return "blue";
    if (lt == "Green")   return "green";
    if (lt == "Red")     return "red";
    if (lt == "Magenta") return "magenta";
    if (lt == "Cyan")    return "cyan";
    if (lt == "Yellow")  return "yellow";
    return "grey";
}

// Channel roles from the colour words of the channels (lutColour), into
// dcBLUE / dcGREEN / dcRED / dcDIC: grey = DIC, otherwise the colour, the
// first channel of each colour; cyan, magenta and yellow fill a role still
// free. The pole marker channel (dcSkip) is never one of them. Returns 1.
function sugFromCols(cols) {
    dcDIC = 0; dcRED = 0; dcGREEN = 0; dcBLUE = 0;
    for (c = 1; c <= cols.length; c++) {
        if (c == dcSkip) continue;
        cw = cols[c - 1];
        if (cw == "grey") {
            if (dcDIC == 0) dcDIC = c;
        } else if (cw == "blue") {
            if (dcBLUE == 0) dcBLUE = c;
        } else if (cw == "green") {
            if (dcGREEN == 0) dcGREEN = c;
        } else if (cw == "red") {
            if (dcRED == 0) dcRED = c;
        } else if (cw == "cyan") {
            if (dcBLUE == 0) dcBLUE = c;
            else if (dcGREEN == 0) dcGREEN = c;
        } else if (cw == "magenta") {
            if (dcRED == 0) dcRED = c;
            else if (dcBLUE == 0) dcBLUE = c;
        } else if (cw == "yellow") {
            if (dcRED == 0) dcRED = c;
            else if (dcGREEN == 0) dcGREEN = c;
        }
    }
    return 1;
}

// ---------------- channels: what they are, their colours ----------------

// Label of row k of the channel table: the colour and the name its file
// gives to that channel of the first image.
function rowLabel(k) {
    d = "?";
    if (firstCols.length >= k) d = firstCols[k - 1];
    if (firstNames.length >= k) {
        if (firstNames[k - 1] != "") d = d + ", " + firstNames[k - 1];
    }
    return "Channel " + k + "  (" + d + ")";
}

// Colour keys of the channel table ("file": as in the file), and their index.
function colKey(q) {
    keys = newArray("file", "grey", "blue", "green", "red", "cyan", "magenta", "yellow");
    return keys[q];
}

function colKeyIdx(cv) {
    keys = newArray("file", "grey", "blue", "green", "red", "cyan", "magenta", "yellow");
    for (q = 0; q < keys.length; q++) if (keys[q] == cv) return q;
    return -1;
}

// The usual colour of what a channel is (index in the roles of the table),
// for a channel its file shows in grey.
function roleColour(r) {
    if (r == 0) return "blue";
    if (r == 1) return "green";
    if (r == 2) return "red";
    if (r == 3) return "magenta";
    if (r == 4) return "grey";
    return "file";
}

// ImageJ command of the LUT of a colour key ("" for "file").
function lutCmd(cw) {
    if (cw == "grey")    return "Grays";
    if (cw == "blue")    return "Blue";
    if (cw == "green")   return "Green";
    if (cw == "red")     return "Red";
    if (cw == "cyan")    return "Cyan";
    if (cw == "magenta") return "Magenta";
    if (cw == "yellow")  return "Yellow";
    return "";
}

// For the per-file option: the roles in use (fk: channel of the nuclear
// stain, parasite label, second marker, pole marker, DIC) whose colour in
// the first image is unknown or shared with another one; "" when none.
function colourClash(fk) {
    nm = newArray("nuclear stain", "parasite label", "second marker", "", "transmission / DIC");
    out = "";
    for (a = 0; a < 5; a++) {
        if (a == 3 || fk[a] == 0) continue;
        ca = "?";
        if (fk[a] <= firstCols.length) ca = firstCols[fk[a] - 1];
        if (ca == "?" || ca == "other") {
            out = out + "\n   - " + nm[a] + " (channel " + fk[a] + "): colour unknown";
            continue;
        }
        for (b = a + 1; b < 5; b++) {
            if (b == 3 || fk[b] == 0) continue;
            cb = "?";
            if (fk[b] <= firstCols.length) cb = firstCols[fk[b] - 1];
            if (cb == ca) out = out + "\n   - " + nm[a] + " and " + nm[b] + ": both " + ca;
        }
    }
    return out;
}

// The colours chosen for the images made, on image t (channels beyond the
// table keep theirs); several channels then shown one at a time. Returns 1.
function applyColours(t) {
    selectWindow(t);
    if (bitDepth() == 24) return 1;
    getDimensions(aw, ah, anc, anz, ant);
    for (c = 1; c <= anc; c++) {
        cw = "file";
        if (chCols.length >= c) cw = chCols[c - 1];
        lc = lutCmd(cw);
        if (lc == "") continue;
        if (anc > 1) Stack.setChannel(c);
        getMinAndMax(lo, hi);
        run(lc);
        setMinAndMax(lo, hi);
    }
    if (anc > 1) {
        Stack.setChannel(1);
        if (is("composite")) Stack.setDisplayMode("color");
    }
    return 1;
}

// The colour of each channel, for the Log.
function coloursText() {
    out = "";
    for (c = 1; c <= chCols.length; c++) {
        cw = chCols[c - 1];
        if (cw == "file") {
            cw = "as in the file";
            if (firstCols.length >= c) cw = cw + " (" + firstCols[c - 1] + ")";
        }
        if (out != "") out = out + ", ";
        out = out + c + " " + cw;
    }
    if (out == "") out = "as in the files";
    return out;
}

// The channels going into the merges, for the Log.
function mergeText() {
    out = "";
    for (c = 1; c <= chMrg.length; c++) {
        if (chMrg[c - 1] == 1) {
            if (out != "") out = out + ", ";
            out = out + c;
        }
    }
    if (out == "") out = "none (no merge made)";
    return out;
}

// ---------------- settings files ----------------
// One "key=value" per line; lines starting with # are comments.

// Reads a settings file into setKeys / setVals. Returns how many settings.
function setRead(path) {
    setKeys = newArray(0);
    setVals = newArray(0);
    if (path == "") return 0;
    if (!File.exists(path)) return 0;
    txt = File.openAsString(path);
    lines = split(txt, "\n");
    for (q = 0; q < lines.length; q++) {
        ln = replace(lines[q], "\r", "");
        if (startsWith(ln, "#")) continue;
        e = indexOf(ln, "=");
        if (e <= 0) continue;
        setKeys = Array.concat(setKeys, substring(ln, 0, e));
        setVals = Array.concat(setVals, substring(ln, e + 1));
    }
    return setKeys.length;
}

// Value of a setting as text, def when absent.
function sStr(key, def) {
    for (q = 0; q < setKeys.length; q++) {
        if (setKeys[q] == key) return setVals[q];
    }
    return def;
}

// Value of a setting as a number, def when absent or not a number.
function sNum(key, def) {
    s = sStr(key, "");
    if (s == "") return def;
    s = replace(s, ",", ".");
    v = parseFloat(s);
    if (isNaN(v)) return def;
    return v;
}

// The first dialog: what to do, input, pixel size.
function applyGeneral() {
    if (setKeys.length == 0) return 0;
    doCount      = sNum("doCount", doCount);
    doQC         = sNum("doQC", doQC);
    doWorkbook   = sNum("doWorkbook", doWorkbook);
    doHistExp    = sNum("doHistExp", doHistExp);
    doHistPooled = sNum("doHistPooled", doHistPooled);
    doHyper      = sNum("doHyper", doHyper);
    doProject    = sNum("doProject", doProject);
    doMergeStack = sNum("doMergeStack", doMergeStack);
    doMergeProj  = sNum("doMergeProj", doMergeProj);
    inputMode    = sStr("inputMode", inputMode);
    tiffMode     = sNum("tiffMode", tiffMode);
    extList      = sStr("extList", extList);
    splitSeries  = sNum("splitSeries", splitSeries);
    openMethod   = sStr("openMethod", openMethod);
    useBatch     = sNum("useBatch", useBatch);
    pixelForce   = sNum("pixelForce", pixelForce);
    pixelSize    = sNum("pixelSize", pixelSize);
    pickOutDir   = sNum("pickOutDir", pickOutDir);
    // the task; settings of an earlier version say it by what they did
    tm = sNum("taskMode", -1);
    if (tm < 0) {
        imgO = 0;
        if (doHyper || doProject || doMergeStack || doMergeProj) imgO = 1;
        tm = 0;
        if (imgO == 1 && doCount) tm = 2;
        if (imgO == 1 && doCount == false) tm = 1;
    }
    taskMode = round(tm);
    if (taskMode < 0 || taskMode > 2) taskMode = 0;
    // values a dialog would reject: back to the defaults
    okI = 0;
    for (q = 0; q < inKeys.length; q++) if (inKeys[q] == inputMode) okI = 1;
    if (okI == 0) inputMode = "AUTO";
    okI = 0;
    for (q = 0; q < openKeys.length; q++) if (openKeys[q] == openMethod) okI = 1;
    if (okI == 0) openMethod = "auto";
    if (pixelSize <= 0) pixelSize = 0.11;
    return 1;
}

// Everything else: channels, objects, detection, Calibrate, QC display.
function applyDetection() {
    if (setKeys.length == 0) return 0;
    chBLUE  = sNum("chBLUE", chBLUE);
    chGREEN = sNum("chGREEN", chGREEN);
    chRED   = sNum("chRED", chRED);
    chDIC   = sNum("chDIC", chDIC);
    kwBlue  = sStr("kwBlue", kwBlue);
    kwGreen = sStr("kwGreen", kwGreen);
    kwRed   = sStr("kwRed", kwRed);
    dataSub = sStr("dataSub", dataSub);
    autoDetectChannels = sNum("autoDetectChannels", autoDetectChannels);
    countNuclei = sNum("countNuclei", countNuclei);
    countGreen  = sNum("countGreen", countGreen);
    countDouble = sNum("countDouble", countDouble);
    measureMode = sNum("measureMode", measureMode);
    mergeBlue   = sNum("mergeBlue", mergeBlue);
    mergeGreen  = sNum("mergeGreen", mergeGreen);
    mergeRed    = sNum("mergeRed", mergeRed);
    mergeDIC    = sNum("mergeDIC", mergeDIC);
    chColSet    = sStr("chColours", "");
    chMrgSet    = sStr("chMerges", "");

    nucleiThresholdFactor = sNum("nucleiThresholdFactor", nucleiThresholdFactor);
    nucleiCapF            = sNum("nucleiCapF", nucleiCapF);
    nucleiMinArea         = sNum("nucleiMinArea", nucleiMinArea);
    splitBigNuclei        = sNum("splitBigNuclei", splitBigNuclei);
    nucleiDoubletFactor   = sNum("nucleiDoubletFactor", nucleiDoubletFactor);
    nucleiMaxSplit        = sNum("nucleiMaxSplit", nucleiMaxSplit);
    nucleiFragFactor      = sNum("nucleiFragFactor", nucleiFragFactor);
    nucleiEdgeMode        = sNum("nucleiEdgeMode", nucleiEdgeMode);
    nucleiBgRolling       = sNum("nucleiBgRolling", nucleiBgRolling);
    nucleiBlurSigma       = sNum("nucleiBlurSigma", nucleiBlurSigma);
    nucleiThresholdMethod = sStr("nucleiThresholdMethod", nucleiThresholdMethod);
    nucleiWatershed       = sNum("nucleiWatershed", nucleiWatershed);
    nucleiWsTol           = sNum("nucleiWsTol", nucleiWsTol);
    nucleiCirc            = sStr("nucleiCirc", nucleiCirc);

    greenThresholdFactor  = sNum("greenThresholdFactor", greenThresholdFactor);
    greenCapF             = sNum("greenCapF", greenCapF);
    greenMinArea          = sNum("greenMinArea", greenMinArea);
    greenMaxArea          = sNum("greenMaxArea", greenMaxArea);
    greenWatershed        = sNum("greenWatershed", greenWatershed);
    greenWsTol            = sNum("greenWsTol", greenWsTol);
    splitBigParasites     = sNum("splitBigParasites", splitBigParasites);
    greenDoubletFactor    = sNum("greenDoubletFactor", greenDoubletFactor);
    greenMaxSplit         = sNum("greenMaxSplit", greenMaxSplit);
    greenBgRolling        = sNum("greenBgRolling", greenBgRolling);
    greenBlurSigma        = sNum("greenBlurSigma", greenBlurSigma);
    greenThresholdMethod  = sStr("greenThresholdMethod", greenThresholdMethod);
    greenCirc             = sStr("greenCirc", greenCirc);

    redThresholdFactor    = sNum("redThresholdFactor", redThresholdFactor);
    redCapF               = sNum("redCapF", redCapF);
    minOverlapPercent     = sNum("minOverlapPercent", minOverlapPercent);
    redBgRolling          = sNum("redBgRolling", redBgRolling);
    redBlurSigma          = sNum("redBlurSigma", redBlurSigma);
    redThresholdMethod    = sStr("redThresholdMethod", redThresholdMethod);

    dblMode      = sNum("dblMode", dblMode);
    objMinArea   = sNum("objMinArea", objMinArea);
    objMaxArea   = sNum("objMaxArea", objMaxArea);
    objTol       = sNum("objTol", objTol);
    objMinN      = sNum("objMinN", objMinN);
    objWatershed = sNum("objWatershed", objWatershed);
    encMinArea   = sNum("encMinArea", encMinArea);
    encMaxArea   = sNum("encMaxArea", encMaxArea);
    posMode      = sNum("posMode", posMode);
    centrePct    = sNum("centrePct", centrePct);
    endPct       = sNum("endPct", endPct);
    doPoles      = sNum("doPoles", doPoles);
    poleRef      = sNum("poleRef", poleRef);
    poleSide     = sNum("poleSide", poleSide);
    chPOLE       = sNum("chPOLE", chPOLE);
    kwPole       = sStr("kwPole", kwPole);
    pnMinArea    = sNum("pnMinArea", pnMinArea);
    pnMaxArea    = sNum("pnMaxArea", pnMaxArea);
    poleDeadPct  = sNum("poleDeadPct", poleDeadPct);
    pmThresholdFactor = sNum("pmThresholdFactor", pmThresholdFactor);
    pmMinArea    = sNum("pmMinArea", pmMinArea);
    pmMaxArea    = sNum("pmMaxArea", pmMaxArea);

    stopTarget  = sNum("stopTarget", stopTarget);
    stopOn      = sStr("stopOn", stopOn);
    minCellsImg = sNum("minCellsImg", minCellsImg);
    minParImg   = sNum("minParImg", minParImg);
    lowMode     = sNum("lowMode", lowMode);
    qcGainBlue  = sNum("qcGainBlue", qcGainBlue);
    qcGainGreen = sNum("qcGainGreen", qcGainGreen);
    qcGainRed   = sNum("qcGainRed", qcGainRed);
    qcPanN      = sNum("qcPanN", qcPanN);
    qcPanP      = sNum("qcPanP", qcPanP);
    qcPanR      = sNum("qcPanR", qcPanR);
    qcPanRG     = sNum("qcPanRG", qcPanRG);
    qcPanPol    = sNum("qcPanPol", qcPanPol);
    qcPanAll    = sNum("qcPanAll", qcPanAll);
    qcRaw       = sNum("qcRaw", qcRaw);

    // values a dialog would reject: back to the defaults
    chBLUE  = chanOk(chBLUE);
    chGREEN = chanOk(chGREEN);
    chRED   = chanOk(chRED);
    chDIC   = chanOk(chDIC);
    chPOLE  = chanOk(chPOLE);
    okV = objSettingsOk();
    measureMode = round(measureMode);
    if (measureMode < 0 || measureMode >= measureModes.length) measureMode = 0;
    nucleiEdgeMode = round(nucleiEdgeMode);
    if (nucleiEdgeMode < 0 || nucleiEdgeMode > 2) nucleiEdgeMode = 0;
    lowMode = round(lowMode);
    if (lowMode < 0 || lowMode > 1) lowMode = 0;
    if (stopOn != "parasites") stopOn = "cells";
    okT = 0;
    for (q = 0; q < thrMethods.length; q++) if (thrMethods[q] == nucleiThresholdMethod) okT = 1;
    if (okT == 0) nucleiThresholdMethod = "Triangle";
    okT = 0;
    for (q = 0; q < thrMethods.length; q++) if (thrMethods[q] == greenThresholdMethod) okT = 1;
    if (okT == 0) greenThresholdMethod = "Triangle";
    okT = 0;
    for (q = 0; q < thrMethods.length; q++) if (thrMethods[q] == redThresholdMethod) okT = 1;
    if (okT == 0) redThresholdMethod = "Triangle";
    cm = circMin(nucleiCirc);
    cs = circStr(cm);
    nucleiCirc = cs;
    cm = circMin(greenCirc);
    cs = circStr(cm);
    greenCirc = cs;
    tl = wsTolOk(nucleiWsTol);
    nucleiWsTol = tl;
    tl = wsTolOk(greenWsTol);
    greenWsTol = tl;
    g = gainOk(qcGainBlue);
    qcGainBlue = g;
    g = gainOk(qcGainGreen);
    qcGainGreen = g;
    g = gainOk(qcGainRed);
    qcGainRed = g;
    cv = capOk(nucleiCapF);
    nucleiCapF = cv;
    cv = capOk(greenCapF);
    greenCapF = cv;
    cv = capOk(redCapF);
    redCapF = cv;
    ok = parClusterOk();
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

// Writes every setting of the run to path, for the next run or a later one.
function saveSettings(path) {
    s = "# Count and Colocalise v" + progVersion + " - settings of a run\n";
    s = s + "# Read back by the first dialog: 'Detection settings to start from'.\n";
    s = s + "taskMode=" + taskMode + "\n";
    s = s + "doCount=" + doCount + "\ndoQC=" + doQC + "\ndoWorkbook=" + doWorkbook + "\n";
    s = s + "doHistExp=" + doHistExp + "\ndoHistPooled=" + doHistPooled + "\n";
    s = s + "doHyper=" + doHyper + "\ndoProject=" + doProject + "\n";
    s = s + "doMergeStack=" + doMergeStack + "\ndoMergeProj=" + doMergeProj + "\n";
    s = s + "inputMode=" + inputMode + "\ntiffMode=" + tiffMode + "\nextList=" + extList + "\n";
    s = s + "splitSeries=" + splitSeries + "\nopenMethod=" + openMethod + "\n";
    s = s + "useBatch=" + useBatch + "\npixelForce=" + pixelForce + "\n";
    s = s + "pixelSize=" + d2s(pixelSize, 6) + "\npickOutDir=" + pickOutDir + "\n";
    s = s + "chBLUE=" + chBLUE + "\nchGREEN=" + chGREEN + "\nchRED=" + chRED + "\nchDIC=" + chDIC + "\n";
    s = s + "kwBlue=" + kwBlue + "\nkwGreen=" + kwGreen + "\nkwRed=" + kwRed + "\ndataSub=" + dataSub + "\n";
    s = s + "autoDetectChannels=" + autoDetectChannels + "\n";
    s = s + "countNuclei=" + countNuclei + "\ncountGreen=" + countGreen + "\ncountDouble=" + countDouble + "\n";
    s = s + "measureMode=" + measureMode + "\n";
    s = s + "chColours=" + String.join(chCols, ",") + "\nchMerges=" + String.join(chMrg, ",") + "\n";
    s = s + "nucleiThresholdFactor=" + d2s(nucleiThresholdFactor, 4) + "\n";
    s = s + "nucleiCapF=" + d2s(nucleiCapF, 4) + "\n";
    s = s + "nucleiMinArea=" + d2s(nucleiMinArea, 4) + "\n";
    s = s + "splitBigNuclei=" + splitBigNuclei + "\n";
    s = s + "nucleiDoubletFactor=" + d2s(nucleiDoubletFactor, 4) + "\n";
    s = s + "nucleiMaxSplit=" + nucleiMaxSplit + "\n";
    s = s + "nucleiFragFactor=" + d2s(nucleiFragFactor, 4) + "\n";
    s = s + "nucleiEdgeMode=" + nucleiEdgeMode + "\n";
    s = s + "nucleiBgRolling=" + d2s(nucleiBgRolling, 4) + "\n";
    s = s + "nucleiBlurSigma=" + d2s(nucleiBlurSigma, 4) + "\n";
    s = s + "nucleiThresholdMethod=" + nucleiThresholdMethod + "\n";
    s = s + "nucleiWatershed=" + nucleiWatershed + "\n";
    s = s + "nucleiWsTol=" + d2s(nucleiWsTol, 4) + "\n";
    s = s + "nucleiCirc=" + nucleiCirc + "\n";
    s = s + "greenThresholdFactor=" + d2s(greenThresholdFactor, 4) + "\n";
    s = s + "greenCapF=" + d2s(greenCapF, 4) + "\n";
    s = s + "greenMinArea=" + d2s(greenMinArea, 4) + "\n";
    s = s + "greenMaxArea=" + d2s(greenMaxArea, 4) + "\n";
    s = s + "greenWatershed=" + greenWatershed + "\n";
    s = s + "greenWsTol=" + d2s(greenWsTol, 4) + "\n";
    s = s + "splitBigParasites=" + splitBigParasites + "\n";
    s = s + "greenDoubletFactor=" + d2s(greenDoubletFactor, 4) + "\n";
    s = s + "greenMaxSplit=" + greenMaxSplit + "\n";
    s = s + "greenBgRolling=" + d2s(greenBgRolling, 4) + "\n";
    s = s + "greenBlurSigma=" + d2s(greenBlurSigma, 4) + "\n";
    s = s + "greenThresholdMethod=" + greenThresholdMethod + "\n";
    s = s + "greenCirc=" + greenCirc + "\n";
    s = s + "redThresholdFactor=" + d2s(redThresholdFactor, 4) + "\n";
    s = s + "redCapF=" + d2s(redCapF, 4) + "\n";
    s = s + "minOverlapPercent=" + d2s(minOverlapPercent, 4) + "\n";
    s = s + "redBgRolling=" + d2s(redBgRolling, 4) + "\n";
    s = s + "redBlurSigma=" + d2s(redBlurSigma, 4) + "\n";
    s = s + "redThresholdMethod=" + redThresholdMethod + "\n";
    s = s + "dblMode=" + dblMode + "\nobjMinArea=" + d2s(objMinArea, 4) + "\nobjMaxArea=" +
        d2s(objMaxArea, 4) + "\nobjTol=" + d2s(objTol, 4) + "\nobjMinN=" + objMinN +
        "\nobjWatershed=" + objWatershed + "\nencMinArea=" + d2s(encMinArea, 4) +
        "\nencMaxArea=" + d2s(encMaxArea, 4) + "\nposMode=" + posMode + "\ncentrePct=" +
        d2s(centrePct, 4) + "\nendPct=" + d2s(endPct, 4) + "\n";
    s = s + "doPoles=" + doPoles + "\npoleRef=" + poleRef + "\npoleSide=" + poleSide +
        "\nchPOLE=" + chPOLE + "\nkwPole=" + kwPole + "\npnMinArea=" + d2s(pnMinArea, 4) +
        "\npnMaxArea=" + d2s(pnMaxArea, 4) + "\npoleDeadPct=" + d2s(poleDeadPct, 4) +
        "\npmThresholdFactor=" + d2s(pmThresholdFactor, 4) + "\npmMinArea=" +
        d2s(pmMinArea, 4) + "\npmMaxArea=" + d2s(pmMaxArea, 4) + "\n";
    s = s + "stopTarget=" + stopTarget + "\nstopOn=" + stopOn + "\n";
    s = s + "minCellsImg=" + minCellsImg + "\nminParImg=" + minParImg + "\nlowMode=" + lowMode + "\n";
    s = s + "qcGainBlue=" + d2s(qcGainBlue, 4) + "\nqcGainGreen=" + d2s(qcGainGreen, 4) +
        "\nqcGainRed=" + d2s(qcGainRed, 4) + "\n";
    s = s + "qcPanN=" + qcPanN + "\nqcPanP=" + qcPanP + "\nqcPanR=" + qcPanR + "\nqcPanRG=" +
        qcPanRG + "\nqcPanPol=" + qcPanPol + "\nqcPanAll=" + qcPanAll + "\nqcRaw=" + qcRaw + "\n";
    File.saveString(s, path);
    return 1;
}

// ---------------- reported measure ----------------

// The measure in force (measureUse), its label, and whether it is per 100
// host cells. A measure of double labelling falls back to all parasites when
// double labelling is not counted (the note says so in the Log); without
// parasites, the host cells are reported.
function setMeasure() {
    measureNote = "";
    measureUse = measureMode;
    if (countDouble == false && measureUse != 1) {
        if (measureUse != 0)
            measureNote = "NOTE: double labelling is not counted, so the measure is all parasites, " +
                          "not '" + measureModes[measureMode] + "'.";
        measureUse = 1;
    }
    if (countGreen == false) {
        measureUse = 4;
        measureNote = "";
    }
    normalizeToCells = false;
    if (countNuclei && measureUse <= 2) normalizeToCells = true;
    if (measureUse == 0) measureLabel = "Single-labelled parasites";
    if (measureUse == 1) measureLabel = "Parasites";
    if (measureUse == 2) measureLabel = "Double-labelled parasites";
    if (measureUse <= 2) {
        if (normalizeToCells) measureLabel = measureLabel + " / 100 cells";
        else                  measureLabel = measureLabel + " (count)";
    }
    if (measureUse == 3) measureLabel = "Double-labelled, % of parasites";
    if (measureUse == 4) measureLabel = "Host cells (count)";
    return measureUse;
}

// The measure of one image, or of totals, from its counts.
function measImg(cells, par, dbl) {
    if (measureUse == 3) {
        if (par > 0) return dbl * 100.0 / par;
        return 0;
    }
    if (measureUse == 4) return cells;
    num = par;
    if (measureUse == 0) num = par - dbl;
    if (measureUse == 2) num = dbl;
    if (normalizeToCells) {
        if (cells > 0) return num * 100.0 / cells;
        return 0;
    }
    return num;
}

// Height of a bar from the totals of its images: a ratio is computed on
// the totals (per 100 cells, % of parasites), a count is the mean per image.
function measBar(cells, par, dbl, nImg) {
    v = measImg(cells, par, dbl);
    if (normalizeToCells || measureUse == 3) return v;
    if (nImg > 0) return v / nImg;
    return 0;
}

// Counts of image t for the CSV files: host cells, parasites, double- and
// single-labelled, empty for what was not counted.
function csvCounts(t) {
    a = "";
    b = "";
    c = "";
    d = "";
    if (countNuclei) a = "" + summaryCells[t];
    if (countGreen) {
        b = "" + summaryGreen[t];
        d = "" + (summaryGreen[t] - summaryColoc[t]);
    }
    if (countDouble) c = "" + summaryColoc[t];
    return a + "," + b + "," + c + "," + d;
}

// Headings of the extra CSV columns: marker objects counted (objects
// inside the parasites), the poles (pole option). "" when neither.
function csvExtraHead() {
    s = "";
    if (countDouble && dblMode == 1) s = s + ",Marker_objects_counted";
    if (dblPolesUsed && dblMode != 2)
        s = s + ",Oriented_parasite_objects,Basal_pole,Apical_pole,Pole_indeterminable," +
            "Pole_sum,Not_at_a_pole,Basal_pole_pct,Apical_pole_pct,Pole_indeterminable_pct," +
            "Pole_sum_pct";
    return s;
}

// The extra CSV columns of image t; the percentages are of its
// double-labelled parasites.
function csvExtra(t) {
    s = "";
    if (countDouble && dblMode == 1) s = s + "," + summaryObj[t];
    if (dblPolesUsed && dblMode != 2) {
        d  = summaryColoc[t];
        nb = summaryBas[t];
        na = summaryApi[t];
        ni = summaryInd[t];
        nm = summaryMid[t];
        ns = nb + na + ni;
        p1 = pctTxt(nb, d);
        p2 = pctTxt(na, d);
        p3 = pctTxt(ni, d);
        p4 = pctTxt(ns, d);
        s = s + "," + summaryOri[t] + "," + nb + "," + na + "," + ni + "," + ns + "," + nm +
            "," + p1 + "," + p2 + "," + p3 + "," + p4;
    }
    return s;
}

// n as a % of d, 2 decimals; "" when d is 0.
function pctTxt(n, d) {
    if (d == 0) return "";
    s = num2(100 * n / d);
    return s;
}


// ======================================================================
//  FUNCTIONS - pixel size
// ======================================================================

// Sets the pixel size of the image in front, in um, which is what turns
// every area into um2 and therefore decides what is counted.
//   - forced in the first dialog: the value given there, always;
//   - otherwise the calibration of the file, converted to um when it is in
//     another length unit (nm, mm, cm, m). It is not used - the value of the
//     first dialog is - when the file has none, when it is in inches or any
//     unit that is no length (a TIFF written with a DPI tag, for instance),
//     or when it is out of the range of a microscope (0.005 to 100 um).
// The Z spacing follows the same conversion. The pixel size used is left in
// pxUsed / pxUsedH, with where it came from in pxSource; the first image of
// the run, and every image whose pixel size differs from the one before,
// is reported in the Log.
function applyPixelSize() {
    getVoxelSize(vw, vh, vd, vu);
    f = umPerUnit(vu);
    useFile = 0;
    why = "";
    if (pixelForce) {
        why = "forced in the first dialog; the image said " + vw + " " + vu;
    } else if (f == 0) {
        lu = toLowerCase(vu);
        if (lu == "pixel" || lu == "pixels" || lu == "" || lu == " ")
            why = "the image has none: value of the first dialog";
        else
            why = "the image said " + vw + " " + vu + ", not a length in um: value of the first dialog";
    } else if (vw * f < 0.005 || vw * f > 100 || vh * f < 0.005 || vh * f > 100) {
        why = "the image said " + vw + " " + vu + ", out of the range of a microscope: " +
              "value of the first dialog";
    } else {
        useFile = 1;
    }
    if (useFile == 1) {
        pw = vw * f;
        ph = vh * f;
        pd = vd * f;
        if (pd <= 0) pd = pw;
        pxSource = "read from the image";
        if (f != 1) pxSource = "read from the image, " + vw + " " + vu + " converted to um";
    } else {
        pw = pixelSize;
        ph = pixelSize;
        // Z spacing: kept when it is a length, the pixel size otherwise
        pd = vd * f;
        if (f == 0 || pd <= 0) pd = pixelSize;
        pxSource = why;
    }
    setVoxelSize(pw, ph, pd, "micron");
    pxUsed = pw;
    pxUsedH = ph;
    pxUnit = "um";
    // (the pixel size of each image is written in the Log by its caller)
    // the value of the first dialog only fills in for images without one:
    // said once, the first time it is not used
    if (calibShown == false && useFile == 1 && abs(pixelSize - pw) > 0.01 * pixelSize) {
        print("   NOTE: the pixel size of the first dialog (" + pixelSize +
              " um) is not used, because the image has its own. To " +
              "impose it, choose 'The value below, whatever the image says'.");
        calibShown = true;
    }
    return useFile;
}

// Factor turning a length unit into um; 0 when the unit is no length (pixel,
// inch from a DPI tag, a blank, anything unknown).
function umPerUnit(u) {
    lu = toLowerCase(u);
    while (startsWith(lu, " ")) lu = substring(lu, 1);
    while (endsWith(lu, " ")) lu = substring(lu, 0, lengthOf(lu) - 1);
    if (lu == "um" || lu == "micron" || lu == "microns" || lu == "micrometer" ||
        lu == "micrometers" || lu == "micrometre" || lu == "micrometres") return 1;
    if (lu == "nm" || lu == "nanometer" || lu == "nanometers" || lu == "nanometre") return 0.001;
    if (lu == "mm" || lu == "millimeter" || lu == "millimeters" || lu == "millimetre") return 1000;
    if (lu == "cm" || lu == "centimeter" || lu == "centimeters" || lu == "centimetre") return 10000;
    if (lu == "m" || lu == "meter" || lu == "meters" || lu == "metre") return 1000000;
    if (lu == "km") return 1000000000;
    // the micro sign or a Greek mu followed by m, whatever its encoding
    if (lengthOf(lu) == 2 && endsWith(lu, "m")) return 1;
    if (indexOf(lu, "micro") >= 0) return 1;
    return 0;
}


// ======================================================================
//  FUNCTIONS - MetaMorph .nd, read without Bio-Formats
// ======================================================================
// A .nd is a text file describing the acquisition - wavelengths, stage
// positions, time points, Z series. The images are TIFF (or STK) files next
// to it, one per wavelength, position and time point, each holding the Z
// planes:  <nd name>_w<n><wavelength name>_s<position>_t<time point>.TIF
// where each part is there only when the acquisition has it. They are
// opened here with ImageJ itself and put together as one hyperstack per
// position, in the order of the wavelengths of the .nd.

// Reads the .nd p into the nd... globals. Returns 1, or 0 with the reason
// in openErr.
function ndParse(p) {
    ndWaves = newArray(0);
    ndWaveZ = newArray(0);
    ndStages = newArray(0);
    ndNPos = 1;
    ndNT = 1;
    ndNZ = 1;
    ndZStep = 0;
    ndStage = false;
    ndTime = false;
    ndWave = false;
    if (!File.exists(p)) {
        openErr = "file not found: " + p;
        return 0;
    }
    txt = File.openAsString(p);
    lines = split(txt, "\n");
    nW = 0;
    nS = 0;
    nT = 0;
    doZ = true;
    wI = newArray(0);
    wN = newArray(0);
    zI = newArray(0);
    zV = newArray(0);
    sI = newArray(0);
    sN = newArray(0);
    nKeys = 0;
    for (q = 0; q < lines.length; q++) {
        ln = replace(lines[q], "\r", "");
        // a line holding a key starts with it, in quotes; other lines are
        // the continuation of a long value (a description)
        if (!startsWith(ln, "\"")) continue;
        q2 = indexOf(ln, "\"", 1);
        if (q2 < 0) continue;
        key = substring(ln, 1, q2);
        rest = substring(ln, q2 + 1);
        cm = indexOf(rest, ",");
        if (cm < 0) continue;
        val = trimS(substring(rest, cm + 1));
        if (lengthOf(val) >= 2 && startsWith(val, "\"") && endsWith(val, "\""))
            val = substring(val, 1, lengthOf(val) - 1);
        uv = toUpperCase(val);
        nKeys = nKeys + 1;
        if (key == "DoTimelapse") { if (uv == "TRUE") ndTime = true; }
        else if (key == "NTimePoints") nT = parseInt(val);
        else if (key == "DoStage") { if (uv == "TRUE") ndStage = true; }
        else if (key == "NStagePositions") nS = parseInt(val);
        else if (key == "DoWave") { if (uv == "TRUE") ndWave = true; }
        else if (key == "NWavelengths") nW = parseInt(val);
        else if (key == "DoZSeries") { if (uv == "FALSE") doZ = false; }
        else if (key == "NZSteps") ndNZ = parseInt(val);
        else if (key == "ZStepSize") ndZStep = parseFloat(val);
        else if (startsWith(key, "WaveName")) {
            k = parseInt(substring(key, 8));
            if (!isNaN(k)) { wI = Array.concat(wI, k); wN = Array.concat(wN, val); }
        } else if (startsWith(key, "WaveDoZ")) {
            k = parseInt(substring(key, 7));
            zz = 1;
            if (uv == "FALSE") zz = 0;
            if (!isNaN(k)) { zI = Array.concat(zI, k); zV = Array.concat(zV, zz); }
        } else if (startsWith(key, "Stage")) {
            k = parseInt(substring(key, 5));
            if (!isNaN(k)) { sI = Array.concat(sI, k); sN = Array.concat(sN, val); }
        }
    }
    if (nKeys == 0) {
        openErr = "this .nd file holds no MetaMorph settings: " + p;
        return 0;
    }
    if (isNaN(nW) || nW < 1) nW = 1;
    // a wavelength name given beyond NWavelengths still counts
    for (q = 0; q < wI.length; q++) if (wI[q] > nW) nW = wI[q];
    if (isNaN(ndNZ) || ndNZ < 1 || doZ == false) ndNZ = 1;
    if (isNaN(ndZStep) || ndZStep < 0) ndZStep = 0;
    if (!isNaN(nT) && nT > 1) ndNT = nT;
    if (ndStage && !isNaN(nS) && nS > 1) ndNPos = nS;
    if (wI.length > 0) ndWave = true;
    ndWaves = newArray(nW);
    ndWaveZ = newArray(nW);
    for (q = 0; q < nW; q++) {
        ndWaves[q] = "w" + (q + 1);
        ndWaveZ[q] = 1;
        if (ndNZ == 1) ndWaveZ[q] = 0;
    }
    for (q = 0; q < wI.length; q++) if (wI[q] >= 1 && wI[q] <= nW) ndWaves[wI[q] - 1] = wN[q];
    for (q = 0; q < zI.length; q++) if (zI[q] >= 1 && zI[q] <= nW && ndNZ > 1) ndWaveZ[zI[q] - 1] = zV[q];
    ndStages = newArray(ndNPos);
    for (q = 0; q < ndNPos; q++) ndStages[q] = "position " + (q + 1);
    for (q = 0; q < sI.length; q++) if (sI[q] >= 1 && sI[q] <= ndNPos) ndStages[sI[q] - 1] = sN[q];
    return 1;
}

// The image file of wavelength w, position s, time point t (1-based) of the
// .nd p, "" if there is none. Matched loosely, from the end of the name: the
// time point and position fields when they are there, then <nd name>_w<w>
// followed by anything but a digit (the wavelength name as MetaMorph wrote
// it, its characters sometimes replaced). Case and extension (TIF, TIFF,
// STK) do not matter; thumbnails are skipped.
function ndFile(p, w, s, t) {
    dir = File.getParent(p) + File.separator;
    if (dir != ndDirKey) {
        ndDirLst = getFileList(dir);
        Array.sort(ndDirLst);
        ndDirKey = dir;
    }
    stem = stripExt(File.getName(p));
    base = toLowerCase(stem);
    nW = ndWaves.length;
    best = "";
    for (q = 0; q < ndDirLst.length; q++) {
        nm = ndDirLst[q];
        lc = toLowerCase(nm);
        ok = 0;
        if (endsWith(lc, ".tif") || endsWith(lc, ".tiff") || endsWith(lc, ".stk")) ok = 1;
        if (ok == 0) continue;
        if (indexOf(lc, "_thumb") >= 0) continue;
        if (!startsWith(lc, base)) continue;
        st = stripExt(lc);
        // time point, at the end; required when there are several
        tk = "_t" + t;
        if (endsWith(st, tk)) st = substring(st, 0, lengthOf(st) - lengthOf(tk));
        else if (ndNT > 1) continue;
        // position, then; required when there are several
        sk = "_s" + s;
        if (endsWith(st, sk)) st = substring(st, 0, lengthOf(st) - lengthOf(sk));
        else if (ndNPos > 1) continue;
        // wavelength. Its name may start with a digit ("_w1405" for w1 named
        // 405): w1 could only be mistaken for w10 to w19 with ten
        // wavelengths or more.
        wk = base + "_w" + w;
        if (startsWith(st, wk)) {
            nx = substring(st, lengthOf(wk));
            if (nW >= 10 && lengthOf(nx) > 0) {
                c0 = substring(nx, 0, 1);
                if (indexOf("0123456789", c0) >= 0) continue;
            }
        } else {
            // no wavelength field: only for a single wavelength
            if (nW > 1) continue;
            if (st != base) continue;
        }
        // several candidates (TIF and STK): the first in name order is kept
        if (best == "") best = dir + nm;
    }
    return best;
}

// Opens position s of the .nd p as one hyperstack, channels in the order of
// the wavelengths, with the Z planes and time points; leaves it in front,
// titled after the file and position. A wavelength without Z planes (a
// transmitted light image, say) is repeated over the Z planes of the others
// so that all channels line up. The calibration comes from the MetaMorph
// metadata of the first file when it has some (the .nd gives the Z step);
// each channel gets a LUT after its wavelength name. Returns 1, or 0 with
// the reason in openErr.
function ndOpen(p, s) {
    okN = ndParse(p);
    if (okN == 0) return 0;
    nW = ndWaves.length;
    if (nW > 7) {
        openErr = "more than 7 wavelengths in " + File.getName(p);
        return 0;
    }
    base = stripExt(File.getName(p));
    title = base;
    if (ndNPos > 1) title = base + "_s" + s;
    parts = newArray(0);          // titles, time point by time point, wavelength by wavelength
    nT = 0;
    zMax = 1;
    w0 = 0;
    h0 = 0;
    bdMax = 0;
    info0 = "";
    stopT = 0;
    for (t = 1; t <= ndNT; t++) {
        if (stopT == 1) continue;
        found = newArray(nW);
        allF = 1;
        for (w = 1; w <= nW; w++) {
            f = ndFile(p, w, s, t);
            found[w - 1] = f;
            if (f == "") allF = 0;
        }
        if (allF == 0) {
            if (t == 1) {
                miss = "";
                for (w = 1; w <= nW; w++) {
                    if (found[w - 1] != "") continue;
                    // (a number followed by an array element would be added to it)
                    expT = base + "_w" + w;
                    expT = expT + ndWaves[w - 1];
                    if (ndNPos > 1) expT = expT + "_s" + s;
                    if (ndNT > 1) expT = expT + "_t" + t;
                    miss = miss + " " + expT + ".TIF";
                }
                for (q = 0; q < parts.length; q++) { selectWindow(parts[q]); close(); }
                openErr = "image file(s) of " + File.getName(p) + " not found next to it:" + miss;
                return 0;
            }
            print("   NOTE: " + File.getName(p) + ": time point " + t +
                  " incomplete, read up to time point " + (t - 1));
            stopT = 1;
            continue;
        }
        for (w = 1; w <= nW; w++) {
            fpw = fwd(found[w - 1]);
            open(fpw);
            if (bitDepth() == 24) run("8-bit");
            getDimensions(fw, fh, fc, fz, ft);
            if (fc > 1 || ft > 1) {
                // a hyperstack in one file: its planes are taken as Z planes
                run("Hyperstack to Stack");
            }
            tt = "ND_" + t + "_" + w;
            rename(tt);
            if (t == 1 && w == 1) {
                w0 = getWidth();
                h0 = getHeight();
                info0 = getMetadata("Info");
            }
            if (getWidth() != w0 || getHeight() != h0) {
                close();
                for (q = 0; q < parts.length; q++) { selectWindow(parts[q]); close(); }
                openErr = "the images of " + File.getName(p) + " differ in size";
                return 0;
            }
            if (nSlices > zMax) zMax = nSlices;
            if (bitDepth() > bdMax) bdMax = bitDepth();
            parts = Array.concat(parts, tt);
        }
        nT = nT + 1;
    }
    // same depth for all (the deepest), same number of planes (the most)
    for (q = 0; q < parts.length; q++) {
        selectWindow(parts[q]);
        if (bitDepth() != bdMax) {
            if (bdMax == 16) run("16-bit");
            if (bdMax == 32) run("32-bit");
        }
        if (nSlices < zMax) {
            if (nSlices > 1) {
                for (q2 = 0; q2 < parts.length; q2++) { selectWindow(parts[q2]); close(); }
                openErr = "the wavelengths of " + File.getName(p) + " do not have the same Z planes";
                return 0;
            }
            // one plane: repeated over the Z planes of the others
            one = parts[q];
            rep = one + "_rep";
            if (bdMax == 8)  newImage(rep, "8-bit black", w0, h0, zMax);
            if (bdMax == 16) newImage(rep, "16-bit black", w0, h0, zMax);
            if (bdMax == 32) newImage(rep, "32-bit black", w0, h0, zMax);
            imageCalculator("Add stack", rep, one);
            selectWindow(one);
            close();
            selectWindow(rep);
            rename(one);
        }
    }
    // one stack, wavelength after wavelength, time point after time point
    if (parts.length == 1) {
        selectWindow(parts[0]);
        rename(title);
    } else {
        cc = "title=[" + title + "]";
        for (q = 0; q < parts.length; q++) cc = cc + " image" + (q + 1) + "=[" + parts[q] + "]";
        run("Concatenate...", cc);
        selectWindow(title);
        run("Stack to Hyperstack...", "order=xyzct channels=" + nW + " slices=" + zMax +
            " frames=" + nT + " display=Composite");
    }
    selectWindow(title);
    // calibration from the MetaMorph metadata, if any; else left as read
    um = mmCalib(info0);
    if (um > 0) {
        zs = ndZStep;
        if (zs <= 0) zs = um;
        setVoxelSize(um, um, zs, "micron");
    }
    // channel colours and names after the wavelengths
    lastChNames = Array.copy(ndWaves);
    for (w = 1; w <= nW; w++) {
        if (nW > 1) Stack.setChannel(w);
        lt = lutForName(ndWaves[w - 1]);
        run(lt);
        resetMinAndMax();
    }
    if (nW > 1) Stack.setChannel(1);
    setMetadata("Info", "MetaMorph .nd read by Count and Colocalise\n" +
                "Source: " + p + "\nPosition: " + s + " of " + ndNPos + "\n" + info0);
    return 1;
}

// Pixel size in um from MetaMorph metadata (the XML image description of
// its TIFF files: spatial-calibration-x / -units), 0 when absent or off.
function mmCalib(info) {
    i = indexOf(info, "spatial-calibration-x");
    if (i < 0) return 0;
    j = indexOf(info, "value=", i);
    if (j < 0 || j - i > 120) return 0;
    a = indexOf(info, "\"", j);
    b = indexOf(info, "\"", a + 1);
    if (a < 0 || b < 0) return 0;
    v = parseFloat(substring(info, a + 1, b));
    if (isNaN(v) || v <= 0) return 0;
    k = indexOf(info, "spatial-calibration-state");
    if (k >= 0) {
        j2 = indexOf(info, "value=", k);
        if (j2 >= 0 && j2 - k < 120) {
            st = toLowerCase(substring(info, j2, minOf(j2 + 14, lengthOf(info))));
            if (indexOf(st, "off") >= 0 || indexOf(st, "false") >= 0) return 0;
        }
    }
    f = 1;
    k = indexOf(info, "spatial-calibration-units");
    if (k >= 0) {
        j2 = indexOf(info, "value=", k);
        if (j2 >= 0 && j2 - k < 120) {
            a2 = indexOf(info, "\"", j2);
            b2 = indexOf(info, "\"", a2 + 1);
            if (a2 >= 0 && b2 > a2) {
                un = substring(info, a2 + 1, b2);
                f = umPerUnit(un);
            }
        }
    }
    if (f == 0) return 0;
    return v * f;
}

// LUT for a channel named after its fluorophore, filter or wavelength.
function lutForName(nm) {
    l = toLowerCase(nm);
    far = newArray("cy5", "647", "640", "633", "660", "far red", "far-red", "alexa 680", "a680");
    for (q = 0; q < far.length; q++) if (indexOf(l, far[q]) >= 0) return "Magenta";
    red = newArray("txred", "tx red", "tx-red", "texas", "tritc", "mcherry", "rfp", "cy3", "546",
                   "555", "561", "568", "594", "dsred", "tomato", "red");
    for (q = 0; q < red.length; q++) if (indexOf(l, red[q]) >= 0) return "Red";
    grn = newArray("gfp", "fitc", "488", "green", "yfp", "cy2", "alexa 488", "a488");
    for (q = 0; q < grn.length; q++) if (indexOf(l, grn[q]) >= 0) return "Green";
    blu = newArray("dapi", "hoechst", "405", "blue", "bfp", "350", "uv");
    for (q = 0; q < blu.length; q++) if (indexOf(l, blu[q]) >= 0) return "Blue";
    return "Grays";
}

// Text without the spaces at its ends.
function trimS(s) {
    while (startsWith(s, " ") || startsWith(s, "\t")) s = substring(s, 1);
    while (endsWith(s, " ") || endsWith(s, "\t")) s = substring(s, 0, lengthOf(s) - 1);
    return s;
}


// ======================================================================
//  FUNCTIONS - one TIFF per channel
// ======================================================================
// File names follow  <anything>--W00003--P00020--Z00000--T00000--<channel>.tif
// They are read from the END: the W, P and channel fields have a fixed
// place there, while the text in front is free and may contain anything.

// Walks <root>/<experiment>/<data>/ and groups the channel files of each
// acquisition, i.e. each experiment x coverslip (W) x field (P).
function scanTiffRoot(root) {
    exps = getFileList(root);
    Array.sort(exps);
    for (e = 0; e < exps.length; e++) {
        if (!File.isDirectory(root + exps[e])) continue;
        expName = cleanName(exps[e]);
        if (expName == "Results") continue;
        expDir = root + exps[e];
        dDir = findSub(expDir, dataSub);
        if (dDir == "") dDir = expDir;
        fl = getFileList(dDir);
        Array.sort(fl);
        for (f = 0; f < fl.length; f++) {
            nm = fl[f];
            lc = toLowerCase(nm);
            isTif = 0;
            if (endsWith(lc, ".tif"))  isTif = 1;
            if (endsWith(lc, ".tiff")) isTif = 1;
            if (isTif == 0) continue;
            stem = stripExt(nm);
            wPos = lastIndexOf(stem, "--W");
            if (wPos < 0) continue;
            wNum = digitsAt(stem, wPos + 3);
            pPos = indexOf(stem, "--P", wPos);
            if (pPos < 0) continue;
            pNum = digitsAt(stem, pPos + 3);
            if (lengthOf(wNum) == 0) continue;
            if (lengthOf(pNum) == 0) continue;
            role = chanRole(stem);
            if (role == "") continue;

            key = expName + "|" + wNum + "|" + pNum;
            idx = -1;
            for (k = tKey.length - 1; k >= 0; k--) {
                if (idx >= 0) continue;
                if (tKey[k] == key) idx = k;
            }
            if (idx < 0) {
                covN = parseInt(wNum);
                tKey   = Array.concat(tKey, key);
                tExp   = Array.concat(tExp, expName);
                tCov   = Array.concat(tCov, "Coverslip " + covN);
                tBase  = Array.concat(tBase, "W" + wNum + "_P" + pNum);
                tBlue  = Array.concat(tBlue, "");
                tGreen = Array.concat(tGreen, "");
                tRed   = Array.concat(tRed, "");
                tPole  = Array.concat(tPole, "");
                idx = tKey.length - 1;
            }
            full = dDir + nm;
            // a user function returning a string must be assigned to a
            // plain variable first: ImageJ would evaluate it as a number
            // if it were written straight into an array element
            if (role == "BLUE")  { jp = addPath(tBlue[idx],  full); tBlue[idx]  = jp; }
            if (role == "GREEN") { jp = addPath(tGreen[idx], full); tGreen[idx] = jp; }
            if (role == "RED")   { jp = addPath(tRed[idx],   full); tRed[idx]   = jp; }
            if (role == "POLE")  { jp = addPath(tPole[idx],  full); tPole[idx]  = jp; }
        }
    }
}

// Subfolder of dir whose name matches, ignoring case; "" if none.
function findSub(dir, name) {
    lst = getFileList(dir);
    want = toLowerCase(name);
    for (q = 0; q < lst.length; q++) {
        if (!File.isDirectory(dir + lst[q])) continue;
        cn = cleanName(lst[q]);
        if (toLowerCase(cn) == want) return dir + lst[q];
    }
    return "";
}

// The run of digits starting at pos.
function digitsAt(s, pos) {
    out = "";
    stop = 0;
    for (q = pos; q < lengthOf(s); q++) {
        if (stop == 1) continue;
        c = substring(s, q, q + 1);
        if (indexOf("0123456789", c) >= 0) out = out + c;
        else stop = 1;
    }
    return out;
}

// Channel of a file, from the part after its last "--".
function chanRole(stem) {
    li = lastIndexOf(stem, "--");
    if (li < 0) return "";
    chn = toLowerCase(substring(stem, li + 2));
    if (lengthOf(kwBlue) > 0)  { if (indexOf(chn, toLowerCase(kwBlue))  >= 0) return "BLUE"; }
    if (lengthOf(kwGreen) > 0) { if (indexOf(chn, toLowerCase(kwGreen)) >= 0) return "GREEN"; }
    if (lengthOf(kwRed) > 0)   { if (indexOf(chn, toLowerCase(kwRed))   >= 0) return "RED"; }
    // the pole marker, only when the run uses it
    if (poleNeedMk && lengthOf(kwPole) > 0) {
        if (indexOf(chn, toLowerCase(kwPole)) >= 0) return "POLE";
    }
    return "";
}

// Appends a path to a "|"-joined list. "|" cannot occur in a file path.
function addPath(joined, p) {
    if (joined == "") return p;
    return joined + "|" + p;
}

// First path found among the three channel lists.
function firstPath(a, b, c) {
    if (a != "") { pa = split(a, "|"); return pa[0]; }
    if (b != "") { pb = split(b, "|"); return pb[0]; }
    if (c != "") { pc = split(c, "|"); return pc[0]; }
    return "";
}

// Opens one channel of an acquisition under the title outTitle. Several
// files (several Z planes) are stacked and max-projected; a single file
// holding a Z stack is max-projected too. Returns 1 if the channel was
// opened, 0 if it has no file.
function openTiffChannel(joined, outTitle) {
    if (joined == "") return 0;
    ps = split(joined, "|");
    if (ps.length == 1) {
        fp = fwd(ps[0]);
        open(fp);
        rename(outTitle + "_raw");
    } else {
        for (q = 0; q < ps.length; q++) {
            fp = fwd(ps[q]);
            open(fp);
            rename(outTitle + "_z" + q);
        }
        cc = "title=[" + outTitle + "_raw]";
        for (q = 0; q < ps.length; q++)
            cc = cc + " image" + (q + 1) + "=[" + outTitle + "_z" + q + "]";
        run("Concatenate...", cc);
    }
    selectWindow(outTitle + "_raw");
    if (bitDepth() == 24) run("8-bit");
    if (nSlices > 1) {
        run("Z Project...", "projection=[Max Intensity]");
        rename(outTitle);
        selectWindow(outTitle + "_raw");
        close();
    } else {
        rename(outTitle);
    }
    selectWindow(outTitle);
    applyPixelSize();
    return 1;
}


// ======================================================================
//  FUNCTIONS - assembly and output
// ======================================================================

// Recursive scan: the last two folders above a file give the experiment
// and the condition. With a single level, the experiment is "-" and that
// level is the condition. The TIFF / STK files a MetaMorph .nd of the same
// folder points to are its own planes, not images of their own: they are
// skipped when TIFF is among the file types.
function scanDir(dir, lvl1, lvl2) {
    lst = getFileList(dir);
    Array.sort(lst);
    ndB = ndBases(lst);
    for (q = 0; q < lst.length; q++) {
        nm = lst[q];
        full = dir + nm;
        if (File.isDirectory(full)) {
            cn = cleanName(nm);
            if (cn == "Results") continue;
            if (cn == "Validation") continue;
            if (lvl1 == "")      scanDir(full, cn, "");
            else if (lvl2 == "") scanDir(full, lvl1, cn);
            else                 scanDir(full, lvl2, cn);
        } else {
            ok = isImageFile(nm);
            if (ok == 0) continue;
            own = ndOwned(nm, ndB);
            if (own == 1) continue;
            if (lvl2 == "") { mm = "-";  cc = lvl1; }
            else            { mm = lvl1; cc = lvl2; }
            if (cc == "") cc = "-";
            mAcc = Array.concat(mAcc, mm);
            cAcc = Array.concat(cAcc, cc);
            pAcc = Array.concat(pAcc, full);
        }
    }
}

// Lower-case names, without extension, of the .nd files of a folder listing.
function ndBases(lst) {
    out = newArray(0);
    for (q = 0; q < lst.length; q++) {
        lc = toLowerCase(lst[q]);
        if (endsWith(lc, ".nd")) out = Array.concat(out, substring(lc, 0, lengthOf(lc) - 3));
    }
    return out;
}

// 1 when file nm is a TIFF or STK plane file of one of the .nd files
// (named <nd name>_...), 0 otherwise.
function ndOwned(nm, ndB) {
    lc = toLowerCase(nm);
    isT = 0;
    if (endsWith(lc, ".tif") || endsWith(lc, ".tiff") || endsWith(lc, ".stk")) isT = 1;
    if (isT == 0) return 0;
    for (q = 0; q < ndB.length; q++) {
        if (startsWith(lc, ndB[q] + "_")) return 1;
    }
    return 0;
}

// Channel colour read from the LUT of each channel: from the CZI metadata
// through Bio-Formats, or from the wavelength names of a .nd. Grey = DIC;
// otherwise the dominant component gives the colour. Composite LUTs (cyan,
// magenta, yellow) go to the colour still free.
function detectChannels(t) {
    selectWindow(t);
    getDimensions(dw, dh, dnc, dns, dnf);
    cols = newArray(dnc);
    for (c = 1; c <= dnc; c++) {
        if (dnc > 1) Stack.setChannel(c);
        cw = lutColour();
        cols[c - 1] = cw;
    }
    if (dnc > 1) Stack.setChannel(1);
    okS = rolesFromCols(cols);
}

// Channels of the roles in one file, into dcBLUE / dcGREEN / dcRED / dcDIC:
// the channel with the colour each role has in the first file (roleCols).
// The pole marker channel (dcSkip) is never one of them. Returns 1.
function rolesFromCols(cols) {
    dcDIC = 0; dcRED = 0; dcGREEN = 0; dcBLUE = 0;
    for (c = 1; c <= cols.length; c++) {
        if (c == dcSkip) continue;
        cw = cols[c - 1];
        if (cw == roleCols[0] && dcBLUE == 0) dcBLUE = c;
        else if (cw == roleCols[1] && dcGREEN == 0) dcGREEN = c;
        else if (cw == roleCols[2] && dcRED == 0) dcRED = c;
        else if (cw == roleCols[3] && dcDIC == 0) dcDIC = c;
    }
    return 1;
}

// Colour of the LUT of the image in front (its current channel), in words.
function lutColour() {
    getLut(lr, lg, lb);
    last = lr.length - 1;
    rr = lr[last];
    gg = lg[last];
    bb = lb[last];
    if (rr == gg && gg == bb) return "grey";
    if (bb > rr && bb > gg) return "blue";
    if (gg > rr && gg > bb) return "green";
    if (rr > gg && rr > bb) return "red";
    if (gg == bb && gg > rr) return "cyan";
    if (rr == bb && rr > gg) return "magenta";
    if (rr == gg && rr > bb) return "yellow";
    return "other";
}

// Opens one file, the image ending up in front. Returns 1, or 0 with the
// reason in openErr (only the .nd reader can tell: a file Bio-Formats or
// ImageJ cannot read stops the macro with their own message).
//   auto      .nd read here (ndOpen), TIFF with ImageJ, the rest with the
//             Bio-Formats Importer
//   importer  Bio-Formats Importer for every file
//   ext       Bio-Formats macro extensions (no option string to reject)
//   imagej    ImageJ for every file, .nd read here
function openFile(p, idx) {
    p = fwd(p);
    openErr = "";
    lastChNames = newArray(0);
    lc = toLowerCase(p);
    isND = 0;
    if (endsWith(lc, ".nd")) isND = 1;
    isTif = 0;
    if (endsWith(lc, ".tif") || endsWith(lc, ".tiff")) isTif = 1;
    if (indexOf(lc, ".ome.tif") >= 0) isTif = 0;
    if (isND == 1 && (openMethod == "auto" || openMethod == "imagej")) {
        pos = 1;
        if (serList.length > idx) pos = serList[idx];
        okN = ndOpen(p, pos);
        return okN;
    }
    if (openMethod == "imagej" || (openMethod == "auto" && isTif == 1)) {
        open(p);
        okRgb = rgbChannels();
        return 1;
    }
    if (openMethod == "ext") {
        // simplest Bio-Formats route: no option string at all, so nothing
        // in it can be rejected
        run("Bio-Formats Macro Extensions");
        Ext.setId(p);
        Ext.openImagePlus(p);
        // (it opens the first series of a file: see the Log)
        if (serTot.length > idx) {
            if (serTot[idx] > 1 && serList[idx] > 1)
                print("   NOTE: the Bio-Formats simple opener reads the first series only; " +
                      "choose the Importer to analyse series " + serList[idx]);
        }
        okRgb = rgbChannels();
        return 1;
    }
    // "Composite": each channel in the colour its file gives it (the
    // "Default" colour mode of Bio-Formats would give red, green, blue in
    // channel order, whatever the file says)
    o = importOptions(p, idx, "Composite");
    print("   opening: " + o);
    run("Bio-Formats Importer", o);
    okRgb = rgbChannels();
    return 1;
}

// An RGB image (exported TIFF, slide scanner) in front becomes three
// channels - red, green and blue, each with its colour - so that its
// channels can be told apart like those of any other file. Returns 1.
function rgbChannels() {
    if (nImages == 0) return 0;
    if (bitDepth() != 24) return 0;
    run("Make Composite");
    // no "Red / Green / Blue" slice labels: a merge of three of these
    // channels (QC panels) would be taken for an RGB stack, and shown with
    // its raw values instead of the display range of each channel
    if (nSlices == 3) {
        for (c = 1; c <= 3; c++) {
            Stack.setChannel(c);
            setMetadata("Label", "");
        }
        Stack.setChannel(1);
    }
    return 1;
}

// Bio-Formats import options. The series_ option is added ONLY when the
// file really holds several series: passing it to a single-series file
// makes the reader fail with "Problem during input".
// Windows paths come back with backslashes, and a backslash is an escape
// character in the macro language: the path reaching Bio-Formats is then
// corrupted and the reader answers "problem during import". Forward
// slashes work on every platform, so the path is normalised first.
function fwd(p) {
    return replace(p, "\\", "/");
}

function importOptions(p, idx, mode) {
    p = fwd(p);
    // This is exactly the command Fiji's macro recorder produces when the
    // file is opened by hand, and it is what works. Do not add options to
    // it: "series_" in particular makes MetaMorph .nd files fail with
    // "Problem during input", even when the file holds a single series.
    o = "open=[" + p + "] autoscale color_mode=" + mode +
        " rois_import=[ROI manager] view=Hyperstack stack_order=XYCZT";
    // The series is named only when the file really holds several and the
    // user asked for them to be split.
    if (serTot.length > idx) {
        if (serTot[idx] > 1) o = o + " series_" + serList[idx];
    }
    return o;
}

// Is this file one of the formats selected in the dialog?
function isImageFile(name) {
    lc = toLowerCase(name);
    for (q = 0; q < extArr.length; q++) {
        if (endsWith(lc, extArr[q])) return 1;
    }
    return 0;
}

// The file types of the first dialog ("czi nd", "CZI, .nd", ...) as a list
// of extensions, in extArr: ".czi", ".nd". TIF also takes TIFF. Returns how
// many.
function parseExts(txt) {
    t = toLowerCase(txt);
    t = replace(t, ",", " ");
    t = replace(t, ";", " ");
    parts = split(t, " ");
    extArr = newArray(0);
    for (q = 0; q < parts.length; q++) {
        e = parts[q];
        while (startsWith(e, "*")) e = substring(e, 1);
        while (startsWith(e, ".")) e = substring(e, 1);
        if (lengthOf(e) == 0) continue;
        known = 0;
        for (r = 0; r < extArr.length; r++) if (extArr[r] == "." + e) known = 1;
        if (known == 0) extArr = Array.concat(extArr, "." + e);
        if (e == "tif") {
            known = 0;
            for (r = 0; r < extArr.length; r++) if (extArr[r] == ".tiff") known = 1;
            if (known == 0) extArr = Array.concat(extArr, ".tiff");
        }
    }
    return extArr.length;
}

// File name without its extension.
function stripExt(name) {
    d = lastIndexOf(name, ".");
    if (d > 0) return substring(name, 0, d);
    return name;
}

// Formats currently accepted, for messages.
function fmtText() {
    if (tiffMode) return "TIFF (" + kwBlue + " / " + kwGreen + " / " + kwRed + ")";
    s = "";
    for (q = 0; q < extArr.length; q++) {
        if (q > 0) s = s + " / ";
        s = s + extArr[q];
    }
    return s;
}

function isControlName(name) {
    lc = toLowerCase(name);
    if (indexOf(lc, "control") >= 0) return 1;
    if (indexOf(lc, "controle") >= 0) return 1;
    if (indexOf(lc, "ctrl") >= 0) return 1;
    if (indexOf(lc, "temoin") >= 0) return 1;
    return 0;
}

// Is this experiment x condition excluded from the calculations?
function isExcluded(manip, cond) {
    for (q = 0; q < exclList.length; q++) {
        if (exclList[q] == cond) return 1;
        if (exclList[q] == manip + "/" + cond) return 1;
    }
    return 0;
}

// Do two conditions belong to the same group?
// Two controls: yes. Otherwise, same leading number in the name: yes.
function sameGroup(a, b) {
    ca = isControlName(a);
    cb = isControlName(b);
    if (ca == 1 && cb == 1) return 1;
    if (ca != cb) return 0;
    na = firstNumber(a);
    nb = firstNumber(b);
    if (na < 0) return 0;
    if (nb < 0) return 0;
    if (na == nb) return 1;
    return 0;
}

// Group label: longest common prefix of its members, which gives
// "Control" for Control 1 / Control 2, and "0.25 uM PCIP" for
// 0.25 uM PCIP / 0.25 uM PCIP_slide 1 / _slide 2.
function groupLabel(g) {
    lab = "";
    started = 0;
    for (c = 0; c < condsAll.length; c++) {
        if (gOf[c] != g) continue;
        if (started == 0) {
            lab = condsAll[c];
            started = 1;
        } else {
            cp = commonPrefix(lab, condsAll[c]);
            lab = cp;
        }
    }
    te = trimEnd(lab);
    lab = te;
    if (lengthOf(lab) < 2) {
        for (c = 0; c < condsAll.length; c++) {
            if (gOf[c] == g) return condsAll[c];
        }
    }
    return lab;
}

function commonPrefix(a, b) {
    n = minOf(lengthOf(a), lengthOf(b));
    k = 0;
    stop = 0;
    for (q = 0; q < n; q++) {
        if (stop == 1) continue;
        if (substring(a, q, q + 1) == substring(b, q, q + 1)) k = q + 1;
        else stop = 1;
    }
    return substring(a, 0, k);
}

function trimEnd(s) {
    while (lengthOf(s) > 0) {
        c2 = substring(s, lengthOf(s) - 1, lengthOf(s));
        if (c2 == " " || c2 == "_" || c2 == "-") s = substring(s, 0, lengthOf(s) - 1);
        else return s;
    }
    return s;
}

// Sort key: controls first, then the leading number (0.05, 0.1, 1, 10)
function condSortKey(name) {
    num = firstNumber(name);
    if (isControlName(name) == 1) {
        if (num < 0) return -1000000;
        return -1000000 + num;
    }
    if (num < 0) return 1000000;
    return num;
}

// First number contained in a string, -1 if none.
function firstNumber(s) {
    digits = "0123456789";
    buf = "";
    started = 0;
    stop = 0;
    for (i2 = 0; i2 < lengthOf(s); i2++) {
        if (stop == 1) continue;
        c = substring(s, i2, i2 + 1);
        if (indexOf(digits, c) >= 0) { buf = buf + c; started = 1; }
        else if (started == 1 && (c == "." || c == ",")) { buf = buf + "."; }
        else if (started == 1) stop = 1;
    }
    if (buf == "") return -1;
    if (endsWith(buf, ".")) buf = substring(buf, 0, lengthOf(buf) - 1);
    return parseFloat(buf);
}

// Number as text, 2 decimals, DOT separator. d2s follows the system
// locale and would emit a comma, breaking both the CSV and the XML.
function num2(v) {
    return replace(d2s(v, 2), ",", ".");
}

// A name as a CSV field: quoted when it holds a comma, a quote or a line
// break ("0,5 uM PCIP" would otherwise shift every column after it).
function csvQ(s) {
    s = "" + s;
    if (indexOf(s, ",") < 0 && indexOf(s, "\"") < 0 && indexOf(s, "\n") < 0) return s;
    return "\"" + replace(s, "\"", "\"\"") + "\"";
}

// A name as XML text (workbook): & < > escaped, the line breaks the
// workbook writes itself (&#10;) kept.
function xmlEsc(s) {
    s = "" + s;
    s = replace(s, "&", "&amp;");
    s = replace(s, "&amp;#10;", "&#10;");
    s = replace(s, "<", "&lt;");
    s = replace(s, ">", "&gt;");
    return s;
}

// Whether image t was counted, for the CSV files (no comma in it).
function usedTxt(t) {
    if (summaryUse[t] == 1) return "yes";
    if (summaryUse[t] == 2) return "yes - " + summaryWhy[t] + " but needed for the target";
    return "no - " + summaryWhy[t];
}

function cleanName(s) {
    while (endsWith(s, "/") || endsWith(s, "\\"))
        s = substring(s, 0, lengthOf(s) - 1);
    return File.getName(s);
}


// ======================================================================
//  WORKBOOK
// ======================================================================
// Layout:
//   top     summary, one stacked block per experiment
//   bottom  per-image detail, experiments side by side
//
// The detail is built first into a separate string, which yields the row
// numbers of the TOTAL lines; the summary then points at them by formula,
// so every value stays live if a count is edited.
// The measure column (measureUse, see setMeasure), from the columns of its
// row: host cells RC[-4], parasites RC[-3], double-labelled RC[-2],
// single-labelled RC[-1].
function measureFormula() {
    if (measureUse == 3) return "=IF(RC[-3]=0,0,RC[-2]/RC[-3]*100)";
    if (measureUse == 4) return "=RC[-4]";
    col = "RC[-1]";
    if (measureUse == 1) col = "RC[-3]";
    if (measureUse == 2) col = "RC[-2]";
    if (normalizeToCells) return "=IF(RC[-4]=0,0," + col + "/RC[-4]*100)";
    return "=" + col;
}

function writeWorkbook(outPath) {

    mf = measureFormula();
    // a class not counted keeps its column, at 0, and says so
    hdC = "Host cells";
    if (countNuclei == false) hdC = "Host cells (not counted)";
    hdP = "Parasites";
    hdS = "Single-labelled";
    if (countGreen == false) {
        hdP = "Parasites (not counted)";
        hdS = "Single-labelled (not counted)";
    }
    hdD = "Double-labelled";
    if (countDouble == false) hdD = "Double-labelled (not counted)";

    nMw = manipList.length;
    nCw = condsAll.length;
    BW  = 8;          // 7 columns per experiment + 1 separator column

    // ---------- summary geometry ----------
    // Stacked blocks, one experiment after another, merged left column.
    // Conditions are walked group by group (same concentration). As soon as
    // a group holds two or more conditions, a pooled row sums them just
    // below. For the controls that pooled row is the 100 % reference -
    // otherwise the reference control would be picked arbitrarily.
    sRowOf   = newArray(nMw * nCw);
    grpRowOf = newArray(nMw * gCount);
    gFirst   = newArray(nMw * gCount);
    gLast    = newArray(nMw * gCount);
    ctrlS    = newArray(nMw);
    nPresM   = newArray(nMw);

    sr = 1;                                   // row 1 = headers
    for (m = 0; m < nMw; m++) {
        tot = 0;
        for (g = 0; g < gCount; g++) {
            k2 = 0;
            isCtrlG = 0;
            for (c = 0; c < nCw; c++) {
                if (gOf[c] != g) continue;
                if (aggN[m * nCw + c] == 0) continue;
                if (isControlName(condsAll[c]) == 1) isCtrlG = 1;
                sr = sr + 1;
                sRowOf[m * nCw + c] = sr;
                if (k2 == 0) gFirst[m * gCount + g] = sr;
                gLast[m * gCount + g] = sr;
                k2 = k2 + 1;
            }
            if (k2 == 0) continue;
            tot = tot + k2;
            if (k2 > 1) {
                sr = sr + 1;
                grpRowOf[m * gCount + g] = sr;
                tot = tot + 1;
            } else {
                grpRowOf[m * gCount + g] = 0;
            }
            if (isCtrlG == 1) {
                if (k2 > 1) ctrlS[m] = grpRowOf[m * gCount + g];
                else        ctrlS[m] = gLast[m * gCount + g];
            }
        }
        nPresM[m] = tot;
        sr = sr + 1;                          // separator between experiments
    }
    sr = sr + 1;                              // blank row
    summaryHeight = sr;

    // ---------- detail geometry ----------
    // Side-by-side blocks: experiment m uses columns m*8+1 to m*8+7. The
    // block of a given condition starts on the SAME row in every experiment
    // so they can be compared horizontally. If one experiment has fewer
    // images its extra rows stay empty; the TOTAL sum ignores them.
    maxK   = newArray(nCw);
    totRel = newArray(nCw);
    for (c = 0; c < nCw; c++) {
        mk = 0;
        for (m = 0; m < nMw; m++) {
            if (aggN[m * nCw + c] > mk) mk = aggN[m * nCw + c];
        }
        maxK[c] = mk;
    }

    rr = 0;
    if (singleManip == 0) rr = rr + 1;        // experiment title row
    rr = rr + 1;                              // header row
    for (c = 0; c < nCw; c++) {
        if (maxK[c] == 0) continue;
        rr = rr + maxK[c];
        rr = rr + 1;
        totRel[c] = rr;                       // TOTAL row, relative
        rr = rr + 1;                          // separator
    }

    // ---------- write ----------
    nCols = nMw * BW;
    if (nCols < 8) nCols = 8;
    x = xmlHead(nCols);

    // ---------- SUMMARY ----------
    x = x + "<Row ss:Height=\"34\">" +
        hCell("Experiment") + hCell("Condition") + hCell(hdC) + hCell(hdP) +
        hCell(hdD) + hCell(hdS) +
        hCell(measureLabel);
    if (hasControl) x = x + hCell("% of control");
    x = x + "</Row>\n";

    for (m = 0; m < nMw; m++) {

        nPres = nPresM[m];
        if (nPres == 0) {
            x = x + "<Row></Row>\n";
            continue;
        }

        labM = manipList[m];
        if (singleManip == 0) labM = "EXP " + (m + 1) + "&#10;" + manipList[m];

        first = 1;
        baseD = m * BW + 1;               // column of the experiment's DETAIL block

        for (g = 0; g < gCount; g++) {

            k2 = 0;
            for (c = 0; c < nCw; c++) {
                if (gOf[c] != g) continue;
                if (aggN[m * nCw + c] == 0) continue;
                tr = summaryHeight + totRel[c];
                rowStr = sumRowRef(first, labM, nPres - 1, condsAll[c],
                                   baseD, tr, ctrlS[m]);
                x = x + rowStr;
                first = 0;
                k2 = k2 + 1;
            }

            if (k2 > 1) {
                glab = groupLabel(g);
                gl = glab + " (pooled)";
                rowStr = sumRowGroup(first, labM, nPres - 1, gl,
                                     gFirst[m * gCount + g],
                                     gLast[m * gCount + g], ctrlS[m]);
                x = x + rowStr;
                first = 0;
            }
        }

        x = x + "<Row ss:Height=\"10\"></Row>\n";
    }
    x = x + "<Row></Row>\n";

    // ---------- DETAIL, experiments side by side ----------
    if (singleManip == 0) {
        x = x + "<Row ss:Height=\"30\">";
        for (m = 0; m < nMw; m++) {
            x = x + "<Cell ss:Index=\"" + (m * BW + 1) + "\" ss:StyleID=\"hm\"" +
                " ss:MergeAcross=\"6\"><Data ss:Type=\"String\">" +
                "EXP " + (m + 1) + "  -  " + xmlEsc(manipList[m]) + "</Data></Cell>";
        }
        x = x + "</Row>\n";
    }

    x = x + "<Row ss:Height=\"34\">";
    for (m = 0; m < nMw; m++) {
        x = x + "<Cell ss:Index=\"" + (m * BW + 1) + "\" ss:StyleID=\"h\">" +
            "<Data ss:Type=\"String\">Condition</Data></Cell>" +
            hCell("Image") + hCell(hdC) + hCell(hdP) +
            hCell(hdD) + hCell(hdS) +
            hCell(measureLabel);
    }
    x = x + "</Row>\n";

    for (c = 0; c < nCw; c++) {
        if (maxK[c] == 0) continue;

        // --- image rows ---
        for (j = 0; j < maxK[c]; j++) {
            x = x + "<Row>";
            for (m = 0; m < nMw; m++) {
                baseD = m * BW + 1;
                kk = aggN[m * nCw + c];

                if (kk == 0) {
                    x = x + "<Cell ss:Index=\"" + baseD + "\" ss:StyleID=\"na\"></Cell>";
                    for (z = 0; z < 6; z++) x = x + "<Cell ss:StyleID=\"na\"></Cell>";
                } else if (j < kk) {
                    t = ordIdx[ordStart[m * nCw + c] + j];
                    // (*): under the minimum, counted for the stop target
                    nmT = summaryNames[t];
                    if (summaryUse[t] == 2) nmT = nmT + " (*)";
                    if (j == 0) {
                        lab = condsAll[c];
                        if (singleManip == 0) lab = lab + "&#10;EXP " + (m + 1);
                        // MergeDown = maxK: the label spans the image rows
                        // AND the TOTAL row, even when this experiment has
                        // fewer images than the largest one.
                        x = x + "<Cell ss:Index=\"" + baseD + "\" ss:StyleID=\"t\"" +
                            " ss:MergeDown=\"" + maxK[c] + "\"><Data ss:Type=\"String\">" +
                            xmlEsc(lab) + "</Data></Cell>" + sCell(nmT);
                    } else {
                        x = x + sCellIdx(nmT, baseD + 1);
                    }
                    x = x + nCell(summaryCells[t]) + nCell(summaryGreen[t]) +
                        nCell(summaryColoc[t]) +
                        fCell("c", "=RC[-2]-RC[-1]") +
                        fCell("p", mf);
                } else {
                    // this experiment has fewer images: padding rows
                    x = x + "<Cell ss:Index=\"" + (baseD + 1) + "\" ss:StyleID=\"c\"></Cell>";
                    for (z = 0; z < 5; z++) x = x + "<Cell ss:StyleID=\"c\"></Cell>";
                }
            }
            x = x + "</Row>\n";
        }

        // --- TOTAL row ---
        x = x + "<Row ss:Height=\"28\">";
        for (m = 0; m < nMw; m++) {
            baseD = m * BW + 1;
            kk = aggN[m * nCw + c];
            if (kk == 0) {
                x = x + "<Cell ss:Index=\"" + baseD + "\" ss:StyleID=\"na\"></Cell>";
                for (z = 0; z < 6; z++) x = x + "<Cell ss:StyleID=\"na\"></Cell>";
            } else {
                x = x + tCellIdx("TOTAL", baseD + 1) +
                    fCell("t", "=SUM(R[-" + maxK[c] + "]C:R[-1]C)") +
                    fCell("t", "=SUM(R[-" + maxK[c] + "]C:R[-1]C)") +
                    fCell("t", "=SUM(R[-" + maxK[c] + "]C:R[-1]C)") +
                    fCell("t", "=RC[-2]-RC[-1]") +
                    fCell("tp", mf);
            }
        }
        x = x + "</Row>\n";

        x = x + "<Row ss:Height=\"8\"></Row>\n";
    }

    // ---------- images under the minimum per image ----------
    // Those counted for the stop target carry (*) above; those left out are
    // listed here, outside every total.
    nOut  = 0;
    nUsed = 0;
    for (t = 0; t < summaryUse.length; t++) {
        if (summaryUse[t] == 0) nOut  = nOut + 1;
        if (summaryUse[t] == 2) nUsed = nUsed + 1;
    }
    if (nUsed > 0)
        x = x + "<Row><Cell ss:MergeAcross=\"6\"><Data ss:Type=\"String\">(*) " + lowRule +
            ": counted only because its condition needed it to reach the target" +
            "</Data></Cell></Row>\n";
    if (nOut > 0) {
        x = x + "<Row></Row>\n";
        x = x + "<Row ss:Height=\"30\"><Cell ss:StyleID=\"hm\" ss:MergeAcross=\"6\">" +
            "<Data ss:Type=\"String\">IMAGES LEFT OUT OF THE COUNTS  -  " + lowRule +
            "</Data></Cell></Row>\n";
        x = x + "<Row ss:Height=\"34\">" + hCell("Experiment") + hCell("Condition") +
            hCell("Image") + hCell(hdC) + hCell(hdP) +
            hCell(hdD) + hCell("Why") + "</Row>\n";
        for (t = 0; t < summaryUse.length; t++) {
            if (summaryUse[t] != 0) continue;
            x = x + "<Row>" + sCell(summaryManip[t]) + sCell(summaryCond[t]) +
                sCell(summaryNames[t]) + nCell(summaryCells[t]) + nCell(summaryGreen[t]) +
                nCell(summaryColoc[t]) + sCell(summaryWhy[t]) + "</Row>\n";
        }
    }

    x = x + "</Table>\n</Worksheet>\n";
    // the poles, on a sheet of their own
    if (dblPolesUsed && dblMode != 2) {
        ps = polesSheet();
        x = x + ps;
    }
    x = x + "</Workbook>\n";
    File.saveString(x, outPath);
}

// Second worksheet, with the pole option: per image, then per experiment x
// condition (TOTAL), the double-labelled parasites whose marker is at the
// basal pole, at the apical pole, at an end of a parasite that could not
// be oriented (indeterminable), their sum, and those whose marker is at no
// pole; as counts and as % of the double-labelled. Live formulas, as on
// the first sheet. The images are those counted there.
function polesSheet() {
    nMw = manipList.length;
    nCw = condsAll.length;
    x = "<Worksheet ss:Name=\"Poles\">\n<Table>\n";
    wd = newArray(135, 130, 130, 95, 80, 80, 100, 75, 85, 80, 80, 100, 75, 100);
    for (q = 0; q < wd.length; q++) x = x + "<Column ss:Width=\"" + wd[q] + "\"/>";
    x = x + "\n<Row ss:Height=\"46\">" + hCell("Experiment") + hCell("Condition") +
        hCell("Image") + hCell("Double-labelled") + hCell("Marker at the basal pole") +
        hCell("Marker at the apical pole") + hCell("Pole indeterminable") + hCell("Sum") +
        hCell("Not at a pole") + hCell("% basal") + hCell("% apical") +
        hCell("% indeterminable") + hCell("% sum") + hCell("Parasite objects oriented") +
        "</Row>\n";
    fS  = "=RC5+RC6+RC7";
    fPb = "=IF(RC4=0,0,RC5/RC4*100)";
    fPa = "=IF(RC4=0,0,RC6/RC4*100)";
    fPi = "=IF(RC4=0,0,RC7/RC4*100)";
    fPs = "=IF(RC4=0,0,RC8/RC4*100)";
    for (m = 0; m < nMw; m++) {
        for (c = 0; c < nCw; c++) {
            k = m * nCw + c;
            kk = aggN[k];
            if (kk == 0) continue;
            for (j = 0; j < kk; j++) {
                t = ordIdx[ordStart[k] + j];
                nmT = summaryNames[t];
                if (summaryUse[t] == 2) nmT = nmT + " (*)";
                x = x + "<Row>" + sCell(manipList[m]) + sCell(condsAll[c]) + sCell(nmT) +
                    nCell(summaryColoc[t]) + nCell(summaryBas[t]) + nCell(summaryApi[t]) +
                    nCell(summaryInd[t]) + fCell("c", fS) + nCell(summaryMid[t]) +
                    fCell("p", fPb) + fCell("p", fPa) + fCell("p", fPi) + fCell("p", fPs) +
                    nCell(summaryOri[t]) + "</Row>\n";
            }
            fT = "=SUM(R[-" + kk + "]C:R[-1]C)";
            x = x + "<Row ss:Height=\"26\">" + tCell(manipList[m]) + tCell(condsAll[c]) +
                tCell("TOTAL") + fCell("t", fT) + fCell("t", fT) + fCell("t", fT) +
                fCell("t", fT) + fCell("t", fS) + fCell("t", fT) + fCell("tp", fPb) +
                fCell("tp", fPa) + fCell("tp", fPi) + fCell("tp", fPs) + fCell("t", fT) +
                "</Row>\n";
            x = x + "<Row ss:Height=\"8\"></Row>\n";
        }
    }
    x = x + "</Table>\n</Worksheet>\n";
    return x;
}

// A summary row pointing at the matching TOTAL row of the detail.
function sumRowRef(isFirst, labM, mergeN, condLabel, baseD, tr, ctrlRow) {
    mf = measureFormula();
    r = "<Row ss:Height=\"26\">";
    if (isFirst == 1) r = r + mCell(labM, mergeN) + tCell(condLabel);
    else              r = r + tCellIdx(condLabel, 2);
    r = r + fCell("t",  "=R" + tr + "C" + (baseD + 2)) +
            fCell("t",  "=R" + tr + "C" + (baseD + 3)) +
            fCell("t",  "=R" + tr + "C" + (baseD + 4)) +
            fCell("t",  "=RC[-2]-RC[-1]") +
            fCell("tp", mf);
    // no control anywhere: the column does not exist at all
    if (hasControl) {
        if (ctrlRow > 0)
            r = r + fCell("tp", "=IF(R" + ctrlRow + "C7=0,0,RC[-1]/R" + ctrlRow + "C7*100)");
        else
            r = r + "<Cell ss:StyleID=\"na\"></Cell>";
    }
    return r + "</Row>\n";
}

// Pooled row: sum of the group member rows just above. Live formulas,
// like the rest of the table.
function sumRowGroup(isFirst, labM, mergeN, grpLab, r1, r2, ctrlRow) {
    mf = measureFormula();
    r = "<Row ss:Height=\"26\">";
    if (isFirst == 1) r = r + mCell(labM, mergeN) + tCell(grpLab);
    else              r = r + tCellIdx(grpLab, 2);
    r = r + fCell("t",  "=SUM(R" + r1 + "C3:R" + r2 + "C3)") +
            fCell("t",  "=SUM(R" + r1 + "C4:R" + r2 + "C4)") +
            fCell("t",  "=SUM(R" + r1 + "C5:R" + r2 + "C5)") +
            fCell("t",  "=RC[-2]-RC[-1]") +
            fCell("tp", mf);
    // same rule as a condition row; without the guard, an experiment with
    // no control produced a reference to row 0 (R0C7), an error in Excel
    if (hasControl) {
        if (ctrlRow > 0)
            r = r + fCell("tp", "=IF(R" + ctrlRow + "C7=0,0,RC[-1]/R" + ctrlRow + "C7*100)");
        else
            r = r + "<Cell ss:StyleID=\"na\"></Cell>";
    }
    return r + "</Row>\n";
}

function xmlHead(nCols) {
    b1 = "<Borders>" +
        "<Border ss:Position=\"Bottom\" ss:LineStyle=\"Continuous\" ss:Weight=\"1\"/>" +
        "<Border ss:Position=\"Left\" ss:LineStyle=\"Continuous\" ss:Weight=\"1\"/>" +
        "<Border ss:Position=\"Right\" ss:LineStyle=\"Continuous\" ss:Weight=\"1\"/>" +
        "<Border ss:Position=\"Top\" ss:LineStyle=\"Continuous\" ss:Weight=\"1\"/></Borders>";
    b2 = "<Borders>" +
        "<Border ss:Position=\"Bottom\" ss:LineStyle=\"Continuous\" ss:Weight=\"2\"/>" +
        "<Border ss:Position=\"Left\" ss:LineStyle=\"Continuous\" ss:Weight=\"2\"/>" +
        "<Border ss:Position=\"Right\" ss:LineStyle=\"Continuous\" ss:Weight=\"2\"/>" +
        "<Border ss:Position=\"Top\" ss:LineStyle=\"Continuous\" ss:Weight=\"2\"/></Borders>";
    al = "<Alignment ss:Horizontal=\"Center\" ss:Vertical=\"Center\" ss:WrapText=\"1\"/>";

    h = "<?xml version=\"1.0\"?>\n<?mso-application progid=\"Excel.Sheet\"?>\n" +
        "<Workbook xmlns=\"urn:schemas-microsoft-com:office:spreadsheet\"" +
        " xmlns:ss=\"urn:schemas-microsoft-com:office:spreadsheet\">\n<Styles>\n" +
        "<Style ss:ID=\"h\">"  + al + b1 + "<Font ss:Bold=\"1\"/>" +
            "<Interior ss:Color=\"#E8E8E8\" ss:Pattern=\"Solid\"/></Style>\n" +
        "<Style ss:ID=\"hm\">" + al + b2 + "<Font ss:Bold=\"1\" ss:Size=\"12\"/>" +
            "<Interior ss:Color=\"#C6D9F0\" ss:Pattern=\"Solid\"/></Style>\n" +
        "<Style ss:ID=\"c\">"  + al + b1 + "</Style>\n" +
        "<Style ss:ID=\"p\">"  + al + b1 + "<NumberFormat ss:Format=\"0.00\"/></Style>\n" +
        "<Style ss:ID=\"na\">" + al + b1 +
            "<Interior ss:Color=\"#D9D9D9\" ss:Pattern=\"Solid\"/></Style>\n" +
        "<Style ss:ID=\"t\">"  + al + b2 + "<Font ss:Bold=\"1\"/>" +
            "<Interior ss:Color=\"#DCE6F1\" ss:Pattern=\"Solid\"/></Style>\n" +
        "<Style ss:ID=\"tp\">" + al + b2 + "<Font ss:Bold=\"1\"/>" +
            "<Interior ss:Color=\"#DCE6F1\" ss:Pattern=\"Solid\"/>" +
            "<NumberFormat ss:Format=\"0.00\"/></Style>\n</Styles>\n" +
        "<Worksheet ss:Name=\"Results\">\n<Table>\n";

    // Column 8 of each block carries "% of control" in the summary and acts
    // as the separator in the detail, so it cannot be narrow.
    for (q = 0; q < nCols; q++) {
        w = 80;
        r8 = q % 8;
        if (r8 == 0) w = 135;      // Experiment (summary) / Condition (detail)
        if (r8 == 1) w = 130;      // Condition (summary) / Image (detail)
        if (r8 == 5) w = 110;
        if (r8 == 6) w = 125;
        if (r8 == 7) w = 95;
        h = h + "<Column ss:Width=\"" + w + "\"/>";
    }
    return h + "\n";
}

function hCell(s) { return "<Cell ss:StyleID=\"h\"><Data ss:Type=\"String\">" + xmlEsc(s) + "</Data></Cell>"; }
function sCell(s) { return "<Cell ss:StyleID=\"c\"><Data ss:Type=\"String\">" + xmlEsc(s) + "</Data></Cell>"; }
function nCell(v) { return "<Cell ss:StyleID=\"c\"><Data ss:Type=\"Number\">" + v + "</Data></Cell>"; }
function tCell(s) { return "<Cell ss:StyleID=\"t\"><Data ss:Type=\"String\">" + xmlEsc(s) + "</Data></Cell>"; }
function mCell(s, mergeDown) {
    return "<Cell ss:StyleID=\"t\" ss:MergeDown=\"" + mergeDown +
           "\"><Data ss:Type=\"String\">" + xmlEsc(s) + "</Data></Cell>";
}
function sCellIdx(s, idx) {
    return "<Cell ss:Index=\"" + idx + "\" ss:StyleID=\"c\"><Data ss:Type=\"String\">" +
           xmlEsc(s) + "</Data></Cell>";
}
function tCellIdx(s, idx) {
    return "<Cell ss:Index=\"" + idx + "\" ss:StyleID=\"t\"><Data ss:Type=\"String\">" +
           xmlEsc(s) + "</Data></Cell>";
}
function fCell(st, f) {
    return "<Cell ss:StyleID=\"" + st + "\" ss:Formula=\"" + f +
           "\"><Data ss:Type=\"Number\"></Data></Cell>";
}


// ======================================================================
//  HISTOGRAMS
// ======================================================================
// Two separate images:
//   Histogram <name>.png         one panel per experiment, every condition
//                                shown separately
//   Histogram pooled <name>.png  all experiments together, conditions of
//                                equal concentration pooled
// Both use the same vertical scale, computed on the bars they contain.
// ======================================================================

// Records to plot: one per (panel, bar).
var pName  = newArray(0);
var pPct   = newArray(0);
var pSD    = newArray(0);
var pCells = newArray(0);
var pPar   = newArray(0);
var pNf    = newArray(0);
var pNm    = newArray(0);      // number of experiments; 0 outside pooled panel
var pStart = newArray(0);
var pCount = newArray(0);
var pTitle = newArray(0);
var pGlob  = newArray(0);
var wl1 = "";
var wl2 = "";

function resetRecords(nPan) {
    pName  = newArray(0);
    pPct   = newArray(0);
    pSD    = newArray(0);
    pCells = newArray(0);
    pPar   = newArray(0);
    pNf    = newArray(0);
    pNm    = newArray(0);
    pStart = newArray(nPan);
    pCount = newArray(nPan);
    pTitle = newArray(nPan);
    pGlob  = newArray(nPan);
}

// ---------------- one panel per experiment ----------------
// Fills panel pIdx with the conditions of experiment m.
// Bar and error bar are always in the SAME unit (see measBar):
//   a ratio (per 100 cells,   bar = ratio of the totals of the fields,
//   % of parasites)           SD  = field-to-field spread of that ratio;
//   a count (no nuclei)       bar = mean count per field,
//                             SD  = field-to-field spread of that count.
// (A total over all fields would not share a unit with a per-field SD.)
function fillPanel(pIdx, m) {
    nCh2 = condsAll.length;
    pStart[pIdx] = pName.length;
    pGlob[pIdx]  = 0;
    if (singleManip == 0) pTitle[pIdx] = "EXP " + (m + 1) + "  -  " + manipList[m];
    else                  pTitle[pIdx] = manipList[m];
    cnt = 0;

    for (c = 0; c < nCh2; c++) {
        kk = m * nCh2 + c;
        if (aggN[kk] == 0) continue;

        cc = aggCells[kk];
        pp = aggGreen[kk];
        dd = aggColoc[kk];
        nn = aggN[kk];

        vals = newArray(0);
        for (t = 0; t < summaryNames.length; t++) {
            if (summaryManip[t] != manipList[m]) continue;
            if (summaryCond[t] != condsAll[c]) continue;
            if (summaryUse[t] == 0) continue;      // left out: not in the bar
            // a ratio needs its denominator
            if (normalizeToCells && summaryCells[t] <= 0) continue;
            if (measureUse == 3 && summaryGreen[t] <= 0) continue;
            vv = measImg(summaryCells[t], summaryGreen[t], summaryColoc[t]);
            vals = Array.concat(vals, vv);
        }

        pct = measBar(cc, pp, dd, nn);
        sd = 0;
        if (vals.length > 1) {
            Array.getStatistics(vals, vmin, vmax, vmean, vstd);
            sd = vstd;
        }

        pName  = Array.concat(pName, condsAll[c]);
        pPct   = Array.concat(pPct, pct);
        pSD    = Array.concat(pSD, sd);
        pCells = Array.concat(pCells, cc);
        pPar   = Array.concat(pPar, pp);
        pNf    = Array.concat(pNf, nn);
        pNm    = Array.concat(pNm, 0);
        cnt = cnt + 1;
    }
    pCount[pIdx] = cnt;
}

// All experiments, one panel each, in a single image.
function drawHistogram(outPath) {
    nMh = manipList.length;
    if (nMh == 0) return;
    if (condsAll.length == 0) return;
    resetRecords(nMh);
    for (m = 0; m < nMh; m++) fillPanel(m, m);
    renderPanels(outPath);
}

// A single experiment in its own image. Used for slides: one histogram per
// slide, its coverslips as the bars.
function drawHistogramOne(outPath, m) {
    if (condsAll.length == 0) return;
    resetRecords(1);
    fillPanel(0, m);
    if (pCount[0] == 0) return;
    renderPanels(outPath);
}

// ---------------- single pooled panel ----------------
// Bar height: a ratio computed on the totals of all experiments, or the
// mean count per field over all of them. Error bar: standard deviation
// BETWEEN EXPERIMENTS of that same value - the spread that matters for a
// figure, and the reason independent replicates are needed.
function drawGlobalHistogram(outPath) {
    nMh = manipList.length;
    if (nMh == 0) return;
    if (gCount == 0) return;

    resetRecords(1);
    pStart[0] = 0;
    pGlob[0]  = 1;
    pTitle[0] = "ALL EXPERIMENTS  (n = " + nMh + ")";
    cnt = 0;

    for (g = 0; g < gCount; g++) {

        cc = 0; pp = 0; dd = 0; nn = 0; nmc = 0;
        mv = newArray(0);

        for (mm = 0; mm < nMh; mm++) {
            xc = 0; xp = 0; xd = 0; xn = 0;
            for (c = 0; c < condsAll.length; c++) {
                if (gOf[c] != g) continue;
                kk = mm * condsAll.length + c;
                xc = xc + aggCells[kk];
                xp = xp + aggGreen[kk];
                xd = xd + aggColoc[kk];
                xn = xn + aggN[kk];
            }
            if (xn == 0) continue;
            nmc = nmc + 1;
            cc = cc + xc;
            pp = pp + xp;
            dd = dd + xd;
            nn = nn + xn;
            // the value of this experiment, in the unit of the bar
            okV = 1;
            if (normalizeToCells && xc <= 0) okV = 0;
            if (measureUse == 3 && xp <= 0) okV = 0;
            if (okV == 1) {
                xv = measBar(xc, xp, xd, xn);
                mv = Array.concat(mv, xv);
            }
        }
        if (nmc == 0) continue;

        pct = measBar(cc, pp, dd, nn);
        sd = 0;
        if (mv.length > 1) {
            Array.getStatistics(mv, vmin, vmax, vmean, vstd);
            sd = vstd;
        }

        gl = groupLabel(g);
        pName  = Array.concat(pName, gl);
        pPct   = Array.concat(pPct, pct);
        pSD    = Array.concat(pSD, sd);
        pCells = Array.concat(pCells, cc);
        pPar   = Array.concat(pPar, pp);
        pNf    = Array.concat(pNf, nn);
        pNm    = Array.concat(pNm, nmc);
        cnt = cnt + 1;
    }
    pCount[0] = cnt;

    renderPanels(outPath);
}

// ---------------- drawing ----------------
function renderPanels(outPath) {

    nPan = pStart.length;
    nBars = pName.length;
    if (nBars == 0) return;

    // vertical scale, integer step chosen from 1 / 2 / 5 x 10^n
    maxV = 0;
    for (u = 0; u < nBars; u++) {
        top = pPct[u] + pSD[u];
        if (top > maxV) maxV = top;
    }
    if (maxV <= 0) maxV = 1;
    maxV = maxV * 1.15;
    step = 1;
    mant = 1;
    while (maxV / step > 5) {
        if      (mant == 1) { mant = 2; step = step * 2; }
        else if (mant == 2) { mant = 5; step = (step / 2) * 5; }
        else                { mant = 1; step = step * 2; }
    }
    nTicks = 0;
    while (nTicks * step < maxV) nTicks++;
    maxV = nTicks * step;

    slot  = 118;    // wide: condition names are long
    axisW = 62;
    padR  = 22;
    gap   = 40;
    mt    = 46;
    mb    = 150;
    ph    = 380;

    totalW = 0;
    for (m = 0; m < nPan; m++) {
        pw = axisW + slot * pCount[m] + padR;
        if (pCount[m] == 0) pw = 0;
        totalW = totalW + pw;
        if (m < nPan - 1) totalW = totalW + gap;
    }
    W = totalW + 40;
    if (W < 700) W = 700;
    H = mt + ph + mb;

    newImage("Histogram", "RGB white", W, H, 1);

    // what the bars are, bottom left
    setColor(0, 0, 0);
    setFont("SansSerif", 12, "bold");
    setJustification("left");
    sdTxt = "field to field";
    if (pGlob[0] == 1) sdTxt = "between experiments";
    drawString("Bars: " + measureLabel + "   -   error bars: SD " + sdTxt, 20, H - 14);

    setJustification("center");

    xCur = 20;
    for (m = 0; m < nPan; m++) {

        if (pCount[m] == 0) continue;

        plotL = xCur + axisW;
        plotW = slot * pCount[m];
        yTop  = mt;
        yBot  = mt + ph;

        setColor(0, 0, 0);
        setFont("SansSerif", 14, "bold");
        setJustification("center");
        drawString(pTitle[m], plotL + plotW / 2, mt - 22);

        setLineWidth(2);
        drawLine(plotL, yTop, plotL, yBot);
        drawLine(plotL, yBot, plotL + plotW, yBot);

        setLineWidth(1);
        setFont("SansSerif", 12, "");
        setJustification("right");
        for (g = 0; g <= nTicks; g++) {
            yv = step * g;
            yy = yBot - round(ph * g / nTicks);
            setColor(205, 205, 205);
            drawLine(plotL + 1, yy, plotL + plotW, yy);
            setColor(0, 0, 0);
            drawLine(plotL - 6, yy, plotL, yy);
            drawString("" + yv, plotL - 11, yy + 5);
        }

        setJustification("center");
        bw = round(slot * 0.52);
        for (q = 0; q < pCount[m]; q++) {
            idx = pStart[m] + q;
            bx  = round(plotL + slot * q + (slot - bw) / 2);
            bh  = round(ph * pPct[idx] / maxV);

            if (pGlob[m] == 1) setColor(105, 105, 105);
            else               setColor(150, 150, 150);
            fillRect(bx, yBot - bh, bw, bh);
            setColor(70, 70, 70);
            setLineWidth(1);
            drawRect(bx, yBot - bh, bw, bh);

            cx = bx + round(bw / 2);
            if (pSD[idx] > 0) {
                hiV = pPct[idx] + pSD[idx];
                loV = pPct[idx] - pSD[idx];
                if (loV < 0) loV = 0;
                yHi = yBot - round(ph * hiV / maxV);
                yLo = yBot - round(ph * loV / maxV);
                setColor(40, 40, 40);
                setLineWidth(2);
                drawLine(cx, yHi, cx, yLo);
                drawLine(cx - 8, yHi, cx + 8, yHi);
                drawLine(cx - 8, yLo, cx + 8, yLo);
                yVal = yHi - 8;
            } else {
                yVal = yBot - bh - 8;
            }

            setColor(0, 0, 0);
            setFont("SansSerif", 13, "bold");
            drawString(d2s(pPct[idx], 2), cx, yVal);

            // condition name, wrapped onto two lines when too long
            setFont("SansSerif", 12, "bold");
            wrapLabel(pName[idx], slot - 10);
            drawString(wl1, cx, yBot + 22);
            if (wl2 != "") drawString(wl2, cx, yBot + 38);

            setFont("SansSerif", 11, "");
            setColor(60, 60, 60);
            drawString("SD = " + d2s(pSD[idx], 2), cx, yBot + 60);
            setColor(105, 105, 105);
            if (pGlob[m] == 1) {
                drawString("n = " + pNm[idx] + " exp.", cx, yBot + 78);
                if (normalizeToCells)    drawString(pCells[idx] + " cells", cx, yBot + 94);
                else if (measureUse == 3) drawString(pPar[idx] + " parasites", cx, yBot + 94);
                else                     drawString(pNf[idx] + " fields", cx, yBot + 94);
            } else {
                if (normalizeToCells) {
                    drawString("n = " + pCells[idx] + " cells", cx, yBot + 78);
                    drawString("over " + pNf[idx] + " fields", cx, yBot + 94);
                } else if (measureUse == 3) {
                    drawString("n = " + pPar[idx] + " parasites", cx, yBot + 78);
                    drawString("over " + pNf[idx] + " fields", cx, yBot + 94);
                } else {
                    drawString("mean per field", cx, yBot + 78);
                    drawString("n = " + pNf[idx] + " fields", cx, yBot + 94);
                }
            }
        }

        xCur = plotL + plotW + padR + gap;
    }

    saveAs("PNG", outPath);
    close();
}

// Split a label onto two lines when wider than maxW; result in wl1/wl2.
// The current font must be the drawing font: getStringWidth depends on it.
function wrapLabel(txt, maxW) {
    wl1 = txt;
    wl2 = "";
    if (getStringWidth(txt) <= maxW) return;
    parts = split(txt, " ");
    if (parts.length < 2) return;
    k = 1;
    for (q = 1; q < parts.length; q++) {
        cand = "";
        for (z = 0; z < q; z++) {
            if (z > 0) cand = cand + " ";
            cand = cand + parts[z];
        }
        if (getStringWidth(cand) <= maxW) k = q;
    }
    a = "";
    b = "";
    for (z = 0; z < parts.length; z++) {
        if (z < k) {
            if (z > 0) a = a + " ";
            a = a + parts[z];
        } else {
            if (z > k) b = b + " ";
            b = b + parts[z];
        }
    }
    wl1 = a;
    wl2 = b;
}

// ======================================================================
//  FUNCTIONS - detection, shared by the batch and by Calibrate
// ======================================================================
// Both call these same functions, so what the calibration panel shows is
// exactly what the batch counts. They read the settings declared at the
// top; a background radius or blur of 0 skips that step.

// Each object class is detected in three steps, run one after the other
// by the batch:
//   ...Pre      copy of the channel, contrast, background, blur (the slow
//               part: the background subtraction)
//   ...Mask     threshold, binary mask, holes filled, watershed - in place
//   ...Objects  the particles of the mask into the ROI Manager
// Calibrate keeps the result of the first two for each image and redoes
// one only when one of its own settings has changed (calMaskOf): moving a
// size or shape slider then only reruns the last step.

// Nuclei: binary mask of the nuclear channel, then its particles into the
// ROI Manager (emptied first). The mask stays open under maskTitle.
function segmentNuclei(srcTitle, maskTitle) {
    nucleiPre(srcTitle, maskTitle);
    nucleiMask(maskTitle);
    nucleiObjects(maskTitle);
}

function nucleiPre(srcTitle, dstTitle) {
    okP = prepChannel(srcTitle, dstTitle, nucleiMultiply, nucleiBgRolling, nucleiBlurSigma);
}

function nucleiMask(maskTitle) {
    nuLo = autoLow(maskTitle, nucleiThresholdMethod, nucleiCapF, nucleiBlurSigma);
    nucThrAuto = nuLo;
    nucThr = nuLo * nucleiThresholdFactor;
    selectWindow(maskTitle);
    setThreshold(nuLo * nucleiThresholdFactor, 1e30);
    run("Convert to Mask");
    run("8-bit");
    run("Fill Holes");
    if (nucleiWatershed) okW = watershedMask(maskTitle, nucleiWsTol);
}

function nucleiObjects(maskTitle) {
    roiManager("Reset");
    selectWindow(maskTitle);
    run("Analyze Particles...",
        "size=" + nucleiMinArea + "-" + nucleiMaxArea +
        " circularity=" + nucleiCirc + " show=Nothing add");
}

// Host cells from the nuclei in the ROI Manager, sized against the median
// of the field. An object clearly larger than the median counts as several
// overlapping cells (a cluster). With the fragment rule on, an object
// clearly smaller counts as no cell (a fragment) - typically the small
// piece a watershed cut off a nucleus, which would otherwise count one
// cell twice. Areas are read on areaTitle. Returns the number of cells;
// clusters are left in cellClusterIdx, fragments in cellFragIdx, the
// median area in cellMedian.
function countCells(areaTitle) {
    nR = roiManager("count");
    cellClusterIdx = newArray(0);
    cellFragIdx = newArray(0);
    cellMedian = 0;
    useMed = 0;
    if (splitBigNuclei) useMed = 1;
    if (nucleiFragFactor > 0) useMed = 1;
    if (useMed == 0 || nR <= 3) return nR;

    // getValue reads the measurement directly, bypassing the Results
    // table. That table is shared with the rest of ImageJ, so another
    // macro or a renamed window could empty it and getResult then
    // failed with "No results found".
    nAreas = newArray(nR);
    for (n = 0; n < nR; n++) {
        selectWindow(areaTitle);
        roiManager("Select", n);
        nAreas[n] = getValue("Area");
    }
    selectWindow(areaTitle);
    run("Select None");

    sortedA = Array.copy(nAreas);
    Array.sort(sortedA);
    medA = sortedA[floor(nR / 2)];

    totalCells = 0;
    for (n = 0; n < nR; n++) {
        est = 1;
        if (splitBigNuclei && medA > 0 && nAreas[n] >= nucleiDoubletFactor * medA) {
            est = round(nAreas[n] / medA);
            if (est < 2) est = 2;
            if (est > nucleiMaxSplit) est = nucleiMaxSplit;
            cellClusterIdx = Array.concat(cellClusterIdx, n);
        } else if (nucleiFragFactor > 0 && medA > 0 && nAreas[n] < nucleiFragFactor * medA) {
            est = 0;
            cellFragIdx = Array.concat(cellFragIdx, n);
        }
        totalCells = totalCells + est;
    }
    cellMedian = medA;
    return totalCells;
}

// Parasites: binary mask of the parasite channel, then its particles into
// the ROI Manager (emptied first). Returns how many. The threshold used is
// left in parThr, the automatic one it came from in parThrAuto. The mask
// stays open under maskTitle.
function segmentParasites(srcTitle, maskTitle) {
    parasitePre(srcTitle, maskTitle);
    parasiteMask(maskTitle);
    nPar = parasiteObjects(maskTitle);
    return nPar;
}

function parasitePre(srcTitle, dstTitle) {
    okP = prepChannel(srcTitle, dstTitle, greenMultiply, greenBgRolling, greenBlurSigma);
}

function parasiteMask(maskTitle) {
    selectWindow(maskTitle);
    run("Select None");
    grLo = autoLow(maskTitle, greenThresholdMethod, greenCapF, greenBlurSigma);
    grHi = autoHi;
    parThrAuto = grLo;
    grLo = round(grLo * greenThresholdFactor);
    parThr = grLo;
    selectWindow(maskTitle);
    setThreshold(grLo, grHi);
    run("Convert to Mask");
    run("8-bit");
    run("Fill Holes");
    if (greenWatershed) okW = watershedMask(maskTitle, greenWsTol);
}

function parasiteObjects(maskTitle) {
    roiManager("Reset");
    selectWindow(maskTitle);
    run("Analyze Particles...",
        "size=" + greenMinArea + "-" + greenMaxArea +
        " circularity=" + greenCirc + " show=Nothing add");
    nPar = roiManager("count");
    return nPar;
}

// Parasites from the objects in the ROI Manager (all of them parasite
// objects), sized against the median of the field as countCells does for
// nuclei. With "Count clumped parasites as several" ticked and at least 3
// objects, an object clearly larger than the median counts as several
// parasites: area / median, rounded, at least 2, at most greenMaxSplit.
// Areas are read on areaTitle. Returns the number of parasites; the
// clusters are left in parClusterIdx, what each object counts for in
// parWeight, the median area in parMedian (0 when not computed).
function countParasites(areaTitle) {
    nR = roiManager("count");
    parClusterIdx = newArray(0);
    parWeight = newArray(nR);
    for (n = 0; n < nR; n++) parWeight[n] = 1;
    parMedian = 0;
    // fewer than 3 objects: no median to trust
    if (splitBigParasites == false || nR < 3) return nR;

    pAreas = newArray(nR);
    for (n = 0; n < nR; n++) {
        selectWindow(areaTitle);
        roiManager("Select", n);
        pAreas[n] = getValue("Area");
    }
    selectWindow(areaTitle);
    run("Select None");

    sortedP = Array.copy(pAreas);
    Array.sort(sortedP);
    medP = sortedP[floor(nR / 2)];

    totalP = 0;
    for (n = 0; n < nR; n++) {
        est = 1;
        if (medP > 0 && pAreas[n] >= greenDoubletFactor * medP) {
            est = round(pAreas[n] / medP);
            if (est < 2) est = 2;
            if (est > greenMaxSplit) est = greenMaxSplit;
            parClusterIdx = Array.concat(parClusterIdx, n);
        }
        parWeight[n] = est;
        totalP = totalP + est;
    }
    parMedian = medP;
    return totalP;
}

// Cluster settings of the parasites as used: a clump at least 1.1 x the
// median (any lower and a plain parasite would count twice), 2 to 50
// parasites per clump.
function parClusterOk() {
    if (isNaN(greenDoubletFactor) || greenDoubletFactor < 1.1) greenDoubletFactor = 1.1;
    if (isNaN(greenMaxSplit) || greenMaxSplit < 2) greenMaxSplit = 2;
    greenMaxSplit = round(greenMaxSplit);
    if (greenMaxSplit > 50) greenMaxSplit = 50;
    return 1;
}

// Corrected copy of a channel, dstTitle, made from srcTitle: contrast
// (display only), multiplied, background subtracted (rolling ball of the
// given radius, 0 = none), blurred (0 = none). Multiplying an 8- or 16-bit
// image clips at its maximum (255 / 65535): every pixel above max / mult
// ends up equal, a flat plateau that can fool the automatic threshold (it
// then took the plateau for the background and kept only the brightest part
// of each nucleus). When more than 1 % of the pixels would clip, the copy is
// made 32-bit first, which keeps every value. A few hot pixels clipping
// change nothing that matters: the copy is then computed exactly as before.
function prepChannel(srcTitle, dstTitle, mult, rolling, sigma) {
    selectWindow(srcTitle);
    run("Select None");
    run("Duplicate...", "title=[" + dstTitle + "]");
    run("Enhance Contrast", "saturated=" + enhanceSaturated);
    bd = bitDepth();
    if (mult > 1 && (bd == 8 || bd == 16)) {
        getRawStatistics(nPx, meanV, minV, maxV);
        lim = 255;
        if (bd == 16) lim = 65535;
        if (maxV * mult > lim) {
            // pixels that would clip: those above lim / mult
            cutV = floor(lim / mult) + 1;
            nC = countFrom(cutV);
            if (nC > 0.01 * nPx) run("32-bit");
        }
    }
    run("Multiply...", "value=" + mult);
    if (rolling > 0) {
        run("Subtract Background...", "rolling=" + rolling);
        // as the 8- and 16-bit versions do: no value below 0
        if (bitDepth() == 32) run("Min...", "value=0");
    }
    if (sigma > 0) run("Gaussian Blur...", "sigma=" + sigma);
    return 1;
}

// Lower bound of the automatic threshold of the corrected channel t, for
// bright objects on a dark background. Two things are kept from moving it,
// by leaving pixels out of the CALCULATION only - they are still above the
// threshold, and still detected:
//   - a saturated peak: when the most frequent value lies at the top of the
//     histogram - a saturated area larger than the background - the methods
//     take that peak for the background and put the threshold just under it,
//     so that only the saturated part of each nucleus is found. The peak is
//     left out.
//   - the bright cap (capF > 0), adapted to each image: an object holding a
//     pixel brighter than capF x the median brightness of what the
//     threshold detects - debris, a dead cell, a crystal - is left out
//     whole. Repeated, since the threshold, and the median, then change.
//     A bright spot no larger than a hot pixel spread by the blur of sigma
//     sig is no object, and stays in (see dropBright).
// Nothing is left out of an ordinary image: its threshold is the method's
// own. The upper bound of the image's threshold goes to autoHi; what was
// left out to capHit / capVal / capObj / capN / spikeHit / spikeN, and in
// words to thrNote; the median brightness and brightest pixel of what the
// first threshold detected to thrMed / thrMax.
function autoLow(t, method, capF, sig) {
    selectWindow(t);
    run("Select None");
    setAutoThreshold(method + " dark");
    getThreshold(lo, hi);
    autoHi = hi;
    capHit = 0;
    capVal = 0;
    capObj = 0;
    capN = 0;
    spikeHit = 0;
    spikeN = 0;
    thrNote = "";
    nAll = getWidth() * getHeight();
    // a bright spot smaller than this is a hot pixel spread by the blur
    minPx = round(PI * (2 * sig + 1) * (2 * sig + 1));
    // what this threshold detects: median brightness, brightest pixel
    thrMed = 0;
    thrMax = 0;
    if (capF > 0) {
        thrMed = medAbove(lo);
        thrMax = medMax;
        selectWindow(t);
    }
    w = "";
    done = 0;
    for (it = 0; it < 4; it++) {
        if (done == 1) continue;
        if (w == "") selectWindow(t);
        else         selectWindow(w);
        run("Select None");
        changed = 0;
        sp = spikeFrom();
        if (sp < 1e30) {
            if (w == "") w = thrWork(t);
            selectWindow(w);
            run("Select None");
            getRawStatistics(nA);
            changeValues(sp, 1e30, NaN);
            getRawStatistics(nB);
            spikeN = spikeN + nA - nB;
            spikeHit = 1;
            changed = 1;
        } else if (capF > 0) {
            med = thrMed;
            mxI = thrMax;
            if (w != "") {
                med = medAbove(lo);
                mxI = medMax;
            }
            if (med > 0 && mxI >= capF * med) {
                if (w == "") w = thrWork(t);
                selectWindow(w);
                run("Select None");
                getRawStatistics(nA);
                nb = dropBright(w, capF * med, lo, minPx);
                selectWindow(w);
                getRawStatistics(nB);
                // only hot pixels above the cap: nothing left out, nothing changes
                if (nb != 0) {
                    capN = capN + nA - nB;
                    if (capHit == 0) capVal = capF * med;
                    capHit = 1;
                    if (nb < 0) capObj = -1;
                    if (nb > 0 && capObj >= 0) capObj = capObj + nb;
                    changed = 1;
                }
            }
        }
        if (changed == 0) {
            done = 1;
        } else {
            // the threshold of the pixels left in
            selectWindow(w);
            okS = selIncluded();
            setAutoThreshold(method + " dark");
            getThreshold(lo, hi2);
            resetThreshold();
            run("Select None");
        }
    }
    if (w != "") {
        selectWindow(w);
        close();
    }
    if (spikeHit == 1)
        thrNote = "a peak of " + spikeN + " saturated pixels (" + d2s(100.0 * spikeN / nAll, 1) +
                  " % of the image) left out of the threshold calculation";
    if (capHit == 1) {
        if (thrNote != "") thrNote = thrNote + "; ";
        cn = "" + capN + " pixels";
        if (capObj > 0) cn = "" + capObj + " object(s)";
        thrNote = thrNote + cn + " brighter than " + capF + " x the median (" + round(capVal) +
                  ") left out of the threshold calculation";
    }
    selectWindow(t);
    run("Select None");
    return lo;
}

// Number of pixels of the image in front at or above the value v, exact
// (8- or 16-bit image: its histogram has one bin per value).
function countFrom(v) {
    nb = 256;
    if (bitDepth() == 16) nb = 65536;
    getHistogram(hv, hc, nb);
    b0 = round(v);
    if (b0 < 0) b0 = 0;
    n = 0;
    for (b = b0; b < nb; b++) n = n + hc[b];
    return n;
}

// Median of the pixels of the image in front at or above lo (NaN left
// out), from its histogram: one bin per value for 8 and 16 bits, 1024 bins
// for 32 bits. Their brightest goes to medMax. 0 when there are none.
function medAbove(lo) {
    run("Select None");
    getRawStatistics(nPx, meanV, minV, maxV);
    medMax = maxV;
    if (isNaN(maxV) || maxV < lo) return 0;
    if (bitDepth() == 32) {
        nb = 1024;
        if (maxV <= lo) return lo;
        bw = (maxV - lo) / nb;
        // the upper bound just above the brightest pixel, so that it counts
        getHistogram(hv, hc, nb, lo, maxV + bw);
        tot = 0;
        for (b = 0; b < nb; b++) tot = tot + hc[b];
        if (tot == 0) return 0;
        acc = 0;
        b = 0;
        while (b < nb - 1 && acc + hc[b] < tot / 2) {
            acc = acc + hc[b];
            b = b + 1;
        }
        return hv[b] + (hv[1] - hv[0]) / 2;
    }
    nb = 256;
    if (bitDepth() == 16) nb = 65536;
    getHistogram(hv, hc, nb);
    b0 = -floor(-lo);
    if (b0 < 0) b0 = 0;
    tot = 0;
    for (b = b0; b < nb; b++) tot = tot + hc[b];
    if (tot == 0) return 0;
    acc = 0;
    b = b0;
    while (b < nb - 1 && acc + hc[b] < tot / 2) {
        acc = acc + hc[b];
        b = b + 1;
    }
    return b;
}

// 32-bit working copy of t for the threshold calculation: the pixels left
// out of it are set to NaN there. Returns its title.
function thrWork(srcT) {
    w = srcT + "_thr";
    if (isOpen(w)) {
        selectWindow(w);
        close();
    }
    selectWindow(srcT);
    run("Select None");
    run("Duplicate...", "title=[" + w + "]");
    if (bitDepth() != 32) run("32-bit");
    return w;
}

// Selects the pixels of the image in front that are not NaN. (An automatic
// threshold maps NaN to the darkest bin: they must be outside the selection
// to be left out of its histogram.)
function selIncluded() {
    run("Select None");
    setThreshold(-1e30, 1e30);
    run("Create Selection");
    resetThreshold();
    return 1;
}

// Lower edge of a saturated peak in the 256-bin histogram of the image in
// front, or 1e30 when there is none: the most frequent value lies in the
// top quarter of the range the pixels cover. Never so in a fluorescence
// image whose background is the most frequent value, as it should be.
function spikeFrom() {
    getHistogram(hv, hc, 256);
    fnz = -1;
    lnz = -1;
    m = 0;
    for (b = 0; b < 256; b++) {
        if (hc[b] > 0) {
            if (fnz < 0) fnz = b;
            lnz = b;
        }
        if (hc[b] > hc[m]) m = b;
    }
    if (fnz < 0) return 1e30;
    if (lnz - fnz < 8) return 1e30;
    if (m < fnz + 0.75 * (lnz - fnz)) return 1e30;
    // the foot of the peak: down to the bins under a quarter of it
    s = m;
    while (s > fnz && hc[s - 1] * 4 >= hc[m]) s = s - 1;
    return hv[s];
}

// In the working copy w, the objects - at threshold lo - holding a bright
// spot (pixels at or above cap) of at least minPx pixels are set to NaN,
// whole: what an object brighter than the cap adds to the histogram goes,
// its dimmer rim included. A smaller spot is a hot pixel of the camera,
// spread by the blur - every image has some, inside objects or not: they
// were always part of the calculation, and the settings were validated with
// them, so they stay. Returns how many objects were left out. When they
// would cover more than a quarter of the image, or the bright spots are too
// many to trace one by one, all the pixels at or above cap are set to NaN
// instead (returns -1).
function dropBright(w, cap, lo, minPx) {
    selectWindow(w);
    run("Select None");
    getRawStatistics(nIn0);
    getPixelSize(pu, pw, ph);
    w2 = w + "2";
    run("Duplicate...", "title=[" + w2 + "]");
    setThreshold(cap, 1e30);
    run("Find Maxima...", "noise=0 output=[Point Selection] above");
    nP = 0;
    if (selectionType() == 10) {
        getSelectionCoordinates(xs, ys);
        nP = xs.length;
    }
    run("Select None");
    resetThreshold();
    nObj = -1;
    if (nP <= 500) {
        nObj = 0;
        for (q = 0; q < nP; q++) {
            v = getPixel(xs[q], ys[q]);
            if (isNaN(v)) continue;
            // the bright spot: a hot pixel, or an object brighter than the cap?
            setThreshold(cap, 1e30);
            doWand(xs[q], ys[q]);
            getStatistics(aSel);
            run("Select None");
            if (aSel / (pw * ph) >= minPx) {
                // the whole object, down to the threshold
                setThreshold(lo, 1e30);
                doWand(xs[q], ys[q]);
                run("Set...", "value=NaN");
                run("Select None");
                nObj = nObj + 1;
            }
        }
        resetThreshold();
        getRawStatistics(nIn1);
        if (nIn0 - nIn1 > 0.25 * getWidth() * getHeight()) nObj = -1;
    }
    if (nObj < 0) {
        close();
        selectWindow(w);
        changeValues(cap, 1e30, NaN);
        return -1;
    }
    selectWindow(w);
    close();
    selectWindow(w2);
    rename(w);
    return nObj;
}

// Saturation of a raw channel, in words, "" when there is none to speak of:
// the share of its pixels at its brightest value, when that value is the
// top of a camera range (255, 4095, 16383, 65535) and holds at least 0.05 %
// of the pixels, or when it holds 1 % of them whatever it is.
function satCheck(t) {
    selectWindow(t);
    run("Select None");
    bd = bitDepth();
    if (bd != 8 && bd != 16) return "";
    getRawStatistics(nPx, meanV, minV, maxV);
    if (maxV <= minV) return "";
    nS = countFrom(maxV);
    pc = 100.0 * nS / nPx;
    top = 0;
    if (maxV == 255 || maxV == 4095 || maxV == 16383 || maxV == 65535) top = 1;
    if (pc < 1 && (top == 0 || pc < 0.05)) return "";
    return "" + d2s(pc, 2) + " % of the pixels saturated (at " + maxV +
           "): lower the exposure if you can";
}

// Double labelling: binary mask of the marker channel. Each parasite of the
// ROI Manager (indices 0 to nPar-1) is double-labelled when at least
// minOverlapPercent of its area is positive in that mask. Returns how many;
// their indices are left in dblIdx, the threshold in dblThr / dblThrAuto,
// and how many reach 20, 35 and 55 % in dblN20 / dblN35 / dblN55. The mask
// stays open under maskTitle.
function scoreDouble(srcTitle, maskTitle, nPar) {
    markerPre(srcTitle, maskTitle);
    markerMask(maskTitle);
    nDbl = markerScore(maskTitle, nPar);
    return nDbl;
}

function markerPre(srcTitle, dstTitle) {
    okP = prepChannel(srcTitle, dstTitle, redMultiply, redBgRolling, redBlurSigma);
}

function markerMask(maskTitle) {
    redLo = autoLow(maskTitle, redThresholdMethod, redCapF, redBlurSigma);
    redHi = autoHi;
    dblThrAuto = redLo;
    redLo = round(redLo * redThresholdFactor);
    dblThr = redLo;
    selectWindow(maskTitle);
    setThreshold(redLo, redHi);
    run("Convert to Mask");
    run("8-bit");
    run("Fill Holes");
}

// Overlap of each parasite (ROI Manager items 0 to nPar-1) with the marker
// mask; see scoreDouble.
// The counts are in parasites: an object counted as a cluster of n
// parasites (parWeight, from countParasites) counts n times.
function markerScore(maskTitle, nPar) {
    dblIdx = newArray(0);
    dblN20 = 0;
    dblN35 = 0;
    dblN55 = 0;
    nDbl = 0;
    // weights only when they describe these very objects
    useW = 0;
    if (parWeight.length == nPar) useW = 1;
    for (r = 0; r < nPar; r++) {
        w = 1;
        if (useW == 1) w = parWeight[r];
        selectWindow(maskTitle);
        roiManager("Select", r);
        // Mean of the marker mask inside the parasite: 255 = fully positive,
        // 0 = not at all. Read without the Results table.
        coveragePercent = (getValue("Mean") / 255) * 100;
        if (coveragePercent >= 20) dblN20 = dblN20 + w;
        if (coveragePercent >= 35) dblN35 = dblN35 + w;
        if (coveragePercent >= 55) dblN55 = dblN55 + w;
        if (coveragePercent >= minOverlapPercent) {
            dblIdx = Array.concat(dblIdx, r);
            nDbl = nDbl + w;
        }
    }
    selectWindow(maskTitle);
    run("Select None");
    return nDbl;
}


// ======================================================================
//  FUNCTIONS - marker objects, places along the parasite, poles
// ======================================================================
// A place along a parasite is read on its long axis - its Feret diameter,
// tip to tip - as t, from 0 at one tip (end 1) to 1 at the other (end 2).
// Its distance from the centre is d = |t - 0.5| x 2: 0 at the centre, 1 at
// a tip. The centre is d <= centrePct %; an extremity, and a pole, is
// d >= 1 - endPct % (with endPct 20: the last 20 % of the length at each
// end). The parasites are the ROI Manager items 0 to nPar-1, as everywhere
// in the double labelling; these functions leave the ROI Manager as they
// found it.

// Double labelling of the parasites against the marker mask maskT, as
// dblMode asks: overlap (markerScore), marker objects inside the parasite,
// or the parasite inside a marker object. Then, with the pole option, the
// pole of the marker on each double-labelled parasite, oriented from the
// nuclear stain blueT and / or the pole marker poleT ("" = not available).
// imgT is any image of the same size, the outlines are read on it. Returns
// the double-labelled count (a cluster counting for the parasites it
// holds); dblIdx lists them.
function scoreDoubleAll(maskT, nPar, imgT, blueT, poleT) {
    dblN20 = 0;
    dblN35 = 0;
    dblN55 = 0;
    objN = 0;
    objOn = 0;
    objUsed = 0;
    oX = newArray(0);
    oY = newArray(0);
    oR = newArray(0);
    oOwn = newArray(0);
    oT = newArray(0);
    oUse = newArray(0);
    oPole = newArray(0);
    pObj = newArray(nPar);
    pCnt = newArray(nPar);
    pBas = newArray(nPar);
    pWhy = newArray(nPar);
    pNuc = newArray(nPar);
    pMk  = newArray(nPar);
    // the long axis of each parasite: needed to place objects, and poles
    if (dblMode == 1 || dblPolesUsed) okA = parasiteAxes(nPar, imgT);
    if (dblMode == 0) {
        nDbl = markerScore(maskT, nPar);
    } else if (dblMode == 1) {
        nDbl = objectsInParasites(maskT, nPar, imgT);
    } else {
        // the marker objects of the size given (a vacuole, a cell...): how
        // many, then a mask of them alone, against which the overlap of
        // each parasite is read as in the first mode
        objN = findObjects(maskT, encMinArea, encMaxArea, false);
        encT = enclosingMask(maskT);
        nDbl = markerScore(encT, nPar);
        selectWindow(encT);
        close();
    }
    // the pole of the marker on each double-labelled parasite
    pCls = newArray(nPar);
    pPlace = newArray(nPar);
    for (p = 0; p < nPar; p++) pPlace[p] = -1;
    poleN = newArray(5);
    poleOri = 0;
    poleNote = "";
    if (dblPolesUsed && dblMode != 2) {
        okO = orientParasites(nPar, imgT, blueT, poleT);
        okC = classifyPoles(maskT, nPar, imgT);
    }
    return nDbl;
}

// The objects of the binary mask maskT (objects 255) with an area of aMin
// to aMax um2 - split by a watershed first when ws is true: centres in fX /
// fY (pixels), areas in fA (um2). The ROI Manager is left as it was.
// Returns how many.
function findObjects(maskT, aMin, aMax, ws) {
    tmpT = "CAC_objects";
    if (isOpen(tmpT)) { selectWindow(tmpT); close(); }
    selectWindow(maskT);
    run("Select None");
    run("Duplicate...", "title=[" + tmpT + "]");
    if (ws) run("Watershed");
    getPixelSize(pu, pw0, ph0);
    c0 = roiManager("count");
    run("Analyze Particles...", "size=" + aMin + "-" + aMax +
        " circularity=0.00-1.00 show=Nothing add");
    nO = roiManager("count") - c0;
    fX = newArray(nO);
    fY = newArray(nO);
    fA = newArray(nO);
    for (q = 0; q < nO; q++) {
        roiManager("select", c0 + q);
        List.setMeasurements();
        fX[q] = List.getValue("X") / pw0;
        fY[q] = List.getValue("Y") / ph0;
        fA[q] = List.getValue("Area");
    }
    List.clear();
    nd = roiTrim(c0);
    selectWindow(tmpT);
    close();
    return nO;
}

// Mask (objects 255, whatever the LUT) of the marker objects of encMinArea
// to encMaxArea um2 of the mask maskT, titled CAC_enclosing, calibrated as
// maskT. Returns its title.
function enclosingMask(maskT) {
    encT = "CAC_enclosing";
    if (isOpen(encT)) { selectWindow(encT); close(); }
    selectWindow(maskT);
    run("Select None");
    getPixelSize(pu, pw1, ph1);
    run("Analyze Particles...", "size=" + encMinArea + "-" + encMaxArea +
        " circularity=0.00-1.00 show=Masks");
    t2 = getTitle();
    if (t2 == maskT) {
        // no mask made: an empty one
        run("Duplicate...", "title=[" + encT + "]");
        run("Select All");
        setColor(0);
        fill();
        run("Select None");
    } else {
        rename(encT);
    }
    selectWindow(encT);
    setVoxelSize(pw1, ph1, 1, pu);
    return encT;
}

// Long axis of each parasite: its ends aX1/aY1 and aX2/aY2 (pixels), its
// length aLen (um) and area aArea (um2).
function parasiteAxes(nPar, imgT) {
    aX1 = newArray(nPar);
    aY1 = newArray(nPar);
    aX2 = newArray(nPar);
    aY2 = newArray(nPar);
    aLen = newArray(nPar);
    aArea = newArray(nPar);
    selectWindow(imgT);
    getPixelSize(pu, pw0, ph0);
    for (p = 0; p < nPar; p++) {
        roiManager("select", p);
        lp = feretEnds();
        aX1[p] = fe1x;
        aY1[p] = fe1y;
        aX2[p] = fe2x;
        aY2[p] = fe2y;
        aLen[p] = lp * pw0;
        aArea[p] = getValue("Area");
    }
    run("Select None");
    return nPar;
}

// Ends of the long axis (Feret diameter) of the selection, in pixels:
// fe1x, fe1y where ImageJ starts it, fe2x, fe2y the point of the outline
// farthest from there - the other tip. (Rebuilding the second tip from the
// Feret angle goes wrong for half of the orientations: ImageJ folds that
// angle into 0-180 degrees.) Returns the length in pixels.
function feretEnds() {
    List.setMeasurements();
    fe1x = List.getValue("FeretX");
    fe1y = List.getValue("FeretY");
    List.clear();
    getSelectionCoordinates(sx, sy);
    bd = -1;
    fe2x = fe1x;
    fe2y = fe1y;
    for (q = 0; q < sx.length; q++) {
        dx = sx[q] - fe1x;
        dy = sy[q] - fe1y;
        dd = dx * dx + dy * dy;
        if (dd > bd) {
            bd = dd;
            fe2x = sx[q];
            fe2y = sy[q];
        }
    }
    return sqrt(bd);
}

// Place t of the point (x, y) along the axis of parasite p: 0 at end 1,
// 1 at end 2 (below 0 or above 1 beyond the tips).
function placeOn(p, x, y) {
    vx = aX2[p] - aX1[p];
    vy = aY2[p] - aY1[p];
    l2 = vx * vx + vy * vy;
    if (l2 <= 0) return 0.5;
    return ((x - aX1[p]) * vx + (y - aY1[p]) * vy) / l2;
}

// 1 when a place t is counted under posMode (anywhere, centre, extremity).
function placeCounted(t) {
    d = abs(t - 0.5) * 2;
    if (posMode == 1) {
        if (d <= centrePct / 100) return 1;
        return 0;
    }
    if (posMode == 2) {
        if (d >= 1 - endPct / 100) return 1;
        return 0;
    }
    return 1;
}

// Pole of the place t on parasite p: 1 basal, 2 apical, 3 at an end of a
// parasite that could not be oriented, 4 not at an end.
function poleAt(p, t) {
    d = abs(t - 0.5) * 2;
    if (d < 1 - endPct / 100) return 4;
    if (pBas[p] == 0) return 3;
    endN = 1;
    if (t > 0.5) endN = 2;
    if (endN == pBas[p]) return 1;
    return 2;
}

// Deletes the ROI Manager items from index n on. Returns how many.
function roiTrim(n) {
    c = roiManager("count");
    if (c <= n) return 0;
    idx = newArray(c - n);
    for (q = n; q < c; q++) idx[q - n] = q;
    roiManager("select", idx);
    roiManager("delete");
    run("Select None");
    return c - n;
}

// For each point (xs, ys, pixels), the parasite holding it: the one whose
// outline holds it, else one within tolPx pixels of it (0 = none); -1 when
// none. Outlines read on image imgT.
function ownerOf(xs, ys, nPar, tolPx, imgT) {
    n = xs.length;
    own = newArray(n);
    for (k = 0; k < n; k++) own[k] = -1;
    if (n == 0 || nPar == 0) return own;
    selectWindow(imgT);
    run("Select None");
    tl = round(tolPx);
    for (pass = 0; pass < 2; pass++) {
        if (pass == 1 && tl < 1) continue;
        for (p = 0; p < nPar; p++) {
            roiManager("select", p);
            if (pass == 1) run("Enlarge...", "enlarge=" + tl + " pixel");
            getSelectionBounds(bx, by, bw, bh);
            for (k = 0; k < n; k++) {
                if (own[k] >= 0) continue;
                if (xs[k] < bx || ys[k] < by || xs[k] >= bx + bw || ys[k] >= by + bh) continue;
                if (Roi.contains(floor(xs[k]), floor(ys[k])) == 1) own[k] = p;
            }
        }
    }
    selectWindow(imgT);
    run("Select None");
    return own;
}

// Marker objects inside the parasites (dblMode 1): the objects of the
// marker mask of objMinArea to objMaxArea um2, each given to the parasite
// holding its centre (or within objTol um of it - beads sit on the
// surface); counted when its place suits posMode. A parasite with at
// least objMinN counted objects is double-labelled. Fills the object
// lists oX... and pObj / pCnt; returns the double-labelled count.
function objectsInParasites(maskT, nPar, imgT) {
    nO = findObjects(maskT, objMinArea, objMaxArea, objWatershed);
    selectWindow(maskT);
    getPixelSize(pu, pw0, ph0);
    oX = Array.copy(fX);
    oY = Array.copy(fY);
    oR = newArray(nO);
    oT = newArray(nO);
    oUse = newArray(nO);
    oPole = newArray(nO);
    for (k = 0; k < nO; k++) oR[k] = sqrt(fA[k] / PI) / pw0;
    oOwn = ownerOf(oX, oY, nPar, objTol / pw0, imgT);
    objN = nO;
    useW = 0;
    if (parWeight.length == nPar) useW = 1;
    for (k = 0; k < nO; k++) {
        p = oOwn[k];
        oT[k] = -1;
        if (p < 0) continue;
        objOn = objOn + 1;
        pObj[p] = pObj[p] + 1;
        t = placeOn(p, oX[k], oY[k]);
        oT[k] = t;
        if (placeCounted(t) == 1) {
            oUse[k] = 1;
            pCnt[p] = pCnt[p] + 1;
            objUsed = objUsed + 1;
        }
    }
    dblIdx = newArray(0);
    nDbl = 0;
    for (p = 0; p < nPar; p++) {
        if (pCnt[p] < objMinN || pCnt[p] == 0) continue;
        w = 1;
        if (useW == 1) w = parWeight[p];
        dblIdx = Array.concat(dblIdx, p);
        nDbl = nDbl + w;
    }
    return nDbl;
}

// Nuclei of the parasites in the nuclear stain srcT, with the nuclei
// settings of the detection dialog: the objects of pnMinArea to pnMaxArea
// um2 (a host nucleus is far larger; the apicoplast, also stained by DAPI,
// smaller). Centres in fX / fY. Returns how many.
function parasiteNuclei(srcT) {
    t = "CAC_pnuclei";
    if (isOpen(t)) { selectWindow(t); close(); }
    okP = prepChannel(srcT, t, nucleiMultiply, nucleiBgRolling, nucleiBlurSigma);
    lo = autoLow(t, nucleiThresholdMethod, nucleiCapF, nucleiBlurSigma);
    selectWindow(t);
    setThreshold(lo * nucleiThresholdFactor, 1e30);
    run("Convert to Mask");
    run("8-bit");
    run("Fill Holes");
    n = findObjects(t, pnMinArea, pnMaxArea, false);
    selectWindow(t);
    close();
    return n;
}

// Objects of the pole marker srcT: its own threshold (factor
// pmThresholdFactor) and sizes (pmMinArea to pmMaxArea um2). Centres in
// fX / fY. Returns how many.
function poleMarkerObjects(srcT) {
    t = "CAC_polemarker";
    if (isOpen(t)) { selectWindow(t); close(); }
    okP = prepChannel(srcT, t, pmMultiply, pmBgRolling, pmBlurSigma);
    lo = autoLow(t, pmThresholdMethod, pmCapF, pmBlurSigma);
    hi = autoHi;
    selectWindow(t);
    setThreshold(lo * pmThresholdFactor, hi);
    run("Convert to Mask");
    run("8-bit");
    run("Fill Holes");
    n = findObjects(t, pmMinArea, pmMaxArea, false);
    selectWindow(t);
    close();
    return n;
}

// Basal end of each parasite, from its nucleus (the nucleus of a
// tachyzoite lies in its basal half) and / or the pole marker (basal or
// apical, poleSide), as poleRef asks: pBas = 1 or 2 (end 1 or 2 of its
// axis), 0 when it cannot be told, the reason in pWhy. A reference closer
// to the centre than poleDeadPct % of the length tells nothing. With both
// references, one is enough; if they disagree, the parasite is not
// oriented. Needs parasiteAxes.
function orientParasites(nPar, imgT, blueT, poleT) {
    pBas = newArray(nPar);
    pWhy = newArray(nPar);
    pNuc = newArray(nPar);
    pMk  = newArray(nPar);
    dead = poleDeadPct / 100;
    rN = newArray(nPar);
    rM = newArray(nPar);
    sN = newArray(nPar);
    sM = newArray(nPar);
    selectWindow(imgT);
    getPixelSize(pu, pw0, ph0);
    if (poleNeedNuc) {
        if (blueT != "" && isOpen(blueT)) {
            nn = parasiteNuclei(blueT);
            nX = Array.copy(fX);
            nY = Array.copy(fY);
            ownN = ownerOf(nX, nY, nPar, 0, imgT);
            tN = newArray(nPar);
            for (k = 0; k < nn; k++) {
                p = ownN[k];
                if (p < 0) continue;
                pNuc[p] = pNuc[p] + 1;
                tN[p] = placeOn(p, nX[k], nY[k]);
            }
            for (p = 0; p < nPar; p++) {
                sN[p] = "";
                if (pNuc[p] == 0)     sN[p] = "no nucleus";
                else if (pNuc[p] > 1) sN[p] = "several nuclei";
                else if (abs(tN[p] - 0.5) < dead) sN[p] = "nucleus at the centre";
                else {
                    rN[p] = 1;
                    if (tN[p] > 0.5) rN[p] = 2;
                }
            }
        } else {
            for (p = 0; p < nPar; p++) sN[p] = "no nuclear stain";
        }
    }
    if (poleNeedMk) {
        if (poleT != "" && isOpen(poleT)) {
            nm = poleMarkerObjects(poleT);
            mX = Array.copy(fX);
            mY = Array.copy(fY);
            ownM = ownerOf(mX, mY, nPar, objTol / pw0, imgT);
            e1 = newArray(nPar);
            e2 = newArray(nPar);
            for (k = 0; k < nm; k++) {
                p = ownM[k];
                if (p < 0) continue;
                pMk[p] = pMk[p] + 1;
                t = placeOn(p, mX[k], mY[k]);
                if (t < 0.5 - dead) e1[p] = e1[p] + 1;
                if (t > 0.5 + dead) e2[p] = e2[p] + 1;
            }
            for (p = 0; p < nPar; p++) {
                sM[p] = "";
                mEnd = 0;
                if (pMk[p] == 0)                    sM[p] = "no pole marker";
                else if (e1[p] > 0 && e2[p] > 0)    sM[p] = "pole marker at both ends";
                else if (e1[p] == 0 && e2[p] == 0)  sM[p] = "pole marker at the centre";
                else if (e1[p] > 0)                 mEnd = 1;
                else                                mEnd = 2;
                // the basal end: the marker's own, or the other one
                if (mEnd > 0) {
                    rM[p] = mEnd;
                    if (poleSide == 1) rM[p] = 3 - mEnd;
                }
            }
        } else {
            for (p = 0; p < nPar; p++) sM[p] = "no pole marker channel";
        }
    }
    for (p = 0; p < nPar; p++) {
        pBas[p] = 0;
        pWhy[p] = "";
        if (poleNeedNuc && poleNeedMk) {
            if (rN[p] > 0 && rM[p] > 0) {
                if (rN[p] == rM[p]) pBas[p] = rN[p];
                else pWhy[p] = "nucleus and pole marker disagree";
            } else if (rN[p] > 0) {
                pBas[p] = rN[p];
            } else if (rM[p] > 0) {
                pBas[p] = rM[p];
            } else {
                why = "" + sN[p] + " / " + sM[p];
                pWhy[p] = why;
            }
        } else if (poleNeedNuc) {
            pBas[p] = rN[p];
            pWhy[p] = sN[p];
        } else {
            pBas[p] = rM[p];
            pWhy[p] = sM[p];
        }
        if (pBas[p] > 0) poleOri = poleOri + 1;
    }
    // why the others were not oriented, in words, for the Log
    whyL = newArray(0);
    whyC = newArray(0);
    for (p = 0; p < nPar; p++) {
        if (pBas[p] > 0) continue;
        f = -1;
        for (q = 0; q < whyL.length; q++) if (whyL[q] == pWhy[p]) f = q;
        if (f < 0) {
            whyL = Array.concat(whyL, pWhy[p]);
            whyC = Array.concat(whyC, 1);
        } else {
            whyC[f] = whyC[f] + 1;
        }
    }
    poleNote = "";
    for (q = 0; q < whyL.length; q++) {
        if (q > 0) poleNote = poleNote + ", ";
        wq = "" + whyL[q] + " " + whyC[q];
        poleNote = poleNote + wq;
    }
    selectWindow(imgT);
    run("Select None");
    return poleOri;
}

// Pole of the marker on each double-labelled parasite (pCls): from its
// counted objects (dblMode 1) - the pole holding more of them; as many at
// both: indeterminable - or from the centre of its marker-positive part
// (dblMode 0). pPlace keeps that place along the parasite. Weighted counts of
// the four classes in poleN[1..4].
function classifyPoles(maskT, nPar, imgT) {
    useW = 0;
    if (parWeight.length == nPar) useW = 1;
    selectWindow(maskT);
    getPixelSize(pu, pw0, ph0);
    for (z = 0; z < dblIdx.length; z++) {
        p = dblIdx[z];
        w = 1;
        if (useW == 1) w = parWeight[p];
        cls = 4;
        if (dblMode == 1) {
            nB = 0;
            nA = 0;
            nI = 0;
            sT = 0;
            nT = 0;
            for (k = 0; k < oX.length; k++) {
                if (oOwn[k] != p || oUse[k] != 1) continue;
                c = poleAt(p, oT[k]);
                oPole[k] = c;
                if (c == 1) nB = nB + 1;
                if (c == 2) nA = nA + 1;
                if (c == 3) nI = nI + 1;
                sT = sT + oT[k];
                nT = nT + 1;
            }
            if (nT > 0) pPlace[p] = sT / nT;
            if (pBas[p] == 0) {
                if (nI > 0) cls = 3;
            } else {
                if (nB > nA) cls = 1;
                else if (nA > nB) cls = 2;
                else if (nA > 0) cls = 3;
            }
        } else {
            // centre of the marker-positive part of the parasite: the
            // centre of mass of the mask (0 / 255) inside its outline
            selectWindow(maskT);
            roiManager("select", p);
            List.setMeasurements();
            xm = List.getValue("XM") / pw0;
            ym = List.getValue("YM") / ph0;
            List.clear();
            if (isNaN(xm) || isNaN(ym)) {
                cls = 4;
            } else {
                t = placeOn(p, xm, ym);
                pPlace[p] = t;
                cls = poleAt(p, t);
            }
        }
        pCls[p] = cls;
        poleN[cls] = poleN[cls] + w;
    }
    selectWindow(maskT);
    run("Select None");
    return 1;
}

// Watershed of a binary mask, in place. A watershed cuts an object between
// two peaks of its distance map (each pixel's distance to the edge of the
// object). ImageJ's Watershed cuts as soon as both peaks rise more than
// 0.5 px above the pass between them; tol <= 0.5 runs that very command.
// A larger tol, in px, only cuts at deeper notches: the distance map is
// segmented by Find Maxima with that tolerance, and the cuts are copied
// into the mask. The map is first raised by more than tol above a zero
// background, so that Find Maxima never reaches from one object into
// another: every object keeps at least its highest peak, and can only be
// cut less - never merged with a neighbour, never lost. Returns 1 when the
// tolerance was applied.
function watershedMask(maskTitle, tol) {
    selectWindow(maskTitle);
    run("Select None");
    if (tol <= 0.5) {
        run("Watershed");
        return 0;
    }
    if (tol > 100) tol = 100;
    edmT = maskTitle + "_edm";
    run("Duplicate...", "title=[" + edmT + "]");
    run("Distance Map");
    // With the default EDM output of Process > Binary > Options
    // ("Overwrite") the copy itself becomes the map; the other settings
    // put the map in a new image.
    newT = getTitle();
    if (newT != edmT) {
        selectWindow(edmT);
        close();
        selectWindow(newT);
        rename(edmT);
    }
    selectWindow(edmT);
    getRawStatistics(nPx, meanE, minE, maxE);
    if (maxE <= 0) {
        close();
        selectWindow(maskTitle);
        return 0;
    }
    lift = floor(tol) + 1;
    run("Add...", "value=" + lift);
    changeValues(lift, lift, 0);
    // Only the objects are segmented, not the background: much faster.
    getRawStatistics(nPx, meanE, minE, maxE);
    setThreshold(lift + 1, maxE);
    // The peaks are maxima of the map; with an inverting LUT, Find Maxima
    // looks for minima unless told the background is light.
    fm = "noise=" + tol + " output=[Segmented Particles] above";
    if (is("Inverting LUT")) fm = fm + " light";
    run("Find Maxima...", fm);
    segT = getTitle();
    if (segT == edmT) {
        print("   watershed tolerance could not be applied: standard watershed used");
        close();
        selectWindow(maskTitle);
        run("Watershed");
        return 0;
    }
    imageCalculator("AND", maskTitle, segT);
    selectWindow(segT);
    close();
    selectWindow(edmT);
    close();
    selectWindow(maskTitle);
    return 1;
}


// ======================================================================
//  FUNCTIONS - image list
// ======================================================================

// Asks for the input: the folder of experiments, a single folder, or a
// single file (the first dialog says which). Done once, before the second
// dialog. Sets rootFolder, and inFile for a single file.
function pickInput() {
    if (inputPicked) return 1;
    sp = File.separator;
    inFile = "";
    if (tiffMode) {
        rootFolder = getDirectory("Select the folder containing the experiments");
    } else if (inputMode == "FILE") {
        inFile = File.openDialog("Select an image file");
        rootFolder = File.getParent(inFile) + sp;
    } else if (inputMode == "FOLDER") {
        rootFolder = getDirectory("Select the folder containing the images");
    } else {
        rootFolder = getDirectory("Select the folder containing the experiments");
    }
    if (!endsWith(rootFolder, sp) && !endsWith(rootFolder, "/")) rootFolder = rootFolder + sp;
    inputPicked = true;
    return 1;
}

// Lists the images to process, in imgPaths / imgMans / imgConds, with
// serList / serTot / serName for files holding several positions or
// series: each becomes an entry of its own (a .nd always; other files when
// the first dialog asks for it, through Bio-Formats). Done once. Returns
// how many entries were found.
function collectImages() {
    if (imagesCollected) return imgPaths.length;
    okP = pickInput();
    sp = File.separator;

    ml = newArray(0);
    cl = newArray(0);
    pl = newArray(0);

    if (tiffMode) {
        scanTiffRoot(rootFolder);
        for (q = 0; q < tKey.length; q++) {
            ml = Array.concat(ml, tExp[q]);
            cl = Array.concat(cl, tCov[q]);
            pp = firstPath(tBlue[q], tGreen[q], tRed[q]);
            pl = Array.concat(pl, pp);
        }
    }
    else if (inputMode == "FILE") {
        cnFile = cleanName(rootFolder);
        ml = Array.concat(ml, "-");
        cl = Array.concat(cl, cnFile);
        pl = Array.concat(pl, inFile);
    }
    else if (inputMode == "FOLDER") {
        cond1 = cleanName(rootFolder);
        fl = getFileList(rootFolder);
        Array.sort(fl);
        ndB = ndBases(fl);
        for (a = 0; a < fl.length; a++) {
            ok = isImageFile(fl[a]);
            own = ndOwned(fl[a], ndB);
            if (ok == 1 && own == 0) {
                ml = Array.concat(ml, "-");
                cl = Array.concat(cl, cond1);
                pl = Array.concat(pl, rootFolder + fl[a]);
            }
        }
    }
    else {
        // Recursive scan: the last two folders above a file give the
        // experiment and the condition. Depth-tolerant.
        scanDir(rootFolder, "", "");
        ml = mAcc;
        cl = cAcc;
        pl = pAcc;
    }

    // One entry per position of a MetaMorph .nd, read from the .nd itself.
    // Other files holding several series are expanded only when asked, with
    // the Bio-Formats macro extensions - loaded only then, so that a plain
    // run never depends on them.
    expP = newArray(0);
    expM = newArray(0);
    expC = newArray(0);
    expS = newArray(0);
    expT = newArray(0);
    expN = newArray(0);
    if (pl.length > 0) {
        anyBF = 0;
        if (splitSeries && tiffMode == false) {
            for (q = 0; q < pl.length; q++) {
                lcq = toLowerCase(pl[q]);
                isNd = endsWith(lcq, ".nd");
                isTf = 0;
                if (endsWith(lcq, ".tif") || endsWith(lcq, ".tiff")) isTf = 1;
                if (indexOf(lcq, ".ome.tif") >= 0) isTf = 0;
                if (openMethod == "imagej") isTf = 1;
                if (isNd == false && isTf == 0) anyBF = 1;
                if (isNd && (openMethod == "importer" || openMethod == "ext")) anyBF = 1;
            }
        }
        if (anyBF == 1) run("Bio-Formats Macro Extensions");

        for (q = 0; q < pl.length; q++) {
            ns = 1;
            names = newArray(1);
            names[0] = "";
            lcq = toLowerCase(pl[q]);
            nativeNd = 0;
            if (endsWith(lcq, ".nd") && (openMethod == "auto" || openMethod == "imagej")) nativeNd = 1;
            if (tiffMode == false && nativeNd == 1) {
                okN = ndParse(pl[q]);
                if (okN == 1) {
                    ns = ndNPos;
                    names = Array.copy(ndStages);
                }
            } else if (anyBF == 1) {
                isTf = 0;
                if (endsWith(lcq, ".tif") || endsWith(lcq, ".tiff")) isTf = 1;
                if (indexOf(lcq, ".ome.tif") >= 0) isTf = 0;
                if (openMethod == "imagej") isTf = 1;
                if (isTf == 0) {
                    fpq = fwd(pl[q]);
                    Ext.setId(fpq);
                    Ext.getSeriesCount(ns);
                    if (ns < 1) ns = 1;
                }
            }
            for (sIdx = 1; sIdx <= ns; sIdx++) {
                expP = Array.concat(expP, pl[q]);
                expM = Array.concat(expM, ml[q]);
                expC = Array.concat(expC, cl[q]);
                expS = Array.concat(expS, sIdx);
                expT = Array.concat(expT, ns);
                nmS = "";
                if (names.length >= sIdx) nmS = names[sIdx - 1];
                expN = Array.concat(expN, nmS);
            }
        }
    }
    imgPaths = expP;
    imgMans  = expM;
    imgConds = expC;
    serList  = expS;
    serTot   = expT;
    serName  = expN;
    imagesCollected = true;
    nImg = imgPaths.length;
    return nImg;
}

// Removes from the image list what an earlier run wrote into the output
// folder dir. Returns how many entries are left. An output folder inside the
// input folder: everything under it. The input folder itself (or a folder
// holding it): only the folders this tool writes there - otherwise every
// image would be left out.
function dropUnder(dir) {
    d = fwd(dir);
    if (!endsWith(d, "/")) d = d + "/";
    d = toLowerCase(d);
    r = toLowerCase(fwd(rootFolder));
    if (!endsWith(r, "/")) r = r + "/";
    pre = newArray(d);
    if (startsWith(r, d))
        pre = newArray(d + "stacks_", d + "data/", d + "hyperstacks/", d + "projections/",
                       d + "merges/", d + "projection merges/");
    kP = newArray(0); kM = newArray(0); kC = newArray(0);
    kS = newArray(0); kT = newArray(0); kN = newArray(0);
    for (q = 0; q < imgPaths.length; q++) {
        pq = toLowerCase(fwd(imgPaths[q]));
        isOut = 0;
        for (u = 0; u < pre.length; u++) if (startsWith(pq, pre[u])) isOut = 1;
        if (isOut == 1) continue;
        kP = Array.concat(kP, imgPaths[q]);
        kM = Array.concat(kM, imgMans[q]);
        kC = Array.concat(kC, imgConds[q]);
        kS = Array.concat(kS, serList[q]);
        kT = Array.concat(kT, serTot[q]);
        nmQ = serName[q];
        kN = Array.concat(kN, nmQ);
    }
    droppedOut = imgPaths.length - kP.length;
    imgPaths = kP; imgMans = kM; imgConds = kC;
    serList = kS; serTot = kT; serName = kN;
    return imgPaths.length;
}

// No image found: the folder contents go to the Log, and the macro stops.
function noImageExit() {
    print("Contents of " + rootFolder + " :");
    dbg = getFileList(rootFolder);
    for (a = 0; a < dbg.length; a++) print("   " + dbg[a]);
    ft = fmtText();
    exit("No " + ft + " file found under " + rootFolder +
         "\nSee the Log window for the folder contents. The file types are set\n" +
         "in the first dialog.");
}


// ======================================================================
//  FUNCTIONS - Calibrate
// ======================================================================
// One object class, one or several images. A preview stack shows, for each
// image, the raw channel, then the same view with what is counted drawn
// over it; a panel holds every setting of that class. OK redraws the preview with the values of the
// panel, until "Keep" sends them back to the detection dialog (or
// "Discard" restores the ones it had). A macro panel cannot redraw while a
// slider is being dragged: it redraws at OK, or Enter.
//
// Drawn on the counted views:
//   orange   everything above the threshold (outlined only in orange:
//            rejected by size or shape)
//   nuclei   cyan counted, magenta cluster counted as several cells,
//            grey left out at the image border
//   parasites  magenta counted, white cluster counted as several parasites
//   double   white double-labelled, cyan the other parasites; orange is
//            then the area positive for the marker

// Is this ImageJ at least version want ("1.52a")? Fiji can report
// "2.x.y/1.54f": only the part after the last "/" is compared.
function versionAtLeast(want) {
    v = getVersion();
    sl = lastIndexOf(v, "/");
    if (sl >= 0) v = substring(v, sl + 1);
    n = lengthOf(want);
    if (lengthOf(v) < n) return 0;
    for (q = 0; q < n; q++) {
        a = charCodeAt(v, q);
        b = charCodeAt(want, q);
        if (a > b) return 1;
        if (a < b) return 0;
    }
    return 1;
}

// The next dialog field goes on the same row as the previous one, from
// ImageJ 1.51r; with an older version it simply gets a row of its own.
function sameRow() {
    if (versionAtLeast("1.51r")) Dialog.addToSameRow();
    return 1;
}

function calibrate(what, src) {
    calSrc = src;
    calSave();
    // the random choices draw from the list of images to process
    if (src != calSrcModes[0]) {
        nFound = collectImages();
        if (nFound == 0) {
            showMessage("Calibrate", "No image to pick from in this folder.");
            return 0;
        }
    }
    if (useBatch) setBatchMode(true);
    calCloseTemp();
    okC = calLoad(what, src);
    if (okC == 0) {
        calCloseTemp();
        if (useBatch) setBatchMode(false);
        return 0;
    }
    calCacheInit();
    calPrevID = calPreview(what);
    calLastKey = "";
    calRedraw(what);

    done = 0;
    while (done == 0) {
        calDialog(what);
        if (calAction == calActKeep) {
            done = 1;
        } else if (calAction == calActDiscard) {
            calRestore();
            done = 1;
        } else {
            calUpdate(what);
            if (calAction == calActLook)
                waitForUser("Zoom and scroll through the preview freely.\n" +
                            "Click OK to come back to the settings.");
        }
    }

    // The preview stays open, showing what the settings now in force count.
    calUpdate(what);
    calCloseTemp();
    selectImage(calPrevID);
    setSlice(2);
    if (useBatch) setBatchMode(false);
    return 1;
}

// Opens the image(s) to calibrate on: image k leaves its channels as
// CAL_BLUE_k / CAL_GREEN_k / CAL_RED_k, projected and calibrated as in the
// batch. Sets calN and the labels. Returns 1 if at least one image is
// usable.
function calLoad(what, src) {
    calN = 0;
    calLab  = newArray(0);
    calPxV  = newArray(0);
    calPxU  = newArray(0);
    calPxS  = newArray(0);
    calSkip = 0;
    calMem  = 0;

    if (src == calSrcModes[0]) {
        if (tiffMode) okI = calOpenChosenTiff(what, 1);
        else          okI = calOpenChosen(what, 1);
        if (okI == 1) calKeep(calChosenLab);
        if (calN == 0) return 0;
        return 1;
    }

    picks = calPickIndices(src);
    nvL = 2;
    if (what == "double") nvL = 3;
    chanL = "CAL_BLUE_";
    if (what == "parasites") chanL = "CAL_GREEN_";
    if (what == "double")    chanL = "CAL_RED_";
    calNeed = 0;
    for (j = 0; j < picks.length; j++) {
        i = picks[j];
        k = calN + 1;
        showStatus("Calibrate: opening image " + (j + 1) + " / " + picks.length);
        showProgress(j + 1, picks.length);
        if (tiffMode) okI = calOpenTiffIndex(i, what, k);
        else          okI = calOpenFile(imgPaths[i], i, what, k);
        if (okI == 1) {
            lab = imgConds[i];
            if (imgMans[i] != "-") lab = imgMans[i] + " / " + lab;
            nmI = File.getName(imgPaths[i]);
            if (tiffMode) nmI = tBase[i];
            else if (serTot[i] > 1) nmI = nmI + " (position " + serList[i] + ")";
            lab = lab + " / " + nmI;
            // The preview holds, per image, nvL RGB views and the overlay
            // canvas: about 5 x nvL bytes per pixel; the corrected channel
            // and mask kept to redraw faster, 3 more (6 for the double
            // labelling: parasites and marker). Images that would not fit
            // in memory are left out (the first one is always kept).
            selectWindow(chanL + k);
            keptB = 3;
            if (what == "double") keptB = 6;
            need = getWidth() * getHeight() * (5 * nvL + keptB);
            call("java.lang.System.gc");
            usedM = parseFloat(IJ.currentMemory());   // both come as strings
            maxM  = parseFloat(IJ.maxMemory());
            if (calN >= 1 && usedM + calNeed + need > 0.8 * maxM) {
                calDropImage(k);
                calMem = picks.length - j;
                j = picks.length;
            } else {
                calNeed = calNeed + need;
                calKeep(lab);
            }
        } else {
            calDropImage(k);
            calSkip = calSkip + 1;
        }
    }
    if (calN == 0) {
        showMessage("Calibrate", "None of the images picked could be opened with the channels\n" +
                    "needed. Check the channel numbers in the previous dialogs\n" +
                    "(and the Log, for files that could not be opened).");
        return 0;
    }
    return 1;
}

// Records one image as usable, with the pixel size it is measured with.
function calKeep(lab) {
    calN = calN + 1;
    calLab = Array.concat(calLab, lab);
    calPxV = Array.concat(calPxV, pxUsed);
    calPxU = Array.concat(calPxU, pxUnit);
    calPxS = Array.concat(calPxS, pxSource);
}

// Indices into the image list: one at random, or one at random from each
// experiment x condition.
function calPickIndices(src) {
    n = imgPaths.length;
    if (src == calSrcModes[1]) {
        r = floor(random() * n);
        if (r >= n) r = n - 1;
        one = newArray(1);
        one[0] = r;
        return one;
    }
    keys = newArray(0);
    for (i = 0; i < n; i++) {
        key = imgMans[i] + "|" + imgConds[i];
        known = 0;
        for (u = 0; u < keys.length; u++) {
            if (keys[u] == key) known = 1;
        }
        if (known == 0) keys = Array.concat(keys, key);
    }
    out = newArray(keys.length);
    for (u = 0; u < keys.length; u++) {
        cand = newArray(0);
        for (i = 0; i < n; i++) {
            key = imgMans[i] + "|" + imgConds[i];
            if (key == keys[u]) cand = Array.concat(cand, i);
        }
        r = floor(random() * cand.length);
        if (r >= cand.length) r = cand.length - 1;
        out[u] = cand[r];
    }
    return out;
}

// The image picked by hand (multi-channel file).
function calOpenChosen(what, k) {
    p = File.openDialog("Calibrate " + what + ": choose one image");
    calChosenLab = File.getName(p);
    okF = calOpenFile(p, 1000000000, what, k);   // index beyond any series list
    return okF;
}

// Opens file p (series of entry idx) and leaves its channels as
// CAL_BLUE_k, CAL_GREEN_k and CAL_RED_k.
function calOpenFile(p, idx, what, k) {
    okO = openFile(p, idx);
    if (okO == 0) {
        print("Calibrate: " + File.getName(p) + " could not be opened: " + openErr);
        if (calSrc == calSrcModes[0])
            showMessage("Calibrate", "This image could not be opened:\n" + openErr);
        return 0;
    }
    src = getTitle();
    // as in the batch: first time point, maximum projection of the Z planes
    proj = countView(src);
    selectWindow(proj);
    okPx = applyPixelSize();

    uB = chBLUE;
    uG = chGREEN;
    uR = chRED;
    if (autoDetectChannels) {
        detectChannels(proj);
        okDc = 1;
        if (chBLUE > 0 && dcBLUE == 0)   okDc = 0;
        if (chGREEN > 0 && dcGREEN == 0) okDc = 0;
        if (chRED > 0 && dcRED == 0)     okDc = 0;
        if (okDc == 1) {
            uB = dcBLUE;
            uG = dcGREEN;
            uR = dcRED;
        }
    }
    selectWindow(proj);
    getDimensions(pw, ph, pnc, pnz, pnt);
    if (pnc > 1) {
        run("Split Channels");
        // only the channels this calibration uses are kept
        if (what == "nuclei") calTake("C" + uB + "-" + proj, "CAL_BLUE_" + k);
        if (what != "nuclei") calTake("C" + uG + "-" + proj, "CAL_GREEN_" + k);
        if (what == "double") calTake("C" + uR + "-" + proj, "CAL_RED_" + k);
        // the references of the poles, when the option is on
        if (what == "double" && poleNeedMk && chPOLE > 0)
            calTake("C" + chPOLE + "-" + proj, "CAL_POLE_" + k);
        if (what == "double" && poleNeedNuc) calTake("C" + uB + "-" + proj, "CAL_BLUE_" + k);
        for (c = 1; c <= pnc; c++) {
            t = "C" + c + "-" + proj;
            if (isOpen(t)) {
                selectWindow(t);
                close();
            }
        }
    } else {
        // a single-channel file is taken as the channel being calibrated
        selectWindow(proj);
        if (what == "nuclei")    rename("CAL_BLUE_" + k);
        if (what == "parasites") rename("CAL_GREEN_" + k);
        if (what == "double")    rename("CAL_RED_" + k);
    }
    okH = calHas(what, k);
    return okH;
}

// One TIFF per channel, picked by hand: the file gives the coverslip (W)
// and the field (P); every channel file of that field in the same folder
// is used.
function calOpenChosenTiff(what, k) {
    p = File.openDialog("Calibrate " + what + ": choose one TIFF of the field (any channel)");
    dir = File.getParent(p) + File.separator;
    nm = File.getName(p);
    calChosenLab = nm;
    stem = stripExt(nm);
    wPos = lastIndexOf(stem, "--W");
    pPos = -1;
    if (wPos >= 0) pPos = indexOf(stem, "--P", wPos);
    if (wPos < 0 || pPos < 0) {
        showMessage("Calibrate", "This file name holds no --W...--P... fields:\n" + nm);
        return 0;
    }
    wNum = digitsAt(stem, wPos + 3);
    pNum = digitsAt(stem, pPos + 3);
    calChosenLab = "W" + wNum + "_P" + pNum;

    fl = getFileList(dir);
    jB = "";
    jG = "";
    jR = "";
    jP = "";
    for (f = 0; f < fl.length; f++) {
        lc = toLowerCase(fl[f]);
        isTif = 0;
        if (endsWith(lc, ".tif"))  isTif = 1;
        if (endsWith(lc, ".tiff")) isTif = 1;
        if (isTif == 0) continue;
        st = stripExt(fl[f]);
        w2 = lastIndexOf(st, "--W");
        if (w2 < 0) continue;
        p2 = indexOf(st, "--P", w2);
        if (p2 < 0) continue;
        wn2 = digitsAt(st, w2 + 3);
        pn2 = digitsAt(st, p2 + 3);
        if (wn2 != wNum) continue;
        if (pn2 != pNum) continue;
        role = chanRole(st);
        full = dir + fl[f];
        if (role == "BLUE")  jB = addPath(jB, full);
        if (role == "GREEN") jG = addPath(jG, full);
        if (role == "RED")   jR = addPath(jR, full);
        if (role == "POLE")  jP = addPath(jP, full);
    }
    if (what == "nuclei") okB = openTiffChannel(jB, "CAL_BLUE_" + k);
    if (what != "nuclei") okG = openTiffChannel(jG, "CAL_GREEN_" + k);
    if (what == "double") okR = openTiffChannel(jR, "CAL_RED_" + k);
    if (what == "double" && poleNeedNuc) okB = openTiffChannel(jB, "CAL_BLUE_" + k);
    if (what == "double" && poleNeedMk)  okPm = openTiffChannel(jP, "CAL_POLE_" + k);
    okH = calHas(what, k);
    return okH;
}

// One TIFF per channel, acquisition i of the scanned list.
function calOpenTiffIndex(i, what, k) {
    if (what == "nuclei") okB = openTiffChannel(tBlue[i], "CAL_BLUE_" + k);
    if (what != "nuclei") okG = openTiffChannel(tGreen[i], "CAL_GREEN_" + k);
    if (what == "double") okR = openTiffChannel(tRed[i], "CAL_RED_" + k);
    if (what == "double" && poleNeedNuc) okB = openTiffChannel(tBlue[i], "CAL_BLUE_" + k);
    if (what == "double" && poleNeedMk)  okPm = openTiffChannel(tPole[i], "CAL_POLE_" + k);
    okH = calHas(what, k);
    return okH;
}

function calTake(src, dst) {
    if (isOpen(src)) {
        selectWindow(src);
        rename(dst);
    }
}

// 1 if the channels needed for image k are open. Only says which one is
// missing when a single image was asked for.
function calHas(what, k) {
    miss = "";
    if (what == "nuclei" && isOpen("CAL_BLUE_" + k) == false) miss = "nuclear stain";
    if (what != "nuclei" && isOpen("CAL_GREEN_" + k) == false) miss = "parasite label";
    if (what == "double" && isOpen("CAL_RED_" + k) == false) miss = "second marker";
    if (miss == "") return 1;
    if (calSrc == calSrcModes[0])
        showMessage("Calibrate", "The " + miss + " channel was not found in this image.\n" +
                    "Check the channel numbers in the previous dialogs, or pick another image.");
    return 0;
}

// Closes what image k left open when it turned out unusable.
function calDropImage(k) {
    sfx = "_" + k;
    lst = getList("image.titles");
    for (w = lst.length - 1; w >= 0; w--) {
        if (startsWith(lst[w], "CAL_") && endsWith(lst[w], sfx)) {
            selectWindow(lst[w]);
            close();
        }
    }
}

// Preview stack, built once: for each image, the raw channel in its colour,
// then the view(s) the counted objects are drawn on. Images of different
// sizes sit in the top-left corner of the largest. Returns its image ID.
function calPreview(what) {
    nv = 2;
    if (what == "double") nv = 3;
    calNV = nv;
    base = "CAL_BLUE_";
    if (what == "parasites") base = "CAL_GREEN_";
    if (what == "double")    base = "CAL_RED_";
    mw = 0;
    mh = 0;
    for (k = 1; k <= calN; k++) {
        selectWindow(base + k);
        mw = maxOf(mw, getWidth());
        mh = maxOf(mh, getHeight());
    }
    newImage("CAL_prev", "RGB black", mw, mh, calN * nv);
    calPrevID = getImageID();
    // calibrated as the (first) image it shows
    if (calPxV.length > 0) setVoxelSize(calPxV[0], calPxV[0], calPxV[0], "micron");
    calViews(what);
    selectImage(calPrevID);
    setSlice(2);
    rename("Calibration QC - " + what);
    id = getImageID();
    if (useBatch) setBatchMode("show");
    return id;
}

// The views of every calibration image, drawn into the preview (titled
// CAL_prev meanwhile) with the brightness in force. Its overlay - the
// counted objects - is left as it is.
function calViews(what) {
    nv = calNV;
    for (k = 1; k <= calN; k++) {
        s0 = (k - 1) * nv;
        lab = calLab[k - 1];
        if (what == "nuclei") {
            calView("CAL_BLUE_" + k, "Blue", "CAL_v", qcGainBlue);
            calPut("CAL_v", s0 + 1, lab + "  -  raw nuclear stain");
            calPut("CAL_v", s0 + 2, lab + "  -  counted nuclei");
        }
        if (what == "parasites") {
            calView("CAL_GREEN_" + k, "Green", "CAL_v", qcGainGreen);
            calPut("CAL_v", s0 + 1, lab + "  -  raw parasite label");
            calPut("CAL_v", s0 + 2, lab + "  -  counted parasites");
        }
        if (what == "double") {
            calView("CAL_RED_" + k, "Red", "CAL_v", qcGainRed);
            calPut("CAL_v", s0 + 1, lab + "  -  raw marker");
            calPut("CAL_v", s0 + 2, lab + "  -  marker alone, double-labelled drawn");
            selectWindow("CAL_v");
            close();
            calMergeView("CAL_RED_" + k, "CAL_GREEN_" + k, "CAL_v");
            calPut("CAL_v", s0 + 3, lab + "  -  marker + parasites, double-labelled drawn");
        }
        selectWindow("CAL_v");
        close();
    }
    calViewGains = calGainKey();
}

// New brightness: the views are redrawn in the preview already open,
// without counting anything again.
function calRefreshViews(what) {
    wasBatch = is("Batch Mode");
    if (wasBatch == false) setBatchMode(true);
    selectImage(calPrevID);
    prevT = getTitle();
    rename("CAL_prev");
    calViews(what);
    selectImage(calPrevID);
    rename(prevT);
    if (wasBatch == false) setBatchMode(false);
}

// Brings the preview up to date with the settings in force, redoing only
// what changed: the views for a new brightness, the counting for a new
// detection setting (itself redoing only the steps that setting affects).
function calUpdate(what) {
    if (isOpen(calPrevID) == false) {
        // the preview was closed meanwhile: built again
        calPrevID = calPreview(what);
        calLastKey = "";
    } else {
        gk = calGainKey();
        if (gk != calViewGains) calRefreshViews(what);
    }
    dk = calDetKey(what);
    if (dk != calLastKey) calRedraw(what);
}

// The brightness of the three channels, as one string.
function calGainKey() {
    return "" + qcGainBlue + "|" + qcGainGreen + "|" + qcGainRed;
}

// Every setting the counting of this class depends on, as one string.
function calDetKey(what) {
    kn = "" + d2s(nucleiThresholdFactor, 6) + "|" + d2s(nucleiMinArea, 6) + "|" +
         nucleiBgRolling + "|" + d2s(nucleiBlurSigma, 6) + "|" + nucleiCirc + "|" +
         nucleiThresholdMethod + "|" + nucleiEdgeMode + "|" + nucleiWatershed + "|" +
         d2s(nucleiWsTol, 6) + "|" + splitBigNuclei + "|" + d2s(nucleiDoubletFactor, 6) +
         "|" + nucleiMaxSplit + "|" + d2s(nucleiFragFactor, 6) + "|" + d2s(nucleiCapF, 6);
    kp = "" + d2s(greenThresholdFactor, 6) + "|" + d2s(greenMinArea, 6) + "|" +
         d2s(greenMaxArea, 6) + "|" + greenBgRolling + "|" + d2s(greenBlurSigma, 6) + "|" +
         greenCirc + "|" + greenThresholdMethod + "|" + greenWatershed + "|" +
         d2s(greenWsTol, 6) + "|" + d2s(greenCapF, 6) + "|" + splitBigParasites + "|" +
         d2s(greenDoubletFactor, 6) + "|" + greenMaxSplit;
    kr = "" + d2s(redThresholdFactor, 6) + "|" + d2s(minOverlapPercent, 6) + "|" +
         redBgRolling + "|" + d2s(redBlurSigma, 6) + "|" + redThresholdMethod + "|" +
         d2s(redCapF, 6);
    // objects, places, poles (and the nuclei settings their nuclei are found with)
    ko = "" + dblMode + "|" + d2s(objMinArea, 6) + "|" + d2s(objMaxArea, 6) + "|" +
         d2s(objTol, 6) + "|" + objMinN + "|" + objWatershed + "|" + d2s(encMinArea, 6) + "|" +
         d2s(encMaxArea, 6) + "|" + posMode + "|" + d2s(centrePct, 6) + "|" + d2s(endPct, 6) +
         "|" + d2s(pnMinArea, 6) + "|" + d2s(pnMaxArea, 6) + "|" + d2s(pmThresholdFactor, 6) +
         "|" + d2s(pmMinArea, 6) + "|" + d2s(pmMaxArea, 6) + "|" + d2s(poleDeadPct, 6);
    if (what == "nuclei")    return kn;
    if (what == "parasites") return kp;
    return kp + "|" + kr + "|" + ko + "|" + kn;
}

// Calibrate keeps, for each image, the background-corrected channel and its
// binary mask, with the settings they were made with (slots: nuclei,
// parasites, marker, for images 1 to calN).
function calCacheInit() {
    calSlots = calN + 1;
    nCs = 3 * calSlots;
    calKeyP = newArray(nCs);
    calKeyM = newArray(nCs);
    calThr  = newArray(nCs);
    calThrA = newArray(nCs);
    calHit  = newArray(nCs);
    calNote = newArray(nCs);
    calMedT = newArray(nCs);
    calMaxT = newArray(nCs);
    for (q = 0; q < nCs; q++) {
        calKeyP[q] = "";
        calKeyM[q] = "";
        calNote[q] = "";
    }
}

// Title of the binary mask of class cls ("n" nuclei, "p" parasites, "m"
// marker) for calibration image k, made with the settings in force. Each
// step is redone only when one of its settings changed since it was last
// made; the steps are those of the batch (nucleiPre, nucleiMask...). The
// thresholds a mask was made with are restored with it.
function calMaskOf(cls, k) {
    if (cls == "n") {
        src = "CAL_BLUE_" + k;
        slot = k;
        kp = "" + nucleiBgRolling + "|" + d2s(nucleiBlurSigma, 6);
        km = kp + "|" + nucleiThresholdMethod + "|" + d2s(nucleiThresholdFactor, 6) + "|" +
             nucleiWatershed + "|" + d2s(nucleiWsTol, 6) + "|" + d2s(nucleiCapF, 6);
    } else if (cls == "p") {
        src = "CAL_GREEN_" + k;
        slot = calSlots + k;
        kp = "" + greenBgRolling + "|" + d2s(greenBlurSigma, 6);
        km = kp + "|" + greenThresholdMethod + "|" + d2s(greenThresholdFactor, 6) + "|" +
             greenWatershed + "|" + d2s(greenWsTol, 6) + "|" + d2s(greenCapF, 6);
    } else {
        src = "CAL_RED_" + k;
        slot = 2 * calSlots + k;
        kp = "" + redBgRolling + "|" + d2s(redBlurSigma, 6);
        km = kp + "|" + redThresholdMethod + "|" + d2s(redThresholdFactor, 6) + "|" +
             d2s(redCapF, 6);
    }
    preT = "CAL_" + cls + "pre_" + k;
    binT = "CAL_" + cls + "bin_" + k;
    if (calKeyP[slot] != kp || isOpen(preT) == false) {
        if (isOpen(preT)) { selectWindow(preT); close(); }
        if (cls == "n") nucleiPre(src, preT);
        if (cls == "p") parasitePre(src, preT);
        if (cls == "m") markerPre(src, preT);
        calKeyP[slot] = kp;
        calKeyM[slot] = "";
    }
    if (calKeyM[slot] != km || isOpen(binT) == false) {
        if (isOpen(binT)) { selectWindow(binT); close(); }
        selectWindow(preT);
        run("Select None");
        run("Duplicate...", "title=[" + binT + "]");
        if (cls == "n") {
            nucleiMask(binT);
            calThrA[slot] = nucThrAuto;
            calThr[slot]  = nucThr;
        }
        if (cls == "p") {
            parasiteMask(binT);
            calThrA[slot] = parThrAuto;
            calThr[slot]  = parThr;
        }
        if (cls == "m") {
            markerMask(binT);
            calThrA[slot] = dblThrAuto;
            calThr[slot]  = dblThr;
        }
        calHit[slot]  = capHit + 2 * spikeHit;
        calNote[slot] = thrNote;
        calMedT[slot] = thrMed;
        calMaxT[slot] = thrMax;
        calKeyM[slot] = km;
    }
    // what the threshold left out of its calculation, as if just made
    capHit   = calHit[slot] % 2;
    spikeHit = floor(calHit[slot] / 2);
    thrNote  = calNote[slot];
    thrMed   = calMedT[slot];
    thrMax   = calMaxT[slot];
    if (cls == "n") {
        nucThrAuto = calThrA[slot];
        nucThr     = calThr[slot];
    }
    if (cls == "p") {
        parThrAuto = calThrA[slot];
        parThr     = calThr[slot];
    }
    if (cls == "m") {
        dblThrAuto = calThrA[slot];
        dblThr     = calThr[slot];
    }
    return binT;
}

// Copies the view into slice sl of the preview and labels that slice.
function calPut(viewT, sl, lab) {
    selectWindow("CAL_prev");
    setSlice(sl);
    imageCalculator("Copy", "CAL_prev", viewT);
    selectWindow("CAL_prev");
    setSlice(sl);
    setMetadata("Label", lab);
}

// Display copy of one channel, in the given colour and brightness,
// flattened to RGB.
function calView(src, lut, dst, gain) {
    selectWindow(src);
    run("Select None");
    run("Duplicate...", "title=[" + dst + "]");
    run(lut);
    qcRange(gain);
    run("RGB Color");
}

// Red + green display merge, as RGB, each with its brightness.
function calMergeView(redT, greenT, dst) {
    selectWindow(redT);
    run("Select None");
    run("Duplicate...", "title=[CAL_mr]");
    qcRange(qcGainRed);
    selectWindow(greenT);
    run("Select None");
    run("Duplicate...", "title=[CAL_mg]");
    qcRange(qcGainGreen);
    // no "create": the merge comes out as a plain RGB image
    run("Merge Channels...", "c1=[CAL_mr] c2=[CAL_mg]");
    rename(dst);
}

// Runs the detection on every calibration image with the settings in force
// and redraws the overlay of the preview. The counts go to calSummary,
// shown in the panel.
function calRedraw(what) {
    // The overlay is always built on a hidden blank stack of the same size,
    // in batch mode, then copied onto the preview in one go - also when
    // "Batch mode" is unticked in the first dialog. Drawn straight onto the
    // displayed preview, ImageJ repaints each outline while its colour and
    // width are being set, and some outlines kept the wrong ones (a border
    // nucleus drawn thin under the orange outline, for instance).
    wasBatch = is("Batch Mode");
    if (wasBatch == false) setBatchMode(true);
    selectImage(calPrevID);
    run("Select None");
    iw = getWidth();
    ih = getHeight();
    nsl = nSlices;
    fs = maxOf(10, round(iw / 90));
    lw = maxOf(1, round(iw / 500));
    newImage("CAL_canvas", "8-bit black", iw, ih, nsl);
    calDrawID = getImageID();
    selectImage(calDrawID);
    Overlay.remove;

    tA = 0;
    tB = 0;
    tC = 0;
    tD = 0;
    tE = 0;
    // fragments are only mentioned when the rule is on
    frOn = 0;
    if (nucleiFragFactor > 0) frOn = 1;
    // what each threshold left out of its calculation
    calCapHits = 0;
    calSpikeHits = 0;
    calPoleT = newArray(6);
    calObjT = 0;
    lines = "";
    for (k = 1; k <= calN; k++) {
        showStatus("Calibrate: counting image " + k + " / " + calN);
        showProgress(k, calN);
        roiManager("Reset");
        sl = (k - 1) * calNV + 2;
        if (what == "nuclei")    calRedrawNuclei(k, sl, fs, lw);
        if (what == "parasites") calRedrawParasites(k, sl, fs, lw);
        if (what == "double")    calRedrawDouble(k, sl, fs, lw);
        tA = tA + calCntA;
        tB = tB + calCntB;
        tC = tC + calCntC;
        tD = tD + calCntD;
        tE = tE + calCntE;
        if (calN > 1) {
            if (what == "nuclei") {
                // "" first: a number first would make the whole line a number (NaN)
                ln = "" + calCntA + " cells (" + calCntB + " nuclei, " + calCntC + " clusters";
                if (frOn == 1) ln = ln + ", " + calCntE + " fragments";
                ln = ln + ")";
            }
            if (what == "parasites") {
                if (splitBigParasites)
                    ln = "" + calCntA + " parasites (" + calCntB + " objects, " + calCntD +
                         " clusters; threshold " + calCntC + ")";
                else
                    ln = "" + calCntA + " parasites (threshold " + calCntC + ")";
            }
            if (what == "double")
                ln = "" + calCntA + " / " + calCntB + " double-labelled (marker threshold " +
                     calCntC + ")" + calImgLine;
            // the panel stays short: four images there, all of them in the Log
            if (k <= 4) lines = lines + "\n   " + calLab[k - 1] + ":  " + ln;
            print("Calibrate " + what + "   " + calLab[k - 1] + ":  " + ln);
        }
    }
    if (calN > 4) lines = lines + "\n   ... and " + (calN - 4) + " more image(s): see the Log";

    if (what == "nuclei") {
        calSummary = "COUNTED: " + tA + " host cells from " + tB + " nuclei (" + tC +
                     " clusters";
        if (frOn == 1) calSummary = calSummary + ", " + tE + " fragments";
        calSummary = calSummary + ")";
        if (tD > 0) calSummary = calSummary + ", " + tD + " at the border left out";
    }
    if (what == "parasites") {
        calSummary = "COUNTED: " + tA + " parasites";
        if (splitBigParasites)
            calSummary = calSummary + " from " + tB + " objects (" + tD + " clusters)";
    }
    if (what == "double") {
        if (dblMode == 0)
            calSummary = "COUNTED: " + tA + " double-labelled of " + tB +
                         " parasites (overlap >= " + minOverlapPercent + " %)";
        if (dblMode == 1) {
            pm = posModes[posMode];
            calSummary = "COUNTED: " + tA + " double-labelled of " + tB + " parasites (at least " +
                         objMinN + " marker object(s) of " + objMinArea + " to " + objMaxArea +
                         " um2, " + calObjT + " counted: " + pm + ")";
        }
        if (dblMode == 2)
            calSummary = "COUNTED: " + tA + " double-labelled of " + tB + " parasites (overlap >= " +
                         minOverlapPercent + " % with a marker object of " + encMinArea + " to " +
                         encMaxArea + " um2)";
        if (dblPolesUsed && dblMode != 2) {
            pSum = calPoleT[1] + calPoleT[2] + calPoleT[3];
            calSummary = calSummary + "\nPOLES: marker at the basal pole " + calPoleT[1] +
                         ", apical " + calPoleT[2] + ", indeterminable " + calPoleT[3] + " (sum " +
                         pSum + "), not at a pole " + calPoleT[4] + "; " + calPoleT[5] +
                         " parasite object(s) oriented";
        }
    }
    if (calN > 1) calSummary = calSummary + " on " + calN + " images:" + lines;
    else          calSummary = calSummary + "  (" + calLab[0] + ")";
    if (calSkip > 0)
        calSummary = calSummary + "\n" + calSkip + " image(s) picked lacked a channel and were left out";
    if (calMem > 0)
        calSummary = calSummary + "\n" + calMem + " more image(s) not shown: not enough memory";
    bl = calBrightLine(what);
    calSummary = calSummary + "\n" + bl;
    calSummary = calSummary + "\n" + calPixelLine(what);

    selectImage(calDrawID);
    run("Select None");
    Overlay.copy;
    close();
    selectImage(calPrevID);
    Overlay.remove;
    Overlay.paste;
    selectImage(calPrevID);
    run("Select None");
    Overlay.show;
    showStatus("");
    // what the preview now shows, so that an OK without change redraws nothing
    calLastKey = calDetKey(what);
    // back to the mode the run is in; the preview, being displayed already,
    // stays as it is
    if (wasBatch == false) setBatchMode(false);
}

// Pixel size of the calibration images, and what the area limits are in
// pixels with it.
function calPixelLine(what) {
    same = 1;
    // (0.11 read from a file can come back as 0.10999999: not a difference)
    for (k = 1; k < calN; k++) {
        if (abs(calPxV[k] - calPxV[0]) > 0.001 * abs(calPxV[0])) same = 0;
    }
    pv = calPxV[0];
    s = "Pixel size " + d2s(pv, 4) + " " + calPxU[0] + " (" + calPxS[0] + ")";
    if (same == 0) s = s + " for the first image; it varies between images";
    if (pv <= 0) return s;
    if (what == "nuclei")
        s = s + "\nMinimum area " + nucleiMinArea + " um2 = " +
            round(nucleiMinArea / (pv * pv)) + " pixels";
    if (what == "parasites")
        s = s + "\nArea " + greenMinArea + " to " + greenMaxArea + " um2 = " +
            round(greenMinArea / (pv * pv)) + " to " + round(greenMaxArea / (pv * pv)) +
            " pixels";
    if (what == "double" && dblMode == 1)
        s = s + "\nMarker objects " + objMinArea + " to " + objMaxArea + " um2 = " +
            round(objMinArea / (pv * pv)) + " to " + round(objMaxArea / (pv * pv)) +
            " pixels; " + objTol + " um = " + d2s(objTol / pv, 1) + " pixels";
    return s;
}

// The bright cap on the images drawn: how many images it acted on (and a
// saturated peak, when one was left out).
function calBrightLine(what) {
    capF = nucleiCapF;
    if (what == "parasites") capF = greenCapF;
    if (what == "double") capF = redCapF;
    if (capF > 0)
        s = "Bright cap " + capF + " x the median brightness: acted on " + calCapHits +
            " of " + calN + " image(s)";
    else
        s = "Bright cap off";
    if (calSpikeHits > 0)
        s = s + "; a saturated peak was left out on " + calSpikeHits + " image(s)";
    return s;
}

// What the threshold of the mask just made left out of its calculation,
// added to the counts of the panel.
function calThrInfo() {
    if (capHit == 1) calCapHits = calCapHits + 1;
    if (spikeHit == 1) calSpikeHits = calSpikeHits + 1;
    return 1;
}

// Nuclei of calibration image k, drawn on slice sl. Counts in calCntA
// (cells), calCntB (nuclei), calCntC (clusters), calCntD (border),
// calCntE (fragments).
function calRedrawNuclei(k, sl, fs, lw) {
    // the mask from the cache (same steps as segmentNuclei), then its objects
    binT = calMaskOf("n", k);
    okT = calThrInfo();
    noteN = thrNote;
    selectWindow(binT);
    run("Select None");
    run("Duplicate...", "title=[CAL_mask]");
    nucleiObjects("CAL_mask");
    nEdge = 0;
    edgeZ = getDirectory("temp") + "CAC_calibration_border.zip";
    if (File.exists(edgeZ)) dl = File.delete(edgeZ);
    if (nucleiEdgeMode > 0)
        nEdge = removeBorderNuclei("CAL_mask", nucleiEdgeMode, edgeZ);
    nObj = roiManager("count");
    nCells = countCells("CAL_BLUE_" + k);
    clus = cellClusterIdx;
    frag = cellFragIdx;
    allIdx = makeSeq(nObj);

    // Analyze Particles turns on "Show All" in the ROI Manager, which then
    // hands out its own outlines instead of copies: every outline added to
    // the preview must be a copy of its own, or its colour can change later
    roiManager("Show None");
    // what the filters rejected near their limits, underneath the rest:
    // orange by size, yellow by shape; nothing of what the border took
    zE = "";
    if (nEdge > 0) zE = edgeZ;
    okR = rejectMasks("CAL_mask", nObj, zE, nucleiMinArea, nucleiMaxArea, "CAL_rejS", "CAL_rejC");
    okM = ovlMask("CAL_rejS", colRejS, lw, sl, 0);
    okM = ovlMask("CAL_rejC", colRejC, lw, sl, 0);
    selectWindow("CAL_rejS");
    close();
    selectWindow("CAL_rejC");
    close();
    selectWindow("CAL_mask");
    close();
    if (nEdge > 0) {
        off = roiManager("count");
        roiManager("Open", edgeZ);
        nE = roiManager("count") - off;
        eIdx = newArray(nE);
        for (q = 0; q < nE; q++) eIdx[q] = off + q;
        ovlROIs(eIdx, colEdge, lw, sl);
    }
    ovlROIs(allIdx, colNuc, lw, sl);
    ovlROIs(clus, colNClu, lw, sl);
    ovlROIs(frag, colNFrag, lw, sl);
    ovlLabels(allIdx, colNuc, fs, sl);

    ovlText(calLab[k - 1], 6, 4, "ffffff", fs, sl);
    // legend on two lines, so that it fits on narrow images too
    leg = legNuclei(1);
    ovlText(leg, 6, 4 + round(fs * 1.6), "ffffff", fs, sl);
    leg = legNuclei(2);
    ovlText(leg, 6, 4 + round(fs * 3.2), "ffffff", fs, sl);
    selectWindow("CAL_BLUE_" + k);
    yb = getHeight() - fs * 2;
    cntT = "Cells = " + nCells + "   (" + nObj + " nuclei, " + clus.length + " clusters";
    if (nucleiFragFactor > 0) cntT = cntT + ", " + frag.length + " fragments";
    if (nucleiEdgeMode > 0) cntT = cntT + ", " + nEdge + " cut by the border left out";
    cntT = cntT + ")";
    // the clusters and fragments are sized against this median
    if (cellMedian > 0) cntT = cntT + "   median area " + d2s(cellMedian, 1) + " um2";
    ovlText(cntT, 6, yb, colNuc, fs, sl);
    if (noteN != "") ovlText("Threshold: " + noteN, 6, yb - round(fs * 1.6), "ffff00", fs, sl);

    calCntA = nCells;
    calCntB = nObj;
    calCntC = clus.length;
    calCntD = nEdge;
    calCntE = frag.length;
}

// The legend of the nuclei panels (Calibrate and QC stacks), line ln (1, 2).
function legNuclei(ln) {
    if (ln == 1) {
        t = "cyan: counted   magenta: cluster (several cells)";
        if (nucleiFragFactor > 0) t = t + "   green: fragment (no cell)";
        return t;
    }
    t = "orange: rejected by size   yellow: rejected by shape";
    if (nucleiEdgeMode > 0) t = "grey: cut by the border, left out   " + t;
    return t;
}

// The legend of the parasite panels, line ln (1, 2).
function legParasites(ln) {
    if (ln == 1) {
        t = "magenta: counted";
        if (splitBigParasites) t = t + "   white: cluster (several parasites)";
        return t;
    }
    return "orange: rejected by size   yellow: rejected by shape";
}

// Parasites of calibration image k, drawn on slice sl. Counts in calCntA
// (parasites), calCntB (objects), calCntD (clusters); threshold used in
// calCntC.
function calRedrawParasites(k, sl, fs, lw) {
    binT = calMaskOf("p", k);
    okT = calThrInfo();
    noteP = thrNote;
    selectWindow(binT);
    run("Select None");
    run("Duplicate...", "title=[CAL_mask]");
    nObj = parasiteObjects("CAL_mask");
    nPar = countParasites("CAL_GREEN_" + k);
    clus = parClusterIdx;
    allIdx = makeSeq(nObj);
    roiManager("Show None");          // copies, not shared outlines (see nuclei)
    okR = rejectMasks("CAL_mask", nObj, "", greenMinArea, greenMaxArea, "CAL_rejS", "CAL_rejC");
    okM = ovlMask("CAL_rejS", colRejS, lw, sl, 0);
    okM = ovlMask("CAL_rejC", colRejC, lw, sl, 0);
    selectWindow("CAL_rejS");
    close();
    selectWindow("CAL_rejC");
    close();
    selectWindow("CAL_mask");
    close();
    ovlROIs(allIdx, colPar, lw, sl);
    ovlROIs(clus, colPClu, lw, sl);
    ovlLabels(allIdx, colPar, fs, sl);

    ovlText(calLab[k - 1], 6, 4, "ffffff", fs, sl);
    // legend on two lines, so that it fits on narrow images too
    leg = legParasites(1);
    ovlText(leg, 6, 4 + round(fs * 1.6), "ffffff", fs, sl);
    leg = legParasites(2);
    ovlText(leg, 6, 4 + round(fs * 3.2), "ffffff", fs, sl);
    selectWindow("CAL_GREEN_" + k);
    yb = getHeight() - fs * 2;
    cntT = "Parasites = " + nPar;
    if (splitBigParasites) {
        cntT = cntT + "   (" + nObj + " objects, " + clus.length + " clusters";
        if (parMedian > 0) cntT = cntT + ", median area " + d2s(parMedian, 1) + " um2";
        cntT = cntT + ")";
    }
    cntT = cntT + "   (threshold " + parThr + " = automatic " + nfmt(parThrAuto) + " x " +
           greenThresholdFactor + ")";
    ovlText(cntT, 6, yb, colPar, fs, sl);
    if (noteP != "") ovlText("Threshold: " + noteP, 6, yb - round(fs * 1.6), "ffff00", fs, sl);

    calCntA = nPar;
    calCntB = nObj;
    calCntC = parThr;
    calCntD = clus.length;
    calCntE = 0;
}

// Double labelling of calibration image k, drawn on slices sl and sl + 1.
// Counts in calCntA (double-labelled) and calCntB (parasites); marker
// threshold in calCntC.
function calRedrawDouble(k, sl, fs, lw) {
    // parasites detected with the parasite settings in force; a cluster
    // counts for the parasites it holds, as in the batch
    binP = calMaskOf("p", k);
    selectWindow(binP);
    run("Select None");
    run("Duplicate...", "title=[CAL_pmask]");
    nObj = parasiteObjects("CAL_pmask");
    nPar = countParasites("CAL_GREEN_" + k);
    selectWindow("CAL_pmask");
    close();
    binM = calMaskOf("m", k);
    okT = calThrInfo();
    noteM = thrNote;
    // the thresholds of the marker mask, before the poles' own detections
    thrM = dblThr;
    selectWindow(binM);
    run("Select None");
    run("Duplicate...", "title=[CAL_mask]");
    // the same scoring as the batch: overlap, objects, or enclosing objects,
    // then the poles
    refB = "";
    if (poleNeedNuc && isOpen("CAL_BLUE_" + k)) refB = "CAL_BLUE_" + k;
    refP = "";
    if (poleNeedMk && isOpen("CAL_POLE_" + k)) refP = "CAL_POLE_" + k;
    nDbl = scoreDoubleAll("CAL_mask", nObj, "CAL_mask", refB, refP);
    dIdx = dblIdx;
    roiManager("Show None");          // copies, not shared outlines (see nuclei)
    oth = newArray(0);
    for (q = 0; q < nObj; q++) {
        isD = 0;
        for (z = 0; z < dIdx.length; z++) {
            if (dIdx[z] == q) isD = 1;
        }
        if (isD == 0) oth = Array.concat(oth, q);
    }
    // the marker objects by kind: 2 counted, 1 on a parasite elsewhere, 0 on none
    oKind = newArray(oX.length);
    for (q = 0; q < oX.length; q++) {
        if (oOwn[q] >= 0) oKind[q] = 1;
        if (oUse[q] == 1) oKind[q] = 2;
    }
    encT = "";
    if (dblMode == 2) encT = enclosingMask("CAL_mask");
    // specks of the marker are not drawn: under half the smallest parasite
    // (overlap), under the smallest object (objects), under half the
    // smallest object (enclosing objects)
    minSp = mkSpeck();
    polesOn = 0;
    if (dblPolesUsed && dblMode != 2) polesOn = 1;
    selectWindow("CAL_RED_" + k);
    yb = getHeight() - fs * 2;
    for (v = sl; v <= sl + 1; v++) {
        okM = ovlMask("CAL_mask", colMk, lw, v, minSp);
        if (encT != "") okE = ovlMask(encT, "ffff00", lw, v, 0);
        // the merge view (second one) shows the poles, when asked for
        if (polesOn == 1 && v == sl + 1) {
            cB = newArray(0);
            cA = newArray(0);
            cI = newArray(0);
            cM = newArray(0);
            for (q = 0; q < nObj; q++) {
                if (pCls[q] == 1) cB = Array.concat(cB, q);
                if (pCls[q] == 2) cA = Array.concat(cA, q);
                if (pCls[q] == 3) cI = Array.concat(cI, q);
                if (pCls[q] == 4) cM = Array.concat(cM, q);
            }
            ovlROIs(oth, "808080", lw, v);
            ovlROIs(cM, "ff9600", lw, v);
            ovlROIs(cI, "ffffff", lw, v);
            ovlROIs(cA, "00ffff", lw, v);
            ovlROIs(cB, "ff00ff", lw, v);
            if (dblMode == 1) {
                ovlObjects(oPole, 4, "ff9600", lw, v);
                ovlObjects(oPole, 3, "ffffff", lw, v);
                ovlObjects(oPole, 2, "00ffff", lw, v);
                ovlObjects(oPole, 1, "ff00ff", lw, v);
            }
            ovlBasalEnds(nObj, maxOf(3, lw * 2), "ffff00", v);
            ovlLabels(dIdx, "ffffff", fs, v);
            ovlText(calLab[k - 1], 6, 4, "ffffff", fs, v);
            lg = legPoles(1);
            ovlText(lg, 6, 4 + round(fs * 1.6), "ffffff", fs, v);
            lg = legPoles(2);
            ovlText(lg, 6, 4 + round(fs * 3.2), "ffffff", fs, v);
            pSum = poleN[1] + poleN[2] + poleN[3];
            cntP = "Poles: basal " + poleN[1] + ", apical " + poleN[2] + ", indeterminable " +
                   poleN[3] + " (sum " + pSum + " of " + nDbl + "), not at a pole " + poleN[4] +
                   "   -   " + poleOri + " of " + nObj + " parasite object(s) oriented";
            ovlText(cntP, 6, yb, "ffffff", fs, v);
            if (poleNote != "")
                ovlText("Not oriented: " + poleNote, 6, yb - round(fs * 1.6), "ffff00", fs, v);
        } else {
            ovlROIs(oth, colOth, lw, v);
            ovlROIs(dIdx, colDbl, lw, v);
            if (dblMode == 1) {
                ovlObjects(oKind, 0, "808080", lw, v);
                ovlObjects(oKind, 1, "ffff00", lw, v);
                ovlObjects(oKind, 2, "ffffff", lw, v);
            }
            ovlLabels(dIdx, "ffffff", fs, v);
            ovlText(calLab[k - 1], 6, 4, "ffffff", fs, v);
            lg = legDouble(1);
            ovlText(lg, 6, 4 + round(fs * 1.6), "ffffff", fs, v);
            lg = legDouble(2);
            if (lg != "") ovlText(lg, 6, 4 + round(fs * 3.2), "ffffff", fs, v);
            cntD = "Double-labelled = " + nDbl + " / " + nPar;
            if (dblMode == 0)
                cntD = cntD + "   (20 / 35 / 55 % overlap: " + dblN20 + " / " + dblN35 + " / " +
                       dblN55 + ")";
            if (dblMode == 1)
                cntD = cntD + "   (objects: " + objUsed + " counted, " + objOn +
                       " on a parasite, " + objN + " found)";
            if (dblMode == 2)
                cntD = cntD + "   (" + objN + " marker objects of the size given)";
            ovlText(cntD, 6, yb, "ffffff", fs, v);
            if (noteM != "") ovlText("Marker threshold: " + noteM, 6, yb - round(fs * 1.6), "ffff00", fs, v);
        }
    }
    selectWindow("CAL_mask");
    close();
    if (encT != "") {
        if (isOpen(encT)) { selectWindow(encT); close(); }
    }

    calCntA = nDbl;
    calCntB = nPar;
    calCntC = thrM;
    calCntD = 0;
    calCntE = 0;
    // the poles, for the panel: summed over the images
    calPoleT[1] = calPoleT[1] + poleN[1];
    calPoleT[2] = calPoleT[2] + poleN[2];
    calPoleT[3] = calPoleT[3] + poleN[3];
    calPoleT[4] = calPoleT[4] + poleN[4];
    calPoleT[5] = calPoleT[5] + poleOri;
    calObjT = calObjT + objUsed;
    calImgLine = "";
    if (polesOn == 1)
        calImgLine = ", poles basal / apical / indet. " + poleN[1] + " / " + poleN[2] + " / " + poleN[3];
}

// Smallest marker-positive area drawn (um2), Calibrate and QC stacks.
function mkSpeck() {
    if (dblMode == 1) return objMinArea;
    if (dblMode == 2) return encMinArea * nearFrac;
    return greenMinArea * nearFrac;
}

// Circles round the marker objects (oX, oY, oR) of class cls in cl, in the
// preview overlay on slice sl.
function ovlObjects(cl, cls, colHex, width, sl) {
    selectImage(calDrawID);
    for (q = 0; q < oX.length; q++) {
        if (cl[q] != cls) continue;
        r = oR[q] + width + 1;
        makeOval(oX[q] - r, oY[q] - r, 2 * r, 2 * r);
        Overlay.addSelection(colHex, width);
        Overlay.setPosition(sl);
    }
    run("Select None");
}

// A dot at the basal end of each oriented parasite, in the preview overlay.
function ovlBasalEnds(nPar, rad, colHex, sl) {
    selectImage(calDrawID);
    for (p = 0; p < nPar; p++) {
        if (pBas[p] == 0) continue;
        cx = aX1[p];
        cy = aY1[p];
        if (pBas[p] == 2) {
            cx = aX2[p];
            cy = aY2[p];
        }
        makeOval(cx - rad, cy - rad, 2 * rad, 2 * rad);
        Overlay.addSelection(colHex, 1, colHex);
        Overlay.setPosition(sl);
    }
    run("Select None");
}

// Everything above the threshold in a mask, outlined in the preview overlay
// on slice sl - but for the specks smaller than minA (um2), noise nobody
// needs to see. Goes through the ROI Manager, appended after the objects,
// so their indices do not move.
function ovlMask(maskTitle, colHex, width, sl, minA) {
    selectWindow(maskTitle);
    run("Select None");
    src = maskTitle;
    if (minA > 0) {
        // the particles of at least minA, as a mask of their own
        run("Analyze Particles...", "size=" + minA + "-Infinity show=Masks");
        src = getTitle();
        if (src == maskTitle) return 0;
    }
    // In both masks the objects are 255, whatever the LUT; said explicitly,
    // since without a threshold Create Selection guesses from the LUT and
    // the "Black background" option, and the two masks differ there.
    selectWindow(src);
    run("Select None");
    setThreshold(255, 255);
    run("Create Selection");
    resetThreshold();
    sty = selectionType();
    if (sty == -1) {
        if (src != maskTitle) close();
        return 0;
    }
    roiManager("Add");
    k = roiManager("count") - 1;
    selectWindow(src);
    run("Select None");
    if (src != maskTitle) close();
    selectImage(calDrawID);
    roiManager("Select", k);
    Overlay.addSelection(colHex, width);
    Overlay.setPosition(sl);
    run("Select None");
    return 1;
}

// ROI Manager items idx[] added to the preview overlay, on slice sl.
function ovlROIs(idx, colHex, width, sl) {
    selectImage(calDrawID);
    for (q = 0; q < idx.length; q++) {
        roiManager("Select", idx[q]);
        Overlay.addSelection(colHex, width);
        Overlay.setPosition(sl);
    }
    run("Select None");
}

// Numbers 1..N next to the ROI Manager items idx[], on slice sl.
function ovlLabels(idx, colHex, fs, sl) {
    selectImage(calDrawID);
    iw = getWidth();
    ih = getHeight();
    setFont("SansSerif", fs, "bold");
    for (q = 0; q < idx.length; q++) {
        roiManager("Select", idx[q]);
        getSelectionBounds(bx, by, bw, bh);
        tx = bx + bw + 2;
        if (tx > iw - fs * 2) tx = bx - fs * 2;
        if (tx < 0) tx = 0;
        ty = by + bh / 2 - fs / 2;
        if (ty < 0) ty = 0;
        if (ty > ih - fs) ty = ih - fs;
        makeText("" + (q + 1), tx, ty);
        Overlay.addSelection(colHex);
        Overlay.setPosition(sl);
    }
    run("Select None");
}

// A line of text in the preview overlay, on slice sl.
function ovlText(txt, x, y, colHex, fs, sl) {
    selectImage(calDrawID);
    setFont("SansSerif", fs, "bold");
    makeText(txt, x, y);
    Overlay.addSelection(colHex);
    Overlay.setPosition(sl);
    run("Select None");
}

// Slider when this ImageJ can put decimals on one, numeric field otherwise
// (before 1.52a a slider only shows decimals over a range of 5 or less).
// A value off the slider's steps - typed in the detection dialog - also
// gets a numeric field, so that it is not rounded without a word.
function calSlider(label, lo, hi, val, step) {
    // every second one on the row of the one before (calSlN, set to 0 by
    // calDialog)
    if (calSlN % 2 == 1) sr = sameRow();
    calSlN = calSlN + 1;
    if (val < lo) lo = val;
    if (val > hi) hi = val;
    steps = (val - lo) / step;
    onGrid = 0;
    if (abs(steps - round(steps)) < 0.001) onGrid = 1;
    if (stepSliders && onGrid == 1) {
        Dialog.addSlider(label, lo, hi, val, step);
    } else if (onGrid == 1 && step >= 1 && val == round(val) &&
               lo == round(lo) && hi == round(hi)) {
        Dialog.addSlider(label, lo, hi, val);
    } else {
        Dialog.addNumber(label, val, 2, 6, "");
    }
}

// The panel: every setting of the class being calibrated, and what to do
// at OK. The values read are applied at once; calAction tells what next.
function calDialog(what) {
    t = "Calibrate " + what;
    if (what == "double") t = "Calibrate double labelling";
    if (nonBlocking) Dialog.createNonBlocking(t);
    else             Dialog.create(t);
    if (stepSliders) {
        // at the right of the screen, the preview on its left
        dx = screenWidth - 980;
        if (dx < 0) dx = 0;
        Dialog.setLocation(dx, 30);
    }
    Dialog.addMessage(calSummary);
    Dialog.addMessage("Move the sliders, then OK (or Enter) to redraw. Brightness: display only.");

    // two sliders a row, so that the panel fits a laptop screen
    calSlN = 0;
    if (what == "nuclei") {
        calSlider("Threshold factor", 0.2, 3, nucleiThresholdFactor, 0.05);
        calSlider("Bright cap (x median, 0 = off)", 0, 20, nucleiCapF, 0.5);
        calSlider("Minimum area (um2)", 0, 500, nucleiMinArea, 1);
        calSlider("Background radius (px, 0 = none)", 0, 300, nucleiBgRolling, 1);
        calSlider("Blur sigma (px, 0 = none)", 0, 5, nucleiBlurSigma, 0.1);
        cMin = circMin(nucleiCirc);
        calSlider("Minimum circularity", 0, 1, cMin, 0.05);
        calSlider("Watershed tolerance (px)", 0.5, 20, nucleiWsTol, 0.5);
        calSlider("Cluster above (x median)", 1.1, 4, nucleiDoubletFactor, 0.05);
        calSlider("Cluster: at most (cells)", 2, 10, nucleiMaxSplit, 1);
        calSlider("No cell under (x median, 0 = off)", 0, 0.9, nucleiFragFactor, 0.05);
        calSlider("Brightness (x)", 0.25, 10, qcGainBlue, 0.25);
        Dialog.addChoice("Threshold method", thrMethods, nucleiThresholdMethod);
        sr = sameRow();
        Dialog.addChoice("Border", edgeModes, edgeModes[nucleiEdgeMode]);
        Dialog.addCheckbox("Split touching nuclei (watershed)", nucleiWatershed);
        sr = sameRow();
        Dialog.addCheckbox("Overlapping nuclei count as several cells", splitBigNuclei);
    }
    if (what == "parasites") {
        calSlider("Threshold factor", 0.5, 10, greenThresholdFactor, 0.05);
        calSlider("Bright cap (x median, 0 = off)", 0, 20, greenCapF, 0.5);
        calSlider("Minimum area (um2)", 0, 100, greenMinArea, 0.25);
        calSlider("Maximum area (um2)", 1, 300, greenMaxArea, 0.5);
        calSlider("Background radius (px, 0 = none)", 0, 300, greenBgRolling, 1);
        calSlider("Blur sigma (px, 0 = none)", 0, 5, greenBlurSigma, 0.1);
        cMin = circMin(greenCirc);
        calSlider("Minimum circularity", 0, 1, cMin, 0.05);
        calSlider("Watershed tolerance (px)", 0.5, 10, greenWsTol, 0.5);
        calSlider("Cluster above (x median)", 1.1, 4, greenDoubletFactor, 0.05);
        calSlider("Cluster: at most (parasites)", 2, 20, greenMaxSplit, 1);
        calSlider("Brightness (x)", 0.25, 10, qcGainGreen, 0.25);
        Dialog.addChoice("Threshold method", thrMethods, greenThresholdMethod);
        Dialog.addCheckbox("Split touching parasites (watershed)", greenWatershed);
        sr = sameRow();
        Dialog.addCheckbox("Clumps count as several", splitBigParasites);
    }
    if (what == "double") {
        calSlider("Marker threshold factor", 0.5, 10, redThresholdFactor, 0.05);
        calSlider("Bright cap (x median, 0 = off)", 0, 20, redCapF, 0.5);
        if (dblMode != 1) calSlider("Minimum overlap (%)", 0, 100, minOverlapPercent, 1);
        calSlider("Background radius (px, 0 = none)", 0, 300, redBgRolling, 1);
        calSlider("Blur sigma (px, 0 = none)", 0, 5, redBlurSigma, 0.1);
        calSlider("Marker brightness (x)", 0.25, 10, qcGainRed, 0.25);
        calSlider("Parasite brightness (x)", 0.25, 10, qcGainGreen, 0.25);
        if (dblMode == 1) {
            calSlider("Object: minimum area (um2)", 0, 20, objMinArea, 0.05);
            calSlider("Object: maximum area (um2)", 0.1, 200, objMaxArea, 0.1);
            calSlider("Centre at most outside (um)", 0, 3, objTol, 0.1);
            calSlider("Objects per parasite (at least)", 1, 20, objMinN, 1);
        }
        if (dblMode == 2) {
            calSlider("Object: minimum area (um2)", 0, 1000, encMinArea, 1);
            calSlider("Object: maximum area (um2)", 0, 20000, encMaxArea, 10);
        }
        if (dblMode == 1 || dblPolesUsed) {
            calSlider("Centre: central % of the length", 1, 100, centrePct, 1);
            calSlider("Extremity, pole: last % at each end", 1, 50, endPct, 1);
        }
        if (dblPolesUsed) {
            if (poleNeedNuc) {
                calSlider("Parasite nucleus: min. area (um2)", 0, 20, pnMinArea, 0.1);
                calSlider("Parasite nucleus: max. area (um2)", 0.5, 100, pnMaxArea, 0.5);
            }
            if (poleNeedMk) {
                calSlider("Pole marker threshold factor", 0.5, 10, pmThresholdFactor, 0.05);
                calSlider("Pole marker: min. area (um2)", 0, 10, pmMinArea, 0.05);
                calSlider("Pole marker: max. area (um2)", 0.1, 50, pmMaxArea, 0.1);
            }
            calSlider("No orientation within (% from centre)", 0, 45, poleDeadPct, 1);
        }
        Dialog.addChoice("Threshold method", thrMethods, redThresholdMethod);
        if (dblMode == 1) {
            sr = sameRow();
            Dialog.addChoice("Objects counted", posModes, posModes[posMode]);
            Dialog.addCheckbox("Split touching marker objects (watershed)", objWatershed);
        }
    }
    acts = newArray(calActRedraw, calActKeep, calActDiscard);
    if (nonBlocking == false) acts = Array.concat(acts, calActLook);
    Dialog.addChoice("At OK", acts, acts[0]);
    Dialog.show();

    if (what == "nuclei") {
        nucleiThresholdFactor = Dialog.getNumber();
        nucleiCapF             = Dialog.getNumber();
        nucleiMinArea         = Dialog.getNumber();
        nucleiBgRolling       = Dialog.getNumber();
        nucleiBlurSigma       = Dialog.getNumber();
        cMin                  = Dialog.getNumber();
        nucleiWsTol           = Dialog.getNumber();
        nucleiDoubletFactor   = Dialog.getNumber();
        nucleiMaxSplit        = Dialog.getNumber();
        nucleiFragFactor      = Dialog.getNumber();
        qcGainBlue            = Dialog.getNumber();
        nucleiThresholdMethod = Dialog.getChoice();
        ec                    = Dialog.getChoice();
        nucleiWatershed       = Dialog.getCheckbox();
        splitBigNuclei        = Dialog.getCheckbox();
        nucleiEdgeMode = 0;
        if (ec == edgeModes[1]) nucleiEdgeMode = 1;
        if (ec == edgeModes[2]) nucleiEdgeMode = 2;
        cs = circStr(cMin);
        nucleiCirc = cs;
        tl = wsTolOk(nucleiWsTol);
        nucleiWsTol = tl;
        if (isNaN(nucleiFragFactor) || nucleiFragFactor < 0) nucleiFragFactor = 0;
        if (nucleiFragFactor > 0.9) nucleiFragFactor = 0.9;
        g = gainOk(qcGainBlue);
        qcGainBlue = g;
        cv = capOk(nucleiCapF);
        nucleiCapF = cv;
    }
    if (what == "parasites") {
        greenThresholdFactor = Dialog.getNumber();
        greenCapF             = Dialog.getNumber();
        greenMinArea         = Dialog.getNumber();
        greenMaxArea         = Dialog.getNumber();
        greenBgRolling       = Dialog.getNumber();
        greenBlurSigma       = Dialog.getNumber();
        cMin                 = Dialog.getNumber();
        greenWsTol           = Dialog.getNumber();
        greenDoubletFactor   = Dialog.getNumber();
        greenMaxSplit        = Dialog.getNumber();
        qcGainGreen          = Dialog.getNumber();
        greenThresholdMethod = Dialog.getChoice();
        greenWatershed       = Dialog.getCheckbox();
        splitBigParasites    = Dialog.getCheckbox();
        cs = circStr(cMin);
        greenCirc = cs;
        tl = wsTolOk(greenWsTol);
        greenWsTol = tl;
        g = gainOk(qcGainGreen);
        qcGainGreen = g;
        cv = capOk(greenCapF);
        greenCapF = cv;
        ok = parClusterOk();
    }
    if (what == "double") {
        redThresholdFactor = Dialog.getNumber();
        redCapF             = Dialog.getNumber();
        if (dblMode != 1) minOverlapPercent = Dialog.getNumber();
        redBgRolling       = Dialog.getNumber();
        redBlurSigma       = Dialog.getNumber();
        qcGainRed          = Dialog.getNumber();
        qcGainGreen        = Dialog.getNumber();
        if (dblMode == 1) {
            objMinArea = Dialog.getNumber();
            objMaxArea = Dialog.getNumber();
            objTol     = Dialog.getNumber();
            objMinN    = Dialog.getNumber();
        }
        if (dblMode == 2) {
            encMinArea = Dialog.getNumber();
            encMaxArea = Dialog.getNumber();
        }
        if (dblMode == 1 || dblPolesUsed) {
            centrePct = Dialog.getNumber();
            endPct    = Dialog.getNumber();
        }
        if (dblPolesUsed) {
            if (poleNeedNuc) {
                pnMinArea = Dialog.getNumber();
                pnMaxArea = Dialog.getNumber();
            }
            if (poleNeedMk) {
                pmThresholdFactor = Dialog.getNumber();
                pmMinArea         = Dialog.getNumber();
                pmMaxArea         = Dialog.getNumber();
            }
            poleDeadPct = Dialog.getNumber();
        }
        redThresholdMethod = Dialog.getChoice();
        if (dblMode == 1) {
            pc = Dialog.getChoice();
            for (q = 0; q < posModes.length; q++) if (pc == posModes[q]) posMode = q;
            objWatershed = Dialog.getCheckbox();
        }
        okV = objSettingsOk();
        g = gainOk(qcGainRed);
        qcGainRed = g;
        g = gainOk(qcGainGreen);
        qcGainGreen = g;
        cv = capOk(redCapF);
        redCapF = cv;
    }
    calAction = Dialog.getChoice();
}

// The marker object, place and pole settings as used: sizes not negative
// and in order, whole counts, percentages in range, menu choices that
// exist. Returns 1.
function objSettingsOk() {
    if (isNaN(dblMode) || dblMode < 0 || dblMode > 2) dblMode = 0;
    dblMode = round(dblMode);
    if (isNaN(objMinArea) || objMinArea < 0) objMinArea = 0;
    if (isNaN(objMaxArea) || objMaxArea < 0) objMaxArea = 10;
    if (objMaxArea < objMinArea) { sw = objMinArea; objMinArea = objMaxArea; objMaxArea = sw; }
    if (isNaN(objTol) || objTol < 0) objTol = 0;
    if (objTol > 10) objTol = 10;
    if (isNaN(objMinN) || objMinN < 1) objMinN = 1;
    objMinN = round(objMinN);
    if (isNaN(encMinArea) || encMinArea < 0) encMinArea = 0;
    if (isNaN(encMaxArea) || encMaxArea < 0) encMaxArea = 5000;
    if (encMaxArea < encMinArea) { sw = encMinArea; encMinArea = encMaxArea; encMaxArea = sw; }
    if (isNaN(posMode) || posMode < 0 || posMode > 2) posMode = 0;
    posMode = round(posMode);
    // the centre and each extremity cannot be the whole parasite
    if (isNaN(centrePct) || centrePct < 1) centrePct = 1;
    if (centrePct > 100) centrePct = 100;
    if (isNaN(endPct) || endPct < 1) endPct = 1;
    if (endPct > 50) endPct = 50;
    if (isNaN(poleRef) || poleRef < 0 || poleRef > 2) poleRef = 0;
    poleRef = round(poleRef);
    if (isNaN(poleSide) || poleSide < 0 || poleSide > 1) poleSide = 0;
    poleSide = round(poleSide);
    if (isNaN(pnMinArea) || pnMinArea < 0) pnMinArea = 0;
    if (isNaN(pnMaxArea) || pnMaxArea < 0) pnMaxArea = 15;
    if (pnMaxArea < pnMinArea) { sw = pnMinArea; pnMinArea = pnMaxArea; pnMaxArea = sw; }
    if (isNaN(pmMinArea) || pmMinArea < 0) pmMinArea = 0;
    if (isNaN(pmMaxArea) || pmMaxArea < 0) pmMaxArea = 5;
    if (pmMaxArea < pmMinArea) { sw = pmMinArea; pmMinArea = pmMaxArea; pmMaxArea = sw; }
    if (isNaN(pmThresholdFactor) || pmThresholdFactor <= 0) pmThresholdFactor = 2;
    if (isNaN(poleDeadPct) || poleDeadPct < 0) poleDeadPct = 0;
    if (poleDeadPct > 45) poleDeadPct = 45;
    return 1;
}

// Bright cap as used: 0 (off) when not a positive number, at least 1.5 x
// the median otherwise (under that, ordinary objects would be left out).
function capOk(v) {
    if (isNaN(v)) return 0;
    if (v <= 0) return 0;
    if (v < 1.5) return 1.5;
    return v;
}

// "0.20-1.00" -> 0.2
function circMin(s) {
    d = indexOf(s, "-");
    if (d < 0) return 0;
    v = parseFloat(substring(s, 0, d));
    if (isNaN(v)) return 0;
    return v;
}

// Watershed tolerance as used, in px: 0.5 is ImageJ's own watershed; above
// that, 1 to 100. (By default the distance map holds whole pixels, so a
// value between 0.5 and 1 would cut more than ImageJ's watershed, not less.)
function wsTolOk(v) {
    if (isNaN(v)) return 0.5;
    if (v <= 0.5) return 0.5;
    if (v < 1) return 1;
    if (v > 100) return 100;
    return v;
}

// 0.2 -> "0.20-1.00"
function circStr(v) {
    if (v < 0) v = 0;
    if (v > 1) v = 1;
    t = num2(v);
    return t + "-1.00";
}

// Settings as they were before calibrating, for "Discard". Numbers and
// strings are kept in separate arrays.
function calSave() {
    calBak = newArray(nucleiThresholdFactor, nucleiMinArea, nucleiBgRolling,
                      nucleiBlurSigma, nucleiWatershed, splitBigNuclei,
                      nucleiDoubletFactor, nucleiMaxSplit, nucleiEdgeMode,
                      greenThresholdFactor, greenMinArea, greenMaxArea,
                      greenBgRolling, greenBlurSigma, greenWatershed,
                      redThresholdFactor, minOverlapPercent, redBgRolling,
                      redBlurSigma, nucleiWsTol, greenWsTol, nucleiFragFactor,
                      qcGainBlue, qcGainGreen, qcGainRed, nucleiCapF, greenCapF,
                      redCapF, splitBigParasites, greenDoubletFactor, greenMaxSplit,
                      objMinArea, objMaxArea, objTol, objMinN, objWatershed, encMinArea,
                      encMaxArea, posMode, centrePct, endPct, pnMinArea, pnMaxArea,
                      pmThresholdFactor, pmMinArea, pmMaxArea, poleDeadPct);
    calBakS = newArray(nucleiCirc, nucleiThresholdMethod, greenCirc,
                       greenThresholdMethod, redThresholdMethod);
}

function calRestore() {
    nucleiThresholdFactor = calBak[0];
    nucleiMinArea         = calBak[1];
    nucleiBgRolling       = calBak[2];
    nucleiBlurSigma       = calBak[3];
    nucleiWatershed       = calBak[4];
    splitBigNuclei        = calBak[5];
    nucleiDoubletFactor   = calBak[6];
    nucleiMaxSplit        = calBak[7];
    nucleiEdgeMode        = calBak[8];
    greenThresholdFactor  = calBak[9];
    greenMinArea          = calBak[10];
    greenMaxArea          = calBak[11];
    greenBgRolling        = calBak[12];
    greenBlurSigma        = calBak[13];
    greenWatershed        = calBak[14];
    redThresholdFactor    = calBak[15];
    minOverlapPercent     = calBak[16];
    redBgRolling          = calBak[17];
    redBlurSigma          = calBak[18];
    nucleiWsTol           = calBak[19];
    greenWsTol            = calBak[20];
    nucleiFragFactor      = calBak[21];
    qcGainBlue            = calBak[22];
    qcGainGreen           = calBak[23];
    qcGainRed             = calBak[24];
    nucleiCapF             = calBak[25];
    greenCapF              = calBak[26];
    redCapF                = calBak[27];
    splitBigParasites     = calBak[28];
    greenDoubletFactor    = calBak[29];
    greenMaxSplit         = calBak[30];
    objMinArea            = calBak[31];
    objMaxArea            = calBak[32];
    objTol                = calBak[33];
    objMinN               = calBak[34];
    objWatershed          = calBak[35];
    encMinArea            = calBak[36];
    encMaxArea            = calBak[37];
    posMode               = calBak[38];
    centrePct             = calBak[39];
    endPct                = calBak[40];
    pnMinArea             = calBak[41];
    pnMaxArea             = calBak[42];
    pmThresholdFactor     = calBak[43];
    pmMinArea             = calBak[44];
    pmMaxArea             = calBak[45];
    poleDeadPct           = calBak[46];
    nucleiCirc            = calBakS[0];
    nucleiThresholdMethod = calBakS[1];
    greenCirc             = calBakS[2];
    greenThresholdMethod  = calBakS[3];
    redThresholdMethod    = calBakS[4];
}

// Closes every image the calibration made, except the preview.
function calCloseTemp() {
    lst = getList("image.titles");
    for (w = lst.length - 1; w >= 0; w--) {
        if (startsWith(lst[w], "CAL_")) {
            selectWindow(lst[w]);
            close();
        }
    }
    roiManager("Reset");
}


// ======================================================================
//  FUNCTIONS - nuclei at the image border
// ======================================================================

// Leaves out the nuclei cut by the image border. Every particle of the mask
// that touches the border is judged - also one the size or shape filters
// rejected because it is cut: a nucleus cut short is smaller than a whole
// one, and was shown as rejected by size instead of as at the border.
//   mode 1: every particle touching the border is left out
//   mode 2: those with more than half of their area outside are left out;
//           a cut nucleus mostly inside is counted when its visible part
//           passes the filters with half the minimum area (at most half of
//           it is missing)
// Particles under a quarter of the minimum area are specks, not judged.
// On entry the ROI Manager holds the nuclei of the filters (nucleiObjects);
// on return, the nuclei counted, in scan order. Those left out are saved to
// zipPath. Returns how many were left out.
function removeBorderNuclei(maskTitle, mode, zipPath) {
    selectWindow(maskTitle);
    run("Select None");
    iw = getWidth();
    ih = getHeight();
    getPixelSize(bu, bpw, bph);
    floorA = nucleiMinArea / 4;
    // the particles touching the border: all of them, minus the others
    okA = apMask(maskTitle, "size=" + floorA + "-Infinity", "CAC_e_all");
    okB = apMask(maskTitle, "size=" + floorA + "-Infinity exclude", "CAC_e_in");
    imageCalculator("Subtract create", "CAC_e_all", "CAC_e_in");
    rename("CAC_edge");
    selectWindow("CAC_e_all");
    close();
    selectWindow("CAC_e_in");
    close();
    nR = roiManager("count");
    selectWindow("CAC_edge");
    run("Select None");
    setThreshold(255, 255);
    run("Analyze Particles...", "size=0-Infinity show=Nothing add");
    resetThreshold();
    nE = roiManager("count") - nR;
    if (nE <= 0) {
        selectWindow("CAC_edge");
        close();
        return 0;
    }

    // each one alone on a blank image, to read its widths (mode 2)
    out = newArray(nE);
    nOut = 0;
    newImage("Border_test", "8-bit black", iw, ih, 1);
    tmpID = getImageID();
    for (k = 0; k < nE; k++) {
        selectImage(tmpID);
        roiManager("Select", nR + k);
        getSelectionBounds(bx, by, bw, bh);
        o = 1;
        if (mode == 2) {
            setColor(255);
            fill();
            o = 0;
            if (bx <= 0) {
                o1 = centreOutside(bx, by, bw, bh, "left", iw, ih);
                if (o1 == 1) o = 1;
            }
            if (bx + bw >= iw) {
                o2 = centreOutside(bx, by, bw, bh, "right", iw, ih);
                if (o2 == 1) o = 1;
            }
            if (by <= 0) {
                o3 = centreOutside(bx, by, bw, bh, "top", iw, ih);
                if (o3 == 1) o = 1;
            }
            if (by + bh >= ih) {
                o4 = centreOutside(bx, by, bw, bh, "bottom", iw, ih);
                if (o4 == 1) o = 1;
            }
            run("Select None");
            setColor(0);
            fillRect(bx, by, bw, bh);
        }
        out[k] = o;
        nOut = nOut + o;
    }
    selectImage(tmpID);
    run("Select None");
    close();

    // counted: the nuclei of the filters not cut by the border...
    okC = apMask(maskTitle, "size=" + nucleiMinArea + "-" + nucleiMaxArea + " circularity=" +
                 nucleiCirc, "CAC_cnt");
    imageCalculator("Subtract", "CAC_cnt", "CAC_edge");
    selectWindow("CAC_edge");
    close();
    // ... and the cut ones mostly inside, the minimum area halved
    if (nOut < nE) {
        newImage("CAC_inb", "8-bit black", iw, ih, 1);
        setVoxelSize(bpw, bph, 1, bu);
        for (k = 0; k < nE; k++) {
            if (out[k] == 1) continue;
            selectWindow("CAC_inb");
            roiManager("Select", nR + k);
            setColor(255);
            fill();
        }
        selectWindow("CAC_inb");
        run("Select None");
        okI = apMask("CAC_inb", "size=" + (nucleiMinArea / 2) + "-" + nucleiMaxArea +
                     " circularity=" + nucleiCirc, "CAC_inok");
        imageCalculator("Max", "CAC_cnt", "CAC_inok");
        selectWindow("CAC_inok");
        close();
        selectWindow("CAC_inb");
        close();
    }

    // those left out to their own file (only they are left in the manager
    // for the save), then the nuclei counted, in scan order
    if (nOut > 0) {
        del = newArray(0);
        for (k = 0; k < nR + nE; k++) {
            if (k >= nR) {
                if (out[k - nR] == 1) continue;
            }
            del = Array.concat(del, k);
        }
        if (del.length > 0) {
            roiManager("Select", del);
            roiManager("Delete");
        }
        roiManager("Deselect");
        roiManager("Save", zipPath);
    }
    roiManager("Reset");
    selectWindow("CAC_cnt");
    run("Select None");
    setThreshold(255, 255);
    run("Analyze Particles...", "size=0-Infinity show=Nothing add");
    resetThreshold();
    close();
    selectWindow(maskTitle);
    run("Select None");
    return nOut;
}

// Analyze Particles on the mask src (objects 255, whatever its LUT): the
// particles kept by opts as a new mask dst, objects 255, calibrated as src.
// Returns 1.
function apMask(src, opts, dst) {
    selectWindow(src);
    run("Select None");
    sw = getWidth();
    sh = getHeight();
    getPixelSize(su, spw, sph);
    setThreshold(255, 255);
    run("Analyze Particles...", opts + " show=Masks");
    t = getTitle();
    selectWindow(src);
    resetThreshold();
    if (t == src) {
        // no mask made: an empty one
        newImage(dst, "8-bit black", sw, sh, 1);
        setVoxelSize(spw, sph, 1, su);
        return 1;
    }
    selectWindow(t);
    rename(dst);
    return 1;
}

// The particles of the mask maskTitle neither counted nor left out at the
// border, as two masks (objects 255) for Calibrate and the QC stacks:
//   outS  rejected by size - under the minimum area but at least nearFrac
//         of it (smaller specks are not shown), or above the maximum
//   outC  rejected by shape - area in range, circularity under the minimum
// The objects counted are ROI Manager items 0 to nC-1; zipOut, when it
// exists, holds those left out at the border. The ROI Manager is left as it
// was. Returns 1.
function rejectMasks(maskTitle, nC, zipOut, minA, maxA, outS, outC) {
    selectWindow(maskTitle);
    run("Select None");
    run("Duplicate...", "title=[CAC_rem]");
    selectWindow("CAC_rem");
    setColor(0);
    for (k = 0; k < nC; k++) {
        roiManager("Select", k);
        fill();
    }
    run("Select None");
    if (zipOut != "") {
        if (File.exists(zipOut)) {
            n0 = roiManager("count");
            roiManager("Open", zipOut);
            n1 = roiManager("count");
            selectWindow("CAC_rem");
            setColor(0);
            for (k = n0; k < n1; k++) {
                roiManager("Select", k);
                fill();
            }
            run("Select None");
            rt = roiTrim(n0);
        }
    }
    lo = nearFrac * minA;
    okA = apMask("CAC_rem", "size=" + lo + "-Infinity", outS);
    okB = apMask("CAC_rem", "size=" + minA + "-" + maxA, outC);
    imageCalculator("Subtract", outS, outC);
    selectWindow("CAC_rem");
    close();
    return 1;
}

// 1 if the centre of the nucleus drawn on the current image lies beyond the
// given border (more than half of it outside), 0 otherwise.
// The widths of the nucleus are read line by line parallel to the border,
// from the border inwards. For an ellipse the squared width is a parabola
// of the distance from the border, with its peak at the centre, so the
// peak of the parabola fitted by least squares gives the depth of the
// centre - including when that centre lies outside the image.
// In an image corner, lines that also reach the other border are cut short
// and are not used; with fewer than 3 full lines the nucleus is left out.
function centreOutside(bx, by, bw, bh, side, iw, ih) {
    nd = bw;
    if (side == "top" || side == "bottom") nd = bh;
    if (nd < 3) return 1;
    dep = newArray(nd);
    w2  = newArray(nd);
    nOk = 0;
    for (q = 0; q < nd; q++) {
        if (side == "left" || side == "right") {
            xq = bx + q;
            if (side == "right") xq = bx + bw - 1 - q;
            if (getPixel(xq, 0) > 0 || getPixel(xq, ih - 1) > 0) continue;
            makeRectangle(xq, by, 1, bh);
            getStatistics(ar, mn);
            // mean of a one-pixel line x its length / 255 = its pixels
            wq = mn * bh / 255;
        } else {
            yq = by + q;
            if (side == "bottom") yq = by + bh - 1 - q;
            if (getPixel(0, yq) > 0 || getPixel(iw - 1, yq) > 0) continue;
            makeRectangle(bx, yq, bw, 1);
            getStatistics(ar, mn);
            wq = mn * bw / 255;
        }
        dep[nOk] = q + 0.5;
        w2[nOk]  = wq * wq;
        nOk = nOk + 1;
    }
    run("Select None");
    if (nOk < 3) return 1;
    nd = nOk;

    // least-squares parabola  w2 = a + b*u + c*u^2,  u = depth - mean depth
    // (centring keeps the sums well conditioned); b and c by Cramer's rule
    mu = 0;
    for (q = 0; q < nd; q++) mu = mu + dep[q];
    mu = mu / nd;
    s2 = 0; s3 = 0; s4 = 0; sy = 0; suy = 0; su2y = 0;
    for (q = 0; q < nd; q++) {
        u = dep[q] - mu;
        s2   = s2 + u * u;
        s3   = s3 + u * u * u;
        s4   = s4 + u * u * u * u;
        sy   = sy + w2[q];
        suy  = suy + u * w2[q];
        su2y = su2y + u * u * w2[q];
    }
    det = nd * (s2 * s4 - s3 * s3) - s2 * s2 * s2;
    if (det == 0) return 1;
    pb = (nd * (suy * s4 - s3 * su2y) + sy * s3 * s2 - s2 * s2 * suy) / det;
    pc = (nd * (s2 * su2y - suy * s3) - sy * s2 * s2) / det;
    if (pc < 0) {
        centre = mu - pb / (2 * pc);
        if (centre < 0) return 1;
        return 0;
    }
    // no single peak (irregular outline): the side where the nucleus is
    // wider decides
    slope = pb + 2 * pc * (0.5 - mu);
    if (slope <= 0) return 1;
    return 0;
}


// ======================================================================
//  FUNCTIONS - image annotation
// ======================================================================

// Display copy of a channel for the QC stacks, in the colour lut ("" keeps
// its own) and with the brightness set in Calibrate - the very view of the
// Calibrate preview. Display range only: pixel values, and so measurements,
// are untouched.
function qcCopy(srcTitle, newTitle, gain, lut) {
    selectWindow(srcTitle);
    run("Select None");
    run("Duplicate...", "title=[" + newTitle + "]");
    if (lut != "") run(lut);
    qcRange(gain);
}

// Display range of the image in front, adapted to it (QC stacks and the
// Calibrate preview): from its background - the most frequent value - to
// the brightness of its objects - the value 99.5 % of the pixels above its
// automatic (Triangle) threshold stay under; then brightened by gain: 2
// shows everything twice as bright, 0.5 half as bright. An image with no
// such objects (or a saturated background) gets the automatic contrast of
// ImageJ instead (0.35 % of the pixels saturated).
function qcRange(gain) {
    resetMinAndMax();
    bd = bitDepth();
    if (bd == 24) return;
    getMinAndMax(mn, mx);
    if (mx <= mn) return;
    // histogram: full resolution for 8 and 16 bits (bin = value, only the
    // values between min and max are visited), 4096 bins for 32 bits
    h0 = 0;
    hs = 1;
    qa = round(mn);
    qb = round(mx);
    if (bd == 8) {
        getHistogram(hv, hc, 256);
    } else if (bd == 16) {
        getHistogram(hv, hc, 65536);
    } else {
        getHistogram(hv, hc, 4096);
        h0 = mn;
        hs = (mx - mn) / 4096;
        qa = 0;
        qb = 4095;
    }
    if (qa < 0) qa = 0;
    if (qb > hc.length - 1) qb = hc.length - 1;
    // the background: the most frequent value
    iMode = qa;
    for (q = qa + 1; q <= qb; q++) {
        if (hc[q] > hc[iMode]) iMode = q;
    }
    lo = h0 + iMode * hs;
    // the objects: above the Triangle threshold
    setAutoThreshold("Triangle dark");
    getThreshold(tLo, tHi);
    resetThreshold();
    iT = floor((tLo - h0) / hs);
    if (iT <= iMode) iT = iMode + 1;
    hi = lo;
    if (iT <= qb) {
        nObj = 0;
        for (q = iT; q <= qb; q++) nObj = nObj + hc[q];
        if (nObj > 0) {
            want = 0.995 * nObj;
            acc = 0;
            iHi = qb;
            for (q = iT; q <= qb; q++) {
                acc = acc + hc[q];
                if (acc >= want) {
                    iHi = q;
                    q = qb + 1;
                }
            }
            hi = h0 + (iHi + 1) * hs;
        }
    }
    if (hi <= lo) {
        run("Enhance Contrast", "saturated=0.35");
        getMinAndMax(lo, hi);
        if (hi <= lo) return;
    }
    if (gain > 0 && gain != 1) hi = lo + (hi - lo) / gain;
    setMinAndMax(lo, hi);
}

// Brightness as used: 1 when not a usable number, kept within 0.05 - 50.
function gainOk(v) {
    if (isNaN(v)) return 1;
    if (v <= 0) return 1;
    if (v < 0.05) return 0.05;
    if (v > 50) return 50;
    return v;
}

function makeSeq(nn) {
    a = newArray(nn);
    for (q = 0; q < nn; q++) a[q] = q;
    return a;
}

// Flatten to RGB with no overlay and no "Show All": annotations are then
// written into the pixels, which is reliable in batch mode. A single
// channel is made RGB first, from its display range: Flatten would draw the
// image as last painted on screen, which outside batch mode may still be
// the view before its contrast was set (the panel then varied from run to
// run). In batch mode both give the very same pixels.
function flattenClean(newTitle) {
    run("Select None");
    run("Remove Overlay");
    roiManager("Show None");
    if (bitDepth() != 24) run("RGB Color");
    run("Flatten");
    rename(newTitle);
}

function drawROIs(indices, colR, colG, colB, lineWidth) {
    if (indices.length == 0) return;
    run("Line Width...", "line=" + lineWidth);
    setForegroundColor(colR, colG, colB);
    for (q = 0; q < indices.length; q++) {
        roiManager("Select", indices[q]);
        run("Draw", "slice");
    }
    run("Select None");
}

// Numbers 1..N next to each ROI. The black halo keeps them readable on any
// background; without it, red vanishes on the red channel.
function labelROIs(indices, colR, colG, colB, fontSize, dy) {
    nLab = indices.length;
    if (nLab == 0) return;

    imgWl = getWidth();
    imgHl = getHeight();
    dx = maxOf(4, round(fontSize / 4));
    lx = newArray(nLab);
    ly = newArray(nLab);

    for (q = 0; q < nLab; q++) {
        roiManager("Select", indices[q]);
        getSelectionBounds(bx, by, bw, bh);
        px = bx + bw + dx;
        py = by + bh / 2 + dy;
        if (px > imgWl - fontSize * 1.6) px = bx - round(fontSize * 1.3);
        px = minOf(maxOf(px, 2), imgWl - round(fontSize * 1.5));
        py = minOf(maxOf(py, fontSize + 2), imgHl - 2);
        lx[q] = px;
        ly[q] = py;
    }

    run("Select None");
    setFont("SansSerif", fontSize, "bold");   // order: name, SIZE, style
    setJustification("left");
    halo = maxOf(1, round(fontSize / 12));

    setColor(0, 0, 0);
    for (q = 0; q < nLab; q++) {
        txt = "" + (q + 1);
        drawString(txt, lx[q] - halo, ly[q]);
        drawString(txt, lx[q] + halo, ly[q]);
        drawString(txt, lx[q], ly[q] - halo);
        drawString(txt, lx[q], ly[q] + halo);
    }
    setColor(colR, colG, colB);
    for (q = 0; q < nLab; q++) drawString("" + (q + 1), lx[q], ly[q]);
}

// As labelROIs, numbered numOf[index] instead of 1..N: the same parasite
// keeps its number from one panel to the next.
function labelROIsNum(indices, numOf, colR, colG, colB, fontSize, dy) {
    nLab = indices.length;
    if (nLab == 0) return;
    imgWl = getWidth();
    imgHl = getHeight();
    dx = maxOf(4, round(fontSize / 4));
    lx = newArray(nLab);
    ly = newArray(nLab);
    for (q = 0; q < nLab; q++) {
        roiManager("Select", indices[q]);
        getSelectionBounds(bx, by, bw, bh);
        px = bx + bw + dx;
        py = by + bh / 2 + dy;
        if (px > imgWl - fontSize * 1.6) px = bx - round(fontSize * 1.3);
        px = minOf(maxOf(px, 2), imgWl - round(fontSize * 1.5));
        py = minOf(maxOf(py, fontSize + 2), imgHl - 2);
        lx[q] = px;
        ly[q] = py;
    }
    run("Select None");
    setFont("SansSerif", fontSize, "bold");
    setJustification("left");
    halo = maxOf(1, round(fontSize / 12));
    setColor(0, 0, 0);
    for (q = 0; q < nLab; q++) {
        txt = "" + numOf[indices[q]];
        drawString(txt, lx[q] - halo, ly[q]);
        drawString(txt, lx[q] + halo, ly[q]);
        drawString(txt, lx[q], ly[q] - halo);
        drawString(txt, lx[q], ly[q] + halo);
    }
    setColor(colR, colG, colB);
    for (q = 0; q < nLab; q++) {
        txt = "" + numOf[indices[q]];
        drawString(txt, lx[q], ly[q]);
    }
}

// Circles round the marker objects (oX, oY, oR) whose class in cl - an
// array as long - is cls, on the image in front.
function drawObjects(cl, cls, colR, colG, colB, lineWidth) {
    setColor(colR, colG, colB);
    setLineWidth(lineWidth);
    for (q = 0; q < oX.length; q++) {
        if (cl[q] != cls) continue;
        r = oR[q] + lineWidth + 1;
        drawOval(oX[q] - r, oY[q] - r, 2 * r, 2 * r);
    }
    setLineWidth(1);
}

// A dot at the basal end of each oriented parasite, on the image in front.
function drawBasalEnds(nPar, rad, colR, colG, colB) {
    setColor(colR, colG, colB);
    for (p = 0; p < nPar; p++) {
        if (pBas[p] == 1) fillOval(aX1[p] - rad, aY1[p] - rad, 2 * rad, 2 * rad);
        if (pBas[p] == 2) fillOval(aX2[p] - rad, aY2[p] - rad, 2 * rad, 2 * rad);
    }
}

// The outline of the objects of the mask maskT (objects 255) drawn on the
// panel panelT. Goes through the ROI Manager, after its first n items, and
// leaves it as it was; the panel ends up in front.
function drawMaskOutline(maskT, panelT, colR, colG, colB, lineWidth, n) {
    selectWindow(maskT);
    run("Select None");
    setThreshold(255, 255);
    run("Create Selection");
    resetThreshold();
    sty = selectionType();
    selectWindow(panelT);
    if (sty == -1) return 0;
    selectWindow(maskT);
    roiManager("Add");
    k = roiManager("count") - 1;
    run("Select None");
    selectWindow(panelT);
    run("Line Width...", "line=" + lineWidth);
    setForegroundColor(colR, colG, colB);
    roiManager("Select", k);
    run("Draw", "slice");
    run("Select None");
    nd = roiTrim(n);
    selectWindow(panelT);
    return 1;
}

// The lines of Parasites.csv for the image just scored: one per parasite
// object (ROI Manager items 0 to nPar-1, numbered as on the GREEN panel).
function parasiteLines(manip, cond, base, nPar) {
    s = "";
    useW = 0;
    if (parWeight.length == nPar) useW = 1;
    dN = newArray(nPar);
    for (z = 0; z < dblIdx.length; z++) dN[dblIdx[z]] = z + 1;
    clsTxt = newArray("", "basal pole", "apical pole", "indeterminable", "not at a pole");
    for (p = 0; p < nPar; p++) {
        w = 1;
        if (useW == 1) w = parWeight[p];
        ln = "" + csvQ(manip) + "," + csvQ(cond) + "," + csvQ(base) + "," + (p + 1) + "," + w + "," +
             num2(aArea[p]) + "," + num2(aLen[p]) + ",";
        if (dN[p] > 0) ln = ln + "yes," + dN[p];
        else           ln = ln + "no,";
        if (dblMode == 1) {
            ln = ln + "," + pObj[p] + "," + pCnt[p];
        }
        if (dblPolesUsed && dblMode != 2) {
            oriT = "yes";
            if (pBas[p] == 0) {
                wy = "" + pWhy[p];
                wy = replace(wy, ",", ";");
                oriT = "no - " + wy;
            }
            plc = "";
            if (pPlace[p] >= 0) plc = num2(100 * pPlace[p]);
            // the place, from the basal end when it is known
            if (pPlace[p] >= 0 && pBas[p] == 2) plc = num2(100 * (1 - pPlace[p]));
            pc = pCls[p];
            ct = clsTxt[pc];
            ln = ln + "," + oriT + "," + pNuc[p] + "," + pMk[p] + "," + ct + "," + plc;
        }
        s = s + ln + "\n";
    }
    return s;
}

// Red, green, blue of a colour given as "rrggbb".
function hexR(h) {
    v = parseInt(substring(h, 0, 2), 16);
    return v;
}
function hexG(h) {
    v = parseInt(substring(h, 2, 4), 16);
    return v;
}
function hexB(h) {
    v = parseInt(substring(h, 4, 6), 16);
    return v;
}

// drawROIs, labelROIs, drawMaskOutline and drawRecap with a colour "rrggbb".
function drawROIsH(indices, h, lw) {
    cr = hexR(h);
    cg = hexG(h);
    cb = hexB(h);
    drawROIs(indices, cr, cg, cb, lw);
}
function labelROIsH(indices, h, fs, dy) {
    cr = hexR(h);
    cg = hexG(h);
    cb = hexB(h);
    labelROIs(indices, cr, cg, cb, fs, dy);
}
function drawMaskH(maskT, panelT, h, lw, n) {
    if (maskT == "") return 0;
    if (isOpen(maskT) == false) return 0;
    cr = hexR(h);
    cg = hexG(h);
    cb = hexB(h);
    ok = drawMaskOutline(maskT, panelT, cr, cg, cb, lw, n);
    return ok;
}
function drawRecapH(txt, h, fs) {
    cr = hexR(h);
    cg = hexG(h);
    cb = hexB(h);
    drawRecap(txt, cr, cg, cb, fs);
}

// A line of text at the top of the image in front, line ln (1 = top), as the
// legend of the Calibrate preview, with a black halo. Returns 1.
function drawTop(txt, ln, h, fs) {
    if (txt == "") return 0;
    run("Select None");
    setFont("SansSerif", fs, "bold");
    setJustification("left");
    halo = maxOf(1, round(fs / 14));
    px = 6;
    py = 4 + fs + (ln - 1) * round(fs * 1.6);
    setColor(0, 0, 0);
    drawString(txt, px - halo, py);
    drawString(txt, px + halo, py);
    drawString(txt, px, py - halo);
    drawString(txt, px, py + halo);
    cr = hexR(h);
    cg = hexG(h);
    cb = hexB(h);
    setColor(cr, cg, cb);
    drawString(txt, px, py);
    return 1;
}

// The legend of the double labelling panels (marker, marker + parasites), line ln.
function legDouble(ln) {
    if (ln == 1) {
        t = "white: double-labelled   cyan: other parasites   orange: marker-positive area";
        if (dblMode == 2) t = t + "   yellow: marker objects";
        return t;
    }
    if (dblMode == 1) return "objects (circles): white counted, yellow on a parasite elsewhere, grey on none";
    return "";
}

// The legend of the poles panels, line ln.
function legPoles(ln) {
    if (ln == 1) return "marker at the basal pole: magenta   apical: cyan   indeterminable: white";
    return "not at a pole: orange   other parasites: grey   yellow dot: basal end";
}

function drawRecap(txt, colR, colG, colB, fontSize) {
    run("Select None");
    setFont("SansSerif", fontSize, "bold");
    setJustification("left");
    halo = maxOf(1, round(fontSize / 14));
    px = 20;
    py = getHeight() - round(fontSize * 0.7);

    setColor(0, 0, 0);
    drawString(txt, px - halo, py);
    drawString(txt, px + halo, py);
    drawString(txt, px, py - halo);
    drawString(txt, px, py + halo);

    setColor(colR, colG, colB);
    drawString(txt, px, py);
}

// A warning line just above the recap, drawn like it (the top of a panel
// holds its legend).
function drawNote(txt, colR, colG, colB, fontSize) {
    run("Select None");
    setFont("SansSerif", fontSize, "bold");
    setJustification("left");
    halo = maxOf(1, round(fontSize / 14));
    px = 20;
    py = getHeight() - round(fontSize * 0.7) - round(fontSize * 1.6);

    setColor(0, 0, 0);
    drawString(txt, px - halo, py);
    drawString(txt, px + halo, py);
    drawString(txt, px, py - halo);
    drawString(txt, px, py + halo);

    setColor(colR, colG, colB);
    drawString(txt, px, py);
}
