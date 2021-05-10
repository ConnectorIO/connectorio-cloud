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
package org.connectorio.cloud.device.auth.setup.internal;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.connectorio.cloud.device.auth.DeviceAuthenticationCallback;
import org.connectorio.cloud.device.auth.DeviceAuthenticationRequest;
import org.connectorio.cloud.device.auth.DeviceToken;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class SetupTokenListener implements DeviceAuthenticationCallback {

  private final Logger logger = LoggerFactory.getLogger(SetupTokenListener.class);

  @Override
  public void failedAuthentication(DeviceAuthenticationRequest request, Throwable failure) {
    logger.info("Could not authenticate request {}", request.getUserCode(), failure);
  }

  @Override
  public void deviceAuthenticated(DeviceAuthenticationRequest request, DeviceToken deviceToken) {
    logger.info("Device successfully authenticated with code {}", request.getUserCode());

    try {
      Map<String, String> props = new HashMap<>();
      props.put("refreshToken", deviceToken.getRefreshToken());
      createConfiguration(props);
    } catch (IOException e) {
      logger.error("Could not store refresh token for {}", request.getUserCode(), e);
    }
  }

  protected void createConfiguration(Map<String, String> properties) throws IOException {
    String factoryPid = "org.connectorio.cloud.device.auth.token";

    Properties props = new Properties();
    File file = new File(new File(System.getProperty("karaf.etc")), factoryPid + ".cfg");

    props.putAll(properties);
    props.store(new FileOutputStream(file), "Generated at " + new Date());
  }

}
