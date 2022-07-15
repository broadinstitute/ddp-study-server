package org.broadinstitute.ddp.db.dto;

import java.io.Serializable;
import java.util.Set;

import lombok.Getter;
import org.broadinstitute.ddp.model.activity.types.OrientationType;
import org.jdbi.v3.core.mapper.Nested;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Getter
public final class CompositeQuestionDto extends QuestionDto implements Serializable {
    private final boolean allowMultiple;
    private final boolean unwrapOnExport;
    private final Long addButtonTemplateId;
    private final Long additionalItemTemplateId;
    private final OrientationType childOrientation;
    private final String tabularSeparator;

    @JdbiConstructor
    public CompositeQuestionDto(@Nested QuestionDto questionDto,
                                @ColumnName("allow_multiple") boolean allowMultiple,
                                @ColumnName("unwrap_on_export") boolean unwrapOnExport,
                                @ColumnName("add_button_template_id") Long addButtonTemplateId,
                                @ColumnName("additional_item_template_id") Long additionalItemTemplateId,
                                @ColumnName("child_orientation") OrientationType childOrientation,
                                @ColumnName("tabular_separator") String tabularSeparator) {
        super(questionDto);
        this.allowMultiple = allowMultiple;
        this.unwrapOnExport = unwrapOnExport;
        this.addButtonTemplateId = addButtonTemplateId;
        this.additionalItemTemplateId = additionalItemTemplateId;
        this.childOrientation = childOrientation;
        this.tabularSeparator = tabularSeparator;
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
