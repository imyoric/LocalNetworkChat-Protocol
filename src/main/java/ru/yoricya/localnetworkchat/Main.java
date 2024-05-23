package ru.yoricya.localnetworkchat;

import ru.yoricya.localnetworkchat.NetChat.MessageHandler;
import ru.yoricya.localnetworkchat.NetChat.NetChat;

import java.net.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class Main {

    public static void main(String[] args) throws Exception {
        System.out.print("Введите код комнаты, или нажмите Enter для продолжения без кода: ");
        String roomKey = new Scanner(System.in).nextLine();

        NetChat netChat = new NetChat(roomKey, new MessageHandler() {
            @Override
            public void onMessage(String message, InetAddress address) {
                if(address != null)
                    sendToChat(address.getHostAddress(), message);
                else
                    sendToChat("(ME)", message);
            }
        });

        NetChat.isDebug = true;

        Scanner sc = new Scanner(System.in);

        new Thread(new Runnable() {
            @Override
            public void run() {
                while(true){
                    String line = sc.nextLine();
                    netChat.broadcastMessage(line);
                }
            }
        }).start();
    }

    public static void sendToChat(String user, String msg){
        LocalTime currentTime = LocalTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        String timeString = currentTime.format(formatter);
        System.out.println("["+timeString+"] "+user+" ->  "+msg);
    }
}