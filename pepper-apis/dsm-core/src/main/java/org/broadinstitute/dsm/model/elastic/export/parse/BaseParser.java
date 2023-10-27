package org.broadinstitute.dsm.model.elastic.export.parse;

import java.lang.reflect.Field;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.structure.DbDateConversion;
import org.broadinstitute.dsm.model.elastic.export.generate.PropertyInfo;

public abstract class BaseParser implements Parser {

    protected static final String TYPE = "type";
    protected PropertyInfo propertyInfo;
    protected String fieldName;
    protected String realm;

    public void setPropertyInfo(PropertyInfo propertyInfo) {
        this.propertyInfo = propertyInfo;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public void setRealm(String realm) {
        this.realm = realm;
    }

    @Override
    public Object parse(String element) {
        Class<?> propertyClass = propertyInfo.getPropertyClass();
        Object elementMap;
        try {
            Field field = propertyClass.getDeclaredField(fieldName);
            if (isNumericTypeField(field)) {
                elementMap = forNumeric(element);
            } else if (isBooleanTypeField(field)) {
                elementMap = forBoolean(element);
            } else if (isDateTypeField(field)) {
                elementMap = forDate(element);
            } else {
                elementMap = forString(element);
            }
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
        return elementMap;
    }

    private boolean isDateTypeField(Field field) {
        return field.getAnnotation(DbDateConversion.class) != null;
    }

    private boolean isBooleanTypeField(Field field) {
        return boolean.class.isAssignableFrom(field.getType()) || Boolean.class.isAssignableFrom(field.getType());
    }

    private boolean isNumericTypeField(Field field) {
        Class<?> fieldType = field.getType();
        return long.class.isAssignableFrom(fieldType)
                || Long.class.isAssignableFrom(fieldType)
                || int.class.isAssignableFrom(fieldType)
                || Integer.class.isAssignableFrom(fieldType);
    }

    abstract Object forNumeric(String value);

    protected abstract Object forBoolean(String value);

    protected abstract Object forDate(String value);

    protected abstract Object forString(String value);

    protected boolean isBoolean(String value) {
        return convertBoolean(value).equalsIgnoreCase(Boolean.TRUE.toString()) || convertBoolean(value).equalsIgnoreCase(
                Boolean.FALSE.toString());
    }

    public String convertBoolean(String value) {
        if ("'1'".equals(value) || "NOT'0'".equals(value) || "'true'".equals(value)) {
            return "true";
        } else if ("'0'".equals(value) || "NOT'1'".equals(value) || "'false'".equals(value)) {
            return "false";
        } else {
            return value;
        }
    }

    public String convertString(String value) {
        if (isWrappedByChar(value)) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    protected boolean isWrappedByChar(String value) {
        return StringUtils.isNotBlank(value) && value.charAt(0) == '\'' && value.charAt(value.length() - 1) == '\'';
    }

    public void setHelperParser(BaseParser parser) {}
}
