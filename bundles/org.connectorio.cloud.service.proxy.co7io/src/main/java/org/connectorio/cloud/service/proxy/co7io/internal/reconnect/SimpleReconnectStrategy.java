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
package org.connectorio.cloud.service.proxy.co7io.internal.reconnect;

import java.net.http.WebSocket;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.connectorio.cloud.service.proxy.co7io.internal.ConnectionStateListener;
import org.connectorio.cloud.service.proxy.co7io.internal.ConnectorioProxyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimpleReconnectStrategy implements ConnectionStateListener, ReconnectStrategy {

  private final static ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor((runnable) -> new Thread(runnable, "websocket-econnect"));

  private final Logger logger = LoggerFactory.getLogger(SimpleReconnectStrategy.class);
  private final AtomicInteger attempts = new AtomicInteger(0);
  private final ConnectorioProxyService connection;
  private ScheduledFuture<?> reconnectFuture;

  public SimpleReconnectStrategy(ConnectorioProxyService connection) {
    this.connection = connection;
  }

  @Override
  public void connected(WebSocket webSocket) {
    logger.debug("WebSocket client connected, disabling reconnect logic");
    attempts.set(0);
    stop();
  }

  @Override
  public void disconnected(WebSocket webSocket) {
    logger.debug("WebSocket client disconnected, activating reconnect logic");
    start();
  }

  @Override
  public void start() {
    if (attempts.get() == -1) {
      // shutdown
      return;
    }

    reconnectFuture = scheduler.scheduleAtFixedRate(new Runnable() {
      @Override
      public void run() {
        try {
          logger.debug("Connecting to websocket server. Attempt {}", attempts.incrementAndGet());
          connection.connect();
        } catch (Exception e) {
          logger.debug("Reconnection attempt {} failure", attempts.get(), e);
        }
      }
    }, 1000, 60000, TimeUnit.MILLISECONDS);
  }

  @Override
  public void stop() {
    if (reconnectFuture != null) {
      reconnectFuture.cancel(true);
      reconnectFuture = null;
    }
  }

  @Override
  public void shutdown() {
    attempts.set(-1);
    stop();
  }
}
