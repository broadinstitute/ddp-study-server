package org.broadinstitute.ddp.db.dao;

import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.broadinstitute.ddp.db.dto.ComponentDto;
import org.broadinstitute.ddp.db.dto.InstitutionPhysicianComponentDto;
import org.broadinstitute.ddp.db.dto.MailingAddressComponentDto;
import org.broadinstitute.ddp.model.activity.types.ComponentType;
import org.jdbi.v3.core.result.LinkedHashMapRowReducer;
import org.jdbi.v3.core.result.RowView;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.KeyColumn;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.config.ValueColumn;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.statement.UseRowReducer;

public interface JdbiComponent extends SqlObject {

    @SqlQuery("select c.component_id"
            + "  from block_component as bc"
            + "  join component as c on c.component_id = bc.component_id"
            + "  join revision as rev on rev.revision_id = c.revision_id"
            + "  join activity_instance as ai on ai.activity_instance_guid = :instanceGuid"
            + " where bc.block_id = :blockId"
            + "   and rev.start_date <= ai.created_at"
            + "   and (rev.end_date is null or ai.created_at < rev.end_date)")
    long findComponentIdByBlockIdAndInstanceGuid(
            @Bind("blockId") long blockId,
            @Bind("instanceGuid") String activityInstanceGuid);

    @SqlQuery("select bc.block_id, bc.component_id"
            + "  from block_component as bc"
            + "  join component as c on bc.component_id = c.component_id"
            + "  join revision as rev on rev.revision_id = c.revision_id"
            + " where bc.block_id in (<blockIds>)"
            + "   and rev.start_date <= :timestamp"
            + "   and (rev.end_date is null or :timestamp < rev.end_date)")
    @KeyColumn("block_id")
    @ValueColumn("component_id")
    Map<Long, Long> findComponentIdsByBlockIdsAndTimestamp(
            @BindList(value = "blockIds", onEmpty = BindList.EmptyHandling.NULL) Set<Long> blockIds,
            @Bind("timestamp") long timestamp);

    @SqlQuery("select ct.component_type_code as component_type,"
            + "       c.component_id,"
            + "       c.hide_number,"
            + "       c.revision_id,"
            + "       coalesce(mac.title_template_id, ipc.title_template_id) as title_template_id,"
            + "       coalesce(mac.subtitle_template_id, ipc.subtitle_template_id) as subtitle_template_id,"
            + "       mac.require_verified,"
            + "       mac.require_phone,"
            + "       it.institution_type_code as institution_type,"
            + "       ipc.add_button_template_id,"
            + "       ipc.allow_multiple,"
            + "       ipc.show_fields_initially,"
            + "       ipc.required"
            + "  from component as c"
            + "  join component_type as ct on ct.component_type_id = c.component_type_id"
            + "  left join mailing_address_component as mac on mac.component_id = c.component_id"
            + "  left join institution_physician_component as ipc on ipc.institution_physician_component_id = c.component_id"
            + "  left join institution_type as it on it.institution_type_id = ipc.institution_type_id"
            + " where c.component_id in (<ids>)")
    @RegisterConstructorMapper(MailingAddressComponentDto.class)
    @RegisterConstructorMapper(InstitutionPhysicianComponentDto.class)
    @UseRowReducer(ComponentDtoReducer.class)
    Stream<ComponentDto> findComponentDtosByIds(
            @BindList(value = "ids", onEmpty = BindList.EmptyHandling.NULL) Set<Long> componentIds);

    class ComponentDtoReducer implements LinkedHashMapRowReducer<Long, ComponentDto> {
        @Override
        public void accumulate(Map<Long, ComponentDto> container, RowView view) {
            ComponentDto componentDto;
            var type = ComponentType.valueOf(view.getColumn("component_type", String.class));
            if (type == ComponentType.MAILING_ADDRESS) {
                componentDto = view.getRow(MailingAddressComponentDto.class);
            } else {
                componentDto = view.getRow(InstitutionPhysicianComponentDto.class);
            }
            container.put(componentDto.getComponentId(), componentDto);
        }
    }

    @GetGeneratedKeys
    @SqlUpdate("insert into component(component_type_id,hide_number,revision_id) (select component_type_id,:hideNumber,:revisionId from "
            + "component_type where component_type_code = :componentType)")
    long insert(@Bind("componentType") ComponentType componentType,
                @Bind("hideNumber") boolean hideNumber,
                @Bind("revisionId") long revisionId);

    @SqlUpdate("insert into mailing_address_component (component_id, title_template_id, subtitle_template_id,"
            + "        require_verified, require_phone)"
            + " values (:componentId, :titleTemplateId, :subtitleTemplateId, :requireVerified, :requirePhone)")
    int insertMailingAddressComponent(
            @Bind("componentId") long componentId,
            @Bind("titleTemplateId") Long titleTemplateId,
            @Bind("subtitleTemplateId") Long subtitleTemplateId,
            @Bind("requireVerified") boolean requireVerified,
            @Bind("requirePhone") boolean requirePhone);

    @SqlUpdate("update mailing_address_component"
            + "    set title_template_id = :titleTemplateId,"
            + "        subtitle_template_id = :subtitleTemplateId,"
            + "        require_verified = :requireVerified,"
            + "        require_phone = :requirePhone"
            + "  where component_id = :componentId")
    int updateMailingAddressComponent(
            @Bind("componentId") long componentId,
            @Bind("titleTemplateId") Long titleTemplateId,
            @Bind("subtitleTemplateId") Long subtitleTemplateId,
            @Bind("requireVerified") boolean requireVerified,
            @Bind("requirePhone") boolean requirePhone);
}
