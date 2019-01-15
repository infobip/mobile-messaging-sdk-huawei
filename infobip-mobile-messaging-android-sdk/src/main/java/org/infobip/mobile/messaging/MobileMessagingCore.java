package org.infobip.mobile.messaging;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Pair;

import org.infobip.mobile.messaging.api.appinstance.AppInstanceAtts;
import org.infobip.mobile.messaging.api.support.http.serialization.JsonSerializer;
import org.infobip.mobile.messaging.app.ActivityLifecycleMonitor;
import org.infobip.mobile.messaging.app.ContextHelper;
import org.infobip.mobile.messaging.cloud.MobileMessageHandler;
import org.infobip.mobile.messaging.cloud.MobileMessagingCloudService;
import org.infobip.mobile.messaging.cloud.PlayServicesSupport;
import org.infobip.mobile.messaging.dal.sqlite.DatabaseHelper;
import org.infobip.mobile.messaging.dal.sqlite.PushDatabaseHelperImpl;
import org.infobip.mobile.messaging.dal.sqlite.SqliteDatabaseProvider;
import org.infobip.mobile.messaging.interactive.MobileInteractiveImpl;
import org.infobip.mobile.messaging.interactive.notification.InteractiveNotificationHandler;
import org.infobip.mobile.messaging.logging.MobileMessagingLogger;
import org.infobip.mobile.messaging.mobile.BatchReporter;
import org.infobip.mobile.messaging.mobile.InternalSdkError;
import org.infobip.mobile.messaging.mobile.MobileApiResourceProvider;
import org.infobip.mobile.messaging.mobile.MobileMessagingError;
import org.infobip.mobile.messaging.mobile.appinstance.InstallationActionListener;
import org.infobip.mobile.messaging.mobile.appinstance.InstallationSynchronizer;
import org.infobip.mobile.messaging.mobile.common.MAsyncTask;
import org.infobip.mobile.messaging.mobile.common.RetryPolicyProvider;
import org.infobip.mobile.messaging.mobile.messages.MessagesSynchronizer;
import org.infobip.mobile.messaging.mobile.messages.MoMessageSender;
import org.infobip.mobile.messaging.mobile.seen.SeenStatusReporter;
import org.infobip.mobile.messaging.mobile.user.InstallationsActionListener;
import org.infobip.mobile.messaging.mobile.user.LogoutActionListener;
import org.infobip.mobile.messaging.mobile.user.LogoutServerListener;
import org.infobip.mobile.messaging.mobile.user.LogoutUserSynchronizer;
import org.infobip.mobile.messaging.mobile.user.UserDataReporter;
import org.infobip.mobile.messaging.mobile.version.VersionChecker;
import org.infobip.mobile.messaging.notification.NotificationHandler;
import org.infobip.mobile.messaging.platform.AndroidBroadcaster;
import org.infobip.mobile.messaging.platform.Broadcaster;
import org.infobip.mobile.messaging.platform.MobileMessagingJobService;
import org.infobip.mobile.messaging.platform.Platform;
import org.infobip.mobile.messaging.platform.Time;
import org.infobip.mobile.messaging.stats.MobileMessagingStats;
import org.infobip.mobile.messaging.storage.MessageStore;
import org.infobip.mobile.messaging.storage.MessageStoreWrapper;
import org.infobip.mobile.messaging.storage.MessageStoreWrapperImpl;
import org.infobip.mobile.messaging.telephony.MobileNetworkStateListener;
import org.infobip.mobile.messaging.util.ComponentUtil;
import org.infobip.mobile.messaging.util.DeviceInformation;
import org.infobip.mobile.messaging.util.ExceptionUtils;
import org.infobip.mobile.messaging.util.MobileNetworkInformation;
import org.infobip.mobile.messaging.util.ModuleLoader;
import org.infobip.mobile.messaging.util.PreferenceHelper;
import org.infobip.mobile.messaging.util.SHA1;
import org.infobip.mobile.messaging.util.SoftwareInformation;
import org.infobip.mobile.messaging.util.StringUtils;
import org.infobip.mobile.messaging.util.SystemInformation;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.infobip.mobile.messaging.UserDataMapper.filterOutDeletedData;
import static org.infobip.mobile.messaging.UserDataMapper.toJson;


/**
 * @author sslavin
 * @since 28.04.2016.
 */
public class MobileMessagingCore
        extends MobileMessaging
        implements LogoutServerListener {

    private static final int MESSAGE_ID_PARAMETER_LIMIT = 100;
    private static final long MESSAGE_EXPIRY_TIME = TimeUnit.DAYS.toMillis(7);
    private static final JsonSerializer nullSerializer = new JsonSerializer(true);
    public static final String MM_DEFAULT_HIGH_PRIORITY_CHANNEL_ID = "mm_default_channel_high_priority";
    public static final String MM_DEFAULT_CHANNEL_ID = "mm_default_channel";

    protected static MobileApiResourceProvider mobileApiResourceProvider;
    static String applicationCode;
    private static Map<String, String> applicationCodeHashMap;
    static ApplicationCodeProvider applicationCodeProvider;
    private static DatabaseHelper databaseHelper;
    private static MobileMessagingSynchronizationReceiver mobileMessagingSynchronizationReceiver;
    private final MobileMessagingStats stats;
    private final ExecutorService registrationAlignedExecutor;
    private final RetryPolicyProvider retryPolicyProvider;
    private final Broadcaster broadcaster;
    private final ModuleLoader moduleLoader;
    private NotificationHandler notificationHandler;

    private MessagesSynchronizer messagesSynchronizer;
    private UserDataReporter userDataReporter;
    private InstallationSynchronizer installationSynchronizer;
    private LogoutUserSynchronizer logoutUserSynchronizer;

    private MoMessageSender moMessageSender;
    private SeenStatusReporter seenStatusReporter;
    private VersionChecker versionChecker;
    private ActivityLifecycleMonitor activityLifecycleMonitor;
    @SuppressWarnings("unused")
    private MobileNetworkStateListener mobileNetworkStateListener;
    private PlayServicesSupport playServicesSupport;
    private NotificationSettings notificationSettings;
    private MessageStore messageStore;
    private MessageStoreWrapper messageStoreWrapper;
    private final Context context;
    private Map<String, MessageHandlerModule> messageHandlerModules;
    private volatile boolean didSyncAtLeastOnce;

    protected MobileMessagingCore(Context context) {
        this(context, new AndroidBroadcaster(context), Executors.newSingleThreadExecutor(), new ModuleLoader(context));
    }

    protected MobileMessagingCore(Context context, Broadcaster broadcaster, ExecutorService registrationAlignedExecutor, ModuleLoader moduleLoader) {
        MobileMessagingLogger.init(context);

        this.context = context;
        this.broadcaster = broadcaster;
        this.registrationAlignedExecutor = registrationAlignedExecutor;
        this.stats = new MobileMessagingStats(context);
        this.retryPolicyProvider = new RetryPolicyProvider(context);
        this.moduleLoader = moduleLoader;
        this.notificationHandler = new InteractiveNotificationHandler(context);
        this.messageHandlerModules = loadMessageHandlerModules();

        if (mobileMessagingSynchronizationReceiver == null) {
            mobileMessagingSynchronizationReceiver = new MobileMessagingSynchronizationReceiver();
        }

        Application application = new ContextHelper(context).getApplication();
        if (application != null) {
            this.activityLifecycleMonitor = new ActivityLifecycleMonitor(application);
        }

        ComponentUtil.setSyncronizationReceiverStateEnabled(context, mobileMessagingSynchronizationReceiver, true);
        ComponentUtil.setConnectivityComponentsStateEnabled(context, true);

        initDefaultChannels();
        migratePrefsIfNecessary();
    }

    /**
     * There is no need to migrate system data fields - they'll be newly fetched/synced on the first call of sync method
     */
    private void migratePrefsIfNecessary() {
        if (PreferenceHelper.contains(context, MobileMessagingProperty.PERFORMED_USER_DATA_MIGRATION)) {
            return;
        }

        migrateUserData(MobileMessagingProperty.USER_DATA);
        migrateUserData(MobileMessagingProperty.UNREPORTED_USER_DATA);
        PreferenceHelper.saveBoolean(context, MobileMessagingProperty.PERFORMED_USER_DATA_MIGRATION, true);
    }

    private void migrateUserData(MobileMessagingProperty userDataProperty) {
        if (!PreferenceHelper.contains(context, userDataProperty)) {
            return;
        }

        String unreportedUserData = PreferenceHelper.findString(context, userDataProperty);
        if (unreportedUserData == null) {
            return;
        }

        Pair<UserData, Map<String, CustomUserDataValue>> userDataWithCustomAtts = UserDataMapper.migrateToNewModels(unreportedUserData);
        if (userDataWithCustomAtts.first != null) {
            if (userDataProperty == MobileMessagingProperty.UNREPORTED_USER_DATA) {
                saveUnreportedUserData(userDataWithCustomAtts.first);
            } else if (userDataProperty == MobileMessagingProperty.USER_DATA) {
                saveUserData(userDataWithCustomAtts.first);
            }
        }

        Map<String, CustomUserDataValue> customAtts = userDataWithCustomAtts.second;
        if (customAtts != null) {
            saveCustomAttributes(customAtts);
            if (userDataProperty == MobileMessagingProperty.UNREPORTED_USER_DATA) {
                setUnreportedCustomAttributes(customAtts);
            } else if (userDataProperty == MobileMessagingProperty.USER_DATA) {
                saveCustomAttributes(customAtts);
            }
        }
    }

    private void initDefaultChannels() {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            return;
        }

        CharSequence channelName = SoftwareInformation.getAppName(context);

        NotificationChannel notificationChannel = new NotificationChannel(MM_DEFAULT_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_DEFAULT);
        notificationChannel.enableLights(true);
        notificationChannel.enableVibration(true);
        notificationManager.createNotificationChannel(notificationChannel);

        NotificationSettings notificationSettings = getNotificationSettings();
        if (notificationSettings != null && notificationSettings.areHeadsUpNotificationsEnabled()) {
            NotificationChannel highPriorityNotificationChannel = new NotificationChannel(MM_DEFAULT_HIGH_PRIORITY_CHANNEL_ID, channelName + " High Priority", NotificationManager.IMPORTANCE_HIGH);
            highPriorityNotificationChannel.enableLights(true);
            highPriorityNotificationChannel.enableVibration(true);
            notificationManager.createNotificationChannel(highPriorityNotificationChannel);
        }
    }

    /**
     * Gets an instance of MobileMessagingCore after it is initialized via {@link MobileMessagingCore.Builder}.
     * <br>
     * {@link MobileMessagingCore} is initialized here from application context to minimize possibility of memory leaks.
     *
     * @param context android context object.
     * @return instance of MobileMessagingCore.
     * @see MobileMessagingCore.Builder
     */
    public static MobileMessagingCore getInstance(Context context) {
        return Platform.mobileMessagingCore.get(context);
    }

    public Collection<MessageHandlerModule> getMessageHandlerModules() {
        return messageHandlerModules.values();
    }

    public <T extends MessageHandlerModule> T getMessageHandlerModule(Class<? extends MessageHandlerModule> cls) {
        if (messageHandlerModules.containsKey(cls.getName())) {
            //noinspection unchecked
            return (T) messageHandlerModules.get(cls.getName());
        }

        //noinspection unchecked
        T module = (T) moduleLoader.createModule(cls);
        if (module != null) {
            module.init(context);
            messageHandlerModules.put(cls.getName(), module);
        }
        return module;
    }

    private Map<String, MessageHandlerModule> loadMessageHandlerModules() {
        Map<String, MessageHandlerModule> modules = moduleLoader.loadModulesFromManifest(MessageHandlerModule.class);
        modules.put(MobileInteractiveImpl.class.getName(), new MobileInteractiveImpl());
        for (MessageHandlerModule module : modules.values()) {
            module.init(context);
        }
        return modules;
    }

    public static DatabaseHelper getDatabaseHelper(Context context) {
        if (null == databaseHelper) {
            databaseHelper = new PushDatabaseHelperImpl(context.getApplicationContext());
        }
        return databaseHelper;
    }

    public static SqliteDatabaseProvider getDatabaseProvider(Context context) {
        return (SqliteDatabaseProvider) getDatabaseHelper(context);
    }

    @Nullable
    public ActivityLifecycleMonitor getActivityLifecycleMonitor() {
        return activityLifecycleMonitor;
    }

    public void sync() {
        didSyncAtLeastOnce = true;
        if (!MobileNetworkInformation.isNetworkAvailableSafely(context)) {
            registerForNetworkAvailability();
            return;
        }

        if (TextUtils.isEmpty(MobileMessagingCore.getApplicationCode(context))) {
            MobileMessagingLogger.w("Application code is not found, check your setup");
            return;
        }

        versionChecker().sync();
        performSyncActions();
    }

    public void retrySyncOnNetworkAvailable() {
        if (!didSyncAtLeastOnce) {
            return;
        }

        performSyncActions();
    }

    private void performSyncActions() {
        logoutOnServerIfNeeded();
        syncInstallation();

        if (isLogoutInProgress()) {
            return;
        }

        userDataReporter().sync(null, getUnreportedUserData());
        messagesSynchronizer().sync();
        moMessageSender().sync();
        seenStatusReporter().sync();
    }

    protected void syncInstallation() {
        installationSynchronizer().sync();
    }

    public Boolean getUnreportedPrimarySetting() {
        return PreferenceHelper.runTransaction(new PreferenceHelper.Transaction<Boolean>() {
            @Override
            public Boolean run() {
                if (PreferenceHelper.contains(context, MobileMessagingProperty.IS_PRIMARY_UNREPORTED)) {
                    return PreferenceHelper.findBoolean(context, MobileMessagingProperty.IS_PRIMARY_UNREPORTED);
                }
                return null;
            }
        });
    }

    public boolean isRegistrationUnavailable() {
        if (StringUtils.isBlank(getPushRegistrationId())) {
            MobileMessagingLogger.w("Registration is not available yet");
            return true;
        }
        return false;
    }

    private void logoutOnServerIfNeeded() {
        if (isRegistrationUnavailable()) {
            return;
        }

        if (!isLogoutInProgress()) {
            return;
        }

        logoutUserSynchronizer().logout();
    }

    @Override
    public void onServerLogoutStarted() {
        onLogoutStarted(getPushRegistrationId());
    }

    @Override
    public void onServerLogoutCompleted() {
        onLogoutCompleted();
    }

    @Override
    public void onServerLogoutFailed(Throwable error) {
        MobileMessagingLogger.w("Server logout failed", error);
    }

    private void onLogoutCompleted() {
        PreferenceHelper.saveBoolean(context, MobileMessagingProperty.IS_LOGOUT_UNREPORTED, false);
        broadcaster.userLoggedOut();
        resetCloudToken();
    }

    private void onLogoutStarted(String pushRegId) {
        if (pushRegId == null) {
            return;
        }

        if (pushRegId.equals(getPushRegistrationId())) {
            logoutCurrentInstallation();
            return;
        }

        PreferenceHelper.saveBoolean(context, MobileMessagingProperty.IS_LOGOUT_UNREPORTED, true);
    }

    private void logoutCurrentInstallation() {
        PreferenceHelper.remove(context, MobileMessagingProperty.UNREPORTED_USER_DATA);
        PreferenceHelper.remove(context, MobileMessagingProperty.USER_DATA);
        PreferenceHelper.remove(context, MobileMessagingProperty.INFOBIP_UNREPORTED_MESSAGE_IDS);
        PreferenceHelper.remove(context, MobileMessagingProperty.INFOBIP_UNREPORTED_SEEN_MESSAGE_IDS);
        PreferenceHelper.remove(context, MobileMessagingProperty.INFOBIP_SYNC_MESSAGES_IDS);
        PreferenceHelper.remove(context, MobileMessagingProperty.IS_PRIMARY_UNREPORTED);
        PreferenceHelper.remove(context, MobileMessagingProperty.UNSENT_MO_MESSAGES);

        PreferenceHelper.saveBoolean(context, MobileMessagingProperty.IS_LOGOUT_UNREPORTED, true);
        if (messageStore != null) {
            messageStore.deleteAll(context);
        }
        getNotificationHandler().cancelAllNotifications();
        for (MessageHandlerModule module : messageHandlerModules.values()) {
            module.logoutUser();
        }
    }

    public boolean isLogoutInProgress() {
        return PreferenceHelper.findBoolean(context, MobileMessagingProperty.IS_LOGOUT_UNREPORTED);
    }

    private void registerForNetworkAvailability() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            MobileMessagingJobService.registerJobForConnectivityUpdates(context);
        }
    }

    public NotificationHandler getNotificationHandler() {
        return notificationHandler;
    }

    @Override
    public void enablePushRegistration() {
        PreferenceHelper.saveBoolean(context, MobileMessagingProperty.PUSH_REGISTRATION_ENABLED, true);
        installationSynchronizer().updatePushRegEnabledStatus(true, null);
    }

    @Override
    public void disablePushRegistration() {
        PreferenceHelper.saveBoolean(context, MobileMessagingProperty.PUSH_REGISTRATION_ENABLED, false);
        installationSynchronizer().updatePushRegEnabledStatus(false, null);
    }

    @Override
    public String getPushRegistrationId() {
        return PreferenceHelper.findString(context, MobileMessagingProperty.INFOBIP_REGISTRATION_ID);
    }

    @Override
    public void setAsPrimaryDevice(boolean isPrimary) {
        setAsPrimaryDevice(isPrimary, null);
    }

    @Override
    public void setAsPrimaryDevice(final boolean isPrimary, final InstallationActionListener listener) {
        PreferenceHelper.saveBoolean(context, MobileMessagingProperty.IS_PRIMARY_UNREPORTED, isPrimary);

        if (isLogoutInProgress()) {
            reportErrorLogoutInProgress(listener);
            return;
        }
        installationSynchronizer().updatePrimaryStatus(isPrimary, listener);
    }

    public void savePrimarySetting(boolean isPrimary) {
        PreferenceHelper.saveBoolean(context, MobileMessagingProperty.IS_PRIMARY, isPrimary);
    }

    @Override
    public void setAsPrimaryDevice(String pushRegistrationId, boolean isPrimary) {
        setAsPrimaryDevice(pushRegistrationId, isPrimary, null);
    }

    @Override
    public void setAsPrimaryDevice(final String pushRegistrationId, final boolean isPrimary, final InstallationsActionListener listener) {
        installationSynchronizer().updatePrimaryStatus(pushRegistrationId, isPrimary, new InstallationActionListener() {
            @Override
            public void onSuccess(Installation installations) {
                List<Installation> installationsToReturn = performLocalSettingOfPrimary(pushRegistrationId, isPrimary);
                if (listener != null) {
                    listener.onSuccess(installationsToReturn);
                }
            }

            @Override
            public void onError(MobileMessagingError error) {
                if (listener != null) {
                    listener.onError(error);
                }
            }
        });
    }

    private List<Installation> performLocalSettingOfPrimary(String pushRegId, boolean isPrimary) {
        UserData userData = getUserData();
        if (userData == null) {
            return null;
        }

        List<Installation> installations = userData.getInstallations();
        if (installations != null && !installations.isEmpty()) {
            List<Installation> installationsTemp = new ArrayList<>();
            for (Installation installation : installations) {
                if (pushRegId.equals(installation.getPushRegId())) {
                    installation.setPrimary(isPrimary);
                } else if (installation.getPrimary()) {
                    installation.setPrimary(false);
                }
                installationsTemp.add(installation);
            }
            userData.setInstallations(installationsTemp);
            saveUserDataToPrefs(userData);
        }
        return userData.getInstallations();
    }

    @Override
    public void setApplicationUserId(final String applicationUserId, final InstallationActionListener listener) {
        setApplicationUserIdReported(false);
        saveApplicationUserId(applicationUserId);

        if (isLogoutInProgress()) {
            reportErrorLogoutInProgress(listener);
        } else {
            installationSynchronizer().updateApplicationUserId(applicationUserId, listener);
        }
    }

    @Override
    public void setCustomAttributes(Map<String, CustomUserDataValue> customAttributes, final InstallationActionListener listener) {
        if (customAttributes == null) {
            return;
        }
        setUnreportedCustomAttributes(customAttributes);

        if (isLogoutInProgress()) {
            reportErrorLogoutInProgress(listener);
        } else {
            installationSynchronizer().updateCustomAttributes(customAttributes, listener);
        }
    }

    @Override
    public void setCustomAttributes(Map<String, CustomUserDataValue> customAttributes) {
        setCustomAttributes(customAttributes, null);
    }

    public void saveCustomAttributes(Map<String, CustomUserDataValue> customAttributes) {
        if (customAttributes == null) {
            customAttributes = new HashMap<>();
        }
        PreferenceHelper.saveString(context, MobileMessagingProperty.CUSTOM_ATTRIBUTES, nullSerializer.serialize(customAttributes));
    }

    public String getCustomAttributes() {
        return PreferenceHelper.findString(context, MobileMessagingProperty.CUSTOM_ATTRIBUTES);
    }

    public void setUnreportedCustomAttributes(Map<String, CustomUserDataValue> customAttributes) {
        if (customAttributes == null) {
            customAttributes = new HashMap<>();
        }
        PreferenceHelper.saveString(context, MobileMessagingProperty.UNREPORTED_CUSTOM_ATTRIBUTES, nullSerializer.serialize(customAttributes));
    }

    public String getUnreportedCustomAttributes() {
        if (PreferenceHelper.contains(context, MobileMessagingProperty.UNREPORTED_CUSTOM_ATTRIBUTES)) {
            return PreferenceHelper.findString(context, MobileMessagingProperty.UNREPORTED_CUSTOM_ATTRIBUTES);
        }
        return null;
    }

    @Override
    public boolean isPrimaryDevice() {
        return PreferenceHelper.runTransaction(new PreferenceHelper.Transaction<Boolean>() {
            @Override
            public Boolean run() {
                if (PreferenceHelper.contains(context, MobileMessagingProperty.IS_PRIMARY_UNREPORTED)) {
                    return PreferenceHelper.findBoolean(context, MobileMessagingProperty.IS_PRIMARY_UNREPORTED);
                }
                return PreferenceHelper.findBoolean(context, MobileMessagingProperty.IS_PRIMARY);
            }
        });
    }

    public void saveApplicationUserId(String applicationUserId) {
        PreferenceHelper.saveString(context, MobileMessagingProperty.APP_USER_ID, applicationUserId);
    }

    public String getApplicationUserId() {
        return PreferenceHelper.findString(context, MobileMessagingProperty.APP_USER_ID);
    }

    @Override
    public void setApplicationUserId(String applicationUserId) {
        setApplicationUserId(applicationUserId, null);
    }

    public void setApplicationUserIdReported(boolean reported) {
        PreferenceHelper.saveBoolean(context, MobileMessagingProperty.IS_APP_USER_ID_UNREPORTED, !reported);
    }

    public Boolean isApplicationUserIdReported() {
        return !PreferenceHelper.findBoolean(context, MobileMessagingProperty.IS_APP_USER_ID_UNREPORTED);
    }

    private void reportErrorLogoutInProgress(final InstallationActionListener listener) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (listener != null) {
                    listener.onError(InternalSdkError.LOGOUT_IN_PROGRESS.getError());
                }
            }
        });
    }

    private void reportErrorLogoutInProgress(final ResultListener listener) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (listener != null) {
                    listener.onError(InternalSdkError.LOGOUT_IN_PROGRESS.getError());
                }
            }
        });
    }

    public boolean isPushRegistrationEnabled() {
        return PreferenceHelper.findBoolean(context, MobileMessagingProperty.PUSH_REGISTRATION_ENABLED);
    }

    public String getCloudToken() {
        return PreferenceHelper.findString(context, MobileMessagingProperty.CLOUD_TOKEN);
    }

    public void setCloudToken(String registrationId) {
        PreferenceHelper.saveString(context, MobileMessagingProperty.CLOUD_TOKEN, registrationId);
        setCloudTokenUnreported();
    }

    public String[] getAndRemoveUnreportedMessageIds() {
        return PreferenceHelper.findAndRemoveStringArray(context, MobileMessagingProperty.INFOBIP_UNREPORTED_MESSAGE_IDS);
    }

    public void addUnreportedMessageIds(String... messageIDs) {
        PreferenceHelper.appendToStringArray(context, MobileMessagingProperty.INFOBIP_UNREPORTED_MESSAGE_IDS, messageIDs);
    }

    public void addSyncMessagesIds(String... messageIDs) {
        String[] timestampMessageIdPair = concatTimestampToMessageId(messageIDs);
        PreferenceHelper.appendToStringArray(context, MobileMessagingProperty.INFOBIP_SYNC_MESSAGES_IDS, timestampMessageIdPair);
    }

    public String[] getSyncMessagesIds() {
        String[] messageIds = PreferenceHelper.findStringArray(context, MobileMessagingProperty.INFOBIP_SYNC_MESSAGES_IDS);
        ArrayList<String> messageIdsArrayList = new ArrayList<>(Arrays.asList(messageIds));
        Set<String> messageIdsToSync = new HashSet<>(messageIdsArrayList.size() <= MESSAGE_ID_PARAMETER_LIMIT ? messageIdsArrayList.size() : MESSAGE_ID_PARAMETER_LIMIT);
        boolean shouldUpdateMessageIds = false;

        for (int i = 0; i < messageIdsArrayList.size(); i++) {
            String syncMessage = messageIdsArrayList.get(i);
            String[] messageIdWithTimestamp = syncMessage.split(StringUtils.COMMA_WITH_SPACE);

            String strTimeMessageReceived = messageIdWithTimestamp[1];

            long timeMessageReceived = Long.valueOf(strTimeMessageReceived);
            long timeInterval = Time.now() - timeMessageReceived;

            if (timeInterval > MESSAGE_EXPIRY_TIME || i >= MESSAGE_ID_PARAMETER_LIMIT) {
                messageIdsArrayList.remove(i);
                shouldUpdateMessageIds = true;
            } else {
                messageIdsToSync.add(messageIdWithTimestamp[0]);
            }
        }

        if (shouldUpdateMessageIds) {
            String[] messageIdsToUpdate = new String[messageIdsArrayList.size()];
            messageIdsToUpdate = messageIdsArrayList.toArray(messageIdsToUpdate);
            PreferenceHelper.remove(context, MobileMessagingProperty.INFOBIP_SYNC_MESSAGES_IDS);
            PreferenceHelper.appendToStringArray(context, MobileMessagingProperty.INFOBIP_SYNC_MESSAGES_IDS, messageIdsToUpdate);
        }

        return messageIdsToSync.toArray(new String[messageIdsToSync.size()]);
    }

    public String[] getUnreportedSeenMessageIds() {
        String[] ids = PreferenceHelper.findStringArray(context, MobileMessagingProperty.INFOBIP_UNREPORTED_SEEN_MESSAGE_IDS);
        return filterOutGeneratedMessageIds(ids);
    }

    /**
     * Method to update unreported seen ids
     *
     * @param messageIdMap map that contains old id as key and new id as value
     */
    public void updateUnreportedSeenMessageIds(final Map<String, String> messageIdMap) {
        if (messageIdMap == null || messageIdMap.isEmpty()) {
            return;
        }

        PreferenceHelper.runTransaction(new PreferenceHelper.Transaction<Void>() {
            @Override
            public Void run() {
                String[] reports = PreferenceHelper.findStringArray(context, MobileMessagingProperty.INFOBIP_UNREPORTED_SEEN_MESSAGE_IDS);
                if (reports.length == 0) {
                    return null;
                }

                for (int i = 0; i < reports.length; i++) {
                    String messageIdAndTimestamp[] = reports[i].split(StringUtils.COMMA_WITH_SPACE);
                    String newMessageId = messageIdMap.get(messageIdAndTimestamp[0]);
                    if (newMessageId != null) {
                        reports[i] = StringUtils.concat(newMessageId, messageIdAndTimestamp[1], StringUtils.COMMA_WITH_SPACE);
                    }
                }

                PreferenceHelper.saveStringArray(context, MobileMessagingProperty.INFOBIP_UNREPORTED_SEEN_MESSAGE_IDS, reports);
                return null;
            }
        });
    }

    public void updatedGeneratedMessageIDs(final Map<String, String> messageIdMap) {
        if (messageIdMap == null || messageIdMap.isEmpty()) {
            return;
        }

        PreferenceHelper.runTransaction(new PreferenceHelper.Transaction<Void>() {
            @Override
            public Void run() {
                String[] generatedMessageIds = getGeneratedMessageIds();
                for (String messageId : generatedMessageIds) {
                    if (messageIdMap.get(messageId) != null) {
                        removeGeneratedMessageIds(messageId);
                    }
                }
                return null;
            }
        });
    }

    public void addGeneratedMessageIds(final String... messageIDs) {
        PreferenceHelper.appendToStringArray(context, MobileMessagingProperty.INFOBIP_GENERATED_MESSAGE_IDS, messageIDs);
    }

    private String[] getGeneratedMessageIds() {
        return PreferenceHelper.findStringArray(context, MobileMessagingProperty.INFOBIP_GENERATED_MESSAGE_IDS);
    }

    private void removeGeneratedMessageIds(final String... messageIDs) {
        PreferenceHelper.deleteFromStringArray(context, MobileMessagingProperty.INFOBIP_GENERATED_MESSAGE_IDS, messageIDs);
    }

    private void addUnreportedSeenMessageIds(final String... messageIDs) {
        String[] seenMessages = concatTimestampToMessageId(messageIDs);
        PreferenceHelper.appendToStringArray(context, MobileMessagingProperty.INFOBIP_UNREPORTED_SEEN_MESSAGE_IDS, seenMessages);
    }

    private String[] concatTimestampToMessageId(String[] messageIDs) {
        List<String> syncMessages = new ArrayList<>(messageIDs.length);
        if (messageIDs.length > 0) {
            for (String messageId : messageIDs) {
                if (messageId == null) {
                    continue;
                }
                String seenTimestamp = String.valueOf(Time.now());
                syncMessages.add(StringUtils.concat(messageId, seenTimestamp, StringUtils.COMMA_WITH_SPACE));
            }
        }

        return syncMessages.toArray(new String[syncMessages.size()]);
    }

    private String[] filterOutGeneratedMessageIds(String[] messageIDs) {
        String generatedMessageIDs[] = getGeneratedMessageIds();
        if (generatedMessageIDs.length == 0) {
            return messageIDs;
        }

        List<String> seenIds = getSeenMessageIdsFromReports(messageIDs);
        List<String> filteredSeenReports = new ArrayList<>(Arrays.asList(messageIDs));
        for (String generatedMessageId : generatedMessageIDs) {
            int idIndex = seenIds.indexOf(generatedMessageId);
            if (idIndex >= 0) {
                filteredSeenReports.remove(idIndex);
                seenIds.remove(idIndex);
            }
        }
        return filteredSeenReports.toArray(new String[filteredSeenReports.size()]);
    }

    /**
     * Returns list of messageId and seenTimestamp
     *
     * @param reports concatenated message id and timestamp
     * @return reports
     */
    private List<String> getSeenMessageIdsFromReports(String[] reports) {
        List<String> ids = new ArrayList<>();
        for (String report : reports) {
            ids.add(getSeenMessageIdFromReport(report));
        }
        return ids;
    }

    /**
     * Returns message id from seen report string
     *
     * @param report concatenated message id and timestamp
     * @return message id
     */
    private String getSeenMessageIdFromReport(String report) {
        String[] reportContents = report.split(StringUtils.COMMA_WITH_SPACE);
        return reportContents.length > 0 ? reportContents[0] : null;
    }

    public void removeUnreportedSeenMessageIds(final String... messageIDs) {
        PreferenceHelper.deleteFromStringArray(context, MobileMessagingProperty.INFOBIP_UNREPORTED_SEEN_MESSAGE_IDS, messageIDs);
    }

    public void setMessagesDelivered(String... messageIds) {
        addUnreportedMessageIds(messageIds);
        addSyncMessagesIds(messageIds);
        sync();
    }

    public void setMessagesSeen(String... messageIds) {
        addUnreportedSeenMessageIds(messageIds);
        updateStoredMessagesWithSeenStatus(messageIds);
        sync();
    }

    public void setMessagesSeenDontStore(String... messageIds) {
        addUnreportedSeenMessageIds(messageIds);
        sync();
    }

    private void updateStoredMessagesWithSeenStatus(String[] messageIds) {
        if (!isMessageStoreEnabled()) {
            return;
        }

        MessageStore messageStore = getMessageStore();
        List<String> messageIdList = Arrays.asList(messageIds);
        for (Message m : new ArrayList<>(messageStore.findAll(context))) {
            if (messageIdList.contains(m.getMessageId())) {
                m.setSeenTimestamp(Time.now());
                messageStore.save(context, m);
            }
        }
    }

    @Nullable
    public NotificationSettings getNotificationSettings() {
        if (!isDisplayNotificationEnabled()) {
            return null;
        }
        if (null != notificationSettings)
            return notificationSettings;

        notificationSettings = new NotificationSettings(context);
        return notificationSettings;
    }

    private void setNotificationSettings(NotificationSettings notificationSettings) {
        PreferenceHelper.saveBoolean(context, MobileMessagingProperty.DISPLAY_NOTIFICATION_ENABLED, null != notificationSettings);
        this.notificationSettings = notificationSettings;
    }

    private boolean isDisplayNotificationEnabled() {
        return PreferenceHelper.findBoolean(context, MobileMessagingProperty.DISPLAY_NOTIFICATION_ENABLED);
    }

    static void setSenderId(Context context, String senderId) {
        if (StringUtils.isBlank(senderId)) {
            throw new IllegalArgumentException("senderId is mandatory! Get one here: https://github.com/infobip/mobile-messaging-sdk-android/wiki/Firebase-Cloud-Messaging");
        }
        PreferenceHelper.saveString(context, MobileMessagingProperty.SENDER_ID, senderId);
    }

    public static String getSenderId(Context context) {
        return PreferenceHelper.findString(context, MobileMessagingProperty.SENDER_ID);
    }

    public boolean isRegistrationIdReported() {
        return installationSynchronizer().isCloudTokenReported();
    }

    public boolean isPushServiceTypeChanged() {
        String reportedPushServiceType = getReportedPushServiceType();
        return StringUtils.isBlank(reportedPushServiceType) || !Platform.usedPushServiceType.name().equals(reportedPushServiceType);
    }

    private String getReportedPushServiceType() {
        return PreferenceHelper.findString(context, MobileMessagingProperty.REPORTED_PUSH_SERVICE_TYPE);
    }

    public void setReportedPushServiceType() {
        PreferenceHelper.saveString(context, MobileMessagingProperty.REPORTED_PUSH_SERVICE_TYPE, Platform.usedPushServiceType.name());
    }

    private void setCloudTokenUnreported() {

        if (TextUtils.isEmpty(MobileMessagingCore.getApplicationCode(context))) {
            MobileMessagingLogger.w("Application code not found, check your setup");
            return;
        }

        installationSynchronizer().setCloudTokenReported(false);
    }

    public static void setMessageStoreClass(Context context, Class<? extends MessageStore> messageStoreClass) {
        String value = null != messageStoreClass ? messageStoreClass.getName() : null;
        PreferenceHelper.saveString(context, MobileMessagingProperty.MESSAGE_STORE_CLASS, value);
    }

    public MessageStore getMessageStore() {
        if (!isMessageStoreEnabled()) {
            return null;
        }

        if (null != messageStore) {
            return messageStore;
        }

        Class<? extends MessageStore> messageStoreClass = null;
        try {
            messageStoreClass = getMessageStoreClass();
            messageStore = messageStoreClass.newInstance();
            return messageStore;
        } catch (Exception e) {
            throw new MessageStoreInstantiationException("Can't create message store of type: " + messageStoreClass, e);
        }
    }

    @Override
    public Installation getInstallation() {
        Map<String, CustomUserDataValue> customAttsMap = new HashMap<>();
        String customAttributes = getCustomAttributes();
        if (customAttributes != null) {
            customAttsMap = UserDataMapper.customAttsFrom(customAttributes);
        }
        return new Installation(
                getPushRegistrationId(),
                isPushRegistrationEnabled(),
                isDisplayNotificationEnabled(),
                isGeofencingActivated(),
                SoftwareInformation.getSDKVersion(),
                SoftwareInformation.getAppVersion(context),
                Platform.os,
                SystemInformation.getAndroidSystemVersion(),
                DeviceInformation.getDeviceManufacturer(),
                DeviceInformation.getDeviceModel(),
                DeviceInformation.isDeviceSecure(context),
                SystemInformation.getAndroidSystemLanguage(),
                null, // TODO: get timezone
                getApplicationUserId(),
                DeviceInformation.getDeviceModel(),
                isPrimaryDevice(),
                Platform.usedPushServiceType,
                getCloudToken(),
                customAttsMap);
    }

    @Override
    public void getInstallationFromServer(InstallationActionListener listener) {
        installationSynchronizer().fetchInstance(listener);
    }

    @Override
    public void saveInstallation(Installation installation) {
        saveInstallation(installation, null);
    }

    @Override
    public void saveInstallation(Installation installation, InstallationActionListener listener) {
        if (installation.containsField(AppInstanceAtts.customAttributes)) {
            setUnreportedCustomAttributes(installation.getCustomAttributes());
        }
        if (installation.containsField(AppInstanceAtts.isPrimary)) {
            PreferenceHelper.saveBoolean(context, MobileMessagingProperty.IS_PRIMARY, installation.getPrimary());
            PreferenceHelper.saveBoolean(context, MobileMessagingProperty.IS_PRIMARY_UNREPORTED, true);
        }
        installationSynchronizer().patch(installation, listener);
    }

    @SuppressWarnings({"unchecked", "WeakerAccess"})
    protected Class<? extends MessageStore> getMessageStoreClass() {
        return PreferenceHelper.findClass(context, MobileMessagingProperty.MESSAGE_STORE_CLASS);
    }

    public boolean isMessageStoreEnabled() {
        return null != getMessageStoreClass();
    }

    public MessageStoreWrapper getMessageStoreWrapper() {
        if (messageStoreWrapper == null) {
            messageStoreWrapper = new MessageStoreWrapperImpl(context, getMessageStore());
        }
        return messageStoreWrapper;
    }

    public MobileMessagingStats getStats() {
        return stats;
    }

    public void setLastHttpException(Throwable lastHttpException) {
        PreferenceHelper.saveString(context, MobileMessagingProperty.LAST_HTTP_EXCEPTION, ExceptionUtils.stacktrace(lastHttpException));
    }

    @SuppressWarnings("unused")
    public String getLastHttpException() {
        return PreferenceHelper.findString(context, MobileMessagingProperty.LAST_HTTP_EXCEPTION);
    }

    private void setApplicationCode(String applicationCode) {
        if (shouldSaveApplicationCode(context)) {
            if (StringUtils.isBlank(applicationCode)) {
                throw new IllegalArgumentException("applicationCode is mandatory! Get one here: https://portal.infobip.com/push/applications");
            }
            PreferenceHelper.saveString(context, MobileMessagingProperty.APPLICATION_CODE, applicationCode);
            return;
        }

        PreferenceHelper.saveString(context, MobileMessagingProperty.APPLICATION_CODE, "");
    }

    public static String getApplicationCode(Context context) {

        if (shouldSaveApplicationCode(context)) {
            applicationCode = PreferenceHelper.findString(context, MobileMessagingProperty.APPLICATION_CODE);
            return applicationCode;
        }

        if (applicationCode != null) {
            return applicationCode;
        }

        if (applicationCodeProvider != null) {
            applicationCode = applicationCodeProvider.resolve();
            return applicationCode;
        }

        String appCodeProviderCanonicalClassName = getApplicationCodeProviderClassName(context);

        try {
            Class<?> c = Class.forName(appCodeProviderCanonicalClassName);
            Object applicationCodeProvider = c.newInstance();
            Method resolve = ApplicationCodeProvider.class.getMethod("resolve");
            applicationCode = String.valueOf(resolve.invoke(applicationCodeProvider));

        } catch (Exception e) {
            e.printStackTrace();
        }

        return applicationCode;
    }

    public String getApplicationCode() {
        return getApplicationCode(context);
    }

    public static String getApplicationCodeHash(Context context) {
        return getApplicationCodeHash(getApplicationCode(context));
    }

    public static String getApplicationCodeHash(String applicationCode) {
        if (StringUtils.isBlank(applicationCode)) {
            return null;
        }

        if (applicationCodeHashMap != null && applicationCodeHashMap.containsKey(applicationCode)) {
            return applicationCodeHashMap.get(applicationCode);
        }

        String appCodeHash = SHA1.calc(applicationCode).substring(0, 10);
        applicationCodeHashMap = Collections.singletonMap(applicationCode, appCodeHash);

        return appCodeHash;
    }

    public static void setApiUri(Context context, String apiUri) {
        if (StringUtils.isBlank(apiUri)) {
            throw new IllegalArgumentException("apiUri is mandatory! If in doubt, use " + MobileMessagingProperty.API_URI.getDefaultValue());
        }
        PreferenceHelper.saveString(context, MobileMessagingProperty.API_URI, apiUri);
    }

    public static void resetApiUri(Context context) {
        PreferenceHelper.saveString(context, MobileMessagingProperty.API_URI, (String) MobileMessagingProperty.API_URI.getDefaultValue());
    }

    public static String getApiUri(Context context) {
        return PreferenceHelper.findString(context, MobileMessagingProperty.API_URI);
    }

    static void setReportCarrierInfo(Context context, boolean reportCarrierInfo) {
        PreferenceHelper.saveBoolean(context, MobileMessagingProperty.REPORT_CARRIER_INFO, reportCarrierInfo);
    }

    static void setReportSystemInfo(Context context, boolean reportSystemInfo) {
        PreferenceHelper.saveBoolean(context, MobileMessagingProperty.REPORT_SYSTEM_INFO, reportSystemInfo);
    }

    static void setDoMarkSeenOnNotificationTap(Context context, boolean doMarkSeenOnNotificationTap) {
        PreferenceHelper.saveBoolean(context, MobileMessagingProperty.MARK_SEEN_ON_NOTIFICATION_TAP, doMarkSeenOnNotificationTap);
    }

    public static void setShouldSaveUserData(Context context, boolean shouldSaveUserData) {
        PreferenceHelper.saveBoolean(context, MobileMessagingProperty.SAVE_USER_DATA_ON_DISK, shouldSaveUserData);
    }

    public boolean shouldSaveUserData() {
        return PreferenceHelper.findBoolean(context, MobileMessagingProperty.SAVE_USER_DATA_ON_DISK.getKey(), true);
    }

    public static void setShouldSaveAppCode(Context context, boolean shouldSaveAppCode) {
        PreferenceHelper.saveBoolean(context, MobileMessagingProperty.SAVE_APP_CODE_ON_DISK, shouldSaveAppCode);
    }

    static boolean shouldSaveApplicationCode(Context context) {
        return PreferenceHelper.findBoolean(context, MobileMessagingProperty.SAVE_APP_CODE_ON_DISK.getKey(), true);
    }

    @Override
    public void cleanup() {
        MobileMessagingCore.cleanup(context);
    }

    private static void cleanup(Context context) {
        for (MessageHandlerModule module : Platform.mobileMessagingCore.get(context).messageHandlerModules.values()) {
            module.cleanup();
        }

        applicationCode = null;
        if (mobileMessagingSynchronizationReceiver != null) {
            ComponentUtil.setSyncronizationReceiverStateEnabled(context, mobileMessagingSynchronizationReceiver, false);
            mobileMessagingSynchronizationReceiver = null;
        }
        ComponentUtil.setConnectivityComponentsStateEnabled(context, false);
        resetMobileApi();

        String gcmSenderID = PreferenceHelper.findString(context, MobileMessagingProperty.SENDER_ID);
        MobileMessagingCloudService.enqueueTokenCleanup(context, gcmSenderID);

        PreferenceHelper.remove(context, MobileMessagingProperty.CLOUD_TOKEN);
        PreferenceHelper.remove(context, MobileMessagingProperty.INFOBIP_REGISTRATION_ID);
        PreferenceHelper.remove(context, MobileMessagingProperty.REPORTED_PUSH_SERVICE_TYPE);

        PreferenceHelper.saveBoolean(context, MobileMessagingProperty.CLOUD_TOKEN_REPORTED, false);

        PreferenceHelper.remove(context, MobileMessagingProperty.UNREPORTED_USER_DATA);
        PreferenceHelper.remove(context, MobileMessagingProperty.USER_DATA);
        PreferenceHelper.remove(context, MobileMessagingProperty.INFOBIP_UNREPORTED_MESSAGE_IDS);
        PreferenceHelper.remove(context, MobileMessagingProperty.INFOBIP_UNREPORTED_SEEN_MESSAGE_IDS);
        PreferenceHelper.remove(context, MobileMessagingProperty.UNREPORTED_SYSTEM_DATA);
        PreferenceHelper.remove(context, MobileMessagingProperty.REPORTED_SYSTEM_DATA_HASH);
        PreferenceHelper.remove(context, MobileMessagingProperty.IS_PRIMARY_UNREPORTED);
        PreferenceHelper.remove(context, MobileMessagingProperty.UNSENT_MO_MESSAGES);
    }

    private void resetCloudToken() {
        String gcmSenderID = PreferenceHelper.findString(context, MobileMessagingProperty.SENDER_ID);
        MobileMessagingCloudService.enqueueTokenReset(context, gcmSenderID);
    }

    public static void resetMobileApi() {
        mobileApiResourceProvider = null;
    }

    @Override
    public void saveUserData(UserData userData) {
        saveUserData(userData, null);
    }

    @Override
    public void saveUserData(UserData userData, final MobileMessaging.ResultListener<UserData> listener) {
        UserData existingData = getUnreportedUserData();
        UserData userDataToReport = UserDataMapper.merge(existingData, userData);

        if (userDataToReport != null) {
            saveUnreportedUserData(userDataToReport);
        }

        if (isLogoutInProgress()) {
            reportErrorLogoutInProgress(listener);
            return;
        }

        userDataReporter().sync(listener, userDataToReport);
    }

    @Override
    public void fetchUserData(MobileMessaging.ResultListener<UserData> listener) {
        if (isLogoutInProgress()) {
            reportErrorLogoutInProgress(listener);
            return;
        }

        userDataReporter().fetch(listener);
    }

    @Nullable
    public UserData getUserData() {
        UserData existing = null;
        if (PreferenceHelper.contains(context, MobileMessagingProperty.USER_DATA)) {
            existing = UserDataMapper.fromJson(PreferenceHelper.findString(context, MobileMessagingProperty.USER_DATA));
            if (areInstallationsExpired()) {
                existing.setInstallations(null);
            }
        }

        return UserDataMapper.merge(existing, getUnreportedUserData());
    }

    private boolean areInstallationsExpired() {
        Date now = new Date();
        long expiryTimestamp = PreferenceHelper.findLong(context, MobileMessagingProperty.USER_DATA_INSTALLATIONS_EXPIRE_AT);
        if (expiryTimestamp != 0) {
            Date expiryDate = new Date(expiryTimestamp);
            return expiryDate.before(now);
        }
        return false;
    }

    @Override
    public void logout() {
        if (isRegistrationUnavailable()) {
            return;
        }

        String currentPushRegistrationId = getPushRegistrationId();
        onLogoutStarted(currentPushRegistrationId);
        if (!MobileNetworkInformation.isNetworkAvailableSafely(context)) {
            registerForNetworkAvailability();
            return;
        }

        logoutUserSynchronizer().logout();
    }

    @Override
    public void logout(ResultListener<SuccessPending> listener) {
        logout(getPushRegistrationId(), listener);
    }

    @Override
    public void logout(String pushRegId, final ResultListener<SuccessPending> listener) {
        if (isRegistrationUnavailable()) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (listener != null) {
                        listener.onError(InternalSdkError.NO_VALID_REGISTRATION.getError());
                    }
                }
            });
            return;
        }

        onLogoutStarted(pushRegId);
        if (!MobileNetworkInformation.isNetworkAvailableSafely(context)) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (listener != null) {
                        listener.onResult(SuccessPending.Pending);
                    }
                }
            });
            registerForNetworkAvailability();
            return;
        }

        logoutUserSynchronizer().logout(pushRegId, new LogoutActionListener() {
            @Override
            public void onUserInitiatedLogoutCompleted() {
                onLogoutCompleted();
                if (listener != null) {
                    listener.onResult(SuccessPending.Success);
                }
            }

            @Override
            public void onUserInitiatedLogoutFailed(Throwable error) {
                if (listener != null) {
                    listener.onError(MobileMessagingError.createFrom(error));
                }
            }
        });
    }

    @Override
    public void logout(final String pushRegId, final InstallationsActionListener listener) {
        logout(pushRegId, new ResultListener<SuccessPending>() {
            @Override
            public void onResult(SuccessPending result) {
                if (SuccessPending.Pending.name().equals(result.name())) {
                    if (listener != null) {
                        listener.onError(MobileMessagingError.createFrom(new IllegalStateException())); //TODO put something more convenient here or use different approach!
                    }
                    return;
                }

                List<Installation> installations = performLocalLogout(pushRegId);
                if (listener != null) {
                    listener.onSuccess(installations);
                }
            }

            @Override
            public void onError(MobileMessagingError e) {
                if (listener != null) {
                    listener.onError(e);
                }
            }
        });
    }

    private List<Installation> performLocalLogout(String pushRegId) {
        UserData userData = getUserData();
        if (userData == null) {
            return null;
        }

        List<Installation> installations = userData.getInstallations();
        if (installations != null && !installations.isEmpty()) {
            List<Installation> installationsTemp = new ArrayList<>(installations);
            for (Installation installation : installationsTemp) {
                if (pushRegId.equals(installation.getPushRegId())) {
                    installations.remove(installation);
                    break;
                }
            }
            userData.setInstallations(installations);
            saveUserDataToPrefs(userData);
        }
        return userData.getInstallations();
    }

    @Nullable
    public UserData getUnreportedUserData() {
        if (PreferenceHelper.contains(context, MobileMessagingProperty.UNREPORTED_USER_DATA)) {
            return UserDataMapper.fromJson(PreferenceHelper.findString(context, MobileMessagingProperty.UNREPORTED_USER_DATA));
        }
        return null;
    }

    public void setUserDataReportedWithError() {
        setUserDataReported(null, false);
    }

    public void setUserDataReported(UserData userData, boolean merge) {
        if (userData != null && shouldSaveUserData()) {
            UserData dataForStoring = userData;
            dataForStoring.clearUnreportedData();
            if (merge) {
                dataForStoring = UserDataMapper.merge(getUserData(), userData);
            }
            saveUserDataToPrefs(filterOutDeletedData(dataForStoring));
        }
        PreferenceHelper.remove(context, MobileMessagingProperty.UNREPORTED_USER_DATA);
    }

    private void saveUserDataToPrefs(UserData dataForStoring) {
        long oneMinuteInMillis = 60000;
        Calendar date = Calendar.getInstance();
        long t = date.getTimeInMillis();
        Date inOneMinute = new Date(t + oneMinuteInMillis);

        PreferenceHelper.saveLong(context, MobileMessagingProperty.USER_DATA_INSTALLATIONS_EXPIRE_AT, inOneMinute.getTime());
        PreferenceHelper.saveString(context, MobileMessagingProperty.USER_DATA, toJson(dataForStoring));
    }

    @Override
    public void sendMessages(Message... messages) {
        sendMessages(null, messages);
    }

    @Override
    public void sendMessages(MobileMessaging.ResultListener<Message[]> listener, Message... messages) {
        if (isMessageStoreEnabled()) {
            getMessageStore().save(context, messages);
        }
        moMessageSender().send(listener, messages);
    }

    public void sendMessagesDontStore(MobileMessaging.ResultListener<Message[]> listener, Message... messages) {
        moMessageSender().sendDontSave(listener, messages);
    }

    public void sendMessagesWithRetry(Message... messages) {
        moMessageSender().sendWithRetry(messages);
    }

    public boolean isGeofencingActivated() {
        return PreferenceHelper.findBoolean(context, MobileMessagingProperty.GEOFENCING_ACTIVATED.getKey(), false);
    }

    public SystemData getUnreportedSystemData() {
        if (PreferenceHelper.contains(context, MobileMessagingProperty.UNREPORTED_SYSTEM_DATA)) {
            return SystemData.fromJson(PreferenceHelper.findString(context, MobileMessagingProperty.UNREPORTED_SYSTEM_DATA));
        }
        return null;
    }

    public void setSystemDataReported() {
        SystemData systemData = getUnreportedSystemData();
        if (systemData == null) {
            return;
        }

        PreferenceHelper.remove(context, MobileMessagingProperty.UNREPORTED_SYSTEM_DATA);
        PreferenceHelper.saveInt(context, MobileMessagingProperty.REPORTED_SYSTEM_DATA_HASH, systemData.hashCode());
    }

    private void setApplicationCodeProviderClassName(ApplicationCodeProvider applicationCodeProvider) {
        MobileMessagingCore.applicationCodeProvider = applicationCodeProvider;
        if (applicationCodeProvider == null) return;
        PreferenceHelper.saveString(context, MobileMessagingProperty.APP_CODE_PROVIDER_CANONICAL_CLASS_NAME, applicationCodeProvider.getClass().getCanonicalName());
    }

    private static String getApplicationCodeProviderClassName(Context context) {
        return PreferenceHelper.findString(context, MobileMessagingProperty.APP_CODE_PROVIDER_CANONICAL_CLASS_NAME);
    }

    public void saveUnreportedUserData(UserData userData) {
        if (shouldSaveUserData()) {
            PreferenceHelper.remove(context, MobileMessagingProperty.UNREPORTED_USER_DATA);
            PreferenceHelper.saveString(context, MobileMessagingProperty.UNREPORTED_USER_DATA, toJson(userData));
        }
    }

    private void runOnUiThread(final Runnable runnable) {
        new MAsyncTask<Void, Void>() {

            @Override
            public Void run(Void[] voids) {
                return null;
            }

            @Override
            public void after(Void aVoid) {
                runnable.run();
            }
        }.execute();
    }

    @NonNull
    private MobileApiResourceProvider mobileApiResourceProvider() {
        if (mobileApiResourceProvider == null) {
            mobileApiResourceProvider = new MobileApiResourceProvider();
        }
        return mobileApiResourceProvider;
    }

    @NonNull
    private MoMessageSender moMessageSender() {
        if (moMessageSender == null) {
            moMessageSender = new MoMessageSender(context, this, broadcaster,
                    registrationAlignedExecutor, stats, retryPolicyProvider.DEFAULT(), mobileApiResourceProvider().getMobileApiMessages(context), getMessageStoreWrapper());
        }
        return moMessageSender;
    }

    @NonNull
    private UserDataReporter userDataReporter() {
        if (userDataReporter == null) {
            userDataReporter = new UserDataReporter(this, registrationAlignedExecutor,
                    broadcaster, retryPolicyProvider, stats, mobileApiResourceProvider().getMobileApiAppInstance(context));
        }
        return userDataReporter;
    }

    @NonNull
    private LogoutUserSynchronizer logoutUserSynchronizer() {
        if (logoutUserSynchronizer == null) {
            logoutUserSynchronizer = new LogoutUserSynchronizer(
                    this,
                    mobileApiResourceProvider().getMobileApiAppInstance(context),
                    retryPolicyProvider.DEFAULT(),
                    registrationAlignedExecutor,
                    new BatchReporter(PreferenceHelper.findLong(context, MobileMessagingProperty.BATCH_REPORTING_DELAY)),
                    this);
        }
        return logoutUserSynchronizer;
    }

    @NonNull
    private MessagesSynchronizer messagesSynchronizer() {
        if (messagesSynchronizer == null) {
            MobileMessageHandler mobileMessageHandler = new MobileMessageHandler(this, broadcaster, getNotificationHandler(), getMessageStoreWrapper());
            messagesSynchronizer = new MessagesSynchronizer(this, stats, registrationAlignedExecutor,
                    broadcaster, retryPolicyProvider.DEFAULT(), mobileMessageHandler, mobileApiResourceProvider().getMobileApiMessages(context));
        }
        return messagesSynchronizer;
    }

    @NonNull
    private SeenStatusReporter seenStatusReporter() {
        if (seenStatusReporter == null) {
            seenStatusReporter = new SeenStatusReporter(this, stats, registrationAlignedExecutor, broadcaster,
                    mobileApiResourceProvider().getMobileApiMessages(context), new BatchReporter(PreferenceHelper.findLong(context, MobileMessagingProperty.BATCH_REPORTING_DELAY)));
        }
        return seenStatusReporter;
    }

    @NonNull
    private VersionChecker versionChecker() {
        if (versionChecker == null) {
            versionChecker = new VersionChecker(context, this, stats, mobileApiResourceProvider().getMobileApiVersion(context), retryPolicyProvider);
        }
        return versionChecker;
    }

    @NonNull
    private InstallationSynchronizer installationSynchronizer() {
        if (installationSynchronizer == null) {
            installationSynchronizer = new InstallationSynchronizer(
                    context,
                    this,
                    stats,
                    registrationAlignedExecutor,
                    broadcaster,
                    retryPolicyProvider,
                    mobileApiResourceProvider().getMobileApiAppInstance(context));
        }
        return installationSynchronizer;
    }

    /**
     * The {@link MobileMessagingCore} builder class.
     *
     * @author sslavin
     * @see MobileMessagingCore
     * @see NotificationSettings.Builder
     * @see NotificationSettings
     * @since 30.05.2016.
     */
    @SuppressWarnings({"unused", "WeakerAccess", "UnusedReturnValue"})
    public static final class Builder {

        private final Application application;
        private NotificationSettings notificationSettings = null;
        private String applicationCode = null;
        private ApplicationCodeProvider applicationCodeProvider;

        public Builder(Application application) {
            if (null == application) {
                throw new IllegalArgumentException("application is mandatory!");
            }
            this.application = application;
        }

        private void validateWithParam(Object o) {
            if (null != o) {
                return;
            }
            throw new IllegalArgumentException("Can't use 'with' method with null argument!");
        }

        /**
         * It will set the notification configuration which will be used to display the notification automatically.
         * <pre>
         * {@code new MobileMessagingCore.Builder(context)
         *       .withDisplayNotification(
         *           new NotificationSettings.Builder(this)
         *               .withDisplayNotification()
         *               .withCallbackActivity(MyActivity.class)
         *               .build()
         *       )
         *       .build();}
         * </pre>
         *
         * @return {@link Builder}
         */
        public Builder withDisplayNotification(NotificationSettings notificationSettings) {
            this.notificationSettings = notificationSettings;
            return this;
        }

        /**
         * When you want to use the Application code that is not stored to <i>infobip_application_code</i> string resource
         * By default it will use <i>infobip_application_code</i> string resource
         *
         * @param applicationCode if you don't have one, you should get one <a href="https://portal.infobip.com/push/applications">here</a>
         * @return {@link Builder}
         */
        public Builder withApplicationCode(String applicationCode) {
            validateWithParam(applicationCode);
            this.applicationCode = applicationCode;
            return this;
        }

        /**
         * When you want to take more care about privacy and don't want to store Application code in <i>infobip_application_code</i>
         * string resource nor in our persistent storage, but would like to use it only from memory. In this case, you should
         * provide it on demand. For example, you should implement <b>sync</b> API call to your server where you store required
         * Application code and provide it to {@link ApplicationCodeProvider#resolve()} method as a return type.
         * <p>
         * Sync (not async) API call is encouraged because we already handle your code in a background thread.
         *
         * @param applicationCodeProvider if you don't have application code, you should get one <a href="https://portal.infobip.com/push/applications">here</a>
         * @return {@link Builder}
         */
        public Builder withApplicationCode(ApplicationCodeProvider applicationCodeProvider) {
            validateWithParam(applicationCodeProvider);
            this.applicationCodeProvider = applicationCodeProvider;
            return this;
        }

        /**
         * Builds the <i>MobileMessagingCore</i> configuration. Registration token sync is started by default.
         * Any messages received in the past will be reported as delivered!
         *
         * @return {@link MobileMessagingCore}
         */
        public MobileMessagingCore build(@Nullable final InitListener initListener) {
            if (shouldSaveApplicationCode(application.getApplicationContext())) {
                String existingApplicationCode = MobileMessagingCore.getApplicationCode(application.getApplicationContext());
                if (existingApplicationCode != null && !applicationCode.equals(existingApplicationCode)) {
                    MobileMessagingCore.cleanup(application);
                }
            }

            Platform.verify(application);

            MobileMessagingCore mobileMessagingCore = new MobileMessagingCore(application);
            mobileMessagingCore.setNotificationSettings(notificationSettings);
            mobileMessagingCore.setApplicationCode(applicationCode);
            mobileMessagingCore.setApplicationCodeProviderClassName(applicationCodeProvider);
            mobileMessagingCore.mobileNetworkStateListener = new MobileNetworkStateListener(application);
            mobileMessagingCore.playServicesSupport = new PlayServicesSupport();
            mobileMessagingCore.playServicesSupport.checkPlayServicesAndTryToAcquireToken(application.getApplicationContext(), initListener);

            Platform.reset(mobileMessagingCore);

            return mobileMessagingCore;
        }
    }
}
