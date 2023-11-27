package org.broadinstitute.dsm.route.participantfiles;

import java.net.URL;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class SignedUrlResponse {
    URL url;
    String fileName;
}
