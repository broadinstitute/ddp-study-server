package org.broadinstitute.ddp.model.activity.instance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.constants.TestConstants;
import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.content.HtmlConverter;
import org.broadinstitute.ddp.content.I18nContentRenderer;
import org.broadinstitute.ddp.content.I18nTemplateConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.UserProfileDao;
import org.broadinstitute.ddp.db.dao.UserProfileSql;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.instance.answer.BoolAnswer;
import org.broadinstitute.ddp.model.activity.instance.question.BoolQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.Question;
import org.broadinstitute.ddp.model.activity.instance.question.TextQuestion;
import org.broadinstitute.ddp.model.activity.instance.validation.RequiredRule;
import org.broadinstitute.ddp.model.activity.types.FormType;
import org.broadinstitute.ddp.model.activity.types.ListStyleHint;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.broadinstitute.ddp.pex.PexException;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.broadinstitute.ddp.pex.TreeWalkInterpreter;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.jdbi.v3.core.Handle;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class FormInstanceTest extends TxnAwareBaseTest {

    private static String userGuid = TestConstants.TEST_USER_GUID;
    private static PexInterpreter interpreter;
    
    @Rule
    public ExpectedException thrown = ExpectedException.none();
    private String controlBlockGuid = "CONTROL";
    private String toggledBlockGuid = "TOGGLED";
    private Handle mockHandle;

    @BeforeClass
    public static void setup() {
        interpreter = new TreeWalkInterpreter();
    }

    @Before
    public void setupMocks() {
        mockHandle = mock(Handle.class);
        var mockProfleDao = mock(UserProfileDao.class);
        var mockActInstDao = mock(ActivityInstanceDao.class);
        doReturn(mockProfleDao).when(mockHandle).attach(UserProfileDao.class);
        doReturn(mockActInstDao).when(mockHandle).attach(ActivityInstanceDao.class);
        doReturn(Optional.empty()).when(mockProfleDao).findProfileByUserId(anyLong());
        doNothing().when(mockActInstDao).saveSubstitutions(anyLong(), any());
    }

    @Test
    public void testRenderContent_contentBlock_standardStyle() {
        Map<Long, String> fixture = new HashMap<>();
        fixture.put(1L, "<p>this is title</p>");
        fixture.put(2L, "<p>this is body</p>");

        I18nContentRenderer mockRenderer = mock(I18nContentRenderer.class);
        when(mockRenderer.bulkRender(any(), anySet(), anyLong(), any())).thenReturn(fixture);

        ContentBlock content = new ContentBlock(1L, 2L);
        FormSection s1 = new FormSection(Collections.singletonList(content));
        FormInstance form = buildEmptyTestInstance();
        form.addBodySections(Collections.singletonList(s1));

        form.renderContent(mockHandle, mockRenderer, 1L, ContentStyle.STANDARD);
        assertEquals(fixture.get(1L), content.getTitle());
        assertEquals(fixture.get(2L), content.getBody());
    }

    @Test
    public void testRenderContent_contentBlock_basicStyle() {
        Map<Long, String> fixture = new HashMap<>();
        fixture.put(1L, "<p>this is title</p>");
        fixture.put(2L, "<p>this is body</p>");

        I18nContentRenderer mockRenderer = mock(I18nContentRenderer.class);
        when(mockRenderer.bulkRender(any(), anySet(), anyLong(), any())).thenReturn(fixture);

        ContentBlock content = new ContentBlock(1L, 2L);
        FormSection s1 = new FormSection(Collections.singletonList(content));
        FormInstance form = buildEmptyTestInstanceWithHtmlInTitleAndSubtitle();
        form.addBodySections(Collections.singletonList(s1));

        form.renderContent(mockHandle, mockRenderer, 1L, ContentStyle.BASIC);
        assertEquals("title", form.getTitle());
        assertEquals("subtitle", form.getSubtitle());
        assertEquals("this is title", content.getTitle());
        assertEquals(fixture.get(2L), content.getBody());
    }

    @Test
    public void testRenderContent_questionBlock_standardStyle() {
        Map<Long, String> fixture = new HashMap<>();
        fixture.put(1L, "this is <strong>bold</strong> prompt");
        fixture.put(2L, "this is bold prompt");

        I18nContentRenderer mockRenderer = mock(I18nContentRenderer.class);
        when(mockRenderer.bulkRender(any(), anySet(), anyLong(), any())).thenReturn(fixture);

        Question question = new TextQuestion("sid", 1, null, Collections.emptyList(), Collections.emptyList(),
                TextInputType.TEXT);
        QuestionBlock block = new QuestionBlock(question);
        FormSection s1 = new FormSection(Collections.singletonList(block));
        FormInstance form = buildEmptyTestInstance();
        form.addBodySections(Collections.singletonList(s1));

        form.renderContent(mockHandle, mockRenderer, 1L, ContentStyle.STANDARD);
        assertTrue(HtmlConverter.hasSameValue(fixture.get(1L), question.getPrompt()));
        assertEquals(fixture.get(2L), question.getTextPrompt());
    }

    @Test
    public void testRenderContent_questionBlock_basicStyle() {
        Map<Long, String> fixture = new HashMap<>();
        fixture.put(1L, "this is <b>bold</b> prompt");

        I18nContentRenderer mockRenderer = mock(I18nContentRenderer.class);
        when(mockRenderer.bulkRender(any(), anySet(), anyLong(), any())).thenReturn(fixture);

        Question question = new TextQuestion("sid", 1, null, Collections.emptyList(), Collections.emptyList(),
                TextInputType.TEXT);
        QuestionBlock block = new QuestionBlock(question);
        FormSection s1 = new FormSection(Collections.singletonList(block));
        FormInstance form = buildEmptyTestInstance();
        form.addBodySections(Collections.singletonList(s1));

        form.renderContent(mockHandle, mockRenderer, 1L, ContentStyle.BASIC);

        assertTrue(HtmlConverter.hasSameValue(fixture.get(1L), question.getPrompt()));
        assertEquals("this is bold prompt", question.getTextPrompt());
    }

    @Test
    public void testRenderContent_sectionName_standardStyle() {
        Map<Long, String> fixture = new HashMap<>();
        fixture.put(1L, "this is <strong>bold</strong> section name");

        I18nContentRenderer mockRenderer = mock(I18nContentRenderer.class);
        when(mockRenderer.bulkRender(any(), anySet(), anyLong(), any())).thenReturn(fixture);

        FormSection s1 = new FormSection(1L);
        FormInstance form = buildEmptyTestInstance();
        form.addBodySections(Collections.singletonList(s1));

        form.renderContent(mockHandle, mockRenderer, 1L, ContentStyle.STANDARD);
        assertEquals(fixture.get(1L), s1.getName());
    }

    @Test
    public void testRenderContent_sectionName_basicStyle() {
        Map<Long, String> fixture = new HashMap<>();
        fixture.put(1L, "this is <strong>bold</strong> section name");

        I18nContentRenderer mockRenderer = mock(I18nContentRenderer.class);
        when(mockRenderer.bulkRender(any(), anySet(), anyLong(), any())).thenReturn(fixture);

        FormSection s1 = new FormSection(1L);
        FormInstance form = buildEmptyTestInstance();
        form.addBodySections(Collections.singletonList(s1));

        form.renderContent(mockHandle, mockRenderer, 1L, ContentStyle.BASIC);
        assertEquals("this is bold section name", s1.getName());
    }

    @Test
    public void testRenderContent_specialVarsContext() {
        TransactionWrapper.useTxn(handle -> {
            var testData = TestDataSetupUtil.generateBasicUserTestData(handle);
            handle.attach(UserProfileSql.class).upsertFirstName(testData.getUserId(), "foo");
            handle.attach(UserProfileSql.class).upsertLastName(testData.getUserId(), "bar");

            I18nContentRenderer mockRenderer = mock(I18nContentRenderer.class);
            doReturn(Collections.emptyMap()).when(mockRenderer).bulkRender(any(), any(), anyLong(), any());
            doReturn(handle.attach(UserProfileDao.class)).when(mockHandle).attach(UserProfileDao.class);

            FormInstance form = buildEmptyTestInstance(testData.getUserId());
            form.renderContent(mockHandle, mockRenderer, 1L, ContentStyle.BASIC);

            verify(mockRenderer, times(1)).bulkRender(any(), anySet(), anyLong(), argThat(context -> {
                assertFalse(context.isEmpty());
                assertNotNull(context.get(I18nTemplateConstants.DASHED_DATE));
                assertEquals("foo", context.get(I18nTemplateConstants.PARTICIPANT_FIRST_NAME));
                assertEquals("bar", context.get(I18nTemplateConstants.PARTICIPANT_LAST_NAME));
                return true;
            }));

            handle.rollback();
        });
    }

    @Test
    public void testIsComplete_emptyForm() {
        FormInstance form = buildEmptyTestInstance();
        assertTrue(form.isComplete());
    }

    @Test
    public void testIsComplete_noQuestions() {
        ContentBlock content = new ContentBlock(1);
        FormSection s1 = new FormSection(Collections.singletonList(content));
        FormInstance form = buildEmptyTestInstance();
        form.addBodySections(Collections.singletonList(s1));

        assertTrue(form.isComplete());
    }

    @Test
    public void testIsComplete_noRequiredQuestions() {
        QuestionBlock block = new QuestionBlock(new BoolQuestion("SID", 1, Collections.emptyList(), Collections.emptyList(), 2, 3));
        FormSection s1 = new FormSection(Collections.singletonList(block));
        FormInstance form = buildEmptyTestInstance();
        form.addBodySections(Collections.singletonList(s1));

        assertTrue(form.isComplete());
    }

    @Test
    public void testIsComplete_requiredQuestionWithNoAnswer() {
        RequiredRule<BoolAnswer> req = new RequiredRule<>(1L, null, "required", false);
        BoolQuestion question = new BoolQuestion("SID", 2, Collections.emptyList(), Collections.singletonList(req), 2, 3);
        QuestionBlock block = new QuestionBlock(question);
        FormSection s1 = new FormSection(Collections.singletonList(block));
        FormInstance form = buildEmptyTestInstance();
        form.addBodySections(Collections.singletonList(s1));

        assertFalse(form.isComplete());
    }

    @Test
    public void testNumbering() {
        FormInstance form = buildEmptyTestInstance();
        Map<String, Integer> expectedNumberForStableId = new HashMap<>();

        int expectedNumber = 2; // starts at 2 since the intro has a question

        for (int formNumber = 0; formNumber < 3; formNumber++) {
            List<FormBlock> blocks = new ArrayList<>();
            for (int blockNumber = 0; blockNumber < 2; blockNumber++) {
                String stableId = "SID" + formNumber + "_" + blockNumber;
                blocks.add(new QuestionBlock(new BoolQuestion(stableId, 2, Collections.emptyList(), Collections
                        .emptyList(), 2, 3)));
                expectedNumberForStableId.put(stableId, expectedNumber++);
            }
            form.addBodySections(Collections.singletonList(new FormSection(blocks)));
        }

        List<FormBlock> introBlocks = new ArrayList<>();
        String introQuestionStableId = "intro";
        introBlocks.add(new QuestionBlock(new BoolQuestion(introQuestionStableId,
                2,
                Collections.emptyList(),
                Collections.emptyList(),
                2,
                3)));
        FormSection introSection = new FormSection(introBlocks);
        expectedNumberForStableId.put(introQuestionStableId, 1);

        List<FormBlock> closingBlocks = new ArrayList<>();
        String closingQuestionStableId = "closing";
        introBlocks.add(new QuestionBlock(new BoolQuestion(introQuestionStableId,
                2,
                Collections.emptyList(),
                Collections.emptyList(),
                2,
                3)));
        FormSection closingSection = new FormSection(closingBlocks);
        expectedNumberForStableId.put(closingQuestionStableId, expectedNumberForStableId.size());

        form.setIntroduction(introSection);
        form.setDisplayNumbers();
        form.setClosing(closingSection);

        for (FormSection formSection : form.getBodySections()) {
            for (FormBlock formBlock : formSection.getBlocks()) {
                int actualDisplayNumber = ((Numberable) formBlock).getDisplayNumber();
                int expectedDisplayNumber = expectedNumberForStableId.get(((QuestionBlock) formBlock).getQuestion()
                        .getStableId());
                Assert.assertEquals(expectedDisplayNumber, actualDisplayNumber);
            }
        }
    }

    @Test
    public void testIsComplete_requiredQuestionWithAnswer() {
        RequiredRule<BoolAnswer> req = new RequiredRule<>(1L, null, "required", false);
        BoolAnswer answer = new BoolAnswer(2L, "SID", "ABC", true);
        BoolQuestion question = new BoolQuestion("SID", 3, Collections.singletonList(answer), Collections.singletonList(req), 2, 3);
        QuestionBlock block = new QuestionBlock(question);
        FormSection s1 = new FormSection(Collections.singletonList(block));
        FormInstance form = buildEmptyTestInstance();
        form.addBodySections(Collections.singletonList(s1));

        assertTrue(form.isComplete());
    }

    @Test
    public void testIsComplete_conditionallyShownQuestion() {
        BoolQuestion control = new BoolQuestion("SID", 1, Collections.emptyList(), Collections.emptyList(), 2, 3);
        RequiredRule<BoolAnswer> req = new RequiredRule<>(1L, null, "required", false);
        BoolQuestion nested = new BoolQuestion("SID", 2, Collections.emptyList(), Collections.singletonList(req), 4, 5);
        ConditionalBlock block = new ConditionalBlock(control);
        block.getNested().add(new QuestionBlock(nested));

        FormSection s1 = new FormSection(Collections.singletonList(block));
        FormInstance form = buildEmptyTestInstance();
        form.addBodySections(Collections.singletonList(s1));

        block.setShown(false);
        assertTrue(form.isComplete());

        block.setShown(true);
        assertFalse(form.isComplete());

        block.getNested().get(0).setShown(false);
        assertTrue(form.isComplete());
    }

    @Test
    public void testIsComplete_groupedQuestionsAreChecked() {
        RequiredRule<BoolAnswer> req = new RequiredRule<>(1L, null, "required", false);
        BoolQuestion nested = new BoolQuestion("SID", 1, Collections.emptyList(), Collections.singletonList(req), 2, 3);
        GroupBlock group = new GroupBlock(null, null);
        group.getNested().add(new QuestionBlock(nested));

        FormSection s1 = new FormSection(Collections.singletonList(group));
        FormInstance form = buildEmptyTestInstance();
        form.addBodySections(Collections.singletonList(s1));

        group.setShown(false);
        assertTrue(form.isComplete());

        group.setShown(true);
        assertFalse(form.isComplete());
    }

    @Test
    public void testUpdateBlockStatuses_childInConditionalBlockIsUpdated() {
        TransactionWrapper.useTxn(handle -> {
            BoolQuestion control = new BoolQuestion("SID", 1, Collections.emptyList(), Collections.emptyList(), 2, 3);
            RequiredRule<BoolAnswer> req = new RequiredRule<>(1L, null, "required", false);
            BoolQuestion nested = new BoolQuestion("SID", 2, Collections.emptyList(), Collections.singletonList(req), 4, 5);
            ConditionalBlock block = new ConditionalBlock(control);
            block.getNested().add(new QuestionBlock(nested));

            FormSection s1 = new FormSection(Collections.singletonList(block));
            FormInstance form = buildEmptyTestInstance();
            form.addBodySections(Collections.singletonList(s1));

            block.getNested().get(0).setShown(false);
            block.getNested().get(0).setShownExpr("true");
            form.updateBlockStatuses(handle, interpreter, userGuid, form.getGuid());
            assertTrue(block.getNested().get(0).isShown());
        });
    }

    @Test
    public void testUpdateBlockStatuses_nonToggleableBlocksUntouched() {
        TransactionWrapper.useTxn(handle -> {
            ContentBlock content = new ContentBlock(1);
            FormSection s1 = new FormSection(Collections.singletonList(content));
            FormInstance form = buildEmptyTestInstance();
            form.addBodySections(Collections.singletonList(s1));

            content.setShown(false);
            form.updateBlockStatuses(handle, interpreter, userGuid, form.getGuid());
            assertFalse(content.isShown());

            content.setShownExpr("true");
            form.updateBlockStatuses(handle, interpreter, userGuid, form.getGuid());
            assertTrue(content.isShown());
        });
    }

    @Test
    public void testUpdateBlockStatuses_withoutExpr_defaultShown() {
        TransactionWrapper.useTxn(handle -> {
            FormInstance form = buildTestInstance();
            form.updateBlockStatuses(handle, interpreter, userGuid, form.getGuid());

            for (FormSection sect : form.getBodySections()) {
                for (FormBlock block : sect.getBlocks()) {
                    if (controlBlockGuid.equals(block.getGuid())) {
                        assertTrue(block.isShown());
                    }
                }
            }
        });
    }

    @Test
    public void testUpdateBlockStatuses_withExpr_evaluated() {
        PexInterpreter mockInterpreter = mock(PexInterpreter.class);

        TransactionWrapper.useTxn(handle -> {
            FormInstance form = buildTestInstance();

            when(mockInterpreter.eval(anyString(), any(Handle.class), eq(userGuid), eq(form.getGuid())))
                    .thenReturn(true);

            form.updateBlockStatuses(handle, mockInterpreter, userGuid, form.getGuid());

            for (FormSection sect : form.getBodySections()) {
                for (FormBlock block : sect.getBlocks()) {
                    if (toggledBlockGuid.equals(block.getGuid())) {
                        assertTrue(block.isShown());
                    }
                }
            }
        });
    }

    @Test
    public void testUpdatedBlockStatuses_withExpr_evalError() {
        thrown.expect(DDPException.class);
        thrown.expectMessage("pex expression");

        PexInterpreter mockInterpreter = mock(PexInterpreter.class);

        TransactionWrapper.useTxn(handle -> {
            FormInstance form = buildTestInstance();

            when(mockInterpreter.eval(anyString(), any(Handle.class), eq(userGuid), eq(form.getGuid())))
                    .thenThrow(new PexException("testing"));

            form.updateBlockStatuses(handle, mockInterpreter, userGuid, form.getGuid());
            fail("expected exception was not thrown");
        });
    }

    private FormInstance buildEmptyTestInstance() {
        return buildEmptyTestInstance(1L);
    }

    private FormInstance buildEmptyTestInstance(long participantUserId) {
        return new FormInstance(
                participantUserId, 1L, 1L, "SOME_CODE", FormType.GENERAL, "SOME_GUID", "name", "subtitle", "CREATED", false,
                ListStyleHint.NUMBER, null, null, null, Instant.now().toEpochMilli(), null, null, null, false
        );
    }

    private FormInstance buildEmptyTestInstanceWithHtmlInTitleAndSubtitle() {
        return new FormInstance(
                1L, 1L, 1L, "SOME_CODE", FormType.GENERAL, "SOME_GUID", "<div>title</div>", "<em>subtitle</em>", "CREATED", false,
                ListStyleHint.NUMBER, null, null, null, Instant.now().toEpochMilli(), null, null, null, false
        );
    }

    private FormInstance buildTestInstance() {
        ContentBlock controlBlock = new ContentBlock(1);
        controlBlock.setGuid(controlBlockGuid);

        ContentBlock toggledBlock = new ContentBlock(2);
        toggledBlock.setGuid(toggledBlockGuid);
        toggledBlock.setShownExpr("true");

        FormSection section = new FormSection(Arrays.asList(controlBlock, toggledBlock));

        FormInstance form = buildEmptyTestInstance();
        form.addBodySections(Collections.singletonList(section));

        return form;
    }
}
