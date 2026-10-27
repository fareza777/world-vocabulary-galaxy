import { pipeline, env } from '@huggingface/transformers';
env.cacheDir = './.cache';
const t0 = Date.now();
const translator = await pipeline('translation', 'Xenova/opus-mt-zh-en');
console.log('loaded in', (Date.now()-t0)/1000, 's');
const texts = [
  'library是借阅书籍的图书馆，bookshop是售书的书店。',
  '动词读/juːz/，名词读/juːs/。',
  '常形容前景、现实或表情，强调沉重而非普通的不悦。'
];
const out = await translator(texts, { max_new_tokens: 96 });
console.log(JSON.stringify(out, null, 2));
