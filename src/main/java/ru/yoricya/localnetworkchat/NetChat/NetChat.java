package ru.yoricya.localnetworkchat.NetChat;

import ru.yoricya.localnetworkchat.CryptoUtils;
import ru.yoricya.localnetworkchat.IPUtils;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Base64;
import java.util.HashMap;

public class NetChat {
    public static boolean isDebug;
    private final byte[] rkBytes;
    public final String localAddr;
    private final MessageHandler messageHandler;
    public final ServerSocket serverSocket;
    public NetChat(String roomKey, MessageHandler messageHandler){
        if(roomKey.isEmpty()) roomKey = "0";
        rkBytes = CryptoUtils.generateMd5(roomKey.getBytes());
        localAddr = IPUtils.getV4()[0];
        this.messageHandler = messageHandler;
        try {
            serverSocket = initLocalServer(rkBytes, messageHandler);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void broadcastMessage(String msg){
        if(msg == null || msg.isEmpty()) return;
        messageHandler.onMessage(msg, null);
        broadCastMessage(localAddr, msg, rkBytes);
    }
    static final HashMap<String, Socket> ConnectedSockets = new HashMap<>();
    static void broadCastMessage(String MyLocalIp, String msg, byte[] roomKey){
        if(msg == null || msg.isEmpty()) return;

        byte[] message = msg.getBytes();
        byte[] body = new byte[message.length+4];

        body[0] = body[1] = body[2] = body[3] = 127;

        System.arraycopy(message, 0, body, 4, message.length);

        byte[] data = Base64.getEncoder().encode(CryptoUtils.encrypt(body, roomKey));

        String[] spl = MyLocalIp.split("\\.");
        String[] alAddress = new String[256];
        for(int i = 0; i!=alAddress.length; i++){
            alAddress[i] = spl[0]+"."+spl[1]+"."+spl[2]+"."+i;
        }

        for (String s : alAddress) {
            if (s.equals(MyLocalIp)) continue;

            Socket socket;
            synchronized (ConnectedSockets){
                socket = ConnectedSockets.get(s);
            }

            if(socket != null && !socket.isClosed()) try {
                sendToSocket(socket, data);

                if (isDebug)
                    System.out.println("[DEBUG] (Connected Socket) Broadcasting success to: " + s);
                continue;
            }catch (Exception ignore){}
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Socket clientSocket = new Socket();
                        clientSocket.connect(new InetSocketAddress(s, 47844), 3000);
                        sendToSocket(clientSocket, data);
                        if(isDebug)
                            System.out.println("[DEBUG] (Opened New Socket) Broadcasting success to: "+s);
                        synchronized (ConnectedSockets){
                            ConnectedSockets.putIfAbsent(s, clientSocket);
                        }
                    } catch (Exception e) {
                        synchronized (ConnectedSockets){
                            ConnectedSockets.remove(s);
                        }
                    }
                }
            }).start();
        }
    }

    public static void sendToSocket(Socket s, byte[] data) throws Exception{
        OutputStream clientOut = s.getOutputStream();
        clientOut.write(data);
        clientOut.flush();
    }

    public static ServerSocket initLocalServer(byte[] roomKey, MessageHandler handler) throws Exception{
        ServerSocket serverSocket = new ServerSocket(47844);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (true) {
                        Socket s = serverSocket.accept();
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    InputStream serverIn = s.getInputStream();

                                    while(!s.isClosed()){
                                        if(serverIn.available() == 0) continue;
                                        System.out.println(serverIn.available());

                                        byte[] bs = new byte[serverIn.available()];
                                        serverIn.read(bs);

                                        bs = Base64.getDecoder().decode(bs);
                                        bs = CryptoUtils.decrypt(bs, roomKey);
                                        //s.close();

                                        if(bs.length < 5) return;
                                        if(!(bs[0] == 127 && bs[1] == 127 && bs[2] == 127 && bs[3] == 127)) return;
                                        handler.onMessage(new String(bs).trim(), s.getInetAddress());
                                    }
                                }catch (Exception e){
                                    throw new RuntimeException(e);
                                }
                            }
                        }).start();
                    }
                }catch (Exception e){
                    throw new RuntimeException(e);
                }
            }
        }).start();

        return serverSocket;
    }
}
