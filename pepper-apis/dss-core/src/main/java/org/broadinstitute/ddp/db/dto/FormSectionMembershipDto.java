package org.broadinstitute.ddp.db.dto;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Value
@AllArgsConstructor(onConstructor = @__(@JdbiConstructor))
public class FormSectionMembershipDto {
    @ColumnName("form_activity__form_section_id")
    long id;

    @ColumnName("form_activity_id")
    long activityId;

    @ColumnName("form_section_id")
    long sectionId;

    @ColumnName("display_order")
    int displayOrder;

    @ColumnName("revision_id")
    long revisionId;
}
