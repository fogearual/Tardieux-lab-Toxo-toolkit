// Smart Time Stamper  -  ImageJ / Fiji macro, version 1 (October 2026), ImageJ 1.53b or later
//
// WHAT IT DOES
// Writes the time on every frame of a movie from the frame's ORIGINAL number in the movie,
// not from its rank in the stack. On a stack made of frames that are not consecutive (for
// example Image > Stacks > Tools > Make Substack with 3,15,27,40,...), each frame gets its
// real time:  (original frame - frame of time zero) x frame interval.
// ImageJ's own Time Stamper counts the frames of the new stack (0, 1 x interval,
// 2 x interval, ...), which is wrong as soon as frames were left out.
//
// WHERE THE ORIGINAL FRAME NUMBERS COME FROM
// Make Substack keeps the slice labels of the movie; they are read in this order:
//   1. Bio-Formats labels (czi, nd2, lif, ome.tif ...): "t:45/200 - movie"  ->  frame 45
//   2. AVI frames opened by ImageJ, labelled with frame number x interval: "39.15 s" with
//      0.87 s  ->  frame 45
//   3. a frame number in the label: "frame 45", "T045", "time000045", "_t45" (numbers that
//      start at 0 are shifted by one)
//   4. a label that is only a number ("45"), then the last number of the label when it
//      changes from frame to frame (image sequences: "movie-0045")
//   5. the title written by Make Substack, "Substack (3,15,27)", when it is not cut short
// Labels that only count the slices of this stack are not used: "t:1/16" to "t:16/16"
// (a saved substack opened again with Bio-Formats, which rewrites the labels; File > Open
// keeps them), or 1, 2, 3 ... in a row.
// The numbers found are shown in the dialog to be checked, and can be corrected there
// (lists and ranges: 3,15,27,40-43). When nothing is found, a first window asks for the
// original frame number of each image, one box per image (up to 60 images; the title of a
// cut-short Make Substack fills in the first and last ones; a tick box for consecutive
// frames). With more than 60 images the frames are taken as consecutive and the main
// dialog says so in orange. The frame interval comes from Image > Properties (or from the
// frame rate).
//
// OUTPUT
//   - an overlay on the image: the pixels are not changed, Image > Overlay > Remove Overlay
//     takes it away, File > Save As > AVI writes it into the movie; or
//   - a new RGB copy with the time burned in, as the image is displayed (for montages,
//     figures, other software).
// Running it again replaces the time stamps it wrote before; other overlay items are kept.
// A table "Time stamps" lists every time point with its original frame, time and text.
// The settings are remembered (the font size as a fraction of the image size).
//
// INSTALL: copy this file into the folder Fiji.app/plugins/Toxo Toolkit/ (create it if
// needed) and restart Fiji: Plugins > Toxo Toolkit > Smart Time Stamper. (Or open it and click Run.)

var gName = "Time stamp";
var parseErr = "";
var selfLab = false;
var gW = 0;
var gH = 0;
var gMg = 0;
var gPos = 0;
var gSelX = 0;
var gSelY = 0;
var gCol = "#ffffff";
var gBg = "";
var gTDim = "t";

requires("1.53b");
if (nImages == 0)
	exit("Smart Time Stamper:\nopen the movie (or the stack made from it) first.");

// ---------------------------------------------------------------- the image
id = getImageID();
title = getTitle();
getDimensions(w, h, nC, nZ, nF);
nSl = nSlices;
if (nF > 1) {
	tDim = "t";
	nT = nF;
} else if (nZ > 1) {
	tDim = "z";
	nT = nZ;
} else {
	tDim = "-";
	nT = 1;
}
cur = getSliceNumber();
selType = selectionType();
selX = 0;
selY = 0;
selW = 0;
selH = 0;
selXs = newArray(0);
selYs = newArray(0);
if (selType >= 0) {
	getSelectionBounds(selX, selY, selW, selH);
	if (selType != 9)
		getSelectionCoordinates(selXs, selYs);
}

// ---------------------------------------------------------------- frame interval
unitList = newArray("ms", "s", "min", "h");
unitSec = newArray(0.001, 1, 60, 3600);
iv = Stack.getFrameInterval();
Stack.getUnits(ux, uy, uz, ut, uv);
fps = Stack.getFrameRate();
ui = unitIndex(ut);
ivNote = "";
ivWarn = false;
if (ui == -2) {
	iv = iv / 1000;
	ui = 0;
}
if (!(iv > 0)) {
	if (fps > 0) {
		iv = 1 / fps;
		ui = 1;
		ivNote = "Frame interval worked out from the frame rate (" + d2s(fps, 3) + " frames per second).";
	} else {
		iv = 1;
		ui = 1;
		ivNote = "No frame interval in Image > Properties: type it here.";
		ivWarn = true;
	}
} else if (ui < 0) {
	ui = 1;
	ivNote = "Time unit \"" + ut + "\" not recognised: taken as seconds.";
	ivWarn = true;
}

// ---------------------------------------------------------------- original frame numbers
setBatchMode(true);
labels = newArray(nT);
anyLabel = false;
for (k = 1; k <= nT; k++) {
	if (nSl > 1) {
		setSlice(stackIdx(k, tDim, nC, nZ));
		lab = getMetadata("Label");
	} else {
		lab = getInfo("slice.label");
		if (lab == "") {
			// (a single image keeps its label in "Slice_Label"; else in its one-slice stack,
			// unless that label is made of the title and the Info text)
			lab2 = getMetadata("Label");
			if (indexOf(lab2, "\n") < 0)
				lab = lab2;
		}
	}
	lab = firstLine(lab);
	labels[k - 1] = lab;
	if (lab != "")
		anyLabel = true;
}
if (nSl > 1)
	setSlice(cur);

ivS0 = iv * unitSec[ui];
fr = newArray(0);
src = 0;
maxP = 5;
if (nT == 1)
	maxP = 4;
if (anyLabel) {
	for (p = 1; p <= maxP; p++) {
		if (fr.length == 0 && !selfLab) {
			fr = tryPattern(labels, p, ivS0);
			if (fr.length > 0)
				src = p;
		}
	}
}
if (fr.length == 0 && startsWith(title, "Substack (") && endsWith(title, ")") && indexOf(title, "...") < 0) {
	t2 = parseFrames(substring(title, 10, lengthOf(title) - 1));
	if (parseErr == "" && t2.length == nT) {
		fr = t2;
		src = 6;
	}
}
if (fr.length == 0) {
	fr = newArray(nT);
	for (i = 0; i < nT; i++)
		fr[i] = i + 1;
	src = 0;
}
frStr = compactFrames(fr);

lab1 = labels[0];
if (lengthOf(lab1) > 32)
	lab1 = substring(lab1, 0, 32) + "...";
srcCol = "#006400";
if (src == 1)
	srcMsg = "Original frame numbers read from the slice labels (slice 1: \"" + lab1 + "\").";
else if (src == 2)
	srcMsg = "Original frame numbers worked out from the frame times in the slice labels (slice 1: \"" + lab1 + "\").";
else if (src >= 3 && src <= 5)
	srcMsg = "Original frame numbers read from the numbers in the slice labels (slice 1: \"" + lab1 + "\").";
else if (src == 6)
	srcMsg = "Original frame numbers read from the title (list given to Make Substack).";
else if (selfLab) {
	srcMsg = "The slice labels number the frames of this stack itself (t:1/" + nT + " to t:" + nT + "/" + nT + "): right if it is the\nwhole movie. If frames were left out, type their numbers in the original movie.";
	srcCol = "#c05000";
} else {
	srcMsg = "No original frame numbers found";
	if (anyLabel)
		srcMsg = srcMsg + " in the slice labels (slice 1: \"" + lab1 + "\")";
	srcMsg = srcMsg + ":\nthe frames are taken as consecutive. Type the right numbers if frames were left out.";
	srcCol = "#c05000";
}
shortTitle = title;
if (lengthOf(shortTitle) > 40)
	shortTitle = substring(shortTitle, 0, 40) + "...";
if (tDim == "t")
	dimNote = "" + nT + " frames";
else if (tDim == "z")
	dimNote = "" + nT + " slices, each taken as one time point";
else
	dimNote = "one image";
if (nC > 1)
	dimNote = dimNote + ", " + nC + " channels";

// ---------------------------------------------------------------- frames not read: one box per image
// (up to 60 images; with more, the list of the main dialog is used)
if (src == 0 && nT <= 60) {
	vals = newArray(nT);
	for (i = 0; i < nT; i++)
		vals[i] = "";
	hint = "";
	// a Make Substack title cut short still gives the first frames and the last one:
	// "Substack (3,15,27,40, ... 199)"
	if (startsWith(title, "Substack (") && endsWith(title, ")") && indexOf(title, ", ... ") > 0) {
		inner = substring(title, 10, lengthOf(title) - 1);
		dd = indexOf(inner, ", ... ");
		first = split(substring(inner, 0, dd), ",");
		last = trim(substring(inner, dd + 6));
		okT = matches(last, "\\d+");
		for (i = 0; i < first.length; i++)
			if (!matches(trim(first[i]), "\\d+"))
				okT = false;
		if (okT && first.length < nT) {
			for (i = 0; i < first.length; i++)
				vals[i] = trim(first[i]);
			vals[nT - 1] = last;
			hint = "Boxes 1 to " + first.length + " and " + nT + " filled in from the title (list given to Make Substack).";
		}
	}
	cons = false;
	done = false;
	while (!done) {
		Dialog.create("Smart Time Stamper - original frames");
		if (selfLab)
			Dialog.addMessage("The slice labels of \"" + shortTitle + "\" only number its own images (t:1/" + nT + " to t:" + nT + "/" + nT + ").");
		else
			Dialog.addMessage("The original frame numbers could not be read from \"" + shortTitle + "\".");
		Dialog.addMessage("For each image of this stack (1 to " + nT + "), type its frame number in the original movie:");
		if (hint != "")
			Dialog.addMessage(hint, 12, "#006400");
		ncol = 4;
		if (nT > 16)
			ncol = 6;
		for (i = 0; i < nT; i++) {
			if (i % ncol != 0)
				Dialog.addToSameRow();
			Dialog.addString("" + (i + 1), vals[i], 5);
		}
		// (a group spans the whole width; a single tick box would widen the first column of boxes)
		// (newArray(n) with one number would make an array of n elements: states set one by one)
		if (nT > 1) {
			cst = newArray(1);
			cst[0] = cons;
			Dialog.addCheckboxGroup(1, 1, newArray("Consecutive frames from box 1 (1 if empty): the other boxes are not needed"), cst);
		}
		Dialog.show();
		for (i = 0; i < nT; i++)
			vals[i] = trim(Dialog.getString());
		if (nT > 1)
			cons = Dialog.getCheckbox();
		fr = newArray(nT);
		bad = "";
		if (cons) {
			st0 = 1;
			if (matches(vals[0], "\\d+"))
				st0 = parseInt(vals[0]);
			for (i = 0; i < nT; i++)
				fr[i] = st0 + i;
		} else {
			for (i = 0; i < nT; i++) {
				if (matches(vals[i], "\\d+"))
					fr[i] = parseInt(vals[i]);
				else {
					if (bad != "")
						bad = bad + ", ";
					bad = bad + (i + 1);
				}
			}
		}
		if (bad == "")
			done = true;
		else
			showMessage("Smart Time Stamper", "Type a frame number (a whole number) in box " + bad + ",\nor tick \"Consecutive frames\".");
	}
	src = 7;
	frStr = compactFrames(fr);
	srcMsg = "Original frame numbers as typed for each image (they can still be changed here).";
	srcCol = "#006400";
}

// ---------------------------------------------------------------- dialog
fmtList = newArray("seconds (38.3 s)", "minutes (0.64 min)", "hours (1.25 h)", "milliseconds (870 ms)", "mm:ss (00:38)", "hh:mm:ss (00:00:38)");
decList = newArray("auto", "0", "1", "2", "3");
posList = newArray("Top left", "Top centre", "Top right", "Bottom left", "Bottom centre", "Bottom right");
if (selType >= 0)
	posList = Array.concat(posList, "At the selection");
colList = newArray("White", "Yellow", "Cyan", "Magenta", "Green", "Red", "Orange", "Grey", "Black");
colHex = newArray("#ffffff", "#ffff00", "#00ffff", "#ff00ff", "#00ff00", "#ff0000", "#ff9900", "#a0a0a0", "#000000");
bgList = newArray("None", "Dark see-through box", "Black box", "White box");
bgHex = newArray("", "#99000000", "#ff000000", "#ffffffff");
outList = newArray("Overlay (pixels unchanged)", "New RGB copy (time burned in)");

// (a function that returns a string is assigned to a variable before it is used)
pv = pget("format", fmtList[0]);
fmtC = inList(fmtList, pv, fmtList[0]);
pv = pget("decimals", "auto");
decC = inList(decList, pv, "auto");
prefix = pget("prefix", "");
pv = pget("addframe", "0");
addFr = false;
if (pv == "1")
	addFr = true;
pv = pget("position", "Top left");
posC = inList(posList, pv, "Top left");
if (selType >= 0)
	posC = "At the selection";
pv = pget("fontfrac", "0.05");
frac = parseFloat(pv);
if (isNaN(frac) || frac <= 0 || frac > 1)
	frac = 0.05;
fsize = maxOf(10, round(minOf(w, h) * frac));
fsize0 = fsize;
pv = pget("colour", "White");
colC = inList(colList, pv, "White");
pv = pget("background", "None");
bgC = inList(bgList, pv, "None");
pv = pget("bold", "1");
bold = false;
if (pv == "1")
	bold = true;
pv = pget("output", outList[0]);
outC = inList(outList, pv, outList[0]);
pv = pget("table", "1");
tableOn = false;
if (pv == "1")
	tableOn = true;
zero = 1;
ivDec = decimalsOf(iv);
ivStr = d2s(iv, ivDec) + " " + unitList[ui];

help = "<html><b>Smart Time Stamper</b><br>"
	+ "Writes on each frame its real time: (original frame - frame of time zero) x frame interval.<br><br>"
	+ "<b>Original frames</b>: the frame number in the movie of each time point of this image, in order. "
	+ "They are read from the slice labels that Make Substack keeps (Bio-Formats \"t:45/200\", AVI times, \"frame 45\"...) "
	+ "or from the title given by Make Substack. Check them. Lists and ranges: 3,15,27,40-43 (40 to 43), "
	+ "1-199-12 (every 12th frame).<br>"
	+ "<b>Frame interval</b>: from Image &gt; Properties; a number and a unit (ms, s, min, h), "
	+ "for example 0.87 s or 870 ms (without a unit: the unit shown at the start).<br>"
	+ "<b>Time zero</b>: the original frame shown as time 0 (for example the frame where a drug was added); "
	+ "earlier frames get negative times.<br>"
	+ "<b>Decimals</b>: auto = enough to tell two consecutive frames of the movie apart.<br>"
	+ "<b>Position</b>: \"At the selection\" puts the text at the top left corner of the selection drawn before starting.<br>"
	+ "<b>Overlay</b>: the pixels are not changed. Image &gt; Overlay &gt; Remove Overlay takes it away; "
	+ "File &gt; Save As &gt; AVI writes it into the movie. Running the macro again replaces the time stamps.<br>"
	+ "<b>RGB copy</b>: the time is burned into a new RGB image, as the image is displayed (brightness, colours), "
	+ "for montages and figures.<br>"
	+ "<b>Table</b>: every time point with its original frame, its time in seconds and the text written.</html>";

ok = false;
while (!ok) {
	Dialog.create("Smart Time Stamper");
	Dialog.addMessage(shortTitle + ":  " + dimNote);
	Dialog.addMessage(srcMsg, 12, srcCol);
	Dialog.addString("Original frames", frStr, 44);
	Dialog.addString("Frame interval", ivStr, 10);
	if (ivNote != "") {
		if (ivWarn)
			Dialog.addMessage(ivNote, 12, "#c05000");
		else
			Dialog.addMessage(ivNote, 12, "#006400");
	}
	Dialog.addNumber("Time zero at original frame", zero, 0, 6, "(earlier frames: negative times)");
	Dialog.addChoice("Display as", fmtList, fmtC);
	Dialog.addChoice("Decimals", decList, decC);
	Dialog.addString("Text before the time", prefix, 10);
	Dialog.addCheckbox("Add the original frame number", addFr);
	Dialog.addChoice("Position", posList, posC);
	Dialog.addNumber("Font size", fsize, 0, 4, "pixels");
	Dialog.addCheckbox("Bold", bold);
	Dialog.addChoice("Colour", colList, colC);
	Dialog.addChoice("Background", bgList, bgC);
	Dialog.addChoice("Write the time", outList, outC);
	Dialog.addCheckbox("List the times in a table", tableOn);
	Dialog.addHelp(help);
	Dialog.show();
	frStr = Dialog.getString();
	ivStr = Dialog.getString();
	prefix = Dialog.getString();
	zero = Dialog.getNumber();
	fsize = Dialog.getNumber();
	fmtC = Dialog.getChoice();
	decC = Dialog.getChoice();
	posC = Dialog.getChoice();
	colC = Dialog.getChoice();
	bgC = Dialog.getChoice();
	outC = Dialog.getChoice();
	addFr = Dialog.getCheckbox();
	bold = Dialog.getCheckbox();
	tableOn = Dialog.getCheckbox();
	ivS = parseInterval(ivStr, ui);
	err = "";
	frN = parseFrames(frStr);
	if (parseErr != "")
		err = "Original frames: " + parseErr + ".";
	else if (frN.length != nT)
		err = "Original frames: " + frN.length + " numbers given, but the image has " + nT + " time points.";
	if (err == "" && !(ivS > 0))
		err = "Frame interval: type a number above 0 and a unit, for example 0.87 s or 870 ms.";
	if (err == "" && isNaN(zero))
		err = "Type the original frame of time zero (1 = first frame of the movie).";
	if (err == "" && !(fsize >= 4))
		err = "The font size must be at least 4 pixels.";
	if (err == "")
		ok = true;
	else
		showMessage("Smart Time Stamper", err);
}
fr = frN;
fsize = round(fsize);
fmtI = indexIn(fmtList, fmtC);
posI = indexIn(posList, posC);
colI = indexIn(colList, colC);
bgI = indexIn(bgList, bgC);
outI = indexIn(outList, outC);
if (decC == "auto")
	dec = autoDec(fmtI, ivS);
else
	dec = parseInt(decC);

pset("format", fmtC);
pset("decimals", decC);
pset("prefix", prefix);
pv = b01(addFr);
pset("addframe", pv);
if (posC != "At the selection")
	pset("position", posC);
if (fsize != fsize0)
	pset("fontfrac", d2s(fsize / minOf(w, h), 5));
pset("colour", colC);
pset("background", bgC);
pv = b01(bold);
pset("bold", pv);
pset("output", outC);
pv = b01(tableOn);
pset("table", pv);

// ---------------------------------------------------------------- texts
times = newArray(nT);
texts = newArray(nT);
pre = prefix;
if (pre != "" && !endsWith(pre, " "))
	pre = pre + " ";
for (k = 0; k < nT; k++) {
	times[k] = (fr[k] - zero) * ivS;
	tstr = fmtTime(times[k], fmtI, dec);
	texts[k] = pre + tstr;
	if (addFr)
		texts[k] = texts[k] + "  (frame " + fr[k] + ")";
}

// ---------------------------------------------------------------- image that gets the stamps
selectImage(id);
if (outI == 1) {
	if (selType >= 0)
		run("Select None");
	base = title;
	dot = lastIndexOf(base, ".");
	if (dot > 0 && lengthOf(base) - dot <= 5)
		base = substring(base, 0, dot);
	copyTitle = base + "_time";
	run("Duplicate...", "title=[" + copyTitle + "] duplicate");
	tid = getImageID();
	selectImage(id);
	if (selType >= 0)
		restoreSel(selType, selXs, selYs, selX, selY, selW, selH);
	selectImage(tid);
	// RGB as displayed; a multi-channel image gives a new RGB image (channels merged)
	if (is("composite")) {
		run("RGB Color", "slices frames keep");
		rid = getImageID();
		if (rid != tid) {
			selectImage(tid);
			close();
			selectImage(rid);
			rename(copyTitle);
			tid = rid;
		}
	} else if (bitDepth() != 24)
		run("RGB Color");
} else
	tid = id;

selectImage(tid);
getDimensions(tw, th, tc, tz, tf);
if (nSlices == 1 || nT == 1)
	pm = 0;
else if (tc == 1 && (tz == 1 || tf == 1))
	pm = 1;
else if (Stack.isHyperstack || tc > 1)
	pm = 2;
else
	pm = 3;

gW = tw;
gH = th;
gMg = maxOf(2, round(fsize * 0.4));
gPos = posI;
gSelX = selX;
gSelY = selY;
gCol = colHex[colI];
gBg = bgHex[bgI];
gTDim = tDim;
if (bold)
	setFont("SansSerif", fsize, "bold antialiased");
else
	setFont("SansSerif", fsize, "antialiased");

if (Overlay.size > 0)
	Overlay.removeRois(gName);
for (k = 1; k <= nT; k++) {
	if (pm == 3) {
		for (c = 1; c <= tc; c++) {
			if (tDim == "t") {
				for (z = 1; z <= tz; z++)
					addStamp(texts[k - 1], 3, k, (k - 1) * tc * tz + (z - 1) * tc + c);
			} else
				addStamp(texts[k - 1], 3, k, (k - 1) * tc + c);
		}
	} else
		addStamp(texts[k - 1], pm, k, k);
}
run("Select None");

if (outI == 1) {
	if (nSlices > 1)
		run("Flatten", "stack");
	else {
		run("Flatten");
		fid = getImageID();
		selectImage(tid);
		close();
		selectImage(fid);
		rename(copyTitle);
		tid = fid;
	}
	selectImage(tid);
	setSlice(1);
} else {
	setSlice(cur);
	if (selType >= 0)
		restoreSel(selType, selXs, selYs, selX, selY, selW, selH);
}
setBatchMode(false);
selectImage(tid);
updateDisplay();

// ---------------------------------------------------------------- table
if (tableOn) {
	Table.create("Time stamps");
	for (k = 0; k < nT; k++) {
		Table.set("Time point", k, k + 1);
		Table.set("Original frame", k, fr[k]);
		Table.set("Time (s)", k, times[k]);
		Table.set("Text", k, texts[k]);
		Table.set("Slice label", k, labels[k]);
	}
	Table.update;
	selectImage(tid);
}

// ================================================================ functions

// Index of the stack plane (channel 1, slice 1 or the time point) of time point k.
function stackIdx(k, td, nc, nz) {
	if (td == "t")
		return (k - 1) * nc * nz + 1;
	if (td == "z")
		return (k - 1) * nc + 1;
	return 1;
}

function firstLine(s) {
	i = indexOf(s, "\n");
	if (i >= 0)
		s = substring(s, 0, i);
	return trim(s);
}

// 0 ms, 1 s, 2 min, 3 h, -2 microseconds, -1 unknown
function unitIndex(u) {
	u = toLowerCase(trim(u));
	if (u == "us" || u == "usec" || u == fromCharCode(181) + "s" || u == fromCharCode(956) + "s" || startsWith(u, "microsec"))
		return -2;
	if (u == "ms" || u == "msec" || startsWith(u, "millisec"))
		return 0;
	if (u == "s" || u == "sec" || u == "secs" || startsWith(u, "second"))
		return 1;
	if (u == "min" || u == "mins" || startsWith(u, "minute"))
		return 2;
	if (u == "h" || u == "hr" || u == "hrs" || startsWith(u, "hour"))
		return 3;
	return -1;
}

function unitFactor(u) {
	if (u == "ms")
		return 0.001;
	if (u == "min")
		return 60;
	if (u == "h")
		return 3600;
	return 1;
}

// "0.87 s", "870 ms", "0.87" (unit defU: index in ms, s, min, h) -> seconds; -1 if unreadable.
function parseInterval(s, defU) {
	s = toLowerCase(trim(s));
	re = "(\\d+(?:\\.\\d*)?|\\.\\d+)\\s*([a-z" + fromCharCode(181) + fromCharCode(956) + "]*)";
	if (!matches(s, re))
		return -1;
	v = parseFloat(replace(s, re, "$1"));
	u = replace(s, re, "$2");
	k = defU;
	if (u != "")
		k = unitIndex(u);
	if (k == -2)
		return v / 1000000;
	if (k == 0)
		return v / 1000;
	if (k == 1)
		return v;
	if (k == 2)
		return v * 60;
	if (k == 3)
		return v * 3600;
	return -1;
}

// Original frame numbers from the slice labels with pattern p (see the header);
// an empty array when the pattern does not fit every label.
function tryPattern(labs, p, ivS) {
	n = labs.length;
	v = newArray(n);
	own = true;
	for (i = 0; i < n; i++) {
		s = labs[i];
		if (s == "")
			return newArray(0);
		if (p == 1) {
			re = "(?i).*(?:^|[^a-z0-9])t[:=](\\d+)/(\\d+).*";
			if (!matches(s, re))
				return newArray(0);
			v[i] = parseInt(replace(s, re, "$1"));
			if (v[i] != i + 1 || parseInt(replace(s, re, "$2")) != n)
				own = false;
		} else if (p == 2) {
			re = "(?i)\\s*(-?\\d+(?:\\.\\d+)?)\\s*(ms|s|sec|min|h)\\s*";
			if (!(ivS > 0) || !matches(s, re))
				return newArray(0);
			tv = parseFloat(replace(s, re, "$1")) * unitFactor(toLowerCase(replace(s, re, "$2")));
			// ImageJ's AVI reader labels frame n with n x interval ("0.87 s" for frame 1)
			f = tv / ivS;
			if (abs(f - round(f)) > 0.26)
				return newArray(0);
			v[i] = round(f);
		} else if (p == 3) {
			re = "(?i).*?(?:^|[^a-z0-9])(?:frame|timepoint|time|tp|t)[ _:=#-]{0,3}(\\d+)(?![\\d.]).*";
			if (!matches(s, re))
				return newArray(0);
			v[i] = parseInt(replace(s, re, "$1"));
		} else if (p == 4) {
			re = "\\s*(\\d+)\\s*";
			if (!matches(s, re))
				return newArray(0);
			v[i] = parseInt(replace(s, re, "$1"));
		} else {
			s = replace(s, "\\.[A-Za-z][A-Za-z0-9]{1,4}$", "");
			re = "(?:.*\\D)?(\\d+)\\D*";
			if (!matches(s, re))
				return newArray(0);
			v[i] = parseInt(replace(s, re, "$1"));
		}
		if (isNaN(v[i]))
			return newArray(0);
	}
	if (p == 1 && own && n > 1) {
		// "t:1/16" ... "t:16/16": the labels number the frames of this stack, not of the movie
		// (for example a substack saved and opened again with Bio-Formats)
		selfLab = true;
		return newArray(0);
	}
	if (p == 2) {
		// times counted from 0 for the first frame ("0.00 s"): frame = time / interval + 1
		zeroT = false;
		for (i = 0; i < n; i++)
			if (v[i] <= 0)
				zeroT = true;
		if (zeroT)
			for (i = 0; i < n; i++)
				v[i] = v[i] + 1;
		for (i = 0; i < n; i++)
			if (v[i] < 1)
				return newArray(0);
	}
	if (p >= 3) {
		// numbers counted from 0 (a label with frame 0): frame = number + 1
		zeroT = false;
		for (i = 0; i < n; i++)
			if (v[i] == 0)
				zeroT = true;
		if (zeroT)
			for (i = 0; i < n; i++)
				v[i] = v[i] + 1;
	}
	if (p >= 3 && n > 1) {
		// 1, 2, 3 ... n: the numbers count the slices of this stack (copies "movie-1",
		// "movie-2"...), they say nothing about the movie
		seq = true;
		for (i = 0; i < n; i++)
			if (v[i] != i + 1)
				seq = false;
		if (seq)
			return newArray(0);
		same = true;
		for (i = 1; i < n; i++)
			if (v[i] != v[0])
				same = false;
		if (same)
			return newArray(0);
	}
	return v;
}

// "3,15,27,40-43,1-199-12" -> array of frame numbers; sets parseErr when it cannot be read.
function parseFrames(s) {
	parseErr = "";
	s = replace(s, "[;\\s]+", ",");
	s = replace(s, ",+", ",");
	s = replace(s, "^,|,$", "");
	if (s == "" || s == ",") {
		parseErr = "no frame number given";
		return newArray(0);
	}
	parts = split(s, ",");
	n = 0;
	for (i = 0; i < parts.length; i++) {
		q = parts[i];
		if (matches(q, "\\d+"))
			n++;
		else if (matches(q, "\\d+-\\d+(-\\d+)?")) {
			r = split(q, "-");
			st = 1;
			if (r.length > 2)
				st = parseInt(r[2]);
			if (st < 1) {
				parseErr = "the step of \"" + q + "\" must be 1 or more";
				return newArray(0);
			}
			n = n + floor(abs(parseInt(r[1]) - parseInt(r[0])) / st) + 1;
		} else {
			parseErr = "\"" + q + "\" is not a frame number or a range (like 40-43)";
			return newArray(0);
		}
	}
	out = newArray(n);
	j = 0;
	for (i = 0; i < parts.length; i++) {
		q = parts[i];
		if (matches(q, "\\d+")) {
			out[j] = parseInt(q);
			j++;
		} else {
			r = split(q, "-");
			a = parseInt(r[0]);
			b = parseInt(r[1]);
			st = 1;
			if (r.length > 2)
				st = parseInt(r[2]);
			m = floor(abs(b - a) / st) + 1;
			for (x = 0; x < m; x++) {
				if (b >= a)
					out[j] = a + x * st;
				else
					out[j] = a - x * st;
				j++;
			}
		}
	}
	return out;
}

// Frame numbers written back as a short list (runs of 3 or more consecutive frames as a-b;
// for long lists, runs of 6 or more with the same step as a-b-step).
function compactFrames(a) {
	s = compactList(a, false);
	if (lengthOf(s) > 200)
		s = compactList(a, true);
	return s;
}

function compactList(a, steps) {
	s = "";
	i = 0;
	n = a.length;
	while (i < n) {
		// (the macro language evaluates both sides of &&: no a[j + 1] in a loop condition)
		j = i;
		while (j + 1 < n) {
			if (a[j + 1] == a[j] + 1)
				j++;
			else
				break;
		}
		if (j - i >= 2) {
			item = "" + a[i] + "-" + a[j];
			i = j + 1;
		} else {
			j = i;
			st = 0;
			if (steps && i + 1 < n) {
				st = a[i + 1] - a[i];
				if (st > 1) {
					while (j + 1 < n) {
						if (a[j + 1] - a[j] == st)
							j++;
						else
							break;
					}
				}
			}
			if (st > 1 && j - i >= 5) {
				item = "" + a[i] + "-" + a[j] + "-" + st;
				i = j + 1;
			} else {
				item = "" + a[i];
				i++;
			}
		}
		if (s == "")
			s = item;
		else
			s = s + "," + item;
	}
	return s;
}

// Decimals that tell two consecutive frames apart in the chosen format.
function autoDec(f, ivs) {
	v = ivs;
	if (f == 1)
		v = ivs / 60;
	else if (f == 2)
		v = ivs / 3600;
	else if (f == 3)
		v = ivs * 1000;
	d = 0;
	while (d < 3 && v * pow(10, d) < 0.9999)
		d++;
	return d;
}

function fmtTime(ts, f, d) {
	if (f <= 3) {
		v = ts;
		u = " s";
		if (f == 1) {
			v = ts / 60;
			u = " min";
		} else if (f == 2) {
			v = ts / 3600;
			u = " h";
		} else if (f == 3) {
			v = ts * 1000;
			u = " ms";
		}
		s = d2s(v, d);
		if (parseFloat(s) == 0)
			s = d2s(0, d);
		return s + u;
	}
	q = pow(10, d);
	n = round(abs(ts) * q);
	sg = "";
	if (ts < 0 && n > 0)
		sg = "-";
	sec = floor(n / q);
	fra = n - sec * q;
	if (f == 4) {
		m = floor(sec / 60);
		s = IJ.pad(m, 2) + ":" + IJ.pad(sec - 60 * m, 2);
	} else {
		hh = floor(sec / 3600);
		m = floor((sec - 3600 * hh) / 60);
		s = IJ.pad(hh, 2) + ":" + IJ.pad(m, 2) + ":" + IJ.pad(sec - 3600 * hh - 60 * m, 2);
	}
	if (d > 0)
		s = s + "." + IJ.pad(fra, d);
	return sg + s;
}

// Adds one time stamp to the overlay of the current image.
// pm 0: on every plane; 1: on stack plane idx; 2: on every channel and slice of time point k
// (hyperstack position); 3: on stack plane idx (plain stacks with several planes per time point).
function addStamp(txt, pm, k, idx) {
	makeText(txt, 0, 0);
	getSelectionBounds(bx, by, bw, bh);
	if (gPos == 6) {
		x = gSelX;
		y = gSelY;
	} else {
		col3 = gPos % 3;
		if (col3 == 0)
			x = gMg;
		else if (col3 == 1)
			x = round((gW - bw) / 2);
		else
			x = gW - gMg - bw;
		if (gPos < 3)
			y = gMg;
		else
			y = gH - gMg - bh;
	}
	Roi.move(x, y);
	Roi.setStrokeColor(gCol);
	if (gBg != "")
		Roi.setFillColor(gBg);
	Roi.setName(gName);
	Overlay.addSelection;
	if (pm == 1 || pm == 3)
		Overlay.setPosition(idx);
	else if (pm == 2) {
		if (gTDim == "t")
			Overlay.setPosition(0, 0, k);
		else
			Overlay.setPosition(0, k, 0);
	}
}

function restoreSel(t, xs, ys, bx, by, bw, bh) {
	if (t == 0)
		makeRectangle(bx, by, bw, bh);
	else if (t == 1)
		makeOval(bx, by, bw, bh);
	else if (t == 5 && xs.length >= 2)
		makeLine(xs[0], ys[0], xs[1], ys[1]);
	else if ((t >= 2 && t <= 4) || (t >= 6 && t <= 8) || t == 10) {
		if (xs.length > 0)
			makeSelection(t, xs, ys);
	} else
		run("Select None");
}

function decimalsOf(v) {
	for (d = 0; d < 6; d++) {
		q = pow(10, d);
		if (abs(v - round(v * q) / q) < 1e-9 * maxOf(1, abs(v)))
			return d;
	}
	return 6;
}

function indexIn(a, v) {
	for (i = 0; i < a.length; i++)
		if (a[i] == v)
			return i;
	return 0;
}

function inList(a, v, d) {
	for (i = 0; i < a.length; i++)
		if (a[i] == v)
			return v;
	return d;
}

function b01(b) {
	if (b)
		return "1";
	return "0";
}

function pget(k, d) {
	return call("ij.Prefs.get", "sts." + k, d);
}

function pset(k, v) {
	call("ij.Prefs.set", "sts." + k, "" + v);
}
