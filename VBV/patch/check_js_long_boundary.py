#!/usr/bin/env python3
"""Fail if a Java `long` / `Long` crosses the TeaVM JavaScript boundary.

Why this matters (TeaVM WebAssembly-GC): a Java `long` is a wasm i64, and a wasm i64 can only be
exchanged with JavaScript as a BigInt. A `@JSBody` that returns a normal JS Number (for example
`return 0;`) for a `long` result - or JS that receives a `long` parameter as a BigInt it did not
expect - throws "TypeError: Cannot convert 0 to a BigInt" at runtime, and nothing in javac or Gradle
warns about it. Use `double` (or `int`) at the boundary and convert in Java.

Checks, per .java file:
  * `native` methods (the @JSBody pattern): return type or any parameter is long/Long.
  * Abstract methods of interfaces/classes that extend/implement a JSObject type (JSO interfaces):
    same rule.
Strings and comments are blanked first, so braces/keywords inside @JSBody script text cannot confuse it.

Usage: check_js_long_boundary.py <dir-or-file> [...]      exit 1 if anything is found.
"""
import re
import sys
from pathlib import Path

LONG = re.compile(r"^(?:java\.lang\.)?(?:long|Long)(?:\s*\[\s*\])*$")


def blank_strings_and_comments(src: str) -> str:
    """Replace comment bodies and string/char literal bodies with spaces (newlines and length kept)."""
    out = []
    i, n = 0, len(src)
    while i < n:
        c = src[i]
        nxt = src[i + 1] if i + 1 < n else ""
        if c == "/" and nxt == "/":
            j = src.find("\n", i)
            j = n if j < 0 else j
            out.append(" " * (j - i))
            i = j
        elif c == "/" and nxt == "*":
            j = src.find("*/", i + 2)
            j = n if j < 0 else j + 2
            out.append("".join(ch if ch == "\n" else " " for ch in src[i:j]))
            i = j
        elif src.startswith('"""', i):
            j = src.find('"""', i + 3)
            j = n if j < 0 else j + 3
            out.append('"""' + "".join(ch if ch == "\n" else " " for ch in src[i + 3:j - 3]) + '"""')
            i = j
        elif c == '"' or c == "'":
            q = c
            j = i + 1
            while j < n and src[j] != q:
                if src[j] == "\\":
                    j += 1
                if j < n and src[j] == "\n":
                    break
                j += 1
            j = min(j + 1, n)
            out.append(q + " " * max(0, j - i - 2) + (q if j - i >= 2 else ""))
            i = j
        else:
            out.append(c)
            i += 1
    return "".join(out)


def split_params(params: str):
    depth, cur, parts = 0, [], []
    for ch in params:
        if ch == "<":
            depth += 1
        elif ch == ">":
            depth -= 1
        if ch == "," and depth == 0:
            parts.append("".join(cur))
            cur = []
        else:
            cur.append(ch)
    if "".join(cur).strip():
        parts.append("".join(cur))
    return parts


def param_type(param: str) -> str:
    param = re.sub(r"@\w+(?:\([^)]*\))?", " ", param)
    param = re.sub(r"\bfinal\b", " ", param).strip()
    toks = param.rsplit(None, 1)
    t = toks[0] if len(toks) == 2 else param
    return re.sub(r"\s+", "", t)


NATIVE = re.compile(r"\bnative\s+(?P<ret>[\w.< >\[\],?]+?)\s+(?P<name>\w+)\s*\((?P<params>[^)]*)\)\s*(?:throws[^;]*)?;")
IFACE = re.compile(r"\b(?:interface|class)\s+(?P<name>\w+)[^{;]*?\b(?:extends|implements)\b[^{;]*?\bJS\w*\b[^{;]*\{")
ABSTRACT = re.compile(r"(?:^|[;{}])\s*(?:@\w+(?:\([^)]*\))?\s*)*(?:public\s+|abstract\s+)*(?P<ret>[\w.< >\[\]]+)\s+(?P<name>\w+)\s*\((?P<params>[^)]*)\)\s*;")


def matching_brace(text: str, open_idx: int) -> int:
    depth = 0
    for k in range(open_idx, len(text)):
        if text[k] == "{":
            depth += 1
        elif text[k] == "}":
            depth -= 1
            if depth == 0:
                return k
    return len(text)


def scan_file(path: Path):
    raw = path.read_text(encoding="utf-8", errors="replace")
    text = blank_strings_and_comments(raw)
    findings = []

    def line_of(idx):
        return text.count("\n", 0, idx) + 1

    def check(kind, ret, name, params, idx):
        bad = []
        if LONG.match(re.sub(r"\s+", "", ret)):
            bad.append(f"returns {ret.strip()}")
        for p in split_params(params):
            if LONG.match(param_type(p)):
                bad.append(f"takes {param_type(p)}")
        if bad:
            findings.append((line_of(idx), kind, name, "; ".join(bad)))

    for m in NATIVE.finditer(text):
        check("native/@JSBody", m["ret"], m["name"], m["params"], m.start())
    for m in IFACE.finditer(text):
        end = matching_brace(text, m.end() - 1)
        body = text[m.end():end]
        base = m.end()
        for a in ABSTRACT.finditer(body):
            if a["ret"] in ("return", "new", "throw", "else"):
                continue
            check(f"JSO method in {m['name']}", a["ret"], a["name"], a["params"], base + a.start())
    return findings


def main(argv):
    if not argv:
        print(__doc__)
        return 2
    files = []
    for a in argv:
        p = Path(a)
        files += [p] if p.is_file() else sorted(p.rglob("*.java"))
    total = 0
    for f in files:
        for line, kind, name, why in scan_file(f):
            total += 1
            print(f"{f}:{line}: [{kind}] {name}() {why}  -> wasm-gc i64 needs BigInt; use double/int at the JS boundary")
    print(f"checked {len(files)} java files, {total} long-at-JS-boundary problem(s)")
    return 1 if total else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
