package tdop;

import java.nio.CharBuffer;

import java.util.Iterator;
import java.util.NoSuchElementException;

import static java.lang.Character.*;
import static tdop.TDOP.*;

// https://commandcenter.blogspot.com/2011/08/regular-expressions-in-lexing-and.html

public class Tokenizer extends Tokens implements Iterator<Token> {

    CharBuffer buf;
    int ch, offset;
    Token token;
    boolean eolIsSignificant;

    @Override
    public boolean hasNext() {
        if (token == null) {
            return nextToken() > -1;
        }
        return token != null;

    }
    @Override
    public Token next() {
        if (hasNext()) {
            Token t = token;
            token = null;
            return t;
        }
        throw new NoSuchElementException();
    }

    public Tokenizer reset(CharBuffer cb) {
        buf = cb;
        token = null;
        if (word == null) words();
        if (operator == null) operators();
        return this;
    }

    public Iterable<Token> wrap(CharBuffer cb) {
        return () -> reset(cb);
    }

    public void eolIsSignificant​(boolean flag) {
        eolIsSignificant = flag;
    }

    // ch = buf[position++];

    int pop() {
        ch = buf.hasRemaining() ? buf.get() : -1;
        return ch;
    }
    void push() {
        if (ch >= 0) skip(-1);
    }
    int peek() {
        int p = buf.position();
        return p < buf.limit() ? buf.get(p) : -1;
    }
    int past() {
        int p = buf.position() - 2;
        return p < 0 ? -1 : buf.get(p);
    }

    //   -2   -1   0 = position
    // past < ch < peek

    void skip(int n) {
        buf.position(buf.position()+n);
    }

    String text() {
        int count = buf.position() - offset;
        return new String(buf.array(),offset,count);
    }

    void makeToken(String type, int nval, String sval) {
        assert(type.length() > 0);
        token = new Token(type,nval,sval);
    }

    int nextToken() {
        if (isWhitespace(pop())) ignore();
        if (ch == -1) return -1;
        if (ch == '\n') return newline();
        // mark start of text
        offset = buf.position() - 1;
        // text spans
        if (isComment(ch,peek())) return comment();
        if (isQuote(ch)) return quoted();
        // text units
        if (isJavaIdentifierStart(ch)) return name();
        if (isDigit(ch)) return number();
        if (isSpecial(ch)) return operator();
        /* else */ return unrecognized();
    }

    void ignore() {
        do { if (ch == '\n' && eolIsSignificant) break; }
        while (isWhitespace(pop()));
    }

    int newline() {
        makeToken(EOL,0,null);
        return 'l';
    }

    int unrecognized() {
        while (!isWhitespace(pop()) && ch != -1) {}
        return unrecognized(text());
    }
    int unrecognized(String sval) {
        makeToken(UNRECOGNIZED,0,sval);
        return '?';
    }

    int name() {
        while (isJavaIdentifierPart(pop())) {}
        push();
        String key = text();
        if (isWord(key)) {
            makeToken(key,0,null);
            return 'w';
        } else {
            makeToken(NAME,0,key);
            return 'n';
        }
    }

    int number() {
        int base = numeric();
        if (base < 0) unrecognized(text());
        makeToken(NUMBER,base,text());
        return 'd';
    }

    int numeric() {
        if (ch == '0') {
            switch (pop()) {
                case 'b': case 'B': return base2();
                case 'x': case 'X': return base16();
                default: push();
            }
        }
        return base10();
    }

    int base2() {
        if (head()) return -1; // 0b_nnn
        while (isBit(pop())) {}
        return tail('l','L') ? -1 : 'b'; // nnn_ or nnn_[l|L]
    }

    int base16() {
        if (head()) return -1; // 0x_nnn
        while (isHex(pop())) {}
        return tail('l','L') ? -1 : 'x'; // nnn_ or nnn_[l|L]
    }

    int base10() {
        digits();
        switch (ch) {
            case '.': return nDot();
            case 'e': case 'E': return nExp();
            case 'f': case 'F': return nFloat();
            case 'l': case 'L': return nFixed();
        }
        return tail() ? -1 : 'i';
    }

    int nDot() {
        if (middle()) return -1; // nn_.nnn or nn._nnn
        digits();
        switch (ch) {
            case 'e': case 'E': return nExp();
            case 'f': case 'F': return nFloat();
        }
        return tail() ? -1 : 'd';
    }
    int nExp() {
        if (middle()) return -1; // nn_Enn or nnE_nnn
        sign();  // nnE+nn or nnE-nn
        digits();
        switch (ch) {
            case 'f': case 'F': return nFloat();
        }
        return tail() ? -1 : 'p';
    }
    int nFloat() {
        return tail('f','F') ? -1 : 'f';
    }
    int nFixed() {
        return tail('l','L') ? -1 : 'l';
    }

    void sign() {
        int e = peek();
        if (e == '+' || e =='-') skip(1);
    }
    void digits() {
        while (pop() == '_' || isDigit(ch)) {}
    }

    static boolean isBit(int b) {
        return b == '_' || b == '0' || b == '1';
    }
    static boolean isHex(int x) {
        return x == '_'  ? true
             : x < '0'   ? false // 0
             : x < '9'+1 ? true  // 9+1
             : x < 'A'   ? false // A
             : x < 'F'+1 ? true  // F+1
             : x < 'a'   ? false // a
             : x < 'f'+1 ;       // f+1
    }

    boolean head() {
        return peek() == '_';
    }
    boolean middle() {
        return past() == '_' || peek() == '_';
    }
    boolean tail(char lc, char uc) {
        if (ch == lc || ch == uc) { // nnn[l|L|f|F]
            if (past() == '_') return true;
            ch = pop();
        }
        return tail();
    }
    boolean tail() {
        if (ch != '_') { // nnn_
            if (isWhitespace(ch) || isSpecial(ch)) { // nnn[\w|{special}]
                push(); // set position to break character
                return false;
            }
        }
        return true;
    }

    int operator() {
        long op = 0;
        do {
            op = (op << 8) | (ch & 0x0ff);
            if (isOperator(op)) continue;
            op >>>= 8;
            break;
        }
        while (isSpecial(pop()));
        push();
        if (op == 0) return unrecognized(text());
        makeToken(text(),(int)op,null);
        return 'o';
    }

    static boolean isComment(int x, int y) {
        return (x == '/' && (y == '/' || y == '*'));
    }

    int comment() {
        int t = -1;
        if (ch == '/') {
            switch (pop()) {
                case '/': t = slashComment(); break;
                case '*': t = starComment(); break;
                default:  push(); break;
            }
        }
        if (t < 0) return unrecognized(text());
        makeToken(COMMENT,t,text());
        return 'c';
    }

    int slashComment() {
        while (pop() != -1) {
            if (ch == '\n') {
                break;
            }
        }
        return '/';
    }

    int starComment() {
        while (pop() != -1) {
            if (ch == '*') {
                if (peek() == '/') {
                    skip(1);
                    return '*';
                }
            }
        }
        return -1;
    }

    static boolean isQuote(int b) {
        return b == '"' || b == '\'';
    }

    int quoted() {
        int q = ch;
        while (pop() != -1) {
            if (ch == q) break;
            if (ch == '\n') break;
            if (ch == '\\') skip(1);
        }
        if (ch != q) return unrecognized(text());
        makeToken(STRING,q,text());
        return 'q';
    }

}
