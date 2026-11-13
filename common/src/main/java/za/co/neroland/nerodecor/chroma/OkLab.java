package za.co.neroland.nerodecor.chroma;

/**
 * sRGB &lt;-&gt; linear sRGB &lt;-&gt; OKLab conversion (Bjorn Ottosson's reference matrices) and
 * perceptual mixing. All functions are allocation-free except those returning arrays or colours.
 */
public final class OkLab {

    private OkLab() {
    }

    /** sRGB transfer function inverse: encoded 0-1 to linear 0-1. */
    public static double srgbToLinear(double c) {
        return c <= 0.04045 ? c / 12.92 : StrictMath.pow((c + 0.055) / 1.055, 2.4);
    }

    /** sRGB transfer function: linear 0-1 to encoded 0-1. */
    public static double linearToSrgb(double c) {
        return c <= 0.0031308 ? 12.92 * c : 1.055 * StrictMath.pow(c, 1.0 / 2.4) - 0.055;
    }

    /** OKLab {@code {L, a, b}} of a colour (brightness is not part of the colour space). */
    public static double[] fromColor(ChromaColor color) {
        double[] out = new double[3];
        fromLinear(srgbToLinear(color.red() / 255.0), srgbToLinear(color.green() / 255.0),
                srgbToLinear(color.blue() / 255.0), out);
        return out;
    }

    /** Linear sRGB to OKLab, written into {@code out[0..2]}. */
    public static void fromLinear(double r, double g, double b, double[] out) {
        double l = 0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b;
        double m = 0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b;
        double s = 0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b;
        double l1 = StrictMath.cbrt(l);
        double m1 = StrictMath.cbrt(m);
        double s1 = StrictMath.cbrt(s);
        out[0] = 0.2104542553 * l1 + 0.7936177850 * m1 - 0.0040720468 * s1;
        out[1] = 1.9779984951 * l1 - 2.4285922050 * m1 + 0.4505937099 * s1;
        out[2] = 0.0259040371 * l1 + 0.7827717662 * m1 - 0.8086757660 * s1;
    }

    /** OKLab to an sRGB colour (gamut-clipped per channel) with the given brightness. */
    public static ChromaColor toColor(double lightness, double a, double b, int brightness) {
        double l1 = lightness + 0.3963377774 * a + 0.2158037573 * b;
        double m1 = lightness - 0.1055613458 * a - 0.0638541728 * b;
        double s1 = lightness - 0.0894841775 * a - 1.2914855480 * b;
        double l = l1 * l1 * l1;
        double m = m1 * m1 * m1;
        double s = s1 * s1 * s1;
        double r = 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s;
        double g = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s;
        double bl = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s;
        return ChromaColor.of(ChromaColor.to8(linearToSrgb(ChromaColor.clamp01(r))),
                ChromaColor.to8(linearToSrgb(ChromaColor.clamp01(g))),
                ChromaColor.to8(linearToSrgb(ChromaColor.clamp01(bl))), brightness);
    }

    /** Mixes two precomputed OKLab triples; brightness is interpolated linearly and rounded. */
    public static ChromaColor mix(double[] labA, int brightnessA, double[] labB, int brightnessB, double t) {
        double u = ChromaColor.clamp01(t);
        return toColor(labA[0] + (labB[0] - labA[0]) * u, labA[1] + (labB[1] - labA[1]) * u,
                labA[2] + (labB[2] - labA[2]) * u,
                (int) StrictMath.round(brightnessA + (brightnessB - brightnessA) * u));
    }

    /** Two-point gradient in OKLab; {@code t} is clamped to 0-1. */
    public static ChromaColor mix(ChromaColor a, ChromaColor b, double t) {
        if (t <= 0.0) {
            return a;
        }
        if (t >= 1.0) {
            return b;
        }
        return mix(fromColor(a), a.brightness(), fromColor(b), b.brightness(), t);
    }
}
