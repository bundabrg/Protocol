package com.nukkitx.protocol.bedrock;

import com.nukkitx.network.util.Preconditions;
import com.nukkitx.protocol.bedrock.exception.PacketSerializeException;
import com.nukkitx.protocol.bedrock.packet.UnknownPacket;
import com.nukkitx.protocol.util.Int2ObjectBiMap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;

@Immutable
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class BedrockPacketCodec {
    private static final InternalLogger log = InternalLoggerFactory.getInstance(BedrockPacketCodec.class);

    @Getter
    private final int protocolVersion;
    @Getter
    private final String minecraftVersion;
    private final BedrockPacketSerializer<BedrockPacket>[] serializers;
    private final Int2ObjectBiMap<Class<? extends BedrockPacket>> idBiMap;
    private final BedrockPacketHelper helper;

    public static Builder builder() {
        return new Builder();
    }

    // @TODO Remove Debugging
    public void dumpBytes(File file, byte[] bytes) {
        StringBuilder b = new StringBuilder();
        for(int i=0;i<bytes.length;i++) {
            if (i%80 == 0) {
                b.append("\n");
            }
            b.append(String.format("%02x ", bytes[i]));
        }

        b.append("\n\n");
        for(int i=0; i<bytes.length; i++) {
            if (i%80 == 0) {
                b.append("\n");
            }
            char c = (char) bytes[i];
            if ( (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c<= 'Z')
                    || (c >= '0' && c<= '9')) {
                b.append((char)bytes[i]);
            } else {
                b.append(".");
            }
            b.append(" ");
        }

        try (PrintStream out = new PrintStream(new FileOutputStream(file))) {
            out.println(b.toString());
        } catch (FileNotFoundException fileNotFoundException) {
            fileNotFoundException.printStackTrace();
        }
    }

    private byte[] byteBufToByteArray(ByteBuf buf) {
        byte[] bytes = new byte[buf.readableBytes()];
        buf.duplicate().readBytes(bytes);
        return bytes;
    }

    public BedrockPacket tryDecode(ByteBuf buf, int id) throws PacketSerializeException {
        BedrockPacket packet;
        BedrockPacketSerializer<BedrockPacket> serializer;
        try {
            packet = idBiMap.get(id).newInstance();
            if (packet instanceof UnknownPacket) {
                //noinspection unchecked
                serializer = (BedrockPacketSerializer<BedrockPacket>) packet;
            } else {
                serializer = serializers[id];
            }
        } catch (ClassCastException | InstantiationException | IllegalAccessException | ArrayIndexOutOfBoundsException e) {
            throw new PacketSerializeException("Packet ID " + id + " does not exist", e);
        }

        // @TODO Remove Debugging
        packet.setRaw(byteBufToByteArray(buf)); // For those watching, this is used in ProxyPass to compare in and out bytes
        try {
            serializer.deserialize(buf, this.helper, packet);
        } catch (Exception e) {
            // @TODO Remove Debugging
            System.err.println("tryDecode(): Unable to serialize " + packet);
            e.printStackTrace();
            dumpBytes(new File(packet.getClass().getSimpleName() + ".txt"), packet.getRaw());
            throw new PacketSerializeException("Error whilst deserializing " + packet, e);
        }

        if (log.isDebugEnabled() && buf.isReadable()) {
            // @TODO Remove Debugging
            dumpBytes(new File(packet.getClass().getSimpleName() + ".txt"), packet.getRaw());
            log.debug(packet.getClass().getSimpleName() + " still has " + buf.readableBytes() + " bytes to read!");
        }
        return packet;
    }

    @SuppressWarnings("unchecked")
    public void tryEncode(ByteBuf buf, BedrockPacket packet) throws PacketSerializeException {
        try {
            BedrockPacketSerializer<BedrockPacket> serializer;
            if (packet instanceof UnknownPacket) {
                serializer = (BedrockPacketSerializer<BedrockPacket>) packet;
            } else {
                int packetId = getId(packet.getClass());
                serializer = serializers[packetId];
            }
            serializer.serialize(buf, this.helper, packet);
        } catch (Exception e) {
            // @TODO Remove Debugging - Needed because Geyser suppresses this exception
            System.err.println("Error whilst serializing " + packet);
            e.printStackTrace();
            throw new PacketSerializeException("Error whilst serializing " + packet, e);
        } finally {
            ReferenceCountUtil.release(packet);
        }
    }

    public int getId(Class<? extends BedrockPacket> clazz) {
        int id = idBiMap.get(clazz);
        if (id == -1) {
            throw new IllegalArgumentException("Packet ID for " + clazz.getName() + " does not exist.");
        }
        return id;
    }

    @SuppressWarnings("unchecked")
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder {
        private final Int2ObjectMap<BedrockPacketSerializer<BedrockPacket>> serializers = new Int2ObjectOpenHashMap<>();
        private final Int2ObjectBiMap<Class<? extends BedrockPacket>> idBiMap = new Int2ObjectBiMap<>(UnknownPacket.class);
        private int protocolVersion = -1;
        private String minecraftVersion = null;
        private BedrockPacketHelper helper = null;

        public <T extends BedrockPacket> Builder registerPacket(Class<T> packetClass, BedrockPacketSerializer<T> serializer, @Nonnegative int id) {
            Preconditions.checkArgument(id >= 0, "id cannot be negative");
            Preconditions.checkArgument(!idBiMap.containsKey(id), "Packet id already registered");
            Preconditions.checkArgument(!idBiMap.containsValue(packetClass), "Packet class already registered");

            serializers.put(id, (BedrockPacketSerializer<BedrockPacket>) serializer);
            idBiMap.put(id, packetClass);
            return this;
        }

        public Builder protocolVersion(@Nonnegative int protocolVersion) {
            Preconditions.checkArgument(protocolVersion >= 0, "protocolVersion cannot be negative");
            this.protocolVersion = protocolVersion;
            return this;
        }

        public Builder minecraftVersion(@Nonnull String minecraftVersion) {
            Preconditions.checkNotNull(minecraftVersion, "minecraftVersion");
            Preconditions.checkArgument(!minecraftVersion.isEmpty() && minecraftVersion.split("\\.").length > 2, "Invalid minecraftVersion");
            this.minecraftVersion = minecraftVersion;
            return this;
        }

        public Builder helper(@Nonnull BedrockPacketHelper helper) {
            Preconditions.checkNotNull(helper, "helper");
            this.helper = helper;
            return this;
        }

        public BedrockPacketCodec build() {
            Preconditions.checkArgument(protocolVersion >= 0, "No protocol version defined");
            Preconditions.checkNotNull(minecraftVersion, "No Minecraft version defined");
            Preconditions.checkNotNull(helper, "helper cannot be null");
            int largestId = -1;
            for (int id : serializers.keySet()) {
                if (id > largestId) {
                    largestId = id;
                }
            }
            Preconditions.checkArgument(largestId > -1, "Must have at least one packet registered");
            BedrockPacketSerializer<BedrockPacket>[] serializers = new BedrockPacketSerializer[largestId + 1];

            for (Int2ObjectMap.Entry<BedrockPacketSerializer<BedrockPacket>> entry : this.serializers.int2ObjectEntrySet()) {
                serializers[entry.getIntKey()] = entry.getValue();
            }
            return new BedrockPacketCodec(protocolVersion, minecraftVersion, serializers, idBiMap, helper);
        }
    }
}
