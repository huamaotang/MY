package com.example.crm.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class FundTrendChartView extends View {
    public static class TrendRow {
        public String date;
        public Double unitNav;
        public Double accumulatedNav;
        public Double returnRate;
    }

    public static class TrendSeries {
        public String key;
        public String label;
        public int color;
        public String suffix;

        public TrendSeries(String key, String label, int color, String suffix) {
            this.key = key;
            this.label = label;
            this.color = color;
            this.suffix = suffix == null ? "" : suffix;
        }
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<TrendRow> rows = new ArrayList<>();
    private final List<TrendSeries> series = new ArrayList<>();
    private String title = "";

    public FundTrendChartView(Context context) {
        super(context);
        init();
    }

    public FundTrendChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public void setData(String title, List<TrendRow> rows, List<TrendSeries> series) {
        this.title = title == null ? "" : title;
        this.rows.clear();
        if (rows != null) {
            this.rows.addAll(rows);
        }
        this.series.clear();
        if (series != null) {
            this.series.addAll(series);
        }
        invalidate();
    }

    private void init() {
        setMinimumHeight(Ui.dp(getContext(), 250));
        setPadding(Ui.dp(getContext(), 12), Ui.dp(getContext(), 12), Ui.dp(getContext(), 12), Ui.dp(getContext(), 12));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredHeight = Ui.dp(getContext(), 270);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = resolveSize(desiredHeight, heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        canvas.drawRect(0, 0, width, height, paint);

        paint.setTextSize(Ui.dp(getContext(), 14));
        paint.setFakeBoldText(true);
        paint.setColor(Ui.TEXT);
        canvas.drawText(title, getPaddingLeft(), Ui.dp(getContext(), 24), paint);
        paint.setFakeBoldText(false);

        drawLegend(canvas, width);

        List<Double> values = values();
        if (rows.size() < 2 || values.size() < 2) {
            paint.setTextSize(Ui.dp(getContext(), 14));
            paint.setColor(Ui.MUTED);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("暂无走势数据", width / 2, height / 2, paint);
            paint.setTextAlign(Paint.Align.LEFT);
            return;
        }

        float left = Ui.dp(getContext(), 54);
        float right = Ui.dp(getContext(), 14);
        float top = Ui.dp(getContext(), 46);
        float bottom = Ui.dp(getContext(), 34);
        float plotWidth = Math.max(width - left - right, 1);
        float plotHeight = Math.max(height - top - bottom, 1);
        double minValue = min(values);
        double maxValue = max(values);
        double valuePadding = Math.max((maxValue - minValue) * 0.08, 0.01);
        double yMin = minValue - valuePadding;
        double yMax = maxValue + valuePadding;

        paint.setStrokeWidth(1);
        paint.setTextSize(Ui.dp(getContext(), 10));
        paint.setColor(Color.rgb(138, 144, 153));
        paint.setTextAlign(Paint.Align.RIGHT);
        double[] yTicks = new double[]{yMax, (yMax + yMin) / 2, yMin};
        for (double tick : yTicks) {
            float y = yFor(tick, yMin, yMax, top, plotHeight);
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(Ui.BORDER);
            canvas.drawLine(left, y, width - right, y, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(138, 144, 153));
            canvas.drawText(formatNumber(tick, series.get(0).suffix), left - Ui.dp(getContext(), 6), y + Ui.dp(getContext(), 4), paint);
        }

        paint.setTextAlign(Paint.Align.LEFT);
        drawXAxisLabel(canvas, 0, left, plotWidth, height);
        paint.setTextAlign(Paint.Align.CENTER);
        drawXAxisLabel(canvas, Math.max((rows.size() - 1) / 2, 0), left, plotWidth, height);
        paint.setTextAlign(Paint.Align.RIGHT);
        drawXAxisLabel(canvas, rows.size() - 1, left, plotWidth, height);
        paint.setTextAlign(Paint.Align.LEFT);

        for (TrendSeries item : series) {
            Path path = new Path();
            boolean started = false;
            for (int i = 0; i < rows.size(); i++) {
                Double value = valueFor(rows.get(i), item.key);
                if (value == null) {
                    continue;
                }
                float x = left + plotWidth * i / Math.max(rows.size() - 1, 1);
                float y = yFor(value, yMin, yMax, top, plotHeight);
                if (started) {
                    path.lineTo(x, y);
                } else {
                    path.moveTo(x, y);
                    started = true;
                }
            }
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Ui.dp(getContext(), 2));
            paint.setColor(item.color);
            canvas.drawPath(path, paint);
        }
    }

    private void drawLegend(Canvas canvas, float width) {
        paint.setTextSize(Ui.dp(getContext(), 11));
        paint.setTextAlign(Paint.Align.RIGHT);
        float x = width - getPaddingRight();
        float y = Ui.dp(getContext(), 23);
        for (int i = series.size() - 1; i >= 0; i--) {
            TrendSeries item = series.get(i);
            float labelWidth = paint.measureText(item.label);
            paint.setColor(Ui.MUTED);
            canvas.drawText(item.label, x, y, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(item.color);
            canvas.drawCircle(x - labelWidth - Ui.dp(getContext(), 8), y - Ui.dp(getContext(), 4), Ui.dp(getContext(), 4), paint);
            x -= labelWidth + Ui.dp(getContext(), 28);
        }
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawXAxisLabel(Canvas canvas, int index, float left, float plotWidth, float height) {
        float x = left + plotWidth * index / Math.max(rows.size() - 1, 1);
        paint.setColor(Color.rgb(138, 144, 153));
        canvas.drawText(formatDate(rows.get(index).date), x, height - Ui.dp(getContext(), 8), paint);
    }

    private List<Double> values() {
        List<Double> values = new ArrayList<>();
        for (TrendRow row : rows) {
            for (TrendSeries item : series) {
                Double value = valueFor(row, item.key);
                if (value != null) {
                    values.add(value);
                }
            }
        }
        return values;
    }

    private Double valueFor(TrendRow row, String key) {
        if ("unitNav".equals(key)) {
            return row.unitNav;
        }
        if ("accumulatedNav".equals(key)) {
            return row.accumulatedNav;
        }
        if ("returnRate".equals(key)) {
            return row.returnRate;
        }
        return null;
    }

    private float yFor(double value, double yMin, double yMax, float top, float plotHeight) {
        return top + plotHeight - (float) ((value - yMin) / Math.max(yMax - yMin, 0.000001)) * plotHeight;
    }

    private double min(List<Double> values) {
        double result = values.get(0);
        for (Double value : values) {
            result = Math.min(result, value);
        }
        return result;
    }

    private double max(List<Double> values) {
        double result = values.get(0);
        for (Double value : values) {
            result = Math.max(result, value);
        }
        return result;
    }

    private String formatNumber(double value, String suffix) {
        return String.format(Math.abs(value) >= 10 ? "%.2f%s" : "%.4f%s", value, suffix);
    }

    private String formatDate(String value) {
        if (value == null || value.length() != 8) {
            return Ui.value(value);
        }
        return value.substring(0, 4) + "-" + value.substring(4, 6) + "-" + value.substring(6);
    }
}
