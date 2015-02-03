package org.palmalabs.android.bluetooth.example;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;

import org.palmalabs.android.bluetooth.BluetoothService;
import org.palmalabs.android.bluetooth.example.R;

import java.util.List;

public class BluetoothDeviceListViewAdapter extends ArrayAdapter<BluetoothDevice> {

    public BluetoothDeviceListViewAdapter(Context context, int textViewResourceId,
            List<BluetoothDevice> bluetoothDevices) {
        super(context, textViewResourceId, bluetoothDevices);
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        View v = convertView;
        ViewHolder viewHolder;
        if (v == null) {
            viewHolder = new ViewHolder();
            LayoutInflater vi = (LayoutInflater) getContext().getSystemService(
                    Context.LAYOUT_INFLATER_SERVICE);
            v = vi.inflate(R.layout.list_item_bluetooth_device, parent, false);
            viewHolder.mDeviceNameTextView = (TextView) v.findViewById(
                    R.id.text_view_device_name);
            viewHolder.mDeviceAddressTextView = (TextView) v.findViewById(
                    R.id.text_view_device_address);
            viewHolder.mPairUnpairButton = (Button) v.findViewById(
                    R.id.button_pair_unpair);
            v.setTag(viewHolder);
        }
        else {
            viewHolder = (ViewHolder) v.getTag();
        }

        final BluetoothDevice bluetoothDevice = getItem(position);
        viewHolder.mDeviceNameTextView.setText(bluetoothDevice.getName());
        viewHolder.mDeviceAddressTextView.setText(bluetoothDevice.getAddress());

        if (bluetoothDevice.getBondState() == BluetoothDevice.BOND_BONDED) {
            viewHolder.mPairUnpairButton.setText(getContext().getString(R.string.unpair));
            viewHolder.mPairUnpairButton.setOnClickListener(new OnClickListener() {

                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(getContext(), BluetoothService.class);
                    intent.setAction(BluetoothService.ACTION_UNPAIR);
                    intent.putExtra("device_address", bluetoothDevice.getAddress());
                    getContext().startService(intent);
                }
            });
        }
        else {
            viewHolder.mPairUnpairButton.setText(getContext().getString(R.string.pair));
            viewHolder.mPairUnpairButton.setOnClickListener(new OnClickListener() {

                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(getContext(), BluetoothService.class);
                    intent.setAction(BluetoothService.ACTION_PAIR);
                    intent.putExtra("device_address", bluetoothDevice.getAddress());
                    getContext().startService(intent);
                }
            });
        }

        return v;
    }

    public boolean contains(BluetoothDevice bluetoothDevice) {
        for (int i = 0; i < getCount(); i++) {
            if (getItem(i).equals(bluetoothDevice)) {
                return true;
            }
        }
        return false;
    }

    private static class ViewHolder {
        private TextView mDeviceNameTextView;
        private TextView mDeviceAddressTextView;
        private Button mPairUnpairButton;
    }
}
