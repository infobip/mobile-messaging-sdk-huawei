package org.infobip.mobile.messaging.chat.core

import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import org.infobip.mobile.messaging.chat.BuildConfig
import org.infobip.mobile.messaging.logging.MobileMessagingLogger

internal class InAppChatWebChromeClient : WebChromeClient() {

    init {
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
    }

    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
        if (BuildConfig.DEBUG)
            MobileMessagingLogger.d("InAppChatWebChromeClient", consoleMessage.format())
        return true
    }

    private fun ConsoleMessage.format(): String = "${lineNumber()}   ${sourceId()}   ${message()}"

}