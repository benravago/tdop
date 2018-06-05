package io;

import java.io.IOException;
import java.io.UncheckedIOException;

import java.nio.file.Path;
import java.nio.CharBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;

public interface Chars {

    static CharBuffer chars(char[] array) {
        return CharBuffer.wrap(array);
    }

    static CharBuffer chars(char[] array, int offset, int length) {
        return CharBuffer.wrap(array,offset,length);
    }

    static CharBuffer chars(String src) {
        return CharBuffer.wrap(src.toCharArray());
    }

    static CharBuffer chars(StringBuilder src) {
        int srcEnd = src.length();
        char[] dst = new char[srcEnd];
        src.getChars(0,srcEnd,dst,0);
        return CharBuffer.wrap(dst);
    }

    static CharBuffer chars(Path path) {
        try {
            byte[] bytes = Files.readAllBytes(path);
            return chars(new String(bytes));
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static CharBuffer load(String filename) {
        return chars(Paths.get(filename));
    }

}
