/**
 * 
 */
package edu.illinois.cs.cogcomp.wikifier.utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.didion.jwnl.JWNLException;
import net.didion.jwnl.data.POS;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.WordUtils;

import edu.illinois.cs.cogcomp.edison.utilities.WordNetHelper;
import edu.illinois.cs.cogcomp.wikifier.common.GlobalParameters;
import edu.illinois.cs.cogcomp.wikifier.common.LevWordNetManager;
import edu.illinois.cs.cogcomp.wikifier.utils.datastructure.LRUCache;
import edu.smu.tspell.wordnet.AdjectiveSynset;
import edu.smu.tspell.wordnet.NounSynset;
import edu.smu.tspell.wordnet.Synset;
import edu.smu.tspell.wordnet.SynsetType;
import edu.smu.tspell.wordnet.WordNetDatabase;
import edu.smu.tspell.wordnet.WordSense;

/**
 * @author Xiao Cheng
 * 
 */
public class WordFeatures {

    static {
        System.out.println("Loading clusters...");
    }

    private static LevWordNetManager wnm = null;
    static {
        try {
            System.out.println("Loading wordnet database...");
            wnm = LevWordNetManager.getInstance(GlobalParameters.paths.wordnetConfig);
            WordNetHelper.wordNetPropertiesFile = GlobalParameters.paths.wordnetConfig;
        } catch (Exception e) {
            System.out.println("WordNet config file not found");
            System.exit(1);
        }
    }

    static {
        System.setProperty("wordnet.database.dir", GlobalParameters.paths.wordNetDictionaryPath);
    }

    static WordNetDatabase wordNet = WordNetDatabase.getFileInstance();

    public static int numOfCaptialLetters(CharSequence s) {
        if (s == null)
            return 0;
        int count = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            count += Character.isUpperCase(c) ? 1 : 0;
        }
        return count;
    }

    public static String lemma(String s, String pos) {
        if (s.contains(" "))
            return s;
        try {
            return WordNetHelper.getLemma(s, pos);
        } catch (Exception e) {
            return s;
        }
    }

    public static boolean isNE(String word) {
        word = normalize(word);
        try {
            if (wnm.getAllSenses(word, POS.NOUN).length == 0)
                return true;
            for (Synset synset : wordNet.getSynsets(word)) {
                if (synset instanceof NounSynset) {
                    NounSynset ns = (NounSynset) synset;
                    // System.out.println(adjSynset.isHeadSynset());
                    if (ns.getInstanceHypernyms().length > 0)
                        return true;
                }
            }
        } catch (JWNLException e) {
            e.printStackTrace();
        }
        return false;
    }
    
    public static int commonPrefixLength(String ai,String bi){
        String a = ai.toLowerCase().trim();
        String b = bi.toLowerCase().trim();
        int minLength = Math.min(a.length(), b.length());
        for (int i = 0; i < minLength; i++) {
            if (a.charAt(i) != b.charAt(i)) {
                return i;
            }
        }
        return minLength;
    }
    
    public static int commonSuffixLength(String ai,String bi){
        
        return commonPrefixLength(StringUtils.reverse(ai), StringUtils.reverse(bi));
        
    }
    
    public static String getAcronym(String s){
        if(StringUtils.isAllLowerCase(s.replace(" ", "")))
            return WordUtils.initials(s).toUpperCase();
        return WikiTitleUtils.getAcronym(s);
    }
    
    public static boolean canEntail(String ai,String bi){
        
        if(isLikelyAcronym(ai)){
            return ai.equals(getAcronym(bi));
        }
        
        if(isLikelyAcronym(bi)){
            return bi.equals(getAcronym(ai));
        }
        
        String a = StringUtils.strip(ai.toLowerCase()," .-");
        String b = StringUtils.strip(bi.toLowerCase()," .-");
        if(a.length()==0||b.length()==0)
            return true;
        
        // Remove common prefix and try again
        int preLength = commonPrefixLength(a, b);
        if (preLength > 0)
            return canEntail(a.substring(preLength),b.substring(preLength));
        
        
        // Remove common suffix and try again
        int sufLength = commonSuffixLength(a, b);
        if (sufLength > 0)
            return canEntail(a.substring(0,a.length()-sufLength),b.substring(0,b.length()-sufLength));
        return false;
    }
    
    

    public static void main(String[] args) {
        
        System.out.println(commonPrefixLength("serb","Serbian"));
        System.out.println(isNE("helicopter"));
        System.out.println(isNE("Clinton"));
        System.out.println(isNE("clinton"));

    }

    public static List<String> getHypernyms(String word) {
        try {
            return wnm.getAllHypernym(word);
        } catch (JWNLException e) {
            e.printStackTrace();
            return new ArrayList<String>();
        }
    }

    public static List<String> getNounHypernyms(String word) {
        try {
            return wnm.getHypernyms(word, POS.NOUN);
        } catch (JWNLException e) {
            e.printStackTrace();
            return new ArrayList<String>();
        }
    }

    public static List<String> getSynonyms(String word) {
        try {
            return wnm.getSynonyms(word, POS.NOUN);
        } catch (JWNLException e) {
            e.printStackTrace();
            return new ArrayList<String>();
        }
    }

    public static Set<String> semantics(String word) {
        if ("she".equals(word.toLowerCase()) || "he".equals(word.toLowerCase()))
            return semantics("person");

        Set<String> semantics = new HashSet<String>();
        try {
            semantics.addAll(wnm.getDefaultHypenymsToTheRoot(lemma(word, "NN"), POS.NOUN));
        } catch (JWNLException e) {
            e.printStackTrace();
        }
        return semantics;
    }
    
    public static boolean startsWithLowerCase(String word){
        return word!=null && word.length() > 0 && Character.isLowerCase(word.charAt(0));
    }

    private static LRUCache<String, String> nounForm = new LRUCache<String, String>(1000) {

        @Override
        protected String loadValue(String word) {
            // Normalizes adjective derived from nouns Iranian -> Iran
            for (Synset synset : wordNet.getSynsets(word)) {
                if (synset instanceof AdjectiveSynset) {
                    AdjectiveSynset adjSynset = (AdjectiveSynset) synset;

                    for (WordSense pertSense : adjSynset.getPertainyms(word)) {
                        return pertSense.getWordForm();
                    }

                    for (WordSense pertSense : adjSynset.getPertainyms(WordUtils.capitalize(word))) {
                        return pertSense.getWordForm();
                    }
                }
            }
            return word;
        }

    };

    /**
     * Iranian=>Iran
     * 
     * @param word
     * @return
     */
    public static String nounForm(String word) {
        try {
            return nounForm.get(word);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return word;
    }

    public static String normalize(String word) {

        // Normalizes adjective derived from nouns Iranian -> Iran
        for (Synset synset : wordNet.getSynsets(word)) {
            if (synset instanceof AdjectiveSynset) {
                AdjectiveSynset adjSynset = (AdjectiveSynset) synset;

                for (WordSense pertSense : adjSynset.getPertainyms(word)) {
                    return pertSense.getWordForm();
                }

                for (WordSense pertSense : adjSynset.getPertainyms(WordUtils.capitalize(word))) {
                    return pertSense.getWordForm();
                }
            }
        }

        // Try returning lemma
        String[] baseForms = wordNet.getBaseFormCandidates(word, SynsetType.NOUN);
        for (String lemma : baseForms)
            return lemma;
        return word;
    }

    public static boolean isLikelyAcronym(String s) {
        return isLikelyAcronym(s,5);
    }
    
    public static boolean isLikelyAcronym(String s,int lengthRequirement) {
        int dots = StringUtils.countMatches(s, ".");
        return s != null && !"I".equals(s) && (s.length() < lengthRequirement && StringUtils.isAllUpperCase(s)) || dots >= Math.max(2, s.length() / 2);
    }

    public static boolean isCapitalized(String s) {
        return s == null || s.length() == 0 ? false : Character.isUpperCase(s.charAt(0));
    }

    // CALIFORNIA -> California
    public static String recapitalize(String s) {
        if (WordFeatures.isCapitalized(s) && s.length() > 1)
            return s.charAt(0) + s.substring(1).toLowerCase();
        return s;
    }

    public static boolean containsNoUpperCase(String str) {
        for(char c:str.toCharArray()){
            if(Character.isUpperCase(c)){
                return false;
            }
        }
        return true;
    }

}
