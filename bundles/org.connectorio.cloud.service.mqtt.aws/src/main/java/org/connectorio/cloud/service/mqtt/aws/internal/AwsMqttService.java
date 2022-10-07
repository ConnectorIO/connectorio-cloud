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
package org.connectorio.cloud.service.mqtt.aws.internal;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.net.ssl.TrustManager;
import org.connectorio.cloud.service.CloudService;
import org.connectorio.cloud.service.CloudServiceState;
import org.connectorio.cloud.service.CloudServiceStates;
import org.connectorio.cloud.service.CloudServiceType;
import org.connectorio.cloud.service.mqtt.MqttConnectionState;
import org.connectorio.cloud.service.mqtt.aws.internal.client.ClientCertificateMqttClientWrapper;
import org.connectorio.cloud.service.mqtt.aws.internal.client.ssl.StringKeyManagerFactory;
import org.connectorio.cloud.service.standard.NamedCloudServiceType;
import org.openhab.core.io.net.http.ExtensibleTrustManager;
import org.openhab.core.io.transport.mqtt.MqttBrokerConnection;
import org.openhab.core.io.transport.mqtt.MqttConnectionObserver;
import org.openhab.core.io.transport.mqtt.internal.client.MqttAsyncClientWrapper;
import org.openhab.core.io.transport.mqtt.ssl.CustomTrustManagerFactory;
import org.osgi.framework.Constants;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(immediate = true, configurationPid = AwsMqttService.SERVICE_PID,
  service = {CloudService.class, AwsPublisher.class},
  configurationPolicy = ConfigurationPolicy.REQUIRE,
  property = {
    Constants.SERVICE_PID + "=" + AwsMqttService.SERVICE_PID,
    "service.config.label=AWS IoT MQTT",
    "service.config.category=ConnectorIO Cloud",
    "service.config.description.uri=connectorio-cloud:mqtt-aws",
}
)
public class AwsMqttService implements CloudService, AwsPublisher, MqttConnectionObserver {

  public static final String SERVICE_PID = "org.connectorio.cloud.service.mqtt.aws";

  private final Logger logger = LoggerFactory.getLogger(AwsMqttService.class);
  private final String host;
  private final int port;

  private final MqttBrokerConnection connection;
  private final File certificateFile;
  private final File privateKeyFile;
  private final String clientId;
  private final ExtensibleTrustManager trustManager;
  private final String topic;
  private final int qos;
  private final boolean retain;

  // payload settings
  private final String dynamicTopic;
  private final boolean attachTags;
  private final boolean attachTimestamp;
  private final boolean stateChange;

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
  public AwsMqttService(@Reference ConfigurationAdmin configurationAdmin, @Reference ExtensibleTrustManager trustManager) throws IOException {
    Configuration configuration = configurationAdmin.getConfiguration("org.connectorio.cloud.service.mqtt.aws");
    host = resolveOption(configuration, "host", Object::toString, () -> null);
    port = resolveOption(configuration, "port", (v -> Integer.parseInt(v.toString())), () -> 8883);
    clientId = resolveOption(configuration, "clientId", (Object::toString), () -> "sdk/java");
    certificateFile = resolveOption(configuration, "certificateFile", (v -> new File("" + v)), () -> null);
    privateKeyFile = resolveOption(configuration, "privateKeyFile", (v -> new File("" + v)), () -> null);
    // topic
    topic = resolveOption(configuration, "topic", (Object::toString), () -> "sdk/test/java");
    dynamicTopic = resolveOption(configuration, "dynamicTopic", (Object::toString), () -> "");
    qos = resolveOption(configuration, "qos", (v -> Integer.parseInt(v.toString())), () -> 0);
    retain = resolveOption(configuration, "retain", (v -> Boolean.parseBoolean(v.toString())), () -> false);
    // payload
    attachTags = resolveOption(configuration, "attachTags", (v -> Boolean.parseBoolean(v.toString())), () -> true);
    attachTimestamp = resolveOption(configuration, "attachTimestamp", (v -> Boolean.parseBoolean(v.toString())), () -> true);
    stateChange = resolveOption(configuration, "stateChange", (v -> Boolean.parseBoolean(v.toString())), () -> false);

    // security
    this.trustManager = trustManager;

    if (host == null) {
      throw new IOException("Missing hostname");
    }
    if (certificateFile == null) {
      throw new IOException("Missing certificate");
    }
    if (privateKeyFile == null) {
      throw new IOException("Missing private key");
    }
    if (clientId == null) {
      throw new IOException("Missing clientId");
    }

    connection = new MqttBrokerConnection(host, port, true, clientId) {
      @Override
      protected MqttAsyncClientWrapper createClient() {
        CustomTrustManagerFactory factory = new CustomTrustManagerFactory(
          new TrustManager[] { trustManager }
        );
        try {
          StringKeyManagerFactory keyManagerFactory = new StringKeyManagerFactory(new char[0], read(privateKeyFile), read(certificateFile));
          return new ClientCertificateMqttClientWrapper(
            host, port, clientId, protocol, secure, connectionCallback, factory, keyManagerFactory.createKeyManager()
          );
        } catch (Exception e) {
          throw new RuntimeException("Could not initialize mqtt connection to " + host, e);
        }
      }
    };
    connection.addConnectionObserver(this);
    connection.start();
  }

  private File file(String path) {
    if (path != null) {
      return new File(path);
    }
    return null;
  }

  private String read(File file) throws IOException {
    return new String(Files.readAllBytes(file.toPath()));
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

  public MqttBrokerConnection getClient() {
    return connection;
  }

  @Override
  public void publish(String item, String messageStr) {
    if (getClient().connectionState() == org.openhab.core.io.transport.mqtt.MqttConnectionState.CONNECTED) {
      getClient().publish(determineTopic(item), messageStr.getBytes(), qos, retain);
    } else {
      logger.debug("Could not publish message {}, connection is not in place", item);
    }
  }

  private String determineTopic(String item) {
    return topic + (dynamicTopic.replaceAll("\\$item", item));
  }

  @Override
  public void connectionStateChanged(org.openhab.core.io.transport.mqtt.MqttConnectionState state, Throwable error) {
    if (error != null) {
      logger.info("Disconnected from AWS broker due to error", error);
    }
  }

  @Override
  public boolean isDynamicTopic() {
    return dynamicTopic.contains("$item");
  }

  @Override
  public boolean isStateChange() {
    return stateChange;
  }

  @Override
  public boolean isAttachTimestamp() {
    return attachTimestamp;
  }

  @Override
  public boolean isAttachTags() {
    return attachTags;
  }
}