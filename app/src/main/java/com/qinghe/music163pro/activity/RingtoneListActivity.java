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
import com.qinghe.music163pro.util.WatchConfirmDialog;

import java.util.ArrayList;
import java.util.List;

/**
 * Ringtone management activity - shows all ringtones set through the app.
 * Long press to delete with confirmation dialog.
 */
public class RingtoneListActivity extends BaseWatchActivity {

    private final List<RingtoneManagerHelper.RingtoneInfo> ringtoneList = new ArrayList<>();
    private ArrayAdapter<RingtoneManagerHelper.RingtoneInfo> adapter;
    private RingtoneManagerHelper ringtoneManager;
    private ListView lvRingtones;
    private TextView tvEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ringtone_list);

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
                    tvName.setText(info.title);
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
        WatchConfirmDialog.show(this, title, message, onConfirm,
                new WatchConfirmDialog.Options(0xFF1E1E1E, 0xFFBB86FC, true));
    }
}
