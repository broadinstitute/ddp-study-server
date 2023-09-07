package org.broadinstitute.dsm.service.admin;

import java.sql.Timestamp;
import java.util.Date;
import java.util.List;

import org.broadinstitute.dsm.DbTxnBaseTest;
import org.junit.Assert;
import org.junit.Test;

public class AdminOperationRecordTest extends DbTxnBaseTest {

    @Test
    public void operationRecordTest() {
        String userId = "test_user";
        AdminOperationService.OperationTypeId operationTypeId = AdminOperationService.OperationTypeId.SYNC_REFERRAL_SOURCE;
        Timestamp testStartTime = nowTime();
        try {
            int operationId = AdminOperationRecord.createOperationRecord(operationTypeId, userId);

            AdminOperationResponse.AdminOperationResult result = AdminOperationRecord.getOperationRecord(operationId);
            Assert.assertEquals(operationTypeId.name(), result.getOperationTypeId());
            Assert.assertEquals(userId, result.getOperatorId());
            Timestamp opStart = result.getOperationStart();
            Assert.assertTrue(opStart.after(testStartTime) && opStart.before(nowTime()));
            Assert.assertNull(result.getOperationEnd());
            Assert.assertEquals(AdminOperationRecord.OperationStatus.STARTED.name(), result.getStatus());
            Assert.assertNull(result.getResults());

            String opResults = "results";
            AdminOperationRecord.updateOperationRecord(operationId, AdminOperationRecord.OperationStatus.COMPLETED, opResults);
            result = AdminOperationRecord.getOperationRecord(operationId);

            Assert.assertEquals(operationTypeId.name(), result.getOperationTypeId());
            Assert.assertEquals(userId, result.getOperatorId());
            Assert.assertEquals(opStart, result.getOperationStart());
            Timestamp opEnd = result.getOperationEnd();
            Assert.assertTrue(opEnd.after(opStart) && opEnd.before(nowTime()));
            Assert.assertEquals(AdminOperationRecord.OperationStatus.COMPLETED.name(), result.getStatus());
            Assert.assertEquals(opResults, result.getResults());

            int operationId2 = AdminOperationRecord.createOperationRecord(operationTypeId, userId);

            List<AdminOperationResponse.AdminOperationResult> results =
                    AdminOperationRecord.getOperationTypeRecords(operationTypeId.name());
            Assert.assertEquals(2, results.size());

            for (var res: results) {
                if (res.getOperationId() == operationId) {
                    Assert.assertEquals(AdminOperationRecord.OperationStatus.COMPLETED.name(), result.getStatus());
                } else {
                    Assert.assertEquals(AdminOperationRecord.OperationStatus.STARTED.name(), result.getStatus());
                }
            }
        } catch (Exception e) {
            Assert.fail("Unexpected exception in operationRecordTest");
            e.printStackTrace();
        }
    }

    private static Timestamp nowTime() {
        Date date = new Date();
        return new Timestamp(date.getTime());
    }
}
