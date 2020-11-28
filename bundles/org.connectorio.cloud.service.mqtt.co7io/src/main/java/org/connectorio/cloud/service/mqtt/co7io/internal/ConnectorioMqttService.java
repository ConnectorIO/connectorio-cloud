/*
 * Copyright (C) 2019-2020 ConnectorIO Sp. z o.o.
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
package org.connectorio.cloud.service.mqtt.co7io.internal;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;
import org.connectorio.cloud.device.auth.DeviceAuthentication;
import org.connectorio.cloud.service.CloudService;
import org.connectorio.cloud.service.CloudServiceState;
import org.connectorio.cloud.service.CloudServiceStates;
import org.connectorio.cloud.service.CloudServiceType;
import org.connectorio.cloud.service.mqtt.CloudMqttConnection;
import org.connectorio.cloud.service.mqtt.MqttConnectionState;
import org.connectorio.cloud.service.standard.NamedCloudServiceType;
import org.eclipse.smarthome.io.transport.mqtt.MqttBrokerConnection;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

@Component(service = {CloudService.class, CloudMqttConnection.class})
public class ConnectorioMqttService implements CloudMqttConnection {

  private final static String DEFAULT_HOST = "mqtt.connectorio.cloud";

  private final String host;
  private final int port;
  private final boolean secure;

  private final MqttBrokerConnection connection;
  private final DeviceAuthentication authentication;

  @Override
  public CloudServiceType getServiceType() {
    return new NamedCloudServiceType("mqtt");
  }

  @Override
  public CloudServiceState getState() {
    if (getClient() == null) {
      return CloudServiceStates.GONE;
    }
    switch (getClient().connectionState()) {
      case CONNECTED:
        return MqttConnectionState.CONNECTED;
      case CONNECTING:
        return MqttConnectionState.CONNECTING;
      case DISCONNECTED:
        return MqttConnectionState.DISCONNECTED;
    }
    return CloudServiceStates.UNKNOWN;
  }

  @Activate
  public ConnectorioMqttService(@Reference DeviceAuthentication authentication, @Reference ConfigurationAdmin configurationAdmin) throws IOException {
    this.authentication = authentication;
    Configuration configuration = configurationAdmin.getConfiguration("org.connectorio.cloud.service.mqtt.co7io");
    host = resolveOption(configuration, "host", Object::toString, () -> DEFAULT_HOST);
    port = resolveOption(configuration, "port", (v -> Integer.parseInt(v.toString())), () -> 1883);
    secure = resolveOption(configuration, "secure", (v -> Boolean.parseBoolean(v.toString())), () -> Boolean.TRUE);

    connection = new MqttBrokerConnection(host, port, secure, "connectorio." + UUID.randomUUID()) {
      @Override
      public CompletableFuture<Boolean> start() {
        // inject credentials right before connection attempt is made
        setCredentials("connectorio", authentication.getAccessToken());
        return super.start();
      }
    };
    connection.start();
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
    connection.stop();
  }

  @Override
  public DeviceAuthentication getDeviceAuthentication() {
    return authentication;
  }

  @Override
  public MqttBrokerConnection getClient() {
    return connection;
  }

}