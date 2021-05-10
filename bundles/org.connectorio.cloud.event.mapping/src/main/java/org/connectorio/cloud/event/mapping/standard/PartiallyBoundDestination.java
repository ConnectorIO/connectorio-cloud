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

import org.connectorio.cloud.event.mapping.Operation;
import org.connectorio.cloud.event.mapping.VariableDefinition;
import org.connectorio.cloud.event.mapping.VariableValue;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class PartiallyBoundDestination extends StandardBindableDestination {

  public PartiallyBoundDestination(String code, Operation operation, String path) {
    this(code, operation, path, Collections.emptyList(), Collections.emptyList());
  }

  public PartiallyBoundDestination(String code, Operation operation, String path, VariableDefinition ... variables) {
    this(code, operation, path, Arrays.asList(variables), Collections.emptyList());
  }

  public PartiallyBoundDestination(String code, Operation operation, String path, List<VariableDefinition> variables, List<VariableValue> values) {
    super(code, operation, path, variables, values);
  }

  @Override
  public String getPath() {
    return path;
  }

}
