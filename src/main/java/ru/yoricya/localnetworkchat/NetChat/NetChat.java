package ru.yoricya.localnetworkchat.NetChat;

import ru.yoricya.localnetworkchat.CryptoUtils;
import ru.yoricya.localnetworkchat.IPUtils;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

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
//    static final HashMap<String, Socket> ConnectedSockets = new HashMap<>();
    static void broadCastMessage(String MyLocalIp, String msg, byte[] roomKey){
        if(msg == null || msg.isEmpty()) return;

        byte[] message = msg.getBytes();

        byte[] decodedData = new byte[message.length+64];
        System.arraycopy(message, 0, decodedData, 64, message.length);

        decodedData[0] = 127;
        decodedData[1] = -127;

        byte[] encodedData = CryptoUtils.encrypt(decodedData, roomKey);

        byte[] data = new byte[encodedData.length + 32];
        System.arraycopy(encodedData, 0, data, 32, encodedData.length);

        byte[] roomKeyHash = CryptoUtils.generateMd5(roomKey);
        System.arraycopy(roomKeyHash, 0, data, 0, roomKeyHash.length);

        byte[] timeUnit = ByteBuffer.allocate(8).putLong(System.currentTimeMillis()).array();
        System.arraycopy(timeUnit, 0, data, roomKeyHash.length, timeUnit.length);

        String[] spl = MyLocalIp.split("\\.");
        String[] alAddress = new String[256];
        for(int i = 0; i!=alAddress.length; i++){
            alAddress[i] = spl[0]+"."+spl[1]+"."+spl[2]+"."+i;
        }

        for (String s : alAddress) {
            if (s.equals(MyLocalIp)) continue;

//            Socket socket;
//            synchronized (ConnectedSockets){
//                socket = ConnectedSockets.get(s);
//            }
//
//            if(socket != null && !socket.isClosed()) try {
//                sendToSocket(socket, data);
//
//                if (isDebug)
//                    System.out.println("[DEBUG] (Connected Socket) Broadcasting success to: " + s);
//                continue;
//            }catch (Exception ignore){
//                try {
//                    socket.close();
//                }catch (Exception e){e.printStackTrace();}
//            }
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Socket clientSocket = new Socket();
                        clientSocket.connect(new InetSocketAddress(s, 47844), 3000);
                        sendToSocket(clientSocket, data);
                        if(isDebug)
                            System.out.println("[DEBUG] (Opened New Socket) Broadcasting success to: "+s);
//                        synchronized (ConnectedSockets){
//                            ConnectedSockets.put(s, clientSocket);
//                        }
                    } catch (Exception e) {
//                        synchronized (ConnectedSockets){
//                            ConnectedSockets.remove(s);
//                        }
                    }
                }
            }).start();
        }
    }

    public static void sendToSocket(Socket s, byte[] data) throws Exception{
        OutputStream clientOut = s.getOutputStream();
        clientOut.write(ByteBuffer.allocate(4).putInt(data.length).array());
        clientOut.write(data);
        s.close();
    }
    static ExecutorService ThreadPool = new ScheduledThreadPoolExecutor(Runtime.getRuntime().availableProcessors());
    static final HashMap<String, Long> PermanentlyBanned = new HashMap<>();
    public static ServerSocket initLocalServer(byte[] roomKey, MessageHandler handler) throws Exception{
        ServerSocket serverSocket = new ServerSocket(47844);
        byte[] roomKeyHash = CryptoUtils.generateMd5(roomKey);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (true) {
                        Socket s = serverSocket.accept();
                        ThreadPool.execute(new Runnable() {
                            @Override
                            public void run() {
                                String host = s.getInetAddress().getHostAddress();
                                Long pb;

                                synchronized (PermanentlyBanned){
                                    pb = PermanentlyBanned.get(host);
                                }

                                if(pb != null && (System.currentTimeMillis() - pb) < 1000) {
                                    try {
                                        s.close();
                                    }catch (Exception ignore){}
                                    return;
                                }

                                try{
                                    InputStream inputStream = s.getInputStream();

                                    byte[] dataLenBuffer = new byte[4];
                                    inputStream.read(dataLenBuffer);

                                    int dataLen = ByteBuffer.wrap(dataLenBuffer).getInt();

                                    byte[] buffer = new byte[dataLen];
                                    inputStream.read(buffer);

                                    byte[] receivedRoomHash = new byte[16];
                                    System.arraycopy(buffer, 0, receivedRoomHash, 0, 16);

                                    if(!Arrays.equals(receivedRoomHash, roomKeyHash)){
                                        s.close();
                                        long cmls = System.currentTimeMillis();
                                        synchronized (PermanentlyBanned){
                                            PermanentlyBanned.put(host, cmls);
                                        }
                                        return;
                                    }

                                    byte[] timeUnitBuffer = new byte[8];
                                    System.arraycopy(buffer, 16, timeUnitBuffer, 0, 8);

                                    long timeUnit = ByteBuffer.wrap(timeUnitBuffer).getLong();

                                    if(isDebug)
                                        System.out.println("[DEBUG] ping: "+(System.currentTimeMillis() - timeUnit)+"ms");

                                    byte[] decodedData = new byte[buffer.length-32];
                                    System.arraycopy(buffer, 32, decodedData, 0, decodedData.length);
                                    decodedData = CryptoUtils.decrypt(decodedData, roomKey);

                                    if(decodedData.length == 0){
                                        s.close();
                                        long cmls = System.currentTimeMillis();
                                        synchronized (PermanentlyBanned){
                                            PermanentlyBanned.put(host, cmls);
                                        }
                                        return;
                                    }

                                    if(decodedData[0] != 127 || decodedData[1] != -127){
                                        s.close();
                                        long cmls = System.currentTimeMillis();
                                        synchronized (PermanentlyBanned){
                                            PermanentlyBanned.put(host, cmls);
                                        }
                                        return;
                                    }

                                    byte[] decodedMessage = new byte[decodedData.length-64];
                                    System.arraycopy(decodedData, 64, decodedMessage, 0, decodedMessage.length);
                                    handler.onMessage(new String(decodedMessage).trim(), s.getInetAddress());

                                    s.close();
                                }catch (Exception e){
                                    try{
                                        s.close();
                                    }catch (Exception ignore){}
                                    handler.onErrorWhileDecoding(e, s.getInetAddress());
                                }
                            }
                        });

                    }
                }catch (Exception e){
                    throw new RuntimeException(e);
                }
            }
        }).start();

        return serverSocket;
    }
}
