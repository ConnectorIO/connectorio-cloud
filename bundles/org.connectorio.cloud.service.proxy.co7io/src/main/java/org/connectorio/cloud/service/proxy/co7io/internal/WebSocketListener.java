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
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
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
  // control access to websocket to avoid race conditions
  private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "websocket-reply-thread"));

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
    return dispatch(ws -> Listener.super.onText(ws, data, last));
  }

  @Override
  public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
    final AtomicReference<UUID> id = new AtomicReference<>(UUID.randomUUID());
    logger.debug("[{}] Received binary data {}, last {}", id, data, last);
    boolean complete = false;
    try {
      byte[] buffer = new byte[data.capacity() - data.position()];
      data.get(buffer, data.position(), buffer.length);
      requestStream.writeBytes(buffer);

      if (last) {
        byte[] bufferedData = requestStream.toByteArray();
        if (bufferedData.length > 0) {
          Request jsonRequest = mapper.readerFor(Request.class).readValue(new GZIPInputStream(new ByteArrayInputStream(bufferedData)));
          logger.debug("[{}] Changing request id to server context {}", id.get(), jsonRequest.getId());
          id.set(jsonRequest.getId());

          URI requestUri = URI.create("http://" + forwardHost + ":" + forwardPort + jsonRequest.getAddress().replace("#", "%23"));
          HttpRequest.Builder builder = HttpRequest.newBuilder(requestUri)
              .method(jsonRequest.getMethod(), BodyPublishers.ofByteArray(jsonRequest.getPayload()));

          for (Entry<String, String> entry : jsonRequest.getHeaders().entrySet()) {
            if (entry.getKey().equalsIgnoreCase("Host") || entry.getKey().equalsIgnoreCase("Content-Length")
                || entry.getKey().equalsIgnoreCase("Connection") || entry.getKey().equalsIgnoreCase("Upgrade") ) {
              continue;
            }
            builder.header(entry.getKey(), entry.getValue());
          }

          logger.debug("[{}] Dispatching request to client {}", id.get(), requestUri);
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
                logger.trace("[{}] Sending response {}", id.get(), responseStream.toString());
              } else if (logger.isDebugEnabled()) {
                logger.debug("[{}] Sending response {}", id.get(), response.statusCode());
              }

              dispatch(ws -> ws.sendBinary(ByteBuffer.wrap(output.toByteArray()), true).whenComplete((r, e) -> {
                responseStream = new ByteArrayOutputStream(1024);
                if (e != null) {
                  logger.error("[{}] Error while publishing http response to websocket connection", id.get(), e);
                } else {
                  logger.debug("[{}] Http response pushed over websocket connection", id.get());
                }
              }));
            } catch (IOException e) {
              logger.error("[{}] Error while handling request", id.get(), e);
            }
          }).whenComplete((r, e) -> {
            if (e != null) {
              logger.error("[{}] Finished http call with status {}", id.get(), r.statusCode(), e);
              return;
            }
            logger.debug("[{}] Finished http call with status {}", id.get(), r.statusCode());
          });
        }
      }
    } catch (IOException e) {
      logger.error("[{}] Could not handle Websocket payload", id.get(), e);
    } finally {
      if (last) {
        // reset buffers
        requestStream = new ByteArrayOutputStream(512);
      }
    }
    return dispatch(ws -> Listener.super.onBinary(ws, data, last));
  }

  @Override
  public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
    logger.debug("{} ping", webSocket);
    return dispatch(ws -> Listener.super.onPing(ws, message));
  }

  @Override
  public CompletionStage<?> onPong(WebSocket webSocket, ByteBuffer message) {
    logger.debug("{} pong", webSocket);
    return dispatch(ws -> Listener.super.onPong(ws, message));
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
        dispatch(ws -> ws.sendClose(1000, "Generic IO Error"));
      } else {
        setState(ProxyConnectionState.DISCONNECTED);
      }
    }
    call(ws -> Listener.super.onError(ws, error));
  }

  public void send(TextEvent event) {
    if (webSocket == null) {
      logger.debug("Ignoring event {} broadcast, connection is not in place", event);
      return;
    }
    call(ws -> {
      try {
       ws.sendText(mapper.writeValueAsString(event), true);
      } catch (JsonProcessingException e) {
        logger.warn("Failed to send state update", e);
      }
    });
  }

  private <X> CompletableFuture<X> dispatch(Function<WebSocket, X> call) {
    return CompletableFuture.supplyAsync(() -> call.apply(webSocket), executor);
  }

  private CompletableFuture<Void> call(Consumer<WebSocket> consumer) {
    return CompletableFuture.runAsync(() -> consumer.accept(webSocket), executor);
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
