package org.broadinstitute.ddp.transformers;

import static org.hamcrest.Matchers.isA;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class LocalDateAdapterTest {

    private static Gson gson;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @BeforeClass
    public static void setup() {
        gson = new GsonBuilder()
                .registerTypeAdapter(LocalDate.class, new LocalDateAdapter().nullSafe())
                .serializeNulls()
                .create();
    }

    @Test
    public void testSerialize() {
        TestContainer data = new TestContainer(LocalDate.of(2018, 3, 14));
        String json = gson.toJson(data);
        assertEquals("{\"date\":\"2018-03-14\"}", json);
    }

    @Test
    public void testSerialize_null() {
        TestContainer data = new TestContainer(null);
        String json = gson.toJson(data);
        assertEquals("{\"date\":null}", json);
    }

    @Test
    public void testDeserialize() {
        String json = "{\"date\":\"2018-03-14\"}";
        TestContainer data = gson.fromJson(json, TestContainer.class);
        assertNotNull(data);
        assertNotNull(data.date);
        assertEquals(data.date.toString(), "2018-03-14");
    }

    @Test
    public void testDeserialize_null() {
        String json = "{\"date\":null}";
        TestContainer data = gson.fromJson(json, TestContainer.class);
        assertNotNull(data);
        assertNull(data.date);
    }

    @Test
    public void testDeserialize_invalidFormat() {
        thrown.expect(JsonParseException.class);
        thrown.expectCause(isA(DateTimeParseException.class));
        String json = "{\"date\":\"2018-3-5\"}";
        gson.fromJson(json, TestContainer.class);
    }

    class TestContainer {
        public LocalDate date;

        TestContainer(LocalDate date) {
            this.date = date;
        }
    }
}
