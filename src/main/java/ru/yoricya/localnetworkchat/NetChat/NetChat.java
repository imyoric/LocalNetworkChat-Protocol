package ru.yoricya.localnetworkchat.NetChat;

import ru.yoricya.localnetworkchat.CryptoUtils;
import ru.yoricya.localnetworkchat.IPUtils;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

public class NetChat {
    public static boolean isDebug;
    private final byte[] rkBytes;
    public final String localAddr;
    public final ServerSocket serverSocket;
    public final MessageHandler messageHandler;
    public NetChat(String roomKey, MessageHandler messageHandler){
        if(roomKey.isEmpty()) roomKey = "0";
        rkBytes = CryptoUtils.getSHAKey(roomKey);
        localAddr = IPUtils.getV4()[0];
        this.messageHandler = messageHandler;

        try {
            serverSocket = initLocalServer(rkBytes, "devicePC", messageHandler);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    public void broadcastMessage(String msg){
        if(msg == null || msg.isEmpty()) return;
        broadCastMessage(localAddr, msg.getBytes(StandardCharsets.UTF_8), rkBytes, messageHandler);
    }
    public void broadcastMessage(byte[] msg){
        broadCastMessage(localAddr, msg, rkBytes, messageHandler);
    }
    public void sendMessage(String msg, String toIP){
        if(msg == null || msg.isEmpty()) return;
        messageOnlyForIp(toIP, msg.getBytes(StandardCharsets.UTF_8), rkBytes, messageHandler);
    }
    public void sendMessage(byte[] msg, String toIP){
        messageOnlyForIp(toIP, msg, rkBytes, messageHandler);
    }
    public List<InetAddress> listOfLocalLNCDevices(){
        return listOfLNCDevicesInLocalNet(localAddr);
    }

    static final HashMap<String, Socket> ConnectedSockets = new HashMap<>();
    static void broadCastMessage(String MyLocalIp, byte[] message, byte[] roomKey, MessageHandler handler){
        if(message == null || message.length == 0) return;

        //Copy message to message chunk in message buffer
        byte[] decodedData = new byte[message.length+64];
        System.arraycopy(message, 0, decodedData, 64, message.length);

        //Add control bytes
        decodedData[0] = 127;
        decodedData[1] = -127;

        //Encrypting message
        byte[] encodedData = CryptoUtils.encrypt(decodedData, roomKey);

        //Copy encrypted message to message chunk in data buffer
        byte[] data = new byte[encodedData.length + 32];
        System.arraycopy(encodedData, 0, data, 32, encodedData.length);

        //Add room key hash to RoomKeyHash Chunk in data buffer
        byte[] roomKeyHash = CryptoUtils.generateMd5(roomKey);
        System.arraycopy(roomKeyHash, 0, data, 0, roomKeyHash.length);

        //Add timestamp to timestamp chunk in data buffer
        byte[] timeUnit = ByteBuffer.allocate(8).putLong(System.currentTimeMillis()).array();
        System.arraycopy(timeUnit, 0, data, roomKeyHash.length, timeUnit.length);

        //Creating pool of local network ips
        String[] spl = MyLocalIp.split("\\.");
        String[] alAddress = new String[256];
        for(int i = 0; i!=alAddress.length; i++){
            alAddress[i] = spl[0]+"."+spl[1]+"."+spl[2]+"."+i;
        }

        //Broadcast message to all ips in local network
        for (String s : alAddress) {
            if (s.equals(MyLocalIp)) continue;

            Socket socket;
            synchronized (ConnectedSockets){
                socket = ConnectedSockets.get(s);
            }

            if(socket != null && !socket.isClosed()) try {
                sendToSocket(socket, data);
                handler.onMessageSendSuccessTo(socket.getInetAddress(), false);

                if (isDebug)
                    System.out.println("[DEBUG] (Connected Socket) Broadcasting success to: " + s);

                continue;
            }catch (Exception ignore){
                try {
                    socket.close();
                }catch (Exception e){e.printStackTrace();}
            }
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Socket clientSocket = new Socket();
                        clientSocket.connect(new InetSocketAddress(s, 47844), 2000);

                        sendToSocket(clientSocket, data);
                        handler.onMessageSendSuccessTo(clientSocket.getInetAddress(), true);

                        if(isDebug)
                            System.out.println("[DEBUG] (Opened New Socket) Broadcasting success to: "+s);
                        synchronized (ConnectedSockets){
                            ConnectedSockets.put(s, clientSocket);
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
    static void messageOnlyForIp(String toIP, byte[] message, byte[] roomKey, MessageHandler handler){
        if(message == null || message.length == 0) return;

        //Copy message to message chunk in message buffer
        byte[] decodedData = new byte[message.length+64];
        System.arraycopy(message, 0, decodedData, 64, message.length);

        //Add control bytes
        decodedData[0] = 127;
        decodedData[1] = -127;

        //Set reply byte
        decodedData[3] = 2;

        //Encrypting message
        byte[] encodedData = CryptoUtils.encrypt(decodedData, roomKey);

        //Copy encrypted message to message chunk in data buffer
        byte[] data = new byte[encodedData.length + 32];
        System.arraycopy(encodedData, 0, data, 32, encodedData.length);

        //Add room key hash to RoomKeyHash Chunk in data buffer
        byte[] roomKeyHash = CryptoUtils.generateMd5(roomKey);
        System.arraycopy(roomKeyHash, 0, data, 0, roomKeyHash.length);

        //Add time unit to TimeUnit chunk in data buffer
        byte[] timeUnit = ByteBuffer.allocate(8).putLong(System.currentTimeMillis()).array();
        System.arraycopy(timeUnit, 0, data, roomKeyHash.length, timeUnit.length);

        //Send message
        new Thread(new Runnable() {
            @Override
            public void run() {
                Socket socket;
                synchronized (ConnectedSockets){
                    socket = ConnectedSockets.get(toIP);
                }

                if(socket != null && !socket.isClosed()) try {
                    sendToSocket(socket, data);
                    handler.onMessageSendSuccessTo(socket.getInetAddress(), false);

                    if (isDebug)
                        System.out.println("[DEBUG] (Connected Socket) Broadcasting success to: " + toIP);

                    return;
                }catch (Exception ignore){
                    try {
                        socket.close();
                    }catch (Exception e){e.printStackTrace();}
                }

                try {
                    Socket clientSocket = new Socket();
                    clientSocket.connect(new InetSocketAddress(toIP, 47844), 2000);

                    sendToSocket(clientSocket, data);
                    handler.onMessageSendSuccessTo(clientSocket.getInetAddress(), true);

                    if(isDebug)
                        System.out.println("[DEBUG] (Opened New Socket) Broadcasting success to: "+toIP);
                    synchronized (ConnectedSockets){
                        ConnectedSockets.put(toIP, clientSocket);
                    }
                } catch (Exception e) {
                    synchronized (ConnectedSockets){
                        ConnectedSockets.remove(toIP);
                    }
                }
            }
        }).start();
    }
    static List<InetAddress> listOfLNCDevicesInLocalNet(String MyLocalIp){
        List<InetAddress> inetAddressList = new ArrayList<>();

        //Creating pool of local network ips
        String[] spl = MyLocalIp.split("\\.");
        String[] alAddress = new String[256];
        for(int i = 0; i!=alAddress.length; i++){
            alAddress[i] = spl[0]+"."+spl[1]+"."+spl[2]+"."+i;
        }

        List<CompletableFuture<InetAddress>> futureList = new ArrayList<>();

        //Broadcast message to all ips in local network
        for (String s : alAddress) {
            Socket socket;
            synchronized (ConnectedSockets){
                socket = ConnectedSockets.get(s);
            }

            if(socket != null && !socket.isClosed() && socket.isConnected() && !socket.isInputShutdown()){
                inetAddressList.add(socket.getInetAddress());
                continue;
            }

            CompletableFuture<InetAddress> future = new CompletableFuture<>();
            futureList.add(future);

            new Thread(new Runnable() {
                @Override
                public void run() {
                    try{
                        Socket clientSocket = new Socket();
                        clientSocket.connect(new InetSocketAddress(s, 47844), 2000);
                        future.complete(clientSocket.getInetAddress());
                        clientSocket.close();
                    }catch (Exception ignore) {}

                    if(!future.isDone()) future.complete(null);
                }
            }).start();
        }

        for(CompletableFuture<InetAddress> future: futureList){
            try{
                InetAddress addr = future.get();
                if(addr != null) inetAddressList.add(addr);
            }catch (Exception ignore){}
        }

        return inetAddressList;
    }
    public static void sendToSocket(Socket s, byte[] data) throws Exception{
        OutputStream clientOut = s.getOutputStream();

        //Add data len bytes
        clientOut.write(ByteBuffer.allocate(6).put((byte) 95).putInt(data.length).put((byte) -95).array());

        //Add data
        clientOut.write(data);
    }
    static ExecutorService ThreadPool = new ScheduledThreadPoolExecutor(Runtime.getRuntime().availableProcessors());
    static final HashMap<String, Long> TemporallyBanned = new HashMap<>();
    static final HashMap<String, Socket> AvailableClients = new HashMap<>();
    public static ServerSocket initLocalServer(byte[] roomKey, String deviceName, MessageHandler handler) throws Exception{
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

                                synchronized (TemporallyBanned){
                                    pb = TemporallyBanned.get(host);
                                }

                                //Check temporally ban
                                if(pb != null && (System.currentTimeMillis() - pb) < 1000) { //Temp ban 1sec
                                    try {
                                        s.close();
                                    }catch (Exception ignore){}
                                    return;
                                }

                                Socket cachedSocket;
                                synchronized (AvailableClients) {
                                    cachedSocket = AvailableClients.get(host);
                                }

                                try {
                                    if (cachedSocket.isConnected() || !cachedSocket.isClosed())
                                        cachedSocket.close();
                                }catch (Exception ignore){}

                                synchronized (AvailableClients) {
                                    AvailableClients.put(host, s);
                                }

                                new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        InputStream inputStream;

                                        try {
                                            inputStream = s.getInputStream();
                                        }catch (Exception e){
                                            e.printStackTrace();
                                            try{
                                                s.close();
                                            }catch (Exception ignore){}
                                            return;
                                        }

                                        while(!s.isClosed()){
                                            try{
                                                //Get data len
                                                byte[] dataLenBuffer = new byte[6];
                                                if(inputStream.read(dataLenBuffer) != 6){
                                                    s.close();
                                                    //Add temp ban
                                                    addTempBan(host);
                                                    break;
                                                }

                                                //Check is request for get device name
                                                if(dataLenBuffer[0] == 35 && dataLenBuffer[5] == -35){
                                                    byte[] deviceNameBuffer = deviceName.getBytes(StandardCharsets.UTF_8);

                                                    OutputStream oStr = s.getOutputStream();
                                                    oStr.write(ByteBuffer.allocate(6).put((byte) 30).putInt(deviceNameBuffer.length).put((byte) -30).array());
                                                    oStr.write(deviceNameBuffer);

                                                    s.close();
                                                    break;
                                                }

                                                //Check length control bytes
                                                if(dataLenBuffer[0] != 95 || dataLenBuffer[5] != -95){
                                                    s.close();
                                                    //Add temp ban
                                                    addTempBan(host);
                                                    break;
                                                }

                                                //Decoding data-length
                                                int dataLen = ByteBuffer.wrap(dataLenBuffer).getInt(1);

                                                //Get data bytes
                                                byte[] buffer = new byte[dataLen];
                                                if(inputStream.read(buffer) != dataLen){
                                                    s.close();
                                                    //Add temp ban
                                                    addTempBan(host);
                                                    break;
                                                }

                                                //Get room key hash bytes
                                                byte[] receivedRoomHash = new byte[16];
                                                System.arraycopy(buffer, 0, receivedRoomHash, 0, 16);

                                                //Check room key hash
                                                if(!Arrays.equals(receivedRoomHash, roomKeyHash)){
                                                    s.close();
                                                    //Add temp ban
                                                    addTempBan(host);
                                                    return;
                                                }

                                                //Get timestamp bytes
                                                byte[] timestampBuffer = new byte[8];
                                                System.arraycopy(buffer, 16, timestampBuffer, 0, 8);
                                                long timestamp = ByteBuffer.wrap(timestampBuffer).getLong();

                                                if(isDebug)
                                                    System.out.println("[DEBUG] ping: "+(System.currentTimeMillis() - timestamp)+"ms");

                                                //Decrypting
                                                byte[] decodedData = new byte[buffer.length-32];
                                                System.arraycopy(buffer, 32, decodedData, 0, decodedData.length);
                                                decodedData = CryptoUtils.decrypt(decodedData, roomKey);

                                                //Check is decrypting success, is len == 0 - decrypting failed
                                                if(decodedData.length == 0){
                                                    s.close();
                                                    //Add temp ban
                                                    addTempBan(host);
                                                    break;
                                                }

                                                //Check control bytes
                                                if(decodedData[0] != 127 || decodedData[1] != -127){
                                                    s.close();
                                                    //Add temp ban
                                                    addTempBan(host);
                                                    break;
                                                }

                                                //Check reply bytes
                                                boolean isReplyForYou = decodedData[3] == 2;

                                                //Decoding message
                                                byte[] decodedMessage = new byte[decodedData.length-64];
                                                System.arraycopy(decodedData, 64, decodedMessage, 0, decodedMessage.length);
                                                handler.onMessage(decodedMessage, s.getInetAddress(), isReplyForYou, timestamp);
                                            }catch (Exception e){
                                                try{
                                                    s.close();
                                                }catch (Exception ignore){}
                                                e.printStackTrace();
                                                handler.onErrorWhileDecoding(e, s.getInetAddress());
                                                break;
                                            }
                                        }
                                    }
                                }).start();
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

    static void addTempBan(String host){
        long cmls = System.currentTimeMillis();
        synchronized (TemporallyBanned){
            TemporallyBanned.put(host, cmls);
        }
    }
}
