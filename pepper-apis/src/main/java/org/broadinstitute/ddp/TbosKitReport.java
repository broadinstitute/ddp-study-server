package org.broadinstitute.ddp;

import java.beans.ConstructorProperties;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.time.Instant;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.commons.io.IOUtils;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.service.KitCheckService;
import org.broadinstitute.ddp.util.GsonUtil;
import org.jdbi.v3.core.mapper.reflect.ConstructorMapper;

public class TbosKitReport {

    static String QUERY = "\n" +
            "select\n" +
            "u.guid,\n" +
            "k.kit_label,\n" +
            "from_unixtime(dkr.created_date/1000) as kit_requested_at,\n" +
            "k.tracking_to_id,\n" +
            "k.tracking_return_id,\n" +
            "json_extract(k.test_result, '$[0].result') test_result,\n" +
            "ifnull(STR_TO_DATE(replace(json_extract(k.test_result, '$[0].timeCompleted'),'\"',''), '%Y-%m-%dT%H:%i:%sZ'),\n" +
            "    STR_TO_DATE(replace(json_extract(k.test_result, '$[0].timeCompleted'),'\"',''), '%Y-%m-%dT%H:%i:%s.%fZ')\n" +
            ")as test_completion_time,\n" +
            "u.hruid,\n" +
            "dkr.upload_reason\n" +
            "from\n" +
            "prod_dsm_db.ddp_kit k,\n" +
            "prod_dsm_db.ddp_kit_request dkr,\n" +
            "pepperapisprod.user u,\n" +
            "prod_dsm_db.ddp_instance study,\n" +
            "prod_dsm_db.kit_type kt\n" +
            "where\n" +
            "study.instance_name = 'testboston'\n" +
            "and\n" +
            "u.guid = dkr.ddp_participant_id\n" +
            "and\n" +
            "study.ddp_instance_id = dkr.ddp_instance_id\n" +
            "and\n" +
            "dkr.dsm_kit_request_id = k.dsm_kit_request_id\n" +
            "and\n" +
            "kt.kit_type_id = dkr.kit_type_id\n" +
            "and\n" +
            "kt.kit_type_name = 'AN'\n" +
            "and k.kit_label like 'TBOS-%'\n" +
            "and dkr.upload_reason is null\n" +
            "order by 7";

    public static void main(String[] args) throws Exception {
        File outputFile = new File(args[0]);
        FileWriter writer = new FileWriter(outputFile);


        Config cfg = ConfigFactory.load();
        String dbUrl = cfg.getString(ConfigFile.DB_URL);

        TransactionWrapper.init(
                new TransactionWrapper.DbConfiguration(TransactionWrapper.DB.APIS, 1, dbUrl));

        TransactionWrapper.useTxn(handle -> {
            handle.registerRowMapper(ConstructorMapper.factory(KitRow.class));

            List<KitRow> kitRows = handle.createQuery(QUERY).setFetchSize(1000).mapTo(KitRow.class).list();

            IOUtils.write(GsonUtil.standardGson().toJson(kitRows),writer);
            writer.flush();
            writer.close();
            System.out.printf("Wrote " + kitRows.size());
        });

        // make mapper

        // run calls to UPS

    }

    public static class KitRow {

        @SerializedName("guid")
        private final String guid;

        @SerializedName("kitBarcode")
        private final String kitBarcode;

        @SerializedName("kitRequestTime")
        private final Instant kitReqestTime;

        @SerializedName("outboundTrackingId")
        private final String outboundTrackingId;

        @SerializedName("returnTrackingId")
        private final String returnTrackingId;

        @SerializedName("testResults")
        private final String testResult;

        @SerializedName("testGenerationTime")
        private final Instant resultGenerationTime;

        @SerializedName("hruid")
        private final String hruid;

        @SerializedName("reason")
        private final String reason;

        @ConstructorProperties({"guid", "kit_label", "kit_requested_at", "tracking_to_id","tracking_return_id","test_result","test_completion_time","hruid","upload_reason"})
        public KitRow(String guid, String kitBarcode, Instant kitReqestTime, String outboundTrackingId,
                      String returnTrackingId, String testResult, Instant resultGenerationTime, String hruid, String reason) {

            this.guid = guid;
            this.kitBarcode = kitBarcode;
            this.kitReqestTime = kitReqestTime;
            this.outboundTrackingId = outboundTrackingId;
            this.returnTrackingId = returnTrackingId;
            this.testResult = testResult;
            this.resultGenerationTime = resultGenerationTime;
            this.hruid = hruid;
            this.reason = reason;
        }

        public String getGuid() {
            return guid;
        }

        public String getKitBarcode() {
            return kitBarcode;
        }

        public Instant getKitReqestTime() {
            return kitReqestTime;
        }

        public String getOutboundTrackingId() {
            return outboundTrackingId;
        }

        public String getReturnTrackingId() {
            return returnTrackingId;
        }

        public String getTestResult() {
            return testResult;
        }

        public Instant getResultGenerationTime() {
            return resultGenerationTime;
        }
    }
}
