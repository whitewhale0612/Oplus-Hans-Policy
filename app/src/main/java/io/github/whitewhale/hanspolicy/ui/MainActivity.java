package io.github.whitewhale.hanspolicy.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Build;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import io.github.whitewhale.hanspolicy.data.PolicyRepository;
import io.github.whitewhale.hanspolicy.model.PolicyCodec;
import io.github.whitewhale.hanspolicy.model.PolicyRule;
import io.github.whitewhale.hanspolicy.model.PolicySnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SuppressLint("SetTextI18n")
public final class MainActivity extends Activity {
    private PolicyRepository repository;
    private RuleAdapter ruleAdapter;
    private Switch masterSwitch;
    private TextView statusTitle;
    private TextView statusDetail;
    private boolean binding;
    private volatile List<String> installedPackages = Collections.emptyList();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repository = new PolicyRepository(this);
        setContentView(createContentView());
        loadInstalledPackages();
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (repository != null) {
            render();
        }
    }

    private View createContentView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(244, 246, 248));

        View statusInset = new View(this);
        statusInset.setBackgroundColor(Color.rgb(32, 37, 43));
        root.addView(statusInset, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int top = Build.VERSION.SDK_INT >= 30
                    ? insets.getInsets(WindowInsets.Type.statusBars()).top
                    : insets.getSystemWindowInsetTop();
            ViewGroup.LayoutParams params = statusInset.getLayoutParams();
            if (params.height != top) {
                params.height = top;
                statusInset.setLayoutParams(params);
            }
            return insets;
        });

        LinearLayout appBar = new LinearLayout(this);
        appBar.setOrientation(LinearLayout.VERTICAL);
        appBar.setGravity(Gravity.CENTER_VERTICAL);
        appBar.setPadding(dp(20), 0, dp(20), 0);
        appBar.setBackgroundColor(Color.rgb(32, 37, 43));
        root.addView(appBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(68)));

        TextView title = text("Hans Policy", 21, Color.WHITE, true);
        appBar.addView(title);
        TextView subtitle = text("Oplus system_server 冻结策略", 12,
                Color.rgb(190, 197, 203), false);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = dp(3);
        appBar.addView(subtitle, subtitleParams);

        LinearLayout statusBand = new LinearLayout(this);
        statusBand.setOrientation(LinearLayout.VERTICAL);
        statusBand.setPadding(dp(20), dp(14), dp(20), dp(14));
        statusBand.setBackgroundColor(Color.rgb(232, 238, 241));
        root.addView(statusBand);
        statusTitle = text("等待 Hook 状态", 14, Color.rgb(48, 56, 62), true);
        statusBand.addView(statusTitle);
        statusDetail = text("", 12, Color.rgb(89, 98, 106), false);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        detailParams.topMargin = dp(4);
        statusBand.addView(statusDetail, detailParams);

        LinearLayout switchRow = new LinearLayout(this);
        switchRow.setGravity(Gravity.CENTER_VERTICAL);
        switchRow.setPadding(dp(20), dp(10), dp(14), dp(10));
        switchRow.setBackgroundColor(Color.WHITE);
        root.addView(switchRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(64)));

        LinearLayout switchLabels = new LinearLayout(this);
        switchLabels.setOrientation(LinearLayout.VERTICAL);
        switchRow.addView(switchLabels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        switchLabels.addView(text("应用冻结策略覆盖", 15,
                Color.rgb(31, 36, 41), true));
        switchLabels.addView(text("关闭时所有规则均不介入", 12,
                Color.rgb(104, 112, 120), false));

        masterSwitch = new Switch(this);
        masterSwitch.setContentDescription("总开关");
        switchRow.addView(masterSwitch);
        masterSwitch.setOnCheckedChangeListener((button, enabled) -> {
            if (!binding) {
                repository.setMasterEnabled(enabled);
                render();
            }
        });

        LinearLayout listHeader = new LinearLayout(this);
        listHeader.setGravity(Gravity.CENTER_VERTICAL);
        listHeader.setPadding(dp(20), dp(8), dp(10), dp(4));
        root.addView(listHeader, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        listHeader.addView(text("应用规则", 14, Color.rgb(67, 75, 82), true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ImageButton add = new ImageButton(this);
        add.setImageResource(android.R.drawable.ic_menu_add);
        add.setContentDescription("添加规则");
        add.setBackgroundColor(Color.TRANSPARENT);
        add.setColorFilter(Color.rgb(8, 127, 91));
        add.setOnClickListener(view -> showRuleDialog(null));
        listHeader.addView(add, new LinearLayout.LayoutParams(dp(44), dp(44)));

        FrameLayout listFrame = new FrameLayout(this);
        root.addView(listFrame, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        ListView list = new ListView(this);
        list.setDivider(new ColorDrawable(Color.rgb(228, 232, 235)));
        list.setDividerHeight(1);
        list.setBackgroundColor(Color.WHITE);
        ruleAdapter = new RuleAdapter(this);
        list.setAdapter(ruleAdapter);
        list.setOnItemClickListener((parent, view, position, id) ->
                showRuleDialog(ruleAdapter.getItem(position)));
        listFrame.addView(list, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView empty = text("暂无应用规则", 14, Color.rgb(128, 137, 145), false);
        empty.setGravity(Gravity.CENTER);
        listFrame.addView(empty, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        list.setEmptyView(empty);
        return root;
    }

    private void render() {
        PolicySnapshot snapshot = repository.load();
        binding = true;
        masterSwitch.setChecked(snapshot.enabled);
        binding = false;
        ruleAdapter.setRules(snapshot.getRules());

        PolicyRepository.RuntimeStatus status = repository.loadStatus();
        String currentBootId = PolicyRepository.currentBootId();
        boolean currentBoot = status.lastReportMs != 0L
                && (currentBootId.isEmpty() || currentBootId.equals(status.bootId));
        if (!currentBoot) {
            statusTitle.setText("模块未连接 system_server");
            statusTitle.setTextColor(Color.rgb(181, 62, 43));
            statusDetail.setText(status.lastReportMs == 0L
                    ? "本次启动未收到上报 · 检查 System Framework 作用域与 Vector 模块路径"
                    : "当前仅有上次启动的状态 · 需要重新加载模块");
        } else if (status.active && status.lastError.isEmpty()) {
            statusTitle.setText("Hook 已连接 · " + status.hookCount + " 个目标");
            statusTitle.setTextColor(Color.rgb(8, 112, 82));
            statusDetail.setText("system revision " + status.policyRevision
                    + " · local revision " + snapshot.revision
                    + (status.runtimeSource.isEmpty() ? "" : " · " + status.runtimeSource));
        } else {
            statusTitle.setText("Hook 状态异常 · " + status.hookCount + " 个目标");
            statusTitle.setTextColor(Color.rgb(181, 62, 43));
            statusDetail.setText(status.lastError.isEmpty()
                    ? "核心 Hook 未完整安装" : status.lastError);
        }
    }

    private void showRuleDialog(PolicyRule existing) {
        ScrollView scroll = new ScrollView(this);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(22), dp(4), dp(22), dp(8));
        scroll.addView(form);

        AutoCompleteTextView packageInput = new AutoCompleteTextView(this);
        packageInput.setSingleLine(true);
        packageInput.setThreshold(1);
        packageInput.setHint("com.example.app");
        List<String> suggestions = installedPackages;
        if (suggestions.isEmpty()) {
            suggestions = queryInstalledPackages();
            installedPackages = suggestions;
        }
        packageInput.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, suggestions));
        addField(form, "包名", packageInput);

        CheckBox enabledInput = addOption(form, "启用此规则");
        CheckBox fullExemptInput = addOption(form, "完全豁免 Hans");

        LinearLayout advanced = new LinearLayout(this);
        advanced.setOrientation(LinearLayout.VERTICAL);
        form.addView(advanced);

        addSectionLabel(advanced, "状态时序");
        CheckBox customTimingInput = addOption(advanced, "自定义 R / M / F 时序");

        LinearLayout timing = new LinearLayout(this);
        timing.setOrientation(LinearLayout.VERTICAL);
        advanced.addView(timing);
        EditText rToMInput = secondsInput();
        addField(timing, "R → M 延时（秒）", rToMInput);
        EditText mToFInput = secondsInput();
        addField(timing, "M → F 延时（秒）", mToFInput);

        addSectionLabel(advanced, "网络包唤醒");
        Spinner packetWakeModeInput = new Spinner(this);
        ArrayAdapter<String> packetWakeModeAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"跟随系统", "限制唤醒频率", "完全阻止唤醒"});
        packetWakeModeAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        packetWakeModeInput.setAdapter(packetWakeModeAdapter);
        addField(advanced, "处理方式", packetWakeModeInput);

        LinearLayout packetThrottle = new LinearLayout(this);
        packetThrottle.setOrientation(LinearLayout.VERTICAL);
        advanced.addView(packetThrottle);
        EditText packetWakeCooldownInput = secondsInput();
        addField(packetThrottle, "最短唤醒间隔（秒）", packetWakeCooldownInput);

        CheckBox customPacketRefreezeInput = addOption(
                advanced, "自定义网络唤醒后保持时间");
        LinearLayout packetRefreezeTiming = new LinearLayout(this);
        packetRefreezeTiming.setOrientation(LinearLayout.VERTICAL);
        advanced.addView(packetRefreezeTiming);
        EditText packetRefreezeInput = secondsInput();
        addField(packetRefreezeTiming, "再次冻结延时（秒）", packetRefreezeInput);

        addSectionLabel(advanced, "阻止冻结来源");
        CheckBox blockNormalInput = addOption(advanced, "普通状态机冻结");
        CheckBox blockFastInput = addOption(advanced, "Fast Freezer");
        CheckBox blockSuperInput = addOption(advanced, "Super Freeze");
        CheckBox blockPreloadInput = addOption(advanced, "预加载冻结");

        addSectionLabel(advanced, "冻结时保留资源");
        CheckBox keepNetworkInput = addOption(advanced, "网络与现有连接");
        CheckBox keepServiceInput = addOption(advanced, "Service 调度");
        CheckBox keepJobInput = addOption(advanced, "Job 调度");
        CheckBox keepBroadcastInput = addOption(advanced, "广播投递");
        CheckBox keepAlarmInput = addOption(advanced, "闹钟与定时器");
        CheckBox keepBinderInput = addOption(advanced, "异步 Binder");
        CheckBox keepSensorInput = addOption(advanced, "传感器");
        CheckBox keepGpsInput = addOption(advanced, "定位 / GPS");
        CheckBox keepWakeLockInput = addOption(advanced, "WakeLock");
        CheckBox keepAudioInput = addOption(advanced, "音频");
        CheckBox keepBtScanInput = addOption(advanced, "蓝牙扫描");

        if (existing == null) {
            enabledInput.setChecked(true);
            fullExemptInput.setChecked(true);
            rToMInput.setText("60");
            mToFInput.setText("60");
            packetWakeModeInput.setSelection(PolicyRule.PACKET_WAKE_ALLOW);
            packetWakeCooldownInput.setText("60");
            packetRefreezeInput.setText("5");
        } else {
            packageInput.setText(existing.packageName);
            enabledInput.setChecked(existing.enabled);
            fullExemptInput.setChecked(existing.fullExempt);
            customTimingInput.setChecked(existing.customTiming);
            rToMInput.setText(String.valueOf(existing.rToMMs / 1_000L));
            mToFInput.setText(String.valueOf(existing.mToFMs / 1_000L));
            packetWakeModeInput.setSelection(existing.packetWakeMode);
            packetWakeCooldownInput.setText(
                    String.valueOf(existing.packetWakeCooldownMs / 1_000L));
            customPacketRefreezeInput.setChecked(existing.hasCustomPacketRefreeze());
            packetRefreezeInput.setText(String.valueOf(
                    (existing.hasCustomPacketRefreeze()
                            ? existing.packetRefreezeMs : existing.mToFMs) / 1_000L));
            blockNormalInput.setChecked(existing.blocksFreeze(PolicyRule.FREEZE_NORMAL));
            blockFastInput.setChecked(existing.blocksFreeze(PolicyRule.FREEZE_FAST));
            blockSuperInput.setChecked(existing.blocksFreeze(PolicyRule.FREEZE_SUPER));
            blockPreloadInput.setChecked(existing.blocksFreeze(PolicyRule.FREEZE_PRELOAD));
            keepNetworkInput.setChecked(existing.keepNetwork);
            keepServiceInput.setChecked(existing.bypassesProxy(PolicyRule.PROXY_SERVICE));
            keepJobInput.setChecked(existing.bypassesProxy(PolicyRule.PROXY_JOB));
            keepBroadcastInput.setChecked(existing.bypassesProxy(PolicyRule.PROXY_BROADCAST));
            keepAlarmInput.setChecked(existing.bypassesProxy(PolicyRule.PROXY_ALARM));
            keepBinderInput.setChecked(existing.bypassesProxy(PolicyRule.PROXY_BINDER));
            keepSensorInput.setChecked(existing.bypassesProxy(PolicyRule.PROXY_SENSOR));
            keepGpsInput.setChecked(existing.bypassesProxy(PolicyRule.PROXY_GPS));
            keepWakeLockInput.setChecked(existing.bypassesProxy(PolicyRule.PROXY_WAKELOCK));
            keepAudioInput.setChecked(existing.bypassesProxy(PolicyRule.PROXY_AUDIO));
            keepBtScanInput.setChecked(existing.bypassesProxy(PolicyRule.PROXY_BT_SCAN));
        }
        advanced.setVisibility(fullExemptInput.isChecked() ? View.GONE : View.VISIBLE);
        timing.setVisibility(customTimingInput.isChecked() ? View.VISIBLE : View.GONE);
        fullExemptInput.setOnCheckedChangeListener((button, checked) ->
                advanced.setVisibility(checked ? View.GONE : View.VISIBLE));
        customTimingInput.setOnCheckedChangeListener((button, checked) ->
                timing.setVisibility(checked ? View.VISIBLE : View.GONE));
        packetWakeModeInput.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view,
                                       int position, long id) {
                updatePacketWakeControls(position, packetThrottle,
                        customPacketRefreezeInput, packetRefreezeTiming);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                updatePacketWakeControls(PolicyRule.PACKET_WAKE_ALLOW, packetThrottle,
                        customPacketRefreezeInput, packetRefreezeTiming);
            }
        });
        customPacketRefreezeInput.setOnCheckedChangeListener((button, checked) ->
                updatePacketWakeControls(packetWakeModeInput.getSelectedItemPosition(),
                        packetThrottle, customPacketRefreezeInput, packetRefreezeTiming));
        updatePacketWakeControls(packetWakeModeInput.getSelectedItemPosition(), packetThrottle,
                customPacketRefreezeInput, packetRefreezeTiming);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "添加应用规则" : "编辑应用规则")
                .setView(scroll)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null);
        if (existing != null) {
            builder.setNeutralButton("删除", null);
        }
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                try {
                    String packageName = packageInput.getText().toString().trim();
                    long rToM = parseSeconds(rToMInput);
                    long mToF = parseSeconds(mToFInput);
                    int packetWakeMode = packetWakeModeInput.getSelectedItemPosition();
                    long packetWakeCooldown = parseSeconds(packetWakeCooldownInput);
                    long packetRefreeze = packetWakeMode != PolicyRule.PACKET_WAKE_BLOCK
                            && customPacketRefreezeInput.isChecked()
                            ? parseSeconds(packetRefreezeInput) : 0L;
                    int blockedSources = checkedFlag(blockNormalInput, PolicyRule.FREEZE_NORMAL)
                            | checkedFlag(blockFastInput, PolicyRule.FREEZE_FAST)
                            | checkedFlag(blockSuperInput, PolicyRule.FREEZE_SUPER)
                            | checkedFlag(blockPreloadInput, PolicyRule.FREEZE_PRELOAD);
                    int bypassFlags = checkedFlag(keepServiceInput, PolicyRule.PROXY_SERVICE)
                            | checkedFlag(keepJobInput, PolicyRule.PROXY_JOB)
                            | checkedFlag(keepBroadcastInput, PolicyRule.PROXY_BROADCAST)
                            | checkedFlag(keepAlarmInput, PolicyRule.PROXY_ALARM)
                            | checkedFlag(keepBinderInput, PolicyRule.PROXY_BINDER)
                            | checkedFlag(keepSensorInput, PolicyRule.PROXY_SENSOR)
                            | checkedFlag(keepGpsInput, PolicyRule.PROXY_GPS)
                            | checkedFlag(keepWakeLockInput, PolicyRule.PROXY_WAKELOCK)
                            | checkedFlag(keepAudioInput, PolicyRule.PROXY_AUDIO)
                            | checkedFlag(keepBtScanInput, PolicyRule.PROXY_BT_SCAN);
                    PolicyCodec.validate(packageName, rToM, mToF,
                            blockedSources, bypassFlags, packetWakeMode,
                            packetWakeCooldown, packetRefreeze);
                    PolicyRule rule = new PolicyRule(packageName,
                            enabledInput.isChecked(), fullExemptInput.isChecked(),
                            customTimingInput.isChecked(), rToM, mToF,
                            blockedSources, bypassFlags, keepNetworkInput.isChecked(),
                            packetWakeMode, packetWakeCooldown, packetRefreeze);
                    repository.upsert(rule, existing == null ? null : existing.key());
                    dialog.dismiss();
                    render();
                } catch (NumberFormatException | ArithmeticException exception) {
                    Toast.makeText(this, "延时必须是整数", Toast.LENGTH_SHORT).show();
                } catch (IllegalArgumentException exception) {
                    Toast.makeText(this, exception.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
            if (existing != null) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(
                        Color.rgb(181, 62, 43));
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view ->
                        confirmDelete(existing, dialog));
            }
        });
        dialog.show();
    }

    private void confirmDelete(PolicyRule rule, AlertDialog editor) {
        new AlertDialog.Builder(this)
                .setTitle("删除规则")
                .setMessage(rule.packageName)
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    repository.delete(rule.key());
                    editor.dismiss();
                    render();
                })
                .show();
    }

    private void loadInstalledPackages() {
        new Thread(() -> {
            installedPackages = queryInstalledPackages();
        }, "HansPolicyPackages").start();
    }

    private List<String> queryInstalledPackages() {
        List<String> packages = new ArrayList<>();
        for (ApplicationInfo info : getPackageManager().getInstalledApplications(0)) {
            packages.add(info.packageName);
        }
        Collections.sort(packages);
        return packages;
    }

    private void addField(LinearLayout parent, String label, View input) {
        TextView labelView = text(label, 12, Color.rgb(88, 96, 104), true);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = dp(13);
        parent.addView(labelView, labelParams);
        parent.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void addSectionLabel(LinearLayout parent, String label) {
        TextView view = text(label, 13, Color.rgb(55, 63, 70), true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(18);
        parent.addView(view, params);
    }

    private CheckBox addOption(LinearLayout parent, String label) {
        CheckBox input = new CheckBox(this);
        input.setText(label);
        input.setTextSize(14);
        parent.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return input;
    }

    private void updatePacketWakeControls(int mode, LinearLayout throttle,
                                          CheckBox customRefreeze,
                                          LinearLayout refreezeTiming) {
        boolean blocked = mode == PolicyRule.PACKET_WAKE_BLOCK;
        throttle.setVisibility(mode == PolicyRule.PACKET_WAKE_THROTTLE
                ? View.VISIBLE : View.GONE);
        customRefreeze.setVisibility(blocked ? View.GONE : View.VISIBLE);
        refreezeTiming.setVisibility(!blocked && customRefreeze.isChecked()
                ? View.VISIBLE : View.GONE);
    }

    private static int checkedFlag(CheckBox input, int flag) {
        return input.isChecked() ? flag : 0;
    }

    private EditText secondsInput() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        return input;
    }

    private long parseSeconds(EditText input) {
        return Math.multiplyExact(Long.parseLong(input.getText().toString().trim()), 1_000L);
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(sp);
        text.setTextColor(color);
        if (bold) {
            text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        return text;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
