package org.broadinstitute.ddp.service;

import static org.broadinstitute.ddp.constants.FireCloudConstants.MAX_FC_TRIES;
import static org.junit.Assert.assertNull;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import java.io.File;
import java.io.IOException;
import java.util.List;

import com.google.gson.Gson;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.constants.TestConstants;
import org.broadinstitute.ddp.db.StudyAdminDao;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.exception.FireCloudException;
import org.broadinstitute.ddp.json.export.Workspace;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.junit.MockServerRule;
import org.mockserver.verify.VerificationTimes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Ignore
public class FireCloudMockedTest extends TxnAwareBaseTest {

    private static final Logger LOG = LoggerFactory.getLogger(FireCloudMockedTest.class);

    private static String fireCloudServiceAccountPath;
    private static String userGuid;
    private static String studyGuid;

    private MockServerClient mockServerClient;
    private FireCloudExportService fireCloudExportService = null;
    private Gson gson = new Gson();

    @Rule
    public MockServerRule mockServerRule = new MockServerRule(this);

    @Before
    public void setMockFireCloudExportService() {
        if (fireCloudExportService == null) {
            String baseUrl = "http://localhost:" + mockServerRule.getPort();
            fireCloudExportService = FireCloudExportService.fromSqlConfig(sqlConfig, baseUrl);
        }
    }

    @BeforeClass
    public static void setup() {
        TestDataSetupUtil.insertStaticTestData();

        userGuid = TestConstants.TEST_ADMIN_GUID;
        studyGuid = TestConstants.TEST_STUDY_GUID;

        File firecloudKeysDir = new File(System.getProperty(ConfigFile.FIRECLOUD_KEYS_DIR_ENV_VAR));
        StudyAdminDao studyAdminDao = StudyAdminDao.init(sqlConfig, firecloudKeysDir);
        fireCloudServiceAccountPath = TransactionWrapper.withTxn(handle ->
                studyAdminDao.getServiceAccountPath(handle, userGuid, studyGuid));
    }

    @Test
    public void testErrorHandlingForFireCloudAPICall() throws IOException {
        mockServerClient = mockServerClient.reset();

        mockServerClient.when(request().withPath(""))
                .respond(response().withStatusCode(HttpStatus.SC_UNAUTHORIZED));

        List<Workspace> result = null;
        try {
            result = fireCloudExportService.getWorkspaces(fireCloudServiceAccountPath);
        } catch (FireCloudException e) {
            LOG.info("Successfully failed getWorkspaces route ", e);
        }

        assertNull(result);
        mockServerClient.verify(request().withPath(""),
                VerificationTimes.exactly(MAX_FC_TRIES));
    }

}
