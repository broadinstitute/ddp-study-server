package org.broadinstitute.ddp.pex;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.broadinstitute.ddp.model.activity.instance.answer.DateValue;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.activity.types.QuestionType;

/**
 * Fetch data from the data source. Only used internally in pex interpreter.
 */
class PexFetcher {

    /**
     * Get the latest activity instance status for given activity.
     *
     * @param ictx         the interpreter context
     * @param studyGuid    the study
     * @param activityCode the activity
     * @return status code, if there is an activity instance
     */
    Optional<InstanceStatusType> findLatestActivityInstanceStatus(InterpreterContext ictx, String studyGuid, String activityCode) {
        try {
            return ictx.getHandle().attach(PexDao.class)
                    .findLatestActivityInstanceStatus(ictx.getUserGuid(), studyGuid, activityCode);
        } catch (Exception e) {
            throw new PexFetchException("Could not determine latest activity instance status for study "
                    + studyGuid + " and activity " + activityCode, e);
        }
    }

    /**
     * Determine the question type. And activity instance needs to exist for the user for given activity,
     * and question needs to be active during the revision time of the instance.
     *
     * @param ictx         the interpreter context
     * @param studyGuid    the study
     * @param activityCode the activity
     * @param stableId     the question stable id
     * @return question type, if found
     */
    Optional<QuestionType> findQuestionType(InterpreterContext ictx, String studyGuid, String activityCode, String stableId) {
        try {
            return ictx.getHandle().attach(PexDao.class)
                    .findQuestionType(ictx.getUserGuid(), studyGuid, activityCode, stableId);
        } catch (Exception e) {
            throw new PexFetchException("Could not determine question type for form "
                    + activityCode + " question " + stableId, e);
        }
    }

    /**
     * Get latest boolean answer value for given question. An activity instance needs to exist for user and activity.
     *
     * @param ictx         the interpreter context
     * @param activityCode the activity
     * @param stableId     the question stable id
     * @return stream of boolean values
     */
    Boolean findLatestBoolAnswer(InterpreterContext ictx, String activityCode, String stableId,
                                 long studyId) {
        try {
            return ictx.getHandle().attach(PexDao.class)
                    .findLatestBoolAnswer(ictx.getUserGuid(), activityCode, stableId, studyId);
        } catch (Exception e) {
            throw new PexFetchException("Could not fetch boolean answers for form "
                    + activityCode + " question " + stableId, e);
        }
    }

    /**
     * Get a specific boolean answer value for given question.
     *
     * @param ictx                 the interpreter context
     * @param activityCode         the activity
     * @param activityInstanceGuid the activity instance guid
     * @param stableId             the question stable id
     * @return text value
     */
    Boolean findSpecificBoolAnswer(InterpreterContext ictx, String activityCode, String activityInstanceGuid, String stableId) {
        try {
            PexDao pexDao =  ictx.getHandle().attach(PexDao.class);
            return pexDao.findSpecificBoolAnswer(activityInstanceGuid, stableId);
        } catch (Exception e) {
            throw new PexFetchException("Could not fetch boolean answer for form "
                    + activityCode + " of instance " + activityInstanceGuid + ", question" + stableId, e);
        }
    }

    /**
     * Get latest date answer value for given question. An activity instance needs to exist for user and activity.
     *
     * @param ictx         the interpreter context
     * @param activityCode the activity
     * @param stableId     the question stable id
     * @return possibly a date value
     */
    Optional<DateValue> findLatestDateAnswer(InterpreterContext ictx, String activityCode, String stableId, long studyId) {
        try {
            return Optional.ofNullable(ictx.getHandle().attach(PexDao.class)
                    .findLatestDateAnswer(ictx.getUserGuid(), activityCode, stableId, studyId));
        } catch (Exception e) {
            throw new PexFetchException("Could not fetch date answer for form "
                    + activityCode + " question " + stableId, e);
        }
    }

    /**
     * Get a specific date answer value for given question.
     *
     * @param ictx                 the interpreter context
     * @param activityCode         the activity
     * @param activityInstanceGuid the activity instance guid
     * @param stableId             the question stable id
     * @return possibly a date value
     */
    Optional<DateValue> findSpecificDateAnswer(InterpreterContext ictx, String activityCode, String activityInstanceGuid, String stableId) {
        try {
            return Optional.ofNullable(ictx.getHandle().attach(PexDao.class)
                    .findSpecificDateAnswer(activityInstanceGuid, stableId));
        } catch (Exception e) {
            throw new PexFetchException("Could not fetch date answer for form "
                    + activityCode + " of instance " + activityInstanceGuid + ", question" + stableId, e);
        }
    }

    /**
     * Get latest text answer value for given question. An activity instance needs to exist for user and activity.
     *
     * @param ictx         the interpreter context
     * @param activityCode the activity
     * @param stableId     the question stable id
     * @return stream of text values
     */
    String findLatestTextAnswer(InterpreterContext ictx, String activityCode, String stableId, long studyId) {
        try {
            return ictx.getHandle().attach(PexDao.class)
                    .findLatestTextAnswer(ictx.getUserGuid(), activityCode, stableId, studyId);
        } catch (Exception e) {
            throw new PexFetchException("Could not fetch text answers for form "
                    + activityCode + " question " + stableId, e);
        }
    }

    /**
     * Get a specific text answer value for given question.
     *
     * @param ictx                 the interpreter context
     * @param activityCode         the activity
     * @param activityInstanceGuid the activity instance guid
     * @param stableId             the question stable id
     * @return text value
     */
    String findSpecificTextAnswer(InterpreterContext ictx, String activityCode, String activityInstanceGuid, String stableId) {
        try {
            return ictx.getHandle().attach(PexDao.class)
                    .findSpecificTextAnswer(activityInstanceGuid, stableId);
        } catch (Exception e) {
            throw new PexFetchException("Could not fetch text answser for form "
                    + activityCode + " of instance " + activityInstanceGuid + ", question" + stableId, e);
        }
    }

    /**
     * Get all picklist answers as list of selected option stable ids for given question. An activity instance needs to
     * exist for user and activity.
     *
     * @param ictx         the interpreter context
     * @param activityCode the activity
     * @param stableId     the question stable id
     * @return stream of selected option lists, of which may be empty
     */
    List<String> findLatestPicklistAnswer(InterpreterContext ictx, String activityCode, String stableId, long studyId) {
        try {
            String result = ictx.getHandle().attach(PexDao.class)
                    .findLatestPicklistAnswer(ictx.getUserGuid(), activityCode, stableId, studyId);
            if (result != null) {
                return Arrays.asList(result.split(","));
            } else {
                return null;
            }
        } catch (Exception e) {
            throw new PexFetchException("Could not fetch picklist answers for form "
                    + activityCode + " question " + stableId, e);
        }
    }

    /**
     * Get a specific picklist answer value for given question.
     *
     * @param ictx                 the interpreter context
     * @param activityCode         the activity
     * @param activityInstanceGuid the activity instance guid
     * @param stableId             the question stable id
     * @return text value
     */
    List<String> findSpecificPicklistAnswer(InterpreterContext ictx, String activityCode, String activityInstanceGuid, String stableId) {
        try {
            String result = ictx.getHandle().attach(PexDao.class)
                    .findSpecificPicklistAnswer(activityInstanceGuid, stableId);
            if (result != null) {
                return Arrays.asList(result.split(","));
            } else {
                return null;
            }
        } catch (Exception e) {
            throw new PexFetchException("Could not fetch picklist answers for form "
                    + activityCode + " question " + stableId, e);
        }
    }

    /**
     * Get latest numeric answer value of integer type for given question. An activity instance needs to exist for user and activity.
     *
     * @param ictx         the interpreter context
     * @param activityCode the activity
     * @param stableId     the question stable id
     * @return numeric integer answer value
     */
    Long findLatestNumericIntegerAnswer(InterpreterContext ictx, String activityCode, String stableId, long studyId) {
        try {
            return ictx.getHandle().attach(PexDao.class)
                    .findLatestNumericIntegerAnswer(ictx.getUserGuid(), activityCode, stableId, studyId);
        } catch (Exception e) {
            throw new PexFetchException("Could not fetch latest numeric integer answer for form "
                    + activityCode + " question " + stableId, e);
        }
    }

    /**
     * Get a specific numeric answer value of integer type for given question.
     *
     * @param ictx                 the interpreter context
     * @param activityCode         the activity
     * @param activityInstanceGuid the activity instance guid
     * @param stableId             the question stable id
     * @return numeric integer answer value
     */
    Long findSpecificNumericIntegerAnswer(InterpreterContext ictx, String activityCode, String activityInstanceGuid, String stableId) {
        try {
            return ictx.getHandle().attach(PexDao.class)
                    .findSpecificNumericIntegerAnswer(activityInstanceGuid, stableId);
        } catch (Exception e) {
            throw new PexFetchException("Could not fetch specific numeric integer answer for form "
                    + activityCode + " of instance " + activityInstanceGuid + ", question" + stableId, e);
        }
    }
}
