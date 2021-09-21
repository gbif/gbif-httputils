/*
 * Copyright 2021 Global Biodiversity Information Facility (GBIF)
 *
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

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.DateUtils;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * A wrapper class for Apache's CloseableHttpClient.
 * <p/>
 * This class itself is thread safe. If you require thread safety please make sure to use a thread safe HTTP client as
 * the underlying client. The ones created here in the static builder methods or via the default constructor are.
 */
@SuppressWarnings("unused")
public class HttpClient {

  private static final Logger LOG = LoggerFactory.getLogger(HttpClient.class);

  private final CloseableHttpClient client;

  public HttpClient() {
    this.client = HttpUtil.newMultithreadedClient(60_000, 250, 5);
  }

  public HttpClient(CloseableHttpClient client) {
    this.client = client;
  }

  public UsernamePasswordCredentials credentials(String username, String password) {
    return new UsernamePasswordCredentials(
        StringUtils.trimToEmpty(username), StringUtils.trimToEmpty(password));
  }

  private HttpContext buildContext(String uri, UsernamePasswordCredentials credentials)
      throws URISyntaxException {
    HttpContext authContext = new BasicHttpContext();
    if (credentials != null) {
      URI authUri = new URI(uri);
      AuthScope scope = new AuthScope(authUri.getHost(), AuthScope.ANY_PORT, AuthScope.ANY_REALM);

      CredentialsProvider credsProvider = new BasicCredentialsProvider();
      credsProvider.setCredentials(scope, credentials);

      authContext.setAttribute(HttpClientContext.CREDS_PROVIDER, credsProvider);
    }

    return authContext;
  }

  /**
   * Executes a generic DELETE request.
   */
  public ExtendedResponse delete(String url, UsernamePasswordCredentials credentials)
      throws IOException, URISyntaxException {
    LOG.info("HTTP DELETE to {}", url);
    HttpDelete delete = new HttpDelete(url);
    HttpContext authContext = buildContext(url, credentials);
    CloseableHttpResponse response = client.execute(delete, authContext);
    ExtendedResponse result = new ExtendedResponse(response);
    try {
      HttpEntity entity = response.getEntity();
      if (entity != null) {
        result.setContent(EntityUtils.toString(entity));
        EntityUtils.consume(entity);
      }
    } finally {
      closeQuietly(response);
    }
    return result;
  }

  /**
   * Downloads something via HTTP GET to the provided file.
   */
  public StatusLine download(String url, File downloadTo) throws IOException {
    return download(new URL(url), downloadTo);
  }

  public StatusLine download(URI url, File downloadTo) throws IOException {
    return download(url.toURL(), downloadTo);
  }

  public String download(URL url) throws IOException {
    try {
      ExtendedResponse resp = get(url.toString());
      return resp.getContent();
    } catch (URISyntaxException e) {
      LOG.error("Invalid URL provided: {}", url, e);
    }
    return null;
  }

  public StatusLine download(URL url, File downloadTo) throws IOException {
    HttpGet get = new HttpGet(url.toString());

    // execute
    CloseableHttpResponse response = client.execute(get);
    final StatusLine status = response.getStatusLine();
    try {
      // write to file only when download succeeds
      if (HttpUtil.success(status)) {
        saveToFile(response, downloadTo);
        LOG.debug("Successfully downloaded {} to {}", url, downloadTo.getAbsolutePath());
      } else {
        LOG.error(
            "Downloading {} to {} failed!: {}",
            url,
            downloadTo.getAbsolutePath(),
            status.getStatusCode());
      }
    } finally {
      closeQuietly(response);
    }
    return status;
  }

  /**
   * @return body content if changed or null if unmodified since lastModified
   */
  public String downloadIfChanged(URL url, Date lastModified) throws IOException {
    Map<String, String> header = new HashMap<>(1);
    header.put(HttpHeaders.IF_MODIFIED_SINCE, DateUtils.formatDate(lastModified));

    try {
      ExtendedResponse resp = get(url.toString(), header, null);
      if (resp.getStatusCode() == HttpStatus.SC_NOT_MODIFIED) {
        LOG.debug("Content not modified since last request");
      }
      return resp.getContent();
    } catch (URISyntaxException e) {
      LOG.error("Invalid URL provided: {}", url, e);
    }
    return null;
  }

  /**
   * Downloads a URL to a file if its modified since the date given.
   * Updates the last modified file property to reflect the server's last-modified HTTP header.
   *
   * @param downloadTo file to download to
   * @return true if changed or false if unmodified since lastModified
   */
  public boolean downloadIfChanged(URL url, Date lastModified, File downloadTo) throws IOException {
    StatusLine status = downloadIfModifiedSince(url, lastModified, downloadTo);
    return HttpUtil.success(status);
  }

  /**
   * Downloads a URL to a local file using conditional GET, i.e. only downloading the file again if it has been changed
   * since the last download.
   */
  public boolean downloadIfChanged(URL url, File downloadTo) throws IOException {
    StatusLine status = downloadIfModifiedSince(url, downloadTo);
    return HttpUtil.success(status);
  }

  /**
   * Downloads a URL to a file if its modified since the date given.
   * Updates the last modified file property to reflect the server's last-modified HTTP header.
   *
   * @param downloadTo file to download to
   * @return true if changed or false if unmodified since lastModified
   */
  public StatusLine downloadIfModifiedSince(
      final URL url, final Date lastModified, final File downloadTo) throws IOException {

    HttpGet get = new HttpGet(url.toString());

    // prepare conditional GET request headers
    if (lastModified != null) {
      // DateFormatUtils is threadsafe
      get.addHeader(HttpHeaders.IF_MODIFIED_SINCE, DateUtils.formatDate(lastModified));
      LOG.debug("Conditional GET: {}", DateUtils.formatDate(lastModified));
    }

    // execute
    CloseableHttpResponse response = client.execute(get);
    final StatusLine status = response.getStatusLine();
    try {
      if (status.getStatusCode() == HttpStatus.SC_NOT_MODIFIED) {
        LOG.debug("Content not modified since last request");
      } else if (HttpUtil.success(status)) {
        // write to file only when download succeeds
        saveToFile(response, downloadTo);
        LOG.debug("Successfully downloaded {} to {}", url, downloadTo.getAbsolutePath());
      } else {
        LOG.error(
            "Downloading {} to {} failed!: {}",
            url,
            downloadTo.getAbsolutePath(),
            status.getStatusCode());
      }
    } finally {
      closeQuietly(response);
    }
    return status;
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  private void saveToFile(CloseableHttpResponse response, File downloadTo) throws IOException {
    HttpEntity entity = response.getEntity();
    if (entity != null) {
      Date serverModified = null;
      Header modHeader = response.getFirstHeader(HttpHeaders.LAST_MODIFIED);
      if (modHeader != null) {
        serverModified = DateUtils.parseDate(modHeader.getValue());
      }

      // copy stream to local file
      FileUtils.forceMkdir(downloadTo.getParentFile());
      if (downloadTo.isFile()) {
        downloadTo.delete();
      }
      try (OutputStream fos = new FileOutputStream(downloadTo, false)) {
        entity.writeTo(fos);
      }
      // update last modified of file with HTTP header date from server
      if (serverModified != null) {
        downloadTo.setLastModified(serverModified.getTime());
      }
    }
  }

  /**
   * Downloads a URL to a local file using conditional GET, i.e. only downloading the file again if it has been changed
   * on the filesystem since the last download.
   *
   * @param url URL to download
   * @param downloadTo file to download into and used to get the last modified date from
   */
  public StatusLine downloadIfModifiedSince(final URL url, final File downloadTo)
      throws IOException {
    Date lastModified = null;
    if (downloadTo.exists()) {
      lastModified = new Date(downloadTo.lastModified());
    }
    return downloadIfModifiedSince(url, lastModified, downloadTo);
  }

  /**
   * @throws IOException in case of a problem or the connection was aborted
   */
  public ExtendedResponse get(String url) throws IOException, URISyntaxException {
    return get(url, null, null);
  }

  public ExtendedResponse get(
      String url, Map<String, String> headers, UsernamePasswordCredentials credentials)
      throws IOException, URISyntaxException {
    HttpGet get = new HttpGet(url);
    // HTTP header
    if (headers != null) {
      for (Map.Entry<String, String> header : headers.entrySet()) {
        get.addHeader(
            StringUtils.trimToEmpty(header.getKey()), StringUtils.trimToEmpty(header.getValue()));
      }
    }
    // authentication
    HttpContext authContext = buildContext(url, credentials);
    CloseableHttpResponse response = client.execute(get, authContext);
    ExtendedResponse result = new ExtendedResponse(response);
    try {
      HttpEntity entity = response.getEntity();
      if (entity != null) {
        // Adding a default charset in case it is not found
        result.setContent(EntityUtils.toString(entity, StandardCharsets.UTF_8));
      }
    } finally {
      closeQuietly(response);
    }

    return result;
  }

  /**
   * Executes a generic POST request.
   */
  public ExtendedResponse post(String uri, HttpEntity requestEntity)
      throws IOException, URISyntaxException {
    return post(uri, null, null, requestEntity);
  }

  public ExtendedResponse post(
      final CloseableHttpClient client,
      String uri,
      UsernamePasswordCredentials credentials,
      HttpEntity requestEntity)
      throws IOException, URISyntaxException {
    return post(uri, null, credentials, requestEntity);
  }

  public ExtendedResponse post(
      String uri, Map<String, String> headers, UsernamePasswordCredentials credentials)
      throws IOException, URISyntaxException {
    return post(uri, headers, credentials, null);
  }

  public ExtendedResponse post(
      String uri,
      Map<String, String> headers,
      UsernamePasswordCredentials credentials,
      HttpEntity requestEntity)
      throws IOException, URISyntaxException {
    HttpPost post = new HttpPost(uri);
    if (headers != null) {
      for (Map.Entry<String, String> header : headers.entrySet()) {
        post.addHeader(
            StringUtils.trimToEmpty(header.getKey()), StringUtils.trimToEmpty(header.getValue()));
      }
    }
    if (requestEntity != null) {
      post.setEntity(requestEntity);
    }
    // authentication
    HttpContext authContext = buildContext(uri, credentials);
    HttpResponse response = client.execute(post, authContext);

    // response
    if (response != null) {
      ExtendedResponse result = new ExtendedResponse(response);
      HttpEntity respEntity = response.getEntity();
      if (respEntity != null) {
        result.setContent(EntityUtils.toString(respEntity));
        EntityUtils.consume(respEntity);
      }
      return result;
    }
    return null;
  }

  public boolean verifyHost(HttpHost host) {
    if (host != null) {
      CloseableHttpResponse resp = null;
      try {
        HttpHead head = new HttpHead(host.toURI());
        resp = client.execute(host, head);
        return true;

      } catch (Exception e) {
        LOG.debug("Exception thrown", e);

      } finally {
        closeQuietly(resp);
      }
    }
    return false;
  }

  private void closeQuietly(CloseableHttpResponse resp) {
    if (resp != null) {
      try {
        resp.close();
      } catch (IOException e) {
        LOG.debug("Failed to close HTTP response", e);
      }
    }
  }

  public CloseableHttpClient getClient() {
    return client;
  }
}
