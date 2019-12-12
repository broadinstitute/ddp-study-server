package org.broadinstitute.ddp.db.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import org.broadinstitute.ddp.constants.SqlConstants.ComponentTable;
import org.broadinstitute.ddp.model.activity.types.ComponentType;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

public interface JdbiComponent extends SqlObject {

    /**
     * Finds the relevant {@link ComponentDto} by filtering through
     * revisions.
     */
    @SqlQuery("queryComponentIdByInstanceGuidAndBlockId")
    @UseStringTemplateSqlLocator
    @RegisterRowMapper(ComponentDtoRowMapper.class)
    ComponentDto findByBlockId(@Bind("activityInstanceGuid") String activityInstanceGuid, @Bind("blockId") long blockId);

    @SqlQuery("select ct.component_type_code, comp.*"
            + "  from block_component as bc"
            + "   join component as comp on bc.component_id = comp.component_id"
            + "   join component_type as ct on ct.component_type_id = comp.component_type_id"
            + "   join revision as rev on comp.revision_id = rev.revision_id"
            + "  where bc.block_id = :blockId"
            + "    and rev.start_date <= :timestamp"
            + "    and (rev.end_date is null or :timestamp < rev.end_date)")
    @RegisterRowMapper(ComponentDtoRowMapper.class)
    Optional<ComponentDto> findDtoByBlockIdAndTimestamp(@Bind("blockId") long blockId,
                                                        @Bind("timestamp") long timestamp);

    @SqlUpdate("insert into component(component_type_id,revision_id) (select component_type_id,:revisionId from "
            + "component_type where component_type_code = :componentType)")
    @GetGeneratedKeys
    long insert(@Bind("componentType") ComponentType componentType, @Bind("revisionId") long revisionId);

    class ComponentDto {
        private long componentId;

        private ComponentType componentType;

        private boolean hideNumber;

        public ComponentDto(long componentId,
                            ComponentType componentType,
                            boolean hideNumber) {
            this.componentId = componentId;
            this.componentType = componentType;
            this.hideNumber = hideNumber;
        }

        public ComponentType getComponentType() {
            return componentType;
        }

        public long getComponentId() {
            return componentId;
        }

        public boolean shouldHideNumber() {
            return hideNumber;
        }
    }

    class ComponentDtoRowMapper implements RowMapper<ComponentDto> {
        @Override
        public ComponentDto map(ResultSet rs, StatementContext ctx) throws SQLException {
            ComponentType componentType = ComponentType.valueOf(rs.getString(ComponentTable.COMPONENT_TYPE));
            return new ComponentDto(rs.getLong(ComponentTable.ID),
                                    componentType,
                                    rs.getBoolean(ComponentTable.HIDE_NUMBER));
        }
    }

}
