package org.broadinstitute.ddp.util;

import java.util.List;

import org.apache.commons.lang3.StringUtils;

public class JsonValidationError {

    private List<String> propertyPath;
    private String message;
    private Object invalidValue;

    public JsonValidationError(List<String> path, String message, Object invalidValue) {
        this.propertyPath = path;
        this.message = message;
        this.invalidValue = invalidValue;
    }

    public List<String> getPropertyPath() {
        return propertyPath;
    }

    /**
     * Returns property path list as string separated by .'s.
     */
    public String getPropertyPathAsString() {
        if (propertyPath != null) {
            return StringUtils.join(getPropertyPath(), ".");
        } else {
            return "";
        }
    }

    public String getMessage() {
        return message;
    }

    public Object getInvalidValue() {
        return invalidValue;
    }

    /**
     * If property path exist, return property path as string with messages, otherwise return message.
     */
    public String toDisplayMessage() {
        if (propertyPath != null && !propertyPath.isEmpty()) {
            return "Property '" + String.join(".", propertyPath) + "' " + message;
        } else {
            return message;
        }
    }
}
