package ru.yoricya.localnetworkchat;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class IPUtils {
    public static String[] getExternalV6(){
        List<String> list = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
                while (inetAddresses.hasMoreElements()) {
                    InetAddress inetAddress = inetAddresses.nextElement();
                    if (inetAddress instanceof Inet6Address && !inetAddress.isLoopbackAddress() && !inetAddress.getHostAddress().startsWith("fe80")) {
                        list.add(inetAddress.getHostAddress().split("%")[0].trim());
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return list.toArray(new String[0]);
    }

    public static String[] getV4(){
        List<String> list = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
                while (inetAddresses.hasMoreElements()) {
                    InetAddress inetAddress = inetAddresses.nextElement();
                    if (inetAddress instanceof Inet4Address && !inetAddress.isLoopbackAddress()) {
                        list.add(inetAddress.getHostAddress().split("%")[0].trim());
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return list.toArray(new String[0]);
    }
}
