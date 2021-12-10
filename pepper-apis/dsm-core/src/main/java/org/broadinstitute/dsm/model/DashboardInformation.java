package org.broadinstitute.dsm.model;

import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class DashboardInformation {

    private static final Logger logger = LoggerFactory.getLogger(DashboardInformation.class);

    public static final String TISSUE_REVIEW = "review";
    public static final String TISSUE_NO = "no";
    public static final String TISSUE_HOLD = "hold";
    public static final String TISSUE_REQUEST = "request";
    public static final String TISSUE_RETURN = "returned";


    private String ddpName;

    private List<KitCounter> sentCounters;
    private List<KitCounter> receivedCounters;
    private List<NameValue> deactivatedCounters;
    private List<NameValue> unsentExpressKits;
    private List<KitDDPSummary> kits;
    private Map<String, Integer> dashboardValues;
    private Map<String, Integer> dashboardValuesDetailed;
    private Map<String, Integer> dashboardValuesPeriod;
    private Map<String, Integer> dashboardValuesPeriodDetailed;

    public DashboardInformation(List<NameValue> unsentExpressKits, List<KitDDPSummary> kits) {
        this.unsentExpressKits = unsentExpressKits;
        this.kits = kits;
    }

    public DashboardInformation(Map<String, Integer> dashboardValues, Map<String, Integer> dashboardValuesDetailed,
                                Map<String, Integer> dashboardValuesPeriod, Map<String, Integer> dashboardValuesPeriodDetailed) {
        this.dashboardValues = dashboardValues;
        this.dashboardValuesDetailed = dashboardValuesDetailed;
        this.dashboardValuesPeriod = dashboardValuesPeriod;
        this.dashboardValuesPeriodDetailed = dashboardValuesPeriodDetailed;
    }

    public DashboardInformation(String ddpName, List<KitCounter> sentCounters,
                                List<KitCounter> receivedCounters,
                                List<NameValue> deactivatedCounters) {
        this.ddpName = ddpName;
        this.sentCounters = sentCounters;
        this.receivedCounters = receivedCounters;
        this.deactivatedCounters = deactivatedCounters;
    }

    public static class KitCounter {
        private String kitType;
        private ArrayList<NameValue> counters;

        public KitCounter(String kitType, ArrayList<NameValue> counters) {
            this.kitType = kitType;
            this.counters = counters;
        }
    }
}
