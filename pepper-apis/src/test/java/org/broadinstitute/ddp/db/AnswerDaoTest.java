package org.broadinstitute.ddp.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.JdbiDateAnswer;
import org.broadinstitute.ddp.db.dao.JdbiLanguageCode;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.exception.OperationNotAllowedException;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.question.BoolQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.CompositeQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.DateQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistOptionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.AnswerRow;
import org.broadinstitute.ddp.model.activity.instance.answer.BoolAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.CompositeAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.PicklistAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.SelectedPicklistOption;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.DateFieldType;
import org.broadinstitute.ddp.model.activity.types.DateRenderMode;
import org.broadinstitute.ddp.model.activity.types.PicklistRenderMode;
import org.broadinstitute.ddp.model.activity.types.PicklistSelectMode;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.model.activity.types.TemplateType;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.broadinstitute.ddp.util.Auth0Util;
import org.broadinstitute.ddp.util.GsonUtil;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.broadinstitute.ddp.util.TestUtil;
import org.jdbi.v3.core.Handle;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class AnswerDaoTest extends TxnAwareBaseTest {

    private static AnswerDao answerDao;

    private static TestDataSetupUtil.GeneratedTestData data;
    private static Auth0Util.TestingUser user;
    private static long enLangId;
    private static String instanceGuid;
    private static String boolStableId;
    private static String textStableId;
    private static String picklistStableId;
    private static String dateStableId;
    private static String compStabledId;
    private static String childTextStableId;
    private static String childDateStableId;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @BeforeClass
    public static void setup() {
        answerDao = AnswerDao.fromSqlConfig(sqlConfig);
        TransactionWrapper.useTxn(handle -> {
            data = TestDataSetupUtil.generateBasicUserTestData(handle);
            user = data.getTestingUser();
            enLangId = handle.attach(JdbiLanguageCode.class).getLanguageCodeId("en");
            setupActivityAndInstance(handle);
        });
    }

    private static void setupActivityAndInstance(Handle handle) {
        long timestamp = Instant.now().toEpochMilli();

        boolStableId = "ANS_BOOL_" + timestamp;
        BoolQuestionDef boolDef = BoolQuestionDef.builder().setStableId(boolStableId)
                .setPrompt(new Template(TemplateType.TEXT, null, "bool prompt"))
                .setTrueTemplate(new Template(TemplateType.TEXT, null, "bool yes"))
                .setFalseTemplate(new Template(TemplateType.TEXT, null, "bool no"))
                .build();

        textStableId = "ANS_TEXT_" + timestamp;
        TextQuestionDef textDef = buildTextQuestionDef(textStableId);

        picklistStableId = "ANS_PICKLIST_" + timestamp;
        PicklistQuestionDef picklistDef = PicklistQuestionDef.builder().setStableId(picklistStableId)
                .setSelectMode(PicklistSelectMode.MULTIPLE)
                .setRenderMode(PicklistRenderMode.LIST)
                .setPrompt(new Template(TemplateType.TEXT, null, "picklist prompt"))
                .addOption(new PicklistOptionDef("OPTION1", tmpl("option 1")))
                .addOption(new PicklistOptionDef("OPTION2", tmpl("option 2"), tmpl("details here")))
                .addOption(new PicklistOptionDef("OPT_OTHER", tmpl("other option"), tmpl("specify here")))
                .build();

        dateStableId = "ANS_DATE_" + timestamp;
        DateQuestionDef dateDef = buildDateQuestionDef(dateStableId);

        childTextStableId = "ANS_TEXT_CHILD_" + timestamp;
        TextQuestionDef childTextDef = buildTextQuestionDef(childTextStableId);

        childDateStableId = "ANS_DATE_CHILD" + timestamp;
        DateQuestionDef childDateDef = buildDateQuestionDef(childDateStableId);

        compStabledId = "ANS_COMPOSITE_" + timestamp;
        CompositeQuestionDef compQ = CompositeQuestionDef.builder()
                .setStableId(compStabledId)
                .setPrompt(new Template(TemplateType.TEXT, null, "Comp1"))
                .addChildrenQuestions(childTextDef, childDateDef)
                .setAllowMultiple(true)
                .setAddButtonTemplate(new Template(TemplateType.TEXT, null, "Add Button Text"))
                .setAdditionalItemTemplate(new Template(TemplateType.TEXT, null, "Add Another Item"))
                .build();

        String activityCode = "ANS_ACT_" + timestamp;
        FormActivityDef form = FormActivityDef.generalFormBuilder(activityCode, "v1", data.getStudyGuid())
                .addName(new Translation("en", "test activity"))
                .addSection(new FormSectionDef(null, TestUtil.wrapQuestions(boolDef, textDef, picklistDef, dateDef, compQ)))
                .build();

        handle.attach(ActivityDao.class).insertActivity(form, RevisionMetadata.now(user.getUserId(), "insert test "
                + "activity"));
        assertNotNull(form.getActivityId());

        ActivityInstanceDto instance = handle.attach(ActivityInstanceDao.class).insertInstance(form.getActivityId(),
                user.getUserGuid());
        instanceGuid = instance.getGuid();
    }


    private static TextQuestionDef buildTextQuestionDef(String stableId) {
        return TextQuestionDef.builder().setStableId(stableId)
                .setInputType(TextInputType.TEXT)
                .setPrompt(new Template(TemplateType.TEXT, null, "text prompt"))
                .build();
    }

    private static DateQuestionDef buildDateQuestionDef(String stableId) {
        return DateQuestionDef.builder().setStableId(stableId)
                .setRenderMode(DateRenderMode.TEXT)
                .setPrompt(new Template(TemplateType.TEXT, null, "date prompt"))
                .addFields(DateFieldType.YEAR, DateFieldType.MONTH, DateFieldType.DAY)
                .build();
    }

    private static Template tmpl(String text) {
        return new Template(TemplateType.TEXT, null, text);
    }

    @Test
    public void testGetAnswerIdByGuids_activityNotFound() {
        TransactionWrapper.useTxn(handle -> {
            Answer answer = new BoolAnswer(null, boolStableId, null, true);
            String guid = answerDao.createAnswer(handle, answer, user.getUserGuid(), instanceGuid);
            Long id = answerDao.getAnswerIdByGuids(handle, "abc", guid);
            assertNull(id);
            handle.rollback();
        });
    }

    @Test
    public void testGetAnswerIdByGuids_answerNotFound() {
        TransactionWrapper.useTxn(handle -> {
            Long id = answerDao.getAnswerIdByGuids(handle, instanceGuid, "abc");
            assertNull(id);
        });
    }

    @Test
    public void testGetAnswerIdByGuids() {
        TransactionWrapper.useTxn(handle -> {
            Answer answer = new BoolAnswer(null, boolStableId, null, true);
            String guid = answerDao.createAnswer(handle, answer, user.getUserGuid(), instanceGuid);
            Long id = answerDao.getAnswerIdByGuids(handle, instanceGuid, guid);
            assertNotNull(id);
            handle.rollback();
        });
    }

    @Test
    public void testGetAnswerGuidsForQuestion_activityNotFound() {
        TransactionWrapper.useTxn(handle -> {
            List<String> guids = answerDao.getAnswerGuidsForQuestion(handle, "abc", boolStableId);
            assertNotNull(guids);
            assertTrue(guids.isEmpty());
        });
    }

    @Test
    public void testGetAnswerGuidsForQuestion_questionNotFound() {
        TransactionWrapper.useTxn(handle -> {
            List<String> guids = answerDao.getAnswerGuidsForQuestion(handle, instanceGuid, "abc");
            assertNotNull(guids);
            assertTrue(guids.isEmpty());
        });
    }

    @Test
    public void testGetAnswerGuidsForQuestion() {
        TransactionWrapper.useTxn(handle -> {
            Answer answer = new BoolAnswer(null, boolStableId, null, true);
            String guid = answerDao.createAnswer(handle, answer, user.getUserGuid(), instanceGuid);

            List<String> guids = answerDao.getAnswerGuidsForQuestion(handle, instanceGuid, boolStableId);
            assertNotNull(guids);
            assertEquals(1, guids.size());
            assertEquals(guid, guids.get(0));

            handle.rollback();
        });
    }

    @Test
    public void testGetAnswersForQuestion_activityNotFound() {
        TransactionWrapper.useTxn(handle -> {
            List<Answer> answers = answerDao.getAnswersForQuestion(handle, "abc", boolStableId, enLangId);
            assertNotNull(answers);
            assertTrue(answers.isEmpty());
        });
    }

    @Test
    public void testGetAnswersForQuestion_questionNotFound() {
        TransactionWrapper.useTxn(handle -> {
            List<Answer> answers = answerDao.getAnswersForQuestion(handle, instanceGuid, "abc", enLangId);
            assertNotNull(answers);
            assertTrue(answers.isEmpty());
        });
    }

    @Test
    public void testGetAnswersForQuestion_bool() {
        TransactionWrapper.useTxn(handle -> {
            Answer expected = new BoolAnswer(null, boolStableId, null, true);
            String guid = answerDao.createAnswer(handle, expected, user.getUserGuid(), instanceGuid);

            List<Answer> answers = answerDao.getAnswersForQuestion(handle, instanceGuid, boolStableId, enLangId);
            assertEquals(1, answers.size());

            BoolAnswer actual = (BoolAnswer) answers.get(0);
            assertEquals(guid, actual.getAnswerGuid());
            assertEquals(QuestionType.BOOLEAN, actual.getQuestionType());
            assertEquals(boolStableId, actual.getQuestionStableId());
            assertNotNull(actual.getValue());
            assertEquals(expected.getValue(), actual.getValue());

            handle.rollback();
        });
    }

    @Test
    public void testGetAnswersForQuestion_text() {
        TransactionWrapper.useTxn(handle -> {
            Answer expected = new TextAnswer(null, textStableId, null, "test");
            String guid = answerDao.createAnswer(handle, expected, user.getUserGuid(), instanceGuid);

            List<Answer> answers = answerDao.getAnswersForQuestion(handle, instanceGuid, textStableId, enLangId);
            assertEquals(1, answers.size());

            TextAnswer actual = (TextAnswer) answers.get(0);
            assertEquals(guid, actual.getAnswerGuid());
            assertEquals(QuestionType.TEXT, actual.getQuestionType());
            assertEquals(textStableId, actual.getQuestionStableId());
            assertEquals(expected.getValue(), actual.getValue());

            handle.rollback();
        });
    }

    @Test
    public void testGetAnswersForQuestion_picklist() {
        TransactionWrapper.useTxn(handle -> {
            PicklistAnswer expected = new PicklistAnswer(picklistStableId, null);
            expected.getValue().add(new SelectedPicklistOption("OPTION1"));
            String guid = answerDao.createAnswer(handle, expected, user.getUserGuid(), instanceGuid);

            List<Answer> answers = answerDao.getAnswersForQuestion(handle, instanceGuid, picklistStableId, enLangId);
            assertEquals(1, answers.size());

            PicklistAnswer actual = (PicklistAnswer) answers.get(0);
            assertEquals(guid, actual.getAnswerGuid());
            assertEquals(QuestionType.PICKLIST, actual.getQuestionType());
            assertEquals(picklistStableId, actual.getQuestionStableId());
            assertEquals(1, actual.getValue().size());
            assertEquals("OPTION1", actual.getValue().get(0).getStableId());
            assertNull(actual.getValue().get(0).getDetailText());

            handle.rollback();
        });
    }

    @Test
    public void testGetAnswersForQuestion_date() {
        TransactionWrapper.useTxn(handle -> {
            DateAnswer expected = new DateAnswer(null, dateStableId, null, 2018, 3, 15);
            String guid = answerDao.createAnswer(handle, expected, user.getUserGuid(), instanceGuid);

            List<Answer> answers = answerDao.getAnswersForQuestion(handle, instanceGuid, dateStableId, enLangId);
            assertEquals(1, answers.size());

            DateAnswer actual = (DateAnswer) answers.get(0);
            assertEquals(guid, actual.getAnswerGuid());
            assertEquals(QuestionType.DATE, actual.getQuestionType());
            assertEquals(dateStableId, actual.getQuestionStableId());
            assertEquals(expected.getValue(), actual.getValue());

            handle.rollback();
        });
    }

    @Test
    public void testCreateAnswer_bool() {
        TransactionWrapper.useTxn(handle -> {
            Answer answer = new BoolAnswer(null, boolStableId, null, true);
            String guid = answerDao.createAnswer(handle, answer, user.getUserGuid(), instanceGuid);
            assertNotNull(guid);
            Long id = answerDao.getAnswerIdByGuids(handle, instanceGuid, guid);
            assertNotNull(id);

            List<Answer> answers = answerDao.getAnswersForQuestion(handle, instanceGuid, boolStableId, enLangId);
            assertEquals(1, answers.size());

            handle.rollback();
        });
    }

    @Test
    public void testCreateAnswer_text() {
        TransactionWrapper.useTxn(handle -> {
            Answer answer = new TextAnswer(null, textStableId, null, "test");
            String guid = answerDao.createAnswer(handle, answer, user.getUserGuid(), instanceGuid);
            assertNotNull(guid);
            Long id = answerDao.getAnswerIdByGuids(handle, instanceGuid, guid);
            assertNotNull(id);

            List<Answer> answers = answerDao.getAnswersForQuestion(handle, instanceGuid, textStableId, enLangId);
            assertEquals(1, answers.size());

            handle.rollback();
        });
    }

    @Test
    public void testCreateAnswer_picklist() {
        TransactionWrapper.useTxn(handle -> {
            PicklistAnswer answer = new PicklistAnswer(picklistStableId, null);
            answer.getValue().add(new SelectedPicklistOption("OPTION1"));
            answer.getValue().add(new SelectedPicklistOption("OPTION2"));

            String guid = answerDao.createAnswer(handle, answer, user.getUserGuid(), instanceGuid);
            assertNotNull(guid);
            Long id = answerDao.getAnswerIdByGuids(handle, instanceGuid, guid);
            assertNotNull(id);

            List<Answer> answers = answerDao.getAnswersForQuestion(handle, instanceGuid, picklistStableId, enLangId);
            assertEquals(1, answers.size());

            handle.rollback();
        });
    }

    @Test
    public void testCreateAnswer_picklist_detailsNotAllowed() {
        thrown.expect(OperationNotAllowedException.class);
        thrown.expectMessage("OPTION1 does not allow details");
        TransactionWrapper.useTxn(handle -> {
            PicklistAnswer answer = new PicklistAnswer(picklistStableId, null);
            answer.getValue().add(new SelectedPicklistOption("OPTION1", "detail text that should not be accepted"));
            answerDao.createAnswer(handle, answer, user.getUserGuid(), instanceGuid);
            fail("expected exception not thrown");
        });
    }

    @Test
    public void testCreateAnswer_date() {
        TransactionWrapper.useTxn(handle -> {
            DateAnswer answer = new DateAnswer(null, dateStableId, null, 2018, 3, 15);
            String guid = answerDao.createAnswer(handle, answer, user.getUserGuid(), instanceGuid);
            assertNotNull(guid);
            Long id = answerDao.getAnswerIdByGuids(handle, instanceGuid, guid);
            assertNotNull(id);

            List<Answer> answers = answerDao.getAnswersForQuestion(handle, instanceGuid, dateStableId, enLangId);
            assertEquals(1, answers.size());

            handle.rollback();
        });
    }

    @Test
    public void testCreateAnswer_composite() {
        TransactionWrapper.useTxn(handle -> {
            TextAnswer textAnswer = new TextAnswer(null, childTextStableId, null, "Hello, Pepper!");
            DateAnswer dateAnswer = new DateAnswer(null, childDateStableId, null, 1944, 9, 1);
            CompositeAnswer compAnswer = new CompositeAnswer(null, compStabledId, null);
            compAnswer.addRowOfChildAnswers(textAnswer, dateAnswer);

            String compGuid = answerDao.createAnswer(handle, compAnswer, user.getUserGuid(), instanceGuid);
            assertNotNull(compGuid);

            Long compId = answerDao.getAnswerIdByGuids(handle, instanceGuid, compGuid);
            assertNotNull(compId);

            List<Answer> compAnswers = answerDao.getAnswersForQuestion(handle, instanceGuid, compStabledId, enLangId);
            assertEquals(1, compAnswers.size());

            assertTrue(compAnswers.get(0) instanceof CompositeAnswer);

            List<AnswerRow> childAnswers = compAnswer.getValue();
            //1 row of answers
            assertEquals(1, childAnswers.size());

            AnswerRow firstRowOfAnswers = childAnswers.get(0);

            //Check same as answers provided and should now have GUIDs and DB ids
            assertTrue(firstRowOfAnswers.getValues().get(0) instanceof TextAnswer);
            assertNotNull(firstRowOfAnswers.getValues().get(0).getAnswerId());
            assertNotNull(firstRowOfAnswers.getValues().get(0).getAnswerGuid());
            assertEquals(textAnswer.getValue(), ((TextAnswer) firstRowOfAnswers.getValues().get(0)).getValue());

            assertTrue(firstRowOfAnswers.getValues().get(1) instanceof DateAnswer);
            assertNotNull(firstRowOfAnswers.getValues().get(1).getAnswerId());
            assertNotNull(firstRowOfAnswers.getValues().get(1).getAnswerGuid());
            assertEquals(dateAnswer.getValue(), ((DateAnswer) firstRowOfAnswers.getValues().get(1)).getValue());

            //checkout the generated JSON
            JsonElement jsonForComposite = GsonUtil.standardGson().toJsonTree(compAnswer);
            assertTrue(jsonForComposite.isJsonObject());
            JsonObject jsonObjectComposite = jsonForComposite.getAsJsonObject();
            assertNotNull(jsonObjectComposite.get("value"));
            assertTrue(jsonObjectComposite.get("value").isJsonArray());
            JsonArray valueJsonArray = jsonObjectComposite.get("value").getAsJsonArray();
            assertEquals(childAnswers.size(), valueJsonArray.size());
            assertTrue(valueJsonArray.get(0).isJsonArray());
            JsonArray firstRowJsonArray = valueJsonArray.get(0).getAsJsonArray();
            assertEquals(childAnswers.get(0).getValues().size(), firstRowJsonArray.size());
            firstRowJsonArray.forEach((childJsonElement) -> assertTrue(childJsonElement.isJsonObject()));

            handle.rollback();
        });

    }

    @Test
    public void testUpdateAnswer_composite() {
        TransactionWrapper.useTxn(handle -> {
            TextAnswer textAnswer1 = new TextAnswer(null, childTextStableId, null, "Hello, Pepper!");
            DateAnswer dateAnswer1 = new DateAnswer(null, childDateStableId, null, 1944, 9, 1);
            CompositeAnswer compAnswer = new CompositeAnswer(null, compStabledId, null);
            compAnswer.addRowOfChildAnswers(textAnswer1, dateAnswer1);

            String compGuid = answerDao.createAnswer(handle, compAnswer, user.getUserGuid(), instanceGuid);
            assertNotNull(compGuid);

            List<Answer> savedAnswerList = answerDao.getAnswersForQuestion(handle, instanceGuid, compStabledId,
                    enLangId);

            assertEquals(1, savedAnswerList.size());
            Answer savedAnswer = savedAnswerList.get(0);
            assertTrue(savedAnswer instanceof CompositeAnswer);
            CompositeAnswer savedCompAnswer = (CompositeAnswer) savedAnswer;


            TextAnswer textAnswer2 = new TextAnswer(null, childTextStableId, null, "Goodbye, Pepper!");
            DateAnswer dateAnswer2 = new DateAnswer(null, childDateStableId, null, 1945, 10, 2);
            //flipped on purpose
            savedCompAnswer.addRowOfChildAnswers(dateAnswer2, textAnswer2);

            answerDao.updateAnswerById(handle, instanceGuid, savedCompAnswer.getAnswerId(), savedCompAnswer, user
                    .getUserGuid());

            List<Answer> updatedAnswerList1 = answerDao.getAnswersForQuestion(handle, instanceGuid, compStabledId,
                    enLangId);
            assertEquals(1, updatedAnswerList1.size());
            Answer updatedAnswer1 = updatedAnswerList1.get(0);
            assertTrue(updatedAnswer1 instanceof CompositeAnswer);
            CompositeAnswer updatedCompAnswer1 = (CompositeAnswer) updatedAnswer1;

            assertEquals(2, updatedCompAnswer1.getValue().size());

            List<AnswerRow> updatedCompAnswerChildren1 = updatedCompAnswer1.getValue();

            updatedCompAnswerChildren1.forEach(resultRow ->
                    resultRow.getValues().forEach(answer -> {
                        assertNotNull(answer.getValue());
                        assertNotNull(answer.getAnswerId());
                        assertNotNull(answer.getQuestionStableId());
                    })
            );
            //Check question types
            assertTrue(updatedCompAnswerChildren1.stream().allMatch(rowOfAnswers -> rowOfAnswers.getValues().stream()
                    .anyMatch(childAnswer -> childAnswer.getQuestionType() == QuestionType.TEXT) && rowOfAnswers.getValues()
                    .stream()
                    .anyMatch(childAnswer -> childAnswer.getQuestionType() == QuestionType.DATE)));

            //checkTextAnswers
            TextAnswer[] textAnswers = {textAnswer1, textAnswer2};

            for (int i = 0; i < updatedCompAnswerChildren1.size(); i++) {
                AnswerRow answerRow = updatedCompAnswerChildren1.get(i);
                Optional<Answer> textAnswer = answerRow.getValues().stream().filter(answer -> answer
                        .getQuestionType() == QuestionType.TEXT).findFirst();
                assertTrue(textAnswer.isPresent());
                assertEquals(textAnswers[i].getValue(), textAnswer.get().getValue());
                assertEquals(textAnswers[i].getQuestionStableId(), textAnswer.get().getQuestionStableId());
            }
            DateAnswer[] dateAnswers = {dateAnswer1, dateAnswer2};
            for (int i = 0; i < updatedCompAnswerChildren1.size(); i++) {
                AnswerRow answerRow = updatedCompAnswerChildren1.get(i);
                Optional<Answer> dateAnswer = answerRow.getValues().stream().filter(answer -> answer
                        .getQuestionType() == QuestionType.DATE).findFirst();
                assertTrue(dateAnswer.isPresent());
                assertEquals(dateAnswers[i].getValue(), dateAnswer.get().getValue());
                assertEquals(dateAnswers[i].getQuestionStableId(), dateAnswer.get().getQuestionStableId());
            }

            assertNotNull(compGuid);

        });

    }

    @Test
    public void testUpdateAnswerById_bool() {
        TransactionWrapper.useTxn(handle -> {
            Answer answer = new BoolAnswer(null, boolStableId, null, true);
            String guid = answerDao.createAnswer(handle, answer, user.getUserGuid(), instanceGuid);
            Long id = answerDao.getAnswerIdByGuids(handle, instanceGuid, guid);

            answer = new BoolAnswer(null, boolStableId, guid, false);
            answerDao.updateAnswerById(handle, instanceGuid, id, answer, user.getUserGuid());

            List<Answer> answers = answerDao.getAnswersForQuestion(handle, instanceGuid, boolStableId, enLangId);
            assertEquals(1, answers.size());

            BoolAnswer actual = (BoolAnswer) answers.get(0);
            assertEquals(guid, actual.getAnswerGuid());
            assertEquals(id, actual.getAnswerId());
            assertFalse(actual.getValue());

            handle.rollback();
        });
    }

    @Test
    public void testUpdateAnswerById_text() {
        TransactionWrapper.useTxn(handle -> {
            Answer answer = new TextAnswer(null, textStableId, null, "old");
            String guid = answerDao.createAnswer(handle, answer, user.getUserGuid(), instanceGuid);
            Long id = answerDao.getAnswerIdByGuids(handle, instanceGuid, guid);

            answer = new TextAnswer(null, textStableId, null, "new");
            answerDao.updateAnswerById(handle, instanceGuid, id, answer, user.getUserGuid());

            List<Answer> answers = answerDao.getAnswersForQuestion(handle, instanceGuid, textStableId, enLangId);
            assertEquals(1, answers.size());

            TextAnswer actual = (TextAnswer) answers.get(0);
            assertEquals(guid, actual.getAnswerGuid());
            assertEquals(id, actual.getAnswerId());
            assertEquals("new", actual.getValue());

            handle.rollback();
        });
    }

    @Test
    public void testUpdateAnswerById_picklist() {
        TransactionWrapper.useTxn(handle -> {
            List<SelectedPicklistOption> selected = new ArrayList<>();
            selected.add(new SelectedPicklistOption("OPTION1"));

            PicklistAnswer answer = new PicklistAnswer(null, picklistStableId, null, selected);
            String guid = answerDao.createAnswer(handle, answer, user.getUserGuid(), instanceGuid);
            Long id = answerDao.getAnswerIdByGuids(handle, instanceGuid, guid);

            selected.add(new SelectedPicklistOption("OPTION2"));
            answer = new PicklistAnswer(null, picklistStableId, guid, selected);
            answerDao.updateAnswerById(handle, instanceGuid, id, answer, user.getUserGuid());

            List<Answer> answers = answerDao.getAnswersForQuestion(handle, instanceGuid, picklistStableId, enLangId);
            assertEquals(1, answers.size());

            PicklistAnswer actual = (PicklistAnswer) answers.get(0);
            assertEquals(guid, actual.getAnswerGuid());
            assertEquals(id, actual.getAnswerId());

            List<String> optionStableIds = actual.getValue().stream()
                    .map(SelectedPicklistOption::getStableId)
                    .collect(Collectors.toList());
            assertEquals(2, optionStableIds.size());
            assertTrue(optionStableIds.contains("OPTION1"));
            assertTrue(optionStableIds.contains("OPTION2"));

            handle.rollback();
        });
    }

    @Test
    public void testUpdateAnswerById_date() {
        TransactionWrapper.useTxn(handle -> {
            DateAnswer old = new DateAnswer(null, dateStableId, null, 2018, 3, 15);
            String guid = answerDao.createAnswer(handle, old, user.getUserGuid(), instanceGuid);
            Long id = answerDao.getAnswerIdByGuids(handle, instanceGuid, guid);

            DateAnswer expected = new DateAnswer(null, dateStableId, guid, 2020, 4, null);
            answerDao.updateAnswerById(handle, instanceGuid, id, expected, user.getUserGuid());

            DateAnswer actual = handle.attach(JdbiDateAnswer.class).getById(id).get();

            assertNotNull(actual);
            assertEquals(id, actual.getAnswerId());
            assertEquals(guid, actual.getAnswerGuid());
            assertEquals(dateStableId, actual.getQuestionStableId());
            assertEquals(expected.getValue(), actual.getValue());

            handle.rollback();
        });
    }

    @Test
    public void testDeleteAnswerByIdAndType_bool() {
        TransactionWrapper.useTxn(handle -> {
            Answer answer = new BoolAnswer(null, boolStableId, null, true);
            String guid = answerDao.createAnswer(handle, answer, user.getUserGuid(), instanceGuid);
            Long id = answerDao.getAnswerIdByGuids(handle, instanceGuid, guid);
            assertNotNull(id);

            answerDao.deleteAnswerByIdAndType(handle, id, QuestionType.BOOLEAN);
            Long res = answerDao.getAnswerIdByGuids(handle, instanceGuid, guid);
            assertNull(res);
        });
    }

    @Test
    public void testDeleteAnswerByIdAndType_text() {
        TransactionWrapper.useTxn(handle -> {
            Answer answer = new TextAnswer(null, textStableId, null, "test");
            String guid = answerDao.createAnswer(handle, answer, user.getUserGuid(), instanceGuid);
            Long id = answerDao.getAnswerIdByGuids(handle, instanceGuid, guid);
            assertNotNull(id);

            answerDao.deleteAnswerByIdAndType(handle, id, QuestionType.TEXT);
            Long res = answerDao.getAnswerIdByGuids(handle, instanceGuid, guid);
            assertNull(res);
        });
    }

    @Test
    public void testDeleteAnswerByIdAndType_picklist() {
        TransactionWrapper.useTxn(handle -> {
            PicklistAnswer answer = new PicklistAnswer(picklistStableId, null);
            answer.getValue().add(new SelectedPicklistOption("OPTION1"));
            String guid = answerDao.createAnswer(handle, answer, user.getUserGuid(), instanceGuid);
            Long id = answerDao.getAnswerIdByGuids(handle, instanceGuid, guid);
            assertNotNull(id);

            answerDao.deleteAnswerByIdAndType(handle, id, QuestionType.PICKLIST);
            Long res = answerDao.getAnswerIdByGuids(handle, instanceGuid, guid);
            assertNull(res);
        });
    }

    @Test
    public void testDeleteAnswerByIdAndType_date() {
        TransactionWrapper.useTxn(handle -> {
            DateAnswer ans = new DateAnswer(null, dateStableId, null, 2018, 3, 15);
            String guid = answerDao.createAnswer(handle, ans, user.getUserGuid(), instanceGuid);
            Long id = answerDao.getAnswerIdByGuids(handle, instanceGuid, guid);
            assertNotNull(id);

            answerDao.deleteAnswerByIdAndType(handle, id, QuestionType.DATE);
            Long res = answerDao.getAnswerIdByGuids(handle, instanceGuid, guid);
            assertNull(res);
        });
    }
}
