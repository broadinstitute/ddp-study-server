package org.broadinstitute.dsm.model.elastic.export.parse;

import org.broadinstitute.dsm.model.elastic.export.ExportFacadePayload;
import org.broadinstitute.dsm.statics.ESObjectConstants;

public abstract class BaseParserFactory {

    public BaseParser of(ExportFacadePayload exportFacadePayload) {
        BaseParser typeParser = getInitialParser();
        if (isDynamicFields(exportFacadePayload.getCamelCaseFieldName())) {
            typeParser = buildDynamicFieldsParser(exportFacadePayload);
        }
        return typeParser;
    }

    protected abstract BaseParser getInitialParser();

    protected BaseParser buildDynamicFieldsParser(ExportFacadePayload exportFacadePayload) {
        BaseParser typeParser;
        DynamicFieldsParser dynamicFieldsParser = new DynamicFieldsParser();
        dynamicFieldsParser.setParser(getInitialParser());
        dynamicFieldsParser.setRealm(exportFacadePayload.getRealm());
        typeParser = dynamicFieldsParser;
        return typeParser;
    }

    protected boolean isDynamicFields(String fieldName) {
        return ESObjectConstants.ADDITIONAL_VALUES_JSON.equals(fieldName) || ESObjectConstants.DATA.equals(fieldName);
    }
}
