package de.mhellmann.net.fluenthttp;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.UnknownHostException;

/**
 * Created with IntelliJ IDEA.
 * Date: 24.03.14
 * Time: 12:36
 *
 * @author <a href="mailto:marten.hellmann@web.de"><strong>Marten Hellmann</strong></a>
 */
public class FluentHttpGetMethodBuilder extends FluentHttpMethodBuilder {

    FluentHttpGetMethodBuilder(FluentHttpClient httpClient, Logger logger, String url) {
        super(httpClient, logger, url);
    }

    /** Applying the execute-around-pattern to avoid duplicated code */
    protected Object executeAroundHttpMethod(ResponseHandler responseHandler) throws IOException {
        if (login==null) {
            logger.debug("FluentHttpClient.get({}, {}, {})", url, proxyInfo, ignoreCookies);
        } else {
            logger.debug("FluentHttpClient.get({}, {}, {}, {}, {})", url, proxyInfo, ignoreCookies, login, password);
        }
        DefaultHttpClient httpClient = null;
        try {
            httpClient = fluentHttpClient.newHttpClient(proxyInfo, ignoreCookies);
            HttpGet get = new HttpGet(url);
            HttpContext httpContext = handleAuthenticationCookiesAndHeaders(httpClient, get);

            HttpResponse response;
            if (httpContext==null) {
                response = httpClient.execute(get);
            } else {
                response = httpClient.execute(get, httpContext);
                if (ignoreCookies==null || !ignoreCookies) {
                    fluentHttpClient.logCookies(httpContext);
                }
            }
            fluentHttpClient.logRequestHeaders(get);
            fluentHttpClient.logResponseHeaders(response);

            return responseHandler.computeResult(httpClient, response);
        } catch (java.net.SocketTimeoutException e) {
            return handleException(e, null);
        } catch (UnknownHostException e) {
            return handleException(e, "Unknown host or Offline.");
        } catch (IOException e) {
            return handleException(e, null);
        } finally {
            closeConnection(httpClient, responseHandler);
        }
    }

    protected Object handleException(IOException e, String msg) throws IOException {
        if (fluentHttpClient.rethrowExceptions) {
            throw e;
        } else {
            logger.error("FluentHttpClient.get({}): {}", url, (msg==null ? "" : msg), e);
        }
        return null;
    }

    private void closeConnection(DefaultHttpClient httpClient, ResponseHandler responseHandler) {
        try {
            // When HttpClient instance is no longer needed,
            // shut down the connection manager to ensure
            // immediate deallocation of all system resources
            if (httpClient!=null && !responseHandler.isHandlingConnectionShutdown()) {
                httpClient.getConnectionManager().shutdown();
            }
        } catch (Exception e) {
            logger.error("HttpClient4.get({}): Error in newHttpClient.getConnectionManager().shutdown()", url, e);
        }
    }
}
