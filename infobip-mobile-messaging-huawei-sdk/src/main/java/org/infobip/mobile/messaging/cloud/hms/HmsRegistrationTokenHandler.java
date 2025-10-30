/*
 * HmsRegistrationTokenHandler.java
 * Mobile Messaging SDK
 *
 * Copyright (c) 2016-2025 Infobip Limited
 * Licensed under the Apache License, Version 2.0
 */
package org.infobip.mobile.messaging.cloud.hms;

import android.content.Context;

import com.huawei.hms.aaid.HmsInstanceId;
import com.huawei.hms.common.ApiException;
import com.huawei.hms.push.HmsMessaging;

import org.infobip.mobile.messaging.MobileMessagingCore;
import org.infobip.mobile.messaging.cloud.RegistrationTokenHandler;
import org.infobip.mobile.messaging.logging.MobileMessagingLogger;
import org.infobip.mobile.messaging.platform.Broadcaster;
import org.infobip.mobile.messaging.util.StringUtils;

/**
 * @author sslavin
 * @since 03/09/2018.
 */
public class HmsRegistrationTokenHandler extends RegistrationTokenHandler {

    private static final String TAG = HmsRegistrationTokenHandler.class.getSimpleName();

    private final Broadcaster broadcaster;

    public HmsRegistrationTokenHandler(MobileMessagingCore mobileMessagingCore, Broadcaster broadcaster) {
        super(mobileMessagingCore);
        this.broadcaster = broadcaster;
    }

    public void handleNewToken(String senderId, String token) {
        if (StringUtils.isBlank(token)) {
            MobileMessagingLogger.w("Not processing empty HMS token");
            return;
        }

        MobileMessagingLogger.v(TAG, "RECEIVED HMS TOKEN", token);
        broadcaster.tokenReceived(token);
        sendRegistrationToServer(token);
    }

    public void cleanupToken(String senderId, Context context) {
        if (StringUtils.isBlank(senderId)) {
            return;
        }

        try {
            // FIXME: It's sync method.  Fix as it done at getAAID
            HmsInstanceId.getInstance(context).deleteToken(senderId, HmsMessaging.DEFAULT_TOKEN_SCOPE);
        } catch (ApiException e) {
            MobileMessagingLogger.e(TAG, "Error while deleting token", e);
        }
    }

    public void acquireNewToken(final String senderId, Context context) {
        // FIxME: It's sync. Fix as it done at getAAID
        try {
            MobileMessagingLogger.i(TAG, "Try to get token for senderId: " + senderId);
            String token = HmsInstanceId.getInstance(context).getToken(senderId, "HCM");
            handleNewToken(senderId, token);
        } catch (ApiException e) {
            MobileMessagingLogger.e(TAG, "Error while acquiring token", e);
        }
    }
}
