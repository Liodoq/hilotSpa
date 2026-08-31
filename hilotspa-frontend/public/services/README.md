# Treatment photographs

Drop the spa's photos in this folder using **exactly** these names. They appear on the
public landing page, the services list, and each treatment's own page.

| File | Treatment | Where it shows |
|---|---|---|
| `signature-massage.jpg` | Signature Massage (60 and 90 both use this one) | landing, services, detail |
| `bone-setting.jpg` | Bone Setting, 60 min | landing, services, detail |
| `therapeutic-massage.jpg` | Therapeutic Massage, 60 min | landing, services, detail |
| `ventosa.jpg` | Ventosa, 30 min | services, detail |
| `hotstone.jpg` | Hotstone, 30 min | services, detail |
| `suob.jpg` | Suob, 30 min | services, detail |
| `head-spa.jpg` | Head Spa, 60 min | services, detail |
| `basic-massage.jpg` | Basic Massage (60 and 90 both use this one) | services, detail |

## Rules

- **Landscape**, around 1200 × 800. The tiles crop to fill, so anything important should sit
  near the middle.
- **Under about 300 KB each.** These load on the spa's connection, on a phone, in the branch.
  Resize before committing; a 4 MB photo from a phone camera will make the page crawl.
- `.jpg` for photographs. `.png` only for something with flat colour or transparency.
- A missing file is **not** a bug. The tile shows a plain tinted block, which is honest about a
  photograph the spa has not supplied. Nothing looks broken.

## Adding a treatment that is not listed above

The filename is stored on the treatment itself, not derived from its name. Set it in
**Admin → Configuration → Service menu → (treatment) → Photo file**. Type the filename, and the
drawer previews it immediately — if the preview stays a plain green block, the file is not in
this folder under that exact name.

## Why a filename and not the service id

The screens used to build the path from the treatment's database id. Ids are regenerated every
time the database is rebuilt, so real photographs would have needed renaming after every reset,
and the files would have been called things like `a3f4b2c1-9d8e-4f10-....jpg` that nobody could
match to a treatment. Names survive a rebuild and a human can read them.
