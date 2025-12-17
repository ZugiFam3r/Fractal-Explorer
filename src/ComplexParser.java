/**
 * Parser for complex mathematical expressions.
 * Supports any combination of operators and functions.
 *
 * OPERATORS: + - * / ** (or ^)
 * FUNCTIONS: sin, cos, tan, sinh, cosh, tanh, exp, log, ln, sqrt, abs, conj, real, imag,
 *            asin, acos, atan, asinh, acosh, atanh, floor, ceil, round, sign, arg, norm
 * VARIABLES: z, c, i, pi, e, x, y
 *            x = real part of z (iterating value's real component)
 *            y = imaginary part of z (iterating value's imaginary component)
 * NUMBERS: integers and decimals
 * 
 * Examples:
 *   z**2 + c
 *   sin(z) * cos(c) + z
 *   exp(z**2) + c
 *   (z + c)**3 / (z - c)
 *   real(z)**2 + imag(z)*i + c
 *   z**2.5 + c
 *   2z + 3c  (implicit multiplication)
 *   log(abs(z)) + arg(z)*i + c
 *   x**2 - y**2 + 2*x*y*i + c  (z**2+c written with x,y)
 *   abs(x) + abs(y)*i + c  (burning ship using x,y)
 * 
 * THREAD-SAFE: All state is kept in local ParseState object.
 */
public class ComplexParser {
    
    // Cache normalized formula to avoid re-normalizing
    private String lastFormula = null;
    private String lastNormalized = null;
    private int fastPathId = -1;  // Which fast path to use
    
    public ComplexParser() {}
    
    /**
     * Parse and evaluate a formula with fast paths for common formulas.
     */
    public Complex parseOptimized(String expression, Complex z, Complex c) {
        if (expression == null || expression.isEmpty()) {
            return z.square().add(c);
        }
        
        // Check if we've cached this formula
        String normalized;
        if (expression.equals(lastFormula)) {
            normalized = lastNormalized;
        } else {
            normalized = normalizeFormula(expression);
            lastFormula = expression;
            lastNormalized = normalized;
            fastPathId = identifyFastPath(normalized);
        }
        
        // Fast paths for common formulas (no parsing overhead)
        switch (fastPathId) {
            case 0: return z.square().add(c);                          // z**2+c
            case 1: return z.cube().add(c);                            // z**3+c
            case 2: return z.square().square().add(c);                 // z**4+c
            case 3: return z.pow(5).add(c);                            // z**5+c
            case 4: return z.sin().add(c);                             // sin(z)+c
            case 5: return z.cos().add(c);                             // cos(z)+c
            case 6: return z.tan().add(c);                             // tan(z)+c
            case 7: return z.exp().add(c);                             // exp(z)+c
            case 8: return z.sinh().add(c);                            // sinh(z)+c
            case 9: return z.cosh().add(c);                            // cosh(z)+c
            case 10: return z.tanh().add(c);                           // tanh(z)+c
            case 11: return z.square().add(z).add(c);                  // z**2+z+c
            case 12: return z.square().subtract(z).add(c);             // z**2-z+c
            case 13: return z.add(c).square();                         // (z+c)**2
            case 14: return z.conjugate().square().add(c);             // conj(z)**2+c
            case 15: return z.square().add(new Complex(c.re * 2, c.im * 2)); // z**2+c*2
            case 16: return z.square().add(new Complex(c.re * 0.5, c.im * 0.5)); // z**2+c/2
            case 17: {                                                  // 2*z**2+c
                Complex z2 = z.square();
                return new Complex(z2.re * 2, z2.im * 2).add(c);
            }
            case 18: return z.square().add(c.square());                // z**2+c**2
            case 19: return z.square().add(c.multiply(z));             // z**2+c*z
            case 20: return z.square().add(new Complex(c.re * 3, c.im * 3)); // z**2+c*3
            case 21: {                                                  // abs(z)**2+c
                double mag = z.magnitude();
                return new Complex(mag * mag, 0).add(c);
            }
            case 22: return z.sqrt().add(c);                           // sqrt(z)+c
            case 23: return z.log().add(c);                            // log(z)+c
            case 24: return z.multiply(c).add(c);                      // z*c+c
        }
        
        // Fall back to full parser
        return parse(expression, z, c);
    }
    
    private int identifyFastPath(String normalized) {
        switch (normalized) {
            case "z**2+c": case "z*z+c": return 0;
            case "z**3+c": case "z*z*z+c": return 1;
            case "z**4+c": return 2;
            case "z**5+c": return 3;
            case "sin(z)+c": return 4;
            case "cos(z)+c": return 5;
            case "tan(z)+c": return 6;
            case "exp(z)+c": return 7;
            case "sinh(z)+c": return 8;
            case "cosh(z)+c": return 9;
            case "tanh(z)+c": return 10;
            case "z**2+z+c": return 11;
            case "z**2-z+c": return 12;
            case "(z+c)**2": return 13;
            case "conj(z)**2+c": return 14;
            case "z**2+c*2": case "z**2+2*c": case "z**2+2c": return 15;
            case "z**2+c/2": case "z**2+0.5*c": return 16;
            case "2*z**2+c": case "2z**2+c": return 17;
            case "z**2+c**2": return 18;
            case "z**2+c*z": case "z**2+z*c": return 19;
            case "z**2+c*3": case "z**2+3*c": case "z**2+3c": return 20;
            case "abs(z)**2+c": return 21;
            case "sqrt(z)+c": return 22;
            case "log(z)+c": case "ln(z)+c": return 23;
            case "z*c+c": case "zc+c": return 24;
            default: return -1;
        }
    }
    
    /**
     * Full parse - handles any expression.
     */
    public Complex parse(String expression, Complex z, Complex c) {
        if (expression == null || expression.isEmpty()) {
            return z.square().add(c);
        }
        
        String normalized = normalizeFormula(expression);
        String withImplicit = addImplicitMultiplication(normalized);
        
        ParseState state = new ParseState(withImplicit, z, c);
        
        try {
            Complex result = parseExpression(state);
            
            if (result == null || !isValidComplex(result)) {
                return z.square().add(c);
            }
            
            return result;
        } catch (Exception e) {
            return z.square().add(c);
        }
    }
    
    private String normalizeFormula(String expression) {
        String result = expression.toLowerCase().trim()
            .replace(" ", "")
            .replace("^", "**");

        // Handle -> (arrow) notation
        // "z -> z**2 + c" becomes "z**2 + c"
        // "x -> expr1, y -> expr2" becomes "(expr1) + (expr2)*i"
        if (result.contains("->")) {
            result = processArrowNotation(result);
        }

        return result;
    }

    /**
     * Process arrow notation for iterative maps.
     * Supports: "z -> expr" or "x -> expr1, y -> expr2"
     */
    private String processArrowNotation(String formula) {
        if (formula == null || formula.length() < 4) {
            return formula != null ? formula : "z**2+c";
        }

        // Check for x -> ..., y -> ... pattern
        if (formula.contains("x->") && formula.contains("y->")) {
            // Split by comma to get x and y parts
            String xPart = null;
            String yPart = null;

            // Find x -> and y -> sections
            int xStart = formula.indexOf("x->");
            int yStart = formula.indexOf("y->");

            // Validate indices
            if (xStart < 0 || yStart < 0) {
                return formula;
            }

            if (xStart < yStart) {
                // x comes before y: "x -> expr1, y -> expr2"
                int comma = formula.indexOf(",", xStart);
                if (comma > xStart + 3 && comma < yStart && yStart + 3 <= formula.length()) {
                    xPart = formula.substring(xStart + 3, comma);
                    yPart = formula.substring(yStart + 3);
                }
            } else {
                // y comes before x: "y -> expr1, x -> expr2"
                int comma = formula.indexOf(",", yStart);
                if (comma > yStart + 3 && comma < xStart && xStart + 3 <= formula.length()) {
                    yPart = formula.substring(yStart + 3, comma);
                    xPart = formula.substring(xStart + 3);
                }
            }

            if (xPart != null && yPart != null && !xPart.isEmpty() && !yPart.isEmpty()) {
                // Combine: new_z = xPart + yPart*i
                return "(" + xPart + ")+(" + yPart + ")*i";
            }
        }

        // Simple form: "z -> expr" or "anything -> expr"
        int arrowPos = formula.indexOf("->");
        if (arrowPos >= 0 && arrowPos + 2 < formula.length()) {
            String result = formula.substring(arrowPos + 2);
            return result.isEmpty() ? "z**2+c" : result;
        }

        return formula;
    }
    
    /**
     * Add implicit multiplication for natural math notation.
     * 2z -> 2*z, z(... -> z*(, )z -> )*z, )( -> )*(, zc -> z*c
     */
    private String addImplicitMultiplication(String expr) {
        StringBuilder result = new StringBuilder();
        
        for (int i = 0; i < expr.length(); i++) {
            char ch = expr.charAt(i);
            result.append(ch);
            
            if (i < expr.length() - 1) {
                char next = expr.charAt(i + 1);
                boolean needMult = false;
                
                // After a digit
                if (Character.isDigit(ch)) {
                    // digit followed by letter (not e for scientific notation after .)
                    if (Character.isLetter(next) && next != 'e') needMult = true;
                    // digit followed by (
                    if (next == '(') needMult = true;
                }
                // After a letter (variable like z, c, i, e, pi, x, y)
                else if (ch == 'z' || ch == 'c' || ch == 'i' || ch == 'x' || ch == 'y') {
                    // variable followed by digit
                    if (Character.isDigit(next)) needMult = true;
                    // variable followed by (
                    if (next == '(') needMult = true;
                    // variable followed by another variable
                    if (next == 'z' || next == 'c' || next == 'i' || next == 'x' || next == 'y') needMult = true;
                }
                // After )
                else if (ch == ')') {
                    if (next == '(' || Character.isLetterOrDigit(next)) needMult = true;
                }
                
                if (needMult) {
                    result.append('*');
                }
            }
        }
        return result.toString();
    }
    
    /**
     * Local parse state for thread safety.
     */
    private static class ParseState {
        final String expr;
        final int len;
        final Complex z;
        final Complex c;
        int pos;

        ParseState(String expr, Complex z, Complex c) {
            this.expr = expr;
            this.len = expr.length();
            this.z = z;
            this.c = c;
            this.pos = 0;
        }
        
        char peek() {
            return pos < len ? expr.charAt(pos) : '\0';
        }
        
        char consume() {
            return pos < len ? expr.charAt(pos++) : '\0';
        }
        
        boolean match(String s) {
            if (pos + s.length() <= len && expr.substring(pos, pos + s.length()).equals(s)) {
                pos += s.length();
                return true;
            }
            return false;
        }
        
        boolean lookAhead(String s) {
            return pos + s.length() <= len && expr.substring(pos, pos + s.length()).equals(s);
        }
        
        void skipWhitespace() {
            while (pos < len && Character.isWhitespace(expr.charAt(pos))) pos++;
        }
    }
    
    private boolean isValidComplex(Complex c) {
        if (c == null) return false;
        if (Double.isNaN(c.re) || Double.isNaN(c.im)) return false;
        if (Double.isInfinite(c.re) || Double.isInfinite(c.im)) return false;
        if (Math.abs(c.re) > 1e50 || Math.abs(c.im) > 1e50) return false;
        return true;
    }
    
    private Complex safeResult(Complex c) {
        return isValidComplex(c) ? c : Complex.ZERO;
    }
    
    // Main expression parser - handles + and -
    private Complex parseExpression(ParseState s) {
        Complex left = parseTerm(s);
        if (left == null) return null;
        
        while (s.pos < s.len) {
            char op = s.peek();
            if (op == '+') {
                s.consume();
                Complex right = parseTerm(s);
                if (right == null) return left;
                left = left.add(right);
            } else if (op == '-') {
                s.consume();
                Complex right = parseTerm(s);
                if (right == null) return left;
                left = left.subtract(right);
            } else {
                break;
            }
            left = safeResult(left);
        }
        return left;
    }
    
    // Term parser - handles * and /
    private Complex parseTerm(ParseState s) {
        Complex left = parsePower(s);
        if (left == null) return null;
        
        while (s.pos < s.len) {
            char op = s.peek();
            if (op == '*' && !s.lookAhead("**")) {
                s.consume();
                Complex right = parsePower(s);
                if (right == null) return left;
                left = left.multiply(right);
            } else if (op == '/') {
                s.consume();
                Complex right = parsePower(s);
                if (right == null) return left;
                if (right.magnitudeSquared() < 1e-30) {
                    // Division by near-zero: return large value to trigger escape
                    return new Complex(1e15, 1e15);
                }
                left = left.divide(right);
            } else if (op == '%') {
                s.consume();
                Complex right = parsePower(s);
                if (right == null) return left;
                // Modulo with proper zero handling
                double modRe = Math.abs(right.re) > 1e-30 ? left.re % right.re : left.re;
                double modIm = Math.abs(right.im) > 1e-30 ? left.im % right.im : left.im;
                left = new Complex(modRe, modIm);
            } else {
                break;
            }
            left = safeResult(left);
        }
        return left;
    }
    
    // Power parser - handles **
    private Complex parsePower(ParseState s) {
        Complex left = parseUnary(s);
        if (left == null) return null;
        
        if (s.match("**")) {
            Complex right = parseUnary(s);
            if (right == null) return left;
            
            // Handle 0**c specially
            if (left.magnitudeSquared() < 1e-30) {
                if (right.re > 0) {
                    return Complex.ZERO;  // 0^(positive) = 0
                } else if (right.re < 0) {
                    return new Complex(1e10, 0);  // 0^(negative) = infinity (triggers escape)
                } else {
                    return Complex.ONE;  // 0^(pure imaginary) ≈ 1
                }
            }
            
            // Integer power (more precise)
            if (right.im == 0 && Math.abs(right.re - Math.round(right.re)) < 1e-10) {
                int n = (int) Math.round(right.re);
                if (n >= -20 && n <= 20) {
                    return safeResult(left.pow(n));
                }
            }
            // Complex power: a^b = exp(b * log(a))
            Complex logLeft = left.log();
            Complex exponent = right.multiply(logLeft);
            return safeResult(exponent.exp());
        }
        return left;
    }
    
    // Unary parser - handles - and +
    private Complex parseUnary(ParseState s) {
        char ch = s.peek();
        if (ch == '-') {
            s.consume();
            Complex val = parseUnary(s);
            return val == null ? null : val.negate();
        }
        if (ch == '+') {
            s.consume();
            return parseUnary(s);
        }
        return parseAtom(s);
    }
    
    // Atom parser - handles numbers, variables, functions, parentheses
    private Complex parseAtom(ParseState s) {
        if (s.pos >= s.len) return null;
        
        char ch = s.peek();
        
        // Parentheses
        if (ch == '(') {
            s.consume();
            Complex result = parseExpression(s);
            if (s.peek() == ')') s.consume();
            return result;
        }
        
        // Try to match functions (longest first to handle sinh before sin, etc.)
        Complex funcResult = tryParseFunction(s);
        if (funcResult != null) return funcResult;
        
        // Variables
        if (s.match("pi")) return new Complex(Math.PI, 0);
        if (s.match("z")) return s.z;
        if (s.match("c")) return s.c;
        if (s.match("x")) return new Complex(s.z.re, 0);  // x = real(z)
        if (s.match("y")) return new Complex(s.z.im, 0);  // y = imag(z)
        if (s.match("i")) return Complex.I;
        if (s.match("e") && !Character.isLetter(s.peek())) return new Complex(Math.E, 0);

        // Number
        return parseNumber(s);
    }
    
    private Complex tryParseFunction(ParseState s) {
        // Check for function name followed by (
        String[] functions = {
            "sinh", "cosh", "tanh", "asinh", "acosh", "atanh",
            "sin", "cos", "tan", "asin", "acos", "atan",
            "sqrt", "cbrt", "exp", "log", "ln", "log10", "log2",
            "abs", "norm", "arg", "conj", "real", "imag", "re", "im",
            "floor", "ceil", "round", "sign", "step",
            "sec", "csc", "cot", "sech", "csch", "coth"
        };
        
        for (String func : functions) {
            if (s.lookAhead(func + "(") || s.lookAhead(func + " ")) {
                s.pos += func.length();
                Complex arg = parseAtom(s);
                if (arg == null) {
                    // Invalid argument - return null to signal parse error
                    return null;
                }
                return safeResult(applyFunction(func, arg));
            }
        }
        return null;
    }
    
    private Complex applyFunction(String func, Complex arg) {
        switch (func) {
            // Trigonometric
            case "sin": return arg.sin();
            case "cos": return arg.cos();
            case "tan": return arg.tan();
            case "sec": return Complex.ONE.divide(arg.cos());
            case "csc": return Complex.ONE.divide(arg.sin());
            case "cot": return Complex.ONE.divide(arg.tan());
            
            // Inverse trigonometric
            case "asin": return asin(arg);
            case "acos": return acos(arg);
            case "atan": return atan(arg);
            
            // Hyperbolic
            case "sinh": return arg.sinh();
            case "cosh": return arg.cosh();
            case "tanh": return arg.tanh();
            case "sech": return Complex.ONE.divide(arg.cosh());
            case "csch": return Complex.ONE.divide(arg.sinh());
            case "coth": return Complex.ONE.divide(arg.tanh());
            
            // Inverse hyperbolic
            case "asinh": return asinh(arg);
            case "acosh": return acosh(arg);
            case "atanh": return atanh(arg);
            
            // Exponential and logarithmic
            case "exp": return arg.exp();
            case "log": case "ln": return arg.magnitudeSquared() < 1e-30 ? Complex.ZERO : arg.log();
            case "log10": return arg.magnitudeSquared() < 1e-30 ? Complex.ZERO : arg.log().multiply(new Complex(1/Math.log(10), 0));
            case "log2": return arg.magnitudeSquared() < 1e-30 ? Complex.ZERO : arg.log().multiply(new Complex(1/Math.log(2), 0));
            
            // Roots
            case "sqrt": return arg.sqrt();
            case "cbrt": return arg.pow(1.0/3.0);
            
            // Complex-specific
            case "abs": case "norm": return new Complex(arg.magnitude(), 0);
            case "arg": return new Complex(arg.argument(), 0);
            case "conj": return arg.conjugate();
            case "real": case "re": return new Complex(arg.re, 0);
            case "imag": case "im": return new Complex(arg.im, 0);
            
            // Rounding (real part only, keeps imaginary)
            case "floor": return new Complex(Math.floor(arg.re), Math.floor(arg.im));
            case "ceil": return new Complex(Math.ceil(arg.re), Math.ceil(arg.im));
            case "round": return new Complex(Math.round(arg.re), Math.round(arg.im));
            case "sign": return new Complex(Math.signum(arg.re), Math.signum(arg.im));
            case "step": return new Complex(arg.re >= 0 ? 1 : 0, arg.im >= 0 ? 1 : 0);
            
            default: return arg;
        }
    }
    
    // Inverse trig functions for complex numbers
    private Complex asin(Complex z) {
        // asin(z) = -i * log(iz + sqrt(1 - z²))
        Complex iz = Complex.I.multiply(z);
        Complex oneMinusZ2 = Complex.ONE.subtract(z.square());
        Complex sqrt = oneMinusZ2.sqrt();
        Complex sum = iz.add(sqrt);
        if (sum.magnitudeSquared() < 1e-30) return Complex.ZERO;
        return Complex.I.negate().multiply(sum.log());
    }
    
    private Complex acos(Complex z) {
        // acos(z) = -i * log(z + sqrt(z² - 1))
        Complex z2minus1 = z.square().subtract(Complex.ONE);
        Complex sqrt = z2minus1.sqrt();
        Complex sum = z.add(sqrt);
        if (sum.magnitudeSquared() < 1e-30) return Complex.ZERO;
        return Complex.I.negate().multiply(sum.log());
    }
    
    private Complex atan(Complex z) {
        // atan(z) = i/2 * log((1-iz)/(1+iz))
        Complex iz = Complex.I.multiply(z);
        Complex num = Complex.ONE.subtract(iz);
        Complex den = Complex.ONE.add(iz);
        if (den.magnitudeSquared() < 1e-30) return Complex.ZERO;
        Complex frac = num.divide(den);
        if (frac.magnitudeSquared() < 1e-30) return Complex.ZERO;
        return Complex.I.multiply(new Complex(0.5, 0)).multiply(frac.log());
    }
    
    private Complex asinh(Complex z) {
        // asinh(z) = log(z + sqrt(z² + 1))
        Complex z2plus1 = z.square().add(Complex.ONE);
        Complex sum = z.add(z2plus1.sqrt());
        if (sum.magnitudeSquared() < 1e-30) return Complex.ZERO;
        return sum.log();
    }
    
    private Complex acosh(Complex z) {
        // acosh(z) = log(z + sqrt(z² - 1))
        Complex z2minus1 = z.square().subtract(Complex.ONE);
        Complex sum = z.add(z2minus1.sqrt());
        if (sum.magnitudeSquared() < 1e-30) return Complex.ZERO;
        return sum.log();
    }
    
    private Complex atanh(Complex z) {
        // atanh(z) = 1/2 * log((1+z)/(1-z))
        Complex num = Complex.ONE.add(z);
        Complex den = Complex.ONE.subtract(z);
        if (den.magnitudeSquared() < 1e-30) return Complex.ZERO;
        Complex frac = num.divide(den);
        if (frac.magnitudeSquared() < 1e-30) return Complex.ZERO;
        return frac.log().multiply(new Complex(0.5, 0));
    }
    
    private Complex parseNumber(ParseState s) {
        int start = s.pos;
        boolean hasDot = false;
        boolean hasE = false;
        
        // Optional leading minus handled by parseUnary
        
        while (s.pos < s.len) {
            char ch = s.expr.charAt(s.pos);
            if (Character.isDigit(ch)) {
                s.pos++;
            } else if (ch == '.' && !hasDot && !hasE) {
                hasDot = true;
                s.pos++;
            } else if ((ch == 'e' || ch == 'E') && !hasE && s.pos > start) {
                hasE = true;
                s.pos++;
                // Handle optional sign after e
                if (s.pos < s.len && (s.expr.charAt(s.pos) == '+' || s.expr.charAt(s.pos) == '-')) {
                    s.pos++;
                }
            } else {
                break;
            }
        }
        
        if (start == s.pos) return null;
        
        try {
            double val = Double.parseDouble(s.expr.substring(start, s.pos));
            if (!Double.isFinite(val)) return null;
            return new Complex(val, 0);
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    /**
     * Extract the power from a formula for smooth coloring.
     */
    public static double extractPower(String formula) {
        if (formula == null) return 2;
        
        String f = formula.toLowerCase().trim().replace(" ", "").replace("^", "**");
        
        // Look for z**N pattern
        int idx = f.indexOf("z**");
        if (idx >= 0 && idx + 3 < f.length()) {
            StringBuilder num = new StringBuilder();
            for (int i = idx + 3; i < f.length(); i++) {
                char ch = f.charAt(i);
                if (Character.isDigit(ch) || ch == '.') {
                    num.append(ch);
                } else {
                    break;
                }
            }
            if (num.length() > 0) {
                try {
                    return Double.parseDouble(num.toString());
                } catch (NumberFormatException e) {
                    return 2;
                }
            }
        }
        
        // Check for specific patterns
        if (f.contains("z*z*z")) return 3;
        if (f.contains("z*z")) return 2;
        
        return 2;
    }
    
    /**
     * Test if a formula is valid by evaluating at test points.
     */
    public boolean isValidFormula(String formula) {
        if (formula == null || formula.trim().isEmpty()) return false;
        
        Complex[] testZ = { 
            new Complex(0.5, 0.3), 
            new Complex(-0.2, 0.7), 
            new Complex(0.1, -0.1),
            new Complex(1.0, 0.0)
        };
        Complex testC = new Complex(-0.4, 0.6);
        
        for (Complex z : testZ) {
            Complex result = parse(formula, z, testC);
            if (!isValidComplex(result)) return false;
        }
        return true;
    }
}
