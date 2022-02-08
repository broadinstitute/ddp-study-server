package org.broadinstitute.ddp.db.dto;

import java.io.Serializable;
import java.util.Set;

import org.broadinstitute.ddp.model.activity.types.OrientationType;
import org.jdbi.v3.core.mapper.Nested;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public final class CompositeQuestionDto extends QuestionDto implements Serializable {

    private boolean allowMultiple;
    private boolean unwrapOnExport;
    private Long addButtonTemplateId;
    private Long additionalItemTemplateId;
    private OrientationType childOrientation;

    @JdbiConstructor
    public CompositeQuestionDto(@Nested QuestionDto questionDto,
                                @ColumnName("allow_multiple") boolean allowMultiple,
                                @ColumnName("unwrap_on_export") boolean unwrapOnExport,
                                @ColumnName("add_button_template_id") Long addButtonTemplateId,
                                @ColumnName("additional_item_template_id") Long additionalItemTemplateId,
                                @ColumnName("child_orientation") OrientationType childOrientation) {
        super(questionDto);
        this.allowMultiple = allowMultiple;
        this.unwrapOnExport = unwrapOnExport;
        this.addButtonTemplateId = addButtonTemplateId;
        this.additionalItemTemplateId = additionalItemTemplateId;
        this.childOrientation = childOrientation;
    }

    public boolean isAllowMultiple() {
        return allowMultiple;
    }

    public boolean isUnwrapOnExport() {
        return unwrapOnExport;
    }

    public Long getAddButtonTemplateId() {
        return addButtonTemplateId;
    }

    public Long getAdditionalItemTemplateId() {
        return additionalItemTemplateId;
    }

    public OrientationType getChildOrientation() {
        return childOrientation;
    }

    @Override
    public Set<Long> getTemplateIds() {
        var ids = super.getTemplateIds();
        if (addButtonTemplateId != null) {
            ids.add(addButtonTemplateId);
        }
        if (additionalItemTemplateId != null) {
            ids.add(additionalItemTemplateId);
        }
        return ids;
    }
}
