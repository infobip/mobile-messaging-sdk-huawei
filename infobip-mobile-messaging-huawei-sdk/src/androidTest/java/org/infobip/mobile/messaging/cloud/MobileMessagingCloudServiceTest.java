package org.infobip.mobile.messaging.cloud;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import org.infobip.mobile.messaging.Message;
import org.infobip.mobile.messaging.platform.PlatformTestCase;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import java.util.concurrent.Executor;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;

/**
 * @author sslavin
 * @since 05/09/2018.
 */
@RunWith(AndroidJUnit4.class)
public class MobileMessagingCloudServiceTest extends PlatformTestCase {

    private final MobileMessagingCloudHandler handler = Mockito.mock(MobileMessagingCloudHandler.class);
    private final Context context = Mockito.mock(Context.class);

    @Before
    public void beforeEach() {
        Mockito.reset(handler);
        resetMobileMessagingCloudHandler(handler);
        resetBackgroundExecutor(Runnable::run);

        // will verify only below "O" logic since after that it will go deep into JobIntentService (cannot mock)
        resetSdkVersion(Build.VERSION_CODES.N_MR1);
        Mockito.when(context.checkPermission(Mockito.eq(Manifest.permission.WAKE_LOCK), Mockito.anyInt(), Mockito.anyInt()))
                .thenReturn(PackageManager.PERMISSION_DENIED);
    }

    @Test
    public void test_shouldHandleMessage() {

        Message message = new Message();
        message.setBody("body");
        message.setMessageId("messageId");

        MobileMessagingCloudService.enqueueNewMessage(context, message);
        Mockito.verify(handler, Mockito.times(1)).handleWork(Mockito.any(Context.class), intentWith(message));
    }

    @Test
    public void test_shouldHandleNewToken() {
        MobileMessagingCloudService.enqueueNewToken(context, "senderId", "token");
        Mockito.verify(handler, Mockito.times(1)).handleWork(Mockito.any(Context.class), intentWith("senderId", "token"));
    }

    @Test
    public void test_shouldHandleTokenCleanup() {
        MobileMessagingCloudService.enqueueTokenCleanup(context, "senderId");
        Mockito.verify(handler, Mockito.times(1)).handleWork(Mockito.any(Context.class), intentWith("senderId"));
    }

    @Test
    public void test_shouldHandleTokenReset() {
        MobileMessagingCloudService.enqueueTokenReset(context, "senderId");
        Mockito.verify(handler, Mockito.times(1)).handleWork(Mockito.any(Context.class), intentWith("senderId"));
    }

    @Test
    public void test_shouldHandleTokenAcquisition() {
        MobileMessagingCloudService.enqueueTokenAcquisition(context, "senderId");
        Mockito.verify(handler, Mockito.times(1)).handleWork(Mockito.any(Context.class), intentWith("senderId"));
    }

    private static Intent intentWith(final Message message) {
        return Mockito.argThat(o -> {
            Message that = Message.createFrom(o.getExtras());
            return message.getBody().equals(that.getBody())
                    && message.getMessageId().equals(that.getMessageId());
        });
    }

    @SuppressWarnings("SameParameterValue")
    private static Intent intentWith(final String senderId) {
        return Mockito.argThat(o -> {
            return senderId.equals(o.getStringExtra(MobileMessagingCloudHandler.EXTRA_SENDER_ID));
        });
    }

    @SuppressWarnings("SameParameterValue")
    private static Intent intentWith(final String senderId, final String token) {
        return Mockito.argThat(o -> {
            return token.equals(o.getStringExtra(MobileMessagingCloudHandler.EXTRA_TOKEN));
        });
    }
}
