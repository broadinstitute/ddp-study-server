package org.broadinstitute.dsm.db;

import java.util.List;

public class KitReport {

    private String ddpName;
    public List<SummaryKitType> summaryKitTypeList;

    public KitReport(String ddpName, List<SummaryKitType> summaryKitTypes) {
        this.ddpName = ddpName;
        this.summaryKitTypeList = summaryKitTypes;
    }
}
