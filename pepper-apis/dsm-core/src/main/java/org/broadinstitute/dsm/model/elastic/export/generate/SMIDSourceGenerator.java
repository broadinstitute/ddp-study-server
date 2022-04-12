package org.broadinstitute.dsm.model.elastic.export.generate;

import java.util.Map;
import java.util.Optional;

import org.broadinstitute.dsm.model.elastic.Util;
import org.broadinstitute.dsm.model.patch.SMIDNameValue;
import org.broadinstitute.dsm.statics.DBConstants;

public class SMIDSourceGenerator extends ParentChildRelationGenerator{

    @Override
    protected Optional<Map<String, Object>> getParentWithId() {
        Optional<Map<String, Object>> parentWithId = super.getParentWithId();
        parentWithId.ifPresent(this::addSMIDType);
        return parentWithId;
    }

    private void addSMIDType(Map<String, Object> stringObjectMap) {
        if (isNewSMIDCreation()) {
            SMIDNameValue nameValue = (SMIDNameValue) generatorPayload.getNameValue();
            stringObjectMap.put(Util.underscoresToCamelCase(DBConstants.SM_ID_TYPE), nameValue.getType());
        }
    }

    private boolean isNewSMIDCreation() {
        return generatorPayload.getNameValue() instanceof SMIDNameValue;
    }
}