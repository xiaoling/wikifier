package edu.illinois.cs.cogcomp.wikifier.utils.lucene;

import java.io.IOException;
import java.util.Iterator;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;

public class LuceneDocIterator implements Iterator<Document> {
    
    private IndexReader reader;
    private int pointer;
    private int max;

    public LuceneDocIterator(IndexReader reader) {
        this.reader = reader;
        pointer = 0;
        max = reader.numDocs();
    }

    @Override
    public boolean hasNext() {
        return pointer < max;
    }

    @Override
    public Document next() {
        Document doc = null;
        try {
            doc = reader.document(pointer);
        } catch (IOException e) {
            e.printStackTrace();
        }
        pointer++;
        return doc;
    }

    @Override
    public void remove() {

    }
}
