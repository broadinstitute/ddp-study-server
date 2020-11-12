package org.broadinstitute.ddp.model.statistics;

import java.beans.ConstructorProperties;

public class StatisticsType {
    private final long id;
    private final StatisticsTypes type;

    @ConstructorProperties({"statistics_type_id", "statistics_type_code"})
    public StatisticsType(long id, StatisticsTypes type) {
        this.id = id;
        this.type = type;
    }

    public long getId() {
        return id;
    }

    public StatisticsTypes getType() {
        return type;
    }
}
