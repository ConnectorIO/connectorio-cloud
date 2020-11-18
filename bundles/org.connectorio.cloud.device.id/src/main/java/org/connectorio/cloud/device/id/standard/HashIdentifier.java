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
package org.connectorio.cloud.device.id.standard;

import java.util.Objects;
import org.connectorio.cloud.device.id.DeviceIdentifier;

public class HashIdentifier implements DeviceIdentifier {

  private final String algorithm;
  private final String hash;

  public HashIdentifier(String algorithm, String hash) {
    this.hash = hash;
    this.algorithm = algorithm;
  }

  public String getAlgorithm() {
    return algorithm;
  }

  public String getHash() {
    return hash;
  }

  @Override
  public String toString() {
    return "{" + algorithm + "}" + hash;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof HashIdentifier)) {
      return false;
    }
    HashIdentifier that = (HashIdentifier) o;
    return Objects.equals(algorithm, that.algorithm) && Objects
        .equals(hash, that.hash);
  }

  @Override
  public int hashCode() {
    return Objects.hash(algorithm, hash);
  }
}
