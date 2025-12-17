#!/bin/bash

# Fractal Explorer Build Script

echo "=================================="
echo "  Building Fractal Explorer"
echo "=================================="

# Create output directory
mkdir -p bin

# Compile all Java files
echo "Compiling..."
javac -d bin src/*.java

if [ $? -eq 0 ]; then
    echo "Build successful!"
    echo ""
    echo "To run: java -cp bin FractalExplorer"
    echo ""
else
    echo "Build failed!"
    exit 1
fi
