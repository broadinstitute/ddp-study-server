package org.broadinstitute.ddp.script;

import java.util.stream.IntStream;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.InvitationFactory;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dto.InvitationDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.util.GuidUtils;
import org.jdbi.v3.core.Handle;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Script for generating and inserting invite codes.
 */
@Ignore
public class InsertInvitationRecruitmentCodesScript extends TxnAwareBaseTest {

    private static final Logger LOG = LoggerFactory.getLogger(InsertInvitationRecruitmentCodesScript.class);

    private String generateRecruitmentInvitationCode(String codePrefix) {
        return GuidUtils.randomWithPrefix(codePrefix, GuidUtils.UPPER_ALPHA_NUMERIC_EXCLUDING_CONFUSING_CHAR, 12);
    }

    private InvitationDto insertRecruitmentInvitationCode(String invitationGuid, StudyDto study, Handle handle) {
        return handle.attach(InvitationFactory.class).createRecruitmentInvitation(study.getId(), invitationGuid);
    }

    private void insertRecruitmentInvitationCodes(String studyGuid, String codePrefix, int qty) {
        TransactionWrapper.useTxn(TransactionWrapper.DB.APIS, handle -> {
            StudyDto study = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);
            IntStream.range(0, qty).forEach(i -> {
                String newGuid = generateRecruitmentInvitationCode(codePrefix);
                InvitationDto invitation = insertRecruitmentInvitationCode(newGuid, study, handle);
                LOG.info("Invitation code: {} inserted with id {}. {} left", newGuid, invitation.getInvitationId(), (qty - 1 - i));
            });
        });
    }
    @Test
    public void generateTestBostonInvitationCodes() {
        insertRecruitmentInvitationCodes("testboston", "TB", 100);
    }
}
