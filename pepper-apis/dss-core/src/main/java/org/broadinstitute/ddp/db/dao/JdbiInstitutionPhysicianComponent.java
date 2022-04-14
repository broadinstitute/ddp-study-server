package org.broadinstitute.ddp.db.dao;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiInstitutionPhysicianComponent extends SqlObject {

    @SqlUpdate("insert into institution_physician_component "
            + "(institution_physician_component_id,"
            + "allow_multiple,add_button_template_id,"
            + "title_template_id,"
            + "subtitle_template_id,"
            + "institution_type_id,"
            + "show_fields_initially,"
            + "required)"
            + "values(:id,:allowMultiple,:buttonTemplateId,:titleTemplateId,:subtitleTemplateId,:instTypeId,"
            + ":showFields,:required)")
    int insert(@Bind("id") long componentId,
               @Bind("allowMultiple") boolean allowMultiple,
               @Bind("buttonTemplateId") Long addButtonTemplateId,
               @Bind("titleTemplateId") Long titleTemplateId,
               @Bind("subtitleTemplateId") Long subtitleTemplateId,
               @Bind("instTypeId") long institutionTypeId,
               @Bind("showFields") boolean showFields,
               @Bind("required") boolean required);
}
