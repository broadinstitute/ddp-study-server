package org.broadinstitute.dsm.model.elastic.export.tabular;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.broadinstitute.dsm.model.elastic.export.tabular.renderer.ValueProviderFactory;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import spark.Response;

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

    protected TabularParticipantExporter(List<ModuleExportConfig> moduleConfigs,
                                         List<Map<String, String>> participantValueMaps, String fileFormat) {
        this.moduleConfigs = moduleConfigs;
        this.participantValueMaps = participantValueMaps;
        this.fileFormat = fileFormat;
    }

    public static TabularParticipantExporter getExporter(List<ModuleExportConfig> moduleConfigs,
                                                         List<Map<String, String>> participantValueMaps, String fileFormat) {
        if (XLSX_FORMAT.equals(fileFormat)) {
            return new ExcelParticipantExporter(moduleConfigs, participantValueMaps, fileFormat);
        }
        // default to tsv
        return new TsvParticipantExporter(moduleConfigs, participantValueMaps, fileFormat);
    }

    protected static String getExportFilename(String suffix) {
        LocalDate date = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(FILE_DATE_FORMAT);
        String exportFileName = String.format("Participant-%s.%s", date.format(formatter), suffix);
        return exportFileName;
    }

    public static List<String> getAllColumnNames(FilterExportConfig filterConfig, int formRepeatNum, int questionRepeatNum) {
        List<String> allColNames = new ArrayList<>();
        List<Map<String, Object>> splitOptions = Collections.singletonList(null);
        if (filterConfig.isSplitOptionsIntoColumns()) {
            splitOptions = filterConfig.getOptions();
        }
        allColNames = splitOptions.stream().map(opt -> {
                    List<String> colNames = new ArrayList<>();
                    colNames.add(getColumnName(
                            filterConfig,
                            formRepeatNum,
                            questionRepeatNum,
                            opt,
                            null));
                    if (filterConfig.isHasDetails()) {
                        colNames.add(getColumnName(
                                filterConfig,
                                formRepeatNum,
                                questionRepeatNum,
                                opt,
                                "DETAIL"));
                    }
                    return colNames;
                }).flatMap(Collection::stream).collect(Collectors.toList());


        return allColNames;
    }


    public List<String> getAllColumnTexts(FilterExportConfig filterConfig) {
        List<String> allColTexts = new ArrayList<>();
        List<Map<String, Object>> splitOptions = Collections.singletonList(null);
        if (filterConfig.isSplitOptionsIntoColumns()) {
            splitOptions = filterConfig.getOptions();
        }
        allColTexts = splitOptions.stream().map(opt -> {
            List<String> colTexts = new ArrayList<>();
            if (opt == null) {
                colTexts.add(filterConfig.getColumn().getDisplay());
            } else {
                colTexts.add((String) opt.get(ESObjectConstants.OPTION_TEXT));
            }
            if (filterConfig.isHasDetails()) {
                colTexts.add("additional detail");
            }
            return colTexts;
        }).flatMap(Collection::stream).collect(Collectors.toList());
        return allColTexts;
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
     */
    public static String getColumnName(FilterExportConfig filterConfig,
                                       int activityRepeatNum,
                                       int questionRepeatNum,
                                       Map<String, Object> option,
                                       String detailName) {
        String activityName = filterConfig.getParent().getName();
        if (TABLE_ALIAS_NAME_MAP.containsKey(filterConfig.getColumn().getTableAlias())) {
            activityName = activityName + DBConstants.ALIAS_DELIMITER + TABLE_ALIAS_NAME_MAP.get(filterConfig.getColumn().getTableAlias());
        }
        String questionStableId = filterConfig.getColumn().getName();
        String activityExportName = activityRepeatNum > 1 ?
                activityName + COLUMN_REPEAT_DELIMITER + activityRepeatNum : activityName;
        if (filterConfig.getColumn().getObject() != null) {
            activityName = activityName + DBConstants.ALIAS_DELIMITER + filterConfig.getColumn().getObject();
        }
        String columnExportName = questionRepeatNum > 1 ?
                questionStableId + COLUMN_REPEAT_DELIMITER + questionRepeatNum : questionStableId;
        if (hasSeparateColumnForOption(option, filterConfig)) {
            columnExportName = columnExportName + DBConstants.ALIAS_DELIMITER + option.get(ESObjectConstants.OPTION_STABLE_ID);
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

        String exportName = activityExportName + DBConstants.ALIAS_DELIMITER + columnExportName;
        return exportName.toUpperCase();
    }

    private static boolean hasSeparateColumnForOption(Map<String, Object> option, FilterExportConfig filterConfig) {
        return option != null && filterConfig.isSplitOptionsIntoColumns();
    }

    protected List<String> getHeaderRow() {
        List<String> headers = new ArrayList<>();
        for (ModuleExportConfig moduleConfig : moduleConfigs) {
            for (int formRepeatNum = 1; formRepeatNum <= moduleConfig.getNumMaxRepeats(); formRepeatNum++) {
                for (FilterExportConfig filterConfig : moduleConfig.getQuestions()) {
                    headers.addAll(getAllColumnNames(filterConfig, formRepeatNum, 1));
                }
            }
        }
        return headers;
    }

    protected List<String> getSubHeaderRow() {
        List<String> headers = new ArrayList<>();
        for (ModuleExportConfig moduleConfigs : moduleConfigs) {
            for (int formRepeatNum = 1; formRepeatNum <= moduleConfigs.getNumMaxRepeats(); formRepeatNum++) {
                for (FilterExportConfig filterConfig : moduleConfigs.getQuestions()) {
                    headers.addAll(getAllColumnTexts(filterConfig));
                }
            }
        }
        return headers;
    }

    protected List<String> getRowValues(Map<String, String> valueMap, List<String> headers) {
        List<String> rowValues = new ArrayList(headers.size());
        for (String header : headers) {
            String value = valueMap.get(header);
            rowValues.add(sanitizeValue(value));
        }
        return rowValues;
    }

}
