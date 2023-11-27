package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.audit.AuditActionType;
import org.broadinstitute.ddp.audit.AuditEntityType;
import org.broadinstitute.ddp.audit.AuditTrailFilter;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.common.Limitation;
import org.broadinstitute.ddp.db.dto.AuditTrailDto;
import org.broadinstitute.ddp.db.dto.StudyDto;

import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.junit.BeforeClass;
import org.junit.Test;
import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;


public class AuditTrailDaoTest extends TxnAwareBaseTest {
    private static TestDataSetupUtil.GeneratedTestData testData;

    @BeforeClass
    public static void setup() {
        testData = TransactionWrapper.withTxn(TestDataSetupUtil::generateBasicUserTestData);
    }

    @Test
    public void testFilter() {
        assertNotNull(new AuditTrailFilter().getConstraints());
        assertNotNull(new AuditTrailFilter().getLimitation());
    }

    @Test
    public void testSearchEmpty() {
        TransactionWrapper.useTxn(handle -> {
            final StudyDto study = TestDataSetupUtil.generateTestStudy(handle, cfg);
            handle.attach(JdbiClientUmbrellaStudy.class).insert(testData.getClientId(), study.getId());

            final List<AuditTrailDto> auditTrail = handle.attach(AuditTrailDao.class).findByFilter(new AuditTrailFilter());
            assertTrue(auditTrail.isEmpty());

            handle.rollback();
        });
    }

    @Test
    public void testSearchSimple() {
        TransactionWrapper.useTxn(handle -> {
            final StudyDto study = TestDataSetupUtil.generateTestStudy(handle, cfg);
            handle.attach(JdbiClientUmbrellaStudy.class).insert(testData.getClientId(), study.getId());

            final Long auditTrailId = handle.attach(AuditTrailDao.class).insert(AuditTrailDto.builder()
                    .studyId(study.getId())
                    .operatorId(testData.getUserId())
                    .subjectUserId(testData.getUserId())
                    .entityType(AuditEntityType.USER)
                    .actionType(AuditActionType.CREATE)
                    .description("Some description")
                    .build());
            assertNotNull(auditTrailId);

            final List<AuditTrailDto> auditTrail = handle.attach(AuditTrailDao.class).findByFilter(new AuditTrailFilter());
            assertEquals(1, auditTrail.size());

            handle.rollback();
        });
    }

    @Test
    public void testSearchWithLimitation() {
        TransactionWrapper.useTxn(handle -> {
            final StudyDto study = TestDataSetupUtil.generateTestStudy(handle, cfg);
            handle.attach(JdbiClientUmbrellaStudy.class).insert(testData.getClientId(), study.getId());

            final Long auditTrailId = handle.attach(AuditTrailDao.class).insert(AuditTrailDto.builder()
                    .studyId(study.getId())
                    .operatorId(testData.getUserId())
                    .subjectUserId(testData.getUserId())
                    .entityType(AuditEntityType.USER)
                    .actionType(AuditActionType.CREATE)
                    .description("Some description")
                    .build());
            assertNotNull(auditTrailId);

            assertEquals(1, handle.attach(AuditTrailDao.class).findByFilter(AuditTrailFilter.builder()
                    .limitation(Limitation.builder()
                            .from(0)
                            .to(10)
                            .build())
                    .build()).size());

            assertEquals(0, handle.attach(AuditTrailDao.class).findByFilter(AuditTrailFilter.builder()
                    .limitation(Limitation.builder()
                            .from(5)
                            .to(10)
                            .build())
                    .build()).size());

            handle.rollback();
        });
    }

    @Test
    public void testSearchWithFiltration() {
        TransactionWrapper.useTxn(handle -> {
            final StudyDto study = TestDataSetupUtil.generateTestStudy(handle, cfg);
            handle.attach(JdbiClientUmbrellaStudy.class).insert(testData.getClientId(), study.getId());

            final Long auditTrailId = handle.attach(AuditTrailDao.class).insert(AuditTrailDto.builder()
                    .studyId(study.getId())
                    .operatorId(testData.getUserId())
                    .subjectUserId(testData.getUserId())
                    .entityType(AuditEntityType.USER)
                    .actionType(AuditActionType.CREATE)
                    .description("Some description")
                    .build());
            assertNotNull(auditTrailId);

            assertEquals(1, handle.attach(AuditTrailDao.class).findByFilter(AuditTrailFilter.builder()
                    .constraints(AuditTrailDto.builder()
                            .studyId(study.getId())
                            .build())
                    .build()).size());

            assertEquals(0, handle.attach(AuditTrailDao.class).findByFilter(AuditTrailFilter.builder()
                    .constraints(AuditTrailDto.builder()
                            .studyId(-42L)
                            .build())
                    .build()).size());

            handle.rollback();
        });
    }

    @Test
    public void testInsert() {
        TransactionWrapper.useTxn(handle -> {
            final StudyDto study = TestDataSetupUtil.generateTestStudy(handle, cfg);
            handle.attach(JdbiClientUmbrellaStudy.class).insert(testData.getClientId(), study.getId());

            final AuditTrailDto expected = AuditTrailDto.builder()
                    .studyId(study.getId())
                    .operatorId(testData.getUserId())
                    .subjectUserId(testData.getUserId())
                    .entityType(AuditEntityType.USER)
                    .actionType(AuditActionType.CREATE)
                    .description("Some description")
                    .build();

            final Long auditTrailId = handle.attach(AuditTrailDao.class).insert(expected);
            assertNotNull(auditTrailId);

            final AuditTrailDto actual = handle.attach(AuditTrailDao.class).findById(auditTrailId);
            assertNotNull(actual);

            assertEquals(expected.toBuilder()
                    .auditTrailId(auditTrailId)
                    .time(actual.getTime())
                    .build(), actual);

            handle.rollback();
        });
    }
}
