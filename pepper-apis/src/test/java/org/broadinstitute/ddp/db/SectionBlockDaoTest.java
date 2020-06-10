package org.broadinstitute.ddp.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.JdbiLanguageCode;
import org.broadinstitute.ddp.db.dao.JdbiQuestion;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.model.activity.definition.ConditionalBlockDef;
import org.broadinstitute.ddp.model.activity.definition.ContentBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.GroupBlockDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.question.BoolQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.instance.ConditionalBlock;
import org.broadinstitute.ddp.model.activity.instance.FormBlock;
import org.broadinstitute.ddp.model.activity.instance.GroupBlock;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.BlockType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.activity.types.ListStyleHint;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.model.activity.types.TemplateType;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.jdbi.v3.core.Handle;
import org.junit.BeforeClass;
import org.junit.Test;

public class SectionBlockDaoTest extends TxnAwareBaseTest {

    private static TestDataSetupUtil.GeneratedTestData data;
    private static String userGuid;
    private static String studyGuid;
    private static long langCodeId;

    private static SectionBlockDao dao;

    @BeforeClass
    public static void setUp() {
        dao = new SectionBlockDao();
        TransactionWrapper.useTxn(handle -> {
            data = TestDataSetupUtil.generateBasicUserTestData(handle);
            userGuid = data.getTestingUser().getUserGuid();
            studyGuid = data.getStudyGuid();
            langCodeId = handle.attach(JdbiLanguageCode.class).getLanguageCodeId("en");
        });
    }

    @Test
    public void testGetBlocksForSections() {
        TransactionWrapper.useTxn(handle -> {
            FormSectionDef intro = new FormSectionDef("intro", Collections.emptyList());
            FormSectionDef closing = new FormSectionDef("closing", Collections.emptyList());

            FormSectionDef body1 = new FormSectionDef("s1", Arrays.asList(
                    new ContentBlockDef(new Template(TemplateType.TEXT, "s1b1", "")),
                    new QuestionBlockDef(TextQuestionDef
                            .builder(TextInputType.TEXT, "s1b2", new Template(TemplateType.TEXT, null, ""))
                            .build())));
            FormSectionDef body2 = new FormSectionDef("s2", Collections.singletonList(
                    new ContentBlockDef(new Template(TemplateType.TEXT, "s2b1", ""))));
            FormSectionDef body3 = new FormSectionDef("s3", Collections.emptyList());

            FormActivityDef form = insertDummyActivity(handle, intro, closing, userGuid, studyGuid, body1, body2, body3);
            String instanceGuid = insertNewInstance(handle, form.getActivityId(), userGuid);

            List<Long> sectionIds = extractSectionIds(intro, closing, body1, body2, body3);
            Map<Long, List<FormBlock>> mapping = dao.getBlocksForSections(handle, sectionIds, instanceGuid, langCodeId);
            assertEquals(sectionIds.size(), mapping.size());

            List<FormBlock> blocks = mapping.get(intro.getSectionId());
            assertNotNull(blocks);
            assertTrue(blocks.isEmpty());

            blocks = mapping.get(closing.getSectionId());
            assertNotNull(blocks);
            assertTrue(blocks.isEmpty());

            blocks = mapping.get(body1.getSectionId());
            assertNotNull(blocks);
            assertEquals(2, blocks.size());
            assertEquals(BlockType.CONTENT, blocks.get(0).getBlockType());
            assertEquals(BlockType.QUESTION, blocks.get(1).getBlockType());

            blocks = mapping.get(body2.getSectionId());
            assertNotNull(blocks);
            assertEquals(1, blocks.size());
            assertEquals(BlockType.CONTENT, blocks.get(0).getBlockType());

            blocks = mapping.get(body3.getSectionId());
            assertNotNull(blocks);
            assertTrue(blocks.isEmpty());

            handle.rollback();
        });
    }

    @Test
    public void testGetBlocksForSections_conditionalBlock() {
        TransactionWrapper.useTxn(handle -> {
            BoolQuestionDef control = BoolQuestionDef.builder("sid", new Template(TemplateType.TEXT, null, "prompt"),
                    new Template(TemplateType.TEXT, null, "yes"), new Template(TemplateType.TEXT, null, "no")).build();
            QuestionBlockDef nested1 = new QuestionBlockDef(TextQuestionDef
                    .builder(TextInputType.TEXT, "sid_nested", new Template(TemplateType.TEXT, null, "nested1"))
                    .build());
            ContentBlockDef nested2 = new ContentBlockDef(new Template(TemplateType.TEXT, null, "nested2"));

            ConditionalBlockDef block = new ConditionalBlockDef(control);
            block.getNested().add(nested1);
            block.getNested().add(nested2);

            FormSectionDef s1 = new FormSectionDef("s1", Collections.singletonList(block));
            FormActivityDef form = insertDummyActivity(handle, null, null, userGuid, studyGuid, s1);
            String instanceGuid = insertNewInstance(handle, form.getActivityId(), userGuid);

            List<Long> sectionIds = extractSectionIds(s1);
            Map<Long, List<FormBlock>> mapping = dao.getBlocksForSections(handle, sectionIds, instanceGuid, langCodeId);
            assertEquals(sectionIds.size(), mapping.size());

            List<FormBlock> blocks = mapping.get(s1.getSectionId());
            assertEquals(1, blocks.size());
            assertEquals(BlockType.CONDITIONAL, blocks.get(0).getBlockType());

            ConditionalBlock condBlock = (ConditionalBlock) blocks.get(0);
            assertEquals(QuestionType.BOOLEAN, condBlock.getControl().getQuestionType());
            assertEquals(2, condBlock.getNested().size());
            assertEquals(BlockType.QUESTION, condBlock.getNested().get(0).getBlockType());
            assertEquals(BlockType.CONTENT, condBlock.getNested().get(1).getBlockType());

            handle.rollback();
        });
    }

    @Test
    public void testGetBlocksForSections_groupBlock() {
        TransactionWrapper.useTxn(handle -> {
            Template title = new Template(TemplateType.TEXT, null, "group title");
            QuestionBlockDef nested1 = new QuestionBlockDef(BoolQuestionDef.builder("sid",
                    new Template(TemplateType.TEXT, null, "prompt"),
                    new Template(TemplateType.TEXT, null, "yes"),
                    new Template(TemplateType.TEXT, null, "no")).build());
            ContentBlockDef nested2 = new ContentBlockDef(new Template(TemplateType.TEXT, null, "nested2"));

            GroupBlockDef block = new GroupBlockDef(ListStyleHint.UPPER_ALPHA, title);
            block.getNested().add(nested1);
            block.getNested().add(nested2);

            FormSectionDef s1 = new FormSectionDef("s1", Collections.singletonList(block));
            FormActivityDef form = insertDummyActivity(handle, null, null, userGuid, studyGuid, s1);
            String instanceGuid = insertNewInstance(handle, form.getActivityId(), userGuid);

            List<Long> sectionIds = extractSectionIds(s1);
            Map<Long, List<FormBlock>> mapping = dao.getBlocksForSections(handle, sectionIds, instanceGuid, langCodeId);
            assertEquals(sectionIds.size(), mapping.size());

            List<FormBlock> blocks = mapping.get(s1.getSectionId());
            assertEquals(1, blocks.size());
            assertEquals(BlockType.GROUP, blocks.get(0).getBlockType());

            GroupBlock groupBlock = (GroupBlock) blocks.get(0);
            assertEquals(ListStyleHint.UPPER_ALPHA, groupBlock.getListStyleHint());
            assertEquals(title.getTemplateId(), groupBlock.getTitleTemplateId());

            assertEquals(2, groupBlock.getNested().size());
            assertEquals(BlockType.QUESTION, groupBlock.getNested().get(0).getBlockType());
            assertEquals(BlockType.CONTENT, groupBlock.getNested().get(1).getBlockType());

            handle.rollback();
        });
    }

    @Test
    public void testGetBlocksForSections_questionBlock_filtersOutDeprecated() {
        TransactionWrapper.useTxn(handle -> {
            TextQuestionDef question = TextQuestionDef.builder(TextInputType.TEXT, "q1", Template.text("foo")).build();
            FormSectionDef body = new FormSectionDef("s1", Collections.singletonList(new QuestionBlockDef(question)));

            FormActivityDef form = insertDummyActivity(handle, null, null, userGuid, studyGuid, body);
            String instanceGuid = insertNewInstance(handle, form.getActivityId(), userGuid);

            List<Long> sectionIds = extractSectionIds(body);
            Map<Long, List<FormBlock>> mapping = dao.getBlocksForSections(handle, sectionIds, instanceGuid, langCodeId);
            assertEquals(sectionIds.size(), mapping.size());

            List<FormBlock> blocks = mapping.get(body.getSectionId());
            assertNotNull(blocks);
            assertEquals(1, blocks.size());
            assertEquals(BlockType.QUESTION, blocks.get(0).getBlockType());

            assertEquals(1, handle.attach(JdbiQuestion.class).updateIsDeprecatedById(question.getQuestionId(), true));
            mapping = dao.getBlocksForSections(handle, sectionIds, instanceGuid, langCodeId);
            assertEquals(sectionIds.size(), mapping.size());

            blocks = mapping.get(body.getSectionId());
            assertNotNull(blocks);
            assertTrue(blocks.isEmpty());

            handle.rollback();
        });
    }

    @Test
    public void testGetBlocksForSections_conditionalBlock_filtersOutWhenControlQuestionDeprecated() {
        TransactionWrapper.useTxn(handle -> {
            TextQuestionDef control = TextQuestionDef.builder(TextInputType.TEXT, "q1", Template.text("foo")).build();
            ConditionalBlockDef block = new ConditionalBlockDef(control);
            block.addNestedBlock(new ContentBlockDef(Template.text("bar")));
            FormSectionDef body = new FormSectionDef("s1", Collections.singletonList(block));

            FormActivityDef form = insertDummyActivity(handle, null, null, userGuid, studyGuid, body);
            String instanceGuid = insertNewInstance(handle, form.getActivityId(), userGuid);

            List<Long> sectionIds = extractSectionIds(body);
            Map<Long, List<FormBlock>> mapping = dao.getBlocksForSections(handle, sectionIds, instanceGuid, langCodeId);
            assertEquals(sectionIds.size(), mapping.size());

            List<FormBlock> blocks = mapping.get(body.getSectionId());
            assertNotNull(blocks);
            assertEquals(1, blocks.size());
            assertEquals(BlockType.CONDITIONAL, blocks.get(0).getBlockType());

            assertEquals(1, handle.attach(JdbiQuestion.class).updateIsDeprecatedById(control.getQuestionId(), true));
            mapping = dao.getBlocksForSections(handle, sectionIds, instanceGuid, langCodeId);
            assertEquals(sectionIds.size(), mapping.size());

            blocks = mapping.get(body.getSectionId());
            assertNotNull(blocks);
            assertTrue(blocks.isEmpty());

            handle.rollback();
        });
    }

    @Test
    public void testGetBlocksForSections_conditionalBlock_filtersOutNestedBlockWhenDeprecated() {
        TransactionWrapper.useTxn(handle -> {
            TextQuestionDef control = TextQuestionDef.builder(TextInputType.TEXT, "q1", Template.text("foo")).build();
            TextQuestionDef nested = TextQuestionDef.builder(TextInputType.TEXT, "q2", Template.text("bar")).build();
            ConditionalBlockDef block = new ConditionalBlockDef(control);
            block.addNestedBlock(new QuestionBlockDef(nested));
            FormSectionDef body = new FormSectionDef("s1", Collections.singletonList(block));

            FormActivityDef form = insertDummyActivity(handle, null, null, userGuid, studyGuid, body);
            String instanceGuid = insertNewInstance(handle, form.getActivityId(), userGuid);

            List<Long> sectionIds = extractSectionIds(body);
            Map<Long, List<FormBlock>> mapping = dao.getBlocksForSections(handle, sectionIds, instanceGuid, langCodeId);
            assertEquals(sectionIds.size(), mapping.size());

            List<FormBlock> blocks = mapping.get(body.getSectionId());
            assertNotNull(blocks);
            assertEquals(1, blocks.size());
            assertEquals(BlockType.CONDITIONAL, blocks.get(0).getBlockType());

            ConditionalBlock cond = (ConditionalBlock) blocks.get(0);
            assertEquals(control.getQuestionId(), (Long) cond.getControl().getQuestionId());
            assertEquals(BlockType.QUESTION, cond.getNested().get(0).getBlockType());

            assertEquals(1, handle.attach(JdbiQuestion.class).updateIsDeprecatedById(nested.getQuestionId(), true));
            mapping = dao.getBlocksForSections(handle, sectionIds, instanceGuid, langCodeId);
            assertEquals(sectionIds.size(), mapping.size());

            blocks = mapping.get(body.getSectionId());
            assertNotNull(blocks);
            assertEquals(1, blocks.size());
            assertEquals(BlockType.CONDITIONAL, blocks.get(0).getBlockType());

            cond = (ConditionalBlock) blocks.get(0);
            assertEquals(control.getQuestionId(), (Long) cond.getControl().getQuestionId());
            assertTrue(cond.getNested().isEmpty());

            handle.rollback();
        });
    }

    @Test
    public void testGetBlocksForSections_groupBlock_filtersOutNestedBlockWhenDeprecated() {
        TransactionWrapper.useTxn(handle -> {
            TextQuestionDef nested = TextQuestionDef.builder(TextInputType.TEXT, "q1", Template.text("foo")).build();
            GroupBlockDef block = new GroupBlockDef(ListStyleHint.NONE, Template.text("bar"));
            block.addNestedBlock(new QuestionBlockDef(nested));
            FormSectionDef body = new FormSectionDef("s1", Collections.singletonList(block));

            FormActivityDef form = insertDummyActivity(handle, null, null, userGuid, studyGuid, body);
            String instanceGuid = insertNewInstance(handle, form.getActivityId(), userGuid);

            List<Long> sectionIds = extractSectionIds(body);
            Map<Long, List<FormBlock>> mapping = dao.getBlocksForSections(handle, sectionIds, instanceGuid, langCodeId);
            assertEquals(sectionIds.size(), mapping.size());

            List<FormBlock> blocks = mapping.get(body.getSectionId());
            assertNotNull(blocks);
            assertEquals(1, blocks.size());
            assertEquals(BlockType.GROUP, blocks.get(0).getBlockType());

            GroupBlock group = (GroupBlock) blocks.get(0);
            assertEquals(1, group.getNested().size());
            assertEquals(BlockType.QUESTION, group.getNested().get(0).getBlockType());

            assertEquals(1, handle.attach(JdbiQuestion.class).updateIsDeprecatedById(nested.getQuestionId(), true));
            mapping = dao.getBlocksForSections(handle, sectionIds, instanceGuid, langCodeId);
            assertEquals(sectionIds.size(), mapping.size());

            blocks = mapping.get(body.getSectionId());
            assertNotNull(blocks);
            assertEquals(1, blocks.size());
            assertEquals(BlockType.GROUP, blocks.get(0).getBlockType());

            group = (GroupBlock) blocks.get(0);
            assertTrue(group.getNested().isEmpty());

            handle.rollback();
        });
    }

    private String insertNewInstance(Handle handle, long activityId, String userGuid) {
        return handle.attach(ActivityInstanceDao.class)
                .insertInstance(activityId, userGuid, userGuid, InstanceStatusType.CREATED, false)
                .getGuid();
    }

    private FormActivityDef insertDummyActivity(Handle handle, FormSectionDef intro, FormSectionDef closing,
                                                String userGuid, String studyGuid, FormSectionDef... sections) {
        FormActivityDef form = FormActivityDef.generalFormBuilder("ACT" + Instant.now().toEpochMilli(), "v1", studyGuid)
                .addName(new Translation("en", "test activity"))
                .addSections(Arrays.asList(sections))
                .setIntroduction(intro)
                .setClosing(closing)
                .build();
        long userId = handle.attach(JdbiUser.class).getUserIdByGuid(userGuid);
        handle.attach(ActivityDao.class).insertActivity(form, RevisionMetadata.now(userId, "add test activity"));
        assertNotNull(form.getActivityId());
        return form;
    }

    private List<Long> extractSectionIds(FormSectionDef... sections) {
        return Stream.of(sections).map(FormSectionDef::getSectionId).collect(Collectors.toList());
    }
}
