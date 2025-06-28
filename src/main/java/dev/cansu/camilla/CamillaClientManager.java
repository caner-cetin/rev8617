// Location: dev.cansu.camilla.CamillaClientManager.java

package dev.cansu.camilla;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class CamillaClientManager {

  private final Map<String, CamillaClient> activeClients = new ConcurrentHashMap<>();

  /**
   * Connects a user to their specified DSP. If already connected, it disconnects the old one first.
   *
   * @param userId A unique identifier for the user (e.g., username, session ID).
   * @param ip     The IP address of the DSP.
   * @param port   The port of the DSP.
   * @return A Uni that completes when the connection is established.
   */
  public Uni<Void> connect(String userId, String ip, int port) {
    if (activeClients.containsKey(userId)) {
      disconnect(userId);
    }

    CamillaClient newClient = new CamillaClient(v -> activeClients.remove(userId));

    activeClients.put(userId, newClient);
    return newClient.connect(ip, port)
      .onFailure().invoke(() -> {
        activeClients.remove(userId);
      });
  }

  /**
   * Disconnects a user's client.
   *
   * @param userId The user's unique identifier.
   */
  public void disconnect(String userId) {
    CamillaClient client = activeClients.remove(userId);
    if (client != null) {
      try {
        client.disconnect();
      } catch (IOException e) {
        System.err.println("Error while disconnecting client for user " + userId + ": " + e.getMessage());
      }
    }
  }

  /**
   * Gets a command-ready client for a given user.
   *
   * @param userId The user's unique identifier.
   * @return A Uni containing the client, or a failure if not connected.
   */
  private Uni<CamillaClient> getClient(String userId) {
    CamillaClient client = activeClients.get(userId);
    if (client == null) {
      return Uni.createFrom().failure(new IllegalStateException("User " + userId + " is not connected to a DSP."));
    }
    return Uni.createFrom().item(client);
  }

  public Uni<String> getVersion(String userId) {
    return getClient(userId)
      .onItem().transformToUni(CamillaClient::getVersion);
  }
}