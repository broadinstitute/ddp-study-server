package org.broadinstitute.dsm.model.elastic.export.generate;

import java.util.Map;
import java.util.Objects;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import org.broadinstitute.dsm.db.structure.DBElement;
import org.broadinstitute.dsm.model.NameValue;
import org.broadinstitute.dsm.model.elastic.Util;
import org.broadinstitute.dsm.model.elastic.export.parse.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseGenerator implements Generator, Collector {

    private static final Logger logger = LoggerFactory.getLogger(BaseGenerator.class);

    public static final String DSM_OBJECT = "dsm";
    public static final String PROPERTIES = "properties";
    public static final String ID = "id";
    protected static final Gson GSON = new Gson();
    protected final Parser parser;
    protected GeneratorPayload generatorPayload;
    private DBElement dbElement;

    public BaseGenerator(Parser parser, GeneratorPayload generatorPayload) {
        this.parser = Objects.requireNonNull(parser);
        this.generatorPayload = Objects.requireNonNull(generatorPayload);
        dbElement = Util.getDBElement(getNameValue().getName());
    }

    //setter method to set dbElement for testing only!!!
    public void setDBElement(DBElement dbElement) {
        this.dbElement = dbElement;
    }

    protected NameValue getNameValue() {
        return generatorPayload.getNameValue();
    }

    //wrap Util.getDBElement in protected method so that we can override it in testing class for tests
    protected DBElement getDBElement() {
        return dbElement;
    }

    protected PropertyInfo getOuterPropertyByAlias() {
        return Util.TABLE_ALIAS_MAPPINGS.get(getDBElement().getTableAlias());
    }

    @Override
    public Object collect() {
        Object sourceToUpsert;
        try {
            sourceToUpsert = parseJson();
        } catch (JsonSyntaxException jse) {
            sourceToUpsert = parseSingleElement();
        }
        return sourceToUpsert;
    }

    protected abstract <T> T parseJson();

    protected Map<String, Object> parseJsonToMapFromValue() {
        return GSON.fromJson(String.valueOf(getNameValue().getValue()), Map.class);
    }

    protected abstract <T> T parseSingleElement();

    protected Object getFieldWithElement() {
        Object fieldElementMap;
        Object element = parser.parse(String.valueOf(getNameValue().getValue()));
        if (getOuterPropertyByAlias().isCollection()) {
            fieldElementMap = getElementWithId(element);
        } else {
            fieldElementMap = getElement(element);
        }
        return fieldElementMap;
    }

    protected abstract Object getElementWithId(Object element);

    protected abstract Map<String, Object> getElement(Object element);

    protected Object constructByPropertyType() {
        Object constructedObject;
        if (getOuterPropertyByAlias().isCollection()) {
            constructedObject = constructCollection();
        } else {
            constructedObject = constructSingleElement();
        }
        return constructedObject;
    }

    protected abstract Object constructSingleElement();

    protected abstract Object constructCollection();

    public static class PropertyInfo {

        private String propertyName;
        private boolean isCollection;

        public PropertyInfo(String propertyName, boolean isCollection) {
            this.propertyName = Objects.requireNonNull(propertyName);
            this.isCollection = isCollection;
        }

        public void setIsCollection(boolean isCollection) {
            this.isCollection = isCollection;
        }

        public String getPropertyName() {
            return propertyName;
        }

        public boolean isCollection() {
            return isCollection;
        }
    }
    
}

