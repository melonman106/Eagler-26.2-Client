package net.lax1dude.eaglercraft.v1_8.profile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Splits Eagler v5 game-message frames used by the texture service. */
final class EaglerV5GameMessageFrames {

   private EaglerV5GameMessageFrames() {
   }

   static List<byte[]> split(final byte[] payload) throws IOException {
      if (payload == null || payload.length == 0) {
         throw new IOException("Empty Eagler v5 game-message frame");
      }
      if ((payload[0] & 0xFF) != 0xFF) {
         return Collections.singletonList(payload.clone());
      }
      Cursor input = new Cursor(payload, 1);
      int count = input.readVarInt();
      if (count < 0 || count > input.remaining() / 2) {
         throw new IOException("Invalid Eagler v5 packet bundle count: " + count);
      }
      List<byte[]> packets = new ArrayList<>(count);
      for (int i = 0; i < count; ++i) {
         int length = input.readVarInt();
         if (length <= 0 || length > input.remaining()) {
            throw new IOException("Invalid Eagler v5 packet length: " + length);
         }
         packets.add(input.readBytes(length));
      }
      if (input.remaining() != 0) {
         throw new IOException("Trailing bytes after Eagler v5 packet bundle");
      }
      return Collections.unmodifiableList(packets);
   }

   private static final class Cursor {
      private final byte[] data;
      private int index;

      private Cursor(final byte[] data, final int index) {
         this.data = data;
         this.index = index;
      }

      private int remaining() {
         return this.data.length - this.index;
      }

      private int readVarInt() throws IOException {
         int value = 0;
         for (int shift = 0; shift < 35; shift += 7) {
            if (this.remaining() == 0) {
               throw new IOException("Truncated Eagler v5 packet bundle VarInt");
            }
            int next = this.data[this.index++] & 0xFF;
            value |= (next & 0x7F) << shift;
            if ((next & 0x80) == 0) {
               if (value < 0) {
                  throw new IOException("Negative Eagler v5 packet bundle VarInt");
               }
               return value;
            }
         }
         throw new IOException("Eagler v5 packet bundle VarInt is too long");
      }

      private byte[] readBytes(final int length) throws IOException {
         if (length < 0 || length > this.remaining()) {
            throw new IOException("Truncated Eagler v5 packet bundle entry");
         }
         byte[] result = new byte[length];
         System.arraycopy(this.data, this.index, result, 0, length);
         this.index += length;
         return result;
      }
   }
}
