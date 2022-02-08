package org.broadinstitute.ddp.route;

import static org.junit.Assert.assertEquals;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.broadinstitute.ddp.transformers.Exclude;
import org.broadinstitute.ddp.util.GsonUtil;

import org.junit.Assert;
import org.junit.Test;

public class GsonUtilTest {

    private JsonParser jsonParser = new JsonParser();

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
    public void testUseWithoutExclusionStrategy() {
        DummyTestClass dummyObject = new DummyTestClass("Ulysses", "Grant", "S");
        String jsonString = new Gson().toJson(dummyObject);

        JsonObject jsonObject = jsonParser.parse(jsonString).getAsJsonObject();
        assertEquals("Ulysses", jsonObject.get("first").getAsString());
        assertEquals("Grant", jsonObject.get("last").getAsString());
        assertEquals("S", jsonObject.get("middle").getAsString());
    }

    @Test
    public void testUseWithExclusionStrategy() {
        DummyTestClass dummyObject = new DummyTestClass("Ulysses", "Grant", "S");
        Gson gson = new GsonBuilder().setExclusionStrategies(new GsonUtil.ExcludeAnnotationStrategy()).create();
        String jsonString = gson.toJson(dummyObject);
        JsonObject jsonObject = jsonParser.parse(jsonString).getAsJsonObject();
        assertEquals("Ulysses", jsonObject.get("first").getAsString());
        assertEquals("Grant", jsonObject.get("last").getAsString());
        Assert.assertNull(jsonObject.get("middle"));
    }

    @Test
    public void testPepperGsonStandard()  {
        DummyTestClass dummyObject = new DummyTestClass("Ulysses", "Grant", "S");
        Gson gson = GsonUtil.standardGson();
        String jsonString = gson.toJson(dummyObject);
        JsonObject jsonObject = jsonParser.parse(jsonString).getAsJsonObject();
        assertEquals("Ulysses", jsonObject.get("first").getAsString());
        assertEquals("Grant", jsonObject.get("last").getAsString());
        Assert.assertNull(jsonObject.get("middle"));
    }
}
