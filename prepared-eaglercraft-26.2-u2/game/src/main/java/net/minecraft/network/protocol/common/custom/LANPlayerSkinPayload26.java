package net.minecraft.network.protocol.common.custom;

import java.util.UUID;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/**
 * Bounded Minecraft custom-payload envelope for skins on an integrated or
 * relayed LAN connection. The byte arrays use the existing Eagler profile
 * packets rather than introducing a second skin format.
 */
public record LANPlayerSkinPayload26(int action, UUID senderId, UUID targetId, int requestId,
      byte[] skinPacket, byte[] capePacket) implements CustomPacketPayload {

   public static final int ANNOUNCE = 0;
   public static final int REQUEST = 1;
   public static final int RESPONSE = 2;

   public static final int MAX_REQUEST_ID = 0x3FFF;
   public static final int MAX_SKIN_PACKET_SIZE = 12290;
   public static final int MAX_CAPE_PACKET_SIZE = 1174;

   public static final Type<LANPlayerSkinPayload26> TYPE = new Type<>(Identifier.parse("eagler:lan_player_skin"));
   public static final StreamCodec<FriendlyByteBuf, LANPlayerSkinPayload26> STREAM_CODEC = CustomPacketPayload.codec(
         (value, output) -> {
            value.validate();
            output.writeByte(value.action);
            output.writeUUID(value.senderId);
            output.writeUUID(value.targetId);
            output.writeVarInt(value.requestId);
            output.writeByteArray(value.skinPacket);
            output.writeByteArray(value.capePacket);
         },
         input -> new LANPlayerSkinPayload26(input.readUnsignedByte(), input.readUUID(), input.readUUID(),
               input.readVarInt(), input.readByteArray(MAX_SKIN_PACKET_SIZE), input.readByteArray(MAX_CAPE_PACKET_SIZE))
   );

   public LANPlayerSkinPayload26 {
      if (senderId == null || targetId == null) {
         throw new IllegalArgumentException("LAN skin sender and target are required");
      }
      if (action < ANNOUNCE || action > RESPONSE) {
         throw new IllegalArgumentException("Unknown LAN skin action " + action);
      }
      if (requestId < 0 || requestId > MAX_REQUEST_ID) {
         throw new IllegalArgumentException("LAN skin request id is out of range");
      }
      if (!isSkinPacketShape(skinPacket) || !isCapePacketShape(capePacket)) {
         throw new IllegalArgumentException("Invalid Eagler LAN skin packet shape");
      }
      if (action == REQUEST && (skinPacket.length != 0 || capePacket.length != 0)) {
         throw new IllegalArgumentException("LAN skin requests cannot carry texture data");
      }
      skinPacket = skinPacket.clone();
      capePacket = capePacket.clone();
   }

   public static LANPlayerSkinPayload26 announce(final UUID playerId, final byte[] skin, final byte[] cape) {
      return new LANPlayerSkinPayload26(ANNOUNCE, playerId, playerId, 0, skin, cape);
   }

   public static LANPlayerSkinPayload26 request(final UUID requesterId, final UUID targetId, final int requestId) {
      return new LANPlayerSkinPayload26(REQUEST, requesterId, targetId, requestId, new byte[0], new byte[0]);
   }

   /** senderId is the requesting player; targetId is the player whose texture is returned. */
   public static LANPlayerSkinPayload26 response(final UUID requesterId, final UUID targetId, final int requestId,
         final byte[] skin, final byte[] cape) {
      return new LANPlayerSkinPayload26(RESPONSE, requesterId, targetId, requestId, skin, cape);
   }

   public boolean isAnnounce() {
      return action == ANNOUNCE;
   }

   public boolean isRequest() {
      return action == REQUEST;
   }

   public boolean isResponse() {
      return action == RESPONSE;
   }

   public boolean hasSkin() {
      return skinPacket.length != 0;
   }

   public boolean hasCape() {
      return capePacket.length != 0;
   }

   @Override
   public byte[] skinPacket() {
      return skinPacket.clone();
   }

   @Override
   public byte[] capePacket() {
      return capePacket.clone();
   }

   @Override
   public Type<LANPlayerSkinPayload26> type() {
      return TYPE;
   }

   private void validate() {
      if (senderId == null || targetId == null || action < ANNOUNCE || action > RESPONSE
            || requestId < 0 || requestId > MAX_REQUEST_ID || !isSkinPacketShape(skinPacket)
            || !isCapePacketShape(capePacket) || (action == REQUEST && (hasSkin() || hasCape()))) {
         throw new IllegalArgumentException("Invalid LAN skin payload");
      }
   }

   public static boolean isSkinPacketShape(final byte[] packet) {
      return packet != null && (packet.length == 0
            || (packet.length == 5 && packet[0] == 0x01)
            || (packet.length == MAX_SKIN_PACKET_SIZE && packet[0] == 0x02));
   }

   public static boolean isCapePacketShape(final byte[] packet) {
      return packet != null && (packet.length == 0
            || (packet.length == 5 && packet[0] == 0x01)
            || (packet.length == MAX_CAPE_PACKET_SIZE && packet[0] == 0x02));
   }
}
