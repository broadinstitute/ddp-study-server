package org.broadinstitute.ddp.constants;

/**
 * Error messages used when logging an error or throwing an exception.
 */
public class ErrorMessages {
    public static final String ARG_CANNOT_BE_NULL = "%s cannot be null";
    // "Could not create answer", "Could not fetch question" ...
    public static final String COULD_NOT_OPERATE_ENTITY = "Could not %s %s";
    // "Could not fetch answer with id = 15"
    public static final String COULD_NOT_OPERATE_ENTITY_WITH_ID = "Could not %s %s with id = %d";
    // "There is no picklist answer(s) for the answer with id = 123"
    public static final String NO_DATA_FOR_ENTITY_ID = "There is no %s(s) for the %s with id = %d";
    // "Expected to insert 1 column(s) but updated 2"
    public static final String UNEXPECTED_NUM_ROWS_CHANGED = "Expected to %s %d column(s) but changed %d";
    public static final String UNEXPECTED_NUM_KEYS_GENERATED = "Expected to generate %d keys but generated %d";
    public static final String MALFORMED_JSON = "'%s' was expected to be of the type '%s', but it's not";
}
