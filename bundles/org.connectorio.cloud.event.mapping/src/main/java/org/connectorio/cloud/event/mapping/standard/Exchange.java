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
package org.connectorio.cloud.event.mapping.standard;

import org.connectorio.cloud.event.mapping.Operation;
import org.connectorio.cloud.event.mapping.participant.Participant;

/**
 * Definition of operation which involves kind of two-way communication.
 * Namely a producer might expect an action from subscriber or reports update to subscriber in reply
 * to dispatched command.
 */
public class Exchange implements Operation {

  private final Participant producer;
  private final Participant subscriber;

  public Exchange(Participant producer, Participant subscriber) {
    this.producer = producer;
    this.subscriber = subscriber;
  }

  @Override
  public Participant producer() {
    return producer;
  }

  @Override
  public Participant subscriber() {
    return subscriber;
  }

  @Override
  public String getNamespace() {
    return code(producer) + "2" + code(subscriber);
  }

  private static String code(Participant participant) {
    return participant.isCloud() ? "c" /*cloud*/ : "d" /*device*/;
  }

}
