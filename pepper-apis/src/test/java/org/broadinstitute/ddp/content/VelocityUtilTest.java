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
    private static final String PREQUAL_VAR1_VALUE = "testvar1";
    private static final String PREQUAL_VAR2_VALUE = "testvar2";

    private static final String PREQUAL_VAR1 = VAR_PREFIX + VARIABLE_SEP + VAR1;
    private static final String PREQUAL_VAR2 = VAR_PREFIX + VARIABLE_SEP + VAR2;

    private static final String TEMPLATE_TEXT_1 =
            "Test template: $prequal.var1 $ddp.isGovernedParticipant('aaa', 'bbb') \\$\\$ $prequal.var2";
    private static final String TEMPLATE_TEXT_2 =
            "$prequal.err_international_self,$prequal.err_international_child,$prequal.err_need_parental.";


    @Test
    public void testConvertTemplateVariablesToValid() {
        Map<String, Object> context = new HashMap<>();
        context.put(PREQUAL_VAR1, PREQUAL_VAR1_VALUE);
        context.put(PREQUAL_VAR2, PREQUAL_VAR2_VALUE);
        Map<String, Object> convertedContext = VelocityUtil.convertNestedVariablesToMap(context);
        assertNotNull(convertedContext.get(VAR_PREFIX));
        Map<String, String> translations = (Map<String, String>)convertedContext.get(VAR_PREFIX);
        assertEquals(PREQUAL_VAR1_VALUE, translations.get(VAR1));
        assertEquals(PREQUAL_VAR2_VALUE, translations.get(VAR2));
    }

    @Test
    public void testDetectVelocityVariablesFromTemplate() {
        Collection<String> variables = VelocityUtil.detectVelocityVariablesFromTemplate(TEMPLATE_TEXT_1);
        assertEquals(2, variables.size());
        assertTrue(variables.contains(PREQUAL_VAR1));
        assertTrue(variables.contains(PREQUAL_VAR2));

        variables = VelocityUtil.detectVelocityVariablesFromTemplate(TEMPLATE_TEXT_2);
        assertEquals(3, variables.size());
    }
}
