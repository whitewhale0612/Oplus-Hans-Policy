package io.github.whitewhale.hanspolicy.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.google.android.material.color.MaterialColors;

import io.github.whitewhale.hanspolicy.R;
import io.github.whitewhale.hanspolicy.model.PolicyRule;

import java.util.ArrayList;
import java.util.List;

final class RuleAdapter extends BaseAdapter {
    private final Context context;
    private final LayoutInflater inflater;
    private List<PolicyRule> rules = new ArrayList<>();

    RuleAdapter(Context context) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);
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
            convertView = inflater.inflate(R.layout.item_rule, parent, false);
            holder = new Holder(
                    convertView.findViewById(R.id.rule_marker),
                    convertView.findViewById(R.id.package_name),
                    convertView.findViewById(R.id.rule_summary));
            convertView.setTag(holder);
        } else {
            holder = (Holder) convertView.getTag();
        }

        PolicyRule rule = getItem(position);
        holder.packageName.setText(rule.packageName);
        holder.summary.setText(summary(rule));
        int colorAttr;
        if (!rule.enabled || !rule.hasIntervention()) {
            colorAttr = com.google.android.material.R.attr.colorOutline;
        } else if (rule.fullExempt) {
            colorAttr = com.google.android.material.R.attr.colorPrimary;
        } else {
            colorAttr = com.google.android.material.R.attr.colorTertiary;
        }
        int markerColor = MaterialColors.getColor(holder.marker, colorAttr);
        holder.marker.setBackgroundTintList(ColorStateList.valueOf(markerColor));
        convertView.setAlpha(rule.enabled ? 1f : 0.62f);
        return convertView;
    }

    private String summary(PolicyRule rule) {
        if (!rule.enabled) {
            return context.getString(R.string.rule_disabled);
        }
        if (rule.fullExempt) {
            return context.getString(R.string.rule_full_exempt);
        }
        List<String> parts = new ArrayList<>();
        if (rule.customTiming) {
            parts.add(context.getString(R.string.rule_custom_timing,
                    seconds(rule.rToMMs), seconds(rule.mToFMs)));
        }
        if (rule.blockedFreezeSources != 0) {
            parts.add(context.getString(R.string.rule_block_freeze,
                    Integer.bitCount(rule.blockedFreezeSources)));
        }
        if (rule.blocksPacketWake()) {
            parts.add(context.getString(R.string.rule_block_packet));
        } else if (rule.throttlesPacketWake()) {
            parts.add(context.getString(R.string.rule_throttle_packet,
                    seconds(rule.packetWakeCooldownMs)));
        }
        if (!rule.blocksPacketWake() && rule.hasCustomPacketRefreeze()) {
            parts.add(context.getString(R.string.rule_packet_refreeze,
                    seconds(rule.packetRefreezeMs)));
        }
        if (rule.blocksAlarmWake()) {
            parts.add(context.getString(R.string.rule_block_alarm));
        } else if (rule.throttlesAlarmWake()) {
            parts.add(context.getString(R.string.rule_throttle_alarm,
                    seconds(rule.alarmWakeCooldownMs)));
        }
        if (!rule.blocksAlarmWake() && rule.hasCustomAlarmRefreeze()) {
            parts.add(context.getString(R.string.rule_alarm_refreeze,
                    seconds(rule.alarmRefreezeMs)));
        }
        if (rule.blockedWakeSources != 0) {
            parts.add(context.getString(R.string.rule_block_wake_sources,
                    Integer.bitCount(rule.blockedWakeSources)));
        }
        int resources = Integer.bitCount(rule.bypassProxyFlags)
                + (rule.keepNetwork ? 1 : 0);
        if (resources != 0) {
            parts.add(context.getString(R.string.rule_keep_resources, resources));
        }
        return parts.isEmpty()
                ? context.getString(R.string.rule_follow_system)
                : String.join(" · ", parts);
    }

    private static long seconds(long milliseconds) {
        return milliseconds / 1_000L;
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
