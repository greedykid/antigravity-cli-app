package com.greedykid.codexremote;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import org.json.JSONArray;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class PromptLibraryTest {
    @Test
    public void addAndDeletePrompt() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        SharedPreferences prefs = context.getSharedPreferences("test", Context.MODE_PRIVATE);
        PromptLibrary library = new PromptLibrary(prefs);

        library.add("Refactor", "Please refactor the module");
        JSONArray all = library.all();
        assertEquals(1, all.length());
        assertEquals("Refactor", all.getJSONObject(0).getString("title"));

        library.delete(all.getJSONObject(0).getString("id"));
        assertTrue(library.all().length() == 0);
    }
}
