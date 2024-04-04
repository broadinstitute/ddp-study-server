package org.broadinstitute.dsm.service.download;

import static org.broadinstitute.dsm.util.ElasticSearchUtil.DEFAULT_FROM;
import static org.broadinstitute.dsm.util.ElasticSearchUtil.DSM;
import static org.broadinstitute.dsm.util.ElasticSearchUtil.MAX_RESULT_SIZE;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.netflix.servo.util.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.model.at.SampleQueue;
import org.broadinstitute.dsm.model.elastic.export.tabular.DataDictionaryExporter;
import org.broadinstitute.dsm.model.elastic.export.tabular.ModuleExportConfig;
import org.broadinstitute.dsm.model.elastic.export.tabular.TabularParticipantExporter;
import org.broadinstitute.dsm.model.elastic.export.tabular.TabularParticipantParser;
import org.broadinstitute.dsm.model.elastic.search.UnparsedDeserializer;
import org.broadinstitute.dsm.model.elastic.search.UnparsedESParticipantDto;
import org.broadinstitute.dsm.model.filter.FilterFactory;
import org.broadinstitute.dsm.model.filter.Filterable;
import org.broadinstitute.dsm.model.participant.DownloadParticipantListParams;
import org.broadinstitute.dsm.model.participant.ParticipantWrapperDto;
import org.broadinstitute.dsm.model.participant.ParticipantWrapperResult;
import spark.QueryParamsMap;
import spark.Response;

@Slf4j

public class DownloadParticipantListService {
    /**
     * Fetches participant data from Elasticsearch based on the given filter and query parameters.
     * returns a list of ParticipantWrapperDto objects
     */
    @VisibleForTesting
    public List<ParticipantWrapperDto> fetchParticipantEsData(Filterable filter, QueryParamsMap queryParamsMap) {
        List<ParticipantWrapperDto> allResults = new ArrayList<>();
        int currentFrom = DEFAULT_FROM;
        int currentTo = MAX_RESULT_SIZE;
        while (true) {
            // For each batch of results, add the DTOs to the allResults list
            filter.setFrom(currentFrom);
            filter.setTo(currentTo);
            ParticipantWrapperResult filteredSubset = (ParticipantWrapperResult) filter.filter(queryParamsMap, new UnparsedDeserializer());
            filteredSubset.getParticipants().forEach(participantWrapperDto -> setSampleQueue(participantWrapperDto));
            allResults.addAll(filteredSubset.getParticipants());
            // if the total count is less than the range we are currently on, stop fetching
            if (filteredSubset.getTotalCount() < currentTo) {
                break;
            }
            currentFrom = currentTo;
            currentTo += MAX_RESULT_SIZE;
        }

        return allResults;
    }

    private void setSampleQueue(ParticipantWrapperDto participantWrapperDto){
        UnparsedESParticipantDto participantDto = (UnparsedESParticipantDto) participantWrapperDto.getEsData();
        Map<String, List> dsmData =  (HashMap<String, List>) participantDto.getDataAsMap().get(DSM);
        if (dsmData != null && dsmData.get("kitRequestShipping") != null) {
            ((List<Map<String, Object>>) dsmData.get("kitRequestShipping"))
                    .forEach(kit -> kit.put("sampleQueue", getSampleQueueValue(kit)));
        }
    }



    /**
     * Checks the given kit for a value that can be used to determine its status in the sample queue.
     * The order of precedence is: externalOrderStatus, deactivatedDate, receiveDate, scanDate, error.
     *
     * @param kit The kit in form of a Map<String, Object>, which is the form that kit data is pulled from ES
     * @return The kit's status
     * */
    private Object getSampleQueueValue(Map<String, Object> kit) {
        if (isStringValuePresent(kit.get("externalOrderStatus"))) {
            return kit.get("externalOrderStatus");
        }
        if (isStringValuePresent(kit.get("deactivatedDate"))) {
            return SampleQueue.DEACTIVATED.uiText;
        }
        if (isStringValuePresent(kit.get("receiveDate"))) {
            return SampleQueue.RECEIVED.uiText;
        }
        if (isStringValuePresent(kit.get("scanDate"))) {
            return SampleQueue.SENT.uiText;
        }
        if (isStringValuePresent(kit.get("error")) && Boolean.parseBoolean(kit.get("error").toString())) {
            return SampleQueue.ERROR.uiText;
        }
        return SampleQueue.QUEUE.uiText;
    }

    /**
     * Checks if an object retrieved from Elasticsearch (or similar) is non-null and has content.
     * Designed for strings, this method checks for non-empty and non-blank (whitespace-only) strings.
     *
     * @param value The object to check for validity, which can be any type.
     * @return {@code true} if the object is not null and not empty (for strings);
     * {@code false} otherwise.
     */
    private static boolean isStringValuePresent(Object value) {
        return value != null && !value.toString().trim().isEmpty();
    }

    public Object createParticipantDownloadZip(Filterable filterable, DownloadParticipantListParams params, TabularParticipantParser parser,
                                               QueryParamsMap queryParamsMap, Response response) {
        List<ParticipantWrapperDto> participants = fetchParticipantEsData(filterable, queryParamsMap);
        log.info("Beginning parse of " + participants.size() + " participants");
        List<ModuleExportConfig> exportConfigs = parser.generateExportConfigs();
        List<Map<String, Object>> participantEsDataMaps = participants.stream().map(dto ->
                ((UnparsedESParticipantDto) dto.getEsData()).getDataAsMap()).collect(Collectors.toList());
        List<Map<String, String>> participantValueMaps = parser.parse(exportConfigs, participantEsDataMaps);

        ZipOutputStream zos = null;
        try {
            zos = new ZipOutputStream(response.raw().getOutputStream());
            TabularParticipantExporter participantExporter = TabularParticipantExporter.getExporter(exportConfigs,
                    participantValueMaps, params.getFileFormat());
            ZipEntry participantFile = new ZipEntry(participantExporter.getExportFilename());
            zos.putNextEntry(participantFile);
            participantExporter.export(zos);

            DataDictionaryExporter dictionaryExporter = new DataDictionaryExporter(exportConfigs);
            ZipEntry dictionaryFile = new ZipEntry(dictionaryExporter.getExportFilename());
            zos.putNextEntry(dictionaryFile);
            dictionaryExporter.export(zos);
            zos.closeEntry();
            zos.finish();
            return response.raw();
        } catch (IOException e) {
            throw new DsmInternalError(e);
        } finally {
            if (zos != null) {
                try {
                    zos.close();
                } catch (IOException e) {
                    log.error("Error closing zip stream", e);
                }
            }
        }
    }
}