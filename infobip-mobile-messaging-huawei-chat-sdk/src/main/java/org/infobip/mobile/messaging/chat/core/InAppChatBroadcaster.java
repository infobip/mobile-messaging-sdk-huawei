package org.infobip.mobile.messaging.chat.core;

public interface InAppChatBroadcaster {

    /**
     * Sends broadcast that In-app chat widget configuration is synced
     */
    void chatConfigurationSynced();

    /**
     * Called whenever a new chat push message arrives, contains current unread message counter value
     * @param unreadMessagesCount new unread message count
     */
    void unreadMessagesCounterUpdated(int unreadMessagesCount);

    /**
     * Sends broadcast when In-app chat widget view is changed
     */
    void chatViewChanged(InAppChatWidgetView view);

    /**
     * Sends broadcast with new In-app chat's availability
     * @param isChatAvailable true if In-app chat is ready to be presented to the user, false otherwise
     */
    void chatAvailabilityUpdated(boolean isChatAvailable);
}
