package edu.illinois.cs.cogcomp.wikifier.utils.io;

import java.util.*;

public class StopWords {
    private Set<String> stopWords = new HashSet<String>();

    public StopWords(String filename) {
        InFile in = new InFile(filename);
        List<String> words = in.readLineTokens();
        while (words != null) {
            for (String word : words) {
                stopWords.add(word.toLowerCase());
            }
            words = in.readLineTokens();
        }
    }

    public boolean isStopword(String s) {
        return stopWords.contains(s);
    }

    public List<String> filterStopWords(Iterable<String> words) {
        if (words == null)
            return null;
        List<String> res = new ArrayList<String>();
        for (String word : words)
            if (!isStopword(word.toLowerCase()))
                res.add(word);
        return res;
    }
}
