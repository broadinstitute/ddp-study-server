package org.broadinstitute.ddp.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.content.I18nTemplateConstants;
import org.broadinstitute.ddp.db.FormInstanceDao;
import org.broadinstitute.ddp.db.SectionBlockDao;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.template.TemplateVariable;
import org.broadinstitute.ddp.model.activity.instance.ActivityInstance;
import org.broadinstitute.ddp.model.activity.instance.FormInstance;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.ActivityType;
import org.broadinstitute.ddp.model.activity.types.FormType;
import org.broadinstitute.ddp.model.activity.types.TemplateType;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.broadinstitute.ddp.pex.TreeWalkInterpreter;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.jdbi.v3.core.Handle;
import org.junit.BeforeClass;
import org.junit.Test;

public class ActivityInstanceServiceTest extends TxnAwareBaseTest {

    private static TestDataSetupUtil.GeneratedTestData testData;
    private static ActivityInstanceService service;

    @BeforeClass
    public static void setup() {
        PexInterpreter interpreter = new TreeWalkInterpreter();
        SectionBlockDao sectBlockDao = new SectionBlockDao();

        org.broadinstitute.ddp.db.ActivityInstanceDao actInstDao =
                new org.broadinstitute.ddp.db.ActivityInstanceDao(FormInstanceDao.fromDaoAndConfig(sectBlockDao, sqlConfig));
        service = new ActivityInstanceService(actInstDao, interpreter);

        TransactionWrapper.useTxn(handle -> testData = TestDataSetupUtil.generateBasicUserTestData(handle));
    }

    @Test
    public void getTranslatedActivity() {
        TransactionWrapper.useTxn(handle -> {
            String instanceGuid = setupActivityAndInstance(handle);

            Optional<ActivityInstance> inst = service.getTranslatedActivity(handle,
                    testData.getUserGuid(), testData.getUserGuid(), ActivityType.FORMS, instanceGuid, "en", ContentStyle.STANDARD);
            assertTrue(inst.isPresent());
            assertEquals(inst.get().getActivityType(), ActivityType.FORMS);
            assertEquals(inst.get().getGuid(), instanceGuid);

            handle.rollback();
        });
    }

    @Test
    public void getTranslatedActivity_notFound() {
        TransactionWrapper.useTxn(handle -> {
            Optional<ActivityInstance> inst = service.getTranslatedActivity(handle,
                    testData.getUserGuid(), testData.getUserGuid(), ActivityType.FORMS, "random guid", "en", ContentStyle.STANDARD);
            assertNotNull(inst);
            assertFalse(inst.isPresent());
        });
    }

    @Test
    public void getTranslatedForm() {
        TransactionWrapper.useTxn(handle -> {
            String instanceGuid = setupActivityAndInstance(handle);

            Optional<FormInstance> inst = service.getTranslatedForm(handle, testData.getUserGuid(),
                    testData.getUserGuid(), instanceGuid, "en",
                    ContentStyle.STANDARD);
            assertTrue(inst.isPresent());
            assertEquals(inst.get().getActivityType(), ActivityType.FORMS);
            assertEquals(inst.get().getGuid(), instanceGuid);
            assertEquals(inst.get().getFormType(), FormType.PREQUALIFIER);
            assertFalse(inst.get().getBodySections().isEmpty());
            assertNotNull(inst.get().getActivityDefinitionLastUpdatedText());

            FormActivityDef testActivityDef = buildTestFormActivityDefinition();
            Translation varTransText = testActivityDef.getLastUpdatedTextTemplate().getVariable("LUNAR_MESSAGE").get()
                    .getTranslation("en").get();
            assertTrue(inst.get().getActivityDefinitionLastUpdatedText().startsWith(varTransText.getText()));
            assertTrue(inst.get().getActivityDefinitionLastUpdatedText().endsWith("July 20, 1969"));
            assertEquals(testActivityDef.getLastUpdated(), inst.get().getActivityDefinitionLastUpdated());


            handle.rollback();
        });
    }

    @Test
    public void getTranslatedForm_notFound() {
        TransactionWrapper.useTxn(handle -> {
            Optional<FormInstance> inst = service.getTranslatedForm(handle, testData.getUserGuid(), testData.getUserGuid(),
                    "random guid", "en",
                    ContentStyle.STANDARD);
            assertNotNull(inst);
            assertFalse(inst.isPresent());
        });
    }

    private String setupActivityAndInstance(Handle handle) {
        FormActivityDef formDef = buildTestFormActivityDefinition();
        handle.attach(ActivityDao.class).insertActivity(formDef, RevisionMetadata.now(testData.getUserId(), "add activity"));
        assertNotNull(formDef.getActivityId());

        return handle.attach(ActivityInstanceDao.class)
                .insertInstance(formDef.getActivityId(), testData.getUserGuid())
                .getGuid();
    }

    private FormActivityDef buildTestFormActivityDefinition() {
        String code = "ACT" + Instant.now().toEpochMilli();

        Template lastUpdatedTextTemplate = new Template(TemplateType.HTML, null, "$LUNAR_MESSAGE $"
                + I18nTemplateConstants.LAST_UPDATED);

        lastUpdatedTextTemplate.addVariable(new TemplateVariable("LUNAR_MESSAGE", Arrays.asList(
                new Translation("en", "The Eagle landed on "))));

        return FormActivityDef.formBuilder(FormType.PREQUALIFIER, code, "v1", testData.getStudyGuid())
                .addName(new Translation("en", "test prequal activity"))
                .addSection(new FormSectionDef(null, Collections.emptyList()))
                .setLastUpdatedTextTemplate(lastUpdatedTextTemplate)
                .setLastUpdated(LocalDateTime.of(1969, 7, 20, 4, 17))
                .build();
    }
}
