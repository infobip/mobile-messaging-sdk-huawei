package org.infobip.mobile.messaging.geo.geofencing;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.util.Pair;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.ContextCompat;
import com.huawei.hmf.tasks.Task;
import com.huawei.hms.location.Geofence;
import com.huawei.hms.location.GeofenceRequest;
import com.huawei.hms.location.LocationServices;
import org.infobip.mobile.messaging.ConfigurationException;
import org.infobip.mobile.messaging.ConfigurationException.Reason;
import org.infobip.mobile.messaging.Message;
import org.infobip.mobile.messaging.geo.*;
import org.infobip.mobile.messaging.geo.mapper.GeoDataMapper;
import org.infobip.mobile.messaging.geo.storage.GeoSQLiteMessageStore;
import org.infobip.mobile.messaging.geo.transition.GeofenceTransitionsIntentService;
import org.infobip.mobile.messaging.geo.transition.GeofenceTransitionsReceiver;
import org.infobip.mobile.messaging.logging.MobileMessagingLogger;
import org.infobip.mobile.messaging.platform.Time;
import org.infobip.mobile.messaging.storage.MessageStore;
import org.infobip.mobile.messaging.util.ComponentUtil;

import java.util.*;

public class GeofencingImpl extends Geofencing {
    private static final String TAG = "GeofencingImpl";

    @SuppressLint("StaticFieldLeak")
    private static GeofencingImpl instance;
    private final Context context;
    private static GeoEnabledConsistencyReceiver geoEnabledConsistencyReceiver;
    private final GeofencingHelper geofencingHelper;
    private final MessageStore messageStore;
    private List<Geofence> geofences;
    private PendingIntent geofencePendingIntent;

    private GeofencingImpl(Context context) {
        this.context = context;
        geofences = new ArrayList<>();
        geofencingHelper = new GeofencingHelper(context);
        messageStore = geofencingHelper.getMessageStoreForGeo();
    }

    public static GeofencingImpl getInstance(Context context) {
        if (instance != null) {
            return instance;
        }

        instance = new GeofencingImpl(context);
        return instance;
    }

    static void scheduleRefresh(Context context) {
        scheduleRefresh(context, Time.date());
    }

    private static void scheduleRefresh(Context context, Date when) {
        if (when != null) {
            MobileMessagingLogger.i(TAG, "Next refresh in " + when);
        }

        GeofencingConsistencyReceiver.scheduleConsistencyAlarm(context, AlarmManager.RTC_WAKEUP, when,
                GeofencingConsistencyReceiver.SCHEDULED_GEO_REFRESH_ACTION, 0);
    }

    private static void scheduleExpiry(Context context, Date when) {
        GeofencingConsistencyReceiver.scheduleConsistencyAlarm(context, AlarmManager.RTC_WAKEUP, when,
                GeofencingConsistencyReceiver.SCHEDULED_GEO_EXPIRE_ACTION, 0);
    }

    void removeExpiredAreasFromStorage() {
        GeoSQLiteMessageStore messageStoreForGeo = (GeoSQLiteMessageStore) geofencingHelper.getMessageStoreForGeo();
        List<Message> messages = messageStoreForGeo.findAll(context);
        List<String> messageIdsToDelete = new ArrayList<>(messages.size());
        Date now = Time.date();

        for (Message message : messages) {
            Geo geo = GeoDataMapper.geoFromInternalData(message.getInternalData());
            if (geo == null) {
                continue;
            }

            List<Area> areasList = geo.getAreasList();
            Date expiryDate = geo.getExpiryDate();

            if (areasList == null || areasList.isEmpty()) {
                continue;
            }

            for (Area area : areasList) {
                if (!area.isValid() || expiryDate == null) {
                    continue;
                }

                if (expiryDate.before(now)) {
                    messageIdsToDelete.add(message.getMessageId());
                }
            }
        }

        if (!messageIdsToDelete.isEmpty()) {
            messageStoreForGeo.deleteByIds(context, messageIdsToDelete.toArray(new String[]{}));
        }
    }

    @VisibleForTesting
    public Pair<List<Geofence>, Pair<Date, Date>> calculateGeofencesToMonitorDates(MessageStore messageStore) {
        return calculateGeofencesToMonitorAndNextCheckDates(messageStore);
    }

    @SuppressWarnings("WeakerAccess")
    private Pair<List<Geofence>, Pair<Date, Date>> calculateGeofencesToMonitorAndNextCheckDates(MessageStore messageStore) {
        Date nextCheckRefreshDate = null;
        Date nextCheckExpireDate = null;
        Map<String, Geofence> geofences = new HashMap<>();
        Map<String, Date> expiryDates = new HashMap<>();
        List<Message> messages = messageStore.findAll(context);

        for (Message message : messages) {
            Geo geo = GeoDataMapper.geoFromInternalData(message.getInternalData());
            if (geo == null || geo.getAreasList() == null || geo.getAreasList().isEmpty()) {
                continue;
            }

            nextCheckExpireDate = calculateNextCheckDateForGeoExpiry(geo, nextCheckExpireDate);

            final Set<String> finishedCampaignIds = GeofencingHelper.getFinishedCampaignIds(context);
            if (finishedCampaignIds.contains(geo.getCampaignId())) {
                continue;
            }

            if (geo.isEligibleForMonitoring()) {
                List<Area> geoAreasList = geo.getAreasList();
                for (Area area : geoAreasList) {
                    if (!area.isValid()) {
                        continue;
                    }

                    Date expiry = expiryDates.get(area.getId());
                    if (expiry != null && expiry.after(geo.getExpiryDate())) {
                        continue;
                    }

                    expiryDates.put(area.getId(), geo.getExpiryDate());
                    geofences.put(area.getId(), area.toGeofence(geo.getExpiryDate()));
                }
            }

            nextCheckRefreshDate = calculateNextCheckDateForGeoStart(geo, nextCheckRefreshDate);
        }

        List<Geofence> geofenceList = new ArrayList<>(geofences.values());
        return new Pair<>(geofenceList, new Pair<>(nextCheckRefreshDate, nextCheckExpireDate));
    }

    private static Date calculateNextCheckDateForGeoStart(Geo geo, Date oldCheckDate) {
        Date now = Time.date();
        Date expiryDate = geo.getExpiryDate();
        if (expiryDate != null && expiryDate.before(now)) {
            return oldCheckDate;
        }

        Date startDate = geo.getStartDate();
        if (startDate == null || startDate.before(now)) {
            return oldCheckDate;
        }

        if (oldCheckDate != null && oldCheckDate.before(startDate)) {
            return oldCheckDate;
        }

        return startDate;
    }

    private static Date calculateNextCheckDateForGeoExpiry(Geo geo, Date oldCheckDate) {
        Date now = Time.date();
        Date expiryDate = geo.getExpiryDate();

        if (expiryDate == null) {
            return oldCheckDate;
        }

        if (oldCheckDate != null && oldCheckDate.before(expiryDate)) {
            if (oldCheckDate.before(now)) {
                return now;

            } else {
                return oldCheckDate;
            }
        }

        if (expiryDate.before(now)) {
            return now;
        }

        return expiryDate;
    }

    @SuppressWarnings("MissingPermission")
    @Override
    public void startGeoMonitoring() {

        if (!GeofencingHelper.isGeoActivated(context) ||
                // checking this to avoid multiple activation of geofencing API
                GeofencingHelper.areAllActiveGeoAreasMonitored(context)) {
            return;
        }

        if (!checkRequiredPermissions()) {
            return;
        }

        Pair<List<Geofence>, Pair<Date, Date>> tuple = calculateGeofencesToMonitorAndNextCheckDates(messageStore);
        Date nextRefreshDate = tuple.second.first;
        Date nextExpireDate = tuple.second.second;

        scheduleRefresh(context, nextRefreshDate);
        scheduleExpiry(context, nextExpireDate);

        geofences = tuple.first;
        if (geofences.isEmpty()) {
            return;
        }

        LocationServices.getGeofenceService(context).createGeofenceList(geofencingRequest(), geofencePendingIntent())
                .addOnCompleteListener(task -> {
                    logGeofenceStatus(task, true);
                    GeofencingHelper.setAllActiveGeoAreasMonitored(context, task.isSuccessful());
                });
    }

    @Override
    public void stopGeoMonitoring() {
        GeofencingHelper.setAllActiveGeoAreasMonitored(context, false);

        if (!checkRequiredPermissions()) {
            return;
        }


        LocationServices.getGeofenceService(context).deleteGeofenceList(geofencePendingIntent())
                .addOnCompleteListener(task -> {
                    logGeofenceStatus(task, false);
                });
    }

    @Override
    public void cleanup() {
        setGeoComponentsEnabledSettings(context, false);
        stopGeoMonitoring();
        messageStore.deleteAll(context);
    }

    @Override
    public void depersonalize() {
        stopGeoMonitoring();
        messageStore.deleteAll(context);
    }

    @Override
    public void setGeoComponentsEnabledSettings(Context context, boolean componentsStateEnabled) {
        ComponentUtil.setState(context, componentsStateEnabled, GeofenceTransitionsReceiver.class);
        ComponentUtil.setState(context, componentsStateEnabled, GeofenceTransitionsIntentService.class);
        ComponentUtil.setState(context, componentsStateEnabled, GeofencingConsistencyReceiver.class);
        ComponentUtil.setState(context, componentsStateEnabled, GeofencingConsistencyIntentService.class);
        ComponentUtil.setState(context, componentsStateEnabled, BootReceiver.class);

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1) {
            ComponentUtil.setState(context, componentsStateEnabled, GeoEnabledConsistencyReceiver.class);
            return;
        }

        // >= Android O
        if (componentsStateEnabled) {
            if (null != geoEnabledConsistencyReceiver) {
                return;
            }
            geoEnabledConsistencyReceiver = new GeoEnabledConsistencyReceiver();

            final IntentFilter intentFilter = new IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION);
            context.registerReceiver(geoEnabledConsistencyReceiver, intentFilter);

        } else {
            if (null != geoEnabledConsistencyReceiver) {
                context.unregisterReceiver(geoEnabledConsistencyReceiver);
                geoEnabledConsistencyReceiver = null;
            }
        }
    }

    private boolean checkRequiredPermissions() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            MobileMessagingLogger.e("Unable to configure geofencing", new ConfigurationException(Reason.MISSING_REQUIRED_PERMISSION, Manifest.permission.ACCESS_FINE_LOCATION).getMessage());
            return false;
        }
        return true;
    }

    private void logGeofenceStatus(@NonNull Task task, boolean activated) {
        if (task.isSuccessful()) {
            MobileMessagingLogger.d(TAG, "Geofencing monitoring " + (activated ? "" : "de-") + "activated successfully");

        } else {
            MobileMessagingLogger.e(TAG, "Geofencing monitoring " + (activated ? "" : "de-") + "activation failed: " + task.getException());
        }
    }

    private GeofenceRequest geofencingRequest() {
        GeofenceRequest.Builder builder = new GeofenceRequest.Builder();
        builder.setInitConversions(GeofenceRequest.ENTER_INIT_CONVERSION);
        builder.createGeofenceList(geofences);
        return builder.build();
    }

    private PendingIntent geofencePendingIntent() {
        if (geofencePendingIntent == null) {
            Intent intent = new Intent(context, GeofenceTransitionsReceiver.class);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags = flags | PendingIntent.FLAG_IMMUTABLE;
            }
            geofencePendingIntent = PendingIntent.getBroadcast(context, 0, intent, flags);
        }

        return geofencePendingIntent;
    }
}
