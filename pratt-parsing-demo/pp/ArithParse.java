package pp;

import java.util.Arrays;
import java.util.function.Supplier;

import static pp.TDOP.*;

/** arith_parse.py: Parse shell-like and C-like arithmetic. */
public class ArithParse {

    // # Null Denotation -- token that takes nothing on the left

    NUD NullConstant = (p,token,bp) -> {
        return new Node(token);
    };

    /** Arithmetic grouping. */
    NUD NullParen = (p,token,bp) -> {
        Node r = p.parseUntil(bp);
        p.eat(")");
        return r;
    };

    /**
     * Prefix operator.
     *
     * Low precedence:  return, raise, etc.
     *   return x+y is return (x+y), not (return x) + y
     *
     * High precedence: logical negation, bitwise complement, etc.
     *   !x && y is (!x) && y, not !(x && y)
     */
    NUD NullPrefixOp = (p,token,bp) -> {
        Node r = p.parseUntil(bp);
        return new CompositeNode(token, r );
    };

    /** ++x or ++x[1]. */
    NUD NullIncDec = (p,token,bp) -> {
        Node right = p.parseUntil(bp);
        if (!in( right.token.type, "name", "get" )) {
            throw new ParseError("Can't assign to "+right+" ("+right.token+')');
        }
        return new CompositeNode(token, right );
    };

    // # Left Denotation -- token that takes an expression on the left

    /** For i++ and i--. */
    LED LeftIncDec = (p,token,left,rbp) -> {
        if (!in( left.token.type, "name","get" )) {
            throw new ParseError("Can't assign to "+left+" ("+left.token+')');
        }
        token.type = "post" + token.type;
        return new CompositeNode(token, left );
    };

    /** index f[x+1]. e.g. f[x] or f[x][y] */
    LED LeftIndex = (p,token,left,unused) -> {
        if (!in( left.token.type, "name", "get" )) {
            throw new ParseError(left.toString()+" can't be indexed");
        }
        Node index = p.parseUntil(0);
        p.eat("]");

        token.type = "get";
        return new CompositeNode(token, left, index );
    };

    /** e.g. a > 1 ? x : y */
    LED LeftTernary = (p,token,left,bp) -> {

        Node trueExpr = p.parseUntil(0);
        // 0 binding power since any operators allowed until ':'.  See:
        //
        // http://en.cppreference.com/w/c/language/operator_precedence#cite_note-2
        //
        // "The expression in the middle of the conditional operator
        // (between ? and * :) is parsed as if parenthesized:
        // its precedence relative to ?: is ignored."

        p.eat(":");
        Node falseExpr = p.parseUntil(bp);
        Node[] children = { left, trueExpr, falseExpr };
        return new CompositeNode(token, children);
    };

    /** Normal binary operator like 1+2 or 2*3, etc. */
    LED LeftBinaryOp = (p,token,left,rbp) -> {
        return new CompositeNode(token, left, p.parseUntil(rbp) );
    };

    /** Normal binary operator like 1+2 or 2*3, etc. e.g. x += 1, or a[i] += 1 */
    LED LeftAssign = (p,token,left,rbp) -> {
        if (!in( left.token.type, "name", "get" )) {
            throw new ParseError("Can't assign to "+left+" ("+left.token+')');
        }
        return new CompositeNode(token, left, p.parseUntil(rbp) );
    };

    /** foo, bar, baz. Could be sequencing operator, or tuple without parens */
    LED LeftComma = (p,token,left,rbp) -> {
        Node r = p.parseUntil(rbp);
        if (left.token.type.equals(",")) { // Keep adding more children
            CompositeNode l = (CompositeNode)left;
            l.children = append(l.children,r);
            return left;
        }
        Node[] children = { left, r };
        return new CompositeNode(token, children);
    };

    // # For overloading of , inside function calls
    static final int COMMA_PREC = 1;


    /** Function call f(a, b). */
    LED LeftFuncCall = (p,token,left,unused) -> {
        Node[] children = { left };
        // f(x) or f[i](x)
        if (!in( left.token.type, "name", "get" )) {
            throw new ParseError(left.toString()+" can't be called");
        }
        while (!p.atToken(")")) {
            // We don't want to grab the comma, e.g. it is NOT a sequence operator.
            // So set the precedence to 5.
            children = append(children, p.parseUntil(COMMA_PREC));
            if (p.atToken(",")) {
                p.next();
            }
        }
        p.eat(")");
        token.type = "call";
        return new CompositeNode(token, children );
    };

    static boolean in(String a, String ... b) {
        for (String c:b) if (a.equals(c)) return true;
        return false;
    }
    static <T> T[] append(T[] a, T c) {
        T[] b = Arrays.copyOf(a,a.length+1);
        b[a.length] = c;
        return b;
    }

    /**
     * Create a parser.
     *
     * Compare the code below with this table of C operator precedence:
     * http://en.cppreference.com/w/c/language/operator_precedence
     */
    ParserSpec makeShellParserSpec() {
        ParserSpec spec = new ParserSpec();

        spec.Left(31, LeftIncDec, "++", "--" );
        spec.Left(31, LeftFuncCall, "(" );
        spec.Left(31, LeftIndex, "[" );

        // 29 -- binds to everything except function call, indexing, postfix ops
        spec.Null(29, NullIncDec, "++", "--" );
        spec.Null(29, NullPrefixOp, "+", "!", "~", "-" );

        // Right associative: 2 ** 3 ** 2 == 2 ** (3 ** 2)
        spec.LeftRightAssoc(27, LeftBinaryOp, "**" );
        spec.Left(25, LeftBinaryOp, "*", "/", "%" );

        spec.Left(23, LeftBinaryOp, "+", "-" );
        spec.Left(21, LeftBinaryOp, "<<", ">>" );
        spec.Left(19, LeftBinaryOp, "<", ">", "<=", ">=" );
        spec.Left(17, LeftBinaryOp, "!=", "==" );

        spec.Left(15, LeftBinaryOp, "&" );
        spec.Left(13, LeftBinaryOp, "^" );
        spec.Left(11, LeftBinaryOp, "|" );
        spec.Left(9, LeftBinaryOp, "&&" );
        spec.Left(7, LeftBinaryOp, "||" );

        spec.LeftRightAssoc(5, LeftTernary, "?" );

        // Right associative: a = b = 2 is a = (b = 2)
        spec.LeftRightAssoc(3, LeftAssign,
                "=",
                "+=", "-=", "*=", "/=", "%=",
                "<<=", ">>=", "&=", "^=", "|=" );

        spec.Left(COMMA_PREC, LeftComma, ",");

        // 0 precedence -- doesn't bind until )
        spec.Null(0, NullParen, "(" );  // # for grouping

        // -1 precedence -- never used
        spec.Null(-1, NullConstant, "name", "number" );
        spec.Null(-1, NullError, ")", "]", ":", "eof" );

        return spec;
    }

    /** Used by tests. */
    Parser makeParser(String s) {
        ParserSpec spec = makeShellParserSpec();
        Supplier<Token> lexer = tokenize(s);
        Parser p = new Parser(spec, lexer);
        return p;
    }

    /** Used by tests. */
    Node parseShell(String s, String expected) {
        Parser p = makeParser(s);
        Node tree = p.parse();

        String sexpr = tree.toString();
        if (expected != null && !sexpr.equals(expected)) {
            throw new ParseError(sexpr+" != "+expected);
        }
        System.out.println(s+" -> "+sexpr); // print('%-40s %s' % (s, sexpr))
        return tree;
    }

    public static void main(String[] argv) {
        if (argv.length != 1) {
            System.out.println("Usage: ArithParse EXPRESSION");
        } else {
            String s = argv[0];
            try {
                Node tree = new ArithParse().parseShell(s,null);
            }
            catch (ParseError e) {
                System.err.println("Error parsing "+s+"; "+e);
            }
        }
    }
}
