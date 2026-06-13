# ROSE Map Editor (Java / libGDX)

ROSE Online Map Editor built with **Java** and **libGDX**. This is a WIP.

## Requirements

- JDK 11+
- ROSE Online client data (`3DDATA/`, map folders, textures)
- Gradle 8+ (or use the Gradle wrapper in this folder)

## Quick start

1. Run the desktop launcher:

```powershell
cd map-editor-java
.\gradlew.bat :desktop:run
```

2. On first launch, pick your ROSE client folder when prompted. The folder must contain `3DDATA/`.

3. Click **Open Map...** to load a map. Use **Reload STB/STL** if you change game data on disk.

### Game root folder

The editor resolves the client folder in this order:

1. Command-line argument (first arg)
2. Saved setting (`%LOCALAPPDATA%\RoseMapEditor\editor.properties` on Windows, or `~/.rose-map-editor/editor.properties` elsewhere)
3. `ROSE_GAME_ROOT` environment variable
4. Native folder picker on first run

You can also pass the path explicitly:

```powershell
.\gradlew.bat :desktop:run --args="E:\Path\To\Rose Online"
```

Or set the environment variable (the Gradle `run` task forwards it automatically):

```powershell
$env:ROSE_GAME_ROOT = "E:\Path\To\Rose Online"
.\gradlew.bat :desktop:run
```

## Controls

| Input | Action |
|--------|--------|
| W / A / S / D | Move camera |
| Q / E | Move camera up / down |
| Shift | Move faster |
| Right mouse button | Look around |
| Left mouse button | Inspect object under cursor (when height tool is off) |
| H | Toggle height brush tool |
| L | Toggle terrain lightmap |
| F | Toggle wireframe |
| G | Toggle terrain textures |
| O | Toggle object textures |
| T | Toggle soft diffuse lighting |
| N | Toggle NPC spawn markers |
| M | Toggle monster spawn markers |
| Escape | Close dialog, or quit |

## Editor features

### Map management

- **Open Map...** — pick a map from `LIST_ZONE.STB`
- **Map Info...** — read-only summary of the loaded map
- **Delete Map...** — remove a map folder and its `LIST_ZONE` entry
- **New Flat Map...** — create a new map with flat HIM/TIL/IFO/MOV sectors
- **Import Flyff Map...** — import a Flyff `.wld` / `.lnd` map into ROSE format (*experimental*)
- **Procedural Map...** — generate terrain and objects from a seed (*experimental*)
- **Reload STB/STL** — reload core tables from the current game root

### Editing tools

- **Height tool** — brush-based HIM height editing with save back to disk
- **Bake lightmaps** — rebake terrain lightmaps for the loaded map

## TODO

- Full skeletal CHR animation on NPCs/characters
- Object batch renderer culling and frustum optimizations
- Menus, undo system, and property editors
- Tile painting, object placement/movement
- Audio preview, sprite rendering

## Building a fat JAR

```powershell
.\gradlew.bat :desktop:jar
java -jar desktop/build/libs/RoseMapEditor.jar "E:\Path\To\Rose Online"
```

## Screenshots

<img width="1280" height="797" alt="image" src="https://github.com/user-attachments/assets/d9c9f6ea-d5c5-428e-a9a4-573c1ba91da2" />
<img width="1280" height="798" alt="image" src="https://github.com/user-attachments/assets/3e8ce64d-4d21-4182-9880-d4ee9d832614" />

