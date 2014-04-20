package de.mhellmann.net.fluenthttp;

import org.apache.commons.lang3.CharEncoding;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * Date: 24.03.14
 * Time: 12:45
 *
 * @author <a href="mailto:marten.hellmann@web.de"><strong>Marten Hellmann</strong></a>
 */
public class FluentHttpPostMethodBuilder extends FluentHttpMethodBuilder {

    private Map<String, String> postParams;
    private String requestBodyString;

    FluentHttpPostMethodBuilder(FluentHttpClient httpClient, Logger logger, String url) {
        super(httpClient, logger, url);
    }

    public FluentHttpPostMethodBuilder withParams(Map<String, String> postParams) {
        this.postParams = postParams;
        return this;
    }

    public FluentHttpPostMethodBuilder withParam(String paramName, String paramValue) {
        if (postParams==null) {
            postParams = new LinkedHashMap<String, String>();
        }
        postParams.put(paramName, paramValue);
        return this;
    }

    public FluentHttpPostMethodBuilder withRequestBody(String requestBodyString) {
        this.requestBodyString = requestBodyString;
        return this;
    }

    /** Applying the execute-around-pattern */
    protected Object executeAroundHttpMethod(ResponseHandler responseHandler) throws IOException {
        if (login==null) {
            logger.debug("FluentHttpClient.post({}, {}, {})", url, proxyInfo, ignoreCookies);
        } else {
            logger.debug("FluentHttpClient.post({}, {}, {}, {}, {})", url, proxyInfo, ignoreCookies, login, password);
        }
        DefaultHttpClient httpClient = null;
        try {
            httpClient = fluentHttpClient.newHttpClient(proxyInfo, ignoreCookies);
            HttpPost post = newPost(url, postParams);
            HttpContext httpContext = handleAuthenticationCookiesAndHeaders(httpClient, post);

            if (requestBodyString!=null) {
                // example code for file upload:
                //FileEntity fileEntity=new FileEntity(new File(filename),"multipart/form-data");
                //StringEntity msgEntity = new StringEntity(msg, CharEncoding.UTF_8);
                StringEntity requestBodyStringEntity = new StringEntity(requestBodyString, CharEncoding.UTF_8);

                //post.setEntity(fileEntity);
                //post.setEntity(msgEntity);
                post.setEntity(requestBodyStringEntity);
            }

            HttpResponse response;
            if (httpContext==null) {
                response = httpClient.execute(post);
            } else {
                response = httpClient.execute(post, httpContext);
                if (ignoreCookies==null || !ignoreCookies) {
                    fluentHttpClient.logCookies(httpContext);
                }
            }
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
            logger.error("FluentHttpClient.post({}): {}", url, (msg==null ? "" : msg), e);
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
            logger.error("HttpClient4.post({}): Error in newHttpClient.getConnectionManager().shutdown()", url, e);
        }
    }

    private HttpPost newPost(String url, Map<String, String> postParams) throws UnsupportedEncodingException {
        HttpPost httpPost = new HttpPost(url);
        if (postParams!=null && postParams.size()>0) {
            List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(postParams.size());
            for (Map.Entry<String,String> entry : postParams.entrySet()) {
                nameValuePairs.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
            }
            httpPost.setEntity(new UrlEncodedFormEntity(nameValuePairs));
        }
        httpPost.setHeader("ContentType", "application/x-www-form-urlencoded");
        return httpPost;
    }

}
