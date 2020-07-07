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

import static de.openknowledge.projects.greet.ServiceVerticle.OPERATION_SERVICE_GET_GREETING;
import static de.openknowledge.projects.greet.ServiceVerticle.OPERATION_SERVICE_GET_MESSAGE;
import static de.openknowledge.projects.greet.ServiceVerticle.OPERATION_SERVICE_UPDATE_GREETING;

import java.util.Arrays;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.Status;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.api.RequestParameters;
import io.vertx.ext.web.api.contract.RouterFactoryOptions;
import io.vertx.ext.web.api.contract.openapi3.OpenAPI3RouterFactory;
import io.vertx.ext.web.handler.StaticHandler;

public class HttpServerVerticle extends AbstractVerticle {

  private HttpServer server;

  @Override
  public void start() {
    OpenAPI3RouterFactory.create(this.vertx, "openapi.yaml", ar -> {
      OpenAPI3RouterFactory routerFactory = ar.result();
      routerFactory.setOptions(new RouterFactoryOptions().setMountResponseContentTypeHandler(true));
      addApiOperations(routerFactory);

      Router router = routerFactory.getRouter();
      addErrorHandler(router);
      addHealthChecks(router);
      addOpenapi(router);
      addStaticContent(router);

      vertx.createHttpServer().requestHandler(router).listen(8080);
    });

    System.out.println("HttpServerVerticle started!");
  }

  private void addApiOperations(final OpenAPI3RouterFactory routerFactory) {
    routerFactory.addHandlerByOperationId("greetSomeone", routingContext -> {
      RequestParameters requestParameters = routingContext.get("parsedParameters");
      String name = requestParameters.pathParameter("name").getString();

      vertx.eventBus().send(OPERATION_SERVICE_GET_MESSAGE, new JsonObject().put("name", name), objectMessage -> {
        GreetDTO message = new GreetDTO(objectMessage.result().body().toString());
        routingContext.response()
            .setStatusCode(200)
            .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
            .end(Json.encode(message));
      });
    });

    routerFactory.addHandlerByOperationId("greetTheWorld", routingContext -> {
      vertx.eventBus().send(OPERATION_SERVICE_GET_MESSAGE, new JsonObject().put("name", "World"), objectMessage -> {
        GreetDTO message = new GreetDTO(objectMessage.result().body().toString());
        routingContext.response()
            .setStatusCode(200)
            .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
            .end(Json.encode(message));
      });
    });

    routerFactory.addHandlerByOperationId("getGreeting", routingContext -> {
      vertx.eventBus().send(OPERATION_SERVICE_GET_GREETING, new JsonObject(), objectMessage -> {
        GreetingDTO greeting = new GreetingDTO(objectMessage.result().body().toString());
        routingContext.response()
            .setStatusCode(200)
            .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
            .end(Json.encode(greeting));
      });
    });

    routerFactory.addHandlerByOperationId("updateGreeting", routingContext -> {
      RequestParameters requestParameters = routingContext.get("parsedParameters");
      GreetingDTO greeting = requestParameters.body().getJsonObject().mapTo(GreetingDTO.class);
      vertx.eventBus().send(OPERATION_SERVICE_UPDATE_GREETING, greeting.getGreeting());
      routingContext.response().setStatusCode(204).setStatusMessage("NO_CONTENT").end();
    });
  }

  private void addErrorHandler(final Router router) {
    router.errorHandler(400, routingContext -> {
      JsonObject error = new JsonObject()
          .put("message", (routingContext.failure() != null) ? routingContext.failure().getMessage() : "Validation Exception");
      routingContext.response()
          .setStatusCode(400)
          .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
          .end(error.encode());
    });

    router.errorHandler(404, routingContext -> {
      JsonObject error = new JsonObject()
          .put("message", (routingContext.failure() != null) ? routingContext.failure().getMessage() : "Not Found");
      routingContext.response()
          .setStatusCode(404)
          .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
          .end(error.encode());
    });

    router.errorHandler(500, routingContext ->
        routingContext.response()
            .setStatusCode(500)
            .putHeader(HttpHeaders.CONTENT_TYPE, "plain/text")
            .end(routingContext.failure() + "\n" + Arrays.toString(routingContext.failure().getStackTrace())));
  }

  private void addHealthChecks(final Router router) {
    HealthCheckHandler handler = HealthCheckHandler.create(vertx);
    router.get("/health").handler(handler);

    HealthCheckHandler livenessHandler = HealthCheckHandler.create(vertx);
    router.get("/health/live").handler(livenessHandler);

    HealthCheckHandler readinessHandler = HealthCheckHandler.create(vertx);
    router.get("/health/ready").handler(readinessHandler);

    addAliveHealthCheck(handler);
    addAliveHealthCheck(livenessHandler);

    addReadyHealthCheck(handler);
    addReadyHealthCheck(readinessHandler);
  }

  private void addAliveHealthCheck(final HealthCheckHandler handler) {
    handler.register("alive", future -> future.complete(Status.OK()));
  }

  private void addReadyHealthCheck(final HealthCheckHandler handler) {
    handler.register("ready", future -> future.complete(Status.OK()));
  }

  private void addOpenapi(final Router router) {
    router.get("/openapi").handler(StaticHandler.create("openapi.yaml"));
  }

  private void addStaticContent(final Router router) {
    router.route("/").handler(StaticHandler.create());
    router.route("/swagger-ui/*").handler(StaticHandler.create("webroot/swagger-ui"));
  }

  @Override
  public void stop() {
    this.server.close();
  }
}