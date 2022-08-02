package org.broadinstitute.dsm.model.elastic.export.tabular;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.broadinstitute.dsm.model.elastic.export.tabular.renderer.ValueProviderFactory;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import spark.Response;

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

    public abstract void export(Response response) throws IOException;

    protected abstract String sanitizeValue(String value);

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

    /** returns a filename such that alphabetical sorting will also put them in chronological order */
    protected String getExportFilename(String suffix) {
        LocalDate date = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(FILE_DATE_FORMAT);
        String exportFileName = String.format("Participant-%s.%s", date.format(formatter), suffix);
        return exportFileName;
    }

    /**
     * Gets all the column names that need to be rendered for a given config, including repeats, details, and/or
     *  splitting options across multiple column.  The column name is based off the stable name of the associated item
     *  and needs to be globally unique
     */

    protected static List<String> getConfigColumnNames(FilterExportConfig filterConfig, int formRepeatNum, int questionRepeatNum) {
        List<String> allColNames = new ArrayList<>();
        if (filterConfig.isSplitOptionsIntoColumns()) {
            List<String> optionStableIds = filterConfig.getOptions().stream().map(opt -> (String) opt.get(ESObjectConstants.OPTION_STABLE_ID))
                    .collect(Collectors.toList());
            allColNames = optionStableIds.stream().map(optionStableId ->
                    getColumnNamesForItem(filterConfig, formRepeatNum, questionRepeatNum, optionStableId)
            ).flatMap(Collection::stream).collect(Collectors.toList());
        } else {
            allColNames = getColumnNamesForItem(filterConfig, formRepeatNum, questionRepeatNum, null);
        }
        return allColNames;
    }

    /** handles iterating over child questions and question details */
    protected static List<String> getColumnNamesForItem(FilterExportConfig filterConfig, int formRepeatNum, int questionRepeatNum, String optionStableId) {
        List<String> colNames = new ArrayList<>();
        if (filterConfig.getChildConfigs() != null) {
            colNames.addAll(filterConfig.getChildConfigs().stream().map(childConfig -> getColumnName(filterConfig, formRepeatNum,
                    questionRepeatNum, optionStableId,null, childConfig)).collect(Collectors.toList()));
        } else {
            colNames.add(getColumnName(filterConfig, formRepeatNum, questionRepeatNum, optionStableId,null, null));
            if (filterConfig.isHasDetails()) {
                colNames.add(getColumnName(filterConfig, formRepeatNum, questionRepeatNum, optionStableId,"DETAIL", null));
            }
        }
        return colNames;
    }


    /**
     * Gets all the column texts to be rendered.  This is a human-readable counterpart to the column name.  e.g.
     *  while the column name might be "MEDICIAL_HISTORY_2.ANALYSIS_TYPE" the text might be "Type of sample analysis"
     */
    protected List<String> getConfigColumnTexts(FilterExportConfig filterConfig) {
        List<String> allColTexts = new ArrayList<>();
        if (filterConfig.isSplitOptionsIntoColumns()) {
            List<Map<String, Object>> splitOptions = filterConfig.getOptions();
            allColTexts = splitOptions.stream().map(opt ->
                    getColumnTextForItem(filterConfig, opt)
            ).flatMap(Collection::stream).collect(Collectors.toList());
        } else {
            allColTexts = getColumnTextForItem(filterConfig, null);
        }

        return allColTexts;
    }

    /** handles iterating over child questions and question details */
    protected static List<String> getColumnTextForItem(FilterExportConfig filterConfig, Map<String, Object> opt) {
        List<String> colTexts = new ArrayList<>();
        if (filterConfig.getChildConfigs() != null) {
            colTexts.addAll(filterConfig.getChildConfigs().stream().map(childConfig -> childConfig.getColumn().getDisplay())
                    .collect(Collectors.toList()));
        } else {
            if (opt == null) {
                colTexts.add(filterConfig.getColumn().getDisplay());
            } else {
                colTexts.add((String) opt.get(ESObjectConstants.OPTION_TEXT));
            }
            if (filterConfig.isHasDetails()) {
                colTexts.add("additional detail");
            }
        }
        return colTexts;
    }



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
     * gets what should be a unique name for the column to use in the generated file.  It is very important that the generated
     * name be unique across various filters/data entries, since the column names are also used to store a map of the participant data,
     * so duplicated names will lead to export data being written to the wrong column.
     *
     * The general format is [OBJECT]_[REPEATNUM].[QUESTION/PROPERTY]_[REPEAT_NUM].[OPTION/DETAIL]
     */
    public static String getColumnName(FilterExportConfig filterConfig,
                                       int activityRepeatNum,
                                       int questionRepeatNum,
                                       String optionStableId,
                                       String detailName,
                                       FilterExportConfig childConfig) {

        String moduleExportPrefix = getModuleColumnPrefix(filterConfig, activityRepeatNum);

        String questionStableId = filterConfig.getColumn().getName();
        String columnExportName = questionRepeatNum > 1 ?
                questionStableId + COLUMN_REPEAT_DELIMITER + questionRepeatNum : questionStableId;

        if (childConfig != null) {
            columnExportName = columnExportName + DBConstants.ALIAS_DELIMITER + childConfig.getColumn().getName();
        }
        if (hasSeparateColumnForOption(optionStableId, filterConfig)) {
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

        String activityExportName = activityRepeatNum > 1 ?
                activityName + COLUMN_REPEAT_DELIMITER + activityRepeatNum : activityName;
        if (filterConfig.getColumn().getObject() != null) {
            activityName = activityName + DBConstants.ALIAS_DELIMITER + filterConfig.getColumn().getObject();
        }
        return activityExportName;
    }

    /** whether a separate column will be rendered for the option */
    private static boolean hasSeparateColumnForOption(String optionStableId, FilterExportConfig filterConfig) {
        return optionStableId != null && filterConfig.isSplitOptionsIntoColumns();
    }

    /** gets the header row -- this iterates through all the configs and calls "getAllColumnNames" for each */
    protected List<String> getHeaderRow() {
        List<String> headers = new ArrayList<>();
        for (ModuleExportConfig moduleConfig : moduleConfigs) {
            for (int formRepeatNum = 1; formRepeatNum <= moduleConfig.getNumMaxRepeats(); formRepeatNum++) {
                for (FilterExportConfig filterConfig : moduleConfig.getQuestions()) {
                    for (int questionRepeatNum = 1; questionRepeatNum <= filterConfig.getMaxRepeats(); questionRepeatNum++) {
                        headers.addAll(getConfigColumnNames(filterConfig, formRepeatNum, questionRepeatNum));
                    }
                }
            }
        }
        return headers;
    }

    /** gets the subheader row -- this iterates through all the configs and calls "getAllColumnNames" for each */
    protected List<String> getSubHeaderRow() {
        List<String> headers = new ArrayList<>();
        for (ModuleExportConfig moduleConfigs : moduleConfigs) {
            for (int formRepeatNum = 1; formRepeatNum <= moduleConfigs.getNumMaxRepeats(); formRepeatNum++) {
                for (FilterExportConfig filterConfig : moduleConfigs.getQuestions()) {
                    for (int questionRepeatNum = 1; questionRepeatNum <= filterConfig.getMaxRepeats(); questionRepeatNum++) {
                        headers.addAll(getConfigColumnTexts(filterConfig));
                    }
                }
            }
        }
        return headers;
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
