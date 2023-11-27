package org.broadinstitute.dsm.model.elastic.export.generate;

import java.util.Map;
import java.util.Optional;

import org.broadinstitute.dsm.model.elastic.converters.camelcase.CamelCaseConverter;
import org.broadinstitute.dsm.model.patch.SMIDNameValue;
import org.broadinstitute.dsm.statics.DBConstants;

public class SMIDSourceGenerator extends ParentChildRelationGenerator {

    @Override
    protected Optional<Map<String, Object>> getAdditionalData() {
        Optional<Map<String, Object>> parentWithId = super.getAdditionalData();
        parentWithId.ifPresent(this::addSMIDType);
        return parentWithId;
    }

    private void addSMIDType(Map<String, Object> stringObjectMap) {
        if (isNewSMIDCreation()) {
            SMIDNameValue nameValue = (SMIDNameValue) generatorPayload.getNameValue();
            stringObjectMap.put(CamelCaseConverter.of(DBConstants.SM_ID_TYPE).convert(), nameValue.getType());
        }
    }

    private boolean isNewSMIDCreation() {
        return generatorPayload.getNameValue() instanceof SMIDNameValue;
    }
}
