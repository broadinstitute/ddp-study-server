package org.broadinstitute.ddp.content;


import static org.broadinstitute.ddp.content.I18nTemplateConstants.DDP;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Collection;
import java.util.List;

import org.junit.Test;

/**
 * Tests methods of class {@link VelocityUtil}.
 */
public class VelocityUtilTest {

    private static final String PREQUAL_VAR1 = "prequal.var1";
    private static final String PREQUAL_VAR2 = "prequal.var2";

    private static final Collection<String> VARIABLES_1 = List.of(DDP, PREQUAL_VAR1, PREQUAL_VAR2);
    private static final String TEMPLATE_TEXT_1 =
            "Test template: $prequal.var1 $ddp.isGovernedParticipant('aaa', 'bbb') \\$\\$ $prequal.var2";
    private static final String CONVERTED_TEMPLATE_TEXT_1 =
            "Test template: $prequal-var1 $ddp.isGovernedParticipant('aaa', 'bbb') \\$\\$ $prequal-var2";

    private static final Collection<String> VARIABLES_2 = List.of(
            "prequal.err_international_self", "prequal.err_international_child", "prequal.err_need_parental");
    private static final String TEMPLATE_TEXT_2 =
            "$prequal.err_international_self,$prequal.err_international_child,$prequal.err_need_parental.";
    private static final String CONVERTED_TEMPLATE_TEXT_2 =
            "$prequal-err_international_self,$prequal-err_international_child,$prequal-err_need_parental.";

    @Test
    public void testConvertTemplateVariablesToValid() {
        String convertedTemplateText = VelocityUtil.convertTemplateVariablesToValid(TEMPLATE_TEXT_1, VARIABLES_1);
        assertEquals(CONVERTED_TEMPLATE_TEXT_1, convertedTemplateText);

        convertedTemplateText = VelocityUtil.convertTemplateVariablesToValid(TEMPLATE_TEXT_2, VARIABLES_2);
        assertEquals(CONVERTED_TEMPLATE_TEXT_2, convertedTemplateText);
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
