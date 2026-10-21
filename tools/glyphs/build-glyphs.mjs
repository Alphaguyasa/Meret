// Builds the glyph PBFs that tools/style/meret-style.json asks for.
//
//   node glyphs/build-glyphs.mjs <font-dir> <out-dir>
//
// MapLibre requests glyphs per font *stack*, not per font. For
// "text-font": ["Noto Sans Regular", "Noto Sans Ethiopic Regular"] it fetches
// "Noto Sans Regular,Noto Sans Ethiopic Regular/4608-4863.pbf": the names joined by a comma
// with no space. There is no glyph server on the phone to merge fonts on request, so this
// writes one directory per single font and one per stack in the style, merged with
// fontnik.composite. The first font in a stack wins for a codepoint both fonts have.
//
// Font names map to TTF files in tools/style/fonts.json.

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { promisify } from 'node:util';
import fontnik from 'fontnik';

const range = promisify(fontnik.range);
const composite = promisify(fontnik.composite);

const toolsDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const [fontDir, outDir] = process.argv.slice(2);
if (!fontDir || !outDir) {
  console.error('usage: node glyphs/build-glyphs.mjs <font-dir> <out-dir>');
  process.exit(2);
}

const fonts = JSON.parse(fs.readFileSync(path.join(toolsDir, 'style/fonts.json'), 'utf8'));
const style = JSON.parse(fs.readFileSync(path.join(toolsDir, 'style/meret-style.json'), 'utf8'));

// Every distinct text-font stack in the style. Stacks are literal arrays in our style;
// an expression here would need a resolver, so refuse it instead of guessing.
const stacks = new Map();
for (const layer of style.layers) {
  const stack = layer.layout?.['text-font'];
  if (stack === undefined) continue;
  if (!Array.isArray(stack) || !stack.every((f) => typeof f === 'string')) {
    throw new Error(`${layer.id}: text-font must be a literal array of font names`);
  }
  stacks.set(stack.join(','), stack);
}

const missing = [...new Set([...stacks.values()].flat())].filter((f) => !(f in fonts));
if (missing.length) throw new Error(`no TTF mapped in style/fonts.json for: ${missing.join(', ')}`);

const RANGES = Array.from({ length: 256 }, (_, i) => [i * 256, i * 256 + 255]);
const fileName = ([start, end]) => `${start}-${end}.pbf`;

// Single fonts: every range, so a stack can be composed from any of them.
const single = new Map();
for (const [name, file] of Object.entries(fonts)) {
  const ttf = path.join(fontDir, file);
  if (!fs.existsSync(ttf)) throw new Error(`${ttf} not found (see tools/README.md for where to get it)`);
  const font = fs.readFileSync(ttf);
  const dir = path.join(outDir, name);
  fs.mkdirSync(dir, { recursive: true });
  const pbfs = [];
  for (const r of RANGES) {
    const pbf = await range({ font, start: r[0], end: r[1] });
    fs.writeFileSync(path.join(dir, fileName(r)), pbf);
    pbfs.push(pbf);
  }
  single.set(name, pbfs);
  console.log(`  ${name}/  (${RANGES.length} ranges)`);
}

for (const [dirName, stack] of stacks) {
  if (stack.length === 1) continue;
  const dir = path.join(outDir, dirName);
  fs.mkdirSync(dir, { recursive: true });
  for (const [i, r] of RANGES.entries()) {
    const merged = await composite(stack.map((name) => single.get(name)[i]));
    fs.writeFileSync(path.join(dir, fileName(r)), merged);
  }
  console.log(`  ${dirName}/  (stack)`);
}
