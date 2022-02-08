package org.broadinstitute.ddp.db.dto.kit;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.broadinstitute.ddp.constants.SqlConstants.KitPexRuleTable;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

public class KitPexRuleDto {

    private Long expressionId;
    private Long kitRuleId;

    public KitPexRuleDto(Long expressionId, Long kitRuleId) {
        this.expressionId = expressionId;
        this.kitRuleId = kitRuleId;
    }

    public Long getExpressionId() {
        return expressionId;
    }

    public Long getKitRuleId() {
        return kitRuleId;
    }

    public static class KitPexRuleDtoMapper implements RowMapper<KitPexRuleDto> {
        @Override
        public KitPexRuleDto map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new KitPexRuleDto(
                    rs.getLong(KitPexRuleTable.EXPRESSION_ID),
                    rs.getLong(KitPexRuleTable.RULE_ID));
        }
    }
}
