package org.broadinstitute.dsm.jetty;

import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.ExceptionMapper;
import spark.embeddedserver.EmbeddedServer;
import spark.embeddedserver.EmbeddedServerFactory;
import spark.embeddedserver.EmbeddedServers;
import spark.embeddedserver.jetty.EmbeddedJettyServer;
import spark.embeddedserver.jetty.HttpRequestWrapper;
import spark.embeddedserver.jetty.JettyServerFactory;
import spark.http.matching.MatcherFilter;
import spark.route.Routes;
import spark.staticfiles.StaticFilesConfiguration;

public class JettyConfig {

    private static final Logger LOG = LoggerFactory.getLogger(JettyConfig.class);

    public static void setupJetty(String preferredSourceIPHeader) {
        if (StringUtils.isBlank(preferredSourceIPHeader)) {
            LOG.warn("Source IP will be servlet default.  If you're running behind a load balancer or other device, source IP "
                    + "may be incorrect.");
        } else {
            LOG.warn("Source IP will be taken from header {} if available.", preferredSourceIPHeader);
        }
        EmbeddedServers.add(EmbeddedServers.Identifiers.JETTY, new AppEngineServerFactory(new JettyServer(), preferredSourceIPHeader));
    }

    /**
     * Lifted from {@link spark.embeddedserver.jetty.EmbeddedJettyFactory} with a change
     * to use {@link JettyCustomRemoteAddrHeaderHandler} to override {@link HttpServletRequest#getRemoteAddr()}
     */
    private static class AppEngineServerFactory implements EmbeddedServerFactory {
        private final JettyServerFactory serverFactory;
        private String preferredIPSourceHeader;
        private ThreadPool threadPool;
        private boolean httpOnly = true;

        public AppEngineServerFactory(JettyServerFactory serverFactory, String preferredIPSourceHeader) {
            this.serverFactory = serverFactory;
            this.preferredIPSourceHeader = preferredIPSourceHeader;
        }

        public EmbeddedServer create(Routes routeMatcher, StaticFilesConfiguration staticFilesConfiguration,
                                     ExceptionMapper exceptionMapper, boolean hasMultipleHandler) {
            MatcherFilter matcherFilter = new MatcherFilter(routeMatcher, staticFilesConfiguration, exceptionMapper, false,
                    hasMultipleHandler);
            matcherFilter.init(null);
            SessionHandler handler = new JettyCustomRemoteAddrHeaderHandler(matcherFilter, this.preferredIPSourceHeader);
            handler.getSessionCookieConfig().setHttpOnly(this.httpOnly);
            return (new EmbeddedJettyServer(this.serverFactory, handler)).withThreadPool(this.threadPool);
        }

        public AppEngineServerFactory withThreadPool(ThreadPool threadPool) {
            this.threadPool = threadPool;
            return this;
        }

        public AppEngineServerFactory withHttpOnly(boolean httpOnly) {
            this.httpOnly = httpOnly;
            return this;
        }
    }

    /**
     * Lifted from {@link spark.embeddedserver.jetty.JettyHandler}, with one
     * change to use {@link SourceIPHeaderRequestWrapper} to set
     * the source IP properly in the appengine environment
     */
    public static class JettyCustomRemoteAddrHeaderHandler extends SessionHandler {
        private javax.servlet.Filter filter;
        private String preferredIPSourceHeader;

        public JettyCustomRemoteAddrHeaderHandler(Filter filter, String preferredIPSourceHeader) {
            this.filter = filter;
            this.preferredIPSourceHeader = preferredIPSourceHeader;
        }

        public void doHandle(String target, org.eclipse.jetty.server.Request baseRequest, HttpServletRequest request,
                             HttpServletResponse response) throws IOException, ServletException {
            HttpRequestWrapper wrapper = new SourceIPHeaderRequestWrapper(request, preferredIPSourceHeader);
            this.filter.doFilter(wrapper, response, null);
            if (wrapper.notConsumed()) {
                baseRequest.setHandled(false);
            } else {
                baseRequest.setHandled(true);
            }
        }
    }

    /**
     * Lifted from {@link spark.embeddedserver.jetty.JettyServer}
     */
    public static class JettyServer implements JettyServerFactory {
        JettyServer() {
        }

        public Server create(int maxThreads, int minThreads, int threadTimeoutMillis) {
            Server server;
            if (maxThreads > 0) {
                int min = minThreads > 0 ? minThreads : 8;

                int idleTimeout = threadTimeoutMillis > 0 ? threadTimeoutMillis : 30_000;
                server = new Server(new QueuedThreadPool(maxThreads, min, idleTimeout));
            } else {
                server = new Server();
            }

            return server;
        }

        public Server create(ThreadPool threadPool) {
            return threadPool != null ? new Server(threadPool) : new Server();
        }
    }

    /**
     * RequestWrapper that uses a different header as the preferred source
     * for the ip address that is returned by {@link HttpServletRequest#getRemoteAddr()}.
     */
    public static class SourceIPHeaderRequestWrapper extends HttpRequestWrapper {

        String remoteAddr;

        public SourceIPHeaderRequestWrapper(HttpServletRequest request, String preferredHeaderForIP) {
            super(request);
            remoteAddr = request.getRemoteAddr();
            if (StringUtils.isNotBlank(preferredHeaderForIP)) {
                String ipFromHeader = request.getHeader(preferredHeaderForIP);
                if (StringUtils.isNotBlank(ipFromHeader)) {
                    remoteAddr = ipFromHeader;
                }
            }
        }

        @Override
        public String getRemoteAddr() {
            return remoteAddr;
        }
    }
}
