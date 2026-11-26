# Agri-Brain planting-mode icons

These three PNGs are the original imagegen concepts, retained as reference art.
The exact prompts are recorded in `prompts.json`. They are not used to generate
the production icons: shrinking them lost detail at button size.

Run `scripts/generate_planting_icons.ps1` from PowerShell for the production icons.
It draws hand-authored pixel grids directly onto transparent 16×16 canvases;
each grid character is one pixel. The artwork occupies at most 12×12 pixels,
leaving two transparent pixels on each edge. There is no image resampling.

The output directory is
`src/main/resources/assets/industrial_overkill/textures/gui/sprites/`:

- `planting_first_found.png`: sprouting seed and selection arrow.
- `planting_round_robin.png`: two seeds with cycling arrows.
- `planting_rows.png`: three planted soil rows.

The shared button renderer draws the sprite at its native 16×16 size inside an
18×18 button, leaving at least three GUI pixels between the artwork and the
button's outer edge. Only the native pixel sprites are packaged in the mod.
