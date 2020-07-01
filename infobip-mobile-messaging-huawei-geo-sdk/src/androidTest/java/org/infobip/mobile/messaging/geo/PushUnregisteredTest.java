package org.infobip.mobile.messaging.geo;

import android.annotation.SuppressLint;

import org.infobip.mobile.messaging.Installation;
import org.infobip.mobile.messaging.MobileMessagingProperty;
import org.infobip.mobile.messaging.api.appinstance.AppInstanceAtts;
import org.infobip.mobile.messaging.api.geo.EventReportBody;
import org.infobip.mobile.messaging.api.geo.EventReportResponse;
import org.infobip.mobile.messaging.api.geo.MobileApiGeo;
import org.infobip.mobile.messaging.api.messages.MessageResponse;
import org.infobip.mobile.messaging.api.messages.MobileApiMessages;
import org.infobip.mobile.messaging.api.messages.SyncMessagesBody;
import org.infobip.mobile.messaging.api.messages.SyncMessagesResponse;
import org.infobip.mobile.messaging.cloud.MobileMessageHandler;
import org.infobip.mobile.messaging.geo.report.GeoReport;
import org.infobip.mobile.messaging.geo.report.GeoReporter;
import org.infobip.mobile.messaging.geo.tools.MobileMessagingTestCase;
import org.infobip.mobile.messaging.mobileapi.BatchReporter;
import org.infobip.mobile.messaging.mobileapi.appinstance.InstallationSynchronizer;
import org.infobip.mobile.messaging.mobileapi.common.MRetryPolicy;
import org.infobip.mobile.messaging.mobileapi.common.RetryPolicyProvider;
import org.infobip.mobile.messaging.mobileapi.messages.MessagesSynchronizer;
import org.infobip.mobile.messaging.mobileapi.seen.SeenStatusReporter;
import org.infobip.mobile.messaging.stats.MobileMessagingStats;
import org.infobip.mobile.messaging.util.PreferenceHelper;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.verification.VerificationMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;


public class PushUnregisteredTest extends MobileMessagingTestCase {

    private GeoReporter geoReporter;
    private SeenStatusReporter seenStatusReporter;
    private InstallationSynchronizer installationSynchronizer;
    private MessagesSynchronizer messagesSynchronizer;
    private MRetryPolicy retryPolicy;

    private MobileApiMessages mobileApiMessages;
    private MobileApiGeo mobileApiGeo;

    private MobileMessageHandler mobileMessageHandler;

    private ArgumentCaptor<Map> captor;

    @SuppressLint({"CommitPrefEdits", "ApplySharedPref"})
    @Override
    public void setUp() throws Exception {
        super.setUp();
        ExecutorService taskExecutor = Executors.newSingleThreadExecutor();
        MobileMessagingStats stats = mobileMessagingCore.getStats();

        PreferenceHelper.saveLong(context, MobileMessagingProperty.BATCH_REPORTING_DELAY, 100L);
        PreferenceHelper.saveBoolean(context, MobileMessagingProperty.GEOFENCING_ACTIVATED, true);

        mobileApiMessages = mock(MobileApiMessages.class);
        mobileApiGeo = mock(MobileApiGeo.class);
        mobileMessageHandler = mock(MobileMessageHandler.class);

        RetryPolicyProvider retryPolicyProvider = new RetryPolicyProvider(context);
        retryPolicy = retryPolicyProvider.DEFAULT();
        installationSynchronizer = new InstallationSynchronizer(context, mobileMessagingCore, stats, taskExecutor, coreBroadcaster, retryPolicyProvider, mobileApiAppInstance);
        seenStatusReporter = new SeenStatusReporter(mobileMessagingCore, stats, taskExecutor, coreBroadcaster, mobileApiMessages, new BatchReporter(100L));
        geoReporter = new GeoReporter(context, mobileMessagingCore, geoBroadcaster, mobileMessagingCore.getStats(), mobileApiGeo);
        messagesSynchronizer = new MessagesSynchronizer(mobileMessagingCore, stats, taskExecutor, coreBroadcaster, retryPolicy, mobileMessageHandler, mobileApiMessages);

        captor = ArgumentCaptor.forClass(Map.class);
    }

    @Test
    public void test_push_registration_disabled() throws Exception {

        // Given
        Map<String, Object> appInstance = new HashMap<>();
        appInstance.put(AppInstanceAtts.regEnabled, false);
        mobileApiAppInstance.patchInstance(anyString(), eq(appInstance));

        // Then
        verifyRegistrationStatusUpdate(after(1000).atLeastOnce(), false);
        Map<String, Object> actualAppInstance = captor.getValue();
        assertFalse((Boolean) actualAppInstance.get(AppInstanceAtts.regEnabled));

        verifySeenStatusReporter(after(1000).atLeastOnce());

        // reports should NOT be called if push is disabled
        verifyGeoReporting(after(1000).never());
        verifyMessagesSynchronizer(after(1000).never());
    }

    @Test
    public void test_push_registration_enabled() throws Exception {
        // Given
        Map<String, Object> appInstance = new HashMap<>();
        appInstance.put(AppInstanceAtts.regEnabled, true);
        mobileApiAppInstance.patchInstance(anyString(), eq(appInstance));

        // Then
        verifyRegistrationStatusUpdate(after(1000).atLeastOnce(), true);
        Map<String, Object> actualAppInstance = captor.getValue();
        assertTrue((Boolean) actualAppInstance.get(AppInstanceAtts.regEnabled));
        verifySeenStatusReporter(after(1000).atLeastOnce());

        // reports should BE called if push is enabled
        verifyGeoReporting(after(1000).atLeastOnce());
        verifyMessagesSynchronizer(after(1000).atLeastOnce());
    }

    private void verifyMessagesSynchronizer(VerificationMode verificationMode) throws InterruptedException {
        mobileMessagingCore.addSyncMessagesIds("test-message-id");
        given(mobileApiMessages.sync(any(SyncMessagesBody.class))).willReturn(new SyncMessagesResponse(new ArrayList<MessageResponse>() {{
            add(new MessageResponse(
                    "test-message-id",
                    "this is title",
                    "body",
                    "sound",
                    "true",
                    "false",
                    "UNKNOWN",
                    "{}",
                    "{}"
            ));
        }}));
        messagesSynchronizer.sync();

        verify(mobileApiMessages, verificationMode).sync(any(SyncMessagesBody.class));
    }

    private void verifySeenStatusReporter(VerificationMode verificationMode) throws InterruptedException {
        String[] messageIds = {"1"};
        mobileMessagingCore.setMessagesSeen(messageIds);
        seenStatusReporter.sync();

        verify(coreBroadcaster, verificationMode).seenStatusReported(any(String[].class));
    }

    private void verifyGeoReporting(VerificationMode verificationMode) throws InterruptedException {

        // Given
        GeoReport report1 = createReport(context, "signalingMessageId1", "campaignId1", "messageId1", true, createArea("areaId1"));
        GeoReport report2 = createReport(context, "signalingMessageId2", "campaignId2", "messageId2", true, createArea("areaId2"));
        GeoReport report3 = createReport(context, "signalingMessageId3", "campaignId3", "messageId3", true, createArea("areaId3"));
        createMessage(context, "signalingMessageId1", "campaignId1", true, report1.getArea(), report2.getArea());
        createMessage(context, "signalingMessageId2", "campaignId2", true, report3.getArea());
        given(mobileApiGeo.report(any(EventReportBody.class))).willReturn(new EventReportResponse());

        // When
        geoReporter.synchronize();

        // Then
        //noinspection unchecked
        verify(geoBroadcaster, verificationMode).geoReported(any(List.class));
    }

    private void verifyRegistrationStatusUpdate(VerificationMode verificationMode, boolean enable) throws InterruptedException {
        Installation installation = new Installation();
        installation.setPushRegistrationEnabled(enable);
        installationSynchronizer.patchMyInstallation(installation, null);

        verify(mobileApiAppInstance, verificationMode).patchInstance(anyString(), captor.capture());
        Map instanceMap = captor.getValue();
        assertEquals(enable, instanceMap.get(AppInstanceAtts.regEnabled));
    }
}
