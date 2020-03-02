package org.broadinstitute.ddp.monitoring;

import com.google.api.MetricDescriptor;

public enum StackdriverCustomMetric {

    /**
     * Use this if you need to experiment with metrics
     */
    TESTING("garbage_testing",
            "Junk for testing",
            MetricDescriptor.MetricKind.GAUGE,
            MetricDescriptor.ValueType.INT64),
    KITS_REQUESTED("kits_requested",
            "Number of kits requested",
            MetricDescriptor.MetricKind.GAUGE,
            MetricDescriptor.ValueType.INT64),
    EMAILS_SENT("emails_sent",
            "Number of emails sent",
            MetricDescriptor.MetricKind.GAUGE,
            MetricDescriptor.ValueType.INT64),
    HOUSEKEEPING_CYCLES("housekeeping_runs",
            "Number of times through main housekeeping event loop",
            MetricDescriptor.MetricKind.GAUGE,
            MetricDescriptor.ValueType.INT64),
    API_ACTIVITY("api_activity",
            "General API activity",
            MetricDescriptor.MetricKind.GAUGE,
            MetricDescriptor.ValueType.INT64),
    DATA_EXPORTS("exports",
            "Number of data exports from housekeeping",
            MetricDescriptor.MetricKind.GAUGE,
            MetricDescriptor.ValueType.INT64),
    DB_BACKUP("db_backup",
            "Number of Database backups",
            MetricDescriptor.MetricKind.GAUGE,
            MetricDescriptor.ValueType.INT64
    );

    private final String metricName;

    private final String metricDescription;

    private final MetricDescriptor.MetricKind metricKind;

    private final MetricDescriptor.ValueType valueType;

    private StackdriverCustomMetric(String metricName,
                                    String metricDescription,
                                    MetricDescriptor.MetricKind metricKind,
                                    MetricDescriptor.ValueType valueType) {
        this.metricName = metricName;
        this.metricDescription = metricDescription;
        this.metricKind = metricKind;
        this.valueType = valueType;
    }

    public String getMetricName() {
        return metricName;
    }

    public String getMetricDescription() {
        return metricDescription;
    }

    public MetricDescriptor.MetricKind getMetricKind() {
        return metricKind;
    }

    public MetricDescriptor.ValueType getValueType() {
        return valueType;
    }
}
