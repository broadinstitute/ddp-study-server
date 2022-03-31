package org.broadinstitute.dsm.route;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.export.ExcelUtils;
import org.broadinstitute.dsm.model.ParticipantColumn;
import org.broadinstitute.dsm.model.elastic.sort.Alias;
import org.broadinstitute.dsm.model.elastic.export.excel.ColumnValue;
import org.broadinstitute.dsm.model.elastic.export.excel.ParticipantRecord;
import org.broadinstitute.dsm.model.elastic.export.excel.ParticipantRecordData;
import org.broadinstitute.dsm.model.filter.FilterFactory;
import org.broadinstitute.dsm.model.filter.Filterable;
import org.broadinstitute.dsm.model.elastic.export.excel.DownloadParticipantListPayload;
import org.broadinstitute.dsm.model.participant.ParticipantWrapperDto;
import org.broadinstitute.dsm.model.participant.ParticipantWrapperResult;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

public class DownloadParticipantListRoute extends RequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(DownloadParticipantListRoute.class);

    public DownloadParticipantListRoute() {
    }

    @Override
    public Object processRequest(Request request, Response response, String userId) throws Exception {
        DownloadParticipantListPayload payload =
                ObjectMapperSingleton.instance().readValue(request.body(), DownloadParticipantListPayload.class);
        List<ParticipantColumn> columnNames = payload.getColumnNames();
        Map<Alias, List<ParticipantColumn>> columnAliasEsPathMap =
                new TreeMap<>(Comparator.comparing(Alias::isCollection).thenComparing(Alias::getValue));
        columnNames.forEach(column -> {
            Alias alias = Alias.of(column);
            columnAliasEsPathMap.computeIfAbsent(alias, paths -> new ArrayList<>())
                    .add(column);
        });

        Filterable filterable = FilterFactory.of(request);
        ParticipantWrapperResult filteredList = (ParticipantWrapperResult) filterable.filter(request.queryMap());

        ParticipantRecordData rowData = new ParticipantRecordData(columnAliasEsPathMap);
        for (ParticipantWrapperDto participant : filteredList.getParticipants()) {
            ParticipantRecord participantRecord = new ParticipantRecord();
            Map<String, Object> esDataAsMap = participant.getEsDataAsMap();
            for (Map.Entry<Alias, List<ParticipantColumn>> aliasListEntry : columnAliasEsPathMap.entrySet()) {
                Alias key = aliasListEntry.getKey();
                for (ParticipantColumn column : aliasListEntry.getValue()) {
                    String esPath = getEsPath(key, column);
                    Object nestedValue = getNestedValue(esPath, esDataAsMap);
                    if (aliasListEntry.getKey() == Alias.ACTIVITIES) {
                        nestedValue = getQuestionAnswerValue(nestedValue, column);
                    }
                    ColumnValue columnValue = new ColumnValue(key, nestedValue);
                    participantRecord.add(columnValue);
                }
            }
            rowData.addParticipant(participantRecord);
        }

        ExcelUtils.createResponseFile(rowData, response);
        return response.raw();
    }

    private Object getQuestionAnswerValue(Object nestedValue, ParticipantColumn column) {
        List<LinkedHashMap<String, Object>> activities = (List<LinkedHashMap<String, Object>>) nestedValue;
        return activities.stream().filter(activity -> activity.get(ElasticSearchUtil.ACTIVITY_CODE).equals(column.getTableAlias()))
                .findFirst()
                .map(foundActivity -> {
                    if (Objects.isNull(column.getObject())) {
                        return foundActivity.get(column.getName());
                    }
                    List<LinkedHashMap<String, Object>> questionAnswers =
                            (List<LinkedHashMap<String, Object>>) foundActivity.get(ElasticSearchUtil.QUESTIONS_ANSWER);
                    return questionAnswers.stream().filter(qa -> qa.get(ElasticSearchUtil.STABLE_ID).equals(column.getName()))
                            .findFirst().map(fq -> fq.get(column.getName())).orElse(StringUtils.EMPTY);
                }).orElse(StringUtils.EMPTY);
    }

    private String getEsPath(Alias alias, ParticipantColumn column) {
        if (alias == Alias.ACTIVITIES) {
            return alias.getValue();
        }
        return alias.getValue().isEmpty() ? column.getName() : alias.getValue() + DBConstants.ALIAS_DELIMITER + column.getName();
    }

    private Object getNestedValue(String fieldName, Map<String, Object> esDataAsMap) {
        int dotIndex = fieldName.indexOf('.');
        if (dotIndex != -1) {
            Object o = esDataAsMap.get(fieldName.substring(0, dotIndex));
            if (o == null) {
                return StringUtils.EMPTY;
            }
            if (o instanceof Collection) {
                return ((Collection<?>) o).stream().map(singleDataMap -> getNestedValue(fieldName.substring(dotIndex + 1),
                        (Map<String, Object>) singleDataMap)).collect(Collectors.toList());
            } else {
                return getNestedValue(fieldName.substring(dotIndex + 1), (Map<String, Object>) o);
            }
        }
        return esDataAsMap.getOrDefault(fieldName, StringUtils.EMPTY);
    }

}
