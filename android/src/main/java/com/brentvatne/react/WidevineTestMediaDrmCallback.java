/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.brentvatne.react;

import com.google.android.exoplayer.drm.MediaDrmCallback;
import com.google.android.exoplayer.util.Util;

import android.annotation.TargetApi;
import android.media.MediaDrm.KeyRequest;
import android.media.MediaDrm.ProvisionRequest;
import android.text.TextUtils;
import android.util.Log;
import android.content.Context;

import java.io.IOException;
import java.util.UUID;

import android.drm.DrmErrorEvent;
import android.drm.DrmEvent;
import android.drm.DrmInfo;
import android.drm.DrmInfoEvent;
import android.drm.DrmInfoRequest;
import android.drm.DrmManagerClient;
import android.drm.DrmStore;

/**
 * A {@link MediaDrmCallback} for Widevine test content.
 */
@TargetApi(18)
public class WidevineTestMediaDrmCallback implements MediaDrmCallback {

    private final DrmManagerClient mDrmManager;
    private final String assetUri;
    private final String userData;

    /**
     * Drm Manager Configuration Methods
     */

    public static class Settings {
        public static String WIDEVINE_MIME_TYPE = "video/wvm";
        public static String DRM_SERVER_URI = "https://lic.staging.drmtoday.com/license-proxy-widevine/";
        public static String DEVICE_ID = "device12345"; // use a unique device
        // ID
        public static String PORTAL_NAME = "castlabs";
    }

    public WidevineTestMediaDrmCallback(Context context, String url, String user) {
        assetUri = url;
        userData = user;
        Log.d("defaultUri", assetUri);
        // mContext = context;
        mDrmManager = new DrmManagerClient(context);
    }

    public DrmInfoRequest getDrmInfoRequest() {
        DrmInfoRequest rightsAcquisitionInfo;
        rightsAcquisitionInfo = new DrmInfoRequest(
                DrmInfoRequest.TYPE_RIGHTS_ACQUISITION_INFO,
                Settings.WIDEVINE_MIME_TYPE);

        rightsAcquisitionInfo.put("WVDRMServerKey", Settings.DRM_SERVER_URI);
        rightsAcquisitionInfo.put("WVDeviceIDKey", Settings.DEVICE_ID);
        rightsAcquisitionInfo.put("WVPortalKey", Settings.PORTAL_NAME);
        rightsAcquisitionInfo.put("WVAssetURIKey", assetUri);
        rightsAcquisitionInfo.put("WVCAUserDataKey", userData);

        return rightsAcquisitionInfo;
    }

    @Override
    public byte[] executeProvisionRequest(UUID uuid, ProvisionRequest request) throws IOException {
        DrmInfoRequest requestDrm = new DrmInfoRequest(
                DrmInfoRequest.TYPE_REGISTRATION_INFO,
                Settings.WIDEVINE_MIME_TYPE);
        requestDrm.put("WVPortalKey", Settings.PORTAL_NAME);
        DrmInfo response = mDrmManager.acquireDrmInfo(requestDrm);
        int rights = mDrmManager.acquireRights(getDrmInfoRequest());

        return response.getData();
    }

    @Override
    public byte[] executeKeyRequest(UUID uuid, KeyRequest request) throws IOException {
        DrmInfoRequest requestDrm = new DrmInfoRequest(
                DrmInfoRequest.TYPE_REGISTRATION_INFO,
                Settings.WIDEVINE_MIME_TYPE);
        requestDrm.put("WVPortalKey", Settings.PORTAL_NAME);
        DrmInfo response = mDrmManager.acquireDrmInfo(requestDrm);
        int rights = mDrmManager.acquireRights(getDrmInfoRequest());

        return response.getData();
    }

}