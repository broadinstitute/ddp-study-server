package org.broadinstitute.dsm.model.elastic.export;

import org.elasticsearch.action.update.UpdateRequest;

import java.util.Map;

public class UpsertDataRequestPayload {

    private static final String DOC = "_doc";

    private String index;
    private String type;
    private String id;
    private boolean docAsUpsert;
    private int retryOnConflict;

    private UpsertDataRequestPayload(Builder builder) {
        this.index = builder.index;
        this.type = builder.type;
        this.id = builder.id;
        this.docAsUpsert = builder.docAsUpsert;
        this.retryOnConflict = builder.retryOnConflict;
    }

    public UpdateRequest getUpdateRequest(Map<String, Object> data) {
        return new UpdateRequest()
                .index(index)
                .type(DOC)
                .id(id)
                .doc(data)
                .docAsUpsert(docAsUpsert)
                .retryOnConflict(retryOnConflict);
    }

    public static class Builder {
        private String index;
        private String type;
        private String id;
        private boolean docAsUpsert = true;
        private int retryOnConflict;

        public Builder(String index, String id) {
            this.index = index;
            this.id = id;
        }

        public Builder withIndex(String index) {
            this.index = index;
            return this;
        }
        
        public Builder withType(String type) {
            this.type = type;
            return this;
        }
        
        public Builder withId(String id) {
            this.id = id;
            return this;
        }

        public Builder withDocAsUpsert(boolean docAsUpsert) {
            this.docAsUpsert = docAsUpsert;
            return this;
        }

        public Builder withRetryOnConflict(int retryOnConflict) {
            this.retryOnConflict = retryOnConflict;
            return this;
        }

        public UpsertDataRequestPayload build() {
            return new UpsertDataRequestPayload(this);
        }


    }

}
