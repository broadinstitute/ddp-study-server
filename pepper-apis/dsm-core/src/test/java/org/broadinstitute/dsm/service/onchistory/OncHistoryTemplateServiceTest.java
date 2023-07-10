package org.broadinstitute.dsm.service.onchistory;

import static org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace;
import static org.broadinstitute.dsm.service.onchistory.OncHistoryTemplateService.FULL_DATE_DESC;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.xssf.streaming.SXSSFCell;
import org.apache.poi.xssf.streaming.SXSSFRow;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.broadinstitute.dsm.DbTxnBaseTest;
import org.broadinstitute.dsm.util.SystemUtil;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

@Slf4j
public class OncHistoryTemplateServiceTest extends DbTxnBaseTest {

    private static final String DEFAULT_REALM = "osteo2";
    private static Map<String, String> recordIdColumn;

    @BeforeClass
    public static void setup() {
        recordIdColumn = OncHistoryTemplateService.createRecordIdColumn();
    }

    @Test
    public void testWriteTemplate() {
        StudyColumnsProvider columnsProvider = new CodeStudyColumnsProvider();
        // use the DB values for a real realm
        OncHistoryTemplateService service = new OncHistoryTemplateService(DEFAULT_REALM, new CodeStudyColumnsProvider());

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try {
            service.writeTemplate(os);
        } catch (Exception e) {
            Assert.fail(getStackTrace(e));
        }

        Map<String, OncHistoryUploadColumn> studyColumns = columnsProvider.getColumnsForStudy(DEFAULT_REALM);
        List<OncHistoryUploadColumn> uploadColumns = new ArrayList<>(studyColumns.values());
        List<String> expectedValues = uploadColumns.stream().map(OncHistoryUploadColumn::getColumnAlias)
                .collect(Collectors.toList());
        try {
            String headerRow = os.toString(StandardCharsets.UTF_8);
            Assert.assertEquals(headerRow.length() - 1, headerRow.indexOf("\n"));
            headerRow = headerRow.trim();
            Assert.assertEquals(expectedValues, Arrays.asList((headerRow.split(SystemUtil.TAB_SEPARATOR))));
        } catch (Exception e) {
            Assert.fail(getStackTrace(e));
        }
    }

    @Test
    public void testCreateRow() {
        StudyColumnsProvider columnsProvider = new CodeStudyColumnsProvider();
        // use the DB values for a real realm
        OncHistoryTemplateService service = new OncHistoryTemplateService(DEFAULT_REALM, new CodeStudyColumnsProvider());

        Map<String, OncHistoryUploadColumn> studyColumns = columnsProvider.getColumnsForStudy(DEFAULT_REALM);
        List<OncHistoryUploadColumn> uploadColumns = new ArrayList<>(studyColumns.values());

        try {
            List<String> headerValues = service.createHeaderRow(uploadColumns, "Column header");
            validateHeader(headerValues, uploadColumns);

            List<String> values = service.createTypeRow(uploadColumns, "Column type");
            validateType(values, headerValues);

            service.initialize();

            values = service.createDescRow(uploadColumns, "Description");
            validateDescription(values, headerValues);

            values = service.createNotesRow(uploadColumns, "Notes");
            validateNotes(values, headerValues, service.getColumnOptions());
        } catch (Exception e) {
            Assert.fail(getStackTrace(e));
        }
    }

    @Test
    public void testCreateSheet() {
        StudyColumnsProvider columnsProvider = new CodeStudyColumnsProvider();
        // use the DB values for a real realm
        OncHistoryTemplateService service = new OncHistoryTemplateService(DEFAULT_REALM, new CodeStudyColumnsProvider());

        Map<String, OncHistoryUploadColumn> studyColumns = columnsProvider.getColumnsForStudy(DEFAULT_REALM);
        List<OncHistoryUploadColumn> uploadColumns = new ArrayList<>(studyColumns.values());

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(10)) {
            service.initialize();
            service.createSheet(workbook, uploadColumns);
            SXSSFSheet sheet = workbook.getSheetAt(0);
            Assert.assertEquals(4, sheet.getLastRowNum());

            int rowNum = 1;
            SXSSFRow row = sheet.getRow(rowNum);
            List<String> headerValues = getRowValues(row);
            validateHeader(getRowValues(row), uploadColumns);

            row = sheet.getRow(++rowNum);
            validateDescription(getRowValues(row), headerValues);

            row = sheet.getRow(++rowNum);
            validateType(getRowValues(row), headerValues);

            row = sheet.getRow(++rowNum);
            validateNotes(getRowValues(row), headerValues, service.getColumnOptions());
        } catch (Exception e) {
            Assert.fail(getStackTrace(e));
        }
    }

    private List<String> getRowValues(SXSSFRow row) {
        Iterator<Cell> it = row.allCellsIterator();
        List<String> values = new ArrayList<>();
        while (it.hasNext()) {
            values.add(it.next().getStringCellValue());
        }
        return values;
    }

    private void validateHeader(List<String> headerValues, List<OncHistoryUploadColumn> uploadColumns) {
        List<String> expectedValues = uploadColumns.stream().map(OncHistoryUploadColumn::getColumnAlias)
                .collect(Collectors.toList());
        Assert.assertEquals("Column header", headerValues.get(0));
        Assert.assertEquals(recordIdColumn.get("header"), headerValues.get(1));
        Assert.assertTrue(headerValues.containsAll(expectedValues));
    }

    private void validateType(List<String> values, List<String> headerValues) {
        Assert.assertEquals("Column type", values.get(0));
        Assert.assertEquals(recordIdColumn.get("type"), values.get(1));
        Assert.assertEquals(headerValues.size(), values.size());
        Assert.assertEquals("Text", values.get(headerValues.indexOf("HISTOLOGY")));
        Assert.assertEquals("Date", values.get(headerValues.indexOf("DATE_PX")));
        Assert.assertEquals("Select", values.get(headerValues.indexOf("LOCAL_CONTROL")));
    }

    private void validateDescription(List<String> values, List<String> headerValues) {
        Assert.assertEquals("Description", values.get(0));
        Assert.assertEquals(recordIdColumn.get("description"), values.get(1));
        Assert.assertEquals(headerValues.size(), values.size());
        Assert.assertEquals("Accession Number", values.get(headerValues.indexOf("ACCESSION")));
        Assert.assertEquals("Date of PX", values.get(headerValues.indexOf("DATE_PX")));
    }

    private void validateNotes(List<String> values, List<String> headerValues, Map<String, List<String>> columnOptions) {
        Assert.assertEquals("Notes", values.get(0));
        Assert.assertEquals(recordIdColumn.get("notes"), values.get(1));
        Assert.assertEquals(headerValues.size(), values.size());
        Assert.assertTrue(values.get(headerValues.indexOf("DATE_PX")).contains(FULL_DATE_DESC));
        List<String> requestOptions = columnOptions.get("REQUEST_STATUS");
        String content = values.get(headerValues.indexOf("REQUEST_STATUS"));
        for (var option: requestOptions) {
            Assert.assertTrue(content.contains(option));
        }
    }
}
