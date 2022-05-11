package org.broadinstitute.dsm.model;

public enum QuestionType {
    CHECKBOX("CHECKBOX"),
    NUMBER("NUMBER"),
    BOOLEAN("BOOLEAN"),
    COMPOSITE("COMPOSITE"),
    AGREEMENT("AGREEMENT"),
    MATRIX("MATRIX"),
    DATE("DATE"),
    OPTIONS("OPTIONS"),
    TEXT("TEXT"),
    JSON_ARRAY("JSON_ARRAY");

    private final String value;

    QuestionType(String value) {
        this.value = value;
    }

    public static QuestionType getByValue(String value) {
        for (QuestionType questionType : values()) {
            if (questionType.value.equals(value)) {
                return questionType;
            }
        }
        return TEXT;
    }
}
