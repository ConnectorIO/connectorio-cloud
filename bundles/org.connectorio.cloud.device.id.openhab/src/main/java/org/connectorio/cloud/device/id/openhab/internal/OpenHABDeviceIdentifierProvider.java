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
package org.connectorio.cloud.device.id.openhab.internal;

import org.apache.commons.codec.digest.DigestUtils;
import org.connectorio.cloud.device.id.DeviceIdentifierProvider;
import org.connectorio.cloud.device.id.DeviceIdentifierType;
import org.connectorio.cloud.device.id.DeviceIdentityTypes;
import org.connectorio.cloud.device.id.standard.HashIdentifier;
import org.eclipse.smarthome.core.id.InstanceUUID;
import org.osgi.service.component.annotations.Component;

@Component
public class OpenHABDeviceIdentifierProvider implements DeviceIdentifierProvider<HashIdentifier> {

  private static final String HASH_ALGORITHM = "SHA-256";

  @Override
  public HashIdentifier getIdentifier() {
    String instanceUUID = new DigestUtils(HASH_ALGORITHM).digestAsHex(InstanceUUID.get());
    return new HashIdentifier(HASH_ALGORITHM, instanceUUID);
  }

  @Override
  public DeviceIdentifierType<HashIdentifier> getIdentityType() {
    return DeviceIdentityTypes.OPENHAB;
  }

}
