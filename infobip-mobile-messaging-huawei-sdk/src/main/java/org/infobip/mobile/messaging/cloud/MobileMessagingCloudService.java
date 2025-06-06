package org.infobip.mobile.messaging.cloud;

import static org.infobip.mobile.messaging.cloud.MobileMessagingCloudHandler.ACTION_CLOUD_MESSAGE_RECEIVE;
import static org.infobip.mobile.messaging.cloud.MobileMessagingCloudHandler.ACTION_NEW_TOKEN;
import static org.infobip.mobile.messaging.cloud.MobileMessagingCloudHandler.ACTION_TOKEN_ACQUIRE;
import static org.infobip.mobile.messaging.cloud.MobileMessagingCloudHandler.ACTION_TOKEN_CLEANUP;
import static org.infobip.mobile.messaging.cloud.MobileMessagingCloudHandler.ACTION_TOKEN_RESET;
import static org.infobip.mobile.messaging.cloud.MobileMessagingCloudHandler.EXTRA_SENDER_ID;
import static org.infobip.mobile.messaging.cloud.MobileMessagingCloudHandler.EXTRA_TOKEN;
import static org.infobip.mobile.messaging.cloud.MobileMessagingCloudHandler.MM_ACTION;
import static org.infobip.mobile.messaging.platform.Platform.mobileMessagingCloudHandler;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.infobip.mobile.messaging.Message;
import org.infobip.mobile.messaging.dal.bundle.MessageBundleMapper;
import org.infobip.mobile.messaging.dal.data.MessageDataMapper;
import org.infobip.mobile.messaging.logging.MobileMessagingLogger;
import org.infobip.mobile.messaging.platform.Platform;

/**
 * @author sslavin
 * @since 03/09/2018.
 */
public class MobileMessagingCloudService extends Worker {

    public MobileMessagingCloudService(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    /**
     * Convenience methods for enqueuing work in to this service.
     */

    public static void enqueueTokenAcquisition(Context context, String senderId) {
        if (TextUtils.isEmpty(senderId)) {
            MobileMessagingLogger.e("Cannot acquire token, senderId is empty");
            return;
        }

        enqueueWorkOrBackground(context, ACTION_TOKEN_ACQUIRE, senderId);
    }

    public static void enqueueTokenCleanup(Context context, String senderId) {
        if (TextUtils.isEmpty(senderId)) {
            MobileMessagingLogger.e("Cannot cleanup token, senderId is empty");
            return;
        }

        enqueueWorkOrBackground(context, ACTION_TOKEN_CLEANUP, senderId);
    }

    public static void enqueueTokenReset(Context context, String senderId) {
        if (TextUtils.isEmpty(senderId)) {
            MobileMessagingLogger.e("Cannot reset token, senderId is empty");
            return;
        }

        enqueueWorkOrBackground(context, ACTION_TOKEN_RESET, senderId);
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

        if (shouldEnqueueViaWorker(context)) {
            enqueueWork(context, tokenData(senderId, token));
        } else {
            enqueueInBackground(context, new Intent(ACTION_NEW_TOKEN)
                    .putExtra(EXTRA_SENDER_ID, senderId)
                    .putExtra(EXTRA_TOKEN, token));
        }
    }

    public static void enqueueNewMessage(Context context, @NonNull Message message) {
        if (shouldEnqueueViaWorker(context)) {
            enqueueWork(context, messageData(message));
        } else {
            Bundle messageBundle = MessageBundleMapper.messageToBundle(message);
            enqueueInBackground(context, new Intent(ACTION_CLOUD_MESSAGE_RECEIVE)
                    .putExtras(messageBundle));
        }
    }

    private static void enqueueWorkOrBackground(Context context, String action, String senderId) {
        if (shouldEnqueueViaWorker(context)) {
            enqueueWork(context, inputData(action, senderId));
        } else {
            enqueueInBackground(context, new Intent(action)
                    .putExtra(EXTRA_SENDER_ID, senderId));
        }
    }

    private static void enqueueWork(Context context, Data inputData) {
        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(MobileMessagingCloudService.class)
                .setInputData(inputData)
                .setConstraints(setConstraints())
                .build();

        WorkManager.getInstance(context).enqueue(workRequest);
    }

    static Data inputData(String action, String senderId) {
        return new Data.Builder()
                .putString(MM_ACTION, action)
                .putString(EXTRA_SENDER_ID, senderId)
                .build();
    }

    static Data messageData(Message message) {
        String str = MessageDataMapper.messageToString(message);

        return new Data.Builder()
                .putString(MM_ACTION, ACTION_CLOUD_MESSAGE_RECEIVE)
                .putString(ACTION_CLOUD_MESSAGE_RECEIVE, str)
                .build();
    }

    static Data tokenData(String senderId, String token) {
        return new Data.Builder()
                .putString(MM_ACTION, ACTION_NEW_TOKEN)
                .putString(EXTRA_SENDER_ID, senderId)
                .putString(EXTRA_TOKEN, token)
                .build();
    }

    private static Constraints setConstraints() {
        return new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
    }

    @NonNull
    @Override
    public Result doWork() {
        Data inputData = getInputData();
        mobileMessagingCloudHandler.get(this.getApplicationContext()).handleWork(this.getApplicationContext(), inputData);
        return Result.success();
    }

    // Used for pre-Oreo versions
    private static boolean shouldEnqueueViaWorker(Context context) {
        // 1) for Oreo and above will use Worker -> OK
        // 1) for Oreo and above JobIntentService will use JobScheduler -> OK
        // 2) below Oreo it needs WAKE_LOCK permission, won't be able to enqueue w/o it
        return Platform.sdkInt >= Build.VERSION_CODES.O
                || ActivityCompat.checkSelfPermission(context, Manifest.permission.WAKE_LOCK) == PackageManager.PERMISSION_GRANTED;
    }

    private static void enqueueInBackground(final Context context, final Intent work) {
        MobileMessagingLogger.w("Enqueuing " + work.getAction() + " without WAKE_LOCK permission");
        Platform.executeInBackground(() -> mobileMessagingCloudHandler.get(context).handleWork(context, work));
    }
}
