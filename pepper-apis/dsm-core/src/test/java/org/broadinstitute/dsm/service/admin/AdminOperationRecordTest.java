package org.broadinstitute.dsm.service.admin;

import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.DbTxnBaseTest;
import org.junit.Assert;
import org.junit.Test;

@Slf4j
public class AdminOperationRecordTest extends DbTxnBaseTest {

    @Test
    public void operationRecordTest() {
        String userId = "test_user";
        AdminOperationService.OperationTypeId operationTypeId = AdminOperationService.OperationTypeId.SYNC_REFERRAL_SOURCE;
        Timestamp testStartTime = secondsOffsetNowTime(-2);
        try {
            int operationId = AdminOperationRecord.createOperationRecord(operationTypeId, userId);

            AdminOperationResponse.AdminOperationResult result = AdminOperationRecord.getOperationRecord(operationId);
            Assert.assertEquals(operationTypeId.name(), result.getOperationTypeId());
            Assert.assertEquals(userId, result.getOperatorId());
            Timestamp opStart = result.getOperationStart();
            log.info("operation start time: {}, now time {}, test start time {}", opStart, secondsOffsetNowTime(2), testStartTime);
            Assert.assertTrue(opStart.after(testStartTime) && opStart.before(secondsOffsetNowTime(2)));
            Assert.assertNull(result.getOperationEnd());
            Assert.assertEquals(AdminOperationRecord.OperationStatus.STARTED.name(), result.getStatus());
            Assert.assertNull(result.getResults());

            String opResults = "results";
            AdminOperationRecord.updateOperationRecord(operationId, AdminOperationRecord.OperationStatus.COMPLETED, opResults);
            result = AdminOperationRecord.getOperationRecord(operationId);

            Assert.assertEquals(operationTypeId.name(), result.getOperationTypeId());
            Assert.assertEquals(userId, result.getOperatorId());
            Timestamp opEnd = result.getOperationEnd();
            log.info("operation end time: {}, now time {}, operation start time {}", opEnd, secondsOffsetNowTime(2), opStart);
            Assert.assertTrue((opEnd.after(opStart) || opEnd.equals(opStart)) && opEnd.before(secondsOffsetNowTime(2)));
            Assert.assertEquals(AdminOperationRecord.OperationStatus.COMPLETED.name(), result.getStatus());
            Assert.assertEquals(opResults, result.getResults());

            int operationId2 = AdminOperationRecord.createOperationRecord(operationTypeId, userId);

            List<AdminOperationResponse.AdminOperationResult> results =
                    AdminOperationRecord.getOperationTypeRecords(operationTypeId.name());
            Assert.assertEquals(2, results.size());

            for (var res: results) {
                if (res.getOperationId() == operationId) {
                    Assert.assertEquals(AdminOperationRecord.OperationStatus.COMPLETED.name(), res.getStatus());
                } else {
                    Assert.assertEquals(operationId2, res.getOperationId());
                    Assert.assertEquals(AdminOperationRecord.OperationStatus.STARTED.name(), res.getStatus());
                }
            }
        } catch (Exception e) {
            Assert.fail("Unexpected exception in operationRecordTest");
            e.printStackTrace();
        }
    }

    private static Timestamp secondsOffsetNowTime(int seconds) {
        long millis = System.currentTimeMillis();
        if (seconds >= 0) {
            millis += TimeUnit.SECONDS.toMillis(seconds);
        } else {
            millis -= TimeUnit.SECONDS.toMillis(-seconds);
        }
        Date date = new Date(millis);
        return new Timestamp(date.getTime());
    }
}
