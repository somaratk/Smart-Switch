package com.somaratne.kasun.smartswitch;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_LOCATION_PERMISSION = 2;
    private static final long SCAN_PERIOD = 10000;
    private BluetoothLeScanner bluetoothLeScanner =
            BluetoothAdapter.getDefaultAdapter().getBluetoothLeScanner();
    private boolean mScanning;
    private ListAdapter mLeDeviceListAdapter;
    private ListAdapter mScannedDeviceListAdapter;
    private Handler handler = new Handler();
    private Dialog scanDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                scanDialog = new Dialog(MainActivity.this);
                scanDialog.setContentView(R.layout.add_device_dialog);
                scanDialog.setTitle("Smart Switches nearby...");
                scanDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog){
                        if(mScanning) {
                            mScanning = false;
                            bluetoothLeScanner.stopScan(leScanCallback);
                        }
                    }
                } );
                ListView scannedListView = scanDialog.findViewById(R.id.scannedList);
                mScannedDeviceListAdapter = new ListAdapter();
                scannedListView.setAdapter(mScannedDeviceListAdapter);
                scannedListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                        if(mScanning) {
                            mScanning = false;
                            bluetoothLeScanner.stopScan(leScanCallback);
                        }
                        BluetoothDevice device = mScannedDeviceListAdapter.getDevice(position);
                        mLeDeviceListAdapter.addDevice(device);
                        mLeDeviceListAdapter.notifyDataSetChanged();
                        scanDialog.dismiss();
                    }
                });

                DisplayMetrics metrics = getResources().getDisplayMetrics();
                int width = metrics.widthPixels;
                int height = metrics.heightPixels;
                scanDialog.getWindow().setLayout((6 * width)/7, (4 * height)/5);
                scanDialog.show();
                scanLeDevice();
            }
        });

        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                REQUEST_LOCATION_PERMISSION);

        mLeDeviceListAdapter = new ListAdapter();
        ListView listView = this.findViewById(R.id.deviceList);
        listView.setAdapter(mLeDeviceListAdapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                BluetoothDevice device = mLeDeviceListAdapter.getDevice(position);
                String deviceFriendlyName = mLeDeviceListAdapter.getDeviceFriendlyName(position);
                Intent intent = new Intent(MainActivity.this, SwitchControlActivity.class);
                intent.putExtra(SwitchControlActivity.EXTRA_NAME, deviceFriendlyName);
                intent.putExtra(SwitchControlActivity.EXTRA_ID, device.getAddress());
                startActivity(intent);
            }
        });

        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                mLeDeviceListAdapter.showCheckBox();
                mLeDeviceListAdapter.notifyDataSetChanged();
                return true;
            }
        });

        // Initializes Bluetooth adapter.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();

        // Ensures Bluetooth is available on the device and it is enabled. If not,
        // displays a dialog requesting user permission to enable Bluetooth.
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    }

    @Override
    protected void onPause(){
        super.onPause();
        SharedPreferences sharedPref = this.getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putInt("saved_num_devices", mLeDeviceListAdapter.getCount());
        // add the name and address of each device to the saved preferences
        for(int i=0; i<mLeDeviceListAdapter.getCount(); i++){
            String nameKey = "name" + Integer.toString(i);
            String addressKey = "addr" + Integer.toString(i);
            editor.putString(nameKey, mLeDeviceListAdapter.getDeviceFriendlyName(i));
            editor.putString(addressKey, mLeDeviceListAdapter.getDevice(i).getAddress());
        }
        editor.apply();
    }

    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences sharedPref = this.getPreferences(Context.MODE_PRIVATE);
        int numDevices = sharedPref.getInt("saved_num_devices", 0);
        for(int i=0; i<numDevices; i++){
            String nameKey = "name" + Integer.toString(i);
            String addressKey = "addr" + Integer.toString(i);
            String devFriendlyName = sharedPref.getString(nameKey, "unknown");
            String devAddr = sharedPref.getString(addressKey, "unknown");
            if(!devFriendlyName.equals("unknown") && !devAddr.equals("unknown")){
                BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(devAddr);
                mLeDeviceListAdapter.addDevice(device, devFriendlyName);
            }
        }
    }

    @Override
    public void onBackPressed(){
        if(mLeDeviceListAdapter.isLongPressed){
            mLeDeviceListAdapter.isLongPressed = false;
            mLeDeviceListAdapter.clearSelected();
            mLeDeviceListAdapter.notifyDataSetChanged();
        }
        else{
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == R.id.action_rename) {
            if(mLeDeviceListAdapter.getSelectedCount() > 0) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Enter new name for device");
                final EditText nameEdit = new EditText(this);
                builder.setView(nameEdit);
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // User clicked OK button
                        String newName = nameEdit.getText().toString();
                        if(TextUtils.isEmpty(newName)){
                            nameEdit.setError("Name cannot be empty");
                            return;
                        }
                        else {
                            mLeDeviceListAdapter.renameSelected(newName);
                        }
                        mLeDeviceListAdapter.isLongPressed = false;
                        mLeDeviceListAdapter.notifyDataSetChanged();
                    }
                });
                builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // User cancelled the dialog
                        mLeDeviceListAdapter.isLongPressed = false;
                        mLeDeviceListAdapter.notifyDataSetChanged();
                    }
                });
                AlertDialog dialog = builder.create();
                dialog.show();
            }
            mLeDeviceListAdapter.isLongPressed = false;
            mLeDeviceListAdapter.notifyDataSetChanged();
            return true;
        }
        else if(id == R.id.action_remove){
            mLeDeviceListAdapter.removeSelected();
            mLeDeviceListAdapter.isLongPressed = false;
            mLeDeviceListAdapter.notifyDataSetChanged();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void scanLeDevice() {
        if (!mScanning) {
            // Stops scanning after a pre-defined scan period.
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    bluetoothLeScanner.stopScan(leScanCallback);
                    if (scanDialog.isShowing()) {
                        Toast.makeText(getApplicationContext(), "Scan complete", Toast.LENGTH_LONG).show();
                    }
                }
            }, SCAN_PERIOD);

            mScanning = true;
            // pass in switch service id to filter out devices that don't support it
            List<ScanFilter> filters = new ArrayList<>();
            ScanFilter filter = new ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString("9d3b8366-4853-11e6-beb8-9e71128cae77")).build();
            filters.add(filter);
            ScanSettings settings = new ScanSettings.Builder().build();
            bluetoothLeScanner.startScan(filters, settings, leScanCallback);
        } else {
            mScanning = false;
            bluetoothLeScanner.stopScan(leScanCallback);
        }
    }

    // Device scan callback.
    private ScanCallback leScanCallback =
            new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    super.onScanResult(callbackType, result);
                    if(!mLeDeviceListAdapter.contains(result.getDevice())) {
                        mScannedDeviceListAdapter.addDevice(result.getDevice());
                        mScannedDeviceListAdapter.notifyDataSetChanged();
                    }
                }

                @Override
                public void onScanFailed(int errorCode){
                    super.onScanFailed(errorCode);
                    String errMsg = "";
                    switch(errorCode){
                        case ScanCallback.SCAN_FAILED_ALREADY_STARTED:
                            errMsg = "SCAN_FAILED_ALREADY_STARTED";
                            break;
                        case ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED:
                            errMsg = "SCAN_FAILED_APPLICATION_REGISTRATION_FAILED";
                            break;
                        case ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED:
                            errMsg = "SCAN_FAILED_FEATURE_UNSUPPORTED";
                            break;
                        case ScanCallback.SCAN_FAILED_INTERNAL_ERROR:
                            errMsg = "SCAN_FAILED_INTERNAL_ERROR";
                            break;
                    }
                    Toast.makeText(getApplicationContext(), errMsg, Toast.LENGTH_LONG).show();
                }
            };

    // adapter for list view
    private class ListAdapter extends BaseAdapter {
        private ArrayList<BluetoothDevice> mLeDevices;
        private ArrayList<String> deviceFriendlyNames;
        private ArrayList<BluetoothDevice> selectedDevices;
        private boolean isLongPressed;

        // constructor for a ListAdapter
        private ListAdapter() {
            super();
            mLeDevices = new ArrayList<>();
            deviceFriendlyNames = new ArrayList<>();
            selectedDevices = new ArrayList<>();
            isLongPressed = false;
        }

        // adds a device to Bluetooth devices array
        private void addDevice(BluetoothDevice device) {
            if (!mLeDevices.contains(device)) {
                mLeDevices.add(device);
                deviceFriendlyNames.add(device.getName());
            }
        }

        private void addDevice(BluetoothDevice device, String friendlyName){
            if (!mLeDevices.contains(device)) {
                mLeDevices.add(device);
                deviceFriendlyNames.add(friendlyName);
            }
        }

        // returns a Bluetooth device at a given position in List
        private BluetoothDevice getDevice(int position) {
            return mLeDevices.get(position);
        }

        // returns friendly name of Bluetooth device at a given position in List
        private String getDeviceFriendlyName(int position) {
            return deviceFriendlyNames.get(position);
        }

        // removes all devices from list
        public void clear() {
            mLeDevices.clear();
            deviceFriendlyNames.clear();
        }

        // removes all selected devices from selected list
        private void clearSelected(){
            selectedDevices.clear();
        }

        // remove all selected devices from device list
        private void removeSelected(){
            for(int i=0; i<selectedDevices.size(); i++){
                deviceFriendlyNames.remove(mLeDevices.indexOf(selectedDevices.get(i)));
                mLeDevices.remove(selectedDevices.get(i));

            }
            clearSelected();
        }

        // change name of selected devices
        private void renameSelected(String name){
            for(int i=0; i<selectedDevices.size(); i++) {
                deviceFriendlyNames.set(mLeDevices.indexOf(selectedDevices.get(i)), name);
            }
            clearSelected();
        }

        // show checkbox on all list items
        private void showCheckBox(){
            isLongPressed = true;
        }

        // check if device is in the list
        private boolean contains(BluetoothDevice device){
            if(mLeDevices.contains(device)){
                return true;
            }
            else{
                return false;
            }
        }

        // returns the number of devices in list
        @Override
        public int getCount() {
            return mLeDevices.size();
        }

        // returns the number of selected devices in list
        private int getSelectedCount() {
            return selectedDevices.size();
        }

        // returns the object at given index in list
        @Override
        public Object getItem(int i) {
            return mLeDevices.get(i);
        }

        // returns the id of the device at given index of the list
        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            ViewHolder viewHolder;
            // List view optimization code
            if (view == null) {
                view = MainActivity.this.getLayoutInflater().inflate(R.layout.list_row, null);
                viewHolder = new ViewHolder();
                viewHolder.text = view.findViewById(R.id.textView);
                viewHolder.select = view.findViewById(R.id.checkBox);
                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) view.getTag();
            }
            final BluetoothDevice device = mLeDevices.get(i);
            final String deviceName = deviceFriendlyNames.get(i);
            if (deviceName != null && deviceName.length() > 0) {
                viewHolder.text.setText(deviceName);
            } else {
                viewHolder.text.setText(R.string.unknown_device_text);
            }
            if(isLongPressed)
            {
                viewHolder.select.setVisibility(View.VISIBLE);
                viewHolder.select.setChecked(false);
                viewHolder.select.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        if(isChecked){
                            selectedDevices.add(device);
                        }
                        else{
                            selectedDevices.remove(device);
                        }
                    }
                });

            } else {
                viewHolder.select.setVisibility(View.GONE);
            }

            return view;
        }
    }

    static class ViewHolder {
        public TextView text;
        public CheckBox select;
    }
}

