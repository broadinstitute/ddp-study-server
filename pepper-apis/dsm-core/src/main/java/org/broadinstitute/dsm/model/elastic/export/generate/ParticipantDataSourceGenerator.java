package org.broadinstitute.dsm.model.elastic.export.generate;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ParticipantDataSourceGenerator extends CollectionSourceGenerator {

    private static final Logger logger = LoggerFactory.getLogger(ParticipantDataSourceGenerator.class);

    @Override
    protected List<Map<String, Object>> getCollectionElementMap(Map<String, Object> element) {
        if (!isFirstParticipantDataCreation()) {
            return super.getCollectionElementMap(element);
        }
        Map<String, Object> collectionElementMap = super.getCollectionElementMap(element).get(0);
        fillMapWithParticipantDataFields(collectionElementMap);
        return List.of(collectionElementMap);
    }

    private boolean isFirstParticipantDataCreation() {
        //if object returned by getNameValue is type of ParticipantDataNameValue
        //means that record for this concrete participant in ddp_participant_data table does not exist yet
        return generatorPayload.getNameValue() instanceof ParticipantDataNameValue;
    }

    private void fillMapWithParticipantDataFields(Map<String, Object> collectionElementMap) {
        ParticipantDataNameValue nameValue = (ParticipantDataNameValue) generatorPayload.getNameValue();
        Field[] participantNameValueFields = nameValue.getClass().getDeclaredFields();
        for (Field field : participantNameValueFields) {
            field.setAccessible(true);
            try {
                collectionElementMap.put(field.getName(), field.get(nameValue));
            } catch (IllegalAccessException ignore) {
                logger.warn("IllegalAccessException while fillMapWithParticipantDataFields");
            }
        }
    }

}
