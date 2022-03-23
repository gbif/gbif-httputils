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
package org.gbif.utils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Date;

import org.apache.http.StatusLine;
import org.apache.http.client.utils.DateUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HttpClientTest {

  @Test
  public void testClientRedirect() throws IOException {
    HttpClient httpClient = HttpUtil.newSinglethreadedClient(10_000);
    File tmp = File.createTempFile("httputils", "test");
    tmp.deleteOnExit();
    // a redirect to http://rs.gbif.org/extension/gbif/1.0/distribution.xml
    StatusLine status = httpClient.download("http://rs.gbif.org/terms/1.0/Distribution", tmp);
    assertTrue(HttpUtil.success(status));

    // assert false on 404s
    status = httpClient.download("https://rs.gbif.org/gbif-httputils-unit-test-404", tmp);
    assertFalse(HttpUtil.success(status));
    assertEquals(404, status.getStatusCode());

    // HTTP 307
    status = httpClient.download("https://httpstat.us/307", tmp);
    assertTrue(HttpUtil.success(status));

    // HTTP 308
    status = httpClient.download("https://httpstat.us/308", tmp);
    assertTrue(HttpUtil.success(status));
  }

  /**
   * Tests a condition get against an apache http server within GBIF.
   */
  @Test
  public void testConditionalGet() throws IOException {
    HttpClient httpClient = HttpUtil.newDefaultMultithreadedClient();
    // We know for sure it has changed since this date
    Date last = DateUtils.parseDate("Wed, 03 Aug 2009 22:37:31 GMT");
    File tmp = File.createTempFile("vocab", ".xml");
    URL url = new URL("https://rs.gbif.org/vocabulary/gbif/rank.xml");
    StatusLine status = httpClient.downloadIfModifiedSince(url, last, tmp);
    assertEquals(304, status.getStatusCode());
  }

  /**
   * Testing if the older (≤2.5.7) IPT DWCA file serving respects is-modified-since.
   */
  @Test
  public void testIptConditionalGet() throws IOException {
    HttpClient httpClient = HttpUtil.newDefaultMultithreadedClient();

    Date beforeChange = DateUtils.parseDate("Wed, 03 Aug 2009 22:37:31 GMT");
    Date afterChange = DateUtils.parseDate("Tue, 01 Feb 2022 17:43:12 GMT");

    File tmp = File.createTempFile("dwca", ".zip");
    URL url = new URL("http://ipt.iobis.org/obis-deepsea/archive.do?r=ccz_uk1_cnidaria");
    boolean downloaded = httpClient.downloadIfChanged(url, beforeChange, tmp);
    assertTrue(downloaded);

    downloaded = httpClient.downloadIfChanged(url, afterChange, tmp);
    assertFalse(downloaded);
  }

  /**
   * Testing strict conditional get: downloads the file if the timestamp is different, even if it's older.
   */
  @Test
  public void testStrictConditionalGet() throws IOException {
    // IPT version 2.5.8 or newer, which supports Last-Modified nicely.
    // (Although forward proxies like Apache HTTPD may still block it.)
    testStrictConditionalGet(
        new URL("http://ipt.gbif-uat.org:8080/ipt/archive.do?r=baa_prueba_ipt&v=1.13"),
        DateUtils.parseDate("Thu, 22 Dec 2016 08:50:43 GMT"),
        DateUtils.parseDate("Fri, 10 Mar 2017 12:26:33 GMT"));

    // Apache strips the Last-Modified header from the 304 response here.
    testStrictConditionalGet(
        new URL("https://ipt.gbif-uat.org/archive.do?r=baa_prueba_ipt&v=1.13"),
        DateUtils.parseDate("Thu, 22 Dec 2016 08:50:43 GMT"),
        DateUtils.parseDate("Fri, 10 Mar 2017 12:26:33 GMT"));

    // Plazi have lots and lots of datasets.
    testStrictConditionalGet(
        new URL("http://tb.plazi.org/GgServer/dwca/FF8AFFE74A2BFF95FF8D79079C26D70C.zip"),
        DateUtils.parseDate("Thu, 22 Dec 2016 08:50:43 GMT"),
        DateUtils.parseDate("Tue, 01 Feb 2022 17:43:12 GMT"));
  }

  private void testStrictConditionalGet(URL url, Date beforeChange, Date exactChange)
      throws IOException {
    HttpClient httpClient = HttpUtil.newDefaultMultithreadedClient();

    File tmp = File.createTempFile("dwca", ".zip");

    boolean downloaded = httpClient.downloadIfChanged(url, beforeChange, tmp);
    assertTrue(downloaded);

    // File should have timestamp equal to Last-Modified header
    assertEquals(exactChange.getTime(), tmp.lastModified());

    // Downloading based on known timestamp returns 304 Not Modified
    downloaded = httpClient.downloadIfChanged(url, exactChange, tmp);
    assertFalse(downloaded);
    // Downloading based on the on-disk lastModified also returns 304 Not Modified
    assertEquals(exactChange.getTime(), tmp.lastModified());
    downloaded = httpClient.downloadIfChanged(url, tmp);
    assertFalse(downloaded);

    // Set file's timestamp to now, simulating an incorrect response (e.g. museum homepage instead
    // of the IPT).
    tmp.setLastModified(new Date().getTime());
    downloaded = httpClient.downloadIfChanged(url, tmp);
    assertTrue(downloaded);
    assertEquals(exactChange.getTime(), tmp.lastModified());
  }

  @Test
  public void testMultithreadedCommonsLogDependency() throws Exception {
    HttpClient httpClient = HttpUtil.newMultithreadedClient(10_000, 10_000, 10);
    httpClient.get("http://rs.gbif.org/vocabulary/gbif/rank.xml");
    httpClient.get("https://rs.gbif.org/vocabulary/gbif/rank.xml");
  }
}
