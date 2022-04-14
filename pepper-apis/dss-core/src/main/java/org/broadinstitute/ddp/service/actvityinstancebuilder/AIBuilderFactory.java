package org.broadinstitute.ddp.service.actvityinstancebuilder;

import org.broadinstitute.ddp.model.activity.instance.ActivityInstance;
import org.broadinstitute.ddp.service.actvityinstancebuilder.context.AIBuilderParams;
import org.broadinstitute.ddp.service.actvityinstancebuilder.form.FormInstanceCreator;
import org.broadinstitute.ddp.service.actvityinstancebuilder.form.FormInstanceCreatorHelper;
import org.broadinstitute.ddp.service.actvityinstancebuilder.form.FormSectionCreator;
import org.broadinstitute.ddp.service.actvityinstancebuilder.form.SectionIconCreator;
import org.broadinstitute.ddp.service.actvityinstancebuilder.form.block.FormBlockCreator;
import org.broadinstitute.ddp.service.actvityinstancebuilder.form.block.FormBlockCreatorHelper;
import org.broadinstitute.ddp.service.actvityinstancebuilder.form.block.question.PicklistCreatorHelper;
import org.broadinstitute.ddp.service.actvityinstancebuilder.form.block.question.MatrixCreatorHelper;
import org.broadinstitute.ddp.service.actvityinstancebuilder.form.block.question.QuestionCreator;
import org.broadinstitute.ddp.service.actvityinstancebuilder.form.block.question.QuestionCreatorHelper;
import org.broadinstitute.ddp.service.actvityinstancebuilder.form.block.question.ValidationRuleCreator;
import org.jdbi.v3.core.Handle;


/**
 * AI builder main factory: through this factory it is possible to get instances
 * of all classes (creators, helpers, etc) participating in {@link ActivityInstance} building.
 * It is possible to customize any of these classes via the factory setter methods (for example it is
 * helpful in test cases).
 * Also it provides {@link ActivityInstanceFromDefinitionBuilder} creation - via static methods:
 * <ul>
 * <li>{@link #createAIBuilder(Handle, AIBuilderParams)} - this version utilizes a default implementation of
 *    the factory class - {@link AIBuilderFactory}</li>
 * <li>{@link #createAIBuilder(AIBuilderFactory, Handle, AIBuilderParams)} - this method allows to specify
 *    custom version of factory class (rewriting any parts of AI builder code).</li>
 * </ul>
 */
public class AIBuilderFactory {

    /**
     * ActivityInstance builder creator method.
     * In this version of creator method used a default {@link AIBuilderFactory}.
     *
     * @param handle jdbi Handle
     * @param params object with parameters
     * @return created AI builder
     */
    public static ActivityInstanceFromDefinitionBuilder createAIBuilder(
            Handle handle, AIBuilderParams params) {
        return new ActivityInstanceFromDefinitionBuilder(createAIBuilderFactory(), handle, params);
    }

    /**
     * ActivityInstance builder creator method.
     * In this version it is possible to specify custom version of {@link AIBuilderFactory}.
     *
     * @param aiBuilderFactory custom {@link AIBuilderFactory}
     * @param handle jdbi Handle
     * @param params object with parameters
     * @return created AI builder
     */
    public static ActivityInstanceFromDefinitionBuilder createAIBuilder(
            AIBuilderFactory aiBuilderFactory, Handle handle, AIBuilderParams params) {
        return new ActivityInstanceFromDefinitionBuilder(aiBuilderFactory, handle, params);
    }

    /**
     * Creates default {@link AIBuilderFactory}
     */
    public static AIBuilderFactory createAIBuilderFactory() {
        return new AIBuilderFactory();
    }


    private TemplateRenderHelper templateRenderHelper = new TemplateRenderHelper();

    private FormInstanceCreator formInstanceCreator = new FormInstanceCreator();
    private FormSectionCreator formSectionCreator = new FormSectionCreator();
    private SectionIconCreator sectionIconCreator = new SectionIconCreator();
    private FormBlockCreator formBlockCreator = new FormBlockCreator();
    private QuestionCreator questionCreator = new QuestionCreator();

    private FormInstanceCreatorHelper formInstanceCreatorHelper = new FormInstanceCreatorHelper();
    private FormBlockCreatorHelper formBlockCreatorHelper = new FormBlockCreatorHelper();
    private PicklistCreatorHelper picklistCreatorHelper = new PicklistCreatorHelper();
    private MatrixCreatorHelper matrixCreatorHelper = new MatrixCreatorHelper();
    private QuestionCreatorHelper questionCreatorHelper = new QuestionCreatorHelper();
    private ValidationRuleCreator validationRuleCreator = new ValidationRuleCreator();


    private AIBuilderFactory() {
    }

    public TemplateRenderHelper getTemplateRenderHelper() {
        return templateRenderHelper;
    }

    public AIBuilderFactory setTemplateRenderHelper(TemplateRenderHelper templateRenderHelper) {
        this.templateRenderHelper = templateRenderHelper;
        return this;
    }

    public FormInstanceCreator getFormInstanceCreator() {
        return formInstanceCreator;
    }

    public AIBuilderFactory setFormInstanceCreator(FormInstanceCreator formInstanceCreator) {
        this.formInstanceCreator = formInstanceCreator;
        return this;
    }

    public FormSectionCreator getFormSectionCreator() {
        return formSectionCreator;
    }

    public AIBuilderFactory setFormSectionCreator(FormSectionCreator formSectionCreator) {
        this.formSectionCreator = formSectionCreator;
        return this;
    }

    public SectionIconCreator getSectionIconCreator() {
        return sectionIconCreator;
    }

    public AIBuilderFactory setSectionIconCreator(SectionIconCreator sectionIconCreator) {
        this.sectionIconCreator = sectionIconCreator;
        return this;
    }

    public FormBlockCreator getFormBlockCreator() {
        return formBlockCreator;
    }

    public AIBuilderFactory setFormBlockCreator(FormBlockCreator formBlockCreator) {
        this.formBlockCreator = formBlockCreator;
        return this;
    }

    public QuestionCreator getQuestionCreator() {
        return questionCreator;
    }

    public AIBuilderFactory setQuestionCreator(QuestionCreator questionCreator) {
        this.questionCreator = questionCreator;
        return this;
    }

    public FormInstanceCreatorHelper getFormInstanceCreatorHelper() {
        return formInstanceCreatorHelper;
    }

    public AIBuilderFactory setFormInstanceCreatorHelper(FormInstanceCreatorHelper formInstanceCreatorHelper) {
        this.formInstanceCreatorHelper = formInstanceCreatorHelper;
        return this;
    }

    public FormBlockCreatorHelper getFormBlockCreatorHelper() {
        return formBlockCreatorHelper;
    }

    public AIBuilderFactory setFormBlockCreatorHelper(FormBlockCreatorHelper formBlockCreatorHelper) {
        this.formBlockCreatorHelper = formBlockCreatorHelper;
        return this;
    }

    public PicklistCreatorHelper getPicklistCreatorHelper() {
        return picklistCreatorHelper;
    }


    public MatrixCreatorHelper getMatrixCreatorHelper() {
        return matrixCreatorHelper;
    }

    public AIBuilderFactory setPicklistCreatorHelper(PicklistCreatorHelper picklistCreatorHelper) {
        this.picklistCreatorHelper = picklistCreatorHelper;
        return this;
    }

    public QuestionCreatorHelper getQuestionCreatorHelper() {
        return questionCreatorHelper;
    }

    public AIBuilderFactory setQuestionCreatorHelper(QuestionCreatorHelper questionCreatorHelper) {
        this.questionCreatorHelper = questionCreatorHelper;
        return this;
    }

    public ValidationRuleCreator getValidationRuleCreator() {
        return validationRuleCreator;
    }

    public AIBuilderFactory setValidationRuleCreator(ValidationRuleCreator validationRuleCreator) {
        this.validationRuleCreator = validationRuleCreator;
        return this;
    }
}
