package com.qinghe.music163pro.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.qinghe.music163pro.R;
import com.qinghe.music163pro.api.MusicApiHelper;
import com.qinghe.music163pro.model.Song;
import com.qinghe.music163pro.player.MusicPlayerManager;
import com.qinghe.music163pro.util.WatchUiUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Collections;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OpenShareLinkActivity extends BaseWatchActivity {

    private static final String TAG = "OpenShareLinkActivity";
    private static final String EXTRA_SHARE_TYPE = "_xtc_api_share_type";
    private static final Pattern MUSIC_SHARE_PATTERN = Pattern.compile("^MUSIC_(\\d+)$");

    private TextView tvStatus;
    private View errorContainer;
    private TextView tvErrorMessage;
    private TextView tvIntentDump;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_open_share_link);

        tvStatus = findViewById(R.id.tv_status);
        errorContainer = findViewById(R.id.error_container);
        tvErrorMessage = findViewById(R.id.tv_error_message);
        tvIntentDump = findViewById(R.id.tv_intent_dump);

        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        showLoading("正在解析分享参数…");
        String shareType = getShareTypeValue(intent);
        if (TextUtils.isEmpty(shareType)) {
            showError("未获取到 _xtc_api_share_type 参数", intent);
            return;
        }

        Matcher matcher = MUSIC_SHARE_PATTERN.matcher(shareType.trim());
        if (!matcher.matches()) {
            showError("参数格式错误: " + shareType, intent);
            return;
        }

        long songId;
        try {
            songId = Long.parseLong(matcher.group(1));
        } catch (NumberFormatException e) {
            showError("无法解析歌曲ID: " + shareType, intent);
            return;
        }

        playSongById(songId, intent);
    }

    private void playSongById(long songId, Intent sourceIntent) {
        tvStatus.setText("正在加载歌曲信息并跳转主界面…");

        SharedPreferences preferences = getSharedPreferences(WatchUiUtils.SETTINGS_PREFERENCES, MODE_PRIVATE);
        String cookie = preferences.getString("cookie", "");
        MusicPlayerManager playerManager = MusicPlayerManager.getInstance();
        playerManager.setContext(this);
        playerManager.setCookie(cookie);

        MusicApiHelper.getSongDetail(songId, cookie, new MusicApiHelper.SongDetailCallback() {
            @Override
            public void onResult(JSONObject songJson) {
                Song song = parseSong(songJson);
                if (song == null) {
                    showError("歌曲详情为空，无法播放", sourceIntent);
                    return;
                }

                playerManager.setPlaylist(Collections.singletonList(song), 0);
                playerManager.playCurrent();
                openMainActivity();
            }

            @Override
            public void onError(String error) {
                showError("获取歌曲信息失败: " + error, sourceIntent);
            }
        });
    }

    private void openMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void showLoading(String message) {
        tvStatus.setText(message);
        errorContainer.setVisibility(View.GONE);
    }

    private void showError(String message, Intent intent) {
        String intentDump = dumpIntent(intent);
        Log.e(TAG, message + "\n" + intentDump);
        tvStatus.setText("分享链接打开失败");
        errorContainer.setVisibility(View.VISIBLE);
        tvErrorMessage.setText(message);
        tvIntentDump.setText(intentDump);
    }

    private String getShareTypeValue(Intent intent) {
        if (intent == null) {
            return null;
        }
        Bundle extras = intent.getExtras();
        if (extras == null || !extras.containsKey(EXTRA_SHARE_TYPE)) {
            return null;
        }
        Object value = extras.get(EXTRA_SHARE_TYPE);
        return value != null ? String.valueOf(value) : null;
    }

    private Song parseSong(JSONObject songJson) {
        if (songJson == null) {
            return null;
        }
        long id = songJson.optLong("id");
        String name = songJson.optString("name", "");
        String album = "";
        JSONObject albumObj = songJson.optJSONObject("al");
        if (albumObj == null) {
            albumObj = songJson.optJSONObject("album");
        }
        if (albumObj != null) {
            album = albumObj.optString("name", "");
        }

        JSONArray artists = songJson.optJSONArray("ar");
        if (artists == null) {
            artists = songJson.optJSONArray("artists");
        }

        StringBuilder artistBuilder = new StringBuilder();
        if (artists != null) {
            for (int i = 0; i < artists.length(); i++) {
                JSONObject artistObj = artists.optJSONObject(i);
                if (artistObj == null) {
                    continue;
                }
                String artistName = artistObj.optString("name", "");
                if (TextUtils.isEmpty(artistName)) {
                    continue;
                }
                if (artistBuilder.length() > 0) {
                    artistBuilder.append(" / ");
                }
                artistBuilder.append(artistName);
            }
        }

        if (id <= 0 || TextUtils.isEmpty(name)) {
            return null;
        }
        return new Song(id, name, artistBuilder.toString(), album);
    }

    private String dumpIntent(Intent intent) {
        if (intent == null) {
            return "Intent = null";
        }

        StringBuilder builder = new StringBuilder();
        builder.append("action = ").append(intent.getAction()).append('\n');
        builder.append("data = ").append(intent.getDataString()).append('\n');
        builder.append("type = ").append(intent.getType()).append('\n');
        builder.append("package = ").append(intent.getPackage()).append('\n');
        builder.append("component = ").append(intent.getComponent()).append('\n');

        Set<String> categories = intent.getCategories();
        builder.append("categories = ").append(categories == null ? "[]" : categories.toString()).append('\n');

        Bundle extras = intent.getExtras();
        if (extras == null || extras.isEmpty()) {
            builder.append("extras = {}");
            return builder.toString();
        }

        builder.append("extras = {\n");
        for (String key : extras.keySet()) {
            Object value = extras.get(key);
            builder.append("  ").append(key).append(" = ").append(String.valueOf(value)).append('\n');
        }
        builder.append('}');
        return builder.toString();
    }
}
