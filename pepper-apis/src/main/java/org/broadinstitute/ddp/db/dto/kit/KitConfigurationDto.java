package org.broadinstitute.ddp.db.dto.kit;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.broadinstitute.ddp.constants.SqlConstants.KitConfigurationTable;
import org.broadinstitute.ddp.constants.SqlConstants.KitTypeTable;
import org.broadinstitute.ddp.model.dsm.KitTypes;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

public class KitConfigurationDto {

    private long id;
    private Long studyId;
    private Long numberOfKits;
    private KitTypes kitType;

    public KitConfigurationDto(long id, Long studyId, Long numberOfKits, KitTypes kitType) {
        this.id = id;
        this.studyId = studyId;
        this.numberOfKits = numberOfKits;
        this.kitType = kitType;
    }

    public long getId() {
        return id;
    }

    public Long getStudyId() {
        return studyId;
    }

    public long getNumberOfKits() {
        return numberOfKits;
    }

    public String getKitType() {
        return kitType.toString();
    }

    public static class KitConfigurationDtoMapper implements RowMapper<KitConfigurationDto> {
        @Override
        public KitConfigurationDto map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new KitConfigurationDto(
                    rs.getLong(KitConfigurationTable.KIT_CONFIGURATION_ID),
                    rs.getLong(KitConfigurationTable.STUDY_ID),
                    rs.getLong(KitConfigurationTable.NUMBER_OF_KITS),
                    KitTypes.valueOf(rs.getString(KitTypeTable.NAME)));
        }
    }
}
