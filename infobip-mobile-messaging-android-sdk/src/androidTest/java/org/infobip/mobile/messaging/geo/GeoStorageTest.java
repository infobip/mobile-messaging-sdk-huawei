package org.infobip.mobile.messaging.geo;

import android.content.Context;
import android.content.Intent;
import android.test.InstrumentationTestCase;

import org.infobip.mobile.messaging.Message;
import org.infobip.mobile.messaging.MobileMessagingCore;
import org.infobip.mobile.messaging.MobileMessagingProperty;
import org.infobip.mobile.messaging.api.support.http.serialization.JsonSerializer;
import org.infobip.mobile.messaging.dal.bundle.BundleMessageMapper;
import org.infobip.mobile.messaging.gcm.MobileMessageHandler;
import org.infobip.mobile.messaging.storage.MessageStore;
import org.infobip.mobile.messaging.storage.SQLiteMessageStore;
import org.infobip.mobile.messaging.util.PreferenceHelper;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * @author sslavin
 * @since 13/02/2017.
 */

public class GeoStorageTest extends InstrumentationTestCase {

    private Context context;
    private MessageStore geoStore;
    private MessageStore commonStore;
    private MobileMessageHandler handler;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        PreferenceHelper.saveString(getInstrumentation().getContext(), MobileMessagingProperty.MESSAGE_STORE_CLASS, SQLiteMessageStore.class.getName());

        context = getInstrumentation().getContext();
        handler = new MobileMessageHandler();
        geoStore = MobileMessagingCore.getInstance(context).getMessageStoreForGeo();
        geoStore.deleteAll(context);
        commonStore = MobileMessagingCore.getInstance(context).getMessageStore();
        commonStore.deleteAll(context);
    }

    public void test_shouldSaveGeoMessagesToGeoStore() throws Exception {

        // Given
        Geo geo = new Geo(0.0, 0.0, new ArrayList<Area>(){{add(new Area("SomeAreaId", null, 0.0, 0.0, 1));}}, null, new ArrayList<GeoEvent>(), null, null, "SomeCampaignId");
        JSONObject internalData = new JSONObject(new JsonSerializer().serialize(geo));
        Message message = new Message();
        message.setMessageId("SomeMessageId");
        message.setInternalData(internalData);
        message.setGeo(geo);
        Intent intent = new Intent();
        intent.putExtras(BundleMessageMapper.toBundle(message));

        // When
        handler.handleMessage(context, intent);

        // Then
        List<Message> messages = geoStore.findAll(context);
        assertEquals(1, messages.size());
        assertEquals("SomeMessageId", messages.get(0).getMessageId());
        assertEquals("SomeCampaignId", messages.get(0).getGeo().getCampaignId());
        assertEquals("SomeAreaId", messages.get(0).getGeo().getAreasList().get(0).getId());
        assertEquals(0, commonStore.countAll(context));
    }

    public void test_shouldSaveNonGeoMessagesToCommonStore() throws Exception {
        // Given
        Message message = new Message();
        message.setMessageId("SomeMessageId");
        Intent intent = new Intent();
        intent.putExtras(BundleMessageMapper.toBundle(message));

        // When
        handler.handleMessage(context, intent);

        // Then
        List<Message> messages = commonStore.findAll(context);
        assertEquals(1, messages.size());
        assertEquals("SomeMessageId", messages.get(0).getMessageId());
        assertEquals(0, geoStore.countAll(context));
    }

    public void test_shouldSaveMessagesToCorrespondingSeparateStores() throws Exception {
        // Given
        Message message1 = new Message();
        message1.setMessageId("SomeMessageId1");
        Intent intent1 = new Intent();
        intent1.putExtras(BundleMessageMapper.toBundle(message1));
        Geo geo = new Geo(0.0, 0.0, new ArrayList<Area>(){{add(new Area("SomeAreaId1", null, 0.0, 0.0, 1));}}, null, new ArrayList<GeoEvent>(), null, null, "SomeCampaignId2");
        JSONObject internalData = new JSONObject(new JsonSerializer().serialize(geo));
        Message message2 = new Message();
        message2.setMessageId("SomeMessageId2");
        message2.setInternalData(internalData);
        message2.setGeo(geo);
        Intent intent2 = new Intent();
        intent2.putExtras(BundleMessageMapper.toBundle(message2));

        // When
        handler.handleMessage(context, intent1);
        handler.handleMessage(context, intent2);

        // Then
        List<Message> messages = commonStore.findAll(context);
        assertEquals(1, messages.size());
        assertEquals("SomeMessageId1", messages.get(0).getMessageId());
        messages = geoStore.findAll(context);
        assertEquals(1, messages.size());
        assertEquals("SomeMessageId2", messages.get(0).getMessageId());
        assertEquals("SomeCampaignId2", messages.get(0).getGeo().getCampaignId());
        assertEquals("SomeAreaId1", messages.get(0).getGeo().getAreasList().get(0).getId());
    }
}