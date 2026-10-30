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

        content.addView(makeText("开源", 0xFFFFFFFF, px(14), true, Gravity.CENTER));

        LinearLayout introCard = makeCard();
        content.addView(introCard);
        introCard.addView(makeText(
                "本仓库采用 MIT License 开源，以下为本项目和使用到的开源项目说明。",
                0xFFAAAAAA, px(13), false, Gravity.START));
        introCard.addView(makeSpacer(px(6)));
        introCard.addView(makeText(
                "163MusicPro 代码部分可在 MIT 协议下使用、修改和分发；网易云音乐相关 API 与内容版权仍归网易公司所有。",
                0xFFAAAAAA, px(13), false, Gravity.START));
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
        LinearLayout card = makeCard();
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, px(4), 0, px(6));
        content.addView(card, cardParams);
        card.addView(makeText(name, 0xFFFFFFFF, px(13), true, Gravity.START));
        card.addView(makeSpacer(px(4)));
        card.addView(makeText(developer, 0xFFCCCCCC, px(13), false, Gravity.START));
        card.addView(makeSpacer(px(2)));
        TextView linkTv = makeText(githubUrl,
                getResources().getColor(R.color.colorPrimary), px(13), false, Gravity.START);
        Linkify.addLinks(linkTv, Linkify.WEB_URLS);
        card.addView(linkTv);
    }

    /** Unified M3 card container for content sections. */
    private LinearLayout makeCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card_rounded);
        card.setPadding(px(10), px(8), px(10), px(8));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, px(4), 0, px(6));
        
        return card;
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
        return (int) (baseValue * getResources().getDisplayMetrics().density + 0.5f);
    }
}
