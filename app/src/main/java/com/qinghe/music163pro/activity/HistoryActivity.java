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
import com.qinghe.music163pro.manager.HistoryManager;
import com.qinghe.music163pro.model.Song;
import com.qinghe.music163pro.player.MusicPlayerManager;

import java.util.ArrayList;
import java.util.List;

/**
 * History activity - shows play history sorted by time (newest first).
 * Designed for watch screen (320x360 dpi).
 */
public class HistoryActivity extends AppCompatActivity {

    private ListView lvHistory;
    private final List<Song> displayList = new ArrayList<>();
    private ArrayAdapter<Song> adapter;
    private MusicPlayerManager playerManager;
    private TextView tvEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences("music163_settings", MODE_PRIVATE);
        if (prefs.getBoolean("keep_screen_on", false)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        playerManager = MusicPlayerManager.getInstance();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF212121);
        root.setPadding(px(6), px(6), px(6), px(6));

        // Title bar with clear button
        LinearLayout titleBar = new LinearLayout(this);
        titleBar.setOrientation(LinearLayout.HORIZONTAL);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);
        titleBar.setPadding(0, 0, 0, px(6));
        root.addView(titleBar);

        TextView title = new TextView(this);
        title.setText("📜  历史记录");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(15));
        title.setGravity(Gravity.CENTER);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        titleBar.addView(title);

        TextView btnClear = new TextView(this);
        btnClear.setText("清空");
        btnClear.setTextColor(0xFFD32F2F);
        btnClear.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(12));
        btnClear.setPadding(px(8), px(4), px(8), px(4));
        btnClear.setClickable(true);
        btnClear.setFocusable(true);
        btnClear.setOnClickListener(v -> showClearConfirmDialog());
        titleBar.addView(btnClear);

        tvEmpty = new TextView(this);
        tvEmpty.setText("暂无播放记录");
        tvEmpty.setTextColor(0xFF757575);
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
     * Uses fixed pixel values for consistent sizing on watch displays.
     */
    private void showConfirmDialog(String title, String message, Runnable onConfirm) {
        FrameLayout rootView = findViewById(android.R.id.content);

        FrameLayout overlay = new FrameLayout(this);
        overlay.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        overlay.setBackgroundColor(0xCC333333);

        LinearLayout dialog = new LinearLayout(this);
        dialog.setOrientation(LinearLayout.VERTICAL);
        dialog.setBackgroundColor(0xFF424242);
        dialog.setPadding(px(16), px(12), px(16), px(12));
        FrameLayout.LayoutParams dlgParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        dlgParams.gravity = Gravity.CENTER;
        dlgParams.leftMargin = px(16);
        dlgParams.rightMargin = px(16);
        dialog.setLayoutParams(dlgParams);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextColor(0xFFFFFFFF);
        tvTitle.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(18));
        tvTitle.setGravity(Gravity.CENTER);
        tvTitle.setPadding(0, 0, 0, px(6));
        dialog.addView(tvTitle);

        TextView tvMessage = new TextView(this);
        tvMessage.setText(message);
        tvMessage.setTextColor(0xFFCCCCCC);
        tvMessage.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(15));
        tvMessage.setGravity(Gravity.CENTER);
        tvMessage.setPadding(0, 0, 0, px(12));
        dialog.addView(tvMessage);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);
        dialog.addView(btnRow);

        TextView btnCancel = new TextView(this);
        btnCancel.setText("取消");
        btnCancel.setTextColor(0xFFFFFFFF);
        btnCancel.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(16));
        btnCancel.setGravity(Gravity.CENTER);
        btnCancel.setPadding(px(12), px(8), px(12), px(8));
        btnCancel.setBackgroundColor(0xFF616161);
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        cancelParams.rightMargin = px(4);
        btnCancel.setLayoutParams(cancelParams);
        btnCancel.setClickable(true);
        btnCancel.setFocusable(true);
        btnCancel.setOnClickListener(v -> rootView.removeView(overlay));
        btnRow.addView(btnCancel);

        TextView btnConfirm = new TextView(this);
        btnConfirm.setText("确定");
        btnConfirm.setTextColor(0xFFFFFFFF);
        btnConfirm.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(16));
        btnConfirm.setGravity(Gravity.CENTER);
        btnConfirm.setPadding(px(12), px(8), px(12), px(8));
        btnConfirm.setBackgroundColor(0xFFD32F2F);
        LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        confirmParams.leftMargin = px(4);
        btnConfirm.setLayoutParams(confirmParams);
        btnConfirm.setClickable(true);
        btnConfirm.setFocusable(true);
        btnConfirm.setOnClickListener(v -> {
            rootView.removeView(overlay);
            onConfirm.run();
        });
        btnRow.addView(btnConfirm);

        overlay.addView(dialog);
        overlay.setOnClickListener(v -> rootView.removeView(overlay));
        dialog.setOnClickListener(v -> { /* consume click */ });
        rootView.addView(overlay);
    }

    private int px(int baseValue) {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        return (int) (baseValue * screenWidth / 320f + 0.5f);
    }
}
