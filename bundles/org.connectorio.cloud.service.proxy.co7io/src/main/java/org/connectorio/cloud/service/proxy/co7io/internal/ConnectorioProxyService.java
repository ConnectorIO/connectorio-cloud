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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.WebSocket;
import java.net.http.WebSocket.Builder;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import org.connectorio.cloud.device.auth.DeviceAuthentication;
import org.connectorio.cloud.service.CloudService;
import org.connectorio.cloud.service.CloudServiceState;
import org.connectorio.cloud.service.CloudServiceStates;
import org.connectorio.cloud.service.CloudServiceType;
import org.connectorio.cloud.service.proxy.CloudProxyConnection;
import org.connectorio.cloud.service.proxy.ProxyConnectionState;
import org.connectorio.cloud.service.proxy.co7io.internal.reconnect.ReconnectStrategy;
import org.connectorio.cloud.service.proxy.co7io.internal.reconnect.SimpleReconnectStrategy;
import org.connectorio.cloud.service.standard.NamedCloudServiceType;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(immediate = true, service = {CloudService.class, CloudProxyConnection.class})
public class ConnectorioProxyService implements CloudProxyConnection {

  private final static String DEFAULT_HOST = "proxy.connectorio.cloud";
  public static final int WEBSOCKET_MESSAGE_LIMIT = 65536;

  private final Logger logger = LoggerFactory.getLogger(ConnectorioProxyService.class);
  private final AtomicReference<ProxyConnectionState> state = new AtomicReference<>(ProxyConnectionState.DISCONNECTED);

  private final String host;
  private final int port;
  private final boolean secure;
  private final DeviceAuthentication authentication;
  private final ReconnectStrategy reconnectStrategy;

  private WebSocket connection;

  @Activate
  public ConnectorioProxyService(@Reference DeviceAuthentication authentication, @Reference ConfigurationAdmin configurationAdmin) throws IOException {
    this.authentication = authentication;
    Configuration configuration = configurationAdmin.getConfiguration("org.connectorio.cloud.service.proxy.co7io");
    String serverHost = resolveOption(configuration, "host", Object::toString, () -> DEFAULT_HOST);
    secure = resolveOption(configuration, "secure", (v -> Boolean.parseBoolean(v.toString())), () -> Boolean.TRUE);
    port = resolveOption(configuration, "port", (v -> Integer.parseInt(v.toString())), () -> secure ? 443 : 80);

    // hm.. can it become stale once device is re-authenticated to different organization?
    //String clientId = authentication.getOrganizationId() + "." + authentication.getDeviceId();

    host = authentication.getOrganizationId() + "-" + authentication.getDeviceId() + "." + serverHost;

    reconnectStrategy = new SimpleReconnectStrategy(this);
    reconnectStrategy.start();
  }

  @Override
  public CloudServiceType getServiceType() {
    return new NamedCloudServiceType("proxy");
  }

  @Override
  public CloudServiceState getState() {
    if (getClient() == null) {
      return CloudServiceStates.GONE;
    }

    return state.get();
  }

  public void connect() {
    Builder webSocketBuilder = HttpClient.newHttpClient().newWebSocketBuilder()
        .header("Authorization", authentication.getAccessToken());
    URI serverUri = URI.create((secure ? "wss://" : "ws://") + host + ":" + port + "/connect");
    logger.info("Launching WebSocket connection to {}", serverUri);
    webSocketBuilder.buildAsync(serverUri, new WebSocketListener(HttpClient.newBuilder().version(Version.HTTP_1_1).build(), reconnectStrategy))
      .whenComplete((response, error) -> {
        if (error != null) {
          logger.error("Could not open web socket connection", error);
          return;
        }
        connection = response;
      });
  }

  private <T> T resolveOption(Configuration configuration, String key, Function<Object, T> mapping, Supplier<T> fallback) {
    return Optional.ofNullable(configuration)
      .map(Configuration::getProperties)
      .map(prop -> prop.get(key))
      .map(mapping)
      .orElseGet(fallback);
  }

  @Deactivate
  public void deactivate() {
    reconnectStrategy.shutdown();
    connection.sendClose(WebSocket.NORMAL_CLOSURE, "connection deactivation")
      .join(); // block until completion of disconnect request
  }

  @Override
  public DeviceAuthentication getDeviceAuthentication() {
    return authentication;
  }

  @Override
  public WebSocket getClient() {
    return connection;
  }

}