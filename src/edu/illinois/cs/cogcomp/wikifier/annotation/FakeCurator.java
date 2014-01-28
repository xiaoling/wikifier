package edu.illinois.cs.cogcomp.wikifier.annotation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.concurrent.ConcurrentException;
import org.apache.commons.lang3.concurrent.LazyInitializer;
import org.apache.thrift.TException;

import LBJ2.nlp.Word;
import LBJ2.nlp.seg.Token;
import LBJ2.parse.LinkedVector;
import edu.illinois.cs.cogcomp.LbjNer.ExpressiveFeatures.ExpressiveFeaturesAnnotator;
import edu.illinois.cs.cogcomp.LbjNer.InferenceMethods.Decoder;
import edu.illinois.cs.cogcomp.LbjNer.LbjFeatures.NETaggerLevel1;
import edu.illinois.cs.cogcomp.LbjNer.LbjFeatures.NETaggerLevel2;
import edu.illinois.cs.cogcomp.LbjNer.LbjTagger.Data;
import edu.illinois.cs.cogcomp.LbjNer.LbjTagger.NERDocument;
import edu.illinois.cs.cogcomp.LbjNer.LbjTagger.NEWord;
import edu.illinois.cs.cogcomp.LbjNer.LbjTagger.Parameters;
import edu.illinois.cs.cogcomp.LbjNer.LbjTagger.ParametersForLbjCode;
import edu.illinois.cs.cogcomp.LbjNer.ParsingProcessingData.PlainTextReader;
import edu.illinois.cs.cogcomp.edison.sentences.Constituent;
import edu.illinois.cs.cogcomp.edison.sentences.SpanLabelView;
import edu.illinois.cs.cogcomp.edison.sentences.TextAnnotation;
import edu.illinois.cs.cogcomp.edison.sentences.TokenizerUtilities.SentenceViewGenerators;
import edu.illinois.cs.cogcomp.edison.sentences.View;
import edu.illinois.cs.cogcomp.edison.sentences.ViewNames;
import edu.illinois.cs.cogcomp.lbj.chunk.Chunker;
import edu.illinois.cs.cogcomp.thrift.base.Labeling;
import edu.illinois.cs.cogcomp.thrift.base.Span;
import edu.illinois.cs.cogcomp.wikifier.utils.datastructure.LRUCache;

/*
 * This class allows the wikifier to work without a curator. 
 * It still returns a TextAnnotation with NER, POS and Chunker 
 * annotation, but it doesn't really call the curator. 
 */
class FakeCurator {

    private static String nerModelFile;

    public static final int CacheSize = 10;

//    private ConcurrentHashMap<String,Object> currentlyAnnotating = new ConcurrentHashMap<>();
    private LRUCache<String, TextAnnotation> annotationCache = 
        new LRUCache<String, TextAnnotation>(CacheSize){
        public TextAnnotation loadValue(String text){
            TextAnnotation ta = null;
            try {
                ta = addViews(text);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return ta;
        }
    };

    final boolean useCoref;
    private Chunker chunker = new Chunker();;
    private LazyInitializer<NETaggerLevel1> level1NerTagger = new LazyInitializer<NETaggerLevel1>(){
        @Override
        protected NETaggerLevel1 initialize() throws ConcurrentException {
            // ensure nerModelFile is set
            nerData.get();
            return new NETaggerLevel1(nerModelFile+".level1",nerModelFile+".level1.lex");
        }
    };
    private LazyInitializer<NETaggerLevel2> level2NerTagger = new LazyInitializer<NETaggerLevel2>(){
        @Override
        protected NETaggerLevel2 initialize() throws ConcurrentException {
            // ensure nerModelFile is set
            nerData.get();
            return new NETaggerLevel2(nerModelFile+".level2",nerModelFile+".level2.lex");
        }
    };
    private LazyInitializer<Boolean> nerData = null;

	public FakeCurator(String pathToNerConfigFile) throws Exception{
	    this(pathToNerConfigFile,false);
	}
	
	public FakeCurator(final String pathToNerConfigFile,boolean useCoref) throws Exception {
	    nerData = new LazyInitializer<Boolean>() {
            @Override
            protected Boolean initialize(){
                try {
                    Parameters.readConfigAndLoadExternalData(pathToNerConfigFile);
                    nerModelFile = ParametersForLbjCode.currentParameters.pathToModelFile;
                } catch (Exception e) {
                    e.printStackTrace();
                    System.exit(0);
                }
                return true;
            }
        };
	    this.useCoref = useCoref;
	}

	public TextAnnotation annotate(String text) throws Exception{
	    return annotationCache.get(text);
	}
		
	private TextAnnotation addViews(String text) throws Exception {
		TextAnnotation ta = createAnnotation(text);
		ExecutorService exe = null;
		Thread coref = null;
		if(useCoref){
		    coref = Coreference.getCorefThread(ta);
		    exe = Executors.newSingleThreadExecutor();
		    exe.execute(coref);
		    exe.shutdown();
		}
		List<String> nerWords  = new ArrayList<String>();
		List<String> nerTags = new ArrayList<String>();
		performNER(ta.getText(), true, level1NerTagger.get(), level2NerTagger.get(), nerWords, nerTags);
		List<String> chunkerWords = new ArrayList<String>();
		List<String> chunkerTags = new ArrayList<String>();
		List<String> posTags = new ArrayList<String>();
		performChunkerAndPos(ta.getText(), true, chunker, chunkerWords, chunkerTags, posTags);
		View nerView = new SpanLabelView(ViewNames.NER, "IllinoisNER.2.1", ta, 0, true);
		Labeling nerLabels = getLabeling(nerWords, nerTags,  ta.getText(), "IllinoisNER.2.1");
		for(Iterator<Span> label= nerLabels.getLabelsIterator(); label.hasNext() ; ) {
			Span span = label.next();
			Constituent c = makeConstituentFixTokenBoundaries(span.label, ViewNames.NER, ta, span.start, span.ending);
			if(c!=null)
				nerView.addConstituent(c);
		}
		ta.addView(ViewNames.NER, nerView);

		View chunkerView = new SpanLabelView(ViewNames.SHALLOW_PARSE, "IllinoisChunker", ta, 0);
		Labeling chunkerLabels = getLabeling(chunkerWords, chunkerTags, ta.getText(), "IllinoisChunker");
		for(Iterator<Span> label= chunkerLabels.getLabelsIterator(); label.hasNext() ; ) {
			Span span = label.next();
			Constituent c = makeConstituentFixTokenBoundaries(span.label, ViewNames.SHALLOW_PARSE, ta, span.start, span.ending); 
			if(c!=null)
				chunkerView.addConstituent(c);
		}
		ta.addView(ViewNames.SHALLOW_PARSE, chunkerView);

		View posView = new View(ViewNames.POS, "IllinoisPOS", ta, 0);
		Labeling posLabels = getLabeling(chunkerWords, posTags, ta.getText(), "IllinoisPOS");
		for(Span span : posLabels.labels ) {
		    Constituent c = makeConstituentFixTokenBoundaries(span.label, ViewNames.POS, ta, span.start, span.ending);
			if(c!=null)
				posView.addConstituent(c);
		}
		ta.addView(ViewNames.POS, posView);

		if(useCoref){
		    try{
		        exe.awaitTermination(4000, TimeUnit.MINUTES);
		        coref.interrupt();
		    }catch(InterruptedException e){
		        System.out.println("Coreference takig too long, interrupted!!!");
		        e.printStackTrace();
		    }
		}
		return ta;
	}

	/*
	 * Returns the results through parameters!!!! Make sure to refresh the result params!
	 */
	private synchronized void performNER(String input,
			boolean newline2newsent,
			NETaggerLevel1 t1, 
			NETaggerLevel2 t2,
			List<String> resultWords,
			List<String> resultPredictions) throws TException{
		ParametersForLbjCode.currentParameters.forceNewSentenceOnLineBreaks = newline2newsent;
		if (input.trim().length() == 0) 
			return;
		Vector<LinkedVector> sentences = PlainTextReader.parseText(input);
		Data data = new Data(new NERDocument(sentences, "input"));
		try {
			ExpressiveFeaturesAnnotator.annotate(data);
			Decoder.annotateDataBIO(data, t1, t2);
		} catch (Exception e) {
			System.out.println("Cannot annotate the test, the exception was: ");
			e.printStackTrace();
			throw new TException("Cannot annotate the test, the exception was "+e.toString());
		}
        for (LinkedVector v : sentences)
            for (int j = 0; j < v.size(); j++) {
                NEWord w = (NEWord) v.get(j);
                resultWords.add(w.form);
                resultPredictions.add(w.neTypeLevel2);
            }
	}

	
	/*
	 * Returns the results through parameters!!!! Make sure to refresh the result params!
	 */
	private synchronized void performChunkerAndPos(String input,
			boolean newline2newsent, Chunker chunker, 
			List<String> words, 
			List<String> chunkerPrediction,
			List<String> posPrediction)  throws TException{
		ParametersForLbjCode.currentParameters.forceNewSentenceOnLineBreaks = newline2newsent;
		if (input.trim().equals("")) 
			return;
		List<LinkedVector> NEWordSentences = PlainTextReader.parseText(input);
		for(int i=0;i<NEWordSentences.size();i++) {
			LinkedVector chunkerSentence = new LinkedVector();
			for(int j=0;j<NEWordSentences.get(i).size();j++)  {
				Token t = null;
				if(j>0)
					t = new Token((Word)NEWordSentences.get(i).get(j), (Token)chunkerSentence.get(j-1) , "O");
				else
					t = new Token((Word)NEWordSentences.get(i).get(j), null , "O");
				chunkerSentence.add(t);
			}
			for(int j=1;j<chunkerSentence.size();j++) { 
				chunkerSentence.get(j).previous =chunkerSentence.get(j-1);
				chunkerSentence.get(j-1).next =chunkerSentence.get(j);
			}
			for(int j=0;j<chunkerSentence.size();j++) {
				Token w = (Token)chunkerSentence.get(j);
				chunkerPrediction.add(chunker.discreteValue(w));
				posPrediction.add("B-"+w.partOfSpeech); // this is to make the annotation consistent with NER and the chunker....
				words.add(w.form);
			}
		}
	}

	private static Labeling getLabeling(List<String> words, List<String> predictions, String originalText, String labelerName) throws  TException {
		List<Integer> quoteLocs = findQuotationLocations(originalText);
		List<Span> labels = new ArrayList<Span>();

		// track the location we have reached in the input
		int location = 0;
		boolean open = false;

		// the span for this entity
		Span span = null;

		for (int j = 0; j < words.size(); j++) {

			// the current word (NER's interpretation)
			String word = words.get(j);

			int[] startend = findStartEndForSpan(originalText, location, word,
					quoteLocs);
			location = startend[1];

			if (predictions.get(j).startsWith("B-")
					|| (j > 0 && predictions.get(j).startsWith("I-") && (!predictions.get(j - 1)
					                                                              .endsWith(predictions.get(j).substring(2))))) {
				span = new Span();
				span.setStart(startend[0]);
				span.setLabel(predictions.get(j).substring(2));
				open = true;
			}

			if (open) {
				boolean close = false;
				if (j == words.size() - 1) {
					close = true;
				} else {
					if (predictions.get(j + 1).startsWith("B-"))
						close = true;
					if (predictions.get(j + 1).equals("O"))
						close = true;
					if (predictions.get(j + 1).indexOf('-') > -1
							&& (!predictions.get(j).endsWith(predictions.get(j + 1)
							                                         .substring(2))))
						close = true;
				}
				if (close) {
					span.setEnding(startend[1]);
					if(span.start > span.ending ){
						System.out.println("Critical tagging error : negative index.");
						span.setStart(startend[0]);
					}
					
					span.source = originalText.substring(span.start, span.ending);
					labels.add(span);
					// System.out.println("Found span: \""+span.source+"\"\t["+span.start+":"+span.ending+"]\t"+span.label);
					open = false;
				}
			}
		}
		Labeling labeling = new Labeling();
		labeling.setSource(labelerName);
		labeling.setLabels(labels);
		return labeling;
	}

	/**
	 * Finds where double tick quotation marks are and returns their start
	 * locations in the string NER will use.
	 * 
	 * NER performs preprocessing internally on the input string to turn double
	 * ticks to the double quote character, we need to be able to recover the
	 * double tick locations in the original to make sure our spans are
	 * consistent.
	 * 
	 * If input is: He said, ``This is great'', but he's "hip". NER will modify
	 * it to: He said, "This is great", but he's "hip". so we need to know that
	 * locations <9, 23> are locations in the NER string that should be double
	 * ticks.
	 * 
	 * @param input
	 *            rawText input not modified by NER
	 * @return list of integer locations that double quote should be translated
	 *         to double tick
	 */
	private static List<Integer> findQuotationLocations(String input) {
		List<Integer> quoteLocs = new ArrayList<Integer>();
		if (input.contains("``") || input.contains("''")) {
			int from = 0;
			int index;
			int counter = 0;
			while ((index = input.indexOf("``", from)) != -1) {
				quoteLocs.add(index - counter);
				counter++;
				from = index + 2;
			}
			while ((index = input.indexOf("''", from)) != -1) {
				quoteLocs.add(index - counter);
				counter++;
				from = index + 2;
			}
			Collections.sort(quoteLocs);
		}
		return quoteLocs;
	}

	/**
	 * Finds that start and end indices in the input of the span corresponding
	 * to the word. The location is a pointer to how far we have processed the
	 * input.
	 * 
	 * @param input
	 * @param location
	 * @param word
	 * @param quoteLocs
	 * @return
	 */
	private static int[] findStartEndForSpan(String input, int location, String word,
			List<Integer> quoteLocs) {
		int[] startend = null;

		if (word.equals("\"")) {
			// double quote is a special case because it could have been a
			// double tick before
			// inputAsNer is how NER viewed the input (we replicate the
			// important transforms
			// ner makes, this is very fragile!)
		    StringBuilder inputAsNer = new StringBuilder();
			inputAsNer.append(input.substring(0, location));
			// translate double ticks to double quote in the original input
			inputAsNer.append(input.substring(location).replace("``", "\"")
					.replace("''", "\""));
			// find start end for the word in the input as ner
			startend = findSpan(location, inputAsNer.toString(),
					word);
			if (quoteLocs.contains(startend[0])) {
				// if the double quote was original translated we should move
				// the end pointer one
				startend[1]++;
			}
		} else {
			startend = findSpan(location, input, word);
		}
		return startend;
	}


	/**
	 * Finds the span (as start and end indices) where the word occurs in the
	 * rawText starting at from.
	 * 
	 * @param from
	 * @param rawText
	 * @param word
	 * @return
	 */
	private  static int[] findSpan(int from, String rawText, String word) {
		int start = rawText.indexOf(word, from);
		if(start>0) {
			int end = start + word.length();
			return new int[] { start, end };
		} else {
			// find the span brutally (very brute force...)			
			for(start = from; start<rawText.length(); start++) {
				String sub = rawText.substring(start, Math.min(start+5+word.length()*2, rawText.length()));
				if(PlainTextReader.normalizeText(sub).startsWith(word)) {
					for(int end = start+word.length(); end<start+Math.min(start+5+word.length()*2, rawText.length()); end++) 
						if(PlainTextReader.normalizeText(rawText.substring(start,end)).equals(word)) 
							return new int[]{start, end};
				}
			}			
			System.out.println("Critical warning: word "+word+" is not found in the text "+ rawText.substring(start,rawText.length()));
			return new int[]{0, 0};
		}
	}
	
	private static Constituent makeConstituentFixTokenBoundaries(String label, String viewName, TextAnnotation ta, int start, int end) {
        int e = end - 1;
        if (e < start)
            e = start;
		Constituent c = null;
		try {
			c = new Constituent(label, viewName, ta, ta.getTokenIdFromCharacterOffset(start), ta.getTokenIdFromCharacterOffset(e)+1);
			if(ta.getText().substring(start, end).equals(c.getSurfaceString()))
				return c;

			c = new Constituent(label, viewName, ta, ta.getTokenIdFromCharacterOffset(start), ta.getTokenIdFromCharacterOffset(end));
			if(ta.getText().substring(start, end).equals(c.getSurfaceString()))
				return c;
			c = new Constituent(label, viewName, ta, ta.getTokenIdFromCharacterOffset(start), ta.getTokenIdFromCharacterOffset(end)+1);
			if(ta.getText().substring(start, end).equals(c.getSurfaceString()))
				return c;
		} catch (Exception ex) {			
		}
		return null;
	}

	public static void main(String[] args) throws Exception {
		
		String s= "Michael NBA.";
		TextAnnotation ta1 = createAnnotation(s);
		Constituent c1 = makeConstituentFixTokenBoundaries("LABEL", ViewNames.NER, ta1, 8, 11);
		System.out.println(c1.getSurfaceString()+"\t-\t"+s.substring(8, 11));
		
		s= "NBA.";
		ta1 = createAnnotation(s);
		c1 = new Constituent("LABEL", 
				ViewNames.NER, ta1, ta1.getTokenIdFromCharacterOffset(0), 
				ta1.getTokenIdFromCharacterOffset(3));
		System.out.println(c1.getSurfaceString()+"\t-\t"+s.substring(0, 3));
		
		FakeCurator curator = new FakeCurator("configs/NER.config");
		TextAnnotation ta = curator.annotate("The long ousted Egyptian President Hosni Mubarak" +
				" said in WASHINGTON (AP) _ All else being equal for Detroit Red Wings, " +
				"Duane Roelands would prefer to dash off short instant text messages to " +
				"co-workers and friends with the service offered by Microsoft _ the one he " +
				"finds easiest to use. But for Roelands, all else is not equal: His office, " +
				"clients and nearly everyone else he knows use America Online's messaging " +
				"system. Now, he does too. ``There are features that I want and I like,'' " +
				"said Roelands, a Web developer, who likens it to the battle between VHS and" +
				" Beta video recorders in the 1980s. ``But the reality is if I use the better" +
				" product, I get less functionality.'' For this reason, instant messaging " +
				"rivals like Microsoft, AT&AMP;T and ExciteAtHome maintain their users ought" +
				" to be able to send messages to anyone else, regardless of what service they" +
				" happen to have. That's not currently possible. The companies are lobbying " +
				"the Federal Communications Commission to require AOL to make its product " +
				"compatible with those offered by competitors as a condition of its merger " +
				"with Time Warner. So far, the agency appears to favor a more tailored approach" +
				". The commission's staff has recommended that AOL be required to make its " +
				"system work with at least one other provider, but the requirement would apply" +
				" only to advanced instant messaging services offered over Time Warner's cable" +
				" lines. How the agency defines advanced services is unclear. They could refer" +
				" to features beyond text messaging, such as video teleconferencing, the sharing" +
				" of files or messaging over interactive television. Today, consumers more " +
				"commonly take advantage of the garden variety functions. They type short " +
				"real-time phrases to others, allowing them to ``chat'' back-and-forth using" +
				" text. Unlike e-mail, it's instantaneous and gets the recipient's attention" +
				" right away. People can communicate with international friends without the " +
				"hefty phone bills. And the service has taken hold with those who have hearing" +
				" or speech disabilities. Unlike the telephone, people can discreetly interact" +
				" with others _ or decide not to. ``It's communications that can be ignored,''" +
				" said Jonathan Sacks, a vice president at AOL, which runs the two leading " +
				"messaging services _ ICQ and AIM _ with 140 million users. ``On the telephone" +
				", you can't see when somebody is near the phone. You can't see when it's " +
				"convenient for them to communicate with you.'' AOL rivals say that if instant" +
				" messaging is to be as ubiquitous as the phone network, it has to work the " +
				"same way: People who use different providers must still be able to contact " +
				"one another. They continue to lobby the FCC, hoping to see the conditions " +
				"broadened before the agency issues its final decision. ``It's really important" +
				" to get this right before innovation is squashed because one company has " +
				"a monopoly,'' said Jon Englund, vice president of government affairs for " +
				"ExciteAtHome. ``It's absolutely critical that Internet uses have real choice" +
				" among competing platforms.'' AOL has said it wants to work toward " +
				"interoperability, but first needs to protect consumer privacy and security " +
				"to prevent the kinds of problems that have emerged in the e-mail world, like" +
				" spamming _ unwanted junk messages. Company officials disagreed that AOL's " +
				"market share was keeping out competitors. AOL executives cited a recent study " +
				"by Media Metrix indicating that the messaging services offered by Yahoo! and " +
				"Microsoft are the fastest growing in the United States. Why all the fuss over " +
				"a free product that anyone, even those who don't subscribe to AOL, can use? Some" +
				" pointed to the recent demise of two instant messaging competitors _ iCAST and" +
				" Tribal Voice _ as evidence that AOL's dominance could prevent choices in the " +
				"market. Another concern is that AOL could use its substantial customer base to" +
				" tack on new advanced services and then charge for them. Rivals said the ability" +
				" of various services to work together will become increasingly important in the" +
				" future. For example, as instant messaging migrates to cell phones or hand-held" +
				" computer organizers, consumers won't want to have to install multiple services " +
				"on these devices, said Brian Park, senior product for Yahoo! Communications Services." +
				" ``You can have the best service and the coolest features, but nobody is going to " +
				"use it if it doesn't communicate with other services,'' Park said. ___ On the Net: " +
				"America Online corporate site: http://corp.aol.com IMUnified, coalition formed by" +
				"AT&AMP;T, ExciteAtHome, Microsoft: http://www.imunified.org/ ");
		View nerView = ta.getView(ViewNames.NER);
		for (Constituent c : nerView) 
			System.out.println(c.getSurfaceString()+"\t-\t"+c.getLabel());
		View chunkView = ta.getView(ViewNames.SHALLOW_PARSE);
		for (Constituent c : chunkView) 
			System.out.println(c.getSurfaceString()+"\t-\t"+c.getLabel());
		View posView = ta.getView(ViewNames.POS);
		for (Constituent c : posView) 
			System.out.println(c.getSurfaceString()+"\t-\t"+c.getLabel());
	}
	
	public static TextAnnotation createAnnotation(String text){
	    return new TextAnnotation("fakeCorpus","fakeId", text,SentenceViewGenerators.LBJSentenceViewGenerator);
	}
}
