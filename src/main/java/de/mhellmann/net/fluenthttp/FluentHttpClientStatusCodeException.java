package de.mhellmann.net.fluenthttp;

import java.io.IOException;

/**
 * Created with IntelliJ IDEA.
 * Date: 24.03.14
 * Time: 12:25
 *
 * @author <a href="mailto:marten.hellmann@web.de"><strong>Marten Hellmann</strong></a>
 */
public class FluentHttpClientStatusCodeException extends IOException {

    private String statusLine;
    private int statusCode;

    public FluentHttpClientStatusCodeException(String statusLine, int statusCode) {
        super(statusLine);
        this.statusLine = statusLine;
        this.statusCode = statusCode;
    }

    public String getStatusLine() {
        return statusLine;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
