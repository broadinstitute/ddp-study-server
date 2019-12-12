package org.broadinstitute.ddp.db.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Consumer;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.PicklistOptionDto;
import org.broadinstitute.ddp.db.dto.RevisionDto;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistOptionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.template.TemplateVariable;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.activity.types.PicklistRenderMode;
import org.broadinstitute.ddp.model.activity.types.TemplateType;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.broadinstitute.ddp.util.TestUtil;
import org.jdbi.v3.core.Handle;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class PicklistQuestionDaoTest extends TxnAwareBaseTest {

    private static TestDataSetupUtil.GeneratedTestData testData;
    private static String userGuid;
    private static long userId;

    private PicklistQuestionDao dao;
    private ActivityDao activityDao;
    private ActivityInstanceDao instanceDao;
    private JdbiPicklistOption jdbiOption;

    private FormActivityDef activity;
    private PicklistQuestionDef question;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @BeforeClass
    public static void setup() {
        TransactionWrapper.useTxn(handle -> {
            testData = TestDataSetupUtil.generateBasicUserTestData(handle);
            userGuid = testData.getUserGuid();
            userId = testData.getUserId();
        });
    }

    private ActivityVersionDto setupTestActivity() {
        return setupTestActivity(RevisionMetadata.now(testData.getUserId(), "test"));
    }

    private ActivityVersionDto setupTestActivity(RevisionMetadata meta) {
        String stableId = "PQ" + Instant.now().toEpochMilli();
        question = PicklistQuestionDef.buildSingleSelect(PicklistRenderMode.LIST, stableId, textTmpl("prompt"))
                .addOption(new PicklistOptionDef("PO1", textTmpl("option1")))
                .build();
        String actCode = "ACT" + Instant.now().toEpochMilli();
        activity = FormActivityDef.generalFormBuilder(actCode, "v1", testData.getStudyGuid())
                .addName(new Translation("en", "activity"))
                .addSection(new FormSectionDef(null, TestUtil.wrapQuestions(question)))
                .build();
        return activityDao.insertActivity(activity, meta);
    }

    private void rolledbackTest(Consumer<Handle> test) {
        TransactionWrapper.useTxn(handle -> {
            dao = handle.attach(PicklistQuestionDao.class);
            activityDao = handle.attach(ActivityDao.class);
            instanceDao = handle.attach(ActivityInstanceDao.class);
            jdbiOption = handle.attach(JdbiPicklistOption.class);

            test.accept(handle);

            handle.rollback();
        });
    }

    private String createActivityInstance(FormActivityDef form) {
        return instanceDao.insertInstance(form.getActivityId(), userGuid).getGuid();
    }

    private Template textTmpl(String text) {
        return new Template(TemplateType.TEXT, null, text);
    }

    @Test
    public void testAddOption() {
        rolledbackTest(handle -> {
            ActivityVersionDto version1 = setupTestActivity();

            // Create an activity instance
            String instanceGuid = createActivityInstance(activity);
            Optional<PicklistOptionDto> opt = jdbiOption.getByStableId(question.getQuestionId(), "PO1", instanceGuid);
            assertTrue(opt.isPresent());

            // Add a new option using a new version
            RevisionMetadata meta = new RevisionMetadata(version1.getRevStart() + 5000L, userId, "test");
            ActivityVersionDto version2 = activityDao.changeVersion(activity.getActivityId(), "v2", meta);
            RevisionDto revDto = RevisionDto.fromStartMetadata(version2.getRevId(), meta);
            PicklistOptionDef option2 = new PicklistOptionDef("PO2", textTmpl("option2"));
            dao.addOption(question.getQuestionId(), option2, 1, revDto);
            assertNotNull(option2.getOptionId());

            // Ensure new option is added but not part of existing instance
            opt = jdbiOption.getByStableId(question.getQuestionId(), "PO2", instanceGuid);
            assertFalse(opt.isPresent());
            assertTrue(jdbiOption.isCurrentlyActive(question.getQuestionId(), "PO2"));
        });
    }

    @Test
    public void testAddOption_otherOptionWithDetails() {
        rolledbackTest(handle -> {
            ActivityVersionDto version1 = setupTestActivity();

            // Create an activity instance
            String instanceGuid = createActivityInstance(activity);
            Optional<PicklistOptionDto> opt = jdbiOption.getByStableId(question.getQuestionId(), "PO_OTHER", instanceGuid);
            assertFalse(opt.isPresent());

            // Add other option with details using a new version
            RevisionMetadata meta = new RevisionMetadata(version1.getRevStart() + 5000L, userId, "test");
            ActivityVersionDto version2 = activityDao.changeVersion(activity.getActivityId(), "v2", meta);
            RevisionDto revDto = RevisionDto.fromStartMetadata(version2.getRevId(), meta);
            PicklistOptionDef otherOption = new PicklistOptionDef("PO_OTHER",
                    textTmpl("other option label"), textTmpl("detail field label"));
            dao.addOption(question.getQuestionId(), otherOption, 1, revDto);
            assertNotNull(otherOption.getOptionId());

            // Ensure other option is added but not part of existing instance
            opt = jdbiOption.getByStableId(question.getQuestionId(), "PO_OTHER", instanceGuid);
            assertFalse(opt.isPresent());
            assertTrue(jdbiOption.isCurrentlyActive(question.getQuestionId(), "PO_OTHER"));
        });
    }

    @Test
    public void testAddOption_alreadyActive() {
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("PO1");
        rolledbackTest(handle -> {
            ActivityVersionDto version1 = setupTestActivity();

            // Add existing option
            RevisionMetadata meta = new RevisionMetadata(version1.getRevStart() + 5000L, userId, "test");
            ActivityVersionDto version2 = activityDao.changeVersion(activity.getActivityId(), "v2", meta);
            RevisionDto revDto = RevisionDto.fromStartMetadata(version2.getRevId(), meta);
            PicklistOptionDef option1 = new PicklistOptionDef("PO1", textTmpl("option1"));
            dao.addOption(question.getQuestionId(), option1, 1, revDto);

            fail("Expected exception not thrown");
        });
    }

    @Test
    public void testAddOption_ordering_headOfList() {
        rolledbackTest(handle -> {
            RevisionMetadata meta = RevisionMetadata.now(userId, "test");
            ActivityVersionDto version1 = setupTestActivity(meta);

            RevisionDto revDto = RevisionDto.fromStartMetadata(version1.getRevId(), meta);
            PicklistOptionDef option2 = new PicklistOptionDef("PO2", textTmpl("option2"));

            // Add to start of option list.
            dao.addOption(question.getQuestionId(), option2, 0, revDto);

            List<PicklistOptionDto> options = jdbiOption.findAllActiveOrderedOptionsByQuestionId(question.getQuestionId());
            assertEquals(2, options.size());
            assertEquals("PO2", options.get(0).getStableId());
            assertEquals("PO1", options.get(1).getStableId());
        });
    }

    @Test
    public void testAddOption_ordering_tailOfList() {
        rolledbackTest(handle -> {
            RevisionMetadata meta = RevisionMetadata.now(userId, "test");
            ActivityVersionDto version1 = setupTestActivity(meta);

            RevisionDto revDto = RevisionDto.fromStartMetadata(version1.getRevId(), meta);
            PicklistOptionDef option2 = new PicklistOptionDef("PO2", textTmpl("option2"));

            // Add to the end with some randomly large position.
            dao.addOption(question.getQuestionId(), option2, 25, revDto);

            List<PicklistOptionDto> options = jdbiOption.findAllActiveOrderedOptionsByQuestionId(question.getQuestionId());
            assertEquals(2, options.size());
            assertEquals("PO1", options.get(0).getStableId());
            assertEquals("PO2", options.get(1).getStableId());
        });
    }

    @Test
    public void testAddOption_ordering_middleOfList() {
        rolledbackTest(handle -> {
            RevisionMetadata meta = RevisionMetadata.now(userId, "test");
            ActivityVersionDto version1 = setupTestActivity(meta);

            RevisionDto revDto = RevisionDto.fromStartMetadata(version1.getRevId(), meta);
            PicklistOptionDef option2 = new PicklistOptionDef("PO2", textTmpl("option2"));
            PicklistOptionDef option3 = new PicklistOptionDef("PO3", textTmpl("option3"));
            dao.addOption(question.getQuestionId(), option2, 1, revDto);

            // Add to middle of option list.
            dao.addOption(question.getQuestionId(), option3, 1, revDto);

            List<PicklistOptionDto> options = jdbiOption.findAllActiveOrderedOptionsByQuestionId(question.getQuestionId());
            assertEquals(3, options.size());
            assertEquals("PO1", options.get(0).getStableId());
            assertEquals("PO3", options.get(1).getStableId());
            assertEquals("PO2", options.get(2).getStableId());
        });
    }

    @Test
    public void testDisableOption() {
        rolledbackTest(handle -> {
            RevisionMetadata meta = RevisionMetadata.now(userId, "test");
            ActivityVersionDto version1 = setupTestActivity(meta);

            // Add option 2 to same version
            RevisionDto revDto = RevisionDto.fromStartMetadata(version1.getRevId(), meta);
            Template tmpl2 = new Template(TemplateType.HTML, null, "<p>$var</p>");
            tmpl2.addVariable(new TemplateVariable("var", Arrays.asList(
                    new Translation("en", "option 2 english"),
                    new Translation("ru", "option 2 russian"))));
            PicklistOptionDef option2 = new PicklistOptionDef("PO2", tmpl2);
            dao.addOption(question.getQuestionId(), option2, 1, revDto);

            // Create an activity instance
            String instanceGuid = createActivityInstance(activity);
            List<PicklistOptionDto> options = jdbiOption.findAllOrderedOptions(question.getQuestionId(), instanceGuid);
            assertEquals(2, options.size());
            assertEquals("PO1", options.get(0).getStableId());
            assertEquals("PO2", options.get(1).getStableId());

            // Terminate option 2 in new version
            meta = new RevisionMetadata(version1.getRevStart() + 5000L, userId, "test");
            ActivityVersionDto version2 = activityDao.changeVersion(activity.getActivityId(), "v2", meta);
            dao.disableOption(question.getQuestionId(), "PO2", meta);

            // Ensure option 2 shows up for old instance but not new ones
            Optional<PicklistOptionDto> opt = jdbiOption.getByStableId(question.getQuestionId(), "PO2", instanceGuid);
            assertTrue(opt.isPresent());
            assertFalse(jdbiOption.isCurrentlyActive(question.getQuestionId(), "PO2"));

            String instanceGuid2 = instanceDao.insertInstance(activity.getActivityId(), userGuid, userGuid,
                    InstanceStatusType.CREATED, false, version2.getRevStart()).getGuid();
            opt = jdbiOption.getByStableId(question.getQuestionId(), "PO2", instanceGuid2);
            assertFalse(opt.isPresent());
        });
    }

    @Test
    public void testDisableOption_notCurrentlyActive() {
        thrown.expect(NoSuchElementException.class);
        thrown.expectMessage("PO2");
        rolledbackTest(handle -> {
            ActivityVersionDto version1 = setupTestActivity();

            // Terminate non-existing option
            RevisionMetadata meta = new RevisionMetadata(version1.getRevStart() + 5000L, userId, "test");
            dao.disableOption(question.getQuestionId(), "PO2", meta);

            fail("Expected exception not thrown");
        });
    }
}
