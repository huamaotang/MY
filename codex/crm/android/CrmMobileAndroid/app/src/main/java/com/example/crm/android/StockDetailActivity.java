package com.example.crm.android;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StockDetailActivity extends Activity {
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private LinearLayout list;
    private ProgressBar progress;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        StockQuote stock=(StockQuote)getIntent().getSerializableExtra("stock");
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this,16),Ui.dp(this,20),Ui.dp(this,16),Ui.dp(this,12)); setContentView(root);
        root.addView(Ui.text(this,Ui.value(stock.stockName)+" "+Ui.value(stock.stockCode),24,Ui.TEXT,Typeface.BOLD));
        root.addView(Ui.text(this,"最后更新时间 "+Ui.value(stock.updatedAt),12,Ui.MUTED,Typeface.NORMAL));
        root.addView(Ui.text(this,"备注 "+Ui.value(stock.comment),12,Ui.MUTED,Typeface.NORMAL));
        progress=new ProgressBar(this); root.addView(progress);
        ScrollView scroll=new ScrollView(this); list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list); root.addView(scroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
        SessionStore session=new SessionStore(this);
        executor.execute(() -> {
            try {
                PageResult<StockQuote> result=session.apiClient().stockHistory(stock.stockCode,1,100);
                runOnUiThread(() -> { progress.setVisibility(android.view.View.GONE); for(StockQuote item:result.records) addRow(item); });
            } catch(Exception ex) { Ui.toast(this,ex.getMessage()); }
        });
    }
    private void addRow(StockQuote item) {
        LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0,Ui.dp(this,10),0,Ui.dp(this,10));
        int color=Ui.MUTED; try { double n=Double.parseDouble(item.changeRate); color=n>0?Ui.RED:n<0?Ui.GREEN:Ui.MUTED; } catch(Exception ignored) {}
        row.addView(Ui.text(this,Ui.value(item.tradeDate)+"    "+Ui.value(item.changeRate)+"%",15,color,Typeface.BOLD));
        row.addView(Ui.text(this,"最后更新时间 "+Ui.value(item.updatedAt),12,Ui.MUTED,Typeface.NORMAL));
        row.addView(Ui.text(this,"备注 "+Ui.value(item.comment),12,Ui.MUTED,Typeface.NORMAL));
        row.addView(Ui.text(this,"开 "+Ui.value(item.openPrice)+"  高 "+Ui.value(item.highPrice)+"  低 "+Ui.value(item.lowPrice)+"  收 "+Ui.value(item.latestPrice),13,Ui.TEXT,Typeface.NORMAL));
        row.addView(Ui.text(this,"成交量 "+Ui.value(item.volume)+"  成交额 "+Ui.value(item.amount)+"  换手 "+Ui.value(item.turnoverRate)+"%",12,Ui.MUTED,Typeface.NORMAL));
        list.addView(row);
    }
    @Override protected void onDestroy() { executor.shutdownNow(); super.onDestroy(); }
}
