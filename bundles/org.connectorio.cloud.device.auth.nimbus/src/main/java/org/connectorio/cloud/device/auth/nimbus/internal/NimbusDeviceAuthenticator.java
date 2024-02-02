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

import com.nimbusds.oauth2.sdk.Scope;
import com.nimbusds.oauth2.sdk.TokenRequest;
import com.nimbusds.oauth2.sdk.auth.ClientSecretBasic;
import com.nimbusds.oauth2.sdk.auth.Secret;
import com.nimbusds.oauth2.sdk.device.DeviceAuthorizationErrorResponse;
import com.nimbusds.oauth2.sdk.device.DeviceAuthorizationRequest.Builder;
import com.nimbusds.oauth2.sdk.device.DeviceAuthorizationResponse;
import com.nimbusds.oauth2.sdk.device.DeviceAuthorizationSuccessResponse;
import com.nimbusds.oauth2.sdk.device.DeviceCodeGrant;
import com.nimbusds.oauth2.sdk.http.HTTPRequest;
import com.nimbusds.oauth2.sdk.http.HTTPResponse;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.token.AccessToken;
import com.nimbusds.oauth2.sdk.token.RefreshToken;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponse;
import java.lang.Thread.UncaughtExceptionHandler;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.connectorio.cloud.device.auth.DeviceAuthenticationCallback;
import org.connectorio.cloud.device.auth.DeviceAuthenticationRequest;
import org.connectorio.cloud.device.auth.DeviceAuthenticator;
import org.connectorio.cloud.device.auth.DeviceToken;
import org.connectorio.cloud.device.id.DeviceIdentifier;
import org.connectorio.cloud.device.id.DeviceIdentifierProvider;
import org.connectorio.cloud.device.id.DeviceIdentifierType;
import org.connectorio.cloud.device.id.DeviceIdentityTypes;
import org.connectorio.cloud.device.id.standard.CertificateIdentifier;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(service = DeviceAuthenticator.class)
public class NimbusDeviceAuthenticator implements DeviceAuthenticator {

  private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1, (runnable) -> {
    Thread thread = new Thread(runnable, "device-authenticator");
    thread.setUncaughtExceptionHandler(new UncaughtExceptionHandler() {
      @Override
      public void uncaughtException(Thread thread, Throwable throwable) {
        logger.error("Could not handle authentication request, unexpected error occurred.", throwable);
      }
    });
    return thread;
  });

  private final Logger logger = LoggerFactory.getLogger(NimbusDeviceAuthenticator.class);
  private final AuthServiceConfiguration configuration;

  private final List<DeviceAuthenticationCallback> callbacks = new CopyOnWriteArrayList<>();
  private final List<DeviceIdentifierProvider<?>> deviceIdentifierProviders = new CopyOnWriteArrayList<>();

  @Activate
  public NimbusDeviceAuthenticator(@Reference AuthServiceConfiguration configuration) {
    this.configuration = configuration;
  }

  @Override
  public DeviceAuthenticationRequest authenticateDevice(DeviceAuthenticationCallback callback, String... scopes) {
    try {
      DeviceAuthenticationCallback compositeCallback = new CompositeAuthenticationCallback(new ArrayList<>(callbacks), callback);
      URI deviceAuthURI = new URI(configuration.getAuthURI());
      URI deviceTokenURI = new URI(configuration.getTokenURI());
      ClientID clientID = new ClientID(configuration.getClientId());
      Secret secret = new Secret(configuration.getSecret());
      Scope scope = new Scope(scopes);

      //String hashAlgorithm = "SHA-256";
      //String instanceUUID = new DigestUtils(hashAlgorithm).digestAsHex(InstanceUUID.get());

      final Builder builder = new Builder(clientID)
        .scope(scope)
        .endpointURI(deviceAuthURI);

      deviceIds((idp -> DeviceIdentityTypes.CERT != idp))
        .forEach(provider -> append(builder, provider));

      HTTPRequest httpRequest = builder.build().toHTTPRequest();
      logger.debug("Prepared device code request: {}. Additional parameters: {}", httpRequest.getURI(), httpRequest.getQueryParameters());
      // append device certificate if any
      appendCertificate(httpRequest);

      HTTPResponse httpResponse = httpRequest.send();

      DeviceAuthorizationResponse response = DeviceAuthorizationResponse.parse(httpResponse);

      if (!response.indicatesSuccess()) {
        DeviceAuthorizationErrorResponse errorResponse = response.toErrorResponse();
        throw new RuntimeException("Could not finish request. Server returned code " + errorResponse.getErrorObject().getHTTPStatusCode() + " and description " + errorResponse.getErrorObject().getDescription());
      }

      DeviceAuthorizationSuccessResponse successResponse = response.toSuccessResponse();

      Map<String, List<String>> customParams = new HashMap<>();
      //customParams.put("deviceId", Collections.singletonList(instanceUUID));
      //customParams.put("algorithm", Collections.singletonList(hashAlgorithm));

      DeviceCodeGrant grant = new DeviceCodeGrant(successResponse.getDeviceCode());
      TokenRequest request = new TokenRequest(deviceTokenURI, new ClientSecretBasic(clientID, secret), grant, scope, null, customParams);

      long maxLifetime = TimeUnit.SECONDS.toMillis(successResponse.getLifetime()) + System.currentTimeMillis();

      DeviceAuthenticationRequest authenticationRequest = new DeviceAuthenticationRequest(
        configuration.getTokenURI(),
        configuration.getAuthURI(),
        successResponse.getVerificationURI(),
        successResponse.getVerificationURIComplete(),
        successResponse.getUserCode().getValue(),
        maxLifetime,
        successResponse.getLifetime(),
        successResponse.getInterval()
      );

      logger.info("Scheduling code refresh to every {} seconds", successResponse.getInterval());
      CompletableFuture<DeviceToken> tokenFuture = CompletableFuture
        .supplyAsync(new TokenRequester(request, successResponse.getUserCode().getValue(),
          successResponse.getInterval(), maxLifetime), executor);

      tokenFuture.whenComplete((token, failure) -> {
        if (failure != null) {
          compositeCallback.failedAuthentication(authenticationRequest, failure);
        }
        compositeCallback.deviceAuthenticated(authenticationRequest, token);
      });

      return authenticationRequest;

    } catch (Exception e) {
      throw new RuntimeException("Could not handle device authentication request", e);
    }
  }

  private void appendCertificate(HTTPRequest httpRequest) {
    deviceIds((DeviceIdentityTypes.CERT.getClass()::isInstance)).findFirst()
      .ifPresent(cert -> certificate(httpRequest, cert));
  }

  private void certificate(HTTPRequest httpRequest, DeviceIdentifierProvider<DeviceIdentifier> cert) {
    if (cert.getIdentifier() instanceof CertificateIdentifier) {
      CertificateIdentifier certificate = (CertificateIdentifier) cert.getIdentifier();
      httpRequest.setClientX509Certificate(certificate.getClientCertificate());
      return;
    }
    logger.debug("Could not fetch device certificate, sending HTTP request without client certificate.");
  }

  private void append(Builder builder, DeviceIdentifierProvider<DeviceIdentifier> provider) {
    final DeviceIdentifier identifier = provider.getIdentifier();
    final String requestParamName = "deviceId." + provider.getIdentityType().getType();
    builder.customParameter(requestParamName, identifier.toString());
  }

  private <X extends DeviceIdentifier> Stream<DeviceIdentifierProvider<X>> deviceIds(Predicate<DeviceIdentifierType<?>> predicate) {
    return deviceIdentifierProviders.stream()
      .filter(idp -> predicate.test(idp.getIdentityType()))
      .map(idp -> (DeviceIdentifierProvider<X>) idp);
  }

  @Deactivate
  public void stop() {
    executor.shutdown();
  }

  @Reference(cardinality = ReferenceCardinality.AT_LEAST_ONE) // make sure setup callback is present
  public void addDeviceAuthenticationCallback(DeviceAuthenticationCallback callback) {
    callbacks.add(callback);
  }

  public void removeDeviceAuthenticationCallback(DeviceAuthenticationCallback callback) {
    callbacks.remove(callback);
  }

  @Reference(cardinality = ReferenceCardinality.AT_LEAST_ONE)
  public void addDeviceIdentifierProvider(DeviceIdentifierProvider<?> identifierProvider) {
    deviceIdentifierProviders.add(identifierProvider);
  }

  public void removeDeviceIdentifierProvider(DeviceIdentifierProvider<?> identifierProvider) {
    deviceIdentifierProviders.remove(identifierProvider);
  }

  class TokenRequester implements Supplier<DeviceToken> {

    private final TokenRequest tokenRequest;
    private final String usercode;
    private final long interval;
    private final long maxLifetime;
    private Logger logger = LoggerFactory.getLogger(TokenRequester.class);

    public TokenRequester(TokenRequest tokenRequest, String usercode, long interval, long maxLifetime) {
      this.tokenRequest = tokenRequest;
      this.usercode = usercode;
      this.interval = interval;
      this.maxLifetime = maxLifetime;
    }

    @Override
    public DeviceToken get() {
      while (System.currentTimeMillis() < maxLifetime) {
        try {
          final HTTPRequest httpRequest = tokenRequest.toHTTPRequest();
          appendCertificate(httpRequest);
          HTTPResponse httpResponse = httpRequest.send();

          if (httpResponse.getStatusCode() == 400) {
            logger.debug("User did not complete operation with code {}", usercode);
            sleep();
          }

          if (httpResponse.indicatesSuccess()) {
            logger.info("Authentication for code {} completed", usercode);
            OIDCTokenResponse tokenResponse = OIDCTokenResponse.parse(httpResponse);
            AccessToken accessToken = tokenResponse.getTokens().getAccessToken();
            RefreshToken refreshToken = tokenResponse.getTokens().getRefreshToken();
            //JWT idToken = tokenResponse.getOIDCTokens().getIDToken();
            return new DeviceToken(
              accessToken.getValue(), refreshToken.getValue(), accessToken.getLifetime()
            );
          }
        } catch (Exception e) {
          throw new RuntimeException("Could not retrieve token", e);
        }
      }

      return null;
    }

    private void sleep() {
      try {
        logger.trace("Sleep a bit");
        Thread.sleep(TimeUnit.SECONDS.toMillis(interval));
      } catch (InterruptedException e) {
        logger.warn("Could not complete operation", e);
        Thread.currentThread().interrupt();
      }
    }
  }

}
