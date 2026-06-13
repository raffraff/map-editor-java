package com.rose.mapeditor.map;

import com.rose.mapeditor.GameData;
import com.rose.mapeditor.data.Stb;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Builds new maps from existing terrain.
 * <p>
 * {@link GenerationMode#REGION} copies a contiguous crop from the source map (recommended).
 * {@link GenerationMode#MOSAIC} mixes random sectors with edge-aligned height warping and tile seam stitching.
 */
public final class ProceduralMapGenerator {

    public enum GenerationMode {
        /** Copies a contiguous region from the source map - terrain, tiles, and lighting stay coherent. */
        REGION,
        /** Random sector collage with normalized heights and blended seams (experimental). */
        MOSAIC
    }

    /** @deprecated Use {@link GenerationMode}. */
    @Deprecated
    public enum LayoutMode {
        RANDOM,
        SHUFFLED
    }

    public static final class Request {
        public String name;
        public String mapFolder;
        public String planetName;
        public int planetId = 1;
        public int sizeX = 2;
        public int sizeY = 2;
        public int mapId;
        public int skyId = 1;
        public String zoneFileName;
        public String decorationPath;
        public String constructionPath;
        public int primarySourceMapId;
        public List<Integer> sourceMapIds = new ArrayList<>();
        public long seed = System.currentTimeMillis();
        public GenerationMode generationMode = GenerationMode.REGION;
        /** Used by mosaic mode - blends heights across sector borders. */
        public boolean smoothSeams = true;
        public boolean copyLightmaps = true;
        public boolean copyWalkability = true;
        /** Places decorations on generated maps (copy in region mode, scatter in mosaic). */
        public boolean placeObjects = true;
        /** Random decorations/constructions placed per sector in mosaic mode. */
        public int objectsPerSector = 6;
        public boolean includeConstructions = true;

        /** @deprecated Use {@link #generationMode}. */
        @Deprecated
        public LayoutMode layoutMode = LayoutMode.SHUFFLED;
    }

    private ProceduralMapGenerator() {
    }

    public static Request defaultRequest(GameData data, int sourceMapId) throws IOException {
        FlatMapCreator.Request flat = FlatMapCreator.defaultRequest(data);
        Request request = new Request();
        request.mapId = flat.mapId;
        request.planetName = flat.planetName;
        request.planetId = flat.planetId;
        request.zoneFileName = flat.zoneFileName;
        request.skyId = flat.skyId;
        request.primarySourceMapId = sourceMapId;
        request.sourceMapIds.add(sourceMapId);
        request.generationMode = GenerationMode.REGION;
        request.copyLightmaps = true;
        request.placeObjects = true;
        request.objectsPerSector = 6;

        if (data.stbs.isEmpty()) {
            data.loadCoreTables();
        }
        Stb listZone = data.stbs.get("LIST_ZONE");
        request.decorationPath = listZone.cell(sourceMapId, 12);
        request.constructionPath = listZone.cell(sourceMapId, 13);
        return request;
    }

    public static FlatMapCreator.Result generate(GameData data, Request request) throws IOException {
        validate(data, request);

        if (data.stbs.isEmpty()) {
            data.loadCoreTables();
        }

        Path mapRoot = MapCreationSupport.resolveMapRoot(data, toFlatRequest(request));
        MapCreationSupport.ensureMapRootAvailable(mapRoot);
        Files.createDirectories(mapRoot);

        List<MapSectorCatalog.SectorRef> pool = MapSectorCatalog.listSectorsForMaps(data, request.sourceMapIds);
        if (pool.isEmpty()) {
            throw new IOException("No terrain sectors found in selected source map(s).");
        }

        Stb listZone = data.stbs.get("LIST_ZONE");
        String primaryZonePath = listZone.cell(request.primarySourceMapId, 2);
        Zone zone = cloneZoneFromSource(data.resolve(primaryZonePath));
        Path lightmapTemplate = findLightmapTemplate(data);

        Random random = new Random(request.seed);
        List<MapSectorCatalog.SectorRef> picks = pickSectors(pool, request, random);

        ProceduralObjectPopulator.ObjectPool objectPool = null;
        if (request.placeObjects && request.generationMode == GenerationMode.MOSAIC) {
            objectPool = ProceduralObjectPopulator.buildPool(data, pool,
                request.decorationPath, request.constructionPath);
        }

        Map<String, Him> outputHims = new HashMap<>();
        Map<String, Path> outputHimPaths = new HashMap<>();
        Map<String, Til> outputTils = new HashMap<>();
        Map<String, Path> outputTilPaths = new HashMap<>();

        int pickIndex = 0;
        for (int gy = 0; gy < request.sizeY; gy++) {
            for (int gx = 0; gx < request.sizeX; gx++) {
                int cellY = gy + 30;
                int cellX = gx + 30;
                String sectorName = cellY + "_" + cellX;
                MapSectorCatalog.SectorRef source = picks.get(pickIndex++);

                copySectorTerrain(mapRoot, sectorName, source, request, lightmapTemplate);

                Path destHim = mapRoot.resolve(sectorName + ".HIM");
                Path destTil = mapRoot.resolve(sectorName + ".TIL");
                Him him = new Him(destHim.toString());
                Til til = new Til(destTil.toString());
                outputHims.put(sectorName, him);
                outputHimPaths.put(sectorName, destHim);
                outputTils.put(sectorName, til);
                outputTilPaths.put(sectorName, destTil);
            }
        }

        if (request.generationMode == GenerationMode.MOSAIC) {
            MosaicTerrainStitcher.stitch(outputHims, outputTils, request.sizeX, request.sizeY,
                request.smoothSeams);
            MosaicTerrainStitcher.saveAll(outputHims, outputHimPaths, outputTils, outputTilPaths);
            TerrainLightmapBaker.bakeSectors(outputHims, mapRoot);
        } else if (!request.copyLightmaps) {
            TerrainLightmapBaker.bakeSectors(outputHims, mapRoot);
        }

        pickIndex = 0;
        Map<String, Ifo> outputIfos = new HashMap<>();
        for (int gy = 0; gy < request.sizeY; gy++) {
            for (int gx = 0; gx < request.sizeX; gx++) {
                int cellY = gy + 30;
                int cellX = gx + 30;
                String sectorName = cellY + "_" + cellX;
                MapSectorCatalog.SectorRef source = picks.get(pickIndex++);

                Ifo ifo = Ifo.createEmptySector(cellY, cellX, sectorName);
                if (request.placeObjects) {
                    Him him = outputHims.get(sectorName);
                    if (request.generationMode == GenerationMode.REGION) {
                        ProceduralObjectPopulator.copyFromSource(source, cellY, cellX, ifo);
                    } else if (objectPool != null) {
                        ProceduralObjectPopulator.scatter(random, ifo, him, cellY, cellX,
                            request.objectsPerSector, objectPool, request.includeConstructions);
                    }
                }
                outputIfos.put(sectorName, ifo);
                ifo.save(mapRoot.resolve(sectorName + ".IFO").toString());
            }
        }

        if (request.placeObjects) {
            ObjectLightmapBaker.bakeSectors(data, mapRoot, outputIfos,
                request.decorationPath, request.constructionPath);
        }

        String zoneFileName = MapCreationSupport.normalizeZoneFileName(request.zoneFileName);
        zone.save(mapRoot.resolve(zoneFileName).toString());

        FlatMapCreator.Request flatRequest = toFlatRequest(request);
        flatRequest.tileTemplatePath = primaryZonePath;
        MapCreationSupport.registerNewMap(data, flatRequest, mapRoot, zoneFileName);
        return MapCreationSupport.buildResult(data, flatRequest, mapRoot, zoneFileName);
    }

    private static List<MapSectorCatalog.SectorRef> pickSectors(List<MapSectorCatalog.SectorRef> pool,
                                                                Request request, Random random) throws IOException {
        if (request.generationMode == GenerationMode.REGION) {
            return pickContiguousRegion(pool, request.sizeX, request.sizeY, random);
        }
        return pickMosaicSectors(pool, request.sizeX, request.sizeY, random);
    }

    private static List<MapSectorCatalog.SectorRef> pickContiguousRegion(List<MapSectorCatalog.SectorRef> pool,
                                                                         int sizeX, int sizeY,
                                                                         Random random) throws IOException {
        List<int[]> origins = MapSectorCatalog.listValidRegionOrigins(pool, sizeX, sizeY);
        if (origins.isEmpty()) {
            throw new IOException(String.format(Locale.ROOT,
                "Source map has no contiguous %d×%d region of sectors. Try a smaller size or use Mosaic mode.",
                sizeX, sizeY));
        }

        int[] origin = origins.get(random.nextInt(origins.size()));
        Map<Long, MapSectorCatalog.SectorRef> grid = MapSectorCatalog.buildSectorGrid(pool);

        List<MapSectorCatalog.SectorRef> picks = new ArrayList<>(sizeX * sizeY);
        for (int dy = 0; dy < sizeY; dy++) {
            for (int dx = 0; dx < sizeX; dx++) {
                long key = MapSectorCatalog.packCoords(origin[0] + dy, origin[1] + dx);
                MapSectorCatalog.SectorRef sector = grid.get(key);
                if (sector == null) {
                    throw new IOException("Internal error: missing sector at " + (origin[0] + dy)
                        + "_" + (origin[1] + dx));
                }
                picks.add(sector);
            }
        }
        return picks;
    }

    private static List<MapSectorCatalog.SectorRef> pickMosaicSectors(List<MapSectorCatalog.SectorRef> pool,
                                                                      int sizeX, int sizeY, Random random)
        throws IOException {
        int count = sizeX * sizeY;
        if (count <= 1) {
            List<MapSectorCatalog.SectorRef> single = new ArrayList<>(1);
            single.add(pool.get(random.nextInt(pool.size())));
            return single;
        }
        return pickCompatibleMosaicSectors(pool, sizeX, sizeY, random);
    }

    /**
     * Places sectors in row-major order, preferring candidates whose edge heights match
     * already placed west/north neighbors so cross-fade stitching has less to correct.
     */
    private static List<MapSectorCatalog.SectorRef> pickCompatibleMosaicSectors(
        List<MapSectorCatalog.SectorRef> pool, int sizeX, int sizeY, Random random) throws IOException {

        Map<Path, float[][]> edgeCache = new HashMap<>();
        MapSectorCatalog.SectorRef[][] placed = new MapSectorCatalog.SectorRef[sizeY][sizeX];
        List<MapSectorCatalog.SectorRef> picks = new ArrayList<>(sizeX * sizeY);
        List<MapSectorCatalog.SectorRef> available = new ArrayList<>(pool);

        for (int gy = 0; gy < sizeY; gy++) {
            for (int gx = 0; gx < sizeX; gx++) {
                float[] westEdge = gy > 0 ? loadEastEdge(placed[gy - 1][gx], edgeCache) : null;
                float[] northEdge = gx > 0 ? loadSouthEdge(placed[gy][gx - 1], edgeCache) : null;

                MapSectorCatalog.SectorRef pick;
                if (available.isEmpty()) {
                    pick = pool.get(random.nextInt(pool.size()));
                } else {
                    pick = chooseBestMosaicCandidate(available, westEdge, northEdge, random);
                    available.remove(pick);
                }

                placed[gy][gx] = pick;
                picks.add(pick);
            }
        }
        return picks;
    }

    private static MapSectorCatalog.SectorRef chooseBestMosaicCandidate(
        List<MapSectorCatalog.SectorRef> candidates, float[] westEdge, float[] northEdge,
        Random random) throws IOException {

        MapSectorCatalog.SectorRef best = null;
        float bestScore = Float.MAX_VALUE;
        int tieCount = 0;

        for (MapSectorCatalog.SectorRef candidate : candidates) {
            float score = mosaicEdgeMismatch(candidate, westEdge, northEdge);
            if (score < bestScore - 0.001f) {
                bestScore = score;
                best = candidate;
                tieCount = 1;
            } else if (Math.abs(score - bestScore) <= 0.001f) {
                tieCount++;
                if (random.nextInt(tieCount) == 0) {
                    best = candidate;
                }
            }
        }
        return best != null ? best : candidates.get(random.nextInt(candidates.size()));
    }

    private static float mosaicEdgeMismatch(MapSectorCatalog.SectorRef candidate,
                                            float[] westEdge, float[] northEdge)
        throws IOException {
        Him him = new Him(candidate.himFile.toString());
        float score = 0f;
        if (westEdge != null) {
            for (int row = 0; row < VERTS_PER_SECTOR; row++) {
                score += Math.abs(westEdge[row] - him.position[row][0]);
            }
        }
        if (northEdge != null) {
            for (int col = 0; col < VERTS_PER_SECTOR; col++) {
                score += Math.abs(northEdge[col] - him.position[0][col]);
            }
        }
        return score;
    }

    private static float[] loadEastEdge(MapSectorCatalog.SectorRef sector,
                                          Map<Path, float[][]> cache) throws IOException {
        Him him = loadCachedHim(sector.himFile, cache);
        float[] edge = new float[VERTS_PER_SECTOR];
        for (int row = 0; row < VERTS_PER_SECTOR; row++) {
            edge[row] = him.position[row][QUAD_CELLS_PER_SECTOR];
        }
        return edge;
    }

    private static float[] loadSouthEdge(MapSectorCatalog.SectorRef sector,
                                           Map<Path, float[][]> cache) throws IOException {
        Him him = loadCachedHim(sector.himFile, cache);
        float[] edge = new float[VERTS_PER_SECTOR];
        for (int col = 0; col < VERTS_PER_SECTOR; col++) {
            edge[col] = him.position[QUAD_CELLS_PER_SECTOR][col];
        }
        return edge;
    }

    private static Him loadCachedHim(Path himFile, Map<Path, float[][]> cache) throws IOException {
        float[][] position = cache.get(himFile);
        if (position == null) {
            Him loaded = new Him(himFile.toString());
            position = loaded.position;
            cache.put(himFile, position);
        }
        Him him = new Him();
        him.position = position;
        return him;
    }

    private static final int VERTS_PER_SECTOR = 65;
    private static final int QUAD_CELLS_PER_SECTOR = 64;

    private static void validate(GameData data, Request request) throws IOException {
        if (request.name == null || request.name.trim().isEmpty()) {
            throw new IOException("Map name is required.");
        }
        if (request.mapFolder == null || request.mapFolder.trim().isEmpty()) {
            throw new IOException("Map folder name is required.");
        }
        if (request.sizeX < 1 || request.sizeY < 1) {
            throw new IOException("Map size must be at least 1x1.");
        }
        if (request.sourceMapIds == null || request.sourceMapIds.isEmpty()) {
            throw new IOException("Select at least one source map.");
        }
        if (!request.sourceMapIds.contains(request.primarySourceMapId)) {
            request.sourceMapIds.add(0, request.primarySourceMapId);
        }

        if (data.stbs.isEmpty()) {
            data.loadCoreTables();
        }
        Stb listZone = data.stbs.get("LIST_ZONE");
        String primaryZone = listZone.cell(request.primarySourceMapId, 2);
        for (int mapId : request.sourceMapIds) {
            String zonePath = listZone.cell(mapId, 2);
            if (zonePath == null || zonePath.isBlank()) {
                throw new IOException("Source map " + mapId + " has no zone path.");
            }
            if (!zonePath.equalsIgnoreCase(primaryZone)) {
                throw new IOException(
                    "Source map " + mapId + " uses a different .ZON than the primary source.");
            }
        }
    }

    private static FlatMapCreator.Request toFlatRequest(Request request) {
        FlatMapCreator.Request flat = new FlatMapCreator.Request();
        flat.name = request.name;
        flat.mapFolder = request.mapFolder;
        flat.planetName = request.planetName;
        flat.planetId = request.planetId;
        flat.sizeX = request.sizeX;
        flat.sizeY = request.sizeY;
        flat.mapId = request.mapId;
        flat.skyId = request.skyId;
        flat.zoneFileName = request.zoneFileName;
        flat.decorationPath = request.decorationPath;
        flat.constructionPath = request.constructionPath;
        return flat;
    }

    private static void copySectorTerrain(Path mapRoot, String sectorName,
                                          MapSectorCatalog.SectorRef source, Request request,
                                          Path lightmapTemplate) throws IOException {
        Files.copy(source.himFile, mapRoot.resolve(sectorName + ".HIM"), StandardCopyOption.REPLACE_EXISTING);
        Files.copy(source.tilFile, mapRoot.resolve(sectorName + ".TIL"), StandardCopyOption.REPLACE_EXISTING);

        if (request.copyWalkability) {
            Files.copy(source.movFile, mapRoot.resolve(sectorName + ".MOV"), StandardCopyOption.REPLACE_EXISTING);
        } else {
            Mov mov = new Mov();
            mov.isWalkable = new byte[32][32];
            mov.save(mapRoot.resolve(sectorName + ".MOV").toString());
        }

        boolean useSourceLightmap = request.copyLightmaps
            && (request.generationMode == GenerationMode.REGION || Files.isRegularFile(source.planeLightmap));
        Path lightmapSource = useSourceLightmap && Files.isRegularFile(source.planeLightmap)
            ? source.planeLightmap : null;
        MapCreationSupport.writeSectorLightmapTree(mapRoot, sectorName, lightmapSource, lightmapTemplate);

        if (useSourceLightmap && !request.placeObjects) {
            Path sectorDataDir = mapRoot.resolve(sectorName);
            Path sourceSectorDir = source.mapFolder.resolve(source.sectorName);
            ObjectLightmapBaker.copyLightmapFolder(sourceSectorDir, sectorDataDir);
        }
    }

    private static void copyIfExists(Path source, Path dest) throws IOException {
        if (source != null && Files.isRegularFile(source)) {
            Files.createDirectories(dest.getParent());
            Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Zone cloneZoneFromSource(Path zonePath) throws IOException {
        Zone source = new Zone(zonePath.toString());
        Zone zone = new Zone();
        zone.blocks = new ArrayList<>(5);
        for (Zone.BlockType type : Zone.BlockType.values()) {
            Zone.Block block = new Zone.Block();
            block.type = type;
            zone.blocks.add(block);
        }

        zone.zoneInfo = new Zone.Block0();
        zone.zoneInfo.zoneType = source.zoneInfo.zoneType;
        zone.zoneInfo.zoneWidth = source.zoneInfo.zoneWidth;
        zone.zoneInfo.zoneHeight = source.zoneInfo.zoneHeight;
        zone.zoneInfo.gridCount = source.zoneInfo.gridCount;
        zone.zoneInfo.gridSize = source.zoneInfo.gridSize;
        zone.zoneInfo.xCount = source.zoneInfo.xCount;
        zone.zoneInfo.yCount = source.zoneInfo.yCount;
        zone.zoneInfo.zoneParts = new Zone.ZonePart[source.zoneInfo.zoneWidth][source.zoneInfo.zoneHeight];
        for (int x = 0; x < source.zoneInfo.zoneWidth; x++) {
            for (int y = 0; y < source.zoneInfo.zoneHeight; y++) {
                Zone.ZonePart part = new Zone.ZonePart();
                part.useMap = 0;
                zone.zoneInfo.zoneParts[x][y] = part;
            }
        }

        zone.spawnPoints = new ArrayList<>();
        Zone.SpawnPoint start = new Zone.SpawnPoint();
        start.name = "start";
        start.position.set(4930.0f, 5470.0f, 0.0f);
        zone.spawnPoints.add(start);
        Zone.SpawnPoint restore = new Zone.SpawnPoint();
        restore.name = "restore";
        restore.position.set(4910.0f, 5500.0f, 0.0f);
        zone.spawnPoints.add(restore);

        zone.textures = new ArrayList<>();
        for (Zone.TileTexture texture : source.textures) {
            Zone.TileTexture copy = new Zone.TileTexture();
            copy.path = texture.path;
            zone.textures.add(copy);
        }

        zone.tiles = new ArrayList<>();
        for (Zone.Tile tile : source.tiles) {
            Zone.Tile copy = new Zone.Tile();
            copy.baseId1 = tile.baseId1;
            copy.baseId2 = tile.baseId2;
            copy.offset1 = tile.offset1;
            copy.offset2 = tile.offset2;
            copy.blending = tile.blending;
            copy.rotation = tile.rotation;
            copy.tileType = tile.tileType;
            zone.tiles.add(copy);
        }

        zone.economyInfo = new Zone.Economy();
        return zone;
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
}
