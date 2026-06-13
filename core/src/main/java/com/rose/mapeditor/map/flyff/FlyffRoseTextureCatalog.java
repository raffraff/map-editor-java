package com.rose.mapeditor.map.flyff;

import com.rose.mapeditor.GameData;
import com.rose.mapeditor.map.Til;
import com.rose.mapeditor.map.Zone;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Maps Flyff terrain layers to Rose zone textures and 2-layer blend tiles. */
public final class FlyffRoseTextureCatalog {

    private static final String ROSE_TEXTURE_FOLDER = "3Ddata/TERRAIN/TEXTURES/FLYFF_IMPORT";

    private final Zone zone;
    private final Path roseTextureRoot;
    private final Path flyffRoot;
    private final FlyffTerrainRegistry registry;
    private final FlyffTextureImageCache imageCache;
    private final int fallbackTileId;

    /** Flyff texture ID -> Rose {@link Zone.Tile} index for single-layer tiles. */
    private final Map<Integer, Integer> solidRoseTileId = new HashMap<>();
    /** Flyff texture ID -> Rose texture list index. */
    private final Map<Integer, Integer> flyffToRoseTextureIndex = new HashMap<>();
    /** Blend patch signature -> Rose {@link Zone.Tile} index. */
    private final Map<Integer, Integer> blendRoseTileId = new HashMap<>();

    private FlyffRoseTextureCatalog(Zone zone, Path roseTextureRoot, Path flyffRoot,
                                    FlyffTerrainRegistry registry, int fallbackTileId) {
        this.zone = zone;
        this.roseTextureRoot = roseTextureRoot;
        this.flyffRoot = flyffRoot;
        this.registry = registry;
        this.imageCache = new FlyffTextureImageCache(flyffRoot, registry);
        this.fallbackTileId = fallbackTileId;
    }

    public static FlyffRoseTextureCatalog create(GameData roseData, Path tileTemplate,
                                                 Path flyffRoot, FlyffTerrainRegistry registry)
        throws IOException {
        Zone template = new Zone(tileTemplate.toString());
        Zone zone = cloneZoneShell(template);
        int fallbackTileId = template.tiles.size() > 1 ? 1 : 0;
        Path roseTextureRoot = roseData.resolve(ROSE_TEXTURE_FOLDER);
        Files.createDirectories(roseTextureRoot);
        return new FlyffRoseTextureCatalog(zone, roseTextureRoot, flyffRoot, registry, fallbackTileId);
    }

    public Zone zone() {
        return zone;
    }

    public int importedTextureCount() {
        return flyffToRoseTextureIndex.size() + blendRoseTileId.size();
    }

    public static Set<Integer> collectUsedTextureIds(Map<String, FlyffLndReader.LandscapeTile> tiles) {
        Set<Integer> used = new HashSet<>();
        for (FlyffLndReader.LandscapeTile tile : tiles.values()) {
            for (FlyffLndReader.Layer layer : tile.layers) {
                used.add(layer.textureId);
            }
        }
        return used;
    }

    public static Til buildSectorTil(FlyffLndReader.LandscapeTile flyffTile, int subGx, int subGy,
                                     FlyffRoseTextureCatalog catalog) throws IOException {
        Til til = new Til();
        til.tiles = new Til.Tile[16][16];
        for (int ty = 0; ty < 16; ty++) {
            for (int tx = 0; tx < 16; tx++) {
                FlyffLayerCompositor.CellBlendPatch patch = FlyffLayerCompositor.bakeRoseCell(
                    flyffTile.layers, subGx, subGy, tx, ty, catalog.imageCache);

                Til.Tile cell = new Til.Tile();
                cell.brushId = 1;
                cell.tileIndex = 15;
                cell.tileSetNumber = 1;
                cell.tileId = catalog.resolveRoseTileId(patch);
                til.tiles[ty][tx] = cell;
            }
        }
        return til;
    }

    private int resolveRoseTileId(FlyffLayerCompositor.CellBlendPatch patch) throws IOException {
        if (!patch.blending || patch.topFlyffTextureId < 0) {
            return ensureSolidRoseTileId(patch.baseFlyffTextureId);
        }

        int signature = FlyffLayerCompositor.patchSignature(patch);
        Integer cached = blendRoseTileId.get(signature);
        if (cached != null) {
            return cached;
        }

        int baseTextureIndex = ensureRoseTextureIndex(patch.baseFlyffTextureId);
        String baseLabel = sanitizeBaseName(registry.filenameFor(patch.baseFlyffTextureId));
        String topLabel = sanitizeBaseName(registry.filenameFor(patch.topFlyffTextureId));
        String blendName = String.format(Locale.ROOT, "BLEND_%s_%s_%08X.dds",
            baseLabel, topLabel, signature);
        Path blendDds = roseTextureRoot.resolve(blendName);
        if (!Files.isRegularFile(blendDds)) {
            FlyffTgaToDds.writeBgra(blendDds, patch.topWidth(), patch.topHeight(), patch.topBgra);
        }

        int blendTextureIndex = zone.textures.size();
        Zone.TileTexture blendTexture = new Zone.TileTexture();
        blendTexture.path = ROSE_TEXTURE_FOLDER + "/" + blendName;
        zone.textures.add(blendTexture);

        Zone.Tile tile = new Zone.Tile();
        tile.baseId1 = baseTextureIndex;
        tile.baseId2 = blendTextureIndex;
        tile.offset1 = 0;
        tile.offset2 = 0;
        tile.blending = true;
        tile.rotation = Zone.RotationType.NORMAL;
        tile.tileType = 0;

        int roseTileId = zone.tiles.size();
        zone.tiles.add(tile);
        blendRoseTileId.put(signature, roseTileId);
        return roseTileId;
    }

    private int ensureSolidRoseTileId(int flyffTextureId) throws IOException {
        Integer cached = solidRoseTileId.get(flyffTextureId);
        if (cached != null) {
            return cached;
        }

        int textureIndex = ensureRoseTextureIndex(flyffTextureId);
        Zone.Tile tile = new Zone.Tile();
        tile.baseId1 = textureIndex;
        tile.baseId2 = textureIndex;
        tile.offset1 = 0;
        tile.offset2 = 0;
        tile.blending = false;
        tile.rotation = Zone.RotationType.NORMAL;
        tile.tileType = 0;

        int roseTileId = zone.tiles.size();
        zone.tiles.add(tile);
        solidRoseTileId.put(flyffTextureId, roseTileId);
        return roseTileId;
    }

    private int ensureRoseTextureIndex(int flyffTextureId) throws IOException {
        Integer cached = flyffToRoseTextureIndex.get(flyffTextureId);
        if (cached != null) {
            return cached;
        }

        if (!imageCache.canLoad(flyffTextureId)) {
            throw new IOException("Cannot load Flyff terrain texture ID " + flyffTextureId);
        }

        String sourceName = registry.filenameFor(flyffTextureId);
        String ddsName = sanitizeBaseName(sourceName) + ".dds";
        Path destinationDds = roseTextureRoot.resolve(ddsName);
        Path sourcePath = resolveFlyffTexturePath(flyffRoot, sourceName);
        FlyffTgaToDds.convertIfNeeded(sourcePath, destinationDds);

        int textureIndex = zone.textures.size();
        Zone.TileTexture texture = new Zone.TileTexture();
        texture.path = ROSE_TEXTURE_FOLDER + "/" + ddsName;
        zone.textures.add(texture);
        flyffToRoseTextureIndex.put(flyffTextureId, textureIndex);
        return textureIndex;
    }

    static Path resolveFlyffTexturePath(Path flyffRoot, String filename) {
        String normalized = filename.replace('\\', '/');
        Path direct = flyffRoot.resolve(normalized);
        if (Files.isRegularFile(direct)) {
            return direct;
        }
        String[] folders = {"World/TextureMid/", "World/TextureLow/", "World/Texture/"};
        for (String folder : folders) {
            Path candidate = flyffRoot.resolve(folder + normalized);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            candidate = flyffRoot.resolve(folder + Path.of(normalized).getFileName());
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return flyffRoot.resolve(normalized);
    }

    private static String sanitizeBaseName(String filename) {
        if (filename == null || filename.isBlank()) {
            return "TEXTURE";
        }
        String base = Path.of(filename.replace('\\', '/')).getFileName().toString();
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        String sanitized = base.replaceAll("[^A-Za-z0-9_\\-]", "_");
        if (sanitized.isBlank()) {
            sanitized = "texture";
        }
        return sanitized.toUpperCase(Locale.ROOT);
    }

    private static Zone cloneZoneShell(Zone source) {
        Zone zone = new Zone();
        zone.blocks = new java.util.ArrayList<>(5);
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
        zone.zoneInfo.zoneParts = source.zoneInfo.zoneParts;

        zone.spawnPoints = new java.util.ArrayList<>();
        for (Zone.SpawnPoint sp : source.spawnPoints) {
            Zone.SpawnPoint copy = new Zone.SpawnPoint();
            copy.name = sp.name;
            copy.position.set(sp.position);
            zone.spawnPoints.add(copy);
        }

        zone.textures = new java.util.ArrayList<>();
        for (Zone.TileTexture texture : source.textures) {
            Zone.TileTexture copy = new Zone.TileTexture();
            copy.path = texture.path;
            zone.textures.add(copy);
        }

        zone.tiles = new java.util.ArrayList<>();
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

        zone.economyInfo = source.economyInfo != null ? source.economyInfo : new Zone.Economy();
        return zone;
    }
}
