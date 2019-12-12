package org.broadinstitute.ddp.db.dto;

import org.broadinstitute.ddp.model.activity.types.DateRenderMode;
import org.jdbi.v3.core.mapper.Nested;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public final class DateQuestionDto extends QuestionDto {

    private DateRenderMode renderMode;
    private boolean displayCalendar;
    private Long placeholderTemplateId;

    @JdbiConstructor
    public DateQuestionDto(@Nested QuestionDto questionDto,
                           @ColumnName("date_render_mode_code") DateRenderMode renderMode,
                           @ColumnName("display_calendar") boolean displayCalendar,
                           @ColumnName("placeholder_template_id") Long placeholderTemplateId) {
        super(questionDto);
        this.renderMode = renderMode;
        this.displayCalendar = displayCalendar;
        this.placeholderTemplateId = placeholderTemplateId;
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
}
