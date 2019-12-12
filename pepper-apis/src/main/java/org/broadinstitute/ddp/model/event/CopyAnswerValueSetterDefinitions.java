package org.broadinstitute.ddp.model.event;

import org.broadinstitute.ddp.db.dao.JdbiProfile;
import org.broadinstitute.ddp.exception.DDPException;

public class CopyAnswerValueSetterDefinitions {
    // If we implement this method, then we can use lambdas for the the setter implementations
    public interface StringValueSetter extends ValueSetter<String> {
        @Override
        default Class<String> getValueType() {
            return String.class;
        }
    }

    private static final StringValueSetter PARTICIPANT_PROFILE_FIRST_NAME_SETTER =
            (newValue, participantId, operatorId, handle) -> handle.attach(JdbiProfile.class)
                    .upsertFirstName(participantId, newValue) == 1;

    private static final StringValueSetter PARTICIPANT_PROFILE_LAST_NAME_SETTER =
            (newLastName, participantId, operatorId, handle) -> handle.attach(JdbiProfile.class)
                    .upsertLastName(participantId, newLastName) == 1;

    private static final StringValueSetter OPERATOR_PROFILE_FIRST_NAME_SETTER =
            (newFirstName, participantId, operatorId, handle) -> handle.attach(JdbiProfile.class)
                    .upsertFirstName(operatorId, newFirstName) == 1;

    private static final StringValueSetter OPERATOR_PROFILE_LAST_NAME_SETTER =
            (newLastName, participantId, operatorId, handle) -> handle.attach(JdbiProfile.class)
                    .upsertLastName(operatorId, newLastName) == 1;

    public static ValueSetter<?> findValueSetter(CopyAnswerTarget targetEnum) {
        switch (targetEnum) {
            case PARTICIPANT_PROFILE_LAST_NAME:
                return PARTICIPANT_PROFILE_LAST_NAME_SETTER;
            case PARTICIPANT_PROFILE_FIRST_NAME:
                return PARTICIPANT_PROFILE_FIRST_NAME_SETTER;
            case OPERATOR_PROFILE_LAST_NAME:
                return OPERATOR_PROFILE_LAST_NAME_SETTER;
            case OPERATOR_PROFILE_FIRST_NAME:
                return OPERATOR_PROFILE_FIRST_NAME_SETTER;
            default:
                throw new DDPException("Could not find CopyAnswerTarget definition for:" + targetEnum);
        }
    }

}
