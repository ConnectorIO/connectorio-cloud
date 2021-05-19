package org.connectorio.cloud.service.proxy.co7io.internal;

class Event implements TextEvent {

  public final String topic;
  public final String payload;
  public final String type;

  Event(String topic, String payload, String type) {
    this.topic = topic;
    this.payload = payload;
    this.type = type;
  }

}