/*
 * MobileMessagingHmsService.java
 * Mobile Messaging SDK
 *
 * Copyright (c) 2016-2025 Infobip Limited
 * Licensed under the Apache License, Version 2.0
 */
package org.infobip.mobile.messaging.cloud.hms;

import android.content.Context;

import com.huawei.hms.push.HmsMessageService;
import com.huawei.hms.push.RemoteMessage;

import org.infobip.mobile.messaging.Message;
import org.infobip.mobile.messaging.MobileMessagingCore;
import org.infobip.mobile.messaging.cloud.MobileMessagingCloudService;
import org.infobip.mobile.messaging.logging.MobileMessagingLogger;

/**
 * @author sslavin
 * @since 03/09/2018.
 */
public class MobileMessagingHmsService extends HmsMessageService {

    private final static String TAG = MobileMessagingHmsService.class.getSimpleName();

    private static HmsMessageMapper messageMapper;

    public static HmsMessageMapper getMessageMapper() {
        if (null == messageMapper) {
            messageMapper = new HmsMessageMapper();
        }
        return messageMapper;
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        onMessageReceived(this, remoteMessage);
    }

    @Override
    public void onNewToken(String token) {
        onNewToken(this, token);
    }

    public static boolean onMessageReceived(Context context, RemoteMessage remoteMessage) {
        Message message = getMessageMapper().createMessage(remoteMessage);
        MobileMessagingLogger.v(TAG, "RECEIVED MESSAGE FROM HMS", message);
        if (message != null) {
            MobileMessagingCloudService.enqueueNewMessage(context, message);
            return true;
        } else {
            MobileMessagingLogger.w("Cannot process message");
            return false;
        }
    }

    public static void onNewToken(Context context, String token) {
        MobileMessagingLogger.v(TAG, "RECEIVED NEW HMS TOKEN", token);
        String senderId = MobileMessagingCore.getSenderId(context);
        MobileMessagingCloudService.enqueueNewToken(context, senderId, token);
    }
}
