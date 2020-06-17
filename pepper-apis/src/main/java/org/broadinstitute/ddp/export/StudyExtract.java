package org.broadinstitute.ddp.export;

import java.util.List;
import java.util.Map;

import org.broadinstitute.ddp.db.dto.InvitationDto;
import org.broadinstitute.ddp.model.pdf.PdfConfigInfo;
import org.broadinstitute.ddp.model.pdf.PdfVersion;

public class StudyExtract {

    private List<ActivityExtract> activities;
    private List<PdfConfigInfo> studyPdfConfigs;
    private Map<Long, List<PdfVersion>> pdfVersions;
    private Map<String, List<String>> participantProxyGuids; //<participantGuid, List<proxyGuid>>
    private List<InvitationDto> invitations;

    public StudyExtract(List<ActivityExtract> activities,
                        List<PdfConfigInfo> studyPdfConfigs,
                        Map<Long, List<PdfVersion>> pdfVersions,
                        Map<String, List<String>> participantProxyGuids,
                        List<InvitationDto> invitations) {
        this.activities = activities;
        this.studyPdfConfigs = studyPdfConfigs;
        this.pdfVersions = pdfVersions;
        this.participantProxyGuids = participantProxyGuids;
        this.invitations = invitations;
    }

    public List<PdfConfigInfo> getStudyPdfConfigs() {
        return studyPdfConfigs;
    }

    public Map<Long, List<PdfVersion>> getPdfVersions() {
        return pdfVersions;
    }

    public List<ActivityExtract> getActivities() {
        return activities;
    }

    public Map<String, List<String>> getParticipantProxyGuids() {
        return participantProxyGuids;
    }

    public List<InvitationDto> getInvitations() {
        return invitations;
    }
}
