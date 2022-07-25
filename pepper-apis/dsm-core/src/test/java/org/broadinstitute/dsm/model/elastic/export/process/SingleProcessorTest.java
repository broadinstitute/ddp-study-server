package org.broadinstitute.dsm.model.elastic.export.process;

import static org.broadinstitute.dsm.model.patch.Patch.PARTICIPANT_ID;

import java.util.Map;
import java.util.Optional;

import org.broadinstitute.dsm.db.Participant;
import org.broadinstitute.dsm.model.NameValue;
import org.broadinstitute.dsm.model.elastic.ESDsm;
import org.broadinstitute.dsm.model.elastic.export.generate.BaseGenerator;
import org.broadinstitute.dsm.model.elastic.export.generate.GeneratorFactory;
import org.broadinstitute.dsm.model.elastic.export.generate.GeneratorPayload;
import org.broadinstitute.dsm.model.elastic.export.generate.PropertyInfo;
import org.broadinstitute.dsm.model.elastic.export.generate.SingleSourceGenerator;
import org.broadinstitute.dsm.model.elastic.export.generate.SourceGeneratorFactory;
import org.broadinstitute.dsm.model.elastic.export.parse.DynamicFieldsParser;
import org.broadinstitute.dsm.model.elastic.export.parse.ValueParser;
import org.broadinstitute.dsm.model.patch.Patch;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.junit.Assert;
import org.junit.Test;

public class SingleProcessorTest {

    private static final String ASSIGNEE_ID_TISSUE_VALUE = "assignee_id_tissue";
    private static final String ASSIGNEE_ID_TISSUE = "r.assigneeIdTissue";

    @Test
    public void processExisting() {
        PropertyInfo propertyInfo = PropertyInfo.TABLE_ALIAS_MAPPINGS.get(DBConstants.DDP_PARTICIPANT_ALIAS);
        ValueParser valueParser = new ValueParser();
        valueParser.setPropertyInfo(propertyInfo);

        DynamicFieldsParser dynamicFieldsParser = new DynamicFieldsParser();
        dynamicFieldsParser.setHelperParser(valueParser);
        dynamicFieldsParser.setDisplayType("TEXT");

        GeneratorFactory sourceGeneratorFactory = new SourceGeneratorFactory();
        BaseGenerator generator = sourceGeneratorFactory.make(propertyInfo);
        generator.setParser(dynamicFieldsParser);
        Patch patch = new Patch();
        patch.setId("0");
        generator.setPayload(new GeneratorPayload(new NameValue("p.additionalValuesJson", "{\"key\":\"value\"}"), patch));

        ESDsm esDsm = new ESDsm();
        esDsm.setParticipant(new Participant(2174L, null, null, null, null, null, null,
                null, null, null, false, false, "{\"key\": \"oldVal\"}", 12874512387612L));
        BaseProcessor processor = new SingleProcessor();
        processor.setEsDsm(esDsm);
        processor.setPropertyName(propertyInfo.getPropertyName());
        processor.setRecordId(0);
        processor.setCollector(generator);
        Map<String, Object> processedData = (Map<String, Object>) processor.process();
        Assert.assertEquals("value", ((Map<String, Object>) processedData.get(ESObjectConstants.DYNAMIC_FIELDS)).get("key"));
    }

    @Test
    public void processNew() {
        PropertyInfo propertyInfo = PropertyInfo.TABLE_ALIAS_MAPPINGS.get(DBConstants.DDP_PARTICIPANT_ALIAS);
        ValueParser valueParser = new ValueParser();

        DynamicFieldsParser dynamicFieldsParser = new DynamicFieldsParser();
        dynamicFieldsParser.setHelperParser(valueParser);
        dynamicFieldsParser.setDisplayType("TEXT");

        GeneratorFactory sourceGeneratorFactory = new SourceGeneratorFactory();
        BaseGenerator generator = sourceGeneratorFactory.make(propertyInfo);
        generator.setParser(dynamicFieldsParser);
        Patch patch = new Patch();
        patch.setId("0");
        generator.setPayload(new GeneratorPayload(new NameValue("p.additionalValuesJson", "{\"key\":\"value\"}"), patch));

        ESDsm esDsm = new ESDsm();
        esDsm.setParticipant(new Participant(2174L, null, null, null, null, null, null,
                null, null, null, false, false, "", 12874512387612L));
        BaseProcessor processor = new SingleProcessor();
        processor.setEsDsm(esDsm);
        processor.setPropertyName(propertyInfo.getPropertyName());
        processor.setRecordId(0);
        processor.setCollector(generator);
        Map<String, Object> processedData = (Map<String, Object>) processor.process();
        Assert.assertEquals("value", ((Map<String, Object>) processedData.get(ESObjectConstants.DYNAMIC_FIELDS)).get("key"));
    }

    @Test
    public void getPrimaryKeyForParticipant() {
        SingleProcessor processor = new SingleProcessor();
        ESDsm esDsm = new ESDsm();
        Participant participant = new Participant("15", null, ASSIGNEE_ID_TISSUE_VALUE);
        esDsm.setParticipant(participant);
        BaseGenerator collector = new SingleSourceGenerator();
        Patch patch = new Patch();
        NameValue nameValue = new NameValue(ASSIGNEE_ID_TISSUE, ASSIGNEE_ID_TISSUE_VALUE);
        patch.setNameValue(nameValue);
        patch.setTableAlias("r");
        collector.setPayload(new GeneratorPayload(nameValue, patch));
        processor.setEsDsm(esDsm);
        processor.setCollector(collector);
        Optional<String> maybePrimaryKey = processor.getPrimaryKey();
        maybePrimaryKey.ifPresent(pk -> Assert.assertEquals(pk, PARTICIPANT_ID));
    }

    @Test
    public void putPrimaryKeyIfAbsentForParticipant() {
        SingleProcessor processor = new SingleProcessor();
        ESDsm esDsm = new ESDsm();
        Participant participant = new Participant("42", null, ASSIGNEE_ID_TISSUE_VALUE);
        esDsm.setParticipant(participant);
        processor.setEsDsm(esDsm);
        BaseGenerator collector = new SingleSourceGenerator();
        ValueParser parser = new ValueParser();
        parser.setPropertyInfo(new PropertyInfo(Participant.class, false));
        collector.setParser(parser);
        Patch patch = new Patch();
        NameValue nameValue = new NameValue(ASSIGNEE_ID_TISSUE, ASSIGNEE_ID_TISSUE_VALUE);
        patch.setNameValue(nameValue);
        patch.setTableAlias("r");
        collector.setPayload(new GeneratorPayload(nameValue, patch));
        processor.setCollector(collector);
        Map<String, Object> endResult = (Map<String, Object>) collector.collect();
        Map<String, Object> actual = processor.putPrimaryKeyIfAbsent(endResult);
        Assert.assertTrue(actual.containsKey(PARTICIPANT_ID));
    }
}
