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
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.constants.TestConstants;
import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.content.HtmlConverter;
import org.broadinstitute.ddp.content.I18nContentRenderer;
import org.broadinstitute.ddp.content.I18nTemplateConstants;
import org.broadinstitute.ddp.content.RenderValueProvider;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dao.UserProfileDao;
import org.broadinstitute.ddp.db.dao.UserProfileSql;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.BoolAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
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
        var mockUserDao = mock(UserDao.class);
        var mockProfleDao = mock(UserProfileDao.class);
        var mockActInstDao = mock(ActivityInstanceDao.class);
        doReturn(mockUserDao).when(mockHandle).attach(UserDao.class);
        doReturn(mockProfleDao).when(mockHandle).attach(UserProfileDao.class);
        doReturn(mockActInstDao).when(mockHandle).attach(ActivityInstanceDao.class);
        doReturn(Optional.empty()).when(mockUserDao).findUserByGuid(anyString());
        doReturn(Optional.empty()).when(mockProfleDao).findProfileByUserId(anyLong());
        doNothing().when(mockActInstDao).saveSubstitutions(anyLong(), any());
    }

    @Test
    public void testRenderContent_contentBlock_standardStyle() {
        Map<Long, String> fixture = new HashMap<>();
        fixture.put(1L, "<p>this is title</p>");
        fixture.put(2L, "<p>this is body</p>");

        I18nContentRenderer spyRenderer = spy(I18nContentRenderer.class);
        doReturn(fixture).when(spyRenderer).bulkRender(any(), anySet(), anyLong(), any(), anyLong());
        doCallRealMethod().when(spyRenderer).renderToString(anyString(), any());

        ContentBlock content = new ContentBlock(1L, 2L);
        FormSection s1 = new FormSection(Collections.singletonList(content));
        FormInstance form = buildEmptyTestInstance();
        form.addBodySections(Collections.singletonList(s1));

        form.renderContent(mockHandle, spyRenderer, 1L, ContentStyle.STANDARD);
        assertEquals(fixture.get(1L), content.getTitle());
        assertEquals(fixture.get(2L), content.getBody());
    }

    @Test
    public void testRenderContent_contentBlock_basicStyle() {
        Map<Long, String> fixture = new HashMap<>();
        fixture.put(1L, "<p>this is title</p>");
        fixture.put(2L, "<p>this is body</p>");

        I18nContentRenderer spyRenderer = spy(I18nContentRenderer.class);
        doReturn(fixture).when(spyRenderer).bulkRender(any(), anySet(), anyLong(), any(), anyLong());
        doCallRealMethod().when(spyRenderer).renderToString(anyString(), any());

        ContentBlock content = new ContentBlock(1L, 2L);
        FormSection s1 = new FormSection(Collections.singletonList(content));
        FormInstance form = buildEmptyTestInstanceWithHtmlInTitleAndSubtitle();
        form.addBodySections(Collections.singletonList(s1));

        form.renderContent(mockHandle, spyRenderer, 1L, ContentStyle.BASIC);
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

        I18nContentRenderer spyRenderer = spy(I18nContentRenderer.class);
        doReturn(fixture).when(spyRenderer).bulkRender(any(), anySet(), anyLong(), any(), anyLong());
        doCallRealMethod().when(spyRenderer).renderToString(anyString(), any());

        Question question = new TextQuestion("sid", 1, null, Collections.emptyList(), Collections.emptyList(),
                TextInputType.TEXT);
        QuestionBlock block = new QuestionBlock(question);
        FormSection s1 = new FormSection(Collections.singletonList(block));
        FormInstance form = buildEmptyTestInstance();
        form.addBodySections(Collections.singletonList(s1));

        form.renderContent(mockHandle, spyRenderer, 1L, ContentStyle.STANDARD);
        assertTrue(HtmlConverter.hasSameValue(fixture.get(1L), question.getPrompt()));
        assertEquals(fixture.get(2L), question.getTextPrompt());
    }

    @Test
    public void testRenderContent_questionBlock_basicStyle() {
        Map<Long, String> fixture = new HashMap<>();
        fixture.put(1L, "this is <b>bold</b> prompt");

        I18nContentRenderer spyRenderer = spy(I18nContentRenderer.class);
        doReturn(fixture).when(spyRenderer).bulkRender(any(), anySet(), anyLong(), any(), anyLong());
        doCallRealMethod().when(spyRenderer).renderToString(anyString(), any());

        Question question = new TextQuestion("sid", 1, null, Collections.emptyList(), Collections.emptyList(),
                TextInputType.TEXT);
        QuestionBlock block = new QuestionBlock(question);
        FormSection s1 = new FormSection(Collections.singletonList(block));
        FormInstance form = buildEmptyTestInstance();
        form.addBodySections(Collections.singletonList(s1));

        form.renderContent(mockHandle, spyRenderer, 1L, ContentStyle.BASIC);

        assertTrue(HtmlConverter.hasSameValue(fixture.get(1L), question.getPrompt()));
        assertEquals("this is bold prompt", question.getTextPrompt());
    }

    @Test
    public void testRenderContent_sectionName_standardStyle() {
        Map<Long, String> fixture = new HashMap<>();
        fixture.put(1L, "this is <strong>bold</strong> section name");

        I18nContentRenderer spyRenderer = spy(I18nContentRenderer.class);
        doReturn(fixture).when(spyRenderer).bulkRender(any(), anySet(), anyLong(), any(), anyLong());
        doCallRealMethod().when(spyRenderer).renderToString(anyString(), any());

        FormSection s1 = new FormSection(1L);
        FormInstance form = buildEmptyTestInstance();
        form.addBodySections(Collections.singletonList(s1));

        form.renderContent(mockHandle, spyRenderer, 1L, ContentStyle.STANDARD);
        assertEquals(fixture.get(1L), s1.getName());
    }

    @Test
    public void testRenderContent_sectionName_basicStyle() {
        Map<Long, String> fixture = new HashMap<>();
        fixture.put(1L, "this is <strong>bold</strong> section name");

        I18nContentRenderer spyRenderer = spy(I18nContentRenderer.class);
        doReturn(fixture).when(spyRenderer).bulkRender(any(), anySet(), anyLong(), any(), anyLong());
        doCallRealMethod().when(spyRenderer).renderToString(anyString(), any());

        FormSection s1 = new FormSection(1L);
        FormInstance form = buildEmptyTestInstance();
        form.addBodySections(Collections.singletonList(s1));

        form.renderContent(mockHandle, spyRenderer, 1L, ContentStyle.BASIC);
        assertEquals("this is bold section name", s1.getName());
    }

    @Test
    public void testRenderContent_specialVarsContext() {
        TransactionWrapper.useTxn(handle -> {
            var testData = TestDataSetupUtil.generateBasicUserTestData(handle);
            handle.attach(UserProfileSql.class).upsertFirstName(testData.getUserId(), "foo");
            handle.attach(UserProfileSql.class).upsertLastName(testData.getUserId(), "bar");

            I18nContentRenderer spyRenderer = spy(I18nContentRenderer.class);
            doReturn(Collections.emptyMap()).when(spyRenderer).bulkRender(any(), anySet(), anyLong(), any(), anyLong());
            doCallRealMethod().when(spyRenderer).renderToString(anyString(), any());
            doReturn(handle.attach(UserProfileDao.class)).when(mockHandle).attach(UserProfileDao.class);

            FormInstance form = buildEmptyTestInstance(testData.getUserId());
            form.renderContent(mockHandle, spyRenderer, 1L, ContentStyle.BASIC);

            verify(spyRenderer, times(1)).bulkRender(any(), anySet(), anyLong(), argThat(context -> {
                assertNotNull(context);
                assertNotNull(context.get(I18nTemplateConstants.DDP));
                RenderValueProvider provider = (RenderValueProvider) context.get(I18nTemplateConstants.DDP);
                var snaphost = provider.getSnapshot();
                assertEquals(LocalDate.now().toString(), snaphost.get(I18nTemplateConstants.Snapshot.DATE));
                assertEquals("foo", snaphost.get(I18nTemplateConstants.Snapshot.PARTICIPANT_FIRST_NAME));
                assertEquals("bar", snaphost.get(I18nTemplateConstants.Snapshot.PARTICIPANT_LAST_NAME));
                return true;
            }), anyLong());

            handle.rollback();
        });
    }

    @Test
    public void testRenderContent_answerSubstitutions() {
        Map<Long, String> fixture = new HashMap<>();
        fixture.put(1L, "some template");

        var spyRenderer = spy(I18nContentRenderer.class);
        doReturn(fixture).when(spyRenderer).bulkRender(any(), anySet(), anyLong(), any(), anyLong());
        doCallRealMethod().when(spyRenderer).renderToString(anyString(), any());

        String title = "$ddp.answer(\"Q_TITLE\",\"fallback\")";
        String subtitle = "$ddp.answer(\"Q_SUBTITLE\",\"fallback\")";
        var form = new FormInstance(1L, 1L, 1L, "ACT", FormType.GENERAL, "guid",
                title, subtitle, "CREATED", null, ListStyleHint.NONE, null,
                111L, 112L, 1L, null, null, null, false, false, false, false, 0);

        var body = new FormSection(List.of(
                new QuestionBlock(new TextQuestion("Q_TITLE", 1L, null, List.of(
                        new TextAnswer(null, "Q_TITLE", null, "title-answer")),
                        List.of(), TextInputType.TEXT)),
                new QuestionBlock(new TextQuestion("Q_SUBTITLE", 1L, null, List.of(
                        new TextAnswer(null, "Q_SUBTITLE", null, "subtitle-answer")),
                        List.of(), TextInputType.TEXT))));
        form.addBodySections(List.of(body));

        form.renderContent(mockHandle, spyRenderer, 1L, ContentStyle.BASIC);
        assertEquals("title-answer", form.getTitle());
        assertEquals("subtitle-answer", form.getSubtitle());
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
            form.updateBlockStatuses(handle, interpreter, userGuid, userGuid, form.getGuid(), null);
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
            form.updateBlockStatuses(handle, interpreter, userGuid, userGuid, form.getGuid(), null);
            assertFalse(content.isShown());

            content.setShownExpr("true");
            form.updateBlockStatuses(handle, interpreter, userGuid, userGuid, form.getGuid(), null);
            assertTrue(content.isShown());
        });
    }

    @Test
    public void testUpdateBlockStatuses_withoutExpr_defaultShown() {
        TransactionWrapper.useTxn(handle -> {
            FormInstance form = buildTestInstance();
            form.updateBlockStatuses(handle, interpreter, userGuid, userGuid, form.getGuid(), null);

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

            when(mockInterpreter.eval(anyString(), any(Handle.class), eq(userGuid), eq(userGuid), eq(form.getGuid()), any()))
                    .thenReturn(true);

            form.updateBlockStatuses(handle, mockInterpreter, userGuid, userGuid, form.getGuid(), null);

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

            when(mockInterpreter.eval(anyString(), any(Handle.class), eq(userGuid), eq(userGuid), eq(form.getGuid()), any()))
                    .thenThrow(new PexException("testing"));

            form.updateBlockStatuses(handle, mockInterpreter, userGuid, userGuid, form.getGuid(), null);
            fail("expected exception was not thrown");
        });
    }

    @Test
    public void testCollectHiddenAnswers() {
        var q1 = new QuestionBlock(new BoolQuestion("b1", 1L,
                List.of(new BoolAnswer(1L, "b1", "1", true)), List.of(), 2L, 3L));
        q1.setShown(true);
        var q2 = new QuestionBlock(new BoolQuestion("b2", 1L,
                List.of(new BoolAnswer(2L, "b2", "2", false)), List.of(), 2L, 3L));
        q2.setShown(false);
        var cond1 = new ConditionalBlock(new TextQuestion("t1", 1L, null,
                List.of(new TextAnswer(3L, "t1", "3", "cond1")), List.of(), TextInputType.TEXT));
        var nest1 = new QuestionBlock(new TextQuestion("t2", 1L, null,
                List.of(new TextAnswer(4L, "t2", "4", "nest1")), List.of(), TextInputType.TEXT));
        cond1.getNested().add(nest1);
        cond1.setShown(false);
        nest1.setShown(false);

        var section = new FormSection(List.of(q1, q2, cond1));
        var form = buildEmptyTestInstance();
        form.addBodySections(List.of(section));

        var hidden = form.collectHiddenAnswers();
        assertNotNull(hidden);
        assertEquals(3, hidden.size());

        var answerIds = hidden.stream().map(Answer::getAnswerId).collect(Collectors.toSet());
        assertTrue(answerIds.containsAll(Set.of(2L, 3L, 4L)));
    }

    private FormInstance buildEmptyTestInstance() {
        return buildEmptyTestInstance(1L);
    }

    private FormInstance buildEmptyTestInstance(long participantUserId) {
        return new FormInstance(
                participantUserId, 1L, 1L, "SOME_CODE", FormType.GENERAL, "SOME_GUID", "name", "subtitle", "CREATED", false,
                ListStyleHint.NUMBER, null, null, null, Instant.now().toEpochMilli(), null, null, null,
                false, false, false, false, 0
        );
    }

    private FormInstance buildEmptyTestInstanceWithHtmlInTitleAndSubtitle() {
        return new FormInstance(
                1L, 1L, 1L, "SOME_CODE", FormType.GENERAL, "SOME_GUID", "<div>title</div>", "<em>subtitle</em>", "CREATED", false,
                ListStyleHint.NUMBER, null, null, null, Instant.now().toEpochMilli(), null, null, null,
                false, false, false, false, 0
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
