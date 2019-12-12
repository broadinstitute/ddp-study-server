package org.broadinstitute.ddp.db.dto;

import static org.broadinstitute.ddp.constants.SqlConstants.BlockGroupHeaderTable;
import static org.broadinstitute.ddp.constants.SqlConstants.ListStyleHintTable;
import static org.broadinstitute.ddp.constants.SqlConstants.PresentationHintTable;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.broadinstitute.ddp.model.activity.types.ListStyleHint;
import org.broadinstitute.ddp.model.activity.types.PresentationHint;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

public class BlockGroupHeaderDto {

    private long id;
    private long blockId;
    private ListStyleHint listStyleHint;
    private PresentationHint presentationHint;
    private Long titleTemplateId;
    private long revisionId;

    public BlockGroupHeaderDto(long id, long blockId, ListStyleHint listStyleHint, PresentationHint presentationHint,
                               Long titleTemplateId, long revisionId) {
        this.id = id;
        this.blockId = blockId;
        this.listStyleHint = listStyleHint;
        this.presentationHint = presentationHint;
        this.titleTemplateId = titleTemplateId;
        this.revisionId = revisionId;
    }

    public long getId() {
        return id;
    }

    public long getBlockId() {
        return blockId;
    }

    public ListStyleHint getListStyleHint() {
        return listStyleHint;
    }

    public PresentationHint getPresentationHint() {
        return presentationHint;
    }

    public Long getTitleTemplateId() {
        return titleTemplateId;
    }

    public long getRevisionId() {
        return revisionId;
    }

    public static class BlockGroupHeaderDtoMapper implements RowMapper<BlockGroupHeaderDto> {
        @Override
        public BlockGroupHeaderDto map(ResultSet rs, StatementContext ctx) throws SQLException {
            String listStyleHintCode = rs.getString(ListStyleHintTable.CODE);
            ListStyleHint hint = listStyleHintCode == null ? null : ListStyleHint.valueOf(listStyleHintCode);
            PresentationHint presentationHint = PresentationHint.valueOf(rs.getString(PresentationHintTable.CODE));
            return new BlockGroupHeaderDto(
                    rs.getLong(BlockGroupHeaderTable.ID),
                    rs.getLong(BlockGroupHeaderTable.BLOCK_ID),
                    hint,
                    presentationHint,
                    (Long) rs.getObject(BlockGroupHeaderTable.TITLE_TEMPLATE_ID),
                    rs.getLong(BlockGroupHeaderTable.REVISION_ID));
        }
    }
}
