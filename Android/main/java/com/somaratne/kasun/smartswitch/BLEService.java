package com.somaratne.kasun.smartswitch;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import java.util.UUID;

public class BLEService extends Service {

    //private Handler mActivityHandler = null;
    private Messenger mMessenger;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGatt mBluetoothGatt;
    private BluetoothManager mBluetoothManager;

    // service UUIDS
    public static final String SWITCH_SERVICE = "9d3b8366-4853-11e6-beb8-9e71128cae77";

    // service characteristic UUIDS
    public static final String SWITCH_STATE = "9d3b8366-4853-11e6-beb8-9e71128c0001";
    public static final String SWITCH_MODE = "9d3b8366-4853-11e6-beb8-9e71128c0002";
    public static final String TIME_TO_SWITCH_ON = "9d3b8366-4853-11e6-beb8-9e71128c0003";
    public static final String TIME_TO_SWITCH_OFF = "9d3b8366-4853-11e6-beb8-9e71128c0004";
    public static final String DAYLIGHT_THRESHOLD = "9d3b8366-4853-11e6-beb8-9e71128c0005";

    // this is for setting characteristic notifications
    public static final String CHARACTERISTIC_UPDATE_NOTIFICATION_DESCRIPTOR = "00002902-0000-1000-8000-00805f9b34fb";

    // messages sent back to activity
    public static final int GATT_CONNECTED = 1;
    public static final int GATT_DISCONNECT = 2;
    public static final int GATT_SERVICES_DISCOVERED = 3;
    public static final int GATT_DESCRIPTOR_WRITE = 4;
    public static final int GATT_CHARACTERISTIC_READ = 5;
    public static final int GATT_CHARACTERISTIC_WRITE = 6;
    public static final int GATT_REMOTE_RSSI = 7;

    // message parameters
    public static final String PARCEL_UUID = "UUID";
    public static final String PARCEL_VALUE = "VALUE";
    public static final String PARCEL_RSSI = "RSSI";

    // set Activity that will receive the messages
    public void setMessenger(Messenger messenger) {
        mMessenger = messenger;
    }


    @Override
    public void onCreate() {
        if(mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if(mBluetoothManager == null) {
                return;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if(mBluetoothAdapter == null) {
            return;
        }
    }

    // function to connect to GATT server
    public boolean connect(final String address) {
        if(mBluetoothAdapter == null || address == null) {
            return false;
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if(device == null) {
            return false;
        }

        // set auto connect to true
        mBluetoothGatt = device.connectGatt(this, true, mGattCallback);
        return true;
    }

    // function to disconnect from GATT server
    public void disconnect() {
        if(mBluetoothAdapter == null || mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.disconnect();
    }

    // Service binder /////////////////////////////
    private final IBinder mBinder = new LocalBinder();

    public class LocalBinder extends Binder {
        BLEService getService() {
            return BLEService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return super.onUnbind(intent);
    }

    // BLE GATT callback///////////////////////
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if(newState == BluetoothProfile.STATE_CONNECTED) {
                Message msg = Message.obtain(null, GATT_CONNECTED);
                try {
                    mMessenger.send(msg);
                } catch (RemoteException e){
                    e.printStackTrace();
                }
                mBluetoothGatt.discoverServices();
            } else if(newState == BluetoothProfile.STATE_DISCONNECTED) {
                Message msg = Message.obtain(null, GATT_DISCONNECT);
                try {
                    mMessenger.send(msg);
                } catch (RemoteException e){
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            //setCharacteristicNotification(BLE_SERVICE, BLE_SERVICE_RX, true);
            //writeDescriptor(BLE_SERVICE, BLE_SERVICE_RX, CLIENT_CHARACTERISTIC_CONFIG,
            // BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            Message msg = Message.obtain(null, GATT_SERVICES_DISCOVERED);
            try {
                mMessenger.send(msg);
            } catch (RemoteException e){
                e.printStackTrace();
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status){
            if(status == BluetoothGatt.GATT_SUCCESS){
                Bundle bundle = new Bundle();
                bundle.putString(PARCEL_UUID, descriptor.getUuid().toString());
                bundle.putByteArray(PARCEL_VALUE, descriptor.getValue());

                Message msg = Message.obtain(null, GATT_DESCRIPTOR_WRITE);
                msg.setData(bundle);
                try {
                    mMessenger.send(msg);
                } catch (RemoteException e){
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic, int status) {
            if(status == BluetoothGatt.GATT_SUCCESS) {
                Bundle bundle = new Bundle();
                bundle.putString(PARCEL_UUID, characteristic.getUuid().toString());
                bundle.putByteArray(PARCEL_VALUE, characteristic.getValue());

                Message msg = Message.obtain(null, GATT_CHARACTERISTIC_READ);
                msg.setData(bundle);
                try {
                    mMessenger.send(msg);
                } catch (RemoteException e){
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                                          BluetoothGattCharacteristic characteristic, int status) {
            if(status == BluetoothGatt.GATT_SUCCESS){
                Bundle bundle = new Bundle();
                bundle.putString(PARCEL_UUID, characteristic.getUuid().toString());
                bundle.putByteArray(PARCEL_VALUE, characteristic.getValue());

                Message msg = Message.obtain(null, GATT_CHARACTERISTIC_READ);
                msg.setData(bundle);
                try {
                    mMessenger.send(msg);
                } catch (RemoteException e){
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            Bundle bundle = new Bundle();
            bundle.putString(PARCEL_UUID, characteristic.getUuid().toString());
            bundle.putByteArray(PARCEL_VALUE, characteristic.getValue());

            Message msg = Message.obtain(null, GATT_CHARACTERISTIC_READ);
            msg.setData(bundle);
            try {
                mMessenger.send(msg);
            } catch (RemoteException e){
                e.printStackTrace();
            }
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            if(status == BluetoothGatt.GATT_SUCCESS) {
                Bundle bundle = new Bundle();
                bundle.putInt(PARCEL_RSSI, rssi);
                Message msg = Message.obtain(null, GATT_REMOTE_RSSI);
                msg.setData(bundle);
                try {
                    mMessenger.send(msg);
                } catch (RemoteException e){
                    e.printStackTrace();
                }
            }
        }
    };

    // sets notification on a characteristic
    public void setCharacteristicNotification(String serviceUuid,
                                              String characteristicUuid, boolean enabled) {
        if(mBluetoothAdapter == null || mBluetoothGatt == null) {
            return;
        }
        BluetoothGattService gattService = mBluetoothGatt.getService(UUID.fromString(serviceUuid));
        if(gattService == null) {
            return;
        }
        BluetoothGattCharacteristic gattChar = gattService.getCharacteristic(
                UUID.fromString(characteristicUuid));
        if(gattChar == null) {
            return;
        }
        mBluetoothGatt.setCharacteristicNotification(gattChar, enabled);
    }

    // writes value of a descriptor to remote device
    public void writeDescriptor(String serviceUuid, String characteristicUuid,
                                String descriptorUuid, byte[] value) {
        if(mBluetoothAdapter == null || mBluetoothGatt == null) {
            return;
        }
        BluetoothGattService gattService = mBluetoothGatt.getService(UUID.fromString(serviceUuid));
        if(gattService == null) {
            return;
        }
        BluetoothGattCharacteristic gattChar = gattService.getCharacteristic(
                UUID.fromString(characteristicUuid));
        if(gattChar == null) {
            return;
        }

        BluetoothGattDescriptor descriptor = gattChar
                .getDescriptor(UUID.fromString(descriptorUuid));
        descriptor.setValue(value);
        mBluetoothGatt.writeDescriptor(descriptor);
    }

    // writes a value to a service
    public boolean writeCharacteristic(String serviceUuid, String characteristicUuid,
                                       byte[] value) {
        if(mBluetoothAdapter == null || mBluetoothGatt == null) {
            return false;
        }

        BluetoothGattService gattService = mBluetoothGatt.getService(UUID.fromString(serviceUuid));
        if(gattService == null) {
            return false;
        }
        BluetoothGattCharacteristic gattChar = gattService.getCharacteristic(
                UUID.fromString(characteristicUuid));
        if(gattChar == null) {
            return false;
        }
        gattChar.setValue(value);

        return mBluetoothGatt.writeCharacteristic(gattChar);
    }

    // read value from service
    public boolean readCharacteristic(String serviceUuid, String characteristicUuid) {
        if(mBluetoothAdapter == null || mBluetoothGatt == null) {
            return false;
        }
        BluetoothGattService gattService = mBluetoothGatt.getService(UUID.fromString(serviceUuid));
        if(gattService == null) {
            return false;
        }
        BluetoothGattCharacteristic gattChar = gattService.getCharacteristic(UUID.fromString(
                characteristicUuid));
        if(gattChar == null) {
            return false;
        }

        return mBluetoothGatt.readCharacteristic(gattChar);
    }

    // read rssi value of a connected device
    public void readRemoteRssi() {
        if(mBluetoothAdapter == null || mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.readRemoteRssi();
    }

}
