package com.qinghe.music163pro.activity;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.qinghe.music163pro.R;
import com.qinghe.music163pro.api.MusicApiHelper;
import com.qinghe.music163pro.player.MusicPlayerManager;

import org.json.JSONObject;

/**
 * Profile activity - shows user account info and VIP details.
 * Designed for watch screen (320x360 dpi).
 */
public class ProfileActivity extends AppCompatActivity {

    private LinearLayout contentLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences("music163_settings", MODE_PRIVATE);
        if (prefs.getBoolean("keep_screen_on", false)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(0xFF212121);
        scrollView.setFillViewport(true);

        contentLayout = new LinearLayout(this);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setPadding(px(8), px(8), px(8), px(8));
        scrollView.addView(contentLayout);

        setContentView(scrollView);

        // Title
        TextView title = new TextView(this);
        title.setText("👤  个人中心");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(15));
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, px(8));
        contentLayout.addView(title);

        // Loading
        TextView tvLoading = new TextView(this);
        tvLoading.setText("加载中...");
        tvLoading.setTextColor(0xFF757575);
        tvLoading.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(13));
        tvLoading.setGravity(Gravity.CENTER);
        contentLayout.addView(tvLoading);

        // Fetch account info
        String cookie = MusicPlayerManager.getInstance().getCookie();
        MusicApiHelper.getUserAccount(cookie, new MusicApiHelper.AccountCallback() {
            @Override
            public void onResult(JSONObject json) {
                contentLayout.removeView(tvLoading);
                displayAccountInfo(json);
                // Also fetch VIP info separately for reliable expiry time
                fetchVipInfo(cookie);
            }

            @Override
            public void onError(String message) {
                tvLoading.setText("加载失败: " + message);
            }
        });
    }

    /**
     * Fetch VIP info from dedicated endpoint for reliable expiry display.
     */
    private void fetchVipInfo(String cookie) {
        MusicApiHelper.getVipInfo(cookie, new MusicApiHelper.VipInfoCallback() {
            @Override
            public void onResult(JSONObject json) {
                displayVipInfo(json);
            }

            @Override
            public void onError(String message) {
                // VIP info is supplementary, don't show error
            }
        });
    }

    private void displayVipInfo(JSONObject json) {
        try {
            JSONObject data = json.optJSONObject("data");
            if (data == null) return;

            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
            long now = System.currentTimeMillis();
            boolean foundAny = false;

            // Try all known VIP sub-objects; field may be "expireTime" or "expTime"
            String[] keys   = {"associator", "redVipLevel", "musicPackage"};
            String[] labels = {"黑胶VIP",     "红钻VIP",      "音乐包"};

            for (int i = 0; i < keys.length; i++) {
                JSONObject obj = data.optJSONObject(keys[i]);
                if (obj == null) continue;
                // Support both field name variants used by different API versions
                long expTime = obj.optLong("expireTime", 0);
                if (expTime <= 0) expTime = obj.optLong("expTime", 0);
                if (expTime <= 0) continue;

                if (!foundAny) {
                    addSectionTitle("VIP详情");
                    foundAny = true;
                }
                boolean expired = expTime < now;
                addInfoRow(labels[i] + "到期", sdf.format(new java.util.Date(expTime))
                        + (expired ? " (已过期)" : " ✓"));
                if (expTime > now) {
                    long days = (expTime - now) / (1000 * 60 * 60 * 24);
                    addInfoRow(labels[i] + "剩余", days + " 天");
                }
            }
        } catch (Exception e) {
            // Non-critical, ignore
        }
    }

    private void displayAccountInfo(JSONObject json) {
        try {
            JSONObject profile = json.optJSONObject("profile");
            JSONObject account = json.optJSONObject("account");

            if (profile == null && account == null) {
                addInfoRow("状态", "未登录或获取信息失败");
                return;
            }

            // Profile info
            if (profile != null) {
                addSectionTitle("基本信息");
                addInfoRow("昵称", profile.optString("nickname", "未知"));

                long userId = profile.optLong("userId", 0);
                if (userId > 0) {
                    addInfoRow("用户ID", String.valueOf(userId));
                }

                int gender = profile.optInt("gender", 0);
                String genderStr = gender == 1 ? "男" : (gender == 2 ? "女" : "未设置");
                addInfoRow("性别", genderStr);

                String signature = profile.optString("signature", "");
                if (!signature.isEmpty()) {
                    addInfoRow("签名", signature);
                }

                int level = profile.optInt("level", -1);
                if (level >= 0) {
                    addInfoRow("等级", "Lv." + level);
                }

                long birthday = profile.optLong("birthday", 0);
                if (birthday > 0) {
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
                    addInfoRow("生日", sdf.format(new java.util.Date(birthday)));
                }

                String province = profile.optString("province", "");
                String city = profile.optString("city", "");
                if (!province.isEmpty() || !city.isEmpty()) {
                    addInfoRow("地区", province + " " + city);
                }

                int follows = profile.optInt("follows", 0);
                int followeds = profile.optInt("followeds", 0);
                addInfoRow("关注", String.valueOf(follows));
                addInfoRow("粉丝", String.valueOf(followeds));

                int playlistCount = profile.optInt("playlistCount", 0);
                if (playlistCount > 0) {
                    addInfoRow("歌单数", String.valueOf(playlistCount));
                }

                long createTime = profile.optLong("createTime", 0);
                if (createTime > 0) {
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
                    addInfoRow("注册时间", sdf.format(new java.util.Date(createTime)));
                }

                int authority = profile.optInt("authority", 0);
                int authStatus = profile.optInt("authStatus", 0);
                if (authStatus > 0) {
                    addInfoRow("认证状态", "已认证 ✓");
                }

                int vipType = profile.optInt("vipType", 0);
                if (vipType > 0) {
                    String vipStr;
                    switch (vipType) {
                        case 10:
                        case 11: vipStr = "黑胶VIP"; break;
                        default: vipStr = "VIP (类型" + vipType + ")"; break;
                    }
                    addInfoRow("VIP类型", vipStr);
                }

                // VIP expiry from profile.vipRights — show regardless of vipType field
                java.text.SimpleDateFormat sdfDate = new java.text.SimpleDateFormat("yyyy-MM-dd");
                long profileVipExp = 0;
                JSONObject vipRights = profile.optJSONObject("vipRights");
                if (vipRights != null) {
                    JSONObject assoc = vipRights.optJSONObject("associator");
                    if (assoc != null) {
                        profileVipExp = assoc.optLong("expireTime", 0);
                        if (profileVipExp <= 0) profileVipExp = assoc.optLong("expTime", 0);
                        if (profileVipExp > 0) {
                            long now = System.currentTimeMillis();
                            boolean expired = profileVipExp < now;
                            addInfoRow("VIP到期", sdfDate.format(new java.util.Date(profileVipExp))
                                    + (expired ? " (已过期)" : " ✓"));
                            if (!expired) {
                                long days = (profileVipExp - now) / (1000 * 60 * 60 * 24);
                                addInfoRow("VIP剩余", days + " 天");
                            }
                        }
                    }
                    JSONObject musicPkg = vipRights.optJSONObject("musicPackage");
                    if (musicPkg != null) {
                        long expTime = musicPkg.optLong("expireTime", 0);
                        if (expTime <= 0) expTime = musicPkg.optLong("expTime", 0);
                        if (expTime > 0) {
                            long now = System.currentTimeMillis();
                            boolean expired = expTime < now;
                            addInfoRow("音乐包到期", sdfDate.format(new java.util.Date(expTime))
                                    + (expired ? " (已过期)" : " ✓"));
                        }
                    }
                }
            }

            // Account info
            if (account != null) {
                addSectionTitle("账号信息");

                int status = account.optInt("status", -1);
                addInfoRow("账号状态", status == 0 ? "正常" : "异常(" + status + ")");

                int vipType = account.optInt("vipType", 0);
                String vipStr;
                switch (vipType) {
                    case 0: vipStr = "普通用户"; break;
                    case 10:
                    case 11: vipStr = "黑胶VIP 🎵"; break;
                    default: vipStr = "VIP (类型" + vipType + ")"; break;
                }
                addInfoRow("会员类型", vipStr);

                // Show VIP expiry from account — try both common field names
                long vipExpireTime = account.optLong("vipExpiresTime", 0);
                if (vipExpireTime <= 0) {
                    vipExpireTime = account.optLong("vipExpireTime", 0);
                }
                if (vipExpireTime > 0) {
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
                    boolean isExpired = vipExpireTime < System.currentTimeMillis();
                    addInfoRow("会员到期", sdf.format(new java.util.Date(vipExpireTime))
                            + (isExpired ? " (已过期)" : " ✓"));
                }

                long createTime = account.optLong("createTime", 0);
                if (createTime > 0) {
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm");
                    addInfoRow("创建时间", sdf.format(new java.util.Date(createTime)));
                }

                boolean anonimousUser = account.optBoolean("anonimousUser", false);
                if (anonimousUser) {
                    addInfoRow("匿名用户", "是");
                }

                boolean paidFee = account.optBoolean("paidFee", false);
                addInfoRow("付费状态", paidFee ? "已付费 ✓" : "未付费");
            }

        } catch (Exception e) {
            addInfoRow("错误", "解析信息失败: " + e.getMessage());
        }
    }

    private void addSectionTitle(String title) {
        TextView tv = new TextView(this);
        tv.setText("── " + title + " ──");
        tv.setTextColor(0xFFD32F2F);
        tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(13));
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, px(8), 0, px(4));
        contentLayout.addView(tv);
    }

    private void addInfoRow(String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(px(4), px(3), px(4), px(3));
        row.setBackgroundColor(0xFF333333);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.bottomMargin = px(2);
        row.setLayoutParams(rowParams);

        TextView tvLabel = new TextView(this);
        tvLabel.setText(label + "：");
        tvLabel.setTextColor(0xFF999999);
        tvLabel.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(12));
        tvLabel.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        row.addView(tvLabel);

        TextView tvValue = new TextView(this);
        tvValue.setText(value);
        tvValue.setTextColor(0xFFFFFFFF);
        tvValue.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(12));
        tvValue.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        row.addView(tvValue);

        contentLayout.addView(row);
    }

    private int px(int baseValue) {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        return (int) (baseValue * screenWidth / 320f + 0.5f);
    }
}
