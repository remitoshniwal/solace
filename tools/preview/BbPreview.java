import com.solacemod.solace.client.model.BbModel;
import com.solacemod.solace.client.model.Mat4;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Draws Solace's Blockbench model through the mod's own loader (client/model/BbModel) to PNG files, without starting
 * Minecraft, so the loader can be checked against Blockbench quickly. Textured, with cut-out see-through pixels and
 * simple mob-style lighting. Build and run (from the repository folder; gson.jar from Maven or any Minecraft install):
 *   javac -cp gson.jar -d build/preview src/main/java/com/solacemod/solace/client/model/*.java tools/preview/BbPreview.java
 *   java -cp build/preview:gson.jar BbPreview build/preview-images
 */
public class BbPreview {
    static final int W = 420, H = 720;
    static final String TEX = "src/main/resources/assets/solace/textures/entity/";

    record Tri(float[] p, float[] uv, float light) {
    }

    public static void main(String[] args) throws Exception {
        File out = new File(args.length > 0 ? args[0] : "build/preview-images");
        out.mkdirs();
        BbModel model = BbModel.load(new FileInputStream("src/main/resources/assets/solace/models/entity/solace.bbmodel"));
        BufferedImage normal = ImageIO.read(new File(TEX + "solace_shade.png"));
        BufferedImage negative = ImageIO.read(new File(TEX + "solace_shade_negative.png"));
        for (int turn : new int[]{20, 100, 200}) {
            ImageIO.write(render(model, turn, 0, normal, negative, 0x8A8D96), "png", new File(out, "normal_" + turn + ".png"));
            ImageIO.write(render(model, turn, BbModel.ALL, normal, negative, 0x1B1C22), "png", new File(out, "negative_" + turn + ".png"));
        }
        ImageIO.write(render(model, 20, BbModel.RIGHT_ARM, normal, negative, 0x8A8D96), "png", new File(out, "flash_right_arm.png"));
        System.out.println("Wrote previews to " + out.getAbsolutePath());
    }

    /** turnDeg 0 looks straight at its front, like the Blockbench previews. */
    static BufferedImage render(BbModel model, float turnDeg, int mask, BufferedImage tex, BufferedImage negTex, int bg) {
        Mat4 m = new Mat4().rotateY((float) Math.toRadians(-turnDeg)).mul(BbModel.base());
        float[] depth = new float[W * H];
        java.util.Arrays.fill(depth, Float.MAX_VALUE);
        int[] px = new int[W * H];
        java.util.Arrays.fill(px, bg);
        for (int pass = 0; pass < 2; pass++) {
            boolean neg = pass == 1;
            if (neg ? mask == 0 : mask == BbModel.ALL) continue;
            List<Tri> tris = new ArrayList<>();
            model.draw(m, (pos, nrm, uv, argb) -> {
                float l = neg ? 1 : Math.max(0.45F, Math.min(1, 0.45F + 0.55F * Math.max(0, nrm[1] * 0.6F + Math.abs(nrm[2]) * 0.6F)));
                tris.add(new Tri(new float[]{pos[0], pos[1], pos[2], pos[3], pos[4], pos[5], pos[6], pos[7], pos[8]}, new float[]{uv[0], uv[1], uv[2], uv[3], uv[4], uv[5]}, l));
                tris.add(new Tri(new float[]{pos[0], pos[1], pos[2], pos[6], pos[7], pos[8], pos[9], pos[10], pos[11]}, new float[]{uv[0], uv[1], uv[4], uv[5], uv[6], uv[7]}, l));
            }, mask, neg, 20, 0, 0xFFFFFFFF);
            for (Tri tr : tris) raster(tr, neg ? negTex : tex, depth, px);
        }
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, W, H, px, 0, W);
        return img;
    }

    /** The camera stands on +z (in front of the entity) looking back at it. */
    static void raster(Tri t, BufferedImage tex, float[] depth, int[] px) {
        float dist = 4.2F, lookY = 1.25F, f = 640;
        float[] sx = new float[3], sy = new float[3], sz = new float[3];
        for (int k = 0; k < 3; k++) {
            float z = dist - t.p[k * 3 + 2];
            sx[k] = W / 2F + t.p[k * 3] * f / z; // looking towards -z, +x is on the right
            sy[k] = H / 2F - (t.p[k * 3 + 1] - lookY) * f / z;
            sz[k] = z;
        }
        float area = (sx[1] - sx[0]) * (sy[2] - sy[0]) - (sx[2] - sx[0]) * (sy[1] - sy[0]);
        if (Math.abs(area) < 1e-6) return;
        int minX = (int) Math.max(0, Math.floor(Math.min(sx[0], Math.min(sx[1], sx[2]))));
        int maxX = (int) Math.min(W - 1, Math.ceil(Math.max(sx[0], Math.max(sx[1], sx[2]))));
        int minY = (int) Math.max(0, Math.floor(Math.min(sy[0], Math.min(sy[1], sy[2]))));
        int maxY = (int) Math.min(H - 1, Math.ceil(Math.max(sy[0], Math.max(sy[1], sy[2]))));
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                float qx = x + 0.5F, qy = y + 0.5F;
                float w0 = ((sx[1] - qx) * (sy[2] - qy) - (sx[2] - qx) * (sy[1] - qy)) / area;
                float w1 = ((sx[2] - qx) * (sy[0] - qy) - (sx[0] - qx) * (sy[2] - qy)) / area;
                float w2 = 1 - w0 - w1;
                if (w0 < 0 || w1 < 0 || w2 < 0) continue;
                float z = w0 * sz[0] + w1 * sz[1] + w2 * sz[2];
                int i = y * W + x;
                if (z >= depth[i]) continue;
                float u = w0 * t.uv[0] + w1 * t.uv[2] + w2 * t.uv[4], v = w0 * t.uv[1] + w1 * t.uv[3] + w2 * t.uv[5];
                int tx = Math.max(0, Math.min(tex.getWidth() - 1, (int) (u * tex.getWidth())));
                int ty = Math.max(0, Math.min(tex.getHeight() - 1, (int) (v * tex.getHeight())));
                int c = tex.getRGB(tx, ty);
                if ((c >>> 24) < 26) continue; // cut-out, like Minecraft's entity cutout
                depth[i] = z;
                px[i] = (int) (((c >> 16) & 255) * t.light) << 16 | (int) (((c >> 8) & 255) * t.light) << 8 | (int) ((c & 255) * t.light);
            }
        }
    }
}
