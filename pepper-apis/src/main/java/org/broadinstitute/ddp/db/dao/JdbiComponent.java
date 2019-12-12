package org.broadinstitute.ddp.db.dao;

import java.util.Optional;

import org.broadinstitute.ddp.db.dto.ComponentDto;
import org.broadinstitute.ddp.db.dto.MailingAddressComponentDto;
import org.broadinstitute.ddp.model.activity.types.ComponentType;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

public interface JdbiComponent extends SqlObject {

    @UseStringTemplateSqlLocator
    @SqlQuery("queryComponentIdByInstanceGuidAndBlockId")
    @RegisterConstructorMapper(ComponentDto.class)
    ComponentDto findByBlockId(@Bind("activityInstanceGuid") String activityInstanceGuid, @Bind("blockId") long blockId);

    @SqlQuery("select ct.component_type_code as component_type, comp.*"
            + "  from block_component as bc"
            + "  join component as comp on bc.component_id = comp.component_id"
            + "  join component_type as ct on ct.component_type_id = comp.component_type_id"
            + "  join revision as rev on comp.revision_id = rev.revision_id"
            + " where bc.block_id = :blockId"
            + "   and rev.start_date <= :timestamp"
            + "   and (rev.end_date is null or :timestamp < rev.end_date)")
    @RegisterConstructorMapper(ComponentDto.class)
    Optional<ComponentDto> findDtoByBlockIdAndTimestamp(@Bind("blockId") long blockId,
                                                        @Bind("timestamp") long timestamp);

    @SqlQuery("select * from mailing_address_component where component_id = :componentId")
    @RegisterConstructorMapper(MailingAddressComponentDto.class)
    Optional<MailingAddressComponentDto> findMailingAddressComponentDtoById(@Bind("componentId") long componentId);

    @GetGeneratedKeys
    @SqlUpdate("insert into component(component_type_id,hide_number,revision_id) (select component_type_id,:hideNumber,:revisionId from "
            + "component_type where component_type_code = :componentType)")
    long insert(@Bind("componentType") ComponentType componentType,
                @Bind("hideNumber") boolean hideNumber,
                @Bind("revisionId") long revisionId);

    @SqlUpdate("insert into mailing_address_component (component_id, title_template_id, subtitle_template_id)"
            + " values (:componentId, :titleTemplateId, :subtitleTemplateId)")
    int insertMailingAddressComponent(
            @Bind("componentId") long componentId,
            @Bind("titleTemplateId") Long titleTemplateId,
            @Bind("subtitleTemplateId") Long subtitleTemplateId);

    @SqlUpdate("update mailing_address_component"
            + "    set title_template_id = :titleTemplateId,"
            + "        subtitle_template_id = :subtitleTemplateId"
            + "  where component_id = :componentId")
    int updateMailingAddressComponent(
            @Bind("componentId") long componentId,
            @Bind("titleTemplateId") Long titleTemplateId,
            @Bind("subtitleTemplateId") Long subtitleTemplateId);
}
