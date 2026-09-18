package com.qinghe.music163pro.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.qinghe.music163pro.api.MusicApiHelper;
import com.qinghe.music163pro.player.MusicPlayerManager;
import com.qinghe.music163pro.util.NetworkImageLoader;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Profile activity - shows and edits user account info.
 * Editable fields (avatar, nickname, gender, signature) are grouped together
 * with larger rows; other info (age, follows, fans, register time, VIP) is
 * shown read-only.
 */
public class ProfileActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "music163_settings";
    private static final int REQ_PICK_AVATAR = 4001;

    private LinearLayout contentLayout;
    private ImageView avatarView;
    private String cookie = "";
    private String avatarUrl = "";
    private String nickname = "";
    private int gender = 0;
    private String signature = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (prefs.getBoolean("keep_screen_on", false)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        cookie = MusicPlayerManager.getInstance().getCookie();
        if (cookie == null) cookie = "";

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(0xFF121212);
        scrollView.setFillViewport(true);
        contentLayout = new LinearLayout(this);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setPadding(px(10), px(10), px(10), px(10));
        scrollView.addView(contentLayout);
        setContentView(scrollView);

        TextView title = new TextView(this);
        title.setText("个人中心");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(15));
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, px(8));
        contentLayout.addView(title);

        buildAvatarHeader();

        TextView loading = new TextView(this);
        loading.setText("加载中...");
        loading.setTextColor(0xFF757575);
        loading.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(13));
        loading.setGravity(Gravity.CENTER);
        loading.setTag("loading");
        contentLayout.addView(loading);

        fetchAccount();
    }

    private void buildAvatarHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, px(8));

        avatarView = new ImageView(this);
        int avatarSize = px(64);
        avatarView.setLayoutParams(new LinearLayout.LayoutParams(avatarSize, avatarSize));
        avatarView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable avatarBg = new GradientDrawable();
        avatarBg.setShape(GradientDrawable.OVAL);
        avatarBg.setColor(0xFF333333);
        avatarView.setBackground(avatarBg);
        avatarView.setClipToOutline(true);
        avatarView.setClickable(true);
        avatarView.setFocusable(true);
        avatarView.setOnClickListener(v -> changeAvatar());
        header.addView(avatarView);

        TextView hint = new TextView(this);
        hint.setText("点头像可更换\n登录后显示信息");
        hint.setTextColor(0x80FFFFFF);
        hint.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(12));
        hint.setPadding(px(12), 0, 0, 0);
        header.addView(hint);

        contentLayout.addView(header);
    }

    private void fetchAccount() {
        if (cookie.isEmpty()) {
            removeLoading();
            addInfoRow("状态", "未登录");
            return;
        }
        MusicApiHelper.getUserAccount(cookie, new MusicApiHelper.AccountCallback() {
            @Override
            public void onResult(JSONObject json) {
                runOnUiThread(() -> {
                    removeLoading();
                    displayAccountInfo(json);
                    fetchVipInfo();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    removeLoading();
                    addInfoRow("状态", "加载失败");
                });
            }
        });
    }

    private void fetchVipInfo() {
        MusicApiHelper.getVipInfo(cookie, new MusicApiHelper.VipInfoCallback() {
            @Override
            public void onResult(JSONObject json) {
                runOnUiThread(() -> displayVipInfo(json));
            }

            @Override
            public void onError(String message) {
            }
        });
    }

    private void removeLoading() {
        for (int i = contentLayout.getChildCount() - 1; i >= 0; i--) {
            if ("loading".equals(contentLayout.getChildAt(i).getTag())) {
                contentLayout.removeViewAt(i);
            }
        }
    }

    private void displayAccountInfo(JSONObject json) {
        JSONObject profile = json.optJSONObject("profile");
        if (profile == null) {
            addInfoRow("状态", "获取信息失败");
            return;
        }

        nickname = profile.optString("nickname", "");
        gender = profile.optInt("gender", 0);
        signature = profile.optString("signature", "");
        avatarUrl = profile.optString("avatarUrl", "");
        long userId = profile.optLong("userId", 0);

        if (userId > 0) {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            prefs.edit().putLong("current_user_id", userId).apply();
        }

        if (!avatarUrl.isEmpty()) {
            NetworkImageLoader.load(avatarView, avatarUrl);
        }

        // Editable section
        addSectionTitle("可修改");
        addEditableRow("昵称", nickname.isEmpty() ? "未设置" : nickname, v -> editNickname());
        addEditableRow("性别", genderText(gender), v -> editGender());
        addEditableRow("签名", signature.isEmpty() ? "未设置" : signature, v -> editSignature());

        // Read-only info
        addSectionTitle("基本信息");
        addInfoRow("用户ID", String.valueOf(userId));

        long birthday = profile.optLong("birthday", 0);
        if (birthday > 0) {
            long ageMillis = System.currentTimeMillis() - birthday;
            int age = (int) (ageMillis / (1000L * 60 * 60 * 24 * 365));
            if (age > 0) addInfoRow("年龄", age + " 岁");
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
            addInfoRow("生日", sdf.format(new java.util.Date(birthday)));
        }

        int follows = profile.optInt("follows", 0);
        int followeds = profile.optInt("followeds", 0);
        addInfoRow("关注", String.valueOf(follows));
        addInfoRow("粉丝", String.valueOf(followeds));

        long createTime = profile.optLong("createTime", 0);
        if (createTime > 0) {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
            addInfoRow("注册时间", sdf.format(new java.util.Date(createTime)));
        }

        addSectionTitle("VIP");
        // VIP expiry is shown via dedicated endpoint below.
    }

    private void displayVipInfo(JSONObject json) {
        try {
            JSONObject data = json.optJSONObject("data");
            if (data == null) return;
            boolean found = false;
            String[] keys = {"associator", "redVipLevel", "musicPackage"};
            String[] labels = {"黑胶VIP", "红钻VIP", "音乐包"};
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
            long now = System.currentTimeMillis();
            for (int i = 0; i < keys.length; i++) {
                JSONObject obj = data.optJSONObject(keys[i]);
                if (obj == null) continue;
                long exp = obj.optLong("expireTime", obj.optLong("expTime", 0));
                if (exp <= 0) continue;
                boolean expired = exp < now;
                addInfoRow(labels[i] + "到期",
                        sdf.format(new java.util.Date(exp)) + (expired ? " (已过期)" : " ✓"));
                found = true;
            }
            if (!found) {
                addInfoRow("VIP状态", "未开通");
            }
        } catch (Exception ignored) {
        }
    }

    private String genderText(int g) {
        return g == 1 ? "男" : (g == 2 ? "女" : "未设置");
    }

    private void editNickname() {
        showInputDialog("修改昵称", nickname, InputType.TYPE_CLASS_TEXT, text -> {
            nickname = text;
            doUpdateProfile();
        });
    }

    private void editSignature() {
        showInputDialog("修改签名", signature, InputType.TYPE_CLASS_TEXT, text -> {
            signature = text;
            doUpdateProfile();
        });
    }

    private void editGender() {
        FrameLayout root = (FrameLayout) getWindow().getDecorView().findViewById(android.R.id.content);
        FrameLayout overlay = new FrameLayout(this);
        overlay.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        overlay.setBackgroundColor(0x99000000);
        overlay.setClickable(true);
        overlay.setFocusable(true);

        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setBackgroundColor(0xFF1E1E1E);
        sheet.setPadding(0, px(6), 0, px(6));
        FrameLayout.LayoutParams sheetParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        sheetParams.gravity = Gravity.BOTTOM;
        sheet.setLayoutParams(sheetParams);
        sheet.addView(makeActionRow("男", 0xFFFFFFFF, v -> {
            root.removeView(overlay);
            sendGender(1);
        }));
        sheet.addView(makeActionRow("女", 0xFFFFFFFF, v -> {
            root.removeView(overlay);
            sendGender(2);
        }));
        sheet.addView(makeActionRow("保密", 0xFFFFFFFF, v -> {
            root.removeView(overlay);
            sendGender(0);
        }));
        sheet.addView(makeActionRow("取消", 0xFF999999, v -> root.removeView(overlay)));
        overlay.addView(sheet);
        overlay.setOnClickListener(v -> {});
        sheet.setOnClickListener(v -> {});
        root.addView(overlay);
    }

    private void sendGender(int g) {
        gender = g;
        doUpdateProfile();
    }

    private void doUpdateProfile() {
        JSONObject params = new JSONObject();
        try {
            params.put("nickname", nickname);
            params.put("gender", gender);
            params.put("signature", signature);
        } catch (Exception ignored) {
        }
        Toast.makeText(this, "正在保存...", Toast.LENGTH_SHORT).show();
        MusicApiHelper.updateUserProfile(params, cookie, new MusicApiHelper.CommentActionCallback() {
            @Override
            public void onResult(boolean success) {
                runOnUiThread(() -> {
                    Toast.makeText(ProfileActivity.this, "已保存", Toast.LENGTH_SHORT).show();
                    reloadAccount();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> Toast.makeText(ProfileActivity.this,
                        "保存失败: " + message, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void reloadAccount() {
        contentLayout.removeAllViews();
        TextView title = new TextView(this);
        title.setText("个人中心");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(15));
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, px(8));
        contentLayout.addView(title);
        buildAvatarHeader();
        TextView loading = new TextView(this);
        loading.setText("加载中...");
        loading.setTextColor(0xFF757575);
        loading.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(13));
        loading.setGravity(Gravity.CENTER);
        loading.setTag("loading");
        contentLayout.addView(loading);
        fetchAccount();
    }

    private void changeAvatar() {
        if (cookie.isEmpty()) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, REQ_PICK_AVATAR);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_AVATAR && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri == null) return;
            File avatarFile = new File(getCacheDir(), "avatar_tmp.jpg");
            try {
                InputStream is = getContentResolver().openInputStream(uri);
                FileOutputStream fos = new FileOutputStream(avatarFile);
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) != -1) fos.write(buf, 0, n);
                fos.close();
                is.close();
            } catch (Exception e) {
                Toast.makeText(this, "读取图片失败", Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(this, "正在上传头像...", Toast.LENGTH_SHORT).show();
            MusicApiHelper.uploadAvatar(avatarFile, cookie, new MusicApiHelper.CommentActionCallback() {
                @Override
                public void onResult(boolean success) {
                    runOnUiThread(() -> {
                        Toast.makeText(ProfileActivity.this, "头像已更新", Toast.LENGTH_SHORT).show();
                        reloadAccount();
                    });
                }

                @Override
                public void onError(String message) {
                    runOnUiThread(() -> Toast.makeText(ProfileActivity.this,
                            "头像更新失败: " + message, Toast.LENGTH_SHORT).show());
                }
            });
        }
    }

    private View makeActionRow(String label, int color, View.OnClickListener listener) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(color);
        tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(15));
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(px(12), px(14), px(12), px(14));
        tv.setClickable(true);
        tv.setFocusable(true);
        tv.setOnClickListener(listener);
        return tv;
    }

    private void showInputDialog(String title, String initial, int inputType,
                                 final TextCallback callback) {
        FrameLayout root = (FrameLayout) getWindow().getDecorView().findViewById(android.R.id.content);
        FrameLayout overlay = new FrameLayout(this);
        overlay.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        overlay.setBackgroundColor(0x99000000);
        overlay.setClickable(true);
        overlay.setFocusable(true);

        LinearLayout dialog = new LinearLayout(this);
        dialog.setOrientation(LinearLayout.VERTICAL);
        dialog.setBackgroundColor(0xFF1E1E1E);
        dialog.setPadding(px(16), px(12), px(16), px(12));
        FrameLayout.LayoutParams dialogParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        dialogParams.gravity = Gravity.CENTER;
        dialogParams.leftMargin = px(16);
        dialogParams.rightMargin = px(16);
        dialog.setLayoutParams(dialogParams);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(0xFFFFFFFF);
        titleView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(16));
        titleView.setGravity(Gravity.CENTER);
        titleView.setPadding(0, 0, 0, px(8));
        dialog.addView(titleView);

        EditText input = new EditText(this);
        input.setText(initial);
        input.setInputType(inputType);
        input.setTextColor(0xFFFFFFFF);
        input.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(14));
        input.setBackgroundColor(0xFF333333);
        input.setPadding(px(8), px(8), px(8), px(8));
        input.setSingleLine(title.startsWith("签名"));
        dialog.addView(input);

        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.CENTER);
        buttonRow.setPadding(0, px(8), 0, 0);

        TextView cancel = new TextView(this);
        cancel.setText("取消");
        cancel.setTextColor(0xFFFFFFFF);
        cancel.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(15));
        cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(px(12), px(8), px(12), px(8));
        cancel.setBackgroundColor(0xFF2D2D2D);
        cancel.setOnClickListener(v -> root.removeView(overlay));
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        cancelParams.rightMargin = px(4);
        cancel.setLayoutParams(cancelParams);
        buttonRow.addView(cancel);

        TextView confirm = new TextView(this);
        confirm.setText("确定");
        confirm.setTextColor(0xFFFFFFFF);
        confirm.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(15));
        confirm.setGravity(Gravity.CENTER);
        confirm.setPadding(px(12), px(8), px(12), px(8));
        confirm.setBackgroundColor(0xFFBB86FC);
        confirm.setOnClickListener(v -> {
            root.removeView(overlay);
            callback.onText(input.getText().toString().trim());
        });
        LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        confirmParams.leftMargin = px(4);
        confirm.setLayoutParams(confirmParams);
        buttonRow.addView(confirm);

        dialog.addView(buttonRow);
        overlay.addView(dialog);
        overlay.setOnClickListener(v -> {});
        dialog.setOnClickListener(v -> {});
        root.addView(overlay);
        input.requestFocus();
    }

    private interface TextCallback {
        void onText(String text);
    }

    private void addSectionTitle(String title) {
        TextView tv = new TextView(this);
        tv.setText("── " + title + " ──");
        tv.setTextColor(0xFFBB86FC);
        tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(13));
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, px(8), 0, px(4));
        contentLayout.addView(tv);
    }

    private void addEditableRow(String label, String value, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(px(12), px(14), px(12), px(14));
        row.setClickable(true);
        row.setFocusable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF2D2D2D);
        bg.setCornerRadius(px(6));
        row.setBackground(bg);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = px(4);
        row.setLayoutParams(params);

        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextColor(0xFF999999);
        tvLabel.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(13));
        row.addView(tvLabel);

        TextView tvValue = new TextView(this);
        tvValue.setText(value);
        tvValue.setTextColor(0xFFFFFFFF);
        tvValue.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(14));
        tvValue.setGravity(Gravity.END);
        tvValue.setPadding(px(8), 0, 0, 0);
        tvValue.setSingleLine(true);
        tvValue.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tvValue.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(tvValue);

        TextView edit = new TextView(this);
        edit.setText("›");
        edit.setTextColor(0xFFBB86FC);
        edit.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(16));
        edit.setPadding(px(4), 0, 0, 0);
        row.addView(edit);

        row.setOnClickListener(listener);
        contentLayout.addView(row);
    }

    private void addInfoRow(String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(px(12), px(14), px(12), px(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF1E1E1E);
        bg.setCornerRadius(px(6));
        row.setBackground(bg);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = px(3);
        row.setLayoutParams(params);

        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextColor(0xFF999999);
        tvLabel.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(13));
        row.addView(tvLabel);

        TextView tvValue = new TextView(this);
        tvValue.setText(value);
        tvValue.setTextColor(0xFFFFFFFF);
        tvValue.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(14));
        tvValue.setGravity(Gravity.END);
        tvValue.setPadding(px(8), 0, 0, 0);
        tvValue.setSingleLine(true);
        tvValue.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tvValue.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(tvValue);

        contentLayout.addView(row);
    }

    private int px(int baseValue) {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        return (int) (baseValue * screenWidth / 320f + 0.5f);
    }
}
