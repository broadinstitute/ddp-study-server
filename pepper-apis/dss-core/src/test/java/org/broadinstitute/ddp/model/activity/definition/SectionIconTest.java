package org.broadinstitute.ddp.model.activity.definition;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.net.URL;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.broadinstitute.ddp.model.activity.types.FormSectionState;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class SectionIconTest {

    private static Gson gson;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @BeforeClass
    public static void setup() {
        gson = new GsonBuilder().serializeNulls()
                .registerTypeAdapter(SectionIcon.class, new SectionIcon.Serializer())
                .registerTypeAdapter(SectionIcon.class, new SectionIcon.Deserializer())
                .create();
    }

    @Test
    public void testSerialize() throws Exception {
        SectionIcon icon = new SectionIcon(FormSectionState.COMPLETE, 10, 20);
        icon.putSource("1x", new URL("http://localhost/icon.png"));

        String actual = gson.toJson(icon);
        assertNotNull(actual);
        assertEquals("{\"state\":\"COMPLETE\",\"height\":10,\"width\":20,\"1x\":\"http://localhost/icon.png\"}", actual);
    }

    @Test
    public void testDeserialize_missingState() {
        thrown.expect(JsonParseException.class);
        thrown.expectMessage(containsString("state"));

        String json = "{\"height\":10,\"width\":20,\"1x\":\"http://localhost/icon.png\"}";
        gson.fromJson(json, SectionIcon.class);
    }

    @Test
    public void testDeserialize_missingHeight() {
        thrown.expect(JsonParseException.class);
        thrown.expectMessage(containsString("height"));

        String json = "{\"state\":\"COMPLETE\",\"width\":20,\"1x\":\"http://localhost/icon.png\"}";
        gson.fromJson(json, SectionIcon.class);
    }

    @Test
    public void testDeserialize_missingWidth() {
        thrown.expect(JsonParseException.class);
        thrown.expectMessage(containsString("width"));

        String json = "{\"state\":\"COMPLETE\",\"height\":10,\"1x\":\"http://localhost/icon.png\"}";
        gson.fromJson(json, SectionIcon.class);
    }

    @Test
    public void testDeserialize_missingUrl() {
        thrown.expect(JsonParseException.class);
        thrown.expectMessage(containsString("required scale factor 1x"));

        String json = "{\"state\":\"COMPLETE\",\"height\":10,\"width\":20}";
        gson.fromJson(json, SectionIcon.class);
    }

    @Test
    public void testDeserialize() throws Exception {
        SectionIcon icon = new SectionIcon(FormSectionState.COMPLETE, 10, 20);
        icon.putSource("1x", new URL("http://localhost/icon.png"));

        String json = gson.toJson(icon);
        SectionIcon actual = gson.fromJson(json, SectionIcon.class);
        assertNotNull(actual);

        assertEquals(icon.getState(), actual.getState());
        assertEquals(icon.getHeight(), actual.getHeight());
        assertEquals(icon.getWidth(), actual.getWidth());
        assertEquals(icon.getSources().size(), actual.getSources().size());
        assertEquals(icon.getSources().get("1x"), actual.getSources().get("1x"));
    }

    @Test
    public void testDeserialize_multipleScales() throws Exception {
        SectionIcon icon = new SectionIcon(FormSectionState.COMPLETE, 10, 20);
        icon.putSource("1x", new URL("http://localhost/1x.png"));
        icon.putSource("2x", new URL("http://localhost/2x.png"));

        String json = gson.toJson(icon);
        SectionIcon actual = gson.fromJson(json, SectionIcon.class);
        assertNotNull(actual);

        assertEquals(icon.getSources().size(), actual.getSources().size());
        assertEquals(icon.getSources().get("1x"), actual.getSources().get("1x"));
        assertEquals(icon.getSources().get("2x"), actual.getSources().get("2x"));
    }
}
