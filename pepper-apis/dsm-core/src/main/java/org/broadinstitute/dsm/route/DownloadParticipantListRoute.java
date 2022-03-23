package org.broadinstitute.dsm.route;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.gson.Gson;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.Assignee;
import org.broadinstitute.dsm.db.Cancer;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.Drug;
import org.broadinstitute.dsm.db.FieldSettings;
import org.broadinstitute.dsm.db.InstanceSettings;
import org.broadinstitute.dsm.db.KitType;
import org.broadinstitute.dsm.db.MedicalRecord;
import org.broadinstitute.dsm.db.ViewFilter;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.settings.InstanceSettingsDto;
import org.broadinstitute.dsm.db.structure.DBElement;
import org.broadinstitute.dsm.model.KitRequestSettings;
import org.broadinstitute.dsm.model.KitSubKits;
import org.broadinstitute.dsm.model.ParticipantListRequestModel;
import org.broadinstitute.dsm.model.ddp.PreferredLanguage;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearch;
import org.broadinstitute.dsm.model.elastic.sort.Alias;
import org.broadinstitute.dsm.model.elastic.sort.SortBy;
import org.broadinstitute.dsm.model.filter.FilterFactory;
import org.broadinstitute.dsm.model.filter.Filterable;
import org.broadinstitute.dsm.model.participant.ParticipantWrapper;
import org.broadinstitute.dsm.model.participant.ParticipantWrapperDto;
import org.broadinstitute.dsm.model.participant.ParticipantWrapperPayload;
import org.broadinstitute.dsm.model.participant.ParticipantWrapperResult;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.RequestParameter;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.AbstractionUtil;
import org.broadinstitute.dsm.util.DDPRequestUtil;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.PatchUtil;
import org.broadinstitute.dsm.util.UserUtil;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;
import org.broadinstitute.lddp.handlers.util.Result;
import org.broadinstitute.lddp.util.JsonTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;

public class DownloadParticipantListRoute extends RequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(DownloadParticipantListRoute.class);

    private PatchUtil patchUtil;

    public DownloadParticipantListRoute(@NonNull PatchUtil patchUtil) {
        this.patchUtil = patchUtil;
    }

    @Override
    public Object processRequest(Request request, Response response, String userId) throws Exception {
        if (patchUtil.getColumnNameMap() == null) {
            throw new RuntimeException("ColumnNameMap is null!");
        }
        System.out.println(patchUtil.getColumnNameMap());
        QueryParamsMap queryParamsMap = request.queryMap();
        String realm = queryParamsMap.get("realm").value();
        if (StringUtils.isBlank(realm)) {
            logger.error("Realm is empty");
        }
        Map<String, Collection<FieldSettings>> fieldSettings = FieldSettings.getFieldSettings(realm);

        String ddpGroupId = DDPInstance.getDDPGroupId(realm);
        if (StringUtils.isBlank(ddpGroupId)) {
            logger.error("GroupId is empty");
        }
        String[] fieldNames = queryParamsMap.get("fieldNames").values();
        List<String[]> dataLines = new ArrayList<>();
        dataLines.add(fieldNames);
        List<String> esAliases = Arrays.stream(fieldNames)
                .map(column -> column.split("\\.")[0])
                .map(column -> Alias.of(column).getValue())
                .collect(Collectors.toList());

        Filterable filterable = FilterFactory.of(request);
        ParticipantWrapperResult filteredList = (ParticipantWrapperResult) filterable.filter(queryParamsMap);

        if (queryParamsMap.get("byParticipant").booleanValue()) {
            for (ParticipantWrapperDto participant : filteredList.getParticipants()) {
                List<String> records = participant.getMedicalRecords().stream().map(MedicalRecord::toString).collect(Collectors.toList());
                Map<String, Object> esDataAsMap = participant.getEsDataAsMap();
                List<String> row = new ArrayList<>();
                for (String esAlias : esAliases) {
                    Object nestedValue = getNestedValue(esAlias, esDataAsMap);
                    if (nestedValue instanceof Collection) {
                        System.out.println(nestedValue);
                        Collection<?> value = (Collection<?>) nestedValue;
                        value.forEach(val -> row.add(val.toString()));
                    } else {
                        row.add(nestedValue.toString());
                    }
                }
                dataLines.add(row.toArray(String[]::new));
            }
        }
        if (queryParamsMap.hasKey(SortBy.SORT_BY)) {
            SortBy sortBy = ObjectMapperSingleton.readValue(queryParamsMap.get(SortBy.SORT_BY).value(), new TypeReference<>() {
            });
        }

        File csvOutputFile = new File("test.csv");
        try (PrintWriter pw = new PrintWriter(csvOutputFile)) {
            dataLines.stream()
                    .map(this::convertToCSV)
                    .forEach(pw::println);
        }
        return new Result(200);
    }

    private Object getNestedValue(String fieldName, Map<String, Object> esDataAsMap) {
        int dotIndex = fieldName.indexOf('.');
        if (dotIndex != -1) {
            return getNestedValue(fieldName.substring(dotIndex+1), (Map<String, Object>) esDataAsMap.get(fieldName.substring(0, dotIndex)));
        }
        return esDataAsMap.get(fieldName);
    }

    public String convertToCSV(String[] data) {
        return Stream.of(data)
                .map(this::escapeSpecialCharacters)
                .collect(Collectors.joining(","));
    }

    public String escapeSpecialCharacters(String data) {
        String escapedData = data.replaceAll("\\R", " ");
        if (data.contains(",") || data.contains("\"") || data.contains("'")) {
            data = data.replace("\"", "\"\"");
            escapedData = "\"" + data + "\"";
        }
        return escapedData;
    }
}
