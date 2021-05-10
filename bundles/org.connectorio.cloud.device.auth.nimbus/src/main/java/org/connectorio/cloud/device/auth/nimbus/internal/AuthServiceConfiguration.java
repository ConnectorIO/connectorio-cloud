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
package org.connectorio.cloud.device.auth.nimbus.internal;

import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;

@Component(configurationPid = "org.connectorio.cloud.device.auth", service = AuthServiceConfiguration.class,
  configurationPolicy = ConfigurationPolicy.REQUIRE, property = {
    Constants.SERVICE_PID + "=org.connectorio.cloud.device.auth",
    "service.config.label=Device Authentication",
    "service.config.category=ConnectorIO Cloud",
    "service.config.description.uri=connectorio:device-auth",
  }
)
public class AuthServiceConfiguration {

  private final String authURI;
  private final String tokenURI;
  private final String clientId;
  private final String secret;

  @interface Config {
    String authURI() default "";
    String tokenURI() default "";
    String clientId() default "";
    String secret() default "";
  }

  @Activate
  public AuthServiceConfiguration(Config config) {
    this.authURI = config.authURI();
    this.tokenURI = config.tokenURI();
    this.clientId = config.clientId();
    this.secret = config.secret();
  }

  public String getAuthURI() {
    return authURI;
  }

  public String getTokenURI() {
    return tokenURI;
  }

  public String getClientId() {
    return clientId;
  }

  public String getSecret() {
    return secret;
  }

}
