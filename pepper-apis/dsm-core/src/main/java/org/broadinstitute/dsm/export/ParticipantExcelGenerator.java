package org.broadinstitute.dsm.export;

import javax.servlet.ServletOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import com.google.common.net.MediaType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.broadinstitute.dsm.model.elastic.export.excel.ExcelRow;
import spark.Response;

public class ParticipantExcelGenerator {
    private static final String FILE_DATE_FORMAT = "ddMMyyyy";

    private final SXSSFWorkbook workbook = new SXSSFWorkbook(200);

    private final SXSSFSheet sheet;

    private int currentRow = 0;

    public ParticipantExcelGenerator() {
        this.sheet = workbook.createSheet("Participant List");
        sheet.trackAllColumnsForAutoSizing();
    }

    private void setResponseHeaders(Response response, String filename) {
        response.type(MediaType.OCTET_STREAM.toString());
        response.header("Access-Control-Expose-Headers", "Content-Disposition");
        response.header("Content-Disposition", "attachment;filename=" + filename);
    }

    public void appendData(ExcelRow rowValues) {
        Row row = sheet.createRow(currentRow);
        for (int i = 0; i < rowValues.getRowData().size(); i++) {
            row.createCell(i).setCellValue(rowValues.getRowData().get(i));
        }
        currentRow++;
    }

    private static String getFormattedDate() {
        LocalDate date = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(FILE_DATE_FORMAT);
        return date.format(formatter);
    }

    public void writeInResponse(Response response) throws IOException {
        try (ServletOutputStream os = response.raw().getOutputStream()) {
            setResponseHeaders(response, String.format("Participant-%s.xlsx", getFormattedDate()));
            workbook.write(os);
        }
        workbook.dispose();
        workbook.close();
    }

}
