package org.infobip.mobile.messaging.mobileapi.common.exceptions;

import org.infobip.mobile.messaging.api.support.ApiBackendExceptionWithContent;

/**
 * @author sslavin
 * @since 17/10/2017.
 */

public class BackendBaseExceptionWithContent extends BackendBaseException {

    private final Object content;

    public BackendBaseExceptionWithContent(String message, ApiBackendExceptionWithContent cause) {
        super(message, cause);
        this.content = cause.getContent();
    }

    public Object getContent() {
        return content;
    }

    public <R> R getContent(Class<R> cls) {
        if (!cls.isInstance(content)) {
            return null;
        }
        //noinspection unchecked
        return (R) content;
    }
}
