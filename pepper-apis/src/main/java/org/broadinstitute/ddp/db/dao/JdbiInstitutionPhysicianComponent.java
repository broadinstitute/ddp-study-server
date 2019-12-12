package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.db.dto.InstitutionPhysicianComponentDto;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiInstitutionPhysicianComponent extends SqlObject {

    @SqlQuery("select comp.*, it.institution_type_code as institution_type "
            + "from institution_physician_component comp,institution_type it "
            + "where "
            + "comp.institution_physician_component_id = :componentId "
            + "and "
            + "it.institution_type_id = comp.institution_type_id")
    @RegisterConstructorMapper(InstitutionPhysicianComponentDto.class)
    InstitutionPhysicianComponentDto findById(@Bind("componentId") long componentId);

    @SqlUpdate("insert into institution_physician_component "
            + "(institution_physician_component_id,"
            + "allow_multiple,add_button_template_id,"
            + "title_template_id,"
            + "subtitle_template_id,"
            + "institution_type_id,"
            + "show_fields_initially)"
            + "values(:id,:allowMultiple,:buttonTemplateId,:titleTemplateId,:subtitleTemplateId,:instTypeId,"
            + ":showFields)")
    int insert(@Bind("id") long componentId,
               @Bind("allowMultiple") boolean allowMultiple,
               @Bind("buttonTemplateId") Long addButtonTemplateId,
               @Bind("titleTemplateId") Long titleTemplateId,
               @Bind("subtitleTemplateId") Long subtitleTemplateId,
               @Bind("instTypeId") long institutionTypeId,
               @Bind("showFields") boolean showFields);
}
