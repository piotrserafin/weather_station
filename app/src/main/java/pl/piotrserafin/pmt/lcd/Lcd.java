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
    private static final byte CMD_CLEAR_DISPLAY = 0x01;
    private static final byte CMD_RETURN_HOME = 0x02;
    private static final byte CMD_ENTRY_MODE_SET = 0x04;
    private static final byte CMD_DISPLAY_CTRL = 0x08;
    private static final byte CMD_SHIFT = 0x10;
    private static final byte CMD_FUNCTION_SET = 0x20;
    private static final byte CMD_CCGRAM = 0x40;
    private static final short CMD_CDDRAM = 0x80;

    //Entry register
    private static final byte REG_ENTRY_MODE_SET_SH = 0x01;
    private static final byte REG_ENTRY_MODE_SET_ID = 0x02;

    //Display control register
    private static final byte REG_CTRL_B = 0x01;
    private static final byte REG_CTRL_C = 0x02;
    private static final byte REG_CTRL_D = 0x04;

    //Function set register
    private static final byte REG_FUNC_F = 0x04;
    private static final byte REG_FUNC_N = 0x08;
    private static final byte REG_FUNC_DL = 0x10;

    private static final byte CDSHIFT_RL = 0x04;


    private static final byte LCD_DDRAM_ADDR_COL1_ROW2 = 0x40;

    private static final byte CLEAR_DISPLAY = CMD_CLEAR_DISPLAY;
    private static final byte RETURN_HOME = CMD_RETURN_HOME;
    private static final byte SET_NO_SHIFT = (CMD_ENTRY_MODE_SET | REG_ENTRY_MODE_SET_ID);
    private static final byte SET_8_BIT_MODE = ((CMD_FUNCTION_SET | REG_FUNC_DL) >> 4);
    private static final byte SET_4_BIT_MODE = CMD_FUNCTION_SET >> 4;
    private static final byte SET_2_ROWS_5_X_7_DOTS = (CMD_FUNCTION_SET | REG_FUNC_N);

    private static final int ROWS = 2;
    private static final int COLUMNS = 16;

    private static byte lcdCtrl = CMD_DISPLAY_CTRL;

    private Gpio resetPin;
    private Gpio enablePin;
    private List<Gpio> dataBus;

    public enum Pin {
        RS, EN, D4, D5, D6, D7
    }

    public Lcd() throws IOException {

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

        delay(35);

        //4-bit mode
        writeCmd(SET_8_BIT_MODE, true);
        delay(35);
        writeCmd(SET_8_BIT_MODE, true);
        delay(35);
        writeCmd(SET_8_BIT_MODE, true);
        delay(35);
        writeCmd(SET_4_BIT_MODE, true);
        delay(35);

        //Set 2 rows, 5x7 dots
        writeCmd(SET_2_ROWS_5_X_7_DOTS);
        delay(35);

        // Rest of the initialisation sequence
        setDisplay(true);
        setBlink(false);
        setCursor(false);
        clearDisplay();

        writeCmd(SET_NO_SHIFT);
        delay(35);
    }

    public void clearDisplay() throws IOException {
        writeCmd(CLEAR_DISPLAY);
        delay(5);
    }

    public void returnHome() throws IOException {
        writeCmd(RETURN_HOME);
        delay(5);
    }

    public void setDisplay(boolean state) throws IOException {
        if (state)
            lcdCtrl |= REG_CTRL_D;
        else
            lcdCtrl &= ~REG_CTRL_D;

        writeCmd(lcdCtrl);
    }

    public void setCursor(boolean state) throws IOException {
        if (state)
            lcdCtrl |= REG_CTRL_C;
        else
            lcdCtrl &= ~REG_CTRL_C;

        writeCmd(lcdCtrl);
    }

    public void setBlink(boolean state) throws IOException {
        if (state)
            lcdCtrl |= REG_CTRL_B;
        else
            lcdCtrl &= ~REG_CTRL_B;

        writeCmd(lcdCtrl);
    }


    public void setPosition(int row, int column) throws IOException {

        if ((row >= ROWS) || (row < 0))
            row = 0;
        if ((column >= COLUMNS) || (column < 0))
            column = 0;

        writeCmd((byte) (CMD_CDDRAM | ((LCD_DDRAM_ADDR_COL1_ROW2 * (row)) + (column))));
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
        strobe();
        delay(1);
    }

    private void strobe() throws IOException {
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
