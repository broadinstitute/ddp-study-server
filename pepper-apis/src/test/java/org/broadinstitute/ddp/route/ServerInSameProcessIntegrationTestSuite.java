package org.broadinstitute.ddp.route;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 * These tests need to have server running in same process. In CircleCi running tests these are treated
 * differently then other route tests that just need access to the server via http and and the database
 *
 * <p>NOTE: need to add exclude patterns in the CircleciParallelBuild profile in parent-pom.xml,
 * or rename your test class so it ends with `StandaloneTest`.
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({
        AdminCreateStudyParticipantRouteStandaloneTest.class,
        GetParticipantInfoRouteStandaloneTest.class,
        ListCancersRouteStandaloneTest.class,
        GetCancerSuggestionsRouteStandaloneTest.class,
        GetDsmDrugSuggestionsRouteStandaloneTest.class,
        GovernedParticipantRegistrationRouteStandaloneTest.class,
        PatchFormAnswersRouteStandaloneTest.class,
        PutFormAnswersRouteStandaloneTest.class,
        PutFormAnswersRouteStandalone2Test.class,
        PutFormAnswersRouteStandalone3Test.class,
        GetActivityInstanceRouteStandaloneTest.class,
        SendEmailRouteStandaloneTest.class,
        UserActivityInstanceListRouteStandaloneTest.class,
        UserRegistrationRouteStandaloneTest.class
})
public class ServerInSameProcessIntegrationTestSuite extends IntegrationTestSuite {
}
