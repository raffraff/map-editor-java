package com.rose.mapeditor.ui;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.rose.mapeditor.tool.HeightBrushMode;
import com.rose.mapeditor.tool.HeightBrushSettings;
import com.rose.mapeditor.tool.HeightBrushShape;
import com.rose.mapeditor.tool.HeightBrushTool;

/** Scene2D panel for the libGDX height brush tool. */
public final class HeightBrushPanel extends Table {

    public interface Listener {
        void onSaveRequested();
    }

    private final HeightBrushTool tool;
    private final Listener listener;
    private final Label innerValueLabel;
    private final Label outerValueLabel;
    private final Label powerValueLabel;

    public HeightBrushPanel(Skin skin, HeightBrushTool tool, Listener listener) {
        this.tool = tool;
        this.listener = listener;
        left().padTop(4).padLeft(16);

        SelectBox<HeightBrushMode> modeBox = new SelectBox<>(skin);
        Array<HeightBrushMode> modes = new Array<>();
        for (HeightBrushMode mode : HeightBrushMode.values()) {
            modes.add(mode);
        }
        modeBox.setItems(modes);
        modeBox.setSelected(HeightBrushMode.RAISE);
        modeBox.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                HeightBrushSettings settings = tool.settings();
                if (settings != null) {
                    settings.mode = modeBox.getSelected();
                }
            }
        });

        SelectBox<HeightBrushShape> shapeBox = new SelectBox<>(skin);
        Array<HeightBrushShape> shapes = new Array<>();
        for (HeightBrushShape shape : HeightBrushShape.values()) {
            shapes.add(shape);
        }
        shapeBox.setItems(shapes);
        shapeBox.setSelected(HeightBrushShape.SQUARE);
        shapeBox.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                HeightBrushSettings settings = tool.settings();
                if (settings != null) {
                    settings.shape = shapeBox.getSelected();
                }
            }
        });

        innerValueLabel = new Label("3", skin);
        outerValueLabel = new Label("7", skin);
        powerValueLabel = new Label("5", skin);

        Slider innerSlider = createRadiusSlider(skin, 3, true);
        Slider outerSlider = createRadiusSlider(skin, 7, false);
        Slider powerSlider = createPowerSlider(skin);

        TextButton saveButton = new TextButton("Save HIM files", skin);
        saveButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (listener != null) {
                    listener.onSaveRequested();
                }
            }
        });

        final int panelWidth = 200;

        add(new Label("Mode", skin)).left().padBottom(2).row();
        add(modeBox).width(panelWidth).left().padBottom(4).row();
        add(new Label("Shape", skin)).left().padBottom(2).row();
        add(shapeBox).width(panelWidth).left().padBottom(4).row();
        add(sliderRow(skin, "Inner", innerSlider, innerValueLabel)).width(panelWidth).left().padBottom(6).row();
        add(sliderRow(skin, "Outer", outerSlider, outerValueLabel)).width(panelWidth).left().padBottom(6).row();
        add(sliderRow(skin, "Power", powerSlider, powerValueLabel)).width(panelWidth).left().padBottom(6).row();
        add(saveButton).width(panelWidth).left();

        setVisible(false);
    }

    public void syncFromTool() {
        HeightBrushSettings settings = tool.settings();
        if (settings == null) {
            return;
        }
        innerValueLabel.setText(String.valueOf(settings.innerRadius));
        outerValueLabel.setText(String.valueOf(settings.outerRadius));
        powerValueLabel.setText(String.valueOf(settings.power));
    }

    private Slider createRadiusSlider(Skin skin, float initial, boolean inner) {
        Slider slider = new Slider(1f, 20f, 1f, false, skin);
        slider.setValue(initial);
        slider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                HeightBrushSettings settings = tool.settings();
                if (settings == null) {
                    return;
                }
                int value = Math.round(slider.getValue());
                if (inner) {
                    settings.innerRadius = value;
                    if (settings.innerRadius > settings.outerRadius) {
                        settings.outerRadius = settings.innerRadius;
                        outerValueLabel.setText(String.valueOf(settings.outerRadius));
                    }
                    innerValueLabel.setText(String.valueOf(value));
                } else {
                    settings.outerRadius = value;
                    if (settings.outerRadius < settings.innerRadius) {
                        settings.innerRadius = settings.outerRadius;
                        innerValueLabel.setText(String.valueOf(settings.innerRadius));
                    }
                    outerValueLabel.setText(String.valueOf(value));
                }
            }
        });
        return slider;
    }

    private Slider createPowerSlider(Skin skin) {
        Slider slider = new Slider(1f, 100f, 1f, false, skin);
        slider.setValue(5f);
        slider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                HeightBrushSettings settings = tool.settings();
                if (settings == null) {
                    return;
                }
                settings.power = Math.round(slider.getValue());
                powerValueLabel.setText(String.valueOf(settings.power));
            }
        });
        return slider;
    }

    private static Table sliderRow(Skin skin, String name, Slider slider, Label valueLabel) {
        Table row = new Table(skin);
        Table header = new Table(skin);
        header.add(new Label(name, skin)).expandX().left();
        header.add(valueLabel).right();
        row.add(header).growX().left().padBottom(2).row();
        row.add(slider).height(12).growX().fillX().left();
        return row;
    }
}
