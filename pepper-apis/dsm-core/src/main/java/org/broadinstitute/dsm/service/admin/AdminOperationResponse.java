package org.broadinstitute.dsm.service.admin;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Provides structure for admin operation result requests
 */
@Data
public class AdminOperationResponse {

    private List<AdminOperationResult> results;

    public AdminOperationResponse() {
        results = new ArrayList<>();
    }

    public AdminOperationResponse(AdminOperationResult result) {
        results = new ArrayList<>();
        addResult(result);
    }

    public AdminOperationResponse(List<AdminOperationResult> results) {
        this.results = results;
    }

    public void addResult(AdminOperationResult result) {
        results.add(result);
    }

    @AllArgsConstructor
    @Data
    public static class AdminOperationResult {
        private int operationId;
        private String operationTypeId;
        private String operatorId;
        private Timestamp operationStart;
        private Timestamp operationEnd;
        private String status;
        private String results;

        public AdminOperationResult(AdminOperationResult.Builder builder) {
            this.operationId = builder.operationId;
            this.operationTypeId = builder.operationTypeId;
            this.operatorId = builder.operatorId;
            this.operationStart = builder.operationStart;
            this.operationEnd = builder.operationEnd;
            this.status = builder.status;
            this.results = builder.results;
        }

        public static class Builder {
            private int operationId;
            private String operationTypeId;
            private String operatorId;
            private Timestamp operationStart;
            private Timestamp operationEnd;
            private String status;
            private String results;

            public AdminOperationResult.Builder withOperationId(int operationId) {
                this.operationId = operationId;
                return this;
            }

            public AdminOperationResult.Builder withOperationTypeId(String operationTypeId) {
                this.operationTypeId = operationTypeId;
                return this;
            }

            public AdminOperationResult.Builder withOperatorId(String operatorId) {
                this.operatorId = operatorId;
                return this;
            }

            public AdminOperationResult.Builder withOperationStart(Timestamp operationStart) {
                this.operationStart = operationStart;
                return this;
            }

            public AdminOperationResult.Builder withOperationEnd(Timestamp operationEnd) {
                this.operationEnd = operationEnd;
                return this;
            }

            public AdminOperationResult.Builder withStatus(String status) {
                this.status = status;
                return this;
            }

            public AdminOperationResult.Builder withResults(String results) {
                this.results = results;
                return this;
            }

            public AdminOperationResult build() {
                return new AdminOperationResult(this);
            }
        }
    }
}
