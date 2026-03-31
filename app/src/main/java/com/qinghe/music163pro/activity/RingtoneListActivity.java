package com.qinghe.music163pro.activity;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
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
import com.qinghe.music163pro.manager.RingtoneManagerHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Ringtone management activity - shows all ringtones set through the app.
 * Long press to delete with confirmation dialog.
 */
public class RingtoneListActivity extends AppCompatActivity {

    private final List<RingtoneManagerHelper.RingtoneInfo> ringtoneList = new ArrayList<>();
    private ArrayAdapter<RingtoneManagerHelper.RingtoneInfo> adapter;
    private RingtoneManagerHelper ringtoneManager;
    private ListView lvRingtones;
    private TextView tvEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ringtone_list);

        // Apply keep screen on setting
        SharedPreferences prefs = getSharedPreferences("music163_settings", MODE_PRIVATE);
        if (prefs.getBoolean("keep_screen_on", false)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        lvRingtones = findViewById(R.id.lv_ringtones);
        tvEmpty = findViewById(R.id.tv_empty);

        ringtoneManager = new RingtoneManagerHelper(this);

        adapter = new ArrayAdapter<RingtoneManagerHelper.RingtoneInfo>(
                this, R.layout.item_song, R.id.tv_item_name, ringtoneList) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                RingtoneManagerHelper.RingtoneInfo info = getItem(position);
                if (info != null) {
                    TextView tvName = view.findViewById(R.id.tv_item_name);
                    TextView tvArtist = view.findViewById(R.id.tv_item_artist);
                    tvName.setText("🔔 " + info.title);
                    // Enable marquee scrolling for long names
                    tvName.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
                    tvName.setMarqueeRepeatLimit(-1);
                    tvName.setSingleLine(true);
                    tvName.setSelected(true);
                    tvArtist.setText(info.filePath);
                    tvArtist.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
                    tvArtist.setMarqueeRepeatLimit(-1);
                    tvArtist.setSingleLine(true);
                    tvArtist.setSelected(true);
                }
                return view;
            }
        };
        lvRingtones.setAdapter(adapter);

        lvRingtones.setOnItemLongClickListener((parent, view, position, id) -> {
            RingtoneManagerHelper.RingtoneInfo info = ringtoneList.get(position);
            showConfirmDialog("确认删除", "确定删除铃声「" + info.title + "」？", () -> {
                ringtoneManager.removeRingtone(position);
                loadRingtones();
                Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
            });
            return true;
        });

        loadRingtones();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadRingtones();
    }

    private void loadRingtones() {
        ringtoneList.clear();
        ringtoneList.addAll(ringtoneManager.getRingtones());
        adapter.notifyDataSetChanged();
        updateEmptyState();
    }

    private void updateEmptyState() {
        if (ringtoneList.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            lvRingtones.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            lvRingtones.setVisibility(View.VISIBLE);
        }
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

        // Title
        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextColor(0xFFFFFFFF);
        tvTitle.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(18));
        tvTitle.setGravity(Gravity.CENTER);
        tvTitle.setPadding(0, 0, 0, px(6));
        dialog.addView(tvTitle);

        // Message
        TextView tvMessage = new TextView(this);
        tvMessage.setText(message);
        tvMessage.setTextColor(0xFFCCCCCC);
        tvMessage.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(15));
        tvMessage.setGravity(Gravity.CENTER);
        tvMessage.setPadding(0, 0, 0, px(12));
        dialog.addView(tvMessage);

        // Buttons row
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);
        dialog.addView(btnRow);

        // Cancel button
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

        // Confirm button
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

    /**
     * Convert a value scaled for a 320px-wide watch screen to actual pixels.
     * Base reference: 320px width. Values are proportionally scaled.
     */
    private int px(int baseValue) {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        return (int) (baseValue * screenWidth / 320f + 0.5f);
    }
}
