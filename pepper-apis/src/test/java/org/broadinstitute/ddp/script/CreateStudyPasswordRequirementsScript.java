package org.broadinstitute.ddp.script;

import java.util.List;
import java.util.stream.Collectors;

import com.google.gson.Gson;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiStudyPasswordRequirements;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.StudyPasswordRequirementsDto;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Ignore
public class CreateStudyPasswordRequirementsScript extends TxnAwareBaseTest {

    private static final Logger LOG = LoggerFactory.getLogger(CreateStudyPasswordRequirementsScript.class);

    @Ignore
    @Test
    public void createStudyPasswordRequirements() {
        List<StudyDto> studies = TransactionWrapper.withTxn(handle -> handle.attach(JdbiUmbrellaStudy.class).findAll());
        List<StudyPasswordRequirementsDto> passwdReqs = studies.stream().map(
                // Setting requirements according to the Auth0 dashboard
                study -> new StudyPasswordRequirementsDto(
                    study.getAuth0TenantId(),
                    8,
                    true,
                    true,
                    false,
                    true,
                    2
                )
        ).collect(Collectors.toList());
        TransactionWrapper.useTxn(
                handle -> {
                    int totalPasswordReqInserted = 0;
                    JdbiStudyPasswordRequirements dao = handle.attach(JdbiStudyPasswordRequirements.class);
                    for (StudyPasswordRequirementsDto req: passwdReqs) {
                        int numInsertedPasswdReqRows = dao.insert(
                                req.getAuth0TenantId(),
                                req.getMinLength(),
                                req.isUppercaseLetterRequired(),
                                req.isLowercaseLetterRequired(),
                                req.isSpecialCharacterRequired(),
                                req.isNumberRequired(),
                                req.getMaxIdenticalConsecutiveCharacters()
                        );
                        totalPasswordReqInserted += numInsertedPasswdReqRows;
                        String reqJson = new Gson().toJson(req);
                        LOG.info(
                                "Successfully created a new password requirement for the study with the tenantId "
                                + req.getAuth0TenantId() + ": " + reqJson
                        );
                    }
                    String errMsg = "The number of inserted password requirements doesn't match the number of studies";
                    Assert.assertEquals(errMsg, studies.size(), totalPasswordReqInserted);
                }
        );
    }
}
