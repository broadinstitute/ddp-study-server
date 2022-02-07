package org.broadinstitute.ddp.service.actvityinstancebuilder;

import static org.broadinstitute.ddp.content.RendererInitialContextCreator.RenderContextSource.FORM_RESPONSE_AND_ACTIVITY_DEF;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.context.AIBuilderParams.createParams;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.model.activity.types.FormType;
import org.broadinstitute.ddp.service.ActivityInstanceServiceTestAbstract;
import org.broadinstitute.ddp.service.actvityinstancebuilder.context.AIBuildStep;
import org.junit.Test;


/**
 * Tests {@link ActivityInstanceFromDefinitionBuilder}.
 * Call it with different configurations (for example with different combinations of building steps).
 */
public class ActivityInstanceFromDefinitionBuilderTest extends ActivityInstanceServiceTestAbstract {

    private static final String VALID_LANG_CODE = "en";
    private static final String INVALID_LANG_CODE = "abc";
    private static final ContentStyle CONTENT_STYLE = ContentStyle.BASIC;

    /**
     * POSITIVE TEST
     * Test {@link ActivityInstanceFromDefinitionBuilder} with all steps passed.<br>
     * Other conditions:
     * <pre>
     * - operatorGuid is not specified (AIBuilder should set it with userGuid).
     * </pre>
     */
    @Test
    public void testAIBuilderWithAllStepsExecutedOK() {
        TransactionWrapper.useTxn(handle -> {
            String instanceGuid = setupActivityAndInstance(handle);
            var context = AIBuilderFactory.createAIBuilder(handle,

                    createParams(userGuid, studyGuid, instanceGuid)
                            .setIsoLangCode(VALID_LANG_CODE)
                            .setStyle(CONTENT_STYLE))
                    .checkParams()
                    .readFormInstanceData()
                    .readActivityDef()
                    .createRendererContext(FORM_RESPONSE_AND_ACTIVITY_DEF)
                    .startBuild()
                    .buildFormInstance()
                    .buildFormChildren()
                    .renderFormTitles()
                    .renderContent()
                    .setDisplayNumbers()
                    .updateBlockStatuses()
                    .populateSnapshottedAddress()
                    .endBuild()
                    .getContext();

            assertEquals(userGuid, context.getParams().getOperatorGuid());

            assertEquals(14, context.getPassedBuildSteps().size());
            assertTrue(context.isBuildStepPassed(AIBuildStep.SET_DISPLAY_NUMBERS));
            assertTrue(context.isBuildStepPassed(AIBuildStep.UPDATE_BLOCK_STATUSES));

            assertEquals(AIBuildStep.END_BUILD, context.getBuildStep());
            assertEquals(FormType.PREQUALIFIER, context.getFormActivityDef().getFormType());
            assertEquals(1, context.getFormActivityDef().getAllSections().size());

            assertNotNull(context.getFormInstance());

            handle.rollback();
        });
    }

    /**
     * POSITIVE TEST
     * Test {@link ActivityInstanceFromDefinitionBuilder} with only some of steps executed successfully
     * (meaning that all mandatory steps presents).<br>
     * Other conditions:
     * <pre>
     * - contentStyle is not specified (because step 'renderContent' not specified).
     * </pre>
     */
    @Test
    public void testAIBuilderWithAllNotAllStepsExecutedOK() {
        TransactionWrapper.useTxn(handle -> {
            String instanceGuid = setupActivityAndInstance(handle);
            var context = AIBuilderFactory.createAIBuilder(handle,
                    createParams(userGuid, studyGuid, instanceGuid)
                            .setIsoLangCode(VALID_LANG_CODE))
                    .checkParams()
                    .readFormInstanceData()
                    .readActivityDef()
                    .createRendererContext(FORM_RESPONSE_AND_ACTIVITY_DEF)
                    .startBuild()
                    .buildFormInstance()
                    .buildFormChildren()
                    .renderFormTitles()
                    .populateSnapshottedAddress()
                    .endBuild()
                    .getContext();

            assertEquals(userGuid, context.getParams().getOperatorGuid());

            assertEquals(11, context.getPassedBuildSteps().size());
            assertFalse(context.isBuildStepPassed(AIBuildStep.RENDER_CONTENT));
            assertFalse(context.isBuildStepPassed(AIBuildStep.SET_DISPLAY_NUMBERS));
            assertFalse(context.isBuildStepPassed(AIBuildStep.UPDATE_BLOCK_STATUSES));

            assertEquals(AIBuildStep.END_BUILD, context.getBuildStep());

            assertNotNull(context.getFormInstance());

            handle.rollback();
        });
    }

    /**
     * NEGATIVE TEST
     * Test {@link ActivityInstanceFromDefinitionBuilder} with steps passed in wrong order
     * (some steps are set before steps which it depends upon).<br>
     * Other conditions:
     * <pre>
     * - not all steps are specified.
     * </pre>
     */
    @Test
    public void testAIBuilderWithStepsInWrongOrderNOK() {
        TransactionWrapper.useTxn(handle -> {
            String instanceGuid = setupActivityAndInstance(handle);
            try {
                AIBuilderFactory.createAIBuilder(handle,
                        createParams(userGuid, studyGuid, instanceGuid)
                                .setIsoLangCode(VALID_LANG_CODE)
                                .setStyle(CONTENT_STYLE))
                        .checkParams()
                        .readActivityDef()
                        .readFormInstanceData()
                        .createRendererContext(FORM_RESPONSE_AND_ACTIVITY_DEF)
                        .startBuild()
                        .buildFormInstance()
                        .buildFormChildren()
                        .populateSnapshottedAddress()
                        .endBuild()
                        .getContext();

            } catch (IllegalStateException e) {
                assertEquals("Incorrect last step before step=READ_ACTIVITY_DEF of ActivityInstance building: "
                        + "last step=CHECK_PARAMS, but required lastStep=READ_FORM_INSTANCE", e.getMessage());
            }

            handle.rollback();
        });
    }

    /**
     * NEGATIVE TEST
     * Test {@link ActivityInstanceFromDefinitionBuilder} with some mandatory steps are missed.<br>
     * Other conditions:
     * <pre>
     * - not all steps are specified.
     * </pre>
     */
    @Test
    public void testAIBuilderWithSkippedMandatoryStepsNOK() {
        TransactionWrapper.useTxn(handle -> {
            String instanceGuid = setupActivityAndInstance(handle);
            try {
                AIBuilderFactory.createAIBuilder(handle,
                        createParams(userGuid, studyGuid, instanceGuid)
                                .setIsoLangCode(VALID_LANG_CODE)
                                .setStyle(CONTENT_STYLE))
                        .readActivityDef()
                        .readFormInstanceData()
                        .createRendererContext(FORM_RESPONSE_AND_ACTIVITY_DEF)
                        .startBuild()
                        .buildFormInstance()
                        .buildFormChildren()
                        .populateSnapshottedAddress()
                        .endBuild()
                        .getContext();

            } catch (IllegalStateException e) {
                assertEquals("Incorrect last step before step=READ_ACTIVITY_DEF of ActivityInstance building: "
                        + "last step=INIT, but required lastStep=READ_FORM_INSTANCE", e.getMessage());
            }

            handle.rollback();
        });
    }

    /**
     * NEGATIVE TEST
     * Test {@link ActivityInstanceFromDefinitionBuilder} with invalid language code passed.<br>
     * Other conditions:
     * <pre>
     * - not all steps are specified.
     * </pre>
     */
    @Test
    public void testAIBuilderWithUnknownLangCodeNOK() {
        TransactionWrapper.useTxn(handle -> {
            String instanceGuid = setupActivityAndInstance(handle);
            var context = AIBuilderFactory.createAIBuilder(handle,
                    createParams(userGuid, studyGuid, instanceGuid)
                            .setIsoLangCode(INVALID_LANG_CODE)
                            .setStyle(CONTENT_STYLE))
                    .checkParams()
                    .readFormInstanceData()
                    .readActivityDef()
                    .createRendererContext(FORM_RESPONSE_AND_ACTIVITY_DEF)
                    .startBuild()
                    .buildFormInstance()
                    .endBuild()
                    .getContext();

            assertEquals(2, context.getPassedBuildSteps().size());
            assertTrue(context.isBuildStepPassed(AIBuildStep.CHECK_PARAMS));
            assertEquals(AIBuildStep.CHECK_PARAMS, context.getBuildStep());

            assertEquals(AIBuildStep.CHECK_PARAMS, context.getFailedStep());
            assertEquals("Unknown language code: abc", context.getFailedMessage());

            assertNull(context.getFormInstance());

            handle.rollback();
        });
    }

    /**
     * NEGATIVE TEST
     * Test {@link ActivityInstanceFromDefinitionBuilder} with style not set (whereas
     * step `renderContent()` is set).<br>
     * Other conditions:
     * <pre>
     * - not all steps are specified.
     * </pre>
     */
    @Test
    public void testAIBuilderWithStyleNotSetWhereasRenderContentIsSetNOK() {
        TransactionWrapper.useTxn(handle -> {
            String instanceGuid = setupActivityAndInstance(handle);
            try {
                var context = AIBuilderFactory.createAIBuilder(handle,
                        createParams(userGuid, studyGuid, instanceGuid)
                                .setIsoLangCode(VALID_LANG_CODE))
                        .checkParams()
                        .readFormInstanceData()
                        .readActivityDef()
                        .createRendererContext(FORM_RESPONSE_AND_ACTIVITY_DEF)
                        .startBuild()
                        .buildFormInstance()
                        .buildFormChildren()
                        .populateSnapshottedAddress()
                        .renderContent()
                        .endBuild()
                        .getContext();
            } catch (IllegalStateException e) {
                assertEquals("RenderContent() cannot be executed because ContentStyle parameter is not set", e.getMessage());
            }

            handle.rollback();
        });
    }
}
