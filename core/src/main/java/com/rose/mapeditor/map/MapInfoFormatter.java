package com.rose.mapeditor.map;

import com.rose.mapeditor.GameData;
import com.rose.mapeditor.data.Stb;
import com.rose.mapeditor.data.Stl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Builds a read-only summary of the current map from LIST_ZONE and loaded data. */
public final class MapInfoFormatter {

    private MapInfoFormatter() {
    }

    public static String format(GameData data, MapLoader.LoadedMap map) throws IOException {
        if (map == null) {
            return "No map is loaded.";
        }
        if (data.stbs.isEmpty()) {
            data.loadCoreTables();
        }

        Stb listZone = data.stbs.get("LIST_ZONE");
        int mapId = map.mapId;

        String name = map.name != null && !map.name.isBlank() ? map.name.trim() : safeCell(listZone, mapId, 1);
        String zonePath = safeCell(listZone, mapId, 2);
        String skyId = safeCell(listZone, mapId, 8);
        String decorationZsc = safeCell(listZone, mapId, 12);
        String constructionZsc = safeCell(listZone, mapId, 13);
        String planet = resolvePlanetName(data, safeCell(listZone, mapId, 20));

        Path gameRoot = data.getGameRoot().toAbsolutePath().normalize();
        Path mapFolderAbs = Path.of(map.mapFolder).toAbsolutePath().normalize();
        String mapFolderRel = formatRelative(gameRoot, mapFolderAbs);

        StringBuilder out = new StringBuilder();
        out.append("Name: ").append(name.isBlank() ? "-" : name).append('\n');
        out.append("Map ID: ").append(mapId).append('\n');
        out.append("Planet: ").append(planet).append('\n');
        out.append("Sky ID: ").append(skyId.isBlank() ? "-" : skyId).append('\n');
        out.append('\n');
        out.append("ZON file: ").append(zonePath.isBlank() ? "-" : zonePath).append('\n');
        out.append("Map folder: ").append(mapFolderRel).append('\n');
        out.append("Map folder (absolute): ").append(mapFolderAbs).append('\n');
        out.append('\n');
        out.append("Decoration ZSC: ").append(decorationZsc.isBlank() ? "-" : decorationZsc).append('\n');
        out.append("Construction ZSC: ").append(constructionZsc.isBlank() ? "-" : constructionZsc).append('\n');
        if (map.blockCount > 0) {
            out.append('\n');
            out.append("Terrain sectors: ").append(map.blockCount);
        }
        return out.toString();
    }

    private static String resolvePlanetName(GameData data, String planetStringId) throws IOException {
        if (planetStringId == null || planetStringId.isBlank()) {
            return "-";
        }
        ensurePlanetTable(data);
        Stl planets = data.stls.get("STR_PLANET");
        if (planets != null) {
            String resolved = planets.search(planetStringId.trim());
            if (resolved != null && !resolved.isBlank() && !resolved.equalsIgnoreCase(planetStringId.trim())) {
                return resolved.trim();
            }
        }
        return planetStringId.trim();
    }

    private static void ensurePlanetTable(GameData data) throws IOException {
        if (data.stls.containsKey("STR_PLANET")) {
            return;
        }
        Path path = data.resolve("3DDATA/STB/STR_PLANET.STL");
        if (Files.isRegularFile(path)) {
            data.stls.put("STR_PLANET", new Stl(path.toString()));
        }
    }

    private static String safeCell(Stb listZone, int mapId, int column) {
        if (listZone == null || mapId < 0 || mapId >= listZone.cells.size()) {
            return "";
        }
        try {
            return listZone.cell(mapId, column);
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String formatRelative(Path gameRoot, Path target) {
        try {
            return gameRoot.relativize(target).toString().replace('\\', '/');
        } catch (Exception ignored) {
            return target.toString().replace('\\', '/');
        }
    }
}
