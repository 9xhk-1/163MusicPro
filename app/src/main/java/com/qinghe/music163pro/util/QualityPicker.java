package com.qinghe.music163pro.util;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.qinghe.music163pro.R;

import java.util.List;

/**
 * 音质选择浮层 —— 从原作者「播放音质」页面原样抽出来的唯一实现。
 *
 * 之前只有播放页三点菜单和单曲下载用这套（遮罩 + 居中 ScrollView + 标题栏 +
 * MaterialCardView 卡片行 + 右侧会员徽标），歌单批量下载用的是另一套简单列表对话框，
 * 三处观感不一致。现在播放音质、单曲下载音质、批量下载音质都走本类，
 * 保证档位名、码率描述、徽标颜色、选中态、置灰态完全一致。
 *
 * 本类只负责展示与点击回传，不做权限检测；档位可用性由调用方按
 * {@link com.qinghe.music163pro.api.MusicApiHelper.SongQualityInfo} 的结果转成 Row。
 */
public final class QualityPicker {

    /**
     * 唯一一份音质档位表：{level key, 名称, 码率描述}。
     * 播放音质、单曲下载音质、歌单批量下载音质都必须从这里取，避免口径漂移。
     * 顺序 = 网易云 qualityLevelRank 从低到高，名称与官方「当前歌曲音质」面板一致：
     * <pre>
     *  免费     标准 / 较高
     *  黑胶 VIP  极高 HQ / 无损 SQ / Hi-Res / 高清臻音
     *  黑胶 SVIP 沉浸环绕声 / 臻音全景声 / 超清母带
     * </pre>
     * 会员归属不由本表决定，统一走 MusicApiHelper.requiredMembership()。
     */
    public static final String[][] LEVELS = {
            {"standard", "标准",       "128K MP3"},
            {"higher",   "较高",       "192K MP3"},
            {"exhigh",   "极高 HQ",    "近 CD 音质 · 最高 320kbps"},
            {"lossless", "无损 SQ",    "高保真无损 · 最高 48kHz/16bit"},
            {"hires",    "Hi-Res",     "192kHz/24bit"},
            {"jyeffect", "高清臻音",   "Spatial Audio · 96kHz/24bit"},
            {"sky",      "沉浸环绕声", "Surround Audio · 最高 5.1 声道"},
            {"dolby",    "臻音全景声", "Audio Vivid · 最高 7.1.4 声道"},
            {"jymaster", "超清母带",   "Master · 极致细节 192kHz/24bit"},
    };

    public static final class Row {
        public final String level;      // 内部音质 key，如 "jymaster"
        public final String name;       // 主标题，如 "超清母带"
        public final String detail;     // 副标题，如 "极致细节 192kHz/24bit"
        public final String badge;      // 右侧徽标，如 "SVIP" / "需 SVIP" / "12/20 可下"
        public final int badgeColor;
        public final boolean selected;
        public final boolean enabled;

        public Row(String level, String name, String detail, String badge,
                   int badgeColor, boolean selected, boolean enabled) {
            this.level = level;
            this.name = name;
            this.detail = detail;
            this.badge = badge;
            this.badgeColor = badgeColor;
            this.selected = selected;
            this.enabled = enabled;
        }
    }

    public interface OnPickListener {
        void onPick(Row row);
    }

    /** 浮层内部引用，用于「先显示检测中、接口回来再替换行」而不重建整个遮罩。 */
    private static final class Holder {
        Activity activity;
        FrameLayout overlay;
        LinearLayout content;
        TextView subtitleView;
        int rowStart;
        OnPickListener listener;
        Runnable onClose;
    }

    private QualityPicker() {}

    public static FrameLayout show(Activity activity, String title, String subtitle,
                                   List<Row> rows, OnPickListener listener) {
        return show(activity, title, subtitle, rows, listener, null);
    }

    /**
     * 创建并显示浮层，返回遮罩 View。
     * @param onClose 关闭时调用（播放页传 dismissOverlay，用来同步清掉自己的字段）；
     *                为 null 时由本类直接移除遮罩。
     */
    public static FrameLayout show(Activity activity, String title, String subtitle,
                                   List<Row> rows, OnPickListener listener, Runnable onClose) {
        FrameLayout rootView = activity.getWindow().getDecorView()
                .findViewById(android.R.id.content);

        final Holder holder = new Holder();
        holder.activity = activity;
        holder.listener = listener;
        holder.onClose = onClose;

        final FrameLayout overlay = new FrameLayout(activity);
        overlay.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        overlay.setBackgroundColor(0xCC000000);
        overlay.setClickable(true);
        holder.overlay = overlay;

        ScrollView scrollView = new ScrollView(activity);
        FrameLayout.LayoutParams scrollParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        scrollParams.gravity = Gravity.CENTER;
        scrollView.setLayoutParams(scrollParams);
        // 吃掉内容区点击，避免穿透到遮罩
        scrollView.setOnClickListener(v -> { });

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        content.setPadding(px(activity, 20), px(activity, 16),
                px(activity, 20), px(activity, 16));
        holder.content = content;
        content.addView(createTitleBar(activity, title, () -> close(holder)));

        TextView sub = new TextView(activity);
        sub.setText(subtitle == null ? "" : subtitle);
        sub.setTextColor(0xFFAEAEB2);
        sub.setTextSize(11);
        sub.setMaxLines(2);
        sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        subParams.bottomMargin = px(activity, 6);
        sub.setLayoutParams(subParams);
        sub.setVisibility(subtitle == null || subtitle.isEmpty()
                ? android.view.View.GONE : android.view.View.VISIBLE);
        content.addView(sub);
        holder.subtitleView = sub;

        holder.rowStart = content.getChildCount();
        replaceRows(holder, rows);

        scrollView.addView(content);
        overlay.addView(scrollView);
        overlay.setTag(holder);
        rootView.addView(overlay);
        return overlay;
    }

    /** 接口检测完成后就地替换档位行，遮罩不闪、不重建。 */
    public static void update(FrameLayout overlay, String subtitle,
                              List<Row> rows, OnPickListener listener) {
        if (overlay == null) return;
        Object tag = overlay.getTag();
        if (!(tag instanceof Holder)) return;
        Holder h = (Holder) tag;
        if (h.activity.isFinishing()) return;
        h.listener = listener;
        if (h.subtitleView != null) {
            h.subtitleView.setText(subtitle == null ? "" : subtitle);
            h.subtitleView.setVisibility(subtitle == null || subtitle.isEmpty()
                    ? android.view.View.GONE : android.view.View.VISIBLE);
        }
        replaceRows(h, rows);
    }

    private static void replaceRows(Holder h, List<Row> rows) {
        int count = h.content.getChildCount() - h.rowStart;
        if (count > 0) h.content.removeViews(h.rowStart, count);
        if (rows == null) return;
        for (Row row : rows) {
            h.content.addView(createRowCard(h.activity, row, () -> {
                close(h);
                if (h.listener != null) h.listener.onPick(row);
            }));
        }
    }

    private static void close(Holder h) {
        if (h.onClose != null) {
            h.onClose.run();
        } else {
            dismiss(h.activity, h.overlay);
        }
    }

    public static void dismiss(Activity activity, FrameLayout overlay) {
        if (activity == null || overlay == null || activity.isFinishing()) return;
        try {
            FrameLayout rootView = activity.getWindow().getDecorView()
                    .findViewById(android.R.id.content);
            rootView.removeView(overlay);
        } catch (Exception ignored) {}
    }

    /** 与原作者 createOverlayTitleBar 完全一致：11sp 白色标题 + 右上角透明关闭键。 */
    private static FrameLayout createTitleBar(Activity activity, String titleText,
                                              Runnable onClose) {
        FrameLayout titleBar = new FrameLayout(activity);
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, px(activity, 36));
        barParams.bottomMargin = px(activity, 4);
        titleBar.setLayoutParams(barParams);

        TextView title = new TextView(activity);
        title.setText(titleText);
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(11);
        title.setMaxLines(1);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        titleParams.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        titleParams.setMarginEnd(px(activity, 36));
        title.setLayoutParams(titleParams);
        titleBar.addView(title);

        MaterialButton btnClose = new MaterialButton(activity);
        btnClose.setIconResource(R.drawable.ic_close);
        btnClose.setIconTint(android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(activity, R.color.text_primary)));
        btnClose.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.TRANSPARENT));
        btnClose.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        btnClose.setIconSize(px(activity, 18));
        btnClose.setIconPadding(0);
        btnClose.setInsetTop(0);
        btnClose.setInsetBottom(0);
        btnClose.setMinWidth(0);
        btnClose.setMinimumWidth(0);
        btnClose.setMinHeight(0);
        btnClose.setMinimumHeight(0);
        btnClose.setPadding(0, 0, 0, 0);
        btnClose.setCornerRadius(px(activity, 24));
        btnClose.setContentDescription("关闭");
        int closeSize = px(activity, 36);
        FrameLayout.LayoutParams closeParams =
                new FrameLayout.LayoutParams(closeSize, closeSize);
        closeParams.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        btnClose.setLayoutParams(closeParams);
        btnClose.setClickable(true);
        btnClose.setFocusable(true);
        btnClose.setOnClickListener(v -> onClose.run());
        titleBar.addView(btnClose);
        return titleBar;
    }

    /**
     * 原作者 createQualityCard 的卡片底：14dp 圆角、零高度阴影、4dp 行距。
     * 「播放器信息」浮层也用它，所以样式只在这里定义一次。
     */
    public static MaterialCardView createCard(Activity activity,
                                              boolean selected, boolean enabled) {
        MaterialCardView card = new MaterialCardView(activity);
        card.setRadius(px(activity, 14));
        card.setCardElevation(0);
        card.setCardBackgroundColor(ContextCompat.getColor(activity,
                selected ? R.color.primary_container : R.color.surface_elevated));
        card.setClickable(enabled);
        card.setFocusable(enabled);
        card.setEnabled(enabled);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = px(activity, 4);
        card.setLayoutParams(params);
        return card;
    }

    /** 与原作者 createQualityCard + buildQualityRows 完全一致的一行。 */
    private static MaterialCardView createRowCard(Activity activity, Row row,
                                                   Runnable onClick) {
        LinearLayout line = new LinearLayout(activity);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(px(activity, 10), px(activity, 9),
                px(activity, 10), px(activity, 9));
        line.setMinimumHeight(px(activity, 48));

        MaterialCardView card = createCard(activity, row.selected, row.enabled);
        card.addView(line);

        LinearLayout leftCol = new LinearLayout(activity);
        leftCol.setOrientation(LinearLayout.VERTICAL);
        leftCol.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView tvName = new TextView(activity);
        tvName.setText(row.name);
        tvName.setTextColor(ContextCompat.getColor(activity, row.enabled
                ? (row.selected ? R.color.on_primary_container : R.color.text_primary)
                : R.color.text_disabled));
        tvName.setTextSize(14);
        leftCol.addView(tvName);

        TextView tvDetail = new TextView(activity);
        tvDetail.setText(row.detail);
        tvDetail.setTextColor(row.enabled ? 0x80FFFFFF : 0x30FFFFFF);
        tvDetail.setTextSize(11);
        leftCol.addView(tvDetail);
        line.addView(leftCol);

        TextView badge = new TextView(activity);
        badge.setText(row.badge);
        badge.setTextColor(row.enabled ? row.badgeColor : 0x60FFFFFF);
        badge.setTextSize(11);
        badge.setPadding(px(activity, 6), px(activity, 2),
                px(activity, 6), px(activity, 2));
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(px(activity, 4));
        bg.setColor(0x22FFFFFF);
        badge.setBackground(bg);
        line.addView(badge);

        if (row.enabled) {
            card.setOnClickListener(v -> onClick.run());
        }
        return card;
    }

    private static int px(Activity activity, int dp) {
        return WatchUiUtils.px(activity, dp);
    }

    // 徽标配色集中在这里，两处音质页面不会一个橙一个金
    public static final int COLOR_FREE       = 0xFF4CAF50;
    public static final int COLOR_VIP        = 0xFFFFAA00;
    public static final int COLOR_SVIP       = 0xFFFFD54F;
    public static final int COLOR_DISABLED   = 0x60FFFFFF;
    public static final int COLOR_DOWNLOADED = 0xFF4CAF50;
}
