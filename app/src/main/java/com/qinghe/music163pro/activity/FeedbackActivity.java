package com.qinghe.music163pro.activity;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.qinghe.music163pro.api.AppApiHelper;

/**
 * Feedback activity — lets the user submit a text suggestion/issue via POST /suggest.
 * Located at: 更多 → 设置 → 功能反馈
 */
public class FeedbackActivity extends AppCompatActivity {

    private EditText etContent;
    private TextView btnSubmit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            android.content.SharedPreferences prefs =
                    getSharedPreferences("music163_settings", MODE_PRIVATE);
            if (prefs.getBoolean("keep_screen_on", false)) {
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        } catch (Exception ignored) {}

        buildUI();
    }

    private void buildUI() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(0xFF212121);
        scrollView.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(px(16), px(20), px(16), px(20));

        // Title
        TextView tvTitle = new TextView(this);
        tvTitle.setText("功能反馈");
        tvTitle.setTextColor(0xFFFFFFFF);
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(18));
        tvTitle.setTypeface(tvTitle.getTypeface(), Typeface.BOLD);
        tvTitle.setGravity(Gravity.CENTER);
        tvTitle.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(tvTitle);

        root.addView(makeSpacer(px(16)));

        // Input box
        etContent = new EditText(this);
        etContent.setHint("请输入问题");
        etContent.setHintTextColor(0xFF888888);
        etContent.setTextColor(0xFFFFFFFF);
        etContent.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(14));
        etContent.setBackgroundColor(0xFF2D2D2D);
        etContent.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        etContent.setImeOptions(EditorInfo.IME_FLAG_NO_ENTER_ACTION);
        etContent.setGravity(Gravity.TOP | Gravity.START);
        etContent.setPadding(px(12), px(10), px(12), px(10));
        etContent.setMinLines(5);
        LinearLayout.LayoutParams etParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        etContent.setLayoutParams(etParams);
        root.addView(etContent);

        root.addView(makeSpacer(px(16)));

        // Submit button
        btnSubmit = new TextView(this);
        btnSubmit.setText("提交");
        btnSubmit.setTextColor(0xFFFFFFFF);
        btnSubmit.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(15));
        btnSubmit.setTypeface(btnSubmit.getTypeface(), Typeface.BOLD);
        btnSubmit.setGravity(Gravity.CENTER);
        btnSubmit.setBackgroundColor(0xFFBB86FC);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnSubmit.setLayoutParams(btnParams);
        btnSubmit.setPadding(0, px(12), 0, px(12));
        btnSubmit.setClickable(true);
        btnSubmit.setFocusable(true);
        btnSubmit.setOnClickListener(v -> submitFeedback());
        root.addView(btnSubmit);

        scrollView.addView(root);
        setContentView(scrollView);
    }

    private void submitFeedback() {
        String content = etContent.getText().toString().trim();
        if (content.isEmpty()) {
            Toast.makeText(this, "请输入问题内容", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSubmit.setClickable(false);
        btnSubmit.setAlpha(0.6f);

        AppApiHelper.postSuggest(content, new AppApiHelper.SuggestCallback() {
            @Override
            public void onSuccess() {
                Toast.makeText(FeedbackActivity.this, "提交成功", Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(FeedbackActivity.this, "提交失败: " + error, Toast.LENGTH_SHORT).show();
                btnSubmit.setClickable(true);
                btnSubmit.setAlpha(1f);
            }
        });
    }

    private android.view.View makeSpacer(int heightPx) {
        android.view.View spacer = new android.view.View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, heightPx));
        return spacer;
    }

    private int px(int baseValue) {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        return (int) (baseValue * screenWidth / 320f + 0.5f);
    }
}
