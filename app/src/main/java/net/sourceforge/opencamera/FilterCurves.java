package net.sourceforge.opencamera;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Color;
import android.graphics.RenderEffect;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.os.Build;
import android.util.Log;
import android.view.View;

/** "Bright & clean" colour filter: three per-channel tone curves measured from a
 *  before/after pair edited in Google Photos. Brightens the
 *  midtones (about +0.12 at mid-grey), leaves shadows almost untouched, rolls highlights
 *  off smoothly and cools the upper tones slightly.
 *  Applied to saved photos (CPU, in PostProcessing) and to the live view (GPU shader on the
 *  preview TextureView, Android 13+), using the same curves.
 */
public class FilterCurves {
    private static final String TAG = "FilterCurves";

    /** Whether the filter is enabled; updated from settings by MainActivity. */
    public static volatile boolean enabled;

    // output level (0-255) for each input level (0-255); measured per-channel curves, smoothed
    private static final int [] CURVE_R = {
            0, 1, 3, 4, 5, 6, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17,
            18, 19, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 32, 33,
            34, 35, 36, 37, 38, 39, 40, 41, 42, 44, 45, 46, 47, 48, 49, 50,
            51, 52, 53, 54, 56, 57, 58, 59, 61, 62, 63, 65, 66, 68, 69, 70,
            72, 73, 75, 76, 77, 79, 80, 81, 83, 84, 85, 87, 88, 89, 91, 92,
            93, 95, 96, 97, 98, 100, 101, 102, 104, 105, 106, 108, 109, 110, 112, 113,
            115, 116, 117, 119, 120, 121, 123, 124, 125, 127, 128, 129, 131, 132, 133, 134,
            136, 137, 138, 140, 141, 142, 143, 145, 146, 147, 149, 150, 151, 153, 154, 155,
            156, 158, 159, 160, 161, 162, 163, 164, 165, 167, 168, 169, 170, 171, 172, 173,
            174, 175, 176, 177, 178, 179, 180, 181, 182, 183, 184, 185, 186, 186, 187, 188,
            189, 190, 191, 192, 193, 193, 194, 195, 196, 197, 198, 199, 200, 201, 202, 203,
            204, 204, 205, 206, 207, 208, 209, 209, 210, 211, 212, 212, 213, 214, 214, 215,
            216, 216, 217, 217, 218, 218, 218, 219, 219, 220, 220, 220, 221, 221, 222, 222,
            223, 223, 223, 224, 224, 225, 225, 226, 227, 227, 228, 228, 229, 229, 230, 231,
            231, 232, 232, 233, 234, 234, 235, 236, 236, 237, 238, 238, 239, 239, 240, 241,
            241, 242, 242, 243, 244, 244, 245, 246, 247, 248, 249, 250, 251, 253, 254, 255
    };
    private static final int [] CURVE_G = {
            0, 2, 3, 4, 6, 7, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18,
            19, 20, 21, 22, 23, 24, 25, 26, 27, 29, 30, 31, 32, 34, 35, 36,
            37, 39, 40, 41, 42, 44, 45, 46, 47, 48, 49, 50, 51, 53, 54, 55,
            56, 57, 58, 60, 61, 62, 63, 65, 66, 67, 68, 70, 71, 72, 74, 75,
            76, 78, 79, 80, 82, 83, 84, 86, 87, 88, 90, 91, 92, 93, 95, 96,
            97, 99, 100, 101, 103, 104, 105, 107, 108, 109, 111, 112, 113, 115, 116, 118,
            119, 120, 122, 123, 124, 126, 127, 128, 129, 131, 132, 133, 134, 136, 137, 138,
            139, 141, 142, 143, 144, 145, 147, 148, 149, 150, 151, 152, 154, 155, 156, 157,
            158, 159, 160, 161, 162, 163, 164, 165, 166, 167, 168, 169, 170, 171, 172, 173,
            174, 175, 176, 177, 178, 178, 179, 180, 181, 182, 182, 183, 184, 185, 186, 186,
            187, 188, 189, 190, 191, 191, 192, 193, 194, 195, 196, 196, 197, 198, 199, 200,
            200, 201, 202, 203, 203, 204, 205, 206, 206, 207, 208, 209, 209, 210, 211, 211,
            212, 213, 213, 214, 215, 215, 216, 217, 217, 218, 219, 219, 220, 220, 221, 221,
            222, 223, 223, 224, 224, 225, 226, 226, 227, 227, 228, 229, 230, 230, 231, 232,
            232, 233, 234, 234, 235, 236, 236, 237, 238, 238, 239, 239, 240, 240, 241, 241,
            242, 242, 243, 243, 244, 245, 245, 246, 247, 248, 249, 250, 251, 253, 254, 255
    };
    private static final int [] CURVE_B = {
            0, 1, 3, 4, 5, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17,
            18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 30, 31, 32, 33, 34,
            35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 46, 47, 48, 49, 50, 51,
            52, 54, 55, 56, 57, 58, 60, 61, 62, 63, 65, 66, 67, 68, 69, 71,
            72, 73, 74, 76, 77, 78, 79, 81, 82, 84, 85, 86, 88, 89, 91, 92,
            94, 95, 97, 98, 99, 101, 102, 104, 105, 107, 108, 110, 111, 113, 114, 115,
            117, 118, 120, 121, 122, 124, 125, 126, 128, 129, 130, 131, 133, 134, 135, 137,
            138, 140, 141, 142, 144, 145, 146, 148, 149, 150, 152, 153, 154, 155, 157, 158,
            159, 160, 161, 163, 164, 165, 166, 167, 169, 170, 171, 172, 173, 174, 175, 176,
            177, 178, 179, 180, 181, 182, 183, 184, 185, 186, 187, 187, 188, 189, 190, 191,
            192, 192, 193, 194, 195, 196, 197, 197, 198, 199, 200, 201, 202, 203, 204, 205,
            206, 206, 207, 208, 209, 210, 211, 212, 212, 213, 214, 215, 216, 216, 217, 218,
            219, 219, 220, 221, 222, 222, 223, 224, 225, 225, 226, 227, 228, 228, 229, 230,
            230, 231, 232, 233, 233, 234, 235, 235, 236, 236, 237, 238, 238, 239, 239, 240,
            240, 241, 241, 241, 242, 242, 243, 243, 243, 244, 244, 244, 245, 245, 246, 246,
            246, 247, 247, 248, 248, 249, 249, 250, 250, 251, 251, 252, 253, 253, 254, 255
    };

    /** Applies the curves to a mutable bitmap in place, a strip of rows at a time to limit memory. */
    public static void applyToBitmap(Bitmap bitmap) {
        long time_s = System.currentTimeMillis();
        final int width = bitmap.getWidth();
        final int height = bitmap.getHeight();
        final int strip = Math.max(1, Math.min(height, (1 << 20) / Math.max(1, width))); // ~4MB per strip
        int [] pixels = new int[width * strip];
        for(int y=0;y<height;y+=strip) {
            int rows = Math.min(strip, height - y);
            bitmap.getPixels(pixels, 0, width, 0, y, width, rows);
            int n = width * rows;
            for(int i=0;i<n;i++) {
                int p = pixels[i];
                pixels[i] = (p & 0xff000000) |
                        (CURVE_R[(p >> 16) & 0xff] << 16) |
                        (CURVE_G[(p >> 8) & 0xff] << 8) |
                        CURVE_B[p & 0xff];
            }
            bitmap.setPixels(pixels, 0, width, 0, y, width, rows);
        }
        if( MyDebug.LOG )
            Log.d(TAG, "applyToBitmap took " + (System.currentTimeMillis() - time_s) + "ms");
    }

    private static final String SHADER_SRC =
            "uniform shader content;\n" +
            "uniform shader curve;\n" +
            "half4 main(float2 p) {\n" +
            "    half4 c = content.eval(p);\n" +
            "    float3 v = clamp(float3(c.rgb), 0.0, 1.0) * 255.0 + 0.5;\n" +
            "    return half4(curve.eval(float2(v.r, 0.5)).r, curve.eval(float2(v.g, 0.5)).g, curve.eval(float2(v.b, 0.5)).b, c.a);\n" +
            "}\n";

    /** Shows (or removes) the filter on the live preview view. Only supported on Android 13+;
     *  on older versions the live view stays unfiltered (photos are still filtered).
     */
    public static void applyToView(View view, boolean enable) {
        if( view == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU )
            return;
        if( !enable ) {
            view.setRenderEffect(null);
            return;
        }
        Bitmap curve = Bitmap.createBitmap(256, 1, Bitmap.Config.ARGB_8888);
        for(int i=0;i<256;i++)
            curve.setPixel(i, 0, Color.argb(255, CURVE_R[i], CURVE_G[i], CURVE_B[i]));
        RuntimeShader shader = new RuntimeShader(SHADER_SRC);
        shader.setInputBuffer("curve", new BitmapShader(curve, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        view.setRenderEffect(RenderEffect.createRuntimeShaderEffect(shader, "content"));
    }
}
