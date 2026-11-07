package com.ecommerce.FerreViky.utils;

import com.ecommerce.FerreViky.exceptions.product.ProductExceptions.InvalidImageException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

@Component
public class ImageValidator {
    private static final Map<String, String> EXT = Map.of("image/jpeg", ".jpg", "image/png", ".png");
    private static final Map<String, String> FORMATO = Map.of("image/jpeg", "jpeg", "image/png", "png");
    private static final int MAX_LADO_PX = 4000;
    private static final long MAX_PIXELES = 16_000_000L;

    /** Resultado: bytes de la imagen sin metadatos y su extensión (.jpg / .png). */
    public record ImagenLimpia(byte[] data, String ext) {}

    /** Valida, decodifica UNA sola vez y devuelve los bytes sin metadatos (EXIF/GPS/XMP). */
    public ImagenLimpia validarYSanitizar(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty())
            throw new InvalidImageException("An image must be provided");

        String ct = file.getContentType();
        String ext = (ct == null) ? null : EXT.get(ct);
        if (ext == null) throw new InvalidImageException("Unsupported format. Use JPG or PNG");

        BufferedImage original;
        try (ImageInputStream iis = ImageIO.createImageInputStream(file.getInputStream())) {
            if (iis == null) throw new InvalidImageException("The file is not a valid image");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) throw new InvalidImageException("The file is not a valid image");

            ImageReader reader = readers.next();
            try {
                if (!reader.getFormatName().toLowerCase().equals(FORMATO.get(ct)))
                    throw new InvalidImageException("The file content does not match its format");

                reader.setInput(iis, true, true);
                int w = reader.getWidth(0), h = reader.getHeight(0);
                if (w <= 0 || h <= 0 || w > MAX_LADO_PX || h > MAX_LADO_PX || (long) w * h > MAX_PIXELES)
                    throw new InvalidImageException(
                            "Image is too large (maximum " + MAX_LADO_PX + "x" + MAX_LADO_PX + " px)");

                original = reader.read(0); // única decodificación
            } catch (InvalidImageException e) {
                throw e;
            } catch (IOException | RuntimeException e) {
                throw new InvalidImageException("The file is not a valid image");
            } finally {
                reader.dispose();
            }
        }

        boolean png = ext.equals(".png");
        BufferedImage limpia = original;

        // JPEG no soporta canal alfa: convertir a RGB para evitar fallos al escribir
        if (!png) {
            limpia = new BufferedImage(original.getWidth(), original.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = limpia.createGraphics();
            g.drawImage(original, 0, 0, Color.WHITE, null);
            g.dispose();
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(limpia, png ? "png" : "jpg", out))
            throw new InvalidImageException("Could not process the image");

        return new ImagenLimpia(out.toByteArray(), ext);
    }
}