package org.broadinstitute.dsm.service.participantdata;

import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.model.bookmark.Bookmark;

public class RgpFamilyIdProvider implements FamilyIdProvider {
    public static final String RGP_FAMILY_ID = "rgp_family_id";
    public final Bookmark bookmark;

    public RgpFamilyIdProvider() {
        this.bookmark = new Bookmark();
    }

    public RgpFamilyIdProvider(Bookmark bookmark) {
        this.bookmark = bookmark;
    }

    public long createFamilyId(String ddpParticipantId) {
        try {
            return bookmark.getThenIncrementBookmarkValue(RGP_FAMILY_ID);
        } catch (Exception e) {
            throw new DsmInternalError("Could not create family ID for participant " + ddpParticipantId, e);
        }
    }
}
