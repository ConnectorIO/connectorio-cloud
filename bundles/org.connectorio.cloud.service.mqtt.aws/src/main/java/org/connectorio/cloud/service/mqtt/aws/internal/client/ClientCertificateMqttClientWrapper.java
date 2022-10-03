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
package org.connectorio.cloud.service.mqtt.aws.internal.client;

import com.hivemq.client.mqtt.MqttClientState;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client;
import com.hivemq.client.mqtt.mqtt3.Mqtt3ClientBuilder;
import com.hivemq.client.mqtt.mqtt3.message.connect.Mqtt3Connect;
import com.hivemq.client.mqtt.mqtt3.message.connect.Mqtt3ConnectBuilder;
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish;
import com.hivemq.client.mqtt.mqtt3.message.subscribe.Mqtt3Subscribe;
import com.hivemq.client.mqtt.mqtt3.message.unsubscribe.Mqtt3Unsubscribe;
import java.util.concurrent.CompletableFuture;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import org.openhab.core.io.transport.mqtt.MqttBrokerConnection.ConnectionCallback;
import org.openhab.core.io.transport.mqtt.MqttBrokerConnection.Protocol;
import org.openhab.core.io.transport.mqtt.MqttWillAndTestament;
import org.openhab.core.io.transport.mqtt.internal.Subscription;
import org.openhab.core.io.transport.mqtt.internal.client.MqttAsyncClientWrapper;

public class ClientCertificateMqttClientWrapper extends MqttAsyncClientWrapper {

  private final Mqtt3AsyncClient client;

  public ClientCertificateMqttClientWrapper(String host, int port, String clientId, Protocol protocol, boolean secure,
    ConnectionCallback connectionCallback, TrustManagerFactory trustManagerFactory, KeyManagerFactory keyManagerFactory) {

    Mqtt3ClientBuilder clientBuilder = Mqtt3Client.builder().serverHost(host)
      .serverPort(port)
      .identifier(clientId)
      .addConnectedListener(connectionCallback)
      .addDisconnectedListener(connectionCallback);

    if (protocol == Protocol.WEBSOCKETS) {
      clientBuilder.webSocketWithDefaultConfig();
    }
    if (secure) {
      clientBuilder.sslWithDefaultConfig().sslConfig()
        .trustManagerFactory(trustManagerFactory)
        .keyManagerFactory(keyManagerFactory)
        .applySslConfig();
    }

    client = clientBuilder.buildAsync();
  }

  @Override
  public MqttClientState getState() {
    return client.getState();
  }

  @Override
  public CompletableFuture<?> subscribe(String topic, int qos, Subscription subscription) {
    Mqtt3Subscribe subscribeMessage = Mqtt3Subscribe.builder().topicFilter(topic)
      .qos(getMqttQosFromInt(qos))
      .build();
    return client.subscribe(subscribeMessage, subscription::messageArrived);
  }

  @Override
  public CompletableFuture<?> unsubscribe(String topic) {
    Mqtt3Unsubscribe unsubscribeMessage = Mqtt3Unsubscribe.builder().topicFilter(topic)
      .build();
    return client.unsubscribe(unsubscribeMessage);
  }

  @Override
  public CompletableFuture<Mqtt3Publish> publish(String topic, byte[] payload, boolean retain, int qos) {
    Mqtt3Publish publishMessage = Mqtt3Publish.builder().topic(topic)
      .qos(getMqttQosFromInt(qos))
      .payload(payload).retain(retain)
      .build();
    return client.publish(publishMessage);
  }

  @Override
  public CompletableFuture<?> connect(MqttWillAndTestament lwt, int keepAliveInterval,
      String username, String password) {
    Mqtt3ConnectBuilder connectMessageBuilder = Mqtt3Connect.builder().keepAlive(keepAliveInterval);
    if (lwt != null) {
      Mqtt3Publish willPublish = Mqtt3Publish.builder().topic(lwt.getTopic())
        .payload(lwt.getPayload())
        .retain(lwt.isRetain())
        .qos(getMqttQosFromInt(lwt.getQos()))
        .build();
      connectMessageBuilder.willPublish(willPublish);
    }

    if (username != null && !username.isBlank() && password != null && !password.isBlank()) {
      connectMessageBuilder.simpleAuth().username(username).password(password.getBytes()).applySimpleAuth();
    }

    return client.connect(connectMessageBuilder.build());
  }

  @Override
  public CompletableFuture<Void> disconnect() {
    return client.disconnect();
  }
}
