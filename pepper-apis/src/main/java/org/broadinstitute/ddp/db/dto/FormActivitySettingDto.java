package org.broadinstitute.ddp.db.dto;

import static org.broadinstitute.ddp.constants.SqlConstants.FormActivitySettingTable;
import static org.broadinstitute.ddp.constants.SqlConstants.ListStyleHintTable;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import org.broadinstitute.ddp.model.activity.types.ListStyleHint;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

public class FormActivitySettingDto {

    private long id;
    private ListStyleHint listStyleHint;
    private Long introductionSectionId;
    private Long closingSectionId;
    private Long readonlyHintTemplateId;
    private Long lastUpdatedTextTemplateId;
    private LocalDateTime lastUpdated;
    private boolean snapshotSubstitutionsOnSubmit;
    private long revisionId;

    public FormActivitySettingDto(long id, ListStyleHint hint, Long introductionSectionId, Long closingSectionId,
                                  Long readonlyHintTemplateId, Long lastUpdatedTextTemplateId, LocalDateTime lastUpdated,
                                  boolean snapshotSubstitutionsOnSubmit, long revisionId) {
        this.id = id;
        this.listStyleHint = hint;
        this.introductionSectionId = introductionSectionId;
        this.closingSectionId = closingSectionId;
        this.readonlyHintTemplateId = readonlyHintTemplateId;
        this.lastUpdatedTextTemplateId = lastUpdatedTextTemplateId;
        this.lastUpdated = lastUpdated;
        this.revisionId = revisionId;
        this.snapshotSubstitutionsOnSubmit = snapshotSubstitutionsOnSubmit;
    }

    public long getId() {
        return id;
    }

    public ListStyleHint getListStyleHint() {
        return listStyleHint;
    }

    public Long getIntroductionSectionId() {
        return introductionSectionId;
    }

    public Long getClosingSectionId() {
        return closingSectionId;
    }

    public Long getReadonlyHintTemplateId() {
        return readonlyHintTemplateId;
    }

    public Long getLastUpdatedTextTemplateId() {
        return lastUpdatedTextTemplateId;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public boolean shouldSnapshotSubstitutionsOnSubmit() {
        return snapshotSubstitutionsOnSubmit;
    }

    public long getRevisionId() {
        return revisionId;
    }

    public static class FormActivitySettingDtoMapper implements RowMapper<FormActivitySettingDto> {
        @Override
        public FormActivitySettingDto map(ResultSet rs, StatementContext ctx) throws SQLException {
            String listStyleHintCode = rs.getString(ListStyleHintTable.CODE);
            ListStyleHint hint = listStyleHintCode == null ? null : ListStyleHint.valueOf(listStyleHintCode);

            Timestamp ts = rs.getTimestamp(FormActivitySettingTable.LAST_UPDATED);
            LocalDateTime lastUpdated = ts == null ? null : ts.toLocalDateTime();

            return new FormActivitySettingDto(
                    rs.getLong(FormActivitySettingTable.ID),
                    hint,
                    (Long) rs.getObject(FormActivitySettingTable.INTRO_SECTION_ID),
                    (Long) rs.getObject(FormActivitySettingTable.CLOSING_SECTION_ID),
                    (Long) rs.getObject(FormActivitySettingTable.READONLY_HINT_TEMPLATE_ID),
                    (Long) rs.getObject(FormActivitySettingTable.LAST_UPDATED_TEXT_TEMPLATE_ID),
                    lastUpdated,
                    rs.getBoolean(FormActivitySettingTable.SNAPSHOT_SUBSTITUTIONS_ON_SUBMIT),
                    rs.getLong(FormActivitySettingTable.REVISION_ID));
        }
    }
}
