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

import org.connectorio.cloud.event.mapping.BindableDestination;
import org.connectorio.cloud.event.mapping.Operation;
import org.connectorio.cloud.event.mapping.VariableDefinition;
import org.connectorio.cloud.event.mapping.VariableValue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class StandardBindableDestination implements BindableDestination {

  private final String code;
  private final Operation operation;
  protected final String path;
  private final List<VariableDefinition> variables;
  private final List<VariableValue> values;

  public StandardBindableDestination(String code, Operation operation, String path) {
    this(code, operation, path, Collections.emptyList(), Collections.emptyList());
  }

  public StandardBindableDestination(String code, Operation operation, String path, VariableDefinition ... variables) {
    this(code, operation, path, Arrays.asList(variables), Collections.emptyList());
  }

  public StandardBindableDestination(String code, Operation operation, String path, List<VariableDefinition> variables, List<VariableValue> values) {
    this.code = code;
    this.operation = operation;
    this.path = path;
    this.variables = variables;
    this.values = values;
  }

  @Override
  public Operation getOperation() {
    return operation;
  }

  @Override
  public String getCode() {
    return code;
  }

  @Override
  public String getPath() {
    return "{operation}/" + path;
  }

  @Override
  public List<VariableDefinition> getVariables() {
    List<VariableDefinition> vars = new ArrayList<>();
    vars.add(new StandardVariableDefinition("operation"));
    vars.addAll(variables);
    return vars;
  }

  @Override
  public List<VariableValue> getValues() {
    return values;
  }

  @Override
  public BindableDestination bind(VariableDefinition definition, String value) {
    return of(code, operation, path, definition, value, variables, values);
  }

  static BindableDestination of(String code, Operation operation, String path, VariableDefinition variable, String value, List<VariableDefinition> variables, List<VariableValue> values) {
    String finalPath = path;
    VariableDefinition definition = variables.stream()
      .filter(vd -> vd.equals(variable)).findFirst()
      .orElseThrow(() -> new IllegalArgumentException("Variable '" + variable.getName() + "' not found in path '" + finalPath + "'"));

    values = new ArrayList<>(values);
    values.add(new StandardVariableValue(variable, value));

    variables.remove(definition);

    path = path.replace("{" + variable.getName() + "}", value);
    if (variables.isEmpty()) {
      return new BoundDestination(code, operation, path, values);
    }
    return new PartiallyBoundDestination(code, operation, path, variables, values);
  }

}
