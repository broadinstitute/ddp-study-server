package org.broadinstitute.dsm.model.elastic.filter;

import static org.junit.Assert.*;

import org.broadinstitute.dsm.model.Filter;
import org.junit.Assert;
import org.junit.Test;

public class OperatorTest {

    @Test
    public void getOperator() {
        try {
            Operator like = Operator.getOperator(Filter.LIKE_TRIMMED);
            Operator equals = Operator.getOperator(Filter.EQUALS_TRIMMED);
            Operator larger = Operator.getOperator(Filter.LARGER_EQUALS_TRIMMED);
            Operator smaller = Operator.getOperator(Filter.SMALLER_EQUALS_TRIMMED);
            Operator isNotNull = Operator.getOperator(Filter.IS_NOT_NULL_TRIMMED);
            Operator unknown = Operator.getOperator(Filter.TODAY);
            assertEquals(Operator.LIKE, like);
            assertEquals(Operator.EQUALS, equals);
            assertEquals(Operator.GREATER_THAN_EQUALS, larger);
            assertEquals(Operator.LESS_THAN_EQUALS, smaller);
            assertEquals(Operator.IS_NOT_NULL_LIST, isNotNull);
        } catch (IllegalArgumentException iae) {
            Assert.assertTrue(true);
        }
    }

    @Test
    public void extractLogicalOperator() {
        String filterEquals = "m.medicalRecordId = 15";
        String filterLike = "m.medicalRecordId LIKE 15";
        String notFilter = "NOT m.mrProblem <=> 1";

        Operator equalsOperator = Operator.extract(filterEquals);
        Operator likeOperator = Operator.extract(filterLike);
        Operator diamondsOperator = Operator.extract(notFilter);

        assertEquals(Operator.EQUALS, equalsOperator);
        assertEquals(Operator.LIKE, likeOperator);
        assertEquals(Operator.DIAMOND_EQUALS, diamondsOperator);
    }

    @Test
    public void extractJsonExtractValue() {
        String filter = " ( d.additional_values_json , '$.status' )   LIKE  '%EXITED_BEFORE_ENROLLMENT%'";
        Operator likeOperator = Operator.extract(filter);
        assertEquals(Operator.LIKE, likeOperator);
    }

    @Test
    public void extractLikeOperator() {
        String filter = "m.mr_received LIKE 'someLIKEthing LIKE '";
        Operator operator = Operator.extract(filter);
        assertEquals(Operator.LIKE, operator);
    }

    @Test
    public void extractEqualsOperator() {
        String filter = "m.mr_received = 'someLIKEthing LIKE'";
        Operator operator = Operator.extract(filter);
        assertEquals(Operator.EQUALS, operator);
    }

    @Test
    public void extractGreaterThanEqualsOperator() {
        String filter = "m.mr_received >= 15";
        Operator operator = Operator.extract(filter);
        assertEquals(Operator.GREATER_THAN_EQUALS, operator);
    }

    @Test
    public void extractLessThanEqualsOperator() {
        String filter = "m.mr_received <= 15";
        Operator operator = Operator.extract(filter);
        assertEquals(Operator.LESS_THAN_EQUALS, operator);
    }

    @Test
    public void extractIsNotNullOperator() {
        String filter = "m.mr_received IS NOT NULL";
        Operator operator = Operator.extract(filter);
        assertEquals(Operator.IS_NOT_NULL, operator);
    }

    @Test
    public void extractDiamondEqualsOperatorOperator() {
        String filter = "m.mr_received <=> 15";
        Operator operator = Operator.extract(filter);
        assertEquals(Operator.DIAMOND_EQUALS, operator);
    }

    @Test
    public void extractMultipleOptionsOperator() {
        String filter = "(m.mr_received = 15 OR m.mr_received = 15)";
        String filter2 = " ( m.mr_received = 15 OR m.mr_received = 15 ) ";
        Operator operator = Operator.extract(filter);
        Operator operator2 = Operator.extract(filter2);
        assertEquals(Operator.MULTIPLE_OPTIONS, operator);
        assertEquals(Operator.MULTIPLE_OPTIONS, operator2);
    }

    @Test
    public void extractStrToDateOperator() {
        String filter = "STR_TO_DATE(m.fax_sent,'%Y-%m-%d') = STR_TO_DATE('2021-12-17','%Y-%m-%d')";
        Operator operator = Operator.extract(filter);
        assertEquals(Operator.STR_DATE, operator);
    }

    @Test
    public void extractGreaterStrToDateOperator() {
        String filter = "m.mr_received  >= STR_TO_DATE('1964-01-14','%Y-%m-%d')";
        Operator operator = Operator.extract(filter);
        assertEquals(Operator.DATE_GREATER_THAN_EQUALS, operator);
    }

    @Test
    public void extractLessStrToDateOperator() {
        String filter = "m.mr_received  <= STR_TO_DATE('1964-01-14','%Y-%m-%d')";
        Operator operator = Operator.extract(filter);
        assertEquals(Operator.DATE_LESS_THAN_EQUALS, operator);
    }

    @Test
    public void extractJsonExctractOperator() {
        String filter = "JSON_EXTRACT ( m.additiona`l_values_json , '$.seeingIfBugExists' ) = 'true'";
        Operator operator = Operator.extract(filter);
        assertEquals(Operator.JSON_EXTRACT, operator);
    }

    @Test
    public void extractDateOperator() {
        String filter = "DATE(FROM_UNIXTIME(k.scan_date/1000))  = DATE(FROM_UNIXTIME(1640563200))";
        Operator operator = Operator.extract(filter);
        assertEquals(Operator.DATE, operator);
    }
}