package org.broadinstitute.ddp.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.json.form.BlockVisibility;
import org.broadinstitute.ddp.model.activity.definition.ConditionalBlockDef;
import org.broadinstitute.ddp.model.activity.definition.ContentBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.question.BoolQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.TemplateType;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.broadinstitute.ddp.pex.PexException;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.broadinstitute.ddp.pex.TreeWalkInterpreter;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.jdbi.v3.core.Handle;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mockito;

public class FormActivityServiceTest extends TxnAwareBaseTest {

    private static TestDataSetupUtil.GeneratedTestData testData;
    private static FormActivityService service;

    private String instanceGuid;
    private String controlBlockGuid;
    private String toggledBlockGuid;
    private String conditionalNestedBlockGuid;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void setup() {
        PexInterpreter interpreter = new TreeWalkInterpreter();
        service = new FormActivityService(interpreter);
        TransactionWrapper.useTxn(handle -> testData = TestDataSetupUtil.generateBasicUserTestData(handle));
    }

    @Test
    public void testGetBlockVisibilities_withoutExpr_excluded() {
        TransactionWrapper.useTxn(handle -> {
            setupActivityAndInstance(handle);
            List<BlockVisibility> visibilities = service.getBlockVisibilities(handle, testData.getUserGuid(), instanceGuid);
            assertNotNull(visibilities);
            assertEquals(2, visibilities.size());
            assertFalse(visibilities.stream().anyMatch(vis -> vis.getGuid().equals(controlBlockGuid)));
            handle.rollback();
        });
    }

    @Test
    public void testGetBlockVisibilities_withExpr_included() {
        PexInterpreter mockInterpreter = Mockito.mock(PexInterpreter.class);
        when(mockInterpreter.eval(anyString(), any(Handle.class), eq(testData.getUserGuid()), anyString()))
                .thenReturn(true);

        FormActivityService formService = new FormActivityService(mockInterpreter);

        TransactionWrapper.useTxn(handle -> {
            setupActivityAndInstance(handle);
            List<BlockVisibility> visibilities = formService.getBlockVisibilities(handle, testData.getUserGuid(), instanceGuid);
            assertNotNull(visibilities);
            assertEquals(2, visibilities.size());

            Optional<BlockVisibility> visibility = visibilities.stream()
                    .filter(vis -> vis.getGuid().equals(toggledBlockGuid)).findFirst();
            assertTrue(visibility.isPresent());
            assertTrue(visibility.get().getShown());

            handle.rollback();
        });
    }

    @Test
    public void testGetBlockVisibilities_withExpr_evalError() {
        thrown.expect(DDPException.class);
        thrown.expectMessage("pex expression");

        PexInterpreter mockInterpreter = Mockito.mock(PexInterpreter.class);
        when(mockInterpreter.eval(anyString(), any(Handle.class), eq(testData.getUserGuid()), anyString()))
                .thenThrow(new PexException("testing"));

        FormActivityService formService = new FormActivityService(mockInterpreter);

        TransactionWrapper.useTxn(handle -> {
            setupActivityAndInstance(handle);
            formService.getBlockVisibilities(handle, testData.getUserGuid(), instanceGuid);
            fail("expected exception was not thrown");
        });
    }

    @Test
    public void testGetBlockVisibilities_nestedBlock_included() {
        TransactionWrapper.useTxn(handle -> {
            setupActivityAndInstance(handle);
            List<BlockVisibility> visibilities = service.getBlockVisibilities(handle, testData.getUserGuid(), instanceGuid);
            assertNotNull(visibilities);
            assertEquals(2, visibilities.size());
            assertTrue(visibilities.stream().anyMatch(vis -> vis.getGuid().equals(conditionalNestedBlockGuid)));
            handle.rollback();
        });
    }

    private void setupActivityAndInstance(Handle handle) {
        long timestamp = Instant.now().toEpochMilli();

        String actCode = "ACT_TEST_COND_FORM_" + timestamp;
        String sid = "SID_" + timestamp;

        Template prompt = new Template(TemplateType.TEXT, null, "question that controls toggled block");
        Template yes = new Template(TemplateType.TEXT, null, "show");
        Template no = new Template(TemplateType.TEXT, null, "hide");
        QuestionBlockDef controlBlock = new QuestionBlockDef(BoolQuestionDef.builder(sid, prompt, yes, no).build());

        ContentBlockDef toggledBlock = new ContentBlockDef(new Template(TemplateType.TEXT, null, "this is toggled"));
        String expr = String.format("user.studies[\"%s\"].forms[\"%s\"].questions[\"%s\"].answers.hasTrue()",
                testData.getStudyGuid(), actCode, sid);
        toggledBlock.setShownExpr(expr);

        TextQuestionDef condControl = TextQuestionDef
                .builder(TextInputType.TEXT, sid + "CONTROL", new Template(TemplateType.TEXT, null, "control"))
                .build();
        QuestionBlockDef condNested = new QuestionBlockDef(TextQuestionDef
                .builder(TextInputType.TEXT, sid + "NESTED", new Template(TemplateType.TEXT, null, "nested"))
                .build());
        condNested.setShownExpr("true");
        ConditionalBlockDef condBlock = new ConditionalBlockDef(condControl);
        condBlock.getNested().add(condNested);

        FormActivityDef form = FormActivityDef.generalFormBuilder(actCode, "v1", testData.getStudyGuid())
                .addName(new Translation("en", "dummy activity to test conditional toggling blocks"))
                .addSection(new FormSectionDef(null, Arrays.asList(controlBlock, toggledBlock)))
                .addSection(new FormSectionDef(null, Collections.singletonList(condBlock)))
                .build();

        handle.attach(ActivityDao.class).insertActivity(form, RevisionMetadata.now(testData.getUserId(), "test"));
        assertNotNull(form.getActivityId());
        assertNotNull(controlBlock.getBlockGuid());
        assertNotNull(toggledBlock.getBlockGuid());

        ActivityInstanceDto instanceDto = handle.attach(ActivityInstanceDao.class)
                .insertInstance(form.getActivityId(), testData.getUserGuid());
        instanceGuid = instanceDto.getGuid();
        controlBlockGuid = controlBlock.getBlockGuid();
        toggledBlockGuid = toggledBlock.getBlockGuid();
        conditionalNestedBlockGuid = condNested.getBlockGuid();
    }
}
