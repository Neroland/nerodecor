package za.co.neroland.nerodecor.chroma;

import java.util.Locale;
import java.util.Optional;

/**
 * An immutable Chroma colour: 8-bit sRGB channels plus a 4-bit brightness (0-15, the emitted light
 * level). The packed form is {@code brightness << 24 | r << 16 | g << 8 | b}.
 */
public final class ChromaColor {

    public static final int MAX_BRIGHTNESS = 15;

    public static final ChromaColor BLACK = new ChromaColor(0x000000, MAX_BRIGHTNESS);
    public static final ChromaColor WHITE = new ChromaColor(0xFFFFFF, MAX_BRIGHTNESS);

    private final int rgb;
    private final int brightness;

    private ChromaColor(int rgb, int brightness) {
        this.rgb = rgb & 0xFFFFFF;
        this.brightness = brightness;
    }

    /** Full-brightness colour from 8-bit channels (each clamped to 0-255). */
    public static ChromaColor of(int r, int g, int b) {
        return of(r, g, b, MAX_BRIGHTNESS);
    }

    /** Colour from 8-bit channels and a brightness; out-of-range values are clamped. */
    public static ChromaColor of(int r, int g, int b, int brightness) {
        return new ChromaColor(clamp8(r) << 16 | clamp8(g) << 8 | clamp8(b), clampBrightness(brightness));
    }

    /** Colour from a 24-bit RGB value (upper bits ignored) and a brightness (clamped). */
    public static ChromaColor ofRgb(int rgb, int brightness) {
        return new ChromaColor(rgb, clampBrightness(brightness));
    }

    /** Inverse of {@link #packed()}; bits above the brightness nibble are ignored. */
    public static ChromaColor fromPacked(int packed) {
        return new ChromaColor(packed & 0xFFFFFF, (packed >>> 24) & 0xF);
    }

    /** Whether {@code packed} is a canonical packed colour (no bits set above the brightness nibble). */
    public static boolean isValidPacked(int packed) {
        return (packed >>> 28) == 0;
    }

    /** Parses {@code #RRGGBB} (the leading {@code #} is optional); brightness is full. */
    public static Optional<ChromaColor> parseHex(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String s = text.trim();
        if (s.startsWith("#")) {
            s = s.substring(1);
        }
        if (s.length() != 6) {
            return Optional.empty();
        }
        int value = 0;
        for (int i = 0; i < 6; i++) {
            int digit = Character.digit(s.charAt(i), 16);
            if (digit < 0) {
                return Optional.empty();
            }
            value = value << 4 | digit;
        }
        return Optional.of(new ChromaColor(value, MAX_BRIGHTNESS));
    }

    /** Builds a colour from HSV (each component 0-1, hue wraps). */
    public static ChromaColor fromHsv(double hue, double saturation, double value, int brightness) {
        double h = hue - StrictMath.floor(hue);
        double s = clamp01(saturation);
        double v = clamp01(value);
        double scaled = h * 6.0;
        int sector = (int) scaled;
        if (sector > 5) {
            sector = 5;
        }
        double f = scaled - sector;
        double p = v * (1.0 - s);
        double q = v * (1.0 - s * f);
        double t = v * (1.0 - s * (1.0 - f));
        double r;
        double g;
        double b;
        switch (sector) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return of(to8(r), to8(g), to8(b), brightness);
    }

    public int red() {
        return rgb >>> 16 & 0xFF;
    }

    public int green() {
        return rgb >>> 8 & 0xFF;
    }

    public int blue() {
        return rgb & 0xFF;
    }

    public int rgb() {
        return rgb;
    }

    public int brightness() {
        return brightness;
    }

    public int packed() {
        return brightness << 24 | rgb;
    }

    public ChromaColor withBrightness(int newBrightness) {
        return new ChromaColor(rgb, clampBrightness(newBrightness));
    }

    /** {@code #RRGGBB}, upper case. */
    public String toHex() {
        return String.format(Locale.ROOT, "#%06X", rgb);
    }

    /** HSV in {@code [0,1]}: {@code {hue, saturation, value}}. For the picker; allocates. */
    public double[] toHsv() {
        double r = red() / 255.0;
        double g = green() / 255.0;
        double b = blue() / 255.0;
        double max = StrictMath.max(r, StrictMath.max(g, b));
        double min = StrictMath.min(r, StrictMath.min(g, b));
        double delta = max - min;
        double h;
        if (delta == 0.0) {
            h = 0.0;
        } else if (max == r) {
            h = ((g - b) / delta) / 6.0;
        } else if (max == g) {
            h = ((b - r) / delta + 2.0) / 6.0;
        } else {
            h = ((r - g) / delta + 4.0) / 6.0;
        }
        if (h < 0.0) {
            h += 1.0;
        }
        return new double[] {h, max == 0.0 ? 0.0 : delta / max, max};
    }

    /** HSV saturation, 0-1. */
    public double saturation() {
        int max = StrictMath.max(red(), StrictMath.max(green(), blue()));
        int min = StrictMath.min(red(), StrictMath.min(green(), blue()));
        return max == 0 ? 0.0 : (max - min) / (double) max;
    }

    /** WCAG relative luminance of the RGB channels (0-1), ignoring brightness. */
    public double relativeLuminance() {
        return 0.2126 * OkLab.srgbToLinear(red() / 255.0)
                + 0.7152 * OkLab.srgbToLinear(green() / 255.0)
                + 0.0722 * OkLab.srgbToLinear(blue() / 255.0);
    }

    /** Relative luminance scaled by brightness: what the flash clamp measures. */
    public double emittedLuminance() {
        return relativeLuminance() * brightness / MAX_BRIGHTNESS;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ChromaColor other && other.rgb == rgb && other.brightness == brightness;
    }

    @Override
    public int hashCode() {
        return packed();
    }

    @Override
    public String toString() {
        return toHex() + "/" + brightness;
    }

    static int clamp8(int v) {
        return v < 0 ? 0 : v > 255 ? 255 : v;
    }

    static int clampBrightness(int v) {
        return v < 0 ? 0 : v > MAX_BRIGHTNESS ? MAX_BRIGHTNESS : v;
    }

    static double clamp01(double v) {
        return v < 0.0 ? 0.0 : v > 1.0 ? 1.0 : v;
    }

    static int to8(double unit) {
        return (int) StrictMath.round(clamp01(unit) * 255.0);
    }
}
