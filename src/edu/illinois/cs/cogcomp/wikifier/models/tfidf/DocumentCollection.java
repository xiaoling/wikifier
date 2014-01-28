package edu.illinois.cs.cogcomp.wikifier.models.tfidf;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import edu.illinois.cs.cogcomp.wikifier.utils.datastructure.SortedObjects.SortedWords;
import edu.illinois.cs.cogcomp.wikifier.utils.datastructure.OccurrenceCounter;
import edu.illinois.cs.cogcomp.wikifier.utils.io.InFile;
import edu.illinois.cs.cogcomp.wikifier.utils.io.StopWords;

public class DocumentCollection implements Iterable<Document> {

    public List<Document> docs = new ArrayList<Document>();

    public DocumentCollection() {
    }

    public void addDoc(Document doc) {
        docs.add(doc);
    }

    public void addDocuments(List<Document> _docs) {
        for (int i = 0; i < _docs.size(); i++)
            this.docs.add(_docs.get(i));
    }

    /*
     * This code assumes each line in a file contains a new document
     */
    public void addDocuments(String filename, int classID, StopWords stops) {
        InFile in = new InFile(filename);
        List<String> words = in.readLineTokens();
        if (stops != null)
            words = stops.filterStopWords(words);
        while (words != null) {
            if (words.size() > 0)
                docs.add(new Document(words, classID, stops));
            words = in.readLineTokens();
            if (stops != null)
                words = stops.filterStopWords(words);
        }
    }

    /*
     * This format assumes that the folder contains a bunch of files. each files is a single doc
     */
    public void addFolder(String path, int classID, StopWords stops, boolean discardFirstToken) {
        String[] files = (new File(path)).list();
        for (int i = 0; i < files.length; i++) {
            InFile in = new InFile(path + "/" + files[i]);
            List<String> allWords = new ArrayList<String>();
            List<String> words = in.readLineTokens();
            if ((discardFirstToken) && (words != null) && (words.size() > 0))
                words.remove(0);
            if (stops != null)
                words = stops.filterStopWords(words);
            while (words != null) {
                for (String word : words)
                    allWords.add(word);
                words = in.readLineTokens();
                if ((discardFirstToken) && (words != null) && (words.size() > 0))
                    words.remove(0);
                if (stops != null)
                    words = stops.filterStopWords(words);
            }
            docs.add(new Document(allWords, classID, stops));
        }
    }

    /*
     * 
     * returns the top tf words per class. note that these don't necessarrily have to be good for
     * classification stop words like "a,the etc." can appear
     */
    public SortedWords[] getMostCommonWordsPerClass(int numClasses) {
        OccurrenceCounter[] invIndex = new OccurrenceCounter[numClasses];
        for (int i = 0; i < numClasses; i++)
            invIndex[i] = new OccurrenceCounter();
        for (int i = 0; i < docs.size(); i++) {
            Document d = docs.get(i);
            invIndex[d.classID].addDoc(d);
        }
        SortedWords[] res = new SortedWords[numClasses];
        for (int i = 0; i < numClasses; i++) {
            res[i] = invIndex[i].getCharacteristicWords();
            res[i].sort();
        }
        return res;
    }

    @Override
    public Iterator<Document> iterator() {
        return docs.iterator();
    }
}
