package org.broadinstitute.dsm.model.elastic.export.tabular;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import org.broadinstitute.dsm.statics.DBConstants;
import spark.Response;

public abstract class TabularParticipantExporter {
    protected List<ModuleExportConfig> moduleConfigs;
    protected String fileFormat;
    protected List<Map<String, String>> participantValueMaps;

    protected TabularParticipantExporter(List<ModuleExportConfig> moduleConfigs,
                                         List<Map<String, String>> participantValueMaps, String fileFormat) {
        this.moduleConfigs = moduleConfigs;
        this.participantValueMaps = participantValueMaps;
        this.fileFormat = fileFormat;
    }

    public static TabularParticipantExporter getExporter(List<ModuleExportConfig> moduleConfigs,
                                                         List<Map<String, String>> participantValueMaps, String fileFormat) {
        if ("tsv".equals(fileFormat)) {
            return new TsvParticipantExporter(moduleConfigs, participantValueMaps, fileFormat);
        } else if ("xlsx".equals(fileFormat)) {
            return new ExcelParticipantExporter(moduleConfigs, participantValueMaps, fileFormat);
        }
        throw new RuntimeException("Unrecognized file format");

    }


    public abstract void export(Response response) throws IOException;

    protected static final String FILE_DATE_FORMAT = "yyyy-MM-dd";
    protected static String getExportFilename(String suffix) {
        LocalDate date = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(FILE_DATE_FORMAT);
        String exportFileName = String.format("Participant-%s.%s", date.format(formatter), suffix);
        return exportFileName;
    }

    protected List<String> getHeaderRow() {
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

    protected List<String> getSubHeaderRow() {
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

    protected List<String> getRowValues(Map<String, String> valueMap, List<String> headers) {
        List<String> rowValues = new ArrayList(headers.size());
        for (String header : headers) {
            String value = valueMap.get(header);
            rowValues.add(sanitizeValue(value));
        }
        return rowValues;
    }

    protected abstract String sanitizeValue(String value);

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
