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

import java.net.URI;

import org.apache.http.annotation.Contract;
import org.apache.http.client.methods.HttpRequestBase;

/**
 * The HTTP BAN method is used by varnish to flush its cache via http.
 */
@Contract
public class HttpBan extends HttpRequestBase {

  public static final String METHOD_NAME = "BAN";
  public static final String BAN_HEADER = "x-ban-url";

  public HttpBan(URI uri, String banRegex) {
    super();
    setURI(uri);
    setHeader(BAN_HEADER, banRegex);
  }

  @Override
  public String getMethod() {
    return METHOD_NAME;
  }
}
