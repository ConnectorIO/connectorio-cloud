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

import org.connectorio.cloud.event.mapping.BindableDestination;
import org.connectorio.cloud.event.mapping.Operation;
import org.connectorio.cloud.event.mapping.VariableDefinition;
import org.connectorio.cloud.event.mapping.VariableValue;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Destination with all elements set.
 */
public class BoundDestination implements BindableDestination {

  private final String code;
  private final Operation operation;
  private final String path;
  private final List<VariableValue> values;

  public BoundDestination(String code, Operation operation, String path) {
    this(code, operation, path, Collections.emptyList());
  }

  public BoundDestination(String code, Operation operation, String path, VariableValue ... values) {
    this(code, operation, path, Arrays.asList(values));
  }

  public BoundDestination(String code, Operation operation, String path, List<VariableValue> values) {
    this.code = code;
    this.operation = operation;
    this.path = path;
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
    return path;
  }

  @Override
  public List<VariableDefinition> getVariables() {
    return values.stream().map(VariableValue::getVariable).collect(Collectors.toList());
  }

  @Override
  public List<VariableValue> getValues() {
    return values;
  }

  @Override
  public BindableDestination bind(VariableDefinition definition, String value) {
    throw new UnsupportedOperationException("Can not bind variable, destination is already bound");
  }

}
