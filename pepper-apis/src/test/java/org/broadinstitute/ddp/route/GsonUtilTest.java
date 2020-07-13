package org.broadinstitute.ddp.route;

import static org.junit.Assert.assertEquals;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.transformers.Exclude;
import org.broadinstitute.ddp.util.GsonUtil;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

public class GsonUtilTest {
    public static class DummyTestClass {
        private String first;
        private String last;
        @Exclude
        private String middle;

        DummyTestClass(String first, String last, String middle) {
            this.first = first;
            this.last = last;
            this.middle = middle;
        }

    }
    @Test
    public void testUseWithoutExclusionStrategy() throws JSONException {
        DummyTestClass dummyObject = new DummyTestClass("Ulysses", "Grant", "S");
        String jsonString = new Gson().toJson(dummyObject);
        JSONObject jsonObject = new JSONObject(jsonString);
        assertEquals("Ulysses", jsonObject.getString("first"));
        assertEquals("Grant", jsonObject.getString("last"));
        assertEquals("S", jsonObject.getString("middle"));
    }

    @Test
    public void testUseWithExclusionStrategy() throws JSONException {
        DummyTestClass dummyObject = new DummyTestClass("Ulysses", "Grant", "S");
        Gson gson = new GsonBuilder().setExclusionStrategies(new GsonUtil.ExcludeAnnotationStrategy()).create();
        String jsonString = gson.toJson(dummyObject);
        JSONObject jsonObject = new JSONObject(jsonString);
        assertEquals("Ulysses", jsonObject.getString("first"));
        assertEquals("Grant", jsonObject.getString("last"));
        assertEquals("NOTHINGHERE", jsonObject.optString("middle", "NOTHINGHERE"));
    }

    @Test
    public void testPepperGsonStandard() throws JSONException {
        DummyTestClass dummyObject = new DummyTestClass("Ulysses", "Grant", "S");
        Gson gson = GsonUtil.standardGson();
        String jsonString = gson.toJson(dummyObject);
        JSONObject jsonObject = new JSONObject(jsonString);
        assertEquals("Ulysses", jsonObject.getString("first"));
        assertEquals("Grant", jsonObject.getString("last"));
        assertEquals("NOTHINGHERE", jsonObject.optString("middle", "NOTHINGHERE"));
    }
}
