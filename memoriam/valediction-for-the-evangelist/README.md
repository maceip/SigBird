# Valediction for the Evangelist

Memoriam artifact for Vint Cerf — poem + portrait encoded for a DTN-inspired
Bundle Protocol Version 7 payload block using CBOR.

## Printable (use this in the post)

- **`payload-block-thumb.post.txt`** — paste-ready Payload Block in the memoriam format
  (poem excerpt + unbroken `h'…'` JPEG byte string).
- **`payload-block-thumb.diag.printable.txt`** — full poem + diagnostic notes + wrapped hex.
- **`payload-block-thumb.diag.cbor.txt`** — machine-oriented diagnostic form, unbroken hex.

## DTN / CBOR format

Per the memoriam format, the Payload Block embeds the poem as a text string and
the image immediately after as a CBOR byte string:

```
/ Payload Block (Type 1) /
[
  1,                                  / Block Type Code: Payload /
  1,                                  / Block Number /
  0,                                  / Block Processing Control Flags /
  36,                                 / CRC Type: CRC-32C /
  "The packet you bade sleep...",     / Poem Text (Text String) /
  h'FFD8FFE0...'                      / Image (Byte String, Major Type 2) /
]
```

Binary CBOR of that array: `payload-block-thumb.cbor` (and full-res twin).

## All retained versions

| File | Description |
|------|-------------|
| `poem.txt` | Plain-text poem |
| `cerf-bust-blue.jpg` | Original portrait |
| `cerf-bust-blue-thumb.jpg` | Thumbnail (256×213) |
| `cerf-bust-blue.hex.txt` / `*-thumb.hex.txt` | Raw JPEG hex |
| `valediction.vints` | Earlier map form (metadata + thumb payload) |
| `valediction-fullres.vints` | Map form with full-res image |
| `valediction*.b64*` / `*.hex.txt` | Encodings of the `.vints` artifacts |
| `valediction-memoriam-vints-armor.txt` | PEM-style BEGIN/END VINTS armor |
| `payload-block-*.diag.*` | BPv7-inspired payload block diagnostic notation |
| `payload-block-*.cbor` | Binary CBOR of the payload block array |

Decode a `.cbor` file with any CBOR tool; the 6th array element is the JPEG bytes.

## JPEG with embedded VINTS armor

The full armored memoriam text (`valediction-memoriam-vints-armor.txt`) is also
embedded inside valid JPEGs via a JPEG **COM** comment segment (`0xFFFE`) right
after SOI, prefixed with the magic `VINTS-ARMOR\0`:

| File | Description |
|------|-------------|
| `cerf-bust-blue-thumb-with-vints-armor.jpg` | Thumbnail that still opens as a normal JPEG |
| `cerf-bust-blue-with-vints-armor.jpg` | Full-res twin with the same armor embedded |

Extract the armored ASCII with stock macOS/Linux tools:

```bash
strings cerf-bust-blue-thumb-with-vints-armor.jpg | sed -n '/-----BEGIN VINTS-----/,/-----END VINTS-----/p'
```

The images remain valid; viewers ignore the COM payload.
