package com.example.aggregator.web;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Map;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

/**
 * 文字列（URL）から QR コードの PNG を生成し {@code data:} URI で返す（外部サービスに依存しない）。
 * ZXing の {@code core} だけを使い、{@link BitMatrix}→PNG は {@code java.awt}/ImageIO で自前変換する
 * （javase 依存を足さない）。LINE公式アカウントの友だち追加QRの表示に使う（SC-05）。
 */
@Component
public class QrCodeGenerator {

    /** {@code text} の QR を一辺 {@code size}px の PNG にして {@code data:image/png;base64,...} で返す。 */
    public String pngDataUri(String text, int size) {
        try {
            BitMatrix matrix = new MultiFormatWriter().encode(
                    text, BarcodeFormat.QR_CODE, size, size,
                    Map.of(EncodeHintType.MARGIN, 1, EncodeHintType.CHARACTER_SET, "UTF-8"));
            BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
            int black = 0x000000, white = 0xFFFFFF;
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    img.setRGB(x, y, matrix.get(x, y) ? black : white);
                }
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("QRコード生成に失敗: " + text, e);
        }
    }
}
