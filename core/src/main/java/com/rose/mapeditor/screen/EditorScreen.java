package com.rose.mapeditor.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.rose.mapeditor.MapEditorGame;
import com.rose.mapeditor.GameData;
import com.rose.mapeditor.map.AsyncMapLoadResult;
import com.rose.mapeditor.map.FlatMapCreator;
import com.rose.mapeditor.map.MapCatalog;
import com.rose.mapeditor.map.MapInfoFormatter;
import com.rose.mapeditor.map.MapLoader;
import com.rose.mapeditor.map.ProceduralMapGenerator;
import com.rose.mapeditor.map.ObjectLightmapBaker;
import com.rose.mapeditor.map.TerrainLightmapBaker;
import com.rose.mapeditor.map.Zone;
import com.rose.mapeditor.render.EditorCamera;
import com.rose.mapeditor.render.GlState;
import com.rose.mapeditor.render.HeightmapBlock;
import com.rose.mapeditor.render.MapSceneRenderer;
import com.rose.mapeditor.render.RenderOptions;
import com.rose.mapeditor.render.SkyRenderer;
import com.rose.mapeditor.render.TerrainRenderer;
import com.rose.mapeditor.render.WireframeGl;
import com.rose.mapeditor.map.ObjectInfoFormatter;
import com.rose.mapeditor.scene.MapScene;
import com.rose.mapeditor.scene.MapSceneBuilder;
import com.rose.mapeditor.scene.SceneObjectRef;
import com.rose.mapeditor.tool.HeightBrushTool;
import com.rose.mapeditor.tool.SceneInspectInput;
import com.rose.mapeditor.tool.TerrainHeightGrid;
import com.rose.mapeditor.ui.GameRootPicker;
import com.rose.mapeditor.ui.EditorSkinUtil;
import com.rose.mapeditor.ui.HeightBrushPanel;
import com.rose.mapeditor.ui.WindowTitle;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Main editor viewport with terrain rendering and basic map controls. */
public class EditorScreen extends ScreenAdapter {

    /** Heightmap quads uploaded to the GPU per frame. */
    private static final int GPU_BLOCKS_PER_FRAME = 6;
    /** Terrain tile DDS textures preloaded per frame while a map is loading. */
    private static final int TILE_TEXTURES_PER_FRAME = 8;
    /** Object meshes uploaded per frame while a map scene is warming up. */
    private static final int OBJECT_MESHES_PER_FRAME = 10;
    /** Object diffuse/lightmap textures uploaded per frame while a map scene is warming up. */
    private static final int OBJECT_TEXTURES_PER_FRAME = 4;

    private final EditorCamera camera = new EditorCamera();
    private final RenderOptions renderOptions = new RenderOptions();
    private final TerrainRenderer terrainRenderer = new TerrainRenderer();
    private final SkyRenderer skyRenderer = new SkyRenderer();
    private final MapSceneRenderer sceneRenderer = new MapSceneRenderer();
    private final MapLoader mapLoader = new MapLoader();
    private final ExecutorService loadExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "MapLoader");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicInteger loadGeneration = new AtomicInteger();
    private final List<HeightmapBlock> visibleBlocks = new ArrayList<>();
    private MapScene sceneGpuPreloadTarget;

    private Stage uiStage;
    private Label gameRootLabel;
    private Label statusLabel;
    private Label positionLabel;
    private TextButton softDiffuseButton;
    private TextButton npcMarkersButton;
    private TextButton monsterMarkersButton;
    private TextButton effectsButton;
    private TextButton terrainLightmapButton;
    private TextButton wireframeButton;
    private TextButton terrainTexturesButton;
    private TextButton objectTexturesButton;
    private TextButton bakeLightmapsButton;
    private TextButton heightToolButton;
    private HeightBrushPanel heightBrushPanel;
    private Skin skin;

    private final HeightBrushTool heightBrushTool = new HeightBrushTool();
    private final HeightBrushTool.PickContext pickContext = new HeightBrushTool.PickContext() {
        @Override
        public Ray pickRay(int screenX, int screenY) {
            return camera.getPickRay(screenX, screenY);
        }
    };
    private InputMultiplexer inputMultiplexer;
    private SceneInspectInput sceneInspectInput;

    private MapLoader.LoadedMap currentMap;
    private AsyncMapLoadResult activeLoad;
    private boolean cameraFocusedForLoad;
    private boolean loading;
    private final Vector3 cameraGdx = new Vector3();
    private final Matrix4 skyMatrix = new Matrix4();
    private final Vector2 uiHitCoords = new Vector2();

    @Override
    public void show() {
        skin = new Skin();
        skin.add("default-font", new BitmapFont());
        skin.add("default", new Label.LabelStyle(skin.getFont("default-font"), com.badlogic.gdx.graphics.Color.WHITE));
        skin.add("default", new TextButton.TextButtonStyle(null, null, null, skin.getFont("default-font")));
        EditorSkinUtil.extend(skin);

        uiStage = new Stage(new ScreenViewport());

        terrainRenderer.setRenderOptions(renderOptions);
        sceneRenderer.setRenderOptions(renderOptions);

        Table root = new Table();
        root.setFillParent(true);
        root.setTouchable(Touchable.childrenOnly);
        root.top().left().pad(8);

        statusLabel = new Label("Loading...", skin);
        positionLabel = new Label("Camera: -", skin);

        gameRootLabel = new Label("", skin);
        gameRootLabel.setWrap(true);
        gameRootLabel.setColor(new Color(0.55f, 0.85f, 1f, 1f));
        gameRootLabel.addListener(new ClickListener() {
            @Override
            public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                promptForGameRootChange();
            }
        });

        TextButton openMapButton = new TextButton("Open Map...", skin);
        openMapButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                showMapPicker();
            }
        });

        TextButton mapInfoButton = new TextButton("Map Info...", skin);
        mapInfoButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                showMapInfoOverlay();
            }
        });

        TextButton deleteMapButton = new TextButton("Delete Map...", skin);
        deleteMapButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                showDeleteMapOverlay();
            }
        });

        TextButton newMapButton = new TextButton("New Flat Map...", skin);
        newMapButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                showNewMapOverlay();
            }
        });

        TextButton importFlyffButton = new TextButton("Import Flyff Map...", skin);
        importFlyffButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                showImportFlyffMapOverlay();
            }
        });

        TextButton proceduralMapButton = new TextButton("Procedural Map...", skin);
        proceduralMapButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                showProceduralMapOverlay();
            }
        });

        TextButton reloadTables = new TextButton("Reload STB/STL", skin);
        reloadTables.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                reloadCoreData();
            }
        });

        softDiffuseButton = new TextButton("Soft diffuse: OFF", skin);
        softDiffuseButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                toggleSoftDiffuse();
            }
        });

        npcMarkersButton = new TextButton("NPC markers: OFF", skin);
        npcMarkersButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                toggleNpcMarkers();
            }
        });

        monsterMarkersButton = new TextButton("Monster markers: OFF", skin);
        monsterMarkersButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                toggleMonsterMarkers();
            }
        });

        effectsButton = new TextButton("Effects: ON", skin);
        effectsButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                toggleEffects();
            }
        });

        terrainLightmapButton = new TextButton("Terrain lightmap: ON", skin);
        terrainLightmapButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                toggleTerrainLightmap();
            }
        });

        wireframeButton = new TextButton("Wireframe: OFF", skin);
        wireframeButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                toggleWireframe();
            }
        });

        terrainTexturesButton = new TextButton("Terrain textures: ON", skin);
        terrainTexturesButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                toggleTerrainTextures();
            }
        });

        objectTexturesButton = new TextButton("Object textures: ON", skin);
        objectTexturesButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                toggleObjectTextures();
            }
        });

        bakeLightmapsButton = new TextButton("Bake lightmaps", skin);
        bakeLightmapsButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                bakeTerrainLightmaps();
            }
        });

        heightToolButton = new TextButton("Height tool: OFF", skin);
        heightToolButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                toggleHeightTool();
            }
        });

        heightBrushPanel = new HeightBrushPanel(skin, heightBrushTool, () -> saveHeightmaps());

        root.add(gameRootLabel).width(360).left().row();
        root.add(statusLabel).left().padTop(4).row();
        root.add(positionLabel).left().padTop(4).row();
        root.add(openMapButton).width(180).padTop(8).left().row();
        root.add(mapInfoButton).width(180).padTop(4).left().row();
        root.add(deleteMapButton).width(180).padTop(4).left().row();
        root.add(newMapButton).width(180).padTop(4).left().row();
        root.add(importFlyffButton).width(180).padTop(4).left().row();
        root.add(proceduralMapButton).width(180).padTop(4).left().row();
        root.add(reloadTables).width(180).padTop(4).left().row();
        root.add(softDiffuseButton).width(180).padTop(4).left().row();
        root.add(npcMarkersButton).width(180).padTop(4).left().row();
        root.add(monsterMarkersButton).width(180).padTop(4).left().row();
        root.add(effectsButton).width(180).padTop(4).left().row();
        root.add(terrainLightmapButton).width(180).padTop(4).left().row();
        root.add(wireframeButton).width(180).padTop(4).left().row();
        root.add(terrainTexturesButton).width(180).padTop(4).left().row();
        root.add(objectTexturesButton).width(180).padTop(4).left().row();
        root.add(bakeLightmapsButton).width(180).padTop(4).left().row();
        root.add(heightToolButton).width(180).padTop(4).left().row();
        root.add(heightBrushPanel).padLeft(4).left().row();

        uiStage.addActor(root);

        inputMultiplexer = new InputMultiplexer();
        inputMultiplexer.addProcessor(uiStage);
        inputMultiplexer.addProcessor(heightBrushTool);
        sceneInspectInput = new SceneInspectInput(new SceneInspectInput.Listener() {
            @Override
            public boolean canInspect() {
                return !heightBrushTool.isActive()
                    && !isModalOverlayOpen()
                    && currentMap != null
                    && currentMap.scene != null;
            }

            @Override
            public Ray pickRay(int screenX, int screenY) {
                return camera.getPickRay(screenX, screenY);
            }

            @Override
            public MapScene scene() {
                return currentMap != null ? currentMap.scene : null;
            }

            @Override
            public void onObjectPicked(SceneObjectRef ref) {
                showObjectInfoOverlay(ref);
            }
        });
        inputMultiplexer.addProcessor(sceneInspectInput);
        Gdx.input.setInputProcessor(inputMultiplexer);

        reloadCoreData();
        camera.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    private void reloadCoreData() {
        try {
            GameData.get().loadCoreTables();
            updateGameRootLabel();
            statusLabel.setText("Loaded STB/STL tables.");
        } catch (Exception e) {
            updateGameRootLabel();
            statusLabel.setText("Failed loading STB/STL: " + e.getMessage());
        }
    }

    private void updateGameRootLabel() {
        String path = GameData.get().getGameRoot().toAbsolutePath().normalize().toString();
        gameRootLabel.setText("Game root: " + path + " (click to change)");
    }

    private void promptForGameRootChange() {
        if (isModalOverlayOpen()) {
            return;
        }
        if (!GameRootPicker.isAvailable()) {
            statusLabel.setText("Game root picker is not available on this platform.");
            return;
        }

        statusLabel.setText("Choose a new game folder...");
        new Thread(() -> {
            String chosen;
            try {
                chosen = GameRootPicker.prompt();
            } catch (RuntimeException e) {
                Gdx.app.postRunnable(() -> statusLabel.setText(
                    "Failed opening folder chooser: " + e.getMessage()));
                return;
            }
            Gdx.app.postRunnable(() -> applyGameRootChange(chosen));
        }, "GameRootPicker").start();
    }

    private void applyGameRootChange(String chosen) {
        if (chosen == null) {
            statusLabel.setText("Game root unchanged.");
            return;
        }

        String current = GameData.get().getGameRoot().toAbsolutePath().normalize().toString();
        if (chosen.equals(current)) {
            statusLabel.setText("Game root unchanged.");
            return;
        }

        if (Gdx.app.getApplicationListener() instanceof MapEditorGame) {
            ((MapEditorGame) Gdx.app.getApplicationListener()).setGameRoot(chosen);
        } else {
            GameData.get().setGameRoot(chosen);
        }

        unloadCurrentMap();
        GameData.get().resetForNewGameRoot();
        reloadCoreData();
        statusLabel.setText("Game root updated. Open a map to continue.");
    }

    private void showProceduralMapOverlay() {
        if (isModalOverlayOpen()) {
            return;
        }

        try {
            GameData.get().loadCoreTables();
            var entries = MapCatalog.listValidMaps(GameData.get());
            if (entries.isEmpty()) {
                statusLabel.setText("No valid source maps in LIST_ZONE.STB");
                return;
            }

            int defaultSourceId = currentMap != null && currentMap.mapId > 0
                ? currentMap.mapId : entries.get(0).id;
            ProceduralMapGenerator.Request defaults = ProceduralMapGenerator.defaultRequest(
                GameData.get(), defaultSourceId);

            ProceduralMapOverlay overlay = new ProceduralMapOverlay(skin, entries, defaults,
                new ProceduralMapOverlay.Listener() {
                    @Override
                    public void onMapCreated(int mapId) {
                        loadMap(mapId);
                    }

                    @Override
                    public void onCancelled() {
                        // no-op
                    }
                });
            uiStage.addActor(overlay);
        } catch (Exception e) {
            statusLabel.setText("Failed opening procedural map dialog: " + e.getMessage());
            Gdx.app.error("EditorScreen", "Procedural map overlay failed", e);
        }
    }

    private void showNewMapOverlay() {
        if (isModalOverlayOpen()) {
            return;
        }

        try {
            GameData.get().loadCoreTables();
            FlatMapCreator.Request defaults = FlatMapCreator.defaultRequest(GameData.get());
            NewMapOverlay overlay = new NewMapOverlay(skin, defaults, new NewMapOverlay.Listener() {
                @Override
                public void onMapCreated(int mapId) {
                    loadMap(mapId);
                }

                @Override
                public void onCancelled() {
                    // no-op
                }
            });
            uiStage.addActor(overlay);
        } catch (Exception e) {
            statusLabel.setText("Failed opening new map dialog: " + e.getMessage());
            Gdx.app.error("EditorScreen", "New map overlay failed", e);
        }
    }

    private void showImportFlyffMapOverlay() {
        if (isModalOverlayOpen()) {
            return;
        }

        try {
            GameData.get().loadCoreTables();
            FlatMapCreator.Request defaults = FlatMapCreator.defaultRequest(GameData.get());
            ImportFlyffMapOverlay overlay = new ImportFlyffMapOverlay(skin, defaults,
                new ImportFlyffMapOverlay.Listener() {
                    @Override
                    public void onMapImported(int mapId, String summary) {
                        statusLabel.setText(summary);
                        loadMap(mapId);
                    }

                    @Override
                    public void onCancelled() {
                        // no-op
                    }
                });
            uiStage.addActor(overlay);
        } catch (Exception e) {
            statusLabel.setText("Failed opening Flyff import dialog: " + e.getMessage());
            Gdx.app.error("EditorScreen", "Flyff import overlay failed", e);
        }
    }

    private void showDeleteMapOverlay() {
        if (isModalOverlayOpen()) {
            return;
        }

        try {
            GameData.get().loadCoreTables();
            var entries = MapCatalog.listDeletableMaps(GameData.get());
            if (entries.isEmpty()) {
                statusLabel.setText("No maps with zone paths found in LIST_ZONE.STB");
                return;
            }

            DeleteMapOverlay overlay = new DeleteMapOverlay(skin, entries, new DeleteMapOverlay.Listener() {
                @Override
                public void onMapDeleted(int mapId, String mapName) {
                    handleMapDeleted(mapId, mapName);
                }

                @Override
                public void onCancelled() {
                    // no-op
                }
            });
            uiStage.addActor(overlay);
        } catch (Exception e) {
            statusLabel.setText("Failed opening delete map dialog: " + e.getMessage());
            Gdx.app.error("EditorScreen", "Delete map overlay failed", e);
        }
    }

    private void handleMapDeleted(int mapId, String mapName) {
        if (currentMap != null && currentMap.mapId == mapId) {
            unloadCurrentMap();
        }
        statusLabel.setText(String.format("Deleted map [%d] %s from disk and LIST_ZONE.", mapId, mapName));
    }

    private void unloadCurrentMap() {
        loadGeneration.incrementAndGet();
        loading = false;
        activeLoad = null;
        disposeCurrentMap();
        heightBrushTool.clearTerrain();
        terrainRenderer.clearTileTextures();
        sceneRenderer.clearMapResources();
        skyRenderer.clearMapResources();
        GameData.get().clearMapCaches();
    }

    private void showMapPicker() {
        if (isModalOverlayOpen()) {
            return;
        }

        try {
            GameData.get().loadCoreTables();
            var entries = MapCatalog.listValidMaps(GameData.get());
            if (entries.isEmpty()) {
                statusLabel.setText("No valid maps in LIST_ZONE.STB");
                return;
            }

            MapPickerOverlay picker = new MapPickerOverlay(skin, entries, new MapPickerOverlay.Listener() {
                @Override
                public void onMapChosen(int mapId) {
                    loadMap(mapId);
                }

                @Override
                public void onCancelled() {
                    // no-op
                }
            });
            uiStage.addActor(picker);
        } catch (Exception e) {
            statusLabel.setText("Failed reading map list: " + e.getMessage());
            Gdx.app.error("EditorScreen", "Map picker failed", e);
        }
    }

    private void showObjectInfoOverlay(SceneObjectRef ref) {
        if (ref == null) {
            return;
        }
        closeObjectInfoOverlay();
        try {
            String infoText = ObjectInfoFormatter.format(GameData.get(), ref);
            String title = "Object Info - " + ObjectInfoFormatter.titleFor(ref);
            uiStage.addActor(new ObjectInfoOverlay(skin, title, infoText));
        } catch (Exception e) {
            statusLabel.setText("Failed reading object info: " + e.getMessage());
            Gdx.app.error("EditorScreen", "Object info failed", e);
        }
    }

    private void closeObjectInfoOverlay() {
        for (int i = uiStage.getActors().size - 1; i >= 0; i--) {
            Actor actor = uiStage.getActors().get(i);
            if (actor instanceof ObjectInfoOverlay) {
                actor.remove();
            }
        }
    }

    private void showMapInfoOverlay() {
        if (currentMap == null) {
            statusLabel.setText("Load a map before viewing map info.");
            return;
        }
        if (isModalOverlayOpen()) {
            return;
        }
        try {
            String info = MapInfoFormatter.format(GameData.get(), currentMap);
            uiStage.addActor(new MapInfoOverlay(skin, info));
        } catch (Exception e) {
            statusLabel.setText("Failed reading map info: " + e.getMessage());
            Gdx.app.error("EditorScreen", "Map info failed", e);
        }
    }

    private boolean isModalOverlayOpen() {
        for (Actor actor : uiStage.getActors()) {
            if (actor instanceof MapPickerOverlay || actor instanceof NewMapOverlay
                || actor instanceof ImportFlyffMapOverlay || actor instanceof ProceduralMapOverlay
                || actor instanceof DeleteMapOverlay || actor instanceof ConfirmDialogOverlay
                || actor instanceof MapInfoOverlay || actor instanceof ObjectInfoOverlay) {
                return true;
            }
        }
        return false;
    }

    private boolean isMapPickerOpen() {
        for (Actor actor : uiStage.getActors()) {
            if (actor instanceof MapPickerOverlay) {
                return true;
            }
        }
        return false;
    }

    private void closeModalOverlays() {
        for (int i = uiStage.getActors().size - 1; i >= 0; i--) {
            Actor actor = uiStage.getActors().get(i);
            if (actor instanceof MapPickerOverlay || actor instanceof NewMapOverlay
                || actor instanceof ImportFlyffMapOverlay || actor instanceof ProceduralMapOverlay
                || actor instanceof DeleteMapOverlay || actor instanceof ConfirmDialogOverlay
                || actor instanceof MapInfoOverlay || actor instanceof ObjectInfoOverlay) {
                actor.remove();
            }
        }
    }

    private void closeMapPicker() {
        for (int i = uiStage.getActors().size - 1; i >= 0; i--) {
            Actor actor = uiStage.getActors().get(i);
            if (actor instanceof MapPickerOverlay) {
                actor.remove();
            }
        }
    }

    private void toggleSoftDiffuse() {
        renderOptions.toggleSoftDiffuse();
        updateSoftDiffuseButtonLabel();
    }

    private void toggleNpcMarkers() {
        renderOptions.toggleNpcMarkers();
        updateNpcMarkersButtonLabel();
    }

    private void toggleMonsterMarkers() {
        renderOptions.toggleMonsterMarkers();
        updateMonsterMarkersButtonLabel();
    }

    private void toggleEffects() {
        renderOptions.toggleEffects();
        updateEffectsButtonLabel();
    }

    private void toggleTerrainLightmap() {
        renderOptions.toggleTerrainLightmap();
        updateTerrainLightmapButtonLabel();
    }

    private void toggleWireframe() {
        renderOptions.toggleWireframe();
        updateWireframeButtonLabel();
    }

    private void toggleTerrainTextures() {
        renderOptions.toggleTerrainTextures();
        updateTerrainTexturesButtonLabel();
    }

    private void toggleObjectTextures() {
        renderOptions.toggleObjectTextures();
        updateObjectTexturesButtonLabel();
    }

    private void updateSoftDiffuseButtonLabel() {
        if (softDiffuseButton != null) {
            softDiffuseButton.setText(renderOptions.isSoftDiffuse() ? "Soft diffuse: ON" : "Soft diffuse: OFF");
        }
    }

    private void updateNpcMarkersButtonLabel() {
        if (npcMarkersButton != null) {
            npcMarkersButton.setText(renderOptions.isNpcMarkers() ? "NPC markers: ON" : "NPC markers: OFF");
        }
    }

    private void updateMonsterMarkersButtonLabel() {
        if (monsterMarkersButton != null) {
            monsterMarkersButton.setText(renderOptions.isMonsterMarkers() ? "Monster markers: ON" : "Monster markers: OFF");
        }
    }

    private void updateEffectsButtonLabel() {
        if (effectsButton != null) {
            effectsButton.setText(renderOptions.isEffectsEnabled() ? "Effects: ON" : "Effects: OFF");
        }
    }

    private void updateTerrainLightmapButtonLabel() {
        if (terrainLightmapButton != null) {
            terrainLightmapButton.setText(renderOptions.isTerrainLightmap() ? "Terrain lightmap: ON" : "Terrain lightmap: OFF");
        }
    }

    private void updateWireframeButtonLabel() {
        if (wireframeButton != null) {
            wireframeButton.setText(renderOptions.isWireframe() ? "Wireframe: ON" : "Wireframe: OFF");
        }
    }

    private void updateTerrainTexturesButtonLabel() {
        if (terrainTexturesButton != null) {
            terrainTexturesButton.setText(renderOptions.isTerrainTextures() ? "Terrain textures: ON" : "Terrain textures: OFF");
        }
    }

    private void updateObjectTexturesButtonLabel() {
        if (objectTexturesButton != null) {
            objectTexturesButton.setText(renderOptions.isObjectTextures() ? "Object textures: ON" : "Object textures: OFF");
        }
    }

    private void toggleHeightTool() {
        heightBrushTool.toggleActive();
        if (heightBrushPanel != null) {
            heightBrushPanel.setVisible(heightBrushTool.isActive());
        }
        if (heightToolButton != null) {
            heightToolButton.setText(heightBrushTool.isActive() ? "Height tool: ON" : "Height tool: OFF");
        }
    }

    private void bindHeightToolToMap() {
        if (currentMap == null || currentMap.blocks == null || currentMap.zone == null) {
            heightBrushTool.clearTerrain();
            return;
        }
        heightBrushTool.bindTerrain(new TerrainHeightGrid(currentMap.blocks, currentMap.zone));
        heightBrushTool.setTerrain(currentMap.blocks, currentMap.blockCount, pickContext);
        if (heightBrushPanel != null) {
            heightBrushPanel.syncFromTool();
        }
    }

    private boolean isPointerOverUi() {
        if (uiStage == null || isModalOverlayOpen()) {
            return true;
        }
        uiHitCoords.set(Gdx.input.getX(), Gdx.input.getY());
        uiStage.screenToStageCoordinates(uiHitCoords);
        return uiStage.hit(uiHitCoords.x, uiHitCoords.y, true) != null;
    }

    private void saveHeightmaps() {
        try {
            int count = heightBrushTool.saveAll();
            if (count == 0) {
                statusLabel.setText("Load a map before saving heights.");
            } else {
                statusLabel.setText("Saved " + count + " HIM file(s).");
            }
        } catch (Exception e) {
            statusLabel.setText("Save failed: " + e.getMessage());
            Gdx.app.error("EditorScreen", "HIM save failed", e);
        }
    }

    private void bakeTerrainLightmaps() {
        if (currentMap == null || currentMap.blocks == null || currentMap.blocks.length == 0) {
            statusLabel.setText("Load a map before baking lightmaps.");
            return;
        }
        final MapLoader.LoadedMap mapSnapshot = currentMap;
        try {
            if (GameData.get().stbs.isEmpty()) {
                GameData.get().loadCoreTables();
            }
        } catch (IOException e) {
            statusLabel.setText("Lightmap bake failed: " + e.getMessage());
            Gdx.app.error("EditorScreen", "Failed loading STB tables for lightmap bake", e);
            return;
        }
        statusLabel.setText("Baking lightmaps...");
        loadExecutor.execute(() -> {
            try {
                Path mapFolder = Path.of(mapSnapshot.mapFolder);
                int terrainCount = TerrainLightmapBaker.bakeBlocks(mapSnapshot.blocks, mapFolder);
                int objectCount = 0;
                if (mapSnapshot.ifoFiles != null && !mapSnapshot.ifoFiles.isEmpty()) {
                    objectCount = ObjectLightmapBaker.bakeLoadedMap(
                        GameData.get(), mapFolder, mapSnapshot.ifoFiles, mapSnapshot.mapId);
                }
                final int terrainBaked = terrainCount;
                final int objectBaked = objectCount;
                Gdx.app.postRunnable(() -> applyBakedLightmaps(mapSnapshot, terrainBaked, objectBaked));
            } catch (Exception e) {
                Gdx.app.postRunnable(() -> {
                    statusLabel.setText("Lightmap bake failed: " + e.getMessage());
                    Gdx.app.error("EditorScreen", "Lightmap bake failed", e);
                });
            }
        });
    }

    /** Reloads GPU textures and rebuilds the scene on the render thread after a background bake. */
    private void applyBakedLightmaps(MapLoader.LoadedMap mapSnapshot, int terrainBaked, int objectBaked) {
        if (currentMap != mapSnapshot) {
            return;
        }
        try {
            reloadTerrainShadowMaps(mapSnapshot.blocks);
            if (currentMap.scene != null) {
                sceneRenderer.disposeScene(currentMap.scene);
            }
            currentMap.scene = new MapSceneBuilder().build(mapSnapshot);
            sceneRenderer.clearMapResources();
            sceneGpuPreloadTarget = null;
            renderOptions.setTerrainLightmap(true);
            updateTerrainLightmapButtonLabel();
            statusLabel.setText(String.format(
                "Baked %d terrain + %d object lightmap sector(s). Lightmaps enabled.",
                terrainBaked, objectBaked));
        } catch (Exception e) {
            statusLabel.setText("Lightmap bake failed: " + e.getMessage());
            Gdx.app.error("EditorScreen", "Lightmap apply failed", e);
        }
    }

    private static void reloadTerrainShadowMaps(HeightmapBlock[] blocks) {
        if (blocks == null) {
            return;
        }
        for (HeightmapBlock block : blocks) {
            if (block != null) {
                block.reloadShadowMap();
            }
        }
    }

    private void loadMap(int mapId) {
        final int generation = loadGeneration.incrementAndGet();
        loading = true;
        cameraFocusedForLoad = false;
        visibleBlocks.clear();

        disposeCurrentMap();
        heightBrushTool.clearTerrain();
        terrainRenderer.clearTileTextures();
        sceneRenderer.clearMapResources();
        skyRenderer.clearMapResources();
        GameData.get().clearMapCaches();
        currentMap = new MapLoader.LoadedMap();
        currentMap.mapId = mapId;

        activeLoad = new AsyncMapLoadResult();
        activeLoad.map.mapId = mapId;

        statusLabel.setText("Loading map " + mapId + "...");

        loadExecutor.execute(() -> {
            AsyncMapLoadResult result = activeLoad;
            try {
                mapLoader.streamMapAsync(mapId, result, generation, loadGeneration);
            } catch (Exception e) {
                if (generation == loadGeneration.get()) {
                    Gdx.app.error("MapLoader", "Load failed", e);
                    result.failed = true;
                    result.errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                }
            }
        });
        sceneGpuPreloadTarget = null;
    }

    private void applyLoadedMapMetadata(AsyncMapLoadResult result) {
        MapLoader.LoadedMap source = result.map;
        currentMap.name = source.name;
        currentMap.mapFolder = source.mapFolder;
        currentMap.zone = source.zone;
        currentMap.ifoFiles = source.ifoFiles;
        currentMap.ifoSummary = source.ifoSummary;
    }

    private void tickAsyncLoad() {
        if (activeLoad == null) {
            return;
        }

        if (activeLoad.failed) {
            statusLabel.setText("Load failed: " + activeLoad.errorMessage);
            loading = false;
            activeLoad = null;
            return;
        }

        if (activeLoad.metadataReady && currentMap.zone == null) {
            applyLoadedMapMetadata(activeLoad);
        }

        if (loading && currentMap.zone != null) {
            terrainRenderer.preloadZoneTextures(currentMap.zone, TILE_TEXTURES_PER_FRAME);
        }

        HeightmapBlock block;
        int budget = GPU_BLOCKS_PER_FRAME;
        while (budget-- > 0 && (block = activeLoad.preparedBlocks.poll()) != null) {
            block.finishGpuLoad();
            visibleBlocks.add(block);
        }

        if (!visibleBlocks.isEmpty()) {
            currentMap.blocks = visibleBlocks.toArray(new HeightmapBlock[0]);
            currentMap.blockCount = visibleBlocks.size();

            if (!cameraFocusedForLoad) {
                focusCameraOnMap(currentMap);
                cameraFocusedForLoad = true;
            }
        }

        if (activeLoad.sceneComplete && currentMap.scene == null) {
            currentMap.scene = activeLoad.scene;
            currentMap.ifoFiles = activeLoad.map.ifoFiles;
            currentMap.ifoSummary = activeLoad.map.ifoSummary;
            sceneGpuPreloadTarget = null;
        }

        int uploaded = visibleBlocks.size();
        int total = activeLoad.totalBlockCount;
        int prepared = activeLoad.blocksPrepared.get();

        if (activeLoad.isFullyComplete() && uploaded >= total) {
            currentMap.blocks = activeLoad.allBlocks.toArray(new HeightmapBlock[0]);
            currentMap.blockCount = activeLoad.allBlocks.size();
            bindHeightToolToMap();
            if (currentMap.blockCount == 0) {
                statusLabel.setText(String.format(
                    "Loaded map %d but found no .HIM terrain files in %s",
                    currentMap.mapId, currentMap.mapFolder));
            } else {
                statusLabel.setText(String.format("Loaded: %s | %d blocks | %s | %s",
                    currentMap.name, currentMap.blockCount, currentMap.ifoSummary,
                    currentMap.scene != null ? currentMap.scene.summary() : "scene=-"));
            }
            loading = false;
            activeLoad = null;
            return;
        }

        String phase;
        if (!activeLoad.blocksComplete) {
            phase = "reading terrain " + prepared + "/" + (total > 0 ? total : "?");
        } else if (uploaded < total) {
            phase = "uploading terrain " + uploaded + "/" + total;
        } else if (!activeLoad.sceneComplete) {
            phase = "loading objects...";
        } else if (!sceneRenderer.isScenePreloadComplete()) {
            int objectTotal = sceneRenderer.countScenePreloadTotal();
            int objectRemaining = sceneRenderer.countScenePreloadRemaining();
            phase = "uploading objects " + (objectTotal - objectRemaining) + "/" + objectTotal;
        } else {
            phase = "finishing...";
        }
        statusLabel.setText(String.format("Loading map %d: %s", currentMap.mapId, phase));
    }

    private void focusCameraOnMap(MapLoader.LoadedMap map) {
        if (map == null || map.zone == null) {
            return;
        }
        for (Zone.SpawnPoint spawn : map.zone.spawnPoints) {
            if ("start".equalsIgnoreCase(spawn.name)) {
                camera.setPosition(new Vector3(spawn.position).add(0, 0, 5));
                return;
            }
        }
        if (map.blocks != null && map.blocks.length > 0 && map.blocks[0] != null) {
            Vector3 center = map.blocks[0].bounds.getCenter(new Vector3());
            center.z += 10f;
            camera.setPosition(center);
        }
    }

    private void disposeCurrentMap() {
        if (currentMap != null && currentMap.scene != null) {
            skyRenderer.disposeScene(currentMap.scene);
            sceneRenderer.disposeScene(currentMap.scene);
        }
        if (currentMap != null && currentMap.blocks != null) {
            for (HeightmapBlock block : currentMap.blocks) {
                if (block != null) {
                    block.dispose();
                }
            }
        }
        currentMap = null;
        visibleBlocks.clear();
        sceneGpuPreloadTarget = null;
    }

    private void tickSceneResourcePreload() {
        if (currentMap == null || currentMap.scene == null) {
            sceneGpuPreloadTarget = null;
            return;
        }
        if (currentMap.scene != sceneGpuPreloadTarget) {
            sceneRenderer.beginScenePreload(currentMap.scene);
            sceneGpuPreloadTarget = currentMap.scene;
        }
        if (!sceneRenderer.isScenePreloadComplete()) {
            sceneRenderer.preloadSceneResources(OBJECT_MESHES_PER_FRAME, OBJECT_TEXTURES_PER_FRAME);
        }
    }

    @Override
    public void render(float delta) {
        tickAsyncLoad();
        tickSceneResourcePreload();

        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        camera.update(delta);
        sceneRenderer.update(currentMap != null ? currentMap.scene : null, delta);

        boolean drewSky = false;
        if (currentMap != null && currentMap.scene != null) {
            drewSky = skyRenderer.render(currentMap.scene, camera.skyCombined(skyMatrix));
        }
        if (!drewSky) {
            Gdx.gl.glClearColor(0.15f, 0.18f, 0.22f, 1f);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        }

        if (currentMap != null && currentMap.zone != null && currentMap.blockCount > 0) {
            if (renderOptions.isWireframe()) {
                WireframeGl.enable();
            }
            terrainRenderer.render(
                currentMap.blocks,
                currentMap.blockCount,
                camera.combined(),
                currentMap.zone
            );
        }

        if (currentMap != null && currentMap.scene != null) {
            sceneRenderer.render(currentMap.scene, camera.combined(), camera.gdxPosition(cameraGdx), camera);
        }

        WireframeGl.disable();

        if (heightBrushTool.isActive() && !isPointerOverUi()) {
            heightBrushTool.updatePointer(Gdx.input.getX(), Gdx.input.getY());
            heightBrushTool.renderPreview(camera.combined());
        }

        positionLabel.setText(String.format(
            "Camera: X=%.1f Y=%.1f Z=%.1f  [WASD move, Q/E up/down, Shift fast, RMB look, LMB inspect, H height, L lightmap, F wireframe, G terrain tex, O object tex, T soft diffuse, \r\nN NPC markers, M monster markers, X effects]",
            camera.position().x, camera.position().y, camera.position().z));

        if (Gdx.input.isKeyJustPressed(Input.Keys.H)) {
            toggleHeightTool();
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.L)) {
            toggleTerrainLightmap();
            updateTerrainLightmapButtonLabel();
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.F)) {
            toggleWireframe();
            updateWireframeButtonLabel();
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.G)) {
            toggleTerrainTextures();
            updateTerrainTexturesButtonLabel();
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.O)) {
            toggleObjectTextures();
            updateObjectTexturesButtonLabel();
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.T)) {
            toggleSoftDiffuse();
            updateSoftDiffuseButtonLabel();
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.N)) {
            toggleNpcMarkers();
            updateNpcMarkersButtonLabel();
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.M)) {
            toggleMonsterMarkers();
            updateMonsterMarkersButtonLabel();
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.X)) {
            toggleEffects();
            updateEffectsButtonLabel();
        }

        GlState.resetForUi();
        uiStage.act(delta);
        uiStage.draw();

        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            if (isModalOverlayOpen()) {
                closeModalOverlays();
            } else {
                Gdx.app.exit();
            }
        }

        WindowTitle.updateFps(delta, Gdx.graphics.getFramesPerSecond());
    }

    @Override
    public void resize(int width, int height) {
        camera.resize(width, height);
        uiStage.getViewport().update(width, height, true);
    }

    @Override
    public void dispose() {
        loadGeneration.incrementAndGet();
        loadExecutor.shutdownNow();
        disposeCurrentMap();
        activeLoad = null;
        terrainRenderer.dispose();
        skyRenderer.dispose();
        sceneRenderer.dispose();
        heightBrushTool.dispose();
        if (skin != null) {
            skin.dispose();
        }
        if (uiStage != null) {
            uiStage.dispose();
        }
    }
}
