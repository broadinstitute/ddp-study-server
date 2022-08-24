
package org.broadinstitute.dsm.model.elastic.filter;

public class NonDsmAndOrFilterSeparator extends AndOrFilterSeparator {

    private static final String REGEX = "([A-z]|\\(|\\s)+(\\.|\\s)*(\\.)*";

    @Override
    protected String getRegex() {
        return REGEX;
    }

    public NonDsmAndOrFilterSeparator(String filter) {
        super(filter);
    }
}