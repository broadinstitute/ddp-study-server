package org.broadinstitute.ddp.util;

import java.net.MalformedURLException;
import java.net.URL;

import com.sendgrid.Client;
import com.sendgrid.SendGrid;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.apache.http.impl.NoConnectionReuseStrategy;
import org.apache.http.impl.client.HttpClients;

/**
 * Central factory for creating {@link SendGrid} instances in
 * DSM and DSS.
 */
@Slf4j
public class SendGridFactory {

    /**
     * Creates a new SendGrid instance using the proxy url for
     * outbound egress if not null.
     */
    public static SendGrid createSendGridInstance(String sendGridApiKey, String proxy) {
        var httpClientBuilder = HttpClients.custom();
        if (proxy != null && !proxy.isBlank()) {
            URL proxyUrl;
            try {
                proxyUrl = new URL(proxy);
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException("proxy needs to be a valid url");
            }
            httpClientBuilder.setProxy(new HttpHost(proxyUrl.getHost(), proxyUrl.getPort(), proxyUrl.getProtocol()));
            httpClientBuilder.setConnectionReuseStrategy(NoConnectionReuseStrategy.INSTANCE);
            log.info("Using SendGrid proxy: {}", proxy);
        }
        var client = new Client(httpClientBuilder.build());
        return new SendGrid(sendGridApiKey, client);
    }

}
