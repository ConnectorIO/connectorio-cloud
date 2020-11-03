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
import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.oauth2.sdk.RefreshTokenGrant;
import com.nimbusds.oauth2.sdk.TokenRequest;
import com.nimbusds.oauth2.sdk.auth.ClientSecretBasic;
import com.nimbusds.oauth2.sdk.auth.Secret;
import com.nimbusds.oauth2.sdk.http.HTTPResponse;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.token.RefreshToken;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponse;
import com.nimbusds.openid.connect.sdk.token.OIDCTokens;
import java.io.IOException;
import java.net.URI;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class RefreshTask extends TimerTask {

  private final Logger logger = LoggerFactory.getLogger(RefreshTask.class);

  private final AuthServiceConfiguration configuration;
  private final Timer timer;
  private final RefreshToken refreshToken;
  private final Consumer<OIDCTokens> consumer;
  private final int failures;

  public RefreshTask(AuthServiceConfiguration configuration, Timer timer, RefreshToken refreshToken, Consumer<OIDCTokens> consumer) {
    this(configuration, timer, refreshToken, consumer, 0);
  }

  public RefreshTask(AuthServiceConfiguration configuration, Timer timer, RefreshToken refreshToken, Consumer<OIDCTokens> consumer, int failures) {
    this.configuration = configuration;
    this.timer = timer;
    this.refreshToken = refreshToken;
    this.consumer = consumer;
    this.failures = failures;
  }

  @Override
  public void run() {
    TokenRequest request = new TokenRequest(
      URI.create(configuration.getTokenURI()),
      new ClientSecretBasic(
        new ClientID(configuration.getClientId()),
        new Secret(configuration.getSecret())
      ),
      new RefreshTokenGrant(refreshToken)
    );

    try {
      HTTPResponse response = request.toHTTPRequest().send();
      if (response.indicatesSuccess()) {
        OIDCTokens tokens = OIDCTokenResponse.parse(response).toSuccessResponse().getOIDCTokens();
        consumer.accept(tokens);

        long lifetime = tokens.getAccessToken().getLifetime();
        long delay = (long) (lifetime - (lifetime * 0.1));

        timer.schedule(new RefreshTask(configuration, timer, tokens.getRefreshToken(), consumer), TimeUnit.SECONDS.toMillis(delay));
        logger.info("Scheduling refresh of access token in next {}s (lifetime is {}s)", delay, lifetime);
      } else {
        logger.warn("Error while refreshing token. Code: {}, message: {}, content: {}", response.getStatusCode(), response.getStatusMessage(), response.getContent());
        retry();
      }
    } catch (ParseException | IOException e) {
      logger.warn("Unexpected error while refreshing token.", e);
      retry();
    }
  }

  private void retry() {
    long remainingTime = getRefreshTokenExpirationTime() - System.currentTimeMillis();
    if (remainingTime > 0) {
      int backoff = calculateBackoff(remainingTime);
      timer.schedule(new RefreshTask(configuration, timer, refreshToken, consumer, failures + 1), backoff);
    } else {
      logger.error("Refresh token expired. Device must be authenticated again.");
    }
  }

  /**
   * Basic backoff calculation which simply delays refresh depending on number of previous failures.
   * Few words - if token expires in less than minute, then attempt will be made in 25s.
   * If number of failures is 0 so far, we wait 60s. For all subsequent requests method will return 10, 40, 90s and 160s.
   * Any failure after fourth will delay execution by 5 minutes.
   *
   * Remaining time is not take into consideration when calculating refresh for failures above first, thus this method
   * logic might cause token expiry if remainingTime is ie 61s and we have 3rd attempt.
   *
   * @param remainingTime Time left for token to be refreshed.
   * @return Desired delay.
   */
  private int calculateBackoff(long remainingTime) {
    if (remainingTime < 60_000) {
      return 25_000;
    }

    if (failures == 0) {
      return 60_0000;
    }
    return failures < 4 ? failures * failures * 10000 : 300_0000;
  }

  private long getRefreshTokenExpirationTime() {
    try {
      JWT jwt = JWTParser.parse(refreshToken.getValue());
      JWTClaimsSet jwtClaimsSet = jwt.getJWTClaimsSet();
      if (jwtClaimsSet != null && jwtClaimsSet.getExpirationTime() != null) {
        return jwtClaimsSet.getExpirationTime().getTime();
      }
    } catch (java.text.ParseException e) {
      logger.error("Failed to determine refresh token lifetime", e);
    }
    return 0;
  }

}
