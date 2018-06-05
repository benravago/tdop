package tdop;

import java.util.Map;
import java.util.HashMap;

import java.nio.CharBuffer;

import static tdop.TDOP.*;

public class Parser implements TDOP.Parser {

    class Spec implements TDOP.Spec {

        Map<String,NullInfo> nullLookup = new HashMap<>();
        Map<String,LeftInfo> leftLookup = new HashMap<>();

        class LeftInfo {
            LeftInfo(LED f, int l, int r) {
                fn=f; lbp=l; rbp=r;
            }
            final LED fn;
            final int lbp, rbp;
        }
        class NullInfo {
            NullInfo(NUD f, int b) {
                fn=f; bp=b;
            }
            final NUD fn;
            final int bp;
        }

        @Override
        public Spec Null(int bp, NUD nud, String... tokens) {
            if (nud == null) nud = NullError;
            for (String key : tokens) {
                nullLookup.put(key, new NullInfo(nud,bp));
                if (!leftLookup.containsKey(key)) {
                    leftLookup.put(key,new LeftInfo(LeftError,0,0));
                }
                lexer.word(key);
            }
            return this;
        }

        private Spec Left(int lbp, int rbp, LED led, String... tokens) {
            if (led == null) led = LeftError;
            for (String key : tokens) {
                if (!nullLookup.containsKey(key)) {
                    nullLookup.put(key, new NullInfo(NullError,0));
                }
                leftLookup.put(key, new LeftInfo(led,lbp,rbp));
                lexer.word(key);
            }
            return this;
        }

        @Override
        public Spec Left(int bp, LED led, String... tokens) {
            return Left(bp,bp,led,tokens);
        }
        @Override
        public Spec Right(int bp, LED led, String... tokens) {
            return Left(bp,bp-1,led,tokens);
        }

        /** Get the parsing function and precedence for a null position token. */
        NullInfo lookupNull(String key) {
            NullInfo nud = nullLookup.get(key);
            if (nud == null) {
                throw new ParseError("Unexpected token "+type(key));
            }
            return nud;
        }

        /** Get the parsing function and precedence for a left position token. */
        LeftInfo lookupLeft(String key) {
            LeftInfo led = leftLookup.get(key);
            if (led == null) {
                throw new ParseError("Unexpected token "+type(key));
            }
            return led;
        }

        NUD NullError = (p,t,b) -> {
            throw new ParseError(str(t)+" can't be used in prefix position");
        };
        LED LeftError = (p,t,n,b) -> {
            throw new ParseError(str(t)+" can't be used in infix position");
        };
    }  // Spec

    public static class ParseError extends RuntimeException {
        public ParseError(String msg) { super(msg); }
    }

    Spec spec = new Spec();
    Tokenizer lexer = new Tokenizer();
    Token token;

    @Override
    public TDOP.Spec spec() {
        return spec;
    }

    @Override
    public boolean atToken(String tokenType) {
        return token.type.equals(tokenType);
    }

    @Override
    public void nextToken() {
        token = lexer.hasNext() ? lexer.next() : new Token(EOF,0,null);
    }

    @Override
    public void expect(String val) { // eat()
        if (atToken(val)) {
            nextToken();
        } else {
            throw new ParseError("expected "+val+", got "+str(token));
        }
    }

    @Override
    public Node parseUntil(int rbp) {
        if (atToken(EOF)) {
            throw new ParseError("Unexpected end of input");
        }

        Token t = token;
        nextToken(); // skip over the token, e.g. ! ~ + -

        Spec.NullInfo nullInfo = spec.lookupNull(t.type);
        Node node = nullInfo.fn.nud(this,t,nullInfo.bp);
        for (;;) {
            t = token;
            Spec.LeftInfo leftInfo = spec.lookupLeft(t.type);
            if (rbp >= leftInfo.lbp) {
                break;
            }
            nextToken(); // skip over the token, e.g. / *
            node = leftInfo.fn.led(this,t,node,leftInfo.rbp);
        }
        return node;
    }

    @Override
    public Node parse(CharBuffer src)  {
        lexer.reset(src);
        nextToken();
        return parseUntil(0);
    }

    public static String str(Token t) {
        return "Token("+type(t.type)+','+t.info+','+t.text+')';
    }

}
