// Builds the sprite atlas from Maki icons (CC0-1.0):
//
//   node sprite/build-sprite.mjs <out-dir>
//
// Writes sprite.png, sprite.json, sprite@2x.png and sprite@2x.json, in the format MapLibre
// reads ({name: {x, y, width, height, pixelRatio}}). Only the icons in
// tools/style/sprite-icons.txt are packed, so the atlas stays a few KB.
//
// This replaces spritezero, which depends on mapnik and no longer builds on current Node.

import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import sharp from 'sharp';

const toolsDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const makiIcons = path.join(path.dirname(createRequire(import.meta.url).resolve('@mapbox/maki/package.json')), 'icons');
const [outDir] = process.argv.slice(2);
if (!outDir) {
  console.error('usage: node sprite/build-sprite.mjs <out-dir>');
  process.exit(2);
}

const names = fs.readFileSync(path.join(toolsDir, 'style/sprite-icons.txt'), 'utf8')
  .split('\n').map((l) => l.trim()).filter((l) => l && !l.startsWith('#'));

// Maki draws in black with no fill set. A dark grey reads as well in daylight and matches
// the label colour in the style.
const ICON_COLOUR = '#3b3f45';
const PADDING = 2;
const COLUMNS = 8;

fs.mkdirSync(outDir, { recursive: true });

for (const ratio of [1, 2]) {
  const icons = [];
  for (const name of names) {
    const file = path.join(makiIcons, `${name}.svg`);
    if (!fs.existsSync(file)) throw new Error(`Maki has no icon "${name}" (${file})`);
    const svg = fs.readFileSync(file, 'utf8').replace('<svg ', `<svg fill="${ICON_COLOUR}" `);
    const png = await sharp(Buffer.from(svg), { density: 72 * ratio }).png().toBuffer();
    const { width, height } = await sharp(png).metadata();
    icons.push({ name, png, width, height });
  }

  const cell = Math.max(...icons.map((i) => Math.max(i.width, i.height))) + PADDING * 2;
  const columns = Math.min(COLUMNS, icons.length);
  const rows = Math.ceil(icons.length / columns);
  const index = {};
  const composites = icons.map((icon, i) => {
    const x = (i % columns) * cell + PADDING;
    const y = Math.floor(i / columns) * cell + PADDING;
    index[icon.name] = { x, y, width: icon.width, height: icon.height, pixelRatio: ratio };
    return { input: icon.png, left: x, top: y };
  });

  const suffix = ratio === 1 ? '' : `@${ratio}x`;
  await sharp({
    create: { width: columns * cell, height: rows * cell, channels: 4, background: { r: 0, g: 0, b: 0, alpha: 0 } },
  }).composite(composites).png().toFile(path.join(outDir, `sprite${suffix}.png`));
  fs.writeFileSync(path.join(outDir, `sprite${suffix}.json`), JSON.stringify(index, null, 2) + '\n');
  console.log(`  sprite${suffix}.png  (${icons.length} icons, ${columns * cell}x${rows * cell})`);
}
