package com.rose.mapeditor.map;

import com.rose.mapeditor.render.HeightmapBlock;
import com.rose.mapeditor.scene.MapScene;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/** Incremental map load shared between the loader thread and the render thread. */
public final class AsyncMapLoadResult {

    public final MapLoader.LoadedMap map = new MapLoader.LoadedMap();
    /** CPU-prepared blocks waiting for GPU upload on the render thread. */
    public final ConcurrentLinkedQueue<HeightmapBlock> preparedBlocks = new ConcurrentLinkedQueue<>();
    public final List<HeightmapBlock> allBlocks = new ArrayList<>();

    public volatile boolean metadataReady;
    public volatile int totalBlockCount;
    public volatile boolean blocksComplete;
    public volatile MapScene scene;
    public volatile boolean sceneComplete;
    public volatile boolean failed;
    public volatile String errorMessage;

    public final AtomicInteger blocksPrepared = new AtomicInteger();

    public boolean isFullyComplete() {
        return metadataReady && blocksComplete && sceneComplete && !failed;
    }
}
