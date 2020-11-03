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
package org.connectorio.cloud.event.mapping.parser;

import org.connectorio.cloud.event.mapping.BindableDestination;
import org.connectorio.cloud.event.mapping.DestinationParser;
import org.connectorio.cloud.event.mapping.MappingTable;
import org.connectorio.cloud.event.mapping.VariableValue;
import org.connectorio.cloud.event.mapping.parser.node.StaticNode;
import org.connectorio.cloud.event.mapping.parser.node.VariableNode;
import org.connectorio.cloud.event.mapping.standard.BoundDestination;
import org.connectorio.cloud.event.mapping.standard.StandardVariableDefinition;
import org.connectorio.cloud.event.mapping.standard.StandardVariableValue;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class StandardDestinationParser implements DestinationParser {

  private final DestinationTree mappings;

  public StandardDestinationParser(MappingTable mapping) {
    this.mappings = constructTree(mapping);
  }

  private DestinationTree constructTree(MappingTable mapping) {
    DestinationTree tree = new DestinationTree();
    for (BindableDestination destination : mapping.getDestinations()) {
      String[] parts = destination.getPath().split("/");

      List<DestinationNode> nodes = new ArrayList<>();
      for (String part : parts) {
        if (part.contains("{")) {
          int beginIndex = part.indexOf('{') + 1;
          int endIndex = part.indexOf('}');
          nodes.add(new VariableNode(part.substring(beginIndex, endIndex), beginIndex - 1));
        } else {
          nodes.add(new StaticNode(part));
        }
      }

      tree.push(destination, nodes);
    }

    return tree;
  }

  @Override
  public BindableDestination parse(String name) {
    String[] destination = name.split("/");

    return mappings.lookup(destination);
  }

  private class DestinationTree {

    private Map<BindableDestination, List<DestinationNode>> paths = new HashMap<>();

    public BindableDestination lookup(String[] elements) {
      for (Entry<BindableDestination, List<DestinationNode>> entry : paths.entrySet()) {
        List<DestinationNode> path = entry.getValue();
        List<VariableValue> values = new ArrayList<>();

        if (elements.length == path.size()) {
          for (int index = 0; index < elements.length; index++) {
            DestinationNode node = path.get(index);
            if (!node.matches(elements[index])) {
              break;
            }

            if (node instanceof VariableNode) {
              VariableNode variable = (VariableNode) node;
              values.add(new StandardVariableValue(new StandardVariableDefinition(variable.getVariable()), elements[index].substring(variable.getOffset())));
            }

            if (index + 1 < elements.length) {
              continue;
            }

            BindableDestination destination = entry.getKey();
            return new BoundDestination(destination.getCode(), destination.getOperation(), destination.getPath(), values);
          }
        }
      }

      return null;
    }

    public void push(BindableDestination destination, List<DestinationNode> nodes) {
      this.paths.put(destination, nodes);
    }
  }

}
