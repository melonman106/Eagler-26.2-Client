#!/usr/bin/env python3
"""Syntax-check the JavaScript inside every @JSBody(script = "...") of the VBV Java sources with Node.

A @JSBody script is a Java string, so one wrong escape (a bare apostrophe in a single-quoted JS string needs
``\\\\'`` in the Java source) produces JavaScript that no longer parses, and the compiler cannot tell. TeaVM's own
parser also warns about perfectly good modern syntax (``async``, arrow functions, optional catch binding), so its
build-log warnings are not a usable signal; Node is.

Each script is wrapped as ``(function(<params>){ <script> })`` and run through ``node --check``. Scripts that are not a
plain string literal (a constant, a method call) are reported as skipped, never as failures.

usage: check_jsbody_syntax.py <java-source-dir> [...]      exit 1 if any script has a syntax error
"""
import os
import re
import shutil
import subprocess
import sys
import tempfile

ESCAPES = {"n": "\n", "t": "\t", "r": "\r", "b": "\b", "f": "\f", "s": " ", '"': '"', "'": "'", "\\": "\\", "0": "\0"}


def java_unescape(text):
    out = []
    i = 0
    while i < len(text):
        c = text[i]
        if c != "\\":
            out.append(c)
            i += 1
            continue
        n = text[i + 1]
        if n == "u":
            j = i + 1
            while text[j] == "u":
                j += 1
            out.append(chr(int(text[j:j + 4], 16)))
            i = j + 4
        elif n == "\n":  # text-block line continuation
            i += 2
        else:
            out.append(ESCAPES.get(n, n))
            i += 2
    return "".join(out)


def text_block(raw):
    """Java text-block rules: strip the common indentation and trailing spaces, then process escapes."""
    lines = raw.split("\n")
    significant = [line for line in lines[:-1] if line.strip()] + [lines[-1]]
    indent = min((len(line) - len(line.lstrip(" \t")) for line in significant), default=0)
    stripped = []
    for index, line in enumerate(lines):
        line = line[indent:] if len(line) >= indent else line.lstrip(" \t")
        if index != len(lines) - 1:
            line = re.sub(r"[ \t]+$", "", line)
        stripped.append(line)
    return java_unescape("\n".join(stripped))


def skip_string(src, i):
    if src.startswith('"""', i):
        k = src.index('"""', i + 3)
        while src[k - 1] == "\\":
            k = src.index('"""', k + 3)
        return k + 3
    j = i + 1
    while src[j] != src[i]:
        if src[j] == "\\":
            j += 1
        j += 1
    return j + 1


def read_string_expr(src, i):
    """Reads a chain of Java string literals joined by plus signs. Returns the value, or None if it is not one."""
    parts = []
    while True:
        while i < len(src) and src[i].isspace():
            i += 1
        if i >= len(src):
            return None
        if src.startswith('"""', i):
            start = src.index("\n", i) + 1
            end = start
            while True:
                end = src.index('"""', end)
                backslashes = 0
                while src[end - 1 - backslashes] == "\\":
                    backslashes += 1
                if backslashes % 2 == 0:
                    break
                end += 3
            parts.append(text_block(src[start:end]))
            i = end + 3
        elif src[i] == '"':
            j = i + 1
            while True:
                if src[j] == "\\":
                    j += 2
                    continue
                if src[j] == '"':
                    break
                j += 1
            parts.append(java_unescape(src[i + 1:j]))
            i = j + 1
        else:
            return None
        k = i
        while k < len(src) and src[k].isspace():
            k += 1
        if k < len(src) and src[k] == "+":
            i = k + 1
            continue
        # Anything else after the literal (a method call, a comma, a constant) means it was not a plain literal chain.
        return "".join(parts) if k >= len(src) or src[k] in ",)" else None


def annotation_arguments(src, start):
    """Text between the parentheses of an annotation whose opening parenthesis ends just before ``start``."""
    i = start
    depth = 1
    while depth:
        c = src[i]
        if c in "\"'":
            i = skip_string(src, i)
            continue
        if src.startswith("//", i):
            i = src.index("\n", i)
            continue
        if src.startswith("/*", i):
            i = src.index("*/", i) + 2
            continue
        if c == "(":
            depth += 1
        elif c == ")":
            depth -= 1
        i += 1
    return src[start:i - 1]


def main(roots):
    node = shutil.which("node")
    if not node:
        print("::error::node is not installed, so the @JSBody scripts cannot be syntax-checked")
        return 2
    total = failed = skipped = 0
    with tempfile.TemporaryDirectory() as tmp:
        for root in roots:
            for directory, _dirs, files in os.walk(root):
                for name in sorted(files):
                    if not name.endswith(".java"):
                        continue
                    path = os.path.join(directory, name)
                    with open(path, encoding="utf-8") as handle:
                        src = handle.read()
                    for match in re.finditer(r"@JSBody\s*\(", src):
                        args = annotation_arguments(src, match.end())
                        line = src.count("\n", 0, match.start()) + 1
                        total += 1
                        script = re.search(r"\bscript\s*=\s*", args)
                        value = read_string_expr(args, script.end()) if script else None
                        if value is None:
                            skipped += 1
                            print(f"skipped {name}:{line} (the script is not a plain string literal)")
                            continue
                        param_match = re.search(r"params\s*=\s*(\{[^}]*\}|\"[^\"]*\")", args)
                        params = re.findall(r'"([^"]*)"', param_match.group(1)) if param_match else []
                        target = os.path.join(tmp, f"{name[:-5]}_{line}.js")
                        with open(target, "w", encoding="utf-8") as out:
                            out.write("(function(" + ",".join(params) + "){\n" + value + "\n})")
                        result = subprocess.run([node, "--check", target], capture_output=True, text=True)
                        if result.returncode != 0:
                            failed += 1
                            message = next((l for l in result.stderr.splitlines() if re.match(r"\w*Error:", l)), "syntax error")
                            print(f"::error file={path},line={line}::@JSBody script does not parse: {message.strip()[:200]}")
    print(f"checked {total} @JSBody script(s): {failed} with a JavaScript syntax error, {skipped} skipped")
    return 1 if failed else 0


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(2)
    sys.exit(main(sys.argv[1:]))
