package org.broadinstitute.ddp.model.activity.definition.validation;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.time.LocalDate;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.broadinstitute.ddp.model.activity.types.RuleType;
import org.broadinstitute.ddp.transformers.LocalDateAdapter;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class RuleDefTest {

    private static Gson gson;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @BeforeClass
    public static void setup() {
        gson = new GsonBuilder().serializeNulls()
                .registerTypeAdapter(RuleDef.class, new RuleDef.Deserializer())
                .registerTypeAdapter(LocalDate.class, new LocalDateAdapter().nullSafe())
                .create();
    }

    @Test
    public void testDeserialize_missingQuestionType() {
        thrown.expect(JsonParseException.class);
        thrown.expectMessage(containsString("rule type"));

        String json = "{}";
        gson.fromJson(json, RuleDef.class);
    }

    @Test
    public void testDeserialize_complete() {
        String json = "{\"ruleType\":\"COMPLETE\"}";

        RuleDef actual = gson.fromJson(json, RuleDef.class);
        assertNotNull(actual);
        assertEquals(RuleType.COMPLETE, actual.getRuleType());
    }

    @Test
    public void testDeserialize_dateFieldRequired() {
        String json = "{\"ruleType\":\"YEAR_REQUIRED\"}";

        RuleDef actual = gson.fromJson(json, RuleDef.class);
        assertNotNull(actual);
        assertEquals(RuleType.YEAR_REQUIRED, actual.getRuleType());
    }

    @Test
    public void testDeserialize_dateRange() {
        String json = "{\"ruleType\":\"DATE_RANGE\",\"startDate\":\"2018-03-14\"}";

        RuleDef actual = gson.fromJson(json, RuleDef.class);
        assertNotNull(actual);
        assertEquals(RuleType.DATE_RANGE, actual.getRuleType());
        assertNotNull(((DateRangeRuleDef) actual).getStartDate());
        assertEquals(2018, ((DateRangeRuleDef) actual).getStartDate().getYear());
        assertEquals(3, ((DateRangeRuleDef) actual).getStartDate().getMonthValue());
        assertEquals(14, ((DateRangeRuleDef) actual).getStartDate().getDayOfMonth());
    }

    @Test
    public void testDeserialize_length() {
        String json = "{\"ruleType\":\"LENGTH\",\"minLength\":10}";

        RuleDef actual = gson.fromJson(json, RuleDef.class);
        assertNotNull(actual);
        assertEquals(RuleType.LENGTH, actual.getRuleType());
        assertEquals((Integer) 10, ((LengthRuleDef) actual).getMin());
    }

    @Test
    public void testDeserialize_numOptionsSelected() {
        String json = "{\"ruleType\":\"NUM_OPTIONS_SELECTED\",\"minSelections\":10}";

        RuleDef actual = gson.fromJson(json, RuleDef.class);
        assertNotNull(actual);
        assertEquals(RuleType.NUM_OPTIONS_SELECTED, actual.getRuleType());
        assertEquals((Integer) 10, ((NumOptionsSelectedRuleDef) actual).getMin());
    }

    @Test
    public void testDeserialize_regex() {
        String json = "{\"ruleType\":\"REGEX\",\"pattern\":\"abc\"}";

        RuleDef actual = gson.fromJson(json, RuleDef.class);
        assertNotNull(actual);
        assertEquals(RuleType.REGEX, actual.getRuleType());
        assertEquals("abc", ((RegexRuleDef) actual).getPattern());
    }

    @Test
    public void testDeserialize_required() {
        String json = "{\"ruleType\":\"REQUIRED\"}";

        RuleDef actual = gson.fromJson(json, RuleDef.class);
        assertNotNull(actual);
        assertEquals(RuleType.REQUIRED, actual.getRuleType());
    }
}
