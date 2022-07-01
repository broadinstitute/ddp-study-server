package org.broadinstitute.dsm.model.elastic.export.tabular;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.statics.DBConstants;

public class TabularParticipantExporter {
    private static final String DELIMITER = "\t";
    public TabularParticipantExporter(List<ModuleExportConfig> moduleConfigs, List<Map<String, String>> participantValueMaps) {
        this.moduleConfigs = moduleConfigs;
        this.participantValueMaps = participantValueMaps;
    }
    private List<ModuleExportConfig> moduleConfigs;
    private List<Map<String, String>> participantValueMaps;

    public void writeTable(PrintWriter writer) {
        List<String> headerRow = getHeaderRow();
        List<String> subHeaderRow = getSubHeaderRow();

        writer.println(String.join(DELIMITER, headerRow));
        writer.println(String.join(DELIMITER, subHeaderRow));
        for (Map<String, String> valueMap : participantValueMaps) {
            String rowString = getRowString(valueMap, headerRow);
            writer.println(rowString);
        }
        writer.flush();
    }

    private List<String> getHeaderRow() {
        List<String> headers = new ArrayList<>();
        for (ModuleExportConfig moduleConfig : moduleConfigs) {
            for (int formRepeatNum = 1; formRepeatNum <= moduleConfig.getNumMaxRepeats(); formRepeatNum++) {
                for (FilterExportConfig fConfig : moduleConfig.getQuestions()) {
                    headers.addAll(getAllColumnNames(fConfig, formRepeatNum, 1));
                }
            }
        }
        return headers;
    }

    private List<String> getSubHeaderRow() {
        List<String> headers = new ArrayList<>();
        for (ModuleExportConfig moduleConfigs : moduleConfigs) {
            for (int formRepeatNum = 1; formRepeatNum <= moduleConfigs.getNumMaxRepeats(); formRepeatNum++) {
                for (FilterExportConfig fConfig : moduleConfigs.getQuestions()) {
                    headers.addAll(getAllColumnTexts(fConfig));
                }
            }
        }
        return headers;
    }

    private String getRowString(Map<String, String> valueMap, List<String> headers) {
        String[] rowValues = new String[headers.size()];
        for (int colNum = 0; colNum < headers.size(); colNum++) {
            String value = valueMap.get(headers.get(colNum));
            rowValues[colNum] = sanitizeValue(value);
        }
        return String.join(DELIMITER, rowValues);
    }

    public String sanitizeValue(String value) {
        if (value == null) {
            value = StringUtils.EMPTY;
        }
        // first replace double quotes with single '
        String sanitizedValue = value.replace("\"", "'");
        // then quote the whole string if needed
        if (sanitizedValue.indexOf("\n") >= 0 || sanitizedValue.indexOf(DELIMITER) >= 0) {
            sanitizedValue = String.format("\"%s\"", sanitizedValue);
        }
        return sanitizedValue;
    }

    public static List<String> getAllColumnNames(FilterExportConfig fConfig, int formRepeatNum, int questionRepeatNum) {
        if (fConfig.isSplitOptionsIntoColumns()) {
            return fConfig.getOptions().stream().map(opt -> {
                return getColumnName(
                        fConfig,
                        formRepeatNum,
                        questionRepeatNum,
                        opt);
            }).collect(Collectors.toList());
        }
        return Collections.singletonList(getColumnName(
                fConfig,
                formRepeatNum,
                questionRepeatNum,
                null
                ));
    }

    public List<String> getAllColumnTexts(FilterExportConfig fConfig) {
        if (fConfig.isSplitOptionsIntoColumns()) {
            return fConfig.getOptions().stream().map(opt -> (String) opt.get("optionText"))
                    .collect(Collectors.toList());
        }
        return Collections.singletonList(fConfig.getColumn().getDisplay());
    }


    public static String getColumnName(FilterExportConfig fConfig,
                                       int activityRepeatNum,
                                       int questionRepeatNum,
                                       Map<String, Object> option) {
        String activityName = fConfig.getParent().getName();
        String questionStableId = fConfig.getColumn().getName();
        String activityExportName = activityRepeatNum > 1 ? activityName + "_" + activityRepeatNum : activityName;
        String columnExportName = questionRepeatNum > 1 ? questionStableId + "_" + questionRepeatNum : questionStableId;
        if (option != null && fConfig.isSplitOptionsIntoColumns()) {
            columnExportName = columnExportName + "." + (String) option.get("optionStableId");
        }
        String exportName = activityExportName + DBConstants.ALIAS_DELIMITER + columnExportName;
        return exportName;
    }
}
