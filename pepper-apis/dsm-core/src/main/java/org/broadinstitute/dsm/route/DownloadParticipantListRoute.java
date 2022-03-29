package org.broadinstitute.dsm.route;

import javax.servlet.ServletOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.google.gson.Gson;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.broadinstitute.dsm.model.ParticipantColumn;
import org.broadinstitute.dsm.model.elastic.sort.Alias;
import org.broadinstitute.dsm.model.excel.ParticipantRecord;
import org.broadinstitute.dsm.model.filter.FilterFactory;
import org.broadinstitute.dsm.model.filter.Filterable;
import org.broadinstitute.dsm.model.participant.DownloadParticipantListPayload;
import org.broadinstitute.dsm.model.participant.ParticipantWrapperDto;
import org.broadinstitute.dsm.model.participant.ParticipantWrapperResult;
import org.broadinstitute.dsm.security.RequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

public class DownloadParticipantListRoute extends RequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(DownloadParticipantListRoute.class);
    private static final String FILE_DATE_FORMAT = "ddMMyyyy";

    public DownloadParticipantListRoute() {
    }

    @Override
    public Object processRequest(Request request, Response response, String userId) throws Exception {
        DownloadParticipantListPayload payload = new Gson().fromJson(request.body(), DownloadParticipantListPayload.class);

        List<ParticipantColumn> columnNames = payload.getColumnNames();
        List<String> esAliases = columnNames.stream()
                .map(column -> {
                    Alias esAlias;
                    if (column.getObject() != null) {
                        esAlias = Alias.of(column.getObject());
                    } else {
                        esAlias = Alias.of(column.getTableAlias());
                    }
                    return esAlias.getValue().isEmpty()? column.getName() : esAlias.getValue() + "." + column.getName();
                })
                .collect(Collectors.toList());

        Filterable filterable = FilterFactory.of(request);
        ParticipantWrapperResult filteredList = (ParticipantWrapperResult) filterable.filter(request.queryMap());
        Workbook workbook = new XSSFWorkbook();
        List<String> headerNames = payload.getHeaderNames();
        Sheet sheet = workbook.createSheet("Participant List");
        Row header = sheet.createRow(0);
        IntStream.range(0, headerNames.size()).forEach(i -> {
            Cell cell = header.createCell(i);
            cell.setCellValue(headerNames.get(i));
            sheet.autoSizeColumn(i);
        });
        int currentRow = 1;
        for (ParticipantWrapperDto participant : filteredList.getParticipants()) {
            List<Object> excelRow = new ArrayList<>();
            Map<String, Object> esDataAsMap = participant.getEsDataAsMap();
            List<String> row = new ArrayList<>();
            for (String esAlias : esAliases) {
                Object nestedValue = getNestedValue(esAlias, esDataAsMap);
                excelRow.add(nestedValue);
                if (nestedValue instanceof Collection) {
                    Collection<?> value = (Collection<?>) nestedValue;
                    row.add(value.stream().map(Object::toString)
                            .collect(Collectors.joining(System.lineSeparator())));
                } else {
                    row.add(nestedValue.toString());
                }
            }
            currentRow = createRecord(sheet, currentRow, new ParticipantRecord(excelRow));
        }
        responseFile(response, workbook);
        return response.raw();
    }


    private void responseFile(Response response, Workbook workbook) throws Exception {
        File currDir = new File(".");
        String path = currDir.getAbsolutePath();
        String strDate = getFormattedDate();
        String fileLocation = String.format("%sParticipant-%s.xlsx", path.substring(0, path.length() - 1), strDate);
        FileOutputStream outputStream = new FileOutputStream(fileLocation);
        workbook.write(outputStream);
        workbook.close();
        File file = new File(fileLocation);
        response.raw().setContentType("application/octet-stream");
        response.raw().setHeader("Content-Disposition", "attachment; filename=" + file.getName());
        byte[] encoded = Files.readAllBytes(Paths.get(fileLocation));
        ServletOutputStream os = response.raw().getOutputStream();
        os.write(encoded);
        os.close();
        Files.deleteIfExists(file.toPath());
    }

    private String getFormattedDate() {
        Date date = new Date();
        SimpleDateFormat formatter = new SimpleDateFormat(FILE_DATE_FORMAT);
        return formatter.format(date);
    }

    private int createRecord(Sheet sheet, int startRow, ParticipantRecord participantRecord) {
        List<Object> values = participantRecord.getValues();
        List<Row> rows = createEmptyRows(sheet, startRow, participantRecord.getMaxRows(), values.size());
        IntStream.range(0, values.size()).forEach(i -> {
            Cell currentCell = rows.get(0).getCell(i);
            Object currentValue = values.get(i);
            int filledRows = 1;
            if (currentValue instanceof Collection) {
                Collection<?> value = (Collection<?>) currentValue;
                fillRowColumnsWithValues(rows, value, i);
                filledRows = Math.max(value.size(), filledRows);
            } else {
                currentCell.setCellValue(currentValue.toString());
            }
            sheet.autoSizeColumn(i);
            int firstRow = startRow + filledRows - 1;
            int lastRow = startRow + participantRecord.getMaxRows() - 1;
            if (firstRow != lastRow) {
                sheet.addMergedRegion(new CellRangeAddress(firstRow, lastRow, i, i));
            }
        });
        return startRow + participantRecord.getMaxRows();
    }

    private void fillRowColumnsWithValues(List<Row> rows, Collection<?> value, int column) {
        int i = 0;
        for (Object o : value) {
            rows.get(i).getCell(column).setCellValue(o.toString());
            i++;
        }
    }

    private List<Row> createEmptyRows(Sheet sheet, int startRow, int count, int columns) {
        List<Row> rows = new ArrayList<>();
        IntStream.range(0, count).forEach(i -> {
            Row row = sheet.createRow(startRow + i);
            IntStream.range(0, columns).forEach(row::createCell);
            rows.add(row);
        });
        return rows;
    }

    private Object getNestedValue(String fieldName, Map<String, Object> esDataAsMap) {
        int dotIndex = fieldName.indexOf('.');
        if (dotIndex != -1) {
            Object o = esDataAsMap.get(fieldName.substring(0, dotIndex));
            if (o == null) {
                return StringUtils.EMPTY;
            }
            if (o instanceof Collection) {
                return ((Collection<?>) o).stream().map(singleDataMap -> getNestedValue(fieldName.substring(dotIndex + 1),
                        (Map<String, Object>) singleDataMap)).collect(Collectors.toList());
            } else {
                return getNestedValue(fieldName.substring(dotIndex + 1), (Map<String, Object>) o);
            }
        }
        return esDataAsMap.getOrDefault(fieldName, StringUtils.EMPTY);
    }

}
