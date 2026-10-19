import com.solacemod.solace.client.mesh.Figure;
import com.solacemod.solace.client.mesh.LanternShape;
import com.solacemod.solace.client.mesh.Mat4;
import com.solacemod.solace.client.mesh.Mesh;
import com.solacemod.solace.client.mesh.MeshSink;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Draws Solace's 3D models to PNG files without starting Minecraft, so their shapes can be checked quickly.
 * Build and run (from the repository folder):
 *   javac -d build/preview src/main/java/com/solacemod/solace/client/mesh/*.java tools/preview/MeshPreview.java
 *   java -cp build/preview MeshPreview build/preview-images
 * It uses the same lighting idea as Minecraft's mobs (two soft lights from above), so it's close, not exact.
 */
public class MeshPreview {
    static final int W = 480, H = 600;
    static boolean CLOSE = Boolean.getBoolean("close");

    record Tri(float[] p, int color, int mode) { // mode 0 solid, 1 see-through, 2 glowing (added)
    }

    static final class Collector implements MeshSink {
        final List<Tri> tris = new ArrayList<>();
        int mode;
        boolean lit = true;

        @Override
        public void quad(float[] pos, float[] nrm, float[] uv, int[] argb) {
            int c = argb[0];
            if (lit) {
                float nx = nrm[0], ny = nrm[1], nz = nrm[2];
                float l = (float) Math.min(1, 0.4 + 0.6 * (Math.max(0, dot(nx, ny, nz, 0.2f, 1f, -0.7f)) + Math.max(0, dot(nx, ny, nz, -0.2f, 1f, 0.7f))));
                c = shade(c, l);
            }
            tris.add(new Tri(new float[]{pos[0], pos[1], pos[2], pos[3], pos[4], pos[5], pos[6], pos[7], pos[8]}, c, mode));
            tris.add(new Tri(new float[]{pos[0], pos[1], pos[2], pos[6], pos[7], pos[8], pos[9], pos[10], pos[11]}, c, mode));
        }
    }

    static float dot(float ax, float ay, float az, float bx, float by, float bz) {
        float l = (float) Math.sqrt(bx * bx + by * by + bz * bz);
        return (ax * bx + ay * by + az * bz) / l;
    }

    static int shade(int c, float l) {
        int r = (int) (((c >> 16) & 255) * l), g = (int) (((c >> 8) & 255) * l), b = (int) ((c & 255) * l);
        return (c & 0xFF000000) | r << 16 | g << 8 | b;
    }

    public static void main(String[] args) throws Exception {
        File out = new File(args.length > 0 ? args[0] : "preview");
        out.mkdirs();
        Figure fig = new Figure(0.03f);
        fig.pose(0, 0, 20, 0, 0, 0, 1);
        save(figure(fig, 0, false, false), new File(out, "figure_front.png"));
        save(figure(fig, 0.7f, false, false), new File(out, "figure_three_quarter.png"));
        save(figure(fig, 1.57f, false, false), new File(out, "figure_side.png"));
        fig.pose(1.2f, 0.9f, 20, 0, 0, 0, 1);
        save(figure(fig, 0.5f, false, false), new File(out, "figure_walking.png"));
        fig.pose(0, 0, 20, 0, 0, 0, 1);
        save(figure(fig, 0.4f, true, true), new File(out, "figure_negative_night.png"));
        save(figure(fig, 0.4f, false, true), new File(out, "figure_night.png"));
        LanternShape lantern = new LanternShape();
        lantern.update(200, 1, false);
        save(lantern(lantern, 0.3f, false), new File(out, "lantern.png"));
        lantern.update(260, 1, false);
        save(lantern(lantern, 0.3f, true), new File(out, "lantern_night.png"));
        lantern.update(200, 1.8f, true);
        save(lantern(lantern, 0.3f, true), new File(out, "hollow_night.png"));
        System.out.println("Wrote previews to " + out.getAbsolutePath());
    }

    static int negative(int c) {
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        int v = 255 - (int) (0.3 * r + 0.59 * g + 0.11 * b);
        return (c & 0xFF000000) | Math.max(0, v - 6) << 16 | Math.max(0, v - 4) << 8 | Math.min(255, v + 4);
    }

    static Image3 figure(Figure fig, float turn, boolean negative, boolean night) {
        Collector col = new Collector();
        Mat4 m = new Mat4().rotateY(turn);
        fig.eyes.visible = fig.eyeHalo.visible = false;
        col.lit = !negative;
        fig.root.draw(m, col, Figure.ALL, false, 0xFFFFFFFF, negative ? MeshPreview::negative : null, 0);
        if (negative) {
            col.mode = 2;
            fig.root.draw(m, col, Figure.ALL, true, 0xFF404040, c -> 0xFFFFFFFF, 0);
        } else {
            col.lit = false;
            col.mode = 0;
            fig.eyes.visible = true;
            fig.eyes.draw(new Mat4().rotateY(turn).translate(0, 0.92f, 0), col, Figure.ALL, false, 0xFFFFFFFF, null, Figure.ALL);
            col.mode = 2;
            fig.eyeHalo.visible = true;
            fig.eyeHalo.draw(new Mat4().rotateY(turn).translate(0, 0.92f, 0), col, Figure.ALL, false, 0xFF383838, null, Figure.ALL);
        }
        return new Image3(col.tris, CLOSE ? 1.25f : 0.95f, 1.35f, CLOSE ? 1.9f : 3.3f, night);
    }

    static Image3 lantern(LanternShape l, float turn, boolean night) {
        Collector col = new Collector();
        Mat4 m = new Mat4().rotateY(turn).translate(0, 0.45f, 0);
        col.lit = false;
        col.mode = 0;
        l.ferro.emit(m, col, 0xFFFFFFFF, null);
        col.mode = 1;
        l.tendrils.emit(m, col, 0xFFFFFFFF, null);
        l.glass.emit(m, col, 0xFFFFFFFF, null);
        col.mode = 2;
        l.wax.emit(m, col, 0xFFB0B0B0, null);
        l.core.emit(m, col, 0xFFFFFFFF, null);
        l.coreGlow.emit(m, col, 0xFF404040, null);
        l.aura.emit(m, col, 0xFF0A3238, null);
        return new Image3(col.tris, 1.05f, 1.2f, 3.3f, night);
    }

    /** A tiny rasteriser: solid faces with a depth buffer, then see-through faces back to front, then glowing faces added on top. */
    static final class Image3 {
        final BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        final float[] depth = new float[W * H];
        final float[] rgb = new float[W * H * 3];
        final float camY, lookY, dist;

        Image3(List<Tri> tris, float lookY, float camY, float dist, boolean night) {
            this.lookY = lookY;
            this.camY = camY;
            this.dist = dist;
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    int i = y * W + x;
                    depth[i] = Float.MAX_VALUE;
                    float sky = (float) y / H;
                    int bg = night ? (y > H * 0.62 ? 0x1A2414 : 0x0B0D16) : (y > H * 0.62 ? 0x5C8A3A : lerp(0x86ABF5, 0xC4D8FF, sky));
                    rgb[i * 3] = (bg >> 16) & 255;
                    rgb[i * 3 + 1] = (bg >> 8) & 255;
                    rgb[i * 3 + 2] = bg & 255;
                }
            }
            for (Tri t : tris) if (t.mode == 0) raster(t, true);
            List<Tri> see = new ArrayList<>();
            for (Tri t : tris) if (t.mode == 1) see.add(t);
            see.sort((a, b) -> Float.compare(camDist(b), camDist(a)));
            for (Tri t : see) raster(t, false);
            for (Tri t : tris) if (t.mode == 2) raster(t, false);
            for (int i = 0; i < W * H; i++) {
                int r = (int) Math.min(255, rgb[i * 3]), g = (int) Math.min(255, rgb[i * 3 + 1]), b = (int) Math.min(255, rgb[i * 3 + 2]);
                img.setRGB(i % W, i / W, r << 16 | g << 8 | b);
            }
        }

        static int lerp(int a, int b, float f) {
            int r = (int) (((a >> 16) & 255) * (1 - f) + ((b >> 16) & 255) * f);
            int g = (int) (((a >> 8) & 255) * (1 - f) + ((b >> 8) & 255) * f);
            int bb = (int) ((a & 255) * (1 - f) + (b & 255) * f);
            return r << 16 | g << 8 | bb;
        }

        float camDist(Tri t) {
            float cz = (t.p[2] + t.p[5] + t.p[8]) / 3;
            return dist - cz;
        }

        void raster(Tri t, boolean write) {
            float[] sx = new float[3], sy = new float[3], sz = new float[3];
            for (int k = 0; k < 3; k++) {
                float x = t.p[k * 3], y = t.p[k * 3 + 1] - camY, z = dist - t.p[k * 3 + 2];
                // Camera looks toward -z, slightly down at lookY.
                float pitch = (float) Math.atan2(camY - lookY, dist);
                float yy = (float) (y * Math.cos(pitch) + z * Math.sin(pitch) * 0) + (camY - lookY) * 0;
                float f = 520 / z;
                sx[k] = W / 2f + x * f;
                sy[k] = H / 2f - (y + (camY - lookY)) * f + (camY - lookY) * 0;
                sz[k] = z;
                sy[k] = H / 2f - (t.p[k * 3 + 1] - lookY) * f;
            }
            int minX = (int) Math.max(0, Math.floor(Math.min(sx[0], Math.min(sx[1], sx[2]))));
            int maxX = (int) Math.min(W - 1, Math.ceil(Math.max(sx[0], Math.max(sx[1], sx[2]))));
            int minY = (int) Math.max(0, Math.floor(Math.min(sy[0], Math.min(sy[1], sy[2]))));
            int maxY = (int) Math.min(H - 1, Math.ceil(Math.max(sy[0], Math.max(sy[1], sy[2]))));
            float area = (sx[1] - sx[0]) * (sy[2] - sy[0]) - (sx[2] - sx[0]) * (sy[1] - sy[0]);
            if (Math.abs(area) < 1e-6) return;
            int c = t.color;
            float a = ((c >>> 24) & 255) / 255f, r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
            for (int y = minY; y <= maxY; y++) {
                for (int x = minX; x <= maxX; x++) {
                    float px = x + 0.5f, py = y + 0.5f;
                    float w0 = ((sx[1] - px) * (sy[2] - py) - (sx[2] - px) * (sy[1] - py)) / area;
                    float w1 = ((sx[2] - px) * (sy[0] - py) - (sx[0] - px) * (sy[2] - py)) / area;
                    float w2 = 1 - w0 - w1;
                    if (w0 < 0 || w1 < 0 || w2 < 0) continue;
                    float z = w0 * sz[0] + w1 * sz[1] + w2 * sz[2];
                    int i = y * W + x;
                    if (z >= depth[i]) continue;
                    if (t.mode == 0) {
                        if (write) depth[i] = z;
                        rgb[i * 3] = r;
                        rgb[i * 3 + 1] = g;
                        rgb[i * 3 + 2] = b;
                    } else if (t.mode == 1) {
                        rgb[i * 3] = rgb[i * 3] * (1 - a) + r * a;
                        rgb[i * 3 + 1] = rgb[i * 3 + 1] * (1 - a) + g * a;
                        rgb[i * 3 + 2] = rgb[i * 3 + 2] * (1 - a) + b * a;
                    } else {
                        rgb[i * 3] += r * 0.6f;
                        rgb[i * 3 + 1] += g * 0.6f;
                        rgb[i * 3 + 2] += b * 0.6f;
                    }
                }
            }
        }
    }

    static void save(Image3 im, File f) throws Exception {
        ImageIO.write(im.img, "png", f);
    }
}
