package org.broadinstitute.dsm.model.elastic.export.tabular;

import org.broadinstitute.dsm.model.elastic.export.tabular.renderer.ValueProviderFactory;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.ESObjectConstants;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** base class for taking a String=>String map of participant data and writing out a tabular file
 * It is designed to work with the outputs from TabularParticipantParser
 */
public abstract class TabularParticipantExporter {
    protected static final String FILE_DATE_FORMAT = "yyyy-MM-dd";
    public static final String TSV_FORMAT = "tsv";
    public static final String XLSX_FORMAT = "xlsx";
    public static final String COLUMN_REPEAT_DELIMITER = "_";
    public static final String OPTION_DETAIL_DELIMITER = "_";
    protected List<ModuleExportConfig> moduleConfigs;
    protected String fileFormat;
    protected List<Map<String, String>> participantValueMaps;

    /** some tables are stored off the same data object as another (e.g. proxies are stored off of "profile")
     * this map lets us add distinguishing names to columns from those objects.  e.g. profile.email will not
     * collide with profile.PROXY.email
     */
    private static final Map<String, String> TABLE_ALIAS_NAME_MAP = Map.of(
            "proxy", "PROXY",
            "r", "RECORD",
            "ex", "EXIT"
    );

    /**
     * writes the data to the given stream. This does not close the stream, so that multi-part streams (e.g. zip files)
     * can be supported
     */
    public abstract void export(OutputStream os) throws IOException;

    protected abstract String sanitizeValue(String value);

    public abstract String getExportFilename();

    /** returns a filename such that alphabetical sorting will also put them in chronological order */
    protected String getExportFilename(String suffix) {
        LocalDate date = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(FILE_DATE_FORMAT);
        String exportFileName = String.format("Participant-%s.%s", date.format(formatter), suffix);
        return exportFileName;
    }

    /** protected constructor -- outside callers should use `getExporter` */
    protected TabularParticipantExporter(List<ModuleExportConfig> moduleConfigs,
                                         List<Map<String, String>> participantValueMaps, String fileFormat) {
        this.moduleConfigs = moduleConfigs;
        this.participantValueMaps = participantValueMaps;
        this.fileFormat = fileFormat;
    }

    /** factory-style method to get an exporter appropriate for the given file format */
    public static TabularParticipantExporter getExporter(List<ModuleExportConfig> moduleConfigs,
                                                         List<Map<String, String>> participantValueMaps, String fileFormat) {
        if (XLSX_FORMAT.equals(fileFormat)) {
            return new ExcelParticipantExporter(moduleConfigs, participantValueMaps, fileFormat);
        }
        // default to tsv
        return new TsvParticipantExporter(moduleConfigs, participantValueMaps, fileFormat);
    }


    protected static String getColumnDisplayText(FilterExportConfig filterConfig, Map<String, Object> opt, String detailName) {
        if (detailName != null) {
            return (String) opt.getOrDefault(ESObjectConstants.OPTION_DETAILS_TEXT, "additional details");
        } else if (opt != null) {
            return (String) opt.get(ESObjectConstants.OPTION_TEXT);
        }
        return filterConfig.getColumn().getDisplay();
    }


    /**
     * gets what should be a unique name for the column to use in the generated file.  It is very important that the generated
     * name be unique across various filters/data entries, since the column names are also used to store a map of the participant data,
     * so duplicated names will lead to export data being written to the wrong column.
     * The general format is [OBJECT]_[REPEATNUM].[QUESTION/PROPERTY]_[REPEAT_NUM].[OPTION/DETAIL]
     */
    public static String getColumnName(FilterExportConfig filterConfig,
                                       int activityRepeatNum,
                                       int questionRepeatNum,
                                       Map<String, Object> option,
                                       String detailName,
                                       FilterExportConfig parentConfig) {

        String moduleExportPrefix = getModuleColumnPrefix(filterConfig, activityRepeatNum);

        String questionStableId = filterConfig.getColumn().getName();
        String columnExportName = questionRepeatNum > 1 ? questionStableId + COLUMN_REPEAT_DELIMITER + questionRepeatNum : questionStableId;

        if (parentConfig != null) {
            columnExportName = parentConfig.getColumn().getName() + DBConstants.ALIAS_DELIMITER + columnExportName;
        }
        if (hasSeparateColumnForOption(option, filterConfig)) {
            String optionStableId = (String) option.get(ESObjectConstants.OPTION_STABLE_ID);
            columnExportName = columnExportName + DBConstants.ALIAS_DELIMITER + optionStableId;
        }

        if (detailName != null) {
            columnExportName = columnExportName + OPTION_DETAIL_DELIMITER + detailName;
        }

        // if this is a collated column, use the suffix (e.g. use "REGISTRATION_STATE_PROVINCE" instead of "CA_REGISTRATION_STATE_PROVINCE")
        for (String suffix : ValueProviderFactory.COLLATED_SUFFIXES) {
            if (columnExportName.endsWith(suffix)) {
                columnExportName = suffix;
            }
        }

        String exportName = moduleExportPrefix + DBConstants.ALIAS_DELIMITER + columnExportName;
        return exportName.toUpperCase();
    }


    protected static String getModuleColumnPrefix(FilterExportConfig filterConfig, int activityRepeatNum) {
        String activityName = filterConfig.getParent().getName();
        if (TABLE_ALIAS_NAME_MAP.containsKey(filterConfig.getColumn().getTableAlias())) {
            activityName = activityName + DBConstants.ALIAS_DELIMITER + TABLE_ALIAS_NAME_MAP.get(filterConfig.getColumn().getTableAlias());
        }

        String activityExportName = activityRepeatNum > 1 ? activityName + COLUMN_REPEAT_DELIMITER + activityRepeatNum : activityName;
        if (filterConfig.getColumn().getObject() != null) {
            activityName = activityName + DBConstants.ALIAS_DELIMITER + filterConfig.getColumn().getObject();
        }
        return activityExportName;
    }

    /** whether a separate column will be rendered for the option */
    private static boolean hasSeparateColumnForOption(Map<String, Object> option, FilterExportConfig filterConfig) {
        return option != null && filterConfig.isSplitOptionsIntoColumns();
    }

    /** gets the header row -- this iterates through all the configs and calls "getAllColumnNames" for each */
    protected List<String> getHeaderRow() {
        List<String> headers = new ArrayList<>();
        applyToEveryColumn((filterConfig, activityRepeatNum, questionRepeatNum, option, detailName, parentConfig) -> {
            headers.add(getColumnName(filterConfig, activityRepeatNum, questionRepeatNum, option, detailName, parentConfig));
        });
        return headers;
    }

    /** gets the subheader row -- this iterates through all the configs and calls "getAllColumnNames" for each */
    protected List<String> getSubHeaderRow() {
        List<String> headers = new ArrayList<>();
        applyToEveryColumn((filterConfig, activityRepeatNum, questionRepeatNum, option, detailName, parentConfig) -> {
            headers.add(getColumnDisplayText(filterConfig, option, detailName));
        });
        return headers;
    }

    /** class for operating iteratively over columns (variables) of an export */
    public interface ColumnProcessor {
        public void apply(FilterExportConfig filterConfig,
                          int activityRepeatNum,
                          int questionRepeatNum,
                          Map<String, Object> option,
                          String detailName,
                          FilterExportConfig parentConfig);
    }

    /**
     * useful for iterating over every column that should be generated.  this will create an iteration for every module, every repeat,
     * every question, every question option (if the question is a multiselect that should be split), and every question response instance
     * This also will give a separate iteration for questions that have associated details.
     */
    public void applyToEveryColumn(ColumnProcessor colFunc) {
        for (ModuleExportConfig moduleConfigs : moduleConfigs) {
            for (int activityRepeatNum = 1; activityRepeatNum <= moduleConfigs.getNumMaxRepeats(); activityRepeatNum++) {
                for (FilterExportConfig filterConfig : moduleConfigs.getQuestions()) {
                    for (int questionRepeatNum = 1; questionRepeatNum <= filterConfig.getMaxRepeats(); questionRepeatNum++) {
                        if (filterConfig.getChildConfigs() != null) {
                            for (FilterExportConfig childConfig : filterConfig.getChildConfigs()) {
                                applyToQuestionColumns(colFunc, childConfig, activityRepeatNum, questionRepeatNum, filterConfig);
                            }
                        } else {
                            applyToQuestionColumns(colFunc, filterConfig, activityRepeatNum, questionRepeatNum, null);
                        }
                    }
                }
            }
        }
    }

    /** helper method for iterating over every column to be generated from a single question instance */
    private void applyToQuestionColumns(ColumnProcessor colFunc, FilterExportConfig filterConfig,
                                        int activityRepeatNum,
                                        int questionRepeatNum,
                                        FilterExportConfig parentConfig) {
        if (filterConfig.isSplitOptionsIntoColumns()) {
            for (Map<String, Object> option : filterConfig.getOptions()) {
                colFunc.apply(filterConfig, activityRepeatNum, questionRepeatNum, option, null, parentConfig);
                if (filterConfig.hasDetailsForOption((String) option.get(ESObjectConstants.OPTION_STABLE_ID))) {
                    colFunc.apply(filterConfig, activityRepeatNum, questionRepeatNum, option, "DETAIL", parentConfig);
                }
            }
        } else {
            colFunc.apply(filterConfig, activityRepeatNum, questionRepeatNum, null, null, parentConfig);
            if (filterConfig.hasAnyOptionDetails()) {
                colFunc.apply(filterConfig, activityRepeatNum, questionRepeatNum, Collections.EMPTY_MAP, "DETAIL", parentConfig);
            }
        }
    }


    /**
     * Gets the values to render for a row (usually a participant).  This handles any sanitization of string values
     * (e.g. if commas/newlines/tabs need to be escaped)
     * @param valueMap map of columnName => value
     * @param columnNames the odered list of columns
     * @return the ordered list of values
     */
    protected List<String> getRowValues(Map<String, String> valueMap, List<String> columnNames) {
        List<String> rowValues = new ArrayList(columnNames.size());
        for (String header : columnNames) {
            String value = valueMap.get(header);
            rowValues.add(sanitizeValue(value));
        }
        return rowValues;
    }

}
