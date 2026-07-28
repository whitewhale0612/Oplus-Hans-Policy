package io.github.whitewhale.hanspolicy.ui;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
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

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.card.MaterialCardView;
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
    private static final String STATE_NAVIGATION_ITEM = "navigation_item";
    private static final String DEVELOPER_URL = "https://github.com/whitewhale0612";
    private static final String REPOSITORY_URL =
            "https://github.com/whitewhale0612/Oplus-Hans-Policy";

    private PolicyRepository repository;
    private RuleAdapter ruleAdapter;
    private MaterialSwitch masterSwitch;
    private MaterialCardView statusBand;
    private View statusMarker;
    private TextView statusTitle;
    private TextView statusDetail;
    private TextView ruleCount;
    private View homePage;
    private View configPage;
    private View settingsPage;
    private BottomNavigationView bottomNavigation;
    private OnBackPressedCallback navigationBackCallback;
    private int selectedNavigationItem = R.id.nav_home;
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
        bindSystemInfo();
        bindExternalLinks();
        setupNavigation(savedInstanceState);
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

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(STATE_NAVIGATION_ITEM, selectedNavigationItem);
        super.onSaveInstanceState(outState);
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

    private void setupNavigation(Bundle savedInstanceState) {
        homePage = findViewById(R.id.home_page);
        configPage = findViewById(R.id.config_page);
        settingsPage = findViewById(R.id.settings_page);
        bottomNavigation = findViewById(R.id.bottom_navigation);

        navigationBackCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                bottomNavigation.setSelectedItemId(R.id.nav_home);
            }
        };
        getOnBackPressedDispatcher().addCallback(this, navigationBackCallback);

        bottomNavigation.setOnItemSelectedListener(item -> {
            showPage(item.getItemId());
            return true;
        });
        int initialItem = savedInstanceState == null
                ? R.id.nav_home
                : savedInstanceState.getInt(STATE_NAVIGATION_ITEM, R.id.nav_home);
        bottomNavigation.setSelectedItemId(initialItem);
        showPage(initialItem);
    }

    private void showPage(int itemId) {
        int page = itemId == R.id.nav_config || itemId == R.id.nav_settings
                ? itemId : R.id.nav_home;
        selectedNavigationItem = page;
        homePage.setVisibility(page == R.id.nav_home ? View.VISIBLE : View.GONE);
        configPage.setVisibility(page == R.id.nav_config ? View.VISIBLE : View.GONE);
        settingsPage.setVisibility(page == R.id.nav_settings ? View.VISIBLE : View.GONE);
        navigationBackCallback.setEnabled(page != R.id.nav_home);
    }

    private void bindSystemInfo() {
        String manufacturer = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.trim();
        String model = Build.MODEL == null ? "" : Build.MODEL.trim();
        String device = (manufacturer + " " + model).trim();
        findTextView(R.id.device_model_value).setText(device);
        findTextView(R.id.android_version_value).setText(getString(
                R.string.android_version_value, Build.VERSION.RELEASE, Build.VERSION.SDK_INT));
        findTextView(R.id.kernel_version_value).setText(
                System.getProperty("os.version", "-"));
        findTextView(R.id.system_build_value).setText(Build.DISPLAY);
        findTextView(R.id.system_fingerprint_value).setText(Build.FINGERPRINT);
        findTextView(R.id.module_version_value).setText(getString(
                R.string.module_version_value, readVersionName()));
    }

    private void bindExternalLinks() {
        findViewById(R.id.developer_link).setOnClickListener(view -> openUrl(DEVELOPER_URL));
        findViewById(R.id.repository_link).setOnClickListener(view -> openUrl(REPOSITORY_URL));
    }

    private TextView findTextView(int id) {
        return findViewById(id);
    }

    @SuppressWarnings("deprecation")
    private String readVersionName() {
        try {
            String version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            return version == null || version.isEmpty() ? "-" : version;
        } catch (PackageManager.NameNotFoundException ignored) {
            return "-";
        }
    }

    private void openUrl(String value) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(value)));
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(this, R.string.open_link_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void applySystemBarInsets() {
        View root = findViewById(R.id.root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
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
        int container = connected
                ? ContextCompat.getColor(this, R.color.hans_success_container)
                : MaterialColors.getColor(statusBand,
                com.google.android.material.R.attr.colorErrorContainer);
        int content = connected
                ? ContextCompat.getColor(this, R.color.hans_on_success_container)
                : MaterialColors.getColor(statusBand,
                com.google.android.material.R.attr.colorOnErrorContainer);
        int marker = connected
                ? ContextCompat.getColor(this, R.color.hans_success)
                : MaterialColors.getColor(statusBand,
                com.google.android.material.R.attr.colorError);
        statusBand.setCardBackgroundColor(container);
        statusMarker.setBackgroundTintList(ColorStateList.valueOf(marker));
        statusTitle.setTextColor(content);
        statusDetail.setTextColor(content);
        statusTitle.setText(title);
        statusDetail.setText(detail);
    }

    @SuppressWarnings("deprecation")
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
        MaterialAutoCompleteTextView alarmWakeModeInput =
                form.findViewById(R.id.alarm_wake_mode_input);
        View alarmThrottle = form.findViewById(R.id.alarm_throttle_options);
        EditText alarmWakeCooldownInput = form.findViewById(R.id.alarm_wake_cooldown_input);
        MaterialSwitch customAlarmRefreezeInput =
                form.findViewById(R.id.custom_alarm_refreeze_input);
        View alarmRefreezeTiming = form.findViewById(R.id.alarm_refreeze_options);
        EditText alarmRefreezeInput = form.findViewById(R.id.alarm_refreeze_input);
        MaterialCheckBox blockWakeAsyncBinderInput =
                form.findViewById(R.id.block_wake_async_binder_input);
        MaterialCheckBox blockWakeSyncBinderInput =
                form.findViewById(R.id.block_wake_sync_binder_input);
        MaterialCheckBox blockWakeTransBinderInput =
                form.findViewById(R.id.block_wake_trans_binder_input);
        MaterialCheckBox blockWakeSignalInput =
                form.findViewById(R.id.block_wake_signal_input);
        MaterialCheckBox blockWakeActivityInput =
                form.findViewById(R.id.block_wake_activity_input);
        MaterialCheckBox blockWakeServiceInput =
                form.findViewById(R.id.block_wake_service_input);
        MaterialCheckBox blockWakeBroadcastInput =
                form.findViewById(R.id.block_wake_broadcast_input);
        MaterialCheckBox blockWakeProviderInput =
                form.findViewById(R.id.block_wake_provider_input);
        MaterialCheckBox blockWakeJobSyncInput =
                form.findViewById(R.id.block_wake_job_sync_input);
        MaterialCheckBox blockWakeWakelockInput =
                form.findViewById(R.id.block_wake_wakelock_input);
        MaterialCheckBox blockWakeAudioMediaInput =
                form.findViewById(R.id.block_wake_audio_media_input);
        MaterialCheckBox blockWakeConnectivityInput =
                form.findViewById(R.id.block_wake_connectivity_input);
        MaterialCheckBox blockWakeSystemSceneInput =
                form.findViewById(R.id.block_wake_system_scene_input);
        MaterialCheckBox blockWakeOtherInput =
                form.findViewById(R.id.block_wake_other_input);
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
        String[] alarmWakeModes = getResources().getStringArray(R.array.alarm_wake_modes);
        alarmWakeModeInput.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, alarmWakeModes));
        int[] selectedAlarmWakeMode = {PolicyRule.ALARM_WAKE_ALLOW};
        alarmWakeModeInput.setOnItemClickListener((parent, view, position, id) -> {
            selectedAlarmWakeMode[0] = position;
            updateAlarmWakeControls(position, alarmThrottle,
                    customAlarmRefreezeInput, alarmRefreezeTiming);
        });

        if (existing == null) {
            enabledInput.setChecked(true);
            fullExemptInput.setChecked(true);
            rToMInput.setText("60");
            mToFInput.setText("60");
            packetWakeCooldownInput.setText("60");
            packetRefreezeInput.setText("5");
            alarmWakeCooldownInput.setText("900");
            alarmRefreezeInput.setText("5");
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
            selectedAlarmWakeMode[0] = clampAlarmWakeMode(existing.alarmWakeMode);
            alarmWakeCooldownInput.setText(
                    String.valueOf(existing.alarmWakeCooldownMs / 1_000L));
            customAlarmRefreezeInput.setChecked(existing.hasCustomAlarmRefreeze());
            alarmRefreezeInput.setText(String.valueOf(
                    (existing.hasCustomAlarmRefreeze()
                            ? existing.alarmRefreezeMs : existing.mToFMs) / 1_000L));
            blockWakeAsyncBinderInput.setChecked(
                    existing.blocksWake(PolicyRule.WAKE_ASYNC_BINDER));
            blockWakeSyncBinderInput.setChecked(
                    existing.blocksWake(PolicyRule.WAKE_SYNC_BINDER));
            blockWakeTransBinderInput.setChecked(
                    existing.blocksWake(PolicyRule.WAKE_TRANS_BINDER));
            blockWakeSignalInput.setChecked(existing.blocksWake(PolicyRule.WAKE_SIGNAL));
            blockWakeActivityInput.setChecked(
                    existing.blocksWake(PolicyRule.WAKE_ACTIVITY_INPUT));
            blockWakeServiceInput.setChecked(existing.blocksWake(PolicyRule.WAKE_SERVICE));
            blockWakeBroadcastInput.setChecked(
                    existing.blocksWake(PolicyRule.WAKE_BROADCAST));
            blockWakeProviderInput.setChecked(existing.blocksWake(PolicyRule.WAKE_PROVIDER));
            blockWakeJobSyncInput.setChecked(existing.blocksWake(PolicyRule.WAKE_JOB_SYNC));
            blockWakeWakelockInput.setChecked(existing.blocksWake(PolicyRule.WAKE_WAKELOCK));
            blockWakeAudioMediaInput.setChecked(
                    existing.blocksWake(PolicyRule.WAKE_AUDIO_MEDIA));
            blockWakeConnectivityInput.setChecked(
                    existing.blocksWake(PolicyRule.WAKE_CONNECTIVITY));
            blockWakeSystemSceneInput.setChecked(
                    existing.blocksWake(PolicyRule.WAKE_SYSTEM_SCENE));
            blockWakeOtherInput.setChecked(existing.blocksWake(PolicyRule.WAKE_OTHER));
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
        alarmWakeModeInput.setText(alarmWakeModes[selectedAlarmWakeMode[0]], false);

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
        customAlarmRefreezeInput.setOnCheckedChangeListener((button, checked) ->
                updateAlarmWakeControls(selectedAlarmWakeMode[0], alarmThrottle,
                        customAlarmRefreezeInput, alarmRefreezeTiming));
        updateAlarmWakeControls(selectedAlarmWakeMode[0], alarmThrottle,
                customAlarmRefreezeInput, alarmRefreezeTiming);

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
                    int alarmWakeMode = selectedAlarmWakeMode[0];
                    long alarmWakeCooldown = parseSeconds(alarmWakeCooldownInput);
                    long alarmRefreeze = alarmWakeMode != PolicyRule.ALARM_WAKE_BLOCK
                            && customAlarmRefreezeInput.isChecked()
                            ? parseSeconds(alarmRefreezeInput) : 0L;
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
                    int blockedWakeSources = checkedFlag(blockWakeAsyncBinderInput,
                            PolicyRule.WAKE_ASYNC_BINDER)
                            | checkedFlag(blockWakeSyncBinderInput,
                            PolicyRule.WAKE_SYNC_BINDER)
                            | checkedFlag(blockWakeTransBinderInput,
                            PolicyRule.WAKE_TRANS_BINDER)
                            | checkedFlag(blockWakeSignalInput, PolicyRule.WAKE_SIGNAL)
                            | checkedFlag(blockWakeActivityInput,
                            PolicyRule.WAKE_ACTIVITY_INPUT)
                            | checkedFlag(blockWakeServiceInput, PolicyRule.WAKE_SERVICE)
                            | checkedFlag(blockWakeBroadcastInput,
                            PolicyRule.WAKE_BROADCAST)
                            | checkedFlag(blockWakeProviderInput, PolicyRule.WAKE_PROVIDER)
                            | checkedFlag(blockWakeJobSyncInput, PolicyRule.WAKE_JOB_SYNC)
                            | checkedFlag(blockWakeWakelockInput, PolicyRule.WAKE_WAKELOCK)
                            | checkedFlag(blockWakeAudioMediaInput,
                            PolicyRule.WAKE_AUDIO_MEDIA)
                            | checkedFlag(blockWakeConnectivityInput,
                            PolicyRule.WAKE_CONNECTIVITY)
                            | checkedFlag(blockWakeSystemSceneInput,
                            PolicyRule.WAKE_SYSTEM_SCENE)
                            | checkedFlag(blockWakeOtherInput, PolicyRule.WAKE_OTHER);
                    PolicyCodec.validate(packageName, rToM, mToF,
                            blockedSources, bypassFlags, packetWakeMode,
                            packetWakeCooldown, packetRefreeze, alarmWakeMode,
                            alarmWakeCooldown, alarmRefreeze, blockedWakeSources);
                    PolicyRule rule = new PolicyRule(packageName,
                            enabledInput.isChecked(), fullExemptInput.isChecked(),
                            customTimingInput.isChecked(), rToM, mToF,
                            blockedSources, bypassFlags, keepNetworkInput.isChecked(),
                            packetWakeMode, packetWakeCooldown, packetRefreeze,
                            alarmWakeMode, alarmWakeCooldown, alarmRefreeze,
                            blockedWakeSources);
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
        for (ApplicationInfo info : queryInstalledApplications()) {
            packages.add(info.packageName);
        }
        Collections.sort(packages);
        return packages;
    }

    @SuppressWarnings("deprecation")
    private List<ApplicationInfo> queryInstalledApplications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return getPackageManager().getInstalledApplications(
                    PackageManager.ApplicationInfoFlags.of(0L));
        }
        return getPackageManager().getInstalledApplications(0);
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

    private static void updateAlarmWakeControls(int mode, View throttle,
                                                CompoundButton customRefreeze,
                                                View refreezeTiming) {
        boolean blocked = mode == PolicyRule.ALARM_WAKE_BLOCK;
        throttle.setVisibility(mode == PolicyRule.ALARM_WAKE_THROTTLE
                ? View.VISIBLE : View.GONE);
        customRefreeze.setVisibility(blocked ? View.GONE : View.VISIBLE);
        refreezeTiming.setVisibility(!blocked && customRefreeze.isChecked()
                ? View.VISIBLE : View.GONE);
    }

    private static int clampAlarmWakeMode(int mode) {
        return mode >= PolicyRule.ALARM_WAKE_ALLOW && mode <= PolicyRule.ALARM_WAKE_BLOCK
                ? mode : PolicyRule.ALARM_WAKE_ALLOW;
    }

    private static int checkedFlag(CompoundButton input, int flag) {
        return input.isChecked() ? flag : 0;
    }

    private static long parseSeconds(EditText input) {
        return Math.multiplyExact(Long.parseLong(input.getText().toString().trim()), 1_000L);
    }
}
