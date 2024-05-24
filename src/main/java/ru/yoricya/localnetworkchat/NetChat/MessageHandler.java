package ru.yoricya.localnetworkchat.NetChat;

import java.net.InetAddress;

public interface MessageHandler {
    void onMessage(String message, InetAddress address, boolean isReplyForYou);
    default void onErrorWhileDecoding(Throwable error, InetAddress address){};
}
