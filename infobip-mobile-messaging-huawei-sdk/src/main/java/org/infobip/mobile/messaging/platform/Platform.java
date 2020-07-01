package org.infobip.mobile.messaging.platform;

import android.content.Context;
import android.os.Build;
import android.support.annotation.VisibleForTesting;

import org.infobip.mobile.messaging.Installation;
import org.infobip.mobile.messaging.MobileMessagingCore;
import org.infobip.mobile.messaging.cloud.MobileMessageHandler;
import org.infobip.mobile.messaging.cloud.MobileMessagingCloudHandler;
import org.infobip.mobile.messaging.cloud.RegistrationTokenHandler;
import org.infobip.mobile.messaging.cloud.hms.HmsRegistrationTokenHandler;
import org.infobip.mobile.messaging.logging.MobileMessagingLogger;
import org.infobip.mobile.messaging.util.ComponentUtil;

import java.util.concurrent.Executor;

/**
 * This class will try to keep all common components which hold Android Context inside.
 *
 * @author sslavin
 * @since 03/09/2018.
 */
public class Platform {

    public static final String os = "Android";
    public static volatile int sdkInt = Build.VERSION.SDK_INT;
    private static volatile Executor backgroundExecutor = new AsyncTaskExecutor();

    public static volatile Lazy<MobileMessagingCore, Context> mobileMessagingCore = createForConstructorAcceptingContext(MobileMessagingCore.class);
    public static volatile Lazy<AndroidBroadcaster, Context> broadcaster = createForConstructorAcceptingContext(AndroidBroadcaster.class);
    public static volatile Lazy<MobileMessageHandler, Context> mobileMessageHandler = create(new Lazy.Initializer<MobileMessageHandler, Context>() {
        @Override
        public MobileMessageHandler initialize(Context context) {
            MobileMessagingCore mobileMessagingCore = Platform.mobileMessagingCore.get(context);
            return new MobileMessageHandler(
                    mobileMessagingCore,
                    broadcaster.get(context),
                    mobileMessagingCore.getNotificationHandler(),
                    mobileMessagingCore.getMessageStoreWrapper());
        }
    });
    public static volatile Lazy<RegistrationTokenHandler, Context> registrationTokenHandler = create(new Lazy.Initializer<RegistrationTokenHandler, Context>() {
        @Override
        public RegistrationTokenHandler initialize(Context param) {
            return initializeTokenHandler(param);
        }
    });
    public static volatile Lazy<MobileMessagingCloudHandler, Context> mobileMessagingCloudHandler = create(new Lazy.Initializer<MobileMessagingCloudHandler, Context>() {
        @Override
        public MobileMessagingCloudHandler initialize(Context context) {
            return new MobileMessagingCloudHandler(registrationTokenHandler.get(context), mobileMessageHandler.get(context));
        }
    });

    public static final Installation.PushServiceType usedPushServiceType = usedPushServiceType();

    public static <T> Lazy<T, Context> create(Lazy.Initializer<T, Context> initializer) {
        return Lazy.create(initializer);
    }

    public static <T> Lazy<T, Context> createForConstructorAcceptingContext(Class<T> cls) {
        return Lazy.fromSingleArgConstructor(cls, Context.class);
    }

    public static void reset(MobileMessagingCore mobileMessagingCore) {
        Platform.mobileMessagingCore = Lazy.just(mobileMessagingCore);
    }

    public static void verify(Context context) {
        ComponentUtil.verifyManifestComponentsForPush(context);
    }

    public static void executeInBackground(Runnable command) {
        Platform.backgroundExecutor.execute(command);
    }

    @VisibleForTesting
    protected static void reset(AndroidBroadcaster broadcaster) {
        Platform.broadcaster = Lazy.just(broadcaster);
    }

    @VisibleForTesting
    protected static void reset(MobileMessageHandler mobileMessageHandler) {
        Platform.mobileMessageHandler = Lazy.just(mobileMessageHandler);
    }

    @VisibleForTesting
    protected static void reset(int sdkVersionInt) {
        Platform.sdkInt = sdkVersionInt;
    }

    @VisibleForTesting
    protected static void reset(MobileMessagingCloudHandler mobileMessagingCloudHandler) {
        Platform.mobileMessagingCloudHandler = Lazy.just(mobileMessagingCloudHandler);
    }

    @VisibleForTesting
    protected static void reset(Executor backgroundExecutor) {
        Platform.backgroundExecutor = backgroundExecutor;
    }

    private static RegistrationTokenHandler initializeTokenHandler(Context context) {
        return new HmsRegistrationTokenHandler(mobileMessagingCore.get(context), broadcaster.get(context));
    }

    private static Installation.PushServiceType usedPushServiceType() {
        Installation.PushServiceType usedPushServiceType = Installation.PushServiceType.HMS;
        MobileMessagingLogger.d("Will use " + usedPushServiceType.name() + " for messaging");
        return usedPushServiceType;
    }
}
