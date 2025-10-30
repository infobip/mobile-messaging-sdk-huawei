/*
 * SqliteMessageMigrationTest.java
 * Mobile Messaging SDK
 *
 * Copyright (c) 2016-2025 Infobip Limited
 * Licensed under the Apache License, Version 2.0
 */
package org.infobip.mobile.messaging.dal.sqlite;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.infobip.mobile.messaging.Message;
import org.infobip.mobile.messaging.storage.SQLiteMessageStore;
import org.infobip.mobile.messaging.tools.MobileMessagingTestCase;
import org.junit.Test;
import org.skyscreamer.jsonassert.JSONAssert;

import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author sslavin
 * @since 02/02/2017.
 */

public class SqliteMessageMigrationTest extends MobileMessagingTestCase {

    private static final String SQL_CREATE_OLD_MESSAGES_TABLE = "CREATE TABLE " + DatabaseContract.Tables.MESSAGES + " (" +
            DatabaseContract.MessageColumns.MESSAGE_ID + " TEXT PRIMARY KEY NOT NULL ON CONFLICT FAIL, " +
            DatabaseContract.MessageColumns.TITLE + " TEXT, " +
            DatabaseContract.MessageColumns.BODY + " TEXT, " +
            DatabaseContract.MessageColumns.SOUND + " TEXT, " +
            DatabaseContract.MessageColumns.VIBRATE + " INTEGER NOT NULL DEFAULT 1, " +
            DatabaseContract.MessageColumns.ICON + " TEXT, " +
            DatabaseContract.MessageColumns.SILENT + " INTEGER NOT NULL DEFAULT 0, " +
            DatabaseContract.MessageColumns.CATEGORY + " TEXT, " +
            DatabaseContract.MessageColumns.FROM + " TEXT, " +
            DatabaseContract.MessageColumns.RECEIVED_TIMESTAMP + " INTEGER, " +
            DatabaseContract.MessageColumns.SEEN_TIMESTAMP + " INTEGER, " +
            DatabaseContract.MessageColumns.INTERNAL_DATA + " TEXT, " +
            DatabaseContract.MessageColumns.CUSTOM_PAYLOAD + " TEXT, " +
            DatabaseContract.MessageColumns.DESTINATION + " TEXT, " +
            DatabaseContract.MessageColumns.STATUS + " TEXT," +
            DatabaseContract.MessageColumns.STATUS_MESSAGE + " TEXT)";

    private static final String SQL_CREATE_MAY_MESSAGES_TABLE = "CREATE TABLE " + DatabaseContract.Tables.MESSAGES + " (" +
            DatabaseContract.MessageColumns.MESSAGE_ID + " TEXT PRIMARY KEY NOT NULL ON CONFLICT FAIL, " +
            DatabaseContract.MessageColumns.TITLE + " TEXT, " +
            DatabaseContract.MessageColumns.BODY + " TEXT, " +
            DatabaseContract.MessageColumns.SOUND + " TEXT, " +
            DatabaseContract.MessageColumns.VIBRATE + " INTEGER NOT NULL DEFAULT 1, " +
            DatabaseContract.MessageColumns.ICON + " TEXT, " +
            DatabaseContract.MessageColumns.SILENT + " INTEGER NOT NULL DEFAULT 0, " +
            DatabaseContract.MessageColumns.CATEGORY + " TEXT, " +
            DatabaseContract.MessageColumns.FROM + " TEXT, " +
            DatabaseContract.MessageColumns.RECEIVED_TIMESTAMP + " INTEGER, " +
            DatabaseContract.MessageColumns.SEEN_TIMESTAMP + " INTEGER, " +
            DatabaseContract.MessageColumns.INTERNAL_DATA + " TEXT, " +
            DatabaseContract.MessageColumns.CUSTOM_PAYLOAD + " TEXT, " +
            DatabaseContract.MessageColumns.DESTINATION + " TEXT, " +
            DatabaseContract.MessageColumns.STATUS + " TEXT," +
            DatabaseContract.MessageColumns.STATUS_MESSAGE + " TEXT," +
            DatabaseContract.MessageColumns.CONTENT_URL + " TEXT)";

    @Test
    public void test_shouldAddContentUrlColumnDuringMigration() throws Exception {
        // Create SQLiteOpenHelper directly to perform raw operations on database
        SQLiteOpenHelper sqLiteOpenHelper = new SQLiteOpenHelper(context, PushDatabaseHelperImpl.DATABASE_NAME, null, PushDatabaseHelperImpl.VER_2017_FEB_14) {
            @Override
            public void onCreate(SQLiteDatabase db) {
                db.execSQL(SQL_CREATE_OLD_MESSAGES_TABLE);
            }

            @Override
            public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

            }
        };

        SQLiteDatabase db = sqLiteOpenHelper.getWritableDatabase();
        db.close();
        sqLiteOpenHelper.close();

        // Check that content_url column exists
        SQLiteDatabase database = databaseProvider.getDatabase();
        Cursor cursor = database.rawQuery("SELECT * FROM messages", null);
        assertNotEquals(-1, cursor.getColumnIndex("content_url"));
        cursor.close();
    }

    @Test
    public void test_shouldAddSendDateTimeToInternalData() throws Exception {
        // Create SQLiteOpenHelper directly to perform raw operations on database
        context.deleteDatabase(PushDatabaseHelperImpl.DATABASE_NAME);
        SQLiteOpenHelper sqLiteOpenHelper = new SQLiteOpenHelper(context, PushDatabaseHelperImpl.DATABASE_NAME, null, PushDatabaseHelperImpl.VER_2017_MAY_15) {
            @Override
            public void onCreate(SQLiteDatabase db) {
                db.execSQL(SQL_CREATE_MAY_MESSAGES_TABLE);
            }

            @Override
            public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

            }
        };

        SQLiteDatabase db = sqLiteOpenHelper.getWritableDatabase();
        // Save new data to database
        ContentValues contentValues = new ContentValues();
        contentValues.put("id", "SomeMessageId");
        contentValues.put("title", "SomeMessageTitle");
        contentValues.put("body", "SomeMessageBody");
        contentValues.put("sound", "SomeMessageSound");
        contentValues.put("vibrate", 1);
        contentValues.put("icon", "SomeMessageIcon");
        contentValues.put("silent", 1);
        contentValues.put("category", "SomeMessageCategory");
        contentValues.put("_from", "SomeMessageFrom");
        contentValues.put("received_timestamp", 1234L);
        contentValues.put("seen_timestamp", 5678L);
        contentValues.put("internal_data", "{\"key1\":\"value1\"}");
        contentValues.put("custom_payload", "{\"key2\":\"value2\"}");
        contentValues.put("destination", "SomeMessageDestination");
        contentValues.put("status", "ERROR");
        contentValues.put("status_message", "SomeMessageStatusMessage");
        contentValues.put("content_url", "SomeMessageContentUrl");
        db.insertWithOnConflict(DatabaseContract.Tables.MESSAGES, null, contentValues, SQLiteDatabase.CONFLICT_REPLACE);
        db.close();
        sqLiteOpenHelper.close();

        // Check that sent timestamp was added and other fields are the same
        SQLiteMessageStore messageStore = new SQLiteMessageStore();
        Message message = messageStore.findById(context, "SomeMessageId");
        long messagesCnt = messageStore.countAll(context);
        assertEquals(messagesCnt, 1L);
        assertNotNull(message);
        assertEquals("SomeMessageId", message.getMessageId());
        assertEquals("SomeMessageTitle", message.getTitle());
        assertEquals("SomeMessageBody", message.getBody());
        assertEquals("SomeMessageSound", message.getSound());
        assertEquals(true, message.isVibrate());
        assertEquals("SomeMessageIcon", message.getIcon());
        assertEquals(true, message.isSilent());
        assertEquals("SomeMessageCategory", message.getCategory());
        assertEquals("SomeMessageFrom", message.getFrom());
        assertEquals(1234L, message.getReceivedTimestamp());
        assertEquals(5678L, message.getSeenTimestamp());
        assertEquals(1234L, message.getSentTimestamp());
        JSONAssert.assertEquals("{\"key1\" : \"value1\", \"sendDateTime\":1234}",
                message.getInternalData(), true);
        JSONAssert.assertEquals("{\"key2\" : \"value2\"}",
                message.getCustomPayload(), true);
        assertEquals("SomeMessageDestination", message.getDestination());
        assertEquals(Message.Status.ERROR, message.getStatus());
        assertEquals("SomeMessageStatusMessage", message.getStatusMessage());
        assertEquals("SomeMessageContentUrl", message.getContentUrl());
    }
}