#!/usr/bin/env node
/**
 * Normalise a GraphQL introspection response into a stable, diffable listing.
 *
 * Reads the introspection JSON on stdin and writes one sorted line per type, field, input
 * field and enum value. Sorting keeps the output stable against field-ordering churn, so a
 * diff shows only real contract changes. Used by schema-check.sh.
 *
 * Node rather than Python so the repo needs no interpreter beyond the one the frontend
 * already requires. No dependencies — only stdin, JSON and console.
 */

/** Render a possibly-wrapped type reference as SDL-ish text (e.g. [Post!]!). */
function render(t) {
  if (t === null || t === undefined) return '?';
  if (t.kind === 'NON_NULL') return render(t.ofType) + '!';
  if (t.kind === 'LIST') return '[' + render(t.ofType) + ']';
  return t.name || '?';
}

function normalise(doc) {
  if (doc.errors) {
    throw new Error('introspection failed: ' + JSON.stringify(doc.errors).slice(0, 400));
  }

  const lines = [];
  for (const t of doc.data.__schema.types) {
    const name = t.name;
    if (name.startsWith('__')) continue; // skip introspection meta-types
    lines.push(`${t.kind} ${name}`);
    for (const f of t.fields || []) lines.push(`  ${name}.${f.name}: ${render(f.type)}`);
    for (const f of t.inputFields || []) lines.push(`  ${name}.${f.name} (input)`);
    for (const v of t.enumValues || []) lines.push(`  ${name}.${v.name} (enum)`);
  }

  // Default sort compares UTF-16 code units, which matches Python's code-point ordering for
  // the ASCII-only GraphQL names and punctuation here. Deliberately not localeCompare, whose
  // ordering depends on the machine's locale.
  return lines.sort().join('\n');
}

function main() {
  let raw = '';
  process.stdin.setEncoding('utf8');
  process.stdin.on('data', (chunk) => { raw += chunk; });
  process.stdin.on('end', () => {
    try {
      console.log(normalise(JSON.parse(raw)));
    } catch (err) {
      console.error(err.message);
      process.exit(1);
    }
  });
}

main();
