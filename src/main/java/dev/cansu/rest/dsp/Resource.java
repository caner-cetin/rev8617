package dev.cansu.rest.dsp;

import dev.cansu.camilla.CamillaClient;
import dev.cansu.camilla.CamillaClientManager;
import dev.cansu.camilla.version.CamillaVersionResponse;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;

@Path("/api/dsp")
public class Resource {

  @Inject
  CamillaClientManager clientManager;

  @Inject
  @ConfigProperty(name = "rev.version")
  String REV_VERSION;

  public record VersionResponse(String revVersion, String camillaVersion) {
  }

  public record ConnectRequest(String ip, int port) {
  }

  // todo: jwt
  @POST
  @Path("/{userId}/connect")
  @Consumes(MediaType.APPLICATION_JSON)
  public Uni<Void> connect(@PathParam("userId") String userId, ConnectRequest request) {
    return clientManager.connect(userId, request.ip(), request.port());
  }

  @POST
  @Path("/{userId}/disconnect")
  public Uni<Void> disconnect(@PathParam("userId") String userId) {
    clientManager.disconnect(userId);
    return Uni.createFrom().voidItem();
  }

  @GET
  @Path("/{userId}/version")
  @Produces(MediaType.APPLICATION_JSON)
  public Uni<VersionResponse> version(@PathParam("userId") String userId) {
    return clientManager.getVersion(userId)
      .ifNoItem().after(Duration.ofSeconds(5)).failWith(new WebApplicationException("request timed out", 504))
      .onItem().transform(camillaVersion -> new VersionResponse(REV_VERSION, camillaVersion))
      .onFailure().recoverWithItem(error -> {
        System.err.println("Error for user " + userId + ": " + error.getMessage());
        return new VersionResponse(REV_VERSION, "unknown");
      });
  }
}
