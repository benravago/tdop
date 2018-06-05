package tdop;

import java.nio.CharBuffer;

public interface TDOP {

    static <N extends Node> Parser<N> newParser() {
        return new tdop.Parser();
    }

    interface Parser<N extends Node> {
        /**
         * Test if we are looking at a token.
         */
        boolean atToken(String type);
        /**
         * Move to the next token.
         */
        void nextToken();
        /**
         * Assert the type of the current token, then move to the next token.
         */
        void expect(String type);
        /**
         * Parse to the right, eating tokens until we encounter a token
         * with binding power LESS THAN OR EQUAL TO rbp.
         */
        N parseUntil(int rbp);
        /**
         * Initial entry point.
         */
        N parse(CharBuffer src);
        /**
         * Return the Parser's specification instance.
         */
        Spec<N> spec();
    }

    /**
     * Specification for a TDOP parser.
     */
    interface Spec<N extends Node> {
        /**
         * Register a token that doesn't take anything on the left.
         * Examples: constant, prefix operator, error.
         */
        Spec<N> Null(int bp, NUD<N> nud, String... tokens);
        /**
         * Register a token that takes an expression on the left.
         * Examples: infix operator, postfix operator,
         *   the ternary operator b ? 0 : 1, array indexing a[0].
         */
        Spec<N> Left(int bp, LED<N> led, String... tokens);
        /**
         * Register a right associative operator.
         * Examples: exponentiation, assignment, ternary operator.
         */
        Spec<N> Right(int bp, LED<N> led, String... tokens);
    }

    /**
     *  A tag interface for nud/led functions.
     */
    interface Node {}

    /**
     *  A "left denotation" function.
     */
    interface LED<N extends Node> {
        N led(Parser<N> parser, Token token, N node, int rbp);
    }

    /**
     *  A "null denotation" function.
     */
    interface NUD<N extends Node> {
         N nud(Parser<N> parser, Token token, int bp);
    }

    /**
     *  Parser input (from Lexer).
     */
    static class Token {
        Token(String t, int n, String s) {
            type=t; text=s; info=n;
        }
        public final String type;
        public final String text;
        public final int info;
    }

    // Special Token.type values

    static final String EOL     = "l\u0000\uFFFF";
    static final String EOF     = "f\u0000\uFFFF";
    static final String NAME    = "w\u0000\uFFFF";
    static final String NUMBER  = "d\u0000\uFFFF";
    static final String STRING  = "s\u0000\uFFFF";
    static final String COMMENT = "c\u0000\uFFFF";

    static final String UNRECOGNIZED = "?\u0000\uFFFF";

    static String type(String t) {
        switch (t) {
            case EOL: return "EOL";
            case EOF: return "EOF";
            case NAME: return "NAME";
            case NUMBER: return "NUMBER";
            case STRING: return "STRING";
            case COMMENT: return "COMMENT";
        }
        if (t.length() > 1 && t.charAt(1) == '\u0000') {
            return "UNRECOGNIZED"+t.charAt(0);
        }
        return t;
    }
}
