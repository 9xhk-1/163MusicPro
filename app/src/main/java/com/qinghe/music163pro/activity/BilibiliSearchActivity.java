package com.qinghe.music163pro.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.qinghe.music163pro.R;
import com.qinghe.music163pro.api.BilibiliApiHelper;

import java.util.ArrayList;
import java.util.List;

public class BilibiliSearchActivity extends BaseWatchActivity {

    private EditText etKeyword;
    private MaterialButton btnSearch;
    private TextView tvStatus;
    private LinearLayout resultContainer;
    private final List<BilibiliApiHelper.BilibiliSearchVideo> searchResults = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(getResources().getColor(R.color.bg_dark));
        scrollView.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(px(10), px(8), px(10), px(10));

        root.addView(createTitleBar());

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(getResources().getColor(R.color.surface_elevated));
        card.setPadding(px(12), px(12), px(12), px(12));
        root.addView(card);

        TextView label = new TextView(this);
        label.setText("搜索视频");
        label.setTextColor(getResources().getColor(R.color.text_secondary));
        label.setTextSize(12);
        card.addView(label);

        com.google.android.material.textfield.TextInputLayout keywordWrap =
                com.qinghe.music163pro.util.WatchUiUtils.createWatchTextInput(this, "输入标题、UP主、关键词");
        etKeyword = (com.google.android.material.textfield.TextInputEditText) keywordWrap.getEditText();
        etKeyword.setInputType(InputType.TYPE_CLASS_TEXT);
        LinearLayout.LayoutParams keywordParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, px(46));
        keywordParams.topMargin = px(6);
        keywordWrap.setLayoutParams(keywordParams);
        card.addView(keywordWrap);

        btnSearch = createWatchButton("搜索", false);
        btnSearch.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ((LinearLayout.LayoutParams) btnSearch.getLayoutParams()).topMargin = px(8);
        btnSearch.setOnClickListener(v -> searchVideos());
        card.addView(btnSearch);

        tvStatus = new TextView(this);
        tvStatus.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        tvStatus.setTextColor(getResources().getColor(R.color.text_secondary));
        tvStatus.setTextSize(11);
        tvStatus.setPadding(0, px(10), 0, px(4));
        tvStatus.setVisibility(View.GONE);
        root.addView(tvStatus);

        resultContainer = new LinearLayout(this);
        resultContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(resultContainer);

        scrollView.addView(root);
        setContentView(scrollView);
    }

    private void searchVideos() {
        String keyword = etKeyword.getText().toString().trim();
        if (TextUtils.isEmpty(keyword)) {
            Toast.makeText(this, "请输入搜索关键词", Toast.LENGTH_SHORT).show();
            return;
        }
        btnSearch.setEnabled(false);
        tvStatus.setVisibility(View.VISIBLE);
        tvStatus.setText("正在搜索...");
        resultContainer.removeAllViews();
        searchResults.clear();

        SharedPreferences prefs = getSharedPreferences("music163_settings", MODE_PRIVATE);
        String cookie = prefs.getString("bilibili_cookie", "");
        BilibiliApiHelper.searchVideos(keyword, cookie, new BilibiliApiHelper.SearchVideosCallback() {
            @Override
            public void onResult(List<BilibiliApiHelper.BilibiliSearchVideo> videos) {
                btnSearch.setEnabled(true);
                searchResults.clear();
                searchResults.addAll(videos);
                tvStatus.setText(videos.isEmpty() ? "没有找到相关视频" : "找到 " + videos.size() + " 个视频");
                renderResults();
            }

            @Override
            public void onError(String message) {
                btnSearch.setEnabled(true);
                tvStatus.setText("搜索失败: " + message);
            }
        });
    }

    private void renderResults() {
        resultContainer.removeAllViews();
        for (BilibiliApiHelper.BilibiliSearchVideo video : searchResults) {
            resultContainer.addView(createResultItem(video));
        }
    }

    private View createResultItem(BilibiliApiHelper.BilibiliSearchVideo video) {
        LinearLayout item = new LinearLayout(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = px(8);
        item.setLayoutParams(params);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setPadding(px(12), px(10), px(12), px(10));
        item.setBackgroundColor(getResources().getColor(R.color.surface_elevated));
        item.setClickable(true);
        item.setFocusable(true);
        item.setOnClickListener(v -> {
            Intent intent = new Intent(this, BilibiliPlaylistActivity.class);
            intent.putExtra("bvid", video.bvid);
            intent.putExtra("video_title", video.title);
            intent.putExtra("owner_name", video.ownerName);
            startActivity(intent);
        });

        TextView title = new TextView(this);
        title.setText(video.title);
        title.setTextColor(getResources().getColor(R.color.text_primary));
        title.setTextSize(13);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        title.setMarqueeRepeatLimit(-1);
        title.setSelected(true);
        item.addView(title);

        TextView sub = new TextView(this);
        sub.setText(video.bvid + " · " + video.ownerName + " · " + formatDuration(video.duration));
        sub.setTextColor(getResources().getColor(R.color.text_secondary));
        sub.setTextSize(11);
        sub.setSingleLine(true);
        sub.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        sub.setMarqueeRepeatLimit(-1);
        sub.setSelected(true);
        sub.setPadding(0, px(3), 0, 0);
        item.addView(sub);

        if (!TextUtils.isEmpty(video.description)) {
            TextView desc = new TextView(this);
            desc.setText(video.description);
            desc.setTextColor(getResources().getColor(R.color.text_secondary));
            desc.setTextSize(10);
            desc.setMaxLines(2);
            desc.setEllipsize(TextUtils.TruncateAt.END);
            desc.setPadding(0, px(3), 0, 0);
            item.addView(desc);
        }
        return item;
    }

    private LinearLayout createTitleBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(48)));
        bar.setGravity(Gravity.CENTER_VERTICAL);

        MaterialButton back = new MaterialButton(this);
        back.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(48), dpToPx(48)));
        back.setIconResource(R.drawable.ic_arrow_back);
        back.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        back.setIconSize(dpToPx(20));
        back.setIconPadding(0);
        back.setInsetTop(0);
        back.setInsetBottom(0);
        back.setMinWidth(0);
        back.setMinimumWidth(0);
        back.setPadding(0, 0, 0, 0);
        back.setContentDescription("返回");
        back.setOnClickListener(v -> finish());
        bar.addView(back);

        TextView title = new TextView(this);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        title.setLayoutParams(titleParams);
        title.setGravity(Gravity.CENTER);
        title.setText("搜索视频");
        title.setTextColor(getResources().getColor(R.color.text_primary));
        title.setTextSize(14);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        bar.addView(title);

        ImageView placeholder = new ImageView(this);
        placeholder.setLayoutParams(new LinearLayout.LayoutParams(px(22), px(22)));
        bar.addView(placeholder);
        return bar;
    }

    private String formatDuration(int seconds) {
        int min = seconds / 60;
        int sec = seconds % 60;
        return String.format("%d:%02d", min, sec);
    }
}
