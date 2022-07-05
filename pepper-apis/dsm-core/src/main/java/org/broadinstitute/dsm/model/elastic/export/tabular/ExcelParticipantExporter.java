package org.broadinstitute.dsm.model.elastic.export.tabular;

import com.google.common.net.MediaType;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import spark.Response;

import javax.servlet.ServletOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

public class ExcelParticipantExporter extends TabularParticipantExporter {
    private final SXSSFWorkbook workbook = new SXSSFWorkbook(200);

    private final SXSSFSheet sheet;


    public ExcelParticipantExporter(List<ModuleExportConfig> moduleConfigs,
                                      List<Map<String, String>> participantValueMaps, String fileFormat) {
        super(moduleConfigs, participantValueMaps, fileFormat);
        this.sheet = workbook.createSheet("Participant List");
        sheet.trackAllColumnsForAutoSizing();
    }

    public void export(Response response) throws IOException {
        setResponseHeaders(response);
        
        List<String> headerValues = getHeaderRow();
        List<String> subHeaderValues = getSubHeaderRow();

        writeRowToSheet(headerValues, 0);
        writeRowToSheet(subHeaderValues, 1);

        IntStream.range(0, participantValueMaps.size()).forEach(i -> {
            Map<String, String> valueMap = participantValueMaps.get(i);
            List<String> rowValues = getRowValues(valueMap, headerValues);
            writeRowToSheet(rowValues, i + 2);
        });

        try (ServletOutputStream servOut = response.raw().getOutputStream()) {
            workbook.write(servOut);
        }
        workbook.dispose();
        workbook.close();

    }

    private void setResponseHeaders(Response response) {
        response.type(MediaType.OCTET_STREAM.toString());
        response.header("Access-Control-Expose-Headers", "Content-Disposition");
        response.header("Content-Disposition", "attachment;filename=" + getExportFilename(fileFormat));
    }

    private void writeRowToSheet(List<String> rowValues, int rowNum) {
        Row headerRow = sheet.createRow(rowNum);
        IntStream.range(0, rowValues.size()).forEach(i -> {
            headerRow.createCell(i).setCellValue(rowValues.get(i));
        });
    }

    /** we don't need to worry about escaping any characters, we just need to replace null with empty string */
    protected String sanitizeValue(String value) {
        if (value == null) {
            value = StringUtils.EMPTY;
        }
        return value;
    }
}
