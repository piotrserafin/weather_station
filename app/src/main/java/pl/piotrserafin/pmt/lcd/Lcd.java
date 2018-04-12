package pl.piotrserafin.pmt.lcd;

import android.util.Log;

import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.PeripheralManager;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import pl.piotrserafin.pmt.utils.RpiSettings;

/**
 * Created by pserafin on 20.03.2018.
 */

public class Lcd implements AutoCloseable {

    private static final String TAG = Lcd.class.getSimpleName();

    // HD44780U Commands
    private static final int CMD_CLEAR_DISPLAY = 0x01;
    private static final int CMD_RETURN_HOME = 0x02;
    private static final int CMD_ENTRY_MODE_SET = 0x04;
    private static final int CMD_DISPLAY_CTRL = 0x08;
    private static final int CMD_SHIFT = 0x10;
    private static final int CMD_FUNCTION_SET = 0x20;
    private static final int CMD_CCGRAM = 0x40;
    private static final int CMD_CDDRAM = 0x80;

    //Entry register
    private static final int REG_ENTRY_SH = 0x01;
    private static final int REG_ENTRY_ID = 0x02;

    //Control register
    private static final int REG_CTRL_BLINK = 0x01;
    private static final int REG_CTRL_CURSOR = 0x02;
    private static final int REG_CTRL_DISPLAY = 0x04;

    //Function register
    private static final int REG_FUNC_F = 0x04;
    private static final int REG_FUNC_N = 0x08;
    private static final int REG_FUNC_DL = 0x10;

    private static final int CDSHIFT_RL = 0x0;

    private static final byte MODE_4_BIT = 0x02;
    private static final byte LCD_8_BIT_FUNCTION = 0x28;

    private static final int LCD_SET_DDRAM_ADDR = 0x80;
    private static final byte LCD_DDRAM_ADDR_COL1_ROW0 = 0x40;

    private static final byte LCD_DISPLAY_ON = 0x0F;
    private static final byte LCD_CLEAR_DISPLAY = 0x01;
    private static final byte LCD_RETURN_HOME = 0x02;
    private static final byte LCD_SET_ENTRY_MODE_NO_SHIFT_DISPLAY = 0x06;

    private static final byte ROWS = 2;
    private static final byte COLUMNS = 16;

    private Gpio resetPin;
    private Gpio enablePin;
    private List<Gpio> dataBus;

    public enum Pin {
        RS, EN, D4, D5, D6, D7
    }

    public Lcd() throws IOException {

        Log.d(TAG, "Constructor");

        PeripheralManager service = PeripheralManager.getInstance();

        try {
            resetPin = service.openGpio(RpiSettings.getLcdGpioName(Pin.RS));
            resetPin.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);

            enablePin = service.openGpio(RpiSettings.getLcdGpioName(Pin.EN));
            enablePin.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);


            dataBus = Arrays.asList(
                    service.openGpio(RpiSettings.getLcdGpioName(Pin.D4)),
                    service.openGpio(RpiSettings.getLcdGpioName(Pin.D5)),
                    service.openGpio(RpiSettings.getLcdGpioName(Pin.D6)),
                    service.openGpio(RpiSettings.getLcdGpioName(Pin.D7)));

            for (Gpio bit : dataBus) {
                bit.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
            }
        } catch (IOException e) {
            try {
                close();
            } catch (IOException ignored) {
            }
            throw e;
        }

        writeCmd(MODE_4_BIT, true);
        writeCmd(LCD_8_BIT_FUNCTION);
        writeCmd(LCD_DISPLAY_ON);
        writeCmd(LCD_SET_ENTRY_MODE_NO_SHIFT_DISPLAY);
        writeCmd(LCD_CLEAR_DISPLAY);
    }

    public void clearDisplay() throws IOException {
        writeCmd(LCD_CLEAR_DISPLAY);
    }

    public void returnHome() throws IOException {
        writeCmd(LCD_RETURN_HOME);
    }

    public void setCursor(int row, int column) throws IOException {
        writeCmd((byte) (LCD_SET_DDRAM_ADDR | ((LCD_DDRAM_ADDR_COL1_ROW0 * (row - 1)) + (column - 1))));
    }

    public void setText(String val) throws IOException {
        resetPin.setValue(true);
        for (char b : val.toCharArray()) {
            setChar(b);
        }
    }

    private void setChar(char c) throws IOException {
        resetPin.setValue(true);
        write8((byte) c);
    }

    private void writeCmd(byte command) throws IOException {
        writeCmd(command, false);
    }

    private void writeCmd(byte command, boolean fourBitMode) throws IOException {
        resetPin.setValue(false);
        if (fourBitMode) {
            write4(command);
        } else {
            write8(command);
        }
    }

    private void write8(byte value) throws IOException {
        write4((byte) (value >> 4));
        write4(value);
    }

    private void write4(byte value) throws IOException {
        for (int i = 0; i < dataBus.size(); i++) {
            Gpio pin = dataBus.get(i);
            pin.setValue(((value >> i & 0x01) != 0));
        }
        pulseEnable();
        delay(1);
    }

    private void pulseEnable() throws IOException {
        enablePin.setValue(false);
        delay(1);
        enablePin.setValue(true);
        delay(1);
        enablePin.setValue(false);
        delay(1);
    }

    private void delay(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Log.e(TAG, e.getMessage());
        }
    }

    @Override
    public void close() throws IOException {

        if (resetPin != null) {
            try {
                resetPin.close();
            } finally {
                resetPin = null;
            }
        }

        if (enablePin != null) {
            try {
                enablePin.close();
            } finally {
                enablePin = null;
            }
        }

        for (Gpio pin : dataBus) {
            if (pin != null) {
                try {
                    pin.close();
                } finally {
                    pin = null;
                }
            }
        }
    }
}
