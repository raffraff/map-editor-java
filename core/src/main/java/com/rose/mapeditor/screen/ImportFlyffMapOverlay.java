package com.rose.mapeditor.screen;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
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
import com.rose.mapeditor.map.flyff.FlyffMapImporter;
import com.rose.mapeditor.map.flyff.FlyffWldReader;

import java.nio.file.Path;
import java.util.Locale;

/** Imports Flyff terrain (.wld + .lnd) into Rose map folders. */
public final class ImportFlyffMapOverlay extends Table {

    public interface Listener {
        void onMapImported(int mapId, String summary);

        void onCancelled();
    }

    private final Label statusLabel;
    private final Label sizeLabel;
    private final TextField flyffPathField;
    private final TextField flyffRootField;
    private final TextField flyffTerrainIncField;
    private final TextField nameField;
    private final TextField folderField;
    private final TextField zoneFileField;
    private final TextField tileTemplateField;
    private final TextField decorationField;
    private final TextField constructionField;
    private final SelectBox<String> planetBox;
    private final SelectBox<Integer> mapIdBox;
    private final SelectBox<Integer> skyBox;
    private final Slider heightScaleSlider;
    private final Label heightScaleLabel;

    public ImportFlyffMapOverlay(Skin skin, FlatMapCreator.Request defaults, Listener listener) {
        setFillParent(true);
        setBackground(UiDrawables.solid(new Color(0f, 0f, 0f, 0.55f)));

        Table panel = new Table(skin);
        panel.setBackground(UiDrawables.solid(new Color(0.12f, 0.14f, 0.18f, 0.98f)));
        panel.pad(12);

        Label title = new Label("Import Flyff Map", skin);
        title.setAlignment(Align.center);
        panel.add(title).expandX().fillX().padBottom(8).row();

        statusLabel = new Label(
            "Imports Flyff terrain with native Rose 2-layer blending (alpha baked into top DDS).", skin);
        statusLabel.setWrap(true);
        panel.add(statusLabel).width(560).padBottom(8).row();

        Table form = new Table(skin);
        form.defaults().left().pad(3);

        flyffPathField = new TextField("", skin);
        flyffRootField = new TextField("", skin);
        flyffTerrainIncField = new TextField("", skin);
        nameField = new TextField("", skin);
        folderField = new TextField("", skin);
        zoneFileField = new TextField(defaults.zoneFileName, skin);
        tileTemplateField = new TextField(defaults.tileTemplatePath, skin);
        decorationField = new TextField(defaults.decorationPath, skin);
        constructionField = new TextField(defaults.constructionPath, skin);

        planetBox = new SelectBox<>(skin);
        mapIdBox = new SelectBox<>(skin);
        skyBox = new SelectBox<>(skin);

        try {
            GameData data = GameData.get();
            planetBox.setItems(FlatMapCreator.listPlanetNames(data).toArray(new String[0]));
            mapIdBox.setItems(FlatMapCreator.listAvailableMapIds(data).toArray(new Integer[0]));
            skyBox.setItems(FlatMapCreator.listSkyIds(data).toArray(new Integer[0]));
        } catch (Exception e) {
            planetBox.setItems("JUNON");
            mapIdBox.setItems(defaults.mapId);
            skyBox.setItems(defaults.skyId);
            statusLabel.setText("Warning: could not load defaults: " + e.getMessage());
        }

        mapIdBox.setSelected(defaults.mapId);
        skyBox.setSelected(defaults.skyId);

        heightScaleSlider = new Slider(0.1f, 4.0f, 0.05f, false, skin);
        heightScaleSlider.setValue(1.0f);
        heightScaleLabel = new Label(formatHeightScale(1.0f), skin);
        heightScaleSlider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                heightScaleLabel.setText(formatHeightScale(heightScaleSlider.getValue()));
            }
        });

        sizeLabel = new Label("Flyff size: (enter .wld path)", skin);
        sizeLabel.setWrap(true);

        flyffPathField.setTextFieldListener((field, c) -> updateFlyffPreview());

        addRow(form, "Flyff .wld path", flyffPathField);
        addRow(form, "Flyff client root", flyffRootField);
        addRow(form, "Terrain .inc (optional)", flyffTerrainIncField);
        addRow(form, "Rose map name", nameField);
        addRow(form, "Rose map folder", folderField);
        addRow(form, "Planet", planetBox);
        addRow(form, "Map ID", mapIdBox);
        addRow(form, "Sky ID", skyBox);
        addRow(form, "Zone file", zoneFileField);
        addRow(form, "Tile template (.ZON)", tileTemplateField);
        addRow(form, "Decoration ZSC", decorationField);
        addRow(form, "Construction ZSC", constructionField);

        Table scaleRow = new Table(skin);
        scaleRow.add(heightScaleLabel).width(90);
        scaleRow.add(heightScaleSlider).width(260);
        form.add(new Label("Height scale", skin)).right();
        form.add(scaleRow).left().row();

        form.add(new Label("Conversion", skin)).right().top();
        form.add(sizeLabel).width(360).left().row();

        ScrollPane scroll = new ScrollPane(form, skin);
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);
        panel.add(scroll).width(580).height(500).padBottom(8).row();

        Table buttons = new Table(skin);
        TextButton importButton = new TextButton("Import", skin);
        importButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                runImport(listener);
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

        buttons.add(importButton).width(100).padRight(8);
        buttons.add(cancelButton).width(100);
        panel.add(buttons).right().row();

        add(panel).center();
    }

    private void runImport(Listener listener) {
        try {
            FlyffMapImporter.Request request = new FlyffMapImporter.Request();
            request.flyffWldPath = flyffPathField.getText().trim();
            request.flyffRootPath = flyffRootField.getText().trim();
            request.flyffTerrainIncPath = flyffTerrainIncField.getText().trim();
            request.name = nameField.getText().trim();
            request.mapFolder = folderField.getText().trim();
            request.planetName = planetBox.getSelected();
            request.planetId = planetBox.getSelectedIndex() + 1;
            request.mapId = mapIdBox.getSelected();
            request.skyId = skyBox.getSelected();
            request.zoneFileName = zoneFileField.getText().trim();
            request.tileTemplatePath = tileTemplateField.getText().trim();
            request.decorationPath = decorationField.getText().trim();
            request.constructionPath = constructionField.getText().trim();
            request.heightScale = heightScaleSlider.getValue();

            FlyffMapImporter.Result result = FlyffMapImporter.importTerrain(GameData.get(), request);
            String summary = String.format(Locale.ROOT,
                "Imported Flyff %dx%d (%d texture/blend tiles) -> %d Rose sectors at %s",
                result.flyffWidth, result.flyffHeight, result.importedTextureCount,
                result.sectorCount, result.mapFolderRelative);
            listener.onMapImported(result.mapId, summary);
            remove();
        } catch (Exception e) {
            statusLabel.setText("Import failed: " + e.getMessage());
        }
    }

    private void updateFlyffPreview() {
        String pathText = flyffPathField.getText().trim();
        if (pathText.isEmpty()) {
            sizeLabel.setText("Flyff size: (enter .wld path)");
            return;
        }
        try {
            FlyffWldReader.WorldHeader header = FlyffWldReader.read(Path.of(pathText));
            if (nameField.getText().trim().isEmpty()) {
                nameField.setText(header.worldBaseName);
            }
            if (folderField.getText().trim().isEmpty()) {
                folderField.setText(header.worldBaseName.toLowerCase(Locale.ROOT) + "_flyff");
            }
            if (zoneFileField.getText().trim().equals("NEWMAP.ZON")
                || zoneFileField.getText().trim().isEmpty()) {
                zoneFileField.setText(header.worldBaseName.toUpperCase(Locale.ROOT) + ".ZON");
            }
            if (flyffRootField.getText().trim().isEmpty()) {
                Path parent = Path.of(pathText).getParent();
                if (parent != null) {
                    if ("World".equalsIgnoreCase(String.valueOf(parent.getFileName()))
                        && parent.getParent() != null) {
                        flyffRootField.setText(parent.getParent().toString());
                    } else {
                        flyffRootField.setText(parent.toString());
                    }
                }
            }
            sizeLabel.setText(String.format(Locale.ROOT,
                "Flyff grid: %d x %d land tiles\nRose output: %d x %d sectors (%d total)\n"
                    + "Textures: 2-layer Rose blend (base + baked overlay alpha in top DDS)",
                header.width, header.height, header.width * 2, header.height * 2,
                header.width * header.height * 4));
        } catch (Exception e) {
            sizeLabel.setText("Could not read .wld: " + e.getMessage());
        }
    }

    private static void addRow(Table form, String label, Actor field) {
        form.add(new Label(label, form.getSkin())).right().padRight(8);
        form.add(field).width(360).left().row();
    }

    private static String formatHeightScale(float value) {
        return String.format(Locale.ROOT, "%.2fx", value);
    }
}
