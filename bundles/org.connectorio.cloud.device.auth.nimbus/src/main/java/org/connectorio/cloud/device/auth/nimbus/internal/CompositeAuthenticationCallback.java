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
package org.connectorio.cloud.device.auth.nimbus.internal;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.connectorio.cloud.device.auth.DeviceAuthenticationCallback;
import org.connectorio.cloud.device.auth.DeviceAuthenticationRequest;
import org.connectorio.cloud.device.auth.DeviceToken;

public class CompositeAuthenticationCallback implements DeviceAuthenticationCallback {

  private final List<DeviceAuthenticationCallback> callbacks;

  public CompositeAuthenticationCallback(List<DeviceAuthenticationCallback> callbacks, DeviceAuthenticationCallback ... additional) {
    this(Stream.concat(callbacks.stream(), Arrays.stream(additional)).collect(Collectors.toList()));
  }

  public CompositeAuthenticationCallback(List<DeviceAuthenticationCallback> callbacks) {
    this.callbacks = callbacks;
  }

  @Override
  public void deviceAuthenticated(DeviceAuthenticationRequest request, DeviceToken token) {
    callbacks.forEach(cb -> cb.deviceAuthenticated(request, token));
  }

  @Override
  public void failedAuthentication(DeviceAuthenticationRequest request, Throwable failure) {
    callbacks.forEach(cb -> cb.failedAuthentication(request, failure));
  }

}
