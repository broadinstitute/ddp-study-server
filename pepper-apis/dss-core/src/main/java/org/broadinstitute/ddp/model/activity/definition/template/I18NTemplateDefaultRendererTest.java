package org.broadinstitute.ddp.model.activity.definition.template;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.content.I18nTemplateDefaultRenderer;
import org.broadinstitute.ddp.content.I18nTemplateRenderFacade;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * Test methods of class {@link I18nTemplateDefaultRenderer}
 */
public class I18NTemplateDefaultRendererTest extends TxnAwareBaseTest {
    private static TestDataSetupUtil.GeneratedTestData testData;

    @BeforeClass
    public static void setup() {
        TransactionWrapper.useTxn(handle -> testData = TestDataSetupUtil.generateBasicUserTestData(handle));
    }

    /**
     * Verify that method {@link I18nTemplateRenderFacade#renderTemplateWithDefaultValues(String, Collection, String)}
     * generates param1/param2 from isGovernedParticipant() method params and uses fallback value for answer() method
     */
    @Test
    public void testRenderWithDefaultValuesWithCustomMethods() {
        String initialName =
                "AAA $ddp.isGovernedParticipant('your child', 'you') BB $ddp.answer('DIAGNOSIS_TYPE', '[DIAGNOSIS_TYPE]', true) CCC";
        String renderedValue = I18nTemplateRenderFacade.INSTANCE.renderTemplateWithDefaultValues(initialName, null, "en");
        assertEquals("AAA your child/you BB [DIAGNOSIS_TYPE] CCC", renderedValue);
    }

    /**
     * Verify that if render without using of default values
     * (using method {@link I18nTemplateRenderFacade#renderTemplate(Template, String, Map)}
     * then custom method $ddp.isGovernedParticipant() will return 2nd parameter (because by default a participant is not
     * governed, and no user or instance data available here to detect a participant governance state).
     */
    @Test
    public void testRenderWithCustomMethod() {
        String initialName = "AAA $ddp.isGovernedParticipant('your child', 'you') CCC";
        String renderedValue = I18nTemplateRenderFacade.INSTANCE.renderTemplate(null, initialName, null, "en", false);
        assertEquals("AAA you CCC", renderedValue);
    }

    /**
     * Verify that rendering of empty string will return an empty string.
     */
    @Test
    public void testRenderWithDefaultValuesWithEmptyValue() {
        String renderedValue = I18nTemplateRenderFacade.INSTANCE.renderTemplateWithDefaultValues("", null, "en");
        assertEquals("", renderedValue);
    }

    /**
     * Verify that if template variables are specified then it is rendered correctly
     */
    @Test
    public void testRenderWithDefaultValuesWithVariables() {
        TransactionWrapper.useTxn(handle -> {
            Collection<TemplateVariable> vars = new ArrayList<>();
            vars.add(TemplateVariable.single("var1", "en", "VAR1 VALUE"));
            vars.add(TemplateVariable.single("var2", "en", "VAR2 VALUE"));
            String initialName = "AAA $var1 BB $var2 CCC";
            String renderedValue = I18nTemplateRenderFacade.INSTANCE.renderTemplateWithDefaultValues(initialName, vars, "en");
            assertEquals("AAA VAR1 VALUE BB VAR2 VALUE CCC", renderedValue);

            handle.rollback();
        });
    }
}
