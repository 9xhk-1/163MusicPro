package com.qinghe.music163pro.activity;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.util.Linkify;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import com.qinghe.music163pro.R;

public class OpenSourceActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences("music163_settings", MODE_PRIVATE);
        if (prefs.getBoolean("keep_screen_on", false)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(getResources().getColor(R.color.bg_dark));
        scrollView.setFillViewport(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(px(12), px(10), px(12), px(16));

        content.addView(makeText("开源", 0xFFFFFFFF, px(22), true, Gravity.CENTER));
        content.addView(makeSpacer(px(8)));
        content.addView(makeText(
                "本仓库采用 MIT License 开源，以下为本项目和使用到的开源项目说明。",
                0xFFAAAAAA, px(15), false, Gravity.START));
        content.addView(makeSpacer(px(8)));
        content.addView(makeText(
                "163MusicPro 代码部分可在 MIT 协议下使用、修改和分发；网易云音乐相关 API 与内容版权仍归网易公司所有。",
                0xFFAAAAAA, px(15), false, Gravity.START));
        addProject(content,
                "163MusicPro",
                "开发者：Qinghe",
                "https://github.com/9xhk-1/163MusicPro");
        addProject(content,
                "Material Components for Android",
                "开发者：Google",
                "https://github.com/material-components/material-components-android");
        addProject(content,
                "AndroidX Media3 / ExoPlayer",
                "开发者：Google",
                "https://github.com/androidx/media");

        scrollView.addView(content);
        setContentView(scrollView);
    }

    private void addProject(LinearLayout content, String name, String developer, String githubUrl) {
        content.addView(makeSpacer(px(8)));
        content.addView(makeDivider());
        content.addView(makeSpacer(px(8)));
        content.addView(makeText(name, 0xFFFFFFFF, px(18), true, Gravity.START));
        content.addView(makeSpacer(px(4)));
        content.addView(makeText(developer, 0xFFCCCCCC, px(15), false, Gravity.START));
        content.addView(makeSpacer(px(2)));
        TextView linkTv = makeText(githubUrl, 0xFF5599CC, px(15), false, Gravity.START);
        Linkify.addLinks(linkTv, Linkify.WEB_URLS);
        content.addView(linkTv);
    }

    private TextView makeText(String text, int color, int sizePx, boolean bold, int gravity) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx);
        if (bold) {
            tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
        }
        tv.setGravity(gravity);
        tv.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return tv;
    }

    private android.view.View makeSpacer(int heightPx) {
        android.view.View spacer = new android.view.View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, heightPx));
        return spacer;
    }

    private android.view.View makeDivider() {
        android.view.View divider = new android.view.View(this);
        divider.setBackgroundColor(getResources().getColor(R.color.surface_elevated));
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, px(1)));
        return divider;
    }

    private int px(int baseValue) {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        return (int) (baseValue * screenWidth / 320f + 0.5f);
    }
}
