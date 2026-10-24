# Electric VLSI Design System(Edition de Zacharie)

A customized enhancement of [Electric VLSI Design System](https://www.staticfreesoft.com/) 9.08.1 (open-source GPL EDA tool), focused on schematic editing experience. The goal: make schematic wiring feel like JLCEDA (EasyEDA-style) interaction.

## v1.0.0 Features

### Terminal Marking
- **Yellow dot** = unconnected terminal: component pins (cell instances and primitive components such as CN transistors/resistors), ground/power network labels, dangling wire ends, and wire connection nodes
- **Red dot** = connected terminal
- Wire junctions keep the **native Electric disc** (wire-colored, scales with zoom), so connected wires are distinguishable from crossing wires

### Click-to-Wire (JLCEDA-style)
- Click a yellow dot → wiring mode: **live rubber-band preview follows the mouse** + horizontal/vertical dashed alignment guides → click again to commit
- 45° routing; ESC / right-click / clicking the start point again = cancel
- **W key**: toggles wiring mode (crosshair cursor); click a terminal / wire (auto-branch) / empty space (creates a temporary terminal) to start
- **DEL key**: while wiring, deletes the wire segment connected to the start terminal and exits
- Double-click on terminals still opens the native GetInfo dialog (no conflict)
- Quick right-click on a dangling wire end → Create Export menu (right-press-drag still does native wiring)

### Auto-Connect
- **Drag a component**: terminals touching wires or other terminals connect on release (mid-wire contact splits the wire and creates a native wirepin; live green cross markers show contact points, multiple networks at once)
- **Place a component** (palette / component→Cell): terminals landing on wires connect immediately
- **Paste a component**: same auto-connect
- Safety rules: already-connected terminals never re-connect; moving wires never triggers auto-connect
- **R key**: connects ALL overlapping unconnected terminals in the schematic at once

### Context Menu
- **Right-click on empty schematic space**: Wire (W) / Auto-Connect Overlaps (R) / Manipulate Exports... (native Electric command)

### Rendering
- Schematic wires rendered 5px thick
- Ctrl+G grid is now a **semi-transparent line grid** (all windows; content renders above the grid)
- All highlights (NCC hyperlinks, DRC errors, selection) are **green** with a green cross location marker

### Explorer Tree
- Right-click a cell → **Rename All Views of Cell...**: renames all views ({sch}/{lay}/{ic}, ...) of the same name together
- New Cell dialog: checkbox view list — **create multiple same-name views at once** (auto-grouped)

## Run

```
double-click target\run-electric.bat
or
java -jar target\electric-9.08.1.jar
```

Requires JDK 17+.

## Build

```bash
# full compile (equivalent to the official Ant build's compile target)
javac -g -encoding UTF-8 --release 17 -cp ".;lib/*" -d target/classes @sources.txt

# copy resources (*.jelib/*.prm/*.xml/*.gif/*.png etc.) and slf4j (org/) into target/classes
# package
jar cfm target/electric-9.08.1.jar META-INF/MANIFEST.MF -C target/classes .
```

Dependencies: the three JOGL Java3D jars in `lib/` (`java3d-core`, `java3d-utils`, `vecmath`, 1.7.2); slf4j classes live in `org/`.

## Hotkeys (schematic windows only)

| Key | Action |
|---|---|
| W | Toggle wiring mode |
| R | Connect all overlapping unconnected terminals |
| DEL (while wiring) | Delete the wire segment connected to the start terminal |
| ESC / right-click / W | Cancel wiring and exit |
| Ctrl+G | Toggle grid (semi-transparent line grid) |

## Documentation

- `docs/` — method-level GUI module documentation (README index + gui-01 ~ gui-11 sections)
- `docs/work/` — development session summary
- `docs/prompt/` — archived feature-request prompts

## License

GPL v3 (same as Electric). This version only enhances interaction/rendering layers; database formats and file compatibility are unchanged.

---

*Version: v1.0.0 · Based on Electric 9.08.1 · 2026-10-24*
