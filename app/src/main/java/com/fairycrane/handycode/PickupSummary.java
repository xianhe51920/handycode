package com.fairycrane.handycode;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/** Collects the visible pickup codes from the official parcel views. */
final class PickupSummary extends FrameLayout {
    enum Source {
        TAOBAO("淘宝 / 菜鸟"), PDD("拼多多"), JD_MALL("京东商城"), JD_EXPRESS("京东快递"), SF("顺丰");

        final String label;

        Source(String label) { this.label = label; }
    }

    private final EnumMap<Source, List<String>> codes = new EnumMap<>(Source.class);
    private final ScrollView scroll;
    private final LinearLayout cards;
    private final Runnable refreshSources, openTaobao, openPdd, openJd;
    private PickupLookupPage lookup;
    private boolean active, dark;

    private final Runnable sample = new Runnable() {
        @Override public void run() {
            if (!active || lookup != null) return;
            refreshSources.run();
            postDelayed(this, 2000);
        }
    };

    PickupSummary(Context context, Runnable refreshSources, Runnable openTaobao, Runnable openPdd, Runnable openJd) {
        super(context);
        this.refreshSources = refreshSources;
        this.openTaobao = openTaobao;
        this.openPdd = openPdd;
        this.openJd = openJd;

        scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        cards = column();
        cards.setPadding(dp(12), dp(12), dp(12), dp(24));
        scroll.addView(cards);
        addView(scroll, new LayoutParams(-1, -1));
        render();
    }

    void setOrders(Source source, JSONArray orders) {
        List<String> next = new ArrayList<>();
        if (orders != null) {
            for (int i = 0; i < orders.length() && next.size() < 80; i++) {
                JSONObject row = orders.optJSONObject(i);
                if (row == null) continue;
                String code = row.optString("code").trim();
                if (code.matches("[A-Za-z0-9][A-Za-z0-9-]{1,23}")) next.add(code);
            }
        }
        if (!next.equals(codes.put(source, next))) render();
    }

    void setDark(boolean dark) {
        if (this.dark == dark) return;
        this.dark = dark;
        setBackgroundColor(AppTheme.bg(dark));
        if (lookup != null) lookup.setDark(dark);
        render();
    }

    void setActive(boolean active) {
        if (this.active == active) return;
        this.active = active;
        if (lookup != null) lookup.setActive(active);
        removeCallbacks(sample);
        if (active && lookup == null) post(sample);
    }

    private void openJdLookup() {
        new AlertDialog.Builder(getContext()).setTitle("京东查件").setItems(new String[] {
            "京东商城订单", "京东快递收件", "按运单号查京东物流"
        }, (dialog, choice) -> {
            if (choice == 0) openJd.run();
            else openLookup(choice == 1 ? PickupLookupPage.Entry.JD_EXPRESS : PickupLookupPage.Entry.JD_TRACKING);
        }).show();
    }

    private void openSfLookup() {
        new AlertDialog.Builder(getContext()).setTitle("顺丰查件").setItems(new String[] {
            "查看我收的包裹", "按运单号查顺丰物流"
        }, (dialog, choice) -> {
            openLookup(choice == 0 ? PickupLookupPage.Entry.SF_ORDERS : PickupLookupPage.Entry.SF_TRACKING);
        }).show();
    }

    private void openLookup(PickupLookupPage.Entry entry) {
        if (lookup != null) return;
        removeCallbacks(sample);
        lookup = new PickupLookupPage(getContext(), entry, this::closeLookup, orders -> {
            if (entry.source != null) setOrders(entry.source, orders);
        });
        lookup.setDark(dark);
        scroll.setVisibility(GONE);
        addView(lookup, new LayoutParams(-1, -1));
        lookup.setActive(active);
    }

    private void closeLookup() {
        if (lookup == null) return;
        lookup.destroyPage();
        removeView(lookup);
        lookup = null;
        scroll.setVisibility(VISIBLE);
        if (active) post(sample);
    }

    boolean goBack() {
        if (lookup == null) return false;
        if (!lookup.goBack()) closeLookup();
        return true;
    }

    void destroyPage() {
        setActive(false);
        closeLookup();
        codes.clear();
    }

    private void render() {
        cards.removeAllViews();
        LinearLayout actions = column();
        actions.addView(label("登录与查件", 16, true));
        actions.addView(actionRow("淘宝 / 菜鸟", openTaobao, "拼多多", openPdd));
        actions.addView(actionRow("京东", this::openJdLookup, "顺丰", this::openSfLookup));
        actions.addView(button("刷新汇总", refreshSources));
        addCard(actions);

        boolean hasCodes = false;
        for (Source source : Source.values()) {
            List<String> found = codes.get(source);
            if (found == null || found.isEmpty()) continue;
            hasCodes = true;
            LinearLayout group = column();
            group.addView(label(source.label + "（" + found.size() + "）", 16, true));
            for (String code : found) {
                TextView value = label("取件码：" + code, 20, true);
                value.setPadding(0, dp(8), 0, 0);
                value.setTextColor(AppTheme.accentText(dark));
                value.setTextIsSelectable(true);
                group.addView(value);
            }
            addCard(group);
        }
        if (!hasCodes) {
            LinearLayout empty = column();
            empty.addView(label("暂无待取件码", 16, true));
            TextView hint = label("从上方打开平台查件，已读取的待取件码会汇总到这里。", 13, false);
            hint.setPadding(0, dp(6), 0, 0);
            hint.setTextColor(AppTheme.muted(dark));
            empty.addView(hint);
            addCard(empty);
        }
    }

    private LinearLayout column() {
        LinearLayout view = new LinearLayout(getContext());
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }

    private LinearLayout actionRow(String first, Runnable firstAction, String second, Runnable secondAction) {
        LinearLayout row = new LinearLayout(getContext());
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, -2, 1f);
        left.setMarginEnd(dp(8));
        row.addView(button(first, firstAction), left);
        row.addView(button(second, secondAction), new LinearLayout.LayoutParams(0, -2, 1f));
        return row;
    }

    private Button button(String text, Runnable action) {
        Button button = new Button(getContext());
        button.setText(text);
        button.setTextColor(AppTheme.accentText(dark));
        button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private TextView label(String value, int size, boolean bold) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size);
        view.setTextColor(AppTheme.ink(dark));
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private void addCard(LinearLayout card) {
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable background = new GradientDrawable();
        background.setColor(AppTheme.surface(dark));
        background.setCornerRadius(dp(16));
        background.setStroke(dp(1), AppTheme.border(dark));
        card.setBackground(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(12);
        cards.addView(card, params);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
