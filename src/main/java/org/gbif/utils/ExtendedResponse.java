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

import java.util.Locale;

import org.apache.http.Header;
import org.apache.http.HeaderIterator;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;

/**
 * An {@link org.apache.http.HttpResponse} wrapper exposing limited fields.
 */
@SuppressWarnings("unused")
public class ExtendedResponse {

  private String content;

  private final HttpResponse response;

  public ExtendedResponse(HttpResponse resp) {
    response = resp;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public HttpResponse getResponse() {
    return response;
  }

  public boolean containsHeader(String name) {
    return response.containsHeader(name);
  }

  public Header[] getAllHeaders() {
    return response.getAllHeaders();
  }

  public Header getFirstHeader(String name) {
    return response.getFirstHeader(name);
  }

  public Header[] getHeaders(String name) {
    return response.getHeaders(name);
  }

  public Header getLastHeader(String name) {
    return response.getLastHeader(name);
  }

  public Locale getLocale() {
    return response.getLocale();
  }

  public ProtocolVersion getProtocolVersion() {
    return response.getProtocolVersion();
  }

  public int getStatusCode() {
    return response.getStatusLine().getStatusCode();
  }

  public StatusLine getStatusLine() {
    return response.getStatusLine();
  }

  public HeaderIterator headerIterator() {
    return response.headerIterator();
  }

  public HeaderIterator headerIterator(String name) {
    return response.headerIterator(name);
  }
}
