/**
 * Read a simple .xlsx in the browser, without a dependency.
 *
 * WHY THIS EXISTS: people fill these sheets in Excel, and "Save As → CSV UTF-8"
 * is a step that gets skipped, gets done wrong (Excel has several CSV options,
 * one of which mangles non-ASCII), or produces a file with the wrong separator
 * on a machine set to a European locale. The spreadsheet is the artefact the
 * spa actually works in, so the system should read it.
 *
 * WHY NOT A LIBRARY: the obvious one is SheetJS, and its npm package is the
 * outdated fork — the maintained build moved to the author's own CDN. Pulling
 * a stale copy of a parser into a system that will hold patient records, to
 * save a hundred lines, is not a trade worth making for this project.
 *
 * WHAT IT DELIBERATELY DOES NOT DO: formulas (it reads the cached value Excel
 * stored, which is what a person sees), styles, dates as dates, multiple sheets
 * (first sheet only), or anything about the file that is not a cell's text.
 * That is the whole job here — five columns of words.
 *
 * WHEN IT CANNOT: it says so, and the caller falls back to asking for a CSV.
 * An unreadable spreadsheet must never look like an empty one.
 */

/** An .xlsx is a ZIP. This is the least of one that gets a named entry out. */
async function unzip(buf: ArrayBuffer): Promise<Map<string, Uint8Array>> {
  const view = new DataView(buf);
  const bytes = new Uint8Array(buf);
  const out = new Map<string, Uint8Array>();

  // The End of Central Directory record, found by scanning back for its
  // signature. It is at the very end unless the file carries a comment, which
  // Excel does not write — but scanning costs nothing and assuming costs a bug.
  let eocd = -1;
  for (let i = bytes.length - 22; i >= 0 && i > bytes.length - 66_000; i--) {
    if (view.getUint32(i, true) === 0x06054b50) { eocd = i; break; }
  }
  if (eocd < 0) { throw new Error('Not a zip file'); }

  const count = view.getUint16(eocd + 10, true);
  let p = view.getUint32(eocd + 16, true);

  const dec = new TextDecoder();
  for (let n = 0; n < count; n++) {
    if (view.getUint32(p, true) !== 0x02014b50) { break; }
    const method = view.getUint16(p + 10, true);
    const compressedSize = view.getUint32(p + 20, true);
    const nameLen = view.getUint16(p + 28, true);
    const extraLen = view.getUint16(p + 30, true);
    const commentLen = view.getUint16(p + 32, true);
    const localOffset = view.getUint32(p + 42, true);
    const name = dec.decode(bytes.subarray(p + 46, p + 46 + nameLen));

    // The local header repeats the name and extra fields, and its extra length
    // is NOT always the same as the central one. Reading the central value here
    // is the classic way to land four bytes into the data.
    const lNameLen = view.getUint16(localOffset + 26, true);
    const lExtraLen = view.getUint16(localOffset + 28, true);
    const start = localOffset + 30 + lNameLen + lExtraLen;
    const raw = bytes.subarray(start, start + compressedSize);

    if (method === 0) {
      out.set(name, raw);
    } else if (method === 8) {
      out.set(name, await inflateRaw(raw));
    }
    // Anything else is a compression Excel does not use; skipping it means the
    // entry is simply absent, which the caller reports honestly.

    p += 46 + nameLen + extraLen + commentLen;
  }
  return out;
}

/** Built into the browser since Chrome 80. No polyfill, no dependency. */
async function inflateRaw(data: Uint8Array): Promise<Uint8Array> {
  const ds = new DecompressionStream('deflate-raw');
  const stream = new Blob([data as unknown as BlobPart]).stream().pipeThrough(ds);
  return new Uint8Array(await new Response(stream).arrayBuffer());
}

/** "BC" -> 54. Needed because Excel omits empty cells rather than writing them. */
function columnIndex(ref: string): number {
  let n = 0;
  for (const ch of ref) {
    const c = ch.charCodeAt(0);
    if (c < 65 || c > 90) { break; }
    n = n * 26 + (c - 64);
  }
  return n - 1;
}

function decodeEntities(s: string): string {
  return s.replace(/&lt;/g, '<').replace(/&gt;/g, '>')
          .replace(/&quot;/g, '"').replace(/&apos;/g, "'")
          .replace(/&#(\d+);/g, (_, d) => String.fromCodePoint(Number(d)))
          .replace(/&amp;/g, '&');   // last, or &amp;lt; double-decodes
}

/** Every <t> inside one element, joined — a cell can be several runs. */
function textOf(xml: string): string {
  const parts = xml.match(/<t[^>]*>([\s\S]*?)<\/t>/g) ?? [];
  return decodeEntities(parts.map(p => p.replace(/<[^>]+>/g, '')).join(''));
}

/**
 * The first worksheet of an .xlsx, as rows of strings.
 *
 * Throws with a readable message when the file is not one, so the caller can
 * say "save it as CSV instead" rather than showing an empty table.
 */
export async function readXlsx(file: File): Promise<string[][]> {
  let files: Map<string, Uint8Array>;
  try {
    files = await unzip(await file.arrayBuffer());
  } catch {
    throw new Error('That does not look like an Excel file.');
  }

  const dec = new TextDecoder();
  const sheetName = [...files.keys()]
    .filter(n => /^xl\/worksheets\/sheet\d+\.xml$/.test(n))
    .sort()[0];
  if (!sheetName) {
    throw new Error('No worksheet found inside that file.');
  }

  const sharedXml = files.get('xl/sharedStrings.xml');
  const shared: string[] = [];
  if (sharedXml) {
    for (const si of dec.decode(sharedXml).match(/<si>[\s\S]*?<\/si>/g) ?? []) {
      shared.push(textOf(si));
    }
  }

  const sheet = dec.decode(files.get(sheetName)!);
  const rows: string[][] = [];

  for (const rowXml of sheet.match(/<row[^>]*>[\s\S]*?<\/row>/g) ?? []) {
    const cells: string[] = [];
    for (const cellXml of rowXml.match(/<c[^>]*\/>|<c[^>]*>[\s\S]*?<\/c>/g) ?? []) {
      const ref = /\sr="([A-Z]+)\d+"/.exec(cellXml)?.[1] ?? '';
      const type = /\st="([^"]+)"/.exec(cellXml)?.[1] ?? '';
      const at = ref ? columnIndex(ref) : cells.length;

      let value = '';
      if (type === 's') {
        const i = Number(/<v>([\s\S]*?)<\/v>/.exec(cellXml)?.[1] ?? '-1');
        value = shared[i] ?? '';
      } else if (type === 'inlineStr') {
        value = textOf(cellXml);
      } else {
        // Numbers, and formulas via the value Excel cached — which is what the
        // person looking at the sheet actually saw.
        value = decodeEntities(/<v>([\s\S]*?)<\/v>/.exec(cellXml)?.[1] ?? '');
      }

      while (cells.length < at) { cells.push(''); }
      cells[at] = value;
    }
    rows.push(cells);
  }
  return rows;
}

/** Rows back into the CSV the import endpoint already speaks. */
export function rowsToCsv(rows: string[][]): string {
  const cell = (v: string) => {
    const s = String(v ?? '');
    return /[",\r\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
  };
  return rows
    .filter(r => r.some(c => (c ?? '').trim() !== ''))
    .map(r => r.map(cell).join(','))
    .join('\r\n');
}
