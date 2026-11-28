#!/usr/bin/env python3
"""Make a Linux libmpv runtime for the Vision desktop installer."""
import argparse
import os
from pathlib import Path
import re
import shutil
import subprocess


# The host supplies its C runtime and GPU driver stack.
HOST_LIBRARIES = re.compile(
    r"^(?:ld-linux[^/]*|lib(?:c|m|pthread|dl|rt|resolv|util|anl|thread_db)\.so"
    r"|lib(?:cuda|nvcuvid|nvidia[^/]*|GL[^/]*|EGL[^/]*|vulkan[^/]*|drm[^/]*|va(?:-[^/]*)?)\.so)"
)


def output(*command):
    return subprocess.check_output(command, text=True, env={"PATH": os.environ["PATH"], "LC_ALL": "C"})


def prepare(library: Path, destination: Path):
    if not library.is_file():
        raise SystemExit(f"libmpv missing: {library}")
    if not shutil.which("patchelf"):
        raise SystemExit("patchelf is required")
    if destination.exists() and any(destination.iterdir()):
        raise SystemExit(f"Choose an empty output directory: {destination}")
    destination.mkdir(parents=True, exist_ok=True)

    dependencies = {library.name: library.resolve()}
    for row in output("ldd", str(library.resolve())).splitlines():
        name, separator, detail = row.strip().partition(" => ")
        if separator and detail.startswith("not found"):
            raise SystemExit(f"Missing runtime dependency: {name}")
        source = (detail if separator else name).split(" (", 1)[0]
        if not source.startswith("/"):
            continue
        name = Path(name if separator else source).name
        if not HOST_LIBRARIES.match(name):
            dependencies[name] = Path(source)

    for name, source in dependencies.items():
        shutil.copy2(source, destination / name)
    for name in dependencies:
        target = destination / name
        for needed in output("patchelf", "--print-needed", str(target)).splitlines():
            if needed.startswith("/") and Path(needed).name in dependencies:
                subprocess.run(
                    ["patchelf", "--replace-needed", needed, Path(needed).name, str(target)],
                    check=True,
                )
        subprocess.run(["patchelf", "--set-rpath", "$ORIGIN", str(target)], check=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("library", type=Path, help="Path to libmpv.so.2 on the packaging host")
    parser.add_argument("destination", type=Path, help="Empty portable runtime directory")
    args = parser.parse_args()
    prepare(args.library, args.destination)
