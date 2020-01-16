package com.arushi;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;

import static java.util.Collections.list;

public final class Utils {

    private Utils() {
    }

    public static String getDefaultAddress() {
        try {
            return list(NetworkInterface.getNetworkInterfaces()).stream()
                    .flatMap(ni -> list(ni.getInetAddresses()).stream())
                    .filter(address -> !address.isAnyLocalAddress())
                    .filter(address -> !address.isMulticastAddress())
                    .filter(address -> !address.isLoopbackAddress())
                    .filter(address -> !(address instanceof Inet6Address))
                    .map(InetAddress::getHostAddress)
                    .findFirst().orElse("0.0.0.0");
        } catch (SocketException e) {
            return "0.0.0.0";
        }
    }
}
