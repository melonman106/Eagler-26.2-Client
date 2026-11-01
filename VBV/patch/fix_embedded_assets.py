#!/usr/bin/env python3
"""Repair invalid PNG-named files inside an already-built standalone HTML.

The native build can contain AVIF/WebP bytes stored under .png names. The
TeaVM client then reaches Minecraft's PNG loader and crashes with "Bad PNG
Signature" during the initial resource reload. This script rewrites only the
embedded assets.epk, preserving the EPK metadata and every unaffected file.
It also hides the diagnostic console once the client reports __loaded=true.
"""
from pathlib import Path
import base64
import gzip
import re
import shutil
import subprocess
import sys
import tempfile
import zlib

PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
FOOTER = b":::YEE:>"
FFMPEG = shutil.which("ffmpeg")
if not FFMPEG:
    raise SystemExit("::error::ffmpeg is required to repair invalid PNG-named assets")


def normalize_png(data: bytes, name: str) -> tuple[bytes, bool]:
    if data.startswith(PNG_SIGNATURE):
        return data, False

    source_fd, source_name = tempfile.mkstemp(prefix="vbv-src-", suffix=Path(name).suffix or ".bin")
    Path(source_name).unlink(missing_ok=True)
    output_fd, output_name = tempfile.mkstemp(prefix="vbv-png-", suffix=".png")
    Path(output_name).unlink(missing_ok=True)
    source = Path(source_name)
    output = Path(output_name)
    try:
        source.write_bytes(data)
        subprocess.run(
            [FFMPEG, "-y", "-loglevel", "error", "-i", str(source), "-frames:v", "1", str(output)],
            check=True,
        )
        converted = output.read_bytes()
    except Exception as exc:
        raise RuntimeError(f"cannot convert {name}: {exc}") from exc
    finally:
        source.unlink(missing_ok=True)
        output.unlink(missing_ok=True)

    if not converted.startswith(PNG_SIGNATURE):
        raise RuntimeError(f"conversion did not produce a PNG for {name}")
    return converted, True


def rebuild_epk(epk: bytes) -> tuple[bytes, list[str]]:
    if not epk.startswith(b"EAGPKG$$") or not epk.endswith(FOOTER):
        raise RuntimeError("embedded assets are not an EAGPKG$$ v2 package")

    p = 8
    version_len = epk[p]; p += 1 + version_len
    name_len = epk[p]; p += 1 + name_len
    comment_len = int.from_bytes(epk[p:p + 2], "big")
    p += 2 + comment_len
    p += 8
    file_count = int.from_bytes(epk[p:p + 4], "big")
    p += 4
    compression = epk[p:p + 1]
    p += 1
    if compression != b"G":
        raise RuntimeError(f"unsupported embedded EPK compression: {compression!r}")

    raw = gzip.decompress(epk[p:-8])
    out = bytearray()
    q = 0
    seen = 0
    converted: list[str] = []

    while q < len(raw):
        if raw.startswith(b"END$", q):
            out += b"END$"
            q += 4
            break

        if raw.startswith(b"HEAD", q):
            start = q
            q += 4
            n = raw[q]
            q += 1 + n
            size = int.from_bytes(raw[q:q + 4], "big")
            q += 4 + size
            if raw[q:q + 1] != b">":
                raise RuntimeError("malformed HEAD entry in assets.epk")
            q += 1
            out += raw[start:q]
            continue

        if raw.startswith(b"FILE", q):
            q += 4
            n = raw[q]
            q += 1
            name_bytes = raw[q:q + n]
            q += n
            name = name_bytes.decode("utf-8", "replace")
            size = int.from_bytes(raw[q:q + 4], "big")
            q += 4
            stored_crc = int.from_bytes(raw[q:q + 4], "big")
            q += 4
            data_len = size - 5
            if data_len < 0:
                raise RuntimeError(f"malformed FILE size for {name}")
            data = raw[q:q + data_len]
            q += data_len
            if raw[q:q + 2] != b":>":
                raise RuntimeError(f"malformed FILE terminator for {name}")
            q += 2

            if zlib.crc32(data) & 0xffffffff != stored_crc:
                raise RuntimeError(f"CRC mismatch in source assets.epk for {name}")

            if name.lower().endswith(".png"):
                data, changed = normalize_png(data, name)
                if changed:
                    converted.append(name)

            new_crc = zlib.crc32(data) & 0xffffffff
            out += b"FILE" + bytes([n]) + name_bytes
            out += (len(data) + 5).to_bytes(4, "big")
            out += new_crc.to_bytes(4, "big")
            out += data + b":>"
            seen += 1
            continue

        raise RuntimeError(f"unknown EPK record at offset {q}: {raw[q:q + 16]!r}")

    if seen != file_count:
        raise RuntimeError(f"assets.epk file-count mismatch: header={file_count}, parsed={seen}")
    if not out.endswith(b"END$"):
        raise RuntimeError("assets.epk END$ marker was not found")

    rebuilt = epk[:p] + gzip.compress(bytes(out), compresslevel=9, mtime=0) + FOOTER
    return rebuilt, converted


def patch_html(source: Path, target: Path) -> None:
    html = source.read_text(encoding="utf-8")
    pattern = re.compile(
        r'(<script[^>]*id=["\']eag-inline-assets["\'][^>]*>)(.*?)(</script>)',
        re.I | re.S,
    )
    match = pattern.search(html)
    if not match:
        raise RuntimeError("eag-inline-assets script was not found")

    encoded = re.sub(r"\s+", "", match.group(2))
    epk = base64.b64decode(encoded, validate=True)
    rebuilt, converted = rebuild_epk(epk)
    replacement = match.group(1) + base64.b64encode(rebuilt).decode("ascii") + match.group(3)
    html = html[:match.start()] + replacement + html[match.end():]

    hide_script = (
        '<script id="vbv-diag-autohide">'
        '(function(){function h(){if(window.__loaded===true){var e=document.getElementById("vbv-diag-box");'
        'if(e)e.style.display="none";return true;}return false;}'
        'var i=setInterval(function(){if(h())clearInterval(i);},100);h();})();'
        '</script>'
    )
    if 'id="vbv-diag-autohide"' not in html:
        body = re.search(r"</body>", html, re.I)
        if body:
            html = html[:body.start()] + hide_script + html[body.start():]
        else:
            html += hide_script

    target.write_text(html, encoding="utf-8")
    print(f"[VBV repair] converted {len(converted)} invalid PNG-named asset(s)")
    for name in converted:
        print(f"[VBV repair] converted: {name}")
    print(f"[VBV repair] assets.epk: {len(epk):,} -> {len(rebuilt):,} bytes")


if len(sys.argv) != 3:
    raise SystemExit("usage: fix_embedded_assets.py <input.html> <output.html>")

patch_html(Path(sys.argv[1]).resolve(), Path(sys.argv[2]).resolve())
