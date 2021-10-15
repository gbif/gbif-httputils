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

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.net.ssl.SSLContext;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.ProtocolException;
import org.apache.http.StatusLine;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.RedirectStrategy;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HttpContext;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A utility class for HTTP related functions.
 */
@SuppressWarnings("unused")
public final class HttpUtil {

  private static final Logger LOG = LoggerFactory.getLogger(HttpUtil.class);

  static final String GBIF_NAME =
      "null".equals(HttpUtil.class.getPackage().getName())
          ? "org.gbif.utils"
          : HttpUtil.class.getPackage().getName().replace("gbif", "GBIF");
  static final String GBIF_VERSION =
      HttpUtil.class.getPackage().getImplementationVersion() == null
          ? "development"
          : HttpUtil.class.getPackage().getImplementationVersion();
  static final String JAVA_VERSION = System.getProperty("java.version");

  private HttpUtil() {}

  public static UsernamePasswordCredentials credentials(String username, String password) {
    return new UsernamePasswordCredentials(
        StringUtils.trimToEmpty(username), StringUtils.trimToEmpty(password));
  }

  /**
   * Creates a URL form encoded HTTP entity suitable for POST requests with a single given parameter
   * encoded in UTF-8.
   *
   * @param kvp the parameter map to encode
   */
  public static HttpEntity map2Entity(Map<String, String> kvp) {
    List<NameValuePair> formparams = new ArrayList<>(kvp.size());
    for (Map.Entry<String, String> entry : kvp.entrySet()) {
      formparams.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
    }
    return new UrlEncodedFormEntity(formparams, StandardCharsets.UTF_8);
  }

  /**
   * Creates a URL form encoded HTTP entity suitable for POST requests with a single given parameter
   * encoded in UTF-8.
   *
   * @param key the parameter name
   * @param data the value to encode
   */
  public static HttpEntity map2Entity(String key, String data) {
    List<NameValuePair> formparams = new ArrayList<>(1);
    formparams.add(new BasicNameValuePair(key, data));
    return new UrlEncodedFormEntity(formparams, StandardCharsets.UTF_8);
  }

  /**
   * This creates a new threadsafe, singlethreaded HTTP client with support for HTTP and HTTPS.
   * <p>
   * Default HTTP client values are partially overridden to use UTF-8 as the default charset and an explicit timeout
   * is required for configuration.
   *
   * @param timeout in milliseconds
   */
  public static HttpClient newSinglethreadedClient(int timeout) {
    return newClientInternal(timeout, null, null, null, null, false);
  }

  /**
   * This creates a new threadsafe, multithreaded HTTP client with support for HTTP and HTTPS and default values.
   */
  public static HttpClient newDefaultMultithreadedClient() {
    return newMultithreadedClient(60_000, 250, 5);
  }

  /**
   * This creates a new threadsafe, multithreaded HTTP client with support for HTTP and HTTPS.
   * <p>
   * Default HTTP client values are partially overridden to use UTF-8 as the default charset and an explicit timeout
   * is required for configuration.
   *
   * @param timeout in milliseconds
   * @param maxConnections maximum allowed connections in total
   * @param maxPerRoute maximum allowed connections per route
   */
  public static HttpClient newMultithreadedClient(
      int timeout, int maxConnections, int maxPerRoute) {
    return newClientInternal(timeout, maxConnections, maxPerRoute, null, null, true);
  }

  /**
   * This creates a new threadsafe, multithreaded HTTP client with support for HTTP and HTTPS.
   * It also allows to use a custom user agent and first interceptor.
   * <p>
   * Default HTTP client values are partially overridden to use UTF-8 as the default charset and an explicit timeout
   * is required for configuration.
   *
   * @param timeout in milliseconds
   * @param maxConnections maximum allowed connections in total
   * @param maxPerRoute maximum allowed connections per route
   */
  public static HttpClient newMultithreadedClient(
      int timeout,
      int maxConnections,
      int maxPerRoute,
      String userAgent,
      HttpRequestInterceptor firstInterceptor) {
    return newClientInternal(
        timeout, maxConnections, maxPerRoute, userAgent, firstInterceptor, true);
  }

  /**
   * Internal method for client creation.
   *
   * @see HttpUtil#newSinglethreadedClient
   * @see HttpUtil#newMultithreadedClient(int, int, int)
   * @see HttpUtil#newMultithreadedClient(int, int, int, String, HttpRequestInterceptor)
   */
  @SuppressWarnings({"rawtypes", "unchecked"})
  private static HttpClient newClientInternal(
      Integer timeout,
      Integer maxConnections,
      Integer maxPerRoute,
      String userAgent,
      HttpRequestInterceptor firstInterceptor,
      boolean multithreaded) {
    ConnectionConfig connectionConfig =
        ConnectionConfig.custom().setCharset(StandardCharsets.UTF_8).build();

    RequestConfig defaultRequestConfig =
        RequestConfig.custom()
            .setSocketTimeout(timeout)
            .setConnectTimeout(timeout)
            .setConnectionRequestTimeout(timeout)
            .build();

    SSLContext sslcontext = SSLContexts.createSystemDefault();

    Registry registry =
        RegistryBuilder.create()
            .register("http", PlainConnectionSocketFactory.INSTANCE)
            .register("https", new SSLConnectionSocketFactory(sslcontext))
            .build();

    PoolingHttpClientConnectionManager connectionManager =
        new PoolingHttpClientConnectionManager(registry);
    connectionManager.setDefaultConnectionConfig(connectionConfig);
    Optional.ofNullable(maxConnections).ifPresent(connectionManager::setMaxTotal);
    Optional.ofNullable(maxPerRoute).ifPresent(connectionManager::setDefaultMaxPerRoute);

    RedirectStrategy redirectStrategy =
        new DefaultRedirectStrategy() {
          @Override
          public boolean isRedirected(
              HttpRequest request, HttpResponse response, HttpContext context)
              throws ProtocolException {
            return super.isRedirected(request, response, context)
                || (response.getStatusLine().getStatusCode() == 308
                    && isRedirectable(request.getRequestLine().getMethod()));
          }
        };

    final String resultUserAgent;
    if (StringUtils.isNotEmpty(userAgent)) {
      resultUserAgent = userAgent;
    } else if (multithreaded) {
      resultUserAgent =
          String.format(
              "%s/%s (Java/%s; M-%d-%d-%d; +https://www.gbif.org/)",
              GBIF_NAME, GBIF_VERSION, JAVA_VERSION, timeout, maxConnections, maxPerRoute);
    } else {
      resultUserAgent =
          String.format(
              "%s/%s (Java/%s; S-%d; +https://www.gbif.org/)",
              GBIF_NAME, GBIF_VERSION, JAVA_VERSION, timeout);
    }

    HttpClientBuilder builder = HttpClientBuilder.create();

    if (firstInterceptor != null) {
      builder.addInterceptorFirst(firstInterceptor);
    }

    CloseableHttpClient apacheHttpClient =
        builder
            // Retain compressed content, e.g. a tar.gz archive we download
            .disableContentCompression()
            .setRedirectStrategy(redirectStrategy)
            .setDefaultRequestConfig(defaultRequestConfig)
            .setConnectionManager(connectionManager)
            .setUserAgent(resultUserAgent)
            .build();

    return new HttpClient(apacheHttpClient, defaultRequestConfig);
  }

  public static String responseAsString(HttpResponse response) {
    String content = null;
    HttpEntity entity = response.getEntity();
    if (entity != null) {
      try {
        content = EntityUtils.toString(entity);
        EntityUtils.consume(entity);
      } catch (org.apache.http.ParseException e) {
        LOG.error("ParseException consuming HTTP response into string", e);
      } catch (IOException e) {
        LOG.error("IOException consuming HTTP response into string", e);
      }
    }
    return content;
  }

  /**
   * Creates an HTTP entity suitable for POSTs that encodes a single string in UTF-8.
   *
   * @param data to encode
   */
  public static HttpEntity stringEntity(String data) {
    return new StringEntity(data, StandardCharsets.UTF_8);
  }

  /**
   * Whether a request has succeeded,  e.g. 200 response code
   */
  public static boolean success(ExtendedResponse resp) {
    return resp != null && success(resp.getStatusLine());
  }

  public static boolean success(StatusLine status) {
    return status != null && status.getStatusCode() >= 200 && status.getStatusCode() < 300;
  }

  /**
   * Extracts the HttpHost from the httpUrl parameter.
   */
  public static HttpHost getHost(String httpUrl) throws MalformedURLException {
    URL url = new URL(httpUrl);
    HttpHost host;
    if (hasPort(httpUrl)) {
      host = new HttpHost(url.getHost(), url.getPort());
    } else {
      host = new HttpHost(url.getHost());
    }
    return host;
  }

  /**
   * Validates if the url contains a Port section "path:port".
   */
  public static boolean hasPort(String url) {
    return StringUtils.isNotBlank(url) && url.split(":").length > 2;
  }
}
