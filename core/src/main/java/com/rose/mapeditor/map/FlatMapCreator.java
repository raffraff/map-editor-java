package com.rose.mapeditor.map;

import com.badlogic.gdx.math.Vector3;
import com.rose.mapeditor.GameData;
import com.rose.mapeditor.data.Stb;
import com.rose.mapeditor.data.Stl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Creates a new Rose map with flat terrain sectors. */
public final class FlatMapCreator {

    public static final class Request {
        public String name;
        public String mapFolder;
        public String planetName;
        public int planetId = 1;
        public int sizeX = 1;
        public int sizeY = 1;
        public int mapId;
        public int skyId = 1;
        public String zoneFileName;
        public String tileTemplatePath;
        public String decorationPath;
        public String constructionPath;
    }

    public static final class Result {
        public int mapId;
        public String mapFolderRelative;
        public String zoneRelativePath;
    }

    private FlatMapCreator() {
    }

    public static List<Integer> listAvailableMapIds(GameData data) throws IOException {
        ensureTables(data);
        Stb listZone = data.stbs.get("LIST_ZONE");
        List<Integer> ids = new ArrayList<>();
        for (int id = 1; id < listZone.cells.size(); id++) {
            if (!MapCatalog.isValidMap(listZone, id)) {
                ids.add(id);
            }
        }
        ids.add(listZone.cells.size());
        return ids;
    }

    public static Request defaultRequest(GameData data) throws IOException {
        ensureTables(data);
        Request request = new Request();
        request.mapId = listAvailableMapIds(data).get(0);
        request.planetName = resolveDefaultPlanet(data);
        request.planetId = 1;
        request.zoneFileName = "NEWMAP.ZON";
        request.tileTemplatePath = defaultTileTemplate(data);
        request.decorationPath = defaultFromListZone(data, 12);
        request.constructionPath = defaultFromListZone(data, 13);
        request.skyId = defaultSkyId(data);
        return request;
    }

    public static Result create(GameData data, Request request) throws IOException {
        validate(request);

        ensureTables(data);
        Path mapRoot = MapCreationSupport.resolveMapRoot(data, request);
        MapCreationSupport.ensureMapRootAvailable(mapRoot);

        Zone zone = buildZone(data.resolve(request.tileTemplatePath));
        Til defaultTil = createDefaultTil();
        Mov emptyMov = new Mov();
        emptyMov.isWalkable = new byte[32][32];
        Him flatHim = createFlatHim();

        for (int y = 0; y < request.sizeY; y++) {
            for (int x = 0; x < request.sizeX; x++) {
                int cellY = y + 30;
                int cellX = x + 30;
                String sectorName = cellY + "_" + cellX;

                MapCreationSupport.writeSectorLightmapTree(mapRoot, sectorName, null, null, false);

                MapCreationSupport.writeEmptySectorIfo(mapRoot, cellY, cellX, sectorName);

                defaultTil.save(mapRoot.resolve(sectorName + ".TIL").toString());
                emptyMov.save(mapRoot.resolve(sectorName + ".MOV").toString());

                flatHim.filePath = mapRoot.resolve(sectorName + ".HIM").toString();
                flatHim.save();
                TerrainLightmapBaker.bakeToFile(flatHim,
                    PlaneLightmapPaths.resolvePlaneLightmapPath(mapRoot, cellY, cellX));
            }
        }

        String zoneFileName = MapCreationSupport.normalizeZoneFileName(request.zoneFileName);
        zone.save(mapRoot.resolve(zoneFileName).toString());

        MapCreationSupport.registerNewMap(data, request, mapRoot, zoneFileName);

        return MapCreationSupport.buildResult(data, request, mapRoot, zoneFileName);
    }

    private static void validate(Request request) throws IOException {
        if (request.name == null || request.name.trim().isEmpty()) {
            throw new IOException("Map name is required.");
        }
        if (request.mapFolder == null || request.mapFolder.trim().isEmpty()) {
            throw new IOException("Map folder name is required.");
        }
        if (request.planetName == null || request.planetName.trim().isEmpty()) {
            throw new IOException("Planet is required.");
        }
        if (request.sizeX < 1 || request.sizeY < 1) {
            throw new IOException("Map size must be at least 1x1.");
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

    private static Zone buildZone(Path tileTemplate) throws IOException {
        Zone source = new Zone(tileTemplate.toString());
        Zone zone = new Zone();
        zone.blocks = new ArrayList<>(5);
        for (Zone.BlockType type : Zone.BlockType.values()) {
            Zone.Block block = new Zone.Block();
            block.type = type;
            zone.blocks.add(block);
        }

        zone.zoneInfo = new Zone.Block0();
        zone.zoneInfo.zoneType = 0;
        zone.zoneInfo.zoneWidth = 64;
        zone.zoneInfo.zoneHeight = 64;
        zone.zoneInfo.gridCount = 4;
        zone.zoneInfo.gridSize = 250;
        zone.zoneInfo.xCount = 30;
        zone.zoneInfo.yCount = 30;
        zone.zoneInfo.zoneParts = new Zone.ZonePart[64][64];
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                zone.zoneInfo.zoneParts[x][y] = new Zone.ZonePart();
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

    private static Til createDefaultTil() {
        Til til = new Til();
        til.tiles = new Til.Tile[16][16];
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                Til.Tile tile = new Til.Tile();
                tile.brushId = 1;
                tile.tileIndex = 15;
                tile.tileSetNumber = 1;
                tile.tileId = 1;
                til.tiles[y][x] = tile;
            }
        }
        return til;
    }

    private static Him createFlatHim() {
        Him him = new Him();
        him.position = new float[65][65];
        return him;
    }

    private static String defaultTileTemplate(GameData data) throws IOException {
        Path junon = data.resolve("3Ddata/TERRAIN/TILES/JUNON.ZON");
        if (Files.isRegularFile(junon)) {
            return "3Ddata/TERRAIN/TILES/JUNON.ZON";
        }
        List<MapCatalog.Entry> maps = MapCatalog.listValidMaps(data);
        if (!maps.isEmpty()) {
            return maps.get(0).zonePath;
        }
        return "3Ddata/TERRAIN/TILES/JUNON.ZON";
    }

    private static String defaultFromListZone(GameData data, int column) throws IOException {
        List<MapCatalog.Entry> maps = MapCatalog.listValidMaps(data);
        if (maps.isEmpty()) {
            return "";
        }
        return data.stbs.get("LIST_ZONE").cell(maps.get(0).id, column);
    }

    private static int defaultSkyId(GameData data) {
        Stb listSky = data.stbs.get("LIST_SKY");
        if (listSky == null) {
            return 1;
        }
        for (int i = 1; i < listSky.cells.size(); i++) {
            String path = listSky.cell(i, 1);
            if (path != null && !path.trim().isEmpty()) {
                return i;
            }
        }
        return 1;
    }

    private static String resolveDefaultPlanet(GameData data) throws IOException {
        loadPlanetTable(data);
        Stl planets = data.stls.get("STR_PLANET");
        if (planets != null && planets.rows.size() > 1 && planets.rows.get(1).size() > 1) {
            String text = planets.rows.get(1).get(1).text;
            if (text != null && !text.isBlank()) {
                return text.trim();
            }
        }
        return "JUNON";
    }

    public static List<String> listPlanetNames(GameData data) throws IOException {
        loadPlanetTable(data);
        List<String> names = new ArrayList<>();
        Stl planets = data.stls.get("STR_PLANET");
        if (planets == null || planets.rows.size() <= 1) {
            names.add("JUNON");
            return names;
        }
        List<Stl.Row> english = planets.rows.get(1);
        for (int i = 1; i < english.size(); i++) {
            String text = english.get(i).text;
            if (text != null && !text.isBlank()) {
                names.add(text.trim());
            }
        }
        if (names.isEmpty()) {
            names.add("JUNON");
        }
        return names;
    }

    public static List<Integer> listSkyIds(GameData data) throws IOException {
        ensureTables(data);
        List<Integer> ids = new ArrayList<>();
        Stb listSky = data.stbs.get("LIST_SKY");
        for (int i = 1; i < listSky.cells.size(); i++) {
            String path = listSky.cell(i, 1);
            if (path != null && !path.trim().isEmpty()) {
                ids.add(i);
            }
        }
        if (ids.isEmpty()) {
            ids.add(1);
        }
        return ids;
    }

    private static void ensureTables(GameData data) throws IOException {
        if (data.stbs.isEmpty()) {
            data.loadCoreTables();
        }
    }

    private static void loadPlanetTable(GameData data) throws IOException {
        ensureTables(data);
        if (!data.stls.containsKey("STR_PLANET")) {
            data.stls.put("STR_PLANET", new Stl(data.resolve("3DDATA/STB/STR_PLANET.STL").toString()));
        }
    }
}
