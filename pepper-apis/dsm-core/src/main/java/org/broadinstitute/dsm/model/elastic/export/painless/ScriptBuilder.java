package org.broadinstitute.dsm.model.elastic.export.painless;

public interface ScriptBuilder {
    String build();

    default void setPropertyName(String propertyName) {

    }

    default void setUniqueIdentifier(String uniqueIdentifier) {

    }
}
