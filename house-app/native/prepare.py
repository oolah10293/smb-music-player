#!/usr/bin/env python3
"""Fetch verified corresponding source for the bundled, separate Snapclient process."""
import hashlib
import shutil
import subprocess
import tarfile
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent
SOURCES = [
    ("vendor", "https://codeload.github.com/snapcast/snapcast/tar.gz/cf2be07155b850fd3d660416164970688179ce5c",
     "2d444a70c1be222a950f6947b817308e03be79b2ba789d9f5e844d6ae9336028"),
    ("deps/flac", "https://codeload.github.com/xiph/flac/tar.gz/refs/tags/1.4.3",
     "0a4bb82a30609b606650d538a804a7b40205366ce8fc98871b0ecf3fbb0611ee"),
    ("deps/boost", "https://archives.boost.io/release/1.85.0/source/boost_1_85_0.tar.bz2",
     "7009fe1faa1697476bdc7027703a2badb84e849b7b0baad5086b087b971f8617"),
]

for directory, url, digest in SOURCES:
    target = ROOT / directory
    marker = target / ".source-sha256"
    if marker.exists() and marker.read_text() == digest:
        continue
    with tempfile.TemporaryDirectory() as temporary:
        archive = Path(temporary) / "source.archive"
        subprocess.run(["curl", "--fail", "--location", "--retry", "3", "--max-time", "240", url, "-o", str(archive)], check=True)
        if hashlib.sha256(archive.read_bytes()).hexdigest() != digest:
            raise SystemExit(f"Source checksum failed: {directory}")
        extracted = Path(temporary) / "source"
        extracted.mkdir()
        with tarfile.open(archive) as tar:
            for entry in tar.getmembers():
                parts = Path(entry.name).parts[1:]
                if not parts:
                    continue
                if directory == "deps/boost" and parts[0] not in ("boost", "LICENSE_1_0.txt"):
                    continue
                entry.name = str(Path(*parts))
                tar.extract(entry, extracted, filter="data")
        if target.exists():
            shutil.rmtree(target)
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.move(str(extracted), target)
        marker.write_text(digest)

notices = ROOT.parent / "src/main/assets/licenses"
notices.mkdir(parents=True, exist_ok=True)
for source, name in [(ROOT / "vendor/LICENSE", "Snapcast-GPL-3.txt"),
                     (ROOT / "deps/flac/COPYING.Xiph", "FLAC-BSD.txt"),
                     (ROOT / "deps/boost/LICENSE_1_0.txt", "Boost-1.0.txt")]:
    shutil.copyfile(source, notices / name)
print("Snapclient sources and notices ready")
