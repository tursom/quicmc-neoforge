package cn.tursom.netmix.network;

import lombok.experimental.UtilityClass;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@UtilityClass
public class ProtocolManager {
    private final Set<ClientProtocol> clientProtocols = ConcurrentHashMap.newKeySet();

    public void registerClientProtocol(ClientProtocol clientProtocol) {
        clientProtocols.add(clientProtocol);
    }

    public ClientProtocol findClientProtocol(String ip) {
        for (ClientProtocol protocol : clientProtocols) {
            if (protocol.isValidProtocol(ip)) {
                return protocol;
            }
        }

        return null;
    }
}
