package com.rose.mapeditor.map.flyff;

import com.rose.mapeditor.GameData;
import com.rose.mapeditor.map.FlatMapCreator;
import com.rose.mapeditor.map.Him;
import com.rose.mapeditor.map.Ifo;
import com.rose.mapeditor.map.MapCatalog;
import com.rose.mapeditor.map.MapCreationSupport;
import com.rose.mapeditor.map.MosaicTerrainStitcher;
import com.rose.mapeditor.map.Mov;
import com.rose.mapeditor.map.TerrainLightmapBaker;
import com.rose.mapeditor.map.Til;
import com.rose.mapeditor.map.Zone;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Converts Flyff world bundles (.wld + .lnd) into Rose Online map folders.
 * Terrain heights, walkability, and texture layers are imported.
 */
public final class FlyffMapImporter {

    private static final int ROSE_SAMPLES = 65;
    private static final int ROSE_CELLS = 64;
    private static final int ROSE_MOV_GRID = 32;
    private static final int SECTOR_ORIGIN = 30;

    public static final class Request {
        public String flyffWldPath;
        /** Client folder containing {@code World/Texture*} and {@code Masquerade.prj}. */
        public String flyffRootPath;
        /** Optional explicit terrain registry .inc path; auto-detected when empty. */
        public String flyffTerrainIncPath;
        public String name;
        public String mapFolder;
        public String planetName;
        public int planetId = 1;
        public int mapId;
        public int skyId = 1;
        public String zoneFileName;
        public String tileTemplatePath;
        public String decorationPath;
        public String constructionPath;
        /** Multiplier applied to Flyff height samples before writing Rose HIM. */
        public float heightScale = 1.0f;
    }

    public static final class Result {
        public int mapId;
        public String mapFolderRelative;
        public String zoneRelativePath;
        public int flyffWidth;
        public int flyffHeight;
        public int roseSectorWidth;
        public int roseSectorHeight;
        public int sectorCount;
        public int importedTextureCount;
    }

    private FlyffMapImporter() {
    }

    public static Result importTerrain(GameData data, Request request) throws IOException {
        validate(request);

        Path wldPath = Path.of(request.flyffWldPath.trim());
        FlyffWldReader.WorldHeader header = FlyffWldReader.read(wldPath);
        Path flyffRoot = resolveFlyffRoot(wldPath, request.flyffRootPath);
        Path terrainInc = resolveTerrainInc(flyffRoot, request.flyffTerrainIncPath);
        FlyffTerrainRegistry registry = FlyffTerrainRegistry.load(terrainInc);

        int layoutSizeX = header.width * 2;
        int layoutSizeY = header.height * 2;

        Map<String, FlyffLndReader.LandscapeTile> flyffTiles = loadAllTiles(header);
        FlyffRoseTextureCatalog textureCatalog = FlyffRoseTextureCatalog.create(
            data,
            data.resolve(request.tileTemplatePath),
            flyffRoot,
            registry
        );

        Map<String, Him> hims = new HashMap<>();
        Map<String, Til> tils = new HashMap<>();

        for (int tileRow = 0; tileRow < header.height; tileRow++) {
            for (int tileCol = 0; tileCol < header.width; tileCol++) {
                String tileKey = tileKey(tileCol, tileRow);
                FlyffLndReader.LandscapeTile flyffTile = flyffTiles.get(tileKey);

                for (int subGy = 0; subGy < 2; subGy++) {
                    for (int subGx = 0; subGx < 2; subGx++) {
                        int layoutGy = tileRow * 2 + subGy;
                        int layoutGx = tileCol * 2 + subGx;
                        String sectorName = sectorName(layoutGy, layoutGx);

                        Him him = buildSectorHim(flyffTile, subGx, subGy, request.heightScale);
                        Til til = FlyffRoseTextureCatalog.buildSectorTil(flyffTile, subGx, subGy, textureCatalog);
                        hims.put(sectorName, him);
                        tils.put(sectorName, til);
                    }
                }
            }
        }

        MosaicTerrainStitcher.stitchImportedSectors(hims, tils, layoutSizeX, layoutSizeY);

        data.loadCoreTables();
        FlatMapCreator.Request flatRequest = toFlatRequest(request);
        flatRequest.sizeX = layoutSizeX;
        flatRequest.sizeY = layoutSizeY;

        Path mapRoot = MapCreationSupport.resolveMapRoot(data, flatRequest);
        MapCreationSupport.ensureMapRootAvailable(mapRoot);

        Path lightmapTemplate = findLightmapTemplate(data);
        Zone zone = textureCatalog.zone();

        for (int layoutGy = 0; layoutGy < layoutSizeY; layoutGy++) {
            for (int layoutGx = 0; layoutGx < layoutSizeX; layoutGx++) {
                int cellY = layoutGy + SECTOR_ORIGIN;
                int cellX = layoutGx + SECTOR_ORIGIN;
                String sectorName = sectorName(layoutGy, layoutGx);

                Him him = hims.get(sectorName);
                Til til = tils.get(sectorName);

                MapCreationSupport.writeSectorLightmapTree(mapRoot, sectorName, null, lightmapTemplate);

                int tileRow = layoutGy / 2;
                int tileCol = layoutGx / 2;
                int subGy = layoutGy % 2;
                int subGx = layoutGx % 2;
                FlyffLndReader.LandscapeTile flyffTile = flyffTiles.get(tileKey(tileCol, tileRow));
                Mov mov = buildSectorMov(flyffTile, subGx, subGy);

                Ifo ifo = Ifo.createEmptySector(cellY, cellX, sectorName);
                ifo.save(mapRoot.resolve(sectorName + ".IFO").toString());

                til.save(mapRoot.resolve(sectorName + ".TIL").toString());
                mov.save(mapRoot.resolve(sectorName + ".MOV").toString());

                him.filePath = mapRoot.resolve(sectorName + ".HIM").toString();
                him.save();

                TerrainLightmapBaker.bakeToFile(him,
                    mapRoot.resolve(sectorName + "/" + sectorName + "_PLANELIGHTINGMAP.DDS"));
            }
        }

        String zoneFileName = MapCreationSupport.normalizeZoneFileName(request.zoneFileName);
        zone.save(mapRoot.resolve(zoneFileName).toString());
        MapCreationSupport.registerNewMap(data, flatRequest, mapRoot, zoneFileName);

        Result result = new Result();
        result.mapId = request.mapId;
        result.mapFolderRelative = data.getGameRoot().relativize(mapRoot).toString().replace('\\', '/');
        result.zoneRelativePath = result.mapFolderRelative + "/" + zoneFileName;
        result.flyffWidth = header.width;
        result.flyffHeight = header.height;
        result.roseSectorWidth = layoutSizeX;
        result.roseSectorHeight = layoutSizeY;
        result.sectorCount = layoutSizeX * layoutSizeY;
        result.importedTextureCount = textureCatalog.importedTextureCount();
        return result;
    }

    private static Path resolveFlyffRoot(Path wldPath, String configuredRoot) throws IOException {
        if (configuredRoot != null && !configuredRoot.trim().isEmpty()) {
            Path root = Path.of(configuredRoot.trim());
            if (!Files.isDirectory(root)) {
                throw new IOException("Flyff root folder not found: " + root);
            }
            return root;
        }

        Path parent = wldPath.getParent();
        if (parent == null) {
            throw new IOException("Cannot infer Flyff root from .wld path: " + wldPath);
        }
        if ("World".equalsIgnoreCase(parent.getFileName().toString()) && parent.getParent() != null) {
            return parent.getParent();
        }
        return parent;
    }

    private static Path resolveTerrainInc(Path flyffRoot, String configuredInc) throws IOException {
        if (configuredInc != null && !configuredInc.trim().isEmpty()) {
            Path path = Path.of(configuredInc.trim());
            if (!Files.isRegularFile(path)) {
                path = flyffRoot.resolve(configuredInc.trim().replace('\\', '/'));
            }
            if (!Files.isRegularFile(path)) {
                throw new IOException("Terrain registry not found: " + configuredInc);
            }
            return path;
        }
        return FlyffTerrainRegistry.findTerrainInc(flyffRoot);
    }

    private static Map<String, FlyffLndReader.LandscapeTile> loadAllTiles(FlyffWldReader.WorldHeader header)
        throws IOException {
        Map<String, FlyffLndReader.LandscapeTile> tiles = new HashMap<>();
        for (int tileRow = 0; tileRow < header.height; tileRow++) {
            for (int tileCol = 0; tileCol < header.width; tileCol++) {
                Path lndPath = FlyffWldReader.landscapePath(header, tileCol, tileRow);
                tiles.put(tileKey(tileCol, tileRow), FlyffLndReader.read(lndPath));
            }
        }
        return tiles;
    }

    private static String tileKey(int tileCol, int tileRow) {
        return tileCol + "," + tileRow;
    }

    private static Him buildSectorHim(FlyffLndReader.LandscapeTile flyffTile, int subGx, int subGy,
                                      float heightScale) {
        Him him = new Him();
        him.position = new float[ROSE_SAMPLES][ROSE_SAMPLES];
        for (int dx = 0; dx < ROSE_SAMPLES; dx++) {
            for (int dy = 0; dy < ROSE_SAMPLES; dy++) {
                int flyffX = subGx * ROSE_CELLS + dy;
                int flyffZ = subGy * ROSE_CELLS + dx;
                float height = flyffTile.at(flyffX, flyffZ);
                him.position[dx][dy] = normalizeHeight(height) * heightScale;
            }
        }
        return him;
    }

    private static Mov buildSectorMov(FlyffLndReader.LandscapeTile flyffTile, int subGx, int subGy) {
        Mov mov = new Mov();
        mov.isWalkable = new byte[ROSE_MOV_GRID][ROSE_MOV_GRID];
        for (int my = 0; my < ROSE_MOV_GRID; my++) {
            for (int mx = 0; mx < ROSE_MOV_GRID; mx++) {
                boolean blocked = false;
                for (int cy = 0; cy < 2 && !blocked; cy++) {
                    for (int cx = 0; cx < 2 && !blocked; cx++) {
                        int flyffX = subGx * ROSE_CELLS + mx * 2 + cx;
                        int flyffZ = subGy * ROSE_CELLS + my * 2 + cy;
                        if (flyffTile.isBlocked(flyffX, flyffZ)) {
                            blocked = true;
                        }
                    }
                }
                mov.isWalkable[my][mx] = blocked ? (byte) 0 : (byte) 1;
            }
        }
        return mov;
    }

    private static float normalizeHeight(float height) {
        if (height >= FlyffLndReader.HGT_NOWALK) {
            return 0f;
        }
        return height;
    }

    private static String sectorName(int layoutGy, int layoutGx) {
        return (layoutGy + SECTOR_ORIGIN) + "_" + (layoutGx + SECTOR_ORIGIN);
    }

    private static Path findLightmapTemplate(GameData data) throws IOException {
        for (MapCatalog.Entry entry : MapCatalog.listValidMaps(data)) {
            Path zonePath = data.resolve(entry.zonePath);
            Path mapFolder = zonePath.getParent();
            if (mapFolder == null) {
                continue;
            }
            Path candidate = mapFolder.resolve("30_30/30_30_PLANELIGHTINGMAP.DDS");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static FlatMapCreator.Request toFlatRequest(Request request) {
        FlatMapCreator.Request flat = new FlatMapCreator.Request();
        flat.name = request.name;
        flat.mapFolder = request.mapFolder;
        flat.planetName = request.planetName;
        flat.planetId = request.planetId;
        flat.mapId = request.mapId;
        flat.skyId = request.skyId;
        flat.zoneFileName = request.zoneFileName;
        flat.tileTemplatePath = request.tileTemplatePath;
        flat.decorationPath = request.decorationPath;
        flat.constructionPath = request.constructionPath;
        return flat;
    }

    private static void validate(Request request) throws IOException {
        if (request.flyffWldPath == null || request.flyffWldPath.trim().isEmpty()) {
            throw new IOException("Flyff .wld path is required.");
        }
        if (request.name == null || request.name.trim().isEmpty()) {
            throw new IOException("Map name is required.");
        }
        if (request.mapFolder == null || request.mapFolder.trim().isEmpty()) {
            throw new IOException("Map folder name is required.");
        }
        if (request.planetName == null || request.planetName.trim().isEmpty()) {
            throw new IOException("Planet is required.");
        }
        if (request.tileTemplatePath == null || request.tileTemplatePath.trim().isEmpty()) {
            throw new IOException("Tile template .ZON path is required.");
        }
        if (request.decorationPath == null || request.decorationPath.trim().isEmpty()) {
            throw new IOException("Decoration ZSC path is required.");
        }
        if (request.constructionPath == null || request.constructionPath.trim().isEmpty()) {
            throw new IOException("Construction ZSC path is required.");
        }
        if (request.zoneFileName == null || request.zoneFileName.trim().isEmpty()) {
            throw new IOException("Zone file name is required.");
        }
    }
}
