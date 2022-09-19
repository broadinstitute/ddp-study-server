
package org.broadinstitute.dsm.files.parser;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.Assert;
import org.junit.Test;

public class TSVRecordsParserTest {

    @Test
    public void getRegexSeparator() {
        var parser = new TSVRecordsParser<Object>(null, List::of) {

            @Override
            public Optional<String> findMissingHeaderIfAny(List<String> extractedHeaders) {
                return Optional.empty();
            }

            @Override
            public Object transformMapToObject(Map<String, String> recordAsMap) {
                return null;
            }
        };

        Assert.assertEquals("\t", parser.getRegexSeparator());
    }
}
