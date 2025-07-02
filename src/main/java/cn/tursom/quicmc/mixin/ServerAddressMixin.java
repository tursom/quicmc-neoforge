package cn.tursom.quicmc.mixin;

import com.google.common.net.HostAndPort;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerAddress.class)
public class ServerAddressMixin {
    private static final String PROTOCOL = "quic://";

    @Final
    @Shadow
    private static ServerAddress INVALID;

    @Final
    @Shadow
    private static Logger LOGGER;

    @Inject(at = @At("HEAD"),
            method = "parseString(Ljava/lang/String;)Lnet/minecraft/client/multiplayer/resolver/ServerAddress;",
            cancellable = true)
    private static void parseString(String address, CallbackInfoReturnable<ServerAddress> ci) {
        if (address == null || !address.startsWith(PROTOCOL)) {
            return;
        }

        address = address.substring(PROTOCOL.length());
        try {
            HostAndPort hostandport = HostAndPort.fromString(address).withDefaultPort(25565);
            ServerAddress serverAddress = hostandport.getHost().isEmpty() ? INVALID : new ServerAddress(hostandport.getHost(), hostandport.getPort());
            ci.setReturnValue(serverAddress);
            ci.cancel();
        } catch (IllegalArgumentException illegalargumentexception) {
            LOGGER.info("Failed to parse URL {}", address, illegalargumentexception);
            ci.setReturnValue(INVALID);
            ci.cancel();
        }
    }

    // isValidAddress
    @Inject(at = @At("HEAD"),
            method = "isValidAddress(Ljava/lang/String;)Z",
            cancellable = true)
    private static void isValidAddress(String address, CallbackInfoReturnable<Boolean> ci) {
        if (address == null || !address.startsWith(PROTOCOL)) {
            return;
        }

        address = address.substring(PROTOCOL.length());
        ci.setReturnValue(ServerAddress.isValidAddress(address));
        ci.cancel();
    }
}
