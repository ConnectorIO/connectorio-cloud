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

import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.oauth2.sdk.token.RefreshToken;
import com.nimbusds.oauth2.sdk.token.Tokens;
import com.nimbusds.openid.connect.sdk.token.OIDCTokens;
import java.io.IOException;
import java.text.ParseException;
import java.util.Collections;
import java.util.Date;
import java.util.Dictionary;
import java.util.Map;
import java.util.Optional;
import java.util.Timer;
import org.connectorio.cloud.device.auth.DeviceAuthentication;
import org.osgi.framework.Constants;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(configurationPid = "org.connectorio.cloud.device.auth.token", configurationPolicy = ConfigurationPolicy.REQUIRE,
  property = {
    Constants.SERVICE_PID + "=org.connectorio.cloud.device.auth.token",
    "service.config.label=Device Authentication Token",
    "service.config.category=ConnectorIO Cloud",
    "service.config.description.uri=connectorio:device-auth-token",
  }
)
public class NimbusDeviceAuthentication implements DeviceAuthentication {

  private final Logger logger = LoggerFactory.getLogger(NimbusDeviceAuthentication.class);

  private final AuthServiceConfiguration configuration;
  private final ConfigurationAdmin admin;

  private Timer timer = createTimer();
  private RefreshToken refreshToken;

  private String accessToken = null;
  private String organization;
  private String deviceId;
  private Date expirationTime;
  private Tokens tokens;

  @Activate
  public NimbusDeviceAuthentication(@Reference AuthServiceConfiguration configuration, @Reference ConfigurationAdmin admin) {
    this.configuration = configuration;
    this.admin = admin;

    try {
      Configuration stored = admin.getConfiguration("org.connectorio.cloud.device.auth.token");
      Optional.ofNullable(stored.getProperties()).map(p -> p.get("refreshToken"))
        .map(token -> Collections.singletonMap("refreshToken", (String) token))
        .ifPresent(this::updated);
    } catch (IOException e) {
    }
  }

  @Modified
  public void updated(Map<String, String> config) {
    timer.purge();

    this.refreshToken = Optional.ofNullable(config.get("refreshToken"))
      .map(String::trim)
      .filter(string -> !string.isEmpty())
      .map(RefreshToken::new)
      .orElse(null);

    if (this.refreshToken != null) {
      // this is initial call, lets obtain access token quickly as there might be other actions waiting for us!
      timer.schedule(new RefreshTask(configuration, timer, refreshToken, this::update), 100);
    }
  }

  // returns new refresh token
  private void update(OIDCTokens tokens) {
    RefreshToken receiverRefresh = tokens.getRefreshToken();
    this.accessToken = tokens.getAccessToken().toString();
    try {
      JWT jwt = JWTParser.parse(this.accessToken);
      final JWTClaimsSet claimsSet = jwt.getJWTClaimsSet();
      this.deviceId = getClaim(claimsSet, "gateway");
      this.organization =  Optional.ofNullable(getClaim(claimsSet, "organization"))
        .orElseGet(() -> getClaim(claimsSet, "tenant"));
      this.expirationTime = claimsSet.getExpirationTime();
    } catch (ParseException e) {
      throw new RuntimeException("Could not parse token", e);
    }

    if (!receiverRefresh.equals(this.refreshToken)) {
      try {
        save();
      } catch (IOException e) {
        logger.error("Could not save refresh token.", e);
      }
    }
  }

  private String getClaim(JWTClaimsSet claimsSet, String gateway) {
    try {
      return claimsSet.getStringClaim(gateway);
    } catch (ParseException e) {
      throw new RuntimeException("Could not parse token", e);
    }
  }

  private void save() throws IOException {
    Configuration configuration = admin.getConfiguration("org.connectorio.cloud.device.auth.token");
    Dictionary<String, Object> properties = configuration.getProperties();
    properties.put("refreshToken", refreshToken.getValue());
    configuration.updateIfDifferent(properties);
    logger.info("Updated refresh token cause its expiry will happen in 10 minutes.");
  }

  @Deactivate
  public void deactivate() {
    timer.cancel();
  }

  @Override
  public Date getExpirationTime() {
    return expirationTime;
  }

  @Override
  public String getAccessToken() {
    return accessToken;
  }

  @Override
  public String getDeviceId() {
    return deviceId;
  }

  @Override
  public String getOrganizationId() {
    return organization;
  }

  private Timer createTimer() {
    return new Timer("device-token-refresh", true);
  }

}
