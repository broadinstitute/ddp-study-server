package org.broadinstitute.ddp.db.dao;

import static org.junit.Assert.assertEquals;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dto.EnrollmentStatusDto;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.mapper.reflect.ConstructorMapper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class JdbiUserStudyEnrollmentTest extends TxnAwareBaseTest {

    private static TestDataSetupUtil.GeneratedTestData testData;
    private static long userStudyEnrollmentId;

    private static void setStatus(Handle handle, EnrollmentStatusType status, long timestamp) {
        handle.attach(JdbiUserStudyEnrollment.class).changeUserStudyEnrollmentStatus(
                testData.getUserGuid(),
                testData.getStudyGuid(),
                status,
                timestamp
        );
    }

    private static long setStatus(Handle handle, EnrollmentStatusType status) {
        long timestamp = Instant.now().toEpochMilli();
        setStatus(handle, status, timestamp);
        return timestamp;
    }

    @Before
    public void setup() {
        TransactionWrapper.useTxn(
                handle -> {
                    testData = TestDataSetupUtil.generateBasicUserTestData(handle);
                    userStudyEnrollmentId = handle.attach(JdbiUserStudyEnrollment.class).changeUserStudyEnrollmentStatus(
                            testData.getUserGuid(),
                            testData.getStudyGuid(),
                            EnrollmentStatusType.ENROLLED
                    );
                }
        );
    }

    @Test
    public void testFindIdByUserAndStudyGuid() {
        TransactionWrapper.useTxn(
                handle -> {
                    Optional<Long> id = handle.attach(JdbiUserStudyEnrollment.class).findIdByUserAndStudyGuid(
                            testData.getUserGuid(),
                            testData.getStudyGuid()
                    );
                    Assert.assertTrue(id.isPresent());
                    assertEquals(id.get().longValue(), userStudyEnrollmentId);

                    handle.rollback();
                }
        );
    }

    @Test
    public void testUpdateStatusByUserGuidAndStudyGuid() {
        TransactionWrapper.useTxn(
                handle -> {
                    setStatus(handle, EnrollmentStatusType.EXITED_BEFORE_ENROLLMENT);
                    Optional<EnrollmentStatusType> newStatus = handle.attach(JdbiUserStudyEnrollment.class)
                            .getEnrollmentStatusByUserAndStudyGuids(
                                    testData.getUserGuid(),
                                    testData.getStudyGuid()
                            );
                    Assert.assertTrue(newStatus.isPresent());
                    assertEquals(EnrollmentStatusType.EXITED_BEFORE_ENROLLMENT, newStatus.get());
                    setStatus(handle, EnrollmentStatusType.ENROLLED);

                    handle.rollback();
                }
        );
    }

    @Test
    public void testGetEnrollmentStatusByUserGuidAndStudyGuid() {
        TransactionWrapper.useTxn(
                handle -> {
                    Optional<EnrollmentStatusType> newStatus = handle.attach(JdbiUserStudyEnrollment.class)
                            .getEnrollmentStatusByUserAndStudyGuids(
                                    testData.getUserGuid(),
                                    testData.getStudyGuid()
                            );
                    Assert.assertTrue(newStatus.isPresent());
                    assertEquals(EnrollmentStatusType.ENROLLED, newStatus.get());

                    handle.rollback();
                }
        );
    }

    @Test
    public void testGetFullLedgerForUserForEntireEnrollmentCycle() {
        TransactionWrapper.useTxn(
                handle -> {
                    JdbiUserStudyEnrollment jdbiUserStudyEnrollment = handle.attach(JdbiUserStudyEnrollment.class);

                    // clear all previous enrollments
                    jdbiUserStudyEnrollment.deleteByUserGuidStudyGuid(testData.getUserGuid(), testData.getStudyGuid());

                    long registeredTimestamp = setStatus(handle, EnrollmentStatusType.REGISTERED);
                    long enrolledTimestamp = setStatus(handle, EnrollmentStatusType.ENROLLED);
                    long exitedAfterEnrollmentTimestamp = setStatus(handle, EnrollmentStatusType.EXITED_AFTER_ENROLLMENT);

                    List<EnrollmentStatusDto> dtos = new ArrayList<>();
                    handle.createQuery("SELECT usen.user_study_enrollment_id, usen.user_id, user.guid as user_guid, usen.study_id,"
                            + " us.guid as study_guid, est.enrollment_status_type_code as enrollment_status,"
                            + " usen.valid_from as valid_from_millis, usen.valid_to as valid_to_millis"
                            + " FROM user_study_enrollment usen "
                            + " JOIN enrollment_status_type est on usen.enrollment_status_type_id = est.enrollment_status_type_id "
                            + " JOIN user ON usen.user_id = user.user_id "
                            + " JOIN umbrella_study us ON us.umbrella_study_id = usen.study_id "
                            + " WHERE "
                            + " us.guid = :studyGuid"
                            + " AND user.guid = :userGuid")
                            .bind("userGuid", testData.getUserGuid())
                            .bind("studyGuid", testData.getStudyGuid())
                            .registerRowMapper(ConstructorMapper.factory(EnrollmentStatusDto.class))
                            .mapTo(EnrollmentStatusDto.class)
                            .forEach(dtos::add);

                    assertEquals(3, dtos.size());

                    assertLedgerRowValid(EnrollmentStatusType.REGISTERED,
                            registeredTimestamp,
                            enrolledTimestamp,
                            dtos.get(0));

                    assertLedgerRowValid(EnrollmentStatusType.ENROLLED,
                            enrolledTimestamp,
                            exitedAfterEnrollmentTimestamp,
                            dtos.get(1));

                    assertLedgerRowValid(EnrollmentStatusType.EXITED_AFTER_ENROLLMENT,
                            exitedAfterEnrollmentTimestamp,
                            null,
                            dtos.get(2));

                    handle.rollback();
                }
        );
    }

    private void assertLedgerRowValid(EnrollmentStatusType expectedEnrollmentStatusType,
                                      long expectedValidFrom,
                                      Long expectedValidTo,
                                      EnrollmentStatusDto returnedDto) {
        assertEquals(expectedEnrollmentStatusType, returnedDto.getEnrollmentStatus());
        assertEquals(expectedValidFrom, returnedDto.getValidFromMillis());
        assertEquals(expectedValidTo, returnedDto.getValidToMillis());
    }

}
