package org.broadinstitute.ddp.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import com.auth0.exception.Auth0Exception;
import com.typesafe.config.Config;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.constants.TestConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.json.export.FireCloudEntities;
import org.broadinstitute.ddp.json.export.FireCloudEntity;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Ignore
public class FireCloudExportServiceTest extends TxnAwareBaseTest {

    public static final String PARTICIPANT_ID_ENTITY_KEY = "entity:participant_id";
    private static final Logger LOG = LoggerFactory.getLogger(FireCloudExportServiceTest.class);
    private static FireCloudExportService fireCloudExportService;
    private static File pathToTestServiceAccount;
    private static String importEntitiesUrl;
    private static String bulkEntitiesGetUrl;
    private static String workspaceNamespace;
    private static String workspaceName;
    private Collection<String> pushedParticipantIds = new HashSet<>();

    private static Header newFcAuthHeader() {
        return FireCloudExportService.newFcAuthHeader(pathToTestServiceAccount.getAbsolutePath());
    }

    @BeforeClass
    public static void setupFirecloudServiceAccountToken() throws IOException {
        Config firecloudConfig = cfg.getConfig(ConfigFile.FIRECLOUD);
        workspaceNamespace = firecloudConfig.getString(ConfigFile.TEST_FIRECLOUD_WORKSPACE_NAMESPACE);
        workspaceName = firecloudConfig.getString(ConfigFile.TEST_FIRECLOUD_WORKSPACE_NAME);

        String firecloudKeysLocation = System.getProperty(ConfigFile.FIRECLOUD_KEYS_DIR_ENV_VAR);
        File firecloudKeysDir = new File(firecloudKeysLocation);
        if (!firecloudKeysDir.exists() || !firecloudKeysDir.isDirectory()) {
            Assert.fail(String.format("Cannot find directory %s specified in system property %s. Needed for FC service account lookup",
                    firecloudKeysDir, ConfigFile.FIRECLOUD_KEYS_DIR_ENV_VAR));
        }

        File fireCloudTestServiceAccount = new File(firecloudKeysDir, TestConstants.FIRECLOUD_TEST_SERVICE_ACCOUNT_FILE);
        if (!fireCloudTestServiceAccount.exists()) {
            throw new RuntimeException("Firecloud service account "
                    + fireCloudTestServiceAccount.getAbsolutePath() + " not found; no way to connect to firecloud.");
        }
        pathToTestServiceAccount = fireCloudTestServiceAccount;

        importEntitiesUrl = RouteConstants.FireCloud.fireCloudBaseUrl + "/"
                + workspaceNamespace + "/" + workspaceName + "/importEntities";
        bulkEntitiesGetUrl = RouteConstants.FireCloud.fireCloudBaseUrl + "/"
                + workspaceNamespace + "/" + workspaceName + "/entities/participant/tsv";
    }

    @BeforeClass
    public static void setup() throws Auth0Exception {
        fireCloudExportService = FireCloudExportService.fromSqlConfig(sqlConfig);
    }

    @Before
    public void setUp() {
        pushedParticipantIds.clear();
    }

    @Test
    public void testSaveEntitiesToFirecloud() throws Exception {
        String dummyParticipant = "PepperTest" + System.currentTimeMillis();
        String tsvContents = "entity:participant_id\tPrequalAgree\n"
                + dummyParticipant + "\t(not actually real)";

        Request request = Request.Post(importEntitiesUrl)
                .addHeader(newFcAuthHeader())
                .bodyForm(new BasicNameValuePair("entities", tsvContents));
        String waitForFCMessage = "FireCloud down. Waiting 5 seconds then"
                + " trying to post entity to workspace again.";
        HttpResponse httpResponse = fireCloudExportService.runFireCloudAPICallWithBody(request, waitForFCMessage);
        int status = httpResponse.getStatusLine().getStatusCode();

        if (status >= 400) {
            LOG.info("Warning - was unable to post entity to FireCloud.");
        } else {
            pushedParticipantIds.add(dummyParticipant);
            String responseBody = EntityUtils.toString(httpResponse.getEntity());
            Assert.assertEquals("Firecloud responded with:" + responseBody, 200, status);
        }
    }

    @Test
    public void testPutTestStudyOnFireCloud() throws Exception {
        String umbrellaStudyGuid = TestConstants.TEST_STUDY_GUID;
        Date ignoreDataBefore = new Date(0);
        FireCloudEntities fireCloudEntities = TransactionWrapper.withTxn(handle -> {
            FireCloudEntities firecloudEntities = fireCloudExportService
                    .getEntitiesForStudyByUmbrellaStudyGuid(handle, umbrellaStudyGuid, ignoreDataBefore);
            LOG.info("Will post {} participant records to firecloud since {}",
                    firecloudEntities.getFireCloudEntities().size(), ignoreDataBefore);
            return firecloudEntities;
        });
        String tsv = fireCloudEntities.toTSV();

        // transform list into a map for faster comparison.
        // this map will be drained as part of testing and should be empty at the end
        Map<String, FireCloudEntity> entitiesNotFoundAfterComparison = new HashMap<>();
        for (FireCloudEntity fireCloudEntity : fireCloudEntities.getFireCloudEntities()) {
            entitiesNotFoundAfterComparison.put(fireCloudEntity.getEntityName(), fireCloudEntity);
        }

        Collection<String> postedAttributes = new HashSet<>();
        for (FireCloudEntity fireCloudEntity : fireCloudEntities.getFireCloudEntities()) {
            postedAttributes.addAll(fireCloudEntity.getAttributes().keySet());
        }

        LOG.info("Will query for {} attributes", postedAttributes.size());
        long startTime = System.currentTimeMillis();

        Request request = Request.Post(importEntitiesUrl)
                .addHeader(newFcAuthHeader())
                .bodyForm(new BasicNameValuePair("entities", tsv));
        String waitForFCMessage = "FireCloud down. Waiting 5 seconds then "
                + "trying to post entities to workspace again.";
        int status = fireCloudExportService.runFireCloudAPICall(request, waitForFCMessage);

        if (status >= 400) {
            LOG.info("Warning - Entities were not successfully posted to FireCloud.");
        } else {
            LOG.info("It took {}ms to successfully post {} participant records to firecloud",
                    (System.currentTimeMillis() - startTime),
                    fireCloudEntities.getFireCloudEntities().size());
            Collection<String> participantIdsLeftInFirecloud = new HashSet<>();

            // save all the participant ids so we can wipe them out in the teardown
            for (FireCloudEntity fireCloudEntity : fireCloudEntities.getFireCloudEntities()) {
                pushedParticipantIds.add(fireCloudEntity.getEntityName());
            }

            request = Request.Get(bulkEntitiesGetUrl + "?attributeNames=" + StringUtils.join(postedAttributes, ","))
                    .addHeader(newFcAuthHeader());
            waitForFCMessage = "FireCloud down. Waiting 5 seconds then trying to "
                    + "retrieve entities from workspace again.";
            HttpResponse httpResponse = fireCloudExportService.runFireCloudAPICallWithBody(request, waitForFCMessage);
            status = httpResponse.getStatusLine().getStatusCode();

            if (status >= 400) {
                LOG.info("Warning - Entities were not successfully retrieved from FireCloud.");
            } else {
                String queriedTsv = EntityUtils.toString(httpResponse.getEntity());
                BufferedReader reader = new BufferedReader(new StringReader(queriedTsv));

                String line;
                boolean isHeader = true;
                Map<String, Integer> attributeNameToColumn = new HashMap<>();

                while ((line = reader.readLine()) != null) {
                    String[] queriedAttributes = line.split("\t");
                    if (isHeader) {
                        int columnIndex = 0;
                        for (String stableId : queriedAttributes) {
                            attributeNameToColumn.put(stableId, columnIndex++);
                        }
                        isHeader = false;
                    } else {
                        String participantId = queriedAttributes[attributeNameToColumn.get(PARTICIPANT_ID_ENTITY_KEY)];
                        FireCloudEntity postedEntity = entitiesNotFoundAfterComparison.get(participantId);
                        if (postedEntity != null) {
                            long secondStartTime = System.currentTimeMillis();
                            for (String key : postedEntity.getAttributes().keySet()) {
                                String postedAttributeValue = postedEntity.getAttributes().get(key);
                                String queriedAttributeValue = queriedAttributes[attributeNameToColumn.get(key)];
                                if (!postedAttributeValue.equals(queriedAttributeValue)) {
                                    throw new RuntimeException("Saved " + key + " attribute value "
                                            + postedAttributeValue
                                            + " for participant " + participantId
                                            + " but read back " + queriedAttributeValue);
                                }
                            }
                            LOG.info("It took {}ms to read {} attributes records from firecloud",
                                    (System.currentTimeMillis() - secondStartTime),
                                    postedEntity.getAttributes().size());
                        } else {
                            participantIdsLeftInFirecloud.add(participantId);
                            LOG.debug("Skipping participant {} found in workspace.  It's unrelated to what we posted so"
                                    + " we'll ignore it assuming it got there from some other client.", participantId);
                        }
                        entitiesNotFoundAfterComparison.remove(participantId);
                    }
                }
            }
            Assert.assertEquals("There are " + entitiesNotFoundAfterComparison.size()
                            + " entities that we posted to firecloud but that were not returned", 0,
                    entitiesNotFoundAfterComparison.size());
            LOG.info("Leaving {} entities in workspace", participantIdsLeftInFirecloud.size());
        }
    }

    @After
    public void deletePushedEntities() throws IOException, InterruptedException {
        String[] fireCloudEntityNames = new String[pushedParticipantIds.size()];
        fireCloudEntityNames = pushedParticipantIds.toArray(fireCloudEntityNames);
        if (fireCloudEntityNames.length != 0) {
            int responseCode = fireCloudExportService.deleteGivenEntities(fireCloudEntityNames,
                    "participant", workspaceNamespace, workspaceName, pathToTestServiceAccount.getAbsolutePath());
            if (responseCode < 400) {
                Assert.assertEquals("FireCloud delete responded with: " + responseCode + " when deleting "
                        + fireCloudEntityNames.length + " participants.", 204, responseCode);
            } else {
                Assert.assertEquals("Unable to delete " + fireCloudEntityNames.length + " FireCloud participants.",
                        502, responseCode);
            }
        }
    }
}
