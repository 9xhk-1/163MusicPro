package com.qinghe.music163pro.activity;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.qinghe.music163pro.R;
import com.qinghe.music163pro.util.WatchUiUtils;

/**
 * Base activity for shared watch UI behavior.
 */
public abstract class BaseWatchActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WatchUiUtils.applyKeepScreenOnPreference(this);
    }

    protected final int px(int baseValue) {
        return WatchUiUtils.px(this, baseValue);
    }

    /**
     * Convert dp to pixels using the device's display density.
     * Unlike px() which scales by screen width ratio, this follows
     * Android's standard dp-to-px conversion, ensuring consistency
     * with XML-defined layout dimensions (e.g., login page buttons at 36dp).
     */
    protected final int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }

    /**
     * Create a MaterialButton styled for watch screens.
     * Uses dp-based height (matching the XML login buttons at 36dp, 13sp text)
     * to ensure text is always fully visible regardless of screen density.
     */
    protected final MaterialButton createWatchButton(String text, boolean outlined) {
        int styleAttr = outlined
                ? com.google.android.material.R.attr.materialButtonOutlinedStyle
                : com.google.android.material.R.attr.materialButtonStyle;
        MaterialButton button = new MaterialButton(this, null, styleAttr);
        button.setText(text);
        button.setTextColor(getResources().getColor(R.color.text_primary));
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        // Use dp-based minHeight (matching XML button layout_height="36dp")
        int minHeightPx = dpToPx(36);
        button.setMinHeight(minHeightPx);
        button.setMinimumHeight(minHeightPx);
        return button;
    }

    /** A Material destination row with a 48dp target on the compact watch. */
    protected final MaterialButton createWatchMenuButton(int icon, String label,
                                                          View.OnClickListener listener) {
        MaterialButton button = (MaterialButton) getLayoutInflater()
                .inflate(R.layout.item_watch_menu_button, null, false);
        button.setIconResource(icon);
        button.setText(label);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(48));
        params.bottomMargin = dpToPx(4);
        button.setLayoutParams(params);
        return button;
    }
}
