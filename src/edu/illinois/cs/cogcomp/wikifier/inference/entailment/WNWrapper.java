package edu.illinois.cs.cogcomp.wikifier.inference.entailment;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import edu.mit.jwi.IRAMDictionary;
import edu.mit.jwi.RAMDictionary;
import edu.mit.jwi.data.ILoadPolicy;
import edu.mit.jwi.item.IIndexWord;
import edu.mit.jwi.item.IPointer;
import edu.mit.jwi.item.ISynset;
import edu.mit.jwi.item.ISynsetID;
import edu.mit.jwi.item.IWord;
import edu.mit.jwi.item.IWordID;
import edu.mit.jwi.item.POS;
import edu.mit.jwi.item.Pointer;
import edu.mit.jwi.morph.WordnetStemmer;


public class WNWrapper {

	public IRAMDictionary dict = null;
	public WordnetStemmer wstem = null;
	
	public static final int maxSense = 0;
	public static final int maxStem = 0;
	public static final double pTHRESH = 0.2;
	
	public static void main(String[] args){
	    
	    WNWrapper rap = new WNWrapper("data/WordNet/");
	    System.out.println(rap.getStemsList("computation"));
	    System.out.println(rap.getStemsList("computer",POS.NOUN));
	    System.out.println(rap.getStemsList("computing",POS.VERB));
	    System.out.println(rap.getStemsList("mice",POS.NOUN));
	    System.out.println(rap.getStemsList("countries",POS.NOUN));
	    System.out.println(rap.getStemsList("killed",POS.VERB));
	    System.out.println(rap.getStemsList("released",POS.VERB));
	}
	
    public WNWrapper(String wordnetPath) {
        String wnhome = System.getenv("WNHOME");
        if (wnhome == null)
            wnhome = wordnetPath;
        String path = wnhome;
        try {
            System.out.println("Creating wordnet dictionary from "+path+"...");
            dict = new RAMDictionary(new File(path), ILoadPolicy.BACKGROUND_LOAD);
            int tryCount = 10;
            while (--tryCount > 0 && !dict.open());
            System.out.println("Dictionary opened.");
            wstem = new WordnetStemmer(dict);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
	
	public void getSynsetId(String word) {
		List<String> stems = wstem.findStems(word, null);
		if(stems.size() > 0)
			word = stems.get(0);
		IIndexWord idxWord = dict.getIndexWord(word, POS.NOUN) ;
		IWordID wordID = idxWord.getWordIDs().get(0); // 1 st meaning
		IWord iword = dict . getWord (wordID);
		ISynset synset = iword.getSynset();
		System.out.println(synset.getID());
	}
	
	public void getStems(String word) {
		List<String> stems = wstem.findStems(word,null);
		for(String s: stems){
			System.out.println(s);
		}
	}
	
	public ArrayList<String> getStemsList(String word) { //experimental change!!!!!!!!!!!!!!!!!!!!!!!
		return getStemsList(word, null);
	}
	
	public ArrayList<String> getStemsList(final String word, POS pos) { //experimental change!!!!!!!!!!!!!!!!!!!!!!!
		List<String> stems = new ArrayList<String>();
		if(pos!=null) {
			List<String> stems1 = wstem.findStems(word, pos);
			stems=fix(stems1, word.substring(0,1), word);
		}
		else {
			for (POS p : POS.values()) {
				List<String> stems1=wstem.findStems(word, p);
				stems.addAll(fix(stems1, word.substring(0,1), word));
			}
		}
		stems = new ArrayList<String>((new HashSet<String>(stems)));
		if(stems.size() == 0)
			return new ArrayList<String>(Arrays.asList(word));
		return (ArrayList<String>) stems;
	}
	
	private List<String> fix(List<String> stems, String substring, String word) {
		ArrayList<String> stms = new ArrayList<String>();
		
		for(String s : stems) {
			if(s.equals("ha") && word.equals("has"))
				s="has";
			if(s.equals("doe") && word.equals("does"))
				s="does";
			//if(s.substring(0,1).equals(substring)) {
				stms.add(s);
				break;
			//}
		}

		
		return stms;	
	}

	public void printSynsets(String word) {
		IIndexWord idxWord = dict.getIndexWord(word, POS.NOUN) ;
		IWordID wordID = idxWord.getWordIDs().get(0); // 1 st meaning
		IWord iword = dict . getWord (wordID);
		ISynset synset = iword.getSynset();
		List<ISynsetID> sets = synset.getRelatedSynsets(Pointer.HYPERNYM);
		for(ISynsetID is: sets) {
			System.out.println(synset);
			recurseSynset(dict.getSynset(is));
		}
	}
	
	public void getAllSemanticRelations(String lemma) {
		//for each stem, for each sense in stemn\,print lemma, sensem, probability, sr and lr
		List<String> stems = wstem.findStems(lemma, null);
		for(String s: stems){
			for (POS pos : POS.values()) {
				IIndexWord idxWord = dict.getIndexWord(lemma, pos) ;
				if(idxWord==null)
					continue;
				List<IWordID> senses = idxWord.getWordIDs(); // 1 st meaning
				for(IWordID iw : senses) {
					ISynset is = dict.getSynset(iw.getSynsetID());
					System.out.println(is.getGloss());
					getAllSemanticRelations(is);
				}
			}
		}
	}
	
	public ArrayList<IWord> getAntonyms(String st) {
		return getAntonyms(st,null);
	}
	
	public ArrayList<IWord> getAntonyms(String st, POS p) {
		ArrayList<IWord> iwds = getIWords(st,p);
		ArrayList<IWord> antonyms = new ArrayList<IWord>();
		for(IWord iw: iwds) {
			List<IWordID> ants = iw.getRelatedMap().get(Pointer.ANTONYM);
			if(ants==null)
				continue;
			for(IWordID id: ants) {
				//System.out.println(dict.getWord(id));
				antonyms.add(dict.getWord(id));
			}
		}
		
		return antonyms;
	}
	
	public boolean isAntonym(String w1, POS p1, String w2, POS p2) {
		ArrayList<IWord> ants = getAntonyms(w1,p1);
		ArrayList<IWord> wds = getIWords(w2,p2);
		
		for(IWord ant: ants) {
			for(IWord iw: wds) {
				if(ant.equals(iw))
					return true;
			}
		}
		
		return false;
	}
	
	public boolean isAntonym(String w1, String w2) {
		return isAntonym(w1,null,w2,null);
	}
	
	/*uncomment to let capital letterered words be converted to lowercase*/
	public ArrayList<ISynset> getAllSynset(String word) {
		ArrayList<ISynset> syns = getAllSynset(word, false, null);
		if(syns.size()==0)
			return getAllSynset(word, true,  null);
		return syns;
	}
	
	public ArrayList<ISynset> getAllSynset(String word, POS pos) {
		ArrayList<ISynset> syns = getAllSynset(word, false, pos);
		if(syns.size()==0)
			return getAllSynset(word, true, pos);
		return syns;
	}
	
	//if word starts with cap, only let those synsets through that also start with cap.
	public ArrayList<ISynset> getAllSynset(String word, boolean flag, POS pos_) {
		ArrayList<ISynset> syns = new ArrayList<ISynset>();
		List<String> stems = getStemsList(word,pos_);
		for(String s: stems){
			//System.out.println(s);
			for (POS pos : POS.values()) {
				if(pos_ !=null && !pos.equals(pos_))
					continue;
				//System.out.println(pos);
				IIndexWord idxWord = dict.getIndexWord(s, pos) ;
				if(idxWord==null)
					continue;
				List<IWordID> senses = idxWord.getWordIDs(); // 1 st meaning
			//	HashMap<IWordID,Double> probs = getProbs(senses);
				for(IWordID iw : senses) {
			//		if(probs.get(iw) > pTHRESH) {
			//			System.out.println(probs.get(iw));
					ISynset is = dict.getSynset(iw.getSynsetID());
					List<IWord> words = is.getWords();
					if(isAcceptable(words, word) || flag) {
					//	System.out.println(is);
					//if(true) {
						syns.add(is);
//						for(IWord iword: words) {
//						//	System.out.println(iword);
//						}
					}
				}
			}
		}
		
		return syns;
	}
	
	//only acceptable if word is not capitalized or is capitalized and a capitaled word in synset.
	private boolean isAcceptable(List<IWord> words, String word) {
		if(containsCapital(word)) {
			for(IWord iword : words) {
				if(containsCapital(iword.getLemma()))
					return true;
			}
			return false;
		}
		return true;
	}
	
	private boolean containsCapital(String word) {
		
		for(int i = 0; i < word.length(); i++) {	
			if (Character.isUpperCase(word.charAt(i))) {
				return true;
			}
		}
		return false;
	}

	HashMap<IWordID, Double> getProbs(List<IWordID> senses) {
		HashMap<IWordID,Double> probs = new HashMap<>();
		double total = 0;
		for(IWordID iw: senses) {
			total += dict.getSenseEntry(dict.getWord(iw).getSenseKey()).getTagCount();
		}
		for(IWordID iw: senses) {
			double val = dict.getSenseEntry(dict.getWord(iw).getSenseKey()).getTagCount() / total;
			if(Double.isNaN(val)) {
				val = 0.;
			}
			probs.put(iw, val);
		}
		
		return probs;
	}

	public void getAllSemanticRelations(ISynset s) {
		Map<IPointer, List<ISynsetID>> map = s.getRelatedMap();
		Set<Entry<IPointer, List<ISynsetID>>> x = map.entrySet();
		Iterator<Entry<IPointer, List<ISynsetID>>> it = x.iterator();
		while(it.hasNext()) {
			Entry y = it.next();
			System.out.println(y.getKey());
			List<ISynsetID> lis = (List<ISynsetID>) y.getValue();
			for(ISynsetID id: lis) {
				ISynset syn = dict.getSynset(id);
				System.out.println("\t" + syn);
			}
			
		}
	}
	
	public void getAllLexicalRelations(String lemma) {
		//for each stem, for each sense in stemn\,print lemma, sensem, probability, sr and lr
		List<String> stems = wstem.findStems(lemma, null);
		for(String s: stems){
			for (POS pos : POS.values()) {
				IIndexWord idxWord = dict.getIndexWord(lemma, pos) ;
				if(idxWord==null)
					continue;
				List<IWordID> senses = idxWord.getWordIDs(); // 1 st meaning
				for(IWordID iw : senses) {
					ISynset is = dict.getSynset(iw.getSynsetID());
					System.out.println(is.getGloss());
					getAllLexicalRelations(dict.getWord(iw));
				}
			}
		}
	}
	
	
	public ArrayList<IWord> getIWords(String st) {
		return getIWords(st,null);
	}
	
	public ArrayList<IWord> getIWords(String st, POS pos_) {
		List<String> stems = wstem.findStems(st, pos_);
		ArrayList<IWord> iwds = new ArrayList<IWord>();
		for(String s: stems){
			for (POS pos : POS.values()) {
				if(pos_!=null && !pos.equals(pos_))
					continue;
				IIndexWord idxWord = dict.getIndexWord(st, pos) ;
				if(idxWord==null)
					continue;
				List<IWordID> senses = idxWord.getWordIDs(); // 1 st meaning
				for(IWordID iw : senses) {
					iwds.add(dict.getWord(iw));
				}
			}
		}
		
		return iwds;
	}
	
	public void getAllLexicalRelations(IWord s) {
		Map<IPointer, List<IWordID>> map = s.getRelatedMap();
		Set<Entry<IPointer, List<IWordID>>> x = map.entrySet();
		Iterator<Entry<IPointer, List<IWordID>>> it = x.iterator();
		while(it.hasNext()) {
			Entry y = it.next();
			System.out.println(y.getKey());
			List<IWordID> lis = (List<IWordID>) y.getValue();
			for(IWordID id: lis) {
				IWord syn = dict.getWord(id);
				System.out.println("\t" + syn);
			}
			
		}
	}
	
	public void recurseSynset(ISynset s) {
		System.out.println(s);
		List<ISynsetID> lis = s.getRelatedSynsets(Pointer.HYPERNYM);
		for(ISynsetID id: lis) {
			recurseSynset(dict.getSynset(id));
			dict.getSynset(id);
		}
	}
}
