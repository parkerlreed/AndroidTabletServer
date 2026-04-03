package org.cgutman.usbip.usb;

import android.hardware.usb.UsbConfiguration;
import android.hardware.usb.UsbInterface;

import org.cgutman.usbip.service.TabletData;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

class Result {
    public final boolean handled;
    public final int length;

    public Result(boolean handled, int length) {
        this.handled = handled;
        this.length = length;
    }
}

public class MockDeviceConnection {
    public final LinkedBlockingQueue<TabletData> pendingData = new LinkedBlockingQueue<>();
    private final TabletData lastData = new TabletData();

    private final ByteBuffer descriptor;
    private final ByteBuffer configuration;
    private final ByteBuffer initialReport;

    public MockDeviceConnection() {
        descriptor = ByteBuffer.allocate(UsbDeviceDescriptor.DESCRIPTOR_SIZE);
        descriptor.order(ByteOrder.LITTLE_ENDIAN);
        descriptor.put((byte) UsbDeviceDescriptor.DESCRIPTOR_SIZE)
                .put((byte) 1) // descriptor type
                .putShort((short) 0x0110) // bcdUSB
                .put((byte) 0) // device class
                .put((byte) 0) // device sub class
                .put((byte) 0) // device protocol
                .put((byte) 8) // max packet size
                .putShort((short) 5824) // vendor id
                .putShort((short) 1500) // product id
                .putShort((short) 0) // bcd device
                .put((byte) 0) // iManufacturer
                .put((byte) 0) // iProduct
                .put((byte) 0) // iSerialNumber
                .put((byte) 1); // num configurations

        configuration = ByteBuffer.allocate(0x22);
        configuration.order(ByteOrder.LITTLE_ENDIAN);
        // configuration
        configuration.put((byte) 9) // bLength
                .put((byte) 2) // bDescriptorType
                .putShort((short) 0x22) // wTotalLength
                .put((byte) 1) // bNumInterfaces
                .put((byte) 1) // bConfigurationValue
                .put((byte) 0) // iConfiguration 0 is empty string
                .put((byte) 0x80) // bmAttributes
                .put((byte) 0x32); // bMaxPower

        // interface
        configuration.put((byte) 9) // bLength=9
                .put((byte) 4) // bDescriptorType
                .put((byte) 0) // bInterfaceNumber
                .put((byte) 0) // bAlternateSetting=0,
                .put((byte) 1) // bNumEndpoints=1,
                .put((byte) 0x03) // bInterfaceClass=HID,
                .put((byte) 0) // bInterfaceSubClass=1,
                .put((byte) 0) // bInterfaceProtocol=2, 2 is mouse, 0 is None
                .put((byte) 0); // iInterface=0)

        // description (hid class)
        configuration.put((byte) 9) // bLength
                .put((byte) 0x21) // bDescriptorType
                .putShort((short) 0) // bcdHID mouse, keyboard is also 1...
                .put((byte) 0) // bCountryCode
                .put((byte) 1) // bNumDescriptors
                .put((byte) 0x22) // bDescriptprType2
                .putShort((short) 90); // wDescriptionLength

        // endpoint
        configuration.put((byte) 7) // bLength
                .put((byte) 5) // bDescriptorType
                .put((byte) 0x81) // bEndpointAddress
                .put((byte) 3) // bmAttributes
                .putShort((short) 12) // wMaxPacketSize
                .put((byte) 1); // bInterval 1 ms


        // 90-byte report descriptor matching the 12-byte report from bulkTransfer():
        //   byte 0:     report ID (1)
        //   byte 1:     flags (bit1=btn1, bit2=btn2, bit5=inRange)
        //   bytes 2-3:  X (int16 LE)
        //   bytes 4-5:  Y (int16 LE)
        //   bytes 6-7:  pressure (int16 LE, 0-4095)
        //   bytes 8-9:  tiltX (int16 LE)
        //   bytes 10-11: tiltY (int16 LE)
        initialReport = ByteBuffer.allocate(90);
        initialReport.order(ByteOrder.LITTLE_ENDIAN);
        initialReport
                .put((byte) 0x05).put((byte) 0x0D)         // Usage Page (Digitizer)
                .put((byte) 0x09).put((byte) 0x02)         // Usage (Pen)
                .put((byte) 0xA1).put((byte) 0x01)         // Collection (Application)
                .put((byte) 0x85).put((byte) 0x01)         // Report ID (1)
                // byte 1: flags - 8 bits
                .put((byte) 0x09).put((byte) 0x32)         // Usage (In Range)
                .put((byte) 0x75).put((byte) 0x08)         // Report Size (8)
                .put((byte) 0x95).put((byte) 0x01)         // Report Count (1)
                .put((byte) 0x81).put((byte) 0x02)         // Input (Data, Variable, Absolute)
                // bytes 2-3: X - 16 bits
                .put((byte) 0x05).put((byte) 0x01)         // Usage Page (Generic Desktop)
                .put((byte) 0x09).put((byte) 0x30)         // Usage (X)
                .put((byte) 0x15).put((byte) 0x00)         // Logical Minimum (0)
                .put((byte) 0x26).put((byte) 0xFF).put((byte) 0x7F) // Logical Maximum (32767)
                .put((byte) 0x75).put((byte) 0x10)         // Report Size (16)
                .put((byte) 0x95).put((byte) 0x01)         // Report Count (1)
                .put((byte) 0x81).put((byte) 0x02)         // Input (Data, Variable, Absolute)
                // bytes 4-5: Y - 16 bits
                .put((byte) 0x09).put((byte) 0x31)         // Usage (Y)
                .put((byte) 0x15).put((byte) 0x00)         // Logical Minimum (0)
                .put((byte) 0x26).put((byte) 0xFF).put((byte) 0x7F) // Logical Maximum (32767)
                .put((byte) 0x75).put((byte) 0x10)         // Report Size (16)
                .put((byte) 0x95).put((byte) 0x01)         // Report Count (1)
                .put((byte) 0x81).put((byte) 0x02)         // Input (Data, Variable, Absolute)
                // bytes 6-7: pressure - 16 bits
                .put((byte) 0x05).put((byte) 0x0D)         // Usage Page (Digitizer)
                .put((byte) 0x09).put((byte) 0x30)         // Usage (Tip Pressure)
                .put((byte) 0x15).put((byte) 0x00)         // Logical Minimum (0)
                .put((byte) 0x26).put((byte) 0xFF).put((byte) 0x0F) // Logical Maximum (4095)
                .put((byte) 0x75).put((byte) 0x10)         // Report Size (16)
                .put((byte) 0x95).put((byte) 0x01)         // Report Count (1)
                .put((byte) 0x81).put((byte) 0x02)         // Input (Data, Variable, Absolute)
                // bytes 8-9: tiltX - 16 bits signed
                .put((byte) 0x05).put((byte) 0x01)         // Usage Page (Generic Desktop)
                .put((byte) 0x09).put((byte) 0x33)         // Usage (Rx)
                .put((byte) 0x16).put((byte) 0x01).put((byte) 0x80) // Logical Minimum (-32767)
                .put((byte) 0x26).put((byte) 0xFF).put((byte) 0x7F) // Logical Maximum (32767)
                .put((byte) 0x75).put((byte) 0x10)         // Report Size (16)
                .put((byte) 0x95).put((byte) 0x01)         // Report Count (1)
                .put((byte) 0x81).put((byte) 0x02)         // Input (Data, Variable, Absolute)
                // bytes 10-11: tiltY - 16 bits signed
                .put((byte) 0x09).put((byte) 0x34)         // Usage (Ry)
                .put((byte) 0x16).put((byte) 0x01).put((byte) 0x80) // Logical Minimum (-32767)
                .put((byte) 0x26).put((byte) 0xFF).put((byte) 0x7F) // Logical Maximum (32767)
                .put((byte) 0x75).put((byte) 0x10)         // Report Size (16)
                .put((byte) 0x95).put((byte) 0x01)         // Report Count (1)
                .put((byte) 0x81).put((byte) 0x02)         // Input (Data, Variable, Absolute)
                .put((byte) 0xC0);                          // End Collection
    }

    public void close() {
    }

    public boolean setInterface(UsbInterface intf) {
        throw new RuntimeException("Stub!");
    }

    public boolean setConfiguration(UsbConfiguration configuration) {
        throw new RuntimeException("Stub!");
    }

    public int controlTransfer(int requestType, int request, int value, int index, byte[] buffer, int length, int timeout) {
        Result res = new Result(false, 0);

        if (requestType == 0x80) // Host Request
        {
            if (request == 0x06) // Get Descriptor
                res = handleGetDescriptor(request, value, index, buffer, length);
            else if (request == 0x00) { // Get STATUS
                buffer[0] = 1;
                buffer[1] = 0;
                res = new Result(true, 2);
            }
        } else if (requestType == 0x81) {
            if (request == 0x06) { // Get Descriptor
                // send initial report
                if (value == 0x2200) {
                    int reportLen = Math.min(length, initialReport.capacity());
                    initialReport.rewind();
                    initialReport.get(buffer, 0, reportLen);
                    res = new Result(true, reportLen);
                }
            }
        } else if (requestType == 0x21) { // Host Request
            if (request == 0xa) { // Set Idle
                res = new Result(true, 0);
            }
        }
        return res.length;
    }

    public int bulkTransfer(byte[] buffer, int length, int timeout) {
        if (length == 12) {
            TabletData data;
            try {
                data = pendingData.poll(timeout, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                return -110; // ETIMEDOUT
            }
            if (data == null) {
                return -110; // ETIMEDOUT - no new sample, caller will retry
            }
            lastData.penInRange = data.penInRange;
            lastData.x = data.x;
            lastData.y = data.y;
            lastData.pressure = data.pressure;
            lastData.tiltX = data.tiltX;
            lastData.tiltY = data.tiltY;
            lastData.buttonPrimaryPressed = data.buttonPrimaryPressed;
            lastData.buttonSecondaryPressed = data.buttonSecondaryPressed;
            ByteBuffer input = ByteBuffer.allocate(length);
            input.order(ByteOrder.LITTLE_ENDIAN);
            input.put((byte) 1); // to comply with other parser
            byte penByte = (byte) ((lastData.penInRange ? 0x20 : 0) |
                    (lastData.buttonPrimaryPressed ? 2 : 0)
                    | (lastData.buttonSecondaryPressed ? 4 : 0));
            input.put(penByte);
            input.putShort((short) lastData.x);
            input.putShort((short) lastData.y);
            input.putShort((short) lastData.pressure);
            input.putShort((short) lastData.tiltX);
            input.putShort((short) lastData.tiltY);

            input.rewind();
            input.get(buffer);
            return length;
        }
        return 0;
    }

    private Result handleGetDescriptor(int request, int value, int index, byte[] buffer, int length) {
        if (value == 0x100) { // Device
            descriptor.rewind(); // this resets position to 0, needed for .get to work
            descriptor.get(buffer, 0, UsbDeviceDescriptor.DESCRIPTOR_SIZE); // copy it over
            return new Result(true, UsbDeviceDescriptor.DESCRIPTOR_SIZE);
        } else if (value == 0x200) { // Configuration
            configuration.rewind(); // this resets position to 0, needed for .get to work
            configuration.get(buffer, 0, length); // copy it over
            return new Result(true, length);
        }
        return new Result(false, 0);
    }
}
