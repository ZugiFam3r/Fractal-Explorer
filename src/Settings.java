import java.io.*;
import java.util.Properties;

/**
 * Manages application settings with save/load functionality.
 */
public class Settings {

    private static final String SETTINGS_FILE = "fractal_settings.properties";

    // View settings
    public double centerX = -0.5;
    public double centerY = 0.0;
    public double zoom = 1.0;

    // Render settings
    public int maxIterations = 256;
    public boolean autoIterations = true;
    public boolean antiAliasing = false;
    public boolean animatedRender = false;

    // Color settings
    public int paletteIndex = 0;
    public boolean whiteInterior = false;
    public boolean complexityColoring = false;

    // Fractal settings
    public String fractalType = "Mandelbrot";
    public String customFormula = "z**2+c";
    public boolean juliaMode = false;
    public double juliaRe = -0.7;
    public double juliaIm = 0.27;

    // Window settings
    public int windowWidth = 800;
    public int windowHeight = 800;
    public boolean controlPanelVisible = true;

    /**
     * Load settings from file.
     */
    public void load() {
        File file = new File(SETTINGS_FILE);
        if (!file.exists()) return;

        try (FileInputStream fis = new FileInputStream(file)) {
            Properties props = new Properties();
            props.load(fis);

            centerX = Double.parseDouble(props.getProperty("centerX", "-0.5"));
            centerY = Double.parseDouble(props.getProperty("centerY", "0.0"));
            zoom = Double.parseDouble(props.getProperty("zoom", "1.0"));

            maxIterations = Integer.parseInt(props.getProperty("maxIterations", "256"));
            autoIterations = Boolean.parseBoolean(props.getProperty("autoIterations", "true"));
            antiAliasing = Boolean.parseBoolean(props.getProperty("antiAliasing", "false"));
            animatedRender = Boolean.parseBoolean(props.getProperty("animatedRender", "false"));

            paletteIndex = Integer.parseInt(props.getProperty("paletteIndex", "0"));
            whiteInterior = Boolean.parseBoolean(props.getProperty("whiteInterior", "false"));
            complexityColoring = Boolean.parseBoolean(props.getProperty("complexityColoring", "false"));

            fractalType = props.getProperty("fractalType", "Mandelbrot");
            customFormula = props.getProperty("customFormula", "z**2+c");
            juliaMode = Boolean.parseBoolean(props.getProperty("juliaMode", "false"));
            juliaRe = Double.parseDouble(props.getProperty("juliaRe", "-0.7"));
            juliaIm = Double.parseDouble(props.getProperty("juliaIm", "0.27"));

            windowWidth = Integer.parseInt(props.getProperty("windowWidth", "800"));
            windowHeight = Integer.parseInt(props.getProperty("windowHeight", "800"));
            controlPanelVisible = Boolean.parseBoolean(props.getProperty("controlPanelVisible", "true"));

        } catch (Exception e) {
            System.err.println("Could not load settings: " + e.getMessage());
        }
    }

    /**
     * Save settings to file.
     */
    public void save() {
        try (FileOutputStream fos = new FileOutputStream(SETTINGS_FILE)) {
            Properties props = new Properties();

            props.setProperty("centerX", String.valueOf(centerX));
            props.setProperty("centerY", String.valueOf(centerY));
            props.setProperty("zoom", String.valueOf(zoom));

            props.setProperty("maxIterations", String.valueOf(maxIterations));
            props.setProperty("autoIterations", String.valueOf(autoIterations));
            props.setProperty("antiAliasing", String.valueOf(antiAliasing));
            props.setProperty("animatedRender", String.valueOf(animatedRender));

            props.setProperty("paletteIndex", String.valueOf(paletteIndex));
            props.setProperty("whiteInterior", String.valueOf(whiteInterior));
            props.setProperty("complexityColoring", String.valueOf(complexityColoring));

            props.setProperty("fractalType", fractalType);
            props.setProperty("customFormula", customFormula);
            props.setProperty("juliaMode", String.valueOf(juliaMode));
            props.setProperty("juliaRe", String.valueOf(juliaRe));
            props.setProperty("juliaIm", String.valueOf(juliaIm));

            props.setProperty("windowWidth", String.valueOf(windowWidth));
            props.setProperty("windowHeight", String.valueOf(windowHeight));
            props.setProperty("controlPanelVisible", String.valueOf(controlPanelVisible));

            props.store(fos, "Fractal Explorer Settings");

        } catch (Exception e) {
            System.err.println("Could not save settings: " + e.getMessage());
        }
    }

    /**
     * Reset to defaults.
     */
    public void reset() {
        centerX = -0.5;
        centerY = 0.0;
        zoom = 1.0;
        maxIterations = 256;
        autoIterations = true;
        antiAliasing = false;
        animatedRender = false;
        paletteIndex = 0;
        whiteInterior = false;
        complexityColoring = false;
        fractalType = "Mandelbrot";
        customFormula = "z**2+c";
        juliaMode = false;
        juliaRe = -0.7;
        juliaIm = 0.27;
        windowWidth = 800;
        windowHeight = 800;
        controlPanelVisible = true;
    }
}
