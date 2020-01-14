package org.broadinstitute.ddp.model.event;

import org.broadinstitute.ddp.db.dao.JdbiProfile;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.instance.answer.DateValue;
import org.broadinstitute.ddp.model.copy.CopyLocationType;

public class CopyAnswerValueSetterDefinitions {

    public interface StringValueSetter extends ValueSetter<String> {
        @Override
        default Class<String> getValueType() {
            return String.class;
        }
    }

    public interface DateValueSetter extends ValueSetter<DateValue> {
        @Override
        default Class<DateValue> getValueType() {
            return DateValue.class;
        }
    }

    private static final DateValueSetter PARTICIPANT_PROFILE_BIRTH_DATE_SETTER =
            (newValue, participantId, operatorId, handle) -> handle.attach(JdbiProfile.class)
                    .upsertBirthDate(participantId, newValue.asLocalDate().orElseThrow(
                            () -> new DDPException("Could not copy invalid date")));

    private static final StringValueSetter PARTICIPANT_PROFILE_FIRST_NAME_SETTER =
            (newValue, participantId, operatorId, handle) -> handle.attach(JdbiProfile.class)
                    .upsertFirstName(participantId, newValue);

    private static final StringValueSetter PARTICIPANT_PROFILE_LAST_NAME_SETTER =
            (newLastName, participantId, operatorId, handle) -> handle.attach(JdbiProfile.class)
                    .upsertLastName(participantId, newLastName);

    private static final StringValueSetter OPERATOR_PROFILE_FIRST_NAME_SETTER =
            (newFirstName, participantId, operatorId, handle) -> handle.attach(JdbiProfile.class)
                    .upsertFirstName(operatorId, newFirstName);

    private static final StringValueSetter OPERATOR_PROFILE_LAST_NAME_SETTER =
            (newLastName, participantId, operatorId, handle) -> handle.attach(JdbiProfile.class)
                    .upsertLastName(operatorId, newLastName);

    public static ValueSetter<?> findValueSetter(CopyLocationType targetEnum) {
        switch (targetEnum) {
            case PARTICIPANT_PROFILE_BIRTH_DATE:
                return PARTICIPANT_PROFILE_BIRTH_DATE_SETTER;
            case PARTICIPANT_PROFILE_LAST_NAME:
                return PARTICIPANT_PROFILE_LAST_NAME_SETTER;
            case PARTICIPANT_PROFILE_FIRST_NAME:
                return PARTICIPANT_PROFILE_FIRST_NAME_SETTER;
            case OPERATOR_PROFILE_LAST_NAME:
                return OPERATOR_PROFILE_LAST_NAME_SETTER;
            case OPERATOR_PROFILE_FIRST_NAME:
                return OPERATOR_PROFILE_FIRST_NAME_SETTER;
            default:
                throw new DDPException("Could not find CopyLocationType definition for:" + targetEnum);
        }
    }
}
