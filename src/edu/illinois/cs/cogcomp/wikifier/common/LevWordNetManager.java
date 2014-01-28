package edu.illinois.cs.cogcomp.wikifier.common;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.didion.jwnl.JWNL;
import net.didion.jwnl.JWNLException;
import net.didion.jwnl.data.IndexWord;
import net.didion.jwnl.data.IndexWordSet;
import net.didion.jwnl.data.POS;
import net.didion.jwnl.data.PointerTarget;
import net.didion.jwnl.data.PointerType;
import net.didion.jwnl.data.PointerUtils;
import net.didion.jwnl.data.Synset;
import net.didion.jwnl.data.Word;
import net.didion.jwnl.data.list.PointerTargetNode;
import net.didion.jwnl.data.list.PointerTargetNodeList;
import net.didion.jwnl.data.list.PointerTargetTree;
import net.didion.jwnl.data.relationship.Relationship;
import net.didion.jwnl.data.relationship.RelationshipFinder;
import net.didion.jwnl.data.relationship.RelationshipList;
import net.didion.jwnl.dictionary.Dictionary;

import com.google.common.base.Function;
import com.google.common.collect.Lists;

public class LevWordNetManager {

	// Dictionary object
	private static Dictionary wordnet = null;
	private static LevWordNetManager INSTANCE;

	// Initialize the database
	private LevWordNetManager(String propsFile) throws FileNotFoundException,
	JWNLException {
		System.out.println("WordNet config file: "+propsFile);
		if (wordnet == null) {

			JWNL.initialize(new FileInputStream(propsFile));
			// Create dictionary object
			wordnet = Dictionary.getInstance();
		}
	}
	
	public boolean isInVocabulary(String inputWord, POS pos) throws JWNLException{
		IndexWord indexWord = getIndexWord(pos, inputWord);
		return indexWord!=null;
	}
	public List<String> getDefaultHypenymsToTheRoot(String inputWord, POS pos)	throws JWNLException {
		IndexWord indexWord = getIndexWord(pos, inputWord);
		if (indexWord != null) {
			List<String> res = new ArrayList<String>();
			List<Synset> arrHypSets = getRelatedSynset(indexWord, PointerType.HYPERNYM);
			while (arrHypSets != null && arrHypSets.size() > 0) {
				Synset synset = arrHypSets.get(0);
				Word[] words = synset.getWords();
				if(words.length>0)
					res.add(words[0].getLemma());
				arrHypSets = getRelated(synset, PointerType.HYPERNYM);
			}
			return res;
		} else
			return new ArrayList<String>();
	}

	// Return array of POS objects for a given String
	public synchronized POS[] getPOS(String s) throws JWNLException {
		// Look up all IndexWords (an IndexWord can only be one POS)
		IndexWordSet set = wordnet.lookupAllIndexWords(s);
		// Turn it into an array of IndexWords
		IndexWord[] words = set.getIndexWordArray();
		// Make the array of POS
		POS[] pos = new POS[words.length];
		for (int i = 0; i < words.length; i++) {
			pos[i] = words[i].getPOS();
		}
		return pos;
	}

	// Just gets the related words for first sense of a word
	// Revised to get the list of related words for the 1st Synset that has them
	// We might want to try all of them
	public List<Synset> getRelated(IndexWord word, PointerType type)
	throws JWNLException {
		try {
			Synset[] senses = word.getSenses();
			// Look for the related words for all Senses
			for (int i = 0; i < senses.length; i++) {
				List<Synset> a = getRelated(senses[i], type);
				// If we find some, return them
				if (a != null && !a.isEmpty()) {
					return a;
				}
			}
		} catch (NullPointerException e) {
			// System.out.println("Oops, NULL problem: " + e);
		}
		return new ArrayList<Synset>();
	}

	// Related words for a given sense (do synonyms by default)
	// Probably should implement all PointerTypes
    public List<Synset> getRelated(Synset sense, PointerType type)
	throws JWNLException, NullPointerException {
		PointerTargetNodeList relatedList;
		// Call a different function based on what type of relationship you are
		// looking for
		if (type == PointerType.HYPERNYM) {
			relatedList = PointerUtils.getInstance().getDirectHypernyms(sense);
		} else if (type == PointerType.HYPONYM) {
			relatedList = PointerUtils.getInstance().getDirectHyponyms(sense);
		} else {
			relatedList = PointerUtils.getInstance().getSynonyms(sense);
		}
		// Iterate through the related list and make an ArrayList of Synsets to
		// send back
		
        return node2Synset(relatedList);
	}

	// Just shows the Tree of related words for first sense
	// We may someday want to the Tree for all senses
	public void showRelatedTree(IndexWord word, int depth, PointerType type)
	throws JWNLException {
		showRelatedTree(word.getSense(1), depth, type);
	}

	public void showRelatedTree(Synset sense, int depth, PointerType type)
	throws JWNLException {
		PointerTargetTree relatedTree;
		// Call a different function based on what type of relationship you are
		// looking for
		if (type == PointerType.HYPERNYM) {
			relatedTree = PointerUtils.getInstance().getHypernymTree(sense,
					depth);
		} else if (type == PointerType.HYPONYM) {
			relatedTree = PointerUtils.getInstance().getHyponymTree(sense,
					depth);
		} else {
			relatedTree = PointerUtils.getInstance().getSynonymTree(sense,
					depth);
		}
		// If we really need this info, we wil have to write some code to
		// Process the tree
		// Not just display it
		relatedTree.print();
	}

	// This method looks for any possible relationship
	public Relationship getRelationship(IndexWord start, IndexWord end,
			PointerType type) throws JWNLException {
		// All the start senses
		Synset[] startSenses = start.getSenses();
		// All the end senses
		Synset[] endSenses = end.getSenses();
		// Check all against each other to find a relationship
		for (int i = 0; i < startSenses.length; i++) {
			for (int j = 0; j < endSenses.length; j++) {
				RelationshipList list = RelationshipFinder.getInstance()
				.findRelationships(startSenses[i], endSenses[j], type);
				if (!list.isEmpty()) {
					return (Relationship) list.get(0);
				}
			}
		}
		return null;
	}

	// If you have a relationship, this function will create an ArrayList of
	// Synsets
	// that make up that relationship
	public List<Synset> getRelationshipSenses(Relationship rel)
	throws JWNLException {
		return node2Synset(rel.getNodeList());
	}

	@SuppressWarnings("unchecked")
    private static List<Synset> node2Synset(PointerTargetNodeList list){
	    return Lists.transform(list, new Function<PointerTargetNode,Synset>(){
            @Override
            public Synset apply(PointerTargetNode node) {
                return node.getSynset();
            }
        });
	}
	
	// Get the IndexWord object for a String and POS
	public synchronized IndexWord getIndexWord(POS pos, String s) throws JWNLException {
		// IndexWord word = wordnet.getIndexWord(pos,s);
		IndexWord word = wordnet.lookupIndexWord(pos, s); // This function
		// tries the stemmed
		// form of the lemma
		return word;
	}

	/*
	 * Quang added functions from here
	 */

	// =============
	public List<String> getSynonymForAllSenses(IndexWord indexWord)
	throws JWNLException {
		List<String> arrSynonym = new ArrayList<String>();
		HashSet<String> hsWords = new HashSet<String>();
		Synset[] synsets = indexWord.getSenses();
		if (synsets != null) {
			for (int i = 0; i < synsets.length; i++) {
				Synset synset = synsets[i];
				Word[] words = synset.getWords();
				for (int j = 0; j < words.length; j++) {
					Word word = words[j];
					hsWords.add(word.getLemma());
				}
			}
		}
		arrSynonym.addAll(hsWords);
		return arrSynonym;
	}

	public List<String> getSynonymForFirstSense(IndexWord indexWord)
	throws JWNLException {
		List<String> arrSynonym = new ArrayList<String>();
		HashSet<String> hsWords = new HashSet<String>();
		Synset[] synsets = indexWord.getSenses();
		if (synsets != null) {
			Synset synset = synsets[0];
			Word[] words = synset.getWords();
			for (int j = 0; j < words.length; j++) {
				Word word = words[j];
				hsWords.add(word.getLemma());
			}

		}
		arrSynonym.addAll(hsWords);
		return arrSynonym;
	}

	// ==============
	public List<String> getAllSynonyms(String inputWord)
	throws JWNLException {
		List<String> arrSynonym = new ArrayList<String>();
		HashSet<String> hsWords = new HashSet<String>();
		POS[] poses = { POS.NOUN, POS.VERB, POS.ADJECTIVE, POS.ADVERB };
		for (int i = 0; i < poses.length; i++) {
			POS pos = poses[i];
			IndexWord indexWord = getIndexWord(pos, inputWord);
			if (indexWord != null) {
				List<String> synonyms = getSynonymForAllSenses(indexWord);
				hsWords.addAll(synonyms);
			}
		}
		arrSynonym.addAll(hsWords);
		return arrSynonym;
	}

	public List<String> getSynonyms(String inputWord, POS pos)
	throws JWNLException {
		IndexWord indexWord = getIndexWord(pos, inputWord);
		if (indexWord != null) {
			return getSynonymForAllSenses(indexWord);
		} else
			return new ArrayList<String>();
	}

	public List<String> getSynonymsFrequentSense(String inputWord, POS pos)
	throws JWNLException {
		IndexWord indexWord = getIndexWord(pos, inputWord);
		if (indexWord != null) {
			return getSynonymForFirstSense(indexWord);
		} else
			return new ArrayList<String>();
	}

	public List<String> getHypernyms(String inputWord, POS pos)
	throws JWNLException {
		IndexWord indexWord = getIndexWord(pos, inputWord);
		if (indexWord != null) {
			return getHypernymForAllSenses(indexWord);
		} else
			return new ArrayList<String>();
	}

	public List<String> getHypernymsFrequentSense(String inputWord, POS pos)
	throws JWNLException {
		IndexWord indexWord = getIndexWord(pos, inputWord);
		if (indexWord != null) {
			return getHypernymForFirstSense(indexWord);
		} else
			return new ArrayList<String>();
	}

	// =============
		public List<String> getHypernymForAllSenses(IndexWord indexWord)
		throws JWNLException {
			List<String> arrHypernyms = new ArrayList<String>();
			HashSet<String> hsWords = new HashSet<String>();
			List<Synset> arrSynsets = getRelatedSynset(indexWord,
					PointerType.HYPERNYM);
			int n = arrSynsets.size();
			for (int i = 0; i < n; i++) {
				Synset synset = arrSynsets.get(i);
				Word[] words = synset.getWords();
				for (int j = 0; j < words.length; j++) {
					Word word = words[j];
					hsWords.add(word.getLemma());
				}
			}
			arrHypernyms.addAll(hsWords);
			return arrHypernyms;
		}

		public List<String> getHypernymForFirstSense(IndexWord indexWord)
		throws JWNLException {
			List<String> arrHypernyms = new ArrayList<String>();
			HashSet<String> hsWords = new HashSet<String>();
			List<Synset> arrHypSets = getRelatedSynset(indexWord,
					PointerType.HYPERNYM);
			if (arrHypSets != null && arrHypSets.size() > 0) {

				Synset synset = arrHypSets.get(0);
				Word[] words = synset.getWords();
				for (int j = 0; j < words.length; j++) {
					Word word = words[j];
					hsWords.add(word.getLemma());
				}
			}
			arrHypernyms.addAll(hsWords);
			return arrHypernyms;
		}

		// =============
		public List<String> getHyponymForAllSenses(IndexWord indexWord)
		throws JWNLException {
			List<String> arrHyponyms = new ArrayList<String>();
			HashSet<String> hsWords = new HashSet<String>();
			List<Synset> arrSynsets = getRelatedSynset(indexWord,
					PointerType.HYPONYM);
			int n = arrSynsets.size();
			for (int i = 0; i < n; i++) {
				Synset synset = arrSynsets.get(i);
				Word[] words = synset.getWords();
				for (int j = 0; j < words.length; j++) {
					Word word = words[j];
					hsWords.add(word.getLemma());
				}
			}
			arrHyponyms.addAll(hsWords);
			return arrHyponyms;
		}

		// =============
		private List<Synset> getRelatedSynset(IndexWord indexWord,
				PointerType relationType) throws JWNLException {
			Synset[] synsets = indexWord.getSenses();
			List<Synset> arrAllSynsets = new ArrayList<Synset>();
			for (int i = 0; i < synsets.length; i++) {
				List<Synset> arrSynsets = getRelated(synsets[i], relationType);
				if (arrSynsets != null && !arrSynsets.isEmpty()) {
					arrAllSynsets.addAll(arrSynsets);
				}
			}
			return arrAllSynsets;
		}

		// =============
		public List<String> getAllHypernym(String lexicalForm)
		throws JWNLException {
			return new ArrayList<String>(lookupWordsFollowingPointer(lexicalForm,
					PointerType.HYPERNYM));
		}

		// =============
		public List<String> getAllHyponym(String lexicalForm)
		throws JWNLException {
			return new ArrayList<String>(lookupWordsFollowingPointer(lexicalForm,
					PointerType.HYPONYM));
		}

		// =============
		private HashSet<String> lookupWordsFollowingPointer(String lexicalForm,
				PointerType pointerType) throws JWNLException {
			HashSet<String> relatedWords = new HashSet<String>();
			POS[] poses = { POS.NOUN, POS.VERB, POS.ADJECTIVE, POS.ADVERB };
			for (POS pos : poses) {
				IndexWord indexWord = getIndexWord(pos, lexicalForm);
				if (indexWord == null)
					return relatedWords;
				Synset[] synSets = indexWord.getSenses();
				for (Synset synset : synSets) {
					PointerTarget[] targets = synset.getTargets(pointerType);
					if (targets != null) {
						for (PointerTarget target : targets) {
							Word[] words = ((Synset) target).getWords();
							for (Word word : words) {
								relatedWords.add(word.getLemma());
							}
						}
					}
				}
			}
			return relatedWords;
		}

		// =============
		public List<String> getAllMorph(String lexicalForm)
		throws JWNLException {
			List<String> arrMorph = new ArrayList<String>();
			HashSet<String> hsMorph = new HashSet<String>();
			POS[] poses = { POS.NOUN, POS.VERB, POS.ADJECTIVE, POS.ADVERB };
			for (POS pos : poses) {
				List<String> morphs = getMorph(pos, lexicalForm);
				hsMorph.addAll(morphs);
			}
			arrMorph.addAll(hsMorph);
			return arrMorph;
		}

		// =============
		public synchronized List<String> getMorph(POS pos, String lexicalForm)
		throws JWNLException {
			HashSet<String> forms = new HashSet<String>();
			List<?> baseForms = wordnet.getMorphologicalProcessor()
			.lookupAllBaseForms(pos, lexicalForm);
			for (Object baseForm : baseForms) {
				forms.add(baseForm.toString());
			}
			return new ArrayList<String>(forms);
		}

		public Synset[] getAllSenses(String word, POS pos) throws JWNLException {
			IndexWord iw = getIndexWord(pos, word);
			if (iw == null)
				return new Synset[0];
			else
				return iw.getSenses();
		}

		public String getLemma(String word, POS pos) throws JWNLException {
			IndexWord iw = getIndexWord(pos, word);
			if (iw == null)
				return word;
			else
				return iw.getLemma();
		}

		public Set<String> getAllLemmas(String word) throws JWNLException {
			Set<String> s = new HashSet<String>();

			POS[] poses = { POS.NOUN, POS.VERB, POS.ADJECTIVE, POS.ADVERB };
			for (POS pos : poses) {
				String lemma = getLemma(word, pos);
				// p.put(pos, getLemma(word, pos));
				s.add(lemma);
			}
			return s;
		}

		public Set<String> getAllMorphs(String word) throws JWNLException {

			Set<String> s = new HashSet<String>();

			POS[] poses = { POS.NOUN, POS.VERB, POS.ADJECTIVE, POS.ADVERB };
			for (POS pos : poses) {
				List<String> morphs = getMorph(pos, word);
				// p.put(pos, getLemma(word, pos));
				for (String m : morphs)
					s.add(m);
			}
			return s;
		}

		/**
		 * @return
		 * @throws JWNLException
		 * @throws FileNotFoundException
		 */
		public static LevWordNetManager getInstance(String propsFile)
		throws FileNotFoundException, JWNLException {

			if (INSTANCE == null)
				INSTANCE = new LevWordNetManager(propsFile);

			return INSTANCE;
		}
		
		public static void main(String[] args) throws Exception{
			LevWordNetManager manager = LevWordNetManager.getInstance("../Config/jwnl_properties.xml");
			System.out.println(manager.getIndexWord(POS.ADJECTIVE,"Iranian"));
		}
}
