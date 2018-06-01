package pp;

import java.util.Map;
import java.util.HashMap;

import java.util.function.Supplier;
import java.util.NoSuchElementException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** tdop.py */
public class TDOP {

    interface LED { // left denotation
        Node led(Parser p, Token t, Node node, int rbp);
    }
    interface NUD { // null denotation
        Node nud(Parser p, Token t, int bp);
    }

    static class ParseError extends RuntimeException {
        ParseError(String msg) { super(msg); }
    }

    // # Default parsing functions give errors

    static NUD NullError = (p,token,bp) -> {
        throw new ParseError(token.toString()+" can't be used in prefix position");
    };

    static LED LeftError = (p,token,lbp,rbp) -> {
        throw new ParseError(token.toString()+" can't be used in infix position");
    };  // Hm is this not called because of binding power?

    // # Input

    static class Token {
        String type;
        String val;

        Token(String type, String val) {
            this.type = type;
            this.val = val;
        }
        Token(String s) { this(s,s); }

        @Override
        public String toString() {
            return "<Token "+type+' '+val+'>';
        }
    }

    // # Using the pattern here: http://effbot.org/zone/xml-scanner.htm

    static Pattern tokenRE = Pattern.compile(
        "\\s*" +
         "(?:" + "(\\d+)" // 1
         + '|' + "(\\w+)" // 2
         + '|' + "([\\-\\+\\*/%!~<>=&^|?:,]+)" // 3
         + '|' + "([\\(\\)\\[\\]])"  // 4
         + ')'
    );
    // NOTE: () and [] need to be on their own so (-1+2) works

    static Supplier<Token> tokenize(String s){
      return new Supplier<>() {
        Matcher m = tokenRE.matcher(s);

        @Override
        public Token get() {
            if (m.find()) {
                if (m.group(1) != null)
                    return new Token("number",m.group(1));
                else if (m.group(2) != null)
                    return new Token("name",m.group(2));
                else if (m.group(3) != null)
                    return new Token(m.group(3));
                else if (m.group(4) != null)
                    return new Token(m.group(4));
            }
            throw new NoSuchElementException();
        }
      };
    }

    // # Simple and Composite AST nodes

    /*
     * Args:
     *   type: token type (operator, etc.)
     *   val: token val, only important for number and string
     */
    static class Node {
        Token token;

        Node(Token token) {
            this.token = token;
        }
        @Override
        public String toString() {
            return token.val;
        }
    }

    /*
     * Args:
     *   type: token type (operator, etc.)
     */
    static class CompositeNode extends Node {
        Node[] children;

        CompositeNode(Token token, Node... children) {
            super(token);
            this.children = children;
        }
        @Override
        public String toString() {
            String s = "(" + token.type;
            for (Node n:children) s += " "+n.toString();
            return s + ')';
        }
    }

    // # Parser definition

    /** Row for operator. */
    static class LeftInfo {
        LED fn;
        int lbp, rbp;

        LeftInfo(LED led, int lbp, int rbp) {
            this.fn = (led != null) ? led : LeftError;
            this.lbp = lbp;
            this.rbp = rbp;
        }
    }

    /** Row for operator. */
    static class NullInfo {
        NUD fn;
        int bp;

        NullInfo(NUD nud, int bp) {
            this.fn = (nud != null) ? nud : NullError;
            this.bp = bp;
        }
    }

    /** Specification for a TDOP parser. */
    static class ParserSpec {
        Map<String,NullInfo> nullLookup;
        Map<String,LeftInfo> leftLookup;

        ParserSpec() {
            nullLookup = new HashMap<>();
            leftLookup = new HashMap<>();
        }

        /**
         * Register a token that doesn't take anything on the left.
         *
         * Examples: constant, prefix operator, error.
         */
        void Null(int bp, NUD nud, String...tokens) {
            for (String token : tokens) {
                nullLookup.put(token, new NullInfo(nud,bp));
                if (!leftLookup.containsKey(token)) {
                    leftLookup.put(token,new LeftInfo(null,0,0)); // error
                }
            }
        }

        void registerLed(int lbp, int rbp, LED led, String... tokens) {
            for (String token : tokens) {
                if (!nullLookup.containsKey(token)) {
                    nullLookup.put(token, new NullInfo(null,0));
                }
                leftLookup.put(token, new LeftInfo(led,lbp,rbp));
            }
        }

        /** Register a token that takes an expression on the left. */
        void Left(int bp, LED led, String... tokens) {
            registerLed(bp, bp, led, tokens);
        }

        /** Register a right associative operator. */
        void LeftRightAssoc(int bp, LED led, String... tokens) {
            registerLed(bp, bp-1, led, tokens);
        }

        /** Get the parsing function and precedence for a null position token. */
        NullInfo lookupNull(String token) {
            NullInfo nud = nullLookup.get(token);
            if (nud == null) {
                throw new ParseError("Unexpected token "+token);
            }
            return nud;
        }

        /** Get the parsing function and precedence for a left position token. */
        LeftInfo lookupLeft(String token) {
            LeftInfo led = leftLookup.get(token);
            if (led == null) {
                throw new ParseError("Unexpected token "+token);
            }
            return led;
        }
    }

    static Token EOF_TOKEN = new Token("eof","eof");

    /** Recursive TDOP parser. */
    static class Parser {

        ParserSpec spec;
        Supplier<Token> lexer;
        Token token;

        Parser(ParserSpec spec, Supplier<Token> lexer) {
            this.spec = spec;
            this.lexer = lexer; // iterable
            this.token = null; // current token
        }

        /** Test if we are looking at a token.  */
        boolean atToken(String tokenType) {
            return token.type.equals(tokenType);
        }

        /** Move to the next token. */
        void next() {
            try {
                token = lexer.get();
            }
            catch (NoSuchElementException e) { // StopIteration
                token = EOF_TOKEN;
            }
        }

        /** Assert the value of the current token, then move to the next token. */
        void eat(String val) {
            if (val != null && ! atToken(val)) {
                throw new ParseError("expected "+val+", got "+token);
            }
            next();
        }

        /**
         * Parse to the right, eating tokens until we encounter a token
         * with binding power LESS THAN OR EQUAL TO rbp.
         */
        Node parseUntil(int rbp) {
            if (atToken("eof")) {
                throw new ParseError("Unexpected end of input");
            }
            Token t = token;
            next(); // skip over the token, e.g. ! ~ + -

            NullInfo nullInfo = spec.lookupNull(t.type);
            Node node = nullInfo.fn.nud(this,t, nullInfo.bp);

            while (true) {
                t = token;
                LeftInfo leftInfo = spec.lookupLeft(t.type);

                // Examples:
                //   If we see 1*2+  , rbp = 27 and lbp = 25, so stop.
                //   If we see 1+2+  , rbp = 25 and lbp = 25, so stop.
                //   If we see 1**2**, rbp = 26 and lbp = 27, so keep going.
                if (rbp >= leftInfo.lbp) {
                    break;
                }
                next(); // skip over the token, e.g. / *

                node = leftInfo.fn.led(this,t, node, leftInfo.rbp);
            }
            return node;
        }

        Node parse() {
            next();
            return parseUntil(0);
        }
    }
}