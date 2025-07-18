package org.infobip.mobile.messaging.chat.core.widget

/**
 * Represents Livechat widget JS methods.
 * It must match with JS methods in livechat-widget.html.
 */
internal enum class LivechatWidgetMethod {
    config,
    identify,
    initWidget, //init is Kotlin reserved keyword so there is Widget postfix used
    show,
    pauseConnection,
    resumeConnection,
    sendMessage,
    sendContextualData,
    setTheme,
    setLanguage,
    createThread,
    getThreads,
    getActiveThread,
    showThread,
    showThreadList,
    openNewThread,
}