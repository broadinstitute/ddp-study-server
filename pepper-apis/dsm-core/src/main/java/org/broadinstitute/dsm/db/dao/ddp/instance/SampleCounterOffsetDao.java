package org.broadinstitute.dsm.db.dao.ddp.instance;

import org.broadinstitute.dsm.db.SampleCounterOffset;
import org.broadinstitute.dsm.exception.DsmInternalError;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SampleCounterOffsetDao {

    private static final String QUERY_ALL_OFFSETS_FOR_INSTANCE =
            "select o.participant_short_code, o.legacy_collaborator_participant_id, o.kit_type_id, o.offset "
            + "from sample_counter_offsets o, ddp_instance i, kit_type kt "
            + "where o.ddp_instance_id = i.ddp_instance_id and kt.kit_type_id = o.kit_type_id "
            + "and i.ddp_instance_id = ?";

    public static List<SampleCounterOffset> querySampleCounterOffsetsForInstance(Connection conn, int ddpInstanceId) throws SQLException {
        final List<SampleCounterOffset> offsets = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(QUERY_ALL_OFFSETS_FOR_INSTANCE)) {
            stmt.setInt(1, ddpInstanceId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String participantShortCode = rs.getString("participant_short_code");
                    String collaboratorParticipantId = rs.getString("collaborator_participant_id");
                    Integer kitTypeId = rs.getInt("kit_type_id");
                    Integer offset = rs.getInt("offset");

                    SampleCounterOffset offsetForShortCode = getOffsetForShortCode(participantShortCode, offsets);
                    if (offsetForShortCode == null) {
                        Map<Integer, Integer> offsetsByKitTypeId = Map.of(kitTypeId, offset);
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
        return null;
    }
}
