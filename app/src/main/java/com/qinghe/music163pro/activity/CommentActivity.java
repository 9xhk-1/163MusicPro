package com.qinghe.music163pro.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.qinghe.music163pro.R;
import com.qinghe.music163pro.api.MusicApiHelper;
import com.qinghe.music163pro.util.MusicLog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Comment activity - displays and manages comments for a song.
 * Adapted for watch screen (320x360 DPI).
 */
public class CommentActivity extends AppCompatActivity {

    private static final String TAG = "CommentActivity";
    private static final String PREFS_NAME = "music163_settings";
    private static final int PAGE_SIZE = 20;

    private static final int COLOR_BG = 0xFF121212;
    private static final int COLOR_TEXT_PRIMARY = 0xFFFFFFFF;
    private static final int COLOR_TEXT_SECONDARY = 0x80FFFFFF;
    private static final int COLOR_ACCENT = 0xFF03DAC6;
    private static final int COLOR_DIVIDER = 0xFF1E1E1E;
    private static final int COLOR_INPUT_BG = 0xFF333333;

    private static final int[] AVATAR_COLORS = {
            0xFFE91E63, 0xFF9C27B0, 0xFF3F51B5, 0xFF2196F3,
            0xFF009688, 0xFF4CAF50, 0xFFFF9800, 0xFFFF5722
    };

    private long songId;
    private String songName;
    private String cookie;

    private int currentSortType = 99; // 99=推荐, 2=最热, 3=最新
    private int pageNo = 1;
    private String cursor = null;
    private boolean hasMore = true;
    private boolean isLoading = false;
    private int totalCount = 0;

    // Reply state
    private long replyToCommentId = -1;
    private String replyToNickname = null;

    // UI references
    private LinearLayout commentListLayout;
    private ScrollView commentScrollView;
    private EditText inputField;
    private TextView sendButton;
    private TextView tabRecommend;
    private TextView tabHot;
    private TextView tabNew;
    private TextView totalCountText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (prefs.getBoolean("keep_screen_on", false)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        songId = getIntent().getLongExtra("song_id", 0);
        songName = getIntent().getStringExtra("song_name");
        cookie = getIntent().getStringExtra("cookie");

        if (songName == null) songName = "未知歌曲";
        if (cookie == null) cookie = "";

        MusicLog.op(TAG, "打开评论页", "songId=" + songId + ", songName=" + songName);

        buildUI();
        loadComments(true);
    }

    // ──────────────────────────────────────────────────────
    //  UI Construction
    // ──────────────────────────────────────────────────────

    private void buildUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_BG);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        root.addView(buildTopBar());
        root.addView(buildCommentList(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(buildBottomBar());

        setContentView(root);
    }

    private View buildTopBar() {
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.VERTICAL);
        topBar.setBackgroundColor(0xFF1A1A1A);
        topBar.setPadding(px(8), px(6), px(8), px(4));

        // Song name row
        LinearLayout nameRow = new LinearLayout(this);
        nameRow.setOrientation(LinearLayout.HORIZONTAL);
        nameRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView songTitle = new TextView(this);
        songTitle.setText(songName);
        songTitle.setTextColor(COLOR_TEXT_PRIMARY);
        songTitle.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(16));
        songTitle.setTypeface(songTitle.getTypeface(), Typeface.BOLD);
        songTitle.setSingleLine(true);
        songTitle.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        songTitle.setMarqueeRepeatLimit(-1);
        songTitle.setSelected(true);
        songTitle.setHorizontalFadingEdgeEnabled(true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        songTitle.setLayoutParams(titleParams);
        nameRow.addView(songTitle);

        totalCountText = new TextView(this);
        totalCountText.setTextColor(COLOR_TEXT_SECONDARY);
        totalCountText.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(13));
        totalCountText.setPadding(px(6), 0, 0, 0);
        nameRow.addView(totalCountText);

        topBar.addView(nameRow);

        // Sort tabs row
        LinearLayout tabRow = new LinearLayout(this);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        tabRow.setGravity(Gravity.CENTER_VERTICAL);
        tabRow.setPadding(0, px(4), 0, px(2));

        tabRecommend = makeSortTab("推荐", 99);
        tabHot = makeSortTab("最热", 2);
        tabNew = makeSortTab("最新", 3);

        tabRow.addView(tabRecommend);
        tabRow.addView(makeSortSpacer());
        tabRow.addView(tabHot);
        tabRow.addView(makeSortSpacer());
        tabRow.addView(tabNew);

        topBar.addView(tabRow);

        updateSortTabs();
        return topBar;
    }

    private TextView makeSortTab(String label, int sortType) {
        TextView tab = new TextView(this);
        tab.setText(label);
        tab.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(14));
        tab.setPadding(px(10), px(4), px(10), px(4));
        tab.setGravity(Gravity.CENTER);

        int minTouchTarget = px(40);
        tab.setMinHeight(minTouchTarget);
        tab.setMinWidth(minTouchTarget);

        tab.setOnClickListener(v -> {
            if (currentSortType == sortType || isLoading) return;
            currentSortType = sortType;
            updateSortTabs();
            loadComments(true);
        });
        return tab;
    }

    private View makeSortSpacer() {
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(px(2), px(12)));
        spacer.setBackgroundColor(COLOR_DIVIDER);
        return spacer;
    }

    private void updateSortTabs() {
        styleSortTab(tabRecommend, currentSortType == 99);
        styleSortTab(tabHot, currentSortType == 2);
        styleSortTab(tabNew, currentSortType == 3);
    }

    private void styleSortTab(TextView tab, boolean active) {
        if (active) {
            tab.setTextColor(0xFFBB86FC);
            tab.setTypeface(tab.getTypeface(), Typeface.BOLD);
        } else {
            tab.setTextColor(COLOR_TEXT_SECONDARY);
            tab.setTypeface(Typeface.DEFAULT);
        }
    }

    private View buildCommentList() {
        commentScrollView = new ScrollView(this);
        commentScrollView.setFillViewport(true);

        commentListLayout = new LinearLayout(this);
        commentListLayout.setOrientation(LinearLayout.VERTICAL);
        commentListLayout.setPadding(px(6), px(4), px(6), px(4));

        commentScrollView.addView(commentListLayout);

        // Infinite scroll detection
        commentScrollView.getViewTreeObserver().addOnScrollChangedListener(() -> {
            if (isLoading || !hasMore) return;

            View child = commentScrollView.getChildAt(0);
            if (child == null) return;

            int scrollY = commentScrollView.getScrollY();
            int scrollViewHeight = commentScrollView.getHeight();
            int childHeight = child.getHeight();

            if (scrollY + scrollViewHeight >= childHeight - px(60)) {
                loadComments(false);
            }
        });

        return commentScrollView;
    }

    private View buildBottomBar() {
        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setBackgroundColor(0xFF1A1A1A);
        bottomBar.setPadding(px(6), px(4), px(6), px(4));
        bottomBar.setGravity(Gravity.CENTER_VERTICAL);

        inputField = new EditText(this);
        inputField.setHint("写评论...");
        inputField.setHintTextColor(0xFF666666);
        inputField.setTextColor(COLOR_TEXT_PRIMARY);
        inputField.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(14));
        inputField.setSingleLine(true);
        inputField.setBackgroundColor(COLOR_INPUT_BG);
        inputField.setPadding(px(8), px(6), px(8), px(6));

        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setColor(COLOR_INPUT_BG);
        inputBg.setCornerRadius(px(12));
        inputField.setBackground(inputBg);

        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        inputField.setLayoutParams(inputParams);
        bottomBar.addView(inputField);

        sendButton = new TextView(this);
        sendButton.setText("发送");
        sendButton.setTextColor(COLOR_ACCENT);
        sendButton.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(15));
        sendButton.setTypeface(sendButton.getTypeface(), Typeface.BOLD);
        sendButton.setPadding(px(10), px(8), px(6), px(8));
        sendButton.setMinHeight(px(40));
        sendButton.setMinWidth(px(40));
        sendButton.setGravity(Gravity.CENTER);
        sendButton.setOnClickListener(v -> submitComment());
        bottomBar.addView(sendButton);

        return bottomBar;
    }

    // ──────────────────────────────────────────────────────
    //  Comment Loading
    // ──────────────────────────────────────────────────────

    private void loadComments(boolean reset) {
        if (isLoading) return;
        isLoading = true;

        if (reset) {
            pageNo = 1;
            cursor = null;
            hasMore = true;
            commentListLayout.removeAllViews();
            addLoadingIndicator();
        }

        MusicLog.d(TAG, "加载评论: songId=" + songId + ", page=" + pageNo
                + ", sort=" + currentSortType + ", cursor=" + cursor);

        MusicApiHelper.getComments(songId, pageNo, PAGE_SIZE, currentSortType,
                cursor, cookie, new MusicApiHelper.CommentCallback() {
                    @Override
                    public void onResult(JSONObject commentData) {
                        runOnUiThread(() -> handleCommentsResult(commentData, reset));
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> {
                            isLoading = false;
                            removeLoadingIndicator();
                            MusicLog.e(TAG, "获取评论失败: " + message);
                            if (reset && commentListLayout.getChildCount() == 0) {
                                addEmptyHint("加载失败，点击重试");
                            }
                            Toast.makeText(CommentActivity.this,
                                    "加载评论失败", Toast.LENGTH_SHORT).show();
                        });
                    }
                });
    }

    private void handleCommentsResult(JSONObject data, boolean reset) {
        isLoading = false;
        removeLoadingIndicator();

        if (data == null) {
            if (reset) addEmptyHint("暂无评论");
            return;
        }

        try {
            JSONObject d = data.optJSONObject("data");
            if (d == null) {
                // Some API versions return at top level
                d = data;
            }

            totalCount = d.optInt("totalCount", 0);
            hasMore = d.optBoolean("hasMore", false);
            String newCursor = d.optString("cursor", null);
            if (newCursor != null && !newCursor.isEmpty()) {
                cursor = newCursor;
            }

            if (totalCountText != null) {
                totalCountText.setText(totalCount > 0 ? totalCount + "条评论" : "评论");
            }

            JSONArray comments = d.optJSONArray("comments");
            if (comments == null || comments.length() == 0) {
                if (reset) addEmptyHint("暂无评论，来抢沙发吧~");
                return;
            }

            if (reset) {
                commentListLayout.removeAllViews();
            }

            for (int i = 0; i < comments.length(); i++) {
                JSONObject comment = comments.getJSONObject(i);
                commentListLayout.addView(buildCommentItem(comment));

                // Divider between comments
                if (i < comments.length() - 1) {
                    commentListLayout.addView(makeDivider());
                }
            }

            pageNo++;

        } catch (Exception e) {
            MusicLog.e(TAG, "解析评论数据失败", e);
            if (reset) addEmptyHint("数据解析失败");
        }
    }

    // ──────────────────────────────────────────────────────
    //  Comment Item View
    // ──────────────────────────────────────────────────────

    private View buildCommentItem(JSONObject comment) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setPadding(px(4), px(6), px(4), px(6));

        long commentId = comment.optLong("commentId", 0);
        String content = comment.optString("content", "");
        long time = comment.optLong("time", 0);
        int likedCount = comment.optInt("likedCount", 0);
        boolean liked = comment.optBoolean("liked", false);

        // User info
        JSONObject user = comment.optJSONObject("user");
        String nickname = "匿名用户";
        long userId = 0;
        if (user != null) {
            nickname = user.optString("nickname", "匿名用户");
            userId = user.optLong("userId", 0);
        }

        // IP location
        String ipLocation = "";
        JSONObject ipObj = comment.optJSONObject("ipLocation");
        if (ipObj != null) {
            ipLocation = ipObj.optString("location", "");
        }

        // ── Header row: avatar + nickname + time + IP ──
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        header.addView(buildAvatarView(nickname, userId));

        LinearLayout nameTimeCol = new LinearLayout(this);
        nameTimeCol.setOrientation(LinearLayout.VERTICAL);
        nameTimeCol.setPadding(px(5), 0, 0, 0);
        LinearLayout.LayoutParams nameTimeParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        nameTimeCol.setLayoutParams(nameTimeParams);

        TextView nickTv = new TextView(this);
        nickTv.setText(nickname);
        nickTv.setTextColor(0xFF82B1FF);
        nickTv.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(14));
        nickTv.setSingleLine(true);
        nickTv.setEllipsize(TextUtils.TruncateAt.END);
        nameTimeCol.addView(nickTv);

        String timeAndIp = formatTime(time);
        if (!ipLocation.isEmpty()) {
            timeAndIp += " · " + ipLocation;
        }
        TextView timeTv = new TextView(this);
        timeTv.setText(timeAndIp);
        timeTv.setTextColor(COLOR_TEXT_SECONDARY);
        timeTv.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(12));
        timeTv.setSingleLine(true);
        nameTimeCol.addView(timeTv);

        header.addView(nameTimeCol);
        item.addView(header);

        // ── Content ──
        TextView contentTv = new TextView(this);
        contentTv.setText(content);
        contentTv.setTextColor(COLOR_TEXT_PRIMARY);
        contentTv.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(15));
        contentTv.setPadding(px(27), px(4), 0, px(2));
        contentTv.setLineSpacing(px(2), 1f);
        item.addView(contentTv);

        // ── beReplied (quoted reply) ──
        JSONArray beReplied = comment.optJSONArray("beReplied");
        if (beReplied != null && beReplied.length() > 0) {
            JSONObject replied = beReplied.optJSONObject(0);
            if (replied != null) {
                String repliedContent = replied.optString("content", "");
                JSONObject repliedUser = replied.optJSONObject("user");
                String repliedNick = repliedUser != null
                        ? repliedUser.optString("nickname", "用户") : "用户";

                if (!repliedContent.isEmpty()) {
                    TextView repliedTv = new TextView(this);
                    repliedTv.setText("@" + repliedNick + ": " + repliedContent);
                    repliedTv.setTextColor(COLOR_TEXT_SECONDARY);
                    repliedTv.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(13));
                    repliedTv.setPadding(px(27), px(2), px(4), px(2));
                    repliedTv.setMaxLines(2);
                    repliedTv.setEllipsize(TextUtils.TruncateAt.END);

                    GradientDrawable repliedBg = new GradientDrawable();
                    repliedBg.setColor(0xFF2A2A2A);
                    repliedBg.setCornerRadius(px(4));
                    repliedTv.setBackground(repliedBg);

                    LinearLayout.LayoutParams repliedParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT);
                    repliedParams.setMargins(px(27), px(2), 0, px(2));
                    repliedTv.setLayoutParams(repliedParams);

                    item.addView(repliedTv);
                }
            }
        }

        // ── Footer row: like + reply ──
        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(px(27), px(2), 0, 0);

        // Like button
        final boolean[] isLiked = {liked};
        final int[] currentLikeCount = {likedCount};

        TextView likeTv = new TextView(this);
        updateLikeText(likeTv, isLiked[0], currentLikeCount[0]);
        likeTv.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(13));
        likeTv.setPadding(0, px(4), px(12), px(4));
        likeTv.setMinHeight(px(30));
        likeTv.setGravity(Gravity.CENTER_VERTICAL);
        likeTv.setOnClickListener(v -> {
            boolean newLike = !isLiked[0];
            MusicApiHelper.likeComment(songId, commentId, newLike, cookie,
                    new MusicApiHelper.CommentActionCallback() {
                        @Override
                        public void onResult(boolean success) {
                            runOnUiThread(() -> {
                                if (success) {
                                    isLiked[0] = newLike;
                                    currentLikeCount[0] += newLike ? 1 : -1;
                                    if (currentLikeCount[0] < 0) currentLikeCount[0] = 0;
                                    updateLikeText(likeTv, isLiked[0], currentLikeCount[0]);
                                } else {
                                    Toast.makeText(CommentActivity.this,
                                            "操作失败", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }

                        @Override
                        public void onError(String message) {
                            runOnUiThread(() -> Toast.makeText(CommentActivity.this,
                                    "操作失败", Toast.LENGTH_SHORT).show());
                        }
                    });
        });
        footer.addView(likeTv);

        // Reply button
        final String finalNickname = nickname;
        TextView replyTv = new TextView(this);
        replyTv.setText("回复");
        replyTv.setTextColor(COLOR_TEXT_SECONDARY);
        replyTv.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(13));
        replyTv.setPadding(px(12), px(4), px(12), px(4));
        replyTv.setMinHeight(px(30));
        replyTv.setGravity(Gravity.CENTER_VERTICAL);
        replyTv.setOnClickListener(v -> enterReplyMode(commentId, finalNickname));
        footer.addView(replyTv);

        // Floor comments link
        JSONObject floorComment = comment.optJSONObject("showFloorComment");
        if (floorComment != null) {
            int replyCount = floorComment.optInt("replyCount", 0);
            if (replyCount > 0) {
                TextView floorTv = new TextView(this);
                floorTv.setText("查看" + replyCount + "条回复 >");
                floorTv.setTextColor(0xFF82B1FF);
                floorTv.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(13));
                floorTv.setPadding(px(12), px(4), 0, px(4));
                floorTv.setMinHeight(px(30));
                floorTv.setGravity(Gravity.CENTER_VERTICAL);
                floorTv.setOnClickListener(v -> openFloorComments(commentId));
                footer.addView(floorTv);
            }
        }

        item.addView(footer);

        // Long-press to delete comment (with confirmation)
        final String displayNick = nickname;
        final String displayContent = content;
        item.setOnLongClickListener(v -> {
            showConfirmDialog("删除评论", "确定删除「" + displayNick + "」的评论？\n\n" + (displayContent.length() > 40 ? displayContent.substring(0, 40) + "..." : displayContent), () -> {
                MusicApiHelper.deleteComment(songId, commentId, cookie, new MusicApiHelper.CommentActionCallback() {
                    @Override
                    public void onResult(boolean success) {
                        runOnUiThread(() -> {
                            if (success) {
                                Toast.makeText(CommentActivity.this, "评论已删除", Toast.LENGTH_SHORT).show();
                                loadComments(true);
                            } else {
                                Toast.makeText(CommentActivity.this, "删除失败（可能不是自己的评论）", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> Toast.makeText(CommentActivity.this, "删除失败: " + message, Toast.LENGTH_SHORT).show());
                    }
                });
            });
            return true;
        });

        return item;
    }

    private void updateLikeText(TextView tv, boolean liked, int count) {
        String text = count > 0 ? " " + count : "";
        tv.setText(text);
        int iconRes = liked ? R.drawable.ic_thumb_up : R.drawable.ic_thumb_up_outline;
        android.graphics.drawable.Drawable icon = getResources().getDrawable(iconRes, null);
        int iconSize = px(14);
        icon.setBounds(0, 0, iconSize, iconSize);
        icon.setColorFilter(liked ? COLOR_ACCENT : 0x80FFFFFF,
                android.graphics.PorterDuff.Mode.SRC_IN);
        tv.setCompoundDrawables(icon, null, null, null);
        tv.setCompoundDrawablePadding(px(3));
        tv.setTextColor(liked ? COLOR_ACCENT : 0x80FFFFFF);
    }

    // ──────────────────────────────────────────────────────
    //  Avatar
    // ──────────────────────────────────────────────────────

    private View buildAvatarView(String nickname, long userId) {
        int size = px(26);
        int colorIndex = (int) (Math.abs(userId) % AVATAR_COLORS.length);
        int bgColor = AVATAR_COLORS[colorIndex];
        String initial = "";
        if (nickname != null && !nickname.isEmpty()) {
            int firstCodePoint = nickname.codePointAt(0);
            initial = new String(Character.toChars(firstCodePoint));
        }

        TextView avatar = new TextView(this);
        avatar.setText(initial);
        avatar.setTextColor(Color.WHITE);
        avatar.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(13));
        avatar.setTypeface(avatar.getTypeface(), Typeface.BOLD);
        avatar.setGravity(Gravity.CENTER);
        avatar.setContentDescription(nickname + "的头像");

        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(bgColor);
        circle.setSize(size, size);
        avatar.setBackground(circle);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
        avatar.setLayoutParams(params);

        return avatar;
    }

    // ──────────────────────────────────────────────────────
    //  Comment Posting
    // ──────────────────────────────────────────────────────

    private void submitComment() {
        String text = inputField.getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(this, "评论内容不能为空", Toast.LENGTH_SHORT).show();
            return;
        }

        if (cookie.isEmpty()) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }

        sendButton.setEnabled(false);
        sendButton.setTextColor(COLOR_TEXT_SECONDARY);

        MusicApiHelper.CommentActionCallback callback = new MusicApiHelper.CommentActionCallback() {
            @Override
            public void onResult(boolean success) {
                runOnUiThread(() -> {
                    sendButton.setEnabled(true);
                    sendButton.setTextColor(COLOR_ACCENT);
                    if (success) {
                        inputField.setText("");
                        exitReplyMode();
                        hideKeyboard();
                        Toast.makeText(CommentActivity.this,
                                "评论成功", Toast.LENGTH_SHORT).show();
                        // Delay reload slightly so server has time to process
                        new android.os.Handler().postDelayed(() -> {
                            currentSortType = 3;
                            updateSortTabs();
                            loadComments(true);
                        }, 800);
                    } else {
                        Toast.makeText(CommentActivity.this,
                                "评论失败，请重试", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    sendButton.setEnabled(true);
                    sendButton.setTextColor(COLOR_ACCENT);
                    MusicLog.e(TAG, "发送评论失败: " + message);
                    Toast.makeText(CommentActivity.this,
                            "发送失败: " + message, Toast.LENGTH_SHORT).show();
                });
            }
        };

        if (replyToCommentId > 0) {
            MusicLog.op(TAG, "回复评论", "commentId=" + replyToCommentId + ", content=" + text);
            MusicApiHelper.replyComment(songId, replyToCommentId, text, cookie, callback);
        } else {
            MusicLog.op(TAG, "发送评论", "content=" + text);
            MusicApiHelper.postComment(songId, text, cookie, callback);
        }
    }

    private void enterReplyMode(long commentId, String nickname) {
        replyToCommentId = commentId;
        replyToNickname = nickname;
        inputField.setHint("回复 @" + nickname);
        inputField.requestFocus();

        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(inputField, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void exitReplyMode() {
        replyToCommentId = -1;
        replyToNickname = null;
        inputField.setHint("写评论...");
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && getCurrentFocus() != null) {
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
    }

    // ──────────────────────────────────────────────────────
    //  Floor Comments Navigation
    // ──────────────────────────────────────────────────────

    private void openFloorComments(long parentCommentId) {
        Intent intent = new Intent(this, CommentFloorActivity.class);
        intent.putExtra("song_id", songId);
        intent.putExtra("parent_comment_id", parentCommentId);
        intent.putExtra("cookie", cookie);
        startActivity(intent);
    }

    // ──────────────────────────────────────────────────────
    //  Time Formatting
    // ──────────────────────────────────────────────────────

    private String formatTime(long timestamp) {
        if (timestamp <= 0) return "";

        long now = System.currentTimeMillis();
        long diff = now - timestamp;

        if (diff < 0) return "刚刚";

        long seconds = diff / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (minutes < 1) return "刚刚";
        if (minutes < 60) return minutes + "分钟前";
        if (hours < 24) return hours + "小时前";
        if (days < 7) return days + "天前";

        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    // ──────────────────────────────────────────────────────
    //  Helper Views
    // ──────────────────────────────────────────────────────

    private void addLoadingIndicator() {
        TextView loading = new TextView(this);
        loading.setText("加载中...");
        loading.setTextColor(COLOR_TEXT_SECONDARY);
        loading.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(13));
        loading.setGravity(Gravity.CENTER);
        loading.setPadding(0, px(40), 0, px(40));
        loading.setTag("loading_indicator");
        commentListLayout.addView(loading);
    }

    private void removeLoadingIndicator() {
        for (int i = commentListLayout.getChildCount() - 1; i >= 0; i--) {
            View child = commentListLayout.getChildAt(i);
            if ("loading_indicator".equals(child.getTag())) {
                commentListLayout.removeViewAt(i);
            }
        }
    }

    private void addEmptyHint(String hint) {
        TextView empty = new TextView(this);
        empty.setText(hint);
        empty.setTextColor(COLOR_TEXT_SECONDARY);
        empty.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(13));
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(0, px(40), 0, px(40));
        empty.setTag("empty_hint");
        empty.setOnClickListener(v -> {
            commentListLayout.removeView(empty);
            loadComments(true);
        });
        commentListLayout.addView(empty);
    }

    private View makeDivider() {
        View divider = new View(this);
        divider.setBackgroundColor(COLOR_DIVIDER);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, px(1));
        params.setMargins(px(27), 0, 0, 0);
        divider.setLayoutParams(params);
        return divider;
    }

    /**
     * Show a confirmation dialog adapted for watch (same style as DownloadListActivity).
     */
    private void showConfirmDialog(String title, String message, Runnable onConfirm) {
        android.widget.FrameLayout rootView = findViewById(android.R.id.content);

        android.widget.FrameLayout overlay = new android.widget.FrameLayout(this);
        overlay.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
        overlay.setBackgroundColor(0xCC000000);

        LinearLayout dialog = new LinearLayout(this);
        dialog.setOrientation(LinearLayout.VERTICAL);
        dialog.setBackgroundColor(0xFF1E1E1E);
        dialog.setPadding(px(16), px(12), px(16), px(12));
        android.widget.FrameLayout.LayoutParams dlgParams = new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT);
        dlgParams.gravity = Gravity.CENTER;
        dlgParams.leftMargin = px(16);
        dlgParams.rightMargin = px(16);
        dialog.setLayoutParams(dlgParams);

        // Title
        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextColor(0xFFFFFFFF);
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(18));
        tvTitle.setGravity(Gravity.CENTER);
        tvTitle.setPadding(0, 0, 0, px(6));
        dialog.addView(tvTitle);

        // Message
        TextView tvMessage = new TextView(this);
        tvMessage.setText(message);
        tvMessage.setTextColor(0xB3FFFFFF);
        tvMessage.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(15));
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
        btnCancel.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(16));
        btnCancel.setGravity(Gravity.CENTER);
        btnCancel.setPadding(px(12), px(8), px(12), px(8));
        btnCancel.setBackgroundColor(0xFF2D2D2D);
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
        btnConfirm.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(16));
        btnConfirm.setGravity(Gravity.CENTER);
        btnConfirm.setPadding(px(12), px(8), px(12), px(8));
        btnConfirm.setBackgroundColor(0xFFBB86FC);
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
     * Convert a base value (designed for 320px-wide screen) to actual pixels.
     */
    private int px(int baseValue) {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        return (int) (baseValue * screenWidth / 320f + 0.5f);
    }
}
