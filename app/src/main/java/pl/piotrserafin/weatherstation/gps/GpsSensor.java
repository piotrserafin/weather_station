package pl.piotrserafin.weatherstation.gps;

import android.os.Handler;
import android.util.Log;

import com.google.android.things.pio.PeripheralManager;
import com.google.android.things.pio.UartDevice;
import com.google.android.things.pio.UartDeviceCallback;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by pserafin on 10.04.2018.
 */

public class GpsSensor implements AutoCloseable {

    private static final String TAG = GpsSensor.class.getSimpleName();

    private UartDevice uartDevice;
    private GpsParser gpsParser;

    private float accuracy;

    public GpsSensor(String uartName, int baudRate) throws IOException {
        this(uartName, baudRate, null);
    }

    public GpsSensor(String uartName, int baudRate, Handler handler) throws IOException {

        try {
            PeripheralManager manager = PeripheralManager.getInstance();
            UartDevice device = manager.openUartDevice(uartName);
            init(device, baudRate, handler);
        } catch (IOException | RuntimeException e) {
            close();
            throw e;
        }
    }

    private void init(UartDevice device, int baudRate, Handler handler) throws IOException {
        uartDevice = device;
        uartDevice.setBaudrate(baudRate);
        uartDevice.registerUartDeviceCallback(handler, gpsCallback);
        gpsParser = new GpsParser();
    }

    public float getAccuracy() {
        return accuracy;
    }

    public void setAccuracy(float accuracy) {
        this.accuracy = accuracy;
    }

    public void setGpsCallback(GpsCallback gpsCallback) {
        gpsParser.setGpsCallback(gpsCallback);
    }
    
    @Override
    public void close() throws IOException {
        if (uartDevice != null) {
            uartDevice.unregisterUartDeviceCallback(gpsCallback);
            try {
                uartDevice.close();
            } finally {
                uartDevice = null;
            }
        }
    }
    
    private UartDeviceCallback gpsCallback = new UartDeviceCallback() {
        @Override
        public boolean onUartDeviceDataAvailable(UartDevice uart) {
            try {
                readBuffer();
            } catch (IOException e) {
                Log.w(TAG, "Unable to read UART data", e);
            }

            return true;
        }

        @Override
        public void onUartDeviceError(UartDevice uart, int errorCode) {
            Log.w(TAG, "Error: " + errorCode);
        }
    };

    private static final int CHUNK_SIZE = 512;

    private void readBuffer() throws IOException {
        byte[] buffer = new byte[CHUNK_SIZE];
        int count;
        while ((count = uartDevice.read(buffer, buffer.length)) > 0) {
            processBuffer(buffer, count);
        }
    }

    private boolean mFrameFlag = false;
    private ByteBuffer mMessageBuffer = ByteBuffer.allocate(CHUNK_SIZE*2);
    private void processBuffer(byte[] buffer, int count) {
        for (int i = 0; i < count; i++) {
            if (gpsParser.getStartDelimiter() == buffer[i]) {
                handleMsgStart();
            } else if (gpsParser.getEndDelimiter() == buffer[i]) {
                handleMsgEnd();
            } else if (buffer[i] != 0){
                //Insert all other characters except '0's into the buffer
                mMessageBuffer.put(buffer[i]);
            }
        }
    }

    private void handleMsgStart() {
        mMessageBuffer.clear();
        mFrameFlag = true;
    }

    private void handleMsgEnd() {
        if (!mFrameFlag) {
            // We never saw the whole message, discard
            resetBuffer();
            return;
        }

        // Gather the bytes into a single array
        mMessageBuffer.flip();
        byte[] raw = new byte[mMessageBuffer.limit()];
        mMessageBuffer.get(raw);

        gpsParser.parseMessage(raw);

        // Reset the buffer state
        resetBuffer();
    }

    private void resetBuffer() {
        mMessageBuffer.clear();
        mFrameFlag = false;
    }
}
