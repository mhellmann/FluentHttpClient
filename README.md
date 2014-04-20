FluentHttpClient
================

Fluent Interface for the standard Apache HttpClient v4.2.6

// creating an HttpClient instance with session handling ..
FluentHttpClient client = new FluentHttpClient()
    .withSocketTimeoutMillis(1500)
    .withConnectionTimeoutMillis(1500)
    .withRetries(3)
    .withRedirectHandling()
    .withLoggingCookies()
    .withLoggingHeaders()
    .withReusingLastCookieStore(true); // keep session cookie
      
//.. using the client for logging in..
StatusLine statusLine = client.get(url1)
    .withBaseAuthentication(login, password)
    .withAutoSystemProxy()
    .asStatusLine();
                
//.. and now for loading content by executing http get or post methods
String content = client
    .post(url2)
    .withParams(params)
    .withAutoSystemProxy()
    .asString();
    
Simple!! :)
