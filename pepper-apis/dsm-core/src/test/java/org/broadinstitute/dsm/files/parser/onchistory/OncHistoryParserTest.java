package org.broadinstitute.dsm.files.parser.onchistory;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.service.onchistory.CodeStudyColumnsProvider;
import org.broadinstitute.dsm.service.onchistory.OncHistoryRecord;
import org.broadinstitute.dsm.service.onchistory.OncHistoryUploadService;
import org.broadinstitute.dsm.util.TestUtil;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

@Slf4j
public class OncHistoryParserTest {

    private static OncHistoryUploadService uploadService;

    @BeforeClass
    public static void setup() throws Exception {
        uploadService = initUploadService();
    }

    @Test
    public void testGoodFile() {
        try {
            String content = TestUtil.readFile("onchistory/oncHistoryDetail.txt");
            OncHistoryParser parser = new OncHistoryParser(content, uploadService);
            List<OncHistoryRecord> rows = parser.parseToObjects();
            Assert.assertEquals(3, rows.size());
            // spot check results
            List<Map<String, String>> rawRows = getContentRows(content, uploadService);
            Assert.assertEquals(rawRows.size(), rows.size());

            Iterator<OncHistoryRecord> rowIter = rows.iterator();
            Iterator<Map<String, String>> rawIter = rawRows.iterator();
            while (rowIter.hasNext() && rawIter.hasNext()) {
                OncHistoryRecord row = rowIter.next();
                Map<String, String> rawRow = rawIter.next();

                String recordId = rawRow.get("RECORD_ID");
                Assert.assertEquals(recordId, row.getParticipantTextId());

                // the upload service removes the record id from the map
                rawRow.remove("RECORD_ID");

                Assert.assertEquals(rawRow.size(), row.getColumns().size());
                Set<String> rowValues = new HashSet<>(row.getColumns().values());
                Set<String> rawValues = new HashSet<>(rawRow.values());
                Assert.assertEquals(rowValues, rawValues);
            }
        } catch (Exception e) {
            Assert.fail("Unexpected exception: " + e.toString());
        }
    }

    @Test
    public void testFileWithExtraCols() {
        try {
            String content = TestUtil.readFile("onchistory/oncHistoryExtraCols.txt");
            OncHistoryParser parser = new OncHistoryParser(content, uploadService);
            List<OncHistoryRecord> rows = parser.parseToObjects();
            Assert.assertEquals(3, rows.size());
            // spot check results
            List<Map<String, String>> rawRows = getContentRows(content, uploadService);
            Assert.assertEquals(rawRows.size(), rows.size());

            // extra columns are fine
            Map<String, String> cols = rows.get(0).getColumns();
            Assert.assertEquals(uploadService.getUploadColumnNames().size(), cols.size());
            Map<String, String> rawCols = rawRows.get(0);
            Assert.assertTrue(rawCols.size() > cols.size());
        } catch (Exception e) {
            Assert.fail("Unexpected exception: " + e.toString());
        }
    }

    @Test
    public void testFileWithMissingCols() {
        try {
            String content = TestUtil.readFile("onchistory/oncHistoryMissingCols.txt");
            OncHistoryParser parser = new OncHistoryParser(content, uploadService);

            try {
                parser.parseToObjects();
                Assert.fail("Upload file with missing required columns should fail parser");
            } catch (Exception e) {
                Assert.assertTrue(e.getMessage().contains("File is missing the column"));
            }
        } catch (Exception e) {
            Assert.fail("Unexpected exception: " + e.toString());
        }
    }

    @Test
    public void parseTabs() {
        String str = "\t\tabc\t123\tdef\t\t\t";
        String[] cols = str.split("\t", -1);
        Assert.assertEquals(8, cols.length);
    }

    private static OncHistoryUploadService initUploadService() {
        OncHistoryUploadService uploadService = new OncHistoryUploadService("testRealm", "testUser",
                new CodeStudyColumnsProvider());
        uploadService.setColumnsForStudy();
        return uploadService;
    }

    public static List<Map<String, String>> getContentRows(String content, OncHistoryUploadService uploadService) {
        OncHistoryTestParser parser = new OncHistoryTestParser(content, uploadService);
        return parser.parseToObjects();
    }
}
