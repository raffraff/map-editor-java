package com.rose.mapeditor.map;

import com.rose.mapeditor.GameData;
import com.rose.mapeditor.data.Stb;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Indexes terrain sectors (.HIM) available in Rose map folders. */
public final class MapSectorCatalog {

    public static final class SectorRef {
        public int sourceMapId;
        public String sourceMapName;
        public Path mapFolder;
        public String sectorName;
        /** Sector grid row (Y cell index from name, e.g. 30 in {@code 30_31}). */
        public int cellY;
        /** Sector grid column (X cell index from name, e.g. 31 in {@code 30_31}). */
        public int cellX;
        public Path himFile;
        public Path tilFile;
        public Path movFile;
        public Path planeLightmap;
        public Path buildingLit;
        public Path objectLit;
    }

    private MapSectorCatalog() {
    }

    public static List<SectorRef> listSectorsForMap(GameData data, int mapId) throws IOException {
        if (data.stbs.isEmpty()) {
            data.loadCoreTables();
        }

        Stb listZone = data.stbs.get("LIST_ZONE");
        String zonePath = listZone.cell(mapId, 2);
        Path zoneFile = data.resolve(zonePath);
        Path mapFolder = zoneFile.getParent();
        if (mapFolder == null || !Files.isDirectory(mapFolder)) {
            return List.of();
        }

        String mapName = data.stls.get("LIST_ZONE_S").search(listZone.cell(mapId, 27));
        List<SectorRef> sectors = new ArrayList<>();
        collectSectors(mapFolder, mapId, mapName, sectors);
        sectors.sort(Comparator.comparing(s -> s.sectorName));
        return sectors;
    }

    public static List<SectorRef> listSectorsForMaps(GameData data, Iterable<Integer> mapIds) throws IOException {
        List<SectorRef> all = new ArrayList<>();
        for (int mapId : mapIds) {
            all.addAll(listSectorsForMap(data, mapId));
        }
        return all;
    }

    private static void collectSectors(Path mapFolder, int mapId, String mapName, List<SectorRef> out) {
        File[] himFiles = mapFolder.toFile().listFiles(
            (dir, name) -> name.toUpperCase().endsWith(".HIM"));
        if (himFiles == null || himFiles.length == 0) {
            collectNestedSectors(mapFolder, mapId, mapName, out);
            return;
        }

        Arrays.sort(himFiles, Comparator.comparing(File::getName));
        for (File himFile : himFiles) {
            SectorRef ref = buildSectorRef(mapFolder, mapId, mapName, himFile.toPath());
            if (ref != null) {
                out.add(ref);
            }
        }
    }

    private static void collectNestedSectors(Path mapFolder, int mapId, String mapName, List<SectorRef> out) {
        File[] children = mapFolder.toFile().listFiles(File::isDirectory);
        if (children == null) {
            return;
        }
        for (File child : children) {
            File[] sectorHim = child.listFiles((dir, name) -> name.toUpperCase().endsWith(".HIM"));
            if (sectorHim == null) {
                continue;
            }
            for (File himFile : sectorHim) {
                SectorRef ref = buildSectorRef(mapFolder, mapId, mapName, himFile.toPath());
                if (ref != null) {
                    out.add(ref);
                }
            }
        }
    }

    private static SectorRef buildSectorRef(Path mapFolder, int mapId, String mapName, Path himPath) {
        String fileName = himPath.getFileName().toString();
        String sectorName = fileName.substring(0, fileName.length() - 4);
        if (sectorName.length() < 5 || sectorName.charAt(2) != '_') {
            return null;
        }

        int[] coords = parseSectorCoords(sectorName);
        if (coords == null) {
            return null;
        }

        Path til = resolveSibling(himPath, ".TIL");
        Path mov = resolveSibling(himPath, ".MOV");
        if (!Files.isRegularFile(til) || !Files.isRegularFile(mov)) {
            return null;
        }

        SectorRef ref = new SectorRef();
        ref.sourceMapId = mapId;
        ref.sourceMapName = mapName;
        ref.mapFolder = mapFolder;
        ref.sectorName = sectorName;
        ref.cellY = coords[0];
        ref.cellX = coords[1];
        ref.himFile = himPath;
        ref.tilFile = til;
        ref.movFile = mov;

        Path sectorDataDir = mapFolder.resolve(sectorName);
        ref.planeLightmap = sectorDataDir.resolve(sectorName + "_PLANELIGHTINGMAP.DDS");
        if (!Files.isRegularFile(ref.planeLightmap)) {
            ref.planeLightmap = himPath.getParent().resolve(sectorName + "_PLANELIGHTINGMAP.DDS");
        }
        ref.buildingLit = sectorDataDir.resolve("LIGHTMAP/BUILDINGLIGHTMAPDATA.LIT");
        ref.objectLit = sectorDataDir.resolve("LIGHTMAP/OBJECTLIGHTMAPDATA.LIT");
        return ref;
    }

    /** Returns {@code [cellY, cellX]} parsed from a sector name like {@code 30_31}. */
    public static int[] parseSectorCoords(String sectorName) {
        int split = sectorName.indexOf('_');
        if (split <= 0 || split >= sectorName.length() - 1) {
            return null;
        }
        try {
            return new int[] {
                Integer.parseInt(sectorName.substring(0, split)),
                Integer.parseInt(sectorName.substring(split + 1))
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Counts how many {@code sizeX}×{@code sizeY} contiguous regions exist in the source map. */
    public static int countValidRegions(List<SectorRef> sectors, int sizeX, int sizeY) {
        return listValidRegionOrigins(sectors, sizeX, sizeY).size();
    }

    /** Origins {@code [originCellY, originCellX]} where a full region fits. */
    public static List<int[]> listValidRegionOrigins(List<SectorRef> sectors, int sizeX, int sizeY) {
        java.util.Map<Long, SectorRef> grid = buildSectorGrid(sectors);
        if (grid.isEmpty()) {
            return List.of();
        }

        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        for (SectorRef sector : sectors) {
            minY = Math.min(minY, sector.cellY);
            maxY = Math.max(maxY, sector.cellY);
            minX = Math.min(minX, sector.cellX);
            maxX = Math.max(maxX, sector.cellX);
        }

        List<int[]> origins = new ArrayList<>();
        for (int originY = minY; originY <= maxY - sizeY + 1; originY++) {
            for (int originX = minX; originX <= maxX - sizeX + 1; originX++) {
                if (regionComplete(grid, originY, originX, sizeX, sizeY)) {
                    origins.add(new int[] { originY, originX });
                }
            }
        }
        return origins;
    }

    public static java.util.Map<Long, SectorRef> buildSectorGrid(List<SectorRef> sectors) {
        java.util.Map<Long, SectorRef> grid = new java.util.HashMap<>();
        for (SectorRef sector : sectors) {
            grid.put(packCoords(sector.cellY, sector.cellX), sector);
        }
        return grid;
    }

    public static long packCoords(int cellY, int cellX) {
        return ((long) cellY << 32) | (cellX & 0xffffffffL);
    }

    private static boolean regionComplete(java.util.Map<Long, SectorRef> grid,
                                          int originY, int originX, int sizeX, int sizeY) {
        for (int dy = 0; dy < sizeY; dy++) {
            for (int dx = 0; dx < sizeX; dx++) {
                if (!grid.containsKey(packCoords(originY + dy, originX + dx))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static Path resolveSibling(Path himPath, String extension) {
        String name = himPath.getFileName().toString();
        String base = name.substring(0, name.length() - 4);
        return himPath.getParent().resolve(base + extension);
    }
}
