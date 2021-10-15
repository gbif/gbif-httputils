/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gbif.varnish;

import java.io.IOException;
import java.net.URI;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * Test VarnishPurger generated URI and Http headers
 */
public class VarnishPurgerTest {

  private static final String API_BASEURL = "http://api.gbif-dev.org/v1/";

  /**
   * Mock HttpClient to intercept the uri and headers without sending them.
   */
  private class MockCloseableHttpClientTest
      extends org.apache.http.impl.client.CloseableHttpClient {

    private String uri;
    private Header[] headers;

    @Override
    public HttpParams getParams() {
      return null;
    }

    @Override
    public ClientConnectionManager getConnectionManager() {
      return null;
    }

    @Override
    public void close() throws IOException {}

    @Override
    protected CloseableHttpResponse doExecute(
        HttpHost target, HttpRequest request, HttpContext context)
        throws IOException, ClientProtocolException {
      uri = request.getRequestLine().getUri();
      headers = request.getAllHeaders();

      // mock it
      CloseableHttpResponse response = mock(CloseableHttpResponse.class);
      return response;
    }

    public String getUri() {
      return uri;
    }

    public Header[] getHeaders() {
      return headers;
    }

    public String getFirstHeaderValue(String name) {
      for (Header header : headers) {
        if (name.equals(header.getName())) return header.getValue();
      }
      return null;
    }
  }

  @Test
  public void testPurge() {
    MockCloseableHttpClientTest mockHttClient = new MockCloseableHttpClientTest();
    VarnishPurger purger = new VarnishPurger(mockHttClient, URI.create(API_BASEURL));
    purger.purge("occurrence/15");
    assertEquals(API_BASEURL + "occurrence/15", mockHttClient.getUri());

    purger.purge("/occurrence/15");
    assertEquals(API_BASEURL + "occurrence/15", mockHttClient.getUri());
  }

  @Test
  public void testBan() {
    MockCloseableHttpClientTest mockHttClient = new MockCloseableHttpClientTest();

    VarnishPurger purger = new VarnishPurger(mockHttClient, URI.create(API_BASEURL));
    purger.ban("directory/*");

    assertEquals("/v1/directory/*", mockHttClient.getFirstHeaderValue(HttpBan.BAN_HEADER));
    assertEquals(API_BASEURL, mockHttClient.getUri());

    // test no trailing slash
    purger = new VarnishPurger(mockHttClient, URI.create(StringUtils.removeEnd(API_BASEURL, "/")));
    purger.ban("/directory/*");
    assertEquals("/v1/directory/*", mockHttClient.getFirstHeaderValue(HttpBan.BAN_HEADER));
  }
}
