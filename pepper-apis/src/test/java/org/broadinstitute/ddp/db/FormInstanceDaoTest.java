package org.broadinstitute.ddp.db;

import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.content.I18nContentRenderer;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.template.TemplateVariable;
import org.broadinstitute.ddp.model.activity.instance.ActivityInstance;
import org.broadinstitute.ddp.model.activity.instance.FormInstance;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.FormType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.broadinstitute.ddp.pex.TreeWalkInterpreter;
import org.broadinstitute.ddp.service.ActivityInstanceService;
import org.broadinstitute.ddp.service.actvityinstancebuilder.context.AIBuilderCustomizationFlags;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.jdbi.v3.core.Handle;
import org.junit.BeforeClass;
import org.junit.Test;

public class FormInstanceDaoTest extends TxnAwareBaseTest {

    private static TestDataSetupUtil.GeneratedTestData data;
    private static String userGuid;
    private static String studyGuid;

    private static FormInstanceDao dao;
    private static ActivityInstanceService activityInstanceService;

    @BeforeClass
    public static void setup() {
        SectionBlockDao sectionBlockDao = new SectionBlockDao();
        dao = FormInstanceDao.fromDaoAndConfig(sectionBlockDao, sqlConfig);
        PexInterpreter interpreter = new TreeWalkInterpreter();
        I18nContentRenderer i18nContentRenderer = new I18nContentRenderer();
        activityInstanceService = new ActivityInstanceService(
                new org.broadinstitute.ddp.db.ActivityInstanceDao(dao), interpreter, i18nContentRenderer);
        TransactionWrapper.useTxn(handle -> {
            data = TestDataSetupUtil.generateBasicUserTestData(handle);
            userGuid = data.getTestingUser().getUserGuid();
            studyGuid = data.getStudyGuid();
        });
    }

    @Test
    public void testGetTranslatedFormByGuid_activityNotFound() {
        FormInstance form = TransactionWrapper.withTxn(
                handle -> dao.getTranslatedFormByGuid(handle, "not-an-activity", "en", ContentStyle.STANDARD));
        assertNull(form);
    }

    @Test
    public void testGetTranslatedFormByGuid_isoLangCodeNotFound() {
        TransactionWrapper.useTxn(handle -> {
            FormActivityDef form = insertDummyActivity(handle, userGuid, studyGuid);
            String instanceGuid = insertNewInstance(handle, form.getActivityId(), userGuid);
            FormInstance inst = dao.getTranslatedFormByGuid(handle, instanceGuid, "xyz", ContentStyle.STANDARD);
            assertNull(inst);
            handle.rollback();
        });
    }

    @Test
    public void testGetBaseFormByGuid_WithSubtitle() {
        TransactionWrapper.useTxn(handle -> {
            FormActivityDef formDef = insertDummyActivity(handle, userGuid, studyGuid);
            String instanceGuid = insertNewInstance(handle, formDef.getActivityId(), userGuid);

            Optional<ActivityInstance> enInst = activityInstanceService.buildInstanceFromDefinition(
                    handle, userGuid, userGuid, studyGuid, instanceGuid, "en", null,
                    AIBuilderCustomizationFlags.create().setCreateFormInstance(true).setRenderFormTitleSubtitle(true));
            assertNotNull(enInst.get());
            testTranslation(formDef, FormActivityDef::getTranslatedSubtitles, enInst.get().getSubtitle(), "en");

            Optional<ActivityInstance> ruInst = activityInstanceService.buildInstanceFromDefinition(
                    handle, userGuid, userGuid, studyGuid, instanceGuid, "ru", null,
                    AIBuilderCustomizationFlags.create().setCreateFormInstance(true).setRenderFormTitleSubtitle(true));
            assertNotNull(ruInst.get());
            testTranslation(formDef, FormActivityDef::getTranslatedSubtitles, ruInst.get().getSubtitle(), "ru");
            handle.rollback();
        });
    }

    @Test
    public void testGetBaseFormByGuid_WithoutSubtitle() {
        TransactionWrapper.useTxn(handle -> {
            FormActivityDef formDef = insertDummyActivityWithoutSubtitle(handle, userGuid, studyGuid);
            String instanceGuid = insertNewInstance(handle, formDef.getActivityId(), userGuid);
            Optional<ActivityInstance> enInst = activityInstanceService.buildInstanceFromDefinition(
                    handle, userGuid, userGuid, studyGuid, instanceGuid, "en", null,
                    AIBuilderCustomizationFlags.create().setCreateFormInstance(true).setRenderFormTitleSubtitle(true));
            assertNotNull(enInst);
            assertNull(enInst.get().getSubtitle());
            testTranslation(formDef, FormActivityDef::getTranslatedTitles, enInst.get().getTitle(), "en");

            Optional<ActivityInstance> ruInst = activityInstanceService.buildInstanceFromDefinition(
                    handle, userGuid, userGuid, studyGuid, instanceGuid, "ru", null,
                    AIBuilderCustomizationFlags.create().setCreateFormInstance(true).setRenderFormTitleSubtitle(true));
            testTranslation(formDef, FormActivityDef::getTranslatedTitles, ruInst.get().getTitle(), "ru");
            handle.rollback();
        });
    }

    @Test
    public void testGetBaseFormByGuid_ReadonlyHintTemplateRenderedCorrectly() {
        TransactionWrapper.useTxn(handle -> {
            FormActivityDef formDef = insertDummyActivity(handle, userGuid, studyGuid);
            String instanceGuid = insertNewInstance(handle, formDef.getActivityId(), userGuid);
            FormInstance enInst = dao.getTranslatedFormByGuid(handle, instanceGuid, "en", ContentStyle.STANDARD);
            assertNotNull(enInst);
            assertEquals("Please contact your organization for details", enInst.getReadonlyHint());
            handle.rollback();
        });
    }

    private void testTranslation(
            FormActivityDef form,
            Function<FormActivityDef,
                    List<Translation>> formDefListMethod,
            String expectedValue,
            String languageCode
    ) {
        List<Translation> translations =
                formDefListMethod.apply(form).stream().filter(st -> st.getLanguageCode().equals(languageCode)).collect(toList());
        assertEquals(1, translations.size());
        assertNotNull(expectedValue);
        assertEquals(translations.get(0).getText(), expectedValue);
    }

    @Test
    public void testGetTranslatedFormByGuid() {
        TransactionWrapper.useTxn(handle -> {
            FormActivityDef form = insertDummyActivity(handle, userGuid, studyGuid);
            String instanceGuid = insertNewInstance(handle, form.getActivityId(), userGuid);

            FormInstance inst = dao.getTranslatedFormByGuid(handle, instanceGuid, "en", ContentStyle.STANDARD);
            assertNotNull(form);
            assertEquals(FormType.GENERAL, inst.getFormType());
            assertEquals("test activity", inst.getTitle());
            assertEquals(instanceGuid, inst.getGuid());
            assertEquals(InstanceStatusType.CREATED, inst.getStatusType());
            assertTrue(form.getSections().isEmpty());

            handle.rollback();
        });
    }

    private String insertNewInstance(Handle handle, long activityId, String userGuid) {
        return handle.attach(ActivityInstanceDao.class)
                .insertInstance(activityId, userGuid, userGuid, InstanceStatusType.CREATED, false)
                .getGuid();
    }

    private FormActivityDef insertDummyActivityWithoutSubtitle(Handle handle, String userGuid, String studyGuid) {
        FormActivityDef form = FormActivityDef.generalFormBuilder("ACT" + Instant.now().toEpochMilli(), "v1", studyGuid)
                .addName(new Translation("en", "activity name"))
                .addName(new Translation("ru", "activity name"))
                .addTitle(new Translation("en", "test activity"))
                .addTitle(new Translation("ru", "тестовая деятельность"))
                .build();
        long userId = handle.attach(JdbiUser.class).getUserIdByGuid(userGuid);
        handle.attach(ActivityDao.class).insertActivity(form, RevisionMetadata.now(userId, "add test activity"));
        assertNotNull(form.getActivityId());
        return form;
    }

    private FormActivityDef insertDummyActivity(Handle handle, String userGuid, String studyGuid) {
        Template template = Template.html("$contact_your_organization");
        TemplateVariable templateVariable = TemplateVariable.single(
                "contact_your_organization",
                "en",
                "Please contact your organization for details"
        );
        template.addVariable(templateVariable);
        FormActivityDef form = FormActivityDef.generalFormBuilder("ACT" + Instant.now().toEpochMilli(), "v1", studyGuid)
                .addName(new Translation("en", "activity name"))
                .addName(new Translation("ru", "activity name"))
                .addTitle(new Translation("en", "test activity"))
                .addTitle(new Translation("ru", "тестовая деятельность"))
                .addSubtitle(new Translation("en", "test subtitle"))
                .addSubtitle(new Translation("ru", "тестовый субтитр"))
                .setReadonlyHintTemplate(template)
                .build();
        long userId = handle.attach(JdbiUser.class).getUserIdByGuid(userGuid);
        handle.attach(ActivityDao.class).insertActivity(form, RevisionMetadata.now(userId, "add test activity"));
        assertNotNull(form.getActivityId());
        return form;
    }
}
