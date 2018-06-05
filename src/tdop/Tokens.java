package tdop;

import java.util.Arrays;

import java.util.Set;
import java.util.HashSet;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

class Tokens {

    Matcher punct = Pattern.compile("[\\p{Punct}]+").matcher("");
    Matcher alpha = Pattern.compile("[\\p{Alpha}]+").matcher("");

    Set<String> words = new HashSet<>();
    Set<String> operators = new HashSet<>();

    void word(String ... a) {
        for (String s:a) {
            if (punct.reset(s).matches()) operators.add(s);
            else if (alpha.reset(s).matches()) words.add(s);
        }
    }

    String[] word;
    long[] operator;
    byte[] special = new byte[128];

    void words() {
        word = words.toArray(new String[words.size()]);
        Arrays.sort(word);
    }

    void operators() {
        int i = 0;
        operator = new long[operators.size()];
        for (String s : operators) {
            operator[i++] = ctoi(s);
        }
        Arrays.sort(operator);
    }

    long ctoi(String s) {
        int n = s.length();
        assert (1 <= n && n <= 8);
        long key = 0;
        for (int i = 0; i < n; i++) {
            int b = s.charAt(i);
            special[b] = (byte)b;
            key = (key << 8) | (b & 0x0ff);
        }
        return key;
    }

    boolean isWord(String key) {
        return Arrays.binarySearch(word,key) >= 0;
    }
    boolean isOperator(long key) {
        return Arrays.binarySearch(operator,key) >= 0;
    }
    boolean isSpecial(int c) {
        return 0 < c && c < 128 && special[c] != 0;
    }

}
