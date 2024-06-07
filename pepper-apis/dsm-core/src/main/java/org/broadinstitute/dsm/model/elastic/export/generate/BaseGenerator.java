package org.broadinstitute.dsm.model.elastic.export.generate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.databind.JsonMappingException;
import org.broadinstitute.dsm.db.structure.DBElement;
import org.broadinstitute.dsm.model.NameValue;
import org.broadinstitute.dsm.model.elastic.Util;
import org.broadinstitute.dsm.model.elastic.export.parse.Parser;
import org.broadinstitute.dsm.util.proxy.jackson.JsonParseException;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;

public abstract class BaseGenerator implements Generator, Collector, GeneratorHelper {

    public static final String DSM_OBJECT = "dsm";
    public static final String PROPERTIES = "properties";
    protected Parser parser;
    protected GeneratorPayload generatorPayload;

    public BaseGenerator(Parser parser, GeneratorPayload generatorPayload) {
        this.parser = Objects.requireNonNull(parser);
        this.generatorPayload = Objects.requireNonNull(generatorPayload);
    }

    public BaseGenerator() {

    }

    @Override
    public void setParser(Parser parser) {
        this.parser = Objects.requireNonNull(parser);
    }

    @Override
    public void setPayload(GeneratorPayload generatorPayload) {
        this.generatorPayload = Objects.requireNonNull(generatorPayload);
    }

    protected NameValue getNameValue() {
        return generatorPayload.getNameValue();
    }

    //wrap Util.getDBElement in protected method so that we can override it in testing class for tests
    protected DBElement getDBElement() {
        return Util.getDBElement(getNameValue().getName());
    }

    protected PropertyInfo getOuterPropertyByAlias() {
        return PropertyInfo.of(getTableAlias());
    }

    protected String getTableAlias() {
        return getDBElement().getTableAlias();
    }

    protected String getPrimaryKey() {
        return getOuterPropertyByAlias().getPrimaryKeyAsCamelCase();
    }

    @Override
    public String getPropertyName() {
        return getOuterPropertyByAlias().getPropertyName();
    }

    public String getFieldName() {
        return generatorPayload.getCamelCaseFieldName();
    }

    @Override
    public Object collect() {
        Object sourceToUpsert;
        try {
            sourceToUpsert = parseJson();
            if (sourceToUpsert == null) {
                sourceToUpsert = parseSingleElement();
            }
        } catch (JsonParseException e) {
            sourceToUpsert = parseSingleElement();
        }
        return sourceToUpsert;
    }

    protected abstract <T> T parseJson();

    protected Map<String, Object> parseJsonToMapFromValue() {
        try {
            return ObjectMapperSingleton.instance().readValue(String.valueOf(getNameValue().getValue()), Map.class);
        } catch (com.fasterxml.jackson.core.JsonParseException | JsonMappingException je) {
            throw new JsonParseException(je.getMessage());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected Object parseSingleElement() {
        Object element = parseElement();
        return getElement(element);
    }

    protected Object parseElement() {
        Object result;
        if (generatorPayload.getNameValues().size() > 1) {
            List<Object> parsedObjects = new ArrayList<>();
            for (NameValue nameValue : generatorPayload.getNameValues()) {
                parser.setFieldName(nameValue.getCamelCaseFieldName());
                parsedObjects.add(parser.parse(getValueForParser(nameValue)));
            }
            result = parsedObjects;
        } else {
            parser.setFieldName(getFieldName());
            result = parser.parse(getValueForParser());

        }
        return result;
    }

    protected abstract String getValueForParser();

    protected abstract String getValueForParser(NameValue nameValue);

    protected Object getElement(Object value) {
        Map<String, Object> result = new HashMap<>();
        List<NameValue> nameValues = generatorPayload.getNameValues();
        if (nameValues.size() > 1) {
            List<Object> values = (List<Object>) value;
            for (int i = 0; i < nameValues.size(); i++) {
                result.put(nameValues.get(i).getCamelCaseFieldName(), values.get(i));
            }
        } else {
            result.put(getFieldName(), value);
        }
        return result;
    }

    public abstract Object construct();

}

