package org.broadinstitute.dsm.model.gbf;

import lombok.Data;

import java.util.List;

@Data
public class Response {

    private boolean success;
    private String message;
    private String errorMessage;
    private String XML;
    private String orderNumber;
    private String orderStatus;
    private List<Status> statuses;


}
