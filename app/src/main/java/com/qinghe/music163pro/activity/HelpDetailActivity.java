package com.qinghe.music163pro.activity;

import android.os.Bundle;
import android.widget.TextView;

import com.qinghe.music163pro.R;

public class HelpDetailActivity extends BaseWatchActivity {

    public static final String EXTRA_QUESTION = "help_question";
    public static final String EXTRA_ANSWER = "help_answer";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help_detail);

        TextView tvQuestion = findViewById(R.id.tv_help_detail_question);
        TextView tvAnswer = findViewById(R.id.tv_help_detail_answer);

        String question = getIntent().getStringExtra(EXTRA_QUESTION);
        String answer = getIntent().getStringExtra(EXTRA_ANSWER);

        tvQuestion.setText(question == null || question.trim().isEmpty() ? "未提供问题" : question);
        tvAnswer.setText(answer == null || answer.trim().isEmpty() ? "暂无答案" : answer);
    }
}
