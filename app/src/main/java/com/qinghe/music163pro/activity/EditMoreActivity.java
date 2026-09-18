package com.qinghe.music163pro.activity;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.qinghe.music163pro.R;
import com.qinghe.music163pro.util.MoreMenuPreferences;
import com.qinghe.music163pro.util.WatchUiUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Edit visible entries for the more screen.
 */
public class EditMoreActivity extends BaseWatchActivity {

    private final Map<String, String> itemLabels = new LinkedHashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_more);

        itemLabels.put(MoreMenuPreferences.KEY_FAVORITES, "收藏列表");
        itemLabels.put(MoreMenuPreferences.KEY_MY_PLAYLISTS, "我的歌单");
        itemLabels.put(MoreMenuPreferences.KEY_DAILY_RECOMMEND, "每日推荐");
        itemLabels.put(MoreMenuPreferences.KEY_RADAR_PLAYLIST, "雷达歌单");
        itemLabels.put(MoreMenuPreferences.KEY_MUSIC_CLOUD, "音乐云盘");
        itemLabels.put(MoreMenuPreferences.KEY_SEARCH, "搜索");
        itemLabels.put(MoreMenuPreferences.KEY_SONG_RECOGNITION, "听歌识曲");
        itemLabels.put(MoreMenuPreferences.KEY_DOWNLOADS, "下载列表");
        itemLabels.put(MoreMenuPreferences.KEY_RINGTONES, "铃声管理");
        itemLabels.put(MoreMenuPreferences.KEY_TOPLIST, "排行榜");
        itemLabels.put(MoreMenuPreferences.KEY_HISTORY, "历史记录");
        itemLabels.put(MoreMenuPreferences.KEY_PROFILE, "个人中心");
        itemLabels.put(MoreMenuPreferences.KEY_PERSONAL_FM, "私人漫游");
        itemLabels.put(MoreMenuPreferences.KEY_LOGIN, "登录");

        SharedPreferences prefs = MoreMenuPreferences.getPrefs(this);
        LinearLayout container = findViewById(R.id.container_more_edit);
        for (Map.Entry<String, String> entry : itemLabels.entrySet()) {
            container.addView(createSwitchRow(prefs, entry.getKey(), entry.getValue()));
        }
    }

    private LinearLayout createSwitchRow(SharedPreferences prefs, String key, String label) {
        LinearLayout row = new LinearLayout(this);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, WatchUiUtils.px(this, 56)));
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(WatchUiUtils.px(this, 16), 0, WatchUiUtils.px(this, 12), 0);
        row.setBackgroundResource(android.R.drawable.list_selector_background);

        TextView tvLabel = new TextView(this);
        tvLabel.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        tvLabel.setText(label);
        tvLabel.setTextColor(getResources().getColor(R.color.text_primary));
        tvLabel.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, WatchUiUtils.px(this, 14));
        row.addView(tvLabel);

        SwitchMaterial toggle = new SwitchMaterial(this);
        toggle.setChecked(MoreMenuPreferences.isEnabled(prefs, key));
        toggle.setOnCheckedChangeListener((buttonView, isChecked) ->
                MoreMenuPreferences.setEnabled(prefs, key, isChecked));
        row.addView(toggle);
        return row;
    }
}
