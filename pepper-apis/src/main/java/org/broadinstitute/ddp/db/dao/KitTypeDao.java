package org.broadinstitute.ddp.db.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.broadinstitute.ddp.constants.SqlConstants;
import org.broadinstitute.ddp.model.dsm.KitType;
import org.broadinstitute.ddp.model.dsm.KitTypes;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

public interface KitTypeDao extends SqlObject {
    String SALIVA = KitTypes.SALIVA.toString();
    String BLOOD = KitTypes.BLOOD.toString();

    @UseStringTemplateSqlLocator
    @SqlQuery("getAllKitTypes")
    @RegisterRowMapper(KitTypeRowMapper.class)
    List<KitType> getAllKitTypes();

    @SqlQuery("SELECT kit_type_id, name FROM kit_type WHERE name = :kitTypeName")
    @RegisterRowMapper(KitTypeRowMapper.class)
    Optional<KitType> getKitTypeByName(@Bind String kitTypeName);

    @SqlQuery("select kit_type_id, name from kit_type where kit_type_id = :id")
    @RegisterRowMapper(KitTypeRowMapper.class)
    Optional<KitType> getKitTypeById(@Bind Long id);

    default KitType getSalivaKitType() {
        return this.getKitTypeByName(SALIVA).orElse(null);
    }

    default KitType getBloodKitType() {
        return this.getKitTypeByName(BLOOD).orElse(null);
    }

    class KitTypeRowMapper implements RowMapper<KitType> {
        @Override
        public KitType map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new KitType(
                    rs.getLong(SqlConstants.KitTypeTable.ID),
                    rs.getString(SqlConstants.KitTypeTable.NAME));
        }
    }
}
