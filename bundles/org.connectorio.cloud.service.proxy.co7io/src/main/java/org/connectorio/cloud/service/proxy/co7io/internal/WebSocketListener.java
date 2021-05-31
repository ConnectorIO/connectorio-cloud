/*
 * Copyright (C) 2019-2021 ConnectorIO Sp. z o.o.
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
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.connectorio.cloud.service.proxy.co7io.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.WebSocket;
import java.net.http.WebSocket.Listener;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.connectorio.cloud.service.proxy.ProxyConnectionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebSocketListener implements Listener {

  private final Logger logger = LoggerFactory.getLogger(WebSocketListener.class);
  private final AtomicReference<ProxyConnectionState> state = new AtomicReference<>(ProxyConnectionState.DISCONNECTED);

  private final String forwardHost;
  private final int forwardPort;
  private final HttpClient httpClient;
  private final ConnectionStateListener listener;

  private ByteArrayOutputStream requestStream = new ByteArrayOutputStream(512);
  private ByteArrayOutputStream responseStream = new ByteArrayOutputStream(1024);

  private ObjectMapper mapper = new ObjectMapper();
  private WebSocket webSocket;

  public WebSocketListener(String forwardHost, int forwardPort, HttpClient httpClient, ConnectionStateListener listener) {
    this.forwardHost = forwardHost;
    this.forwardPort = forwardPort;
    this.httpClient = httpClient;
    this.listener = listener;
  }

  @Override
  public void onOpen(WebSocket webSocket) {
    logger.debug("Websocket connection established");
    this.webSocket = webSocket;
    setState(ProxyConnectionState.CONNECTED);

    Listener.super.onOpen(webSocket);
  }

  @Override
  public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
    logger.debug("Received text data {}, last {}", data, last);
    return Listener.super.onText(webSocket, data, last);
  }

  @Override
  public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
    logger.debug("Received binary data {}, last {}", data, last);
    boolean complete = false;
    try {
      byte[] buffer = new byte[data.capacity() - data.position()];
      data.get(buffer, data.position(), buffer.length);
      requestStream.writeBytes(buffer);

      if (last) {
        byte[] bufferedData = requestStream.toByteArray();
        if (bufferedData.length > 0) {
          Request jsonRequest = mapper.readerFor(Request.class).readValue(new GZIPInputStream(new ByteArrayInputStream(bufferedData)));

          URI requestUri = URI.create("http://" + forwardHost + ":" + forwardPort + jsonRequest.getAddress());
          HttpRequest.Builder builder = HttpRequest.newBuilder(requestUri)
              .method(jsonRequest.getMethod(), BodyPublishers.ofByteArray(jsonRequest.getPayload()));

          for (Entry<String, String> entry : jsonRequest.getHeaders().entrySet()) {
            if (entry.getKey().equalsIgnoreCase("Host") || entry.getKey().equalsIgnoreCase("Content-Length")
                || entry.getKey().equalsIgnoreCase("Connection") || entry.getKey().equalsIgnoreCase("Upgrade") ) {
              continue;
            }
            builder.header(entry.getKey(), entry.getValue());
          }

          logger.debug("Received request {}", requestUri);

          httpClient.sendAsync(builder.build(), BodyHandlers.ofByteArray()).whenComplete((response, error) -> {
            Map<String, String> responseHeaders = new LinkedHashMap<>();
            Response jsonResponse = new Response();
            jsonResponse.setId(jsonRequest.getId());
            jsonResponse.setHeaders(responseHeaders);
            jsonResponse.setStatus(response.statusCode());
            jsonResponse.setPayload(response.body());

            for (Entry<String, List<String>> entry : response.headers().map().entrySet()) {
              responseHeaders.put(entry.getKey(), entry.getValue().stream().reduce((l, r) -> l + ", "+ r).orElse(""));
            }

            try {
              ByteArrayOutputStream output = new ByteArrayOutputStream(1024);
              GZIPOutputStream gzipOutputStream = new GZIPOutputStream(output);
              mapper.writer().writeValue(gzipOutputStream, jsonResponse);
              gzipOutputStream.finish();

              if (logger.isTraceEnabled()) {
                logger.trace("Sending response {}", responseStream.toString());
              } else if (logger.isDebugEnabled()) {
                logger.debug("Sending response {}", response.statusCode());
              }

              webSocket.sendBinary(ByteBuffer.wrap(output.toByteArray()), true);
            } catch (IOException e) {
              e.printStackTrace();
            }
          });
        }
      }
    } catch (IOException e) {
      logger.error("Could not handle Websocket payload", e);
    } finally {
      if (last) {
        // reset buffer
        requestStream = new ByteArrayOutputStream(512);
        if (complete) {
          responseStream = new ByteArrayOutputStream(1024);
        }
      }
    }
    return Listener.super.onBinary(webSocket, data, last);
  }

  @Override
  public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
    logger.debug("{} ping", webSocket);
    return Listener.super.onPing(webSocket, message);
  }

  @Override
  public CompletionStage<?> onPong(WebSocket webSocket, ByteBuffer message) {
    logger.debug("{} pong", webSocket);
    return Listener.super.onPong(webSocket, message);
  }

  @Override
  public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
    logger.info("Closing connection, status {}, reason {}", statusCode, reason);

    setState(ProxyConnectionState.DISCONNECTED);
    return null;
  }

  @Override
  public void onError(WebSocket webSocket, Throwable error) {
    logger.warn("Error while handling Websocket conversation", error);
    if (error instanceof IOException) {
      if (!webSocket.isOutputClosed()) {
        webSocket.sendClose(1000, "Generic IO Error");
      } else {
        setState(ProxyConnectionState.DISCONNECTED);
      }
    }
    Listener.super.onError(webSocket, error);
  }

  public void send(TextEvent event) {
    if (webSocket == null) {
      logger.debug("Ignoring event {} broadcast, connection is not in place", event);
      return;
    }
    try {
      webSocket.sendText(mapper.writeValueAsString(event), true);
    } catch (JsonProcessingException e) {
      logger.warn("Failed to send state update", e);
    }
  }

  private void setState(ProxyConnectionState state) {
    this.state.set(state);

    if (state == ProxyConnectionState.CONNECTED) {
      listener.connected(webSocket);
    }
    if (state == ProxyConnectionState.DISCONNECTED) {
      listener.disconnected(webSocket);
    }
  }

}
