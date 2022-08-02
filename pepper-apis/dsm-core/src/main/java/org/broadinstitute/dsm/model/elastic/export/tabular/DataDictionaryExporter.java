package org.broadinstitute.dsm.model.elastic.export.tabular;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFCell;
import org.apache.poi.xssf.streaming.SXSSFRow;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import spark.Response;

public class DataDictionaryExporter extends ExcelParticipantExporter {
    private final static int ROW_ACCESS_WINDOW_SIZE = 200;
    private int currentRowNum = -1;
    private static final String SHEET_NAME = "Data dictionary";
    private static final String FILE_NAME = "DataDictionary.xlsx";

    private final CellStyle wrapStyle;
    private final CellStyle boldStyle;
    private final CellStyle boldUnderlineStyle;
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

    public void export(Response response) throws IOException {
        setResponseHeaders(response);
        sheet.setColumnWidth(0, 40 * 256);
        sheet.setColumnWidth(1, 10 * 256);
        sheet.setColumnWidth(2, 20 * 256);
        sheet.setColumnWidth(3, 60 * 256);
        sheet.setColumnWidth(4, 40 * 256);
        for(ModuleExportConfig moduleConfig : moduleConfigs) {
            writeModuleToSheet(moduleConfig);
        }
        writeAndCloseSheet(response);
    }

    protected void writeModuleToSheet(ModuleExportConfig moduleConfig) {
        // add blank row
        SXSSFRow blankRow = addRowToSheet();
        blankRow.setRowStyle(boldStyle);
        String moduleName = getModuleColumnPrefix(moduleConfig.getQuestions().get(0), 1);

        SXSSFRow moduleNameRow = addRowToSheet();
        moduleNameRow.createCell(0).setCellValue(moduleName.toUpperCase());

        sheet.addMergedRegion(new CellRangeAddress(currentRowNum, currentRowNum, 0, 4));
        SXSSFRow columnHeaders = addRowToSheet();
        columnHeaders.setRowStyle(boldUnderlineStyle);
        columnHeaders.createCell(0).setCellValue("Variable Name");
        columnHeaders.createCell(1).setCellValue("Data type");
        columnHeaders.createCell(2).setCellValue("Question type");
        columnHeaders.createCell(3).setCellValue("Description");
        columnHeaders.createCell(4).setCellValue("Options");



        for (FilterExportConfig filterConfig : moduleConfig.getQuestions()) {
            List<String> variableNames = getConfigColumnNames(filterConfig, 1, 1);
            SXSSFRow questionRow = addRowToSheet();
            addCellToRow(questionRow, 0, variableNames.get(0));
            String dataType = "text";
            addCellToRow(questionRow, 1, dataType);
            String questionType = filterConfig.getQuestionType();
            addCellToRow(questionRow, 2, questionType);
            String description = filterConfig.getColumn().getDisplay();
            if (filterConfig.getQuestionDef() != null) {
                description = (String) filterConfig.getQuestionDef().get(ESObjectConstants.QUESTION_TEXT);
            }
            addCellToRow(questionRow, 3, description);
            if (filterConfig.isSplitOptionsIntoColumns()) {
                addCellToRow(questionRow, 4, "multiselect - a separate variable exists for each option");
                String questionTruncatedId = moduleName + DBConstants.ALIAS_DELIMITER +
                        filterConfig.getQuestionDef().get(ESObjectConstants.STABLE_ID);
                questionRow.getCell(0).setCellValue(questionTruncatedId);
                createSplitOptionRows(filterConfig, variableNames);
            } else if (filterConfig.getOptions() != null && filterConfig.getCollationSuffix() == null) {
                String optionText = filterConfig.getOptions().stream().map(opt ->
                    opt.get(ESObjectConstants.OPTION_STABLE_ID) + " - " + opt.get(ESObjectConstants.OPTION_TEXT)
                ).collect(Collectors.joining("\n"));
                addCellToRow(questionRow, 4, optionText);
            }
        }
    }

    protected void createSplitOptionRows(FilterExportConfig filterConfig, List<String> variableNames) {
        for (int optIndex = 0; optIndex < variableNames.size(); optIndex++) {
            SXSSFRow optionRow = addRowToSheet();
            addCellToRow(optionRow, 0, "  " + variableNames.get(optIndex));
            String description = filterConfig.getColumn().getDisplay();
            addCellToRow(optionRow,3, (String) filterConfig.getOptions().get(optIndex).get(ESObjectConstants.OPTION_TEXT));
            addCellToRow(optionRow,4, "0 - not selected; 1 - selected");
        }
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



    protected String getExportFilename(String suffix) {
        return FILE_NAME;
    }

    protected String getSheetName() {
        return SHEET_NAME;
    }
}
