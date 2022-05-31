package org.broadinstitute.ddp.audit;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiClientUmbrellaStudy;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class AuditTrailServiceTest extends TxnAwareBaseTest {
    private static TestDataSetupUtil.GeneratedTestData testData;

    @BeforeClass
    public static void setup() {
        testData = TransactionWrapper.withTxn(TestDataSetupUtil::generateBasicUserTestData);
    }

    @Test
    public void testSearchEmpty() {
        TransactionWrapper.useTxn(handle -> {
            final StudyDto study = TestDataSetupUtil.generateTestStudy(handle, cfg);
            handle.attach(JdbiClientUmbrellaStudy.class).insert(testData.getClientId(), study.getId());

            assertTrue(AuditTrailService.search(new AuditTrailFilter()).isEmpty());

            handle.rollback();
        });
    }
}
