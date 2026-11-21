#!/usr/bin/env python3
"""Fabrique data/v1, v2, v3 et v1_mod à partir de Caltech-101."""
import argparse, random, shutil
from pathlib import Path
from PIL import ImageOps, Image

p = argparse.ArgumentParser()
p.add_argument("--source", required=True, help="dossier 101_ObjectCategories")
p.add_argument("--out", default="data")
p.add_argument("--categories", type=int, default=10)
p.add_argument("--per-class", type=int, default=50)
p.add_argument("--seed", type=int, default=42)
a = p.parse_args()

rnd = random.Random(a.seed)
src, out = Path(a.source).expanduser(), Path(a.out)
RESERVE = 10

cats = [d for d in sorted(src.iterdir())
        if d.is_dir() and d.name != "BACKGROUND_Google"
        and len(list(d.glob("*.jpg"))) >= a.per_class + RESERVE]
chosen = rnd.sample(cats, a.categories)

base, reserve = [], []
for c in chosen:
    files = sorted(c.glob("*.jpg"))
    rnd.shuffle(files)
    base    += [f"{c.name}/{f.name}" for f in files[:a.per_class]]
    reserve += [f"{c.name}/{f.name}" for f in files[a.per_class:a.per_class + RESERVE]]

rnd.shuffle(reserve)
new_v2 = reserve[:50]
new_v3 = reserve[50:75]

v1 = list(base)
v2 = v1 + new_v2
modified = rnd.sample(v2, 25)
deleted  = rnd.sample([x for x in v2 if x not in modified], 25)
v3 = [x for x in v2 if x not in deleted] + new_v3

def copy_set(files, dest):
    if dest.exists():
        shutil.rmtree(dest)
    for rel in files:
        target = dest / rel
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src / rel, target)

copy_set(v1, out / "v1")
copy_set(v2, out / "v2")
copy_set(v3, out / "v3")

for rel in modified:
    path = out / "v3" / rel
    ImageOps.mirror(Image.open(path)).save(path, "JPEG", quality=95)

# V1_MOD : V1 avec 50 images modifiées (scénario E4)
mod_set = rnd.sample(v1, 50)
copy_set(v1, out / "v1_mod")
for rel in mod_set:
    path = out / "v1_mod" / rel
    ImageOps.mirror(Image.open(path)).save(path, "JPEG", quality=95)

print(f"V1={len(v1)}  V2={len(v2)}  V3={len(v3)}")
print(f"V3 : {len(modified)} modifiées, {len(deleted)} supprimées, {len(new_v3)} nouvelles")
print(f"V1_MOD : {len(mod_set)} modifiées")
