package org.broadinstitute.dsm.db.structure;

import java.time.Instant;

public enum SqlDateConverter {
    EPOCH(new DBElement.EpochDateConverter()),
    STRING_DAY(new DBElement.StringDayConverter());

    private final DBElement.DateConverter dateConverter;

    SqlDateConverter(DBElement.DateConverter dateConverter) {
        this.dateConverter = dateConverter;
    }

    public String convertArgToSql(Instant arg) {
        return dateConverter.convertArgToSql(arg);
    }

    public String convertColumnForSql(String column) {
        return dateConverter.convertColumnForSql(column);
    }

    public String convertArgToSqlDay(Instant arg) {
        return dateConverter.convertArgToSqlForDay(arg);
    }

    public String convertColumnForSqlDay(String column) {
        return dateConverter.convertColumnForSqlForDay(column);
    }
}
