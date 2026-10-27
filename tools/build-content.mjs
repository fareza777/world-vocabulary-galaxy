import fs from 'node:fs';
import path from 'node:path';
import { DatabaseSync } from 'node:sqlite';

const ROOT = path.resolve('..');
const data = JSON.parse(fs.readFileSync(path.join(ROOT, 'repo/data/word-atlas.json'), 'utf8'));
const index = JSON.parse(fs.readFileSync(path.join(ROOT, 'repo/public/atlas/index.json'), 'utf8'));
const TOPICS = index.topics.map(([key]) => key);
const LEVELS = ['A1', 'A2', 'B1', 'B2', 'C1', 'C2'];
const SLUG = (id) => id.replace(/[^a-z0-9]+/gi, '-').replace(/^-|-$/g, '').toLowerCase();
const topicIdx = new Map(TOPICS.map((t, i) => [t, i]));

const COLORS = ["#f2c46d","#ef8f6b","#8fd0c8","#9fb8ff","#9be38f","#ff9fa8","#f7d9a8","#c6a7ff","#ffd36b","#7fc4ff","#ffb38a","#86e0b5","#d8c3a5","#b5e48c","#ffe6b0","#ff8fc3","#a8d8ff","#ffcf7a","#e0b0ff"];

function hashSeed(s) {
  let h = 2166136261;
  for (let i = 0; i < s.length; i++) h = Math.imul(h ^ s.charCodeAt(i), 16777619);
  return h >>> 0;
}
function rand(seed) {
  let s = seed >>> 0 || 1;
  return () => {
    s = (s + 0x6d2b79f5) >>> 0;
    let t = s;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
const counts = new Array(TOPICS.length).fill(0);
for (const e of data) counts[topicIdx.get(e.topic)]++;
const avg = data.length / TOPICS.length;
const arms = TOPICS.length;
const stars = data.map((e) => {
  const r = rand(hashSeed(e.id));
  const gauss = () => (r() + r() + r() - 1.5) / 1.5;
  const cefr = LEVELS.indexOf(e.cefr);
  const radius = 0.1 + (cefr / 5) * 0.72 + r() * 0.16;
  const ti = topicIdx.get(e.topic);
  const arm = (ti / arms) * Math.PI * 2;
  const spread = Math.sqrt(Math.max(0.6, counts[ti] / avg));
  const angle = arm + radius * 3.4 + gauss() * (0.05 + 0.03 / radius) * spread;
  const thick = 0.05 * (1 - radius * 0.6) * spread;
  const x = Math.cos(angle) * radius;
  const y = gauss() * thick;
  const z = Math.sin(angle) * radius;
  const seed = r();
  return { x, y, z, seed };
});

const dbPath = path.join(ROOT, 'content/atlas.db');
if (fs.existsSync(dbPath)) fs.unlinkSync(dbPath);
const db = new DatabaseSync(dbPath);
db.exec(`
  PRAGMA page_size = 4096;
  PRAGMA journal_mode = DELETE;
  PRAGMA synchronous = OFF;
  CREATE TABLE words (
    id TEXT PRIMARY KEY,
    word TEXT NOT NULL,
    ipa_uk TEXT NOT NULL,
    ipa_us TEXT NOT NULL,
    cefr INTEGER NOT NULL,
    topic INTEGER NOT NULL,
    image TEXT NOT NULL,
    audio_uk TEXT NOT NULL,
    audio_us TEXT NOT NULL,
    audio_ex1 TEXT NOT NULL,
    audio_ex2 TEXT,
    pos TEXT NOT NULL,
    search_text TEXT NOT NULL,
    gx REAL NOT NULL,
    gy REAL NOT NULL,
    gz REAL NOT NULL,
    star_seed REAL NOT NULL
  );
  CREATE TABLE senses (word_id TEXT NOT NULL, idx INTEGER NOT NULL, pos TEXT, definition TEXT NOT NULL, PRIMARY KEY(word_id, idx));
  CREATE TABLE examples (word_id TEXT NOT NULL, idx INTEGER NOT NULL, text TEXT NOT NULL, PRIMARY KEY(word_id, idx));
  CREATE TABLE collocations (word_id TEXT NOT NULL, idx INTEGER NOT NULL, text TEXT NOT NULL, PRIMARY KEY(word_id, idx));
  CREATE TABLE family (word_id TEXT NOT NULL, idx INTEGER NOT NULL, text TEXT NOT NULL, PRIMARY KEY(word_id, idx));
  CREATE TABLE synonyms (word_id TEXT NOT NULL, idx INTEGER NOT NULL, text TEXT NOT NULL, PRIMARY KEY(word_id, idx));
  CREATE TABLE antonyms (word_id TEXT NOT NULL, idx INTEGER NOT NULL, text TEXT NOT NULL, PRIMARY KEY(word_id, idx));
  CREATE TABLE cards (
    word_id TEXT PRIMARY KEY, due INTEGER NOT NULL, stability REAL NOT NULL, difficulty REAL NOT NULL,
    elapsed_days INTEGER NOT NULL DEFAULT 0, scheduled_days INTEGER NOT NULL DEFAULT 0, learning_steps INTEGER NOT NULL DEFAULT 0,
    reps INTEGER NOT NULL DEFAULT 0, lapses INTEGER NOT NULL DEFAULT 0, state INTEGER NOT NULL DEFAULT 0,
    last_review INTEGER, first_seen INTEGER NOT NULL, last_grade INTEGER NOT NULL DEFAULT 0
  );
  CREATE INDEX cards_due ON cards(due);
  CREATE TABLE bookmarks (word_id TEXT PRIMARY KEY, created_at INTEGER NOT NULL);
  CREATE TABLE day_stats (
    day TEXT PRIMARY KEY, reviews INTEGER NOT NULL DEFAULT 0, new_words INTEGER NOT NULL DEFAULT 0,
    correct INTEGER NOT NULL DEFAULT 0, total INTEGER NOT NULL DEFAULT 0, xp INTEGER NOT NULL DEFAULT 0,
    seconds INTEGER NOT NULL DEFAULT 0, sessions INTEGER NOT NULL DEFAULT 0,
    challenge_done INTEGER NOT NULL DEFAULT 0, challenge_score INTEGER NOT NULL DEFAULT 0,
    typing_chars INTEGER NOT NULL DEFAULT 0, typing_correct INTEGER NOT NULL DEFAULT 0
  );
  CREATE TABLE achievements (id TEXT PRIMARY KEY, unlocked_at INTEGER NOT NULL);
  CREATE TABLE sessions (
    id INTEGER PRIMARY KEY AUTOINCREMENT, started_at INTEGER NOT NULL, ended_at INTEGER NOT NULL,
    mode TEXT NOT NULL, reviewed INTEGER NOT NULL, correct INTEGER NOT NULL, xp INTEGER NOT NULL, seconds INTEGER NOT NULL
  );
  CREATE TABLE search_history (query TEXT PRIMARY KEY, used_at INTEGER NOT NULL);
  CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL);
  CREATE INDEX words_word ON words(word COLLATE NOCASE);
  CREATE INDEX senses_word ON senses(word_id);
  CREATE INDEX examples_word ON examples(word_id);
`);

const insWord = db.prepare(`INSERT INTO words VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)`);
const insSense = db.prepare(`INSERT INTO senses VALUES (?,?,?,?)`);
const insEx = db.prepare(`INSERT INTO examples VALUES (?,?,?)`);
const insCol = db.prepare(`INSERT INTO collocations VALUES (?,?,?)`);
const insFam = db.prepare(`INSERT INTO family VALUES (?,?,?)`);
const insSyn = db.prepare(`INSERT INTO synonyms VALUES (?,?,?)`);
const insAnt = db.prepare(`INSERT INTO antonyms VALUES (?,?,?)`);

let senseCount = 0, exCount = 0, colCount = 0;
db.exec('BEGIN');
data.forEach((e, i) => {
  const s = SLUG(e.id);
  const img = `atlas/img/${s}.webp`;
  const auk = `atlas/audio/uk/${s}.m4a`;
  const aus = `atlas/audio/us/${s}.m4a`;
  const aex = `atlas/audio/ex/${s}.m4a`;
  const ex2Path = path.join(ROOT, 'repo/public/atlas/audio/ex2', `${s}.mp3`);
  const aex2 = fs.existsSync(ex2Path) ? `atlas/audio/ex2/${s}.m4a` : null;
  const topic = topicIdx.get(e.topic);
  if (topic === undefined) throw new Error('unknown topic ' + e.topic);
  const cefr = LEVELS.indexOf(e.cefr);
  if (cefr < 0) throw new Error('unknown cefr ' + e.cefr);
  const pos = [...new Set(e.senses.map((x) => x.pos).filter(Boolean))].join(' · ');
  const colEn = e.col.map((c) => c.split(/[｜|]/)[0].trim()).filter(Boolean);
  const search = [e.w, ...e.senses.map((x) => x.en), ...colEn, ...e.fam, ...e.syn, ...e.ant].join(' ').toLowerCase();
  const star = stars[i];
  insWord.run(e.id, e.w, e.uk, e.us, cefr, topic, img, auk, aus, aex, aex2, pos, search, star.x, star.y, star.z, star.seed);
  e.senses.forEach((x, j) => { insSense.run(e.id, j, x.pos || '', x.en || ''); senseCount++; });
  e.ex.forEach((x, j) => { insEx.run(e.id, j, x.en); exCount++; });
  colEn.forEach((c, j) => { insCol.run(e.id, j, c); colCount++; });
  e.fam.forEach((x, j) => insFam.run(e.id, j, x));
  e.syn.forEach((x, j) => insSyn.run(e.id, j, x));
  e.ant.forEach((x, j) => insAnt.run(e.id, j, x));
});
db.exec('COMMIT');
const meta = db.prepare('INSERT INTO meta VALUES (?,?)');
meta.run('db_version', '1');
meta.run('content_version', '2026.10');
meta.run('attribution', 'Vocabulary, illustrations and recordings: Word Atlas contributors - CC BY 4.0. English-first edition by Verbilume.');
meta.run('word_count', String(data.length));
meta.run('sense_count', String(senseCount));
meta.run('example_count', String(exCount));
meta.run('collocation_count', String(colCount));
meta.run('audio_count', String(data.length * 3 + fs.readdirSync(path.join(ROOT, 'repo/public/atlas/audio/ex2')).length));
db.exec('PRAGMA user_version = 1; VACUUM;');
db.close();

const perLevel = Object.fromEntries(LEVELS.map((l) => [l, 0]));
const perTopic = Object.fromEntries(TOPICS.map((t) => [t, 0]));
for (const e of data) { perLevel[e.cefr]++; perTopic[e.topic]++; }
const metaOut = {
  name: 'Verbilume',
  contentVersion: '2026.10',
  words: data.length,
  senses: senseCount,
  examples: exCount,
  collocations: colCount,
  audio: data.length * 3 + fs.readdirSync(path.join(ROOT, 'repo/public/atlas/audio/ex2')).length,
  levels: perLevel,
  topics: TOPICS.map((key, i) => ({ key, label: key.replace(/-/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase()), count: perTopic[key], color: COLORS[i] })),
  levelsList: LEVELS,
};
fs.writeFileSync(path.join(ROOT, 'content/meta.json'), JSON.stringify(metaOut, null, 2));
console.log(JSON.stringify({ db: dbPath, size: fs.statSync(dbPath).size, words: data.length, senses: senseCount, examples: exCount, collocations: colCount, levels: metaOut.levels }, null, 2));
