package com.rose.mapeditor.screen;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;

/** Yes/No confirmation dialog shown over other overlays. */
public final class ConfirmDialogOverlay extends Table {

    public interface Listener {
        void onConfirmed();

        void onCancelled();
    }

    public ConfirmDialogOverlay(Skin skin, String title, String message, Listener listener) {
        setFillParent(true);
        setBackground(UiDrawables.solid(new Color(0f, 0f, 0f, 0.65f)));

        Table panel = new Table(skin);
        panel.setBackground(UiDrawables.solid(new Color(0.14f, 0.12f, 0.12f, 0.98f)));
        panel.pad(16);

        Label titleLabel = new Label(title, skin);
        titleLabel.setAlignment(Align.center);
        panel.add(titleLabel).expandX().fillX().padBottom(10).row();

        Label messageLabel = new Label(message, skin);
        messageLabel.setWrap(true);
        messageLabel.setAlignment(Align.left);
        panel.add(messageLabel).width(420).padBottom(14).row();

        Table buttons = new Table(skin);
        TextButton confirmButton = new TextButton("Yes, delete", skin);
        confirmButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                listener.onConfirmed();
                remove();
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

        buttons.add(confirmButton).width(110).padRight(8);
        buttons.add(cancelButton).width(90);
        panel.add(buttons).right().row();

        add(panel).center();
    }
}
