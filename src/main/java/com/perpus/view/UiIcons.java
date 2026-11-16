package com.perpus.view;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

final class UiIcons {
    private static final Map<String, Image> IMAGES = new HashMap<>();

    private UiIcons() {}

    static ImageView image(String name, double size) {
        ImageView imageView = new ImageView(loadImage(name));
        imageView.setFitWidth(size);
        imageView.setFitHeight(size);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        return imageView;
    }

    private static Image loadImage(String name) {
        return IMAGES.computeIfAbsent(name, UiIcons::readImage);
    }

    private static Image readImage(String name) {
        String resourcePath = "/icons/" + name;
        try (InputStream imageStream = UiIcons.class.getResourceAsStream(resourcePath)) {
            if (imageStream == null) {
                throw new IllegalStateException("Ikon aplikasi tidak ditemukan: " + resourcePath);
            }
            Image image = new Image(imageStream);
            if (image.isError()) {
                throw new IllegalStateException("Ikon aplikasi gagal dimuat: " + resourcePath, image.getException());
            }
            return image;
        } catch (IOException ex) {
            throw new IllegalStateException("Gagal membaca ikon aplikasi: " + resourcePath, ex);
        }
    }
}
