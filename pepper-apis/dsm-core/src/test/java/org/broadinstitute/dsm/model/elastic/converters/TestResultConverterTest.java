package org.broadinstitute.dsm.model.elastic.converters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.Test;

public class TestResultConverterTest {

    @Test
    public void convertTestResult() {

        TestResultConverter testResultConverter = new TestResultConverter();
        testResultConverter.fieldName = "test_result";
        testResultConverter.fieldValue =
                "[{\"isCorrected\":false,\"result\":\"UNSATISFACTORY_12\",\"timeCompleted\":\"2020-09-03T12:08:21.657Z\"}]";
        Map<String, Object> actualTestResult = testResultConverter.convert();

        Map<String, Object> testResultAsMap = Map.of(
                "isCorrected", false,
                "result", "UNSATISFACTORY_12",
                "timeCompleted", "2020-09-03T12:08:21.657Z"
        );

        Map<String, List<Map<String, Object>>> expectedTestResults = Map.of("testResult", List.of(testResultAsMap));

        assertEquals(expectedTestResults, actualTestResult);
    }

    @Test
    public void convertEmptyTestResult() {
        TestResultConverter testResultConverter = new TestResultConverter();
        testResultConverter.fieldName = "test_result";
        testResultConverter.fieldValue = "";
        Map<String, Object> actualTestResult = testResultConverter.convert();

        assertEquals(Map.of("testResult", List.of(Map.of())), actualTestResult);
    }

    @Test
    public void transformArrayJsonToMap() {


        String json = "[{\"isCorrected\": true, \"result\": \"Negative\", \"timeCompleted\": \"2020-09-03T12:08:21.657Z\"}]";

        TestResultConverter testResultConverter = new TestResultConverter();
        testResultConverter.fieldName = "test_result";
        testResultConverter.fieldValue = json;

        Map<String, Object> result = testResultConverter.convert();

        Map<String, Object> testResult = (Map) ((List) result.get("testResult")).get(0);
        assertTrue((boolean) testResult.get("isCorrected"));
        assertEquals("Negative", testResult.get("result"));
        assertEquals("2020-09-03T12:08:21.657Z", testResult.get("timeCompleted"));
    }
}
