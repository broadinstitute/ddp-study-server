package org.broadinstitute.ddp.model.activity.instance;

import javax.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.content.HtmlConverter;
import org.broadinstitute.ddp.content.I18nContentRenderer;
import org.broadinstitute.ddp.content.I18nTemplateConstants;
import org.broadinstitute.ddp.content.RenderValueProvider;
import org.broadinstitute.ddp.content.Renderable;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.types.ActivityType;
import org.broadinstitute.ddp.model.activity.types.BlockType;
import org.broadinstitute.ddp.model.activity.types.FormType;
import org.broadinstitute.ddp.model.activity.types.ListStyleHint;
import org.broadinstitute.ddp.pex.PexException;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.broadinstitute.ddp.transformers.LocalDateTimeAdapter;
import org.broadinstitute.ddp.util.MiscUtil;
import org.jdbi.v3.core.Handle;

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

    private transient Long introductionSectionId;
    private transient Long closingSectionId;
    private transient Long readonlyHintTemplateId;
    private transient Long lastUpdatedTextTemplateId;
    private int sectionIndex;

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
            boolean isFollowup,
            boolean isInstanceHidden,
            boolean excludeFromDisplay,
            int sectionIndex
    ) {
        super(participantUserId, instanceId, activityId, ActivityType.FORMS, guid, title, subtitle, statusTypeCode, readonly, activityCode,
                createdAtMillis, firstCompletedAt, isFollowup, isInstanceHidden, excludeFromDisplay);
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

    public LocalDateTime getActivityDefinitionLastUpdated() {
        return activityDefinitionLastUpdated;
    }

    public int getSectionIndex() {
        return sectionIndex;
    }

    public void setSectionIndex(int sectionIndex) {
        this.sectionIndex = sectionIndex;
    }

    /**
     * Render all the content templates in the form by rendering them, translating them to given language,
     * and converting them to the given content style.
     *
     * @param handle     the database handle
     * @param renderer   the template renderer
     * @param langCodeId the language code id to translate templates to
     * @param style      the content style to use for converting content
     */
    public void renderContent(Handle handle, I18nContentRenderer renderer, long langCodeId, ContentStyle style) {
        Set<Long> templateIds = new HashSet<>();
        Consumer<Long> consumer = templateIds::add;

        List<FormSection> allSections = getAllSections();

        for (FormSection section : allSections) {
            section.registerTemplateIds(consumer);
        }

        Map<String, String> snapshot = handle.attach(ActivityInstanceDao.class).findSubstitutions(getInstanceId());
        RenderValueProvider valueProvider = I18nContentRenderer.newValueProvider(handle, getParticipantUserId(), snapshot);

        Map<String, Object> context = new HashMap<>();
        context.put(I18nTemplateConstants.DDP, valueProvider);
        Map<Long, String> rendered = renderer.bulkRender(handle, templateIds, langCodeId, context);
        Renderable.Provider<String> provider = rendered::get;

        for (FormSection section : allSections) {
            section.applyRenderedTemplates(provider, style);
        }

        if (readonlyHintTemplateId != null) {
            readonlyHint = renderer.renderContent(handle, readonlyHintTemplateId, langCodeId);
            // Strip down HTML tags if the plain text is requested
            if (style == ContentStyle.BASIC) {
                readonlyHint = HtmlConverter.getPlainText(readonlyHint);
            }
        }

        if (style == ContentStyle.BASIC) {
            title = HtmlConverter.getPlainText(title);
            subtitle = HtmlConverter.getPlainText(subtitle);
        }

        if (lastUpdatedTextTemplateId != null) {
            Map<String, Object> varNameToValueMap = new HashMap<>();
            // Intentionally converting to a date here for display purposes
            LocalDate lastUpdatedDate = activityDefinitionLastUpdated == null ? null : activityDefinitionLastUpdated.toLocalDate();
            varNameToValueMap.put(I18nTemplateConstants.LAST_UPDATED, lastUpdatedDate);
            activityDefinitionLastUpdatedText = renderer.renderContent(handle, lastUpdatedTextTemplateId, langCodeId, varNameToValueMap);

            if (style == ContentStyle.BASIC) {
                activityDefinitionLastUpdatedText = HtmlConverter.getPlainText(activityDefinitionLastUpdatedText);
            }
        }
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
     * Evaluate and update the form's block visibilities, assuming that those are all loaded. If the block does not have
     * a conditional expression (and thus toggle-able), no change will be made to the block.
     *
     * @param handle       the jdbi handle
     * @param interpreter  the pex interpreter to evaluate expressions
     * @param userGuid     the user guid
     * @param instanceGuid the activity instance guid
     * @throws DDPException if pex evaluation error
     */
    public void updateBlockStatuses(Handle handle, PexInterpreter interpreter, String userGuid, String operatorGuid, String instanceGuid) {
        for (FormSection section : getAllSections()) {
            for (FormBlock block : section.getBlocks()) {
                updateBlockStatus(handle, interpreter, block, userGuid, operatorGuid, instanceGuid);
                if (block.getBlockType().isContainerBlock()) {
                    List<FormBlock> children;
                    if (block.getBlockType() == BlockType.CONDITIONAL) {
                        children = ((ConditionalBlock) block).getNested();
                    } else if (block.getBlockType() == BlockType.GROUP) {
                        children = ((GroupBlock) block).getNested();
                    } else {
                        throw new DDPException("Unhandled container block type " + block.getBlockType());
                    }
                    for (FormBlock child : children) {
                        updateBlockStatus(handle, interpreter, child, userGuid, operatorGuid, instanceGuid);
                    }
                }
            }
        }
    }

    private void updateBlockStatus(Handle handle, PexInterpreter interpreter, FormBlock block, String userGuid,
                                   String operatorGuid, String instanceGuid) {
        if (block.getShownExpr() != null) {
            try {
                boolean shown = interpreter.eval(block.getShownExpr(), handle, userGuid, operatorGuid, instanceGuid);
                block.setShown(shown);
            } catch (PexException e) {
                String msg = String.format("Error evaluating pex expression for form activity instance %s and block %s: `%s`",
                        getGuid(), block.getGuid(), block.getShownExpr());
                throw new DDPException(msg, e);
            }
        }
    }

    /**
     * Sets the display number for the blocks in order,
     * starting at startingNumber
     * @param blocks the blocks to number
     * @param startingNumber the number at which to start
     * @return the ending number
     */
    private int setNumberables(List<FormBlock> blocks, int startingNumber) {
        for (FormBlock formBlock : blocks) {
            if (formBlock instanceof Numberable) {
                Numberable numberable = (Numberable)formBlock;
                if (numberable.shouldHideNumber()) {
                    numberable.setDisplayNumber(null);
                } else {
                    numberable.setDisplayNumber(startingNumber++);
                }
            }
        }
        return startingNumber;
    }

    /**
     * Walks through the sections and blocks in order and
     * sets the {@link Numberable} fields accordingly.
     * @return the maximum display number used
     */
    public int setDisplayNumbers() {
        int startingNumber = 1;
        if (getIntroduction() != null) {
            startingNumber = setNumberables(getIntroduction().getBlocks(), startingNumber);

        }
        if (getBodySections() != null) {
            for (FormSection bodySection : getBodySections()) {
                startingNumber = setNumberables(bodySection.getBlocks(), startingNumber);
            }
        }
        if (getClosing() != null) {
            startingNumber = setNumberables(getClosing().getBlocks(), startingNumber);
        }
        return startingNumber;
    }

    /**
     * Collect all answers that are within hidden blocks.
     *
     * @return all hidden answers
     */
    public List<Answer> collectHiddenAnswers() {
        List<Answer> hidden = new ArrayList<>();
        for (var section : getAllSections()) {
            for (var block : section.getBlocks()) {
                if (!block.isShown()) {
                    hidden.addAll(collectAnswers(block));
                }
                List<FormBlock> children = new ArrayList<>();
                if (block.getBlockType() == BlockType.CONDITIONAL) {
                    children = ((ConditionalBlock) block).getNested();
                } else if (block.getBlockType() == BlockType.GROUP) {
                    children = ((GroupBlock) block).getNested();
                }
                for (FormBlock child : children) {
                    if (!child.isShown()) {
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
}
