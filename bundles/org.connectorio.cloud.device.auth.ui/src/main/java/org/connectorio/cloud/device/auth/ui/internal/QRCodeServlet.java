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

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.io.IOException;
import java.net.URI;
import java.util.UUID;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.connectorio.cloud.device.auth.DeviceAuthenticationRequest;
import org.connectorio.cloud.device.auth.ui.PairingRegistry;

public class QRCodeServlet extends HttpServlet {

  private final PairingRegistry registry;

  public QRCodeServlet(PairingRegistry registry) {
    this.registry = registry;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    String id = req.getParameter("id");
    if (id == null || id.trim().length() != 36) {
      return;
    }

    String uri = registry.get(UUID.fromString(id)).map(DeviceAuthenticationRequest::getCompleteUri)
      .map(URI::toString)
      .orElse(null);

    if (uri == null) {
      return;
    }

    QRCodeWriter writer = new QRCodeWriter();
    try {
      BitMatrix bitMatrix = writer.encode(uri, BarcodeFormat.QR_CODE, 400, 400);

      resp.setHeader("Content-Type", "image/png");
      MatrixToImageWriter.writeToStream(bitMatrix, "PNG", resp.getOutputStream());
    } catch (WriterException e) {
      return;
    }
  }


}
