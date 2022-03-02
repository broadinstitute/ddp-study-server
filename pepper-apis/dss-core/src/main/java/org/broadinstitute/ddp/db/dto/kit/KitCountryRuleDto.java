package org.broadinstitute.ddp.db.dto.kit;

import static org.broadinstitute.ddp.constants.SqlConstants.KitCountryRuleTable;

import java.sql.ResultSet;
import java.sql.SQLException;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

@Value
@AllArgsConstructor
public class KitCountryRuleDto {
    Long countryId;
    Long kitRuleId;

    public static class KitCountryRuleDtoMapper implements RowMapper<KitCountryRuleDto> {
        @Override
        public KitCountryRuleDto map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new KitCountryRuleDto(
                    rs.getLong(KitCountryRuleTable.COUNTRY_ID),
                    rs.getLong(KitCountryRuleTable.RULE_ID));
        }
    }
}
