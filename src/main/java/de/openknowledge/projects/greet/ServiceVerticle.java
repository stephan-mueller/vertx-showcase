/*
 * Copyright (C) open knowledge GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 */
package de.openknowledge.projects.greet;

import io.vertx.config.ConfigRetriever;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;

public class ServiceVerticle extends AbstractVerticle {

  public static final String OPERATION_SERVICE_GET_GREETING = "service/getGreeting";
  public static final String OPERATION_SERVICE_GET_MESSAGE = "service/getMessage";
  public static final String OPERATION_SERVICE_UPDATE_GREETING = "service/updateGreeting";

  private GreetingApplicationService service;

  @Override
  public void start() {
    ConfigRetriever retriever = ConfigRetriever.create(vertx);
    retriever.getConfig(json -> {
      JsonObject properties = json.result();
      String greeting = properties.getString("app.greeting");
      service = new GreetingApplicationService(greeting);
    });

    EventBus eventBus = vertx.eventBus();

    eventBus.consumer(OPERATION_SERVICE_GET_GREETING).handler(objectMessage -> {
      String greeting = service.getGreeting();
      objectMessage.reply(greeting);
    });

    eventBus.consumer(OPERATION_SERVICE_GET_MESSAGE).handler(objectMessage -> {
      JsonObject body = (JsonObject) objectMessage.body();
      String message = service.getMessage(body.getString("name"));
      objectMessage.reply(message);
    });

    eventBus.consumer(OPERATION_SERVICE_UPDATE_GREETING).handler(objectMessage -> {
      String greeting = (String) objectMessage.body();
      service.updateGreeting(greeting);
    });

    System.out.println("ServiceVerticle started!");
  }
}
