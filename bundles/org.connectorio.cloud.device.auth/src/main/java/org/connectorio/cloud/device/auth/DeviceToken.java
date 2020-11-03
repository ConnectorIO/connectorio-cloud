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
package org.connectorio.cloud.device.auth;

import java.util.Objects;

public class DeviceToken {

  private final String accessToken;
  private final String refreshToken;
  private final long expiresIn;

  public DeviceToken(String accessToken, String refreshToken, long expiresIn) {
    this.accessToken = accessToken;
    this.refreshToken = refreshToken;
    this.expiresIn = expiresIn;
  }

  public String getAccessToken() {
    return accessToken;
  }

  public String getRefreshToken() {
    return refreshToken;
  }

  public long getExpiresIn() {
    return expiresIn;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof DeviceToken)) {
      return false;
    }

    DeviceToken that = (DeviceToken) o;
    return getExpiresIn() == that.getExpiresIn() &&
      Objects.equals(getAccessToken(), that.getAccessToken()) &&
      Objects.equals(getRefreshToken(), that.getRefreshToken());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getAccessToken(), getRefreshToken(), getExpiresIn());
  }

}
