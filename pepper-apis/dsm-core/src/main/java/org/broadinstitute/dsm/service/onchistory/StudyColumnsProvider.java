package org.broadinstitute.dsm.service.onchistory;

import java.util.Map;

public interface StudyColumnsProvider {
    // TODO this pattern was convenient for testing and early deployment, but can be replaced
    //  since eventually there will be only one provider - DC

    /**
     * For a given study, return a map of expected column names in content to OncHistoryUploadColumns,
     * which provide info on how to process each column
     */
    Map<String, OncHistoryUploadColumn> getColumnsForStudy(String realm);
}
