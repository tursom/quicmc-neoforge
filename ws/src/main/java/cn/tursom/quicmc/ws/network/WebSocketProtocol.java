package cn.tursom.quicmc.ws.network;

import cn.tursom.netmix.network.ClientProtocol;
import io.netty.handler.codec.http.QueryStringDecoder;
import lombok.NoArgsConstructor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.Connection;
import net.minecraft.util.debugchart.LocalSampleLogger;
import org.jetbrains.annotations.NotNull;

import java.net.SocketAddress;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static lombok.AccessLevel.PRIVATE;

@NoArgsConstructor(access = PRIVATE)
public class WebSocketProtocol implements ClientProtocol {
    public static final WebSocketProtocol INSTANCE = new WebSocketProtocol();

    @Override
    public Thread newConnector(@NotNull String name, ConnectScreen connectScreen, Minecraft minecraft, ServerAddress serverAddress, ServerData serverData, TransferState transferState) {
        return new WebSocketConnector(name, connectScreen, minecraft, serverAddress, serverData, transferState);
    }

    @Override
    public Connection connectToServer(ServerData serverData, SocketAddress remote, boolean useNativeTransport, LocalSampleLogger sampleLogger) {
        return WebSocketConnector.connectToServer(serverData, useNativeTransport, sampleLogger);
    }

    @Override
    public boolean isValidProtocol(String address) {
        return address != null && (address.startsWith("ws://") || address.startsWith("wss://"));
    }

    @Override
    public String getRawAddress(String address) {
        if (!isValidProtocol(address)) {
            throw new IllegalArgumentException("Invalid WebSocket address: " + address);
        }

        if (address.startsWith("ws://")) {
            return getDomain(address, 5);
        } else if (address.startsWith("wss://")) {
            return getDomain(address, 6);
        } else {
            return address;
        }
    }

    private static String getDomain(String address, int beginIndex) {
        // 从 url 参数的 host 字段获取域名
        QueryStringDecoder decoder = new QueryStringDecoder(address);
        Map<String, List<String>> params = decoder.parameters();
        List<String> host = params.getOrDefault("host", Collections.emptyList());
        if (!host.isEmpty()) {
            return host.get(0);
        }

        // 如果没有 host 参数，则从 url 中获取域名
        int domainIndex = address.indexOf('/', beginIndex);
        if (domainIndex != -1) {
            return address.substring(beginIndex, domainIndex);
        } else {
            return address.substring(beginIndex);
        }
    }
}
