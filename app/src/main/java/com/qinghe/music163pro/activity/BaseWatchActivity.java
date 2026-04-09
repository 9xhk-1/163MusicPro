package com.qinghe.music163pro.activity;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

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
}
