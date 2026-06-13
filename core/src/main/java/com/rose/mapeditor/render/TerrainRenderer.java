package com.rose.mapeditor.render;



import com.badlogic.gdx.Gdx;

import com.badlogic.gdx.graphics.GL20;

import com.badlogic.gdx.graphics.Mesh;

import com.badlogic.gdx.graphics.Texture;

import com.badlogic.gdx.graphics.glutils.ShaderProgram;

import com.badlogic.gdx.math.Matrix4;

import com.badlogic.gdx.utils.Disposable;

import com.github.terefang.gdx.ddsdxt.load.DDSLoader;

import com.rose.mapeditor.map.Zone;



import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;



/** Renders terrain heightmap blocks with the height shader. */

public final class TerrainRenderer implements Disposable {



    private final ShaderProgram shader;

    private final Map<String, Texture> tileTextures = new HashMap<>();
    private Texture placeholderTexture;

    private final IdentityHashMap<HeightmapBlock, BlockRenderCache> blockRenderCaches = new IdentityHashMap<>();

    private boolean gridOutlineEnabled = true;
    private RenderOptions renderOptions = new RenderOptions();



    public TerrainRenderer() {

        shader = new ShaderProgram(

            Gdx.files.internal("shaders/height.vert"),

            Gdx.files.internal("shaders/height.frag")

        );

        if (!shader.isCompiled()) {

            throw new IllegalStateException("Height shader compile error: " + shader.getLog());

        }

    }



    public void setGridOutlineEnabled(boolean enabled) {

        gridOutlineEnabled = enabled;

    }

    public void setRenderOptions(RenderOptions renderOptions) {
        this.renderOptions = renderOptions != null ? renderOptions : new RenderOptions();
    }



    public Texture getTileTexture(Zone zone, int textureId) {
        String key = tilePathKey(zone, textureId);
        return tileTextures.computeIfAbsent(key, k -> loadTileTexture(zone, textureId));
    }

    /** Disposes terrain tile textures and cached block meshes from the previous map. */
    public void clearTileTextures() {
        disposeBlockRenderCaches();
        for (Texture texture : tileTextures.values()) {
            if (texture != placeholderTexture) {
                texture.dispose();
            }
        }
        tileTextures.clear();
    }

    private static String tilePathKey(Zone zone, int textureId) {
        if (zone == null || textureId < 0 || textureId >= zone.textures.size()) {
            return "__missing__:" + textureId;
        }
        String path = zone.textures.get(textureId).path;
        if (path == null || path.isBlank()) {
            return "__blank__:" + textureId;
        }
        return TextureCache.normalize(path).toLowerCase();
    }

    /** Preloads terrain tile DDS textures incrementally on the render thread. */
    public int preloadZoneTextures(Zone zone, int maxTextures) {
        if (zone == null || zone.textures.isEmpty() || maxTextures <= 0) {
            return 0;
        }
        int loaded = 0;
        for (int textureId : referencedTextureIds(zone)) {
            if (loaded >= maxTextures) {
                break;
            }
            String key = tilePathKey(zone, textureId);
            if (!tileTextures.containsKey(key)) {
                tileTextures.put(key, loadTileTexture(zone, textureId));
                loaded++;
            }
        }
        return loaded;
    }

    public int countMissingZoneTextures(Zone zone) {
        if (zone == null) {
            return 0;
        }
        int missing = 0;
        for (int textureId : referencedTextureIds(zone)) {
            if (!tileTextures.containsKey(tilePathKey(zone, textureId))) {
                missing++;
            }
        }
        return missing;
    }

    /** Texture IDs used by zone tile definitions. */
    private static Set<Integer> referencedTextureIds(Zone zone) {
        Set<Integer> ids = new HashSet<>();
        for (Zone.Tile tile : zone.tiles) {
            addTextureId(ids, zone, tile.id1());
            addTextureId(ids, zone, tile.id2());
        }
        return ids;
    }

    private static void addTextureId(Set<Integer> ids, Zone zone, int textureId) {
        if (textureId < 0 || textureId >= zone.textures.size()) {
            return;
        }
        if (isLoadableTileTexturePath(zone.textures.get(textureId).path)) {
            ids.add(textureId);
        }
    }

    /** Rose ZON files may end the texture list with a sentinel entry named {@code end}. */
    private static boolean isLoadableTileTexturePath(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        String trimmed = path.trim();
        if (trimmed.equalsIgnoreCase("end")) {
            return false;
        }
        return trimmed.toLowerCase().endsWith(".dds");
    }

    private Texture loadTileTexture(Zone zone, int textureId) {
        if (textureId < 0 || textureId >= zone.textures.size()) {
            return placeholderTexture();
        }
        Zone.TileTexture entry = zone.textures.get(textureId);
        if (!isLoadableTileTexturePath(entry.path)) {
            return placeholderTexture();
        }
        String path = entry.path.replace('\\', '/');
        try {
            Texture texture = new Texture(DDSLoader.fromDdsToTexture(Gdx.files.absolute(
                com.rose.mapeditor.GameData.get().resolve(path).toString()
            )));
            texture.setWrap(Texture.TextureWrap.MirroredRepeat, Texture.TextureWrap.MirroredRepeat);
            return texture;
        } catch (Exception e) {
            Gdx.app.error("TerrainRenderer", "Failed loading tile texture " + textureId + ": " + path, e);
            return placeholderTexture();
        }
    }

    private Texture placeholderTexture() {
        if (placeholderTexture == null) {
            com.badlogic.gdx.graphics.Pixmap pixmap = new com.badlogic.gdx.graphics.Pixmap(4, 4,
                com.badlogic.gdx.graphics.Pixmap.Format.RGB888);
            pixmap.setColor(0.45f, 0.35f, 0.25f, 1f);
            pixmap.fill();
            placeholderTexture = new Texture(pixmap);
            pixmap.dispose();
            placeholderTexture.setWrap(Texture.TextureWrap.MirroredRepeat, Texture.TextureWrap.MirroredRepeat);
        }
        return placeholderTexture;
    }



    public void render(HeightmapBlock[] blocks, int blockCount, Matrix4 projView, Zone zone) {

        if (blocks == null || blockCount == 0) {

            return;

        }



        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
        // Y/Z axis conversion inverts winding
        Gdx.gl.glEnable(GL20.GL_CULL_FACE);
        Gdx.gl.glFrontFace(GL20.GL_CW);
        Gdx.gl.glCullFace(GL20.GL_BACK);



        shader.bind();

        shader.setUniformMatrix("u_projViewMatrix", projView);

        shader.setUniformi("u_gridOutlineEnabled", gridOutlineEnabled ? 1 : 0);
        shader.setUniformi("u_lightmapEnabled", renderOptions.isTerrainLightmap() ? 1 : 0);
        shader.setUniformi("u_texturesEnabled", renderOptions.isTerrainTextures() ? 1 : 0);

        shader.setUniformi("u_shadowTexture", 2);

        shader.setUniformi("u_bottomTexture", 0);

        shader.setUniformi("u_topTexture", 1);



        for (int b = 0; b < blockCount; b++) {

            HeightmapBlock block = blocks[b];

            if (block == null || block.shadowMap == null) {

                continue;

            }



            block.shadowMap.bind(2);

            BlockRenderCache cache = blockRenderCache(block);
            for (int i = 0; i < cache.batches.size(); i++) {
                TextureTileBatch batch = cache.batches.get(i);
                getTileTexture(zone, batch.tileId1).bind(0);
                getTileTexture(zone, batch.tileId2).bind(1);
                batch.mesh.render(shader, GL20.GL_TRIANGLES);
            }
        }



        Gdx.gl.glFrontFace(GL20.GL_CCW);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        GlState.resetForUi();
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
    }

    private BlockRenderCache blockRenderCache(HeightmapBlock block) {
        BlockRenderCache cached = blockRenderCaches.get(block);
        if (cached != null && cached.renderGeneration == block.renderGeneration) {
            return cached;
        }
        if (cached != null) {
            cached.dispose();
        }
        BlockRenderCache built = buildBlockRenderCache(block);
        blockRenderCaches.put(block, built);
        return built;
    }

    private static BlockRenderCache buildBlockRenderCache(HeightmapBlock block) {
        Map<Long, List<int[]>> groupedTiles = new HashMap<>();
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                HeightmapBlock.TerrainTile tile = block.tiles[y][x];
                long key = texturePairKey(tile.tileId1, tile.tileId2);
                groupedTiles.computeIfAbsent(key, k -> new ArrayList<>()).add(new int[] { y, x });
            }
        }

        List<TextureTileBatch> batches = new ArrayList<>(groupedTiles.size());
        for (Map.Entry<Long, List<int[]>> entry : groupedTiles.entrySet()) {
            long key = entry.getKey();
            List<int[]> tiles = entry.getValue();
            int tileCount = tiles.size();
            int vertexCount = tileCount * 25;
            int indexCount = tileCount * HeightmapBlock.TRIANGLE_INDICES_PER_TILE;
            float[] vertices = new float[vertexCount * HeightmapBlock.FLOATS_PER_VERTEX];
            short[] indices = new short[indexCount];

            int vertexOffset = 0;
            int indexOffset = 0;
            for (int i = 0; i < tileCount; i++) {
                int y = tiles.get(i)[0];
                int x = tiles.get(i)[1];
                int baseVertex = (y * 16 + x) * 25;
                System.arraycopy(
                    block.vertices,
                    baseVertex * HeightmapBlock.FLOATS_PER_VERTEX,
                    vertices,
                    vertexOffset * HeightmapBlock.FLOATS_PER_VERTEX,
                    25 * HeightmapBlock.FLOATS_PER_VERTEX
                );

                HeightmapBlock.appendStripAsTriangles(vertexOffset, indices, indexOffset);
                indexOffset += HeightmapBlock.TRIANGLE_INDICES_PER_TILE;
                vertexOffset += 25;
            }

            Mesh mesh = new Mesh(true, vertexCount, indexCount, HeightmapBlock.ATTRIBUTES);
            mesh.setVertices(vertices);
            mesh.setIndices(indices);

            TextureTileBatch batch = new TextureTileBatch();
            batch.tileId1 = (int) (key >> 32);
            batch.tileId2 = (int) key;
            batch.mesh = mesh;
            batches.add(batch);
        }

        BlockRenderCache cache = new BlockRenderCache();
        cache.renderGeneration = block.renderGeneration;
        cache.batches = batches;
        return cache;
    }

    private static long texturePairKey(int tileId1, int tileId2) {
        return ((long) tileId1 << 32) | (tileId2 & 0xFFFFFFFFL);
    }

    private void disposeBlockRenderCaches() {
        for (BlockRenderCache cache : blockRenderCaches.values()) {
            cache.dispose();
        }
        blockRenderCaches.clear();
    }

    private static final class TextureTileBatch {
        int tileId1;
        int tileId2;
        Mesh mesh;
    }

    private static final class BlockRenderCache {
        int renderGeneration;
        List<TextureTileBatch> batches = List.of();

        void dispose() {
            for (int i = 0; i < batches.size(); i++) {
                batches.get(i).mesh.dispose();
            }
            batches = List.of();
        }
    }



    @Override

    public void dispose() {
        shader.dispose();
        disposeBlockRenderCaches();

        for (Texture texture : tileTextures.values()) {
            if (texture != placeholderTexture) {
                texture.dispose();
            }
        }
        tileTextures.clear();
        if (placeholderTexture != null) {
            placeholderTexture.dispose();
            placeholderTexture = null;
        }

    }

}


