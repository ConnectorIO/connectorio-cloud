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
package org.connectorio.cloud.event.mapping.standard;

import org.connectorio.cloud.event.mapping.VariableDefinition;
import org.connectorio.cloud.event.mapping.VariableValue;
import java.util.Objects;

public class StandardVariableValue implements VariableValue {

  private final VariableDefinition variable;
  private final String value;

  public StandardVariableValue(VariableDefinition variable, String value) {
    this.variable = variable;
    this.value = value;
  }

  @Override
  public VariableDefinition getVariable() {
    return variable;
  }

  @Override
  public String getValue() {
    return value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof StandardVariableValue)) {
      return false;
    }
    StandardVariableValue that = (StandardVariableValue) o;
    return Objects.equals(getVariable(), that.getVariable()) &&
        Objects.equals(getValue(), that.getValue());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getVariable(), getValue());
  }

  @Override
  public String toString() {
    return "Variable Value [" + variable + ", " + value + "]";
  }
}
