package com.rose.mapeditor.map;

import com.rose.mapeditor.GameData;
import com.rose.mapeditor.data.Stb;
import com.rose.mapeditor.data.Stl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** Removes a map from disk and clears its LIST_ZONE / LIST_ZONE_S entries. */
public final class MapDeleter {

    public static final class Result {
        public int mapId;
        public String mapName;
        public Path deletedFolder;
    }

    private MapDeleter() {
    }

    public static Result delete(GameData data, int mapId) throws IOException {
        if (data.stbs.isEmpty()) {
            data.loadCoreTables();
        }

        Stb listZone = data.stbs.get("LIST_ZONE");
        if (mapId < 1 || mapId >= listZone.cells.size()) {
            throw new IOException("Invalid map ID: " + mapId);
        }

        String zonePath = listZone.cell(mapId, 2);
        if (zonePath == null || zonePath.trim().isEmpty()) {
            throw new IOException("Map " + mapId + " has no zone path in LIST_ZONE.");
        }

        Path zoneFile = data.resolve(zonePath.trim());
        Path mapFolder = zoneFile.getParent();
        if (mapFolder == null) {
            throw new IOException("Could not resolve map folder for map " + mapId);
        }

        validateDeletablePath(data.getGameRoot(), mapFolder);

        String stringId = listZone.cell(mapId, 27);
        Stl listZoneS = data.stls.get("LIST_ZONE_S");
        String mapName = listZoneS != null ? listZoneS.search(stringId) : listZone.cell(mapId, 1);
        if (mapName == null || mapName.isBlank()) {
            mapName = "Map " + mapId;
        }

        deleteOptionalFile(data, listZone.cell(mapId, 9));

        if (Files.exists(mapFolder)) {
            deleteRecursive(mapFolder);
        }

        clearListZoneRow(listZone, mapId);
        clearListZoneString(listZoneS, stringId);

        listZone.save();
        if (listZoneS != null) {
            listZoneS.save();
        }

        Result result = new Result();
        result.mapId = mapId;
        result.mapName = mapName;
        result.deletedFolder = mapFolder;
        return result;
    }

    private static void deleteOptionalFile(GameData data, String relativePath) throws IOException {
        if (relativePath == null || relativePath.trim().isEmpty()) {
            return;
        }
        Path file = data.resolve(relativePath.trim());
        if (!Files.exists(file)) {
            return;
        }
        validateDeletablePath(data.getGameRoot(), file);
        Files.deleteIfExists(file);
    }

    private static void validateDeletablePath(Path gameRoot, Path target) throws IOException {
        Path normalizedGame = gameRoot.toAbsolutePath().normalize();
        Path normalizedTarget = target.toAbsolutePath().normalize();
        if (!normalizedTarget.startsWith(normalizedGame)) {
            throw new IOException("Refusing to delete path outside game root: " + target);
        }

        String pathText = normalizedTarget.toString().replace('\\', '/').toUpperCase(Locale.ROOT);
        if (!pathText.contains("/MAPS/")) {
            throw new IOException("Refusing to delete path outside MAPS folder: " + target);
        }
    }

    private static void deleteRecursive(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new RuntimeException("Failed deleting " + path + ": " + e.getMessage(), e);
                }
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            }
            throw e;
        }
    }

    private static void clearListZoneRow(Stb listZone, int mapId) {
        List<String> row = listZone.cells.get(mapId);
        for (int i = 0; i < row.size(); i++) {
            row.set(i, "");
        }
    }

    private static void clearListZoneString(Stl listZoneS, String stringId) {
        if (listZoneS == null || stringId == null || stringId.isBlank()) {
            return;
        }

        int entryIndex = -1;
        for (int i = 0; i < listZoneS.entries.size(); i++) {
            if (stringId.equalsIgnoreCase(listZoneS.entries.get(i).stringId)) {
                entryIndex = i;
                break;
            }
        }
        if (entryIndex < 0) {
            return;
        }

        for (List<Stl.Row> languageRows : listZoneS.rows) {
            if (entryIndex < languageRows.size()) {
                Stl.Row row = languageRows.get(entryIndex);
                row.text = "";
                row.comment = "";
            }
        }
    }
}
