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

import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.AuthState;
import org.apache.http.auth.Credentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("unused")
public class PreemptiveAuthenticationInterceptor implements HttpRequestInterceptor {

  private static final Logger LOG =
      LoggerFactory.getLogger(PreemptiveAuthenticationInterceptor.class);

  // registry currently requires Preemptive authentication
  // add preemptive authentication via this interceptor

  @Override
  public void process(final HttpRequest request, final HttpContext context) {
    LOG.debug(request.getRequestLine().toString());

    AuthState authState = (AuthState) context.getAttribute(HttpClientContext.TARGET_AUTH_STATE);
    CredentialsProvider credsProvider =
        (CredentialsProvider) context.getAttribute(HttpClientContext.CREDS_PROVIDER);
    HttpHost targetHost = (HttpHost) context.getAttribute(HttpCoreContext.HTTP_TARGET_HOST);

    // If not auth scheme has been initialized yet
    if (authState.getAuthScheme() == null && credsProvider != null) {
      AuthScope authScope = new AuthScope(targetHost.getHostName(), targetHost.getPort());
      // Obtain credentials matching the target host
      Credentials creds = credsProvider.getCredentials(authScope);
      // If found, generate BasicScheme preemptively
      if (creds != null) {
        LOG.debug("Authentication used for scope " + authScope.getHost());
        authState.update(new BasicScheme(), creds);
      }
    }
  }
}
