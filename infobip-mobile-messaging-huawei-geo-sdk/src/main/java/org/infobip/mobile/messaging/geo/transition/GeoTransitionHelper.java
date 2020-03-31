package org.infobip.mobile.messaging.geo.transition;

import android.content.Intent;
import android.location.Location;
import android.support.v4.util.ArraySet;
import android.util.SparseArray;

import com.huawei.hms.location.Geofence;
import com.huawei.hms.location.GeofenceData;
import com.huawei.hms.location.GeofenceErrorCodes;
import com.huawei.hms.support.api.entity.location.fence.GeofenceEntity;

import org.infobip.mobile.messaging.geo.GeoEventType;
import org.infobip.mobile.messaging.geo.GeoLatLng;

import java.util.Set;

/**
 * @author sslavin
 * @since 08/02/2017.
 */

class GeoTransitionHelper {

    static class GeofenceNotAvailableException extends RuntimeException {
    }

    /**
     * Supported geofence transition events
     */
    private static final SparseArray<GeoEventType> supportedTransitionEvents = new SparseArray<GeoEventType>() {{
        put(Geofence.ENTER_GEOFENCE_CONVERSION, GeoEventType.entry);
    }};

    /**
     * Resolves transition information from geofencing intent
     *
     * @param intent geofencing intent
     * @return transition information
     * @throws RuntimeException if information cannot be resolved
     */
    static GeoTransition resolveTransitionFromIntent(Intent intent) throws RuntimeException {
        GeofenceData geofenceData = GeofenceData.getDataFromIntent(intent);
        if (geofenceData == null) {
            throw new RuntimeException("Geofencing event is null, cannot process");
        }

        if (geofenceData.isFailure()) {
            if (geofenceData.getErrorCode() == GeofenceErrorCodes.GEOFENCE_UNAVAILABLE) {
                throw new GeofenceNotAvailableException();
            }
            throw new RuntimeException("ERROR: " + GeofenceErrorCodes.getStatusCodeString(geofenceData.getErrorCode()));
        }

        GeoEventType event = supportedTransitionEvents.get(geofenceData.getConversion()); // TODO: Double-check is it same as getGeofenceTransition
        if (event == null) {
            throw new RuntimeException("Transition is not supported: " + geofenceData.getConversion());
        }

        Set<String> triggeringRequestIds = new ArraySet<>();
        for (Geofence geofence : geofenceData.getConvertingGeofenceList()) {
            triggeringRequestIds.add(geofence.getUniqueId());
        }

        Location location = geofenceData.getConvertingLocation();
        return new GeoTransition(event, triggeringRequestIds, new GeoLatLng(location.getLatitude(), location.getLongitude()));
    }
}
