package com.qinghe.music163pro.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.qinghe.music163pro.R;

/**
 * More menu activity - shows a flat tile list of functions:
 * 收藏列表, 搜索, 下载列表, 设置
 */
public class MoreActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_more);

        // Apply keep screen on setting
        SharedPreferences prefs = getSharedPreferences("music163_settings", MODE_PRIVATE);
        if (prefs.getBoolean("keep_screen_on", false)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        TextView btnFavorites = findViewById(R.id.btn_menu_favorites);
        TextView btnSearch = findViewById(R.id.btn_menu_search);
        TextView btnDownloads = findViewById(R.id.btn_menu_downloads);
        TextView btnSettings = findViewById(R.id.btn_menu_settings);

        btnFavorites.setOnClickListener(v ->
                startActivity(new Intent(this, FavoritesListActivity.class)));

        btnSearch.setOnClickListener(v ->
                startActivity(new Intent(this, SearchActivity.class)));

        btnDownloads.setOnClickListener(v ->
                startActivity(new Intent(this, DownloadListActivity.class)));

        btnSettings.setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
    }
}
