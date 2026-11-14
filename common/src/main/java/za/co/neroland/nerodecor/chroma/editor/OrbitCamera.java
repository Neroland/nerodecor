package za.co.neroland.nerodecor.chroma.editor;

/**
 * A perspective orbit camera around a target point, in hub-relative block units (a block at offset {@code (x,y,z)}
 * fills {@code [x,x+1) x [y,y+1) x [z,z+1)}). Projects into a viewport of {@code width x height} pixels and turns a
 * pixel back into a ray for picking. Mutable; client thread only.
 */
public final class OrbitCamera {

    public static final double MIN_PITCH = -1.5;
    public static final double MAX_PITCH = 1.5;
    public static final double MIN_DISTANCE = 2.0;
    public static final double MAX_DISTANCE = 256.0;
    private static final double NEAR = 0.05;

    private double targetX;
    private double targetY;
    private double targetZ;
    private double yaw = Math.toRadians(35.0);
    private double pitch = Math.toRadians(30.0);
    private double distance = 24.0;
    private double fovY = Math.toRadians(50.0);
    private int width = 1;
    private int height = 1;

    // Derived basis, refreshed by update().
    private double ex;
    private double ey;
    private double ez;
    private final double[] f = new double[3];
    private final double[] r = new double[3];
    private final double[] u = new double[3];
    private double focal;

    public OrbitCamera() {
        update();
    }

    /** A projected point: pixel position and view depth (distance along the view axis). */
    public record Projected(double x, double y, double depth) {
    }

    /** A ray from the eye. */
    public record Ray(double ox, double oy, double oz, double dx, double dy, double dz) {
    }

    // --- controls ------------------------------------------------------------------------------------------------

    public void setViewport(int w, int h) {
        this.width = Math.max(1, w);
        this.height = Math.max(1, h);
        update();
    }

    public void setTarget(double x, double y, double z) {
        this.targetX = x;
        this.targetY = y;
        this.targetZ = z;
        update();
    }

    /** Frames a box: centres on it and backs off until it fits the view. */
    public void frame(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        setTarget((minX + maxX) / 2.0, (minY + maxY) / 2.0, (minZ + maxZ) / 2.0);
        double dx = maxX - minX;
        double dy = maxY - minY;
        double dz = maxZ - minZ;
        double radius = Math.sqrt(dx * dx + dy * dy + dz * dz) / 2.0;
        distance = clamp(radius / Math.sin(fovY / 2.0) * 1.05, MIN_DISTANCE, MAX_DISTANCE);
        update();
    }

    /** Rotates by a mouse drag (radians per pixel are fixed). */
    public void orbit(double dxPixels, double dyPixels) {
        yaw -= dxPixels * 0.012;
        pitch = clamp(pitch + dyPixels * 0.012, MIN_PITCH, MAX_PITCH);
        update();
    }

    /** Slides the target in the view plane by a mouse drag. */
    public void pan(double dxPixels, double dyPixels) {
        double scale = distance / focal;
        targetX += (-r[0] * dxPixels + u[0] * dyPixels) * scale;
        targetY += (-r[1] * dxPixels + u[1] * dyPixels) * scale;
        targetZ += (-r[2] * dxPixels + u[2] * dyPixels) * scale;
        update();
    }

    /** Zooms by wheel notches (positive = closer). */
    public void zoom(double notches) {
        distance = clamp(distance * Math.pow(0.88, notches), MIN_DISTANCE, MAX_DISTANCE);
        update();
    }

    public double yaw() {
        return yaw;
    }

    public double pitch() {
        return pitch;
    }

    public double distance() {
        return distance;
    }

    public double eyeX() {
        return ex;
    }

    public double eyeY() {
        return ey;
    }

    public double eyeZ() {
        return ez;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    // --- projection ----------------------------------------------------------------------------------------------

    private void update() {
        double cp = Math.cos(pitch);
        ex = targetX + distance * cp * Math.sin(yaw);
        ey = targetY + distance * Math.sin(pitch);
        ez = targetZ + distance * cp * Math.cos(yaw);
        f[0] = targetX - ex;
        f[1] = targetY - ey;
        f[2] = targetZ - ez;
        normalise(f);
        // right = f x up(0,1,0)
        r[0] = -f[2];
        r[1] = 0.0;
        r[2] = f[0];
        normalise(r);
        // up = r x f
        u[0] = r[1] * f[2] - r[2] * f[1];
        u[1] = r[2] * f[0] - r[0] * f[2];
        u[2] = r[0] * f[1] - r[1] * f[0];
        focal = (height / 2.0) / Math.tan(fovY / 2.0);
    }

    /** View depth of a point (negative or tiny = behind the eye). */
    public double depth(double x, double y, double z) {
        return (x - ex) * f[0] + (y - ey) * f[1] + (z - ez) * f[2];
    }

    /** Projects a point, or {@code null} when it is behind the near plane. */
    public Projected project(double x, double y, double z) {
        double dx = x - ex;
        double dy = y - ey;
        double dz = z - ez;
        double zc = dx * f[0] + dy * f[1] + dz * f[2];
        if (zc < NEAR) {
            return null;
        }
        double xc = dx * r[0] + dy * r[1] + dz * r[2];
        double yc = dx * u[0] + dy * u[1] + dz * u[2];
        return new Projected(width / 2.0 + xc / zc * focal, height / 2.0 - yc / zc * focal, zc);
    }

    /** Projects into {@code out = {x, y, depth}}; false when behind the near plane. Allocation-free. */
    public boolean project(double x, double y, double z, double[] out) {
        double dx = x - ex;
        double dy = y - ey;
        double dz = z - ez;
        double zc = dx * f[0] + dy * f[1] + dz * f[2];
        if (zc < NEAR) {
            return false;
        }
        double xc = dx * r[0] + dy * r[1] + dz * r[2];
        double yc = dx * u[0] + dy * u[1] + dz * u[2];
        out[0] = width / 2.0 + xc / zc * focal;
        out[1] = height / 2.0 - yc / zc * focal;
        out[2] = zc;
        return true;
    }

    /** Pixel size of a one-block span at view depth {@code depth}. */
    public double pixelsPerBlock(double depth) {
        return depth <= 0.0 ? 0.0 : focal / depth;
    }

    /** The ray through pixel {@code (px, py)}. */
    public Ray ray(double px, double py) {
        double sx = (px - width / 2.0) / focal;
        double sy = (height / 2.0 - py) / focal;
        double[] d = {f[0] + r[0] * sx + u[0] * sy, f[1] + r[1] * sx + u[1] * sy, f[2] + r[2] * sx + u[2] * sy};
        normalise(d);
        return new Ray(ex, ey, ez, d[0], d[1], d[2]);
    }

    private static void normalise(double[] v) {
        double len = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (len > 1e-12) {
            v[0] /= len;
            v[1] /= len;
            v[2] /= len;
        }
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
