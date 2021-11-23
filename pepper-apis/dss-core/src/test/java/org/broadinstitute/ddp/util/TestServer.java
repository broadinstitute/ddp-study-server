package org.broadinstitute.ddp.util;

import java.util.function.Consumer;

import spark.Service;

/**
 * Little utility for when we you need to create your own server to do stuff
 */
public class TestServer {
    public static int PORT = 6666;
    private Consumer<Service> setupServer;
    private Service service;

    public TestServer(Consumer<Service> setup) {
        this.setupServer = setup;

    }

    public TestServer startServer() {
        service = Service.ignite().port(PORT);
        setupServer.accept(service);
        service.awaitInitialization();
        return this;
    }

    public TestServer stopServer() {
        service.stop();
        service.awaitStop();
        return this;
    }

    public String baseUrl() {
        return "http://localhost:" + PORT;
    }
}
