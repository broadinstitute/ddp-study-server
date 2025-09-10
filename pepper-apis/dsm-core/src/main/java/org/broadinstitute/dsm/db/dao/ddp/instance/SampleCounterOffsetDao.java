package org.broadinstitute.dsm.db.dao.ddp.instance;

import org.broadinstitute.dsm.db.SampleCounterOffset;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.statics.DBConstants;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.broadinstitute.dsm.statics.DBConstants.KIT_COUNTER_OFFSET;
import static org.broadinstitute.dsm.statics.DBConstants.LEGACY_COLLABORATOR_PARTICIPANT_ID;
import static org.broadinstitute.dsm.statics.DBConstants.PARTICIPANT_SHORT_CODE;

public class SampleCounterOffsetDao {

    private static final String QUERY_ALL_OFFSETS_FOR_INSTANCE =
            "select o.participant_short_code, o.legacy_collaborator_participant_id, o.kit_type_id, o.offset "
            + "from sample_counter_offset o, ddp_instance i, kit_type kt "
            + "where o.ddp_instance_id = i.ddp_instance_id and kt.kit_type_id = o.kit_type_id "
            + "and i.ddp_instance_id = ?";

    public static List<SampleCounterOffset> querySampleCounterOffsetsForInstance(Connection conn, int ddpInstanceId) throws SQLException {
        final List<SampleCounterOffset> offsets = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(QUERY_ALL_OFFSETS_FOR_INSTANCE)) {
            stmt.setInt(1, ddpInstanceId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String participantShortCode = rs.getString(PARTICIPANT_SHORT_CODE);
                    String collaboratorParticipantId = rs.getString(LEGACY_COLLABORATOR_PARTICIPANT_ID);
                    Integer kitTypeId = rs.getInt(DBConstants.KIT_TYPE_ID);
                    Integer offset = rs.getInt(KIT_COUNTER_OFFSET);

                    SampleCounterOffset offsetForShortCode = getOffsetForShortCode(participantShortCode, offsets);
                    if (offsetForShortCode == null) {
                        Map<Integer, Integer> offsetsByKitTypeId = new HashMap<>();
                        offsetsByKitTypeId.put(kitTypeId, offset);
                        offsetForShortCode = new SampleCounterOffset(participantShortCode, collaboratorParticipantId, offsetsByKitTypeId);
                        offsets.add(offsetForShortCode);
                    }
                    offsetForShortCode.setSampleCounterOffsetForKitTypeId(kitTypeId, offset);
                }
            }
        }
        return offsets;
    }

    /**
     * Return the counter offset for the given shortcode in the given
     * list of shortcodes
     */
    public static SampleCounterOffset getOffsetForShortCode(String participantShortCode, List<SampleCounterOffset> offsets) {
        SampleCounterOffset foundOffset = null;
        for (SampleCounterOffset offset : offsets) {
            if (participantShortCode.equals(offset.getShortId())) {
                if (foundOffset != null) {
                    throw new DsmInternalError("Found multiple sample counter offsets for " + participantShortCode);
                }
                foundOffset = offset;
            }
        }
        return foundOffset;
    }

}
