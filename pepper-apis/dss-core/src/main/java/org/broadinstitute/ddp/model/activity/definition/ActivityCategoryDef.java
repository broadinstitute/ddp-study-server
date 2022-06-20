package org.broadinstitute.ddp.model.activity.definition;


import com.google.gson.annotations.SerializedName;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.broadinstitute.ddp.db.dto.ActivityCategoryDto;

@Getter
@Setter
@Builder
public class ActivityCategoryDef {
    @SerializedName("categoryCode")
    String code;
    @SerializedName("categoryName")
    String name;

    public static ActivityCategoryDef from(ActivityCategoryDto categoryDto) {
        if (categoryDto == null) {
            return null;
        }
        return builder().code(categoryDto.getCategoryCode())
                .name(categoryDto.getActivityCategoryName()).build();
    }
}
