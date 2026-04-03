package com.openuc2.uc2serial;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class ControlFragment extends Fragment implements SerialInputOutputManager.Listener {

    private static final String TAG = "ControlFragment";
    private static final String INTENT_ACTION_GRANT_USB = BuildConfig.APPLICATION_ID + ".GRANT_USB";
    private static final int WRITE_WAIT_MILLIS = 2000;
    private static final int READ_WAIT_MILLIS = 2000;
    private static final int MAX_SERIAL_LOG_LENGTH = 8000;

    private enum UsbPermission { Unknown, Requested, Granted, Denied }

    private int deviceId, portNum, baudRate;
    private boolean withIoManager;

    private final BroadcastReceiver broadcastReceiver;
    private final Handler mainLooper;

    private SerialInputOutputManager usbIoManager;
    private UsbSerialPort usbSerialPort;
    private UsbPermission usbPermission = UsbPermission.Unknown;
    private boolean connected = false;

    // UI elements
    private TextView textStatus, textBaud;
    private View statusIndicator;
    private TextView textSerialOutput;
    private ScrollView scrollSerial;
    private EditText editSerialCmd;
    private EditText editMotorSpeed, editMotorSteps;
    private SwitchMaterial switchSerialVisible;

    // LED state
    private int ledR = 0, ledG = 0, ledB = 0;

    public ControlFragment() {
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (INTENT_ACTION_GRANT_USB.equals(intent.getAction())) {
                    usbPermission = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                            ? UsbPermission.Granted : UsbPermission.Denied;
                    connect();
                }
            }
        };
        mainLooper = new Handler(Looper.getMainLooper());
    }

    // ---- Lifecycle ----

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        setRetainInstance(true);
        deviceId = getArguments().getInt("device");
        portNum = getArguments().getInt("port");
        baudRate = getArguments().getInt("baud");
        withIoManager = getArguments().getBoolean("withIoManager");
    }

    @Override
    public void onResume() {
        super.onResume();
        getActivity().registerReceiver(broadcastReceiver, new IntentFilter(INTENT_ACTION_GRANT_USB));
        if (usbPermission == UsbPermission.Unknown || usbPermission == UsbPermission.Granted)
            mainLooper.post(this::connect);
    }

    @Override
    public void onPause() {
        if (connected) {
            status("disconnected");
            disconnect();
        }
        getActivity().unregisterReceiver(broadcastReceiver);
        super.onPause();
    }

    // ---- UI setup ----

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_control, container, false);

        // Status bar
        statusIndicator = root.findViewById(R.id.status_indicator);
        textStatus = root.findViewById(R.id.text_status);
        textBaud = root.findViewById(R.id.text_baud);
        textBaud.setText(baudRate + " baud");

        // Motor config fields
        editMotorSpeed = root.findViewById(R.id.edit_motor_speed);
        editMotorSteps = root.findViewById(R.id.edit_motor_steps);

        // Setup the 4 axis control rows
        setupAxisRow(root, R.id.axis_a_row, "A", 0);
        setupAxisRow(root, R.id.axis_x_row, "X", 1);
        setupAxisRow(root, R.id.axis_y_row, "Y", 2);
        setupAxisRow(root, R.id.axis_z_row, "Z", 3);

        // Home all & Stop all
        root.findViewById(R.id.btn_home_all).setOnClickListener(v -> {
            for (int axis = 0; axis < 4; axis++) {
                send(UC2CommandBuilder.homeAxis(axis, getMotorSpeed(), -1, 20000, 1));
            }
        });
        root.findViewById(R.id.btn_stop_all).setOnClickListener(v -> {
            for (int axis = 0; axis < 4; axis++) {
                send(UC2CommandBuilder.motorStop(axis));
            }
        });

        // LED controls
        setupLedControls(root);

        // Laser controls
        setupLaserSlider(root, R.id.seekbar_laser0, R.id.text_laser0_val, 0);
        setupLaserSlider(root, R.id.seekbar_laser1, R.id.text_laser1_val, 1);
        setupLaserSlider(root, R.id.seekbar_laser2, R.id.text_laser2_val, 2);
        setupLaserSlider(root, R.id.seekbar_laser3, R.id.text_laser3_val, 3);
        root.findViewById(R.id.btn_laser_all_off).setOnClickListener(v -> {
            for (int i = 0; i < 4; i++) send(UC2CommandBuilder.laserSet(i, 0));
            // Reset slider positions
            ((SeekBar) root.findViewById(R.id.seekbar_laser0)).setProgress(0);
            ((SeekBar) root.findViewById(R.id.seekbar_laser1)).setProgress(0);
            ((SeekBar) root.findViewById(R.id.seekbar_laser2)).setProgress(0);
            ((SeekBar) root.findViewById(R.id.seekbar_laser3)).setProgress(0);
        });

        // Galvo controls
        setupGalvoControls(root);

        // System buttons
        root.findViewById(R.id.btn_get_state).setOnClickListener(v -> send(UC2CommandBuilder.getState()));
        root.findViewById(R.id.btn_restart_esp).setOnClickListener(v -> send(UC2CommandBuilder.restart()));

        // Serial monitor
        setupSerialMonitor(root);

        return root;
    }

    private void setupAxisRow(View root, int rowId, String label, int axisId) {
        View row = root.findViewById(rowId);
        ((TextView) row.findViewById(R.id.text_axis_label)).setText(label);

        row.findViewById(R.id.btn_minus_large).setOnClickListener(v ->
                send(UC2CommandBuilder.motorMove(axisId, -getMotorSteps(), getMotorSpeed(), false, true)));
        row.findViewById(R.id.btn_minus_small).setOnClickListener(v ->
                send(UC2CommandBuilder.motorMove(axisId, -(getMotorSteps() / 10), getMotorSpeed(), false, true)));
        row.findViewById(R.id.btn_plus_small).setOnClickListener(v ->
                send(UC2CommandBuilder.motorMove(axisId, getMotorSteps() / 10, getMotorSpeed(), false, true)));
        row.findViewById(R.id.btn_plus_large).setOnClickListener(v ->
                send(UC2CommandBuilder.motorMove(axisId, getMotorSteps(), getMotorSpeed(), false, true)));
        row.findViewById(R.id.btn_home).setOnClickListener(v ->
                send(UC2CommandBuilder.homeAxis(axisId, getMotorSpeed(), -1, 20000, 1)));
    }

    private int getMotorSpeed() {
        try {
            return Integer.parseInt(editMotorSpeed.getText().toString());
        } catch (NumberFormatException e) {
            return 15000;
        }
    }

    private int getMotorSteps() {
        try {
            return Integer.parseInt(editMotorSteps.getText().toString());
        } catch (NumberFormatException e) {
            return 1000;
        }
    }

    private void setupLedControls(View root) {
        SeekBar seekR = root.findViewById(R.id.seekbar_led_r);
        SeekBar seekG = root.findViewById(R.id.seekbar_led_g);
        SeekBar seekB = root.findViewById(R.id.seekbar_led_b);
        TextView valR = root.findViewById(R.id.text_led_r_val);
        TextView valG = root.findViewById(R.id.text_led_g_val);
        TextView valB = root.findViewById(R.id.text_led_b_val);

        SeekBar.OnSeekBarChangeListener ledListener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (seekBar == seekR) { ledR = progress; valR.setText(String.valueOf(progress)); }
                else if (seekBar == seekG) { ledG = progress; valG.setText(String.valueOf(progress)); }
                else if (seekBar == seekB) { ledB = progress; valB.setText(String.valueOf(progress)); }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                send(UC2CommandBuilder.ledFill(ledR, ledG, ledB));
            }
        };
        seekR.setOnSeekBarChangeListener(ledListener);
        seekG.setOnSeekBarChangeListener(ledListener);
        seekB.setOnSeekBarChangeListener(ledListener);

        root.findViewById(R.id.btn_led_apply).setOnClickListener(v ->
                send(UC2CommandBuilder.ledFill(ledR, ledG, ledB)));
        root.findViewById(R.id.btn_led_left).setOnClickListener(v ->
                send(UC2CommandBuilder.ledPattern("left", ledR, ledG, ledB)));
        root.findViewById(R.id.btn_led_right).setOnClickListener(v ->
                send(UC2CommandBuilder.ledPattern("right", ledR, ledG, ledB)));
        root.findViewById(R.id.btn_led_off).setOnClickListener(v -> {
            send(UC2CommandBuilder.ledOff());
            seekR.setProgress(0); seekG.setProgress(0); seekB.setProgress(0);
        });
    }

    private void setupLaserSlider(View root, int seekBarId, int textId, int laserId) {
        SeekBar seekBar = root.findViewById(seekBarId);
        TextView textVal = root.findViewById(textId);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            int val = 0;
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                val = progress;
                textVal.setText(String.valueOf(progress));
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                send(UC2CommandBuilder.laserSet(laserId, val));
            }
        });
    }

    private void setupGalvoControls(View root) {
        SeekBar seekX = root.findViewById(R.id.seekbar_galvo_x);
        SeekBar seekY = root.findViewById(R.id.seekbar_galvo_y);
        TextView valX = root.findViewById(R.id.text_galvo_x_val);
        TextView valY = root.findViewById(R.id.text_galvo_y_val);
        EditText editNx = root.findViewById(R.id.edit_galvo_nx);
        EditText editNy = root.findViewById(R.id.edit_galvo_ny);

        seekX.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean u) { valX.setText(String.valueOf(p)); }
        });
        seekY.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean u) { valY.setText(String.valueOf(p)); }
        });

        root.findViewById(R.id.btn_galvo_set).setOnClickListener(v ->
                send(UC2CommandBuilder.galvoSetPosition(seekX.getProgress(), seekY.getProgress())));

        root.findViewById(R.id.btn_galvo_scan).setOnClickListener(v -> {
            int nx = 128, ny = 128;
            try { nx = Integer.parseInt(editNx.getText().toString()); } catch (NumberFormatException ignored) {}
            try { ny = Integer.parseInt(editNy.getText().toString()); } catch (NumberFormatException ignored) {}
            send(UC2CommandBuilder.galvoRasterScan(nx, ny, 500, 3500, 500, 3500, 1, true, true));
        });

        root.findViewById(R.id.btn_galvo_stop).setOnClickListener(v -> send(UC2CommandBuilder.galvoStop()));
    }

    private void setupSerialMonitor(View root) {
        textSerialOutput = root.findViewById(R.id.text_serial_output);
        scrollSerial = root.findViewById(R.id.scroll_serial);
        editSerialCmd = root.findViewById(R.id.edit_serial_cmd);
        switchSerialVisible = root.findViewById(R.id.switch_serial_visible);

        switchSerialVisible.setOnCheckedChangeListener((btn, checked) -> {
            scrollSerial.setVisibility(checked ? View.VISIBLE : View.GONE);
        });

        root.findViewById(R.id.btn_serial_send).setOnClickListener(v -> {
            String cmd = editSerialCmd.getText().toString().trim();
            if (!cmd.isEmpty()) {
                send(cmd);
                appendSerialText("> " + cmd + "\n", getResources().getColor(R.color.colorSendText));
                editSerialCmd.setText("");
            }
        });

        root.findViewById(R.id.btn_serial_clear).setOnClickListener(v ->
                textSerialOutput.setText(""));
    }

    // ---- Menus ----

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_control, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.clear) {
            textSerialOutput.setText("");
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ---- Serial callbacks ----

    @Override
    public void onNewData(byte[] data) {
        mainLooper.post(() -> receive(data));
    }

    @Override
    public void onRunError(Exception e) {
        mainLooper.post(() -> {
            status("connection lost: " + e.getMessage());
            disconnect();
        });
    }

    // ---- Serial connection ----

    private void connect() {
        UsbDevice device = null;
        UsbManager usbManager = (UsbManager) getActivity().getSystemService(Context.USB_SERVICE);
        for (UsbDevice v : usbManager.getDeviceList().values())
            if (v.getDeviceId() == deviceId)
                device = v;
        if (device == null) {
            status("connection failed: device not found");
            return;
        }
        UsbSerialDriver driver = UsbSerialProber.getDefaultProber().probeDevice(device);
        if (driver == null)
            driver = CustomProber.getCustomProber().probeDevice(device);
        if (driver == null) {
            status("connection failed: no driver for device");
            return;
        }
        if (driver.getPorts().size() < portNum) {
            status("connection failed: not enough ports at device");
            return;
        }
        usbSerialPort = driver.getPorts().get(portNum);
        UsbDeviceConnection usbConnection = usbManager.openDevice(driver.getDevice());
        if (usbConnection == null && usbPermission == UsbPermission.Unknown
                && !usbManager.hasPermission(driver.getDevice())) {
            usbPermission = UsbPermission.Requested;
            int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_IMMUTABLE : 0;
            PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(getActivity(), 0,
                    new Intent(INTENT_ACTION_GRANT_USB), flags);
            usbManager.requestPermission(driver.getDevice(), usbPermissionIntent);
            return;
        }
        if (usbConnection == null) {
            if (!usbManager.hasPermission(driver.getDevice()))
                status("connection failed: permission denied");
            else
                status("connection failed: open failed");
            return;
        }

        try {
            usbSerialPort.open(usbConnection);
            usbSerialPort.setParameters(baudRate, 8, 1, UsbSerialPort.PARITY_NONE);
            if (withIoManager) {
                usbIoManager = new SerialInputOutputManager(usbSerialPort, this);
                usbIoManager.start();
            }
            status("connected");
            connected = true;
        } catch (Exception e) {
            status("connection failed: " + e.getMessage());
            disconnect();
        }
    }

    private void disconnect() {
        connected = false;
        if (usbIoManager != null) {
            usbIoManager.setListener(null);
            usbIoManager.stop();
        }
        usbIoManager = null;
        try {
            if (usbSerialPort != null) usbSerialPort.close();
        } catch (IOException ignored) {}
        usbSerialPort = null;
    }

    private void send(String str) {
        if (!connected) {
            Toast.makeText(getActivity(), "not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            byte[] data = (str + '\n').getBytes();
            usbSerialPort.write(data, WRITE_WAIT_MILLIS);
            Log.d(TAG, "TX: " + str);
        } catch (Exception e) {
            onRunError(e);
        }
    }

    private void receive(byte[] data) {
        String str = new String(data, StandardCharsets.UTF_8);
        Log.d(TAG, "RX: " + str);
        if (textSerialOutput != null && switchSerialVisible != null && switchSerialVisible.isChecked()) {
            appendSerialText(str, getResources().getColor(R.color.colorReceiveText));
        }
    }

    private void appendSerialText(String text, int color) {
        if (textSerialOutput == null) return;
        SpannableString span = new SpannableString(text);
        span.setSpan(new ForegroundColorSpan(color), 0, text.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        textSerialOutput.append(span);
        // Trim if too long
        if (textSerialOutput.getText().length() > MAX_SERIAL_LOG_LENGTH) {
            CharSequence t = textSerialOutput.getText();
            textSerialOutput.setText(t.subSequence(t.length() - MAX_SERIAL_LOG_LENGTH / 2, t.length()));
        }
        // Auto-scroll
        scrollSerial.post(() -> scrollSerial.fullScroll(View.FOCUS_DOWN));
    }

    void status(String str) {
        Log.d(TAG, "Status: " + str);
        if (textStatus != null) {
            textStatus.setText(str);
        }
        if (statusIndicator != null) {
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setSize(12, 12);
            dot.setColor(connected ? Color.parseColor("#4CAF50") : Color.parseColor("#F44336"));
            statusIndicator.setBackground(dot);
        }
        if (textSerialOutput != null) {
            appendSerialText("[" + str + "]\n", getResources().getColor(R.color.colorStatusText));
        }
    }

    // Helper base class for seek bar listeners where only onProgressChanged varies
    private abstract static class SimpleSeekBarListener implements SeekBar.OnSeekBarChangeListener {
        @Override public void onStartTrackingTouch(SeekBar seekBar) {}
        @Override public void onStopTrackingTouch(SeekBar seekBar) {}
    }
}
