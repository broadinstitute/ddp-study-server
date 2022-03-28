package org.broadinstitute.dsm.model.elastic.filter.splitter;

public class DateGreaterSplitterStrategy extends GreaterThanEqualsSplitterStrategy {

    @Override
    public String[] getValue() {
        return DateSplitterHelper.splitter(splittedFilter[1]);
    }
}
