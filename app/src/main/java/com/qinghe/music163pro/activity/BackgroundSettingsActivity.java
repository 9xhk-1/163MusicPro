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
import android.widget.TextView;
import android.widget.Toast;

import com.qinghe.music163pro.util.BackgroundUtil;

/**
 * Custom background settings: pick an image to use as the main player and
 * lyrics screen background, or reset to default.
 */
public class BackgroundSettingsActivity extends BaseWatchActivity {

    private static final int REQ_PICK_IMAGE = 3001;

    private ImageView ivPreview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF121212);

        root.addView(buildTitleBar());

        ivPreview = new ImageView(this);
        ivPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        ivPreview.setBackgroundColor(0xFF1E1E1E);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        previewParams.setMargins(px(14), px(8), px(14), px(8));
        ivPreview.setLayoutParams(previewParams);
        root.addView(ivPreview);

        TextView hint = new TextView(this);
        hint.setText("选择一张图片作为主界面和歌词界面的背景");
        hint.setTextColor(0x80FFFFFF);
        hint.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(12));
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(px(10), 0, px(10), px(8));
        root.addView(hint);

        root.addView(buildButton("选择图片", v -> pickImage()));
        root.addView(buildButton("恢复默认背景", v -> resetBackground()));

        setContentView(root);
        refreshPreview();
    }

    private View buildTitleBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(0xFF1A1A1A);
        bar.setPadding(px(8), px(8), px(12), px(8));

        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextColor(0xFFBB86FC);
        back.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(22));
        back.setPadding(px(4), 0, px(8), 0);
        back.setClickable(true);
        back.setFocusable(true);
        back.setOnClickListener(v -> finish());
        bar.addView(back);

        TextView title = new TextView(this);
        title.setText("自定义背景");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(15));
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        title.setLayoutParams(titleParams);
        bar.addView(title);

        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(px(24), 1));
        bar.addView(spacer);
        return bar;
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

    private void resetBackground() {
        BackgroundUtil.clearBackground(this);
        refreshPreview();
        Toast.makeText(this, "已恢复默认背景", Toast.LENGTH_SHORT).show();
    }

    private void refreshPreview() {
        if (BackgroundUtil.hasCustomBackground(this)) {
            try {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = 4;
                Bitmap bmp = BitmapFactory.decodeFile(
                        BackgroundUtil.getBackgroundFile(this).getAbsolutePath(), opts);
                if (bmp != null) {
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
                refreshPreview();
                Toast.makeText(this, "背景已设置", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
