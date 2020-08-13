package org.broadinstitute.ddp.service;

import java.util.ArrayList;
import java.util.List;

import org.broadinstitute.ddp.content.I18nContentRenderer;
import org.broadinstitute.ddp.db.ActivityDefStore;
import org.broadinstitute.ddp.db.dto.ActivityValidationDto;
import org.broadinstitute.ddp.model.activity.instance.validation.ActivityValidationFailure;
import org.broadinstitute.ddp.pex.PexException;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Performs the activity-level validation of answers with respect to their compatibility
 */
public class ActivityValidationService {
    private static final Logger LOG = LoggerFactory.getLogger(ActivityValidationService.class);

    /**
     * Check if the combination of answers in the form is compatible with each other
     * While isComplete() checks if individual required questions are answered,
     * this method makes a check in the context of an entire form. For each validation,
     * it checks a precondition first and only proceeds with a validation if it succeds
     * @param handle      the jdbi handle
     * @param interpreter the pex interpreter to evaluate expressions
     * @param userGuid the guid of the user (required by the interpreter)
     * @param activityInstanceGuid the guid of the activity instance to validate
     * @param activityId the id of the study activity that should be validated
     * @param languageCodeId the id of the language the error message will be translated to
     */
    public List<ActivityValidationFailure> validate(
            Handle handle,
            PexInterpreter interpreter,
            String userGuid,
            String activityInstanceGuid,
            long activityId,
            long languageCodeId
    ) {
        ActivityDefStore activityStore = ActivityDefStore.getInstance();
        List<ActivityValidationDto> activityValidationDtos = activityStore
                .findUntranslatedActivityValidationDtos(handle, activityId);

        List<ActivityValidationDto> failedDtos = new ArrayList<>();
        for (ActivityValidationDto validationDto: activityValidationDtos) {
            try {
                if (validationDto.getPreconditionText() != null) {
                    boolean preconditionSucceeded = interpreter.eval(
                            validationDto.getPreconditionText(), handle, userGuid, activityInstanceGuid
                    );
                    if (!preconditionSucceeded) {
                        continue;
                    }
                }
                boolean validationFailed = interpreter.eval(validationDto.getExpressionText(), handle, userGuid, activityInstanceGuid);
                if (validationFailed) {
                    failedDtos.add(validationDto);
                }
            } catch (PexException e) {
                // DDP-4124: If the validation fails because we are unable to find all the data (PEX Exception), then just ignore.
                // We'll come back to it as soon as those data are entered
                LOG.warn("Failed to evaluate a PEX expression or precondition. This is not considered an error", e);
            }
        }

        // Render the messages only after we have determined the failed validations instead of during query time.
        List<ActivityValidationFailure> validationFailures = new ArrayList<>();
        var renderer = new I18nContentRenderer();
        for (var failed : failedDtos) {
            String msg = renderer.renderContent(handle, failed.getErrorMessageTemplateId(), languageCodeId);
            validationFailures.add(new ActivityValidationFailure(msg, failed.getAffectedQuestionStableIds()));
        }

        return validationFailures;
    }

}
