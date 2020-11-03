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
package org.connectorio.cloud.device.auth.ui.internal;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.connectorio.cloud.device.auth.DeviceAuthenticationRequest;
import org.connectorio.cloud.device.auth.ui.PairingRegistry;
import org.osgi.service.component.annotations.Component;

@Component
public class BasicPairingRegistry implements PairingRegistry {

  private Map<UUID, DeviceAuthenticationRequest> requests = new ConcurrentHashMap<>();

  public UUID add(DeviceAuthenticationRequest request) {
    UUID uuid = UUID.randomUUID();
    requests.put(uuid, request);
    return uuid;
  }

  public Optional<DeviceAuthenticationRequest> get(UUID uuid) {
    return Optional.ofNullable(requests.get(uuid));
  }

  public Optional<DeviceAuthenticationRequest> remove(UUID uuid) {
    return Optional.ofNullable(requests.remove(uuid));
  }

  @Override
  public Map<UUID, DeviceAuthenticationRequest> all() {
    return Collections.unmodifiableMap(requests);
  }

}
