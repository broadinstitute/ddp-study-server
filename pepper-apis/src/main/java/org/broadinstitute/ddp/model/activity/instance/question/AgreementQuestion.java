package org.broadinstitute.ddp.model.activity.instance.question;

import java.util.List;
import java.util.Optional;

import org.broadinstitute.ddp.model.activity.instance.answer.AgreementAnswer;
import org.broadinstitute.ddp.model.activity.instance.validation.RequiredRule;
import org.broadinstitute.ddp.model.activity.instance.validation.Rule;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.model.activity.types.RuleType;

public final class AgreementQuestion extends Question<AgreementAnswer> {

    public AgreementQuestion(
            String stableId,
            long promptTemplateId,
            boolean isRestricted,
            boolean isDeprecated,
            Long additionalInfoHeaderTemplateId,
            Long additionalInfoFooterTemplateId,
            List<AgreementAnswer> answers,
            List<Rule<AgreementAnswer>> validations
    ) {
        super(QuestionType.AGREEMENT,
                stableId,
                promptTemplateId,
                isRestricted,
                isDeprecated,
                additionalInfoHeaderTemplateId,
                additionalInfoFooterTemplateId,
                answers,
                validations);
    }

    public AgreementQuestion(
            String stableId,
            long promptTemplateId,
            List<AgreementAnswer> answers,
            List<Rule<AgreementAnswer>> validations
    ) {
        this(stableId, promptTemplateId, false, false, null, null, answers, validations);
    }

    @Override
    public boolean passesDeferredValidations() {
        if (!super.passesDeferredValidations(answers)) {
            return false;
        }
        Optional<Rule> rule = validations.stream().filter(r -> r.getRuleType().equals(RuleType.REQUIRED)).findFirst();
        // No rule, so the question is complete
        if (!rule.isPresent()) {
            return true;
        }
        // The rule exists, so no answers to check
        if (answers.isEmpty()) {
            return false;
        }
        RequiredRule<AgreementAnswer> requiredRule = (RequiredRule<AgreementAnswer>) rule.get();
        for (AgreementAnswer answer : answers) {
            // For the AgreementAnswer the RequiredRule is a no-op now
            // It effectively merely checks if the answer or it's value is NULL
            if (!requiredRule.validate(this, answer)) {
                return false;
            }
            if (!answer.getValue()) {
                return false;
            }
        }
        return true;
    }

}
