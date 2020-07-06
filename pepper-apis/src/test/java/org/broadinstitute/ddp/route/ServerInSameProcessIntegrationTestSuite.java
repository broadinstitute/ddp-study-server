package org.broadinstitute.ddp.route;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 * These tests need to have server running in same process. In CircleCi running tests these are treated
 * differently then other route tests that just need access to the server via http and and the database
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({
        GetParticipantInfoRouteTest.class,
        ListCancersRouteTest.class,
        GetDsmDrugSuggestionsRouteTest.class,
        PatchFormAnswersRouteStandaloneTest.class
})
public class ServerInSameProcessIntegrationTestSuite extends IntegrationTestSuite {
}
