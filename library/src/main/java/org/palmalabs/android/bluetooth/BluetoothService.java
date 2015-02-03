package org.palmalabs.android.bluetooth;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class BluetoothService extends Service {
    public static final String ACTION_DISCOVER_DEVICES = "org.palmalabs.android.bluetooth" +
            ".BluetoothService.ACTION_DISCOVER_DEVICES";
    public static final String ACTION_PAIR = "org.palmalabs.android.bluetooth" +
            ".BluetoothService.ACTION_PAIR";
    public static final String ACTION_UNPAIR = "org.palmalabs.android.bluetooth" +
            ".BluetoothService.ACTION_UNPAIR";
    public static final String ACTION_RFCOMM_CONNECT = "org.palmalabs.android.bluetooth" +
            ".BluetoothService.ACTION_RFCOMM_CONNECT";
    public static final String ACTION_RFCOMM_WRITE = "org.palmalabs.android.bluetooth" +
            ".BluetoothService.ACTION_RFCOMM_WRITE";
    public static final String ACTION_RFCOMM_MONITOR = "org.palmalabs.android.bluetooth" +
            ".BluetoothService.ACTION_RFCOMM_MONITOR";
    public static final String ACTION_RFCOMM_CONNECTED = "org.palmalabs.android.bluetooth" +
            ".BluetoothService.ACTION_RFCOMM_CONNECTED";
    public static final String ACTION_RFCOMM_DISCONNECTED = "org.palmalabs.android.bluetooth" +
            ".BluetoothService.ACTION_RFCOMM_DISCONNECTED";
    private static final String TAG = BluetoothService.class.getSimpleName();
    public static final String ACTION_TASK_REMOVED = "org.palmalabs.android.bluetooth" +
            ".BluetoothService.ACTION_TASK_REMOVED";
    private BluetoothAdapter mBluetoothAdapter;
    private IBinder mBinder = new BluetoothServiceBinder();
    private BluetoothDiscoveryListener mDiscoveryListener;
    private BluetoothBondListener mBondListener;
    private BluetoothRfcommConnectionListener mRfcommConnectionListener;
    private BluetoothRfcommWriteListener mRfcommWriteListener;
    private Map<String, BluetoothSocket> mBluetoothSockets;
    private boolean mMonitoring;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(BluetoothDevice.ACTION_FOUND)) {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                Log.d(TAG, "Bluetooth device found: " + device.getAddress());

                if (mDiscoveryListener != null) {
                    // Tell the users of this service we've found a device
                    mDiscoveryListener.onDeviceFound(device);
                }
            }
            else if (action.equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
                    Log.d(TAG, "Paired: " + device.getAddress());
                    if (mBondListener != null) {
                        mBondListener.onDeviceBonded(device);
                    }
                }
                else if (device.getBondState() == BluetoothDevice.BOND_NONE) {
                    Log.d(TAG, "Unpaired: " + device.getAddress());
                    if (mBondListener != null) {
                        mBondListener.onDeviceUnbonded(device);
                    }
                }
            }
            else if (action.equals(BluetoothAdapter.ACTION_DISCOVERY_STARTED)) {
                Log.d(TAG, "Started bluetooth discovery");
                if (mDiscoveryListener != null) {
                    // Tell the users of this service we've started looking for devices
                    mDiscoveryListener.onDiscoveryStarted();
                }
            }
            else if (action.equals(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)) {
                Log.d(TAG, "Finished bluetooth discovery");
                if (mDiscoveryListener != null) {
                    // Tell the users of this service we've finished discovery process
                    mDiscoveryListener.onDiscoveryFinished();
                }
            }
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind");
        return mBinder;
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");
        super.onCreate();

        mBluetoothAdapter = mBluetoothAdapter.getDefaultAdapter();

        mBluetoothSockets = new HashMap<String, BluetoothSocket>();

        // Register broadcast receivers for Bluetooth events
        registerReceiver(mReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
        registerReceiver(mReceiver, new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED));
        registerReceiver(mReceiver, new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_STARTED));
        registerReceiver(mReceiver, new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED));
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        super.onDestroy();
    }

    @Override
    public void onRebind(Intent intent) {
        Log.d(TAG, "onRebind");
        super.onRebind(intent);

        // Re-register broadcast receivers for Bluetooth events
        registerReceiver(mReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
        registerReceiver(mReceiver, new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED));
        registerReceiver(mReceiver, new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_STARTED));
        registerReceiver(mReceiver, new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED));
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "onUnbind");
        unregisterReceiver(mReceiver);

        return true;
    }

    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (intent.getAction() == ACTION_DISCOVER_DEVICES) {
            // Do a full discovery of devices
            mBluetoothAdapter.startDiscovery();
        }
        else if (intent.getAction() == ACTION_PAIR) {
            Log.d(TAG, "Pair requested to device = " + intent.getStringExtra("device_address"));
            BluetoothDevice bluetoothDevice = mBluetoothAdapter.getRemoteDevice(intent
                    .getStringExtra("device_address"));
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                bluetoothDevice.createBond();
            }
            else {
                try {
                    Method method = bluetoothDevice.getClass().getMethod("createBond",
                            (Class[]) null);
                    method.invoke(bluetoothDevice, (Object[]) null);
                }
                catch (Exception e) {
                    Log.d(TAG, "Error when pairing to " + intent.getStringExtra("device_address") +
                            ": " + e.getMessage());
                }
            }
        }
        else if (intent.getAction() == ACTION_UNPAIR) {
            Log.d(TAG, "Unpair requested from device = " + intent.getStringExtra("device_address"));
            BluetoothDevice bluetoothDevice = mBluetoothAdapter
                    .getRemoteDevice(intent.getStringExtra("device_address"));
            try {
                Method method = bluetoothDevice.getClass().getMethod("removeBond",
                        (Class[]) null);
                method.invoke(bluetoothDevice, (Object[]) null);
            }
            catch (Exception e) {
                Log.d(TAG, "Error when unpairing from " + intent.getStringExtra("device_address") +
                        ": " + e.getMessage());
            }

        }
        else if (intent.getAction() == ACTION_RFCOMM_CONNECT) {
            Log.d(TAG, "RFCOMM connection requested to device = " + intent.getStringExtra("device_address"));
            final BluetoothDevice bluetoothDevice = mBluetoothAdapter
                    .getRemoteDevice(intent.getStringExtra("device_address"));
            if (mRfcommConnectionListener != null) {
                mRfcommConnectionListener.onRfcommConnectionStarted(bluetoothDevice);
            }
            new Thread(new Runnable() {
                @Override
                public void run() {
                    BluetoothSocket bluetoothSocket = mBluetoothSockets.get(bluetoothDevice.getAddress());
                    try {
                        if (bluetoothSocket != null) {
                            bluetoothSocket.close();
                            bluetoothSocket = null;
                        }
                        bluetoothSocket = bluetoothDevice
                                .createInsecureRfcommSocketToServiceRecord(UUID.fromString
                                        ("00001101-0000-1000-8000-00805F9B34FB"));
                        bluetoothSocket.connect();
                        mBluetoothSockets.put(bluetoothDevice.getAddress(), bluetoothSocket);
                        if (mRfcommConnectionListener != null) {
                            mRfcommConnectionListener.onRfcommConnectionEstablished(bluetoothDevice);
                        }
                        sendBroadcast(new Intent(ACTION_RFCOMM_CONNECTED).putExtra
                                ("bluetooth_device", bluetoothDevice));
                    }
                    catch (IOException e) {
                        Log.d(TAG, "Error while creating RFCOMM socket: " + e.getMessage());
                        if (mRfcommConnectionListener != null) {
                            mRfcommConnectionListener.onRfcommConnectionError(bluetoothDevice);
                        }
                    }

                }
            }).start();
        }
        else if (intent.getAction() == ACTION_RFCOMM_WRITE) {
            Log.d(TAG, "RFCOMM write requested, data = " +
                    new String(intent.getByteArrayExtra("data_bytes")));

            if (mRfcommWriteListener != null) {
                mRfcommWriteListener.onRfcommWriteStarted();
            }

            final BluetoothDevice bluetoothDevice = mBluetoothAdapter
                    .getRemoteDevice(intent.getStringExtra("device_address"));
            BluetoothSocket bluetoothSocket = mBluetoothSockets.get(bluetoothDevice.getAddress());
            if (bluetoothSocket != null) {
                try {
                    synchronized (bluetoothSocket.getOutputStream()) {
                        bluetoothSocket.getOutputStream()
                                .write(intent.getByteArrayExtra("data_bytes"));
                    }
                    if (mRfcommWriteListener != null) {
                        mRfcommWriteListener.onRfcommWriteFinished();
                    }
                }
                catch (IOException e) {
                    Log.d(TAG, "Error when writing to Bluetooth device: " + e.getMessage());
                    if (mRfcommWriteListener != null) {
                        mRfcommWriteListener.onRfcommWriteError();
                    }
                }
            }
            else {
                if (mRfcommWriteListener != null) {
                    mRfcommWriteListener.onRfcommWriteError();
                }
            }
        }
        else if (intent.getAction() == ACTION_RFCOMM_MONITOR) {
            Log.d(TAG, "RFCOMM monitor requested");
            final BluetoothDevice bluetoothDevice = mBluetoothAdapter
                    .getRemoteDevice(intent.getStringExtra("device_address"));
            mMonitoring = true;
            final BluetoothSocket bluetoothSocket = mBluetoothSockets
                    .get(bluetoothDevice.getAddress());

            new Thread(new Runnable() {
                @Override
                public void run() {
                    while (mMonitoring) {
                        try {
                            synchronized (bluetoothSocket.getOutputStream()) {
                                bluetoothSocket.getOutputStream().write(0);
                            }
                            Thread.sleep(10000);
                        }
                        catch (IOException e) {
                            Log.d(TAG, "Disconnection detected from "
                                    + bluetoothDevice.getAddress());
                            if (mRfcommConnectionListener != null) {
                                mRfcommConnectionListener.onRfcommDisconnected(bluetoothDevice);
                            }
                            sendBroadcast(new Intent(ACTION_RFCOMM_DISCONNECTED).putExtra
                                    ("bluetooth_device", bluetoothDevice));
                            try {
                                bluetoothSocket.close();
                                mBluetoothSockets.remove(bluetoothDevice.getAddress());
                            }
                            catch (IOException e1) {
                                Log.d(TAG, "Error closing Bluetooth socket: " + e1.getMessage());
                            }
                            return;
                        }
                        catch (InterruptedException e) {
                            Log.d(TAG, "Monitor thread interrupted");
                        }
                    }
                }
            }).start();
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.d(TAG, "onTaskRemoved");
        mMonitoring = false;
        for (String deviceAddress : mBluetoothSockets.keySet()) {
            BluetoothSocket bluetoothSocket = mBluetoothSockets.get(deviceAddress);
            if (bluetoothSocket != null) {
                try {
                    Log.d(TAG, "Closing socket " + deviceAddress);
                    bluetoothSocket.close();
                }
                catch (IOException e) {
                    Log.d(TAG, "Closing socket error: " + e.getMessage());
                }
            }
            mBluetoothSockets.remove(bluetoothSocket);
            bluetoothSocket = null;
        }
        sendBroadcast(new Intent(ACTION_TASK_REMOVED));
        super.onTaskRemoved(rootIntent);
    }

    public BluetoothDiscoveryListener getDiscoveryListener() {
        return mDiscoveryListener;
    }

    public void setDiscoveryListener(BluetoothDiscoveryListener discoveryListener) {
        mDiscoveryListener = discoveryListener;
    }

    public BluetoothBondListener getBondListener() {
        return mBondListener;
    }

    public void setBondListener(BluetoothBondListener bondListener) {
        mBondListener = bondListener;
    }

    public BluetoothRfcommConnectionListener getRfcommConnectionListener() {
        return mRfcommConnectionListener;
    }

    public void setRfcommConnectionListener(BluetoothRfcommConnectionListener
            rfcommConnectionListener) {
        mRfcommConnectionListener = rfcommConnectionListener;
    }

    public BluetoothRfcommWriteListener getRfcommWriteListener() {
        return mRfcommWriteListener;
    }

    public void setRfcommWriteListener(BluetoothRfcommWriteListener rfcommWriteListener) {
        mRfcommWriteListener = rfcommWriteListener;
    }

    public boolean isBluetoothSocketConnected(String deviceAddress) {
        BluetoothSocket bluetoothSocket = mBluetoothSockets.get(deviceAddress);
        if (bluetoothSocket != null) {
            return true;
        }

        return false;
    }

    public List<BluetoothDevice> getPairedDevices() {
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        return new ArrayList<BluetoothDevice>(pairedDevices);
    }

    public class BluetoothServiceBinder extends Binder {
        public BluetoothService getService() {
            return BluetoothService.this;
        }
    }

    public interface BluetoothDiscoveryListener {
        public void onDiscoveryStarted();
        public void onDiscoveryFinished();
        public void onDeviceFound(BluetoothDevice bluetoothDevice);
    }

    public interface BluetoothBondListener {
        public void onDeviceUnbonded(BluetoothDevice bluetoothDevice);
        public void onDeviceBonded(BluetoothDevice bluetoothDevice);
    }

    public interface BluetoothRfcommConnectionListener {
        public void onRfcommConnectionStarted(BluetoothDevice bluetoothDevice);
        public void onRfcommConnectionError(BluetoothDevice bluetoothDevice);
        public void onRfcommConnectionEstablished(BluetoothDevice bluetoothDevice);
        public void onRfcommDisconnected(BluetoothDevice bluetoothDevice);
    }

    public interface BluetoothRfcommWriteListener {
        public void onRfcommWriteStarted();
        public void onRfcommWriteFinished();
        public void onRfcommWriteError();
    }
}
