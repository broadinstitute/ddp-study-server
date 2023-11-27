package org.broadinstitute.ddp.monitoring;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BinaryOperator;

import com.google.monitoring.v3.Point;
import com.google.monitoring.v3.ProjectName;

/**
 * A data series, including the project, metric definition,
 * and all labels for the series.
 */
public class MetricsSeries {

    private final ProjectName projectName;

    private final String metricDescriptorType;

    private final Map<String, String> seriesLabels = new LinkedHashMap<>();

    private final String hashedLabels;

    private final BinaryOperator<Point> pointsReducer;

    public MetricsSeries(ProjectName projectName, String metricDescriptorType, Map<String, String> seriesLabels,
                         BinaryOperator<Point> pointsReducer) {
        this.projectName = projectName;
        this.metricDescriptorType = metricDescriptorType;
        this.seriesLabels.putAll(seriesLabels);

        StringBuilder hashedLabels = new StringBuilder();
        for (Map.Entry<String, String> seriesLabel : new LinkedHashMap<>(seriesLabels).entrySet()) {
            hashedLabels.append(seriesLabel.getKey()).append('.').append(seriesLabel.getValue());
        }
        this.hashedLabels = hashedLabels.toString();
        this.pointsReducer = pointsReducer;
    }

    public BinaryOperator<Point> getPointsReducer() {
        return pointsReducer;
    }



    public Map<String, String> getSeriesLabels() {
        return seriesLabels;
    }

    public String getHashedLabels() {
        return hashedLabels;
    }

    public String getMetricDescriptorType() {
        return metricDescriptorType;
    }

    public ProjectName getProjectName() {
        return projectName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MetricsSeries that = (MetricsSeries) o;

        return projectName.equals(that.projectName) && metricDescriptorType.equals(that.metricDescriptorType)
                && hashedLabels.equals(that.hashedLabels);
    }

    @Override
    public int hashCode() {
        return Objects.hash(projectName, metricDescriptorType, hashedLabels);
    }
}
