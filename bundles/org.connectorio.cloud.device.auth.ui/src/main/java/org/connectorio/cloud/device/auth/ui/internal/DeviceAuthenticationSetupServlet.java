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
package org.connectorio.cloud.device.auth.ui.internal;

import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.connectorio.cloud.device.auth.DeviceAuthenticationCallback;
import org.connectorio.cloud.device.auth.DeviceAuthenticationRequest;
import org.connectorio.cloud.device.auth.DeviceAuthenticator;
import org.connectorio.cloud.device.auth.DeviceToken;
import org.connectorio.cloud.device.auth.ui.PairingRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeviceAuthenticationSetupServlet extends HttpServlet implements DeviceAuthenticationCallback {

  private final Logger logger = LoggerFactory.getLogger(DeviceAuthenticationSetupServlet.class);
  private final PairingRegistry registry;
  private final DeviceAuthenticator authenticator;

  public DeviceAuthenticationSetupServlet(PairingRegistry registry, DeviceAuthenticator authenticator) {
    this.registry = registry;
    this.authenticator = authenticator;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    String id = req.getParameter("id");
    if (id != null && id.trim().length() == 36) {
      UUID requestId = UUID.fromString(id);
      Optional<DeviceAuthenticationRequest> request = registry.get(requestId);

      if (request.isPresent()) {
        DeviceAuthenticationRequest authenticationRequest = request.get();
        if (System.currentTimeMillis() >= authenticationRequest.getLifetime()) {
          registry.remove(requestId);
          resp.getWriter().println("<head><meta http-equiv=\"refresh\" content=\"10; url=/device/auth\"></head>");
          resp.getWriter().println("<h1>Request expired</h1>");
          return;
        }

        resp.getWriter().println("<head><meta http-equiv=\"refresh\" content=\"" + authenticationRequest.getInterval() + "; url=/device/auth/?id=" + id + "\"></head>");
        resp.getWriter().println("<pre>");
        resp.getWriter().println("In order to complete setup process scan below code and follow instructions:");
        resp.getWriter().println("<img src='/device/qrcode?id=" + id + "' width='200px' height='200px' />");
        resp.getWriter().println("You can also go to URI: " + authenticationRequest.getVerificationUri());
        resp.getWriter().println("And enter code: <h1>" + authenticationRequest.getUserCode() + "</h1>");
        resp.getWriter().println("This request is valid until: " + new Date(authenticationRequest.getLifetime()) + ".");
        resp.getWriter().println("</pre>");

        Map<UUID, DeviceAuthenticationRequest> requests = registry.all();
        resp.getWriter().println("<pre>");
        resp.getWriter().println("Other opened authentication requests");

        int index = 0, limit = requests.size();
        for (Map.Entry<UUID, DeviceAuthenticationRequest> entry : requests.entrySet()) {
          if (!entry.getKey().toString().equals(id)) {
            if (index > 1 && index + 1 < limit) {
              resp.getWriter().print("|");
            }

            resp.getWriter().print("<a href=\"/device/auth?id=" + entry.getKey() + "\">" + entry.getKey() + "</a>");
          }
          index++;
        }
        resp.getWriter().println("</pre>");

        return;
      }
    }

    DeviceAuthenticationRequest authenticationRequest = authenticator.authenticateDevice(
      this, "mqtt offline_access"
    );

    id = registry.add(authenticationRequest).toString();
    resp.sendRedirect("/device/auth?id=" + id);
  }

  @Override
  public void failedAuthentication(DeviceAuthenticationRequest request, Throwable failure) {
    logger.info("Authentication request failed due to error. Removing it from queue.", failure);
    removeRequest(request);
  }

  @Override
  public void deviceAuthenticated(DeviceAuthenticationRequest request, DeviceToken deviceToken) {
    logger.info("Authentication request succeed.");
    removeRequest(request);
  }

  private void removeRequest(DeviceAuthenticationRequest request) {
    Optional<UUID> requestId = registry.all().entrySet().stream().filter(e -> e.getValue().equals(request))
      .map(Map.Entry::getKey).findFirst();
    requestId.ifPresent(registry::remove);
  }

}
