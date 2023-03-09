package org.broadinstitute.dsm.elasticSearch;


import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import lombok.NonNull;
import org.broadinstitute.dsm.TestHelper;
import org.broadinstitute.dsm.db.MedicalRecord;
import org.broadinstitute.dsm.statics.QueryExtension;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.lddp.db.SimpleResult;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElasticSearchAndDBComparison extends TestHelper {

    private static final Logger logger = LoggerFactory.getLogger(ElasticSearchAndDBComparison.class);

    String STUDY_NAME = "osteo2";
    String ENV = "dev";
    String STUDY_INDEX= "participants_structured.cmi.cmi-osteo";
    String WORK_SPACE_PATH= "/Users/ptaheri/IdeaProjects/ddp-study-server";

    @BeforeClass
    public static void before() throws Exception {
        setupDBIgnoreLocal(true);
    }

    @Test
    public void compareESAndDBMedicalRecords() throws Exception {
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        Date date = new Date();
        File outputFile = new File(WORK_SPACE_PATH+"/ES_DB_COMPARE_"+STUDY_NAME+"_"+dateFormat.format(date)+".txt");
        outputFile.createNewFile();
        FileWriter fileWriter = new FileWriter(outputFile);
        PrintWriter printWriter = new PrintWriter(fileWriter);
        printWriter.printf("Checking %s in %s", STUDY_INDEX, ENV + System.lineSeparator());
        String error;
        try (RestHighLevelClient client = ElasticSearchUtil.getClientForElasticsearchCloud(cfg.getString("elasticSearch.url"),
                cfg.getString("elasticSearch.username"), cfg.getString("elasticSearch.password"))) {
            int scrollSize = 1000;
            Map<String, Map<String, Object>> esData = new HashMap<>();
            SearchRequest searchRequest = new SearchRequest(STUDY_INDEX);
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            SearchResponse response = null;
            int i = 0;
            while (response == null || response.getHits().getHits().length != 0) {
                searchSourceBuilder.query(QueryBuilders.matchAllQuery());
                searchSourceBuilder.size(scrollSize);
                searchSourceBuilder.from(i * scrollSize);
                searchRequest.source(searchSourceBuilder);

                response = client.search(searchRequest, RequestOptions.DEFAULT);
                ElasticSearchUtil.addingParticipantStructuredHits(response, esData, "realm", STUDY_INDEX);
                i++;
            }
            for (String esDdpParticipantId: esData.keySet()){
                Map<String, Object> participantESData = esData.get(esDdpParticipantId);
                HashMap<String, Object> esDsm = (HashMap<String, Object>) participantESData.get("dsm");
                ArrayList<HashMap> medicalRecords = (ArrayList<HashMap>) esDsm.get("medicalRecord");
                if (medicalRecords == null)
                    continue;
                for (HashMap esMedicalRecord: medicalRecords){
                    MedicalRecord dbMedicalRecord = getMedicalRecord(STUDY_NAME, esMedicalRecord.get("medicalRecordId")+"");
                    if (dbMedicalRecord == null){
                        error = String.format("In %s , DB medical record with id %s was not found, in ES it belongs to participant with guid %s ",
                                STUDY_NAME, esMedicalRecord.get("medicalRecordId")+"", esDdpParticipantId);
                        logger.error(error);
                        printWriter.print(error + System.lineSeparator());
                        continue;
                    }
                    if (!dbMedicalRecord.getDdpParticipantId().equals(esDdpParticipantId)){
                        error = String.format("In %s ,Data mismatch in Medical Records. Id %s in DB belongs to %s, in ES belongs to %s",
                                STUDY_NAME, esMedicalRecord.get("medicalRecordId")+"", dbMedicalRecord.getDdpParticipantId(), esDdpParticipantId);
                        logger.error(error);
                        printWriter.print(error + System.lineSeparator());
                    }
                }
            }
        } catch (Exception e){
            e.printStackTrace();
        } finally {
            printWriter.close();
        }
    }

    public static MedicalRecord getMedicalRecord(@NonNull String realm,  @NonNull String medicalRecordId) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(
                    MedicalRecord.SQL_SELECT_MEDICAL_RECORD  + QueryExtension.BY_MEDICAL_RECORD_ID)) {
                stmt.setString(1, realm);
                stmt.setString(2, medicalRecordId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        dbVals.resultValue = MedicalRecord.getMedicalRecord(rs);
                    }
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting medicalRecord " + medicalRecordId + " of id " + medicalRecordId,
                    results.resultException);
        }

        return (MedicalRecord) results.resultValue;
    }

}
