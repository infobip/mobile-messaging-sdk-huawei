package org.infobip.mobile.messaging.geo.report;


import org.infobip.mobile.messaging.api.geo.EventReportResponse;
import org.infobip.mobile.messaging.mobileapi.UnsuccessfulResult;

import java.util.Map;
import java.util.Set;

public class GeoReportingResult extends UnsuccessfulResult {

    private final Set<String> finishedCampaignIds;
    private final Set<String> suspendedCampaignIds;

    /**
     * key = sdkMessageId
     * value = realMessageId
     */
    private final Map<String, String> messageIds;

    public GeoReportingResult(Throwable exception) {
        super(exception);
        finishedCampaignIds = null;
        suspendedCampaignIds = null;
        messageIds = null;
    }

    public GeoReportingResult(EventReportResponse eventReportResponse) {
        super(null);
        this.finishedCampaignIds = eventReportResponse.getFinishedCampaignIds();
        this.suspendedCampaignIds = eventReportResponse.getSuspendedCampaignIds();
        this.messageIds = eventReportResponse.getMessageIds();
    }

    public Set<String> getFinishedCampaignIds() {
        return finishedCampaignIds;
    }

    public Set<String> getSuspendedCampaignIds() {
        return suspendedCampaignIds;
    }

    public Map<String, String> getMessageIds() {
        return messageIds;
    }
}
