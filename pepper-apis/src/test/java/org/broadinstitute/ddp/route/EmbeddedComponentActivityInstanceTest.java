package org.broadinstitute.ddp.route;

import static io.restassured.RestAssured.given;
import static org.broadinstitute.ddp.model.activity.types.InstanceStatusType.CREATED;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.restassured.http.ContentType;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.FormActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiRevision;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.InstitutionComponentDef;
import org.broadinstitute.ddp.model.activity.definition.MailingAddressComponentDef;
import org.broadinstitute.ddp.model.activity.definition.PhysicianComponentDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.template.TemplateVariable;
import org.broadinstitute.ddp.model.activity.types.BlockType;
import org.broadinstitute.ddp.model.activity.types.ComponentType;
import org.broadinstitute.ddp.model.activity.types.FormType;
import org.broadinstitute.ddp.model.activity.types.InstitutionType;
import org.broadinstitute.ddp.model.activity.types.TemplateType;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.broadinstitute.ddp.util.TestDataSetupUtil.GeneratedTestData;
import org.jdbi.v3.core.Handle;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EmbeddedComponentActivityInstanceTest extends IntegrationTestSuite.TestCase {

    public static final String ADD_INSTITUTION = "Add Institution";
    public static final String ADD_PHYSICIAN = "Add Physician";
    public static final String INSTITUTION_TITLE = "Institution";
    public static final String INSTITUTION_SUBTITLE = "Your Institutions";
    public static final String PHYSICIAN_TITLE = "Your Physicians";
    static final Logger LOG = LoggerFactory.getLogger(EmbeddedComponentActivityInstanceTest.class);
    public static final String INSTANCE_GUID = "instanceGuid";

    // these vars are all set during @Before so that tests can use generated test data
    private static GeneratedTestData testData;
    private static ActivityInstanceDto createdActivityInstance;
    private static String GET_ACTIVITY_INSTANCE_URL;
    private static InstitutionComponentDef institutionComponentDef;
    private static PhysicianComponentDef physicianComponentDef;

    public static InstitutionComponentDef buildInstitutionsComponentDef(
            boolean allowMultiple, InstitutionType institutionType,
            boolean showFields) {
        Template addButtonTemplate = null;
        if (allowMultiple) {
            addButtonTemplate = buildTemplate("addButton", ADD_INSTITUTION, true);
        }
        Template titleTemplate = buildTemplate("title", INSTITUTION_TITLE, false);
        Template subtitleTemplate = buildTemplate("subtitle", INSTITUTION_SUBTITLE, false);
        InstitutionComponentDef institutionComponentDef = new InstitutionComponentDef(allowMultiple,
                addButtonTemplate,
                titleTemplate,
                subtitleTemplate,
                institutionType,
                showFields);

        return institutionComponentDef;
    }

    private static Template buildTemplate(String seed, String englishText, boolean isPlainText) {
        String templateVar = "componentTest" + seed + System
                .currentTimeMillis();
        String addButtonTemplateText = Template.VELOCITY_VAR_PREFIX + templateVar;
        Template addButtonTemplate = new Template(TemplateType.HTML, templateVar, addButtonTemplateText);
        String text = englishText;
        if (!isPlainText) {
            text = "<div>" + englishText + "</div>";
        }
        addButtonTemplate.addVariable(new TemplateVariable(templateVar, Collections.singletonList(
                new Translation("en", text))));
        return addButtonTemplate;
    }

    public static PhysicianComponentDef buildPhysiciansComponentDef(boolean allowMultiple,
                                                                    InstitutionType institutionType,
                                                                    boolean showFields) {
        Template addButtonTemplate = null;
        if (allowMultiple) {
            addButtonTemplate = buildTemplate("addButton", ADD_PHYSICIAN, true);
        }
        Template titleTemplate = buildTemplate("physicianTitle", PHYSICIAN_TITLE, false);
        PhysicianComponentDef physicianComponentDef = new PhysicianComponentDef(allowMultiple,
                addButtonTemplate,
                titleTemplate,
                null,
                institutionType,
                showFields);
        return physicianComponentDef;
    }

    /**
     * Creates a new form activity that has a mailing address component, a physician component,
     * and an institution component.
     */
    private static FormActivityDef setupTestActivityWithEmbeddedComponents(Handle handle, String studyGuid) {
        FormActivityDao formActivityDao = handle.attach(FormActivityDao.class);
        JdbiRevision revisionDao = handle.attach(JdbiRevision.class);
        JdbiUser userDao = handle.attach(JdbiUser.class);

        String activityCode = "EMBEDDED_COMPONENTS" + System.currentTimeMillis();
        long revisionId = revisionDao.insert(userDao.getUserIdByGuid(testData.getUserGuid()),
                Instant.now().toEpochMilli(),
                null,
                "embedded components test " + System.currentTimeMillis());

        List<FormBlockDef> blocksWithEmbeddedComponents = new ArrayList<>();
        blocksWithEmbeddedComponents.add(new MailingAddressComponentDef(null, null));
        institutionComponentDef = buildInstitutionsComponentDef(true, InstitutionType.INSTITUTION, false);
        blocksWithEmbeddedComponents.add(institutionComponentDef);
        physicianComponentDef = buildPhysiciansComponentDef(false, InstitutionType.PHYSICIAN, true);
        blocksWithEmbeddedComponents.add(physicianComponentDef);

        FormActivityDef formActivity = FormActivityDef.formBuilder(FormType.GENERAL, activityCode, "v1", studyGuid)
                .addName(new Translation("en", "testing"))
                .setMaxInstancesPerUser(1)
                .addSection(new FormSectionDef(null, blocksWithEmbeddedComponents))
                .build();

        formActivityDao.insertActivity(formActivity, revisionId);
        return formActivity;
    }

    @BeforeClass
    public static void setUp() {
        TransactionWrapper.useTxn(handle -> {
            testData = TestDataSetupUtil.generateBasicUserTestData(handle);
            FormActivityDef testForm = setupTestActivityWithEmbeddedComponents(handle, testData.getStudyGuid());

            ActivityInstanceDao activityInstanceDao = handle.attach(org.broadinstitute.ddp.db.dao.ActivityInstanceDao
                    .class);
            String userGuid = testData.getTestingUser().getUserGuid();
            createdActivityInstance = activityInstanceDao.insertInstance(testForm.getActivityId(), userGuid, userGuid,
                    CREATED, false);
            LOG.info("Created test activity instance {} for activity {} for user {}", createdActivityInstance
                    .getGuid(), testForm.getActivityCode(), userGuid);
        });

        String endpoint = RouteConstants.API.USER_ACTIVITIES_INSTANCE
                .replace(RouteConstants.PathParam.USER_GUID, testData.getTestingUser().getUserGuid())
                .replace(RouteConstants.PathParam.STUDY_GUID, testData.getStudyGuid())
                .replace(RouteConstants.PathParam.INSTANCE_GUID, "{" + INSTANCE_GUID + "}");
        GET_ACTIVITY_INSTANCE_URL = RouteTestUtil.getTestingBaseUrl() + endpoint;
    }

    /**
     * Verifies that the activity instance we created with embedded components
     * renders properly via activity instance get route.
     */
    @Test
    public void testEmbeddedComponentsAppearInActivityInstanceGetRoute() {
        String componentType = BlockType.COMPONENT.name();
        given().auth().oauth2(testData.getTestingUser().getToken())
                .pathParam(INSTANCE_GUID, createdActivityInstance.getGuid())
                .when().get(GET_ACTIVITY_INSTANCE_URL).then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("guid", equalTo(createdActivityInstance.getGuid()))
                .body("sections", hasSize(1))
                .body("sections[0].blocks", hasSize(3))
                .body("sections[0].blocks[0].blockType", equalTo(componentType))
                .body("sections[0].blocks[0].displayNumber", equalTo(1))
                .body("sections[0].blocks[1].blockType", equalTo(componentType))
                .body("sections[0].blocks[1].displayNumber", equalTo(2))
                .body("sections[0].blocks[2].blockType", equalTo(componentType))
                .body("sections[0].blocks[2].displayNumber", equalTo(3))
                .body("sections[0].blocks[0].component.componentType", equalTo(ComponentType.MAILING_ADDRESS.name()))
                .body("sections[0].blocks[1].component.componentType", equalTo(ComponentType.INSTITUTION.name()))
                .body("sections[0].blocks[1].component.parameters.allowMultiple", equalTo(
                        institutionComponentDef.allowMultiple()))
                .body("sections[0].blocks[1].component.parameters.showFieldsInitially", equalTo(
                        institutionComponentDef.showFields()))
                .body("sections[0].blocks[1].component.parameters.addButtonText", containsString(
                        ADD_INSTITUTION))
                .body("sections[0].blocks[1].component.parameters.titleText", containsString(
                        INSTITUTION_TITLE))
                .body("sections[0].blocks[1].component.parameters.subtitleText", containsString(
                        INSTITUTION_SUBTITLE))
                .body("sections[0].blocks[1].component.parameters.institutionType", equalTo(
                        institutionComponentDef.getInstitutionType().name()))
                .body("sections[0].blocks[2].component.componentType", equalTo(ComponentType.PHYSICIAN.name()))
                .body("sections[0].blocks[2].component.parameters.allowMultiple", equalTo(
                        physicianComponentDef.allowMultiple()))
                .body("sections[0].blocks[2].component.parameters.showFieldsInitially", equalTo(
                        physicianComponentDef.showFields()))
                .body("sections[0].blocks[2].component.parameters.addButtonText", nullValue())
                .body("sections[0].blocks[2].component.parameters.titleText", containsString(
                        PHYSICIAN_TITLE))
                .body("sections[0].blocks[2].component.parameters.subtitleText", nullValue())
                .body("sections[0].blocks[2].component.parameters.institutionType", equalTo(
                        physicianComponentDef.getInstitutionType().name()));
    }
}
