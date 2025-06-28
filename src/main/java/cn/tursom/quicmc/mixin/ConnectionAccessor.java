package cn.tursom.quicmc.mixin;

import io.netty.channel.ChannelPipeline;
import net.minecraft.network.BandwidthDebugMonitor;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Connection.class)
public interface ConnectionAccessor {
    @Accessor("bandwidthDebugMonitor")
    BandwidthDebugMonitor getBandwidthDebugMonitor();

    @Invoker("configurePacketHandler")
    void invokeConfigurePacketHandler(ChannelPipeline pipeline);
}
