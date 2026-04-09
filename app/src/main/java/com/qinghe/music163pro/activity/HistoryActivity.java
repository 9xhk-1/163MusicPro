package com.qinghe.music163pro.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.qinghe.music163pro.R;
import com.qinghe.music163pro.util.WatchConfirmDialog;
import com.qinghe.music163pro.manager.HistoryManager;
import com.qinghe.music163pro.model.Song;
import com.qinghe.music163pro.player.MusicPlayerManager;

import java.util.ArrayList;
import java.util.List;

/**
 * History activity - shows play history sorted by time (newest first).
 * Designed for watch screen (320x360 dpi).
 */
public class HistoryActivity extends BaseWatchActivity {

    private ListView lvHistory;
    private final List<Song> displayList = new ArrayList<>();
    private ArrayAdapter<Song> adapter;
    private MusicPlayerManager playerManager;
    private TextView tvEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        playerManager = MusicPlayerManager.getInstance();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF212121);
        root.setPadding(px(6), px(6), px(6), px(6));

        // Title bar with clear button - use RelativeLayout for true centering
        android.widget.RelativeLayout titleBar = new android.widget.RelativeLayout(this);
        titleBar.setPadding(0, 0, 0, px(6));
        root.addView(titleBar);

        TextView btnClear = new TextView(this);
        btnClear.setText("清空");
        btnClear.setTextColor(0xFFBB86FC);
        btnClear.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(12));
        btnClear.setPadding(px(8), px(4), px(8), px(4));
        btnClear.setClickable(true);
        btnClear.setFocusable(true);
        btnClear.setId(View.generateViewId());
        btnClear.setOnClickListener(v -> showClearConfirmDialog());
        android.widget.RelativeLayout.LayoutParams clearParams = new android.widget.RelativeLayout.LayoutParams(
                android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT,
                android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT);
        clearParams.addRule(android.widget.RelativeLayout.ALIGN_PARENT_END);
        clearParams.addRule(android.widget.RelativeLayout.CENTER_VERTICAL);
        btnClear.setLayoutParams(clearParams);
        titleBar.addView(btnClear);

        TextView title = new TextView(this);
        title.setText("历史记录");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(15));
        title.setGravity(Gravity.CENTER);
        android.widget.RelativeLayout.LayoutParams titleParams = new android.widget.RelativeLayout.LayoutParams(
                android.widget.RelativeLayout.LayoutParams.MATCH_PARENT,
                android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT);
        titleParams.addRule(android.widget.RelativeLayout.CENTER_IN_PARENT);
        title.setLayoutParams(titleParams);
        titleBar.addView(title);

        tvEmpty = new TextView(this);
        tvEmpty.setText("暂无播放记录");
        tvEmpty.setTextColor(0x80FFFFFF);
        tvEmpty.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(13));
        tvEmpty.setGravity(Gravity.CENTER);
        tvEmpty.setPadding(0, px(40), 0, 0);
        tvEmpty.setVisibility(View.GONE);
        root.addView(tvEmpty);

        lvHistory = new ListView(this);
        lvHistory.setDividerHeight(1);
        lvHistory.setDivider(getResources().getDrawable(android.R.color.transparent));
        LinearLayout.LayoutParams lvParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1);
        lvHistory.setLayoutParams(lvParams);
        root.addView(lvHistory);

        setContentView(root);

        adapter = new ArrayAdapter<Song>(this, R.layout.item_song, R.id.tv_item_name, displayList) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                Song song = getItem(position);
                if (song != null) {
                    TextView tvName = view.findViewById(R.id.tv_item_name);
                    TextView tvArtist = view.findViewById(R.id.tv_item_artist);
                    tvName.setText(song.getName());
                    tvArtist.setText(song.getArtist());
                }
                return view;
            }
        };
        lvHistory.setAdapter(adapter);

        lvHistory.setOnItemClickListener((parent, view, position, id) -> {
            Song song = displayList.get(position);
            List<Song> playlist = new ArrayList<>(displayList);
            playerManager.setPlaylist(playlist, position);
            playerManager.playCurrent();
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        lvHistory.setOnItemLongClickListener((parent, view, position, id) -> {
            Song song = displayList.get(position);
            showConfirmDialog("确认删除", "确定删除「" + song.getName() + "」的播放记录？", () -> {
                HistoryManager.getInstance().removeFromHistory(song.getId());
                loadHistory();
                Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
            });
            return true;
        });

        loadHistory();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadHistory();
    }

    private void loadHistory() {
        displayList.clear();
        displayList.addAll(HistoryManager.getInstance().getHistory());
        adapter.notifyDataSetChanged();

        if (displayList.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            lvHistory.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            lvHistory.setVisibility(View.VISIBLE);
        }
    }

    private void showClearConfirmDialog() {
        showConfirmDialog("确认清空", "确定清空所有播放记录？", () -> {
            HistoryManager.getInstance().clearHistory();
            loadHistory();
            Toast.makeText(this, "已清空", Toast.LENGTH_SHORT).show();
        });
    }
    /**
     * Show a confirmation dialog adapted for watch (360x320 px screen).
     */
    private void showConfirmDialog(String title, String message, Runnable onConfirm) {
        WatchConfirmDialog.show(this, title, message, onConfirm,
                new WatchConfirmDialog.Options(0xFF424242, 0xFFBB86FC, true));
    }
}
