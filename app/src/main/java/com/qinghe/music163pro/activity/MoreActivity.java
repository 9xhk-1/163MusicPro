package com.qinghe.music163pro.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.qinghe.music163pro.R;
import com.qinghe.music163pro.api.MusicApiHelper;
import com.qinghe.music163pro.model.Song;
import com.qinghe.music163pro.player.MusicPlayerManager;

import java.util.ArrayList;
import java.util.List;

/**
 * More menu activity - shows a flat tile list of functions:
 * 收藏列表, 搜索, 下载列表, 铃声管理, 排行榜, 历史记录,
 * 个人中心(登录后), 私人漫游(登录后), 登录, 设置
 *
 * Supports right-swipe gesture to go back to the player screen.
 */
public class MoreActivity extends AppCompatActivity {

    private TextView btnProfile;
    private TextView btnPersonalFM;
    private GestureDetector gestureDetector;

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
        TextView btnRingtones = findViewById(R.id.btn_menu_ringtones);
        TextView btnTopList = findViewById(R.id.btn_menu_toplist);
        TextView btnHistory = findViewById(R.id.btn_menu_history);
        btnProfile = findViewById(R.id.btn_menu_profile);
        btnPersonalFM = findViewById(R.id.btn_menu_personal_fm);
        TextView btnLogin = findViewById(R.id.btn_menu_login);
        TextView btnSettings = findViewById(R.id.btn_menu_settings);

        btnFavorites.setOnClickListener(v ->
                startActivity(new Intent(this, FavoritesListActivity.class)));

        btnSearch.setOnClickListener(v ->
                startActivity(new Intent(this, SearchActivity.class)));

        btnDownloads.setOnClickListener(v ->
                startActivity(new Intent(this, DownloadListActivity.class)));

        btnRingtones.setOnClickListener(v ->
                startActivity(new Intent(this, RingtoneListActivity.class)));

        btnTopList.setOnClickListener(v ->
                startActivity(new Intent(this, TopListActivity.class)));

        btnHistory.setOnClickListener(v ->
                startActivity(new Intent(this, HistoryActivity.class)));

        btnProfile.setOnClickListener(v ->
                startActivity(new Intent(this, ProfileActivity.class)));

        btnPersonalFM.setOnClickListener(v -> startPersonalFM());

        btnLogin.setOnClickListener(v ->
                startActivity(new Intent(this, LoginActivity.class)));

        btnSettings.setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        // Set up right-swipe to go back to player
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 != null && e2 != null) {
                    float diffX = e2.getX() - e1.getX();
                    float diffY = Math.abs(e2.getY() - e1.getY());
                    if (diffX > 80 && diffY < 200 && Math.abs(velocityX) > 200) {
                        finish();
                        return true;
                    }
                }
                return false;
            }
        });
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        gestureDetector.onTouchEvent(event);
        return super.dispatchTouchEvent(event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateLoginDependentVisibility();
    }

    private void updateLoginDependentVisibility() {
        String cookie = MusicPlayerManager.getInstance().getCookie();
        boolean loggedIn = cookie != null && !cookie.isEmpty() && cookie.contains("MUSIC_U");
        btnProfile.setVisibility(loggedIn ? View.VISIBLE : View.GONE);
        btnPersonalFM.setVisibility(loggedIn ? View.VISIBLE : View.GONE);
    }

    private void startPersonalFM() {
        String cookie = MusicPlayerManager.getInstance().getCookie();
        Toast.makeText(this, "正在获取私人漫游...", Toast.LENGTH_SHORT).show();
        MusicApiHelper.getPersonalFM(cookie, new MusicApiHelper.PersonalFMCallback() {
            @Override
            public void onResult(List<Song> songs) {
                if (songs.isEmpty()) {
                    Toast.makeText(MoreActivity.this, "暂无推荐歌曲", Toast.LENGTH_SHORT).show();
                    return;
                }
                MusicPlayerManager playerManager = MusicPlayerManager.getInstance();
                playerManager.setPlaylist(new ArrayList<>(songs), 0);
                playerManager.playCurrent();
                Intent intent = new Intent(MoreActivity.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(MoreActivity.this, "获取失败: " + message, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
