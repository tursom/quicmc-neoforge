package cn.tursom.quicmc.ws.network;

import cn.tursom.netmix.network.AbstractConnector;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.handler.codec.MessageToMessageCodec;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.util.debugchart.LocalSampleLogger;
import org.jetbrains.annotations.NotNull;

import java.net.SocketAddress;
import java.net.URI;
import java.util.List;

@Slf4j
public class WebSocketConnector extends AbstractConnector {
    private static final AttributeKey<Promise<Channel>> HANDSHAKE_PROMISE = AttributeKey.valueOf("websocket_handshake_promise");

    public WebSocketConnector(@NotNull String name, ConnectScreen connectScreen, Minecraft minecraft, ServerAddress serverAddress, ServerData serverData, TransferState transferState) {
        super(name, connectScreen, minecraft, serverAddress, serverData, transferState);
    }

    public static Connection connectToServer(ServerData serverData, boolean useNativeTransport, LocalSampleLogger sampleLogger) {
        Connection connection = new Connection(PacketFlow.CLIENTBOUND);
        if (sampleLogger != null) {
            connection.setBandwidthLogger(sampleLogger);
        }
        connect(serverData, connection, useNativeTransport).syncUninterruptibly();
        return connection;
    }

    @Override
    public Future<? extends Channel> doConnect(SocketAddress remote, Connection connection, boolean useNativeTransport) {
        return connect(serverData, connection, useNativeTransport);
    }

    @NonNull
    @SneakyThrows
    public static Future<? extends Channel> connect(ServerData serverData, Connection connection, boolean useNativeTransport) {
        URI uri = new URI(serverData.ip);
        String scheme = uri.getScheme();
        String host = uri.getHost();
        int port = uri.getPort() == -1 ? (scheme.equals("wss") ? 443 : 80) : uri.getPort();

        Bootstrap bootstrap = socketBootstrap(useNativeTransport)
                .handler(new ChannelInitializer<>() {
                    @Override
                    @SneakyThrows
                    protected void initChannel(Channel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        if ("wss".equals(scheme)) {
                            SslContext sslContext = SslContextBuilder.forClient()
                                    .trustManager(InsecureTrustManagerFactory.INSTANCE).build();
                            pipeline.addLast(sslContext.newHandler(ch.alloc(), host, port));
                        }
                        pipeline.addLast(new HttpClientCodec());
                        pipeline.addLast(new HttpObjectAggregator(8192));
                        pipeline.addLast(new WebSocketClientProtocolHandler(
                                WebSocketClientHandshakerFactory.newHandshaker(
                                        uri, WebSocketVersion.V13, null, true, null)));

                        Promise<Channel> promise = ch.eventLoop().newPromise();
                        ch.attr(HANDSHAKE_PROMISE).set(promise);
                        pipeline.addLast(new WebSocketHandshakeListener(connection, promise));

                        pipeline.addLast("websocket_packer", WebSocketPacker.INSTANCE);
                        initConnectionChannel(connection, ch);
                    }
                });

        ChannelFuture channelFuture = bootstrap.connect(host, port);
        channelFuture.syncUninterruptibly();
        @NonNull
        Promise<Channel> promise = channelFuture.channel().attr(HANDSHAKE_PROMISE).get();
        channelFuture.addListener(future -> {
            if (!future.isSuccess()) {
                promise.setFailure(future.cause());
            }
        });
        return promise;
    }

    @RequiredArgsConstructor
    private static class WebSocketHandshakeListener extends ChannelInboundHandlerAdapter {
        private final Connection connection;
        private final Promise<Channel> promise;

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt == WebSocketClientProtocolHandler.ClientHandshakeStateEvent.HANDSHAKE_COMPLETE) {
                ChannelPipeline pipeline = ctx.channel().pipeline();
                pipeline.remove(WebSocketHandshakeListener.class);
                promise.setSuccess(ctx.channel());
            }
            super.userEventTriggered(ctx, evt);
        }
    }

    @ChannelHandler.Sharable
    private static class WebSocketPacker extends MessageToMessageCodec<WebSocketFrame, ByteBuf> {
        public static final WebSocketPacker INSTANCE = new WebSocketPacker();

        @Override
        protected void encode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) {
            out.add(new BinaryWebSocketFrame(msg.retain()));
        }

        @Override
        protected void decode(ChannelHandlerContext ctx, WebSocketFrame msg, List<Object> out) {
            if (!(msg instanceof BinaryWebSocketFrame binaryFrame)) {
                log.warn("Received non-binary WebSocket frame: {}", msg.getClass().getSimpleName());
                msg.release();
                return;
            }

            ByteBuf content = binaryFrame.content();
            if (content.isReadable()) {
                out.add(content.retain());
            } else {
                content.release();
            }
        }
    }
}
