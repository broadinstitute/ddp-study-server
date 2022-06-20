package org.broadinstitute.ddp.db.dto;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Data
@AllArgsConstructor(onConstructor = @__(@JdbiConstructor))
public class ActivityCategoryDto implements Serializable {
    @ColumnName("activity_category_id")
    private long id;

    @ColumnName("activity_category_code")
    private long categoryCode;

    @ColumnName("activity_category_name")
    private String activityCategoryName;

    @ColumnName("activity_id")
    private Long activityId;

    protected ActivityCategoryDto(final ActivityCategoryDto other) {
        this(other.id, other.categoryCode, other.activityCategoryName, other.activityId);
    }

}
