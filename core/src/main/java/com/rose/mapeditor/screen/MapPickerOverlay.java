package com.rose.mapeditor.screen;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.rose.mapeditor.map.MapCatalog;

import java.util.List;

/** Scrollable map list from LIST_ZONE.STB. */
public final class MapPickerOverlay extends Table {

    public interface Listener {
        void onMapChosen(int mapId);

        void onCancelled();
    }

    private final Label statusLabel;
    private int selectedMapId = -1;

    public MapPickerOverlay(Skin skin, List<MapCatalog.Entry> entries, Listener listener) {
        setFillParent(true);
        setBackground(UiDrawables.solid(new Color(0f, 0f, 0f, 0.55f)));

        Table panel = new Table(skin);
        panel.setBackground(UiDrawables.solid(new Color(0.12f, 0.14f, 0.18f, 0.98f)));
        panel.pad(12);

        Label title = new Label("Open Map", skin);
        title.setAlignment(Align.center);
        panel.add(title).expandX().fillX().padBottom(8).row();

        statusLabel = new Label(entries.size() + " maps - click to select, double-click or Load to open", skin);
        statusLabel.setWrap(true);
        statusLabel.setAlignment(Align.left);
        panel.add(statusLabel).width(420).padBottom(8).row();

        Table listTable = new Table(skin);
        listTable.top().left();
        listTable.defaults().left().pad(2);

        for (MapCatalog.Entry entry : entries) {
            String label = String.format("%4d  %s", entry.id, entry.name);
            TextButton row = new TextButton(label, skin);
            row.getLabel().setAlignment(Align.left);
            row.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    selectedMapId = entry.id;
                    statusLabel.setText("Selected: [" + entry.id + "] " + entry.name);
                    if (getTapCount() >= 2) {
                        listener.onMapChosen(entry.id);
                        remove();
                    }
                }
            });
            listTable.add(row).width(420).row();
        }

        ScrollPane scroll = new ScrollPane(listTable);
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);
        panel.add(scroll).width(440).height(360).padBottom(8).row();

        Table buttons = new Table(skin);
        TextButton loadButton = new TextButton("Load", skin);
        loadButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (selectedMapId >= 0) {
                    listener.onMapChosen(selectedMapId);
                    remove();
                } else {
                    statusLabel.setText("Select a map from the list first.");
                }
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

        buttons.add(loadButton).width(100).padRight(8);
        buttons.add(cancelButton).width(100);
        panel.add(buttons).right().row();

        add(panel).center();
    }

    public boolean isShowing() {
        return getStage() != null;
    }
}
