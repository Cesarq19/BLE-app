package com.example.cerradurainteligente;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class HomeActivity extends AppCompatActivity {
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic txCharacteristic;
    private BluetoothGattCharacteristic rxCharacteristic;
    private List<BluetoothDevice> pairedDevicesList = new ArrayList<>();

    private TextView status_bth;
    private EditText bth_message;
    private TextView rx_message;

    private int REQUEST_ENABLE_BT = 0;

    // UUIDs del servicio y las características UART
    private static final UUID UART_SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
    private static final UUID UART_TX_UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E");
    private static final UUID UART_RX_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        status_bth = findViewById(R.id.estado);
        bth_message = findViewById(R.id.message_bt);
        rx_message = findViewById(R.id.rx_bth);

        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        checkBluetoothPermissions();
    }

    private void checkBluetoothPermissions() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN},
                    REQUEST_ENABLE_BT);
        } else {
            enableBluetooth();
        }
    }

    @SuppressLint("MissingPermission")
    private void enableBluetooth() {
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_ENABLE_BT) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                    grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                enableBluetooth();
            } else {
                Toast.makeText(this, "Permisos Bluetooth necesarios", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @SuppressLint("MissingPermission")
    public void connectBT(View view) {
        // Obtén los dispositivos emparejados
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            pairedDevicesList.clear();
            pairedDevicesList.addAll(pairedDevices);
            showPairedDevicesList();
        } else {
            Toast.makeText(this, "No hay dispositivos emparejados", Toast.LENGTH_SHORT).show();
        }
    }

    @SuppressLint("MissingPermission")
    private void showPairedDevicesList() {
        ArrayList<String> deviceNames = new ArrayList<>();
        for (BluetoothDevice device : pairedDevicesList) {
            deviceNames.add(device.getName() != null ? device.getName() : "Unnamed Device");
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, deviceNames);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Selecciona un dispositivo emparejado");
        builder.setAdapter(adapter, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                BluetoothDevice selectedDevice = pairedDevicesList.get(which);
                connectToDevice(selectedDevice);
            }
        });
        builder.show();
    }

    @SuppressLint("MissingPermission")
    private void connectToDevice(BluetoothDevice device) {
        status_bth.setText("Conectando a " + device.getName());
        bluetoothGatt = device.connectGatt(this, false, gattCallback);
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                status_bth.setText("Conectado a " + gatt.getDevice().getName());
                gatt.discoverServices();
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                status_bth.setText("Desconectado de " + gatt.getDevice().getName());
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService service = gatt.getService(UART_SERVICE_UUID);
                if (service != null) {
                    txCharacteristic = service.getCharacteristic(UART_TX_UUID);
                    rxCharacteristic = service.getCharacteristic(UART_RX_UUID);
                    status_bth.setText("Servicio UART encontrado");

                    // Habilitar notificaciones para la característica TX
                    gatt.setCharacteristicNotification(txCharacteristic, true);
                    BluetoothGattDescriptor descriptor = txCharacteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                    if (descriptor != null) {
                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        gatt.writeDescriptor(descriptor);
                    }
                } else {
                    status_bth.setText("Servicio UART no encontrado");
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (characteristic.getUuid().equals(UART_TX_UUID)) {
                // Recibir el mensaje desde el ESP32
                String receivedMessage = characteristic.getStringValue(0);
                runOnUiThread(() -> rx_message.setText(receivedMessage));
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i("BLE", "Escritura exitosa");
            } else {
                Log.e("BLE", "Fallo en la escritura");
            }
        }
    };

    @SuppressLint("MissingPermission")
    public void led_on(View view) {
        sendCommand("on");
    }

    @SuppressLint("MissingPermission")
    public void led_off(View view) {
        sendCommand("off");
    }

    @SuppressLint("MissingPermission")
    public void send_message(View view) {
        String message = bth_message.getText().toString();
        sendCommand(message);
        bth_message.setText("");
    }

    @SuppressLint("MissingPermission")
    private void sendCommand(String command) {
        if (bluetoothGatt != null && rxCharacteristic != null) {
            rxCharacteristic.setValue(command.getBytes());
            bluetoothGatt.writeCharacteristic(rxCharacteristic);
        } else {
            Toast.makeText(this, "No hay dispositivo conectado o servicio no encontrado", Toast.LENGTH_SHORT).show();
        }
    }
}
