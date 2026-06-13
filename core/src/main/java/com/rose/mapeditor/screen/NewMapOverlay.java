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

import java.util.Locale;

/** Form for creating a new flat-terrain map. */
public final class NewMapOverlay extends Table {

    public interface Listener {
        void onMapCreated(int mapId);

        void onCancelled();
    }

    private final Label statusLabel;
    private final TextField nameField;
    private final TextField folderField;
    private final TextField zoneFileField;
    private final TextField tileTemplateField;
    private final TextField decorationField;
    private final TextField constructionField;
    private final SelectBox<String> planetBox;
    private final SelectBox<Integer> mapIdBox;
    private final SelectBox<Integer> skyBox;
    private final Slider sizeXSlider;
    private final Slider sizeYSlider;
    private final Label sizeXLabel;
    private final Label sizeYLabel;
    private final Label pathLabel;

    public NewMapOverlay(Skin skin, FlatMapCreator.Request defaults, Listener listener) {
        setFillParent(true);
        setBackground(UiDrawables.solid(new Color(0f, 0f, 0f, 0.55f)));

        Table panel = new Table(skin);
        panel.setBackground(UiDrawables.solid(new Color(0.12f, 0.14f, 0.18f, 0.98f)));
        panel.pad(12);

        Label title = new Label("New Flat Map", skin);
        title.setAlignment(Align.center);
        panel.add(title).expandX().fillX().padBottom(8).row();

        statusLabel = new Label("Creates HIM/TIL/IFO/MOV sectors with flat height and default grass tile.", skin);
        statusLabel.setWrap(true);
        panel.add(statusLabel).width(520).padBottom(8).row();

        Table form = new Table(skin);
        form.defaults().left().pad(3);

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

        sizeXSlider = new Slider(1f, 8f, 1f, false, skin);
        sizeYSlider = new Slider(1f, 8f, 1f, false, skin);
        sizeXSlider.setValue(defaults.sizeX);
        sizeYSlider.setValue(defaults.sizeY);
        sizeXLabel = new Label(formatSizeLabel("X", defaults.sizeX), skin);
        sizeYLabel = new Label(formatSizeLabel("Y", defaults.sizeY), skin);

        sizeXSlider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                sizeXLabel.setText(formatSizeLabel("X", Math.round(sizeXSlider.getValue())));
                updatePathPreview();
            }
        });
        sizeYSlider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                sizeYLabel.setText(formatSizeLabel("Y", Math.round(sizeYSlider.getValue())));
                updatePathPreview();
            }
        });

        addRow(form, "Name", nameField);
        addRow(form, "Map folder", folderField);
        addRow(form, "Planet", planetBox);
        addRow(form, "Map ID", mapIdBox);
        addRow(form, "Sky ID", skyBox);
        addRow(form, "Zone file", zoneFileField);
        addRow(form, "Tile template (.ZON)", tileTemplateField);
        addRow(form, "Decoration ZSC", decorationField);
        addRow(form, "Construction ZSC", constructionField);

        Table sizeTable = new Table(skin);
        sizeTable.add(sizeXLabel).width(90);
        sizeTable.add(sizeXSlider).width(260);
        sizeTable.row();
        sizeTable.add(sizeYLabel).width(90);
        sizeTable.add(sizeYSlider).width(260);
        form.add(new Label("Size (sectors)", skin)).right();
        form.add(sizeTable).left().row();

        pathLabel = new Label(buildPathPreview(), skin);
        pathLabel.setWrap(true);
        form.add(new Label("Output path", skin)).right().top();
        form.add(pathLabel).width(360).left().row();

        folderField.setTextFieldListener((field, c) -> updatePathPreview());
        planetBox.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                updatePathPreview();
            }
        });

        ScrollPane scroll = new ScrollPane(form, skin);
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);
        panel.add(scroll).width(540).height(420).padBottom(8).row();

        Table buttons = new Table(skin);
        TextButton createButton = new TextButton("Create", skin);
        createButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                createMap(listener);
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

        buttons.add(createButton).width(100).padRight(8);
        buttons.add(cancelButton).width(100);
        panel.add(buttons).right().row();

        add(panel).center();
    }

    private void createMap(Listener listener) {
        try {
            FlatMapCreator.Request request = new FlatMapCreator.Request();
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
            request.sizeX = Math.round(sizeXSlider.getValue());
            request.sizeY = Math.round(sizeYSlider.getValue());

            FlatMapCreator.Result result = FlatMapCreator.create(GameData.get(), request);
            statusLabel.setText("Created map " + result.mapId + " at " + result.mapFolderRelative);
            listener.onMapCreated(result.mapId);
            remove();
        } catch (Exception e) {
            statusLabel.setText("Create failed: " + e.getMessage());
        }
    }

    private void updatePathPreview() {
        pathLabel.setText(buildPathPreview());
    }

    private String buildPathPreview() {
        String planet = planetBox.getSelected();
        if (planet == null || planet.isBlank()) {
            planet = "JUNON";
        }
        String folder = folderField.getText().trim();
        if (folder.isEmpty()) {
            folder = "<folder>";
        }
        int sizeX = Math.max(1, Math.round(sizeXSlider.getValue()));
        int sizeY = Math.max(1, Math.round(sizeYSlider.getValue()));
        return String.format(Locale.ROOT,
            "3Ddata/MAPS/%s/%s/\n%d sector(s) (%dx%d), flat HIM height 0",
            planet.toUpperCase(Locale.ROOT), folder, sizeX * sizeY, sizeX, sizeY);
    }

    private static void addRow(Table form, String label, Actor field) {
        form.add(new Label(label, form.getSkin())).right().padRight(8);
        form.add(field).width(360).left().row();
    }

    private static String formatSizeLabel(String axis, int sectors) {
        return String.format(Locale.ROOT, "%s: %d (%dm)", axis, sectors, sectors * 160);
    }

    public boolean isShowing() {
        return getStage() != null;
    }
}
