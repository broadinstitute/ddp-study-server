package org.broadinstitute.ddp.export.collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.junit.Before;
import org.junit.Test;

public class TextQuestionFormatStrategyTest {

    private TextQuestionFormatStrategy fmt;

    @Before
    public void setup() {
        fmt = new TextQuestionFormatStrategy();
    }

    @Test
    public void testMappings() {
        TextQuestionDef def = TextQuestionDef.builder(TextInputType.TEXT, "sid", Template.text("")).build();
        Map<String, Object> actual = fmt.mappings(def);

        assertNotNull(actual);
        assertEquals(1, actual.size());
        assertTrue(actual.containsKey("sid"));
        assertEquals("text", ((Map) actual.get("sid")).get("type"));
    }

    @Test
    public void testHeaders() {
        TextQuestionDef def = TextQuestionDef.builder(TextInputType.TEXT, "sid", Template.text("")).build();
        List<String> actual = fmt.headers(def);

        assertNotNull(actual);
        assertEquals(1, actual.size());
        assertEquals("sid", actual.get(0));
    }

    @Test
    public void testCollect_nullAnswer() {
        Map<String, String> actual = fmt.collect(buildQuestion(), buildAnswer(null));

        assertNotNull(actual);
        assertEquals(1, actual.size());
        assertTrue(actual.containsKey("sid"));
        assertEquals("", actual.get("sid"));
    }

    @Test
    public void testCollect_text() {
        Map<String, String> actual = fmt.collect(buildQuestion(), buildAnswer("foobar"));
        assertEquals("foobar", actual.get("sid"));
    }

    @Test
    public void testCollect_containsDelimiters() {
        Map<String, String> actual = fmt.collect(buildQuestion(), buildAnswer("foo, bar.baz ; bob"));
        assertEquals("foo, bar.baz ; bob", actual.get("sid"));
    }

    private TextQuestionDef buildQuestion() {
        return TextQuestionDef.builder(TextInputType.TEXT, "sid", Template.text("")).build();
    }

    private TextAnswer buildAnswer(String value) {
        return new TextAnswer(1L, "sid", "abc", value);
    }
}
