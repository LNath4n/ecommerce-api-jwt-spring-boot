package com.ecommerce.FerreViky.utils;
import com.ecommerce.FerreViky.exceptions.productos.ProductosExceptions.ImagenInvalidaException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;


@Component
public class ImagenValidator {
    private static final Map<String, String> EXT = Map.of("image/jpeg", ".jpg", "image/png", ".png");
    private static final Map<String, String> FORMATO = Map.of("image/jpeg", "jpeg", "image/png", "png");
    private static final int MAX_LADO_PX = 4000;
    private static final long MAX_PIXELES = 16_000_000L;

    /** Valida y devuelve la extensión (.jpg / .png). */
    public String validar(MultipartFile file) throws IOException {
        String ct = file.getContentType();
        String ext = (ct == null) ? null : EXT.get(ct);
        if (ext == null) throw new ImagenInvalidaException("Formato no permitido. Usa JPG o PNG");

        try (ImageInputStream iis = ImageIO.createImageInputStream(file.getInputStream())) {
            if (iis == null) throw new ImagenInvalidaException("El archivo no es una imagen válida");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) throw new ImagenInvalidaException("El archivo no es una imagen válida");

            ImageReader reader = readers.next();
            try {
                if (!reader.getFormatName().toLowerCase().equals(FORMATO.get(ct)))
                    throw new ImagenInvalidaException("El contenido del archivo no coincide con su formato");

                reader.setInput(iis, true, true);
                int w = reader.getWidth(0), h = reader.getHeight(0);
                if (w <= 0 || h <= 0 || w > MAX_LADO_PX || h > MAX_LADO_PX || (long) w * h > MAX_PIXELES)
                    throw new ImagenInvalidaException(
                            "La imagen es demasiado grande (máx. " + MAX_LADO_PX + "x" + MAX_LADO_PX + " px)");
                reader.read(0);
            } catch (ImagenInvalidaException e) {
                throw e;
            } catch (IOException | RuntimeException e) {
                throw new ImagenInvalidaException("El archivo no es una imagen válida");
            } finally {
                reader.dispose();
            }
        }
        return ext;
    }
}