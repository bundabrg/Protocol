package com.nukkitx.protocol.bedrock.v291.serializer;

import com.nukkitx.protocol.bedrock.packet.GuiDataPickItemPacket;
import com.nukkitx.protocol.bedrock.v291.BedrockUtils;
import com.nukkitx.protocol.serializer.PacketSerializer;
import io.netty.buffer.ByteBuf;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class GuiDataPickItemSerializer_v291 implements PacketSerializer<GuiDataPickItemPacket> {
    public static final GuiDataPickItemSerializer_v291 INSTANCE = new GuiDataPickItemSerializer_v291();


    @Override
    public void serialize(ByteBuf buffer, GuiDataPickItemPacket packet) {
        BedrockUtils.writeString(buffer, packet.getDescription());
        BedrockUtils.writeString(buffer, packet.getItemEffects());
        buffer.writeIntLE(packet.getHotbarSlot());
    }

    @Override
    public void deserialize(ByteBuf buffer, GuiDataPickItemPacket packet) {
        packet.setDescription(BedrockUtils.readString(buffer));
        packet.setItemEffects(BedrockUtils.readString(buffer));
        packet.setHotbarSlot(buffer.readIntLE());
    }
}
