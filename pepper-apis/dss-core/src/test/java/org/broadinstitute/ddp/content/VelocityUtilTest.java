package org.broadinstitute.ddp.content;

import static org.broadinstitute.ddp.content.VelocityUtil.VARIABLE_SEP;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

/**
 * Tests methods of class {@link VelocityUtil}.
 */
public class VelocityUtilTest {

    private static final String VAR_PREFIX = "prequal";

    private static final String VAR1 = "var1";
    private static final String VAR2 = "var2";
    private static final String VAR1_VALUE = "testvar1";
    private static final String VAR2_VALUE = "testvar2";

    private static final String PREQUAL_VAR1 = VAR_PREFIX + VARIABLE_SEP + VAR1;
    private static final String PREQUAL_VAR2 = VAR_PREFIX + VARIABLE_SEP + VAR2;

    private static final String TEMPLATE_TEXT_1 =
            "Test template: $prequal.var1 $ddp.isGovernedParticipant('aaa', 'bbb') \\$\\$ $prequal.var2";
    private static final String TEMPLATE_TEXT_2 =
            "$prequal.err_international_self,$prequal.err_international_child,$prequal.err_need_parental.";
    private static final String TEMPLATE_TEXT_3 = "\n"
            + "            <p>$prequal.intro1</p>\n"
            + "            <ul>\n"
            + "              <li>$prequal.intro1_1</li>\n"
            + "              <li>$prequal.intro1_2</li>\n"
            + "              <li>$prequal.intro1_3</li>\n"
            + "              <li>$prequal.intro1_4</li>\n"
            + "            </ul>\n"
            + "            <p>$prequal.intro2</p>\n"
            + "          ";

    /**
     * Verify that variables with compound names (like "prequal.var1", "prequal.var2", containing '.')
     * are converted to map hierarchy
     */
    @Test
    public void testConvertTemplateVariablesWithCompoundNames() {
        Map<String, Object> context = new HashMap<>();
        context.put(PREQUAL_VAR1, VAR1_VALUE);
        context.put(PREQUAL_VAR2, VAR2_VALUE);
        Map<String, Object> convertedContext = VelocityUtil.convertVariablesWithCompoundNamesToMap(context);
        assertNotNull(convertedContext.get(VAR_PREFIX));
        Map<String, String> translations = (Map<String, String>)convertedContext.get(VAR_PREFIX);
        assertEquals(VAR1_VALUE, translations.get(VAR1));
        assertEquals(VAR2_VALUE, translations.get(VAR2));
    }

    /**
     * Verify that variables with simple names (like "var1", "var2", not containing '.')
     * not converted to map hierarchy
     */
    @Test
    public void testConvertTemplateVariablesWithSimpleNames() {
        Map<String, Object> context = new HashMap<>();
        context.put(VAR1, VAR1_VALUE);
        context.put(VAR2, VAR2_VALUE);
        Map<String, Object> convertedContext = VelocityUtil.convertVariablesWithCompoundNamesToMap(context);
        assertEquals(2, convertedContext.size());
        assertEquals(VAR1_VALUE, convertedContext.get(VAR1));
        assertEquals(VAR2_VALUE, convertedContext.get(VAR2));
    }

    @Test
    public void testDetectVelocityVariablesFromTemplate() {
        Collection<String> variables = VelocityUtil.extractVelocityVariablesFromTemplate(TEMPLATE_TEXT_1);
        assertEquals(2, variables.size());
        assertTrue(variables.contains(PREQUAL_VAR1));
        assertTrue(variables.contains(PREQUAL_VAR2));

        variables = VelocityUtil.extractVelocityVariablesFromTemplate(TEMPLATE_TEXT_2);
        assertEquals(3, variables.size());
    }

    @Test
    public void testDetectVelocityVariablesFromTemplate_1() {
        Collection<String> variables = VelocityUtil.extractVelocityVariablesFromTemplate(TEMPLATE_TEXT_3);
        assertEquals(6, variables.size());
    }
}
