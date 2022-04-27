package org.broadinstitute.ddp.audit;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiClientUmbrellaStudy;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

public class AuditTrailServiceTest extends TxnAwareBaseTest {
    private static TestDataSetupUtil.GeneratedTestData testData;

    @BeforeClass
    public static void setup() {
        TransactionWrapper.useTxn(handle -> testData = TestDataSetupUtil.generateBasicUserTestData(handle));
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

    @Test
    public void testUser() {
        TransactionWrapper.useTxn(handle -> {
            final StudyDto study = TestDataSetupUtil.generateTestStudy(handle, cfg);
            handle.attach(JdbiClientUmbrellaStudy.class).insert(testData.getClientId(), study.getId());

            AuditTrailService.user(study.getId(),
                    testData.getUserId(),
                    testData.getUserId(),
                    AuditActionType.CREATE,
                    "User created");

            assertEquals(1, AuditTrailService.search(new AuditTrailFilter()).size());

            handle.rollback();
        });
    }
}
