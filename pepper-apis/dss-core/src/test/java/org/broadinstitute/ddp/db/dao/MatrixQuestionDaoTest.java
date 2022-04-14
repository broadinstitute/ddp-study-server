package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.cache.CacheService;
import org.broadinstitute.ddp.cache.DaoBuilder;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.MatrixGroupDto;
import org.broadinstitute.ddp.db.dto.MatrixOptionDto;
import org.broadinstitute.ddp.db.dto.MatrixRowDto;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.db.dto.RevisionDto;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.question.MatrixGroupDef;
import org.broadinstitute.ddp.model.activity.definition.question.MatrixOptionDef;
import org.broadinstitute.ddp.model.activity.definition.question.MatrixQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.MatrixRowDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.MatrixSelectMode;
import org.broadinstitute.ddp.model.activity.types.TemplateType;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.broadinstitute.ddp.util.TestUtil;
import org.jdbi.v3.core.Handle;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


@RunWith(Parameterized.class)
public class MatrixQuestionDaoTest extends TxnAwareBaseTest {

    private static TestDataSetupUtil.GeneratedTestData testData;
    private static String userGuid;
    private static long userId;
    private final DaoBuilder<MatrixQuestionDao> daoBuilder;
    private final boolean isCachedDao;

    private ActivityDao activityDao;
    private ActivityInstanceDao instanceDao;
    private JdbiMatrixOption jdbiOption;
    private JdbiMatrixGroup jdbiGroup;
    private JdbiMatrixRow jdbiRow;

    private FormActivityDef activity;
    private MatrixQuestionDef question;
    private MatrixQuestionDef question2;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    public MatrixQuestionDaoTest(DaoBuilder daoBuilder, boolean isCachedDao) {
        this.daoBuilder = daoBuilder;
        this.isCachedDao = isCachedDao;
    }

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        Object[] uncached = {(DaoBuilder<MatrixQuestionDao>) (handle) -> handle.attach(MatrixQuestionDao.class), false};
        Object[] cached = {(DaoBuilder<MatrixQuestionCachedDao>) (handle) -> new MatrixQuestionCachedDao(handle), true};
        return List.of(uncached, cached);
    }

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
        String stableId = "S_MAQ" + Instant.now().toEpochMilli();
        String stableId2 = "M_MAQ" + Instant.now().toEpochMilli();

        question = MatrixQuestionDef.builder(MatrixSelectMode.SINGLE, stableId, textTmpl("matrix prompt"))
                .addOptions(List.of(
                        new MatrixOptionDef("SINGLE_OPT_1", textTmpl("option 1"), "DEFAULT"),
                        new MatrixOptionDef("SINGLE_OPT_2", textTmpl("option 2"), "DEFAULT")))
                .addRows(List.of(
                        new MatrixRowDef("SINGLE_ROW_1", textTmpl("row 1")),
                        new MatrixRowDef("SINGLE_ROW_2", textTmpl("row 2"))))
                .addGroup(new MatrixGroupDef("DEFAULT", null))
                .build();

        question2 = MatrixQuestionDef.builder(MatrixSelectMode.MULTIPLE, stableId2, textTmpl("matrix prompt"))
                .addOptions(List.of(
                        new MatrixOptionDef("MULTI_OPT_1", textTmpl("option 1"), "DEFAULT"),
                        new MatrixOptionDef("MULTI_OPT_2", textTmpl("option 2"), "GROUP"),
                        new MatrixOptionDef("MULTI_OPT_3", textTmpl("option 3"), "GROUP")))
                .addRows(List.of(
                        new MatrixRowDef("MULTI_ROW_1", textTmpl("row 1")),
                        new MatrixRowDef("MULTI_ROW_2", textTmpl("row 2"))))
                .addGroups(List.of(new MatrixGroupDef("DEFAULT", null),
                        new MatrixGroupDef("GROUP", textTmpl("group"))))
                .build();

        String actCode = "ACT" + Instant.now().toEpochMilli();
        activity = FormActivityDef.generalFormBuilder(actCode, "v1", testData.getStudyGuid())
                .addName(new Translation("en", "activity"))
                .addSection(new FormSectionDef(null, TestUtil.wrapQuestions(question, question2)))
                .build();
        return activityDao.insertActivity(activity, meta);
    }

    private void rolledbackTest(Consumer<Handle> test) {
        TransactionWrapper.useTxn(handle -> {
            activityDao = handle.attach(ActivityDao.class);
            instanceDao = handle.attach(ActivityInstanceDao.class);
            jdbiOption = handle.attach(JdbiMatrixOption.class);
            jdbiGroup = handle.attach(JdbiMatrixGroup.class);
            jdbiRow = handle.attach(JdbiMatrixRow.class);

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
            assertEquals(1, jdbiOption.findOptions(question.getQuestionId(), List.of("SINGLE_OPT_1"), instanceGuid).size());

            QuestionDto question2Dto = handle.attach(JdbiQuestion.class).findQuestionDtoById(question2.getQuestionId()).get();
            MatrixQuestionDao.GroupOptionRowDtos dtos = handle.attach(MatrixQuestionDao.class)
                    .findOrderedGroupOptionRowDtos(question2Dto.getId(), version1.getRevStart());

            assertNotNull(dtos.getOptions());
            assertNotNull(dtos.getRows());
            assertNotNull(dtos.getGroups());

            List<MatrixGroupDto> groups = dtos.getGroups().stream().filter(g -> g.getStableId() != null).collect(Collectors.toList());
            List<MatrixOptionDto> options = new ArrayList<>(dtos.getOptions());
            List<MatrixRowDto> rows = new ArrayList<>(dtos.getRows());

            assertEquals(question2.getOptions().size(), dtos.getOptions().size());
            assertEquals(question2.getRows().size(), dtos.getRows().size());
            assertEquals(question2.getGroups().size(), groups.size());

            for (int i = 0; i < options.size(); i++) {
                assertEquals(question2.getOptions().get(i).getStableId(), options.get(i).getStableId());
            }
            for (int i = 0; i < rows.size(); i++) {
                assertEquals(question2.getRows().get(i).getStableId(), rows.get(i).getStableId());
            }
            for (int i = 0; i < groups.size(); i++) {
                assertEquals(question2.getGroups().get(i).getStableId(), groups.get(i).getStableId());
            }

            // Add a new option using a new version
            RevisionMetadata meta = new RevisionMetadata(version1.getRevStart() + 5000L, userId, "test");
            ActivityVersionDto version2 = activityDao.changeVersion(activity.getActivityId(), "v2", meta);
            RevisionDto revDto = RevisionDto.fromStartMetadata(version2.getRevId(), meta);
            MatrixOptionDef option2 = new MatrixOptionDef("MULTI_OPT_2_V2", textTmpl("option 2 v2"), "DEFAULT");
            CacheService.getInstance().resetAllCaches();
            daoBuilder.buildDao(handle).addOption(question.getQuestionId(), option2, 2, revDto);
            assertNotNull(option2.getOptionId());

            options = jdbiOption.findAllActiveOrderedMatrixOptionsByQuestionId(question.getQuestionId());
            assertEquals("SINGLE_OPT_1", options.get(0).getStableId());
            assertEquals("SINGLE_OPT_2", options.get(1).getStableId());
            assertEquals("MULTI_OPT_2_V2", options.get(2).getStableId());

            // Ensure new option is added but not part of existing instance;
            assertTrue(jdbiOption.isCurrentlyActive(question.getQuestionId(), "MULTI_OPT_2_V2"));
        });
    }

    @Test
    public void testAddOption_alreadyActive() {
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("SINGLE_OPT_1");
        rolledbackTest(handle -> {
            ActivityVersionDto version1 = setupTestActivity();

            // Add existing option
            RevisionMetadata meta = new RevisionMetadata(version1.getRevStart() + 5000L, userId, "test");
            ActivityVersionDto version2 = activityDao.changeVersion(activity.getActivityId(), "v2", meta);
            RevisionDto revDto = RevisionDto.fromStartMetadata(version2.getRevId(), meta);
            MatrixOptionDef option3 = new MatrixOptionDef("SINGLE_OPT_1", textTmpl("option new"), "DEFAULT");
            CacheService.getInstance().resetAllCaches();
            daoBuilder.buildDao(handle).addOption(question.getQuestionId(), option3, 1, revDto);

            fail("Expected exception not thrown");
        });
    }

    @Test
    public void testAddOption_ordering_headOfList() {
        rolledbackTest(handle -> {
            RevisionMetadata meta = RevisionMetadata.now(userId, "test");
            ActivityVersionDto version1 = setupTestActivity(meta);

            RevisionDto revDto = RevisionDto.fromStartMetadata(version1.getRevId(), meta);
            MatrixOptionDef option3 = new MatrixOptionDef("SINGLE_OPT_3_NEW", textTmpl("option 3"), "DEFAULT");

            // Add to start of option list.
            CacheService.getInstance().resetAllCaches();
            daoBuilder.buildDao(handle).addOption(question.getQuestionId(), option3, 0, revDto);

            List<MatrixOptionDto> options = jdbiOption.findAllActiveOrderedMatrixOptionsByQuestionId(question.getQuestionId());
            assertEquals(2, options.size());
            assertEquals("SINGLE_OPT_3_NEW", options.get(0).getStableId());
            assertEquals("SINGLE_OPT_1", options.get(1).getStableId());
        });
    }

    @Test
    public void testAddOption_ordering_tailOfList() {
        rolledbackTest(handle -> {
            RevisionMetadata meta = RevisionMetadata.now(userId, "test");
            ActivityVersionDto version1 = setupTestActivity(meta);

            RevisionDto revDto = RevisionDto.fromStartMetadata(version1.getRevId(), meta);
            MatrixOptionDef option3 = new MatrixOptionDef("SINGLE_OPT_3_NEW", textTmpl("option 3"), "DEFAULT");

            // Add to the end with some randomly large position.
            CacheService.getInstance().resetAllCaches();
            daoBuilder.buildDao(handle).addOption(question.getQuestionId(), option3, 25, revDto);

            List<MatrixOptionDto> options = jdbiOption.findAllActiveOrderedMatrixOptionsByQuestionId(question.getQuestionId());
            assertEquals(3, options.size());
            assertEquals("SINGLE_OPT_1", options.get(0).getStableId());
            assertEquals("SINGLE_OPT_2", options.get(1).getStableId());
            assertEquals("SINGLE_OPT_3_NEW", options.get(2).getStableId());
        });
    }

    @Test
    public void testAddOption_ordering_middleOfList() {
        rolledbackTest(handle -> {
            RevisionMetadata meta = RevisionMetadata.now(userId, "test");
            ActivityVersionDto version1 = setupTestActivity(meta);

            RevisionDto revDto = RevisionDto.fromStartMetadata(version1.getRevId(), meta);
            MatrixOptionDef option3 = new MatrixOptionDef("SINGLE_OPT_3_NEW", textTmpl("option 3"), "DEFAULT");
            MatrixOptionDef option4 = new MatrixOptionDef("SINGLE_MIDDLE", textTmpl("option 4"), "DEFAULT");
            CacheService.getInstance().resetAllCaches();
            daoBuilder.buildDao(handle).addOption(question.getQuestionId(), option3, 2, revDto);

            // Add to middle of option list.
            daoBuilder.buildDao(handle).addOption(question.getQuestionId(), option4, 1, revDto);

            List<MatrixOptionDto> options = jdbiOption.findAllActiveOrderedMatrixOptionsByQuestionId(question.getQuestionId());
            assertEquals(4, options.size());
            assertEquals("SINGLE_OPT_1", options.get(0).getStableId());
            assertEquals("SINGLE_MIDDLE", options.get(1).getStableId());
            assertEquals("SINGLE_OPT_2", options.get(2).getStableId());
            assertEquals("SINGLE_OPT_3_NEW", options.get(3).getStableId());
        });
    }

    @Test
    public void testDisableOptionsRowsGroups() {
        rolledbackTest(handle -> {
            RevisionMetadata meta = RevisionMetadata.now(userId, "test");
            setupTestActivity(meta);

            CacheService.getInstance().resetAllCaches();
            daoBuilder.buildDao(handle).disableOptionsGroupsRowQuestions(question2.getQuestionId(), meta);

            List<MatrixOptionDto> options = jdbiOption.findAllActiveOrderedMatrixOptionsByQuestionId(question2.getQuestionId());
            List<MatrixRowDto> rows = jdbiRow.findAllActiveOrderedMatrixRowsByQuestionId(question2.getQuestionId());
            List<MatrixGroupDto> groups = jdbiGroup.findAllActiveOrderedMatrixGroupsQuestionId(question2.getQuestionId());

            assertEquals(0, options.size());
            assertEquals(0, rows.size());
            assertEquals(0, groups.size());
        });
    }
}
