package com.somaratne.kasun.smartswitch;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.bluetooth.BluetoothGattDescriptor;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Calendar;
import java.util.Queue;

import static android.content.ContentValues.TAG;

public class SwitchControlActivity extends Activity {

    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_ID = "id";
    private static final int SWITCH_OFF = 0;
    private static final int SWITCH_ON = 1;
    private static final int MODE_SCHEDULE = 0x1111;
    private static final int MODE_DUSK_DAWN = 0x0101;
    private static final int MODE_DUSK_OFF = 0x1010;
    private String mDeviceName;
    private String mDeviceAddress;
    private BLEService mBluetoothLeService;
    private int switchState = SWITCH_OFF;
    private Queue<Runnable> commandQueue = new ArrayDeque<>();
    private boolean commandQueueBusy = false;

    final Messenger mMessenger = new Messenger(new IncomingHandler());

    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BLEService.LocalBinder) service).getService();
            mBluetoothLeService.setMessenger(mMessenger);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_switch_control);

        // read intent data
        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRA_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRA_ID);
        this.setTitle(mDeviceName);

        // connect to the Bluetooth service
        Intent gattServiceIntent = new Intent(this, BLEService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mBluetoothLeService.disconnect();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    // call when flip switch button is clicked
    public void onFlipSwitch(View view) {
        if(mBluetoothLeService != null) {
            if(switchState == SWITCH_ON) {
                final byte[] data = {0x00, 0x00};
                commandQueue.add(new Runnable() {
                    @Override
                    public void run(){
                        mBluetoothLeService.writeCharacteristic(BLEService.SWITCH_SERVICE, BLEService.SWITCH_STATE, data);
                    }
                });
            }
            else{
                final byte[] data = {0x67, 0x28};
                commandQueue.add(new Runnable() {
                    @Override
                    public void run(){
                        mBluetoothLeService.writeCharacteristic(BLEService.SWITCH_SERVICE, BLEService.SWITCH_STATE, data);
                    }
                });
            }
            nextCommand();
        }
    }

    // call when onConnect button is clicked
    public void onConnect(View view) {
        if(mBluetoothLeService != null) {
            mBluetoothLeService.connect(mDeviceAddress);
        }
    }

    // call when schedule switch is clicked
    public void onScheduleSwitchClick(View view){
        if(mBluetoothLeService != null){
            Switch mSwitch = findViewById(R.id.scheduleSwitch);
            if(mSwitch.isChecked()){
                final byte[] data = {0x11, 0x11};
                commandQueue.add(new Runnable() {
                    @Override
                    public void run(){
                        mBluetoothLeService.writeCharacteristic(BLEService.SWITCH_SERVICE, BLEService.SWITCH_MODE, data);
                    }
                });
            }
            else{
                final byte[] data = {0x00, 0x00};
                commandQueue.add(new Runnable() {
                    @Override
                    public void run(){
                        mBluetoothLeService.writeCharacteristic(BLEService.SWITCH_SERVICE, BLEService.SWITCH_MODE, data);
                    }
                });
            }
            nextCommand();
        }
    }

    // call when dusk to dawn switch is clicked
    public void onDuskToDawnSwitchClick(View view){
        if(mBluetoothLeService != null){
            Switch mSwitch = findViewById(R.id.duskToDawnSwitch);
            if(mSwitch.isChecked()){
                final byte[] data = {0x01, 0x01};
                commandQueue.add(new Runnable() {
                    @Override
                    public void run(){
                        mBluetoothLeService.writeCharacteristic(BLEService.SWITCH_SERVICE, BLEService.SWITCH_MODE, data);
                    }
                });
            }
            else{
                final byte[] data = {0x00, 0x00};
                commandQueue.add(new Runnable() {
                    @Override
                    public void run(){
                        mBluetoothLeService.writeCharacteristic(BLEService.SWITCH_SERVICE, BLEService.SWITCH_MODE, data);
                    }
                });
            }
            nextCommand();
        }
    }

    // call when dusk to switch off switch is clicked
    public void onDuskToSwitchOffSwitchClick(View view){
        if(mBluetoothLeService != null){
            Switch mSwitch = findViewById(R.id.duskToSwitchOffSwitch);
            if(mSwitch.isChecked()){
                final byte[] data = {0x10, 0x10};
                commandQueue.add(new Runnable() {
                    @Override
                    public void run(){
                        mBluetoothLeService.writeCharacteristic(BLEService.SWITCH_SERVICE, BLEService.SWITCH_MODE, data);
                    }
                });
            }
            else{
                final byte[] data = {0x00, 0x00};
                commandQueue.add(new Runnable() {
                    @Override
                    public void run(){
                        mBluetoothLeService.writeCharacteristic(BLEService.SWITCH_SERVICE, BLEService.SWITCH_MODE, data);
                    }
                });
            }
            nextCommand();
        }
    }

    // call when switch on time text is clicked
    public void onSwitchOnTimeClick(View view){
        if(mBluetoothLeService != null) {
            final TextView textView = findViewById(R.id.switchOnTime);
            String timeText = textView.getText().toString();
            int hour = Integer.parseInt(timeText.split(":")[0]);
            int minute = Integer.parseInt(timeText.split(":")[1].split(" ")[0]);
            if (timeText.split(" ")[1].equals("PM")) {
                if (hour < 12) {
                    hour += 12;
                }
            }

            TimePickerDialog.OnTimeSetListener myTimeListener = new TimePickerDialog.OnTimeSetListener() {
                @Override
                public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                    if (view.isShown()) {
                        int newTime = hourOfDay * 60 + minute;
                        Calendar myCalender = Calendar.getInstance();
                        int nowTime = myCalender.get(Calendar.HOUR_OF_DAY) * 60 + myCalender.get(Calendar.MINUTE);
                        int timeToSwitchOn = newTime - nowTime;
                        if(timeToSwitchOn < 0){
                            timeToSwitchOn = 24*60 - nowTime + newTime;
                        }
                        final byte[] data = new byte[4];
                        data[0] = (byte) (timeToSwitchOn /*>> 0*/);
                        data[1] = (byte) (timeToSwitchOn >> 8);
                        data[2] = (byte) (timeToSwitchOn >> 16);
                        data[3] = (byte) (timeToSwitchOn >> 24);

                        commandQueue.add(new Runnable() {
                            @Override
                            public void run(){
                                mBluetoothLeService.writeCharacteristic(BLEService.SWITCH_SERVICE, BLEService.TIME_TO_SWITCH_ON, data);
                            }
                        });
                        nextCommand();
                    }
                }
            };
            TimePickerDialog timePickerDialog = new TimePickerDialog(this, myTimeListener, hour, minute, false);

            timePickerDialog.setTitle("Select Switch On Time");
            timePickerDialog.show();
        }
    }

    // call when switch off time text is clicked
    public void onSwitchOffTimeClick(View view){
        if(mBluetoothLeService != null) {
            final TextView textView = findViewById(R.id.switchOffTime);
            String timeText = textView.getText().toString();
            int hour = Integer.parseInt(timeText.split(":")[0]);
            int minute = Integer.parseInt(timeText.split(":")[1].split(" ")[0]);
            if (timeText.split(" ")[1].equals("PM")) {
                if (hour < 12) {
                    hour += 12;
                }
            }

            TimePickerDialog.OnTimeSetListener myTimeListener = new TimePickerDialog.OnTimeSetListener() {
                @Override
                public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                    if (view.isShown()) {
                        int newTime = hourOfDay * 60 + minute;
                        Calendar myCalender = Calendar.getInstance();
                        int nowTime = myCalender.get(Calendar.HOUR_OF_DAY) * 60 + myCalender.get(Calendar.MINUTE);
                        int timeToSwitchOff = newTime - nowTime;
                        if(timeToSwitchOff < 0){
                            timeToSwitchOff = 24*60 - nowTime + newTime;
                        }
                        final byte[] data = new byte[4];
                        data[0] = (byte) (timeToSwitchOff /*>> 0*/);
                        data[1] = (byte) (timeToSwitchOff >> 8);
                        data[2] = (byte) (timeToSwitchOff >> 16);
                        data[3] = (byte) (timeToSwitchOff >> 24);

                        commandQueue.add(new Runnable() {
                            @Override
                            public void run(){
                                mBluetoothLeService.writeCharacteristic(BLEService.SWITCH_SERVICE, BLEService.TIME_TO_SWITCH_OFF, data);
                            }
                        });
                        nextCommand();
                    }
                }
            };
            TimePickerDialog timePickerDialog = new TimePickerDialog(this, myTimeListener, hour, minute, false);

            timePickerDialog.setTitle("Select Switch Off Time");
            timePickerDialog.show();
        }
    }

    // call when daylight threshold is clicked
    public void onDaylightThreshClick(View view) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter new daylight threshold (0 - 127)");
        final EditText threshEdit = new EditText(this);
        builder.setView(threshEdit);
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                // User clicked OK button
                try {
                    int newThresh = Integer.parseInt(threshEdit.getText().toString());
                    final byte[] data = new byte[4];
                    data[0] = (byte) (newThresh /*>> 0*/);
                    data[1] = (byte) (newThresh >> 8);
                    data[2] = (byte) (newThresh >> 16);
                    data[3] = (byte) (newThresh >> 24);

                    commandQueue.add(new Runnable() {
                        @Override
                        public void run(){
                            mBluetoothLeService.writeCharacteristic(BLEService.SWITCH_SERVICE, BLEService.DAYLIGHT_THRESHOLD, data);
                        }
                    });
                    nextCommand();
                }
                catch(Exception e){
                    Toast.makeText(getApplicationContext(), "Error setting threshold",
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                // User cancelled the dialog
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    // executes next command in commandQueue
    private void nextCommand(){
        if(commandQueueBusy){
            return;
        }
        // execute next command in queue
        if(commandQueue.size() > 0){
            final Runnable nextCommand = commandQueue.poll();
            commandQueueBusy = true;
            nextCommand.run();
        }
    }

    // service message handler ///////////////
    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            Bundle bundle;

            switch (msg.what) {
                case BLEService.GATT_CONNECTED:
                    // configure UI
                    SwitchControlActivity.this.findViewById(R.id.connectButton).setEnabled(false);
                    ((TextView) SwitchControlActivity.this.findViewById(R.id.connectStateText)).setText(R.string.connected_state_text);
                    break;
                case BLEService.GATT_DISCONNECT:
                    SwitchControlActivity.this.findViewById(R.id.connectButton).setEnabled(true);
                    ((TextView) SwitchControlActivity.this.findViewById(R.id.connectStateText)).setText(R.string.connect_state_text);
                    break;
                case BLEService.GATT_SERVICES_DISCOVERED:
                    // enable configuration buttons
                    SwitchControlActivity.this.findViewById(R.id.switchStateSwitch).setEnabled(true);
                    SwitchControlActivity.this.findViewById(R.id.scheduleSwitch).setEnabled(true);
                    SwitchControlActivity.this.findViewById(R.id.duskToDawnSwitch).setEnabled(true);
                    SwitchControlActivity.this.findViewById(R.id.duskToSwitchOffSwitch).setEnabled(true);
                    SwitchControlActivity.this.findViewById(R.id.switchOnTime).setEnabled(true);
                    SwitchControlActivity.this.findViewById(R.id.switchOffTime).setEnabled(true);
                    SwitchControlActivity.this.findViewById(R.id.daylightThresh).setEnabled(true);
                    // set characteristic notification on switch state
                    commandQueue.add(new Runnable() {
                        @Override
                        public void run() {
                            mBluetoothLeService.writeDescriptor(BLEService.SWITCH_SERVICE, BLEService.SWITCH_STATE,
                                    BLEService.CHARACTERISTIC_UPDATE_NOTIFICATION_DESCRIPTOR, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        }
                    });
                    // read characteristics
                    commandQueue.add(new Runnable() {
                        @Override
                        public void run() {
                            mBluetoothLeService.readCharacteristic(BLEService.SWITCH_SERVICE, BLEService.SWITCH_STATE);
                        }
                    });
                    commandQueue.add(new Runnable() {
                        @Override
                        public void run() {
                            mBluetoothLeService.readCharacteristic(BLEService.SWITCH_SERVICE, BLEService.SWITCH_MODE);
                        }
                    });
                    commandQueue.add(new Runnable() {
                        @Override
                        public void run() {
                            mBluetoothLeService.readCharacteristic(BLEService.SWITCH_SERVICE, BLEService.TIME_TO_SWITCH_ON);
                        }
                    });
                    commandQueue.add(new Runnable() {
                        @Override
                        public void run() {
                            mBluetoothLeService.readCharacteristic(BLEService.SWITCH_SERVICE, BLEService.TIME_TO_SWITCH_OFF);
                        }
                    });
                    commandQueue.add(new Runnable() {
                        @Override
                        public void run() {
                            mBluetoothLeService.readCharacteristic(BLEService.SWITCH_SERVICE, BLEService.DAYLIGHT_THRESHOLD);
                        }
                    });
                    nextCommand();
                    break;
                case BLEService.GATT_DESCRIPTOR_WRITE:
                    bundle = msg.getData();
                    String descUuid = bundle.getString(BLEService.PARCEL_UUID);
                    if (descUuid.equals(BLEService.CHARACTERISTIC_UPDATE_NOTIFICATION_DESCRIPTOR)) {
                        // enable characteristic notification
                        mBluetoothLeService.setCharacteristicNotification(BLEService.SWITCH_SERVICE,
                                BLEService.SWITCH_STATE, true);
                    }
                    commandQueueBusy = false;
                    nextCommand();
                    break;
                case BLEService.GATT_CHARACTERISTIC_READ:
                    bundle = msg.getData();
                    String uuid = bundle.getString(BLEService.PARCEL_UUID);
                    if (uuid.equals(BLEService.SWITCH_STATE)) {
                        byte[] dBytes = bundle.getByteArray(BLEService.PARCEL_VALUE);
                        int dVal = dBytes[1] & 0xFF | (dBytes[0] & 0xFF) << 8;
                        if (dVal == 0) {
                            ((Switch) SwitchControlActivity.this.findViewById(R.id.switchStateSwitch)).setChecked(false);
                            switchState = SWITCH_OFF;
                            Toast.makeText(getApplicationContext(), "SWITCH IS OFF",
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            ((Switch) SwitchControlActivity.this.findViewById(R.id.switchStateSwitch)).setChecked(true);
                            switchState = SWITCH_ON;
                            Toast.makeText(getApplicationContext(), "SWITCH IS ON",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                    else if(uuid.equals(BLEService.SWITCH_MODE)){
                        byte[] dBytes = bundle.getByteArray(BLEService.PARCEL_VALUE);
                        int dVal = dBytes[1] & 0xFF | (dBytes[0] & 0xFF) << 8;
                        ((Switch) SwitchControlActivity.this.findViewById(R.id.scheduleSwitch)).setChecked(false);
                        ((Switch) SwitchControlActivity.this.findViewById(R.id.duskToDawnSwitch)).setChecked(false);
                        ((Switch) SwitchControlActivity.this.findViewById(R.id.duskToSwitchOffSwitch)).setChecked(false);
                        if (dVal == MODE_SCHEDULE){
                            ((Switch) SwitchControlActivity.this.findViewById(R.id.scheduleSwitch)).setChecked(true);
                            Toast.makeText(getApplicationContext(), "Schedule mode",
                                    Toast.LENGTH_SHORT).show();
                        }
                        else if(dVal == MODE_DUSK_DAWN){
                            ((Switch) SwitchControlActivity.this.findViewById(R.id.duskToDawnSwitch)).setChecked(true);
                            Toast.makeText(getApplicationContext(), "Dusk to Dawn mode",
                                    Toast.LENGTH_SHORT).show();
                        }
                        else if(dVal == MODE_DUSK_OFF){
                            ((Switch) SwitchControlActivity.this.findViewById(R.id.duskToSwitchOffSwitch)).setChecked(true);
                            Toast.makeText(getApplicationContext(), "Dusk to Switch off mode",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                    else if(uuid.equals(BLEService.TIME_TO_SWITCH_ON)){
                        byte[] dBytes = bundle.getByteArray(BLEService.PARCEL_VALUE);
                        int dVal = dBytes[0] & 0xFF | ((dBytes[1] & 0xFF) << 8) | ((dBytes[2] & 0xFF) << 16) | ((dBytes[3] & 0xFF) << 24);
                        Calendar myCalender = Calendar.getInstance();
                        myCalender.add(Calendar.MINUTE, dVal);
                        int hour = myCalender.get(Calendar.HOUR);
                        int minute = myCalender.get(Calendar.MINUTE);
                        String timeStr = String.format("%02d", hour) + ":" + String.format("%02d", minute) + " AM";
                        if(myCalender.get(Calendar.AM_PM) == Calendar.PM){
                            if(hour == 0){
                                hour = 12;
                            }
                            timeStr = String.format("%02d", hour) + ":" + String.format("%02d", minute) + " PM";
                        }
                        ((TextView) SwitchControlActivity.this.findViewById(R.id.switchOnTime)).setText(timeStr);
                    }
                    else if(uuid.equals(BLEService.TIME_TO_SWITCH_OFF)){
                        byte[] dBytes = bundle.getByteArray(BLEService.PARCEL_VALUE);
                        int dVal = dBytes[0] & 0xFF | ((dBytes[1] & 0xFF) << 8) | ((dBytes[2] & 0xFF) << 16) | ((dBytes[3] & 0xFF) << 24);
                        Calendar myCalender = Calendar.getInstance();
                        myCalender.add(Calendar.MINUTE, dVal);
                        int hour = myCalender.get(Calendar.HOUR);
                        int minute = myCalender.get(Calendar.MINUTE);
                        String timeStr = String.format("%02d", hour) + ":" + String.format("%02d", minute) + " AM";
                        if(myCalender.get(Calendar.AM_PM) == Calendar.PM){
                            if(hour == 0){
                                hour = 12;
                            }
                            timeStr = String.format("%02d", hour) + ":" + String.format("%02d", minute) + " PM";
                        }
                        ((TextView) SwitchControlActivity.this.findViewById(R.id.switchOffTime)).setText(timeStr);
                    }
                    else if(uuid.equals(BLEService.DAYLIGHT_THRESHOLD)){
                        byte[] dBytes = bundle.getByteArray(BLEService.PARCEL_VALUE);
                        int dVal = dBytes[0] & 0xFF | ((dBytes[1] & 0xFF) << 8) | ((dBytes[2] & 0xFF) << 16) | ((dBytes[3] & 0xFF) << 24);
                        String daylightThreshStr = Integer.toString(dVal);
                        ((TextView) SwitchControlActivity.this.findViewById(R.id.daylightThresh)).setText(daylightThreshStr);
                    }

                    commandQueueBusy = false;
                    nextCommand();
                    break;
                case BLEService.GATT_CHARACTERISTIC_WRITE:
                    bundle = msg.getData();
                    // read back the characteristic that was written
                    final String charUUID = bundle.getString(BLEService.PARCEL_UUID);
                    commandQueueBusy = false;
                    commandQueue.add(new Runnable() {
                        @Override
                        public void run(){
                            mBluetoothLeService.readCharacteristic(BLEService.SWITCH_SERVICE, charUUID);
                        }
                    });
                    nextCommand();
                    break;                case BLEService.GATT_REMOTE_RSSI:
                    bundle = msg.getData();
                    Toast.makeText(getApplicationContext(), Integer.toString(bundle.getInt(BLEService.PARCEL_RSSI)),
                            Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    }
}
