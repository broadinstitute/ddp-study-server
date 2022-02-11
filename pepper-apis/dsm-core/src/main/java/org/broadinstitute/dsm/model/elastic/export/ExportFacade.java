package org.broadinstitute.dsm.model.elastic.export;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.broadinstitute.dsm.db.structure.DBElement;
import org.broadinstitute.dsm.model.elastic.ESDsm;
import org.broadinstitute.dsm.model.elastic.Util;
import org.broadinstitute.dsm.model.elastic.export.generate.BaseGenerator;
import org.broadinstitute.dsm.model.elastic.export.generate.GeneratorFactory;
import org.broadinstitute.dsm.model.elastic.export.generate.MappingGenerator;
import org.broadinstitute.dsm.model.elastic.export.generate.MappingGeneratorFactory;
import org.broadinstitute.dsm.model.elastic.export.generate.SourceGeneratorFactory;
import org.broadinstitute.dsm.model.elastic.export.parse.BaseParser;
import org.broadinstitute.dsm.model.elastic.export.parse.TypeParser;
import org.broadinstitute.dsm.model.elastic.export.parse.TypeParserFactory;
import org.broadinstitute.dsm.model.elastic.export.parse.ValueParser;
import org.broadinstitute.dsm.model.elastic.export.parse.ValueParserFactory;
import org.broadinstitute.dsm.model.elastic.export.process.BaseProcessor;
import org.broadinstitute.dsm.model.elastic.export.process.ProcessorFactory;
import org.broadinstitute.dsm.model.elastic.export.process.ProcessorFactoryImpl;
import org.broadinstitute.dsm.model.elastic.search.DefaultDeserializer;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearch;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchable;
import org.broadinstitute.dsm.util.PatchUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExportFacade {

    private static final Logger logger = LoggerFactory.getLogger(ExportFacade.class);

    BaseExporter exportable;
    BaseGenerator generator;
    ElasticSearchable searchable;
    private ExportFacadePayload exportFacadePayload;
    BaseProcessor processor;

    public ExportFacade(ExportFacadePayload exportFacadePayload) {
        this.exportFacadePayload = Objects.requireNonNull(exportFacadePayload);
        searchable = new ElasticSearch();
    }

    public void export() {
        upsertMapping();
        upsertData(processData(fetchData()));
    }

    private void upsertMapping() {
        BaseGenerator.PropertyInfo propertyInfo = getPropertyInfo();
        GeneratorFactory generatorFactory = new MappingGeneratorFactory();
        String fieldName = exportFacadePayload.getCamelCaseFieldName();
        propertyInfo.setFieldName(fieldName);
        generator = generatorFactory.make(propertyInfo);
        BaseParser typeParser = new TypeParserFactory().of(exportFacadePayload);
        typeParser.setPropertyInfo(propertyInfo);
        generator.setParser(typeParser);
        generator.setPayload(exportFacadePayload.getGeneratorPayload());
        Map<String, Object> mappingToUpsert = generator.generate();
        RequestPayload upsertMappingRequestPayload = new RequestPayload(exportFacadePayload.getIndex());
        propertyInfo.setFieldName(Util.underscoresToCamelCase(exportFacadePayload.getColumnName()));
        ExportableFactory mappingExporterFactory = new MappingExporterFactory();
        exportable = mappingExporterFactory.make(propertyInfo);
        exportable.setRequestPayload(upsertMappingRequestPayload);
        exportable.setSource(mappingToUpsert);
        exportable.export();
    }

    private ESDsm fetchData() {
        searchable.setDeserializer(new DefaultDeserializer());
        ElasticSearchParticipantDto participantById = searchable.getParticipantById(exportFacadePayload.getIndex(), exportFacadePayload.getDocId());
        // Ensure that participant data will be stored by participant guid
        exportFacadePayload.setDocId(participantById.getParticipantId());
        return participantById.getDsm().orElseThrow();
    }

    private Map<String, Object> processData(ESDsm esDsm) {
        BaseGenerator.PropertyInfo propertyInfo = getPropertyInfo();
        BaseParser valueParser = new ValueParserFactory().of(exportFacadePayload);
        valueParser.setPropertyInfo(propertyInfo);
        GeneratorFactory sourceGeneratorFactory = new SourceGeneratorFactory();
        generator = sourceGeneratorFactory.make(propertyInfo);
        generator.setParser(valueParser);
        generator.setPayload(exportFacadePayload.getGeneratorPayload());
        logger.info("Processing ES participant data");
        ProcessorFactory processorFactory = new ProcessorFactoryImpl();
        processor = processorFactory.make(propertyInfo);
        processor.setEsDsm(esDsm);
        processor.setPropertyName(propertyInfo.getPropertyName());
        processor.setRecordId(exportFacadePayload.getRecordId());
        processor.setCollector(generator);
        Object processedData = processor.process();
        Map<String, Object> dataToReturn = new HashMap<>(Map.of(MappingGenerator.DSM_OBJECT,
                new HashMap<>(Map.of(propertyInfo.getPropertyName(),
                processedData))));
        logger.info("Returning processed ES participant data");
        return dataToReturn;
    }

    private BaseGenerator.PropertyInfo getPropertyInfo() {
        DBElement dbElement = PatchUtil.getColumnNameMap().get(exportFacadePayload.getFieldNameWithAlias());
        return Util.TABLE_ALIAS_MAPPINGS.get(dbElement.getTableAlias());
    }

    private void upsertData(Map<String, Object> elasticDataToExport) {
        RequestPayload requestPayload = new RequestPayload(exportFacadePayload.getIndex(), exportFacadePayload.getDocId());
        logger.info("Built upsert data request payload for the index " + exportFacadePayload.getIndex());
        exportable = new ElasticDataExportAdapter();
        exportable.setRequestPayload(requestPayload);
        exportable.setSource(elasticDataToExport);
        exportable.export();
    }


}
