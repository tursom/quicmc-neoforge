package cn.tursom.quicmc.network;

import cn.tursom.netmix.mixin.ConnectionAccessor;
import cn.tursom.netmix.network.AbstractConnector;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.incubator.codec.quic.*;
import io.netty.util.concurrent.Future;
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

import java.net.InetSocketAddress;
import java.net.SocketAddress;

public class QuicConnector extends AbstractConnector {
    private static final QuicSslContext SSL_CONTEXT = QuicSslContextBuilder.forClient()
            .trustManager(InsecureTrustManagerFactory.INSTANCE)
            .applicationProtocols("minecraft", "raw", "quic") // 多个协议选项
            .build();

    public QuicConnector(@NotNull String name, ConnectScreen connectScreen, Minecraft minecraft, ServerAddress serverAddress, ServerData serverData, TransferState transferState) {
        super(name, connectScreen, minecraft, serverAddress, serverData, transferState);
    }

    @Override
    public Future<? extends Channel> doConnect(SocketAddress remote, Connection connection, boolean useNativeTransport) {
        return connect(remote, connection, useNativeTransport);
    }


    /**
     * 连接到远程服务器
     *
     * @see Connection#connectToServer(InetSocketAddress, boolean, LocalSampleLogger)
     */
    public static Connection connectToServer(SocketAddress remote, boolean useNativeTransport, LocalSampleLogger sampleLogger) {
        var connection = new Connection(PacketFlow.CLIENTBOUND);
        if (sampleLogger != null) {
            connection.setBandwidthLogger(sampleLogger);
        }
        connect(remote, connection, useNativeTransport).syncUninterruptibly();
        return connection;
    }

    @SneakyThrows
    public static Future<? extends Channel> connect(SocketAddress remote, Connection connection, boolean useNativeTransport) {
        Channel channel = datagramBootstrap(useNativeTransport)
                .handler(new QuicClientCodecBuilder()
                        .sslContext(SSL_CONTEXT)
                        .initialMaxData(33554432L)
                        .initialMaxStreamDataBidirectionalLocal(16777216L)
                        .initialMaxStreamDataBidirectionalRemote(16777216L)
                        .initialMaxStreamDataUnidirectional(16777216L)
                        .initialMaxStreamsBidirectional(100L)
                        .initialMaxStreamsUnidirectional(100L)
                        .activeMigration(true)
                        .build())
                .bind(0).sync().channel();

        // 连接到服务器
        QuicChannel quicChannel = QuicChannel.newBootstrap(channel)
                .handler(new ChannelInitializer<QuicChannel>() {
                    @Override
                    protected void initChannel(QuicChannel ch) {
                        // QUIC 连接处理器
                        ch.pipeline().addLast(new QuicConnectionHandler());
                    }
                })
                .streamHandler(new QuicStreamInitializer(connection))
                .remoteAddress(remote)
                .connect()
                .get();

        // 创建流并发送数据
        return quicChannel.createStream(QuicStreamType.BIDIRECTIONAL, new QuicStreamInitializer(connection));
    }

    @Slf4j
    @RequiredArgsConstructor
    private static class QuicStreamInitializer extends ChannelInitializer<QuicStreamChannel> {
        private final Connection connection;

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            ctx.read();
        }

        @Override
        protected void initChannel(QuicStreamChannel ch) {
            ChannelPipeline channelpipeline = ch.pipeline().addLast("timeout", new ReadTimeoutHandler(30));
            Connection.configureSerialization(channelpipeline, PacketFlow.CLIENTBOUND, false, ((ConnectionAccessor) connection).getBandwidthDebugMonitor());
            ((ConnectionAccessor) connection).invokeConfigurePacketHandler(channelpipeline);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            super.channelRead(ctx, msg);
        }
    }

    private static class QuicConnectionHandler extends ChannelInboundHandlerAdapter {
    }
}
