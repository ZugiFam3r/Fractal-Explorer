import javax.sound.sampled.*;
import java.util.List;

/**
 * Audio engine that generates sound from fractal orbits.
 * Converts orbit points to audio waveforms like CodeParade's Fractal Sound Explorer.
 */
public class AudioEngine {
    private static final int SAMPLE_RATE = 48000;
    private static final int BUFFER_SIZE = 1024;  // Larger buffer for stability
    private static final int MAX_FREQ = 4000;

    private SourceDataLine audioLine;
    private volatile boolean running = false;
    private volatile boolean enabled = false;
    private Thread audioThread;

    // Double-buffered orbit data to avoid cloning in audio loop
    private double[] orbitRealA, orbitImagA;
    private double[] orbitRealB, orbitImagB;
    private volatile boolean useBufferA = true;
    private volatile int orbitLengthA = 0;
    private volatile int orbitLengthB = 0;

    private double baseVolume = 8000.0;
    private volatile double currentVolume = 8000.0;
    private volatile boolean damping = true;
    private static final double MIN_VOLUME = 100.0;  // Minimum volume to prevent silence

    // Crossfade state for smooth orbit transitions
    private volatile boolean needsCrossfade = false;
    private double crossfadeProgress = 1.0;
    private static final double CROSSFADE_RATE = 0.002;  // Smooth transition

    // Previous sample values for interpolation continuity
    private double prevLeft = 0, prevRight = 0;
    private double targetLeft = 0, targetRight = 0;

    public AudioEngine() {
        // Pre-allocate buffers
        orbitRealA = new double[2048];
        orbitImagA = new double[2048];
        orbitRealB = new double[2048];
        orbitImagB = new double[2048];

        try {
            AudioFormat format = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                SAMPLE_RATE,
                16,
                2,
                4,
                SAMPLE_RATE,
                false
            );

            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            if (!AudioSystem.isLineSupported(info)) {
                System.err.println("Audio line not supported");
                return;
            }

            audioLine = (SourceDataLine) AudioSystem.getLine(info);
            audioLine.open(format, BUFFER_SIZE * 8);

        } catch (LineUnavailableException e) {
            System.err.println("Audio unavailable: " + e.getMessage());
        }
    }

    public void setOrbit(List<Complex> orbit) {
        if (orbit == null || orbit.isEmpty()) {
            // Fade out instead of abrupt stop
            if (useBufferA) {
                orbitLengthA = 0;
            } else {
                orbitLengthB = 0;
            }
            return;
        }

        // Write to the inactive buffer
        double[] targetReal = useBufferA ? orbitRealB : orbitRealA;
        double[] targetImag = useBufferA ? orbitImagB : orbitImagA;

        int len = Math.min(orbit.size(), 2048);

        // Calculate center for normalization
        double centerX = 0, centerY = 0;
        for (int i = 0; i < len; i++) {
            Complex z = orbit.get(i);
            centerX += z.re;
            centerY += z.im;
        }
        centerX /= len;
        centerY /= len;

        // Store normalized values with soft clamping
        for (int i = 0; i < len; i++) {
            Complex z = orbit.get(i);
            double dx = z.re - centerX;
            double dy = z.im - centerY;
            // Soft clamp using tanh for smoother limiting
            targetReal[i] = Math.tanh(dx);
            targetImag[i] = Math.tanh(dy);
        }

        // Update length and swap buffers
        if (useBufferA) {
            orbitLengthB = len;
        } else {
            orbitLengthA = len;
        }

        // Trigger crossfade and swap
        needsCrossfade = true;
        crossfadeProgress = 0.0;
        useBufferA = !useBufferA;

        // Reset volume on new orbit
        currentVolume = baseVolume;
    }

    public void start() {
        if (audioLine == null || running) return;

        running = true;
        enabled = true;
        audioLine.start();

        audioThread = new Thread(this::audioLoop, "FractalAudio");
        audioThread.setDaemon(true);
        audioThread.setPriority(Thread.MAX_PRIORITY);  // High priority for audio
        audioThread.start();
    }

    public void stop() {
        running = false;
        enabled = false;
        if (audioLine != null) {
            audioLine.stop();
            audioLine.flush();
        }
    }

    public void toggle() {
        if (enabled) {
            stop();
        } else {
            start();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void toggleDamping() {
        this.damping = !this.damping;
        if (!damping) {
            currentVolume = baseVolume;
        }
    }

    public boolean isDamping() {
        return damping;
    }

    public void increaseDamping() {
        toggleDamping();
    }

    public void decreaseDamping() {
        toggleDamping();
    }

    public double getDamping() {
        return damping ? 1.0 : 0.0;
    }

    // Cosine interpolation for smooth transitions
    private double cosineInterp(double y1, double y2, double t) {
        double t2 = 0.5 - 0.5 * Math.cos(t * Math.PI);
        return y1 * (1.0 - t2) + y2 * t2;
    }

    private void audioLoop() {
        byte[] buffer = new byte[BUFFER_SIZE * 4];
        int audioTime = 0;
        int orbitIndex = 0;
        int stepsPerPoint = SAMPLE_RATE / MAX_FREQ;

        double dx = 0, dy = 0;
        double prevDx = 0, prevDy = 0;

        // Old buffer values for crossfade
        double oldDx = 0, oldDy = 0;

        while (running) {
            // Get current buffer info (lock-free read of volatiles)
            boolean currentBuffer = useBufferA;
            double[] realData = currentBuffer ? orbitRealA : orbitRealB;
            double[] imagData = currentBuffer ? orbitImagA : orbitImagB;
            int len = currentBuffer ? orbitLengthA : orbitLengthB;

            // Handle empty orbit - fade to silence
            if (len == 0) {
                for (int i = 0; i < BUFFER_SIZE; i++) {
                    prevLeft *= 0.995;
                    prevRight *= 0.995;

                    short leftSample = (short) (prevLeft * 0.5);
                    short rightSample = (short) (prevRight * 0.5);

                    int bufIdx = i * 4;
                    buffer[bufIdx] = (byte) (leftSample & 0xFF);
                    buffer[bufIdx + 1] = (byte) ((leftSample >> 8) & 0xFF);
                    buffer[bufIdx + 2] = (byte) (rightSample & 0xFF);
                    buffer[bufIdx + 3] = (byte) ((rightSample >> 8) & 0xFF);
                }
                audioLine.write(buffer, 0, buffer.length);

                // Small sleep to prevent busy-waiting
                try { Thread.sleep(1); } catch (InterruptedException e) {}
                continue;
            }

            // Generate audio samples
            for (int i = 0; i < BUFFER_SIZE; i++) {
                int j = audioTime % stepsPerPoint;

                // At start of each step, get next orbit point
                if (j == 0) {
                    prevDx = dx;
                    prevDy = dy;

                    // Safe array access
                    int idx = orbitIndex % len;
                    dx = realData[idx];
                    dy = imagData[idx];

                    // Check for NaN/Infinity and replace with safe values
                    if (!Double.isFinite(dx)) dx = prevDx * 0.9;
                    if (!Double.isFinite(dy)) dy = prevDy * 0.9;

                    orbitIndex++;

                    // Apply damping with minimum volume floor
                    if (damping) {
                        currentVolume *= 0.9995;
                        if (currentVolume < MIN_VOLUME) {
                            currentVolume = MIN_VOLUME;
                        }
                    }
                }

                // Cosine interpolation between points
                double t = (double) j / (double) stepsPerPoint;
                double wx = cosineInterp(prevDx, dx, t);
                double wy = cosineInterp(prevDy, dy, t);

                // Handle crossfade for smooth orbit transitions
                if (needsCrossfade && crossfadeProgress < 1.0) {
                    wx = cosineInterp(oldDx, wx, crossfadeProgress);
                    wy = cosineInterp(oldDy, wy, crossfadeProgress);
                    crossfadeProgress += CROSSFADE_RATE;
                    if (crossfadeProgress >= 1.0) {
                        needsCrossfade = false;
                    }
                }
                oldDx = wx;
                oldDy = wy;

                // Smooth the output to reduce clicks
                double smoothFactor = 0.1;
                targetLeft = wx * currentVolume;
                targetRight = wy * currentVolume;
                prevLeft += (targetLeft - prevLeft) * smoothFactor;
                prevRight += (targetRight - prevRight) * smoothFactor;

                // Soft clipping to prevent harsh distortion
                double left = Math.tanh(prevLeft / 32000.0) * 30000;
                double right = Math.tanh(prevRight / 32000.0) * 30000;

                short leftSample = (short) Math.max(-32767, Math.min(32767, left));
                short rightSample = (short) Math.max(-32767, Math.min(32767, right));

                int bufIdx = i * 4;
                buffer[bufIdx] = (byte) (leftSample & 0xFF);
                buffer[bufIdx + 1] = (byte) ((leftSample >> 8) & 0xFF);
                buffer[bufIdx + 2] = (byte) (rightSample & 0xFF);
                buffer[bufIdx + 3] = (byte) ((rightSample >> 8) & 0xFF);

                audioTime++;
            }

            audioLine.write(buffer, 0, buffer.length);
        }
    }

    public void cleanup() {
        stop();
        if (audioLine != null) {
            audioLine.close();
        }
    }
}
