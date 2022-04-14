package org.broadinstitute.ddp.model.activity.definition;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.broadinstitute.ddp.model.activity.types.BlockType;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class FormBlockDefTest {

    private static Gson gson;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @BeforeClass
    public static void setup() {
        gson = new GsonBuilder().serializeNulls()
                .registerTypeAdapter(FormBlockDef.class, new FormBlockDef.Deserializer())
                .create();
    }

    @Test
    public void testDeserialize_missingBlockType() {
        thrown.expect(JsonParseException.class);
        thrown.expectMessage(containsString("block type"));

        String json = "{\"blockGuid\": \"abc\"}";
        gson.fromJson(json, FormBlockDef.class);
    }

    @Test
    public void testDeserialize_contentBlock() {
        String json = "{\"blockType\":\"CONTENT\",\"shownExpr\":\"abc\"}";

        FormBlockDef actual = gson.fromJson(json, FormBlockDef.class);
        assertNotNull(actual);
        assertEquals(BlockType.CONTENT, actual.getBlockType());
        assertEquals("abc", actual.getShownExpr());
    }

    @Test
    public void testDeserialize_questionBlock() {
        String json = "{\"blockType\":\"QUESTION\",\"shownExpr\":\"abc\"}";

        FormBlockDef actual = gson.fromJson(json, FormBlockDef.class);
        assertNotNull(actual);
        assertEquals(BlockType.QUESTION, actual.getBlockType());
        assertEquals("abc", actual.getShownExpr());
    }

    @Test
    public void testDeserialize_conditionalBlock() {
        String json = "{\"blockType\":\"CONDITIONAL\",\"shownExpr\":\"abc\"}";

        FormBlockDef actual = gson.fromJson(json, FormBlockDef.class);
        assertNotNull(actual);
        assertEquals(BlockType.CONDITIONAL, actual.getBlockType());
        assertEquals("abc", actual.getShownExpr());
    }

    @Test
    public void testDeserialize_groupBlock() {
        String json = "{\"blockType\":\"GROUP\",\"shownExpr\":\"abc\"}";

        FormBlockDef actual = gson.fromJson(json, FormBlockDef.class);
        assertNotNull(actual);
        assertEquals(BlockType.GROUP, actual.getBlockType());
        assertEquals("abc", actual.getShownExpr());
    }
}
