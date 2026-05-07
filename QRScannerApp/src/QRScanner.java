import java.io.File;
import java.awt.image.BufferedImage;
import java.io.IOException;
import javax.imageio.ImageIO;

import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;

public class QRScanner {

    public String scanQRCode(String filePath) throws IOException, NotFoundException {
        if (filePath == null || filePath.isBlank()) {
            throw new IOException("image path is empty");
        }

        File imageFile = new File(filePath);
        if (!imageFile.isFile()) {
            throw new IOException("image file was not found: " + filePath);
        }

        BufferedImage image = ImageIO.read(imageFile);
        if (image == null) {
            throw new IOException("file is not a supported image: " + filePath);
        }

        LuminanceSource source = new BufferedImageLuminanceSource(image);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

        Result result = new MultiFormatReader().decode(bitmap);
        return result.getText();
    }

    /** Decode a QR code directly from an in-memory BufferedImage (used by the web API). */
    public String scanFromImage(BufferedImage image) throws NotFoundException {
        LuminanceSource source = new BufferedImageLuminanceSource(image);
        BinaryBitmap bitmap   = new BinaryBitmap(new HybridBinarizer(source));
        Result result         = new MultiFormatReader().decode(bitmap);
        return result.getText();
    }
}
