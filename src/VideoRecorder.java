import javax.imageio.*;
import javax.imageio.metadata.*;
import javax.imageio.stream.*;
import javax.swing.*;
import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

/**
 * Records frames from a component and saves as animated GIF or MP4 (if FFmpeg available).
 */
public class VideoRecorder {

    private final JComponent component;
    private final List<BufferedImage> frames = new ArrayList<>();
    private javax.swing.Timer captureTimer;
    private boolean recording = false;
    private int frameRate = 30;  // FPS
    private long startTime;

    public VideoRecorder(JComponent component) {
        this.component = component;
    }

    public void setFrameRate(int fps) {
        this.frameRate = fps;
    }

    public boolean isRecording() {
        return recording;
    }

    public void startRecording() {
        if (recording) return;

        frames.clear();
        recording = true;
        startTime = System.currentTimeMillis();

        // Capture frames at specified frame rate
        int delay = 1000 / frameRate;
        captureTimer = new javax.swing.Timer(delay, e -> captureFrame());
        captureTimer.start();
    }

    public void stopRecording() {
        if (!recording) return;

        recording = false;
        if (captureTimer != null) {
            captureTimer.stop();
            captureTimer = null;
        }
    }

    private void captureFrame() {
        if (!recording || component == null) return;

        int w = component.getWidth();
        int h = component.getHeight();
        if (w <= 0 || h <= 0) return;

        BufferedImage frame = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = frame.createGraphics();
        component.paint(g);
        g.dispose();

        frames.add(frame);
    }

    public int getFrameCount() {
        return frames.size();
    }

    public double getRecordingDuration() {
        return frames.size() / (double) frameRate;
    }

    /**
     * Save recording as animated GIF
     */
    public File saveAsGif() throws IOException {
        if (frames.isEmpty()) {
            throw new IOException("No frames recorded");
        }

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File outputFile = new File("fractal_recording_" + timestamp + ".gif");

        ImageOutputStream output = new FileImageOutputStream(outputFile);

        // Get GIF writer
        ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
        ImageWriteParam params = writer.getDefaultWriteParam();

        writer.setOutput(output);
        writer.prepareWriteSequence(null);

        int delay = 100 / frameRate;  // Convert to centiseconds (GIF uses 1/100th second)
        if (delay < 1) delay = 1;

        for (int i = 0; i < frames.size(); i++) {
            BufferedImage frame = frames.get(i);

            // Convert to indexed color for GIF
            BufferedImage indexed = convertToIndexed(frame);

            IIOMetadata metadata = writer.getDefaultImageMetadata(
                new ImageTypeSpecifier(indexed), params);
            configureGifMetadata(metadata, delay, i == 0);

            writer.writeToSequence(new IIOImage(indexed, null, metadata), params);
        }

        writer.endWriteSequence();
        output.close();

        return outputFile;
    }

    /**
     * Save recording as MP4 using FFmpeg (if available)
     */
    public File saveAsMp4() throws IOException {
        if (frames.isEmpty()) {
            throw new IOException("No frames recorded");
        }

        // Create temp directory for frames
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File tempDir = new File(System.getProperty("java.io.tmpdir"), "fractal_frames_" + timestamp);
        tempDir.mkdirs();

        // Save frames as PNG
        for (int i = 0; i < frames.size(); i++) {
            File frameFile = new File(tempDir, String.format("frame_%05d.png", i));
            ImageIO.write(frames.get(i), "PNG", frameFile);
        }

        File outputFile = new File("fractal_recording_" + timestamp + ".mp4");

        // Try to use FFmpeg
        ProcessBuilder pb = new ProcessBuilder(
            "ffmpeg", "-y",
            "-framerate", String.valueOf(frameRate),
            "-i", new File(tempDir, "frame_%05d.png").getAbsolutePath(),
            "-c:v", "libx264",
            "-pix_fmt", "yuv420p",
            "-crf", "18",
            outputFile.getAbsolutePath()
        );
        pb.redirectErrorStream(true);

        try {
            Process process = pb.start();

            // Read output
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                // FFmpeg output
            }

            int exitCode = process.waitFor();

            // Clean up temp files
            for (File f : tempDir.listFiles()) {
                f.delete();
            }
            tempDir.delete();

            if (exitCode == 0 && outputFile.exists()) {
                return outputFile;
            } else {
                throw new IOException("FFmpeg failed with exit code " + exitCode);
            }

        } catch (Exception e) {
            // FFmpeg not available, clean up and fall back to GIF
            for (File f : tempDir.listFiles()) {
                f.delete();
            }
            tempDir.delete();
            throw new IOException("FFmpeg not available: " + e.getMessage());
        }
    }

    /**
     * Save recording - tries MP4 first, falls back to GIF
     */
    public File save() throws IOException {
        try {
            return saveAsMp4();
        } catch (IOException e) {
            // FFmpeg not available, use GIF
            return saveAsGif();
        }
    }

    /**
     * Clear recorded frames
     */
    public void clear() {
        frames.clear();
    }

    private BufferedImage convertToIndexed(BufferedImage src) {
        // Simple quantization to 256 colors for GIF
        BufferedImage indexed = new BufferedImage(
            src.getWidth(), src.getHeight(), BufferedImage.TYPE_BYTE_INDEXED);
        Graphics2D g = indexed.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return indexed;
    }

    private void configureGifMetadata(IIOMetadata metadata, int delayTime, boolean first) {
        String metaFormat = metadata.getNativeMetadataFormatName();
        IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(metaFormat);

        // Graphics Control Extension
        IIOMetadataNode gce = getOrCreateNode(root, "GraphicControlExtension");
        gce.setAttribute("disposalMethod", "none");
        gce.setAttribute("userInputFlag", "FALSE");
        gce.setAttribute("transparentColorFlag", "FALSE");
        gce.setAttribute("delayTime", String.valueOf(delayTime));
        gce.setAttribute("transparentColorIndex", "0");

        // Application Extension for looping
        if (first) {
            IIOMetadataNode appExts = getOrCreateNode(root, "ApplicationExtensions");
            IIOMetadataNode appExt = new IIOMetadataNode("ApplicationExtension");
            appExt.setAttribute("applicationID", "NETSCAPE");
            appExt.setAttribute("authenticationCode", "2.0");
            appExt.setUserObject(new byte[]{1, 0, 0});  // Loop forever
            appExts.appendChild(appExt);
        }

        try {
            metadata.setFromTree(metaFormat, root);
        } catch (IIOInvalidTreeException e) {
            // Ignore metadata errors
        }
    }

    private IIOMetadataNode getOrCreateNode(IIOMetadataNode root, String nodeName) {
        for (int i = 0; i < root.getLength(); i++) {
            if (root.item(i).getNodeName().equalsIgnoreCase(nodeName)) {
                return (IIOMetadataNode) root.item(i);
            }
        }
        IIOMetadataNode node = new IIOMetadataNode(nodeName);
        root.appendChild(node);
        return node;
    }
}
