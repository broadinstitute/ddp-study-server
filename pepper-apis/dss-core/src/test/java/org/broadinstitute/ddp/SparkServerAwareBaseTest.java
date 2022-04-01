package org.broadinstitute.ddp;

import java.util.function.Supplier;

import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.AuthDao;
import org.broadinstitute.ddp.db.dao.JdbiClient;
import org.broadinstitute.ddp.filter.StudyAdminAuthFilter;
import org.broadinstitute.ddp.filter.TokenConverterFilter;
import org.broadinstitute.ddp.route.RouteTestUtil;
import org.broadinstitute.ddp.security.JWTConverter;
import org.broadinstitute.ddp.transformers.NullableJsonTransformer;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import spark.ResponseTransformer;
import spark.Spark;

/**
 * Base class to setup config, database connection (to support transactions) and Spark server
 * to handle REST endpoints.
 */
public abstract class SparkServerAwareBaseTest extends TxnAwareBaseTest {
    protected static final String RESPONSE_BODY_PARAM_CODE = "code";

    protected static final int SPARK_PORT = 5559;
    protected static final String LOCALHOST = "http://localhost:";
    protected static final String PLACEHOLDER__STUDY = "{study}";
    protected static final String PLACEHOLDER__USER = "{user}";

    protected static String urlTemplate;
    protected static TestDataSetupUtil.GeneratedTestData testData;
    protected static int port;
    protected static ResponseTransformer jsonSerializer = new NullableJsonTransformer();


    public static boolean mapFiltersBeforeRoutes() {
        Spark.before(RouteConstants.API.BASE + "/*", new TokenConverterFilter(new JWTConverter()));
        Spark.before(RouteConstants.API.ADMIN_BASE + "/*", new StudyAdminAuthFilter());
        return true;
    }

    public static class SparkServerTestRunner {

        public void setupSparkServer(
                Supplier<Boolean> mapFiltersBeforeRoutes,
                Supplier<Boolean> mapRoutes,
                Supplier<String> buildUrlTemplate) {
            port = RouteTestUtil.findOpenPortOrDefault(SPARK_PORT);
            Spark.port(port);
            mapFiltersBeforeRoutes.get();
            mapRoutes.get();
            Spark.awaitInitialization();
            urlTemplate = buildUrlTemplate.get();
            TransactionWrapper.useTxn(handle -> {
                testData = TestDataSetupUtil.generateBasicUserTestData(handle);
                handle.attach(AuthDao.class).assignStudyAdmin(testData.getUserId(), testData.getStudyId());
                handle.attach(JdbiClient.class).updateWebPasswordRedirectUrlByAuth0ClientIdAndAuth0Domain(
                        LOCALHOST, testData.getAuth0ClientId(), testData.getTestingClient().getAuth0Domain());
            });
        }

        public void tearDownSparkServer() {
            Spark.stop();
            Spark.awaitStop();
            TransactionWrapper.useTxn(handle -> {
                handle.attach(JdbiClient.class).updateWebPasswordRedirectUrlByAuth0ClientIdAndAuth0Domain(
                        null, testData.getAuth0ClientId(), testData.getTestingClient().getAuth0Domain());
                handle.attach(AuthDao.class).removeAdminFromAllStudies(testData.getUserId());
            });
        }
    }
}
