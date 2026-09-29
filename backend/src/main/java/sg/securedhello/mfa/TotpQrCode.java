package sg.securedhello.mfa;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

import javax.imageio.ImageIO;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

/** Renders the provisioning URI as a black-on-white PNG QR code, server side, as MFA_Core §2.2 prescribes (ADR-025). */
final class TotpQrCode {

    /** The image's width and height, in pixels. */
    static final int SIZE = 240;

    private static final int BLACK = 0x000000;
    private static final int WHITE = 0xFFFFFF;

    private TotpQrCode() {
    }

    static byte[] png(String contents) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(contents, BarcodeFormat.QR_CODE, SIZE, SIZE,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 2));
            BufferedImage image = new BufferedImage(matrix.getWidth(), matrix.getHeight(),
                    BufferedImage.TYPE_BYTE_BINARY);
            for (int x = 0; x < matrix.getWidth(); x++) {
                for (int y = 0; y < matrix.getHeight(); y++) {
                    image.setRGB(x, y, matrix.get(x, y) ? BLACK : WHITE);
                }
            }
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(image, "png", png);
            return png.toByteArray();
        } catch (WriterException ex) {
            throw new IllegalStateException("The provisioning URI could not be encoded as a QR code", ex);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
