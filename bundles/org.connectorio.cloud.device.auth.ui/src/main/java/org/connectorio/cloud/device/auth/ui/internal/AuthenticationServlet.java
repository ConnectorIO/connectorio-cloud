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
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.connectorio.cloud.device.auth.DeviceAuthentication;

public class AuthenticationServlet extends HttpServlet {

  private final DeviceAuthentication authentication;

  public AuthenticationServlet(DeviceAuthentication authentication) {
    this.authentication = authentication;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    resp.getWriter().println("<pre>");

    if (authentication == null) {
      resp.getWriter().println("<h2>Device unauthenticated</h2>");
      resp.getWriter().println("<a href=\"/device/auth\">Authenticate</a>");
    } else {
      resp.getWriter().println("<h2>Device authenticated</h2>");
      resp.getWriter().println("Device ID: " + authentication.getDeviceId());
      resp.getWriter().println("Organization ID: " + authentication.getOrganizationId());
      resp.getWriter().println("Expiration: " + authentication.getExpirationTime());
      resp.getWriter().println("<a href=\"/device/auth\">Reauthenticate</a>");
    }

    /*
    resp.getWriter().println("<h2>MQTT connection</h2>");
    ConnectorIoMqttConnection connection = this.mqttConnection.get();
    if (connection == null) {
      resp.getWriter().println("Connection is not ready yet.");
    } else {
      resp.getWriter().println("Connection is in place.");

      resp.getWriter().print("State is: ");
      switch (connection.getClient().connectionState()) {
        case CONNECTED:
          resp.getWriter().println("<b>connected</b>");
          break;
        case CONNECTING:
          resp.getWriter().println("<b>connecting</b>");
          break;
        case DISCONNECTED:
          resp.getWriter().println("<b>disconnected</b>");
          break;
      }
    }
    */

    resp.getWriter().println("</pre>");
  }
}
