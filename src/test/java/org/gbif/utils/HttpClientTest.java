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
import org.junit.jupiter.api.Disabled;
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
   * If the file being tested against has been changed in the last 24h this test is expected to fail!
   */
  @Test
  public void testConditionalGet() throws IOException {
    HttpClient httpClient = HttpUtil.newDefaultMultithreadedClient();
    // We know for sure it has changed since this date
    Date last = DateUtils.parseDate("Wed, 03 Aug 2009 22:37:31 GMT");
    File tmp = File.createTempFile("vocab", ".xml");
    URL url = new URL("http://rs.gbif.org/vocabulary/gbif/rank.xml");
    assertTrue(httpClient.downloadIfChanged(url, last, tmp));

    // Verify that it does not download with a conditional get of 5 minutes ago, in case of clock
    // skew.
    Date nearlyNow = new Date(System.currentTimeMillis() - 1000 * 60 * 5);
    assertFalse(httpClient.downloadIfChanged(url, nearlyNow, tmp));
  }

  /**
   * Testing if the IPT DWCA file serving respects the is-modified-since.
   * Ignoring this test as its only a manual one to run to check the IPT!
   */
  @Test
  @Disabled("Manual test")
  public void testIptConditionalGet() throws IOException {
    HttpClient httpClient = HttpUtil.newDefaultMultithreadedClient();

    Date beforeChange = DateUtils.parseDate("Wed, 03 Aug 2009 22:37:31 GMT");
    Date afterChange = DateUtils.parseDate("Wed, 15 May 2019 14:47:25 GMT");

    File tmp = File.createTempFile("dwca", ".zip");
    URL url = new URL("https://cloud.gbif.org/bid/archive.do?r=nzcs_introduced_fauna_suriname");
    boolean downloaded = httpClient.downloadIfChanged(url, beforeChange, tmp);
    assertTrue(downloaded);

    downloaded = httpClient.downloadIfChanged(url, afterChange, tmp);
    assertFalse(downloaded);
  }

  @Test
  public void testMultithreadedCommonsLogDependency() throws Exception {
    HttpClient httpClient = HttpUtil.newMultithreadedClient(10_000, 10_000, 10);
    httpClient.get("http://rs.gbif.org/vocabulary/gbif/rank.xml");
    httpClient.get("https://rs.gbif.org/vocabulary/gbif/rank.xml");
  }
}
