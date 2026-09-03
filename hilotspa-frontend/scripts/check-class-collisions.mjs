/**
 * Do any of our component class names collide with a Tailwind utility?
 *
 * WHY THIS EXISTS. Three separate bugs in one project, all the same shape:
 *   B96  — .grid  collapsed the staff month calendar into a single column
 *   B108 — .me    gave every chat bubble 96px of padding and centred it
 *   B111 — .fixed made "See times" position:fixed and float over the composer
 *
 * And the Tailwind 4 detail that makes it worse than a normal cascade clash:
 * the generator SCANS TEMPLATES. Writing class="btn fixed" does not merely risk
 * matching a rule that already exists — it makes Tailwind EMIT `position:fixed`
 * for you. You summon the collision by naming it.
 *
 *   node scripts/check-class-collisions.mjs
 *
 * Exit 1 on a hit, so it can go in CI later.
 */
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, extname } from 'node:path';

/** Tailwind utilities that are a single bare word — the ones a person would
 *  plausibly also choose as a component class name. Hyphenated utilities
 *  (`text-lg`, `w-full`) are not a risk: nobody names a component `text-lg`. */
const TAILWIND_BARE = new Set([
  'static','fixed','absolute','relative','sticky',
  'visible','invisible','collapse','isolate',
  'block','inline','flex','grid','contents','hidden','table',
  'grow','shrink','truncate','container',
  'italic','underline','overline','uppercase','lowercase','capitalize',
  'antialiased','ordinal','border','divide','ring','shadow','outline','blur',
  'grayscale','invert','sepia','filter','transition','transform','resize',
  'group','peer','prose','flow-root','list-item',
]);

const files = [];
(function walk(dir) {
  for (const name of readdirSync(dir)) {
    const full = join(dir, name);
    if (name === 'node_modules' || name.startsWith('.')) continue;
    if (statSync(full).isDirectory()) walk(full);
    else if (['.html', '.scss'].includes(extname(name))) files.push(full);
  }
})('src');

const hits = [];
for (const f of files) {
  const text = readFileSync(f, 'utf8');

  if (f.endsWith('.html')) {
    for (const m of text.matchAll(/class="([^"]*)"/g)) {
      for (const cls of m[1].split(/\s+/)) {
        if (TAILWIND_BARE.has(cls)) hits.push({ f, cls, how: 'used in a template' });
      }
    }
  } else if (!f.endsWith('styles.scss')) {
    // a bare `.x {` rule of our own, named after a utility
    for (const m of text.matchAll(/^\s*\.([a-z][\w-]*)[\s,{:]/gm)) {
      if (TAILWIND_BARE.has(m[1])) hits.push({ f, cls: m[1], how: 'declared in SCSS' });
    }
  }
}

const seen = new Set();
const unique = hits.filter(h => {
  const k = h.f + h.cls; if (seen.has(k)) return false; seen.add(k); return true;
});

if (!unique.length) {
  console.log('OK — no component class collides with a Tailwind utility.');
  process.exit(0);
}
console.error(`FOUND ${unique.length} collision(s):\n`);
for (const h of unique) console.error(`  .${h.cls.padEnd(14)} ${h.how.padEnd(20)} ${h.f}`);
console.error('\nRename them. In Tailwind 4 the generator scans templates, so a');
console.error('utility-shaped class name does not just risk a clash — it creates one.');
process.exit(1);
