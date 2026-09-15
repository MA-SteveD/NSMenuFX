package de.jangassen.platform.mac.convert;

import com.sun.jna.Memory;
import de.jangassen.jfa.appkit.NSData;
import de.jangassen.jfa.appkit.NSImage;
import de.jangassen.jfa.cleanup.NSCleaner;
import de.jangassen.util.MemoryUtils;
import de.jangassen.util.PngEncoder;
import javafx.application.Platform;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.scene.transform.Scale;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ImageConverter {

  private static final Logger LOG = Logger.getLogger(ImageConverter.class.getName());

  /**
   * Menu items are drawn at roughly this many points. Anything larger is scaled down on the JavaFX
   * side, because {@code NSImage} built from data takes its size from the pixel dimensions and this
   * binding does not expose {@code setSize:}.
   */
  private static final double MAX_EXTENT = 18;

  /**
   * Converts a menu item graphic to an image AppKit can draw.
   *
   * <p>
   * Three cases, most specific first: an {@link ImageView} backed by a URL is read straight from that
   * URL, keeping its encoded bytes untouched; an {@code ImageView} without one - which is what
   * {@code new Image(InputStream)} produces, since {@link Image#getUrl()} is null for those - is
   * re-encoded from its pixels; and any other node is rendered with {@link Node#snapshot}, which is
   * how a graphic that draws itself, such as a text or vector icon, becomes a bitmap.
   *
   * @param graphic the graphic, which may be {@code null}
   * @return the image, or empty if there was nothing to convert or it could not be converted
   */
  public static Optional<NSImage> convert(Node graphic) {
    if (graphic == null) {
      // Not a fault: separators and plain text items have no graphic, and most items are those.
      return Optional.empty();
    }

    byte[] png = toPng(graphic);
    if (png == null) {
      LOG.log(Level.WARNING,
              "Could not convert a menu item graphic of type {0} to an image; the item will have no icon.",
              graphic.getClass().getName());
      return Optional.empty();
    }

    NSImage nsImage = getNsImage(png);
    NSCleaner.register(graphic, nsImage);
    return Optional.of(nsImage);
  }

  private static byte[] toPng(Node graphic) {
    if (graphic instanceof ImageView) {
      Image image = ((ImageView) graphic).getImage();
      if (image == null) {
        return null;
      }

      byte[] encoded = readUrl(image);
      return encoded != null ? encoded : PngEncoder.encode(image);
    }

    return PngEncoder.encode(snapshot(graphic));
  }

  private static byte[] readUrl(Image image) {
    if (image.getUrl() == null) {
      return null;
    }

    try (InputStream stream = URI.create(image.getUrl()).toURL().openStream()) {
      return stream.readAllBytes();
    } catch (IOException | IllegalArgumentException ignored) {
      // IllegalArgumentException because URI.create rejects a malformed string unchecked, where
      // the URL constructor it replaces threw MalformedURLException and was caught as an IOException.
      return null;
    }
  }

  /**
   * Renders a node to an image.
   *
   * <p>
   * A graphic that has never been shown has no scene, and a node with no scene is never styled or
   * laid out, so it would snapshot to nothing. It is therefore put into a throwaway scene for long
   * enough to measure itself, and handed straight back - the caller's node keeps its own parent, or
   * keeps having none.
   */
  private static Image snapshot(Node graphic) {
    if (!Platform.isFxApplicationThread()) {
      return null;
    }

    Group holder = null;
    if (graphic.getScene() == null && graphic.getParent() == null) {
      holder = new Group(graphic);
      new Scene(holder);
      holder.applyCss();
      holder.layout();
    }

    try {
      SnapshotParameters parameters = new SnapshotParameters();
      parameters.setFill(Color.TRANSPARENT);

      double extent = Math.max(graphic.getLayoutBounds().getWidth(), graphic.getLayoutBounds().getHeight());
      if (extent > MAX_EXTENT) {
        double factor = MAX_EXTENT / extent;
        parameters.setTransform(new Scale(factor, factor));
      }

      return graphic.snapshot(parameters, null);
    } finally {
      if (holder != null) {
        holder.getChildren().clear();
      }
    }
  }

  private static NSImage getNsImage(byte[] imageData) {
    Memory memory = MemoryUtils.toMemory(imageData);
    NSData data = NSData.alloc().initWithBytes(memory, (int) memory.size());
    return NSImage.alloc().initWithData(data);
  }
}
