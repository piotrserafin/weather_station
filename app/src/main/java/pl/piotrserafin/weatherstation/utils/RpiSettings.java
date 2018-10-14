package pl.piotrserafin.weatherstation.utils;

import pl.piotrserafin.weatherstation.lcd.Lcd;

public class RpiSettings {
    private static final String TAG = "rpi3";

    public static String getUartName() {
        return "UART0";
    }

    public static String getButtonGpioName() { return "BCM23"; }

    public static String getLcdGpioName(Lcd.Pin pin) {
        switch (pin) {
            case RS:
                return "BCM6";
            case EN:
                return "BCM19";
            case D4:
                return "BCM26";
            case D5:
                return "BCM16";
            case D6:
                return "BCM20";
            case D7:
                return "BCM21";
            default:
                throw new IllegalArgumentException(TAG + ("Unknown Pin" + pin.name() + ")"));
        }
    }

    public static String getI2cBusName() { return "I2C1"; }
}
