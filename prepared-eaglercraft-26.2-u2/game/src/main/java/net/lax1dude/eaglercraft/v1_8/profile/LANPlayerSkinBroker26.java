package net.lax1dude.eaglercraft.v1_8.profile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.network.Connection;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.LANPlayerSkinPayload26;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-thread texture exchange for integrated and relayed LAN worlds.
 * Ordinary public multiplayer connections are deliberately ineligible.
 */
public final class LANPlayerSkinBroker26 {

   private static final long WAITING_REQUEST_MILLIS = 5_000L;
   private static final int MAX_WAITING_REQUESTS = 256;
   private static final Map<MinecraftServer, ServerState> SERVERS = new WeakHashMap<>();

   private LANPlayerSkinBroker26() {
   }

   /** Accepts a skin announcement from the player represented by connection. */
   public static boolean announce(final MinecraftServer server, final Connection connection,
         final ServerPlayer player, final LANPlayerSkinPayload26 payload) {
      if (!isEligible(connection) || !isPlayer(server, player, payload) || !payload.isAnnounce()
            || payload.requestId() != 0 || !payload.senderId().equals(payload.targetId()) || !payload.hasSkin()) {
         return false;
      }
      ServerState state = state(server);
      state.connections.put(player.getUUID(), connection);
      Texture texture = new Texture(payload.skinPacket(), payload.capePacket());
      state.active.put(player.getUUID(), texture);
      respondToWaiting(server, state, player.getUUID(), texture);
      broadcast(server, player, payload);
      return true;
   }

   /** Answers a bounded request from an eligible LAN player. */
   public static boolean request(final MinecraftServer server, final Connection connection,
         final ServerPlayer requester, final LANPlayerSkinPayload26 payload) {
      if (!isEligible(connection) || !isPlayer(server, requester, payload) || !payload.isRequest()
            || payload.targetId().equals(requester.getUUID())) {
         return false;
      }
      ServerState state = state(server);
      state.connections.put(requester.getUUID(), connection);
      Texture texture = state.active.get(payload.targetId());
      if (texture != null) {
         send(requester, connection, LANPlayerSkinPayload26.response(requester.getUUID(), payload.targetId(),
               payload.requestId(), texture.skin, texture.cape));
         return true;
      }
      pruneWaiting(state, System.currentTimeMillis());
      WaitingKey key = new WaitingKey(requester.getUUID(), payload.targetId());
      if (state.waiting.size() < MAX_WAITING_REQUESTS || state.waiting.containsKey(key)) {
         state.waiting.put(key, new WaitingRequest(payload.requestId(), System.currentTimeMillis() + WAITING_REQUEST_MILLIS));
      }
      return true;
   }

   /** Dispatches a packet after the parent listener has decoded it. */
   public static boolean handle(final MinecraftServer server, final Connection connection,
         final ServerPlayer player, final LANPlayerSkinPayload26 payload) {
      if (payload == null) {
         return false;
      }
      return payload.isAnnounce() ? announce(server, connection, player, payload)
            : payload.isRequest() && request(server, connection, player, payload);
   }

   /** Sends all already-known active skins to a newly joined eligible LAN player. */
   public static void playerJoined(final MinecraftServer server, final Connection connection,
         final ServerPlayer player) {
      if (!isEligible(connection) || server == null || player == null) {
         return;
      }
      ServerState state = state(server);
      state.connections.put(player.getUUID(), connection);
      for (Map.Entry<UUID, Texture> entry : new ArrayList<>(state.active.entrySet())) {
         Texture texture = entry.getValue();
         send(player, connection, LANPlayerSkinPayload26.announce(entry.getKey(), texture.skin, texture.cape));
      }
   }

   /** Removes a player before it leaves the server so stale skin bytes cannot be reused. */
   public static void playerLeft(final MinecraftServer server, final ServerPlayer player) {
      if (server != null && player != null) {
         ServerState state = SERVERS.get(server);
         if (state != null) {
            state.active.remove(player.getUUID());
            state.connections.remove(player.getUUID());
            state.waiting.entrySet().removeIf(entry -> entry.getKey().contains(player.getUUID()));
         }
      }
   }

   /** Drops all per-server texture state after the server has stopped. */
   public static void serverStopped(final MinecraftServer server) {
      if (server != null) {
         SERVERS.remove(server);
      }
   }

   private static void broadcast(final MinecraftServer server, final ServerPlayer sender,
         final LANPlayerSkinPayload26 payload) {
      if (server == null || sender == null) {
         return;
      }
      ServerState state = state(server);
      for (ServerPlayer recipient : new ArrayList<>(server.getPlayerList().getPlayers())) {
         if (recipient != sender) {
            Connection connection = state.connections.get(recipient.getUUID());
            if (isEligible(connection) && recipient.connection != null) {
               recipient.connection.send(new ClientboundCustomPayloadPacket(payload));
            }
         }
      }
   }

   private static void send(final ServerPlayer player, final Connection connection,
         final LANPlayerSkinPayload26 payload) {
      if (player != null && player.connection != null && isEligible(connection)) {
         player.connection.send(new ClientboundCustomPayloadPacket(payload));
      }
   }

   private static void respondToWaiting(final MinecraftServer server, final ServerState state, final UUID targetId,
         final Texture texture) {
      long now = System.currentTimeMillis();
      var iterator = state.waiting.entrySet().iterator();
      while (iterator.hasNext()) {
         Map.Entry<WaitingKey, WaitingRequest> entry = iterator.next();
         WaitingKey key = entry.getKey();
         WaitingRequest request = entry.getValue();
         if (request.expiresAt <= now || key.targetId.equals(targetId)) {
            iterator.remove();
            if (key.targetId.equals(targetId)) {
               ServerPlayer requester = server.getPlayerList().getPlayer(key.requesterId);
               Connection connection = state.connections.get(key.requesterId);
               send(requester, connection, LANPlayerSkinPayload26.response(key.requesterId, targetId, request.requestId,
                     texture.skin, texture.cape));
            }
         }
      }
   }

   private static void pruneWaiting(final ServerState state, final long now) {
      state.waiting.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
   }

   private static boolean isPlayer(final MinecraftServer server, final ServerPlayer player,
         final LANPlayerSkinPayload26 payload) {
      return server != null && player != null && payload != null && player.getUUID().equals(payload.senderId());
   }

   private static boolean isEligible(final Connection connection) {
      return connection != null && (connection.isMemoryConnection() || connection.isRelayedLANConnection());
   }

   private static ServerState state(final MinecraftServer server) {
      if (server == null) {
         throw new IllegalArgumentException("LAN skin server is required");
      }
      return SERVERS.computeIfAbsent(server, ignored -> new ServerState());
   }

   private static final class ServerState {
      final Map<UUID, Texture> active = new HashMap<>();
      final Map<UUID, Connection> connections = new HashMap<>();
      final Map<WaitingKey, WaitingRequest> waiting = new HashMap<>();
   }

   private record WaitingKey(UUID requesterId, UUID targetId) {
      private boolean contains(final UUID playerId) {
         return requesterId.equals(playerId) || targetId.equals(playerId);
      }
   }

   private static final class WaitingRequest {
      final int requestId;
      final long expiresAt;

      WaitingRequest(final int requestId, final long expiresAt) {
         this.requestId = requestId;
         this.expiresAt = expiresAt;
      }
   }

   private static final class Texture {
      final byte[] skin;
      final byte[] cape;

      Texture(final byte[] skin, final byte[] cape) {
         this.skin = skin.clone();
         this.cape = cape.clone();
      }
   }
}
