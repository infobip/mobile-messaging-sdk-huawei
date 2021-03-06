package org.infobip.mobile.messaging.chat.core;

import org.infobip.mobile.messaging.chat.attachments.InAppChatMobileAttachment;
import org.infobip.mobile.messaging.chat.view.InAppChatWebView;
import org.infobip.mobile.messaging.logging.MobileMessagingLogger;
import org.infobip.mobile.messaging.util.StringUtils;

import static org.infobip.mobile.messaging.chat.core.InAppChatWidgetMethods.handleMessageDraftSend;
import static org.infobip.mobile.messaging.chat.core.InAppChatWidgetMethods.handleMessageSend;
import static org.infobip.mobile.messaging.chat.core.InAppChatWidgetMethods.handleMessageWithAttachmentSend;
import static org.infobip.mobile.messaging.chat.utils.CommonUtils.isOSOlderThanKitkat;
import static org.infobip.mobile.messaging.util.StringUtils.isNotBlank;

public class InAppChatClientImpl implements InAppChatClient {

    private final InAppChatWebView webView;

    public InAppChatClientImpl(InAppChatWebView webView) {
        this.webView = webView;
    }

    @Override
    public void sendChatMessage(String message) {
        if (canSendMessage(message)) {
            String script = buildWidgetMethodInvocation(handleMessageSend.name(), message);
            webView.evaluateJavascriptMethod(script, null);
        }
    }

    @Override
    public void sendChatMessage(String message, InAppChatMobileAttachment attachment) {
        String base64UrlString = attachment.base64UrlString();
        String fileName = attachment.getFileName();

        // message can be null - its OK
        if (canSendMessage(base64UrlString)) {
            String script = buildWidgetMethodInvocation(handleMessageWithAttachmentSend.name(), isOSOlderThanKitkat(), message, base64UrlString, fileName);
            webView.evaluateJavascriptMethod(script, null);
        } else {
            MobileMessagingLogger.e("[InAppChat] can't send attachment, base64 is empty");
        }
    }

    @Override
    public void sendInputDraft(String draft) {
        if (webView != null) {
            String script = buildWidgetMethodInvocation(handleMessageDraftSend.name(), isOSOlderThanKitkat(), draft);
            webView.evaluateJavascriptMethod(script, null);
        }
    }

    private boolean canSendMessage(String message) {
        return webView != null && isNotBlank(message);
    }

    private String buildWidgetMethodInvocation(String methodName, String... params) {
        return this.buildWidgetMethodInvocation(methodName, true, params);
    }

    private String buildWidgetMethodInvocation(String methodName, boolean withPrefix, String... params) {
        StringBuilder builder = new StringBuilder();
        if (withPrefix) {
            builder.append("javascript:");
        }
        builder.append(methodName);

        if (params.length > 0) {
            String resultParamsStr = StringUtils.join("','", "('", "')", params);
            builder.append(resultParamsStr);
        }
        return builder.toString();
    }
}