package org.broadinstitute.ddp.service.actvityinstancebuilder.context;

import org.broadinstitute.ddp.model.activity.instance.FormInstance;

/**
 * Contains boolean flags used to enable/disable the {@link FormInstance} building process steps.
 *
 * <p>These flags allows to exclude some of steps of {@link FormInstance} building.
 * In the following example rendering and update blocks are excluded:
 * <pre>
 *     createAllEnabledFlags().setRenderContent(false).setUpdateBlockStatuses(false);
 * </pre>
 * Or it is possible to disable all and enable only some steps:
 * <pre>
 *     create().setCreateFormInstance(true).setRenderFormTitleSubtitle(true);
 * </pre>
 */
public class AIBuilderCustomizationFlags {

    private boolean createFormInstance;
    private boolean addChildren;
    private boolean renderContent;
    private boolean renderFormTitleSubtitle;
    private boolean updateBlockStatuses;
    private boolean setDisplayNumbers;

    /**
     * Create {@link AIBuilderCustomizationFlags} with all flags = 'false'
     */
    public static AIBuilderCustomizationFlags create() {
        return new AIBuilderCustomizationFlags();
    }

    /**
     * Create {@link AIBuilderCustomizationFlags} with all flags = 'true'
     */
    public static AIBuilderCustomizationFlags createAllEnabledFlags() {
        return new AIBuilderCustomizationFlags()
                .setCreateFormInstance(true)
                .setAddChildren(true)
                .setRenderContent(true)
                .setRenderFormTitleSubtitle(true)
                .setUpdateBlockStatuses(true)
                .setSetDisplayNumbers(true);
    }

    private AIBuilderCustomizationFlags() {
    }

    public boolean isCreateFormInstance() {
        return createFormInstance;
    }

    public AIBuilderCustomizationFlags setCreateFormInstance(boolean createFormInstance) {
        this.createFormInstance = createFormInstance;
        return this;
    }

    public boolean isAddChildren() {
        return addChildren;
    }

    public AIBuilderCustomizationFlags setAddChildren(boolean addChildren) {
        this.addChildren = addChildren;
        return this;
    }

    public boolean isRenderContent() {
        return renderContent;
    }

    public AIBuilderCustomizationFlags setRenderContent(boolean renderContent) {
        this.renderContent = renderContent;
        return this;
    }

    public boolean isRenderFormTitleSubtitle() {
        return renderFormTitleSubtitle;
    }

    public AIBuilderCustomizationFlags setRenderFormTitleSubtitle(boolean renderFormTitleSubtitle) {
        this.renderFormTitleSubtitle = renderFormTitleSubtitle;
        return this;
    }

    public boolean isUpdateBlockStatuses() {
        return updateBlockStatuses;
    }

    public AIBuilderCustomizationFlags setUpdateBlockStatuses(boolean updateBlockStatuses) {
        this.updateBlockStatuses = updateBlockStatuses;
        return this;
    }

    public boolean isSetDisplayNumbers() {
        return setDisplayNumbers;
    }

    public AIBuilderCustomizationFlags setSetDisplayNumbers(boolean setDisplayNumbers) {
        this.setDisplayNumbers = setDisplayNumbers;
        return this;
    }
}
