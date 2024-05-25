package ru.yoricya.localnetworkchat.NetChat;

import java.net.InetAddress;

public interface MessageHandler {
    void onMessage(byte[] message, InetAddress address, boolean isReplyForYou, long timestamp);
    default void onErrorWhileDecoding(Throwable error, InetAddress address){};
    default void onMessageSendSuccessTo(InetAddress address, boolean isNewSocket){};
}
