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
package org.connectorio.cloud.device.auth.ui.internal;

import java.util.concurrent.atomic.AtomicReference;
import javax.servlet.ServletException;
import org.connectorio.cloud.device.auth.DeviceAuthentication;
import org.connectorio.cloud.device.auth.DeviceAuthenticator;
import org.connectorio.cloud.device.auth.ui.PairingRegistry;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class SetupService {

  private final Logger logger = LoggerFactory.getLogger(SetupService.class);
  private PairingRegistry registry;
  private HttpService httpService;
  private DeviceAuthenticator authenticator;
  private DeviceAuthentication authentication;

  @Activate
  void activate(BundleContext context) throws ServletException, NamespaceException {
    if (authentication == null) {
      logger.error("Authenticator is not in place. Device authorization will not work.");
    }
    httpService.registerServlet("/device/auth", new DeviceAuthenticationSetupServlet(registry, authenticator), null, httpService.createDefaultHttpContext());
    httpService.registerServlet("/device/qrcode", new QRCodeServlet(registry), null, httpService.createDefaultHttpContext());
    httpService.registerServlet("/device", new AuthenticationServlet(authentication), null, httpService.createDefaultHttpContext());
  }

  @Deactivate
  void deactivate() {
    httpService.unregister("/device/auth");
    httpService.unregister("/device/qrcode");
    httpService.unregister("/device");
  }

  @Reference
  public void setHttpService(HttpService httpService) {
    this.httpService = httpService;
  }

  @Reference
  public void setAuthenticator(DeviceAuthenticator authenticator) {
    this.authenticator = authenticator;
  }

  @Reference(cardinality = ReferenceCardinality.OPTIONAL)
  public void setAuthentication(DeviceAuthentication authentication) {
    this.authentication = authentication;
  }

  @Reference
  public void setRegistry(PairingRegistry registry) {
    this.registry = registry;
  }

  public void unsetRegistry(PairingRegistry registry) {
    this.registry = null;
  }

}
