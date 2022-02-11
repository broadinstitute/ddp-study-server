package org.broadinstitute.dsm.model.elastic.export.process;

import org.broadinstitute.dsm.db.Participant;
import org.broadinstitute.dsm.model.NameValue;
import org.broadinstitute.dsm.model.elastic.ESDsm;
import org.broadinstitute.dsm.model.elastic.Util;
import org.broadinstitute.dsm.model.elastic.export.generate.BaseGenerator;
import org.broadinstitute.dsm.model.elastic.export.generate.GeneratorFactory;
import org.broadinstitute.dsm.model.elastic.export.generate.GeneratorPayload;
import org.broadinstitute.dsm.model.elastic.export.generate.SourceGeneratorFactory;
import org.broadinstitute.dsm.model.elastic.export.parse.DynamicFieldsParser;
import org.broadinstitute.dsm.model.elastic.export.parse.ValueParser;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.junit.Assert;
import org.junit.Test;

import java.util.Map;

public class SingleProcessorTest {

    @Test
    public void processExisting() {
        BaseGenerator.PropertyInfo propertyInfo = Util.TABLE_ALIAS_MAPPINGS.get(DBConstants.DDP_PARTICIPANT_ALIAS);
        ValueParser valueParser = new ValueParser();
        valueParser.setPropertyInfo(propertyInfo);

        DynamicFieldsParser dynamicFieldsParser = new DynamicFieldsParser();
        dynamicFieldsParser.setParser(valueParser);
        dynamicFieldsParser.setDisplayType("TEXT");

        GeneratorFactory sourceGeneratorFactory = new SourceGeneratorFactory();
        BaseGenerator generator = sourceGeneratorFactory.make(propertyInfo);
        generator.setParser(dynamicFieldsParser);
        generator.setPayload(new GeneratorPayload(new NameValue("p.additionalValuesJson", "{\"key\":\"value\"}"), 0));

        ESDsm esDsm = new ESDsm();
        esDsm.setParticipant(new Participant(2174, null, null, null, null, null, null,
                null, null, null, false, false, "{\"key\": \"oldVal\"}", 12874512387612L ));
        BaseProcessor processor = new SingleProcessor();
        processor.setEsDsm(esDsm);
        processor.setPropertyName(propertyInfo.getPropertyName());
        processor.setRecordId(0);
        processor.setCollector(generator);
        Map<String, Object> processedData = (Map<String, Object>) processor.process();
        Assert.assertEquals("value", ((Map<String, Object>)processedData.get(ESObjectConstants.DYNAMIC_FIELDS)).get("key"));
    }

    @Test
    public void processNew() {
        BaseGenerator.PropertyInfo propertyInfo = Util.TABLE_ALIAS_MAPPINGS.get(DBConstants.DDP_PARTICIPANT_ALIAS);
        ValueParser valueParser = new ValueParser();

        DynamicFieldsParser dynamicFieldsParser = new DynamicFieldsParser();
        dynamicFieldsParser.setParser(valueParser);
        dynamicFieldsParser.setDisplayType("TEXT");

        GeneratorFactory sourceGeneratorFactory = new SourceGeneratorFactory();
        BaseGenerator generator = sourceGeneratorFactory.make(propertyInfo);
        generator.setParser(dynamicFieldsParser);
        generator.setPayload(new GeneratorPayload(new NameValue("p.additionalValuesJson", "{\"key\":\"value\"}"), 0));

        ESDsm esDsm = new ESDsm();
        esDsm.setParticipant(new Participant(2174, null, null, null, null, null, null,
                null, null, null, false, false, "", 12874512387612L ));
        BaseProcessor processor = new SingleProcessor();
        processor.setEsDsm(esDsm);
        processor.setPropertyName(propertyInfo.getPropertyName());
        processor.setRecordId(0);
        processor.setCollector(generator);
        Map<String, Object> processedData = (Map<String, Object>) processor.process();
        Assert.assertEquals("value", ((Map<String, Object>)processedData.get(ESObjectConstants.DYNAMIC_FIELDS)).get("key"));
    }
}