package org.palmalabs.android.bluetooth.example;

import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.palmalabs.android.bluetooth.BluetoothService;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity implements
        BluetoothService.BluetoothDiscoveryListener,
        BluetoothService.BluetoothBondListener,
        BluetoothService.BluetoothRfcommConnectionListener,
        BluetoothService.BluetoothRfcommWriteListener {
    private static final String TAG = MainActivity.class.getSimpleName();

    private ListView mBluetoothDeviceListView;
    private BluetoothDeviceListViewAdapter mBluetoothDeviceListViewAdapter;
    private BluetoothAdapter mBluetoothAdapter;
    private ProgressDialog mProgressDialog;
    private BluetoothService mBluetoothService;

    private TextView mDeviceAddressTextView;
    private EditText mDataEditText;
    private Button mSendButton;

    private ServiceConnection mBluetoothServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "(BluetoothService) onServiceDisconnected");
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "(BluetoothService) onServiceConnected");

            BluetoothService.BluetoothServiceBinder binder = (BluetoothService.BluetoothServiceBinder) service;
            mBluetoothService = binder.getService();

            mBluetoothService.setDiscoveryListener(MainActivity.this);
            mBluetoothService.setBondListener(MainActivity.this);
            mBluetoothService.setRfcommConnectionListener(MainActivity.this);
            mBluetoothService.setRfcommWriteListener(MainActivity.this);

            List<BluetoothDevice> pairedDevices = mBluetoothService.getPairedDevices();

            if (pairedDevices.size() != 0) {
                // Put the paired devices in the list and do not scan
                mBluetoothDeviceListViewAdapter.clear();
                mBluetoothDeviceListViewAdapter.addAll(pairedDevices);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mBluetoothDeviceListView = (ListView) findViewById(R.id.list_view_bluetooth_device);
        mBluetoothDeviceListViewAdapter = new BluetoothDeviceListViewAdapter(this,
                0,
                new ArrayList<BluetoothDevice>());

        mBluetoothDeviceListView.setAdapter(mBluetoothDeviceListViewAdapter);
        mBluetoothDeviceListView.setOnItemClickListener(new OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> av, View v, int position, long id) {
                BluetoothDevice bluetoothDevice = (BluetoothDevice) mBluetoothDeviceListView
                        .getAdapter().getItem(position);

                // Try to connect to the selected device if not connected yet
                if (!mBluetoothService.isBluetoothSocketConnected(bluetoothDevice.getAddress())) {
                    Intent intent = new Intent(MainActivity.this, BluetoothService.class);
                    intent.setAction(BluetoothService.ACTION_RFCOMM_CONNECT);
                    intent.putExtra("device_address", bluetoothDevice.getAddress());
                    startService(intent);
                }
            }
        });

        mDeviceAddressTextView = (TextView) findViewById(R.id.text_view_device_address);
        mDataEditText = (EditText) findViewById(R.id.edit_text_data);
        mSendButton = (Button) findViewById(R.id.button_send);
        mSendButton.setEnabled(false);
        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String dataToSend = mDataEditText.getText().toString().trim();
                if (dataToSend.length() > 0) {
                    dataToSend = dataToSend + "\n";
                    Intent intent = new Intent(MainActivity.this, BluetoothService.class);
                    intent.setAction(BluetoothService.ACTION_RFCOMM_WRITE);
                    intent.putExtra("device_address", mDeviceAddressTextView.getText().toString());
                    intent.putExtra("data_bytes", dataToSend.getBytes());
                    startService(intent);
                }
            }
        });
    }

    @Override
    protected void onStart() {
        Log.d(TAG, "onStart");
        super.onStart();

        Intent intent = new Intent(this, BluetoothService.class);
        bindService(intent, mBluetoothServiceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop");
        super.onStop();

        if (mBluetoothService.getDiscoveryListener() instanceof MainActivity) {
            mBluetoothService.setDiscoveryListener(null);
        }
        if (mBluetoothService.getBondListener() instanceof MainActivity) {
            mBluetoothService.setBondListener(null);
        }
        if (mBluetoothService.getRfcommConnectionListener() instanceof MainActivity) {
            mBluetoothService.setRfcommConnectionListener(null);
        }
        if (mBluetoothService.getRfcommWriteListener() instanceof MainActivity) {
            mBluetoothService.setRfcommWriteListener(null);
        }
        unbindService(mBluetoothServiceConnection);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_scan:
                mBluetoothDeviceListViewAdapter.clear();
                Intent intent = new Intent(this, BluetoothService.class);
                intent.setAction(BluetoothService.ACTION_DISCOVER_DEVICES);
                startService(intent);
                return true;
        }
        return false;
    }

    @Override
    public void onDiscoveryStarted() {
        mProgressDialog = new ProgressDialog(this);
        mProgressDialog.setMessage(getResources().getString(R.string.scanning));
        mProgressDialog.setIndeterminate(true);
        mProgressDialog.setCancelable(false);
        mProgressDialog.show();
    }

    @Override
    public void onDiscoveryFinished() {
        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            mProgressDialog.dismiss();
        }
    }

    @Override
    public void onDeviceFound(BluetoothDevice bluetoothDevice) {
        if (!mBluetoothDeviceListViewAdapter.contains(bluetoothDevice)) {
            mBluetoothDeviceListViewAdapter.add(bluetoothDevice);
            mBluetoothDeviceListViewAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onDeviceUnbonded(BluetoothDevice bluetoothDevice) {
        mBluetoothDeviceListViewAdapter.notifyDataSetChanged();
    }

    @Override
    public void onDeviceBonded(BluetoothDevice bluetoothDevice) {
        mBluetoothDeviceListViewAdapter.notifyDataSetChanged();
    }

    @Override
    public void onRfcommConnectionStarted(BluetoothDevice bluetoothDevice) {
        mProgressDialog = new ProgressDialog(this);
        mProgressDialog.setMessage(getResources().getString(R.string.connecting));
        mProgressDialog.setIndeterminate(true);
        mProgressDialog.setCancelable(false);
        mProgressDialog.show();
    }

    @Override
    public void onRfcommConnectionError(BluetoothDevice bluetoothDevice) {
        Log.d(TAG, "onRfcommConnectionError: " + bluetoothDevice.getAddress());
        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            mProgressDialog.dismiss();
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this,
                        getResources().getString(R.string.error_cant_connect_to_bluetooth_device),
                        Toast.LENGTH_LONG)
                        .show();
            }
        });
    }

    @Override
    public void onRfcommConnectionEstablished(final BluetoothDevice bluetoothDevice) {
        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            mProgressDialog.dismiss();
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this,
                        getResources().getString(R.string.connected),
                        Toast.LENGTH_LONG)
                        .show();
                mSendButton.setEnabled(true);
                mDeviceAddressTextView.setText(bluetoothDevice.getAddress());
            }
        });

        // Request BluetoothService to monitor the connection
        Intent intent = new Intent(this, BluetoothService.class);
        intent.setAction(BluetoothService.ACTION_RFCOMM_MONITOR);
        intent.putExtra("device_address", bluetoothDevice.getAddress());
        startService(intent);
    }

    @Override
    public void onRfcommDisconnected(BluetoothDevice bluetoothDevice) {
        Log.d(TAG, "onRfcommDisconnected: " + bluetoothDevice.getAddress());

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this,
                        getString(R.string.bluetooth_device_disconnected),
                        Toast.LENGTH_LONG)
                        .show();
            }
        });
    }

    @Override
    public void onRfcommWriteStarted() {
        Log.d(TAG, "onRfcommWriteStarted");
    }

    @Override
    public void onRfcommWriteFinished() {
        Log.d(TAG, "onRfcommWriteFinished");

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this,
                        getString(R.string.write_finished),
                        Toast.LENGTH_SHORT)
                        .show();
                mDataEditText.setText("");
            }
        });
    }

    @Override
    public void onRfcommWriteError() {
        Log.d(TAG, "onRfcommWriteError");

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this,
                        getString(R.string.write_error),
                        Toast.LENGTH_SHORT)
                        .show();
            }
        });
    }
}
