package org.broadinstitute.ddp.db.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.broadinstitute.ddp.model.activity.types.OrientationType;
import org.jdbi.v3.core.mapper.Nested;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public final class CompositeQuestionDto extends QuestionDto {

    private boolean allowMultiple;
    private boolean unwrapOnExport;
    private Long addButtonTemplateId;
    private Long additionalItemTemplateId;
    private OrientationType childOrientation;
    private List<QuestionDto> childQuestions = new ArrayList<>();

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

    public List<QuestionDto> getChildQuestions() {
        return childQuestions;
    }

    public void addChildQuestion(QuestionDto childQuestion) {
        this.childQuestions.add(childQuestion);
    }

    public Optional<QuestionDto> getChildQuestionByStableId(String childAnswerStableId) {
        return childQuestions.stream().filter(child -> child.getStableId().equals(childAnswerStableId)).findFirst();
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
