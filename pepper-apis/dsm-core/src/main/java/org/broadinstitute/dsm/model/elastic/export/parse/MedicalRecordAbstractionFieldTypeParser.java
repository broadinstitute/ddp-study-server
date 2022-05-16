package org.broadinstitute.dsm.model.elastic.export.parse;

public class MedicalRecordAbstractionFieldTypeParser extends DynamicFieldsParser {

    private String type;
    private BaseParser baseParser;

    public MedicalRecordAbstractionFieldTypeParser(BaseParser baseParser) {
        this.baseParser = baseParser;
    }

    public void setType(String type) {
        this.type = type;
    }

    @Override
    public Object parse(String columnName) {
        Object parsedType;
        var fieldType = MedicalRecordAbstractionFieldType.of(type);
        switch (fieldType) {
            case DATE:
                parsedType = forDate(columnName);
                break;
            case TEXT:
            case TEXT_AREA:
            case BUTTON_SELECT:
                parsedType = forString(columnName);
                break;
            case NUMBER:
                parsedType = forNumeric(columnName);
                break;
            case MULTI_OPTIONS:
                parsedType = forMultiOptions(columnName);

            default:
                System.out.println();
        }

        return super.parse(columnName);
    }

    private Object forMultiOptions(String columnName) {
        
        return null;
    }

    @Override
    protected Object forNumeric(String value) {
        return baseParser.forNumeric(type);
    }

    @Override
    protected Object forBoolean(String value) {
        return baseParser.forBoolean(value);
    }

    @Override
    protected Object forDate(String value) {
        return baseParser.forDate(value);
    }

    @Override
    protected Object forString(String value) {
        return baseParser.forString(value);
    }


}
