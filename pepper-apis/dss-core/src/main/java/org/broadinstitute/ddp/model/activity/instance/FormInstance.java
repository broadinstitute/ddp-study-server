package org.broadinstitute.ddp.model.activity.instance;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.question.Question;
import org.broadinstitute.ddp.model.activity.types.ActivityType;
import org.broadinstitute.ddp.model.activity.types.BlockType;
import org.broadinstitute.ddp.model.activity.types.FormType;
import org.broadinstitute.ddp.model.activity.types.ListStyleHint;
import org.broadinstitute.ddp.transformers.LocalDateTimeAdapter;
import org.broadinstitute.ddp.util.MiscUtil;

public final class FormInstance extends ActivityInstance {

    @NotNull
    @SerializedName("formType")
    private FormType formType;

    @NotNull
    @SerializedName("listStyleHint")
    private ListStyleHint listStyleHint = ListStyleHint.NONE;

    @SerializedName("readonlyHint")
    private String readonlyHint;

    @SerializedName("introduction")
    private FormSection introduction;

    @SerializedName("closing")
    private FormSection closing;

    @NotNull
    @SerializedName("sections")
    private List<FormSection> sections = new ArrayList<>();

    @JsonAdapter(LocalDateTimeAdapter.class)
    @SerializedName("lastUpdated")
    private LocalDateTime activityDefinitionLastUpdated;

    @SerializedName("lastUpdatedText")
    private String activityDefinitionLastUpdatedText;

    @SerializedName("sectionIndex")
    private int sectionIndex;

    private transient Long introductionSectionId;
    private transient Long closingSectionId;
    private transient Long readonlyHintTemplateId;
    private transient Long lastUpdatedTextTemplateId;
    private transient Map<String, Question> stableIdToQuestion;

    public FormInstance(
            long participantUserId,
            long instanceId,
            long activityId,
            String activityCode,
            FormType formType,
            String guid,
            String title,
            String subtitle,
            String statusTypeCode,
            Boolean readonly,
            ListStyleHint listStyleHint,
            Long readonlyHintTemplateId,
            Long introductionSectionId,
            Long closingSectionId,
            long createdAtMillis,
            Long firstCompletedAt,
            Long lastUpdatedTextTemplateId,
            LocalDateTime activityDefinitionLastUpdated,
            boolean canDelete,
            boolean isFollowup,
            boolean isInstanceHidden,
            boolean excludeFromDisplay,
            int sectionIndex
    ) {
        super(participantUserId, instanceId, activityId, ActivityType.FORMS, guid, title, subtitle, statusTypeCode, readonly, activityCode,
                createdAtMillis, firstCompletedAt, canDelete, isFollowup, excludeFromDisplay, isInstanceHidden);
        this.formType = MiscUtil.checkNonNull(formType, "formType");
        if (listStyleHint != null) {
            this.listStyleHint = listStyleHint;
        }
        this.introductionSectionId = introductionSectionId;
        this.closingSectionId = closingSectionId;
        this.readonlyHintTemplateId = readonlyHintTemplateId;
        this.lastUpdatedTextTemplateId = lastUpdatedTextTemplateId;
        this.activityDefinitionLastUpdated = activityDefinitionLastUpdated;
        this.sectionIndex = sectionIndex;
    }

    public FormType getFormType() {
        return formType;
    }

    public List<FormSection> getBodySections() {
        return sections;
    }

    public void addBodySections(List<FormSection> sections) {
        if (sections != null) {
            this.sections.addAll(sections);
        }
    }

    public List<FormSection> getAllSections() {
        List<FormSection> allSections = new ArrayList<>();
        if (introduction != null) {
            allSections.add(introduction);
        }
        allSections.addAll(sections);
        if (closing != null) {
            allSections.add(closing);
        }
        return allSections;
    }

    public ListStyleHint getListStyleHint() {
        return listStyleHint;
    }

    public String getReadonlyHint() {
        return readonlyHint;
    }

    public void setReadonlyHint(String readonlyHint) {
        this.readonlyHint = readonlyHint;
    }

    public Long getIntroductionSectionId() {
        return introductionSectionId;
    }

    public FormSection getIntroduction() {
        return introduction;
    }

    public void setIntroduction(FormSection introduction) {
        this.introduction = introduction;
    }

    public Long getClosingSectionId() {
        return closingSectionId;
    }

    public FormSection getClosing() {
        return closing;
    }

    public void setClosing(FormSection closing) {
        this.closing = closing;
    }

    public String getActivityDefinitionLastUpdatedText() {
        return activityDefinitionLastUpdatedText;
    }

    public void setActivityDefinitionLastUpdatedText(String activityDefinitionLastUpdatedText) {
        this.activityDefinitionLastUpdatedText = activityDefinitionLastUpdatedText;
    }

    public LocalDateTime getActivityDefinitionLastUpdated() {
        return activityDefinitionLastUpdated;
    }

    public int getSectionIndex() {
        return sectionIndex;
    }

    public void setSectionIndex(int sectionIndex) {
        this.sectionIndex = sectionIndex;
    }

    public Long getReadonlyHintTemplateId() {
        return readonlyHintTemplateId;
    }

    public Long getLastUpdatedTextTemplateId() {
        return lastUpdatedTextTemplateId;
    }

    public Question getQuestionByStableId(String stableId) {
        if (stableIdToQuestion == null) {
            stableIdToQuestion = getAllSections().stream()
                    .flatMap(section -> section.getBlocks().stream())
                    .flatMap(FormBlock::streamQuestions)
                    .collect(Collectors.toMap(Question::getStableId, Function.identity()));
        }
        return stableIdToQuestion.get(stableId);
    }

    /**
     * Check if the user has completed everything required in this form.
     *
     * @return true if complete, otherwise false
     */
    public boolean isComplete() {
        for (FormSection section : getAllSections()) {
            for (FormBlock block : section.getBlocks()) {
                if (!block.isComplete()) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Collect all answers that are within hidden or disabled blocks.
     *
     * @return all hidden or disabled answers
     */
    public List<Answer> collectHiddenAndDisabledAnswers() {
        List<Answer> hidden = new ArrayList<>();
        for (var section : getAllSections()) {
            for (var block : section.getBlocks()) {
                if (isPermanentlyHidden(block)) {
                    continue;
                }
                if (!block.isShown() || !block.isEnabled()) {
                    hidden.addAll(collectAnswers(block));
                }
                List<FormBlock> children = new ArrayList<>();
                if (block.getBlockType() == BlockType.CONDITIONAL) {
                    children = ((ConditionalBlock) block).getNested();
                } else if (block.getBlockType() == BlockType.GROUP) {
                    children = ((GroupBlock) block).getNested();
                }
                for (FormBlock child : children) {
                    if (!child.isShown() || !child.isEnabled()) {
                        hidden.addAll(collectAnswers(child));
                    }
                }
            }
        }
        return hidden;
    }

    private List<Answer> collectAnswers(FormBlock block) {
        if (block.getBlockType() == BlockType.QUESTION) {
            return ((QuestionBlock) block).getQuestion().getAnswers();
        } else if (block.getBlockType() == BlockType.CONDITIONAL) {
            return ((ConditionalBlock) block).getControl().getAnswers();
        } else {
            return Collections.emptyList();
        }
    }

    private boolean isPermanentlyHidden(FormBlock block) {
        // Note: we add support for convention of marking a block as "permanently hidden"
        // and not touch its answers. Consider alternatives when we have a better design.
        return StringUtils.isNotBlank(block.getShownExpr()) && block.getShownExpr().equals("false");
    }
}
