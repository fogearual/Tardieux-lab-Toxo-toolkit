// Object Velocity  -  ImageJ / Fiji macro, version 1 (October 2026), ImageJ 1.53b or later
//
// WHAT IT DOES
// Measures the speed of objects (beads, parasites...) that you point yourself on a movie. For each object you click
// its position once on each frame where you follow it (Multi-point tool); the macro keeps
// the track in the ROI Manager ("object 1", "object 2" ...), draws it on the image and measures:
//   SPEED = distance travelled along the track / time between its first and last point.
// This is the mean of the steps of the track, each step weighted by its duration, so it
// stays right when the frames are not evenly spaced (a stack of frames picked from a
// movie). With evenly spaced frames it equals the plain mean of the step speeds.
// Also given for each object: the straight-line speed (first to last point / time: less
// sensitive to pointing errors, lower when the path curves), the speed of each step, their
// mean and SD, and the straightness (straight-line distance / path; 1 = straight path).
//
// TIME OF EACH FRAME
// As in Smart Time Stamper: time = (original frame - 1) x frame interval, the original
// frame numbers being read from the slice labels that Make Substack keeps (Bio-Formats
// "t:45/200 - movie", AVI times, "frame 45" ...), so that a stack of frames picked from a
// movie gives the right times. They are shown in the dialog to be checked, and can be
// typed there. The frame interval comes from Image > Properties, the pixel size too.
//
// HOW TO USE
// 1. Open the movie (or the substack), run the macro, check frames, interval, pixel size.
// 2. For each object: click it once on each frame (next frame: > key), then OK.
//    Alt+click removes a wrong point. When all objects are done: OK without any point.
// 3. Results: table "Object speeds" (one row per object), table "Object steps" (one row per
//    step), summary in the Log. The rows of other images are kept in the tables (several
//    movies of a condition in one table); running the macro again on an image replaces
//    its rows.
// Tracks already in the ROI Manager are measured too and new objects are numbered after
// them: to go on later, open the saved .zip (it fills the ROI Manager) and run again.
// Saved next to the image (or in a folder you choose): <image>_object_tracks.zip and the
// two tables as .csv. Existing files are never overwritten (_2, _3 ... added).
//
// NOTE: at a zoom of 150% or less, ImageJ rounds the first point of a multi-point selection
// to the pixel while the next ones are kept at x.5 / y.5; the macro puts the first point back
// on the same grid (otherwise the first step would be off by half a pixel in x and y).
//
// INSTALL: copy this file into the folder Fiji.app/plugins/Toxo Toolkit/ (create it if
// needed) and restart Fiji: Plugins > Toxo Toolkit > Object Velocity. (Or open it and click Run.)

var parseErr = "";
var selfLab = false;
var gName = "Object track";
var gPm = 0;
var gTDim = "t";
var gLW = 1;
var gR = 5;
var gFS = 12;
var gW = 0;
var gH = 0;

requires("1.53b");
if (nImages == 0)
	exit("Object Velocity:\nopen the movie (or the stack made from it) first.");

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
} else
	exit("Object Velocity:\nthis image has only one frame: open the movie.");
cur = getSliceNumber();
um = fromCharCode(181) + "m";

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

// ---------------------------------------------------------------- pixel size (in micrometres)
getPixelSize(pu, pw, ph);
pf = lengthFactor(pu);
pxNote = "";
pxWarn = false;
if (pf > 0 && pw > 0) {
	px = pw * pf;
	pyRatio = ph / pw;
} else {
	px = 1;
	pyRatio = 1;
	pxNote = "No pixel size in Image > Properties: type it here.";
	pxWarn = true;
}

// ---------------------------------------------------------------- original frame numbers
setBatchMode(true);
labels = newArray(nT);
anyLabel = false;
for (k = 1; k <= nT; k++) {
	setSlice(stackIdx(k, tDim, nC, nZ));
	lab = getMetadata("Label");
	lab = firstLine(lab);
	labels[k - 1] = lab;
	if (lab != "")
		anyLabel = true;
}
setSlice(cur);
setBatchMode(false);

ivS0 = iv * unitSec[ui];
fr = newArray(0);
src = 0;
if (anyLabel) {
	for (p = 1; p <= 5; p++) {
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
else
	dimNote = "" + nT + " slices, each taken as one time point";
if (nC > 1)
	dimNote = dimNote + ", " + nC + " channels";

// ---------------------------------------------------------------- ROI Manager
nRM = 0;
nextObj = 1;
if (isOpen("ROI Manager")) {
	nRM = roiManager("count");
	for (i = 0; i < nRM; i++) {
		nm = call("ij.plugin.frame.RoiManager.getName", "" + i);
		if (matches(nm, "(?i)(?:object|bead)\\s*\\d+")) {
			v = parseInt(replace(nm, "(?i)(?:object|bead)\\s*(\\d+)", "$1"));
			if (v >= nextObj)
				nextObj = v + 1;
		}
	}
}

// ---------------------------------------------------------------- dialog
spList = newArray(um + "/s", um + "/min");
pv = pget("speedunit", spList[0]);
spC = inList(spList, pv, spList[0]);
pv = pget("point", "1");
pointNew = false;
if (pv == "1" || nRM == 0)
	pointNew = true;
pv = pget("draw", "1");
drawOn = false;
if (pv == "1")
	drawOn = true;
pv = pget("save", "1");
saveOn = false;
if (pv == "1")
	saveOn = true;
dir0 = getDirectory("image");
if (dir0 == "")
	saveLabel = "Save the tracks (.zip) and the tables (.csv); a folder will be asked";
else
	saveLabel = "Save the tracks (.zip) and the tables (.csv) next to the image";
ivDec = decimalsOf(iv);
ivStr = d2s(iv, ivDec) + " " + unitList[ui];
pxDec = maxOf(2, decimalsOf(px));

help = "<html><b>Object Velocity</b><br>"
	+ "<b>Pointing</b>: for each object, click it once on each frame where you follow it (Multi-point tool; "
	+ "next frame with the &gt; key or the slider), then OK. Alt+click removes a point. "
	+ "When all objects are done, click OK without any point. Each object is kept in the ROI Manager.<br>"
	+ "<b>Speed</b> = distance along the track / time between its first and last point: the mean of its steps, "
	+ "each weighted by its duration (with evenly spaced frames: the plain mean of the step speeds).<br>"
	+ "<b>Straight-line speed</b> = distance between the first and last point / time: less sensitive to "
	+ "pointing errors, lower when the path curves. <b>Straightness</b> = straight-line distance / path.<br>"
	+ "<b>Original frames</b>: the frame number in the movie of each time point of this image, in order, "
	+ "read from the slice labels (as in Smart Time Stamper). Check them; lists and ranges: 3,15,27,40-43.<br>"
	+ "<b>Frame interval</b>: a number and a unit (ms, s, min, h), from Image &gt; Properties.<br>"
	+ "<b>Tables</b>: \"Object speeds\" (one row per object) and \"Object steps\" (one row per step). The rows of "
	+ "other images are kept: several movies go in one table; running again on an image replaces its rows.<br>"
	+ "<b>Going on later</b>: open the saved tracks (.zip): they fill the ROI Manager; run again, new objects "
	+ "are added after them and all are measured.</html>";

ok = false;
while (!ok) {
	Dialog.create("Object Velocity");
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
	Dialog.addNumber("Pixel size", px, pxDec, 8, um);
	if (pxNote != "")
		Dialog.addMessage(pxNote, 12, "#c05000");
	Dialog.addChoice("Speed in", spList, spC);
	Dialog.addCheckbox("Point new objects now", pointNew);
	Dialog.addCheckbox("Draw the tracks on the image", drawOn);
	Dialog.addCheckbox(saveLabel, saveOn);
	if (nRM > 0)
		Dialog.addMessage("ROI Manager: " + nRM + " selection(s) already there; the object tracks among them are measured too\n(new objects start at object " + nextObj + ").", 12, "#006400");
	Dialog.addHelp(help);
	Dialog.show();
	frStr = Dialog.getString();
	ivStr = Dialog.getString();
	px = Dialog.getNumber();
	spC = Dialog.getChoice();
	pointNew = Dialog.getCheckbox();
	drawOn = Dialog.getCheckbox();
	saveOn = Dialog.getCheckbox();
	ivS = parseInterval(ivStr, ui);
	err = "";
	frN = parseFrames(frStr);
	if (parseErr != "")
		err = "Original frames: " + parseErr + ".";
	else if (frN.length != nT)
		err = "Original frames: " + frN.length + " numbers given, but the image has " + nT + " time points.";
	if (err == "" && !(ivS > 0))
		err = "Frame interval: type a number above 0 and a unit, for example 0.87 s or 870 ms.";
	if (err == "" && !(px > 0))
		err = "Type the pixel size in micrometres (more than 0).";
	if (err == "" && !pointNew && nRM == 0)
		err = "The ROI Manager has no object track: tick \"Point new objects now\".";
	if (err == "")
		ok = true;
	else
		showMessage("Object Velocity", err);
}
fr = frN;
py = px * pyRatio;
if (spC == spList[1]) {
	spF = 60;
	su = "um/min";
} else {
	spF = 1;
	su = "um/s";
}
pset("speedunit", spC);
pv = b01(pointNew);
pset("point", pv);
pv = b01(drawOn);
pset("draw", pv);
pv = b01(saveOn);
pset("save", pv);

// overlay geometry and positions (one drawing per frame of the track)
gW = w;
gH = h;
md = minOf(w, h);
gLW = maxOf(1, round(md / 500));
gR = maxOf(4, round(md / 70));
gFS = maxOf(10, round(md / 40));
gTDim = tDim;
if (nC == 1 && (nZ == 1 || nF == 1))
	gPm = 1;
else if (Stack.isHyperstack || nC > 1)
	gPm = 2;
else
	gPm = 0;
pal = newArray("#ff00ff", "#00ffff", "#ffff00", "#ff9900", "#00ff00", "#ff4040", "#9999ff", "#ffffff");
setFont("SansSerif", gFS, "bold antialiased");

// ---------------------------------------------------------------- pointing
if (pointNew) {
	selectImage(id);
	setTool("multipoint");
	b = nextObj;
	keep = false;
	if (selectionType() == 10)
		keep = true;
	going = true;
	while (going) {
		selectImage(id);
		if (!keep)
			run("Select None");
		keep = false;
		waitForUser("Object Velocity  -  object " + b, "Object " + b + ": click it once on each frame where you follow it\n"
			+ "(next frame: > key or the slider; Alt+click removes a point).\nThen click OK.\n \n"
			+ "All objects done: click OK without any point.");
		selectImage(id);
		if (selectionType() != 10)
			going = false;
		else {
			getSelectionCoordinates(xs, ys);
			fixFirst(xs, ys);
			n = xs.length;
			msg = "";
			tps = newArray(n);
			cnt = newArray(nT + 1);
			for (j = 0; j < n; j++) {
				pos = Roi.getPointPosition(j);
				if (pos < 1)
					msg = "its points are not attached to frames";
				else {
					tps[j] = timePoint(pos, nC, nZ, tDim);
					cnt[tps[j]] = cnt[tps[j]] + 1;
					if (cnt[tps[j]] == 2)
						msg = "2 points on the same frame (time point " + tps[j] + " of this stack)";
				}
			}
			if (n < 2)
				msg = "a track needs the object on at least 2 frames";
			if (msg != "") {
				showMessage("Object Velocity", "Object " + b + " not kept: " + msg + ".\n \nYour points are still there: correct them (Alt+click removes a point)\n"
					+ "and click OK, or remove them all (Edit > Selection > Select None)\nand point the object again.");
				keep = true;
			} else {
				Roi.setName("object " + b);
				roiManager("add");
				if (drawOn) {
					rk = Array.rankPositions(tps);
					sx = newArray(n);
					sy = newArray(n);
					st = newArray(n);
					for (j = 0; j < n; j++) {
						sx[j] = xs[rk[j]];
						sy[j] = ys[rk[j]];
						st[j] = tps[rk[j]];
					}
					drawTrack(sx, sy, st, pal[(b - 1) % pal.length], "" + b);
				}
				b++;
			}
		}
	}
	selectImage(id);
	run("Select None");
}

// ---------------------------------------------------------------- measuring the tracks
if (!isOpen("ROI Manager"))
	exit("Object Velocity:\nno object track to measure.");
nR = roiManager("count");
if (nR == 0)
	exit("Object Velocity:\nno object track to measure.");
setBatchMode(true);
selectImage(id);
if (Overlay.size > 0)
	Overlay.removeRois(gName);
bNum = newArray(0);
bPts = newArray(0);
bF1 = newArray(0);
bF2 = newArray(0);
bDur = newArray(0);
bPath = newArray(0);
bNet = newArray(0);
bSp = newArray(0);
bStr = newArray(0);
bMean = newArray(0);
bSD = newArray(0);
bStn = newArray(0);
sObj = newArray(0);
sStep = newArray(0);
sF1 = newArray(0);
sF2 = newArray(0);
sDt = newArray(0);
sDist = newArray(0);
sSp = newArray(0);
sX1 = newArray(0);
sY1 = newArray(0);
sX2 = newArray(0);
sY2 = newArray(0);
nOther = 0;
skipped = "";
for (r = 0; r < nR; r++) {
	roiManager("select", r);
	nm = call("ij.plugin.frame.RoiManager.getName", "" + r);
	if (selectionType() != 10) {
		nOther++;
		continue;
	}
	getSelectionCoordinates(xs, ys);
	fixFirst(xs, ys);
	n = xs.length;
	why = "";
	if (n < 2)
		why = "fewer than 2 points";
	tps = newArray(n);
	for (j = 0; j < n; j++) {
		pos = Roi.getPointPosition(j);
		if (pos < 1)
			why = "points not attached to frames";
		else {
			tps[j] = timePoint(pos, nC, nZ, tDim);
			if (tps[j] > nT)
				why = "points beyond the last frame of this image";
		}
	}
	if (why == "") {
		rk = Array.rankPositions(tps);
		sx = newArray(n);
		sy = newArray(n);
		st = newArray(n);
		tt = newArray(n);
		for (j = 0; j < n; j++) {
			sx[j] = xs[rk[j]];
			sy[j] = ys[rk[j]];
			st[j] = tps[rk[j]];
			tt[j] = (fr[st[j] - 1] - 1) * ivS;
		}
		for (j = 1; j < n; j++) {
			if (st[j] == st[j - 1])
				why = "2 points on the same frame";
			else if (tt[j] <= tt[j - 1])
				why = "original frames not in increasing order";
		}
	}
	if (why != "") {
		if (skipped != "")
			skipped = skipped + "; ";
		skipped = skipped + nm + " (" + why + ")";
		continue;
	}
	if (matches(nm, "(?i)(?:object|bead)\\s*\\d+"))
		num = parseInt(replace(nm, "(?i)(?:object|bead)\\s*(\\d+)", "$1"));
	else
		num = r + 1;
	path = 0;
	sum = 0;
	sum2 = 0;
	for (j = 1; j < n; j++) {
		dx = (sx[j] - sx[j - 1]) * px;
		dy = (sy[j] - sy[j - 1]) * py;
		d = sqrt(dx * dx + dy * dy);
		dt = tt[j] - tt[j - 1];
		v = d / dt * spF;
		path = path + d;
		sum = sum + v;
		sum2 = sum2 + v * v;
		sObj = Array.concat(sObj, num);
		sStep = Array.concat(sStep, j);
		sF1 = Array.concat(sF1, fr[st[j - 1] - 1]);
		sF2 = Array.concat(sF2, fr[st[j] - 1]);
		sDt = Array.concat(sDt, dt);
		sDist = Array.concat(sDist, d);
		sSp = Array.concat(sSp, v);
		sX1 = Array.concat(sX1, sx[j - 1]);
		sY1 = Array.concat(sY1, sy[j - 1]);
		sX2 = Array.concat(sX2, sx[j]);
		sY2 = Array.concat(sY2, sy[j]);
	}
	dx = (sx[n - 1] - sx[0]) * px;
	dy = (sy[n - 1] - sy[0]) * py;
	net = sqrt(dx * dx + dy * dy);
	dur = tt[n - 1] - tt[0];
	ns = n - 1;
	mean = sum / ns;
	sd = NaN;
	if (ns > 1)
		sd = sqrt(maxOf(0, (sum2 - sum * sum / ns) / (ns - 1)));
	stn = NaN;
	if (path > 0)
		stn = net / path;
	bNum = Array.concat(bNum, num);
	bPts = Array.concat(bPts, n);
	bF1 = Array.concat(bF1, fr[st[0] - 1]);
	bF2 = Array.concat(bF2, fr[st[n - 1] - 1]);
	bDur = Array.concat(bDur, dur);
	bPath = Array.concat(bPath, path);
	bNet = Array.concat(bNet, net);
	bSp = Array.concat(bSp, path / dur * spF);
	bStr = Array.concat(bStr, net / dur * spF);
	bMean = Array.concat(bMean, mean);
	bSD = Array.concat(bSD, sd);
	bStn = Array.concat(bStn, stn);
	if (drawOn)
		drawTrack(sx, sy, st, pal[(num - 1) % pal.length], "" + num);
}
selectImage(id);
run("Select None");
roiManager("deselect");
setSlice(cur);
setBatchMode(false);
selectImage(id);
updateDisplay();
nb = bNum.length;
if (nb == 0) {
	msg = "No usable object track";
	if (skipped != "")
		msg = msg + ":\n" + skipped;
	exit("Object Velocity:\n" + msg + ".");
}

// ---------------------------------------------------------------- tables (rows of other images kept)
spCol = "Speed (" + su + ")";
tb = "Object speeds";
restarted = false;
if (isOpen(tb)) {
	hd = Table.headings(tb);
	if (indexOf("\t" + hd + "\t", "\t" + spCol + "\t") < 0) {
		Table.reset(tb);
		restarted = true;
	}
} else
	Table.create(tb);
for (r = Table.size(tb) - 1; r >= 0; r--)
	if (Table.getString("Image", r, tb) == title)
		Table.deleteRows(r, r, tb);
r0 = Table.size(tb);
for (i = 0; i < nb; i++) {
	q = r0 + i;
	Table.set("Image", q, title, tb);
	Table.set("Object", q, bNum[i], tb);
	Table.set("Points", q, bPts[i], tb);
	Table.set("First frame", q, bF1[i], tb);
	Table.set("Last frame", q, bF2[i], tb);
	Table.set("Duration (s)", q, bDur[i], tb);
	Table.set("Path (um)", q, bPath[i], tb);
	Table.set("Displacement (um)", q, bNet[i], tb);
	Table.set(spCol, q, bSp[i], tb);
	Table.set("Straight-line speed (" + su + ")", q, bStr[i], tb);
	Table.set("Mean step speed (" + su + ")", q, bMean[i], tb);
	Table.set("SD step speeds (" + su + ")", q, bSD[i], tb);
	Table.set("Straightness", q, bStn[i], tb);
}
Table.update(tb);

ts = "Object steps";
if (isOpen(ts)) {
	hd = Table.headings(ts);
	if (indexOf("\t" + hd + "\t", "\t" + spCol + "\t") < 0)
		Table.reset(ts);
} else
	Table.create(ts);
for (r = Table.size(ts) - 1; r >= 0; r--)
	if (Table.getString("Image", r, ts) == title)
		Table.deleteRows(r, r, ts);
r0 = Table.size(ts);
for (i = 0; i < sObj.length; i++) {
	q = r0 + i;
	Table.set("Image", q, title, ts);
	Table.set("Object", q, sObj[i], ts);
	Table.set("Step", q, sStep[i], ts);
	Table.set("From frame", q, sF1[i], ts);
	Table.set("To frame", q, sF2[i], ts);
	Table.set("dt (s)", q, sDt[i], ts);
	Table.set("Distance (um)", q, sDist[i], ts);
	Table.set(spCol, q, sSp[i], ts);
	Table.set("x1 (px)", q, sX1[i], ts);
	Table.set("y1 (px)", q, sY1[i], ts);
	Table.set("x2 (px)", q, sX2[i], ts);
	Table.set("y2 (px)", q, sY2[i], ts);
}
Table.update(ts);

// ---------------------------------------------------------------- summary in the Log
st1 = stats(bSp);
st2 = stats(bStr);
print("Object Velocity  -  " + title);
line = "   " + nb + " object";
if (nb > 1)
	line = line + "s";
if (src >= 1 && src <= 6)
	line = line + "; original frames from the image";
else
	line = line + "; original frames as typed";
line = line + "; interval " + d2s(ivS, decimalsOf(ivS)) + " s; pixel size " + d2s(px, pxDec) + " um";
print(line);
if (skipped != "")
	print("   not measured: " + skipped);
if (nOther > 0)
	print("   " + nOther + " selection(s) of the ROI Manager are not point selections: not used.");
// (a function that returns a string is assigned to a variable before it is used)
l1 = statLine(st1);
l2 = statLine(st2);
print("   Speed (" + su + "):               " + l1);
print("   Straight-line speed (" + su + "): " + l2);
if (restarted)
	print("   (table \"Object speeds\" started again: the speed unit changed)");
nAll = Table.size(tb);
if (nAll > nb) {
	allSp = Table.getColumn(spCol, tb);
	imgs = "";
	nImg = 0;
	for (r = 0; r < nAll; r++) {
		im = Table.getString("Image", r, tb);
		if (indexOf(imgs, "|" + im + "|") < 0) {
			imgs = imgs + "|" + im + "|";
			nImg++;
		}
	}
	st3 = stats(allSp);
	l3 = statLine(st3);
	print("   All images of the table (" + nImg + " images, " + nAll + " objects): speed " + l3);
}

// ---------------------------------------------------------------- saving
if (saveOn) {
	dir = dir0;
	if (dir == "")
		dir = getDirectory("Folder for the object tracks and tables of " + title);
	base = title;
	dot = lastIndexOf(base, ".");
	if (dot > 0 && lengthOf(base) - dot <= 5)
		base = substring(base, 0, dot);
	base = replace(base, "[^A-Za-z0-9._-]+", "_");
	base = replace(base, "^_+|_+$", "");
	if (base == "")
		base = "image";
	sfx = "";
	k = 1;
	while (File.exists(dir + base + "_object_tracks" + sfx + ".zip") || File.exists(dir + base + "_object_speeds" + sfx + ".csv") || File.exists(dir + base + "_object_steps" + sfx + ".csv")) {
		k++;
		sfx = "_" + k;
	}
	fz = base + "_object_tracks" + sfx + ".zip";
	f1 = base + "_object_speeds" + sfx + ".csv";
	f2 = base + "_object_steps" + sfx + ".csv";
	roiManager("deselect");
	roiManager("save", dir + fz);
	qt = "\"";
	tx = "Image,Object,Points,First frame,Last frame,Duration (s),Path (um),Displacement (um)," + spCol + ",Straight-line speed (" + su + "),Mean step speed (" + su + "),SD step speeds (" + su + "),Straightness\n";
	for (i = 0; i < nb; i++) {
		c1 = num2s(bSD[i]);
		c2 = num2s(bStn[i]);
		tx = tx + qt + title + qt + "," + bNum[i] + "," + bPts[i] + "," + bF1[i] + "," + bF2[i] + "," + d2s(bDur[i], 5) + "," + d2s(bPath[i], 5) + "," + d2s(bNet[i], 5) + "," + d2s(bSp[i], 5) + "," + d2s(bStr[i], 5) + "," + d2s(bMean[i], 5) + "," + c1 + "," + c2 + "\n";
	}
	File.saveString(tx, dir + f1);
	tx = "Image,Object,Step,From frame,To frame,dt (s),Distance (um)," + spCol + ",x1 (px),y1 (px),x2 (px),y2 (px)\n";
	for (i = 0; i < sObj.length; i++)
		tx = tx + qt + title + qt + "," + sObj[i] + "," + sStep[i] + "," + sF1[i] + "," + sF2[i] + "," + d2s(sDt[i], 5) + "," + d2s(sDist[i], 5) + "," + d2s(sSp[i], 5) + "," + sX1[i] + "," + sY1[i] + "," + sX2[i] + "," + sY2[i] + "\n";
	File.saveString(tx, dir + f2);
	print("   saved in " + dir + ": " + fz + ", " + f1 + ", " + f2);
}
selectImage(id);

// ================================================================ functions

// Index of the stack plane (channel 1, slice 1 or the time point) of time point k.
function stackIdx(k, td, nc, nz) {
	if (td == "t")
		return (k - 1) * nc * nz + 1;
	return (k - 1) * nc + 1;
}

// Time point (frame of this stack, or slice when the slices are the time points) of stack index pos.
function timePoint(pos, nc, nz, td) {
	if (td == "t")
		return floor((pos - 1) / (nc * nz)) + 1;
	return floor((pos - 1) / nc) % nz + 1;
}

// Draws one track on the overlay: on each frame of the track, the whole path, a circle on
// the object of that frame and the object number.
function drawTrack(xs, ys, tps, col, lab) {
	n = xs.length;
	for (j = 0; j < n; j++) {
		if (n > 1) {
			makeSelection("polyline", xs, ys);
			addEl(col, tps[j], true);
		}
		makeOval(xs[j] - gR, ys[j] - gR, 2 * gR, 2 * gR);
		addEl(col, tps[j], true);
		tx = xs[j] + gR + 1;
		if (tx > gW - 2 * gFS)
			tx = xs[j] - gR - 2 * gFS;
		ty = ys[j] - gR - gFS - 4;
		if (ty < 0)
			ty = ys[j] + gR + 1;
		makeText(lab, maxOf(0, tx), maxOf(0, ty));
		addEl(col, tps[j], false);
	}
}

function addEl(col, tp, line) {
	Roi.setStrokeColor(col);
	if (line)
		Roi.setStrokeWidth(gLW);
	Roi.setName(gName);
	Overlay.addSelection;
	if (gPm == 1)
		Overlay.setPosition(tp);
	else if (gPm == 2) {
		if (gTDim == "t")
			Overlay.setPosition(0, 0, tp);
		else
			Overlay.setPosition(0, tp, 0);
	}
}

// At a zoom of 150% or less, ImageJ rounds the first point of a multi-point selection to the
// pixel while the next points are kept at x.5 / y.5: the first point is moved by -0.5 to be
// on the same grid (otherwise the first step is off by half a pixel in x and in y).
function fixFirst(xs, ys) {
	n = xs.length;
	if (n < 2)
		return;
	if (xs[0] != round(xs[0]) || ys[0] != round(ys[0]))
		return;
	for (j = 1; j < n; j++) {
		if (abs(xs[j] - floor(xs[j]) - 0.5) > 0.000001 || abs(ys[j] - floor(ys[j]) - 0.5) > 0.000001)
			return;
	}
	xs[0] = xs[0] - 0.5;
	ys[0] = ys[0] - 0.5;
}

// mean, SD, SEM, median, min, max (NaN values left out)
function stats(a) {
	v = newArray(0);
	for (i = 0; i < a.length; i++)
		if (!isNaN(a[i]))
			v = Array.concat(v, a[i]);
	n = v.length;
	out = newArray(n, NaN, NaN, NaN, NaN, NaN, NaN);
	if (n == 0)
		return out;
	s = 0;
	for (i = 0; i < n; i++)
		s = s + v[i];
	m = s / n;
	s2 = 0;
	for (i = 0; i < n; i++)
		s2 = s2 + (v[i] - m) * (v[i] - m);
	sd = NaN;
	if (n > 1)
		sd = sqrt(s2 / (n - 1));
	Array.sort(v);
	if (n % 2 == 1)
		med = v[(n - 1) / 2];
	else
		med = (v[n / 2 - 1] + v[n / 2]) / 2;
	out[1] = m;
	out[2] = sd;
	out[3] = sd / sqrt(n);
	out[4] = med;
	out[5] = v[0];
	out[6] = v[n - 1];
	return out;
}

function statLine(s) {
	a1 = num2s(s[1]);
	a2 = num2s(s[2]);
	a3 = num2s(s[3]);
	a4 = num2s(s[4]);
	a5 = num2s(s[5]);
	a6 = num2s(s[6]);
	return "mean " + a1 + "  SD " + a2 + "  SEM " + a3 + "  median " + a4 + "  (min " + a5 + ", max " + a6 + ", n = " + s[0] + ")";
}

function num2s(v) {
	if (isNaN(v))
		return "NaN";
	return d2s(v, 5);
}

// micrometres per unit of the image calibration; -1 when there is no length unit
function lengthFactor(u) {
	u = toLowerCase(trim(u));
	if (u == "micron" || u == "microns" || u == "um" || u == fromCharCode(181) + "m" || u == fromCharCode(956) + "m" || startsWith(u, "micromet"))
		return 1;
	if (u == "nm" || startsWith(u, "nanomet"))
		return 0.001;
	if (u == "mm" || startsWith(u, "millimet"))
		return 1000;
	return -1;
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

// Original frame numbers from the slice labels with pattern p (as in Smart Time Stamper);
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
		// 1, 2, 3 ... n: the numbers count the slices of this stack
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

function decimalsOf(v) {
	for (d = 0; d < 6; d++) {
		q = pow(10, d);
		if (abs(v - round(v * q) / q) < 1e-9 * maxOf(1, abs(v)))
			return d;
	}
	return 6;
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
	return call("ij.Prefs.get", "objvel." + k, d);
}

function pset(k, v) {
	call("ij.Prefs.set", "objvel." + k, "" + v);
}
