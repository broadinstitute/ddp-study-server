package org.broadinstitute.ddp.transformers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.time.LocalDateTime;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.Test;

public class LocalDateTimeAdapterTest {
    Gson gson = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .create();

    public static class TestClassWithLocalDateTime {
        String identifier;
        LocalDateTime testDateTime;
    }

    @Test
    public void testReadDate() {
        TestClassWithLocalDateTime testObj = new TestClassWithLocalDateTime();
        testObj.identifier = "theId";
        testObj.testDateTime = LocalDateTime.of(1969, 7, 20, 5, 30, 6);

        String jsonString = gson.toJson(testObj);
        assertTrue(jsonString.contains("\"1969-07-20T05:30:06"));
    }

    @Test
    public void testWriteDate() {
        TestClassWithLocalDateTime testObj = gson.fromJson("{\"identifier\":\"theId\",\"testDateTime\":\"1969-07-20T05:30:00Z\"}",
                TestClassWithLocalDateTime.class);
        assertNotNull(testObj.testDateTime);
        assertNotNull(testObj.identifier);
        assertEquals(LocalDateTime.of(1969, 7, 20, 5, 30), testObj.testDateTime);
    }
}
