import fs from 'node:fs';
import path from 'node:path';
import { spawn } from 'node:child_process';

const REPO = path.resolve('../repo/public/atlas/audio');
const OUT = path.resolve('../content/media/audio');
const KINDS = ['uk', 'us', 'ex', 'ex2'];
const SLUG = (id) => id.replace(/[^a-z0-9]+/gi, '-').replace(/^-|-$/g, '').toLowerCase();

const data = JSON.parse(fs.readFileSync('../repo/data/word-atlas.json', 'utf8'));
const jobs = [];
for (const e of data) {
  const s = SLUG(e.id);
  for (const k of KINDS) {
    const src = path.join(REPO, k, `${s}.mp3`);
    if (!fs.existsSync(src)) continue;
    const dst = path.join(OUT, k, `${s}.m4a`);
    if (fs.existsSync(dst) && fs.statSync(dst).size > 200) continue;
    jobs.push({ src, dst });
  }
}
console.log(`audio jobs: ${jobs.length}`);

let done = 0, failed = 0, next = 0;
const CONCURRENCY = Number(process.env.WORKERS || 8);
const t0 = Date.now();
function runOne(job) {
  return new Promise((resolve) => {
    fs.mkdirSync(path.dirname(job.dst), { recursive: true });
    const p = spawn('ffmpeg', ['-y', '-hide_banner', '-loglevel', 'error', '-i', job.src,
      '-c:a', 'aac', '-b:a', '32k', '-ac', '1', '-ar', '24000', '-movflags', '+faststart', job.dst], { stdio: 'ignore' });
    p.on('close', (code) => {
      if (code !== 0 || !fs.existsSync(job.dst) || fs.statSync(job.dst).size < 200) {
        failed++; if (fs.existsSync(job.dst)) fs.unlinkSync(job.dst);
      }
      done++;
      if (done % 500 === 0) {
        const e = (Date.now() - t0) / 1000;
        console.log(`${done}/${jobs.length}  failed=${failed}  ${(done / e).toFixed(1)}/s  eta=${Math.round((jobs.length - done) / (done / e))}s`);
      }
      resolve();
    });
    p.on('error', () => { failed++; done++; resolve(); });
  });
}
async function worker() {
  while (next < jobs.length) { const j = jobs[next++]; await runOne(j); }
}
await Promise.all(Array.from({ length: CONCURRENCY }, worker));
console.log(`AUDIO DONE: ${done} encoded, ${failed} failed, ${Math.round((Date.now() - t0) / 1000)}s`);
