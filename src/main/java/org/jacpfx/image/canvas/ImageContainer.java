package org.jacpfx.image.canvas;

import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.io.IOException;
import java.lang.ref.SoftReference;
import java.nio.file.Path;
import java.util.Map;

/**
 * Created by amo on 11.04.14.
 */
public class ImageContainer implements Cloneable {
    /**
     * start point x
     */
    private double startX;
    /**
     * start point y
     */
    private double startY;
    /**
     * end point x (the image width)
     */
    private double endX;
    /**
     * end point y (the image hight)
     */
    private double endY;
    /**
     * the scaled point x
     */
    private double scaledX;
    /**
     * the scaled point y
     */
    private double scaledY;
    /**
     * image in landscape
     */
    private boolean landsScape;
    /**
     * zooming/scaling factor
     */
    private double scaleFactor = 1;
    /**
     * the path to image
     */
    private Path imagePath;

    /**
     * The image creation factory
     */
    private ImageFactory factory;
    /**
     * maximum hight
     */
    private double maxHight;
    /**
     * maximum width
     */
    private double maxWidth;

    /**
     * position in row
     */
    private int position = 0;

    /**
     * The last position for drawing
     */
    private double lastDrawingStartPosition;

    /**
     * the image ref (maxHight * 2)
     */
    private transient SoftReference<Image> imageRef = new SoftReference<Image>(null);
    private transient SoftReference<Image> imageRefOrig = new SoftReference<Image>(null);

    private boolean selected;

    public ImageContainer(Path imagePath, ImageFactory factory, double maxHight, double maxWidth) {
        this.imagePath = imagePath;
        this.factory = factory;
        this.maxHight = maxHight;
        this.maxWidth = maxWidth;
        if (this.imagePath != null) {

            try {
                final Map.Entry<Double, Double> entry = factory.getImageSize(imagePath, maxHight);
                endX = entry.getKey();
                endY = entry.getValue();
                this.landsScape = endX > endY;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }
    // TODO add offMemory cache: https://github.com/RuedigerMoeller/fast-serialization/blob/master/src/main/java/org/nustaq/offheap/FSTAsciiStringOffheapMap.java

    public void drawImageToCanvas(final GraphicsContext gc, final double start) {
        lastDrawingStartPosition = start;
        if (imageRef.get() == null) {

            try {
                final Image img = factory.createImage(imagePath, maxWidth, maxHight);
                drawAsync(gc, img);
                drawSync(gc, img);
            } catch (Exception e) {
                e.printStackTrace();
            }
            // TODO move placeholder creation to factory
            if (imageRef.get() == null)
                imageRef = new SoftReference<Image>(new Rectangle(getScaledX(), getScaledY()).snapshot(new SnapshotParameters(), null));
        }
        gc.drawImage(imageRef.get(), getStartX(), start, getScaledX(), getScaledY());
    }

    private void drawSync(GraphicsContext gc, Image img) {
        if (img.getProgress() >= 1.0) {
            drawImageWhenFinished(gc, img);
        }
    }

    private void drawAsync(GraphicsContext gc, Image img) {
        img.progressProperty().addListener((ov, oldVal, newVal) -> {
            if (newVal.doubleValue() >= 1.0) {
                drawImageWhenFinished(gc, img);
            }

        });
    }

    private void drawImageWhenFinished(final GraphicsContext gc, final Image img) {
        final Image image = factory.postProcess(img, maxHight, maxWidth);
        gc.drawImage(image, getStartX(), lastDrawingStartPosition, getScaledX(), getScaledY());
        imageRef = new SoftReference<Image>(image);
    }


    // TODO move operation to ImageFactory!!
    public void drawSelectedImageOnConvas(GraphicsContext gc) {
        if (!selected) {
            imageRefOrig = new SoftReference<Image>(imageRef.get());
            ////
            ImageView view = new ImageView(imageRef.get());
            view.setEffect(new DropShadow(20, 10, 10, Color.GRAY));
            final Image imageEffect = view.snapshot(null, null);
            //
            gc.drawImage(imageEffect, getStartX(), lastDrawingStartPosition, getScaledX(), getScaledY());
            imageRef = new SoftReference<Image>(imageEffect);
            selected = true;
        } else {
            imageRef = new SoftReference<Image>(imageRefOrig.get());
            drawImageToCanvas(gc, lastDrawingStartPosition);
            imageRefOrig = new SoftReference<Image>(null);
            selected = false;
        }

    }

    public double getStartX() {
        return startX;
    }

    public void setStartX(double startX) {
        this.startX = startX;
    }

    public double getStartY() {
        return startY;
    }

    public void setStartY(double startY) {
        this.startY = startY;
    }


    public double getEndY() {
        return endY;
    }

    public double getEndX() {
        return endX;
    }


    public double getScaleFactor() {
        return scaleFactor;
    }

    public void setScaleFactor(double scaleFactor) {
        this.scaleFactor = scaleFactor;
        this.scaledX = this.endX * scaleFactor;
        this.scaledY = this.endY * scaleFactor;
    }

    public double getScaledX() {
        return this.scaledX;
    }

    public double getScaledY() {
        return this.scaledY;
    }

    public boolean isSelected() {
        return selected;
    }

    public Path getImagePath() {
        return imagePath;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    @Override
    public String toString() {
        return "ImageContainer{" +
                "startX=" + startX +
                ", startY=" + startY +
                ", endX=" + endX +
                ", endY=" + endY +
                ", scaledX=" + scaledX +
                ", scaledY=" + scaledY +
                ", landsScape=" + landsScape +
                ", scaleFactor=" + scaleFactor +
                ", imagePath=" + imagePath +
                ", factory=" + factory +
                ", maxHight=" + maxHight +
                ", maxWidth=" + maxWidth +
                ", position=" + position +
                '}';
    }

    public ImageContainer resetStart() {
        this.startX = 0d;
        this.startY = 0d;
        return this;
    }

    public void clearImageRef() {
        imageRef.clear();
    }

    public Object clone() {
        this.startX = 0d;
        this.startY = 0d;
        return this;
    }
}
