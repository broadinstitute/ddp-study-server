package org.broadinstitute.dsm.model.elastic.export.tabular;

import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/** generates an excel file with a single sheet containing the participant data */
public class ExcelParticipantExporter extends TabularParticipantExporter {
    private static final int ROW_ACCESS_WINDOW_SIZE = 200;
    protected final SXSSFWorkbook workbook;

    protected final SXSSFSheet sheet;
    private static final String SHEET_NAME = "Participant List";

    public String getExportFilename() {
        return getExportFilename("xlsx");
    }

    public ExcelParticipantExporter(List<ModuleExportConfig> moduleConfigs,
                                    List<Map<String, String>> participantValueMaps, String fileFormat) {
        super(moduleConfigs, participantValueMaps, fileFormat);
        workbook = new SXSSFWorkbook(ROW_ACCESS_WINDOW_SIZE);
        sheet = workbook.createSheet(getSheetName());
        sheet.trackAllColumnsForAutoSizing();
    }

    public void export(OutputStream os) throws IOException {
        List<String> headerValues = getHeaderRow();
        List<String> subHeaderValues = getSubHeaderRow();

        writeRowToSheet(headerValues, 0);
        writeRowToSheet(subHeaderValues, 1);

        IntStream.range(0, participantValueMaps.size()).forEach(i -> {
            Map<String, String> valueMap = participantValueMaps.get(i);
            List<String> rowValues = getRowValues(valueMap, headerValues);
            writeRowToSheet(rowValues, i + 2);
        });

        writeAndCloseSheet(os);
    }

    protected void writeAndCloseSheet(OutputStream os) throws IOException {
        try (workbook) {
            workbook.write(os);
            workbook.dispose();
        }
    }

    protected void writeRowToSheet(List<String> rowValues, int rowNum) {
        Row headerRow = sheet.createRow(rowNum);
        IntStream.range(0, rowValues.size()).forEach(i -> {
            headerRow.createCell(i).setCellValue(rowValues.get(i));
        });
    }

    /**
     * we don't need to worry about escaping any characters, we just need to replace null with empty string
     */
    protected String sanitizeValue(String value) {
        if (value == null) {
            value = StringUtils.EMPTY;
        }
        return value;
    }

    protected String getSheetName() {
        return SHEET_NAME;
    }
}
