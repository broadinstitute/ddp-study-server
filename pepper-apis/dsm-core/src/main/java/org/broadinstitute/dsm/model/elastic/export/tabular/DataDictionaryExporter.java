package org.broadinstitute.dsm.model.elastic.export.tabular;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFCell;
import org.apache.poi.xssf.streaming.SXSSFRow;
import org.broadinstitute.dsm.statics.ESObjectConstants;

/**
 * Writes a data dictionary file based on the given configs.
 * columns include variable name, type, description, and options
 */
public class DataDictionaryExporter extends ExcelParticipantExporter {
    private static final int ROW_ACCESS_WINDOW_SIZE = 200;
    private int currentRowNum = -1;
    private static final String SHEET_NAME = "Data dictionary";
    private static final String FILE_NAME = "DataDictionary.xlsx";

    private final CellStyle wrapStyle;
    private final CellStyle boldStyle;
    private final CellStyle boldUnderlineStyle;
    private static final Map<String, String> QUESTION_TYPE_TO_DATA_TYPE =
            Map.of("DATE", "datetime", "DATE_SHORT", "date", "AGREEMENT", "boolean", "BOOLEAN", "boolean", "NUMERIC", "number", "CHECKBOX",
                    "boolean", "NUMBER", "number");

    private static final int VARIABLE_NAME_COL_NUMBER = 0;
    private static final int DATATYPE_COL_NUMBER = 1;
    private static final int QUESTION_TYPE_COL_NUMBER = 2;
    private static final int DESCRIPTION_COL_NUMBER = 3;
    private static final int OPTIONS_COL_NUMBER = 4;


    public String getExportFilename() {
        return FILE_NAME;
    }

    /**
     * initializes the dictionary and internal spreadsheet
     */
    public DataDictionaryExporter(List<ModuleExportConfig> configs) {
        super(configs, null, TabularParticipantExporter.XLSX_FORMAT);
        wrapStyle = workbook.createCellStyle();
        wrapStyle.setWrapText(true);
        boldStyle = workbook.createCellStyle();
        Font boldFont = workbook.createFont();
        boldFont.setBold(true);
        boldStyle.setFont(boldFont);

        boldUnderlineStyle = workbook.createCellStyle();
        Font boldUnderlineFont = workbook.createFont();
        boldUnderlineFont.setBold(true);
        boldUnderlineFont.setUnderline(Font.U_SINGLE);
        boldUnderlineStyle.setFont(boldUnderlineFont);
    }

    /**
     * writes the dictionary
     */
    public void export(OutputStream os) throws IOException {
        sheet.setColumnWidth(VARIABLE_NAME_COL_NUMBER, 40 * 256);
        sheet.setColumnWidth(DATATYPE_COL_NUMBER, 10 * 256);
        sheet.setColumnWidth(QUESTION_TYPE_COL_NUMBER, 12 * 256);
        sheet.setColumnWidth(DESCRIPTION_COL_NUMBER, 60 * 256);
        sheet.setColumnWidth(OPTIONS_COL_NUMBER, 40 * 256);

        applyToEveryColumn(new DictionaryRowWriter());

        writeAndCloseSheet(os);
    }

    protected class DictionaryRowWriter implements ColumnProcessor {
        String previousModuleName = null;
        String previousQuestionId = null;
        String previousParentQuestionId = null;

        public void apply(FilterExportConfig filterConfig, int activityRepeatNum, int questionRepeatNum, Map<String, Object> option,
                          String detailName, FilterExportConfig parentConfig) {
            // we don't make separate entries for each iteration of a variable
            if (!isFirstQuestionIteration(activityRepeatNum, questionRepeatNum)) {
                return;
            }

            String moduleName = getModuleColumnPrefix(filterConfig, 1);
            if (!moduleName.equals(previousModuleName)) {
                addModuleHeaderRows(moduleName, filterConfig.getParent());
            }
            String questionId = null;
            String parentQuestionId = null;
            if (filterConfig.getQuestionDef() != null) {
                if (parentConfig != null) {
                    parentQuestionId = (String) parentConfig.getQuestionDef().get(ESObjectConstants.STABLE_ID);
                }
                questionId = (String) filterConfig.getQuestionDef().get(ESObjectConstants.STABLE_ID);
            }

            if (isFirstQuestionOfComposite(parentQuestionId, previousParentQuestionId)) {
                addCompositeQuestionHeaderRow(filterConfig, activityRepeatNum, questionRepeatNum, option, detailName, parentConfig);
            }
            if (isFirstOptionOfSplitOptions(questionId, previousQuestionId, filterConfig)) {
                addSplitOptionsHeaderRow(filterConfig, activityRepeatNum, questionRepeatNum, option, detailName, parentConfig);
            }

            if (detailName != null) {
                addAdditionalDetailRow(filterConfig, activityRepeatNum, questionRepeatNum, option, detailName, parentConfig);
            } else if (option != null) {
                addSplitOptionsRow(filterConfig, activityRepeatNum, questionRepeatNum, option, null, parentConfig);
            } else {
                addRegularVariableRow(filterConfig, activityRepeatNum, questionRepeatNum, null, null, parentConfig);
            }
            previousModuleName = moduleName;
            previousQuestionId = questionId;
            previousParentQuestionId = parentQuestionId;
        }
    }

    private boolean isFirstQuestionIteration(int moduleRepeatNum, int questionRepeatNum) {
        return moduleRepeatNum == 1 && questionRepeatNum == 1;
    }

    private boolean isFirstQuestionOfComposite(String parentQuestionId, String previousParentQuestionId) {
        return !StringUtils.equals(previousParentQuestionId, parentQuestionId) && parentQuestionId != null;
    }

    private boolean isFirstOptionOfSplitOptions(String questionId, String previousQuestionId, FilterExportConfig filterConfig) {
        return !StringUtils.equals(questionId, previousQuestionId) && filterConfig.isSplitOptionsIntoColumns();
    }

    protected void addModuleHeaderRows(String moduleName, ModuleExportConfig moduleExportConfig) {
        // two blank rows
        addRowToSheet();
        addRowToSheet();

        SXSSFRow moduleNameRow = addRowToSheet();
        moduleNameRow.setRowStyle(boldStyle);
        moduleNameRow.createCell(VARIABLE_NAME_COL_NUMBER).setCellValue(moduleName.toUpperCase());

        if (moduleExportConfig.getNumMaxRepeats() > 1) {
            addModuleRepeatDescription(moduleName, moduleNameRow, moduleExportConfig);
        }
        sheet.addMergedRegion(new CellRangeAddress(currentRowNum, currentRowNum, DATATYPE_COL_NUMBER, OPTIONS_COL_NUMBER));

        SXSSFRow columnHeaders = addRowToSheet("Variable Name", "Data type", "Question type", "Description", "Options");
        columnHeaders.setRowStyle(boldUnderlineStyle);
    }

    private void addModuleRepeatDescription(String moduleName, SXSSFRow moduleNameRow, ModuleExportConfig moduleExportConfig) {
        String repeatString = "Up to " + moduleExportConfig.getNumMaxRepeats() + " entries for this module exist for each participant.\n";
        repeatString += "Entries are indicated in reverse chronological order.\n";
        repeatString += "The most recent entry has no suffix, the next-most-recent is suffixed with _2,"
                + " the next-most-recent is suffixed with _3, etc...\n ";
        repeatString += "e.g. " + moduleName + ".[QUESTION] is the most recent completion, and " + moduleName
                + "_2.QUESTION is the next-most recent completion.";
        moduleNameRow.createCell(DATATYPE_COL_NUMBER).setCellValue(repeatString);
    }

    protected void addCompositeQuestionHeaderRow(FilterExportConfig filterConfig, int activityRepeatNum, int questionRepeatNum,
                                                 Map<String, Object> option, String detailName, FilterExportConfig parentConfig) {

        String questionRootName = "[[" + getColumnName(parentConfig, activityRepeatNum, questionRepeatNum, null, null, null) + "]]";
        String descriptionText = (String) parentConfig.getQuestionDef().get(ESObjectConstants.QUESTION_TEXT);
        if (parentConfig.getMaxRepeats() > 1) {
            descriptionText += "\n May have up to " + parentConfig.getMaxRepeats()
                    + " response variables for each question, denoted with _2, _3, etc. for each response after the first.";
        }
        addRowToSheet(questionRootName, null, "COMPOSITE", descriptionText, null);

    }

    protected void addSplitOptionsHeaderRow(FilterExportConfig filterConfig, int activityRepeatNum, int questionRepeatNum,
                                            Map<String, Object> option, String detailName, FilterExportConfig parentConfig) {
        String questionRootName = "[[" + getColumnName(filterConfig, activityRepeatNum, questionRepeatNum, null, null, null) + "]]";
        String descriptionText = (String) filterConfig.getQuestionDef().get(ESObjectConstants.QUESTION_TEXT);
        addRowToSheet(questionRootName, "text", "MULTISELECT", descriptionText, null);
    }

    protected void addRegularVariableRow(FilterExportConfig filterConfig, int activityRepeatNum, int questionRepeatNum,
                                         Map<String, Object> option, String detailName, FilterExportConfig parentConfig) {
        String variableName = getColumnName(filterConfig, activityRepeatNum, questionRepeatNum, option, detailName, parentConfig);

        String questionType = filterConfig.getQuestionType();
        if (questionType == null) {
            questionType = "";
        }
        String dataType = QUESTION_TYPE_TO_DATA_TYPE.getOrDefault(questionType, "text");

        String descriptionText = filterConfig.getColumn().getDisplay();
        if (filterConfig.getQuestionDef() != null) {
            String questionText = (String) filterConfig.getQuestionDef().get(ESObjectConstants.QUESTION_TEXT);
            descriptionText = StringUtils.isNotBlank(questionText) ? questionText : descriptionText;
        }
        if (StringUtils.isBlank(descriptionText)) {
            descriptionText = "no description provided";
        }
        if (descriptionText.contains("_")) {
            descriptionText = Arrays.stream(descriptionText.split("_")).map(word -> StringUtils.toRootLowerCase(word))
                    .collect(Collectors.joining(" "));
        }
        if (filterConfig.getMaxRepeats() > 1) {
            descriptionText += "\n May have up to " + filterConfig.getMaxRepeats()
                    + " response variables, denoted with _2, _3, etc. for each response after the first.";
        }

        String optionText = null;
        if (filterConfig.getOptions() != null && filterConfig.getCollationSuffix() == null) {
            optionText = filterConfig.getOptions().stream()
                    .map(opt -> opt.get(ESObjectConstants.OPTION_STABLE_ID) + " - " + opt.get(ESObjectConstants.OPTION_TEXT))
                    .collect(Collectors.joining("\n"));
        }
        addRowToSheet(variableName, dataType, questionType, descriptionText, optionText);

    }

    protected void addAdditionalDetailRow(FilterExportConfig filterConfig, int activityRepeatNum, int questionRepeatNum,
                                          Map<String, Object> option, String detailName, FilterExportConfig parentConfig) {
        String variableName = getColumnName(filterConfig, activityRepeatNum, questionRepeatNum, option, detailName, parentConfig);
        addRowToSheet(variableName, "text", "TEXT", "additional detail", null);
    }

    protected void addSplitOptionsRow(FilterExportConfig filterConfig, int activityRepeatNum, int questionRepeatNum,
                                      Map<String, Object> option, String detailName, FilterExportConfig parentConfig) {
        String variableName = getColumnName(filterConfig, activityRepeatNum, questionRepeatNum, option, detailName, parentConfig);
        String descriptionText = (String) option.get(ESObjectConstants.OPTION_TEXT);
        String optionText = "0 - not selected; 1 - selected";
        addRowToSheet(variableName, null, null, descriptionText, optionText);
    }

    protected SXSSFRow addRowToSheet(String variableName, String dataType, String questionType, String description, String options) {
        SXSSFRow newRow = addRowToSheet();
        addCellToRow(newRow, VARIABLE_NAME_COL_NUMBER, variableName != null ? variableName : StringUtils.EMPTY);
        addCellToRow(newRow, DATATYPE_COL_NUMBER, dataType != null ? dataType : StringUtils.EMPTY);
        addCellToRow(newRow, QUESTION_TYPE_COL_NUMBER, questionType != null ? questionType : StringUtils.EMPTY);
        addCellToRow(newRow, DESCRIPTION_COL_NUMBER, description != null ? description : StringUtils.EMPTY);
        addCellToRow(newRow, OPTIONS_COL_NUMBER, options != null ? options : StringUtils.EMPTY);
        return newRow;
    }

    protected SXSSFRow addRowToSheet() {
        currentRowNum++;
        return sheet.createRow(currentRowNum);
    }

    protected SXSSFCell addCellToRow(SXSSFRow row, int colNum, String value) {
        SXSSFCell cell = row.createCell(colNum);
        cell.setCellValue(value);
        cell.setCellStyle(wrapStyle);
        return cell;
    }

    protected String getSheetName() {
        return SHEET_NAME;
    }
}
