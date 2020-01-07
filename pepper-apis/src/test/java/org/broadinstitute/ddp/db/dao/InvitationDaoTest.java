package org.broadinstitute.ddp.db.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.sql.Timestamp;
import java.time.Instant;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dto.InvitationDto;
import org.broadinstitute.ddp.model.invitation.InvitationType;
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
            InvitationDto invitation = handle.attach(InvitationFactory.class).createInvitation(InvitationType.AGE_UP,
                    studyId, userId, email);

            // check some generated values
            assertNotNull(invitation.getInvitationId());
            assertTrue(invitation.getCreatedAt().before(new Timestamp(Instant.now().toEpochMilli())));

            // set various dates
            Timestamp createdAt = invitation.getCreatedAt();
            Timestamp acceptedAt = new Timestamp(createdAt.getTime() + (2 * 10000));
            Timestamp verifiedAt = new Timestamp(createdAt.getTime() + (4 * 10000));
            Timestamp voidedAt = new Timestamp(createdAt.getTime() + (6 * 10000));

            invitationDao.updateAcceptedAt(acceptedAt, invitation.getInvitationGuid());
            invitationDao.updateVerifiedAt(verifiedAt, invitation.getInvitationGuid());
            invitationDao.updateVoidedAt(voidedAt, invitation.getInvitationGuid());

            // requery and verify
            InvitationDto requeriedInvitation = invitationDao.findByInvitationGuid(invitation.getInvitationGuid()).get();

            assertEquals(createdAt, requeriedInvitation.getCreatedAt());
            assertEquals(acceptedAt, requeriedInvitation.getAcceptedAt());
            assertEquals(verifiedAt, requeriedInvitation.getVerifiedAt());
            assertEquals(voidedAt, requeriedInvitation.getVoidedAt());
            assertEquals(invitation.getInvitationGuid(), requeriedInvitation.getInvitationGuid());
            assertEquals(email, requeriedInvitation.getContactEmail());
        });
    }
}
