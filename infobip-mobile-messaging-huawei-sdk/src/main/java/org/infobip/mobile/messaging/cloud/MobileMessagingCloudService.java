package org.infobip.mobile.messaging.cloud;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import org.infobip.mobile.messaging.Message;
import org.infobip.mobile.messaging.dal.bundle.MessageBundleMapper;
import org.infobip.mobile.messaging.logging.MobileMessagingLogger;
import org.infobip.mobile.messaging.platform.JobIntentService;
import org.infobip.mobile.messaging.platform.Platform;

import static org.infobip.mobile.messaging.cloud.MobileMessagingCloudHandler.*;
import static org.infobip.mobile.messaging.platform.MobileMessagingJob.CLOUD_INTENT_JOB_ID;
import static org.infobip.mobile.messaging.platform.MobileMessagingJob.getScheduleId;
import static org.infobip.mobile.messaging.platform.Platform.mobileMessagingCloudHandler;

/**
 * @author sslavin
 * @since 03/09/2018.
 */
public class MobileMessagingCloudService extends JobIntentService {

    /**
     * Convenience methods for enqueuing work in to this service.
     */

    public static void enqueueTokenAcquisition(Context context, String senderId) {
        if (TextUtils.isEmpty(senderId)) {
            MobileMessagingLogger.e("Cannot acquire token, senderId is empty");
            return;
        }

        enqueueWork(context, new Intent(ACTION_TOKEN_ACQUIRE)
                .putExtra(EXTRA_SENDER_ID, senderId));
    }

    public static void enqueueTokenCleanup(Context context, String senderId) {
        if (TextUtils.isEmpty(senderId)) {
            MobileMessagingLogger.e("Cannot cleanup token, senderId is empty");
            return;
        }

        enqueueWork(context, new Intent(ACTION_TOKEN_CLEANUP)
                .putExtra(EXTRA_SENDER_ID, senderId));
    }

    public static void enqueueTokenReset(Context context, String senderId) {
        if (TextUtils.isEmpty(senderId)) {
            MobileMessagingLogger.e("Cannot reset token, senderId is empty");
            return;
        }

        enqueueWork(context, new Intent(ACTION_TOKEN_RESET)
                .putExtra(EXTRA_SENDER_ID, senderId));
    }

    public static void enqueueNewToken(Context context, String senderId, String token) {
        if (TextUtils.isEmpty(senderId)) {
            MobileMessagingLogger.e("Cannot process new token, senderId is empty");
            return;
        }

        if (TextUtils.isEmpty(token)) {
            MobileMessagingLogger.e("Cannot process new token, token is empty");
            return;
        }

        enqueueWork(context, new Intent(ACTION_NEW_TOKEN)
                .putExtra(EXTRA_SENDER_ID, senderId)
                .putExtra(EXTRA_TOKEN, token));
    }

    public static void enqueueNewMessage(Context context, @NonNull Message message) {
        Bundle messageBundle = MessageBundleMapper.messageToBundle(message);
        enqueueWork(context, new Intent(ACTION_CLOUD_MESSAGE_RECEIVE)
                .putExtras(messageBundle));
    }

    private static void enqueueWork(Context context, Intent work) {
        if (shouldEnqueueViaJobIntentService(context)) {
            enqueueWork(context, MobileMessagingCloudService.class, getScheduleId(context, CLOUD_INTENT_JOB_ID), work.setPackage(context.getPackageName()));
        } else {
            MobileMessagingLogger.w("Enqueuing " + work.getAction() + " without WAKE_LOCK permission");
            enqueueInBackground(context, work);
        }
    }

    private static boolean shouldEnqueueViaJobIntentService(Context context) {
        // 1) for Oreo and above JobIntentService will use JobScheduler -> OK
        // 2) below Oreo it needs WAKE_LOCK permission, won't be able to enqueue w/o it
        return Platform.sdkInt >= Build.VERSION_CODES.O
                || ActivityCompat.checkSelfPermission(context, Manifest.permission.WAKE_LOCK) == PackageManager.PERMISSION_GRANTED;
    }

    private static void enqueueInBackground(final Context context, final Intent work) {
        Platform.executeInBackground(new Runnable() {
            @Override
            public void run() {
                mobileMessagingCloudHandler.get(context).handleWork(context, work);
            }
        });
    }

    @Override
    protected void onHandleWork(@NonNull Intent intent) {
        mobileMessagingCloudHandler.get(this).handleWork(this, intent);
    }
}
