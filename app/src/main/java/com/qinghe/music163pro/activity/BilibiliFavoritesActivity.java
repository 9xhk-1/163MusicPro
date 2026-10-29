package com.qinghe.music163pro.activity;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.qinghe.music163pro.R;
import com.qinghe.music163pro.util.WatchUiUtils;
import com.qinghe.music163pro.manager.BilibiliFavoritesManager;
import com.qinghe.music163pro.model.BilibiliFavorite;

import java.util.List;

public class BilibiliFavoritesActivity extends BaseWatchActivity {

    private BilibiliFavoritesManager favoritesManager;
    private LinearLayout listContainer;
    private TextView emptyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        favoritesManager = new BilibiliFavoritesManager(this);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(getResources().getColor(R.color.bg_dark));
        scrollView.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(px(10), px(8), px(10), px(10));

        root.addView(createTitleBar("B站收藏"));

        emptyView = new TextView(this);
        emptyView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setTextColor(getResources().getColor(R.color.text_secondary));
        emptyView.setTextSize(12);
        emptyView.setPadding(0, px(32), 0, 0);
        emptyView.setText("暂无收藏的 BV 号");
        root.addView(emptyView);

        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(listContainer);

        scrollView.addView(root);
        setContentView(scrollView);
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderFavorites();
    }

    private LinearLayout createTitleBar(String title) {
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

        TextView tvTitle = new TextView(this);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tvTitle.setLayoutParams(titleParams);
        tvTitle.setGravity(Gravity.CENTER);
        tvTitle.setText(title);
        tvTitle.setTextColor(getResources().getColor(R.color.text_primary));
        tvTitle.setTextSize(14);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        bar.addView(tvTitle);

        ImageView placeholder = WatchUiUtils.createRoundedImage(this);
        placeholder.setLayoutParams(new LinearLayout.LayoutParams(px(22), px(22)));
        bar.addView(placeholder);

        return bar;
    }

    private void renderFavorites() {
        listContainer.removeAllViews();
        List<BilibiliFavorite> favorites = favoritesManager.getFavorites();
        emptyView.setVisibility(favorites.isEmpty() ? View.VISIBLE : View.GONE);
        for (BilibiliFavorite favorite : favorites) {
            listContainer.addView(createFavoriteItem(favorite));
        }
    }

    private LinearLayout createFavoriteItem(BilibiliFavorite favorite) {
        LinearLayout item = new LinearLayout(this);
        item.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        item.setOrientation(LinearLayout.VERTICAL);
        item.setPadding(px(12), px(10), px(12), px(10));
        ((LinearLayout.LayoutParams) item.getLayoutParams()).topMargin = px(6);
        item.setBackgroundColor(getResources().getColor(R.color.surface_elevated));
        item.setClickable(true);
        item.setFocusable(true);
        item.setOnClickListener(v -> {
            Intent intent = new Intent(this, BilibiliPlaylistActivity.class);
            intent.putExtra("bvid", favorite.getBvid());
            intent.putExtra("video_title", favorite.getTitle());
            intent.putExtra("owner_name", favorite.getOwner());
            startActivity(intent);
        });

        TextView title = new TextView(this);
        title.setText(TextUtils.isEmpty(favorite.getTitle()) ? favorite.getBvid() : favorite.getTitle());
        title.setTextColor(getResources().getColor(R.color.text_primary));
        title.setTextSize(14);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        title.setMarqueeRepeatLimit(-1);
        title.setSelected(true);
        item.addView(title);

        TextView bvid = new TextView(this);
        String extra = favorite.getBvid();
        if (!TextUtils.isEmpty(favorite.getOwner())) {
            extra += " · " + favorite.getOwner();
        }
        bvid.setText(extra);
        bvid.setTextColor(getResources().getColor(R.color.text_secondary));
        bvid.setTextSize(12);
        bvid.setSingleLine(true);
        bvid.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        bvid.setMarqueeRepeatLimit(-1);
        bvid.setSelected(true);
        bvid.setPadding(0, px(2), 0, 0);
        item.addView(bvid);
        return item;
    }
}
