package com.qinghe.music163pro.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.qinghe.music163pro.R;
import com.qinghe.music163pro.api.MusicApiHelper;
import com.qinghe.music163pro.player.MusicPlayerManager;
import com.qinghe.music163pro.util.NetworkImageLoader;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Top List activity - shows different chart categories.
 * Designed for watch screen (320x360 dpi).
 */
public class TopListActivity extends AppCompatActivity {

    private ListView lvTopList;
    private final List<TopListItem> items = new ArrayList<>();
    private ArrayAdapter<TopListItem> adapter;

    static class TopListItem {
        long id;
        String name;
        String updateFrequency;
        String coverUrl;

        TopListItem(long id, String name, String updateFrequency, String coverUrl) {
            this.id = id;
            this.name = name;
            this.updateFrequency = updateFrequency;
            this.coverUrl = coverUrl;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences("music163_settings", MODE_PRIVATE);
        if (prefs.getBoolean("keep_screen_on", false)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF212121);
        root.setPadding(px(6), px(6), px(6), px(6));

        // Title
        TextView title = new TextView(this);
        title.setText("排行榜");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(15));
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, px(6));
        root.addView(title);

        lvTopList = new ListView(this);
        lvTopList.setDividerHeight(px(1));
        lvTopList.setDivider(getResources().getDrawable(android.R.color.transparent));
        LinearLayout.LayoutParams lvParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1);
        lvTopList.setLayoutParams(lvParams);
        root.addView(lvTopList);

        setContentView(root);

        adapter = new ArrayAdapter<TopListItem>(this, R.layout.item_song, R.id.tv_item_name, items) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TopListItem item = getItem(position);
                if (item != null) {
                    TextView tvName = view.findViewById(R.id.tv_item_name);
                    TextView tvArtist = view.findViewById(R.id.tv_item_artist);
                    ImageView ivCover = view.findViewById(R.id.iv_cover);
                    tvName.setText(item.name);
                    tvArtist.setText(item.updateFrequency != null ? item.updateFrequency : "");
                    if (ivCover != null) {
                        NetworkImageLoader.load(ivCover, item.coverUrl);
                    }
                }
                return view;
            }
        };
        lvTopList.setAdapter(adapter);

        lvTopList.setOnItemClickListener((parent, view, position, id) -> {
            TopListItem item = items.get(position);
            Intent intent = new Intent(this, TopListDetailActivity.class);
            intent.putExtra("playlist_id", item.id);
            intent.putExtra("playlist_name", item.name);
            startActivity(intent);
        });

        loadTopList();
    }

    private void loadTopList() {
        String cookie = MusicPlayerManager.getInstance().getCookie();
        MusicApiHelper.getTopList(cookie, new MusicApiHelper.TopListCallback() {
            @Override
            public void onResult(JSONArray listArray) {
                items.clear();
                for (int i = 0; i < listArray.length(); i++) {
                    try {
                        JSONObject obj = listArray.getJSONObject(i);
                        long id = obj.getLong("id");
                        String name = obj.optString("name", "未知榜单");
                        String updateFrequency = obj.optString("updateFrequency", "");
                        String coverUrl = obj.optString("coverImgUrl", obj.optString("picUrl", ""));
                        items.add(new TopListItem(id, name, updateFrequency, coverUrl));
                    } catch (Exception e) {
                        // skip invalid entry
                    }
                }
                adapter.notifyDataSetChanged();
                if (items.isEmpty()) {
                    Toast.makeText(TopListActivity.this, "暂无排行榜数据", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(String message) {
                Toast.makeText(TopListActivity.this, "加载失败: " + message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private int px(int baseValue) {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        return (int) (baseValue * screenWidth / 320f + 0.5f);
    }
}
