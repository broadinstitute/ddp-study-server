package org.broadinstitute.ddp.model.activity.definition.template;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Collection;

import org.junit.Test;

/**
 * Test methods of class {@link TemplateUtil}
 */
public class TemplateUtilTest {

    /**
     * Verify that method {@link TemplateUtil#renderWithDefaultValues(String, Collection, String)}
     * generates param1/param2 from isGovernedParticipant() method params and uses fallback value for answer() method
     */
    @Test
    public void testRenderWithDefaultValuesWithCustomMethods() {
        String initialName =
                "AAA $ddp.isGovernedParticipant('your child', 'you') BB $ddp.answer('DIAGNOSIS_TYPE', '[DIAGNOSIS_TYPE]', true) CCC";
        String renderedValue = TemplateUtil.renderWithDefaultValues(initialName, null, "en");
        assertEquals("AAA your child/you BB [DIAGNOSIS_TYPE] CCC", renderedValue);
    }

    /**
     * Verify that if render without using of default values
     * (using method {@link TemplateUtil#render(String, Collection, String, boolean)} with last parameter = false)
     * then custom method $ddp.isGovernedParticipant() will return 2nd parameter (because by default a participant is not
     * governed, and no user or instance data available here to detect a participant governance state).
     */
    @Test
    public void testRenderWithCustomMethod() {
        String initialName = "AAA $ddp.isGovernedParticipant('your child', 'you') CCC";
        String renderedValue = TemplateUtil.render(initialName, null, "en", false);
        assertEquals("AAA you CCC", renderedValue);
    }

    /**
     * Verify that rendering of empty string will return an empty string.
     */
    @Test
    public void testRenderWithDefaultValuesWithEmptyValue() {
        String renderedValue = TemplateUtil.renderWithDefaultValues("", null, "en");
        assertEquals("", renderedValue);
    }

    /**
     * Verify that if template variables are specified then it is rendered correctly
     */
    @Test
    public void testRenderWithDefaultValuesWithVariables() {
        Collection<TemplateVariable> vars = new ArrayList<>();
        vars.add(TemplateVariable.single("var1", "en", "VAR1 VALUE"));
        vars.add(TemplateVariable.single("var2", "en", "VAR2 VALUE"));
        String initialName = "AAA $var1 BB $var2 CCC";
        String renderedValue = TemplateUtil.renderWithDefaultValues(initialName, vars, "en");
        assertEquals("AAA VAR1 VALUE BB VAR2 VALUE CCC", renderedValue);
    }
}
