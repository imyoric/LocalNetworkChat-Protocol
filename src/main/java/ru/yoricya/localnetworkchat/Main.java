package ru.yoricya.localnetworkchat;

import ru.yoricya.localnetworkchat.NetChat.MessageHandler;
import ru.yoricya.localnetworkchat.NetChat.NetChat;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class Main {
    public static void main(String[] args) throws Exception {
        System.out.print("Введите код комнаты, или нажмите Enter для продолжения без кода: ");
        String roomKey = new Scanner(System.in).nextLine();

        NetChat netChat = new NetChat(roomKey, new MessageHandler() {
            @Override
            public void onMessage(byte[] messageBytes, InetAddress address, boolean isReplyForYou, long timestamp) {
                String message = new String(messageBytes, StandardCharsets.UTF_8);
                String host = address.getHostAddress();
                if(isReplyForYou) host = "(Message for you) "+host;
                sendToChat(host, message);
            }
        });

        new Thread(new Runnable() {
            @Override
            public void run() {
                Scanner sc = new Scanner(System.in);
                while(true){
                    String line = sc.nextLine();

                    if(line.startsWith("/")){
                        String[] spl = line.substring(1).split(" ");

                        if(spl.length >= 3 && spl[0].equals("reply")){
                            sendToChat("ME (Reply for: "+spl[1]+")", line);
                            netChat.sendMessage(ArrToStr(spl, 2), spl[1]);
                        }

                        if(spl.length >= 1 && spl[0].equals("lnclist")){
                            List<InetAddress> addresses = netChat.listOfLocalLNCDevices();
                            StringBuilder s = new StringBuilder();

                            s.append("<<<< LNC Devices list >>>>\n");
                            int i = 1;
                            for(InetAddress address: addresses){
                                s.append("LNC Device #").append(i).append(": ").append(address.getHostAddress());
                                if(address.getHostAddress().equals(netChat.localAddr)) s.append(" - (ME)");
                                s.append("\n");
                                i++;
                            }
                            s.append("Count: ").append(i-1).append("\n--------------------------\n");

                            System.out.println(s);
                        }
                        continue;
                    }

                    sendToChat("ME", line);
                    netChat.broadcastMessage(line);
                }
            }
        }).start();
    }

    public static String ArrToStr(String[] str, int startIndex){
        String s = "";
        for(int i = startIndex; i != str.length; i++) s += str[i] + " ";
        return s;
    }

    public static void sendToChat(String user, String msg){
        LocalTime currentTime = LocalTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        String timeString = currentTime.format(formatter);
        System.out.println("["+timeString+"] "+user+" ->  "+msg);
    }
}