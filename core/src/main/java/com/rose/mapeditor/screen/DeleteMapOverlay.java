package com.rose.mapeditor.screen;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.rose.mapeditor.GameData;
import com.rose.mapeditor.map.MapCatalog;
import com.rose.mapeditor.map.MapDeleter;

import java.util.List;
import java.util.Locale;

/** Map picker with delete action and confirmation step. */
public final class DeleteMapOverlay extends Table {

    public interface Listener {
        void onMapDeleted(int mapId, String mapName);

        void onCancelled();
    }

    private final Label statusLabel;
    private final List<MapCatalog.Entry> entries;
    private int selectedMapId = -1;
    private String selectedMapName = "";

    public DeleteMapOverlay(Skin skin, List<MapCatalog.Entry> entries, Listener listener) {
        this.entries = entries;

        setFillParent(true);
        setBackground(UiDrawables.solid(new Color(0f, 0f, 0f, 0.55f)));

        Table panel = new Table(skin);
        panel.setBackground(UiDrawables.solid(new Color(0.12f, 0.14f, 0.18f, 0.98f)));
        panel.pad(12);

        Label title = new Label("Delete Map", skin);
        title.setAlignment(Align.center);
        panel.add(title).expandX().fillX().padBottom(8).row();

        statusLabel = new Label(
            entries.size() + " maps - select a map, then click Delete (confirmation required)", skin);
        statusLabel.setWrap(true);
        statusLabel.setAlignment(Align.left);
        panel.add(statusLabel).width(420).padBottom(8).row();

        Table listTable = new Table(skin);
        listTable.top().left();
        listTable.defaults().left().pad(2);

        for (MapCatalog.Entry entry : entries) {
            String label = String.format(Locale.ROOT, "%4d  %s", entry.id, entry.name);
            TextButton row = new TextButton(label, skin);
            row.getLabel().setAlignment(Align.left);
            row.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    selectedMapId = entry.id;
                    selectedMapName = entry.name;
                    statusLabel.setText("Selected: [" + entry.id + "] " + entry.name);
                }
            });
            listTable.add(row).width(420).row();
        }

        ScrollPane scroll = new ScrollPane(listTable, skin);
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);
        panel.add(scroll).width(440).height(360).padBottom(8).row();

        Table buttons = new Table(skin);
        TextButton deleteButton = new TextButton("Delete...", skin);
        deleteButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (selectedMapId < 0) {
                    statusLabel.setText("Select a map from the list first.");
                    return;
                }
                showConfirmation(skin, listener);
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

        buttons.add(deleteButton).width(100).padRight(8);
        buttons.add(cancelButton).width(100);
        panel.add(buttons).right().row();

        add(panel).center();
    }

    private void showConfirmation(Skin skin, Listener listener) {
        Stage stage = getStage();
        if (stage == null) {
            return;
        }

        MapCatalog.Entry selected = findSelectedEntry();
        String folderHint = selected != null ? selected.zonePath : "";

        String message = String.format(Locale.ROOT,
            "Permanently delete map [%d] %s?\n\n"
                + "This removes all files under:\n%s\n\n"
                + "The LIST_ZONE and LIST_ZONE_S entries will be cleared.\n"
                + "This action cannot be undone.",
            selectedMapId, selectedMapName, folderHint);

        ConfirmDialogOverlay confirm = new ConfirmDialogOverlay(skin, "Confirm Delete", message,
            new ConfirmDialogOverlay.Listener() {
                @Override
                public void onConfirmed() {
                    try {
                        MapDeleter.Result result = MapDeleter.delete(GameData.get(), selectedMapId);
                        listener.onMapDeleted(result.mapId, result.mapName);
                        remove();
                    } catch (Exception e) {
                        statusLabel.setText("Delete failed: " + e.getMessage());
                    }
                }

                @Override
                public void onCancelled() {
                    statusLabel.setText("Delete cancelled. Selected: [" + selectedMapId + "] " + selectedMapName);
                }
            });
        stage.addActor(confirm);
    }

    private MapCatalog.Entry findSelectedEntry() {
        for (MapCatalog.Entry entry : entries) {
            if (entry.id == selectedMapId) {
                return entry;
            }
        }
        return null;
    }
}
