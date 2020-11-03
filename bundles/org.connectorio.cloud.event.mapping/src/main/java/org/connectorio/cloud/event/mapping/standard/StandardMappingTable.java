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
package org.connectorio.cloud.event.mapping.standard;

import static java.util.Arrays.asList;

import org.connectorio.cloud.event.mapping.BindableDestination;
import org.connectorio.cloud.event.mapping.MappingTable;
import org.connectorio.cloud.event.mapping.Operation;
import org.connectorio.cloud.event.mapping.VariableDefinition;
import org.connectorio.cloud.event.mapping.participant.Participant;
import java.util.List;

public class StandardMappingTable implements MappingTable {

  @Override
  public List<BindableDestination> getDestinations() {
    return asList(
      new VersionedMapping(new NamespacedMapping(new StandardBindableDestination("status", d2c(), "status"))),

      // channel type synchronization
      new VersionedMapping(new NamespacedMapping(new StandardBindableDestination("channel-types", syn(), "channel-types"))),

      // extension synchronization
      new VersionedMapping(new NamespacedMapping(new StandardBindableDestination("extension.sync", syn(), "extension"))),
      // extension events
      new VersionedMapping(new NamespacedMapping(new StandardBindableDestination("extension.install", d2c(), "extension/{id}/install", var("id")))),
      new VersionedMapping(new NamespacedMapping(new StandardBindableDestination("extension.uninstall", d2c(), "extension/{id}/uninstall", var("id")))),
      // extension cloud commands
      new VersionedMapping(new NamespacedMapping(new StandardBindableDestination("cloud.extension.uninstall", c2d(), "extension/{id}/install", var("id")))),
      new VersionedMapping(new NamespacedMapping(new StandardBindableDestination("cloud.extension.uninstall", c2d(), "extension/{id}/uninstall", var("id")))),


      // thing synchronization
      new VersionedMapping(new NamespacedMapping(new StandardBindableDestination("thing.sync", syn(), "thing", var("thing-id")))),
      // thing callbacks
      new VersionedMapping(new NamespacedMapping(new StandardBindableDestination("thing.update", d2c(), "thing/{thing-id}/update", var("thing-id")))),
      new VersionedMapping(new NamespacedMapping(new StandardBindableDestination("thing.remove", d2c(), "thing/{thing-id}/remove", var("thing-id")))),
      // thing cloud commands
      new VersionedMapping(new NamespacedMapping(new StandardBindableDestination("cloud.thing.update", c2d(), "thing/{thing-id}/update", var("thing-id")))),
      new VersionedMapping(new NamespacedMapping(new StandardBindableDestination("cloud.thing.remove", c2d(), "thing/{thing-id}/remove", var("thing-id")))),


      // item synchronization
      new VersionedMapping(new NamespacedMapping(new StandardBindableDestination("item.sync", syn(), "item", var("item-id")))),
      // item callbacks
      new VersionedMapping(new NamespacedMapping(new StandardBindableDestination("item.state", d2c(), "item/{item-id}/state", var("item-id")))),
      new VersionedMapping(new NamespacedMapping(new StandardBindableDestination("item.update", d2c(), "item/{item-id}/update", var("item-id")))),
      new VersionedMapping(new NamespacedMapping(new StandardBindableDestination("item.remove", d2c(), "item/{item-id}/remove", var("item-id")))),
      // item commands
      new VersionedMapping(new NamespacedMapping(new StandardBindableDestination("cloud.item.update", c2d(), "item/{item-id}/update", var("item-id")))),
      new VersionedMapping(new NamespacedMapping(new StandardBindableDestination("cloud.item.remove", c2d(), "item/{item-id}/remove", var("item-id"))))
    );
  }

  static Operation d2c() {
    return exchange(device(), cloud());
  }

  static Operation c2d() {
    return exchange(cloud(), device());
  }

  static Operation syn() {
    return new Synchronisation();
  }

  static Operation exchange(Participant producer, Participant subscriber) {
    return new Exchange(producer, subscriber);
  }

  static Participant cloud() {
    return new StandardCloudParticipant();
  }

  static Participant device() {
    return new StandardDeviceParticipant();
  }

  static VariableDefinition var(String name) {
    return new StandardVariableDefinition(name);
  }

}
