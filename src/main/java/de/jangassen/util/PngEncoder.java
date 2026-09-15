package de.jangassen.util;

import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;

import java.io.ByteArrayOutputStream;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * Writes a JavaFX {@link Image} out as PNG bytes.
 *
 * <p>
 * Deliberately hand rolled rather than delegating to {@code SwingFXUtils} and {@code ImageIO}: those
 * initialise AWT, and an AWT that starts before JavaFX takes ownership of {@code NSApplication}, at
 * which point the Glass toolkit quietly declines to install a system menu bar at all. A menu library
 * cannot afford to boot AWT as a side effect of drawing an icon.
 *
 * <p>
 * Only what PNG requires is implemented: 8 bits per channel, colour type 6 (truecolour with alpha),
 * no interlacing, a single {@code IDAT} chunk and the "none" row filter. Menu icons are small enough
 * that neither filtering nor chunk splitting is worth the code.
 */
public final class PngEncoder {

  private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};

  private static final byte BIT_DEPTH = 8;
  private static final byte COLOUR_TYPE_RGBA = 6;
  private static final byte FILTER_NONE = 0;

  private PngEncoder() {
  }

  /**
   * Encodes an image as PNG.
   *
   * @param image the image to encode
   * @return the PNG bytes, or {@code null} if the image has no readable pixels or no extent, which
   *         is what a snapshot of a node that was never laid out looks like
   */
  public static byte[] encode(Image image) {
    if (image == null) {
      return null;
    }

    PixelReader reader = image.getPixelReader();
    int width = (int) Math.round(image.getWidth());
    int height = (int) Math.round(image.getHeight());
    if (reader == null || width <= 0 || height <= 0) {
      return null;
    }

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    writeBytes(out, SIGNATURE);
    writeChunk(out, "IHDR", header(width, height));
    writeChunk(out, "IDAT", deflate(scanlines(reader, width, height)));
    writeChunk(out, "IEND", new byte[0]);
    return out.toByteArray();
  }

  private static byte[] header(int width, int height) {
    ByteArrayOutputStream header = new ByteArrayOutputStream();
    writeInt(header, width);
    writeInt(header, height);
    header.write(BIT_DEPTH);
    header.write(COLOUR_TYPE_RGBA);
    header.write(0); // compression: deflate, the only method PNG defines
    header.write(0); // filter method: adaptive, the only method PNG defines
    header.write(0); // interlace: none
    return header.toByteArray();
  }

  /**
   * Reads the image a pixel at a time into filtered scanlines. {@code getArgb} rather than a bulk
   * {@code getPixels} because the channel order is then beyond doubt, and a menu icon is a couple of
   * hundred pixels.
   */
  private static byte[] scanlines(PixelReader reader, int width, int height) {
    byte[] raw = new byte[height * (1 + width * 4)];
    int index = 0;

    for (int y = 0; y < height; y++) {
      raw[index++] = FILTER_NONE;
      for (int x = 0; x < width; x++) {
        int argb = reader.getArgb(x, y);
        raw[index++] = (byte) (argb >> 16); // red
        raw[index++] = (byte) (argb >> 8);  // green
        raw[index++] = (byte) argb;         // blue
        raw[index++] = (byte) (argb >> 24); // alpha
      }
    }

    return raw;
  }

  private static byte[] deflate(byte[] raw) {
    Deflater deflater = new Deflater();
    try {
      deflater.setInput(raw);
      deflater.finish();

      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] buffer = new byte[4096];
      while (!deflater.finished()) {
        out.write(buffer, 0, deflater.deflate(buffer));
      }
      return out.toByteArray();
    } finally {
      deflater.end();
    }
  }

  private static void writeChunk(ByteArrayOutputStream out, String type, byte[] data) {
    byte[] typeBytes = type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    writeInt(out, data.length);
    writeBytes(out, typeBytes);
    writeBytes(out, data);

    CRC32 crc = new CRC32();
    crc.update(typeBytes);
    crc.update(data);
    writeInt(out, (int) crc.getValue());
  }

  private static void writeInt(ByteArrayOutputStream out, int value) {
    out.write(value >> 24);
    out.write(value >> 16);
    out.write(value >> 8);
    out.write(value);
  }

  private static void writeBytes(ByteArrayOutputStream out, byte[] bytes) {
    out.write(bytes, 0, bytes.length);
  }
}
