package demo;

import java.util.Arrays;

import static tdop.Parser.ParseError;
import static tdop.TDOP.*;

class Arith {

    Parser<SimpleNode> parser;

    Arith() {
        parser = newParser();
        makeShellParser();
    }

    class SimpleNode implements Node {
        String type, key, val;

        SimpleNode(String t, Token k) {
            type=t; key=k.type; val=k.text;
        }
        SimpleNode(Token k) {
            this(k.type,k);
        }
        @Override
        public String toString() {
            return val != null ? val : type;
        }
    }

    class CompositeNode extends SimpleNode {
        SimpleNode[] children;

        CompositeNode(String t, Token k, SimpleNode... c) {
            super(t,k); children=c;
        }
        CompositeNode(Token k, SimpleNode... c) {
            this(k.type,k,c);
        }
        @Override
        public String toString() {
            String s = "(" + type;
            for (SimpleNode n:children) s += " "+n.toString();
            return s + ')';
        }
    }

    static final int COMMA_PREC = 1;

    final void makeShellParser() {
      parser.spec()
        .Left(31, LeftIncDec, "++", "--" )
        .Left(31, LeftFuncCall, "(" )
        .Left(31, LeftIndex, "[" )

        .Null(29, NullIncDec, "++", "--" )
        .Null(29, NullPrefixOp, "+", "!", "~", "-" )

        .Right(27, LeftBinaryOp, "**" )
        .Left(25, LeftBinaryOp, "*", "/", "%" )

        .Left(23, LeftBinaryOp, "+", "-" )
        .Left(21, LeftBinaryOp, "<<", ">>" )
        .Left(19, LeftBinaryOp, "<", ">", "<=", ">=" )
        .Left(17, LeftBinaryOp, "!=", "==" )

        .Left(15, LeftBinaryOp, "&" )
        .Left(13, LeftBinaryOp, "^" )
        .Left(11, LeftBinaryOp, "|" )
        .Left(9, LeftBinaryOp, "&&" )
        .Left(7, LeftBinaryOp, "||" )

        .Right(5, LeftTernary, "?" )

        .Right(3, LeftAssign,
                  "=",
                  "+=", "-=", "*=", "/=", "%=",
                  "<<=", ">>=", "&=", "^=", "|=" )

        .Left(COMMA_PREC, LeftComma, ",")

        .Null(0, NullParen, "(" )

        .Null(-1, NullConstant, NAME, NUMBER, STRING )
        .Null(-1, NullError, ")", "]", ":", EOF );
    }

    NUD<SimpleNode> NullConstant = (p,token,bp) -> {
        String type = token.type.equals(NAME) ? "name" : token.text;
        return new SimpleNode(type,token);
    };

    NUD<SimpleNode> NullParen = (p,token,bp) -> {
        SimpleNode r = p.parseUntil(bp);
        p.expect(")");
        return r;
    };

    NUD<SimpleNode> NullPrefixOp = (p,token,bp) -> {
        SimpleNode r = p.parseUntil(bp);
        return new CompositeNode(token,r);
    };

    NUD<SimpleNode> NullIncDec = (p,token,bp) -> {
        SimpleNode right = p.parseUntil(bp);
        if (!in( right.type, "name", "get" )) {
            throw new ParseError("Can't assign to "+right+" ("+token(right)+')');
        }
        return new CompositeNode(token,right);
    };

    LED<SimpleNode> LeftIncDec = (p,token,left,rbp) -> {
        if (!in( left.type, "name", "get" )) {
            throw new ParseError("Can't assign to "+left+" ("+token(left)+')');
        }
        return new CompositeNode("post"+token.type,token,left);
    };

    LED<SimpleNode> LeftIndex = (p,token,left,unused) -> {
        if (!in( left.type, "name", "get" )) {
            throw new ParseError(left.toString()+" can't be indexed");
        }
        SimpleNode index = p.parseUntil(0);
        p.expect("]");
        return new CompositeNode("get",token,left,index);
    };

    LED<SimpleNode> LeftTernary = (p,token,left,bp) -> {
        SimpleNode trueExpr = p.parseUntil(0);
        p.expect(":");
        SimpleNode falseExpr = p.parseUntil(bp);
        SimpleNode[] children = { left, trueExpr, falseExpr };
        return new CompositeNode(token,children);
    };

    LED<SimpleNode> LeftBinaryOp = (p,token,left,rbp) -> {
        return new CompositeNode(token,left,p.parseUntil(rbp));
    };

    LED<SimpleNode> LeftAssign = (p,token,left,rbp) -> {
        if (!in( left.type, "name", "get" )) {
            throw new ParseError("Can't assign to "+left+" ("+token(left)+')');
        }
        return new CompositeNode(token,left,p.parseUntil(rbp));
    };

    LED<SimpleNode> LeftComma = (p,token,left,rbp) -> {
        SimpleNode r = p.parseUntil(rbp);
        if (left.type.equals(",")) {
            CompositeNode l = (CompositeNode)left;
            l.children = append(l.children,r);
            return left;
        }
        SimpleNode[] children = { left, r };
        return new CompositeNode(token,children);
    };

    LED<SimpleNode> LeftFuncCall = (p,token,left,unused) -> {
        SimpleNode[] children = { left };
        if (!in( left.type, "name", "get" )) {
            throw new ParseError(left.toString()+" can't be called");
        }
        while (!p.atToken(")")) {
            children = append(children, p.parseUntil(COMMA_PREC));
            if (p.atToken(",")) {
                p.nextToken();
            }
        }
        p.expect(")");
        return new CompositeNode("call",token,children);
    };

    NUD<SimpleNode> NullError = (p,t,bp) -> {
        throw new ParseError(token(t)+" can't be used in prefix position");
    };

    boolean in(String a, String ... b) {
        for (String c:b) if (a.equals(c)) return true;
        return false;
    }

    <T> T[] append(T[] a, T c) {
        T[] b = Arrays.copyOf(a,a.length+1);
        b[a.length] = c;
        return b;
    }

    String token(SimpleNode n) {
        return "<Token "+n.key+' '+n.val+'>';
    }
    String token(Token t) {
        return "<Token "+t.type+' '+t.text+'>';
    }

}
