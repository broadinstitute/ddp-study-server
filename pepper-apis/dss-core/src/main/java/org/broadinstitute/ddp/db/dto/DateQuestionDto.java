package org.broadinstitute.ddp.db.dto;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.experimental.Accessors;
import org.broadinstitute.ddp.model.activity.definition.question.DatePicklistDef;
import org.broadinstitute.ddp.model.activity.types.DateFieldType;
import org.broadinstitute.ddp.model.activity.types.DateRenderMode;
import org.jdbi.v3.core.mapper.Nested;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Getter
public final class DateQuestionDto extends QuestionDto implements Serializable {
    private final DateRenderMode renderMode;

    @Accessors(fluent = true)
    private final boolean shouldDisplayCalendar;
    private final Long placeholderTemplateId;
    private final DatePicklistDef picklistDef;
    private final List<DateFieldType> fields;

    @JdbiConstructor
    public DateQuestionDto(@Nested QuestionDto questionDto,
                           @Nested DatePicklistDef picklistDef,
                           @ColumnName("date_fields") String dateFields,
                           @ColumnName("date_render_mode") DateRenderMode renderMode,
                           @ColumnName("display_calendar") boolean displayCalendar,
                           @ColumnName("placeholder_template_id") Long placeholderTemplateId) {
        super(questionDto);
        this.renderMode = renderMode;
        this.shouldDisplayCalendar = displayCalendar;
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

    @Override
    public Set<Long> getTemplateIds() {
        var ids = super.getTemplateIds();
        if (placeholderTemplateId != null) {
            ids.add(placeholderTemplateId);
        }
        return ids;
    }
}
