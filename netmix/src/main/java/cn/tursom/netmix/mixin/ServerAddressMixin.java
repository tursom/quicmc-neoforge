package cn.tursom.netmix.mixin;

import cn.tursom.netmix.network.ClientProtocol;
import cn.tursom.netmix.network.ProtocolManager;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ServerAddress.class)
public class ServerAddressMixin {
    @ModifyVariable(
            method = {"parseString", "isValidAddress"},
            at = @At("HEAD"),
            ordinal = 0,
            argsOnly = true)
    private static String removeHeader(String address) {
        ClientProtocol protocol = ProtocolManager.findClientProtocol(address);
        if (protocol == null) {
            return address;
        } else {
            return protocol.getRawAddress(address);
        }
    }
}
