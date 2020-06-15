package org.broadinstitute.ddp.model.activity.instance.question;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Consumer;

import javax.annotation.Nullable;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.content.HtmlConverter;
import org.broadinstitute.ddp.model.activity.instance.answer.PicklistAnswer;
import org.broadinstitute.ddp.model.activity.instance.validation.Rule;
import org.broadinstitute.ddp.model.activity.types.PicklistRenderMode;
import org.broadinstitute.ddp.model.activity.types.PicklistSelectMode;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.util.MiscUtil;

public final class PicklistQuestion extends Question<PicklistAnswer> {

    @NotNull
    @SerializedName("selectMode")
    private PicklistSelectMode selectMode;

    @NotNull
    @SerializedName("renderMode")
    private PicklistRenderMode renderMode;

    @SerializedName("picklistLabel")
    private String picklistLabel;

    @SerializedName("groups")
    private List<PicklistGroup> groups = new ArrayList<>();

    @NotEmpty
    @SerializedName("picklistOptions")
    private List<PicklistOption> picklistOptions = new ArrayList<>();

    private transient Long picklistLabelTemplateId;

    /**
     * Construct an instance view of picklist question, where list of picklist options must be non-empty.
     * 
     * <p>The additional header and footer template IDs may be null.
     */
    public PicklistQuestion(String stableId, long promptTemplateId,
                            boolean isRestricted, boolean isDeprecated, Long tooltipTemplateId,
                            @Nullable Long additionalInfoHeaderTemplateId, @Nullable Long additionalInfoFooterTemplateId,
                            List<PicklistAnswer> answers, List<Rule<PicklistAnswer>> validations,
                            PicklistSelectMode selectMode, PicklistRenderMode renderMode,
                            Long picklistLabelTemplateId, List<PicklistOption> picklistOptions,
                            List<PicklistGroup> groups) {
        super(QuestionType.PICKLIST,
                stableId,
                promptTemplateId,
                isRestricted,
                isDeprecated,
                tooltipTemplateId,
                additionalInfoHeaderTemplateId,
                additionalInfoFooterTemplateId,
                answers,
                validations);

        this.selectMode = MiscUtil.checkNonNull(selectMode, "selectMode");
        this.renderMode = MiscUtil.checkNonNull(renderMode, "renderMode");

        if (renderMode == PicklistRenderMode.DROPDOWN && picklistLabelTemplateId == null) {
            throw new IllegalArgumentException("picklist label is required for dropdown render mode");
        } else {
            this.picklistLabelTemplateId = picklistLabelTemplateId;
        }

        if (picklistOptions == null || picklistOptions.isEmpty()) {
            throw new IllegalArgumentException("options list needs to be non-empty");
        } else {
            this.picklistOptions.addAll(picklistOptions);
        }

        if (groups != null) {
            this.groups.addAll(groups);
        }
    }

    /**
     * Construct an instance view of picklist question, where list of picklist options must be non-empty.
     */
    public PicklistQuestion(String stableId, long promptTemplateId,
                            List<PicklistAnswer> answers, List<Rule<PicklistAnswer>> validations,
                            PicklistSelectMode selectMode, PicklistRenderMode renderMode,
                            Long picklistLabelTemplateId, List<PicklistOption> picklistOptions,
                            List<PicklistGroup> groups) {
        this(stableId,
                promptTemplateId,
                false,
                false,
                null,
                null,
                null,
                answers,
                validations,
                selectMode,
                renderMode,
                picklistLabelTemplateId,
                picklistOptions,
                groups);
    }

    public PicklistQuestion(String stableId, long promptTemplateId,
                            List<PicklistAnswer> answers, List<Rule<PicklistAnswer>> validations,
                            PicklistSelectMode selectMode, PicklistRenderMode renderMode,
                            Long picklistLabelTemplateId, List<PicklistOption> picklistOptions) {
        this(stableId, promptTemplateId, answers, validations, selectMode, renderMode, picklistLabelTemplateId, picklistOptions, null);
    }

    public PicklistSelectMode getSelectMode() {
        return selectMode;
    }

    public PicklistRenderMode getRenderMode() {
        return renderMode;
    }

    public String getPicklistLabel() {
        return picklistLabel;
    }

    public List<PicklistGroup> getGroups() {
        return groups;
    }

    public List<PicklistOption> getPicklistOptions() {
        return picklistOptions;
    }

    public Long getPicklistLabelTemplateId() {
        return picklistLabelTemplateId;
    }

    @Override
    public void registerTemplateIds(Consumer<Long> registry) {
        super.registerTemplateIds(registry);

        if (picklistLabelTemplateId != null) {
            registry.accept(picklistLabelTemplateId);
        }

        for (PicklistGroup group : groups) {
            group.registerTemplateIds(registry);
        }

        for (PicklistOption option : picklistOptions) {
            option.registerTemplateIds(registry);
        }
    }

    @Override
    public void applyRenderedTemplates(Provider<String> rendered, ContentStyle style) {
        super.applyRenderedTemplates(rendered, style);

        // If we're rendering as dropdown, force the options and the label to convert content as plain text.
        if (renderMode == PicklistRenderMode.DROPDOWN) {
            style = ContentStyle.BASIC;
        }

        if (picklistLabelTemplateId != null) {
            picklistLabel = rendered.get(picklistLabelTemplateId);
            if (picklistLabel == null) {
                throw new NoSuchElementException("No rendered template found for picklist label with id " + picklistLabelTemplateId);
            }
        }

        if (style == ContentStyle.BASIC) {
            picklistLabel = HtmlConverter.getPlainText(picklistLabel);
        }

        for (PicklistGroup group : groups) {
            group.applyRenderedTemplates(rendered, style);
        }

        for (PicklistOption option : picklistOptions) {
            option.applyRenderedTemplates(rendered, style);
        }
    }
}
