package edu.illinois.cs.cogcomp.wikifier.wiki.importing;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import edu.illinois.cs.cogcomp.wikifier.utils.TTLIterator;
import edu.illinois.cs.cogcomp.wikifier.utils.WikiTitleUtils;
import edu.illinois.cs.cogcomp.wikifier.utils.datastructure.Triple;
import edu.illinois.cs.cogcomp.wikifier.utils.io.Serializer;

/**
 * 
 * @author cheng88
 *
 */
public class DisambiguationPages extends HashMap<String,List<String>>{

    private static final String SERIALIZED_FILE_NAME = "disambiguationPages.serialized";
    /**
     * 
     */
    private static final long serialVersionUID = -4023951551572184084L;
    
    private HashSet<String> originalDisambiguationPages;

    public DisambiguationPages(String path) throws IOException{
        TTLIterator iterator = new TTLIterator(path);

        originalDisambiguationPages = new HashSet<String>();
        while(iterator.hasNext()){
            Triple t = iterator.next();
            String title = t.getArg1();
            originalDisambiguationPages.add(title);
            List<String> list = getCandidates(title);
            list.add(t.getArg2());
        }
        iterator.close();
    }
    
    public List<String> get(Object key){
        List<String> ret = super.get(key);
        return ret == null ? new ArrayList<String>() : ret;
    }
    
    
    /**
     * Maps both title and its canonical form to the same list
     * @param title
     * @return the list of titles that this title maps to currently
     */
    private List<String> getCandidates(String title){
        List<String> ret = super.get(title);
        if(ret != null)
            return ret;
        String canonicalTitle = WikiTitleUtils.getCanonicalTitle(title);
        ret = super.get(canonicalTitle);
        if(ret != null){
            put(title,ret);
            return ret;
        }
        ret = new ArrayList<>();
        put(title,ret);
        put(canonicalTitle,ret);
        return ret;
    }

    public static DisambiguationPages load(String indexDir){
        return (DisambiguationPages)Serializer.read(new File(indexDir,SERIALIZED_FILE_NAME));
    }
    
    public boolean isDisambiguationPage(String title){
        if(title == null)
            return false;
        return title.endsWith("(disambiguation)") || originalDisambiguationPages.contains(title);
    }
    
    public static void dump(String ttl,String outfile) throws IOException{
        DisambiguationPages pages = new DisambiguationPages(ttl);
        System.out.println("Dumped disambiguation entries of size "+pages.size());
        Serializer.write(pages, new File(outfile,SERIALIZED_FILE_NAME));
    }
    
    /**
     * @param args
     * @throws IOException 
     */
    public static void main(String[] args) throws IOException {
//        dump("../../dbpedia/disambiguations_en.ttl", "data/Lucene4Index");
        System.out.println(load("data/Lucene4Index").get("Socialist_Party_(disambiguation)"));
        System.out.println(load("data/Lucene4Index").get("UK"));
        System.out.println(load("data/Lucene4Index").isDisambiguationPage("Oklahoma"));
    }

}
