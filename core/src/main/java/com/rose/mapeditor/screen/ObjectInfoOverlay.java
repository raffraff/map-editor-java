package com.rose.mapeditor.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextArea;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;

/** Read-only dialog showing metadata for a picked map object. */
public final class ObjectInfoOverlay extends Table {

    public ObjectInfoOverlay(Skin skin, String title, String infoText) {
        setFillParent(true);
        setBackground(UiDrawables.solid(new Color(0f, 0f, 0f, 0.65f)));

        Table panel = new Table(skin);
        panel.setBackground(UiDrawables.solid(new Color(0.14f, 0.12f, 0.12f, 0.98f)));
        panel.pad(16);

        Label titleLabel = new Label(title, skin);
        titleLabel.setAlignment(Align.center);
        panel.add(titleLabel).expandX().fillX().padBottom(10).row();

        int lineCount = infoText.split("\n", -1).length;
        TextArea textArea = createReadOnlyTextArea(skin, infoText, lineCount);
        ScrollPane scrollPane = new ScrollPane(textArea, skin);
        scrollPane.setFadeScrollBars(false);
        scrollPane.setScrollingDisabled(true, false);
        panel.add(scrollPane).width(560).height(Math.min(360, Math.max(140, lineCount * 18 + 12))).left().padBottom(8).row();

        Label hintLabel = new Label("Select text and Ctrl+C, or use Copy all.", skin);
        hintLabel.setColor(0.75f, 0.75f, 0.75f, 1f);
        panel.add(hintLabel).left().padBottom(10).row();

        Label copyStatusLabel = new Label("", skin);
        copyStatusLabel.setColor(0.55f, 1f, 0.55f, 1f);

        Table buttons = new Table(skin);
        TextButton copyButton = new TextButton("Copy all", skin);
        copyButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                Gdx.app.getClipboard().setContents(infoText);
                copyStatusLabel.setText("Copied to clipboard.");
            }
        });

        TextButton closeButton = new TextButton("Close", skin);
        closeButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                remove();
            }
        });

        buttons.add(copyButton).width(90).padRight(8);
        buttons.add(closeButton).width(90).padRight(12);
        buttons.add(copyStatusLabel).left().expandX();
        panel.add(buttons).expandX().fillX().row();

        add(panel).center();
    }

    private static TextArea createReadOnlyTextArea(Skin skin, String text, int lineCount) {
        TextArea textArea = new TextArea(text, skin);
        textArea.setPrefRows(Math.min(18, Math.max(6, lineCount)));
        textArea.setCursorPosition(0);
        textArea.setSelection(0, 0);
        textArea.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if (isControlDown()) {
                    return false;
                }
                return keycode == Input.Keys.BACKSPACE
                    || keycode == Input.Keys.FORWARD_DEL
                    || keycode == Input.Keys.ENTER
                    || keycode == Input.Keys.NUMPAD_ENTER
                    || keycode == Input.Keys.TAB;
            }

            @Override
            public boolean keyTyped(InputEvent event, char character) {
                if (isControlDown()) {
                    return false;
                }
                return !Character.isISOControl(character);
            }
        });
        return textArea;
    }

    private static boolean isControlDown() {
        return Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)
            || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
    }
}
