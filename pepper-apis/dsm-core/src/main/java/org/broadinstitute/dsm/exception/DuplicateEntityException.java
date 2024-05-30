package org.broadinstitute.dsm.exception;

/**
 * Exception thrown when there is a duplicate value
 * and the user should try their operation again
 * after changing the duplicate value to a unique value
 */
public class DuplicateEntityException extends RuntimeException {

    private final String entityName;

    private final String entityValue;

    /**
     * Create a new one in response to an attempt to save
     * a duplicte value
     * @param entityName the name of the entity, in a way that
     *                   makes sense to the user
     * @param entityValue the value of the entity
     */
    public DuplicateEntityException(String entityName, String entityValue) {
        this.entityName = entityName;
        this.entityValue = entityValue;
    }

    /**
     * The kind of entity that has a duplicate value, such as "filter"
     */
    public String getEntityName() {
        return entityName;
    }

    /**
     * The name of the entity that is duplicated, that the user
     * has control over and can change on a retry
     */
    public String getEntityValue() {
        return entityValue;
    }
}
