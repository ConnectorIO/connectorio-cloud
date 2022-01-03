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
package org.connectorio.cloud.service.proxy.co7io.internal;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.WebSocket;
import java.net.http.WebSocket.Builder;
import java.time.DateTimeException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.measure.Unit;
import org.connectorio.cloud.device.auth.DeviceAuthentication;
import org.connectorio.cloud.service.CloudService;
import org.connectorio.cloud.service.CloudServiceState;
import org.connectorio.cloud.service.CloudServiceStates;
import org.connectorio.cloud.service.CloudServiceType;
import org.connectorio.cloud.service.proxy.CloudProxyConnection;
import org.connectorio.cloud.service.proxy.ProxyConnectionState;
import org.connectorio.cloud.service.proxy.co7io.internal.reconnect.ReconnectStrategy;
import org.connectorio.cloud.service.proxy.co7io.internal.reconnect.SimpleReconnectStrategy;
import org.connectorio.cloud.service.standard.NamedCloudServiceType;
import org.openhab.core.events.EventFilter;
import org.openhab.core.events.EventSubscriber;
import org.openhab.core.i18n.LocaleProvider;
import org.openhab.core.items.Item;
import org.openhab.core.items.ItemNotFoundException;
import org.openhab.core.items.ItemRegistry;
import org.openhab.core.items.events.ItemStateChangedEvent;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.transform.TransformationException;
import org.openhab.core.transform.TransformationHelper;
import org.openhab.core.types.StateDescription;
import org.openhab.core.types.StateOption;
import org.openhab.core.types.UnDefType;
import org.openhab.core.types.util.UnitUtils;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.framework.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(immediate = true, service = {CloudService.class, CloudProxyConnection.class, EventSubscriber.class},
  property = {
    Constants.SERVICE_PID + "=" + ConnectorioProxyService.SERVICE_PID,
    "service.config.label=ConnectorIO Proxy",
    "service.config.category=ConnectorIO Cloud",
    "service.config.description.uri=connectorio:proxy",
  })
public class ConnectorioProxyService implements CloudProxyConnection, EventSubscriber, ConnectionStateListener {

  public static final String SERVICE_PID = "org.connectorio.cloud.service.proxy.co7io";

  private final static String DEFAULT_HOST = "proxy.connectorio.cloud";
  private final static String DEFAULT_FORWARD_HOST = "127.0.0.1";
  private final static int DEFAULT_FORWARD_PORT = 8080;
  public static final int WEBSOCKET_MESSAGE_LIMIT = 65536;

  private final Logger logger = LoggerFactory.getLogger(ConnectorioProxyService.class);
  private final AtomicReference<ProxyConnectionState> state = new AtomicReference<>(ProxyConnectionState.DISCONNECTED);

  private final String host;
  private final int port;
  private final boolean secure;
  private final String forwardHost;
  private final int forwardPort;
  private final DeviceAuthentication authentication;
  private final BundleContext bundleContext;
  private final ItemRegistry itemRegistry;
  private final LocaleProvider localeProvider;
  private final ReconnectStrategy reconnectStrategy;

  private WebSocket connection;
  private WebSocketListener listener;

  @Activate
  public ConnectorioProxyService(@Reference DeviceAuthentication authentication, @Reference ConfigurationAdmin configurationAdmin,
    BundleContext bundleContext, @Reference ItemRegistry itemRegistry, @Reference LocaleProvider localeProvider) throws IOException {
    this.authentication = authentication;
    this.bundleContext = bundleContext;
    this.itemRegistry = itemRegistry;
    this.localeProvider = localeProvider;
    Configuration configuration = configurationAdmin.getConfiguration(SERVICE_PID);
    String serverHost = resolveOption(configuration, "host", Object::toString, () -> DEFAULT_HOST);
    secure = resolveOption(configuration, "secure", (v -> Boolean.parseBoolean(v.toString())), () -> Boolean.TRUE);
    port = resolveOption(configuration, "port", (v -> Integer.parseInt(v.toString())), () -> secure ? 443 : 80);

    forwardHost = resolveOption(configuration, "forwardHost", Object::toString, () -> DEFAULT_FORWARD_HOST);
    forwardPort = resolveOption(configuration, "forwardPort", (v -> Integer.parseInt(v.toString())), () -> DEFAULT_FORWARD_PORT);

    // hm.. can it become stale once device is re-authenticated to different organization?
    //String clientId = authentication.getOrganizationId() + "." + authentication.getDeviceId();
    host = authentication.getOrganizationId() + "-" + authentication.getDeviceId() + "." + serverHost;

    reconnectStrategy = new SimpleReconnectStrategy(this);
    reconnectStrategy.start();
  }

  @Override
  public CloudServiceType getServiceType() {
    return new NamedCloudServiceType("proxy");
  }

  @Override
  public CloudServiceState getState() {
    if (getClient() == null) {
      return CloudServiceStates.GONE;
    }

    return state.get();
  }

  public void connect() {
    if (authentication.getAccessToken() == null || authentication.getDeviceId() == null || authentication.getOrganizationId() == null) {
      logger.info("Authentication information is missing, please re-authenticate device");
      return;
    }

    Builder webSocketBuilder = HttpClient.newHttpClient().newWebSocketBuilder()
        .header("Authorization", authentication.getAccessToken());

    URI serverUri = URI.create((secure ? "wss://" : "ws://") + host + ":" + port + "/connect");
    logger.info("Launching WebSocket connection to {}", serverUri);
    ConnectionStateListener connectionListener = new CompositeConnectionStateListener(reconnectStrategy, this);
    listener = new WebSocketListener(forwardHost, forwardPort, HttpClient.newBuilder().version(Version.HTTP_1_1).build(), connectionListener);
    webSocketBuilder.buildAsync(serverUri, listener)
      .whenComplete((response, error) -> {
        if (error != null) {
          logger.error("Could not open web socket connection", error);
          return;
        }
        connection = response;
      });
  }

  private void sendItemStates() {
    for (Item item : itemRegistry.getAll()) {
      org.openhab.core.types.State state = item.getState();
      if (UnDefType.NULL == state || UnDefType.UNDEF == state) {
        logger.trace("Ignore item {} state, not relevant {}", item.getName(), state);
        continue;
      }
      try {
        String displayState = getDisplayState(item, localeProvider.getLocale(), item.getState());
        logger.trace("Send item {} state {}", item.getName(), state);
        listener.send(new StatesEvent(item.getName(), new State("" + state, displayState)));
      } catch (Exception e) {
        logger.warn("Could not item {} initial state.", item.getName(), e);
      }
    }
  }

  private <T> T resolveOption(Configuration configuration, String key, Function<Object, T> mapping, Supplier<T> fallback) {
    return Optional.ofNullable(configuration)
      .map(Configuration::getProperties)
      .map(prop -> prop.get(key))
      .map(mapping)
      .orElseGet(fallback);
  }

  @Deactivate
  public void deactivate() {
    reconnectStrategy.shutdown();
    if (connection != null) {
      connection.sendClose(WebSocket.NORMAL_CLOSURE, "connection deactivation")
        .join(); // block until completion of disconnect request
      connection = null;
    }

    if (listener != null) {
      listener = null;
    }
  }

  @Override
  public DeviceAuthentication getDeviceAuthentication() {
    return authentication;
  }

  @Override
  public WebSocket getClient() {
    return connection;
  }

  @Override
  public Set<String> getSubscribedEventTypes() {
    return Set.of(EventSubscriber.ALL_EVENT_TYPES);
  }

  @Override
  public EventFilter getEventFilter() {
    return null;
  }

  @Override
  public void receive(org.openhab.core.events.Event event) {
    if (listener != null) {
      if (event instanceof ItemStateChangedEvent) {
        ItemStateChangedEvent item = (ItemStateChangedEvent) event;
        try {
          String displayState = getDisplayState(itemRegistry.getItem(item.getItemName()), localeProvider.getLocale(), item.getItemState());
          listener.send(new StatesEvent(item.getItemName(), new State("" + item.getItemState(), displayState)));
        } catch (ItemNotFoundException e) {
          logger.warn("Received event for item called '{}' which is gone", item.getItemName(), e);
        }
      } else {
        listener.send(new Event(event.getTopic(), event.getPayload(), event.getType()));
      }
    }
  }

  private String getDisplayState(Item item, Locale locale, org.openhab.core.types.State state) {
    StateDescription stateDescription = item.getStateDescription(locale);
    String displayState = state.toString();

    if (!(state instanceof UnDefType)) {
      if (stateDescription != null) {
        if (!stateDescription.getOptions().isEmpty()) {
          // Look for a state option with a label corresponding to the state
          for (StateOption option : stateDescription.getOptions()) {
            if (option.getValue().equals(state.toString()) && option.getLabel() != null) {
              displayState = option.getLabel();
              break;
            }
          }
        } else {
          // If there's a pattern, first check if it's a transformation
          String pattern = stateDescription.getPattern();
          if (pattern != null) {
            if (TransformationHelper.isTransform(pattern)) {
              try {
                displayState = TransformationHelper.transform(bundleContext, pattern, state.toString());
              } catch (NoClassDefFoundError ex) {
                // TransformationHelper is optional dependency, so ignore if class not found
                // return state as it is without transformation
              } catch (TransformationException e) {
                logger.warn("Failed transforming the state '{}' on item '{}' with pattern '{}': {}",
                    state, item.getName(), pattern, e.getMessage());
              }
            } else {
              // if it's not a transformation pattern, then it must be a format string

              if (state instanceof QuantityType) {
                QuantityType<?> quantityState = (QuantityType<?>) state;
                // sanity convert current state to the item state description unit in case it was
                // updated in the meantime. The item state is still in the "original" unit while the
                // state description will display the new unit:
                Unit<?> patternUnit = UnitUtils.parseUnit(pattern);
                if (patternUnit != null && !quantityState.getUnit().equals(patternUnit)) {
                  quantityState = quantityState.toUnit(patternUnit);
                }

                if (quantityState != null) {
                  state = quantityState;
                }
              } else if (state instanceof DateTimeType) {
                // Translate a DateTimeType state to the local time zone
                try {
                  state = ((DateTimeType) state).toLocaleZone();
                } catch (DateTimeException e) {
                }
              }

              // The following exception handling has been added to work around a Java bug with formatting
              // numbers. See http://bugs.sun.com/view_bug.do?bug_id=6476425
              // This also handles IllegalFormatConversionException, which is a subclass of
              // IllegalArgument.
              try {
                displayState = state.format(pattern);
              } catch (IllegalArgumentException e) {
                logger.warn("Exception while formatting value '{}' of item {} with format '{}': {}",
                  state, item.getName(), pattern, e);
                displayState = state.toString();
              }
            }
          }
        }
      }
    }

    return displayState;
  }

  @Override
  public void connected(WebSocket webSocket) {
    sendItemStates();
  }

  @Override
  public void disconnected(WebSocket webSocket) {
  }

}