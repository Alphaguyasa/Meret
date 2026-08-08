'use strict';
// Generates the phase 4 GPX fixtures for core/location/src/test/resources/traces/.
// Deterministic: a seeded PRNG, so regenerating produces byte-identical files and the
// ground-truth numbers printed at the end stay valid.
//
// Geometry matches GeoMath: spherical earth, R = 6371008.8 (IUGG mean radius).

const fs = require('fs');
const path = require('path');

const R = 6371008.8;
const toRad = (d) => (d * Math.PI) / 180;
const toDeg = (r) => (r * 180) / Math.PI;

function destination(lat, lon, distanceM, bearingDeg) {
  const ang = distanceM / R;
  const br = toRad(bearingDeg);
  const p1 = toRad(lat);
  const l1 = toRad(lon);
  const sinLat = Math.sin(p1) * Math.cos(ang) + Math.cos(p1) * Math.sin(ang) * Math.cos(br);
  const p2 = Math.asin(Math.max(-1, Math.min(1, sinLat)));
  const l2 = l1 + Math.atan2(
    Math.sin(br) * Math.sin(ang) * Math.cos(p1),
    Math.cos(ang) - Math.sin(p1) * sinLat,
  );
  return [toDeg(p2), toDeg(l2)];
}

function haversine(a, b) {
  const dLat = toRad(b[0] - a[0]);
  const dLon = toRad(b[1] - a[1]);
  const s1 = Math.sin(dLat / 2);
  const s2 = Math.sin(dLon / 2);
  const h = s1 * s1 + Math.cos(toRad(a[0])) * Math.cos(toRad(b[0])) * s2 * s2;
  return 2 * R * Math.asin(Math.min(1, Math.sqrt(h)));
}

function pathLength(points) {
  let total = 0;
  for (let i = 1; i < points.length; i++) total += haversine(points[i - 1], points[i]);
  return total;
}

// mulberry32 — small, seeded, good enough for jitter.
function rng(seed) {
  return function () {
    seed |= 0;
    seed = (seed + 0x6d2b79f5) | 0;
    let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function gaussian(rand) {
  // Box-Muller.
  let u = 0;
  let v = 0;
  while (u === 0) u = rand();
  while (v === 0) v = rand();
  return Math.sqrt(-2 * Math.log(u)) * Math.cos(2 * Math.PI * v);
}

// Offsets a point by (north, east) metres.
function offsetMetres(p, northM, eastM) {
  const q = destination(p[0], p[1], Math.abs(northM), northM >= 0 ? 0 : 180);
  return destination(q[0], q[1], Math.abs(eastM), eastM >= 0 ? 90 : 270);
}

const START = [8.9806, 38.7578]; // Addis Ababa.
const WALK_SPEED_MPS = 1.4;

/**
 * A noise-free walked path. Bearing turns gently so the trace is not a pure straight line,
 * but stays gentle enough that Kalman corner-cutting does not eat into the 2% budget.
 */
function walkTruth(steps, stepMetres, startBearing, turnPerStep) {
  const points = [START];
  let bearing = startBearing;
  for (let i = 1; i <= steps; i++) {
    points.push(destination(points[i - 1][0], points[i - 1][1], stepMetres, bearing));
    bearing += turnPerStep;
  }
  return points;
}

function jitter(points, seed, sigmaM) {
  const rand = rng(seed);
  return points.map((p) => offsetMetres(p, gaussian(rand) * sigmaM, gaussian(rand) * sigmaM));
}

const BASE_TIME = Date.parse('2026-03-14T07:30:00Z');

function isoAt(offsetMs) {
  return new Date(BASE_TIME + offsetMs).toISOString().replace('.000Z', 'Z');
}

function gpx({ name, description, points, times, accuracy, segments }) {
  const lines = [];
  lines.push('<?xml version="1.0" encoding="UTF-8"?>');
  lines.push('<gpx version="1.1" creator="Meret fixture generator" xmlns="http://www.topografix.com/GPX/1/1">');
  lines.push('  <metadata>');
  lines.push(`    <name>${name}</name>`);
  lines.push(`    <desc>${description}</desc>`);
  lines.push(`    <time>${isoAt(0)}</time>`);
  lines.push('  </metadata>');
  lines.push('  <trk>');
  lines.push(`    <name>${name}</name>`);

  const segmentOf = segments || (() => 0);
  let currentSegment = null;
  points.forEach((p, i) => {
    const seg = segmentOf(i);
    if (seg !== currentSegment) {
      if (currentSegment !== null) lines.push('    </trkseg>');
      lines.push('    <trkseg>');
      currentSegment = seg;
    }
    const acc = typeof accuracy === 'function' ? accuracy(i) : accuracy;
    lines.push(`      <trkpt lat="${p[0].toFixed(7)}" lon="${p[1].toFixed(7)}">`);
    lines.push(`        <time>${isoAt(times[i])}</time>`);
    lines.push('        <extensions>');
    lines.push(`          <accuracy>${acc.toFixed(1)}</accuracy>`);
    lines.push('        </extensions>');
    lines.push('      </trkpt>');
  });
  lines.push('    </trkseg>');
  lines.push('  </trk>');
  lines.push('</gpx>');
  return lines.join('\n') + '\n';
}

const outDir = process.argv[2];
fs.mkdirSync(outDir, { recursive: true });
const report = {};

// ---------------------------------------------------------------- clean 1 km walk
{
  const stepMetres = WALK_SPEED_MPS * 1.0; // 1 Hz
  const steps = Math.round(1000 / stepMetres);
  const truth = walkTruth(steps, stepMetres, 42, 0.02);
  const noisy = jitter(truth, 20260314, 1.5);
  const times = noisy.map((_, i) => i * 1000);

  fs.writeFileSync(
    path.join(outDir, 'clean_walk_1km.gpx'),
    gpx({
      name: 'Clean 1 km walk',
      description:
        'Gently curving walk at 1.4 m/s, 1 Hz, Gaussian jitter sigma 1.5 m, reported accuracy 4.0 m. ' +
        `Noise-free path length ${pathLength(truth).toFixed(2)} m.`,
      points: noisy,
      times,
      accuracy: 4.0,
    }),
  );
  report.clean = {
    points: noisy.length,
    truthMetres: pathLength(truth),
    noisyMetres: pathLength(noisy),
    durationS: times[times.length - 1] / 1000,
  };
}

// ------------------------------------------------- same walk with 200 m outliers
{
  const stepMetres = WALK_SPEED_MPS * 1.0;
  const steps = Math.round(1000 / stepMetres);
  const truth = walkTruth(steps, stepMetres, 42, 0.02);
  const noisy = jitter(truth, 20260314, 1.5);
  const times = noisy.map((_, i) => i * 1000);

  // Indices chosen to sit well inside the trace, spaced apart, never adjacent — an
  // adjacent pair would let the second be judged against the first as its reference.
  const outlierIndices = [97, 198, 301, 404, 512, 631];
  const withOutliers = noisy.map((p, i) =>
    outlierIndices.includes(i) ? offsetMetres(p, 200 * (i % 2 === 0 ? 1 : -1), 0) : p,
  );

  fs.writeFileSync(
    path.join(outDir, 'walk_1km_with_outliers.gpx'),
    gpx({
      name: 'Clean 1 km walk with injected outliers',
      description:
        `Identical to clean_walk_1km.gpx with ${outlierIndices.length} points displaced 200 m ` +
        `north/south at indices ${outlierIndices.join(', ')}. Accuracy still reported as 4.0 m, ` +
        'so the validity gate must catch them on implied speed rather than on accuracy.',
      points: withOutliers,
      times,
      accuracy: 4.0,
    }),
  );
  report.outliers = { points: withOutliers.length, outlierIndices };
}

// ---------------------------------------------------------- stationary 30 minutes
{
  const intervalMs = 3000; // STANDARD profile.
  const count = (30 * 60 * 1000) / intervalMs;
  const fixed = Array.from({ length: count }, () => START);
  const noisy = jitter(fixed, 77001, 1.5);
  const times = noisy.map((_, i) => i * intervalMs);

  fs.writeFileSync(
    path.join(outDir, 'stationary_30min.gpx'),
    gpx({
      name: 'Stationary 30 minutes',
      description:
        'A device sitting still for 30 minutes at 3 s intervals, Gaussian jitter sigma 1.5 m, ' +
        'reported accuracy 4.0 m. Raw jitter sums to a large distance; the filter chain must not.',
      points: noisy,
      times,
      accuracy: 4.0,
    }),
  );
  report.stationary = {
    points: noisy.length,
    rawJitterMetres: pathLength(noisy),
    durationMin: (times[times.length - 1] / 60000).toFixed(1),
  };
}

// ------------------------------------------------------------ walk with 5 min gap
{
  const stepMetres = WALK_SPEED_MPS * 1.0;
  const before = walkTruth(119, stepMetres, 42, 0.02);
  // The walk continues from where a 5-minute absence would plausibly leave it: 5 minutes of
  // walking further along, which is ~420 m away. That distance must NOT be booked.
  const resumeFrom = destination(before[before.length - 1][0], before[before.length - 1][1], 420, 55);
  const afterTruth = [resumeFrom];
  let bearing = 55;
  for (let i = 1; i <= 119; i++) {
    afterTruth.push(destination(afterTruth[i - 1][0], afterTruth[i - 1][1], stepMetres, bearing));
    bearing += 0.02;
  }

  const noisyBefore = jitter(before, 4242, 1.5);
  const noisyAfter = jitter(afterTruth, 9393, 1.5);
  const points = [...noisyBefore, ...noisyAfter];

  const GAP_MS = 5 * 60 * 1000;
  // The gap is measured between consecutive fix timestamps, so the first point after the
  // silence sits exactly GAP_MS after the last point before it — no extra sampling interval
  // on top. "A five minute gap" should mean t[120] - t[119] == 300_000 exactly, because that
  // difference is the only thing the filter chain ever sees.
  const times = points.map((_, i) =>
    i < noisyBefore.length
      ? i * 1000
      : (noisyBefore.length - 1) * 1000 + GAP_MS + (i - noisyBefore.length) * 1000,
  );

  fs.writeFileSync(
    path.join(outDir, 'walk_with_5min_gap.gpx'),
    gpx({
      name: 'Walk with a 5 minute gap',
      description:
        `Two ${noisyBefore.length}-point walks at 1 Hz separated by a 5 minute gap in time, ` +
        'inside a SINGLE trkseg. The break must be inferred from the timestamps, not from the ' +
        `file structure. The device is ${haversine(before[before.length - 1], resumeFrom).toFixed(0)} m ` +
        'away when it resumes, and that jump must not be counted as distance walked.',
      points,
      times,
      accuracy: 4.0,
    }),
  );
  report.gap = {
    pointsBefore: noisyBefore.length,
    pointsAfter: noisyAfter.length,
    truthBeforeMetres: pathLength(before),
    truthAfterMetres: pathLength(afterTruth),
    jumpMetres: haversine(before[before.length - 1], resumeFrom),
    gapSeconds: GAP_MS / 1000,
  };
}

console.log(JSON.stringify(report, null, 2));
