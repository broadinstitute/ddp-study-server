package org.broadinstitute.ddp.db.dto.kit;

import static org.broadinstitute.ddp.constants.SqlConstants.KitRuleTable;
import static org.broadinstitute.ddp.constants.SqlConstants.KitRuleTypeTable;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.broadinstitute.ddp.model.kit.KitRuleType;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

public class KitRuleDto {

    private Long id;
    private KitRuleType kitRuleType;

    public KitRuleDto(Long id, KitRuleType kitRuleType) {
        this.id = id;
        this.kitRuleType = kitRuleType;
    }

    public Long getId() {
        return id;
    }

    public KitRuleType getKitRuleType() {
        return kitRuleType;
    }

    public static class KitRuleDtoMapper implements RowMapper<KitRuleDto> {
        @Override
        public KitRuleDto map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new KitRuleDto(
                    rs.getLong(KitRuleTable.ID),
                    KitRuleType.valueOf(rs.getString(KitRuleTypeTable.CODE)));
        }
    }
}
