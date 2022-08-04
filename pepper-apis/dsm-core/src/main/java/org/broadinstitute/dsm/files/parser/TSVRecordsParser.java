package org.broadinstitute.dsm.files.parser;

import org.broadinstitute.dsm.util.SystemUtil;

public abstract class TSVRecordsParser<T> extends AbstractRecordsParser<T> {
    public TSVRecordsParser(String fileContent) {
        super(fileContent, SystemUtil.TAB_SEPARATOR);
    }
}
