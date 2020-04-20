package org.broadinstitute.ddp.route;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
        GetParticipantInfoRouteTest.class,
        ListCancersRouteTest.class,
        GetDsmDrugSuggestionsRouteTest.class
})
public class ServerInSameProcessIntegrationTestSuite extends IntegrationTestSuite {
}
