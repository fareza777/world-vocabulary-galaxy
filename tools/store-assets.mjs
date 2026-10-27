import sharp from 'sharp';
import { mkdir } from 'node:fs/promises';
import path from 'node:path';

const root = path.resolve('..');
const media = path.join(root, 'content', 'media', 'atlas');
const outDir = path.join(root, 'store');
await mkdir(outDir, { recursive: true });

const PAPER = '#F5EDDE';
const PAPER_ICON = '#F2E7D3';
const INK = '#0B1622';
const TERRACOTTA = '#C55E3F';
const GOLD = '#B5822E';

const mark = (scale, cx, cy) => `
  <g transform="translate(${cx} ${cy}) scale(${scale}) translate(-54 -54)">
    <circle cx="54" cy="54" r="29" fill="none" stroke="#1C2A33" stroke-opacity="0.26" stroke-width="1.5"/>
    <path d="M39,42 L54,71 L69,42" fill="none" stroke="${INK}" stroke-width="4.4" stroke-linecap="round" stroke-linejoin="round"/>
    <circle cx="39" cy="39" r="3" fill="${GOLD}"/>
    <circle cx="69" cy="39" r="3" fill="${GOLD}"/>
    <circle cx="54" cy="68" r="3" fill="${TERRACOTTA}"/>
    <path d="M71,31 L72.3,35.2 L76.5,36.5 L72.3,37.8 L71,42 L69.7,37.8 L65.5,36.5 L69.7,35.2 Z" fill="${GOLD}"/>
  </g>`;

const iconSvg = `<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 108 108">
  <rect width="108" height="108" fill="${PAPER_ICON}"/>
  ${mark(1, 54, 54)}
</svg>`;
await sharp(Buffer.from(iconSvg)).png({ compressionLevel: 9 }).toFile(path.join(outDir, 'icon-512.png'));

const W = 1024, H = 500;
const baseSvg = `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}">
  <defs>
    <radialGradient id="glow" cx="30%" cy="18%" r="80%">
      <stop offset="0%" stop-color="#FFF6E4"/>
      <stop offset="100%" stop-color="${PAPER}"/>
    </radialGradient>
  </defs>
  <rect width="${W}" height="${H}" fill="url(#glow)"/>
  <rect x="0" y="0" width="${W}" height="${H}" fill="none" stroke="${INK}" stroke-opacity="0.08" stroke-width="8"/>
  ${mark(0.62, 92, 108)}
  <text x="92" y="248" font-family="Georgia, 'Times New Roman', serif" font-size="76" font-weight="600" fill="${INK}" letter-spacing="-1.5">Verbilume</text>
  <text x="92" y="298" font-family="Georgia, serif" font-size="27" fill="${TERRACOTTA}">Every word, a little light.</text>
  <text x="92" y="352" font-family="Segoe UI, Arial, sans-serif" font-size="20" fill="${INK}" fill-opacity="0.72">7,298 illustrated words · spoken aloud</text>
  <text x="92" y="384" font-family="Segoe UI, Arial, sans-serif" font-size="20" fill="${INK}" fill-opacity="0.72">Spaced repetition that remembers for you</text>
  <text x="92" y="416" font-family="Segoe UI, Arial, sans-serif" font-size="20" fill="${INK}" fill-opacity="0.72">Works fully offline — no account, no ads</text>
</svg>`;

const topics = ['topic-nature', 'topic-technology', 'topic-travel', 'topic-education'];
const TILE = 208, GAP = 24, TX = 560, TY = 24;
const roundedMask = (size, radius) => Buffer.from(
  `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}"><rect width="${size}" height="${size}" rx="${radius}" ry="${radius}" fill="#fff"/></svg>`
);

const composites = [];
for (let i = 0; i < topics.length; i++) {
  const col = i % 2, row = Math.floor(i / 2);
  const left = TX + col * (TILE + GAP);
  const top = TY + row * (TILE + GAP);
  const tile = await sharp(path.join(media, 'brand', `${topics[i]}.webp`))
    .resize(TILE, TILE, { fit: 'cover' })
    .composite([{ input: roundedMask(TILE, 34), blend: 'dest-in' }])
    .png()
    .toBuffer();
  composites.push({ input: tile, left, top });
}

await sharp(Buffer.from(baseSvg))
  .composite(composites)
  .png({ compressionLevel: 9 })
  .toFile(path.join(outDir, 'feature-graphic-1024x500.png'));

const meta = async (f) => {
  const m = await sharp(path.join(outDir, f)).metadata();
  console.log(f, m.width + 'x' + m.height, m.channels + 'ch', (await sharp(path.join(outDir, f)).stats()).isOpaque ? 'opaque' : 'alpha');
};
await meta('icon-512.png');
await meta('feature-graphic-1024x500.png');