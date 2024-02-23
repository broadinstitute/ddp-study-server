package org.broadinstitute.dsm.db.dto.onchistory;

import static org.broadinstitute.dsm.db.dao.ddp.onchistory.OncHistoryDao.ONC_HISTORY_CHANGED_BY;
import static org.broadinstitute.dsm.db.dao.ddp.onchistory.OncHistoryDao.ONC_HISTORY_LAST_CHANGED;

import lombok.Data;
import org.broadinstitute.dsm.db.structure.ColumnName;
import org.broadinstitute.dsm.db.structure.TableName;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.SystemUtil;

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

    @ColumnName(ONC_HISTORY_LAST_CHANGED)
    private long lastChanged;

    @ColumnName(ONC_HISTORY_CHANGED_BY)
    private String changedBy;

    public OncHistoryDto(int oncHistoryId, int participantId, String created, String reviewed, long lastChanged) {
        this.oncHistoryId = oncHistoryId;
        this.participantId = participantId;
        this.created = created;
        this.reviewed = reviewed;
        this.lastChanged = lastChanged;
    }

    public OncHistoryDto(Builder builder) {
        this.oncHistoryId = builder.oncHistoryId;
        this.participantId = builder.participantId;
        this.created = builder.created;
        this.reviewed = builder.reviewed;
        this.lastChanged = builder.lastChanged;
        this.changedBy = builder.changedBy;
    }

    public static class Builder {
        int oncHistoryId;
        int participantId;
        String created;
        String reviewed;
        long lastChanged;
        String changedBy;

        public OncHistoryDto.Builder withOncHistoryId(int oncHistoryId) {
            this.oncHistoryId = oncHistoryId;
            return this;
        }

        public OncHistoryDto.Builder withParticipantId(Integer participantId) {
            this.participantId = participantId;
            return this;
        }

        public OncHistoryDto.Builder withCreated(String created) {
            this.created = created;
            return this;
        }

        public OncHistoryDto.Builder withCreatedNow() {
            this.created = SystemUtil.getDateFormatted(System.currentTimeMillis());
            return this;
        }

        public OncHistoryDto.Builder withReviewed(String reviewed) {
            this.reviewed = reviewed;
            return this;
        }

        public OncHistoryDto.Builder withLastChanged(long lastChanged) {
            this.lastChanged = lastChanged;
            return this;
        }

        public OncHistoryDto.Builder withLastChangedNow() {
            this.lastChanged = System.currentTimeMillis();
            return this;
        }

        public OncHistoryDto.Builder withChangedBy(String changedBy) {
            this.changedBy = changedBy;
            return this;
        }

        public OncHistoryDto build() {
            return new OncHistoryDto(this);
        }
    }
}
