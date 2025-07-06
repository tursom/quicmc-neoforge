package cn.tursom.netmix.mixin;

import cn.tursom.netmix.network.ClientProtocol;
import cn.tursom.netmix.network.ProtocolManager;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.network.Connection;
import net.minecraft.util.debugchart.LocalSampleLogger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.net.InetSocketAddress;

@Mixin(ServerStatusPinger.class)
public class ServerStatusPingerMixin {
    @Unique
    private final ThreadLocal<ServerData> CURRENT_SERVER = new ThreadLocal<>();

    @Redirect(
            method = "pingServer",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/network/Connection;connectToServer(Ljava/net/InetSocketAddress;ZLnet/minecraft/util/debugchart/LocalSampleLogger;)Lnet/minecraft/network/Connection;"
            )
    )
    private Connection redirectConnectToServer(
            InetSocketAddress address,
            boolean useEpollIfAvailable,
            LocalSampleLogger sampleLogger,
            @Local ServerData serverData
    ) {
        ClientProtocol protocol = ProtocolManager.findClientProtocol(serverData.ip);
        if (protocol == null) {
            return Connection.connectToServer(address, useEpollIfAvailable, sampleLogger);
        }

        return protocol.connectToServer(serverData, address, useEpollIfAvailable, sampleLogger);
    }
}