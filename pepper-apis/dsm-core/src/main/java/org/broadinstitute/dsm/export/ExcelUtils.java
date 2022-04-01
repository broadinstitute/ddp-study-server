package org.broadinstitute.dsm.export;

import javax.servlet.ServletOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.IntStream;

import com.google.common.net.MediaType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.broadinstitute.dsm.model.elastic.export.excel.ParticipantRecordData;
import spark.Response;

public class ExcelUtils {
    private static final String FILE_DATE_FORMAT = "ddMMyyyy";

    public static void createResponseFile (ParticipantRecordData rowData, Response response) throws Exception {
        List<List<String>> allData = rowData.getRowData();
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Participant List");
            createRecords(sheet, 1, allData);
            createHeader(sheet, rowData.getHeader());
            responseFile(response, workbook);
        }
    }

    private static void createHeader(Sheet sheet, List<String> headerColumns) {
        Row header = sheet.createRow(0);
        IntStream.range(0, headerColumns.size()).forEach(i -> {
            header.createCell(i).setCellValue(headerColumns.get(i));
            sheet.autoSizeColumn(i);
        });
    }

    private static void responseFile(spark.Response response, Workbook workbook) throws Exception {
        File currDir = new File(".");
        String path = currDir.getAbsolutePath();
        String strDate = getFormattedDate();
        String fileLocation = String.format("%sParticipant-%s.xlsx", path.substring(0, path.length() - 1), strDate);
        try (FileOutputStream outputStream = new FileOutputStream(fileLocation); ServletOutputStream os = response.raw().getOutputStream()){
            workbook.write(outputStream);
            File file = new File(fileLocation);
            setResponseHeaders(response, file.getName());
            byte[] encoded = Files.readAllBytes(Paths.get(fileLocation));
            os.write(encoded);
            Files.deleteIfExists(file.toPath());
        }
    }

    private static void setResponseHeaders(Response response, String filename) {
        response.type(MediaType.OCTET_STREAM.toString());
        response.header("Access-Control-Expose-Headers", "Content-Disposition");
        response.header("Content-Disposition", "attachment;filename=" + filename);
    }

    private static void createRecords(Sheet sheet, int currentRow, List<List<String>> rowValues) {
        for (List<String> rowValue : rowValues) {
            Row row = sheet.createRow(currentRow);
            for (int j = 0; j < rowValue.size(); j++) {
                row.createCell(j).setCellValue(rowValue.get(j));
            }
            currentRow++;
        }
    }

    private static String getFormattedDate() {
        LocalDate date = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(FILE_DATE_FORMAT);
        return date.format(formatter);
    }
}
