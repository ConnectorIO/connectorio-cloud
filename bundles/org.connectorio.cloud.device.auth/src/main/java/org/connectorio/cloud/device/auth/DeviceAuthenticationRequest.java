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

import java.net.URI;

public class DeviceAuthenticationRequest {

  private final String tokenURI;
  private final String clientId;
  private final URI verificationUri;
  private final URI completeUri; // one click address
  private final String userCode;
  private final long lifetime;
  private final long validity;
  private final long interval;

  public DeviceAuthenticationRequest(String tokenURI, String clientId, URI verificationUri, URI completeUri, String userCode,
          long lifetime, long validity, long interval) {
    this.tokenURI = tokenURI;
    this.clientId = clientId;
    this.verificationUri = verificationUri;
    this.completeUri = completeUri;
    this.userCode = userCode;
    this.lifetime = lifetime;
    this.validity = validity;
    this.interval = interval;
  }

  public String getTokenURI() {
    return tokenURI;
  }

  public URI getVerificationUri() {
    return verificationUri;
  }

  public URI getCompleteUri() {
    return completeUri;
  }

  public String getUserCode() {
    return userCode;
  }

  public long getValidity() {
    return validity;
  }

  public long getInterval() {
    return interval;
  }

  public String getClientId() {
    return clientId;
  }

  public long getLifetime() {
    return lifetime;
  }
}
