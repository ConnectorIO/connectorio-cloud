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

import java.util.ArrayList;
import java.util.List;
import org.connectorio.cloud.event.mapping.BindableDestination;
import org.connectorio.cloud.event.mapping.Operation;
import org.connectorio.cloud.event.mapping.VariableDefinition;
import org.connectorio.cloud.event.mapping.VariableValue;

public class NamespacedMapping implements BindableDestination {

  private final BindableDestination delegate;

  public NamespacedMapping(BindableDestination delegate) {
    this.delegate = delegate;
  }

  @Override
  public Operation getOperation() {
    return delegate.getOperation();
  }

  @Override
  public String getCode() {
    return delegate.getCode();
  }

  @Override
  public String getPath() {
    return "{organization}/{device}/" + delegate.getPath();
  }

  @Override
  public List<VariableDefinition> getVariables() {
    List<VariableDefinition> variables = new ArrayList<>();
    variables.add(new StandardVariableDefinition("organization"));
    variables.add(new StandardVariableDefinition("device"));
    variables.addAll(delegate.getVariables());
    return variables;
  }

  @Override
  public List<VariableValue> getValues() {
    return delegate.getValues();
  }

  @Override
  public BindableDestination bind(VariableDefinition definition, String value) {
    return StandardBindableDestination
        .of(delegate.getCode(), delegate.getOperation(), getPath(), definition, value, getVariables(), getValues());
  }

}
