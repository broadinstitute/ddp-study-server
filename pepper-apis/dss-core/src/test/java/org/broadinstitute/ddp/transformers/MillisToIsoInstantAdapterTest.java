package org.broadinstitute.ddp.transformers;

import static org.hamcrest.Matchers.isA;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.format.DateTimeParseException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.JsonAdapter;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class MillisToIsoInstantAdapterTest {

    private static Gson gson = new GsonBuilder().serializeNulls().create();

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testSerialize() {
        long millis = 1555688897903L;
        Container item = new Container(millis);
        String actual = gson.toJson(item);

        assertNotNull(actual);
        assertTrue(actual.contains("2019-04-19T15:48:17.903Z"));
    }

    @Test
    public void testSerialize_null() {
        Container item = new Container(null);
        String actual = gson.toJson(item);

        assertNotNull(actual);
        assertTrue(actual.contains("null"));
    }

    @Test
    public void testDeserialize() {
        long expected = 1555688897903L;
        String input = "{\"millis\": \"2019-04-19T15:48:17.903Z\"}";
        Container actual = gson.fromJson(input, Container.class);

        assertNotNull(actual);
        assertNotNull(actual.millis);
        assertEquals(expected, (long) actual.millis);
    }

    @Test
    public void testDeserialize_null() {
        String input = "{\"millis\": null}";
        Container actual = gson.fromJson(input, Container.class);

        assertNotNull(actual);
        assertNull(actual.millis);
    }

    @Test
    public void testDeserialize_missing() {
        String input = "{}";
        Container actual = gson.fromJson(input, Container.class);

        assertNotNull(actual);
        assertNull(actual.millis);
    }

    @Test
    public void testDeserialize_malformed() {
        thrown.expect(JsonParseException.class);
        thrown.expectCause(isA(DateTimeParseException.class));

        String input = "{\"millis\":\"2018-3-14\"}";
        gson.fromJson(input, Container.class);
    }

    private static class Container {
        @JsonAdapter(MillisToIsoInstantAdapter.class)
        Long millis;

        Container(Long millis) {
            this.millis = millis;
        }
    }
}
