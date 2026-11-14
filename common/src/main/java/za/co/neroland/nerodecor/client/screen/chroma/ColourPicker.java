package za.co.neroland.nerodecor.client.screen.chroma;

import za.co.neroland.nerodecor.chroma.ChromaColor;

/**
 * The hub screen's colour state: the primary colour as HSV plus a light level (0-15), and a secondary colour that
 * gradients and effects run from. Kept in HSV so dragging through grey or black does not lose the hue.
 */
final class ColourPicker {

    private double hue = 0.52;
    private double saturation = 0.85;
    private double value = 1.0;
    private int brightness = ChromaColor.MAX_BRIGHTNESS;
    private ChromaColor secondary = ChromaColor.of(0x8A, 0x5C, 0xFF);

    ChromaColor primary() {
        return ChromaColor.fromHsv(hue, saturation, value, brightness);
    }

    ChromaColor secondary() {
        return secondary;
    }

    double hue() {
        return hue;
    }

    double saturation() {
        return saturation;
    }

    double value() {
        return value;
    }

    int brightness() {
        return brightness;
    }

    void setHue(double h) {
        hue = Math.max(0.0, Math.min(0.9999, h));
    }

    void setSaturationValue(double s, double v) {
        saturation = Math.max(0.0, Math.min(1.0, s));
        value = Math.max(0.0, Math.min(1.0, v));
    }

    void setBrightness(int b) {
        brightness = Math.max(0, Math.min(ChromaColor.MAX_BRIGHTNESS, b));
    }

    /** Loads a colour (keeps the hue when it is grey). */
    void set(ChromaColor colour) {
        double[] hsv = colour.toHsv();
        if (hsv[1] > 0.0001 && hsv[2] > 0.0001) {
            hue = hsv[0];
        }
        saturation = hsv[1];
        value = hsv[2];
        brightness = colour.brightness();
    }

    void swap() {
        ChromaColor old = primary();
        set(secondary);
        secondary = old;
    }
}
