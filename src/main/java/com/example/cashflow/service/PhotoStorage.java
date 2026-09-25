package com.example.cashflow.service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PhotoStorage {
    private final Path directory;

    public PhotoStorage(@Value("${app.photos.directory:./uploads/images}") String directory) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    public String save(MultipartFile photo) throws IOException {
        if (photo == null || photo.isEmpty()) return null;
        if (photo.getSize() > 5 * 1024 * 1024) {
            throw new IllegalArgumentException("写真は5MB以下にしてください。");
        }
        BufferedImage decoded;
        try (var input = photo.getInputStream(); ImageInputStream stream = ImageIO.createImageInputStream(input)) {
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw new IllegalArgumentException("写真はJPEG・PNG・WebP形式を選択してください。");
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName();
                if (!"JPEG".equalsIgnoreCase(format) && !"PNG".equalsIgnoreCase(format) && !"WebP".equalsIgnoreCase(format)) {
                    throw new IllegalArgumentException("写真はJPEG・PNG・WebP形式を選択してください。");
                }
                reader.setInput(stream);
                if ((long) reader.getWidth(0) * reader.getHeight(0) > 20_000_000) {
                    throw new IllegalArgumentException("写真は2,000万画素以下に縮小してください。");
                }
                decoded = reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (javax.imageio.IIOException exception) {
            throw new IllegalArgumentException("写真を読み込めません。別の写真を選択してください。", exception);
        }
        Files.createDirectories(directory);
        Path target = directory.resolve(UUID.randomUUID() + ".png");
        try {
            if (!ImageIO.write(decoded, "png", target.toFile())) throw new IOException("PNG encoder unavailable");
        } catch (IOException exception) {
            Files.deleteIfExists(target);
            throw exception;
        }
        return "/images/" + target.getFileName();
    }

    public void delete(String imagePath) throws IOException {
        if (imagePath != null && imagePath.matches("/images/[a-zA-Z0-9_-]+\\.(?i:jpg|jpeg|png|webp)")) {
            Files.deleteIfExists(directory.resolve(imagePath.substring("/images/".length())));
        }
    }
}
