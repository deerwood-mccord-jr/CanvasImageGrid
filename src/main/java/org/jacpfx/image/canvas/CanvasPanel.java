/*
 * **********************************************************************
 *
 *  Copyright (C) 2010 - 2015
 *
 *  [CanvasPanel.java]
 *  CanvasPanel Project (https://github.com/amoAHCP/CanvasImageGrid)
 *  All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an "AS IS"
 *  BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied. See the License for the specific language
 *  governing permissions and limitations under the License.
 *
 *
 * *********************************************************************
 */
package org.jacpfx.image.canvas;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.ScrollEvent;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Created by Andy Moncsek on 11.04.14.
 */
public class CanvasPanel extends Canvas {


    private double offset = 0d;
    private double lastOffset = 0d;
    private double lastOffsetEOL = 0d;
    private double currentMaxHight = 0d;
    private final double clippingOffset = 0.9d;

    private final DoubleProperty zoomFactorProperty = new SimpleDoubleProperty(1d);
    private final DoubleProperty maxImageHightProperty = new SimpleDoubleProperty();
    private final DoubleProperty maxImageWidthProperty = new SimpleDoubleProperty();
    private final DoubleProperty paddingProperty = new SimpleDoubleProperty();
    private final DoubleProperty scrollProperty = new SimpleDoubleProperty();
    private final DoubleProperty lineBreakThresholdProperty = new SimpleDoubleProperty();


    private List<RowContainer> containers = Collections.emptyList();
    private final ObservableList<ImageContainer> children = FXCollections.observableList(new ArrayList<>());


    private SelectionListener selectionListener = (x, y, images) -> {
    };


    private CanvasPanel(int x, int y, double padding, double lineBreakLimit, double maxHight, double maxWidth, final List<Path> imageFolder, final ImageFactory factory, SelectionListener selectionListener) {
        super(x, y);

        this.paddingProperty.set(padding);
        this.maxImageHightProperty.set(maxHight);
        this.maxImageWidthProperty.set(maxWidth);
        this.lineBreakThresholdProperty.set(lineBreakLimit);
        this.selectionListener = selectionListener;

        addImages(maxHight, maxWidth, imageFolder, factory);
        registerScroll(this.getGraphicsContext2D());
        registerZoom();
        registerScale(this.getGraphicsContext2D());
        registerMaxHightListener(this.getGraphicsContext2D());
        registerZoomListener(this.getGraphicsContext2D());
        registerPaddingListener(this.getGraphicsContext2D());
        registerChildListener(this.getGraphicsContext2D());
        registerLineBreakThresholdProperty(this.getGraphicsContext2D());
        registerMouseClickListener(selectionListener);

    }


    // Builder
    interface ImagePathBuilder {
        FactoryBuilder imagePath(final List<Path> imageFolder);
    }

    interface FactoryBuilder {
        WidthBuilder imageFactory(ImageFactory factory);
    }

    interface WidthBuilder {
        HightBuilder width(int width);
    }

    interface HightBuilder {
        PaddingBuilder hight(int hight);
    }

    interface PaddingBuilder {
        LineBreakLimitBuilder padding(double padding);
    }

    interface LineBreakLimitBuilder {
        MaxImageWidthBuilder lineBreakLimit(double lineBreakLimit);
    }

    interface MaxImageWidthBuilder {
        MaxImageHightBuilder maxImageWidth(double maxImageWidth);
    }

    interface MaxImageHightBuilder {
        SelectionListenerBuilder maxImageHight(double maxImageHight);
    }

    interface SelectionListenerBuilder {
        CanvasPanel selectionListener(final SelectionListener listener);
    }

    public static ImagePathBuilder createCanvasPanel() {
        return imagePath -> imageFactory -> width -> hight -> padding -> lineBreakLimit -> maxImageWidth -> maxImageHight -> selectionListsner -> new CanvasPanel(width, hight, padding, lineBreakLimit, maxImageHight, maxImageWidth, imagePath, imageFactory, selectionListsner);
    }

    private void registerMouseClickListener(SelectionListener selectionListener) {
        setOnMouseClicked(event -> children.forEach(image -> {
            final double tmpY = image.getStartY() + image.getScaledY();
            final double tmpX = image.getStartX() + image.getScaledX();
            boolean xCoord = image.getStartX() < event.getX() && tmpX > event.getX();
            boolean yCoord = image.getStartY() < event.getY() - offset && tmpY > event.getY() - offset;
            if (yCoord && xCoord) {
                image.drawSelectedImageOnConvas(this.getGraphicsContext2D());
                selectionListener.selected(event.getX(), event.getY(), image);
            } else if (image.isSelected()) {
                image.drawSelectedImageOnConvas(this.getGraphicsContext2D());
            }

        }));
    }

    private void addImages(double maxHight, double maxWidth, List<Path> imageFolder, ImageFactory factory) {
        final List<ImageContainer> all = imageFolder.parallelStream().map(path -> getConatiner(path, factory, maxHight, maxWidth)).collect(Collectors.toList());
        getChildren().addAll(all);
    }


    private ImageContainer getConatiner(Path path, ImageFactory factory, double maxHight, double maxWidth) {
        return new ImageContainer(path, factory, maxHight, maxWidth);
    }


    public ObservableList<ImageContainer> getChildren() {
        return children;
    }

    private void registerMaxHightListener(final GraphicsContext gc) {
        maxImageHightProperty.addListener(change -> containers = paintImages(gc, children));
    }

    private void registerZoomListener(final GraphicsContext gc) {
        zoomFactorProperty.addListener(change ->
                        containers = paintImages(gc, children)
        );
    }

    private void registerPaddingListener(final GraphicsContext gc) {
        paddingProperty.addListener(change ->
                        containers = paintImages(gc, children)
        );
    }

    private void registerLineBreakThresholdProperty(final GraphicsContext gc) {
        lineBreakThresholdProperty.addListener(change ->
                        containers = paintImages(gc, children)
        );
    }


    private void registerChildListener(final GraphicsContext gc) {
        children.addListener((ListChangeListener) change -> containers = paintImages(gc, children));
    }

    private void registerScroll(final GraphicsContext gc) {
        this.setOnScroll(handler -> canvasScroll(gc, handler));
    }

    private void canvasScroll(GraphicsContext gc, ScrollEvent handler) {
        lastOffset = offset;
        final double scrollDeltaY = handler.getDeltaY();
        double offsetNew = lastOffset + scrollDeltaY;
        if (offsetNew * -1 < currentMaxHight) {
            offset = offsetNew;
            double start = offset * -1;
            if (offset > 0d) {
                offset = scrollDeltaY;
            }
            prepareAndRender(gc, offset, start);
        } else {
            double tmp = lastOffsetEOL;
            lastOffsetEOL = scrollDeltaY;
            // prevent strange value "jumping" with getDeltaY
            if (tmp / lastOffsetEOL > .5) {
                final double start = offsetNew * -1;
                prepareAndRender(gc, offsetNew, start);
            }
        }
        handler.consume();
    }

    private void prepareAndRender(GraphicsContext gc, double offsetNew, double start) {
        final double height = this.getHeight();
        final double end = start + height + (height * clippingOffset);
        renderCanvas(this.containers, gc, start, end, offsetNew);
    }

    private void registerZoom() {
        final AtomicBoolean skip = new AtomicBoolean(true);
        final AtomicReference<Double> lastFactor = new AtomicReference<>(1d);
        this.setOnZoom(handler -> {
            double zoomFactor = zoomFactorProperty.doubleValue();
            double zoomFactorTmp = inRange(handler.getTotalZoomFactor() * zoomFactor);
            if (lastFactor.get() != zoomFactorTmp && skip.get()) {
                zoomFactorProperty.set(zoomFactorTmp);
            }
            lastFactor.set(zoomFactorProperty.doubleValue());
            skip.set(!skip.get());
            handler.consume();
        });

    }

    private void registerScale(final GraphicsContext gc) {
        this.widthProperty().addListener((observableValue, oldSceneWidth, newSceneWidth) -> {
            if (oldSceneWidth.doubleValue() != newSceneWidth.doubleValue()) {
                containers = paintImages(gc, children);
            }


        });
        this.heightProperty().addListener((observableValue, oldSceneHight, newSceneHight) -> {
            if (oldSceneHight.doubleValue() != newSceneHight.doubleValue()) {
                containers = paintImages(gc, children);
            }

        });
    }


    private double inRange(final double val) {
        if (val > 1.5d) {
            return 1.5d;
        } else if (val < 0.2d) {
            return 0.2;
        }
        return val;
    }

    private List<RowContainer> createContainer(final List<ImageContainer> all) {
        final List<ImageContainer> collect = all.stream().map(ImageContainer::resetStart).collect(Collectors.toList());
        return getLines(paddingProperty.doubleValue(),
                maxImageHightProperty.multiply(zoomFactorProperty).doubleValue(), collect);
    }

    private double computeMaxRowHight(final List<RowContainer> containers) {
        final RowContainer lastElement = !containers.isEmpty() ? containers.get(containers.size() - 1) : null;
        return lastElement != null ? lastElement.getRowEndHight() : 0d;
    }

    private List<RowContainer> paintImages(final GraphicsContext gc, final List<ImageContainer> all) {
        if (all == null || all.isEmpty()) return Collections.emptyList();
        final List<RowContainer> containers = createContainer(all);
        final double allRowHight = computeMaxRowHight(containers);
        final double height = this.getHeight();
        final double currentZoom = zoomFactorProperty.doubleValue();
        if (currentZoom < 1d) offset = offset * currentZoom;
        final double start = offset * -1;
        final double end = start + height + (height * clippingOffset);
        currentMaxHight = (allRowHight - height) + (paddingProperty.getValue() / 2);
        renderCanvas(containers, gc, start, end, offset);


        return containers;
    }

    private void renderCanvas(final List<RowContainer> containers, final GraphicsContext gc, final double start, final double end, final double offset) {

        gc.clearRect(0, 0, getWidth(), getHeight());
        containers.forEach(container -> container.
                        getImages().
                        stream().
                        filter(imgElem -> filterImagesVisible(start, end, imgElem)).
                        forEach(c ->
                                        c.drawImageToCanvas(gc, container.getRowStartHight() + offset)
                        )
        );
    }

    private boolean filterImagesVisible(double start, double end, ImageContainer imgElem) {
        final double tmp = imgElem.getStartY() + imgElem.getScaledY();
        return tmp > start && tmp < end;
    }


    private List<RowContainer> getLines(final double padding, final double maxHight, final List<ImageContainer> all) {
        final List<RowContainer> rows = createRows(this.getWidth(), maxHight, all);
        return normalizeRows(rows, padding);

    }


    /**
     * create rows with images that fit in each row
     *
     * @param maxWidth
     * @param maxHight
     * @param all
     * @return
     */
    private List<RowContainer> createRows(final double maxWidth, final double maxHight, final List<ImageContainer> all) {
        final List<RowContainer> rows = new ArrayList<>();
        int i = 0;
        double currentWidth = 0;
        RowContainer row = new RowContainer();
        row.setMaxWitdht(maxWidth);
        rows.add(row);
        for (final ImageContainer c : all) {
            c.setScaleFactor(maxHight / c.getEndY());
            final double tempWidth = c.getScaledX();
            if (i == 0) {
                currentWidth = tempWidth;
                row.add(c);
                i++;
                continue;
            }
            double currentWidthTmp = currentWidth + tempWidth;
            if (currentWidthTmp < maxWidth) {
                currentWidth = currentWidthTmp;
                row.add(c);
            } else {
                final double leftSpace = maxWidth - currentWidth;
                final double percentOfCurrentImage = leftSpace / tempWidth;
                if (percentOfCurrentImage > lineBreakThresholdProperty.get()) {
                    currentWidth += tempWidth;
                    row.add(c);
                } else {
                    row = new RowContainer();
                    currentWidth = tempWidth;
                    row.add(c);
                    row.setMaxWitdht(maxWidth);
                    rows.add(row);
                }

            }

            i++;
        }
        return rows;
    }


    private List<RowContainer> normalizeRows(final List<RowContainer> rows, final double padding) {
        if (rows.isEmpty()) return rows;
        rows.stream().findFirst().ifPresent(firstRow -> {
            normalizeWidth(firstRow, padding);
            handleFirstRow(firstRow, padding);
            // normalize width
            rows.parallelStream().
                    peek(row -> normalizeWidth(row, padding)).
                    sequential().
                    skip(1).
                    reduce(firstRow, (a, b) -> {
                        normalizeHight(b, padding, a.getRowEndHight());
                        return b;
                    });
        });
        return rows;
    }

    private void handleFirstRow(final RowContainer row, final double padding) {
        if (row.getImages().isEmpty()) return;
        final double v = padding / 2;
        row.getImages().forEach(img -> img.setStartY(v));
        final Optional<ImageContainer> first = getFirstImageInRow(row);
        // all images are normalized, take first and set row hight
        first.ifPresent(firstElement -> {
            row.setRowStartHight(v);
            row.setRowEndHight(firstElement.getScaledY() + padding * 1.5);
        });
    }


    private void normalizeHight(final RowContainer row, final double padding, final double maxHight) {
        if (row.getImages().isEmpty()) return;
        row.getImages().forEach(img -> img.setStartY(maxHight));
        final Optional<ImageContainer> first = getFirstImageInRow(row);

        // all images are normalized, take first and set row hight
        first.ifPresent(firstElement -> {
            row.setRowStartHight(maxHight);
            row.setRowEndHight(maxHight + firstElement.getScaledY() + padding);
        });


    }

    private Optional<ImageContainer> getFirstImageInRow(RowContainer row) {
        return Optional.ofNullable(row.getImages().size() > 0 ? row.getImages().get(0) : null);
    }

    private RowContainer normalizeWidth(final RowContainer row, final double padding) {
        if (row.getImages().isEmpty()) return row;
        final double max = getWidth();
        final int amount = row.getImages().size();
        final double length = row.getImages().stream().map(ImageContainer::getScaledX).reduce(0d, (a, b) -> a + b);
        final double scaleFactorNew = max / (length + (padding * (amount - 1)));
        final Optional<ImageContainer> first = getFirstImageInRow(row);
        first.ifPresent(fe -> {
            final ImageContainer firstElement = handleFirstImage(fe, scaleFactorNew);
            row.getImages().
                    stream().
                    skip(1).
                    peek(img ->
                            img.setScaleFactor(img.getScaleFactor() * scaleFactorNew)).
                    reduce(firstElement, (a, b) -> normalizeImageContainer(a, b, padding));
        });


        return row;
    }

    private ImageContainer normalizeImageContainer(ImageContainer a, ImageContainer b, double padding) {
        b.setStartX(a.getScaledX() + padding + a.getStartX());
        b.setPosition(a.getPosition() + 1);
        return b;
    }


    private ImageContainer handleFirstImage(final ImageContainer firstImage, final double scaleFactorNew) {
        firstImage.setScaleFactor(firstImage.getScaleFactor() * scaleFactorNew);
        firstImage.setPosition(1);
        return firstImage;
    }

    /**
     * Set image padding (Hgap and VGap)
     *
     * @return DoubleProperty
     */
    public DoubleProperty paddingProperty() {
        return this.paddingProperty;
    }

    /**
     * set the image padding value (Hgap / Vgap)
     *
     * @param padding
     */
    public void setPadding(final double padding) {
        this.paddingProperty.set(padding);
    }

    /**
     * The zoom factor property
     *
     * @return a DoubleProperty
     */
    public DoubleProperty zoomFactorProperty() {
        return this.zoomFactorProperty;
    }


    /**
     * set the zoom factor
     *
     * @param zoom
     */
    public void setZoomFactor(final double zoom) {
        this.zoomFactorProperty.set(zoom);
    }

    /**
     * The maximum hight of images property
     *
     * @return the DoubleProperty
     */
    public DoubleProperty maxImageHightProperty() {
        return this.maxImageHightProperty;
    }

    /**
     * set the maximum hight of images
     *
     * @param maxImageHight
     */
    public void setMaxImageHight(final double maxImageHight) {
        this.maxImageHightProperty.set(maxImageHight);
    }

    /**
     * The maximum width of images property
     *
     * @return the DoubleProperty
     */
    public DoubleProperty maxImageWidthProperty() {
        return this.maxImageWidthProperty;
    }

    /**
     * set the maximum hight of images
     *
     * @param maxImageWidth
     */
    public void setMaxImageWidth(final double maxImageWidth) {
        this.maxImageWidthProperty.set(maxImageWidth);
    }


    /**
     * The line break threshold property
     *
     * @return the Double property
     */
    public DoubleProperty lineBreakThresholdPropertyProperty() {
        return lineBreakThresholdProperty;
    }

    /**
     * Set the line break threshold property
     *
     * @param lineBreakThresholdProperty
     */
    public void setLineBreakThresholdProperty(double lineBreakThresholdProperty) {
        this.lineBreakThresholdProperty.set(lineBreakThresholdProperty);
    }

    /**
     * The scroll property to trigger scrolling
     *
     * @return The scroll property
     */
    public DoubleProperty scrollPropertyProperty() {
        return scrollProperty;
    }

    /**
     * Set the scroll property value
     *
     * @param scrollProperty
     */
    public void setScrollProperty(double scrollProperty) {
        this.scrollProperty.set(scrollProperty);
    }
}
