package org.broadinstitute.ddp.db.dto;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Data
@AllArgsConstructor(onConstructor = @__(@JdbiConstructor))
public class ActivityFormGroupDto implements Serializable {
    @ColumnName("form_id")
    private long id;

    @ColumnName("form_code")
    private String formCode;

    @ColumnName("form_name")
    private String formName;

    @ColumnName("activity_id")
    private Long activityId;

    @ColumnName("section_id")
    private Long sectionId;

    protected ActivityFormGroupDto(final ActivityFormGroupDto other) {
        this(other.id, other.formCode, other.formName, other.activityId, other.sectionId);
    }

}
