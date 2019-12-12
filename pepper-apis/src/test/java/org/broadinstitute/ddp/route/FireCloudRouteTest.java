package org.broadinstitute.ddp.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import com.auth0.exception.Auth0Exception;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.typesafe.config.Config;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.util.EntityUtils;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.constants.RouteConstants.API;
import org.broadinstitute.ddp.constants.RouteConstants.PathParam;
import org.broadinstitute.ddp.constants.TestConstants;
import org.broadinstitute.ddp.db.AnswerDao;
import org.broadinstitute.ddp.db.StudyAdminDao;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.json.export.ExportStudyPayload;
import org.broadinstitute.ddp.json.export.ListStudiesResponse;
import org.broadinstitute.ddp.json.export.Workspace;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.question.DateQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.instance.answer.DateAnswer;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.DateFieldType;
import org.broadinstitute.ddp.model.activity.types.DateRenderMode;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.model.activity.types.TemplateType;
import org.broadinstitute.ddp.service.FireCloudExportService;
import org.broadinstitute.ddp.util.TestUtil;
import org.jdbi.v3.core.Handle;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Ignore
public class FireCloudRouteTest extends IntegrationTestSuite.TestCase {
    private static final Logger LOG = LoggerFactory.getLogger(FireCloudRouteTest.class);

    private static String listWorkspacesUrl;
    private static String listStudiesUrl;
    private static String exportStudiesUrl;

    private static String token;
    private static Gson gson;
    private static Date includeOnlyAfter;

    private static FireCloudExportService fireCloudExportService;
    private static StudyAdminDao studyAdminDao;
    private static AnswerDao answerDao;

    private static String workspaceNamespace;
    private static String workspaceName;

    private static Collection<String> participantIds = new ArrayList<>();
    private static Collection<Integer> acceptableStatuses;

    private static String dateStableId;
    private static String instanceGuid;

    @BeforeClass
    public static void setup() throws Auth0Exception {
        Config sqlConfig = RouteTestUtil.getSqlConfig();
        Config cfg = RouteTestUtil.getConfig();
        Config firecloudConfig = cfg.getConfig(ConfigFile.FIRECLOUD);
        workspaceNamespace = firecloudConfig.getString(ConfigFile.TEST_FIRECLOUD_WORKSPACE_NAMESPACE);
        workspaceName = firecloudConfig.getString(ConfigFile.TEST_FIRECLOUD_WORKSPACE_NAME);
        token = RouteTestUtil.loginStaticAdminUserForToken();
        gson = new Gson();

        listWorkspacesUrl = RouteTestUtil.getTestingBaseUrl() + API.ADMIN_WORKSPACES;
        listStudiesUrl = RouteTestUtil.getTestingBaseUrl() + API.ADMIN_STUDIES;
        exportStudiesUrl = RouteTestUtil.getTestingBaseUrl()
                + API.EXPORT_STUDY.replace(PathParam.STUDY_GUID, TestConstants.TEST_STUDY_GUID);

        fireCloudExportService = FireCloudExportService.fromSqlConfig(sqlConfig);
        studyAdminDao = StudyAdminDao.init(sqlConfig,
                new File(System.getProperty(ConfigFile.FIRECLOUD_KEYS_DIR_ENV_VAR)));
        answerDao = AnswerDao.fromSqlConfig(sqlConfig);

        includeOnlyAfter = new Date(0);

        acceptableStatuses = new ArrayList<>();
        acceptableStatuses.add(HttpStatus.SC_OK);
        acceptableStatuses.add(HttpStatus.SC_BAD_GATEWAY);

        TransactionWrapper.useTxn(FireCloudRouteTest::setupActivityAndInstance);
    }

    private static void setupActivityAndInstance(Handle handle) {
        long userId = handle.attach(JdbiUser.class).getUserIdByGuid(TestConstants.TEST_USER_GUID);
        long timestamp = Instant.now().toEpochMilli();

        dateStableId = "FIRECLOUD_DATE_" + timestamp;
        DateQuestionDef dateDef = DateQuestionDef.builder().setStableId(dateStableId)
                .setRenderMode(DateRenderMode.TEXT)
                .setPrompt(new Template(TemplateType.TEXT, null, "date prompt"))
                .addFields(DateFieldType.YEAR, DateFieldType.MONTH, DateFieldType.DAY)
                .build();

        String activityCode = "FIRECLOUD_ACT_" + timestamp;
        FormActivityDef form = FormActivityDef.generalFormBuilder(activityCode, "v1", TestConstants.TEST_STUDY_GUID)
                .addName(new Translation("en", "test activity"))
                .addSection(new FormSectionDef(null, TestUtil.wrapQuestions(dateDef)))
                .build();

        handle.attach(ActivityDao.class).insertActivity(form, RevisionMetadata.now(userId, "insert test activity"));
        assertNotNull(form.getActivityId());

        instanceGuid = handle.attach(ActivityInstanceDao.class)
                .insertInstance(form.getActivityId(), TestConstants.TEST_USER_GUID)
                .getGuid();
    }

    private static String getServiceAccountPath(Handle handle) {
        String umbrellaStudyGuid = TestConstants.TEST_STUDY_GUID;
        String adminGuid = TestConstants.TEST_ADMIN_GUID;
        return studyAdminDao.getServiceAccountPath(handle, adminGuid, umbrellaStudyGuid);
    }

    @AfterClass
    public static void deletePushedEntities() throws IOException {
        if (participantIds.size() != 0) {
            String[] participantIdArray = new String[participantIds.size()];
            String path = TransactionWrapper.withTxn(FireCloudRouteTest::getServiceAccountPath);
            int responseCode = fireCloudExportService.deleteGivenEntities(participantIds.toArray(participantIdArray),
                    "participant", workspaceNamespace, workspaceName, path);
            if (responseCode < HttpStatus.SC_BAD_REQUEST) {
                Assert.assertEquals("FireCloud delete responded with: " + responseCode + " when deleting "
                        + participantIds.size() + " participants.", HttpStatus.SC_NO_CONTENT, responseCode);
            } else {
                Assert.assertEquals("Unable to delete " + participantIds.size() + " FireCloud participants.",
                        HttpStatus.SC_BAD_GATEWAY, responseCode);
            }
        }
        TransactionWrapper.useTxn(handle ->
                handle.attach(ActivityInstanceDao.class).deleteByInstanceGuid(instanceGuid));
    }

    /**
     * tests that for test user, retrieves all study names for which they are an admin.
     */
    @Test
    public void testGetStudies() throws Exception {
        Response response = RouteTestUtil.buildAuthorizedGetRequest(token, listStudiesUrl).execute();
        HttpResponse res = response.returnResponse();
        Assert.assertEquals(HttpStatus.SC_OK, res.getStatusLine().getStatusCode());

        Type listType = new TypeToken<ArrayList<ListStudiesResponse>>() {
        }.getType();
        ArrayList<ListStudiesResponse> output = gson.fromJson(EntityUtils.toString(res.getEntity()), listType);

        assertEquals(1, output.size());
        assertEquals("test-study", output.get(0).getStudyName());
        assertEquals((Integer) 1, output.get(0).getParticipantCount());
    }

    @Test
    public void testGetWorkspaces() throws Exception {
        Response response = RouteTestUtil.buildAuthorizedGetRequest(token, listWorkspacesUrl).execute();
        HttpResponse res = response.returnResponse();
        int actualStatus = res.getStatusLine().getStatusCode();

        assertTrue(acceptableStatuses.contains(actualStatus));

        if (actualStatus == HttpStatus.SC_BAD_GATEWAY) {
            LOG.error("Warning - FireCloud is down and was unable to retrieve workspaces.");
        } else {
            Type listType = new TypeToken<ArrayList<Workspace>>() {
            }.getType();
            List<Workspace> output = gson.fromJson(EntityUtils.toString(res.getEntity()), listType);
            Assert.assertEquals(1, output.size());
            Assert.assertEquals(workspaceName, output.get(0).getName());
            Assert.assertEquals(workspaceNamespace, output.get(0).getNamespace());
        }
    }

    @Test
    public void testExportStudy() throws Exception {
        String umbrellaStudyGuid = TestConstants.TEST_STUDY_GUID;

        DateAnswer dateAnswer = new DateAnswer(37L, dateStableId, null, 1995, 3, 15);
        String guid = TransactionWrapper.withTxn(handle ->
                answerDao.createAnswer(handle, dateAnswer, TestConstants.TEST_USER_GUID, instanceGuid));

        ExportStudyPayload payload = new ExportStudyPayload(workspaceNamespace, workspaceName, includeOnlyAfter);
        String payloadJson = gson.toJson(payload);
        Request request = RouteTestUtil.buildAuthorizedPostRequest(token, exportStudiesUrl, payloadJson);
        HttpResponse response = request.execute().returnResponse();
        int actualStatus = response.getStatusLine().getStatusCode();

        assertTrue(acceptableStatuses.contains(actualStatus));

        if (actualStatus == HttpStatus.SC_BAD_GATEWAY) {
            LOG.error("Warning - FireCloud is down and was unable to export study.");
            participantIds = new ArrayList<>();
        } else {
            Type collectionType = new TypeToken<Collection<String>>() {
            }.getType();
            participantIds = gson.fromJson(EntityUtils.toString(response.getEntity()), collectionType);

            boolean success = TransactionWrapper.withTxn((Handle handle) -> {
                try {
                    return fireCloudExportService.compareFireCloudToStudyData(handle, umbrellaStudyGuid,
                            payload, getServiceAccountPath(handle));
                } catch (IOException e) {
                    throw new DDPException("Failure to successfully export test", e);
                }
            });
            assertTrue(success);
        }
        TransactionWrapper.useTxn((Handle handle) -> {
            Long id = answerDao.getAnswerIdByGuids(handle, instanceGuid, guid);
            answerDao.deleteAnswerByIdAndType(handle, id, QuestionType.DATE);
        });
    }
}
