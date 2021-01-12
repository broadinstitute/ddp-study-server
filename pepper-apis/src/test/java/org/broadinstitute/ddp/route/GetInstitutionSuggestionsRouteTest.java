package org.broadinstitute.ddp.route;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.util.EntityUtils;
import org.broadinstitute.ddp.constants.RouteConstants.API;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiCity;
import org.broadinstitute.ddp.db.dao.JdbiInstitution;
import org.broadinstitute.ddp.db.dao.JdbiInstitutionType;
import org.broadinstitute.ddp.db.dto.InstitutionDto;
import org.broadinstitute.ddp.json.institution.InstitutionSuggestion;
import org.broadinstitute.ddp.model.activity.types.InstitutionType;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.jdbi.v3.core.Handle;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetInstitutionSuggestionsRouteTest extends IntegrationTestSuite.TestCase {

    private static final Logger LOG = LoggerFactory.getLogger(GetInstitutionSuggestionsRouteTest.class);

    private static TestDataSetupUtil.GeneratedTestData testData;
    private static String token;
    private static String endpoint;
    private static long testCityId;
    private static int limitPadding = 5;
    private static Set<Long> institutionIdsToDelete = new HashSet<>();

    private static void insertTestData() {
        TransactionWrapper.useTxn(
                handle -> {
                    testCityId = handle.attach(JdbiCity.class).insert(
                            NewInstitutionTestData.STATE,
                            NewInstitutionTestData.CITY
                    );
                    JdbiInstitution jdbiInstitution = handle.attach(JdbiInstitution.class);
                    jdbiInstitution.insert(
                            new InstitutionDto(
                                    NewInstitutionTestData.GUID,
                                    testCityId,
                                    NewInstitutionTestData.NAME
                            )
                    );

                    for (int i = 0; i < GetInstitutionSuggestionsRoute.LIMIT + limitPadding; i++) {
                        long id = jdbiInstitution.insert(new InstitutionDto(
                                String.format("%s_limit_%d", NewInstitutionTestData.GUID, i),
                                testCityId,
                                String.format("%s limit %d", NewInstitutionTestData.NAME, i)));
                        institutionIdsToDelete.add(id);
                    }
                }
        );
    }

    private static void deleteTestData() {
        TransactionWrapper.useTxn(
                handle -> {
                    JdbiInstitution jdbiInstitution = handle.attach(JdbiInstitution.class);
                    Assert.assertEquals(institutionIdsToDelete.size(), jdbiInstitution.bulkDeleteByIds(institutionIdsToDelete));

                    jdbiInstitution.deleteByGuid(NewInstitutionTestData.GUID);
                    handle.attach(JdbiCity.class).deleteById(testCityId);
                }
        );
    }

    private static Long getTestInstitutionTypeIdByCode(Handle handle, InstitutionType type) {
        return handle.attach(JdbiInstitutionType.class).getIdByType(type).get();
    }

    private List<InstitutionSuggestion> executeGetRequestAndGetResponseBody(
            String token,
            String url
    ) throws IOException {
        Request request = RouteTestUtil.buildAuthorizedGetRequest(token, url);
        HttpResponse response = request.execute().returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        String json = EntityUtils.toString(response.getEntity());
        return new Gson()
                .fromJson(json, new TypeToken<List<InstitutionSuggestion>>() {
                }.getType());
    }

    private InstitutionSuggestion extractTestInstitutionSuggestion(List<InstitutionSuggestion> suggestions) {
        List<InstitutionSuggestion> testInstSuggestions = suggestions.stream()
                .filter(s -> s.getName().equals(NewInstitutionTestData.NAME))
                .collect(Collectors.toList());
        Assert.assertTrue(testInstSuggestions.size() == 1);
        return testInstSuggestions.get(0);
    }

    @BeforeClass
    public static void setup() {
        TransactionWrapper.useTxn(handle -> testData = TestDataSetupUtil.generateBasicUserTestData(handle));
        token = testData.getTestingUser().getToken();
        endpoint = RouteTestUtil.getTestingBaseUrl() + API.AUTOCOMPLETE_INSTITUTION;
        insertTestData();
    }

    @AfterClass
    public static void teardown() {
        deleteTestData();
    }

    @Test
    public void testPatternMatched() throws IOException {
        String url = endpoint + "?namePattern=Pri";
        List<InstitutionSuggestion> suggestions = executeGetRequestAndGetResponseBody(token, url);
        Assert.assertNotNull(suggestions);
        InstitutionSuggestion testInstSuggestion = extractTestInstitutionSuggestion(suggestions);
        Assert.assertEquals(NewInstitutionTestData.NAME, testInstSuggestion.getName());
        Assert.assertEquals(NewInstitutionTestData.CITY, testInstSuggestion.getCity());
        Assert.assertEquals(NewInstitutionTestData.STATE, testInstSuggestion.getState());
    }

    @Test
    public void testPatternNotMatched() throws IOException {
        String url = endpoint + "?namePattern=Cli";
        List<InstitutionSuggestion> suggestions = executeGetRequestAndGetResponseBody(token, url);
        Assert.assertNotNull(suggestions);
        Assert.assertTrue(suggestions.isEmpty());
    }

    @Test
    public void testResultsAreLimited_defaultLimit() throws IOException {
        String url = endpoint + "?namePattern=limit";
        List<InstitutionSuggestion> suggestions = executeGetRequestAndGetResponseBody(token, url);
        Assert.assertNotNull(suggestions);
        Assert.assertEquals(GetInstitutionSuggestionsRoute.LIMIT, suggestions.size());
    }

    @Test
    public void testResultsAreLimited_customLimit() throws IOException {
        String url = endpoint + "?namePattern=limit&limit=10";
        List<InstitutionSuggestion> suggestions = executeGetRequestAndGetResponseBody(token, url);
        Assert.assertNotNull(suggestions);
        Assert.assertEquals(10, suggestions.size());
    }

    private static class NewInstitutionTestData {
        public static final String GUID = "AABBCCDD77";
        public static final String NAME = "Princeton-Plainsboro Teaching Hospital";
        public static final String CITY = "Princeton";
        public static final String STATE = "New Jersey";
        public static final InstitutionType INSTITUTION_TYPE = InstitutionType.INSTITUTION;
    }

}
