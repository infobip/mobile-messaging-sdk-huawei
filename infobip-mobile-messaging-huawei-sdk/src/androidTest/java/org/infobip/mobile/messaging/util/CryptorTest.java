package org.infobip.mobile.messaging.util;

import android.util.Base64;

import org.infobip.mobile.messaging.tools.MobileMessagingTestCase;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;

/**
 * @author sslavin
 * @since 29/08/16.
 */
public class CryptorTest extends MobileMessagingTestCase {

    @Test
    public void test_encryptDecrypt() throws Exception {

        String data = "thisIsMyTestData";
        String key = "thisIsMySuperSecretKey";
        String encrypted = new Cryptor(key).encrypt(data);
        String decrypted = new Cryptor(key).decrypt(encrypted);

        assertFalse(data.equals(encrypted));
        assertFalse(data.equals(new String(Base64.decode(encrypted, Base64.DEFAULT))));
        assertEquals(data, decrypted);
    }
}