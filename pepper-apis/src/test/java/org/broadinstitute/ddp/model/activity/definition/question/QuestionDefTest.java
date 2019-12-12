package org.broadinstitute.ddp.model.activity.definition.question;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class QuestionDefTest {

    private static Gson gson;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @BeforeClass
    public static void setup() {
        gson = new GsonBuilder().serializeNulls()
                .registerTypeAdapter(QuestionDef.class, new QuestionDef.Deserializer())
                .create();
    }

    @Test
    public void testDeserialize_missingQuestionType() {
        thrown.expect(JsonParseException.class);
        thrown.expectMessage(containsString("question type"));

        String json = "{\"stableId\":\"abc\"}";
        gson.fromJson(json, QuestionDef.class);
    }

    @Test
    public void testDeserialize_agreement() {
        String json = "{\"questionType\":\"AGREEMENT\",\"stableId\":\"abc\"}";

        QuestionDef actual = gson.fromJson(json, QuestionDef.class);
        assertNotNull(actual);
        assertEquals(QuestionType.AGREEMENT, actual.getQuestionType());
        assertEquals("abc", actual.getStableId());
        assertTrue(actual instanceof AgreementQuestionDef);
    }

    @Test
    public void testDeserialize_bool() {
        String json = "{\"questionType\":\"BOOLEAN\",\"stableId\":\"abc\"}";

        QuestionDef actual = gson.fromJson(json, QuestionDef.class);
        assertNotNull(actual);
        assertEquals(QuestionType.BOOLEAN, actual.getQuestionType());
        assertEquals("abc", actual.getStableId());
        assertTrue(actual instanceof BoolQuestionDef);
    }

    @Test
    public void testDeserialize_text() {
        String json = "{\"questionType\":\"TEXT\",\"stableId\":\"abc\"}";

        QuestionDef actual = gson.fromJson(json, QuestionDef.class);
        assertNotNull(actual);
        assertEquals(QuestionType.TEXT, actual.getQuestionType());
        assertEquals("abc", actual.getStableId());
        assertTrue(actual instanceof TextQuestionDef);
    }

    @Test
    public void testDeserialize_date() {
        String json = "{\"questionType\":\"DATE\",\"stableId\":\"abc\"}";

        QuestionDef actual = gson.fromJson(json, QuestionDef.class);
        assertNotNull(actual);
        assertEquals(QuestionType.DATE, actual.getQuestionType());
        assertEquals("abc", actual.getStableId());
        assertTrue(actual instanceof DateQuestionDef);
    }

    @Test
    public void testDeserialize_picklist() {
        String json = "{\"questionType\":\"PICKLIST\",\"stableId\":\"abc\"}";

        QuestionDef actual = gson.fromJson(json, QuestionDef.class);
        assertNotNull(actual);
        assertEquals(QuestionType.PICKLIST, actual.getQuestionType());
        assertEquals("abc", actual.getStableId());
        assertTrue(actual instanceof PicklistQuestionDef);
    }

    @Test
    public void testDeserialize_composite() {
        String json = "{\"questionType\":\"COMPOSITE\",\"stableId\":\"abc\"}";

        QuestionDef actual = gson.fromJson(json, QuestionDef.class);
        assertNotNull(actual);
        assertEquals(QuestionType.COMPOSITE, actual.getQuestionType());
        assertEquals("abc", actual.getStableId());
        assertTrue(actual instanceof CompositeQuestionDef);
    }
}
