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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.DateTimeException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.measure.Unit;
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
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(service = EventSubscriber.class)
public class MqttItemStateEventListener implements EventSubscriber {

  private final Logger logger = LoggerFactory.getLogger(MqttItemStateEventListener.class);

  private final AwsPublisher publisher;
  private final ItemRegistry itemRegistry;
  private final LocaleProvider localeProvider;
  private final ObjectMapper mapper;
  private final BundleContext bundleContext;

  @Activate
  public MqttItemStateEventListener(BundleContext bundleContext, @Reference AwsPublisher publisher, @Reference ItemRegistry itemRegistry, @Reference LocaleProvider localeProvider) {
    this.bundleContext = bundleContext;
    this.publisher = publisher;
    this.itemRegistry = itemRegistry;
    this.localeProvider = localeProvider;

    mapper = new ObjectMapper();
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
    if (publisher != null) {
      if (event instanceof ItemStateChangedEvent) {
        ItemStateChangedEvent item = (ItemStateChangedEvent) event;
        try {
          String displayState = getDisplayState(itemRegistry.getItem(item.getItemName()), localeProvider.getLocale(), item.getItemState());

          Map<String, Object> stateMap = new LinkedHashMap<>();
          stateMap.put("timestamp", System.currentTimeMillis());
          stateMap.put("item", item.getItemName());
          stateMap.put("state", "" + item.getItemState());
          stateMap.put("displayState", displayState);

          Map<String, Object> message = new LinkedHashMap<>();
          message.put("type", "state");
          message.put("state", stateMap);

          publish(message);
        } catch (ItemNotFoundException e) {
          logger.warn("Received event for item called '{}' which is gone", item.getItemName(), e);
        }
      } else {
        Map<String, Object> eventMap = new LinkedHashMap<>();
        eventMap.put("timestamp", System.currentTimeMillis());
        eventMap.put("topic", event.getTopic());

        try {
          Map<String, Object> payload = mapper.readValue(event.getPayload(), new TypeReference<>() {});
          eventMap.put("payload", payload);
        } catch (JsonProcessingException e) {
          logger.warn("Field to deserialize event payload", e);
        }

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("type", event.getType());
        message.put("event", eventMap);

        publish(message);
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

  private void publish(Map<String, Object> message) {
    try {
      String messageStr = mapper.writeValueAsString(message);
      publisher.publish(messageStr);
    } catch (JsonProcessingException e) {
      logger.warn("Failed to serialize event message", e);
    }
  }

}
