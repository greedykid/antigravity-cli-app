package com.greedykid.codexremote;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
public class TranscriptCacheTest {
    @Test
    public void putAndGetRoundTrip() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        TranscriptCache cache = new TranscriptCache(context);
        JSONObject payload = new JSONObject().put("conversationId", "abc").put("turns", 2);
        cache.put("abc", payload);
        assertEquals(2, cache.get("abc").optInt("turns"));
        assertNull(cache.get("missing"));
    }
}
