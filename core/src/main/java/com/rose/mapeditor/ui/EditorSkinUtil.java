package com.rose.mapeditor.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.List;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Array;

/**
 * Adds minimal libGDX Scene2D widget styles (slider, select box) to the editor's font-only skin.
 */
public final class EditorSkinUtil {

    private EditorSkinUtil() {
    }

    public static void extend(Skin skin) {
        if (skin.has("slider-horizontal", Slider.SliderStyle.class)) {
            return;
        }

        TextureRegion white = whiteRegion();
        TextureRegionDrawable whiteDrawable = new TextureRegionDrawable(white);

        TextureRegionDrawable trackBg = new TextureRegionDrawable(white);
        trackBg.setMinHeight(4);
        trackBg.tint(new Color(0.35f, 0.35f, 0.35f, 1f));

        TextureRegionDrawable knob = new TextureRegionDrawable(white);
        knob.setMinWidth(8);
        knob.setMinHeight(8);
        knob.tint(new Color(0.85f, 0.85f, 0.85f, 1f));

        TextureRegionDrawable knobOver = new TextureRegionDrawable(white);
        knobOver.setMinWidth(9);
        knobOver.setMinHeight(9);
        knobOver.tint(Color.WHITE);

        Slider.SliderStyle sliderStyle = new Slider.SliderStyle();
        sliderStyle.background = trackBg.tint(new Color(0.35f, 0.35f, 0.35f, 1f));
        sliderStyle.knob = knob.tint(new Color(0.85f, 0.85f, 0.85f, 1f));
        sliderStyle.knobOver = knobOver.tint(Color.WHITE);
        skin.add("default-horizontal", sliderStyle);

        List.ListStyle listStyle = new List.ListStyle();
        listStyle.font = skin.getFont("default-font");
        listStyle.selection = whiteDrawable.tint(new Color(0.3f, 0.45f, 0.75f, 1f));
        listStyle.fontColorSelected = Color.WHITE;
        listStyle.fontColorUnselected = Color.WHITE;
        skin.add("default", listStyle);

        ScrollPane.ScrollPaneStyle scrollStyle = new ScrollPane.ScrollPaneStyle();
        scrollStyle.background = whiteDrawable.tint(new Color(0.2f, 0.2f, 0.2f, 0.85f));
        scrollStyle.vScroll = whiteDrawable.tint(new Color(0.5f, 0.5f, 0.5f, 1f));
        scrollStyle.vScrollKnob = whiteDrawable.tint(new Color(0.75f, 0.75f, 0.75f, 1f));
        skin.add("default", scrollStyle);

        SelectBox.SelectBoxStyle selectStyle = new SelectBox.SelectBoxStyle();
        selectStyle.font = skin.getFont("default-font");
        selectStyle.fontColor = Color.WHITE;
        selectStyle.background = whiteDrawable.tint(new Color(0.25f, 0.25f, 0.25f, 1f));
        selectStyle.scrollStyle = scrollStyle;
        selectStyle.listStyle = listStyle;
        skin.add("default", selectStyle);

        TextField.TextFieldStyle textFieldStyle = new TextField.TextFieldStyle();
        textFieldStyle.font = skin.getFont("default-font");
        textFieldStyle.fontColor = Color.WHITE;
        textFieldStyle.background = whiteDrawable.tint(new Color(0.18f, 0.18f, 0.18f, 1f));
        textFieldStyle.cursor = whiteDrawable.tint(Color.WHITE);
        textFieldStyle.selection = whiteDrawable.tint(new Color(0.3f, 0.45f, 0.75f, 1f));
        skin.add("default", textFieldStyle);

        CheckBox.CheckBoxStyle checkBoxStyle = new CheckBox.CheckBoxStyle();
        checkBoxStyle.font = skin.getFont("default-font");
        checkBoxStyle.fontColor = Color.WHITE;
        checkBoxStyle.checkboxOn = whiteDrawable.tint(new Color(0.3f, 0.55f, 0.95f, 1f));
        checkBoxStyle.checkboxOff = whiteDrawable.tint(new Color(0.25f, 0.25f, 0.25f, 1f));
        skin.add("default", checkBoxStyle);
    }

    public static CheckBox createCheckBox(Skin skin, String label, boolean checked) {
        CheckBox box = new CheckBox(label, skin);
        box.setChecked(checked);
        return box;
    }

    private static TextureRegion whiteRegion() {
        Pixmap pixmap = new Pixmap(2, 2, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        return new TextureRegion(texture);
    }
}
