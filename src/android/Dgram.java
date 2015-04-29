package org.apache.cordova.dgram;

import android.util.Log;
import android.util.SparseArray;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

public class Dgram extends CordovaPlugin {
    private static final String TAG = Dgram.class.getSimpleName();

    SparseArray<DatagramSocket> m_sockets;
    SparseArray<SocketListener> m_listeners;
    SparseArray<SocketConfig> m_config;

    public Dgram() {
        m_sockets = new SparseArray<DatagramSocket>();
        m_listeners = new SparseArray<SocketListener>();
        m_config = new SparseArray<SocketConfig>();
    }

    private class SocketConfig {
        NetworkInterface networkInterface = null;
        int port;
    }

    private class SocketListener extends Thread {
        int m_socketId;
        DatagramSocket m_socket;

        public SocketListener(int id, DatagramSocket socket) {
            this.m_socketId = id;
            this.m_socket = socket;
        }

        public void run() {
            byte[] data = new byte[2048]; // investigate MSG_PEEK and MSG_TRUNC in java
            DatagramPacket packet = new DatagramPacket(data, data.length);
            while (true) {
                try {
                    packet.setLength(data.length); // reset packet length due to incomplete UDP Packet received
                    this.m_socket.receive(packet);
                    String msg = new String(data, 0, packet.getLength(), "UTF-8")
                            .replace("'", "\'")
                            .replace("\r", "\\r")
                            .replace("\n", "\\n");
                    String address = packet.getAddress().getHostAddress();
                    int port = packet.getPort();

                    Dgram.this.webView.sendJavascript(
                            "cordova.require('cordova-plugin-dgram.dgram')._onMessage("
                                    + this.m_socketId + ","
                                    + "'" + msg + "',"
                                    + "'" + address + "',"
                                    + port + ")");
                } catch (Exception e) {
                    Log.d(TAG, "Receive exception:" + e.toString());
                    return;
                }
            }
        }
    }

    public NetworkInterface getActiveWifiInterface() throws SocketException {
        NetworkInterface activeInterface = null;
        for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();){
            NetworkInterface intf = en.nextElement();
            if (intf.isUp() && intf.supportsMulticast() && intf.getInterfaceAddresses().size() > 0 && !intf.isLoopback() && !intf.isVirtual() && !intf.isPointToPoint()){
                if (activeInterface == null){
                    activeInterface = intf;
                }else{
                    if (!activeInterface.getName().contains("wlan") && !activeInterface.getName().contains("ap")){
                        activeInterface = intf;
                    }
                }
            }
        }
        Log.d(TAG, "Using Network Interface: " + activeInterface.getName());
        return activeInterface;
    }

    @Override
    public boolean execute(String action, JSONArray data, final CallbackContext callbackContext) throws JSONException {
        final int id = data.getInt(0);
        DatagramSocket socket = m_sockets.get(id);
        SocketConfig config = m_config.get(id);
        if (action.equals("create")) {
            assert config == null;
            assert socket == null;
            final boolean isMulticast = data.getBoolean(1);
            config = new SocketConfig();
            config.port = data.getInt(2);
            try {
                if (isMulticast) {
                    MulticastSocket mcSocket = new MulticastSocket(null);
                    config.networkInterface = getActiveWifiInterface();
                    mcSocket.setNetworkInterface(config.networkInterface);
                    socket = mcSocket;
                } else {
                    socket = new DatagramSocket(null);
                }
                m_config.put(id, config);
                m_sockets.put(id, socket);
                callbackContext.success();
            } catch (Exception e) {
                Log.d(TAG, "Create exception:" + e.toString());
                callbackContext.error(e.toString());
            }
        } else if (action.equals("bind")) {
            try {
                socket.bind(new InetSocketAddress(config.port));
                SocketListener listener = new SocketListener(id, socket);
                m_listeners.put(id, listener);
                listener.start();
                callbackContext.success();
            } catch (Exception e) {
                Log.d(TAG, "Bind exception:" + e.toString());
                callbackContext.error(e.toString());
            }
        } else if (action.equals("joinGroup")) {
            final String address = data.getString(1);
            MulticastSocket msocket = (MulticastSocket) socket;
            try {
                msocket.joinGroup(new InetSocketAddress(address, config.port), config.networkInterface);
//                msocket.joinGroup(InetAddress.getByName(address));
                callbackContext.success();
            } catch (Exception e) {
                Log.d(TAG, "joinGroup exception:" + e.toString());
                callbackContext.error(e.toString());
            }
        } else if (action.equals("leaveGroup")) {
            final String address = data.getString(1);
            MulticastSocket msocket = (MulticastSocket) socket;
            try {
                msocket.leaveGroup(new InetSocketAddress(address, config.port), config.networkInterface);
//                msocket.leaveGroup(InetAddress.getByName(address));
                callbackContext.success();
            } catch (Exception e) {
                Log.d(TAG, "leaveGroup exception:" + e.toString());
                callbackContext.error(e.toString());
            }
        } else if (action.equals("send")) {
            final String message = data.getString(1);
            final String address = data.getString(2);
            final int port = data.getInt(3);
            final DatagramSocket localSocket = socket;

            // threadded send to prevent NetworkOnMainThreadException
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    try {
                        byte[] bytes = message.getBytes("UTF-8");
                        DatagramPacket packet = new DatagramPacket(bytes, bytes.length, InetAddress.getByName(address), port);
                        localSocket.send(packet);
                        callbackContext.success(message);
                    } catch (IOException ioe) {
                        Log.d(TAG, "send exception:" + ioe.toString());
                        callbackContext.error("IOException: " + ioe.toString());
                    }
                }
            });
        } else if (action.equals("close")) {
            if (socket != null) {
                socket.close();
                m_sockets.remove(id);
                SocketListener listener = m_listeners.get(id);
                if (listener != null) {
                    listener.interrupt();
                    m_listeners.remove(id);
                }
            }
            callbackContext.success();
        } else {
            return false; // 'MethodNotFound'
        }
        return true;
    }
}
