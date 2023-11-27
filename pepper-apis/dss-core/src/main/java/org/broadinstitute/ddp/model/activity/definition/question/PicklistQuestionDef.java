package org.broadinstitute.ddp.model.activity.definition.question;

import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.validation.RuleDef;
import org.broadinstitute.ddp.model.activity.types.PicklistRenderMode;
import org.broadinstitute.ddp.model.activity.types.PicklistSelectMode;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.util.MiscUtil;

public final class PicklistQuestionDef extends QuestionDef {

    @NotNull
    @SerializedName("selectMode")
    private PicklistSelectMode selectMode;

    @NotNull
    @SerializedName("renderMode")
    private PicklistRenderMode renderMode;

    @Valid
    @SerializedName("picklistLabelTemplate")
    private Template picklistLabelTemplate;

    @NotNull
    @SerializedName("groups")
    private List<@Valid @NotNull PicklistGroupDef> groups = new ArrayList<>();

    @NotNull
    @SerializedName("picklistOptions")
    private List<@Valid @NotNull PicklistOptionDef> picklistOptions = new ArrayList<>();

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(PicklistSelectMode selectMode, PicklistRenderMode renderMode, String stableId, Template prompt) {
        return new Builder()
                .setSelectMode(selectMode)
                .setRenderMode(renderMode)
                .setStableId(stableId)
                .setPrompt(prompt);
    }

    public static Builder buildSingleSelect(PicklistRenderMode renderMode, String stableId, Template prompt) {
        return new Builder()
                .setSelectMode(PicklistSelectMode.SINGLE)
                .setRenderMode(renderMode)
                .setStableId(stableId)
                .setPrompt(prompt);
    }

    public static Builder buildMultiSelect(PicklistRenderMode renderMode, String stableId, Template prompt) {
        return new Builder()
                .setSelectMode(PicklistSelectMode.MULTIPLE)
                .setRenderMode(renderMode)
                .setStableId(stableId)
                .setPrompt(prompt);
    }

    /**
     * Construct a picklist question definition, with one or more picklist options.
     */
    public PicklistQuestionDef(String stableId, boolean isRestricted, Template promptTemplate,
                               Template additionalInfoHeaderTemplate, Template additionalInfoFooterTemplate,
                               List<RuleDef> validations, PicklistSelectMode selectMode, PicklistRenderMode renderMode,
                               Template picklistLabelTemplate, List<PicklistGroupDef> groups, List<PicklistOptionDef> options,
                               boolean hideNumber, boolean writeOnce) {
        super(QuestionType.PICKLIST,
                stableId,
                isRestricted,
                promptTemplate,
                additionalInfoHeaderTemplate,
                additionalInfoFooterTemplate,
                validations,
                hideNumber,
                writeOnce);
        this.selectMode = MiscUtil.checkNonNull(selectMode, "selectMode");
        this.renderMode = MiscUtil.checkNonNull(renderMode, "renderMode");

        if (renderMode == PicklistRenderMode.DROPDOWN && picklistLabelTemplate == null) {
            throw new IllegalArgumentException("picklist label is required for dropdown render mode");
        } else {
            this.picklistLabelTemplate = picklistLabelTemplate;
        }

        groups = (groups == null) ? new ArrayList<>() : groups;
        options = (options == null) ? new ArrayList<>() : options;

        if (groups.isEmpty() && options.isEmpty() && renderMode != PicklistRenderMode.REMOTE_AUTOCOMPLETE) {
            throw new IllegalArgumentException("need to have at least one option or one group");
        }

        this.groups.addAll(groups);
        this.picklistOptions.addAll(options);
    }

    public PicklistSelectMode getSelectMode() {
        return selectMode;
    }

    public PicklistRenderMode getRenderMode() {
        return renderMode;
    }

    public Template getPicklistLabelTemplate() {
        return picklistLabelTemplate;
    }

    public List<PicklistGroupDef> getGroups() {
        return groups;
    }

    public List<PicklistOptionDef> getPicklistOptions() {
        return picklistOptions;
    }

    public List<PicklistOptionDef> getLocalPicklistOptions() {
        if (renderMode == PicklistRenderMode.REMOTE_AUTOCOMPLETE) {
            return Collections.emptyList();
        } else {
            return picklistOptions;
        }
    }

    public List<PicklistOptionDef> getRemotePicklistOptions() {
        if (renderMode == PicklistRenderMode.REMOTE_AUTOCOMPLETE) {
            return picklistOptions;
        } else {
            return Collections.emptyList();
        }
    }

    public List<PicklistOptionDef> getAllPicklistOptions() {
        Stream<PicklistOptionDef> nestedOptions =
                picklistOptions.stream().flatMap(def -> def.getNestedOptions().stream());
        Stream<PicklistOptionDef> nestedOptsIncluded = Stream.concat(picklistOptions.stream(), nestedOptions);
        Stream<PicklistOptionDef> groupOptions =
                getGroups().stream().flatMap(group -> group.getOptions().stream());
        return Stream.concat(nestedOptsIncluded, groupOptions).collect(toList());
    }

    public List<PicklistOptionDef> getDefaultOptions() {
        return getAllPicklistOptions().stream().filter(PicklistOptionDef::isDefault).collect(toList());
    }

    public static final class Builder extends AbstractQuestionBuilder<Builder> {

        private PicklistSelectMode selectMode;
        private PicklistRenderMode renderMode;

        private Template label = null;
        private List<PicklistGroupDef> groups = new ArrayList<>();
        private List<PicklistOptionDef> options = new ArrayList<>();

        private Builder() {
            // Use static factories.
        }

        @Override
        protected Builder self() {
            return this;
        }

        public Builder setSelectMode(PicklistSelectMode selectMode) {
            this.selectMode = selectMode;
            return this;
        }

        public Builder setRenderMode(PicklistRenderMode renderMode) {
            this.renderMode = renderMode;
            return this;
        }

        public Builder setLabel(Template label) {
            this.label = label;
            return this;
        }

        public Builder addGroup(PicklistGroupDef group) {
            this.groups.add(group);
            return this;
        }

        public Builder addGroups(Collection<PicklistGroupDef> groups) {
            this.groups.addAll(groups);
            return this;
        }

        public Builder clearGroups() {
            this.groups.clear();
            return this;
        }

        public Builder addOption(PicklistOptionDef option) {
            this.options.add(option);
            return this;
        }

        public Builder addOptions(Collection<PicklistOptionDef> options) {
            this.options.addAll(options);
            return this;
        }

        public Builder clearOptions() {
            this.options.clear();
            return this;
        }

        public PicklistQuestionDef build() {
            PicklistQuestionDef question = new PicklistQuestionDef(stableId,
                    isRestricted,
                    prompt,
                    getAdditionalInfoHeader(),
                    getAdditionalInfoFooter(),
                    validations,
                    selectMode,
                    renderMode,
                    label,
                    groups,
                    options,
                    hideNumber,
                    writeOnce);
            configure(question);
            return question;
        }
    }
}
