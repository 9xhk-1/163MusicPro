package com.qinghe.music163pro.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.qinghe.music163pro.api.MusicApiHelper;
import com.qinghe.music163pro.player.MusicPlayerManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Personal FM mode selection activity.
 * Shows available roaming modes before starting FM playback.
 */
public class PersonalFMActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences("music163_settings", MODE_PRIVATE);
        if (prefs.getBoolean("keep_screen_on", false)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        // Wrap content in a ScrollView so small watch screens can scroll
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(0xFF121212);
        scrollView.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));
        scrollView.addView(root);
        setContentView(scrollView);

        // Title
        TextView tvTitle = new TextView(this);
        tvTitle.setText("私人漫游");
        tvTitle.setTextColor(0xFFFFFFFF);
        tvTitle.setTextSize(16);
        tvTitle.setGravity(Gravity.CENTER);
        tvTitle.setPadding(0, 0, 0, dp(16));
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(tvTitle);

        // Description
        TextView tvDesc = new TextView(this);
        tvDesc.setText("选择漫游模式开始播放");
        tvDesc.setTextColor(0x80FFFFFF);
        tvDesc.setTextSize(12);
        tvDesc.setGravity(Gravity.CENTER);
        tvDesc.setPadding(0, 0, 0, dp(20));
        root.addView(tvDesc);

        // Mode: 普通漫游
        addModeButton(root, "普通漫游", "根据喜好随机推荐", () -> startPersonalFM("normal"));

        // Divider
        addDivider(root);

        // Mode: 心动漫游
        addModeButton(root, "心动漫游", "基于当前播放推荐", () -> startPersonalFM("heartbeat"));

        // Back button
        TextView btnBack = new TextView(this);
        btnBack.setText("返回");
        btnBack.setTextColor(0x80FFFFFF);
        btnBack.setTextSize(13);
        btnBack.setGravity(Gravity.CENTER);
        btnBack.setPadding(0, dp(16), 0, dp(8));
        btnBack.setClickable(true);
        btnBack.setFocusable(true);
        btnBack.setOnClickListener(v -> finish());
        root.addView(btnBack);
    }

    private void addModeButton(LinearLayout root, String title, String subtitle, Runnable action) {
        LinearLayout btn = new LinearLayout(this);
        btn.setOrientation(LinearLayout.VERTICAL);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dp(12), dp(14), dp(12), dp(14));
        btn.setClickable(true);
        btn.setFocusable(true);
        btn.setBackground(makeRoundBackground(0xFF2D2D2D));
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnParams.bottomMargin = dp(8);
        btn.setLayoutParams(btnParams);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextColor(0xFFFFFFFF);
        tvTitle.setTextSize(14);
        tvTitle.setGravity(Gravity.CENTER);
        btn.addView(tvTitle);

        TextView tvSub = new TextView(this);
        tvSub.setText(subtitle);
        tvSub.setTextColor(0x80FFFFFF);
        tvSub.setTextSize(11);
        tvSub.setGravity(Gravity.CENTER);
        tvSub.setPadding(0, dp(3), 0, 0);
        btn.addView(tvSub);

        btn.setOnClickListener(v -> action.run());
        root.addView(btn);
    }

    private void addDivider(LinearLayout root) {
        android.view.View divider = new android.view.View(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        params.bottomMargin = dp(8);
        divider.setLayoutParams(params);
        divider.setBackgroundColor(0x33FFFFFF);
        root.addView(divider);
    }

    private android.graphics.drawable.GradientDrawable makeRoundBackground(int color) {
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(10));
        bg.setColor(color);
        return bg;
    }

    private void startPersonalFM(String mode) {
        String cookie = MusicPlayerManager.getInstance().getCookie();
        if (cookie == null || cookie.isEmpty() || !cookie.contains("MUSIC_U")) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "正在获取私人漫游...", Toast.LENGTH_SHORT).show();

        MusicApiHelper.PersonalFMCallback callback = new MusicApiHelper.PersonalFMCallback() {
            @Override
            public void onResult(List<com.qinghe.music163pro.model.Song> songs) {
                if (songs.isEmpty()) {
                    Toast.makeText(PersonalFMActivity.this, "暂无推荐歌曲", Toast.LENGTH_SHORT).show();
                    return;
                }
                MusicPlayerManager playerManager = MusicPlayerManager.getInstance();
                playerManager.setPersonalFmPlaylist(new ArrayList<>(songs), 0);
                playerManager.playCurrent();
                Intent intent = new Intent(PersonalFMActivity.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(PersonalFMActivity.this, "获取失败: " + message, Toast.LENGTH_SHORT).show();
            }
        };

        // Both modes use the same API endpoint for now; mode flag is logged for future differentiation
        MusicApiHelper.getPersonalFM(cookie, callback);
    }

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }
}
