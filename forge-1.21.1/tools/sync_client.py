#!/usr/bin/env python3
"""Copies PlebClient's shared code from the 1.8.9 Forge mod into the 1.21.1 mod.

The shared code is written against Minecraft 1.8 names. Here those names come
from net.plebmc.mc (a small layer over 1.21.1 in src/main/java/net/plebmc/mc),
so the copy only rewrites imports. Files in overrides/ replace the copied file
of the same path (for code that has to be written for 1.21.1 by hand).

Gradle does the same copy itself (the syncShared task in build.gradle); this script is
only for quick javac checks. Run from forge-1.21.1/:  python3 tools/sync_client.py
"""
import os
import re
import shutil
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
SRC = os.path.join(ROOT, "..", "java-client", "src", "main", "java", "net", "plebmc", "client")
DST = os.path.join(ROOT, "build", "generated", "shared", "net", "plebmc", "client")
OVR = os.path.join(ROOT, "overrides", "net", "plebmc", "client")

REWRITES = [
    (re.compile(r"\bnet\.minecraft\."), "net.plebmc.mc."),
    (re.compile(r"\borg\.lwjgl\.input\."), "net.plebmc.mc.input."),
    (re.compile(r"\bnet\.plebmc\.forge\.compat\."), "net.plebmc.forge.compat."),
    (re.compile(r"\borg\.lwjgl\.opengl\.Display\b"), "net.plebmc.mc.input.Display"),
]


def convert(text):
    for pat, rep in REWRITES:
        text = pat.sub(rep, text)
    return text


def main():
    if os.path.isdir(DST):
        shutil.rmtree(DST)
    n = 0
    for base, _, files in os.walk(SRC):
        for f in files:
            if not f.endswith(".java"):
                continue
            rel = os.path.relpath(os.path.join(base, f), SRC)
            out = os.path.join(DST, rel)
            os.makedirs(os.path.dirname(out), exist_ok=True)
            ovr = os.path.join(OVR, rel)
            if os.path.exists(ovr):
                shutil.copyfile(ovr, out)
            else:
                with open(os.path.join(base, f), encoding="utf-8") as fh:
                    text = fh.read()
                with open(out, "w", encoding="utf-8") as fh:
                    fh.write(convert(text))
            n += 1
    # overrides that have no 1.8 counterpart
    if os.path.isdir(OVR):
        for base, _, files in os.walk(OVR):
            for f in files:
                rel = os.path.relpath(os.path.join(base, f), OVR)
                out = os.path.join(DST, rel)
                if not os.path.exists(out):
                    os.makedirs(os.path.dirname(out), exist_ok=True)
                    shutil.copyfile(os.path.join(base, f), out)
                    n += 1
    print("synced %d files" % n)


if __name__ == "__main__":
    sys.exit(main())
