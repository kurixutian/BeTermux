package com.betermux.features;

import android.content.Context;
import android.graphics.Color;

/**
 * Builder-style reusable color picker dialog.
 * Supports HSV color wheel and RGB sliders.
 * Remembers last picker style in SharedPreferences ("betermux_theme" / "color_picker_style").
 */
public class ColorPickerDialog {

    public interface OnColorSelectedListener {
        void onColorSelected(int color);
    }

    private final Context mContext;
    private int mInitialColor = Color.WHITE;
    private OnColorSelectedListener mListener;

    public ColorPickerDialog(Context context) {
        mContext = context;
    }

    public ColorPickerDialog setInitialColor(int color) {
        mInitialColor = color;
        return this;
    }

    public ColorPickerDialog setOnColorSelectedListener(OnColorSelectedListener listener) {
        mListener = listener;
        return this;
    }

    public void show() {
        ColorPickerComposeDialog.show(mContext, mInitialColor, mListener);
    }
}
