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
package org.connectorio.cloud.service.mqtt.aws.internal.servlet;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.connectorio.cloud.service.CloudService;
import org.connectorio.cloud.service.mqtt.aws.internal.AwsMqttService;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;

@Component
public class AwsStatusServlet extends HttpServlet {

  public static final String AWS_SVC_FILTER = "(" + Constants.SERVICE_PID + "=" + AwsMqttService.SERVICE_PID + ")";

  private CloudService service; // NOSONAR
  private final HttpService httpService;

  @Activate
  public AwsStatusServlet(@Reference HttpService httpService)
    throws ServletException, NamespaceException {
    this.httpService = httpService;
    this.httpService.registerServlet("/service/aws", this, null, httpService.createDefaultHttpContext());
  }

  @Deactivate
  public void deactivate() {
    try {
      httpService.unregister("/service/aws");
    } catch (IllegalArgumentException e) {
      // happens if we already failed to start
    }
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    resp.getWriter().println("<pre>");

    resp.getWriter().println("<h2>AWS service</h2>");
    if (service == null) {
      resp.getWriter().println("Service is not active");
    } else {
      resp.getWriter().println("MQTT connection: " + service.getState().getState());
    }

    resp.getWriter().println("</pre>");
  }

  @Reference(policy = ReferencePolicy.DYNAMIC, target = AWS_SVC_FILTER, cardinality = ReferenceCardinality.OPTIONAL)
  public void setService(CloudService service) {
    this.service = service;
  }

  public void unsetService(CloudService service) {
    this.service = null;
  }
}
