package io.github.whitewhale.hanspolicy;

import android.net.Uri;

public final class Constants {
    public static final String MODULE_PACKAGE = "io.github.whitewhale.hanspolicy";
    public static final String AUTHORITY = MODULE_PACKAGE + ".policy";
    public static final Uri POLICY_URI = Uri.parse("content://" + AUTHORITY);
    public static final String METHOD_GET_POLICY = "getPolicy";
    public static final String METHOD_REPORT_STATUS = "reportStatus";
    public static final String KEY_POLICY_JSON = "policy_json";
    public static final String ACTION_REFRESH = "io.github.whitewhale.hanspolicy.REFRESH";
    public static final int SCHEMA_VERSION = 5;
    public static final long MIN_DELAY_MS = 1_000L;
    public static final long MAX_DELAY_MS = 86_400_000L;

    private Constants() {
    }
}
