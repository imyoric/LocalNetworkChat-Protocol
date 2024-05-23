package ru.yoricya.localnetworkchat.NetChat;

import java.net.InetAddress;

public interface MessageHandler {
    void onMessage(String message, InetAddress address);
}
