package org.connectorio.cloud.service.proxy.co7io.internal;

import java.util.HashMap;

public class StatesEvent extends HashMap<String, Object> implements TextEvent {

  public StatesEvent(String item, State state) {
    put(item, state);
  }

}
