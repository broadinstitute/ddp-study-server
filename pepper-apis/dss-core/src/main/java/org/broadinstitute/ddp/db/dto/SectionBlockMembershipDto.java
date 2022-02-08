package org.broadinstitute.ddp.db.dto;

import static org.broadinstitute.ddp.constants.SqlConstants.FormSectionBlockTable;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

public class SectionBlockMembershipDto {

    private long id;
    private long sectionId;
    private long blockId;
    private int displayOrder;
    private long revisionId;

    public SectionBlockMembershipDto(long id, long sectionId, long blockId, int displayOrder, long revisionId) {
        this.id = id;
        this.sectionId = sectionId;
        this.blockId = blockId;
        this.displayOrder = displayOrder;
        this.revisionId = revisionId;
    }

    public long getId() {
        return id;
    }

    public long getSectionId() {
        return sectionId;
    }

    public long getBlockId() {
        return blockId;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    public long getRevisionId() {
        return revisionId;
    }

    public static class SectionBlockMembershipDtoMapper implements RowMapper<SectionBlockMembershipDto> {
        @Override
        public SectionBlockMembershipDto map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new SectionBlockMembershipDto(
                    rs.getLong(FormSectionBlockTable.ID),
                    rs.getLong(FormSectionBlockTable.SECTION_ID),
                    rs.getLong(FormSectionBlockTable.BLOCK_ID),
                    rs.getInt(FormSectionBlockTable.DISPLAY_ORDER),
                    rs.getLong(FormSectionBlockTable.REVISION_ID));
        }
    }
}
