package de.mhellmann.net.fluenthttp;

import de.mhellmann.util.Log4JUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.http.*;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.params.CookiePolicy;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;
import org.apache.log4j.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.*;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.*;


/**
 * Completely refactored implementation using HttpClient v4.25,
 * which got a totally different API than former v3.
 *
 * 24.03.2014: Updated to yield a more modern fluent style,
 *             which makes client code better readable and flowing.
 *
 * @link http://www.martinfowler.com/bliki/FluentInterface.html
 *
 * @author <a href="mailto:marten.hellmann@web.de"><strong>Marten Hellmann</strong></a>
 */
public class FluentHttpClient {

    public static final String USER_AGENT_MOZILLA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.8; rv:24.0) Firefox/24.0";
    public static final int DEFAULT_TIMEOUT = 20000;
    private static final int HTTPS_PORT = 443;

    // http://www.whatsmyuseragent.com/
    private String userAgent = USER_AGENT_MOZILLA;
    private Integer connectionTimeoutMillis = DEFAULT_TIMEOUT;
    private Integer socketTimeoutMillis = DEFAULT_TIMEOUT;
    private Boolean tcpNoDelay = true;
    private boolean handleRedirects = true;
    private int retryCount = 0;
    protected boolean rethrowExceptions = true;
    private boolean avoidSSLPeerUnverifiedException = false;

    private final Logger logger;
    private boolean logCookies = false;
    private boolean logHeaders = false;
    private boolean logDebugToSysOut = false;
    private boolean logErrorToSysOut = false;

    /**
     * This can be used to login and keep the session for subsequent calls.
     * IMPORTANT: You have to set useLastCookieStore = true !!!
     **/
    private CookieStore cookieStore = null;
    /** false means, we don't maintain a session cookie over several requests */
    private boolean reuseLastCookieStore = false;

    /** Default constructor */
    public FluentHttpClient() {
        logger = LoggerFactory.getLogger(getClass());
    }

    /**
     *
     * @param name May be used to have different log levels for each instance of FluentHttpClient
     */
    public FluentHttpClient(String name) {
        logger = LoggerFactory.getLogger(getClass().getName() + "." + name);
    }

    public FluentHttpClient withLoggingHeaders() {
        logHeaders = true;
        return this;
    }

    public FluentHttpClient withLoggingCookies() {
        logCookies = true;
        return this;
    }

    public FluentHttpClient withLoggingToSysOut(boolean debugAndErrorToSysOut) {
        return withLoggingToSysOut(debugAndErrorToSysOut, debugAndErrorToSysOut);
    }

    public FluentHttpClient withLoggingToSysOut(boolean debugToSysOut, boolean errorToSysOut) {
        this.logDebugToSysOut = debugToSysOut;
        this.logErrorToSysOut = errorToSysOut;
        if (debugToSysOut) {
            Log4JUtils.addConsoleAppender(logger, Level.DEBUG);
        } else if (errorToSysOut) {
            Log4JUtils.addConsoleAppender(logger, Level.ERROR);
        }
        return this;
    }

    public FluentHttpClient withRetries(int retryCount) {
        this.retryCount = retryCount;
        return this;
    }

    public FluentHttpClient withUserAgent(String userAgent) {
        this.userAgent = userAgent;
        return this;
    }

    public FluentHttpClient withRedirectHandling() {
        this.handleRedirects = true;
        return this;
    }

    public FluentHttpClient withConnectionTimeoutMillis(int connectionTimeoutMillis) {
        this.connectionTimeoutMillis = connectionTimeoutMillis;
        return this;
    }

    public FluentHttpClient withSocketTimeoutMillis(int socketTimeoutMillis) {
        this.socketTimeoutMillis = socketTimeoutMillis;
        return this;
    }

    public FluentHttpClient withTCPNoDelay(Boolean tcpNoDelay) {
        this.tcpNoDelay = tcpNoDelay;
        return this;
    }

    public FluentHttpClient withRethrowingExceptions(boolean rethrowExceptions) {
        this.rethrowExceptions = rethrowExceptions;
        return this;
    }

    public FluentHttpClient withAvoidingSSLPeerUnverifiedException() {
        this.avoidSSLPeerUnverifiedException = true;
        return this;
    }

    public FluentHttpClient withCookieStore(CookieStore cookieStore) {
        this.cookieStore = cookieStore;
        return this;
    }

    /** Allows to simulate session handling by keeping the cookie store for several get/post requests. */
    public FluentHttpClient withReusingLastCookieStore(boolean reuseLastCookieStore) {
        this.reuseLastCookieStore = reuseLastCookieStore;
        return this;
    }

    protected HttpContext newCookieStore(HttpContext httpContext) {
        if (!reuseLastCookieStore) {
            this.cookieStore = new BasicCookieStore();
        } else {
            if (this.cookieStore==null) {
                this.cookieStore = new BasicCookieStore();
            }
        }

        if (httpContext==null) {
            httpContext = new BasicHttpContext();
        }
        // bind custom cookie store to the local context
        httpContext.setAttribute(ClientContext.COOKIE_STORE, cookieStore);
        return httpContext;
    }

    public CookieStore getCookieStore() {
        return cookieStore;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                //.append("name", name)
                .append("userAgent", userAgent)
                .append("connectionTimeoutMillis", connectionTimeoutMillis)
                .append("socketTimeoutMillis", socketTimeoutMillis)
                .append("tcpNoDelay", tcpNoDelay)
                .append("handleRedirects", handleRedirects)
                .append("retryCount", retryCount)
                .append("rethrowExceptions", rethrowExceptions)
                .append("avoidSSLPeerUnverifiedException", avoidSSLPeerUnverifiedException)
                .append("logCookies", logCookies)
                .append("logHeaders", logHeaders)
                .append("logDebugToSysOut", logDebugToSysOut)
                .append("logErrorToSysOut", logErrorToSysOut)
                .append("cookieStore", cookieStore)
                .append("reuseLastCookieStore", reuseLastCookieStore)
                .toString();
    }

    //################################################################
    //### The client calls this to execute http get or post methods
    //################################################################

    public FluentHttpGetMethodBuilder get(String url) {
       return new FluentHttpGetMethodBuilder(this, logger, url);
    }

    public FluentHttpPostMethodBuilder post(String url) {
       return new FluentHttpPostMethodBuilder(this, logger, url);
    }


    //#############################################################
    //### internal impls ..
    //#############################################################

    /** This uses http.proxyHost and https.proxyHost etc. system properties */
    protected DefaultHttpClient newHttpClient(FluentHttpMethodBuilder.ProxyInfo systemProxyInfo, Boolean ignoreCookies) {
        String proxyHost = systemProxyInfo==null ? null : systemProxyInfo.getProxyHost();
        int proxyPort = systemProxyInfo==null ? 0 : systemProxyInfo.getProxyPort();
        return newHttpClient(proxyHost, proxyPort, ignoreCookies);
    }

    private void avoidSSLPeerUnverifiedException(DefaultHttpClient httpclient) {
        try {

            // set up a TrustManager that trusts everything
            javax.net.ssl.KeyManager[] keyManagers = null;
            TrustManager[] trustManagers = new TrustManager[] { new X509TrustManager() {
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    //logger.debug("getAcceptedIssuers =============");
                    return null;
                }

                public void checkClientTrusted(
                        java.security.cert.X509Certificate[] certs, String authType) {
                    //logger.debug("checkClientTrusted =============");
                }

                public void checkServerTrusted(
                        java.security.cert.X509Certificate[] certs, String authType) {
                    //logger.debug("checkServerTrusted =============");
                }
            } };
            javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("SSL");
                sslContext.init(keyManagers, trustManagers, new java.security.SecureRandom());
            org.apache.http.conn.scheme.SocketFactory ssf = new SSLSocketFactory(sslContext);//SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
            ClientConnectionManager ccm = httpclient.getConnectionManager();
            SchemeRegistry sr = ccm.getSchemeRegistry();
            sr.register(new org.apache.http.conn.scheme.Scheme("https", ssf, HTTPS_PORT));
        } catch (Exception e) {
            logger.error("Error in avoidSSLPeerUnverifiedException()", e);
        }
    }

    private DefaultHttpClient newHttpClient(String proxyHost, int proxyPort, Boolean ignoreCookies) {

        DefaultHttpClient httpClient = new DefaultHttpClient();

        if (avoidSSLPeerUnverifiedException) {
            avoidSSLPeerUnverifiedException(httpClient);
        }

        HttpParams httpParams = httpClient.getParams();
        if (connectionTimeoutMillis!=null) {
            HttpConnectionParams.setConnectionTimeout(httpParams, connectionTimeoutMillis);
        }
        if (socketTimeoutMillis!=null) {
            HttpConnectionParams.setSoTimeout(httpParams, socketTimeoutMillis);
        }
        if (tcpNoDelay!=null) {
            HttpConnectionParams.setTcpNoDelay(httpParams, tcpNoDelay);
        }

        if (StringUtils.isNotEmpty(proxyHost) && proxyPort>0) {
            HttpHost proxy = new HttpHost(proxyHost, proxyPort);
            httpClient.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, proxy);
            logger.debug("Using proxy: {}:{}", proxyHost, proxyPort);
        } else {
            logger.debug("Not using proxy.");
        }

        httpClient.getParams().setParameter("http.useragent", userAgent);
        httpClient.getParams().setParameter(CoreProtocolPNames.PROTOCOL_VERSION, HttpVersion.HTTP_1_1);
        httpClient.getParams().setBooleanParameter(ClientPNames.HANDLE_REDIRECTS, handleRedirects);

        if (ignoreCookies!=null) {
            if (ignoreCookies) {
                //newHttpClient.getParams().setParameter(ClientPNames.COOKIE_POLICY,
                //        CookiePolicy.IGNORE_COOKIES);
                // experimental, but could speed it up:
                httpClient.setCookieStore(null);
                httpClient.setCookieSpecs(null);
            } else {
                httpClient.getParams().setParameter(ClientPNames.COOKIE_POLICY,
                        CookiePolicy.BROWSER_COMPATIBILITY);
            }
        }

        if (retryCount>0) {
            httpClient.setHttpRequestRetryHandler(myRetryHandler);
        }

        return httpClient;
    }

    private final HttpRequestRetryHandler myRetryHandler = new HttpRequestRetryHandler() {

        public boolean retryRequest(
                IOException exception,
                int executionCount,
                HttpContext context) {
            logger.debug("HttpRequestRetryHandler({}).retryRequest({}, {}, {})", retryCount, exception, executionCount, context);
            if (executionCount >= retryCount) {
                // Do not retry if over max retry count
                return false;
            }
            if (exception instanceof InterruptedIOException) {
                // Timeout
                logger.debug("is InterruptedIOException");
                // ACHTUNG: RETRY HATTE SO NICHT FUNKTIONIERT:
                //return false;
            }
            if (exception instanceof UnknownHostException) {
                // Unknown host
                logger.debug("is UnknownHostException");
                return false;
            }
            if (exception instanceof ConnectException) {
                // Connection refused
                logger.debug("is ConnectException");
                return false;
            }
            if (exception instanceof SSLException) {
                // SSL handshake exception
                logger.debug("is SSLException");
                return false;
            }
            HttpRequest request = (HttpRequest) context.getAttribute(
                    ExecutionContext.HTTP_REQUEST);
            boolean idempotent = !(request instanceof HttpEntityEnclosingRequest);
            logger.debug("idempotent = {}", idempotent);
            // Retry if the request is considered idempotent
            return idempotent;
        }

    };


    //##################################################
    //### Logging & Debugging stuff
    //##################################################

    /** The purpose of this is to avoid unnecessary string concats if loglevel is not debug */
    /*protected void logDebug(Object ... messageParts) {
        if ((LOGGING_ALWAYS_TO_SYSOUT && logDebugToSysOut) || logger.isDebugEnabled()) {
            if (messageParts!=null && messageParts.length>0) {
                StringBuilder sb = new StringBuilder(128);
                for (Object messagePart : messageParts) {
                    sb.append(messagePart);
                }
                logDebugImpl(sb.toString());
            }
        }
    }

    private void logDebugImpl(String msg) {
        if (LOGGING_ALWAYS_TO_SYSOUT && logDebugToSysOut) {
            System.out.println(msg);
        }
        logger.debug(msg);
    }*/

    /*protected void logError(String msg, Exception e) {
        if (LOGGING_ALWAYS_TO_SYSOUT && logErrorToSysOut) {
            System.out.println(msg + e);
            e.printStackTrace();
        }
        logger.error(msg, e);
    }

    protected void logError(String msg) {
        if (LOGGING_ALWAYS_TO_SYSOUT && logErrorToSysOut) {
            System.out.println(msg);
        }
        logger.error(msg);
    }*/

    protected void logRequestHeaders(HttpRequest request) {
        if (logHeaders && request!=null) {
            Header[] headers = request.getAllHeaders();
            logger.debug("Request Headers: ");
            if (headers!=null && headers.length>0) {
                for (Header header : headers) {
                    logger.debug(" RequestHeader {} = {} / header.elements.length={}", header.getName(), header.getValue(), header.getElements().length);
                }
            } else {
                logger.debug("No headers found.");
            }
        }
    }

    protected void logResponseHeaders(HttpResponse response) {
        if (logHeaders && response!=null) {
            Header[] headers = response.getAllHeaders();
            logger.debug("Response Headers: ");
            if (headers!=null && headers.length>0) {
                for (Header header : headers) {
                    logger.debug(" ResponseHeader {} = {} / header.elements.length={}", header.getName(), header.getValue(), header.getElements().length);
                }
            } else {
                logger.debug("No headers found.");
            }
        }
    }

    protected void logCookies(HttpContext httpContext) {
        if (logCookies && httpContext!=null) {
            CookieStore cookieStore = (CookieStore)httpContext.getAttribute(ClientContext.COOKIE_STORE);
            logger.debug("Cookies: ");
            List<Cookie> cookies = cookieStore==null ? null : cookieStore.getCookies();
            if (cookies!=null && cookies.size()>0) {
                for (Cookie cookie : cookies) {
                    logger.debug(" Cookie {} = {} / {}", cookie.getName(), cookie.getValue(), cookie);
                }
            } else {
                logger.debug("No cookies found.");
            }
        }
    }

}
