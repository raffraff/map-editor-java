package com.rose.mapeditor.screen;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.rose.mapeditor.GameData;
import com.rose.mapeditor.map.FlatMapCreator;
import com.rose.mapeditor.map.MapCatalog;
import com.rose.mapeditor.map.MapSectorCatalog;
import com.rose.mapeditor.map.ProceduralMapGenerator;
import com.rose.mapeditor.ui.EditorSkinUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Dialog for procedural map generation from existing terrain. */
public final class ProceduralMapOverlay extends Table {

    private static final String[] MODE_LABELS = {
        "Region crop (natural)",
        "Random mosaic (varied)"
    };

    public interface Listener {
        void onMapCreated(int mapId);

        void onCancelled();
    }

    private final Label statusLabel;
    private final Label sourceInfoLabel;
    private final TextField nameField;
    private final TextField folderField;
    private final TextField zoneFileField;
    private final TextField seedField;
    private final SelectBox<String> sourceMapBox;
    private final SelectBox<String> planetBox;
    private final SelectBox<Integer> mapIdBox;
    private final SelectBox<Integer> skyBox;
    private final SelectBox<String> modeBox;
    private final Slider sizeXSlider;
    private final Slider sizeYSlider;
    private final Label sizeXLabel;
    private final Label sizeYLabel;
    private final CheckBox smoothSeamsCheck;
    private final CheckBox copyLightmapsCheck;
    private final CheckBox placeObjectsCheck;
    private final CheckBox includeConstructionsCheck;
    private final Slider objectsPerSectorSlider;
    private final Label objectsPerSectorLabel;
    private final List<MapCatalog.Entry> maps;

    public ProceduralMapOverlay(Skin skin, List<MapCatalog.Entry> maps, ProceduralMapGenerator.Request defaults,
                                Listener listener) {
        this.maps = maps;

        setFillParent(true);
        setBackground(UiDrawables.solid(new Color(0f, 0f, 0f, 0.55f)));

        Table panel = new Table(skin);
        panel.setBackground(UiDrawables.solid(new Color(0.12f, 0.14f, 0.18f, 0.98f)));
        panel.pad(12);

        Label title = new Label("Procedural Map", skin);
        title.setAlignment(Align.center);
        panel.add(title).expandX().fillX().padBottom(8).row();

        statusLabel = new Label(
            "Region crop copies a contiguous piece of the source map - best visual quality.", skin);
        statusLabel.setWrap(true);
        panel.add(statusLabel).width(540).padBottom(8).row();

        Table form = new Table(skin);
        form.defaults().left().pad(3);

        nameField = new TextField("", skin);
        folderField = new TextField("", skin);
        zoneFileField = new TextField(defaults.zoneFileName, skin);
        seedField = new TextField(Long.toString(defaults.seed), skin);

        sourceMapBox = new SelectBox<>(skin);
        planetBox = new SelectBox<>(skin);
        mapIdBox = new SelectBox<>(skin);
        skyBox = new SelectBox<>(skin);
        modeBox = new SelectBox<>(skin);
        modeBox.setItems(MODE_LABELS);

        List<String> sourceLabels = new ArrayList<>();
        for (MapCatalog.Entry entry : maps) {
            sourceLabels.add(String.format(Locale.ROOT, "[%d] %s", entry.id, entry.name));
        }
        sourceMapBox.setItems(sourceLabels.toArray(new String[0]));
        selectSourceMap(defaults.primarySourceMapId, maps);

        try {
            GameData data = GameData.get();
            planetBox.setItems(FlatMapCreator.listPlanetNames(data).toArray(new String[0]));
            mapIdBox.setItems(FlatMapCreator.listAvailableMapIds(data).toArray(new Integer[0]));
            skyBox.setItems(FlatMapCreator.listSkyIds(data).toArray(new Integer[0]));
        } catch (Exception e) {
            planetBox.setItems(defaults.planetName);
            mapIdBox.setItems(defaults.mapId);
            skyBox.setItems(defaults.skyId);
        }

        mapIdBox.setSelected(defaults.mapId);
        skyBox.setSelected(defaults.skyId);
        selectMode(defaults.generationMode);

        sizeXSlider = new Slider(1f, 8f, 1f, false, skin);
        sizeYSlider = new Slider(1f, 8f, 1f, false, skin);
        sizeXSlider.setValue(defaults.sizeX);
        sizeYSlider.setValue(defaults.sizeY);
        sizeXLabel = new Label(formatSizeLabel("X", defaults.sizeX), skin);
        sizeYLabel = new Label(formatSizeLabel("Y", defaults.sizeY), skin);

        smoothSeamsCheck = EditorSkinUtil.createCheckBox(skin, "Refine height seams", defaults.smoothSeams);
        copyLightmapsCheck = EditorSkinUtil.createCheckBox(skin, "Copy terrain lightmaps", defaults.copyLightmaps);
        placeObjectsCheck = EditorSkinUtil.createCheckBox(skin, "Place decorations", defaults.placeObjects);
        includeConstructionsCheck = EditorSkinUtil.createCheckBox(skin, "Include constructions",
            defaults.includeConstructions);
        objectsPerSectorSlider = new Slider(0f, 24f, 1f, false, skin);
        objectsPerSectorSlider.setValue(defaults.objectsPerSector);
        objectsPerSectorLabel = new Label(formatObjectsLabel(defaults.objectsPerSector), skin);

        sourceInfoLabel = new Label("", skin);
        sourceInfoLabel.setWrap(true);
        updateSourceInfo();
        updateModeUi();

        ChangeListener refreshSource = new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                updateSourceInfo();
            }
        };

        sizeXSlider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                sizeXLabel.setText(formatSizeLabel("X", Math.round(sizeXSlider.getValue())));
                updateSourceInfo();
            }
        });
        sizeYSlider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                sizeYLabel.setText(formatSizeLabel("Y", Math.round(sizeYSlider.getValue())));
                updateSourceInfo();
            }
        });

        sourceMapBox.addListener(refreshSource);
        modeBox.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                updateModeUi();
                updateSourceInfo();
            }
        });
        objectsPerSectorSlider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                objectsPerSectorLabel.setText(formatObjectsLabel(Math.round(objectsPerSectorSlider.getValue())));
            }
        });
        placeObjectsCheck.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                updateModeUi();
            }
        });

        addRow(form, "Source map", sourceMapBox);
        form.add(sourceInfoLabel).colspan(2).width(420).padBottom(6).row();
        addRow(form, "Name", nameField);
        addRow(form, "Map folder", folderField);
        addRow(form, "Planet", planetBox);
        addRow(form, "Map ID", mapIdBox);
        addRow(form, "Sky ID", skyBox);
        addRow(form, "Zone file", zoneFileField);
        addRow(form, "Method", modeBox);
        addRow(form, "Seed", seedField);

        Table sizeTable = new Table(skin);
        sizeTable.add(sizeXLabel).width(90);
        sizeTable.add(sizeXSlider).width(260);
        sizeTable.row();
        sizeTable.add(sizeYLabel).width(90);
        sizeTable.add(sizeYSlider).width(260);
        form.add(new Label("Size (sectors)", skin)).right();
        form.add(sizeTable).left().row();

        form.add(new Label("Options", skin)).right().top();
        Table options = new Table(skin);
        options.add(smoothSeamsCheck).left().row();
        options.add(copyLightmapsCheck).left().row();
        options.add(placeObjectsCheck).left().row();
        options.add(includeConstructionsCheck).left().row();
        Table objectDensity = new Table(skin);
        objectDensity.add(objectsPerSectorLabel).width(160);
        objectDensity.add(objectsPerSectorSlider).width(260);
        options.add(objectDensity).left().row();
        form.add(options).left().row();

        ScrollPane scroll = new ScrollPane(form, skin);
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);
        panel.add(scroll).width(560).height(460).padBottom(8).row();

        Table buttons = new Table(skin);
        TextButton generateButton = new TextButton("Generate", skin);
        generateButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                generateMap(listener);
            }
        });

        TextButton cancelButton = new TextButton("Cancel", skin);
        cancelButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                listener.onCancelled();
                remove();
            }
        });

        buttons.add(generateButton).width(100).padRight(8);
        buttons.add(cancelButton).width(100);
        panel.add(buttons).right().row();

        add(panel).center();
    }

    private void updateModeUi() {
        boolean mosaic = selectedMode() == ProceduralMapGenerator.GenerationMode.MOSAIC;
        boolean placeObjects = placeObjectsCheck.isChecked();
        smoothSeamsCheck.setDisabled(!mosaic);
        includeConstructionsCheck.setDisabled(!placeObjects);
        objectsPerSectorSlider.setDisabled(!placeObjects || !mosaic);
        if (mosaic) {
            statusLabel.setText(
                "Mosaic picks edge-compatible sectors, cross-fades heights at seams, and can scatter random props.");
            copyLightmapsCheck.setChecked(false);
        } else {
            statusLabel.setText(
                "Region crop copies terrain and decorations from a contiguous source region.");
            copyLightmapsCheck.setChecked(true);
        }
    }

    private void generateMap(Listener listener) {
        try {
            int sourceMapId = parseSourceMapId(sourceMapBox.getSelected());
            ProceduralMapGenerator.Request request = ProceduralMapGenerator.defaultRequest(
                GameData.get(), sourceMapId);
            request.name = nameField.getText().trim();
            request.mapFolder = folderField.getText().trim();
            request.planetName = planetBox.getSelected();
            request.planetId = planetBox.getSelectedIndex() + 1;
            request.mapId = mapIdBox.getSelected();
            request.skyId = skyBox.getSelected();
            request.zoneFileName = zoneFileField.getText().trim();
            request.sizeX = Math.round(sizeXSlider.getValue());
            request.sizeY = Math.round(sizeYSlider.getValue());
            request.generationMode = selectedMode();
            request.smoothSeams = smoothSeamsCheck.isChecked();
            request.copyLightmaps = copyLightmapsCheck.isChecked();
            request.placeObjects = placeObjectsCheck.isChecked();
            request.includeConstructions = includeConstructionsCheck.isChecked();
            request.objectsPerSector = Math.round(objectsPerSectorSlider.getValue());
            request.seed = Long.parseLong(seedField.getText().trim());
            request.primarySourceMapId = sourceMapId;
            request.sourceMapIds.clear();
            request.sourceMapIds.add(sourceMapId);

            FlatMapCreator.Result result = ProceduralMapGenerator.generate(GameData.get(), request);
            statusLabel.setText("Generated map " + result.mapId + " at " + result.mapFolderRelative);
            listener.onMapCreated(result.mapId);
            remove();
        } catch (NumberFormatException e) {
            statusLabel.setText("Seed must be a whole number.");
        } catch (Exception e) {
            statusLabel.setText("Generate failed: " + e.getMessage());
        }
    }

    private ProceduralMapGenerator.GenerationMode selectedMode() {
        int index = modeBox.getSelectedIndex();
        if (index < 0 || index >= ProceduralMapGenerator.GenerationMode.values().length) {
            return ProceduralMapGenerator.GenerationMode.REGION;
        }
        return ProceduralMapGenerator.GenerationMode.values()[index];
    }

    private void selectMode(ProceduralMapGenerator.GenerationMode mode) {
        modeBox.setSelectedIndex(mode.ordinal());
    }

    private void updateSourceInfo() {
        try {
            int sourceMapId = parseSourceMapId(sourceMapBox.getSelected());
            List<MapSectorCatalog.SectorRef> sectors = MapSectorCatalog.listSectorsForMap(
                GameData.get(), sourceMapId);
            int sizeX = Math.max(1, Math.round(sizeXSlider.getValue()));
            int sizeY = Math.max(1, Math.round(sizeYSlider.getValue()));
            int regions = MapSectorCatalog.countValidRegions(sectors, sizeX, sizeY);
            sourceInfoLabel.setText(String.format(Locale.ROOT,
                "%d sector(s) in source. %d possible %d×%d region crop(s) at current size.",
                sectors.size(), regions, sizeX, sizeY));
        } catch (Exception e) {
            sourceInfoLabel.setText("Could not read source sectors: " + e.getMessage());
        }
    }

    private static int parseSourceMapId(String label) {
        int end = label.indexOf(']');
        if (end <= 1) {
            throw new IllegalArgumentException("Invalid source map selection.");
        }
        return Integer.parseInt(label.substring(1, end));
    }

    private void selectSourceMap(int mapId, List<MapCatalog.Entry> entryList) {
        for (MapCatalog.Entry entry : entryList) {
            if (entry.id == mapId) {
                sourceMapBox.setSelected(String.format(Locale.ROOT, "[%d] %s", entry.id, entry.name));
                return;
            }
        }
        if (!entryList.isEmpty()) {
            MapCatalog.Entry first = entryList.get(0);
            sourceMapBox.setSelected(String.format(Locale.ROOT, "[%d] %s", first.id, first.name));
        }
    }

    private static void addRow(Table form, String label, Actor field) {
        form.add(new Label(label, form.getSkin())).right().padRight(8);
        form.add(field).width(360).left().row();
    }

    private static String formatSizeLabel(String axis, int sectors) {
        return String.format(Locale.ROOT, "%s: %d (%dm)", axis, sectors, sectors * 160);
    }

    private static String formatObjectsLabel(int count) {
        return String.format(Locale.ROOT, "Mosaic props/sector: %d", count);
    }
}
