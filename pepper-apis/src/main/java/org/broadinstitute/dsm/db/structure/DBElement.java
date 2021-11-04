package org.broadinstitute.dsm.db.structure;

import lombok.Data;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Data
public class DBElement {

    public String tableName;
    public String tableAlias;
    public String primaryKey;
    public String columnName;
    public SqlDateConverter dateConverter;

    public DBElement(String tableName, String tableAlias, String primaryKey, String columnName) {
        this.tableName = tableName;
        this.tableAlias = tableAlias;
        this.primaryKey = primaryKey;
        this.columnName = columnName;
    }

    public DBElement(String tableName, String tableAlias, String primaryKey, String columnName, DbDateConversion dateConverter) {
        this(tableName, tableAlias, primaryKey, columnName);
        if (dateConverter != null) {
            this.dateConverter = dateConverter.value();
        }
    }

    /**
     * Use this for dates that are stored as millis since the epoch.
     */
    public static class EpochDateConverter implements DateConverter {

        public String convertArgToSql(Instant arg) {
            return Long.toString(arg.toEpochMilli());
        }

        public String convertColumnForSql(String column) {
            return column;
        }

        public String convertArgToSqlForDay(Instant arg) {
            return "DATE(FROM_UNIXTIME(" + arg.toEpochMilli()/1000 + "))";
        }

        public String convertColumnForSqlForDay(String column) {
            return "DATE(FROM_UNIXTIME(" + column + "/1000))";
        }
    }

    /**
     * Use this for simple yyyy-mm-dd string columns
     */
    public static class StringDayConverter implements DateConverter {

        public String convertArgToSql(Instant arg) {
            return "STR_TO_DATE(" + "'" + DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneId.of("UTC")).format(arg) + "'" + ",'%Y-%m-%d')";
        }

        public String convertColumnForSql(String column) {
            return "STR_TO_DATE(" + column + ",'%Y-%m-%d')";
        }

        public String convertArgToSqlForDay(Instant arg) {
            return convertArgToSql(arg);
        }

        public String convertColumnForSqlForDay(String column) {
            return convertColumnForSql(column);
        }
    }

    /**
     * Defines how to translate different storage formats
     * of dates for proper comparison.  Returned values
     * should be valid SQL.
     */
    public interface DateConverter {

        /**
         * Convert a search term into SQL for exact match
         */
        String convertArgToSql(Instant arg);

        /**
         * Convert the column into SQL for exact match
         */
        String convertColumnForSql(String column);

        /**
         * Convert the arg into SQL for a "in this day" match
         */
        String convertArgToSqlForDay(Instant arg);

        /**
         * Convert the column into SQl for a "in this day" match
         */
        String convertColumnForSqlForDay(String column);
    }
}
