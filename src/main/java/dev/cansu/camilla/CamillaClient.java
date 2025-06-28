package dev.cansu.camilla;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.cansu.camilla.version.CamillaVersionResponse;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.subscription.UniEmitter;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.websocket.*;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@ClientEndpoint
public class CamillaClient {

  @Inject
  Event<CamillaVersionResponse> versionEvent;

  private Session session;
  private final Map<String, UniEmitter<? super JsonObject>> pendingRequests = new ConcurrentHashMap<>();
  private final Consumer<Void> onDisconnectCallback;
  private final CompletableFuture<Void> connectionFuture = new CompletableFuture<>();

  /**
   * @param onDisconnectCallback A function to call when this client disconnects,
   *                             so the manager can remove it.
   */
  public CamillaClient(Consumer<Void> onDisconnectCallback) {
    this.onDisconnectCallback = onDisconnectCallback;
  }

  @OnOpen
  public void onOpen(Session session) {
    this.session = session;
    connectionFuture.complete(null);
  }

  /**
   * @param message response from camilla
   * @throws RuntimeException when returned JSON is empty
   */
  @OnMessage
  public void onMessage(String message) throws RuntimeException {
    try {
      JsonObject responseJson = JsonParser.parseString(message).getAsJsonObject();
      String commandName = responseJson.keySet().iterator().next();
      UniEmitter<? super JsonObject> emitter = pendingRequests.remove(commandName);
      if (emitter != null) {
        emitter.complete(responseJson.getAsJsonObject(commandName));
      }
    } catch (Exception e) {
      System.err.println("error parsing message: " + message);
    }
  }

  @OnClose
  public void onClose(Session session, CloseReason reason) {
    // notify the manager that this client is dead
    onDisconnectCallback.accept(null);
    // fail all pending requests
    pendingRequests.forEach((cmd, emitter) -> emitter.fail(new IOException("connection closed")));
    pendingRequests.clear();
    // fail the connection future if it was still pending
    connectionFuture.completeExceptionally(new IOException("connection closed during connect."));
  }

  @OnError
  public void onError(Session session, Throwable throwable) {
    // todo: proper logging
    connectionFuture.completeExceptionally(throwable);
  }

  public void disconnect() throws IOException {
    if (session != null && session.isOpen()) {
      session.close();
    }
  }

  public Uni<Void> connect(String ip, int port) {
    try {
      WebSocketContainer container = ContainerProvider.getWebSocketContainer();
      URI uri = new URI(String.format("ws://%s:%s", ip, port));
      System.out.println("Client: Attempting to connect to " + uri);
      // todo: Possibly blocking call in non-blocking context could lead to thread starvation
      container.connectToServer(this, uri);
      return Uni.createFrom().completionStage(this.connectionFuture);
    } catch (Exception e) {
      return Uni.createFrom().failure(e);
    }
  }

  public Uni<String> getVersion() {
    return sendRequest("GetVersion")
      .onItem().transform(json -> json.get("value").getAsString());
  }

  private Uni<JsonObject> sendRequest(String command) {
    if (session == null || !session.isOpen()) {
      return Uni.createFrom().failure(new IllegalStateException("Not connected."));
    }
    return Uni.createFrom().emitter(emitter -> {
      if (pendingRequests.containsKey(command)) {
        emitter.fail(new IllegalStateException("Command '" + command + "' is already in flight."));
        return;
      }
      pendingRequests.put(command, emitter);
      session.getAsyncRemote().sendText("\"" + command + "\"");
    });
  }
}