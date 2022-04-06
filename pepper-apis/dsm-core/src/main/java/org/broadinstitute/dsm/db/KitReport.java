package org.broadinstitute.dsm.db;

import java.util.List;

public class KitReport {

    public List<SummaryKitType> summaryKitTypeList;
    private String ddpName;

    public KitReport(String ddpName, List<SummaryKitType> summaryKitTypes) {
        this.ddpName = ddpName;
        this.summaryKitTypeList = summaryKitTypes;
    }
}
