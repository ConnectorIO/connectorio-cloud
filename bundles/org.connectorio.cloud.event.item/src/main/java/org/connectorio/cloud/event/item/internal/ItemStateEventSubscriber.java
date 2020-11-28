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
package org.connectorio.cloud.event.item.internal;

import org.connectorio.cloud.device.auth.DeviceAuthentication;
import org.connectorio.cloud.event.mapping.BindableDestination;
import org.connectorio.cloud.event.mapping.standard.BoundDestination;
import org.connectorio.cloud.event.mapping.standard.StandardMappingTable;
import org.connectorio.cloud.event.mapping.standard.StandardVariableDefinition;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.connectorio.cloud.service.mqtt.CloudMqttConnection;
import org.eclipse.smarthome.core.events.Event;
import org.eclipse.smarthome.core.events.EventFilter;
import org.eclipse.smarthome.core.events.EventSubscriber;
import org.eclipse.smarthome.core.items.events.ItemStateChangedEvent;
import org.eclipse.smarthome.io.transport.mqtt.MqttBrokerConnection;
import org.eclipse.smarthome.io.transport.mqtt.MqttConnectionObserver;
import org.eclipse.smarthome.io.transport.mqtt.MqttConnectionState;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class ItemStateEventSubscriber implements EventSubscriber, MqttConnectionObserver {

  private final Logger logger = LoggerFactory.getLogger(ItemStateEventSubscriber.class);

  private final DestinationLookup destinationLookup = new DestinationLookup(new StandardMappingTable());
  private final AtomicBoolean connected = new AtomicBoolean(false);

  private final MqttBrokerConnection brokerConnection;
  private final CloudMqttConnection connection;

  @Activate
  public ItemStateEventSubscriber(@Reference CloudMqttConnection connection) {
    this.brokerConnection = connection.getClient();
    this.connection = connection;
    connection.getClient().addConnectionObserver(this);
  }

  @Deactivate
  public void deactivate() {
    brokerConnection.removeConnectionObserver(this);
  }

  @Override
  public void receive(Event event) {
    String payload = event.getPayload();

    if (connected.get()) {
      ItemStateChangedEvent se = (ItemStateChangedEvent) event;
      DeviceAuthentication authentication = connection.getDeviceAuthentication();
      BindableDestination destination = destinationLookup.get("item.state")
        .map(dst -> {
          return dst.bind(new StandardVariableDefinition("version"), "1")
            .bind(new StandardVariableDefinition("organization"), authentication.getOrganizationId())
            .bind(new StandardVariableDefinition("device"), authentication.getDeviceId())
            .bind(new StandardVariableDefinition("operation"), "d2c")
            .bind(new StandardVariableDefinition("item-id"), se.getItemName());
        }).filter(BoundDestination.class::isInstance)
        .orElseThrow(() -> new IllegalArgumentException("Could not bind destination for event"));
      brokerConnection.publish(destination.getPath(), payload.getBytes(StandardCharsets.UTF_8), 0, false);
    } else {
      logger.trace("Skip publishing of {}, broker is disconnected", event);
    }
  }

  public EventFilter getEventFilter() {
    return null;
  }

  @Override
  public Set<String> getSubscribedEventTypes() {
    return new HashSet<>(Arrays.asList(
      ItemStateChangedEvent.TYPE
    ));

  }

  @Override
  public void connectionStateChanged(MqttConnectionState state, Throwable error) {
    switch (state) {
      case CONNECTED:
        logger.info("Successfully connected to broker", error);
        connected.set(true);
        break;
      case DISCONNECTED:
        logger.info("Failed to connect to broker", error);
        connected.set(false);
        break;
    }
  }

}
