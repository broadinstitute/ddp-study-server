package org.broadinstitute.dsm.model.elastic.filter;

public class NonDsmAndOrFilterSeparator extends AndOrFilterSeparator {

    protected static final String DSM_ALIAS_REGEX = "[A-z]+(\\.|\\s)*(\\.)*";

    public NonDsmAndOrFilterSeparator(String filter) {
        super(filter);
    }
}
