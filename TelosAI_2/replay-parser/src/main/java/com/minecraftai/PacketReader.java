package com.minecraftai;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;

/**
 * Reads the binary .tmcpr packet stream format.
 *
 * Format: [4 bytes timestamp ms][4 bytes packet length][N bytes packet data]
 * Packet data: [VarInt packet ID][packet-specific fields]
 */
public class PacketReader {
    private final DataInputStream stream;
    private boolean hasNext = true;

    public PacketReader(InputStream inputStream) {
        this.stream = new DataInputStream(inputStream);
    }

    /**
     * Read the next packet entry from the stream
     * @return RawPacket with timestamp and data, or null if end of stream
     */
    public RawPacket readNext() throws IOException {
        try {
            // Read timestamp (4 bytes, big-endian, milliseconds)
            int timestamp = stream.readInt();

            // Read packet data length (4 bytes, big-endian)
            int length = stream.readInt();

            if (length <= 0 || length > 2_097_152) { // Max 2MB per packet
                // Skip invalid packets
                System.err.println("Warning: Invalid packet length " + length + " at " + timestamp + "ms, skipping");
                return new RawPacket(timestamp, -1, new byte[0]);
            }

            // Read packet data
            byte[] data = new byte[length];
            stream.readFully(data);

            // Parse VarInt packet ID from data
            int[] varIntResult = readVarInt(data, 0);
            int packetId = varIntResult[0];
            int idLength = varIntResult[1]; // bytes consumed by VarInt

            return new RawPacket(timestamp, packetId, data, idLength);

        } catch (java.io.EOFException e) {
            hasNext = false;
            return null;
        }
    }

    public boolean hasNext() {
        return hasNext;
    }

    public void close() throws IOException {
        stream.close();
    }

    /**
     * Read a Minecraft VarInt from byte array
     * @return [value, bytesConsumed]
     */
    public static int[] readVarInt(byte[] data, int offset) {
        int value = 0;
        int position = 0;
        int index = offset;

        while (index < data.length) {
            byte currentByte = data[index];
            value |= (currentByte & 0x7F) << position;

            if ((currentByte & 0x80) == 0) {
                return new int[]{value, index - offset + 1};
            }

            position += 7;
            index++;

            if (position >= 32) {
                throw new RuntimeException("VarInt too big");
            }
        }

        throw new RuntimeException("VarInt incomplete");
    }

    /**
     * Read a VarInt from a DataInputStream-like byte source
     */
    public static int readVarIntFromBytes(byte[] data, int[] cursor) {
        int value = 0;
        int position = 0;

        while (cursor[0] < data.length) {
            byte currentByte = data[cursor[0]];
            cursor[0]++;
            value |= (currentByte & 0x7F) << position;

            if ((currentByte & 0x80) == 0) {
                return value;
            }

            position += 7;
            if (position >= 32) {
                throw new RuntimeException("VarInt too big");
            }
        }

        throw new RuntimeException("VarInt incomplete");
    }

    /**
     * Read a String (VarInt length + UTF-8 bytes)
     */
    public static String readString(byte[] data, int[] cursor) {
        int length = readVarIntFromBytes(data, cursor);
        if (length > 32767 || cursor[0] + length > data.length) {
            return "";
        }
        String result = new String(data, cursor[0], length, java.nio.charset.StandardCharsets.UTF_8);
        cursor[0] += length;
        return result;
    }

    /**
     * Read a UUID (two longs, 16 bytes)
     */
    public static UUID readUUID(byte[] data, int[] cursor) {
        long msb = readLong(data, cursor);
        long lsb = readLong(data, cursor);
        return new UUID(msb, lsb);
    }

    /**
     * Read a double (8 bytes, big-endian)
     */
    public static double readDouble(byte[] data, int[] cursor) {
        long bits = readLong(data, cursor);
        return Double.longBitsToDouble(bits);
    }
    
    /**
     * Read a float
     */
    public static float readFloatFromBytes(byte[] data, int[] cursor) {
        float v = ByteBuffer.wrap(data, cursor[0], 4).order(ByteOrder.BIG_ENDIAN).getFloat();
        cursor[0] += 4;
        return v;
    }
    
    public static float readFloat(byte[] data, int[] cursor) {
        int bits = 0;
        for (int i = 0; i < 4; i++) {
            bits = (bits << 8) | (data[cursor[0]++] & 0xFF);
        }
        return Float.intBitsToFloat(bits);
    }

    /**
     * Read a long (8 bytes, big-endian)
     */
    public static long readLong(byte[] data, int[] cursor) {
        long value = 0;
        for (int i = 0; i < 8; i++) {
            value = (value << 8) | (data[cursor[0]++] & 0xFF);
        }
        return value;
    }

    /**
     * Read an int (4 bytes, big-endian)
     */
    public static int readInt(byte[] data, int[] cursor) {
        int value = 0;
        for (int i = 0; i < 4; i++) {
            value = (value << 8) | (data[cursor[0]++] & 0xFF);
        }
        return value;
    }

    /**
     * Read a short (2 bytes, big-endian)
     */
    public static short readShort(byte[] data, int[] cursor) {
        short value = (short) ((data[cursor[0]++] & 0xFF) << 8);
        value |= (data[cursor[0]++] & 0xFF);
        return value;
    }

    /**
     * Read a byte
     */
    public static byte readByte(byte[] data, int[] cursor) {
        return data[cursor[0]++];
    }

    /**
     * Read an unsigned byte
     */
    public static int readUnsignedByte(byte[] data, int[] cursor) {
        return data[cursor[0]++] & 0xFF;
    }

    /**
     * Read a boolean
     */
    public static boolean readBoolean(byte[] data, int[] cursor) {
        return data[cursor[0]++] != 0;
    }

    /**
     * Represents a raw packet from the .tmcpr stream
     */
    public static class RawPacket {
        public final int timestamp;    // Milliseconds since start
        public final int packetId;     // Minecraft packet ID
        public final byte[] data;      // Full packet data (including ID)
        public final int dataOffset;   // Offset past the packet ID

        public RawPacket(int timestamp, int packetId, byte[] data) {
            this(timestamp, packetId, data, 0);
        }

        public RawPacket(int timestamp, int packetId, byte[] data, int dataOffset) {
            this.timestamp = timestamp;
            this.packetId = packetId;
            this.data = data;
            this.dataOffset = dataOffset;
        }

        public int getTick() {
            return timestamp / 50; // Convert ms to ticks (20 TPS)
        }
    }
}
