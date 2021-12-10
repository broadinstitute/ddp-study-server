package org.broadinstitute.dsm.model.elastic.export;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.broadinstitute.dsm.db.structure.DBElement;
import org.broadinstitute.dsm.model.elastic.ESDsm;
import org.broadinstitute.dsm.model.elastic.Util;
import org.broadinstitute.dsm.model.elastic.export.generate.BaseGenerator;
import org.broadinstitute.dsm.model.elastic.export.generate.Generator;
import org.broadinstitute.dsm.model.elastic.export.generate.MappingGenerator;
import org.broadinstitute.dsm.model.elastic.export.generate.SourceGenerator;
import org.broadinstitute.dsm.model.elastic.export.parse.TypeParser;
import org.broadinstitute.dsm.model.elastic.export.parse.ValueParser;
import org.broadinstitute.dsm.model.elastic.export.process.CollectionProcessor;
import org.broadinstitute.dsm.model.elastic.export.process.Processor;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearch;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchable;
import org.broadinstitute.dsm.util.PatchUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExportFacade {

    private static final Logger logger = LoggerFactory.getLogger(ExportFacade.class);

    BaseExporter exportable;
    Generator generator;
    ElasticSearchable searchable;
    private ExportFacadePayload exportFacadePayload;
    Processor processor;

    public ExportFacade(ExportFacadePayload exportFacadePayload) {
        this.exportFacadePayload = Objects.requireNonNull(exportFacadePayload);
        exportable = new ElasticExportAdapter();
        searchable = new ElasticSearch();
    }

    public void export() {
        upsertMapping();
        upsertData(processData(fetchData()));
    }

    private void upsertMapping() {
        generator = new MappingGenerator(new TypeParser(), exportFacadePayload.getGeneratorPayload());
        Map<String, Object> mappingToUpsert = generator.generate();
        UpsertMappingRequestPayload upsertMappingRequestPayload = new UpsertMappingRequestPayload(exportFacadePayload.getIndex());
        exportable.setUpsertMappingRequestPayload(upsertMappingRequestPayload);
        exportable.exportMapping(mappingToUpsert);
    }

    private ESDsm fetchData() {
        ElasticSearchParticipantDto participantById = searchable.getParticipantById(exportFacadePayload.getIndex(), exportFacadePayload.getDocId());
        // Ensure that participant data will be stored by participant guid
        exportFacadePayload.setDocId(participantById.getParticipantId());
        return participantById.getDsm().orElseThrow();
    }

    private Map<String, Object> processData(ESDsm esDsm) {
        BaseGenerator.PropertyInfo propertyInfo = getPropertyInfo();
        ValueParser valueParser = new ValueParser();
        SourceGenerator sourceGenerator = new SourceGenerator(valueParser, exportFacadePayload.getGeneratorPayload());
        this.generator = sourceGenerator;
        Map<String, Object> dataToReturn = generator.generate();
        logger.info("Processing ES participant data");
        if (propertyInfo.isCollection()) {
            processor = new CollectionProcessor(esDsm, propertyInfo.getPropertyName(), exportFacadePayload.getGeneratorPayload(),
                    sourceGenerator);
            List<Map<String, Object>> processedData = processor.process();
            if (!processedData.isEmpty()) {
                dataToReturn = Map.of(MappingGenerator.DSM_OBJECT, Map.of(propertyInfo.getPropertyName(), processedData));
            }
        }
        logger.info("Returning processed ES participant data");
        return dataToReturn;
    }

    private BaseGenerator.PropertyInfo getPropertyInfo() {
        DBElement dbElement = PatchUtil.getColumnNameMap().get(exportFacadePayload.getGeneratorPayload().getNameValue().getName());
        return Util.TABLE_ALIAS_MAPPINGS.get(dbElement.getTableAlias());
    }

    private void upsertData(Map<String, Object> elasticDataToExport) {
        UpsertDataRequestPayload upsertDataRequestPayload = new UpsertDataRequestPayload.Builder(exportFacadePayload.getIndex(),
                exportFacadePayload.getDocId())
                .withDocAsUpsert(true)
                .withRetryOnConflict(5)
                .build();
        logger.info("Built upsert data request payload");
        exportable.setUpdateRequestPayload(upsertDataRequestPayload);
        exportable.exportData(elasticDataToExport);
    }


}
