package org.broadinstitute.ddp.util;

import static spark.Spark.before;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class FilterUtil {
    public static void whitelist(String path, Collection<String> ips) {
        Set<String> ipSet = (ips instanceof Set) ? (Set<String>)ips : new HashSet<>(ips);
        before(path, (request, response) -> {
            if(!(ipSet.contains(request.ip()))) {
                throw ResponseUtil.halt404PageNotFound(response);
            }
        });
    }
}
