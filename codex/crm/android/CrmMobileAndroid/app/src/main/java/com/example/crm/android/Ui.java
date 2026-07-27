package com.example.crm.android;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.TextView;
import android.widget.Toast;

public final class Ui {
    public static final int BLUE = Color.rgb(29, 78, 216);
    public static final int TEXT = Color.rgb(17, 24, 39);
    public static final int MUTED = Color.rgb(107, 114, 128);
    public static final int RED = Color.rgb(220, 38, 38);
    public static final int GREEN = Color.rgb(22, 163, 74);
    public static final int BORDER = Color.rgb(229, 231, 235);

    private Ui() {
    }

    public static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static TextView text(Context context, String text, float sp, int color, int style) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        return view;
    }

    public static void toast(Activity activity, String message) {
        activity.runOnUiThread(() -> Toast.makeText(activity, message, Toast.LENGTH_SHORT).show());
    }

    public static void hideKeyboard(Activity activity, View view) {
        InputMethodManager manager = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) {
            manager.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    public static String value(String value) {
        return value == null || value.trim().isEmpty() ? "-" : value;
    }
}
