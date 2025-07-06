package cn.tursom.quicmc.network;

import cn.tursom.netmix.network.ClientProtocol;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.Connection;
import net.minecraft.util.debugchart.LocalSampleLogger;
import org.jetbrains.annotations.NotNull;

import java.net.SocketAddress;

public class QuicClientProtocol implements ClientProtocol {
    public static final QuicClientProtocol INSTANCE = new QuicClientProtocol();

    private QuicClientProtocol() {
        // Private constructor to enforce singleton pattern
    }

    @Override
    public Thread newConnector(@NotNull String name, ConnectScreen connectScreen, Minecraft minecraft, ServerAddress serverAddress, ServerData serverData, TransferState transferState) {
        return new QuicConnector(name, connectScreen, minecraft, serverAddress, serverData, transferState);
    }

    @Override
    public Connection connectToServer(ServerData serverData, SocketAddress remote, boolean useNativeTransport, LocalSampleLogger sampleLogger) {
        return QuicConnector.connectToServer(remote, useNativeTransport, sampleLogger);
    }

    @Override
    public boolean isValidProtocol(String address) {
        return address != null && address.startsWith("quic://");
    }

    @Override
    public String getRawAddress(String address) {
        if (!isValidProtocol(address)) {
            throw new IllegalArgumentException("Invalid QUIC protocol address: " + address);
        }
        return address.substring("quic://".length());
    }
}
