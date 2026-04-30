package com.qinghe.music163pro.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.qinghe.music163pro.R;
import com.qinghe.music163pro.api.ImoowApiHelper;

import java.util.ArrayList;
import java.util.List;

public class HelpActivity extends BaseWatchActivity {

    private final List<ImoowApiHelper.QaItem> helpItems = new ArrayList<>();
    private ArrayAdapter<ImoowApiHelper.QaItem> adapter;
    private TextView tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);

        tvStatus = findViewById(R.id.tv_help_status);
        ListView lvHelp = findViewById(R.id.lv_help);

        adapter = new ArrayAdapter<ImoowApiHelper.QaItem>(this, R.layout.item_help_question,
                R.id.tv_help_question, helpItems) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                ImoowApiHelper.QaItem item = getItem(position);
                if (item != null) {
                    TextView tvQuestion = view.findViewById(R.id.tv_help_question);
                    TextView tvHint = view.findViewById(R.id.tv_help_hint);
                    tvQuestion.setText(item.getQuestion());
                    tvHint.setText("点击查看答案");
                }
                return view;
            }
        };
        lvHelp.setAdapter(adapter);
        lvHelp.setOnItemClickListener((parent, view, position, id) -> {
            ImoowApiHelper.QaItem item = helpItems.get(position);
            Intent intent = new Intent(this, HelpDetailActivity.class);
            intent.putExtra(HelpDetailActivity.EXTRA_QUESTION, item.getQuestion());
            intent.putExtra(HelpDetailActivity.EXTRA_ANSWER, item.getAnswer());
            startActivity(intent);
        });

        loadHelpItems();
    }

    private void loadHelpItems() {
        tvStatus.setVisibility(View.VISIBLE);
        tvStatus.setText("正在加载帮助内容...");
        ImoowApiHelper.fetchQaList(new ImoowApiHelper.QaCallback() {
            @Override
            public void onResult(List<ImoowApiHelper.QaItem> items) {
                helpItems.clear();
                helpItems.addAll(items);
                adapter.notifyDataSetChanged();
                tvStatus.setVisibility(helpItems.isEmpty() ? View.VISIBLE : View.GONE);
                if (helpItems.isEmpty()) {
                    tvStatus.setText("暂无帮助内容");
                }
            }

            @Override
            public void onError(String error) {
                tvStatus.setVisibility(View.VISIBLE);
                tvStatus.setText("加载帮助失败");
                Toast.makeText(HelpActivity.this, "加载失败: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
