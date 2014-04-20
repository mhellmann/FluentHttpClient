package de.mhellmann.net.fluenthttp;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.http.*;
import org.apache.http.auth.*;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.conn.EofSensorInputStream;
import org.apache.http.conn.EofSensorWatcher;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.zip.CRC32;


/**
 * Created with IntelliJ IDEA.
 * Date: 24.03.14
 * Time: 12:50
 *
 * @author <a href="mailto:marten.hellmann@web.de"><strong>Marten Hellmann</strong></a>
 */
public abstract class FluentHttpMethodBuilder {

    private static final int HTTP_200 = 200;

    protected final FluentHttpClient fluentHttpClient;
    protected final Logger logger;
    protected final String url;
    protected ProxyInfo proxyInfo;
    protected Integer statusCode;
    protected Boolean ignoreCookies;
    protected String login;
    protected String password;
    protected Map<String, String> headers;
    protected Set<Integer> allowedStatusCodes;

    protected byte[] bytes;
    
    FluentHttpMethodBuilder(FluentHttpClient fluentHttpClient, Logger logger, String url) {
        this.fluentHttpClient = fluentHttpClient;
        this.logger = logger;
        this.url = url;
    }

    public FluentHttpMethodBuilder withHeader(Map<String, String> headers) {
        this.headers = headers;
        return this;
    }

    public FluentHttpMethodBuilder withHeader(String headerName, String headerValue) {
        if (headers==null) {
            headers = new LinkedHashMap<String, String>();
        }
        headers.put(headerName, headerValue);
        return this;
    }

    //###################################################################
    //### Load & get result
    //#######################
    
    protected abstract Object executeAroundHttpMethod(ResponseHandler responseHandler) throws IOException;

    /**
     * @return the HttpResponse as a byte array
     * @throws java.io.IOException
     */
    public byte[] asBytes() throws IOException {
        ResponseHandler getBytes = new ResponseHandler() {
            @Override
            public byte[] computeResult(DefaultHttpClient httpClient, HttpResponse response) throws IOException {
                if (response==null) {
                    logger.debug("{}.asBytes({}) loaded: HttpResponse is null.", getClass().getSimpleName(), url);
                    throw new IOException("HttpResponse is null.");
                } else {
                    //Locale locale = response.getLocale();
                    logger.debug("{}.asBytes({}) loaded: ", getClass().getSimpleName(), url, response.getStatusLine());
                    int statusCode = response.getStatusLine().getStatusCode();
                    if (isAllowedStatusCode(statusCode)) {
                        HttpEntity httpEntity = response.getEntity();
                        if (httpEntity != null) {
                            return IOUtils.toByteArray(httpEntity.getContent());
                        }
                    }

                    // we return the status code with the exception for further processing by the calling client
                    throw new FluentHttpClientStatusCodeException("Status line " + response.getStatusLine() + " was returned for " + url, response.getStatusLine().getStatusCode());
                }
            }

            @Override
            public boolean isHandlingConnectionShutdown() {
                return false;
            }
        };

        return (byte[]) executeAroundHttpMethod(getBytes);
    }

    protected boolean isAllowedStatusCode(int statusCode) {
        return statusCode == HTTP_200 || (allowedStatusCodes!=null && allowedStatusCodes.contains(statusCode));
    }

    protected HttpContext handleAuthenticationCookiesAndHeaders(DefaultHttpClient httpClient, HttpRequestBase getOrPostMethod) {
        HttpContext httpContext = null;

        if (login!=null && password!=null) {
            //simpleBaseAuthentication(httpClient, url, login, password);
            httpContext = preemptiveBaseAuthentication(httpClient, login, password);
        }

        boolean getCookies = ignoreCookies==null || !ignoreCookies;
        if (getCookies) {
            httpContext = fluentHttpClient.newCookieStore(httpContext);
        }

        if (headers!=null) {
            for (Map.Entry<String,String> header : headers.entrySet()) {
                getOrPostMethod.setHeader(header.getKey(), header.getValue());
            }
        }

        return httpContext;
    }

    /**
     * Executes an HttpGet to the specified url and returns the status code without actually loading the content.
     */
    public StatusLine asStatusLine() throws IOException {
        ResponseHandler getStatusLine = new ResponseHandler() {
            @Override
            public StatusLine computeResult(DefaultHttpClient httpClient, HttpResponse response) throws IOException {
                if (response==null) {
                    logger.debug("{}.asStatusLine({}) loaded: HttpResponse is null.", getClass().getSimpleName(), url);
                    throw new IOException("HttpResponse is null.");
                } else {
                    //Locale locale = response.getLocale();
                    logger.debug("{}.asStatusLine({}) loaded: ", getClass().getSimpleName(), url, response.getStatusLine());
                    return response.getStatusLine();
                }
            }

            @Override
            public boolean isHandlingConnectionShutdown() {
                return false;
            }
        };

        return (StatusLine) executeAroundHttpMethod(getStatusLine);
    }

    /**
     * Executes an HttpGet to the specified url.
     * The http connection is automatically closed when the stream gets closed!!
     *
     * @return the content as a byte array
     * @throws java.io.IOException
     */
    public InputStream asStream() throws IOException {
        ResponseHandler getStream = new ResponseHandler() {
            @Override
            public InputStream computeResult(DefaultHttpClient httpClient, HttpResponse response) throws IOException {
                if (response==null) {
                    logger.debug("{}.asStream({}) loaded: HttpResponse is null.", getClass().getSimpleName(), url);
                    throw new IOException("HttpResponse is null.");
                } else {
                    //Locale locale = response.getLocale();
                    logger.debug("{}.asStream({}) loaded: ", getClass().getSimpleName(), url, response.getStatusLine());
                    int statusCode = response.getStatusLine().getStatusCode();
                    if (isAllowedStatusCode(statusCode)) {
                        HttpEntity httpEntity = response.getEntity();
                        if (httpEntity!=null) {
                            InputStream inputStream = httpEntity.getContent();
                            MyEofSensorWatcher myEofSensorWatcher = new MyEofSensorWatcher(httpClient);
                            // Automatically shutdown the ConnectionManager when the calling client closes the returned FileInputStream
                            return new EofSensorInputStream(inputStream, myEofSensorWatcher);
                        }
                    }

                    // we return the status code with the exception for further processing by the calling client
                    throw new FluentHttpClientStatusCodeException("Status line " + response.getStatusLine() + " was returned for " + url, response.getStatusLine().getStatusCode());
                }
            }

            @Override
            public boolean isHandlingConnectionShutdown() {
                return true;
            }
        };

        return (InputStream) executeAroundHttpMethod(getStream);
    }

    public String asString() throws IOException {
        if (bytes==null) {
            bytes = asBytes();
        }
        return bytes==null ? null : new String(bytes);
    }
    
    public long asCRC32() throws IOException {
        if (bytes==null) {
            bytes = asBytes();
        }
        CRC32 crc32 = new CRC32();
        crc32.update(bytes);
        return crc32.getValue();
    }

    protected static final class MyEofSensorWatcher implements EofSensorWatcher {

        private DefaultHttpClient httpClient = null;

        protected MyEofSensorWatcher(DefaultHttpClient httpClient) {
            this.httpClient = httpClient;
        }

        @Override
        public synchronized boolean eofDetected(InputStream inputStream) throws IOException {
            //System.out.println("EofSensorWatcher:.eofDetected()");
            return false;
        }

        @Override
        public synchronized boolean streamClosed(InputStream inputStream) throws IOException {
            //System.out.println("EofSensorWatcher:.streamClosed()");
            if (httpClient!=null) {
                // This really works!! HttpClient4 is great stuff !!!
                //System.out.println("EofSensorWatcher: Calling httpClient.getConnectionManager().shutdown() on stream.closed ! ");
                httpClient.getConnectionManager().shutdown();
                httpClient = null;
            }
            return true;
        }

        @Override
        public synchronized boolean streamAbort(InputStream inputStream) throws IOException {
            //System.out.println("EofSensorWatcher:.streamAbort()");
            return false;
        }
    }

    protected interface ResponseHandler {

        Object computeResult(DefaultHttpClient httpClient, HttpResponse response) throws IOException;
        boolean isHandlingConnectionShutdown();

    }


    //###################################################################
    //### Other builder stuff
    //#######################

    /**
     * @param ignoreCookies            null -> do nothing
     *                                 true -> set Ignore Cookies
     *                                 false -> print/debug cookies
     */
    public FluentHttpMethodBuilder withIgnoringCookies(boolean ignoreCookies) {
        this.ignoreCookies = ignoreCookies;
        return this;
    }
    
    public FluentHttpMethodBuilder withBaseAuthentication(String login, String password) {
        this.login = login;
        this.password = password;
        return this;
    }

    public FluentHttpMethodBuilder withAllowedStatusCodes(int ... statusCodes) {
        if (statusCodes!=null) {
            if (allowedStatusCodes==null) {
                allowedStatusCodes = new HashSet<Integer>(statusCodes.length);
            }
            for (int statusCode : statusCodes) {
                allowedStatusCodes.add(statusCode);
            }
        }
        return this;
    }

    //###################################################################
    //### Proxy builder stuff
    //#######################

    /**
     * Method withAutoSystemProxy() is slow, so we may later decide to make class ProxyInfo public
     * and allow clients to reuse it for subsequent calls to the same urls.
     */
    /*public FluentHttpMethodBuilder withProxy(ProxyInfo proxyInfo) {
        this.proxyInfo = proxyInfo;
        return this;
    }*/

    public FluentHttpMethodBuilder withProxy(String proxyHost, int proxyPort) {
        this.proxyInfo = new ProxyInfo(proxyHost, proxyPort);
        return this;
    }

    public FluentHttpMethodBuilder withHttpSystemProxy() {
        this.proxyInfo = new ProxyInfo(false);
        return this;
    }

    public FluentHttpMethodBuilder withHttpsSystemProxy() {
        this.proxyInfo = new ProxyInfo(true);
        return this;
    }

    public FluentHttpMethodBuilder withAutoSystemProxy() throws URISyntaxException {
        this.proxyInfo = new ProxyInfo(url);
        return this;
    }

    protected final class ProxyInfo {
        String proxyHost = null;
        int proxyPort = 0;
        String[] nonProxyHosts = {};

        public ProxyInfo(String proxyHost, int proxyPort) {
            this.proxyHost = proxyHost;
            this.proxyPort = proxyPort;
        }

        public ProxyInfo(String url) throws URISyntaxException {
            URI uri = new URI(url);
            boolean https = url.startsWith("https");
            if (!https) {
                nonProxyHosts = StringUtils.split(StringUtils.defaultString(System.getProperty("http.nonProxyHosts")), '|');
                if (nonProxyHosts==null || !ArrayUtils.contains(nonProxyHosts, uri.getHost())) {
                    httpProxy();
                } else {
                    logger.debug("Not using http proxy due to http.nonProxyHosts={}", System.getProperty("http.nonProxyHosts"));
                }
            } else {
                nonProxyHosts = StringUtils.split(StringUtils.defaultString(System.getProperty("https.nonProxyHosts")), '|');
                if (nonProxyHosts==null || !ArrayUtils.contains(nonProxyHosts, uri.getHost())) {
                    httpsProxy();
                } else {
                    logger.debug("Not using https proxy due to https.nonProxyHosts={}", System.getProperty("https.nonProxyHosts"));
                }
            }

        }

        /** Simpifiziert, aber keine Auswertung der NonProxyHosts !!! */
        public ProxyInfo(boolean https) {
            if (!https) {
                httpProxy();
            } else {
                httpsProxy();
            }

        }

        protected void httpProxy() {
            proxyHost = System.getProperty("http.proxyHost");
            try {
                proxyPort = NumberUtils.toInt(System.getProperty("http.proxyPort"));
            } catch (Exception e) {
                logger.error("Invalid system property http.proxyPort : {}", System.getProperty("http.proxyPort"));
            }
        }

        protected void httpsProxy() {
            proxyHost = System.getProperty("https.proxyHost");
            try {
                proxyPort = NumberUtils.toInt(System.getProperty("https.proxyPort"));
            } catch (Exception e) {
                logger.error("Invalid system property https.proxyPort : {}", System.getProperty("https.proxyPort"));
            }
        }

        public String getProxyHost() {
            return proxyHost;
        }

        public int getProxyPort() {
            return proxyPort;
        }

        @Override
        public String toString() {
            String s = "Proxy{" +
                    "proxyHost='" + proxyHost + '\'' +
                    ", proxyPort=" + proxyPort;
            if (nonProxyHosts!=null && nonProxyHosts.length>0) {
                s += ", nonProxyHosts=" + Arrays.asList(nonProxyHosts);
            }
            s += '}';
            return s;
        }
    }


    //#######################################################################
    //### Authentication stuff
    //#################################

    private static class PreemptiveAuthInterceptor implements HttpRequestInterceptor {

        public void process(final HttpRequest request, final HttpContext context) throws HttpException, IOException {
            AuthState authState = (AuthState) context.getAttribute(ClientContext.TARGET_AUTH_STATE);

            // If no auth scheme avaialble yet, try to initialize it
            // preemptively
            if (authState.getAuthScheme() == null) {
                AuthScheme authScheme = (AuthScheme) context.getAttribute("preemptive-auth");
                CredentialsProvider credsProvider = (CredentialsProvider) context.getAttribute(ClientContext.CREDS_PROVIDER);
                HttpHost targetHost = (HttpHost) context.getAttribute(ExecutionContext.HTTP_TARGET_HOST);
                if (authScheme != null) {
                    Credentials creds = credsProvider.getCredentials(new AuthScope(targetHost.getHostName(), targetHost.getPort()));
                    if (creds == null) {
                        throw new HttpException("No credentials for preemptive authentication");
                    }
                    authState.setAuthScheme(authScheme);
                    authState.setCredentials(creds);
                }
            }

        }

    }

    protected static HttpContext preemptiveBaseAuthentication(DefaultHttpClient httpClient, String login, String password) {
        // http://stackoverflow.com/questions/2014700/preemptive-basic-authentication-with-apache-httpclient-4
        Credentials credentials = new UsernamePasswordCredentials(login, password);
        httpClient.getCredentialsProvider().setCredentials(AuthScope.ANY, credentials);
        HttpContext httpContext = new BasicHttpContext();
        BasicScheme basicAuth = new BasicScheme();
        httpContext.setAttribute("preemptive-auth", basicAuth);
        httpClient.addRequestInterceptor(new PreemptiveAuthInterceptor(), 0);
        return httpContext;

        // alternative impl.
        //AuthCache authCache = new BasicAuthCache();
        //BasicScheme basicAuth = new BasicScheme();
        //HttpHost targetHost = new HttpHost("api.heroku.com", -1, "https");
        //authCache.put(targetHost, basicAuth);
    }

    /* alternative impl.
    protected static void simpleBaseAuthentication4get(HttpGet get, String login, String password) {
        String userpass = login + ":" + password;
        String basicAuth = "Basic " + new String(Base64.encode(userpass.getBytes()));
        get.setHeader("Accept",  "application/xml");
        get.setHeader("Authorization", basicAuth);
    }

    protected static void simpleBaseAuthentication(DefaultHttpClient httpClient, String url, String login, String password)
            throws URISyntaxException {
        URI uri = new URI(url);
        httpClient.getCredentialsProvider().setCredentials(
                new AuthScope(uri.getHost(), uri.getPort(), AuthScope.ANY_SCHEME),
                new UsernamePasswordCredentials(login, password));
    }*/

}
