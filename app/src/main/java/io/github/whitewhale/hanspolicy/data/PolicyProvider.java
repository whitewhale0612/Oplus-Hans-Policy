package io.github.whitewhale.hanspolicy.data;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;

import io.github.whitewhale.hanspolicy.Constants;

public final class PolicyProvider extends ContentProvider {
    private Context deviceContext;

    @Override
    public boolean onCreate() {
        Context context = getContext();
        if (context == null) {
            return false;
        }
        deviceContext = context.createDeviceProtectedStorageContext();
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        enforceAllowedCaller();
        if (Constants.METHOD_GET_POLICY.equals(method)) {
            SharedPreferences prefs = deviceContext.getSharedPreferences(
                    PolicyRepository.POLICY_PREFS, Context.MODE_PRIVATE);
            Bundle result = new Bundle();
            result.putString(Constants.KEY_POLICY_JSON,
                    prefs.getString(PolicyRepository.KEY_JSON, null));
            return result;
        }
        if (Constants.METHOD_REPORT_STATUS.equals(method)) {
            if (Binder.getCallingUid() != Process.SYSTEM_UID) {
                throw new SecurityException("Only system_server may report Hook status");
            }
            storeStatus(extras == null ? Bundle.EMPTY : extras);
            return Bundle.EMPTY;
        }
        throw new IllegalArgumentException("Unknown method: " + method);
    }

    private void storeStatus(Bundle status) {
        SharedPreferences.Editor editor = deviceContext.getSharedPreferences(
                PolicyRepository.STATUS_PREFS, Context.MODE_PRIVATE).edit();
        editor.putBoolean("active", status.getBoolean("active", false));
        editor.putString("stage", status.getString("stage", ""));
        editor.putString("runtime_source", status.getString("runtime_source", ""));
        editor.putInt("hook_count", status.getInt("hook_count", 0));
        editor.putString("hook_targets", status.getString("hook_targets", ""));
        editor.putString("last_error", status.getString("last_error", ""));
        editor.putString("fingerprint", status.getString("fingerprint", ""));
        editor.putString("boot_id", status.getString("boot_id", ""));
        editor.putLong("policy_revision", status.getLong("policy_revision", -1L));
        editor.putLong("last_report_ms", System.currentTimeMillis());
        editor.apply();
    }

    private void enforceAllowedCaller() {
        int caller = Binder.getCallingUid();
        if (caller != Process.SYSTEM_UID && caller != Process.myUid()) {
            throw new SecurityException("Caller UID " + caller + " is not allowed");
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }
}
