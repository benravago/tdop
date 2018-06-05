package demo;

import java.nio.CharBuffer;

import org.junit.BeforeClass;
import org.junit.Test;

import tdop.Parser.ParseError;

public class Tests {

    static Arith arith;

    @BeforeClass
    public static void setUp() {
        arith = new Arith();
    }

    String parse(String s) {
        CharBuffer src = CharBuffer.wrap(s.toCharArray());
        return arith.parser.parse(src).toString();
    }

    void t_parse(String s, String expected) {
        String sexpr = parse(s);
        if (expected != null && !sexpr.equals(expected)) {
            throw new AssertionError(sexpr+" != "+expected);
        }
        System.out.println(s+" -> "+sexpr); // print('%-40s %s' % (s, sexpr))
    }

    void assertParseError(String s, String errorSubstring) {
        try {
            parse(s);
            throw new AssertionError(s+" should have failed");
        }
        catch (ParseError e) {
            String err = e.getMessage(); // err = str(e)
            if (err.contains(errorSubstring)) {
                System.out.println(s+" -> got expected: "+err);
            } else {
                throw new AssertionError("Expected "+errorSubstring+" to be in "+err+" for "+s);
            }
        }
    }

    void assertParseError(String s) { assertParseError(s,""); }

    @Test
    public void testArith() {
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
        t_parse("x += y += 3", "(+= x (+= y 3))");
        t_parse("x[1,2]", "(get x (, 1 2))");
        t_parse("+1 - +2", "(- (+ 1) (+ 2))");
        t_parse("f[x] += 1", "(+= (get f x) 1)");
    }

    @Test
    public void testBitwise() {
        t_parse("~1 | ~2", "(| (~ 1) (~ 2))");
        t_parse("x & y | a & b", "(| (& x y) (& a b))");
        t_parse("~x ^ y", "(^ (~ x) y)");
        t_parse("x << y | y << z", "(| (<< x y) (<< y z))");
        t_parse("a ^= b-1", "(^= a (- b 1))");
    }

    @Test
    public void testLogical() {
        t_parse("a && b || c && d", "(|| (&& a b) (&& c d))");
        t_parse("!a && !b", "(&& (! a) (! b))");
        t_parse("a != b && c == d", "(&& (!= a b) (== c d))");

        t_parse("a > b ? 0 : 1", "(? (> a b) 0 1)");
        t_parse("a > b ? x+1 : y+1", "(? (> a b) (+ x 1) (+ y 1))");

        t_parse("1 ? true1 : 2 ? true2 : false", "(? 1 true1 (? 2 true2 false))");
        t_parse("1 ? true1 : (2 ? true2 : false)", "(? 1 true1 (? 2 true2 false))");

        t_parse("1 ? (2 ? true : false1) : false2", "(? 1 (? 2 true false1) false2)");
        t_parse("1 ? 2 ? true : false1 : false2", "(? 1 (? 2 true false1) false2)");

        t_parse("x ? 1 : 2, y ? 3 : 4", "(, (? x 1 2) (? y 3 4))");
        t_parse("a , b ? c, d : e, f", "(, a (? b (, c d) e) f)");
    }

    @Test
    public void testUnary() {
        t_parse("!x", "(! x)");
        t_parse("x--", "(post-- x)");
        t_parse("x[1]--", "(post-- (get x 1))");

        t_parse("--x", "(-- x)");
        t_parse("++x[1]", "(++ (get x 1))");

        t_parse("!x--", "(! (post-- x))");
        t_parse("~x++", "(~ (post++ x))");

        t_parse("x++ - y++", "(- (post++ x) (post++ y))");

        t_parse("++x - ++y", "(- (++ x) (++ y))");
    }

    @Test
    public void testArrays() {
        t_parse("x[1]", "(get x 1)");
        t_parse("x[a+b]", "(get x (+ a b))");
    }

    @Test
    public void testComma() {
        t_parse("x=1,y=2,z=3", "(, (= x 1) (= y 2) (= z 3))");
    }

    @Test
    public void testFuncCalls() {
        t_parse("x = y(2)*3 + y(4)*5",
                "(= x (+ (* (call y 2) 3) (* (call y 4) 5)))");
        t_parse("x(1,2)+y(3,4)",
                "(+ (call x 1 2) (call y 3 4))");
        t_parse("x(a,b,c[d])",
                "(call x a b (get c d))");
        t_parse("x(1,2)*j+y(3,4)*k+z(5,6)*l",
                "(+ (+ (* (call x 1 2) j) (* (call y 3 4) k)) (* (call z 5 6) l))");
        t_parse("print(test(2,3))",
                "(call print (call test 2 3))");
        t_parse("print(\"x\")",
                "(call print \"x\")");
        t_parse("min(255,n*2)",
                "(call min 255 (* n 2))");
        t_parse("c = pal[i*8]",
                "(= c (get pal (* i 8)))");
    }

    @Test
    public void testErrors() {
        assertParseError("}");
        assertParseError("]");
        assertParseError("{");
        assertParseError("x+1 = y", "Can't assign");
        assertParseError("(x+1)++", "Can't assign");
        assertParseError("foo ? 1 :", "Unexpected end");
        assertParseError("foo ? 1 ", "expected :");
        assertParseError("%", "can't be used in prefix position");
        assertParseError("}");
        assertParseError("{");
        assertParseError("]", "can't be used in prefix");
        assertParseError("1 ( 2", "can't be called");
        assertParseError("(x+1) ( 2 )", "can't be called");
        // assertParseError("1 ) 2");
        assertParseError("1 [ 2 ]", "can't be indexed");
    }
}
