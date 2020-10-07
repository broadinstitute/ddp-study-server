package org.broadinstitute.ddp.db.dto;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.broadinstitute.ddp.model.activity.definition.question.DatePicklistDef;
import org.broadinstitute.ddp.model.activity.types.DateFieldType;
import org.broadinstitute.ddp.model.activity.types.DateRenderMode;
import org.jdbi.v3.core.mapper.Nested;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public final class DateQuestionDto extends QuestionDto implements Serializable {

    private DateRenderMode renderMode;
    private boolean displayCalendar;
    private Long placeholderTemplateId;
    private DatePicklistDef picklistDef;
    private List<DateFieldType> fields;

    @JdbiConstructor
    public DateQuestionDto(@Nested QuestionDto questionDto,
                           @Nested DatePicklistDef picklistDef,
                           @ColumnName("date_fields") String dateFields,
                           @ColumnName("date_render_mode") DateRenderMode renderMode,
                           @ColumnName("display_calendar") boolean displayCalendar,
                           @ColumnName("placeholder_template_id") Long placeholderTemplateId) {
        super(questionDto);
        this.renderMode = renderMode;
        this.displayCalendar = displayCalendar;
        this.placeholderTemplateId = placeholderTemplateId;
        this.picklistDef = picklistDef;
        if (dateFields == null || dateFields.isBlank()) {
            throw new IllegalArgumentException("Date question requires date fields");
        } else {
            this.fields = Arrays.stream(dateFields.split(","))
                    .map(DateFieldType::valueOf)
                    .collect(Collectors.toList());
        }
    }

    public DateRenderMode getRenderMode() {
        return renderMode;
    }

    public boolean shouldDisplayCalendar() {
        return displayCalendar;
    }

    public Long getPlaceholderTemplateId() {
        return placeholderTemplateId;
    }

    public DatePicklistDef getPicklistDef() {
        return picklistDef;
    }

    public List<DateFieldType> getFields() {
        return fields;
    }

    @Override
    public Set<Long> getTemplateIds() {
        var ids = super.getTemplateIds();
        if (placeholderTemplateId != null) {
            ids.add(placeholderTemplateId);
        }
        return ids;
    }
}
