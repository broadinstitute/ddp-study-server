package org.broadinstitute.ddp.db.dto.kit;

import static org.broadinstitute.ddp.constants.SqlConstants.KitCountryRuleTable;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

public class KitCountryRuleDto {

    private Long countryId;
    private Long kitRuleId;

    public KitCountryRuleDto(Long countryId, Long kitRuleId) {
        this.countryId = countryId;
        this.kitRuleId = kitRuleId;
    }

    public Long getCountryId() {
        return countryId;
    }

    public Long getKitRuleId() {
        return kitRuleId;
    }

    public static class KitCountryRuleDtoMapper implements RowMapper<KitCountryRuleDto> {
        @Override
        public KitCountryRuleDto map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new KitCountryRuleDto(
                    rs.getLong(KitCountryRuleTable.COUNTRY_ID),
                    rs.getLong(KitCountryRuleTable.RULE_ID));
        }
    }
}
