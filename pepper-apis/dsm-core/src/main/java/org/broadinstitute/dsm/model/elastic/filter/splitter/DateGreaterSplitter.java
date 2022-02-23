package org.broadinstitute.dsm.model.elastic.filter.splitter;

public class DateGreaterSplitter extends GreaterThanEqualsSplitter {

    @Override
    public String[] getValue() {
        return DateSplitterHelper.splitter(splittedFilter[1]);
    }
}
