package za.co.neroland.nerodecor.client.screen.chroma;

/** The Chroma Hub screen's palette: near-black navy panels, cyan and violet accents. ARGB, alpha always set. */
final class HubTheme {

    static final int SHADE = 0xB0020409;
    static final int PANEL = 0xF20A0F1C;
    static final int PANEL_INNER = 0xF00D1424;
    static final int WELL = 0xFF070B15;
    static final int ROW = 0xFF101A2E;
    static final int ROW_HOVER = 0xFF15243F;
    static final int ROW_SELECTED = 0xFF1A2F52;
    static final int LINE = 0xFF1E2C47;

    static final int CYAN = 0xFF22E8FF;
    static final int CYAN_DIM = 0xFF168FA3;
    static final int VIOLET = 0xFF8A5CFF;
    static final int VIOLET_DIM = 0xFF4E3699;

    static final int TEXT = 0xFFDDE8FF;
    static final int SUBTLE = 0xFF7C8AA8;
    /** Body text on {@link #WELL}/{@link #PANEL}: 4.9:1 and 4.8:1 (WCAG AA needs 4.5:1). */
    static final int FAINT = 0xFF707FA0;
    static final int OK = 0xFF5CFFB0;
    static final int WARN = 0xFFFFC75C;
    static final int BAD = 0xFFFF6B8A;

    static final int BUTTON = 0xFF111B30;
    static final int BUTTON_HOVER = 0xFF183056;
    static final int BUTTON_EDGE = 0xFF2A4166;
    static final int DISABLED = 0xFF0C1220;
    /** Disabled labels on {@link #DISABLED}: 3.1:1 (inactive controls are exempt from AA, still legible). */
    static final int DISABLED_TEXT = 0xFF55627F;

    private HubTheme() {
    }

    /** {@code argb} with its alpha replaced. */
    static int alpha(int argb, int alpha) {
        return (alpha & 0xFF) << 24 | (argb & 0xFFFFFF);
    }

    /** Opaque ARGB for a 24-bit colour. */
    static int opaque(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    /** Black or white text, whichever reads better on {@code rgb}. */
    static int ink(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return (r * 299 + g * 587 + b * 114) / 1000 > 140 ? 0xFF05070F : 0xFFFFFFFF;
    }
}
