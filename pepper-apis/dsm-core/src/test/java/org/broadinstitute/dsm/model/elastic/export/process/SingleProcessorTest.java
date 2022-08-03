package org.broadinstitute.dsm.model.elastic.export.process;

import java.util.Map;

import org.broadinstitute.dsm.db.Participant;
import org.broadinstitute.dsm.model.NameValue;
import org.broadinstitute.dsm.model.elastic.Dsm;
import org.broadinstitute.dsm.model.elastic.export.generate.BaseGenerator;
import org.broadinstitute.dsm.model.elastic.export.generate.GeneratorFactory;
import org.broadinstitute.dsm.model.elastic.export.generate.GeneratorPayload;
import org.broadinstitute.dsm.model.elastic.export.generate.PropertyInfo;
import org.broadinstitute.dsm.model.elastic.export.generate.SourceGeneratorFactory;
import org.broadinstitute.dsm.model.elastic.export.parse.DynamicFieldsParser;
import org.broadinstitute.dsm.model.elastic.export.parse.ValueParser;
import org.broadinstitute.dsm.model.patch.Patch;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.junit.Assert;
import org.junit.Test;

public class SingleProcessorTest {

    @Test
    public void processExisting() {
        PropertyInfo propertyInfo = PropertyInfo.of(DBConstants.DDP_PARTICIPANT_ALIAS);
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

        Dsm esDsm = new Dsm();
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
        PropertyInfo propertyInfo = PropertyInfo.of(DBConstants.DDP_PARTICIPANT_ALIAS);
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

        Dsm esDsm = new Dsm();
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
}
