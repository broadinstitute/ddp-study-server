package org.broadinstitute.ddp.db.dto.validation;

import static org.broadinstitute.ddp.constants.SqlConstants.NumOptionsSelectedValidationTable;
import static org.broadinstitute.ddp.constants.SqlConstants.ValidationTable;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

public class NumOptionsSelectedDto {


    private long id;
    private int minSelections;
    private int maxSelections;

    public NumOptionsSelectedDto(long id, int minSelections, int maxSelections) {
        this.id = id;
        this.minSelections = minSelections;
        this.maxSelections = maxSelections;
    }

    public int getMinSelections() {
        return minSelections;
    }

    public int getMaxSelections() {
        return maxSelections;
    }

    public long getId() {
        return id;
    }

    public static class NumOptionsSelectedMapper implements RowMapper<NumOptionsSelectedDto> {
        @Override
        public NumOptionsSelectedDto map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new NumOptionsSelectedDto(
                    rs.getLong(ValidationTable.ID),
                    rs.getInt(NumOptionsSelectedValidationTable.MIN_SELECTIONS),
                    rs.getInt(NumOptionsSelectedValidationTable.MAX_SELECTIONS));
        }


    }
}
