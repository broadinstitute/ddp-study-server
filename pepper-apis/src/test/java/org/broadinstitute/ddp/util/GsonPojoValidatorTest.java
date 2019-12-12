package org.broadinstitute.ddp.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import com.google.gson.Gson;
import org.junit.Test;

public class GsonPojoValidatorTest {
    GsonPojoValidator validator = new GsonPojoValidator();

    @Test
    public void testSimpleObjectNoErrorsConversion() {
        TestName testName = new TestName("Donald", "Duck");
        List errors = validator.validateAsJson(testName);
        assertEquals(0, errors.size());
    }

    @Test
    public void testSimppleObjectSimpleError() {
        TestName testName2 = new TestName("Donald", null);
        List<JsonValidationError> errors2 = validator.validateAsJson(testName2);
        assertEquals(1, errors2.size());
        assertTrue(errors2.iterator().next().getPropertyPath().toString().contains("lastName"));
    }

    @Test
    public void testTwoLevelObjectNoErrors() {
        TestAddress testAddress = new TestAddress("Donald", "Duck", "street number 1",
                "street number 1", "Big City", "Alabama", "US", "66666", "555-1212", true);

        List<JsonValidationError> errors = validator.validateAsJson(testAddress);
        assertEquals(0, errors.size());
    }

    @Test
    public void testTwoLevelObjectErrorInSecondLevel() {
        //Missing last name in second level of object graph
        TestAddress testAddress = new TestAddress("Donald", null, "street number 1",
                "street number 1", "Big City", "Alabama", "US", "66666", "555-1212", true);
        System.out.println(new Gson().toJson(testAddress));
        List<JsonValidationError> errors = validator.validateAsJson(testAddress);
        assertEquals(1, errors.size());
        JsonValidationError error = errors.iterator().next();
        assertEquals(2, error.getPropertyPath().size());
        assertEquals("theName", error.getPropertyPath().get(0));
        assertEquals("lastName", error.getPropertyPath().get(1));
    }

    @Test
    public void testTopLevelPrimitive() {
        //Missing last name in second level of object graph
        TestAddress testAddress = new TestAddress("Donald", "Duck", "street number 1",
                "street number 1", "Big City", "Alabama", "US", "66666", "555-1212", true);
        testAddress.setCars(3);
        System.out.println(new Gson().toJson(testAddress));
        List<JsonValidationError> errors = validator.validateAsJson(testAddress);
        assertEquals(1, errors.size());
        assertTrue(errors.iterator().next().getPropertyPath().toString().contains("cars"));

    }

    @Test
    public void testTopLevelArrayError() {
        //Missing last name in second level of object graph
        TestAddress testAddress = new TestAddress("Donald", "Duck", "street number 1",
                "street number 1", "Big City", "Alabama", "US", "66666", "555-1212", true);
        testAddress.setSomeNumbers(Arrays.asList(1));
        List<JsonValidationError> errors = validator.validateAsJson(testAddress);
        assertEquals(1, errors.size());
        assertTrue(errors.iterator().next().getPropertyPath().toString().contains("theListOfNumbers"));

    }

    @Test
    public void testErrorInElementInList() {
        TestAddress testAddress = new TestAddress("Donald", "Duck", "street number 1",
                "street number 1", "Big City", "Alabama", "US", "66666", "555-1212", true);
        testAddress.setNames(Arrays.asList(new TestName("Donald", null)));
        System.out.println(new Gson().toJson(testAddress));
        List<JsonValidationError> errors = validator.validateAsJson(testAddress);
        assertEquals(1, errors.size());
        assertTrue(errors.iterator().next().getPropertyPath().toString().contains("listOfNames"));
    }

    @Test
    public void testErrorInElementInMap() {
        TestAddress testAddress = new TestAddress("Donald", "Duck", "street number 1",
                "street number 1", "Big City", "Alabama", "US", "66666", "555-1212", true);
        testAddress.addNameMapping(new TestName("Scooby", "123"));
        System.out.println(new Gson().toJson(testAddress));
        List<JsonValidationError> errors = validator.validateAsJson(testAddress);
        assertEquals(1, errors.size());
        assertTrue(errors.iterator().next().getPropertyPath().toString().contains("lastName"));
        assertTrue(errors.iterator().next().getPropertyPath().toString().contains("Scooby"));
    }

    @Test
    public void testErrorInElementInSet() {
        TestAddress testAddress = new TestAddress("Donald", "Duck", "street number 1",
                "street number 1", "Big City", "Alabama", "US", "66666", "555-1212", true);
        testAddress.addToSetOfNames(new TestName("Roddy", "Piper"), new TestName("Iron", null));
        System.out.println(new Gson().toJson(testAddress));
        List<JsonValidationError> errors = validator.validateAsJson(testAddress);
        assertEquals(1, errors.size());
        assertTrue(errors.iterator().next().getPropertyPath().toString().contains("lastName"));
        assertTrue(errors.iterator().next().getPropertyPath().toString().contains("namesInASet["));

    }

    @Test
    public void testErrorInElementInArray() {
        TestAddress testAddress = new TestAddress("Donald", "Duck", "street number 1",
                "street number 1", "Big City", "Alabama", "US", "66666", "555-1212", true);
        testAddress.setArrayOfNames(new TestName("Roddy", "Piper"), new TestName("Iron", null));
        System.out.println(new Gson().toJson(testAddress));
        List<JsonValidationError> errors = validator.validateAsJson(testAddress);
        assertEquals(1, errors.size());
        assertTrue(errors.iterator().next().getPropertyPath().toString().contains("lastName"));
        assertTrue(errors.iterator().next().getPropertyPath().toString().contains("someArrayOfNames["));

    }


}
