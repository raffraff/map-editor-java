package com.rose.mapeditor.map;

import com.rose.mapeditor.GameData;
import com.rose.mapeditor.data.Stb;
import com.rose.mapeditor.data.Stl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Shared helpers for writing new maps into LIST_ZONE and on disk. */
public final class MapCreationSupport {

    private MapCreationSupport() {
    }

    public static Path resolveMapRoot(GameData data, FlatMapCreator.Request request) {
        return data.resolve(String.format("3Ddata/MAPS/%s/%s",
            request.planetName.toUpperCase(Locale.ROOT), request.mapFolder));
    }

    public static String normalizeZoneFileName(String zoneFileName) {
        String trimmed = zoneFileName.trim();
        if (!trimmed.toLowerCase(Locale.ROOT).endsWith(".zon")) {
            trimmed += ".ZON";
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }

    public static void ensureMapRootAvailable(Path mapRoot) throws IOException {
        if (Files.exists(mapRoot)) {
            throw new IOException("Map folder already exists: " + mapRoot);
        }
    }

    public static void writeEmptySectorIfo(Path mapRoot, int cellY, int cellX, String sectorName) throws IOException {
        Ifo ifo = Ifo.createEmptySector(cellY, cellX, sectorName);
        ifo.save(mapRoot.resolve(sectorName + ".IFO").toString());
    }

    public static void writeSectorLightmapTree(Path mapRoot, String sectorName, Path planeLightmapSource,
                                               Path lightmapTemplate) throws IOException {
        writeSectorLightmapTree(mapRoot, sectorName, planeLightmapSource, lightmapTemplate, true);
    }

    public static void writeSectorLightmapTree(Path mapRoot, String sectorName, Path planeLightmapSource,
                                               Path lightmapTemplate, boolean writePlaneLightmap) throws IOException {
        Path sectorDataDir = mapRoot.resolve(sectorName);
        Files.createDirectories(sectorDataDir);
        writeEmptyObjectLightmaps(sectorDataDir);

        if (!writePlaneLightmap) {
            return;
        }

        Path destLightmap = sectorDataDir.resolve(sectorName + "_PLANELIGHTINGMAP.DDS");
        if (planeLightmapSource != null && Files.isRegularFile(planeLightmapSource)) {
            Files.copy(planeLightmapSource, destLightmap);
        } else {
            PlaneLightingMapWriter.write(destLightmap, lightmapTemplate);
        }
    }

    public static void writeEmptyObjectLightmaps(Path sectorDataDir) throws IOException {
        Files.createDirectories(sectorDataDir.resolve("LIGHTMAP"));
        Lit emptyLit = new Lit();
        emptyLit.save(sectorDataDir.resolve("LIGHTMAP/BUILDINGLIGHTMAPDATA.LIT").toString());
        emptyLit.save(sectorDataDir.resolve("LIGHTMAP/OBJECTLIGHTMAPDATA.LIT").toString());
    }

    public static void registerNewMap(GameData data, FlatMapCreator.Request request, Path mapRoot,
                                      String zoneFileName) throws IOException {
        updateListZone(data, request, zoneFileName, mapRoot);
        updateListZoneS(data, request);
        data.stbs.get("LIST_ZONE").save();
        data.stls.get("LIST_ZONE_S").save();
    }

    static FlatMapCreator.Result buildResult(GameData data, FlatMapCreator.Request request,
                                             Path mapRoot, String zoneFileName) {
        FlatMapCreator.Result result = new FlatMapCreator.Result();
        result.mapId = request.mapId;
        result.mapFolderRelative = data.getGameRoot().relativize(mapRoot).toString().replace('\\', '/');
        result.zoneRelativePath = result.mapFolderRelative + "/" + zoneFileName;
        return result;
    }

    private static void updateListZone(GameData data, FlatMapCreator.Request request, String zoneFileName,
                                       Path mapRoot) {
        Stb listZone = data.stbs.get("LIST_ZONE");
        int mapId = request.mapId;
        int columnCount = listZoneColumnCount(listZone);

        while (listZone.cells.size() <= mapId) {
            listZone.cells.add(newListZoneRow(columnCount));
        }

        String folderRelative = data.getGameRoot().relativize(mapRoot).toString().replace('\\', '/') + "/";
        String zoneRelative = folderRelative + zoneFileName;
        String zoneBase = zoneFileName;
        if (zoneBase.toLowerCase(Locale.ROOT).endsWith(".zon")) {
            zoneBase = zoneBase.substring(0, zoneBase.length() - 4);
        }

        List<String> row = listZone.cells.get(mapId);
        ensureRowWidth(row, columnCount);
        setRowCell(row, 0, "");
        setRowCell(row, 1, request.name.trim());
        setRowCell(row, 2, zoneRelative);
        setRowCell(row, 3, "start");
        setRowCell(row, 4, "restore");
        setRowCell(row, 5, "0");
        setRowCell(row, 6, "");
        setRowCell(row, 7, "");
        setRowCell(row, 8, Integer.toString(request.skyId));
        setRowCell(row, 9, folderRelative + zoneBase + ".dds");
        setRowCell(row, 10, "30");
        setRowCell(row, 11, "30");
        setRowCell(row, 12, request.decorationPath.trim());
        setRowCell(row, 13, request.constructionPath.trim());
        setRowCell(row, 14, "160");
        setRowCell(row, 15, "0");
        setRowCell(row, 16, "11");
        setRowCell(row, 17, "112");
        setRowCell(row, 18, "128");
        setRowCell(row, 19, "");
        setRowCell(row, 20, Integer.toString(request.planetId));
        setRowCell(row, 21, "");
        setRowCell(row, 22, "");
        setRowCell(row, 23, "");
        setRowCell(row, 24, "");
        setRowCell(row, 25, "");
        setRowCell(row, 26, "4000");
        setRowCell(row, 27, String.format(Locale.ROOT, "LZON%04d", mapId));
        setRowCell(row, 28, "");
        setRowCell(row, 29, "4");
        setRowCell(row, 30, "4");
        setRowCell(row, 31, "0");
        setRowCell(row, 32, "");
        setRowCell(row, 33, "");
        setRowCell(row, 34, "");
        setRowCell(row, 35, "0");
        setRowCell(row, 36, "25");
        setRowCell(row, 37, Integer.toString(mapId));
    }

    /** Matches the loaded LIST_ZONE schema (37 columns in older clients, 38 in newer ones). */
    private static int listZoneColumnCount(Stb listZone) {
        int max = 0;
        for (List<String> row : listZone.cells) {
            if (row != null) {
                max = Math.max(max, row.size());
            }
        }
        return max > 0 ? max : 38;
    }

    private static List<String> newListZoneRow(int columnCount) {
        List<String> row = new ArrayList<>(columnCount);
        for (int i = 0; i < columnCount; i++) {
            row.add("");
        }
        return row;
    }

    private static void ensureRowWidth(List<String> row, int columnCount) {
        while (row.size() < columnCount) {
            row.add("");
        }
    }

    private static void setRowCell(List<String> row, int index, String value) {
        if (index >= 0 && index < row.size()) {
            row.set(index, value);
        }
    }

    private static void updateListZoneS(GameData data, FlatMapCreator.Request request) {
        Stl listZoneS = data.stls.get("LIST_ZONE_S");
        String stringId = String.format(Locale.ROOT, "LZON%04d", request.mapId);
        String displayName = request.name.trim();

        int useRow = -1;
        for (int i = 0; i < listZoneS.entries.size(); i++) {
            if (stringId.equalsIgnoreCase(listZoneS.entries.get(i).stringId)) {
                useRow = i;
                break;
            }
        }

        if (useRow >= 0) {
            for (List<Stl.Row> languageRows : listZoneS.rows) {
                Stl.Row row = languageRows.get(useRow);
                row.text = displayName;
                row.comment = displayName;
            }
        } else {
            for (List<Stl.Row> languageRows : listZoneS.rows) {
                Stl.Row row = new Stl.Row();
                row.text = displayName;
                row.comment = displayName;
                languageRows.add(row);
            }
            Stl.Entry entry = new Stl.Entry();
            entry.id = listZoneS.entries.size();
            entry.stringId = stringId;
            listZoneS.entries.add(entry);
        }
    }
}
