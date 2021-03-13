package com.example.canreader;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.RequiresApi;

import com.example.canreader.R;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

public class MainActivity extends Activity {
    BluetoothSocket mmSocket;
    BluetoothDevice mmDevice = null;
    final byte delimiter = 35;
    int readBufferPosition = 0;
    private final static int REQUEST_ENABLE_BT = 1;
    private BluetoothAdapter mmBluetoothAdapter;
    private TextView node1;
    private TextView node2;
    private TextView node3;
    private Set<BluetoothDevice> pairedDevices;
    List<BluetoothDevice> PairedDevices;

    public void sendMessage(String send_message){
        UUID uuid = UUID.fromString("ae14f5e2-9eb6-4015-8457-824d76384ba0");
        try{
            mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid);
            if(!mmSocket.isConnected()){
                mmSocket.connect();
            }
            String message = send_message;
            OutputStream mmOutputStream = mmSocket.getOutputStream();
            mmOutputStream.write(message.getBytes());
        } catch(IOException e){
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("CANREADER");
        setContentView(R.layout.activity_main);
        node1 = (TextView) findViewById(R.id.node1);
        //node2 = (TextView) findViewById(R.id.node2);
        //node3 = (TextView) findViewById(R.id.node3);

        final Handler mmHandler = new Handler();
        final BluetoothManager mmBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mmBluetoothAdapter = mmBluetoothManager.getAdapter();
        pairedDevices = mmBluetoothAdapter.getBondedDevices();
        PairedDevices = new ArrayList<>(pairedDevices);
        for (int i = 0; i < PairedDevices.size(); i++) {
            String name = PairedDevices.get(i).getName();
            if (name.equals("raspberrypi")) {
                mmDevice = PairedDevices.get(i);
                break;
            }
        }
        if (mmBluetoothAdapter == null || !mmBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
        final class workerThread implements Runnable {
            private String message;

            public workerThread(String msg) {
                message = msg;
            }

            public void run() {
                sendMessage(message);
                while (!Thread.currentThread().isInterrupted()) {
                    int bytesAvailable;
                    boolean workDone = false;
                    try {
                        final InputStream mmInputstream;
                        mmInputstream = mmSocket.getInputStream();
                        bytesAvailable = mmInputstream.available();
                        if (bytesAvailable > 0) {
                            byte[] packetBytes = new byte[bytesAvailable];
                            Log.e("Bytes recieved from", "Raspberry Pi");
                            byte[] readBuffer = new byte[1024];
                            mmInputstream.read(packetBytes);
                            for (int i = 0; i < bytesAvailable; i++) {
                                byte b = packetBytes[i];
                                if (b == delimiter) {
                                    byte[] encodedBytes = new byte[readBufferPosition];
                                    System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                                    final String data = new String(encodedBytes, "US-ASCII");
                                    readBufferPosition = 0;
                                    mmHandler.post(new Runnable() {
                                        public void run() {
                                            if (message == "node1") {
                                                node1.setText(data);
                                            } else if (message == "node2") {
                                                node2.setText(data);
                                            } else if (message == "node3") {
                                                node3.setText(data);
                                            }
                                        }
                                    });
                                    workDone = true;
                                    break;
                                } else {
                                    readBuffer[readBufferPosition++] = b;
                                }
                            }
                            if (workDone == true) {
                                mmSocket.close();
                                break;
                            }
                        }
                    } catch (IOException e) {
                        //TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }
        }
        final Handler handler = new Handler();
        Timer timer = new Timer(false);
        TimerTask timerTask = new TimerTask() {

            @Override
            public void run() {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        (new Thread(new workerThread("node1"))).start();
                    }
                });
            }
        };
        timer.scheduleAtFixedRate(timerTask, 1000, 1000); // every 1 seconds.
    }
}