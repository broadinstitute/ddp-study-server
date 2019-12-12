package org.broadinstitute.ddp.db.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.Optional;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.model.dsm.PdfMappingType;
import org.broadinstitute.ddp.model.dsm.StudyPdfMapping;
import org.broadinstitute.ddp.model.pdf.PdfConfigInfo;
import org.broadinstitute.ddp.model.pdf.PdfConfiguration;
import org.broadinstitute.ddp.model.pdf.PdfVersion;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.junit.BeforeClass;
import org.junit.Test;

public class JdbiStudyPdfMappingTest extends TxnAwareBaseTest {

    private static TestDataSetupUtil.GeneratedTestData testData;

    @BeforeClass
    public static void setup() {
        TransactionWrapper.useTxn(handle -> testData = TestDataSetupUtil.generateBasicUserTestData(handle));
    }

    @Test
    public void testFindByStudyIdAndMappingType() {
        TransactionWrapper.useTxn(handle -> {
            JdbiStudyPdfMapping dao = handle.attach(JdbiStudyPdfMapping.class);

            long revId = handle.attach(JdbiRevision.class).insertStart(Instant.now().toEpochMilli(), testData.getUserId(), "dummy pdf");
            PdfConfiguration config = new PdfConfiguration(
                    new PdfConfigInfo(testData.getStudyId(), "dummy_pdf", "dummy-pdf"),
                    new PdfVersion("v1-no-data-sources", revId));
            long expectedPdfConfigId = handle.attach(PdfDao.class).insertNewConfig(config);

            long expectedId = dao.insert(testData.getStudyId(), PdfMappingType.RELEASE, expectedPdfConfigId);

            Optional<StudyPdfMapping> res = dao.findByStudyIdAndMappingType(testData.getStudyId(), PdfMappingType.RELEASE);
            assertTrue(res.isPresent());

            StudyPdfMapping mapping = res.get();
            assertEquals(expectedId, mapping.getId());
            assertEquals(testData.getStudyId(), mapping.getStudyId());
            assertEquals(expectedPdfConfigId, mapping.getPdfConfigurationId());

            handle.rollback();
        });
    }
}
