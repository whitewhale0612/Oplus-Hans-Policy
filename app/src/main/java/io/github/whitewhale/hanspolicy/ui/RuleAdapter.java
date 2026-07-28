package io.github.whitewhale.hanspolicy.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import io.github.whitewhale.hanspolicy.model.PolicyRule;

import java.util.ArrayList;
import java.util.List;

final class RuleAdapter extends BaseAdapter {
    private final Context context;
    private List<PolicyRule> rules = new ArrayList<>();

    RuleAdapter(Context context) {
        this.context = context;
    }

    void setRules(List<PolicyRule> rules) {
        this.rules = new ArrayList<>(rules);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return rules.size();
    }

    @Override
    public PolicyRule getItem(int position) {
        return rules.get(position);
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).key().hashCode();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Holder holder;
        if (convertView == null) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(16), 0, dp(12), 0);
            row.setBackgroundColor(Color.WHITE);
            row.setMinimumHeight(dp(72));

            View marker = new View(context);
            row.addView(marker, new LinearLayout.LayoutParams(dp(4), dp(40)));

            LinearLayout labels = new LinearLayout(context);
            labels.setOrientation(LinearLayout.VERTICAL);
            labels.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            labelParams.setMargins(dp(14), 0, dp(8), 0);
            row.addView(labels, labelParams);

            TextView packageName = new TextView(context);
            packageName.setTextColor(Color.rgb(31, 36, 41));
            packageName.setTextSize(15);
            packageName.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            packageName.setSingleLine(true);
            labels.addView(packageName);

            TextView summary = new TextView(context);
            summary.setTextColor(Color.rgb(100, 108, 116));
            summary.setTextSize(13);
            summary.setSingleLine(true);
            summary.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            summaryParams.topMargin = dp(4);
            labels.addView(summary, summaryParams);

            ImageView arrow = new ImageView(context);
            arrow.setImageResource(android.R.drawable.ic_media_next);
            arrow.setAlpha(0.45f);
            row.addView(arrow, new LinearLayout.LayoutParams(dp(24), dp(24)));

            holder = new Holder(marker, packageName, summary);
            row.setTag(holder);
            convertView = row;
        } else {
            holder = (Holder) convertView.getTag();
        }

        PolicyRule rule = getItem(position);
        holder.packageName.setText(rule.packageName);
        holder.summary.setText(summary(rule));
        int color;
        if (!rule.enabled || !rule.hasIntervention()) {
            color = Color.rgb(154, 162, 169);
        } else if (rule.fullExempt) {
            color = Color.rgb(8, 127, 91);
        } else {
            color = Color.rgb(204, 92, 36);
        }
        holder.marker.setBackgroundColor(color);
        convertView.setAlpha(rule.enabled ? 1f : 0.58f);
        return convertView;
    }

    private String summary(PolicyRule rule) {
        if (!rule.enabled) {
            return "规则已停用";
        }
        if (rule.fullExempt) {
            return "完全豁免 Hans 冻结与限制";
        }
        List<String> parts = new ArrayList<>();
        if (rule.customTiming) {
            parts.add("R→M " + seconds(rule.rToMMs) + "秒 / M→F "
                    + seconds(rule.mToFMs) + "秒");
        }
        if (rule.blockedFreezeSources != 0) {
            parts.add("阻止 " + Integer.bitCount(rule.blockedFreezeSources) + " 类冻结");
        }
        if (rule.blocksPacketWake()) {
            parts.add("禁止网络包唤醒");
        } else if (rule.throttlesPacketWake()) {
            parts.add("网络唤醒间隔 " + seconds(rule.packetWakeCooldownMs) + "秒");
        }
        if (!rule.blocksPacketWake() && rule.hasCustomPacketRefreeze()) {
            parts.add("网络唤醒保持 " + seconds(rule.packetRefreezeMs) + "秒");
        }
        int resources = Integer.bitCount(rule.bypassProxyFlags)
                + (rule.keepNetwork ? 1 : 0);
        if (resources != 0) {
            parts.add("保留 " + resources + " 项资源");
        }
        return parts.isEmpty() ? "跟随系统策略" : String.join(" · ", parts);
    }

    private static long seconds(long milliseconds) {
        return milliseconds / 1_000L;
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static final class Holder {
        final View marker;
        final TextView packageName;
        final TextView summary;

        Holder(View marker, TextView packageName, TextView summary) {
            this.marker = marker;
            this.packageName = packageName;
            this.summary = summary;
        }
    }
}
