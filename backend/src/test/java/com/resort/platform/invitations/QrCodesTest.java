package com.resort.platform.invitations;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.resort.platform.TestCodes;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** I4: RN08 e D-002. O PNG gerado é lido de volta e o conteúdo é só RSV: e o código. */
class QrCodesTest {

    @Test
    void pngDecodesToThePrefixAndTheCodeOnly() throws Exception {
        String code = TestCodes.unique();

        byte[] png = QrCodes.png(code);

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(image.getWidth()).isEqualTo(512);
        assertThat(decode(png)).isEqualTo("RSV:" + code);
    }

    public static String decode(byte[] png) throws Exception {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));
        return new MultiFormatReader().decode(bitmap).getText();
    }
}
