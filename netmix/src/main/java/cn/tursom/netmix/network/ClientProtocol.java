package cn.tursom.netmix.network;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.Connection;
import net.minecraft.util.debugchart.LocalSampleLogger;
import org.jetbrains.annotations.NotNull;

import java.net.SocketAddress;

/**
 * 用来表示不同的底层通讯协议，可以是 QUIC、TCP、KCP、WebSocket 等等。
 * {@link ClientProtocol} 的实现应该是全局单例，通过{@link ProtocolManager}进行注册与管理。
 */
public interface ClientProtocol {
    Thread newConnector(@NotNull String name,
                        ConnectScreen connectScreen,
                        Minecraft minecraft,
                        ServerAddress serverAddress,
                        ServerData serverData,
                        TransferState transferState);

    Connection connectToServer(ServerData serverData, SocketAddress remote, boolean useNativeTransport, LocalSampleLogger sampleLogger);

    boolean isValidProtocol(String address);

    String getRawAddress(String address);
}
