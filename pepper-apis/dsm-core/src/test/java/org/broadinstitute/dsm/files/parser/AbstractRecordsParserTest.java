
package org.broadinstitute.dsm.files.parser;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.broadinstitute.dsm.exception.FileColumnMissing;
import org.broadinstitute.dsm.util.SystemUtil;
import org.junit.Assert;
import org.junit.Test;

public class AbstractRecordsParserTest {

    @Test
    public void parseSingleLine() {
        var content = "field1\tfield2\tfield3\nvalue1\tvalue2\tvalue3\n";
        var regexSeparator = SystemUtil.TAB_SEPARATOR;

        List<TestData> actual = new AbstractRecordsParser<TestData>(content, regexSeparator) {

            @Override
            public Optional<String> findMissingHeaderIfAny(List<String> fieldNames) {
                return Optional.empty();
            }

            @Override
            public TestData transformMapToObject(Map<String, String> map) {
                return new TestData(
                        map.get("field1"),
                        map.get("field2"),
                        map.get("field3"));
            }
        }.parseToObjects();

        Assert.assertEquals(List.of(new TestData("value1", "value2", "value3")), actual);
    }

    @Test
    public void parseMultiLines() {
        var content = "field1\tfield2\tfield3\nvalue1\tvalue2\tvalue3\nvalue4\tvalue5\tvalue6\nvalue7\tvalue8\tvalue9\n";
        var regexSeparator = SystemUtil.TAB_SEPARATOR;

        List<TestData> actual = new AbstractRecordsParser<TestData>(content, regexSeparator) {

            @Override
            public Optional<String> findMissingHeaderIfAny(List<String> fieldNames) {
                return Optional.empty();
            }

            @Override
            public TestData transformMapToObject(Map<String, String> map) {
                return new TestData(
                        map.get("field1"),
                        map.get("field2"),
                        map.get("field3"));
            }
        }.parseToObjects();

        Assert.assertEquals(
                List.of(
                        new TestData("value1", "value2", "value3"),
                        new TestData("value4", "value5", "value6"),
                        new TestData("value7", "value8", "value9")), actual);
    }

    @Test
    public void missHeaderOnPurpose() {

        var content = "field1\tfield2\tfield3\nvalue1\tvalue2\tvalue3\nvalue4\tvalue5\tvalue6\n";
        var regexSeparator = SystemUtil.TAB_SEPARATOR;

        AbstractRecordsParser<TestData> recordsParser = new AbstractRecordsParser<>(content, regexSeparator) {

            @Override
            public Optional<String> findMissingHeaderIfAny(List<String> fieldNames) {
                return Optional.of("field3");
            }

            @Override
            public TestData transformMapToObject(Map<String, String> map) {
                return null;
            }
        };

        Assert.assertThrows(FileColumnMissing.class, recordsParser::parseToObjects);
    }

    @Data
    @AllArgsConstructor
    private static class TestData {
        String field1;
        String field2;
        String field3;
    }

}