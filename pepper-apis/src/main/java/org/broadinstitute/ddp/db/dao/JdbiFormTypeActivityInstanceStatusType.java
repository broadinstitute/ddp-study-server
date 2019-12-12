package org.broadinstitute.ddp.db.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.broadinstitute.ddp.db.dto.IconBlobDto;
import org.broadinstitute.ddp.model.activity.types.FormType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

public interface JdbiFormTypeActivityInstanceStatusType extends SqlObject {

    @SqlUpdate("insertIconBlob")
    @UseStringTemplateSqlLocator
    @GetGeneratedKeys
    long insert(@Bind("studyId") long studyId, @Bind("formType") FormType formType,
                @Bind("statusType") InstanceStatusType statusType,
                @Bind("icon") byte[] icon, @Bind("revisionId") long revisionId);

    @SqlQuery("queryLatestIconBlobs")
    @UseStringTemplateSqlLocator
    @RegisterRowMapper(IconBlobDtoMapper.class)
    List<IconBlobDto> getIconBlobs(@Bind("studyGuid") String studyGuid);

    class IconBlobDtoMapper implements RowMapper<IconBlobDto> {
        @Override
        public IconBlobDto map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new IconBlobDto(FormType.valueOf(rs.getString(1)), rs.getString(2), rs.getBlob(3));
        }
    }
}
