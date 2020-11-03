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
package org.connectorio.cloud.event.mapping.parser.node;

import org.connectorio.cloud.event.mapping.parser.DestinationNode;

public class VariableNode implements DestinationNode {

  private final String variable;
  private final int offset;

  public VariableNode(String variable, int offset) {
    this.variable = variable;
    this.offset = offset;
  }

  public String getVariable() {
    return variable;
  }

  public int getOffset() {
    return offset;
  }

  public boolean matches(String part) {
    return true;
  }

  @Override
  public String toString() {
    return "Variable Node [(" + variable + ")]";
  }

}
