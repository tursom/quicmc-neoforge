package cn.tursom.quicmc.network;

import cn.tursom.quicmc.mixin.ConnectScreenAccessor;
import cn.tursom.quicmc.mixin.ConnectionAccessor;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.incubator.codec.quic.*;
import io.netty.util.concurrent.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ResolvedServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerNameResolver;
import net.minecraft.client.resources.server.ServerPackManager;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.login.LoginProtocols;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.function.Consumer;

public class QuicConnector extends Thread {
    private static final QuicSslContext SSL_CONTEXT = QuicSslContextBuilder.forClient()
            .trustManager(InsecureTrustManagerFactory.INSTANCE)
            .applicationProtocols("minecraft", "raw", "quic") // 多个协议选项
            .build();

    private final ConnectScreen connectScreen;
    private final Minecraft minecraft;
    private final ServerAddress serverAddress;
    private final ServerData serverData;
    private final Consumer<Component> callback;
    private final TransferState transferState;

    private final EventLoopGroup group = new NioEventLoopGroup();

    public QuicConnector(@NotNull String name, ConnectScreen connectScreen, Minecraft minecraft, ServerAddress serverAddress, ServerData serverData, Consumer<Component> callback, TransferState transferState) {
        super(name);
        this.connectScreen = connectScreen;
        this.minecraft = minecraft;
        this.serverAddress = serverAddress;
        this.serverData = serverData;
        this.callback = callback;
        this.transferState = transferState;
    }

    @Override
    public void run() {
        ConnectScreenAccessor accessor = (ConnectScreenAccessor) connectScreen;

        InetSocketAddress inetsocketaddress = null;

        try {
            if (accessor.isAborted()) {
                return;
            }

            Optional<InetSocketAddress> optional = ServerNameResolver.DEFAULT.resolveAddress(serverAddress).map(ResolvedServerAddress::asInetSocketAddress);
            if (accessor.isAborted()) {
                return;
            }

            if (optional.isEmpty()) {
                ConnectScreenAccessor.getLogger().error("Couldn't connect to server: Unknown host \"{}\"", serverAddress.getHost());
                net.neoforged.neoforge.network.DualStackUtils.logInitialPreferences();
                minecraft.execute(() -> {
                    minecraft.setScreen(new DisconnectedScreen(accessor.getParent(), accessor.getConnectFailedTitle(), ConnectScreen.UNKNOWN_HOST_MESSAGE));
                });
                return;
            }

            inetsocketaddress = optional.get();
            Connection connection;
            Future<QuicStreamChannel> streamChannelFuture;
            synchronized (connectScreen) {
                if (accessor.isAborted()) {
                    return;
                }

                // same as net.minecraft.network.Connection.connect
                // but replaced TCP to QUIC

                connection = new Connection(PacketFlow.CLIENTBOUND);

                Bootstrap bootstrap = new Bootstrap();
                Channel channel = bootstrap.group(group)
                        .channel(NioDatagramChannel.class)
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
                        .remoteAddress(inetsocketaddress)
                        .connect()
                        .get();

                // 创建流并发送数据
                streamChannelFuture = quicChannel.createStream(QuicStreamType.BIDIRECTIONAL, new QuicStreamInitializer(connection));
            }

            QuicStreamChannel quicChannel = streamChannelFuture.syncUninterruptibly().get();
            accessor.setChannelFuture(quicChannel.newSucceededFuture());

            synchronized (connectScreen) {
                if (accessor.isAborted()) {
                    connection.disconnect(ConnectScreen.ABORT_CONNECTION);
                    return;
                }

                accessor.setConnection(connection);
                minecraft.getDownloadedPackSource().configureForServerControl(connection, convertPackStatus(serverData.getResourcePackStatus()));
            }

            connection.initiateServerboundPlayConnection(
                    inetsocketaddress.getHostName(),
                    inetsocketaddress.getPort(),
                    LoginProtocols.SERVERBOUND,
                    LoginProtocols.CLIENTBOUND,
                    new ClientHandshakePacketListenerImpl(
                            connection,
                            minecraft,
                            serverData,
                            accessor.getParent(),
                            false,
                            null,
                            callback,
                            transferState
                    ),
                    transferState != null
            );
            connection.send(new ServerboundHelloPacket(minecraft.getUser().getName(), minecraft.getUser().getProfileId()));
        } catch (Exception exception2) {
            if (accessor.isAborted()) {
                return;
            }

            Exception exception;
            if (exception2.getCause() instanceof Exception exception1) {
                exception = exception1;
            } else {
                exception = exception2;
            }

            ConnectScreenAccessor.getLogger().error("Couldn't connect to server", exception2);
            String s = inetsocketaddress == null
                    ? exception.getMessage()
                    : exception.getMessage()
                    .replaceAll(inetsocketaddress.getHostName() + ":" + inetsocketaddress.getPort(), "")
                    .replaceAll(inetsocketaddress.toString(), "");
            minecraft.execute(
                    () -> minecraft.setScreen(
                            new DisconnectedScreen(
                                    accessor.getParent(),
                                    accessor.getConnectFailedTitle(),
                                    Component.translatable("disconnect.genericReason", s)
                            )
                    )
            );
        }
    }

    private static ServerPackManager.PackPromptStatus convertPackStatus(ServerData.ServerPackStatus packStatus) {
        return switch (packStatus) {
            case ENABLED -> ServerPackManager.PackPromptStatus.ALLOWED;
            case DISABLED -> ServerPackManager.PackPromptStatus.DECLINED;
            case PROMPT -> ServerPackManager.PackPromptStatus.PENDING;
        };
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
