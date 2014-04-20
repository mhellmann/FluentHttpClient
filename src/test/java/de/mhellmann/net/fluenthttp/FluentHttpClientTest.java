package de.mhellmann.net.fluenthttp;

import de.mhellmann.util.Log4JUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.StatusLine;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import static de.mhellmann.util.TestUtils.loadPropertiesFromPackage;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.CombinableMatcher.both;
import static org.hamcrest.text.IsEmptyString.isEmptyString;
import static org.junit.Assert.*;

/**
 * Created with IntelliJ IDEA.
 * Date: 20.03.14
 * Time: 23:06
 *
 * @author <a href="mailto:marten.hellmann@web.de"><strong>Marten Hellmann</strong></a>
 */
public class FluentHttpClientTest {
    
    private static final boolean LOGGING_TO_SYSOUT = true;
    private static final Logger LOG = LoggerFactory.getLogger(FluentHttpClientTest.class);
    static {
        if (LOGGING_TO_SYSOUT) {
            Log4JUtils.addConsoleAppender(LOG);
        }
    }

    private static final String LOGINS_PROPERTYFILE = "test-logins.properties";
    
    private Properties testLogins;

    @Before
    public void printProxy() {
        new FluentHttpClient()
                .withLoggingToSysOut(LOGGING_TO_SYSOUT)
                .withRethrowingExceptions(true);
        LOG.debug("http.proxyHost: {}", System.getProperty("http.proxyHost"));
        LOG.debug("http.proxyHost: {}", System.getProperty("http.proxyHost"));
        LOG.debug("http.proxyPort: {}", System.getProperty("http.proxyPort"));
        LOG.debug("https.proxyHost: {}", System.getProperty("https.proxyHost"));
        LOG.debug("https.proxyPort: {}", System.getProperty("https.proxyPort"));
    }

    @Before
    public void loadPasswords() throws IOException {
        // loading logins/passwords from test-logins.properties
        testLogins = loadPropertiesFromPackage(getClass(), LOGINS_PROPERTYFILE);
    }

    @Test
    public void testGet200AsBytesWithNoCacheHeader() throws IOException {
        FluentHttpClient client = new FluentHttpClient()
                .withLoggingToSysOut(LOGGING_TO_SYSOUT);
        byte[] bytes = client.get("http://www.heise.de")
                .withHttpSystemProxy()
                .withIgnoringCookies(false)
                .withHeader("Cache-Control", "no-cache")
                .asBytes();
        assertNotNull("Failed to load heise.de", bytes);
        LOG.debug("Loaded {} bytes.", bytes.length);
        String content = new String(bytes);
        assertThat("Loaded content invalid?", content, containsString("heise"));

    }

    @Test
    public void testGet200AsCRC32WithAutoSystemProxy() throws IOException, URISyntaxException {
        FluentHttpClient client = new FluentHttpClient()
                .withLoggingToSysOut(LOGGING_TO_SYSOUT);
        long crc32 = client.get("http://www.heise.de")
                .withAutoSystemProxy()
                .withIgnoringCookies(true)
                .asCRC32();
        LOG.debug("CRC32: {}", crc32);
        assertThat("CRC32", crc32, greaterThan(0L));
   }

    @Test
    public void testGet200AsStreamWithProxy() throws IOException {
        FluentHttpClient client = new FluentHttpClient()
                .withLoggingToSysOut(LOGGING_TO_SYSOUT)
                .withLoggingCookies()
                .withLoggingHeaders()
                .withUserAgent("Chrome");
        InputStream inputStream = client.get("http://www.heise.de")
                .withHttpSystemProxy()
                .asStream();
        String content = IOUtils.toString(inputStream);
        assertNotNull("Failed to load heise.de", content);
        LOG.debug("Loaded {} bytes", content.length());
        assertThat("Loaded content invalid?", content, containsString("heise"));
    }

    @Test(expected=UnknownHostException.class)
    public void testGetNonExistingHostWithRetrow() throws IOException {
        FluentHttpClient client = new FluentHttpClient()
                .withLoggingToSysOut(LOGGING_TO_SYSOUT);
        client.get("http://www.thisisnotexistingnowhere.com/")
              .withHttpSystemProxy().asString();
    }

    @Test(expected=FluentHttpClientStatusCodeException.class)
    public void testGet404WithRetrow() throws IOException {
        FluentHttpClient client = new FluentHttpClient()
                .withLoggingToSysOut(LOGGING_TO_SYSOUT);
        // returns 404:
        try {
            client.get("http://www.heise.de/security/meldung/Klaffende-Loecher-in-Oracles-Java-Cloud-freigelegt-2162v248.html")
                  .withHttpSystemProxy().asString();
        } catch (FluentHttpClientStatusCodeException e) {
            assertEquals("FluentHttpClientStatusCodeException Code", e.getStatusCode(), 404);
            assertNotNull("FluentHttpClientStatusCodeException StatusLine", e.getStatusLine());
            throw e;
        }
    }

    /**
     * I know some code nazis would say "Don't swallow exceptions!" !
     *
     * But I want to leave it to the application developer to decide by himself,
     * thus we support both coding styles here.
     * Just that rethrowing exceptions is the default ...
     *
     * @throws IOException
     */
    @Test
    public void testGet404WithExceptionSwallowing() throws IOException {
        FluentHttpClient client = new FluentHttpClient()
                .withRethrowingExceptions(false);
        // returns 404:
        String content = client.get("http://www.heise.de/security/meldung/Klaffende-Loecher-in-Oracles-Java-Cloud-freigelegt-2162v248.html")
                .withHttpSystemProxy()
                .asString();
        LOG.debug("content = {}", content);
        assertNull("404 content cannot be loaded without adding 404 to the allowed status codes", content);
    }

    @Test
    public void testGet404HtmlContent() throws IOException {
        FluentHttpClient client = new FluentHttpClient()
                .withLoggingToSysOut(LOGGING_TO_SYSOUT);
        // returns 404 but the content is anyway loaded:
        String content = client.get("http://www.heise.de/security/meldung/Klaffende-Loecher-in-Oracles-Java-Cloud-freigelegt-2162v248.html")
                .withHttpSystemProxy()
                .withAllowedStatusCodes(404)
                .asString();
        LOG.debug("content = {}", content);
        assertNotNull("Could not load html from 404 page", content);
        assertThat("404 page invalid?", content, containsString("404"));
    }

    @Test(expected=SocketTimeoutException.class)
    public void testRetryHandler() throws IOException {
        FluentHttpClient client = new FluentHttpClient()
                .withLoggingToSysOut(LOGGING_TO_SYSOUT)
                .withSocketTimeoutMillis(50)
                .withConnectionTimeoutMillis(8000)
                .withRetries(3);
        String content = client.get("http://www.ebay.de/")
                .withHttpSystemProxy()
                .asString();
        assertNotNull("Content", content);
        LOG.debug("Loaded content: {} bytes.", content.length());
    }

    @Test
    @Ignore
    public void testWikiWithLoginAndSessionHandling() throws IOException, URISyntaxException {
        FluentHttpClient client = new FluentHttpClient()
                .withLoggingToSysOut(LOGGING_TO_SYSOUT)
                .withSocketTimeoutMillis(1500)
                .withConnectionTimeoutMillis(1500)
                .withRetries(3)
                .withReusingLastCookieStore(true)
                .withAvoidingSSLPeerUnverifiedException();

        String url1 = testLogins.getProperty("wiki.url1");
        assertThat("Url1", url1, both(not(isEmptyString())).and(notNullValue()));
        String url2 = testLogins.getProperty("wiki.url2");
        assertThat("Url2", url2, both(not(isEmptyString())).and(notNullValue()));
        String login = testLogins.getProperty("wiki.login");
        assertThat("Login", login, both(not(isEmptyString())).and(notNullValue()));
        String password = testLogins.getProperty("wiki.password");
        assertThat("Password", password, both(not(isEmptyString())).and(notNullValue()));

        String content = client.get(url1)
                .withBaseAuthentication(login, password)
                .withAutoSystemProxy()
                .asString();
        assertNotNull("Failed to load " + url1, content);
        LOG.debug("Loaded content: {}", content);
        //assertThat(content, either(containsString("color")).or(containsString("colour")));
        assertThat(content, containsString("Plattform-Betreiber"));

        // Now for subsequent requests, we need not to login again
        String content2 = client.get(url2)
                .withAutoSystemProxy()
                .asString();
        System.out.println("content2 = " + content2);
        assertNotNull("Failed to load " + url2, content2);
        assertThat(content2, containsString("Feldbeschreibung"));
        LOG.debug("Loaded content: {} bytes", content2.length());
    }

    @Test
    @Ignore
    public void testReutersWithLogin() throws IOException {
        FluentHttpClient client = new FluentHttpClient()
            .withLoggingToSysOut(LOGGING_TO_SYSOUT, LOGGING_TO_SYSOUT )
            .withSocketTimeoutMillis(3000)
            .withConnectionTimeoutMillis(3000)
            .withRetries(2)
            .withRedirectHandling()
            .withLoggingCookies()
            .withLoggingHeaders()
            .withReusingLastCookieStore(true); // this enables login session handling

        Map<String,String> params = new LinkedHashMap<String, String>();
        params.put("__MOID__", "1688");
        params.put("__FH__", "LoginForm");

        String login = testLogins.getProperty("reuters.tools.login");
        assertThat("Login", login, both(not(isEmptyString())).and(notNullValue()));
        String password = testLogins.getProperty("reuters.tools.password");
        assertThat("Password", password, both(not(isEmptyString())).and(notNullValue()));

        // this creates a cookie-store with the login session
        StatusLine statusLine = client.post("https://commerce.us.reuters.com/login/pages/login/login.do?backUrl=http%3A%2F%2Fwww.reuters.com&backParameterEncoded=false&source=portfolio&flow=PORTFOLIO&entry_source=registration")
            .withParams(params)
            .withParam("backParameterEncoded", "false")
            .withParam("backUrl", "http://www.reuters.com/")
            .withParam("source", "")
            .withParam("trackingSource", "")
            .withParam("loginName", login)
            .withParam("password", password)
            .withHttpSystemProxy()
            .asStatusLine();
        LOG.debug("StatusLine: {}", statusLine);
        assertEquals("Reuters login returned status code " + statusLine.getStatusCode(), 200, statusLine.getStatusCode());

        LOG.debug("CookieStore: {}", client.getCookieStore());

        // now we can execute normal get requests
        String content = client
            .get("http://www.reuters.com/finance/stocks/incomeStatement/detail?stmtType=BAL&perType=ANN&symbol=MTELy.F")
            .withHttpSystemProxy().asString();
        assertNotNull("Failed to load reuters", content);
        LOG.debug(content);
        LOG.debug("Loaded content: {} bytes", content.length());
    }

    @Test
    @Ignore
    public void testPostWithBaseAuthentication() throws IOException {
        FluentHttpClient client = new FluentHttpClient()
            .withLoggingToSysOut(LOGGING_TO_SYSOUT)
            .withRedirectHandling()
            .withSocketTimeoutMillis(30000)
            .withConnectionTimeoutMillis(20000)
            .withTCPNoDelay(true)
            .withRetries(0);
        LOG.debug("client = {} ", client);

        String url = testLogins.getProperty("svn.tools.url");
        assertThat("Url", url, both(not(isEmptyString())).and(notNullValue()));
        String login = testLogins.getProperty("svn.tools.login");
        assertThat("Login", login, both(not(isEmptyString())).and(notNullValue()));
        String password = testLogins.getProperty("svn.tools.password");
        assertThat("Password", password, both(not(isEmptyString())).and(notNullValue()));

        String content = client.get(url)
            .withBaseAuthentication(login, password)
            .asString();
        assertNotNull("testPostWithBaseAuthentication failed", content);
        LOG.debug("{} returned:", url);
        LOG.debug(content);
    }

    @Test
    public void testPostHttpsWithFormBasedAuthentication() throws IOException {
        FluentHttpClient client = new FluentHttpClient()
            .withLoggingToSysOut(LOGGING_TO_SYSOUT)
            .withRedirectHandling()
            .withSocketTimeoutMillis(30000)
            .withConnectionTimeoutMillis(20000)
            .withTCPNoDelay(true)
            .withRetries(0);
        LOG.debug("client = {}", client);

        String login = testLogins.getProperty("boris.login");
        assertThat("Login", login, both(not(isEmptyString())).and(notNullValue()));
        String password = testLogins.getProperty("boris.password");
        assertThat("Password", password, both(not(isEmptyString())).and(notNullValue()));

        StatusLine statusLine = client.post("https://www.boris.niedersachsen.de/boris/login")
            .withParam("username", login)
            .withParam("password", password)
            .withParam("submit", "Anmelden")
            .asStatusLine();
        LOG.debug("StatusLine: {}", statusLine);
        assertNotNull("Failed to log into boris", statusLine);
        assertThat("Boris login returned status code ", statusLine.getStatusCode(), is(200));
    }

}
