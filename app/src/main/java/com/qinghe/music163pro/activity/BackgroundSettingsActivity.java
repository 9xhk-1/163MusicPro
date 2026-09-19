package com.qinghe.music163pro.activity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.qinghe.music163pro.api.MusicApiHelper;
import com.qinghe.music163pro.model.Song;
import com.qinghe.music163pro.player.MusicPlayerManager;
import com.qinghe.music163pro.util.BackgroundUtil;

/**
 * Custom background settings: pick an image or use the current album cover as
 * the main player / lyrics background, or reset to default.
 */
public class BackgroundSettingsActivity extends BaseWatchActivity {

    private static final int REQ_PICK_IMAGE = 3001;

    private ImageView ivPreview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF121212);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(root);
        setContentView(scroll);

        root.addView(buildTitleBar());

        ivPreview = new ImageView(this);
        ivPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        ivPreview.setBackgroundColor(0xFF1E1E1E);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, px(180));
        previewParams.setMargins(px(14), px(8), px(14), px(8));
        ivPreview.setLayoutParams(previewParams);
        root.addView(ivPreview);

        TextView hint = new TextView(this);
        hint.setText("默认跟随专辑封面：每播放一首歌自动以其封面为背景（获取失败用默认背景）");
        hint.setTextColor(0x80FFFFFF);
        hint.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(12));
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(px(10), 0, px(10), px(8));
        root.addView(hint);

        root.addView(buildButton("选择图片", v -> pickImage()));
        root.addView(buildButton("使用专辑封面", v -> useAlbumCover()));
        root.addView(buildButton("恢复默认背景", v -> resetBackground()));

        refreshPreview();
    }

    private View buildTitleBar() {
        TextView title = new TextView(this);
        title.setText("自定义背景");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(15));
        title.setGravity(Gravity.CENTER);
        title.setPadding(px(12), px(10), px(12), px(6));
        return title;
    }

    private View buildButton(String label, View.OnClickListener listener) {
        TextView btn = new TextView(this);
        btn.setText(label);
        btn.setTextColor(0xFFBB86FC);
        btn.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(14));
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(px(12), px(13), px(12), px(13));
        btn.setClickable(true);
        btn.setFocusable(true);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(0xFF2D2D2D);
        bg.setCornerRadius(px(6));
        btn.setBackground(bg);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(px(14), 0, px(14), px(10));
        btn.setLayoutParams(params);
        btn.setOnClickListener(listener);
        return btn;
    }

    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, REQ_PICK_IMAGE);
    }

    private void useAlbumCover() {
        BackgroundUtil.setMode(this, BackgroundUtil.MODE_COVER);
        final Song song = MusicPlayerManager.getInstance().getBackgroundSong();
        if (song == null) {
            BackgroundUtil.clearCoverBackground();
            refreshPreview();
            Toast.makeText(this, "暂无歌曲，播放歌曲后自动更新", Toast.LENGTH_SHORT).show();
            return;
        }
        String coverUrl = song.getCoverUrl();
        if (coverUrl != null && !coverUrl.isEmpty()) {
            loadCoverAndPreview(coverUrl);
            return;
        }
        if (song.getId() > 0) {
            Toast.makeText(this, "正在获取专辑封面...", Toast.LENGTH_SHORT).show();
            final long songId = song.getId();
            MusicApiHelper.fetchSongsDetails(java.util.Collections.singletonList(songId),
                    MusicPlayerManager.getInstance().getCookie(),
                    new MusicApiHelper.BatchSongDetailsCallback() {
                        @Override
                        public void onResult(java.util.Map<Long, Song> songMap) {
                            Song detail = songMap != null ? songMap.get(songId) : null;
                            String url = detail != null ? detail.getCoverUrl() : null;
                            if (url != null && !url.isEmpty()) {
                                song.setCoverUrl(url);
                                loadCoverAndPreview(url);
                            } else {
                                BackgroundUtil.clearCoverBackground();
                                refreshPreview();
                                Toast.makeText(BackgroundSettingsActivity.this,
                                        "获取封面失败，已使用默认背景", Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onError(String message) {
                            BackgroundUtil.clearCoverBackground();
                            refreshPreview();
                            Toast.makeText(BackgroundSettingsActivity.this,
                                    "获取封面失败，已使用默认背景", Toast.LENGTH_SHORT).show();
                        }
                    });
        } else {
            BackgroundUtil.clearCoverBackground();
            refreshPreview();
            Toast.makeText(this, "当前无歌曲封面", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadCoverAndPreview(final String coverUrl) {
        Toast.makeText(this, "正在获取专辑封面...", Toast.LENGTH_SHORT).show();
        BackgroundUtil.loadCoverBackground(this, coverUrl, () -> {
            refreshPreview();
            boolean ok = BackgroundUtil.hasCoverBackground(coverUrl);
            Toast.makeText(this, ok ? "背景已跟随专辑封面" : "获取封面失败，已使用默认背景",
                    Toast.LENGTH_SHORT).show();
        });
    }

    private void resetBackground() {
        BackgroundUtil.setMode(this, BackgroundUtil.MODE_DEFAULT);
        BackgroundUtil.clearBackground(this);
        BackgroundUtil.clearCoverBackground();
        refreshPreview();
        Toast.makeText(this, "已恢复默认背景", Toast.LENGTH_SHORT).show();
    }

    private void refreshPreview() {
        String mode = BackgroundUtil.getMode(this);
        if (BackgroundUtil.MODE_COVER.equals(mode)) {
            Bitmap cover = BackgroundUtil.getCoverBackground();
            if (cover != null) {
                ivPreview.setBackgroundColor(0xFF1E1E1E);
                ivPreview.setImageDrawable(new BitmapDrawable(getResources(), cover));
            } else {
                ivPreview.setImageDrawable(null);
                ivPreview.setBackgroundColor(0xFF1E1E1E);
            }
            return;
        }
        if (BackgroundUtil.MODE_CUSTOM.equals(mode) && BackgroundUtil.hasCustomBackground(this)) {
            try {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = 2;
                Bitmap bmp = BitmapFactory.decodeFile(
                        BackgroundUtil.getBackgroundFile(this).getAbsolutePath(), opts);
                if (bmp != null) {
                    ivPreview.setBackgroundColor(0xFF1E1E1E);
                    ivPreview.setImageDrawable(new BitmapDrawable(getResources(), bmp));
                    return;
                }
            } catch (Exception ignored) {
            }
        }
        ivPreview.setImageDrawable(null);
        ivPreview.setBackgroundColor(0xFF1E1E1E);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                BackgroundUtil.saveBackground(this, uri);
                BackgroundUtil.setMode(this, BackgroundUtil.MODE_CUSTOM);
                refreshPreview();
                Toast.makeText(this, "背景已设置", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
