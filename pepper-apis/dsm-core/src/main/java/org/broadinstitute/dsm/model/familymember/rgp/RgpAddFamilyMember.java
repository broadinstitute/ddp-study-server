package org.broadinstitute.dsm.model.familymember.rgp;

import org.broadinstitute.dsm.model.familymember.AddFamilyMember;
import org.broadinstitute.dsm.model.participant.data.AddFamilyMemberPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RgpAddFamilyMember extends AddFamilyMember {

    private static final Logger logger = LoggerFactory.getLogger(RgpAddFamilyMember.class);

    public RgpAddFamilyMember(AddFamilyMemberPayload addFamilyMemberPayload) {
        super(addFamilyMemberPayload);
    }

    @Override
    public void exportDataToEs() {
        boolean isCopyProband = addFamilyMemberPayload.getCopyProbandInfo().orElse(Boolean.FALSE);
        if (!isCopyProband) return;
        exportProbandDataForFamilyMemberToEs();
    }

    @Override
    protected void exportProbandDataForFamilyMemberToEs() {
        if(!participantData.hasFamilyMemberApplicantEmail()) return;
        super.exportProbandDataForFamilyMemberToEs();
    }
}
