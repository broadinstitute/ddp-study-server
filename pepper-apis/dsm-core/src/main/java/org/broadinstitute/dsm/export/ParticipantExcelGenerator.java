package org.broadinstitute.dsm.export;

import javax.servlet.ServletOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.IntStream;

import com.google.common.net.MediaType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import spark.Response;

public class ParticipantExcelGenerator {
    private static final String FILE_DATE_FORMAT = "ddMMyyyy";

    private final SXSSFWorkbook workbook = new SXSSFWorkbook(200);

    private final SXSSFSheet sheet;

    private int currentRow = 1;

    public ParticipantExcelGenerator() {
        this.sheet = workbook.createSheet("Participant List");
        sheet.trackAllColumnsForAutoSizing();
    }

    private static String getFormattedDate() {
        LocalDate date = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(FILE_DATE_FORMAT);
        return date.format(formatter);
    }

    public void createHeader(List<String> headerColumns) {
        Row headerRow = sheet.createRow(0);
        IntStream.range(0, headerColumns.size()).forEach(i -> {
            headerRow.createCell(i).setCellValue(headerColumns.get(i));
        });
    }

    private void setResponseHeaders(Response response, String filename) {
        response.type(MediaType.OCTET_STREAM.toString());
        response.header("Access-Control-Expose-Headers", "Content-Disposition");
        response.header("Content-Disposition", "attachment;filename=" + filename);
    }

    public void appendData(List<List<String>> rowValues) {
        for (List<String> rowValue : rowValues) {
            Row row = sheet.createRow(currentRow);
            for (int j = 0; j < rowValue.size(); j++) {
                row.createCell(j).setCellValue(rowValue.get(j));
            }
            currentRow++;
        }
    }

    public void writeInResponse(Response response) throws IOException {
        try (ServletOutputStream os = response.raw().getOutputStream()) {
            setResponseHeaders(response, String.format("Participant-%s.xlsx", getFormattedDate()));
            workbook.write(os);
        }
        workbook.dispose();
        workbook.close();
    }

    public void formatSizes(int columnsNumber) {
        IntStream.range(0, columnsNumber).forEach(sheet::autoSizeColumn);
    }
}
