/*
 * MobileApiInboxHuaweiTransportTest.java
 * Mobile Messaging SDK
 *
 * Copyright (c) 2016-2025 Infobip Limited
 * Licensed under the Apache License, Version 2.0
 */
package org.infobip.mobile.messaging.inbox;

import org.infobip.mobile.messaging.api.inbox.FetchInboxResponse;
import org.infobip.mobile.messaging.api.inbox.MobileApiInbox;
import org.infobip.mobile.messaging.api.support.ApiException;
import org.infobip.mobile.messaging.api.support.Generator;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import static org.junit.Assert.*;

/** Verifies the published shared API dependency, not a copy of Android's transport. */
public class MobileApiInboxHuaweiTransportTest {
    private MockWebServer server;
    private MobileApiInbox api;

    @Before
    public void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        api = new Generator.Builder().withBaseUrl(server.url("/").toString()).build().create(MobileApiInbox.class);
    }

    @After
    public void tearDown() throws Exception {
        if (server != null) {
            server.shutdown();
        }
    }

    @Test
    public void hmsPaginationPreservesRawIdsAndOpaqueCursors() throws Exception {
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
                .setBody("{\"messages\":[],\"countTotal\":0,\"countUnread\":0,\"nextCursor\":\"next+/=\"}"));
        String userId = "huawei;user/a b%2F";
        String cursor = " opaque+/=& cursor ";
        FetchInboxResponse inbox = api.fetchInboxPage(userId, "Bearer jwt", "100", "200", "Offers", 15, cursor, "hms");

        RecordedRequest request = nextRequest();
        URI uri = URI.create(request.getPath());
        assertEquals("GET", request.getMethod());
        assertEquals("/mobile/1/user/huawei%3Buser%2Fa%20b%252F/inbox/hms/messages", uri.getRawPath());
        assertEquals("/mobile/1/user/" + userId + "/inbox/hms/messages", uri.getPath());
        assertEquals("Bearer jwt", request.getHeader("Authorization"));
        assertEquals(Arrays.asList(cursor), queryValues(uri, "cursor"));
        assertEquals(Arrays.asList("15"), queryValues(uri, "limit"));
        assertEquals(Arrays.asList("Offers"), queryValues(uri, "messageTopic"));
        assertEquals("next+/=", inbox.getNextCursor());
    }

    @Test
    public void deletionUsesCloudIndependentEndpointAndRepeatedIds() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(204));
        api.deleteInboxMessages("huawei;user", "App code", new String[]{"first+/=", "second & id"});

        RecordedRequest request = nextRequest();
        URI uri = URI.create(request.getPath());
        assertEquals("DELETE", request.getMethod());
        assertEquals("/mobile/1/user/huawei%3Buser/inbox/messages", uri.getRawPath());
        assertEquals("App code", request.getHeader("Authorization"));
        assertEquals(Arrays.asList("first+/=", "second & id"), queryValues(uri, "messageId"));
    }

    @Test
    public void hmsFetchPreservesChunkedServiceErrorAndHttpStatus() {
        server.enqueue(new MockResponse().setResponseCode(400).setHeader("Content-Type", "application/json")
                .setChunkedBody("{\"requestError\":{\"serviceException\":{\"messageId\":\"INVALID_CURSOR\",\"text\":\"Cursor does not match filters\"}}}", 8));
        try {
            api.fetchInboxPage("user", "Bearer jwt", null, null, null, 15, "cursor", "hms");
            fail("Expected INVALID_CURSOR");
        } catch (ApiException error) {
            assertEquals("INVALID_CURSOR", error.getCode());
            assertEquals("Cursor does not match filters", error.getMessage());
            assertEquals(Integer.valueOf(400), error.getHttpStatusCode());
        }
    }

    private RecordedRequest nextRequest() throws Exception {
        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request);
        return request;
    }

    private List<String> queryValues(URI uri, String key) {
        List<String> values = new ArrayList<>();
        if (uri.getRawQuery() != null) {
            for (String parameter : uri.getRawQuery().split("&")) {
                String[] parts = parameter.split("=", 2);
                if (key.equals(URLDecoder.decode(parts[0], StandardCharsets.UTF_8))) {
                    values.add(URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
                }
            }
        }
        return values;
    }
}
