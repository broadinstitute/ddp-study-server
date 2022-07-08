package org.broadinstitute.dsm.model.elastic.export.tabular;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import spark.Response;

public abstract class TabularParticipantExporter {
    protected static final String FILE_DATE_FORMAT = "yyyy-MM-dd";
    public static final String TSV_FORMAT = "tsv";
    public static final String XLSX_FORMAT = "xlsx";
    public static final String COLUMN_REPEAT_DELIMITER = "_";
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
        if (filterConfig.isSplitOptionsIntoColumns()) {
            return filterConfig.getOptions().stream().map(opt -> {
                return getColumnName(
                        filterConfig,
                        formRepeatNum,
                        questionRepeatNum,
                        opt);
            }).collect(Collectors.toList());
        }
        return Collections.singletonList(getColumnName(
                filterConfig,
                formRepeatNum,
                questionRepeatNum,
                null
        ));
    }

    public static String getColumnName(FilterExportConfig filterConfig,
                                       int activityRepeatNum,
                                       int questionRepeatNum,
                                       Map<String, Object> option) {
        String activityName = filterConfig.getParent().getName();
        String questionStableId = filterConfig.getColumn().getName();
        String activityExportName = activityRepeatNum > 1 ?
                activityName + COLUMN_REPEAT_DELIMITER + activityRepeatNum : activityName;
        String columnExportName = questionRepeatNum > 1 ?
                questionStableId + COLUMN_REPEAT_DELIMITER + questionRepeatNum : questionStableId;
        if (hasSeparateColumnForOption(option, filterConfig)) {
            columnExportName = columnExportName + DBConstants.ALIAS_DELIMITER + option.get(ESObjectConstants.OPTION_STABLE_ID);
        }
        String exportName = activityExportName + DBConstants.ALIAS_DELIMITER + columnExportName;
        return exportName;
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

    public List<String> getAllColumnTexts(FilterExportConfig filterConfig) {
        if (filterConfig.isSplitOptionsIntoColumns()) {
            return filterConfig.getOptions().stream().map(opt -> (String) opt.get(ESObjectConstants.OPTION_TEXT))
                    .collect(Collectors.toList());
        }
        return Collections.singletonList(filterConfig.getColumn().getDisplay());
    }
}
