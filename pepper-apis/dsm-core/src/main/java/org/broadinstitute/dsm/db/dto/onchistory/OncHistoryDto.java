package org.broadinstitute.dsm.db.dto.onchistory;

import lombok.Data;
import org.broadinstitute.dsm.db.structure.ColumnName;
import org.broadinstitute.dsm.db.structure.TableName;
import org.broadinstitute.dsm.statics.DBConstants;

@Data
@TableName(
        name = DBConstants.DDP_ONC_HISTORY,
        alias = DBConstants.DDP_ONC_HISTORY_ALIAS,
        primaryKey = DBConstants.ONC_HISTORY_ID,
        columnPrefix = "")
public class OncHistoryDto {

    @ColumnName(DBConstants.ONC_HISTORY_ID)
    private int oncHistoryId;

    @ColumnName(DBConstants.PARTICIPANT_ID)
    private int participantId;

    @ColumnName(DBConstants.ONC_HISTORY_CREATED)
    private String created;

    @ColumnName(DBConstants.ONC_HISTORY_REVIEWED)
    private String reviewed;

    public OncHistoryDto(int oncHistoryId, int participantId, String created, String reviewed) {
        this.oncHistoryId = oncHistoryId;
        this.participantId = participantId;
        this.created = created;
        this.reviewed = reviewed;
    }
}
