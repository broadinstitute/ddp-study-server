package org.broadinstitute.ddp.db.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.net.URL;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.BlockGroupHeaderDto;
import org.broadinstitute.ddp.db.dto.FormBlockDto;
import org.broadinstitute.ddp.db.dto.RevisionDto;
import org.broadinstitute.ddp.model.activity.definition.ConditionalBlockDef;
import org.broadinstitute.ddp.model.activity.definition.ContentBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.GroupBlockDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.definition.SectionIcon;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.question.BoolQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.instance.FormSection;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.FormSectionState;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.activity.types.ListStyleHint;
import org.broadinstitute.ddp.model.activity.types.TemplateType;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class SectionBlockDaoTest extends TxnAwareBaseTest {

    private static TestDataSetupUtil.GeneratedTestData testData;
    @Rule
    public ExpectedException thrown = ExpectedException.none();
    private String sid;
    private Template prompt;

    @BeforeClass
    public static void setup() {
        TransactionWrapper.useTxn(handle -> testData = TestDataSetupUtil.generateBasicUserTestData(handle));
    }

    @Before
    public void refresh() {
        sid = "QID" + Instant.now().toEpochMilli();
        prompt = Template.text("dummy prompt");
    }

    @Test
    public void testAddBlock_atTopOfSection() {
        TransactionWrapper.useTxn(handle -> {
            SectionBlockDao dao = handle.attach(SectionBlockDao.class);
            ActivityDao actDao = handle.attach(ActivityDao.class);
            ActivityInstanceDao instanceDao = handle.attach(ActivityInstanceDao.class);
            JdbiFormSectionBlock jdbiSectionBlock = handle.attach(JdbiFormSectionBlock.class);

            // Setup a form activity
            ContentBlockDef contentDef = new ContentBlockDef(new Template(TemplateType.TEXT, null, "original first"));
            FormActivityDef form = buildSingleSectionForm(testData.getStudyGuid(), contentDef);
            RevisionMetadata meta = RevisionMetadata.now(testData.getUserId(), "test");
            ActivityVersionDto version1 = actDao.insertActivity(form, meta);
            RevisionDto revDto = RevisionDto.fromStartMetadata(version1.getRevId(), meta);
            long sectionId = form.getSections().get(0).getSectionId();

            // Add a new question block to same version
            QuestionBlockDef question = new QuestionBlockDef(TextQuestionDef.builder(TextInputType.TEXT, sid, prompt
            ).build());
            dao.addBlock(form.getActivityId(), sectionId, 0, question, revDto);
            assertNotNull(question.getBlockId());
            assertNotNull(question.getBlockGuid());

            // Fetch an instance and check the section
            String instanceGuid = instanceDao.insertInstance(form.getActivityId(), testData.getUserGuid()).getGuid();
            List<FormBlockDto> blocks = jdbiSectionBlock.findOrderedFormBlockDtosForSection(sectionId, instanceGuid);
            assertEquals(2, blocks.size());
            assertEquals(question.getBlockGuid(), blocks.get(0).getGuid());
            assertEquals(contentDef.getBlockGuid(), blocks.get(1).getGuid());

            handle.rollback();
        });
    }

    @Test
    public void testAddBlock_inMiddleOfSection() {
        TransactionWrapper.useTxn(handle -> {
            SectionBlockDao dao = handle.attach(SectionBlockDao.class);
            ActivityDao actDao = handle.attach(ActivityDao.class);
            ActivityInstanceDao instanceDao = handle.attach(ActivityInstanceDao.class);
            JdbiFormSectionBlock jdbiSectionBlock = handle.attach(JdbiFormSectionBlock.class);

            // Setup a form activity with two blocks
            ContentBlockDef contentDef1 = new ContentBlockDef(new Template(TemplateType.TEXT, null, "tmpl1"));
            ContentBlockDef contentDef2 = new ContentBlockDef(new Template(TemplateType.TEXT, null, "tmpl2"));
            FormActivityDef form = buildSingleSectionForm(testData.getStudyGuid(), contentDef1, contentDef2);
            RevisionMetadata meta = RevisionMetadata.now(testData.getUserId(), "test");
            ActivityVersionDto version1 = actDao.insertActivity(form, meta);
            RevisionDto revDto = RevisionDto.fromStartMetadata(version1.getRevId(), meta);
            long sectionId = form.getSections().get(0).getSectionId();

            // Add a new block in the middle
            long now = Instant.now().toEpochMilli();
            Template prompt1 = new Template(TemplateType.TEXT, null, "text1");
            QuestionBlockDef question1 = new QuestionBlockDef(TextQuestionDef.builder(TextInputType.TEXT, "q1" + now,
                    prompt1).build());
            dao.addBlock(form.getActivityId(), sectionId, 1, question1, revDto);
            assertNotNull(question1.getBlockId());
            assertNotNull(question1.getBlockGuid());

            // Add another one in same position
            Template prompt2 = new Template(TemplateType.TEXT, null, "text2");
            QuestionBlockDef question2 = new QuestionBlockDef(TextQuestionDef.builder(TextInputType.TEXT, "q2" + now,
                    prompt2).build());
            dao.addBlock(form.getActivityId(), sectionId, 1, question2, revDto);
            assertNotNull(question2.getBlockId());
            assertNotNull(question2.getBlockGuid());

            // Create activity instance and check order of blocks
            String instanceGuid = instanceDao.insertInstance(form.getActivityId(), testData.getUserGuid()).getGuid();
            List<FormBlockDto> blocks = jdbiSectionBlock.findOrderedFormBlockDtosForSection(sectionId, instanceGuid);
            assertEquals(4, blocks.size());
            assertEquals(contentDef1.getBlockGuid(), blocks.get(0).getGuid());
            assertEquals(question2.getBlockGuid(), blocks.get(1).getGuid());
            assertEquals(question1.getBlockGuid(), blocks.get(2).getGuid());
            assertEquals(contentDef2.getBlockGuid(), blocks.get(3).getGuid());

            handle.rollback();
        });
    }

    @Test
    public void testAddBlock_atBottomOfSection() {
        TransactionWrapper.useTxn(handle -> {
            SectionBlockDao dao = handle.attach(SectionBlockDao.class);
            ActivityDao actDao = handle.attach(ActivityDao.class);
            ActivityInstanceDao instanceDao = handle.attach(ActivityInstanceDao.class);
            JdbiFormSectionBlock jdbiSectionBlock = handle.attach(JdbiFormSectionBlock.class);

            // Setup a form activity
            ContentBlockDef contentDef = new ContentBlockDef(new Template(TemplateType.TEXT, null, "first"));
            FormActivityDef form = buildSingleSectionForm(testData.getStudyGuid(), contentDef);
            RevisionMetadata meta = RevisionMetadata.now(testData.getUserId(), "test");
            ActivityVersionDto version1 = actDao.insertActivity(form, meta);
            RevisionDto revDto = RevisionDto.fromStartMetadata(version1.getRevId(), meta);
            long sectionId = form.getSections().get(0).getSectionId();

            // Add a new block to bottom position
            QuestionBlockDef question = new QuestionBlockDef(TextQuestionDef.builder(TextInputType.TEXT, sid, prompt
            ).build());
            dao.addBlock(form.getActivityId(), sectionId, 100, question, revDto);
            assertNotNull(question.getBlockId());
            assertNotNull(question.getBlockGuid());

            // Fetch an instance and check the section
            String instanceGuid = instanceDao.insertInstance(form.getActivityId(), testData.getUserGuid()).getGuid();
            List<FormBlockDto> blocks = jdbiSectionBlock.findOrderedFormBlockDtosForSection(sectionId, instanceGuid);
            assertEquals(2, blocks.size());
            assertEquals(contentDef.getBlockGuid(), blocks.get(0).getGuid());
            assertEquals(question.getBlockGuid(), blocks.get(1).getGuid());

            handle.rollback();
        });
    }

    @Test
    public void testAddBlock_invalidPosition() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("non-negative");
        TransactionWrapper.useTxn(handle -> {
            SectionBlockDao dao = handle.attach(SectionBlockDao.class);
            ContentBlockDef contentDef = new ContentBlockDef(new Template(TemplateType.TEXT, null, "block"));
            dao.addBlock(1L, 1L, -1, contentDef, null);
        });
    }

    @Test
    public void testDisableBlock_template() {
        TransactionWrapper.useTxn(handle -> {
            SectionBlockDao dao = handle.attach(SectionBlockDao.class);
            ActivityDao actDao = handle.attach(ActivityDao.class);
            ActivityInstanceDao instanceDao = handle.attach(ActivityInstanceDao.class);
            JdbiFormSectionBlock jdbiSectionBlock = handle.attach(JdbiFormSectionBlock.class);
            JdbiBlockContent jdbiBlockContent = handle.attach(JdbiBlockContent.class);

            ContentBlockDef contentDef = new ContentBlockDef(new Template(TemplateType.TEXT, null, "first"));
            FormActivityDef form = buildSingleSectionForm(testData.getStudyGuid(), contentDef);
            ActivityVersionDto version1 = actDao.insertActivity(form, RevisionMetadata.now(testData.getUserId(), "test"));
            long sectionId = form.getSections().get(0).getSectionId();

            RevisionMetadata meta = new RevisionMetadata(version1.getRevStart() + 5000L, testData.getUserId(), "test");
            ActivityVersionDto version2 = actDao.changeVersion(form.getActivityId(), "v2", meta);
            dao.disableBlock(contentDef.getBlockId(), meta);

            String instanceGuid = instanceDao.insertInstance(form.getActivityId(), testData.getUserGuid(), testData.getUserGuid(),
                    InstanceStatusType.CREATED, false, version2.getRevStart()).getGuid();
            List<FormBlockDto> blocks = jdbiSectionBlock.findOrderedFormBlockDtosForSection(sectionId, instanceGuid);
            assertTrue(blocks.isEmpty());
            assertFalse(jdbiBlockContent.findActiveDtoByBlockId(contentDef.getBlockId()).isPresent());

            handle.rollback();
        });
    }

    @Test
    public void testDisableBlock_question() {
        TransactionWrapper.useTxn(handle -> {
            SectionBlockDao dao = handle.attach(SectionBlockDao.class);
            ActivityDao actDao = handle.attach(ActivityDao.class);
            ActivityInstanceDao instanceDao = handle.attach(ActivityInstanceDao.class);
            JdbiFormSectionBlock jdbiSectionBlock = handle.attach(JdbiFormSectionBlock.class);
            JdbiBlockQuestion jdbiBlockQuestion = handle.attach(JdbiBlockQuestion.class);

            QuestionBlockDef question = new QuestionBlockDef(TextQuestionDef.builder(TextInputType.TEXT, sid, prompt
            ).build());
            FormActivityDef form = buildSingleSectionForm(testData.getStudyGuid(), question);
            ActivityVersionDto version1 = actDao.insertActivity(form, RevisionMetadata.now(testData.getUserId(), "test"));
            long sectionId = form.getSections().get(0).getSectionId();

            RevisionMetadata meta = new RevisionMetadata(version1.getRevStart() + 5000L, testData.getUserId(), "test");
            ActivityVersionDto version2 = actDao.changeVersion(form.getActivityId(), "v2", meta);
            dao.disableBlock(question.getBlockId(), meta);

            String instanceGuid = instanceDao.insertInstance(form.getActivityId(), testData.getUserGuid(), testData.getUserGuid(),
                    InstanceStatusType.CREATED, false, version2.getRevStart()).getGuid();
            List<FormBlockDto> blocks = jdbiSectionBlock.findOrderedFormBlockDtosForSection(sectionId, instanceGuid);
            assertTrue(blocks.isEmpty());
            assertFalse(jdbiBlockQuestion.getActiveTypedQuestionId(question.getBlockId()).isPresent());

            handle.rollback();
        });
    }

    @Test
    public void testInsertConditionalBlock() {
        TransactionWrapper.useTxn(handle -> {
            TemplateDao templateDao = handle.attach(TemplateDao.class);
            JdbiQuestion jdbiQuestion = handle.attach(JdbiQuestion.class);

            BoolQuestionDef control = BoolQuestionDef.builder(sid, prompt, Template.text("yes"), Template.text("no")).build();
            QuestionBlockDef nested1 = new QuestionBlockDef(
                    TextQuestionDef.builder(TextInputType.TEXT, sid + "_NESTED1", Template.text("nested1")).build());
            ContentBlockDef nested2 = new ContentBlockDef(Template.text("nested2"));

            ConditionalBlockDef group = new ConditionalBlockDef(control);
            group.getNested().addAll(Arrays.asList(nested1, nested2));

            FormActivityDef form = buildSingleSectionForm(testData.getStudyGuid(), group);
            handle.attach(ActivityDao.class).insertActivity(form, RevisionMetadata.now(testData.getUserId(), "test"));

            assertNotNull(group.getBlockId());
            assertNotNull(nested1.getBlockId());
            assertNotNull(nested2.getBlockId());

            assertNotNull(group.getBlockGuid());
            assertNotNull(nested1.getBlockGuid());
            assertNotNull(nested2.getBlockGuid());

            assertNotNull(control.getQuestionId());
            assertTrue(jdbiQuestion.findQuestionDtoById(control.getQuestionId()).isPresent());

            assertNotNull(nested1.getQuestion().getQuestionId());
            assertTrue(jdbiQuestion.findQuestionDtoById(nested1.getQuestion().getQuestionId()).isPresent());

            assertNotNull(nested2.getBodyTemplate().getTemplateId());
            assertTrue(templateDao.findTextAndVarCountById(nested2.getBodyTemplate().getTemplateId()).isPresent());

            handle.rollback();
        });
    }

    @Test
    public void testInsertConditionalBlock_disallowEmptyNested() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("at least one nested");

        TransactionWrapper.useTxn(handle -> {
            BoolQuestionDef control = BoolQuestionDef.builder(sid, prompt, Template.text("yes"), Template.text("no")).build();
            ConditionalBlockDef group = new ConditionalBlockDef(control);

            // Ensure nested be empty list.
            group.getNested().clear();

            FormActivityDef form = buildSingleSectionForm(testData.getStudyGuid(), group);
            handle.attach(ActivityDao.class).insertActivity(form, RevisionMetadata.now(testData.getUserId(), "test"));

            fail("Expected exception not thrown");
        });
    }

    @Test
    public void testInsertConditionalBlock_disallowDeepNesting() {
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("not allowed");

        TransactionWrapper.useTxn(handle -> {
            BoolQuestionDef innerControl = BoolQuestionDef
                    .builder(sid + "INNER", Template.text("prompt"), Template.text("yes"), Template.text("no")).build();
            ContentBlockDef innerNested = new ContentBlockDef(Template.text("nested2"));
            ConditionalBlockDef innerGroup = new ConditionalBlockDef(innerControl);
            innerGroup.getNested().add(innerNested);

            BoolQuestionDef control = BoolQuestionDef.builder(sid, prompt, Template.text("yes"), Template.text("no")).build();
            ConditionalBlockDef group = new ConditionalBlockDef(control);
            group.getNested().add(innerGroup);

            FormActivityDef form = buildSingleSectionForm(testData.getStudyGuid(), group);
            handle.attach(ActivityDao.class).insertActivity(form, RevisionMetadata.now(testData.getUserId(), "test"));

            fail("Expected exception not thrown");
        });
    }

    @Test
    public void testInsertGroupBlock() {
        TransactionWrapper.useTxn(handle -> {
            TemplateDao templateDao = handle.attach(TemplateDao.class);
            JdbiQuestion jdbiQuestion = handle.attach(JdbiQuestion.class);
            JdbiBlockGroupHeader jdbiBlockGroupHeader = handle.attach(JdbiBlockGroupHeader.class);

            Template title = Template.text("this is a group block");
            QuestionBlockDef nested1 = new QuestionBlockDef(
                    BoolQuestionDef.builder(sid, prompt, Template.text("yes"), Template.text("no")).build());
            ContentBlockDef nested2 = new ContentBlockDef(Template.text("nested2"));

            GroupBlockDef group = new GroupBlockDef(ListStyleHint.NUMBER, title);
            group.getNested().add(nested1);
            group.getNested().add(nested2);

            FormActivityDef form = buildSingleSectionForm(testData.getStudyGuid(), group);
            handle.attach(ActivityDao.class).insertActivity(form, RevisionMetadata.now(testData.getUserId(), "test group block"));

            assertNotNull(group.getBlockId());
            assertNotNull(nested1.getBlockId());
            assertNotNull(nested2.getBlockId());

            assertNotNull(group.getBlockGuid());
            assertNotNull(nested1.getBlockGuid());
            assertNotNull(nested2.getBlockGuid());

            assertNotNull(title.getTemplateId());
            assertTrue(templateDao.findTextAndVarCountById(title.getTemplateId()).isPresent());

            Optional<BlockGroupHeaderDto> headerDtoOpt = jdbiBlockGroupHeader.findLatestGroupHeaderDto(group.getBlockId());
            assertTrue(headerDtoOpt.isPresent());
            assertEquals(ListStyleHint.NUMBER, headerDtoOpt.get().getListStyleHint());
            assertEquals(title.getTemplateId(), headerDtoOpt.get().getTitleTemplateId());

            assertNotNull(nested1.getQuestion().getQuestionId());
            assertTrue(jdbiQuestion.findQuestionDtoById(nested1.getQuestion().getQuestionId()).isPresent());

            assertNotNull(nested2.getBodyTemplate().getTemplateId());
            assertTrue(templateDao.findTextAndVarCountById(nested2.getBodyTemplate().getTemplateId()).isPresent());

            handle.rollback();
        });
    }

    @Test
    public void testInsertGroupBlock_disallowEmptyNested() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("at least one nested");

        TransactionWrapper.useTxn(handle -> {
            GroupBlockDef group = new GroupBlockDef(ListStyleHint.NONE, Template.text("title"));

            // Ensure nested be empty list.
            group.getNested().clear();

            FormActivityDef form = buildSingleSectionForm(testData.getStudyGuid(), group);
            handle.attach(ActivityDao.class).insertActivity(form, RevisionMetadata.now(testData.getUserId(), "test"));

            fail("Expected exception not thrown");
        });
    }

    @Test
    public void testInsertGroupBlock_disallowDeepNesting() {
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("not allowed");

        TransactionWrapper.useTxn(handle -> {
            ContentBlockDef innerNested = new ContentBlockDef(Template.text("inner nested"));
            GroupBlockDef innerGroup = new GroupBlockDef(ListStyleHint.BULLET, Template.text("inner title"));
            innerGroup.getNested().add(innerNested);

            GroupBlockDef group = new GroupBlockDef();
            group.getNested().add(innerGroup);

            FormActivityDef form = buildSingleSectionForm(testData.getStudyGuid(), group);
            handle.attach(ActivityDao.class).insertActivity(form, RevisionMetadata.now(testData.getUserId(), "test"));

            fail("Expected exception not thrown");
        });
    }

    @Test
    public void testInsertSection_name() {
        TransactionWrapper.useTxn(handle -> {
            Template nameTmpl = Template.text("section name 123");
            FormSectionDef section = new FormSectionDef(null, nameTmpl, Collections.emptyList(), Collections.emptyList());

            FormActivityDef form = buildSingleSectionForm(testData.getStudyGuid());
            form.getSections().clear();
            form.getSections().add(section);
            handle.attach(ActivityDao.class).insertActivity(form, RevisionMetadata.now(testData.getUserId(), "test section name"));

            assertNotNull(form.getActivityId());
            assertNotNull(form.getSections().get(0).getSectionId());
            assertNotNull(form.getSections().get(0).getNameTemplate().getTemplateId());

            handle.rollback();
        });
    }

    @Test
    public void testInsertSection_icons() throws Exception {
        TransactionWrapper.useTxn(handle -> {
            SectionIcon icon = new SectionIcon(FormSectionState.COMPLETE, 100, 100);
            icon.putSource("1x", new URL("https://dev.ddp.org/some/icon.png"));
            icon.putSource("2x", new URL("https://dev.ddp.org/some/icon_2x.png"));

            FormSectionDef section = new FormSectionDef(null, Collections.emptyList());
            section.addIcon(icon);

            FormActivityDef form = buildSingleSectionForm(testData.getStudyGuid());
            form.getSections().clear();
            form.getSections().add(section);
            handle.attach(ActivityDao.class).insertActivity(form, RevisionMetadata.now(testData.getUserId(), "test section icon"));

            assertNotNull(form.getActivityId());
            assertNotNull(section.getSectionId());

            Long iconId = form.getSections().get(0).getIcons().get(0).getIconId();
            assertNotNull(iconId);

            SectionIcon actual = handle.attach(FormSectionIconDao.class).findById(iconId).get();
            assertNotNull(actual);
            assertEquals(section.getSectionId(), actual.getSectionId());
            assertEquals(icon.getIconId(), actual.getIconId());
            assertEquals(icon.getHeight(), actual.getHeight());
            assertEquals(icon.getWidth(), actual.getWidth());
            assertEquals(icon.getState(), actual.getState());

            assertEquals(icon.getSources().size(), actual.getSources().size());
            assertEquals(icon.getSources().get("1x"), actual.getSources().get("1x"));
            assertEquals(icon.getSources().get("2x"), actual.getSources().get("2x"));

            handle.rollback();
        });
    }

    @Test
    public void testInsertSection_noIcons() {
        TransactionWrapper.useTxn(handle -> {
            FormSectionDef section = new FormSectionDef(null, Collections.emptyList());
            section.getIcons().clear();

            FormActivityDef form = buildSingleSectionForm(testData.getStudyGuid());
            form.getSections().clear();
            form.getSections().add(section);
            handle.attach(ActivityDao.class).insertActivity(form, RevisionMetadata.now(testData.getUserId(), "test section icon"));
            assertNotNull(form.getActivityId());

            Long sectionId = section.getSectionId();
            assertNotNull(sectionId);

            Collection<SectionIcon> icons = handle.attach(FormSectionIconDao.class).findAllBySectionId(sectionId);
            assertNotNull(icons);
            assertTrue(icons.isEmpty());

            handle.rollback();
        });
    }

    @Test
    public void testInsertSection_ensureIconsHaveRequiredScale() throws Exception {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage(FormSectionState.COMPLETE.name());
        thrown.expectMessage("1x");

        TransactionWrapper.useTxn(handle -> {
            SectionIcon icon = new SectionIcon(FormSectionState.COMPLETE, 100, 100);
            icon.putSource("2x", new URL("https://dev.ddp.org/some/icon_2x.png"));
            icon.putSource("3x", new URL("https://dev.ddp.org/some/icon_3x.png"));

            FormSectionDef section = new FormSectionDef(null, Collections.emptyList());
            section.addIcon(icon);

            FormActivityDef form = buildSingleSectionForm(testData.getStudyGuid());
            form.getSections().clear();
            form.getSections().add(section);
            handle.attach(ActivityDao.class).insertActivity(form, RevisionMetadata.now(testData.getUserId(), "test section icon"));

            fail("Expected exception not thrown");
        });
    }

    @Test
    public void testFindAllInstanceSectionsById_withName() {
        TransactionWrapper.useTxn(handle -> {
            Template name = Template.text("section name 123");
            FormSectionDef section = new FormSectionDef(null, name, Collections.emptyList(), Collections.emptyList());

            FormActivityDef form = buildSingleSectionForm(testData.getStudyGuid());
            form.getSections().clear();
            form.getSections().add(section);
            handle.attach(ActivityDao.class).insertActivity(form, RevisionMetadata.now(testData.getUserId(), "test find section"));

            assertNotNull(form.getActivityId());
            assertNotNull(section.getSectionId());

            Map<Long, FormSection> found = handle.attach(SectionBlockDao.class)
                    .findAllInstanceSectionsById(Collections.singletonList(section.getSectionId()));
            assertNotNull(found);
            assertTrue(found.containsKey(section.getSectionId()));

            FormSection actual = found.get(section.getSectionId());
            assertNotNull(actual);
            assertEquals(name.getTemplateId(), actual.getNameTemplateId());
            assertTrue(actual.getBlocks().isEmpty());
            assertTrue(actual.getIcons().isEmpty());

            handle.rollback();
        });
    }

    @Test
    public void testFindAllInstanceSectionsById_multiple() {
        TransactionWrapper.useTxn(handle -> {
            Template name1 = Template.text("sect1");
            FormSectionDef sect1 = new FormSectionDef(null, name1, Collections.emptyList(), Collections.emptyList());
            FormSectionDef sect2 = new FormSectionDef(null, Collections.emptyList());

            FormActivityDef form = buildSingleSectionForm(testData.getStudyGuid());
            form.getSections().clear();
            form.getSections().add(sect1);
            form.getSections().add(sect2);
            handle.attach(ActivityDao.class).insertActivity(form, RevisionMetadata.now(testData.getUserId(), "test find section"));

            assertNotNull(form.getActivityId());
            assertNotNull(sect1.getSectionId());
            assertNotNull(sect2.getSectionId());
            assertNotEquals(sect1.getSectionId(), sect2.getSectionId());

            Map<Long, FormSection> found = handle.attach(SectionBlockDao.class)
                    .findAllInstanceSectionsById(Arrays.asList(sect1.getSectionId(), sect2.getSectionId()));
            assertNotNull(found);
            assertTrue(found.containsKey(sect1.getSectionId()));
            assertTrue(found.containsKey(sect2.getSectionId()));

            FormSection actual = found.get(sect1.getSectionId());
            assertNotNull(actual);
            assertEquals(name1.getTemplateId(), actual.getNameTemplateId());

            actual = found.get(sect2.getSectionId());
            assertNotNull(actual);
            assertNull(actual.getNameTemplateId());

            handle.rollback();
        });
    }

    @Test
    public void testFindAllInstanceSectionsById_withIcon() throws Exception {
        TransactionWrapper.useTxn(handle -> {
            SectionIcon icon = new SectionIcon(FormSectionState.COMPLETE, 100, 100);
            icon.putSource("1x", new URL("https://dev.ddp.org/some/icon.png"));
            icon.putSource("2x", new URL("https://dev.ddp.org/some/icon_2x.png"));
            FormSectionDef section = new FormSectionDef(null, Collections.emptyList());
            section.addIcon(icon);

            FormActivityDef form = buildSingleSectionForm(testData.getStudyGuid());
            form.getSections().clear();
            form.getSections().add(section);
            handle.attach(ActivityDao.class).insertActivity(form, RevisionMetadata.now(testData.getUserId(), "test find section"));

            assertNotNull(form.getActivityId());
            assertNotNull(section.getSectionId());

            Map<Long, FormSection> found = handle.attach(SectionBlockDao.class)
                    .findAllInstanceSectionsById(Collections.singletonList(section.getSectionId()));
            assertNotNull(found);
            assertTrue(found.containsKey(section.getSectionId()));

            FormSection actual = found.get(section.getSectionId());
            assertNotNull(actual);
            assertEquals(1, actual.getIcons().size());

            SectionIcon actualIcon = actual.getIconById(icon.getIconId());
            assertNotNull(actualIcon);
            assertEquals(icon.getState(), actualIcon.getState());
            assertEquals(icon.getHeight(), actualIcon.getHeight());
            assertEquals(icon.getWidth(), actualIcon.getWidth());

            assertEquals(icon.getSources().size(), actualIcon.getSources().size());
            assertEquals(icon.getSources().get("1x"), actualIcon.getSources().get("1x"));
            assertEquals(icon.getSources().get("2x"), actualIcon.getSources().get("2x"));

            handle.rollback();
        });
    }

    @Test
    public void testFindAllInstanceSectionsById_multipleIcons() throws Exception {
        TransactionWrapper.useTxn(handle -> {
            SectionIcon icon1Complete = new SectionIcon(FormSectionState.COMPLETE, 100, 100);
            icon1Complete.putSource("1x", new URL("https://dev.ddp.org/some/icon.png"));
            icon1Complete.putSource("2x", new URL("https://dev.ddp.org/some/icon_2x.png"));

            SectionIcon icon1Incomplete = new SectionIcon(FormSectionState.INCOMPLETE, 200, 200);
            icon1Incomplete.putSource("1x", new URL("https://dev.ddp.org/some/icon_incomplete.png"));

            SectionIcon icon2 = new SectionIcon(FormSectionState.COMPLETE, 150, 150);
            icon2.putSource("1x", new URL("https://dev.ddp.org/some/icon2.png"));

            FormSectionDef sect1 = new FormSectionDef(null, Collections.emptyList());
            sect1.addIcon(icon1Complete);
            sect1.addIcon(icon1Incomplete);

            FormSectionDef sect2 = new FormSectionDef(null, Collections.emptyList());
            sect2.addIcon(icon2);

            FormActivityDef form = buildSingleSectionForm(testData.getStudyGuid());
            form.getSections().clear();
            form.getSections().add(sect1);
            form.getSections().add(sect2);
            handle.attach(ActivityDao.class).insertActivity(form, RevisionMetadata.now(testData.getUserId(), "test find section"));

            assertNotNull(form.getActivityId());
            assertNotNull(sect1.getSectionId());
            assertNotNull(sect2.getSectionId());
            assertNotEquals(sect1.getSectionId(), sect2.getSectionId());

            Map<Long, FormSection> found = handle.attach(SectionBlockDao.class)
                    .findAllInstanceSectionsById(Arrays.asList(sect1.getSectionId(), sect2.getSectionId()));
            assertNotNull(found);
            assertTrue(found.containsKey(sect1.getSectionId()));
            assertTrue(found.containsKey(sect2.getSectionId()));

            FormSection actual = found.get(sect1.getSectionId());
            assertNotNull(actual);
            assertEquals(2, actual.getIcons().size());

            actual = found.get(sect2.getSectionId());
            assertNotNull(actual);
            assertEquals(1, actual.getIcons().size());

            handle.rollback();
        });
    }

    private FormActivityDef buildSingleSectionForm(String studyGuid, FormBlockDef... blocks) {
        return FormActivityDef.generalFormBuilder("ACT" + Instant.now().toEpochMilli(), "v1", studyGuid)
                .addName(new Translation("en", "activity"))
                .addSection(new FormSectionDef(null, Arrays.asList(blocks)))
                .build();
    }
}
