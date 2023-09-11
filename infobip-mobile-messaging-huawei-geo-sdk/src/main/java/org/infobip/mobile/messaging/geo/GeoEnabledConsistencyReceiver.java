package org.infobip.mobile.messaging.geo;

import static org.infobip.mobile.messaging.geo.GeofencingConsistencyReceiver.NETWORK_PROVIDER_ENABLED_ACTION;
import static org.infobip.mobile.messaging.geo.GeofencingConsistencyReceiver.scheduleConsistencyAlarm;

import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;

import androidx.annotation.VisibleForTesting;

import org.infobip.mobile.messaging.geo.geofencing.GeofencingHelper;
import org.infobip.mobile.messaging.logging.MobileMessagingLogger;
import org.infobip.mobile.messaging.platform.Time;
import org.infobip.mobile.messaging.util.StringUtils;

import java.util.Date;

/**
 * @author tjuric
 * @since 26/09/17.
 */

public class GeoEnabledConsistencyReceiver extends BroadcastReceiver {

    private GeofencingHelper geofencingHelper;

    public GeoEnabledConsistencyReceiver() {

    }

    @VisibleForTesting
    GeoEnabledConsistencyReceiver(GeofencingHelper geofencingHelper) {
        this.geofencingHelper = geofencingHelper;
    }

    @Override
    public void onReceive(final Context context, final Intent intent) {
        if (!GeofencingHelper.isGeoActivated(context)) {
            return;
        }

        final String action = intent.getAction();
        if (StringUtils.isBlank(action)) {
            return;
        }

        MobileMessagingLogger.i(String.format("[%s]", action));
        /*
         * This action gets called when GPS or network provider changes it's state. Redundancy in switching GPS only while network available.
         *
         * If the network provider is enabled, local alarm for adding geo areas is scheduled on GeofencingConsistencyReceiver class in 15 seconds.
         * This alarm is necessary because there is a lag in the time that the end user clicks on "Google location services" in settings and accepts
         * it's usage (pre-KitKat versions).
         *
         * From Google's documentation of #addGeofences method:
         * In case network location provider is disabled by the user, the geofence service will stop updating, all registered geofences will
         * be removed and an intent is generated by the provided pending intent.
         */
        if (LocationManager.PROVIDERS_CHANGED_ACTION.equals(action)) {
            if (geofencingHelper(context).isLocationEnabled(context)) {
                final Date triggerDate = new Date(Time.now() + 15 * 1000);
                scheduleConsistencyAlarm(context, AlarmManager.RTC, triggerDate, NETWORK_PROVIDER_ENABLED_ACTION, 0);
                geofencingHelper(context).startGeoMonitoringIfNecessary();
            } else {
                GeofencingHelper.setAllActiveGeoAreasMonitored(context, false);
            }
        }
    }

    public GeofencingHelper geofencingHelper(Context context) {
        if (geofencingHelper == null) {
            geofencingHelper = new GeofencingHelper(context);
        }
        return geofencingHelper;
    }
}
