package com.resort.platform.invitations;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

/** QR do convite (RN08, D-002, D-083): só {@code RSV:} e o código, em PNG de 512 px, correção M. */
public final class QrCodes {

    public static final String PREFIX = "RSV:";
    static final int SIZE = 512;

    private QrCodes() {}

    public static String content(String code) {
        return PREFIX + code;
    }

    public static byte[] png(String code) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(content(code), BarcodeFormat.QR_CODE, SIZE, SIZE,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 2));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (WriterException e) {
            // A mensagem do ZXing não traz o conteúdo; ainda assim não é repassada.
            throw new IllegalStateException("Falha ao gerar o QR do convite.");
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao gerar o PNG do QR do convite.");
        }
    }
}
