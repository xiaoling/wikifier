package edu.illinois.cs.cogcomp.wikifier.utils;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.Iterator;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.apache.commons.lang3.StringUtils;

import edu.illinois.cs.cogcomp.wikifier.utils.TTLIterator.TTLParser.Format;
import edu.illinois.cs.cogcomp.wikifier.utils.datastructure.Triple;


/**
 * This classes parses the Turtle Triple Language files
 * by providing an interator interface over the 
 * triples.
 * @author cheng88
 *
 */
public class TTLIterator implements Iterator<Triple>,Closeable {

    private LineIterator iterator;
    private Triple current = null;
    private TTLParser parser = new TTLParser(Format.DBPEDIA);

    public TTLIterator(String filename) throws IOException {
        iterator = FileUtils.lineIterator(new File(filename), "UTF-8");
    }

    @Override
    public boolean hasNext() {
        if (current != null)
            return true;
        while (iterator.hasNext()) {
            String line = iterator.next();
            if (line.startsWith("#"))
                continue;
            try {
                current = parser.parse(line);
                return true;
            } catch (ParseException e) {
                e.printStackTrace();
                return false;
            }
        }
        return false;
    }

    @Override
    public Triple next() {
        if (!hasNext())
            return null;
        Triple ret = current;
        current = null;
        return ret;
    }

    @Override
    public void remove() {

    }

    @Override
    public void close() throws IOException {
        iterator.close();
    }
    

    public static void test(String file) throws IOException {
        LineIterator iterator = FileUtils.lineIterator(new File(file), "UTF-8");
        TTLParser parser = new TTLParser(Format.DBPEDIA);
        int count = 0;
        while (iterator.hasNext()) {
            String line = iterator.next();
            if (line.startsWith("#"))
                continue;
            Triple triple = null;
            try {
                triple = parser.parse(line);
            } catch (ParseException e) {
                System.out.println("Malformed line " + line);
                e.printStackTrace();
            }
            if(Math.random()<0.0001)
                System.out.println(triple);
            count++;
            if (count % 1000000 == 0)
                System.out.println("Parsed " + count / 1000000 + " million lines");
        }
        iterator.close();
    }

    public static void main(String[] args) throws Exception {
        test("../Data/DBpedia/cleanCombined.ttl");
    }

    static class TTLParser {

        public static enum Format {
            NONE, DBPEDIA
        }

        private final Format format;

        public TTLParser(Format format) {
            this.format = format;
        }

        public TTLParser() {
            this(Format.NONE);
        }

        public Triple parse(String line) throws ParseException {
            String[] parts = StringUtils.split(line, ' ');
            if (parts.length != 4 || !".".equals(parts[3]))
                throw new ParseException("Unrecognized TTL file format", 0);
            return new Triple(normalize(parts[0]), normalize(parts[1]), normalize(parts[2]));
        }

        private String normalize(String str) {

            switch (format) {
            case DBPEDIA:
                if(str.contains("<http://www.w3.org/2001/XMLSchema#integer>"))
                    return StringUtils.substringBetween(str, "\"", "\"^^");
                String URI = StringUtils.substringAfterLast(str, "/");
                return StringUtils.substringBeforeLast(URI, ">");
            default:
                break;
            }
            return str;
        }
    }

}
