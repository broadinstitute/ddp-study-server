package org.broadinstitute.ddp.service.actvityinstancebuilder;

import org.broadinstitute.ddp.model.activity.instance.ActivityInstance;
import org.broadinstitute.ddp.service.actvityinstancebuilder.form.FormInstanceCreator;
import org.broadinstitute.ddp.service.actvityinstancebuilder.form.FormInstanceCreatorHelper;
import org.broadinstitute.ddp.service.actvityinstancebuilder.form.FormSectionCreator;
import org.broadinstitute.ddp.service.actvityinstancebuilder.form.SectionIconCreator;
import org.broadinstitute.ddp.service.actvityinstancebuilder.form.block.FormBlockCreator;
import org.broadinstitute.ddp.service.actvityinstancebuilder.form.block.FormBlockCreatorHelper;
import org.broadinstitute.ddp.service.actvityinstancebuilder.form.block.question.PicklistCreatorHelper;
import org.broadinstitute.ddp.service.actvityinstancebuilder.form.block.question.QuestionCreator;
import org.broadinstitute.ddp.service.actvityinstancebuilder.form.block.question.QuestionCreatorHelper;
import org.broadinstitute.ddp.service.actvityinstancebuilder.form.block.question.ValidationRuleCreator;

/**
 * Creates instances of Creator-classes providing {@link ActivityInstance} building.
 * The Creators constructed once at the beginning of the building process.
 */
public class AICreatorsFactory {

    private final FormInstanceCreator formInstanceCreator = new FormInstanceCreator();
    private final FormSectionCreator formSectionCreator = new FormSectionCreator();
    private final SectionIconCreator sectionIconCreator = new SectionIconCreator();
    private final FormBlockCreator formBlockCreator = new FormBlockCreator();
    private final QuestionCreator questionCreator = new QuestionCreator();

    private final FormInstanceCreatorHelper formInstanceCreatorHelper = new FormInstanceCreatorHelper();
    private final FormBlockCreatorHelper formBlockCreatorHelper = new FormBlockCreatorHelper();
    private final PicklistCreatorHelper picklistCreatorHelper = new PicklistCreatorHelper();
    private final QuestionCreatorHelper questionCreatorHelper = new QuestionCreatorHelper();
    private final ValidationRuleCreator validationRuleCreator = new ValidationRuleCreator();

    public FormInstanceCreator getFormInstanceCreator() {
        return formInstanceCreator;
    }

    public FormSectionCreator getFormSectionCreator() {
        return formSectionCreator;
    }

    public SectionIconCreator getSectionIconCreator() {
        return sectionIconCreator;
    }

    public FormBlockCreator getFormBlockCreator() {
        return formBlockCreator;
    }

    public QuestionCreator getQuestionCreator() {
        return questionCreator;
    }

    public FormInstanceCreatorHelper getFormInstanceCreatorHelper() {
        return formInstanceCreatorHelper;
    }

    public FormBlockCreatorHelper getFormBlockCreatorHelper() {
        return formBlockCreatorHelper;
    }

    public PicklistCreatorHelper getPicklistCreatorHelper() {
        return picklistCreatorHelper;
    }

    public QuestionCreatorHelper getQuestionCreatorHelper() {
        return questionCreatorHelper;
    }

    public ValidationRuleCreator getValidationRuleCreator() {
        return validationRuleCreator;
    }
}
