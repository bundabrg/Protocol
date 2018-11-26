package com.nukkitx.protocol.bedrock.packet;

import com.flowpowered.math.vector.Vector3i;
import com.nukkitx.protocol.bedrock.BedrockPacket;
import com.nukkitx.protocol.bedrock.handler.BedrockPacketHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public abstract class BlockPickRequestPacket extends BedrockPacket {
    protected Vector3i blockPosition;
    protected boolean addUserData;
    protected byte hotbarSlot;

    @Override
    public final boolean handle(BedrockPacketHandler handler) {
        return handler.handle(this);
    }
}