package org.cgutman.usbip.config;

import org.cgutman.usbip.service.TabletData;
import org.cgutman.usbip.service.UsbIpService;
import org.cgutman.usbip.usb.MockDeviceConnection;
import org.cgutman.usbipserverforandroid.R;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.os.Bundle;
import android.view.MotionEvent;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

public class UsbIpConfig extends ComponentActivity {
    private final Point screenSize = new Point();
    private boolean screenSizeSet = false;

    private ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                // We don't actually care if the permission is granted or not. We will launch the service anyway.
                startService(new Intent(UsbIpConfig.this, UsbIpService.class));
            });

    private void enqueueTabletData(MockDeviceConnection dev, boolean penInRange,
                                    int x, int y, int pressure,
                                    boolean buttonPrimary, boolean buttonSecondary) {
        TabletData data = new TabletData();
        data.penInRange = penInRange;
        data.x = x;
        data.y = y;
        data.pressure = pressure;
        data.buttonPrimaryPressed = buttonPrimary;
        data.buttonSecondaryPressed = buttonSecondary;
        dev.pendingData.offer(data);
    }

    private static final long MIN_SAMPLE_INTERVAL_MS = 1000 / 240; // ~4ms for 240Hz
    private long lastSampleTimeMs = 0;

    private void handlePenTouchOrHover(MotionEvent event) {
        MockDeviceConnection dev = UsbIpService.activePenDevice;

        boolean buttonPrimary = event.isButtonPressed(MotionEvent.BUTTON_STYLUS_PRIMARY);
        boolean buttonSecondary = event.isButtonPressed(MotionEvent.BUTTON_STYLUS_SECONDARY);

        float xPrecision = event.getXPrecision();
        float yPrecision = event.getYPrecision();

        // Drain historical samples, skipping any that are closer together than 240Hz allows
        for (int i = 0; i < event.getHistorySize(); i++) {
            long t = event.getHistoricalEventTime(i);
            if (t - lastSampleTimeMs < MIN_SAMPLE_INTERVAL_MS) continue;
            lastSampleTimeMs = t;
            int hx = Math.round(event.getHistoricalX(0, i) * xPrecision);
            int hy = Math.round(event.getHistoricalY(0, i) * yPrecision);
            int hp = Math.round(event.getHistoricalPressure(0, i) * 4095);
            if (dev != null) {
                enqueueTabletData(dev, true, hx, hy, hp, buttonPrimary, buttonSecondary);
            }
        }

        // Current sample
        long t = event.getEventTime();
        if (t - lastSampleTimeMs >= MIN_SAMPLE_INTERVAL_MS) {
            lastSampleTimeMs = t;
            int x = Math.round(event.getRawX() * xPrecision);
            int y = Math.round(event.getRawY() * yPrecision);
            int pressure = Math.round(event.getPressure() * 4095);
            if (dev != null) {
                enqueueTabletData(dev, true, x, y, pressure, buttonPrimary, buttonSecondary);
            }
        }

        if (!screenSizeSet) {
            Intent broadcastSize = new Intent("maxSize");
            broadcastSize.putExtra("maxX", (int)Math.ceil(screenSize.x * xPrecision));
            broadcastSize.putExtra("maxY", (int)Math.ceil(screenSize.y * yPrecision));
            sendBroadcast(broadcastSize);
            screenSizeSet = true;
        }
    }

    private void handlePenWentOutOfRange() {
        Intent broadcast = new Intent("penOutOfRange");
        sendBroadcast(broadcast);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS) {
            if (event.getAction() == MotionEvent.ACTION_HOVER_MOVE)
                handlePenTouchOrHover(event);
            if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT)
                handlePenWentOutOfRange();
            return true;
        } else {
            return super.onGenericMotionEvent(event);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_MOVE && event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS) {
            handlePenTouchOrHover(event);
            return true;
        } else {
            return super.onGenericMotionEvent(event);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_usbip_config);

        if (ContextCompat.checkSelfPermission(UsbIpConfig.this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            startService(new Intent(UsbIpConfig.this, UsbIpService.class));
        } else {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }

        this.getWindowManager().getDefaultDisplay().getRealSize(screenSize);
    }

    @Override
    protected void onDestroy() {
        stopService(new Intent(UsbIpConfig.this, UsbIpService.class));
        super.onDestroy();
    }
}
