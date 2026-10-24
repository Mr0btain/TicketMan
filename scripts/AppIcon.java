import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Draws the app icon (a blue ticket on graphite, the Bench theme's colors) and writes every file that uses it:
 * the desktop installers' .ico/.icns/.png, the desktop window icon, the web favicon, Android's launcher icons
 * (adaptive vectors plus PNGs for Android 7) and iOS's app icon.
 *
 * Run from the repository root after changing it: java scripts/AppIcon.java
 */
public class AppIcon {
    // Bench theme (TmPalettes.Bench dark)
    static final Color BG_TOP = new Color(0x252B33);
    static final Color BG_BOTTOM = new Color(0x14171B);
    static final Color GLOW = new Color(0x7C, 0xC4, 0xFF, 0x38);
    static final Color EDGE = new Color(255, 255, 255, 0x18);
    static final Color TICKET_TOP = new Color(0x9AD2FF);
    static final Color TICKET_BOTTOM = new Color(0x6AB6F2);
    static final Color MARK = new Color(0x0B2236);

    // The ticket, in Android's 108 unit adaptive icon space, centered on (54, 54) and tilted by ROTATION degrees
    static final double LEFT = 26, TOP = 36, RIGHT = 82, BOTTOM = 72, CORNER = 4;
    static final double PERF_X = 65, NOTCH = 5;
    static final double ROTATION = -12;
    /** Dashes down the perforation: y start, length */
    static final double[][] DASHES = {{43, 3}, {49, 3}, {55, 3}, {61, 3}};
    /** Text lines on the main part: x, y, width, height */
    static final double[][] LINES = {{32, 45, 22, 4}, {32, 52, 15, 4}, {32, 59, 18, 4}};

    enum Shape { ROUNDED, SQUARE, CIRCLE }

    public static void main(String[] args) throws IOException {
        Path root = Path.of(args.length > 0 ? args[0] : ".");

        Path icons = root.resolve("composeApp/icons");
        Files.createDirectories(icons);
        writeIco(icons.resolve("ticketman.ico"), List.of(16, 20, 24, 32, 40, 48, 64, 128, 256));
        writeIcns(icons.resolve("ticketman.icns"));
        png(icons.resolve("ticketman.png"), draw(512, Shape.ROUNDED));

        png(root.resolve("composeApp/src/commonMain/composeResources/drawable/app_icon.png"), draw(256, Shape.ROUNDED));
        png(root.resolve("composeApp/src/wasmJsMain/resources/favicon.png"), draw(64, Shape.ROUNDED));
        png(root.resolve("composeApp/src/wasmJsMain/resources/apple-touch-icon.png"), draw(180, Shape.SQUARE));
        png(root.resolve("iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/app-icon-1024.png"), draw(1024, Shape.SQUARE));

        Path res = root.resolve("androidApp/src/main/res");
        String[][] densities = {{"mdpi", "48"}, {"hdpi", "72"}, {"xhdpi", "96"}, {"xxhdpi", "144"}, {"xxxhdpi", "192"}};
        for (String[] d : densities) {
            int size = Integer.parseInt(d[1]);
            png(res.resolve("mipmap-" + d[0] + "/ic_launcher.png"), draw(size, Shape.ROUNDED));
            png(res.resolve("mipmap-" + d[0] + "/ic_launcher_round.png"), draw(size, Shape.CIRCLE));
        }
        Files.writeString(res.resolve("drawable/ic_launcher_background.xml"), backgroundXml());
        Files.writeString(res.resolve("drawable-v24/ic_launcher_foreground.xml"), foregroundXml());
    }

    /** The icon at [size] pixels. The ticket is scaled up from its adaptive icon size, which leaves room for masks */
    static BufferedImage draw(int size, Shape shape) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        // Desktop icons sit a little in from the edge, as the system's own do
        double inset = shape == Shape.ROUNDED ? size * 0.06 : 0;
        double body = size - inset * 2;
        java.awt.Shape outline = switch (shape) {
            case ROUNDED -> new RoundRectangle2D.Double(inset, inset, body, body, body * 0.44, body * 0.44);
            case SQUARE -> new Rectangle2D.Double(0, 0, size, size);
            case CIRCLE -> new Ellipse2D.Double(0, 0, size, size);
        };

        g.setPaint(new GradientPaint(0, (float) inset, BG_TOP, 0, (float) (inset + body), BG_BOTTOM));
        g.fill(outline);
        g.setClip(outline);
        g.setPaint(new RadialGradientPaint(
            new Point2D.Double(size / 2.0, inset), (float) (body * 0.75),
            new float[]{0f, 1f}, new Color[]{GLOW, new Color(GLOW.getRed(), GLOW.getGreen(), GLOW.getBlue(), 0)}
        ));
        g.fill(outline);
        g.setClip(null);
        if (shape != Shape.SQUARE && size >= 48) {
            g.setColor(EDGE);
            g.setStroke(new BasicStroke((float) Math.max(1, size / 256.0)));
            g.draw(outline);
        }

        // The ticket fills more of a small icon, where detail is lost anyway
        double scale = size / 108.0 * (size <= 32 ? 1.55 : 1.4);
        AffineTransform t = new AffineTransform();
        t.translate(size / 2.0, size / 2.0);
        t.scale(scale, scale);
        t.rotate(Math.toRadians(ROTATION));
        t.translate(-54, -54);

        Area ticket = ticketArea();
        g.setPaint(new GradientPaint(0, (float) (size * 0.3), TICKET_TOP, 0, (float) (size * 0.7), TICKET_BOTTOM));
        g.fill(t.createTransformedShape(ticket));

        if (size >= 24) {
            g.setColor(MARK);
            for (double[] dash : DASHES) {
                g.fill(t.createTransformedShape(new RoundRectangle2D.Double(PERF_X - 1, dash[0], 2, dash[1], 1, 1)));
            }
            g.setColor(new Color(MARK.getRed(), MARK.getGreen(), MARK.getBlue(), 0xB0));
            for (double[] line : LINES) {
                g.fill(t.createTransformedShape(new RoundRectangle2D.Double(line[0], line[1], line[2], line[3], line[3], line[3])));
            }
        }
        g.dispose();
        return image;
    }

    static Area ticketArea() {
        Area ticket = new Area(new RoundRectangle2D.Double(LEFT, TOP, RIGHT - LEFT, BOTTOM - TOP, CORNER * 2, CORNER * 2));
        ticket.subtract(new Area(new Ellipse2D.Double(PERF_X - NOTCH, TOP - NOTCH, NOTCH * 2, NOTCH * 2)));
        ticket.subtract(new Area(new Ellipse2D.Double(PERF_X - NOTCH, BOTTOM - NOTCH, NOTCH * 2, NOTCH * 2)));
        return ticket;
    }

    static String backgroundXml() {
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <!-- Made by scripts/AppIcon.java -->
            <vector xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:aapt="http://schemas.android.com/aapt"
                android:width="108dp"
                android:height="108dp"
                android:viewportWidth="108"
                android:viewportHeight="108">
                <path android:pathData="M0,0h108v108h-108z">
                    <aapt:attr name="android:fillColor">
                        <gradient
                            android:type="linear"
                            android:startX="54" android:startY="0"
                            android:endX="54" android:endY="108"
                            android:startColor="%s"
                            android:endColor="%s" />
                    </aapt:attr>
                </path>
                <path android:pathData="M0,0h108v108h-108z">
                    <aapt:attr name="android:fillColor">
                        <gradient
                            android:type="radial"
                            android:centerX="54" android:centerY="0"
                            android:gradientRadius="80"
                            android:startColor="%s"
                            android:endColor="#007CC4FF" />
                    </aapt:attr>
                </path>
            </vector>
            """.formatted(hex(BG_TOP), hex(BG_BOTTOM), hex(GLOW));
    }

    static String foregroundXml() {
        // Notches dip into the ticket: a sweep of 0 runs counterclockwise on screen
        String ticket = String.format(Locale.ROOT,
            "M%s,%s H%s A%s,%s 0 0,0 %s,%s H%s A%s,%s 0 0,1 %s,%s V%s A%s,%s 0 0,1 %s,%s " +
                "H%s A%s,%s 0 0,0 %s,%s H%s A%s,%s 0 0,1 %s,%s V%s A%s,%s 0 0,1 %s,%s Z",
            n(LEFT + CORNER), n(TOP), n(PERF_X - NOTCH), n(NOTCH), n(NOTCH), n(PERF_X + NOTCH), n(TOP),
            n(RIGHT - CORNER), n(CORNER), n(CORNER), n(RIGHT), n(TOP + CORNER),
            n(BOTTOM - CORNER), n(CORNER), n(CORNER), n(RIGHT - CORNER), n(BOTTOM),
            n(PERF_X + NOTCH), n(NOTCH), n(NOTCH), n(PERF_X - NOTCH), n(BOTTOM),
            n(LEFT + CORNER), n(CORNER), n(CORNER), n(LEFT), n(BOTTOM - CORNER),
            n(TOP + CORNER), n(CORNER), n(CORNER), n(LEFT + CORNER), n(TOP));
        StringBuilder dashes = new StringBuilder();
        for (double[] dash : DASHES) {
            dashes.append(String.format(Locale.ROOT, "M%s,%sh2v%sh-2z ", n(PERF_X - 1), n(dash[0]), n(dash[1])));
        }
        StringBuilder lines = new StringBuilder();
        for (double[] line : LINES) {
            double r = line[3] / 2;
            lines.append(String.format(Locale.ROOT, "M%s,%sh%sa%s,%s 0 0,1 0,%sh-%sa%s,%s 0 0,1 0,-%sz ",
                n(line[0] + r), n(line[1]), n(line[2] - line[3]), n(r), n(r), n(line[3]),
                n(line[2] - line[3]), n(r), n(r), n(line[3])));
        }
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <!-- Made by scripts/AppIcon.java -->
            <vector xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:aapt="http://schemas.android.com/aapt"
                android:width="108dp"
                android:height="108dp"
                android:viewportWidth="108"
                android:viewportHeight="108">
                <group android:pivotX="54" android:pivotY="54" android:rotation="%s">
                    <path android:pathData="%s">
                        <aapt:attr name="android:fillColor">
                            <gradient
                                android:type="linear"
                                android:startX="54" android:startY="36"
                                android:endX="54" android:endY="72"
                                android:startColor="%s"
                                android:endColor="%s" />
                        </aapt:attr>
                    </path>
                    <path android:fillColor="%s" android:pathData="%s" />
                    <path android:fillColor="%s" android:fillAlpha="0.69" android:pathData="%s" />
                </group>
            </vector>
            """.formatted(n(ROTATION), ticket, hex(TICKET_TOP), hex(TICKET_BOTTOM),
                hex(MARK), dashes.toString().trim(), hex(MARK), lines.toString().trim());
    }

    /** Windows icon: one PNG per size, which Windows has read since Vista */
    static void writeIco(Path file, List<Integer> sizes) throws IOException {
        byte[][] images = new byte[sizes.size()][];
        for (int i = 0; i < sizes.size(); i++) images[i] = pngBytes(draw(sizes.get(i), Shape.ROUNDED));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeShortLE(out, 0);
        writeShortLE(out, 1);
        writeShortLE(out, sizes.size());
        int offset = 6 + 16 * sizes.size();
        for (int i = 0; i < sizes.size(); i++) {
            int size = sizes.get(i);
            out.write(size >= 256 ? 0 : size);
            out.write(size >= 256 ? 0 : size);
            out.write(0);
            out.write(0);
            writeShortLE(out, 1);
            writeShortLE(out, 32);
            writeIntLE(out, images[i].length);
            writeIntLE(out, offset);
            offset += images[i].length;
        }
        for (byte[] image : images) out.write(image);
        Files.write(file, out.toByteArray());
    }

    /** macOS icon: PNG entries for 16 to 1024 pixels */
    static void writeIcns(Path file) throws IOException {
        Object[][] entries = {{"icp4", 16}, {"icp5", 32}, {"icp6", 64}, {"ic07", 128}, {"ic08", 256}, {"ic09", 512}, {"ic10", 1024}};
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(body);
        for (Object[] entry : entries) {
            byte[] image = pngBytes(draw((Integer) entry[1], Shape.ROUNDED));
            data.writeBytes((String) entry[0]);
            data.writeInt(image.length + 8);
            data.write(image);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream header = new DataOutputStream(out);
        header.writeBytes("icns");
        header.writeInt(body.size() + 8);
        body.writeTo(out);
        Files.write(file, out.toByteArray());
    }

    static void png(Path file, BufferedImage image) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, pngBytes(image));
    }

    static byte[] pngBytes(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    static void writeShortLE(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
    }

    static void writeIntLE(ByteArrayOutputStream out, int value) {
        for (int i = 0; i < 4; i++) out.write((value >> (8 * i)) & 0xFF);
    }

    static String hex(Color c) {
        return String.format("#%02X%02X%02X%02X", c.getAlpha(), c.getRed(), c.getGreen(), c.getBlue());
    }

    static String n(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
