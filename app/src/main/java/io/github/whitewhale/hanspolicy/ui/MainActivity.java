package io.github.whitewhale.hanspolicy.ui;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.pm.ApplicationInfo;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import io.github.whitewhale.hanspolicy.R;
import io.github.whitewhale.hanspolicy.data.PolicyRepository;
import io.github.whitewhale.hanspolicy.model.PolicyCodec;
import io.github.whitewhale.hanspolicy.model.PolicyRule;
import io.github.whitewhale.hanspolicy.model.PolicySnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SuppressLint("SetTextI18n")
public final class MainActivity extends AppCompatActivity {
    private PolicyRepository repository;
    private RuleAdapter ruleAdapter;
    private MaterialSwitch masterSwitch;
    private View statusBand;
    private View statusMarker;
    private TextView statusTitle;
    private TextView statusDetail;
    private TextView ruleCount;
    private boolean binding;
    private volatile List<String> installedPackages = Collections.emptyList();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);
        applySystemBarInsets();

        repository = new PolicyRepository(this);
        bindMainViews();
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

    private void bindMainViews() {
        masterSwitch = findViewById(R.id.master_switch);
        statusBand = findViewById(R.id.status_band);
        statusMarker = findViewById(R.id.status_marker);
        statusTitle = findViewById(R.id.status_title);
        statusDetail = findViewById(R.id.status_detail);
        ruleCount = findViewById(R.id.rule_count);

        View.OnClickListener addRule = view -> showRuleDialog(null);
        findViewById(R.id.add_rule).setOnClickListener(addRule);
        findViewById(R.id.empty_add_rule).setOnClickListener(addRule);

        ListView list = findViewById(R.id.rule_list);
        ruleAdapter = new RuleAdapter(this);
        list.setAdapter(ruleAdapter);
        list.setEmptyView(findViewById(R.id.empty_rules));
        list.setOnItemClickListener((parent, view, position, id) ->
                showRuleDialog(ruleAdapter.getItem(position)));

        masterSwitch.setOnCheckedChangeListener((button, enabled) -> {
            if (!binding) {
                repository.setMasterEnabled(enabled);
                render();
            }
        });
    }

    private void applySystemBarInsets() {
        View root = findViewById(R.id.root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    private void render() {
        PolicySnapshot snapshot = repository.load();
        binding = true;
        masterSwitch.setChecked(snapshot.enabled);
        binding = false;

        List<PolicyRule> rules = snapshot.getRules();
        ruleAdapter.setRules(rules);
        ruleCount.setText(getString(R.string.rule_count, rules.size()));

        PolicyRepository.RuntimeStatus status = repository.loadStatus();
        String currentBootId = PolicyRepository.currentBootId();
        boolean currentBoot = status.lastReportMs != 0L
                && (currentBootId.isEmpty() || currentBootId.equals(status.bootId));
        if (!currentBoot) {
            showRuntimeStatus(false, getString(R.string.status_not_connected),
                    getString(status.lastReportMs == 0L
                            ? R.string.status_no_report : R.string.status_previous_boot));
        } else if (status.active && status.lastError.isEmpty()) {
            String source = status.runtimeSource.isEmpty()
                    ? "" : " · " + status.runtimeSource;
            showRuntimeStatus(true,
                    getString(R.string.status_connected, status.hookCount),
                    getString(R.string.status_revision, status.policyRevision,
                            snapshot.revision, source));
        } else {
            showRuntimeStatus(false,
                    getString(R.string.status_abnormal, status.hookCount),
                    status.lastError.isEmpty()
                            ? getString(R.string.status_incomplete) : status.lastError);
        }
    }

    private void showRuntimeStatus(boolean connected, String title, String detail) {
        int containerAttr = connected
                ? com.google.android.material.R.attr.colorPrimaryContainer
                : com.google.android.material.R.attr.colorErrorContainer;
        int contentAttr = connected
                ? com.google.android.material.R.attr.colorOnPrimaryContainer
                : com.google.android.material.R.attr.colorOnErrorContainer;
        int markerAttr = connected
                ? com.google.android.material.R.attr.colorPrimary
                : com.google.android.material.R.attr.colorError;
        int container = MaterialColors.getColor(statusBand, containerAttr);
        int content = MaterialColors.getColor(statusBand, contentAttr);
        int marker = MaterialColors.getColor(statusBand, markerAttr);
        statusBand.setBackgroundColor(container);
        statusMarker.setBackgroundTintList(ColorStateList.valueOf(marker));
        statusTitle.setTextColor(content);
        statusDetail.setTextColor(content);
        statusTitle.setText(title);
        statusDetail.setText(detail);
    }

    private void showRuleDialog(PolicyRule existing) {
        View form = LayoutInflater.from(this).inflate(R.layout.dialog_rule, null, false);
        MaterialAutoCompleteTextView packageInput = form.findViewById(R.id.package_input);
        MaterialSwitch enabledInput = form.findViewById(R.id.enabled_input);
        MaterialSwitch fullExemptInput = form.findViewById(R.id.full_exempt_input);
        View advanced = form.findViewById(R.id.advanced_options);
        MaterialSwitch customTimingInput = form.findViewById(R.id.custom_timing_input);
        View timing = form.findViewById(R.id.timing_options);
        EditText rToMInput = form.findViewById(R.id.r_to_m_input);
        EditText mToFInput = form.findViewById(R.id.m_to_f_input);
        MaterialAutoCompleteTextView packetWakeModeInput =
                form.findViewById(R.id.packet_wake_mode_input);
        View packetThrottle = form.findViewById(R.id.packet_throttle_options);
        EditText packetWakeCooldownInput = form.findViewById(R.id.packet_wake_cooldown_input);
        MaterialSwitch customPacketRefreezeInput =
                form.findViewById(R.id.custom_packet_refreeze_input);
        View packetRefreezeTiming = form.findViewById(R.id.packet_refreeze_options);
        EditText packetRefreezeInput = form.findViewById(R.id.packet_refreeze_input);
        MaterialCheckBox blockNormalInput = form.findViewById(R.id.block_normal_input);
        MaterialCheckBox blockFastInput = form.findViewById(R.id.block_fast_input);
        MaterialCheckBox blockSuperInput = form.findViewById(R.id.block_super_input);
        MaterialCheckBox blockPreloadInput = form.findViewById(R.id.block_preload_input);
        MaterialCheckBox keepNetworkInput = form.findViewById(R.id.keep_network_input);
        MaterialCheckBox keepServiceInput = form.findViewById(R.id.keep_service_input);
        MaterialCheckBox keepJobInput = form.findViewById(R.id.keep_job_input);
        MaterialCheckBox keepBroadcastInput = form.findViewById(R.id.keep_broadcast_input);
        MaterialCheckBox keepAlarmInput = form.findViewById(R.id.keep_alarm_input);
        MaterialCheckBox keepBinderInput = form.findViewById(R.id.keep_binder_input);
        MaterialCheckBox keepSensorInput = form.findViewById(R.id.keep_sensor_input);
        MaterialCheckBox keepGpsInput = form.findViewById(R.id.keep_gps_input);
        MaterialCheckBox keepWakeLockInput = form.findViewById(R.id.keep_wake_lock_input);
        MaterialCheckBox keepAudioInput = form.findViewById(R.id.keep_audio_input);
        MaterialCheckBox keepBtScanInput = form.findViewById(R.id.keep_bt_scan_input);

        List<String> suggestions = installedPackages;
        if (suggestions.isEmpty()) {
            suggestions = queryInstalledPackages();
            installedPackages = suggestions;
        }
        packageInput.setThreshold(1);
        packageInput.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, suggestions));

        String[] packetWakeModes = getResources().getStringArray(R.array.packet_wake_modes);
        packetWakeModeInput.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, packetWakeModes));
        int[] selectedPacketWakeMode = {PolicyRule.PACKET_WAKE_ALLOW};
        packetWakeModeInput.setOnItemClickListener((parent, view, position, id) -> {
            selectedPacketWakeMode[0] = position;
            updatePacketWakeControls(position, packetThrottle,
                    customPacketRefreezeInput, packetRefreezeTiming);
        });

        if (existing == null) {
            enabledInput.setChecked(true);
            fullExemptInput.setChecked(true);
            rToMInput.setText("60");
            mToFInput.setText("60");
            packetWakeCooldownInput.setText("60");
            packetRefreezeInput.setText("5");
        } else {
            packageInput.setText(existing.packageName, false);
            enabledInput.setChecked(existing.enabled);
            fullExemptInput.setChecked(existing.fullExempt);
            customTimingInput.setChecked(existing.customTiming);
            rToMInput.setText(String.valueOf(existing.rToMMs / 1_000L));
            mToFInput.setText(String.valueOf(existing.mToFMs / 1_000L));
            selectedPacketWakeMode[0] = clampPacketWakeMode(existing.packetWakeMode);
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
        packetWakeModeInput.setText(packetWakeModes[selectedPacketWakeMode[0]], false);

        advanced.setVisibility(fullExemptInput.isChecked() ? View.GONE : View.VISIBLE);
        timing.setVisibility(customTimingInput.isChecked() ? View.VISIBLE : View.GONE);
        fullExemptInput.setOnCheckedChangeListener((button, checked) ->
                advanced.setVisibility(checked ? View.GONE : View.VISIBLE));
        customTimingInput.setOnCheckedChangeListener((button, checked) ->
                timing.setVisibility(checked ? View.VISIBLE : View.GONE));
        customPacketRefreezeInput.setOnCheckedChangeListener((button, checked) ->
                updatePacketWakeControls(selectedPacketWakeMode[0], packetThrottle,
                        customPacketRefreezeInput, packetRefreezeTiming));
        updatePacketWakeControls(selectedPacketWakeMode[0], packetThrottle,
                customPacketRefreezeInput, packetRefreezeTiming);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(existing == null ? R.string.add_rule : R.string.edit_rule)
                .setView(form)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null);
        if (existing != null) {
            builder.setNeutralButton(R.string.delete, null);
        }
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(ignored -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setSoftInputMode(
                        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            }
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(view -> {
                try {
                    String packageName = packageInput.getText().toString().trim();
                    long rToM = parseSeconds(rToMInput);
                    long mToF = parseSeconds(mToFInput);
                    int packetWakeMode = selectedPacketWakeMode[0];
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
                    Toast.makeText(this, R.string.invalid_delay, Toast.LENGTH_SHORT).show();
                } catch (IllegalArgumentException exception) {
                    Toast.makeText(this, exception.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
            if (existing != null) {
                int error = MaterialColors.getColor(dialog.getButton(
                                DialogInterface.BUTTON_NEUTRAL),
                        com.google.android.material.R.attr.colorError);
                dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setTextColor(error);
                dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener(view ->
                        confirmDelete(existing, dialog));
            }
        });
        dialog.show();
    }

    private void confirmDelete(PolicyRule rule, AlertDialog editor) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_rule)
                .setMessage(rule.packageName)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    repository.delete(rule.key());
                    editor.dismiss();
                    render();
                })
                .show();
    }

    private void loadInstalledPackages() {
        new Thread(() -> installedPackages = queryInstalledPackages(),
                "HansPolicyPackages").start();
    }

    private List<String> queryInstalledPackages() {
        List<String> packages = new ArrayList<>();
        for (ApplicationInfo info : getPackageManager().getInstalledApplications(0)) {
            packages.add(info.packageName);
        }
        Collections.sort(packages);
        return packages;
    }

    private static void updatePacketWakeControls(int mode, View throttle,
                                                  CompoundButton customRefreeze,
                                                  View refreezeTiming) {
        boolean blocked = mode == PolicyRule.PACKET_WAKE_BLOCK;
        throttle.setVisibility(mode == PolicyRule.PACKET_WAKE_THROTTLE
                ? View.VISIBLE : View.GONE);
        customRefreeze.setVisibility(blocked ? View.GONE : View.VISIBLE);
        refreezeTiming.setVisibility(!blocked && customRefreeze.isChecked()
                ? View.VISIBLE : View.GONE);
    }

    private static int clampPacketWakeMode(int mode) {
        return mode >= PolicyRule.PACKET_WAKE_ALLOW && mode <= PolicyRule.PACKET_WAKE_BLOCK
                ? mode : PolicyRule.PACKET_WAKE_ALLOW;
    }

    private static int checkedFlag(CompoundButton input, int flag) {
        return input.isChecked() ? flag : 0;
    }

    private static long parseSeconds(EditText input) {
        return Math.multiplyExact(Long.parseLong(input.getText().toString().trim()), 1_000L);
    }
}
