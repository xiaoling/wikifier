package edu.illinois.cs.cogcomp.wikifier.wiki.importing;

import java.io.IOException;

import edu.illinois.cs.cogcomp.wikifier.utils.TTLIterator;
import edu.illinois.cs.cogcomp.wikifier.utils.datastructure.StringMap;
import edu.illinois.cs.cogcomp.wikifier.utils.datastructure.Triple;

public class WikiPageIds extends StringMap<Integer>{

    public void parseFromTTL(String file) throws IOException{
        TTLIterator it = new TTLIterator(file);
        while(it.hasNext()){
            Triple t = it.next();
            put(t.getArg1(),Integer.parseInt(t.getArg2()));
        }
        it.close();
    }

    /**
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        WikiPageIds ids = new WikiPageIds();
        long start = System.currentTimeMillis();
        ids.parseFromTTL("../../dbpedia/page_ids_en.ttl");
        System.out.println(ids.size() + " in " + (System.currentTimeMillis() - start) + "ms");
        System.out.println(((Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())>>20) + "MB");
    }

}
