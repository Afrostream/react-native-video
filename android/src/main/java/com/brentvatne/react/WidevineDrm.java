/*
 * (c)Copyright 2011 Widevine Technologies, Inc
 */

package com.brentvatne.react;

import java.util.EventListener;
import java.util.Locale;
import java.util.Set;

import android.util.Log;
import android.content.ContentValues;
import android.content.Context;
import android.drm.DrmErrorEvent;
import android.drm.DrmEvent;
import android.drm.DrmInfo;
import android.drm.DrmInfoEvent;
import android.drm.DrmInfoRequest;
import android.drm.DrmManagerClient;
import android.drm.DrmStore;
import android.widget.Toast;

public class WidevineDrm {

    public interface WidevineDrmLogEventListener extends EventListener {
    }

    private final static long DEVICE_IS_PROVISIONED = 0;
    // private final static long DEVICE_IS_NOT_PROVISIONED = 1;
    private final static long DEVICE_IS_PROVISIONED_SD_ONLY = 2;
    private long mWVDrmInfoRequestStatusKey = DEVICE_IS_PROVISIONED;

    /**
     * Drm Manager Configuration Methods
     */

    public static class Settings {
        public static String WIDEVINE_MIME_TYPE = "video/wvm";
        public static String DRM_SERVER_URI = "https://lic.staging.drmtoday.com/license-proxy-widevine/";
        public static String DEVICE_ID = "device12345"; // use a unique device
        // ID
        public static String PORTAL_NAME = "castlabs";

        // test with a sizeable block of user data...
        public static String USER_DATA = "eyJ1c2VySWQiOiIyMjQiLCJzZXNzaW9uSWQiOiI0ZWM4NjMwZGEwMTdkMGEzNTRiOGRkMTU2NWJmZTMyYjBhOWE5ZjU3IiwibWVyY2hhbnQiOiJhZnJvc3RyZWFtIn0NCg==";
    }

    private DrmManagerClient mDrmManager;

    // private Context mContext;

    public WidevineDrm(final Context context) {
        Log.d("Widevine", "Drm Server: " + Settings.DRM_SERVER_URI);
        Log.d("Widevine", "Device Id: " + Settings.DEVICE_ID);
        Log.d("Widevine", "Portal Name: " + Settings.PORTAL_NAME);

        // mContext = context;
        mDrmManager = new DrmManagerClient(context);

        mDrmManager.setOnInfoListener(new DrmManagerClient.OnInfoListener() {
            // @Override
            public void onInfo(DrmManagerClient client, DrmInfoEvent event) {
                if (event.getType() == DrmInfoEvent.TYPE_RIGHTS_INSTALLED) {
                    Log.d("Widevine", "Rights installed\n");
                }
            }
        });

        mDrmManager.setOnEventListener(new DrmManagerClient.OnEventListener() {

            public void onEvent(DrmManagerClient client, DrmEvent event) {
                switch (event.getType()) {
                    case DrmEvent.TYPE_DRM_INFO_PROCESSED:
                        Log.d("Widevine", "Info Processed\n");
                        break;
                    case DrmEvent.TYPE_ALL_RIGHTS_REMOVED:
                        Log.d("Widevine", "All rights removed\n");
                        break;
                }
            }
        });

        mDrmManager.setOnErrorListener(new DrmManagerClient.OnErrorListener() {
            public void onError(DrmManagerClient client, DrmErrorEvent event) {

                Toast.makeText(context, event.getType(), Toast.LENGTH_SHORT).show();

                switch (event.getType()) {
                    case DrmErrorEvent.TYPE_NO_INTERNET_CONNECTION:
                        Log.d("Widevine", "No Internet Connection\n");
                        break;
                    case DrmErrorEvent.TYPE_NOT_SUPPORTED:
                        Log.d("Widevine", "Not Supported\n");
                        break;
                    case DrmErrorEvent.TYPE_OUT_OF_MEMORY:
                        Log.d("Widevine", "Out of Memory\n");
                        break;
                    case DrmErrorEvent.TYPE_PROCESS_DRM_INFO_FAILED:
                        Log.d("Widevine", "Process DRM Info failed\n");
                        break;
                    case DrmErrorEvent.TYPE_REMOVE_ALL_RIGHTS_FAILED:
                        Log.d("Widevine", "Remove All Rights failed\n");
                        break;
                    case DrmErrorEvent.TYPE_RIGHTS_NOT_INSTALLED:
                        Log.d("Widevine", "Rights not installed\n");
                        Toast.makeText(context, "We're sorry, you don't have a valid license for this video.", Toast.LENGTH_SHORT).show();
                        break;
                    case DrmErrorEvent.TYPE_RIGHTS_RENEWAL_NOT_ALLOWED:
                        Log.d("Widevine", "Rights renewal not allowed\n");
                        break;
                }

            }
        });
    }

    public DrmInfoRequest getDrmInfoRequest(String assetUri) {
        DrmInfoRequest rightsAcquisitionInfo;
        rightsAcquisitionInfo = new DrmInfoRequest(
                DrmInfoRequest.TYPE_RIGHTS_ACQUISITION_INFO,
                Settings.WIDEVINE_MIME_TYPE);

        rightsAcquisitionInfo.put("WVDRMServerKey", Settings.DRM_SERVER_URI);
        rightsAcquisitionInfo.put("WVAssetURIKey", assetUri);
        rightsAcquisitionInfo.put("WVDeviceIDKey", Settings.DEVICE_ID);
        rightsAcquisitionInfo.put("WVPortalKey", Settings.PORTAL_NAME);
        rightsAcquisitionInfo.put("WVCAUserDataKey", Settings.USER_DATA);

        // FIXME params in demo from Nice but not in Widevine's documentation
        // rightsAcquisitionInfo.put("WVClientIPKey", "XXXclientipXXX");
        // rightsAcquisitionInfo.put("WVClientIdKey", Settings.DEVICE_ID);
        // rightsAcquisitionInfo.put("WVSessionIdKey", "sess4321");

        return rightsAcquisitionInfo;
    }

    public boolean isProvisionedDevice() {
        return ((mWVDrmInfoRequestStatusKey == DEVICE_IS_PROVISIONED) || (mWVDrmInfoRequestStatusKey == DEVICE_IS_PROVISIONED_SD_ONLY));
    }

    public void registerPortal(String portal) {

        DrmInfoRequest request = new DrmInfoRequest(
                DrmInfoRequest.TYPE_REGISTRATION_INFO,
                Settings.WIDEVINE_MIME_TYPE);
        request.put("WVPortalKey", portal);
        DrmInfo response = mDrmManager.acquireDrmInfo(request);

        String drmInfoRequestStatusKey = (String) response
                .get("WVDrmInfoRequestStatusKey");
        if (null != drmInfoRequestStatusKey
                && !drmInfoRequestStatusKey.equals("")) {
            mWVDrmInfoRequestStatusKey = Long
                    .parseLong(drmInfoRequestStatusKey);
        }
    }

    public int acquireRights(String assetUri) {
        Log.d("Widevine", "acquireRights, Asset Uri: " + assetUri);
        int rights = mDrmManager.acquireRights(getDrmInfoRequest(assetUri));
        Log.d("Widevine", "acquireRights = " + rights + "\n");

        return rights;
    }

    public int checkRightsStatus(String assetUri) {

        // Need to use acquireDrmInfo prior to calling checkRightsStatus
        mDrmManager.acquireDrmInfo(getDrmInfoRequest(assetUri));
        int status = mDrmManager.checkRightsStatus(assetUri);
        Log.d("Widevine", "checkRightsStatus  = " + status + "\n");

        return status;
    }

    public void getConstraints(String assetUri) {

        ContentValues values = mDrmManager.getConstraints(assetUri,
                DrmStore.Action.PLAY);
        logContentValues(values, "No Contraints");
    }

    public void showRights(String assetUri) {
        Log.d("Widevine", "showRights\n");

        // Need to use acquireDrmInfo prior to calling getConstraints
        mDrmManager.acquireDrmInfo(getDrmInfoRequest(assetUri));
        ContentValues values = mDrmManager.getConstraints(assetUri,
                DrmStore.Action.PLAY);
        logContentValues(values, "No Rights");

    }

    private void logContentValues(ContentValues values, String defaultMessage) {
        if (values != null) {

            Set<String> keys = values.keySet();
            for (String key : keys) {
                if (key.toLowerCase(Locale.US).contains("time")) {
                    Log.d("Widevine", key + " = "
                            + SecondsToDHMS(values.getAsLong(key)) + "\n");
                } else if (key.toLowerCase(Locale.US).contains("licensetype")) {
                    Log.d("Widevine", key + " = "
                            + licenseType(values.getAsInteger(key)) + "\n");
                } else if (key.toLowerCase(Locale.US).contains(
                        "licensedresolution")) {
                    Log.d("Widevine", key + " = "
                            + licenseResolution(values.getAsInteger(key))
                            + "\n");
                } else {
                    Log.d("Widevine", key + " = " + values.get(key) + "\n");
                }
            }
        } else {
            Log.d("Widevine", defaultMessage + "\n");
        }
    }

    private static final long seconds_per_minute = 60;
    private static final long seconds_per_hour = 60 * seconds_per_minute;
    private static final long seconds_per_day = 24 * seconds_per_hour;

    private String SecondsToDHMS(long seconds) {
        int days = (int) (seconds / seconds_per_day);
        seconds -= days * seconds_per_day;
        int hours = (int) (seconds / seconds_per_hour);
        seconds -= hours * seconds_per_hour;
        int minutes = (int) (seconds / seconds_per_minute);
        seconds -= minutes * seconds_per_minute;
        return Integer.toString(days) + "d " + Integer.toString(hours) + "h "
                + Integer.toString(minutes) + "m " + Long.toString(seconds)
                + "s";
    }

    private String licenseType(int code) {
        switch (code) {
            case 1:
                return "Streaming";
            case 2:
                return "Offline";
            case 3:
                return "Both";
            default:
                return "Unknown";
        }
    }

    private String licenseResolution(int code) {
        switch (code) {
            case 1:
                return "SD only";
            case 2:
                return "HD or SD content";
            default:
                return "Unknown";
        }
    }

    public int removeRights(String assetUri) {

        // Need to use acquireDrmInfo prior to calling removeRights
        mDrmManager.acquireDrmInfo(getDrmInfoRequest(assetUri));
        int removeStatus = mDrmManager.removeRights(assetUri);
        Log.d("Widevine", "removeRights = " + removeStatus + "\n");

        return removeStatus;
    }

    public int removeAllRights() {
        int removeAllStatus = mDrmManager.removeAllRights();
        Log.d("Widevine", "removeAllRights = " + removeAllStatus + "\n");
        return removeAllStatus;
    }
}