package krispasi.omGames.random;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

final class RandomGifImage {
    static final int MAP_SIZE = 128;

    private static final String GIF_IMAGE_METADATA_FORMAT = "javax_imageio_gif_image_1.0";
    private static final String GIF_STREAM_METADATA_FORMAT = "javax_imageio_gif_stream_1.0";
    private static final int DEFAULT_DELAY_CENTISECONDS = 10;

    private final List<Frame> frames;

    private RandomGifImage(List<Frame> frames) {
        this.frames = List.copyOf(frames);
    }

    static RandomGifImage load(File file, int mapColumns, int mapRows) throws IOException {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
        if (!readers.hasNext()) {
            throw new IOException("No GIF reader is available.");
        }

        ImageReader reader = readers.next();
        try (ImageInputStream input = ImageIO.createImageInputStream(file)) {
            if (input == null) {
                throw new IOException("Cannot open GIF file.");
            }
            reader.setInput(input, false);
            int frameCount = reader.getNumImages(true);
            if (frameCount <= 0) {
                throw new IOException("GIF has no frames.");
            }

            int[] logicalSize = readLogicalSize(reader, reader.getWidth(0), reader.getHeight(0));
            BufferedImage canvas = new BufferedImage(logicalSize[0], logicalSize[1], BufferedImage.TYPE_INT_ARGB);
            List<Frame> loadedFrames = new ArrayList<>();
            Control previousControl = null;
            BufferedImage previousRestoreImage = null;

            for (int index = 0; index < frameCount; index++) {
                if (previousControl != null) {
                    if (isRestoreToBackground(previousControl.disposalMethod())) {
                        clear(canvas, previousControl.left(), previousControl.top(),
                                previousControl.width(), previousControl.height());
                    } else if (isRestoreToPrevious(previousControl.disposalMethod()) && previousRestoreImage != null) {
                        canvas = copy(previousRestoreImage);
                    }
                }

                Control control = readControl(reader.getImageMetadata(index), logicalSize[0], logicalSize[1]);
                BufferedImage restoreImage = isRestoreToPrevious(control.disposalMethod()) ? copy(canvas) : null;
                BufferedImage rawFrame = reader.read(index);
                Graphics2D graphics = canvas.createGraphics();
                try {
                    graphics.drawImage(rawFrame, control.left(), control.top(), null);
                } finally {
                    graphics.dispose();
                }
                loadedFrames.add(new Frame(scaleToMap(canvas, mapColumns, mapRows), control.delayTicks()));
                previousControl = control;
                previousRestoreImage = restoreImage;
            }

            return new RandomGifImage(loadedFrames);
        } finally {
            reader.dispose();
        }
    }

    int frameCount() {
        return frames.size();
    }

    BufferedImage frame(int index) {
        return frames.get(Math.floorMod(index, frames.size())).image();
    }

    int delayTicks(int index) {
        return frames.get(Math.floorMod(index, frames.size())).delayTicks();
    }

    private static int[] readLogicalSize(ImageReader reader, int fallbackWidth, int fallbackHeight) {
        try {
            IIOMetadata metadata = reader.getStreamMetadata();
            if (metadata == null) {
                return new int[]{Math.max(1, fallbackWidth), Math.max(1, fallbackHeight)};
            }
            Node root = metadata.getAsTree(GIF_STREAM_METADATA_FORMAT);
            Node descriptor = child(root, "LogicalScreenDescriptor");
            int width = parsePositiveInt(attribute(descriptor, "logicalScreenWidth"), fallbackWidth);
            int height = parsePositiveInt(attribute(descriptor, "logicalScreenHeight"), fallbackHeight);
            return new int[]{Math.max(1, width), Math.max(1, height)};
        } catch (IOException | RuntimeException ex) {
            return new int[]{Math.max(1, fallbackWidth), Math.max(1, fallbackHeight)};
        }
    }

    private static Control readControl(IIOMetadata metadata, int fallbackWidth, int fallbackHeight) {
        int left = 0;
        int top = 0;
        int width = fallbackWidth;
        int height = fallbackHeight;
        int delayCentiseconds = DEFAULT_DELAY_CENTISECONDS;
        String disposalMethod = "none";

        try {
            Node root = metadata.getAsTree(GIF_IMAGE_METADATA_FORMAT);
            Node extension = child(root, "GraphicControlExtension");
            delayCentiseconds = parsePositiveInt(attribute(extension, "delayTime"), DEFAULT_DELAY_CENTISECONDS);
            String rawDisposal = attribute(extension, "disposalMethod");
            if (rawDisposal != null && !rawDisposal.isBlank()) {
                disposalMethod = rawDisposal;
            }

            Node descriptor = child(root, "ImageDescriptor");
            left = parseNonNegativeInt(attribute(descriptor, "imageLeftPosition"), 0);
            top = parseNonNegativeInt(attribute(descriptor, "imageTopPosition"), 0);
            width = parsePositiveInt(attribute(descriptor, "imageWidth"), fallbackWidth);
            height = parsePositiveInt(attribute(descriptor, "imageHeight"), fallbackHeight);
        } catch (RuntimeException ignored) {
            // Keep the conservative defaults if metadata is incomplete.
        }

        int delayTicks = Math.max(1, (int) Math.round(delayCentiseconds / 5.0d));
        return new Control(left, top, width, height, delayTicks, disposalMethod);
    }

    private static BufferedImage scaleToMap(BufferedImage source, int mapColumns, int mapRows) {
        int targetWidth = MAP_SIZE * Math.max(1, mapColumns);
        int targetHeight = MAP_SIZE * Math.max(1, mapRows);
        BufferedImage target = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setColor(Color.BLACK);
            graphics.fillRect(0, 0, targetWidth, targetHeight);

            double scale = Math.min(targetWidth / (double) source.getWidth(), targetHeight / (double) source.getHeight());
            int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
            int x = (targetWidth - width) / 2;
            int y = (targetHeight - height) / 2;
            graphics.drawImage(source, x, y, width, height, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private static BufferedImage copy(BufferedImage source) {
        BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = copy.createGraphics();
        try {
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return copy;
    }

    private static void clear(BufferedImage image, int x, int y, int width, int height) {
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setComposite(AlphaComposite.Clear);
            graphics.fillRect(Math.max(0, x), Math.max(0, y), Math.max(1, width), Math.max(1, height));
        } finally {
            graphics.dispose();
        }
    }

    private static Node child(Node node, String name) {
        if (node == null) {
            return null;
        }
        NodeList children = node.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child != null && name.equals(child.getNodeName())) {
                return child;
            }
        }
        return null;
    }

    private static String attribute(Node node, String name) {
        if (node == null) {
            return null;
        }
        NamedNodeMap attributes = node.getAttributes();
        if (attributes == null) {
            return null;
        }
        Node attribute = attributes.getNamedItem(name);
        return attribute == null ? null : attribute.getNodeValue();
    }

    private static int parsePositiveInt(String value, int fallback) {
        int parsed = parseInt(value, fallback);
        return parsed > 0 ? parsed : fallback;
    }

    private static int parseNonNegativeInt(String value, int fallback) {
        int parsed = parseInt(value, fallback);
        return parsed >= 0 ? parsed : fallback;
    }

    private static int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static boolean isRestoreToBackground(String disposalMethod) {
        return "restoreToBackgroundColor".equalsIgnoreCase(disposalMethod);
    }

    private static boolean isRestoreToPrevious(String disposalMethod) {
        return "restoreToPrevious".equalsIgnoreCase(disposalMethod);
    }

    private record Frame(BufferedImage image, int delayTicks) {
    }

    private record Control(int left, int top, int width, int height, int delayTicks, String disposalMethod) {
    }
}
