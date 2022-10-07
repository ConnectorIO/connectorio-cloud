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
package org.connectorio.cloud.service.mqtt.aws.internal;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * Structure of message sent to iot core.
 */
@JsonInclude(Include.NON_NULL)
public class EventObject {

  private String name;
  private String[] tags;
  private StateObject state;
  private Long timestamp;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String[] getTags() {
    return tags;
  }

  public void setTags(String[] tags) {
    this.tags = tags;
  }

  public StateObject getState() {
    return state;
  }

  public void setState(StateObject state) {
    this.state = state;
  }

  public Long getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(Long timestamp) {
    this.timestamp = timestamp;
  }

  @JsonInclude(Include.NON_NULL)
  static class StateObject {
    private String receiveType;
    private Object received;
    private Object formatted;

    public String getReceiveType() {
      return receiveType;
    }

    public void setReceiveType(String receiveType) {
      this.receiveType = receiveType;
    }

    public Object getReceived() {
      return received;
    }

    public void setReceived(Object received) {
      this.received = received;
    }

    public Object getFormatted() {
      return formatted;
    }

    public void setFormatted(Object formatted) {
      this.formatted = formatted;
    }
  }

}
