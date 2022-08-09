
package org.broadinstitute.dsm.files.parser.stool;

import java.util.List;
import java.util.Map;

import org.broadinstitute.dsm.db.dao.stoolupload.StoolUploadDto;
import org.junit.Assert;
import org.junit.Test;

public class TSVStoolUploadRecordsParserTest {

    @Test
    public void handleMissingHeader() {
        var content = "participantId\tmfBarcode\treceiveDate\nvalue1\tvalue2\tvalue3\n";
        var recordsParser = new TSVStoolUploadRecordsParser(content);
        var maybeMissingHeader = recordsParser.findMissingHeaderIfAny(List.of("participantId", "mfBarcode"));
        maybeMissingHeader.ifPresentOrElse(header -> Assert.assertEquals("receiveDate", header), Assert::fail);
    }

    @Test
    public void handleNoMissingHeaders() {
        var content = "participantId\tmfBarcode\treceiveDate\nvalue1\tvalue2\tvalue3\n";
        var recordsParser = new TSVStoolUploadRecordsParser(content);
        var maybeMissingHeader = recordsParser.findMissingHeaderIfAny(List.of("participantId", "mfBarcode", "receiveDate"));
        maybeMissingHeader.ifPresentOrElse(header -> Assert.fail("header was missing " + header), () -> Assert.assertTrue(true));
    }

    @Test
    public void transformMapToStoolUploadObject() {
        var content = "participantId\tmfBarcode\treceiveDate\nvalue1\tvalue2\tvalue3\n";
        var recordsParser = new TSVStoolUploadRecordsParser(content);
        var actual =
                recordsParser.transformMapToObject(Map.of("participantId", "value1", "mfBarcode", "value2", "receiveDate", "value3"));
        Assert.assertEquals(new StoolUploadDto("value1", "value2", "value3"), actual);
    }

}
