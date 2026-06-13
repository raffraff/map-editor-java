package com.rose.mapeditor.map;

import com.rose.mapeditor.GameData;
import com.rose.mapeditor.data.Stb;
import com.rose.mapeditor.render.HeightmapBlock;
import com.rose.mapeditor.scene.MapScene;
import com.rose.mapeditor.scene.MapSceneBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/** Loads Rose Online map terrain data. */
public final class MapLoader {

    private static final int TERRAIN_PARSE_THREADS = Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors() - 1));

    public static final class LoadedMap {
        public int mapId;
        public String name;
        public String mapFolder;
        public Zone zone;
        public HeightmapBlock[] blocks;
        public int blockCount;
        public List<Ifo> ifoFiles = new ArrayList<>();
        public IfoLoader.IfoSummary ifoSummary;
        public MapScene scene;
    }

    public LoadedMap loadMap(int mapId) throws IOException {
        GameData data = GameData.get();
        if (data.stbs.isEmpty()) {
            data.loadCoreTables();
        }

        Stb listZone = data.stbs.get("LIST_ZONE");
        String zonePath = listZone.cell(mapId, 2);
        Path zoneFile = data.resolve(zonePath);

        LoadedMap map = new LoadedMap();
        map.mapId = mapId;
        map.name = data.stls.get("LIST_ZONE_S").search(listZone.cell(mapId, 27));
        map.mapFolder = zoneFile.getParent().toString();
        map.zone = new Zone(zoneFile.toString());

        List<HeightmapBlock> blocks = new ArrayList<>();
        File[] himFiles = listHimFiles(map.mapFolder);
        for (File himFile : himFiles) {
            try {
                String tilPath = himFile.getPath().toUpperCase().replace(".HIM", ".TIL");
                Him him = new Him(himFile.getPath());
                Til til = new Til(tilPath);
                blocks.add(new HeightmapBlock(him, til, map.zone, data.getGameRoot()));
            } catch (IOException e) {
                throw new RuntimeException("Failed loading " + himFile, e);
            }
        }

        map.blocks = blocks.toArray(new HeightmapBlock[0]);
        map.blockCount = map.blocks.length;

        map.ifoFiles = IfoLoader.loadFromFolder(map.mapFolder);
        map.ifoSummary = IfoLoader.summarize(map.ifoFiles);
        map.scene = new MapSceneBuilder().build(map);

        return map;
    }

    /**
     * Streams map loading with parallel terrain parsing and scene build overlapping terrain I/O.
     */
    public void streamMapAsync(int mapId, AsyncMapLoadResult result, int generation,
                               AtomicInteger loadGeneration) throws IOException {
        if (generation != loadGeneration.get()) {
            return;
        }

        GameData data = GameData.get();
        if (data.stbs.isEmpty()) {
            data.loadCoreTables();
        }
        data.clearMapCaches();

        Stb listZone = data.stbs.get("LIST_ZONE");
        String zonePath = listZone.cell(mapId, 2);
        Path zoneFile = data.resolve(zonePath);

        LoadedMap map = result.map;
        map.mapId = mapId;
        map.name = data.stls.get("LIST_ZONE_S").search(listZone.cell(mapId, 27));
        map.mapFolder = zoneFile.getParent().toString();
        map.zone = new Zone(zoneFile.toString());
        result.metadataReady = true;

        File[] himFiles = listHimFiles(map.mapFolder);
        result.totalBlockCount = himFiles.length;

        ExecutorService scenePool = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "MapSceneLoader");
            t.setDaemon(true);
            return t;
        });

        Future<MapScene> sceneFuture = scenePool.submit(() -> {
            if (generation != loadGeneration.get()) {
                return null;
            }
            map.ifoFiles = IfoLoader.loadFromFolder(map.mapFolder);
            map.ifoSummary = IfoLoader.summarize(map.ifoFiles);
            data.prepareMapAssets(mapId);
            if (generation != loadGeneration.get()) {
                return null;
            }
            return new MapSceneBuilder().build(map);
        });

        parseTerrainBlocksParallel(himFiles, map.zone, result, generation, loadGeneration);

        result.blocksComplete = true;
        map.blockCount = result.allBlocks.size();
        map.blocks = result.allBlocks.toArray(new HeightmapBlock[0]);

        if (generation != loadGeneration.get()) {
            scenePool.shutdownNow();
            return;
        }

        try {
            result.scene = sceneFuture.get();
            map.scene = result.scene;
            result.sceneComplete = result.scene != null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Scene load interrupted", e);
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            throw new IOException("Scene load failed", cause);
        } finally {
            scenePool.shutdown();
        }
    }

    private static void parseTerrainBlocksParallel(File[] himFiles, Zone zone, AsyncMapLoadResult result,
                                                   int generation, AtomicInteger loadGeneration) throws IOException {
        if (himFiles.length == 0) {
            return;
        }

        int threads = Math.min(TERRAIN_PARSE_THREADS, himFiles.length);
        ExecutorService pool = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "TerrainParser");
            t.setDaemon(true);
            return t;
        });

        try {
            List<Callable<HeightmapBlock>> tasks = new ArrayList<>(himFiles.length);
            for (File himFile : himFiles) {
                tasks.add(() -> {
                    if (generation != loadGeneration.get()) {
                        return null;
                    }
                    String tilPath = himFile.getPath().toUpperCase().replace(".HIM", ".TIL");
                    Him him = new Him(himFile.getPath());
                    Til til = new Til(tilPath);
                    return HeightmapBlock.prepare(him, til, zone);
                });
            }

            for (Future<HeightmapBlock> future : pool.invokeAll(tasks)) {
                HeightmapBlock block = future.get();
                if (block == null) {
                    continue;
                }
                result.allBlocks.add(block);
                result.preparedBlocks.offer(block);
                result.blocksPrepared.incrementAndGet();
            }
            result.allBlocks.sort(Comparator.comparing(b -> new File(b.heightFile.filePath).getName()));
        } catch (Exception e) {
            if (e instanceof IOException) {
                throw (IOException) e;
            }
            throw new IOException("Parallel terrain parse failed", e);
        } finally {
            pool.shutdown();
        }
    }

    private static File[] listHimFiles(String mapFolder) {
        File root = new File(mapFolder);
        File[] himFiles = root.listFiles((dir, name) -> name.toUpperCase().endsWith(".HIM"));
        if (himFiles != null && himFiles.length > 0) {
            Arrays.sort(himFiles, Comparator.comparing(File::getName));
            return himFiles;
        }

        // Fallback for maps created with sector files inside subfolders.
        List<File> nested = new ArrayList<>();
        File[] children = root.listFiles(File::isDirectory);
        if (children != null) {
            for (File child : children) {
                File[] sectorHim = child.listFiles((dir, name) -> name.toUpperCase().endsWith(".HIM"));
                if (sectorHim != null) {
                    nested.addAll(Arrays.asList(sectorHim));
                }
            }
        }
        nested.sort(Comparator.comparing(f -> f.getName()));
        return nested.toArray(new File[0]);
    }
}
