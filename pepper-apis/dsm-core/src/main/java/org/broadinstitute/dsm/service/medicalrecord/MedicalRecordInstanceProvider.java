package org.broadinstitute.dsm.service.medicalrecord;

import java.sql.Connection;
import java.util.List;
import java.util.Optional;

import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.dao.bookmark.BookmarkDao;
import org.broadinstitute.dsm.db.dto.bookmark.BookmarkDto;
import org.broadinstitute.dsm.service.participant.OsteoParticipantService;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.DBUtil;

public class MedicalRecordInstanceProvider implements DDPInstanceProvider {
    protected static final BookmarkDao bookmarkDao = new BookmarkDao();

    /**
     * Return all DDP instances that have medical records
     */
    @Override
    public List<DDPInstance> getApplicableInstances() {
        return DDPInstance.getDDPInstanceListWithRole(DBConstants.HAS_MEDICAL_RECORD_ENDPOINTS).stream()
                .filter(DDPInstance::isHasRole).toList();
    }

    /**
     * If the provided instance is an osteo instance and the participant is only in osteo1,
     * return the osteo1 instance
     */
    @Override
    public DDPInstance getEffectiveInstance(DDPInstance ddpInstance, String ddpParticipantId) {
        OsteoParticipantService osteoParticipantService = new OsteoParticipantService();
        if (osteoParticipantService.isOsteoInstance(ddpInstance)
                && osteoParticipantService.isOnlyOsteo1Participant(ddpParticipantId)) {
            return osteoParticipantService.getOsteo1Instance();
        }
        return ddpInstance;
    }

    @Override
    public Optional<Long> getInstanceSequenceNumber(DDPInstance ddpInstance) {
        // bookmark keeps the last sequence number processed
        Optional<BookmarkDto> bookmark = bookmarkDao.getBookmarkByInstance(ddpInstance.getDdpInstanceId());
        return bookmark.map(BookmarkDto::getValue);
    }

    @Override
    public Long getInstanceSequenceNumber(DDPInstance ddpInstance, Connection conn) {
        // bookmark keeps the last sequence number processed
        return DBUtil.getBookmark(conn, ddpInstance.getDdpInstanceId());
    }

    @Override
    public void updateInstanceSequenceNumber(DDPInstance ddpInstance, long sequenceNumber, Connection conn) {
        DBUtil.updateBookmark(conn, sequenceNumber, ddpInstance.getDdpInstanceId());
    }
}
