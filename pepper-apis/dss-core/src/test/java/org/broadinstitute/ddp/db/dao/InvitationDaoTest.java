package org.broadinstitute.ddp.db.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dto.InvitationDto;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.junit.BeforeClass;
import org.junit.Test;

public class InvitationDaoTest extends TxnAwareBaseTest {

    private static TestDataSetupUtil.GeneratedTestData testData;
    private static long studyId;
    private static long userId;

    @BeforeClass
    public static void setup() {
        TransactionWrapper.useTxn(handle -> {
            testData = TestDataSetupUtil.generateBasicUserTestData(handle);
            studyId = testData.getStudyId();
            userId = testData.getUserId();
        });
    }

    @Test
    public void testInsertUpdateAndRead() {
        TransactionWrapper.useTxn(handle -> {
            String email = "test" + System.currentTimeMillis() + "@datadonationplatform.org";

            // create a new one
            InvitationDao invitationDao = handle.attach(InvitationDao.class);
            InvitationDto invitation = handle.attach(InvitationFactory.class).createAgeUpInvitation(
                    studyId, userId, email);

            // check some generated values
            assertTrue(invitation.getCreatedAt().isBefore(Instant.now()));

            // set various dates
            Instant createdAt = invitation.getCreatedAt();
            Instant acceptedAt = createdAt.plus(2 * 10000, ChronoUnit.MILLIS);
            Instant verifiedAt = createdAt.plus(4 * 10000, ChronoUnit.MILLIS);
            Instant voidedAt = createdAt.plus(6 * 10000, ChronoUnit.MILLIS);

            invitationDao.markAccepted(invitation.getInvitationId(), acceptedAt);
            invitationDao.markVerified(invitation.getInvitationId(), verifiedAt);
            invitationDao.markVoided(invitation.getInvitationId(), voidedAt);

            // requery and verify
            InvitationDto requeriedInvitation = invitationDao.findByInvitationGuid(studyId, invitation.getInvitationGuid()).get();

            assertEquals(createdAt, requeriedInvitation.getCreatedAt());
            assertEquals(acceptedAt, requeriedInvitation.getAcceptedAt());
            assertEquals(verifiedAt, requeriedInvitation.getVerifiedAt());
            assertEquals(voidedAt, requeriedInvitation.getVoidedAt());
            assertEquals(invitation.getInvitationGuid(), requeriedInvitation.getInvitationGuid());
            assertEquals(email, requeriedInvitation.getContactEmail());

            handle.rollback();
        });
    }

    @Test
    public void testRecruitmentInvitations() {
        TransactionWrapper.useTxn(handle -> {
            var dao = handle.attach(InvitationDao.class);
            var factory = handle.attach(InvitationFactory.class);

            var invitation = factory.createRecruitmentInvitation(studyId, "foobar");
            assertNotNull(invitation);
            assertNull(invitation.getUserId());
            assertEquals("foobar", invitation.getInvitationGuid());

            Instant now = Instant.now();
            dao.assignAcceptingUser(invitation.getInvitationId(), userId, now);

            var updated = dao.findByInvitationGuid(studyId, invitation.getInvitationGuid()).orElse(null);
            assertNotNull(updated);
            assertEquals((Long) userId, updated.getUserId());
            assertEquals(now, updated.getAcceptedAt());

            handle.rollback();
        });
    }
}
