package edu.illinois.cs.cogcomp.wikifier.utils.datastructure;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import edu.illinois.cs.cogcomp.wikifier.models.tfidf.Document;
import edu.illinois.cs.cogcomp.wikifier.models.tfidf.DocumentCollection;
import edu.illinois.cs.cogcomp.wikifier.models.tfidf.FeatureMap;
import edu.illinois.cs.cogcomp.wikifier.utils.datastructure.SortedObjects.SortedWords;


public class OccurrenceCounter implements Iterable<String> {
    public Map<String, Integer> counts = new StringMap<Integer>();//<String, Integer>();
    public int uniqueTokens = 0;
    public int totalTokens = 0;
    public static int numCharacterisitcWords = 50;

    public void addDocCollection(DocumentCollection docs) {
        for (Document doc : docs)
            addDoc(doc);
    }

    public void addDoc(Document doc) {
        for (String word : doc.words)
            addToken(word);
    }

    public void addToken(String s) {
        totalTokens++;
        if (counts.containsKey(s)) {
            int i = counts.get(s).intValue();
            counts.put(s, i + 1);
        } else {
            uniqueTokens++;
            counts.put(s, 1);
        }
    }

    public void addToken(String s, int weight) {
        totalTokens += weight;
        if (counts.containsKey(s)) {
            int i = counts.get(s).intValue();
            counts.put(s, i + weight);
        } else {
            uniqueTokens++;
            counts.put(s, weight);
        }
    }

    public int getCount(String s) {
        if (counts.containsKey(s))
            return counts.get(s).intValue();
        return 0;
    }

    public Iterator<String> getTokensIterator() {
        return counts.keySet().iterator();
    }

    public int[] getActiveFeatures(FeatureMap map) {
        List<Integer> v = new ArrayList<Integer>(uniqueTokens);
        for (Iterator<String> i = getTokensIterator(); i.hasNext();) {
            String s = i.next();
            if (map.wordToFid.containsKey(s))
                v.add(map.wordToFid.get(s));
        }
        int[] res = new int[v.size()];
        for (int i = 0; i < res.length; i++)
            res[i] = v.get(i).intValue();
        return res;
    }

    public SortedWords getCharacteristicWords() {
//        CharacteristicWords res = new CharacteristicWords(counts);
//        for (Iterator<String> i = getTokensIterator(); i.hasNext();) {
//            String s = i.next();
//            res.add(s, getCount(s));
//        }
        return new SortedWords(counts);
    }

    @Override
    public Iterator<String> iterator() {
        return counts.keySet().iterator();
    }

}
