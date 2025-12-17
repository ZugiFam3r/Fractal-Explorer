# Fractal Explorer

A comprehensive fractal visualization application written in Java.

## Features

- **16 Preset Fractals**: Mandelbrot, Cubic, Quartic, Quintic, Burning Ship, Tricorn, Phoenix, Feather, SFX, Sine, Magnet, Cosh, Zubieta, Mandelbar, Spider, Newton
- **Custom Formula Parser**: Enter your own formulas like `z**3+c`, `sin(z)+c`, `conj(z)**2+c`
- **Infinite Deep Zoom**: Arbitrary precision arithmetic allows unlimited zoom depth!
- **Julia Mode**: Explore Julia sets for any fractal (hold J for live preview!)
- **6D Parameter Space Explorer**: Explore z₀, exponent, rotation, c-offset and more!
- **Orbit Visualization**: See iteration paths with color-coded markers
- **8 Color Palettes**: Psychedelic, Fire, Ocean, Electric, Grayscale, Rainbow, Sunset, Forest
- **Anti-aliasing**: Smooth edges option
- **Progressive Rendering**: Loads in chunks for smooth experience
- **Multi-threaded**: Uses all CPU cores for fast rendering
- **Smooth Coloring**: Escape time algorithm for continuous gradients
- **Axis Lines**: Toggle coordinate axes with X key

## Deep Zoom (Infinite Exploration)

Normal fractal programs are limited to about 10^13x zoom due to double-precision floating-point limits. This explorer automatically switches to **arbitrary precision arithmetic** when you zoom past this limit!

- Below 10^13x: Fast double-precision calculation
- Above 10^13x: Automatic BigDecimal calculation with dynamically adjusted precision
- Precision increases with zoom depth (50-500 digits)
- Status bar shows "∞ Deep zoom" when in arbitrary precision mode

**Note**: Deep zoom rendering is slower (~1000 pixels/sec) due to arbitrary precision math. The Mandelbrot set supports infinite zoom; other fractals are limited to double precision.

## 6D Parameter Space Explorer

Press **4** or **6** to open the 6D Parameter Space Explorer!

### Parameters:
- **z₀ Real/Imag**: Starting point for iteration (instead of 0)
- **Exponent**: Fractional powers like z^2.5
- **View Rotation**: Rotate the complex plane
- **c Offset**: Shift the entire parameter space
- **Julia c**: Fixed c for Julia mode

### Features:
- Animate any parameter with ▶ buttons
- Auto-scaling iterations for deep zoom
- Preset locations for interesting spots

## Julia Mode

Click "✦ Julia Rotation Explorer" or hold **J** for live Julia preview!

## Project Structure

```
fractal-explorer/
├── src/
│   ├── Complex.java              # Complex number math
│   ├── ComplexParser.java        # Formula expression parser
│   ├── ColorPalette.java         # Color generation
│   ├── FractalType.java          # Fractal type definitions
│   ├── FractalCalculator.java    # Fractal calculations
│   ├── OrbitCalculator.java      # Orbit visualization
│   ├── FractalRenderer.java      # Progressive rendering
│   ├── FractalPanel.java         # Display panel
│   ├── ControlPanel.java         # UI controls
│   ├── FractalExplorer.java      # Main application
│   └── JuliaRotationExplorer.java # Julia parameter space explorer
├── build.sh                      # Build script
└── README.md
```

## Building

### Windows

```cmd
if not exist bin mkdir bin
javac -d bin src/*.java
```

### Linux/macOS

```bash
mkdir -p bin
javac -d bin src/*.java
```

## Running

```bash
java -cp bin FractalExplorer
```

## Create JAR (optional)

```bash
jar cfm FractalExplorer.jar MANIFEST.MF -C bin .
java -jar FractalExplorer.jar
```

## Create Standalone EXE (Windows, requires JDK 14+)

```cmd
jpackage --type app-image --input . --main-jar FractalExplorer.jar --name FractalExplorer --dest output
```

This creates a standalone EXE with bundled JRE in the `output/FractalExplorer/` folder.

## Keyboard Shortcuts

| Key | Action |
|-----|--------|
| R | Reset view |
| C | Cycle colors |
| S | Save image |
| Z | Toggle zoom mode |
| O | Toggle orbit mode |
| J | Toggle Julia mode |
| X | Toggle axis lines |
| H / D | Toggle control panel |
| B | Toggle interior color (black/white) |
| A | Toggle anti-aliasing |
| 4 / 6 | Open 6D Parameter Explorer |
| + / - | Zoom in/out |
| Arrow keys | Pan view |
| Escape | Clear orbit |

## Mouse Controls

- **Scroll wheel**: Zoom in/out
- **Left-drag**: Pan view
- **Left-click** (zoom mode): Zoom in at point
- **Left-click** (orbit mode): Show iteration path
- **Right-click**: Zoom out / Clear orbit

## Custom Formulas

The formula parser supports:

### Operators
- `+`, `-`, `*`, `/` - Basic arithmetic
- `**` or `^` - Power (e.g., `z**2`, `z^3`)
- Parentheses `()` for grouping

### Functions
- `sin(z)`, `cos(z)`, `tan(z)` - Trigonometric
- `sinh(z)`, `cosh(z)`, `tanh(z)` - Hyperbolic
- `exp(z)`, `log(z)`, `sqrt(z)` - Exponential/logarithmic
- `abs(z)` - Magnitude
- `conj(z)` - Complex conjugate
- `real(z)`, `imag(z)` - Extract components

### Variables
- `z` - Iterating variable
- `c` - Complex constant
- `i` - Imaginary unit
- `pi`, `e` - Mathematical constants

### Example Formulas
- `z**3 + c` - Cubic Mandelbrot
- `sin(z) + c` - Sine fractal
- `z**2 + z + c` - Mandelbrot with linear term
- `conj(z)**2 + c` - Tricorn
- `(z**2 + c)**2` - Nested iteration
- `z/c + z**2` - Custom combination

## License

MIT License - Feel free to use and modify!
