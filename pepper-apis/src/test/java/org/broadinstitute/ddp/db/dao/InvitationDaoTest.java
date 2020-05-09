package org.broadinstitute.ddp.db.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dto.InvitationDto;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.junit.Test;

public class InvitationDaoTest extends TxnAwareBaseTest {

    @Test
    public void testInsertUpdateAndRead() {
        TransactionWrapper.useTxn(handle -> {
            TestDataSetupUtil.GeneratedTestData generatedTestData = TestDataSetupUtil.generateBasicUserTestData(handle);
            long studyId = generatedTestData.getStudyId();
            long userId = generatedTestData.getUserId();
            String email = "test" + System.currentTimeMillis() + "@datadonationplatform.org";

            // create a new one
            InvitationDao invitationDao = handle.attach(InvitationDao.class);
            InvitationDto invitation = handle.attach(InvitationFactory.class).createAgeUpInvitation(
                    studyId, userId, email);

            // check some generated values
            assertNotNull(invitation.getInvitationId());
            assertTrue(invitation.getCreatedAt().isBefore(Instant.now()));

            // set various dates
            Instant createdAt = invitation.getCreatedAt();
            Instant acceptedAt = createdAt.plus(2 * 10000, ChronoUnit.MILLIS);
            Instant verifiedAt = createdAt.plus(4 * 10000, ChronoUnit.MILLIS);
            Instant voidedAt = createdAt.plus(6 * 10000, ChronoUnit.MILLIS);

            invitationDao.updateAcceptedAt(invitation.getInvitationId(), acceptedAt);
            invitationDao.updateVerifiedAt(invitation.getInvitationId(), verifiedAt);
            invitationDao.updateVoidedAt(invitation.getInvitationId(), voidedAt);

            // requery and verify
            InvitationDto requeriedInvitation = invitationDao.findByInvitationGuid(studyId, invitation.getInvitationGuid()).get();

            assertEquals(createdAt, requeriedInvitation.getCreatedAt());
            assertEquals(acceptedAt, requeriedInvitation.getAcceptedAt());
            assertEquals(verifiedAt, requeriedInvitation.getVerifiedAt());
            assertEquals(voidedAt, requeriedInvitation.getVoidedAt());
            assertEquals(invitation.getInvitationGuid(), requeriedInvitation.getInvitationGuid());
            assertEquals(email, requeriedInvitation.getContactEmail());
        });
    }
}
