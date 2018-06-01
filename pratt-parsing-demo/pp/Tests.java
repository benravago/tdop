package pp;

import static pp.TDOP.*;

/** tests.py */
public class Tests {

    ArithParse arithParse;

    void t_parse(String s, String expected) {
        arithParse.parseShell(s,expected);
    }

    class AssertionError extends RuntimeException {
        AssertionError(String msg) { super(msg); }
    }

    void assertParseError(String s, String errorSubstring) {
        Parser p = arithParse.makeParser(s);
        try {
            Node node = p.parse();
            throw new AssertionError(s+" should have failed");
        }
        catch (ParseError e) {
            String err = e.getMessage(); // err = str(e)
            if (err.contains(errorSubstring)) {
                System.out.println("got expected error for "+s+": "+err);
            } else {
                throw new AssertionError("Expected "+errorSubstring+" to be in "+err);
            }
        }
    }

    void assertParseError(String s) { assertParseError(s,""); }

    void testArith() {
        t_parse("1+2+3", "(+ (+ 1 2) 3)");
        t_parse("1+2*3", "(+ 1 (* 2 3))");
        t_parse("4*(2+3)", "(* 4 (+ 2 3))");
        t_parse("(2+3)*4", "(* (+ 2 3) 4)");
        t_parse("1<2", "(< 1 2)");
        t_parse("x=3", "(= x 3)");
        t_parse("x = 2*3", "(= x (* 2 3))");
        t_parse("x = y", "(= x y)");

        t_parse("x*y - y*z", "(- (* x y) (* y z))");
        t_parse("x/y - y%z", "(- (/ x y) (% y z))");

        t_parse("x = y", "(= x y)");
        t_parse("2 ** 3 ** 2", "(** 2 (** 3 2))");
        t_parse("a = b = 10", "(= a (= b 10))");

        t_parse("x = ((y*4)-2)", "(= x (- (* y 4) 2))");

        t_parse("x - -y", "(- x (- y))");
        t_parse("-1 * -2", "(* (- 1) (- 2))");
        t_parse("-x * -y", "(* (- x) (- y))");
        t_parse("x - -234", "(- x (- 234))");

        // Python doesn't allow this
        t_parse("x += y += 3", "(+= x (+= y 3))");

        // This is sort of nonsensical, but bash allows it.
        // The 1 is discarded as the first element of the comma operator.
        t_parse("x[1,2]", "(get x (, 1 2))");

        // Python doesn't have unary +
        t_parse("+1 - +2", "(- (+ 1) (+ 2))");

        // LHS
        t_parse("f[x] += 1", "(+= (get f x) 1)");
    }

    void testBitwise() {
        t_parse("~1 | ~2", "(| (~ 1) (~ 2))");
        t_parse("x & y | a & b", "(| (& x y) (& a b))");
        t_parse("~x ^ y", "(^ (~ x) y)");
        t_parse("x << y | y << z", "(| (<< x y) (<< y z))");

        t_parse("a ^= b-1", "(^= a (- b 1))");
    }

    void testLogical() {
        t_parse("a && b || c && d", "(|| (&& a b) (&& c d))");
        t_parse("!a && !b", "(&& (! a) (! b))");
        t_parse("a != b && c == d", "(&& (!= a b) (== c d))");

        t_parse("a > b ? 0 : 1", "(? (> a b) 0 1)");
        t_parse("a > b ? x+1 : y+1", "(? (> a b) (+ x 1) (+ y 1))");

        t_parse("1 ? true1 : 2 ? true2 : false", "(? 1 true1 (? 2 true2 false))");
        t_parse("1 ? true1 : (2 ? true2 : false)", "(? 1 true1 (? 2 true2 false))");

        t_parse("1 ? (2 ? true : false1) : false2", "(? 1 (? 2 true false1) false2)");
        t_parse("1 ? 2 ? true : false1 : false2", "(? 1 (? 2 true false1) false2)");

        // [cling]$ true ? 1 : 2, true ? 3 : 4
        // (int) 3
        // Comma expressions can be inside
        t_parse("x ? 1 : 2, y ? 3 : 4", "(, (? x 1 2) (? y 3 4))");
        t_parse("a , b ? c, d : e, f", "(, a (? b (, c d) e) f)");
    }

    void testUnary() {
        t_parse("!x", "(! x)");
        t_parse("x--", "(post-- x)");
        t_parse("x[1]--", "(post-- (get x 1))");

        t_parse("--x", "(-- x)");
        t_parse("++x[1]", "(++ (get x 1))");

        t_parse("!x--", "(! (post-- x))");
        t_parse("~x++", "(~ (post++ x))");

        t_parse("x++ - y++", "(- (post++ x) (post++ y))");

        t_parse("++x - ++y", "(- (++ x) (++ y))");

        // 1.   x++  f()  x[]  left associative
        //                     f(x)[1]++  means
        //                     (++ (get (call f x) 1))
        // 2.   ++x  + - ! ~   right associative
        //                     -++x means (- (++ x))
    }

    /** Shared between shell, oil, and Python. */
    void testArrays() {
        t_parse("x[1]", "(get x 1)");
        t_parse("x[a+b]", "(get x (+ a b))");
    }

    void testComma() {
        t_parse("x=1,y=2,z=3", "(, (= x 1) (= y 2) (= z 3))");
    }

    void testFuncCalls() {
        t_parse("x = y(2)*3 + y(4)*5", "(= x (+ (* (call y 2) 3) (* (call y 4) 5)))");

        t_parse("x(1,2)+y(3,4)", "(+ (call x 1 2) (call y 3 4))");
        t_parse("x(a,b,c[d])", "(call x a b (get c d))");
        t_parse("x(1,2)*j+y(3,4)*k+z(5,6)*l",
                "(+ (+ (* (call x 1 2) j) (* (call y 3 4) k)) (* (call z 5 6) l))");
        t_parse("print(test(2,3))", "(call print (call test 2 3))");
        t_parse("print(\"x\")", "(call print x)");
        t_parse("min(255,n*2)", "(call min 255 (* n 2))");
        t_parse("c = pal[i*8]", "(= c (get pal (* i 8)))");
    }

    void testErrors() {
        assertParseError("}");
        assertParseError("]");

        assertParseError("{"); // depends on language

        assertParseError("x+1 = y", "Can't assign");
        assertParseError("(x+1)++", "Can't assign");

        // Should be an EOF error
        assertParseError("foo ? 1 :", "Unexpected end");

        assertParseError("foo ? 1 ", "expected :");
        assertParseError("%", "can't be used in prefix position");

        String errorStr = "can't be used in prefix";
        assertParseError("}");
        assertParseError("{");
        assertParseError("]", errorStr);

        assertParseError("1 ( 2", "can't be called");
        assertParseError("(x+1) ( 2 )", "can't be called");
        // assertParseError("1 ) 2");

        assertParseError("1 [ 2 ]", "can't be indexed");
    }

    void main() {
        arithParse = new ArithParse();

        testArith();
        testBitwise();
        testLogical();
        testUnary();
        testArrays();
        testFuncCalls();
        testComma();
        testErrors();
    }

    public static void main(String[] args) throws Exception {
        new Tests().main();
    }
}
