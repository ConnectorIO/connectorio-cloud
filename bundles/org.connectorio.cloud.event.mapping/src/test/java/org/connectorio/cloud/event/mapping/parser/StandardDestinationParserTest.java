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
package org.connectorio.cloud.event.mapping.parser;

import static org.assertj.core.api.Assertions.assertThat;

import org.connectorio.cloud.event.mapping.BindableDestination;
import org.connectorio.cloud.event.mapping.VariableValue;
import org.connectorio.cloud.event.mapping.standard.BoundDestination;
import org.connectorio.cloud.event.mapping.standard.StandardMappingTable;
import org.connectorio.cloud.event.mapping.standard.StandardVariableDefinition;
import org.connectorio.cloud.event.mapping.standard.StandardVariableValue;
import org.connectorio.cloud.event.mapping.standard.Synchronisation;
import java.util.Arrays;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class StandardDestinationParserTest {

  @Test
  void verifyMapping() {
    StandardMappingTable mappingTable = new StandardMappingTable();

    StandardDestinationParser parser = new StandardDestinationParser(mappingTable);
    BindableDestination mapping = parser.parse("v2a/ABC/ZXY/d2c/item/123/update");

    assertThat(mapping).isNotNull()
      .matches(
        hasVariables(
          value("version", "2a"),
          value("organization", "ABC"),
          value("device", "ZXY"),
          value("item-id", "123")
        )
      );
  }

  @Test
  void testMockedValues() {
    StandardMappingTable mappingTable = new StandardMappingTable();

    StandardDestinationParser parser = new StandardDestinationParser(mappingTable);
    BindableDestination mapping = parser.parse("v1/QCF9GNM5/0UV3TFSS/syn/extension");

    assertThat(mapping).isNotNull()
      .matches(
        hasVariables(
          value("version", "1"),
          value("organization", "QCF9GNM5"),
          value("device", "0UV3TFSS"),
          value("operation", "syn")
        )
      );
    assertThat(mapping.getOperation()).isInstanceOf(Synchronisation.class);
  }

  @Test
  void testDestinationBinding() {
    StandardMappingTable mappingTable = new StandardMappingTable();
    BindableDestination destination = mappingTable.getDestinations().stream()
      .filter(dst -> dst.getCode().equals("item.state")).findFirst().get();

    assertThat(destination.getVariables()).hasSize(5);

    destination = destination.bind(new StandardVariableDefinition("version"), "1");
    check(destination, 5, 1, "v1/{organization}/{device}/{operation}/item/{item-id}/state");

    destination = destination.bind(new StandardVariableDefinition("organization"), "TENANT");
    check(destination, 4, 2, "v1/TENANT/{device}/{operation}/item/{item-id}/state");

    destination = destination.bind(new StandardVariableDefinition("device"), "GATEWAY");
    check(destination, 3, 3, "v1/TENANT/GATEWAY/{operation}/item/{item-id}/state");

    destination = destination.bind(new StandardVariableDefinition("operation"), "c2c");
    check(destination, 2, 4, "v1/TENANT/GATEWAY/c2c/item/{item-id}/state");

    destination = destination.bind(new StandardVariableDefinition("item-id"), "ITEM");
    check(destination, 5, 5, "v1/TENANT/GATEWAY/c2c/item/ITEM/state");
    assertThat(destination).isInstanceOf(BoundDestination.class);
  }

  private void check(BindableDestination destination, int variables, int values, String path) {
    assertThat(destination.getVariables()).hasSize(variables);
    assertThat(destination.getValues()).hasSize(values);
    assertThat(destination.getPath()).isEqualTo(path);
  }

  private Predicate<BindableDestination> hasVariables(VariableValue ... values) {
    return destination -> {
      assertThat(destination.getValues()).containsAll(Arrays.asList(values));
      return true;
    };
  }

  private VariableValue value(String name, String value) {
    return new StandardVariableValue(new StandardVariableDefinition(name), value);
  }

}