package edu.illinois.cs.cogcomp.annotation.handler;

import static edu.illinois.cs.cogcomp.edison.sentences.ViewNames.*;
import static java.util.Arrays.*;
import static java.util.Collections.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.illinois.cs.cogcomp.edison.data.curator.CuratorClient;
import edu.illinois.cs.cogcomp.edison.data.curator.CuratorDataStructureInterface;
import edu.illinois.cs.cogcomp.edison.sentences.Constituent;
import edu.illinois.cs.cogcomp.edison.sentences.TextAnnotation;
import edu.illinois.cs.cogcomp.edison.sentences.TokenizerUtilities.SentenceViewGenerators;
import edu.illinois.cs.cogcomp.thrift.base.AnnotationFailedException;
import edu.illinois.cs.cogcomp.thrift.base.Labeling;
import edu.illinois.cs.cogcomp.thrift.base.Span;
import edu.illinois.cs.cogcomp.thrift.curator.Record;
import edu.illinois.cs.cogcomp.thrift.labeler.Labeler.Iface;
import edu.illinois.cs.cogcomp.wikifier.common.GlobalParameters;
import edu.illinois.cs.cogcomp.wikifier.inference.InferenceEngine;
import edu.illinois.cs.cogcomp.wikifier.models.LinkingProblem;
import edu.illinois.cs.cogcomp.wikifier.models.Mention;
import edu.illinois.cs.cogcomp.wikifier.utils.io.InFile;
/**
 * Handles Wikfier TextAnnotation generation
 * @author cheng88
 *
 */
public class IllinoisWikifierHandler extends IllinoisAbstractHandler implements Iface {

    public static final List<String> requiredViews = unmodifiableList(asList(POS, SHALLOW_PARSE, NER));
    private static final String WIKI_KEY = "wikifier";

    private static Logger logger = LoggerFactory.getLogger(IllinoisWikifierHandler.class);

    private InferenceEngine inference = null;
    private CuratorClient client = null;
    private boolean forceUpdate = false;

    public IllinoisWikifierHandler(String configFile, String curatorHost, int curatorPort) throws Exception {
        super("illinoiswikifier", "2.0", "illinoiswikifier");

        GlobalParameters.loadConfig(configFile);
        client = new CuratorClient(curatorHost, curatorPort);
        inference = new InferenceEngine(false);
    }

    public IllinoisWikifierHandler(String configFile) throws Exception {
        super("illinoiswikifier", "2.0", "illinoiswikifier");

        System.err.println("## IllinoisWikifierHandler( configfile ) -- constructor for wikifier as curator component...");
        GlobalParameters.loadConfig(configFile);
        inference = new InferenceEngine(false);
    }

    /**
     * for use ONLY when Wikifier is a Curator Component; sidesteps ALL CACHING; assumes Curator
     * will NOT be called to annotate text as all annotations should be in the input record
     * 
     * @param record
     * @return
     * @throws AnnotationFailedException
     * @throws TException
     */
    public String annotateRecord(Record record) throws AnnotationFailedException, TException {
        System.err.printf("## IllinoisWikifierHandler.annotateRecord()...\n");
        String taggedText = "";
        try {

            TextAnnotation ta = CuratorDataStructureInterface.getTextAnnotationViewsFromRecord("", "", record);

            if (!checkViews(ta))
                throw new IllegalArgumentException("Required views not found. Existing views are: " + ta.getAvailableViews());

            // GlobalParameters.curator.curatorComponentTa = ta;

            record.putToLabelViews(WIKI_KEY, tagText(ta));

            logger.info("Wikifier: annotated text... returning to Curator...");
            // LinkingProblem prob= new LinkingProblem("serverinput",ta.getText(),new
            // List<ReferenceInstance>());
            // inference.annotate(prob, null, false, false, 0);
            // taggedText = prob.wikificationString(false);

        } catch (Exception e) {
            throw new AnnotationFailedException("Failed to annotate the text :\n" + record.rawText + "\nThe exception was: \n"
                    + e.toString());
        }

        return taggedText;
    }

    public String annotateText(String input) throws AnnotationFailedException, TException {
        System.err.println("##IllinoisWikifierHandler.annotateText()...");
        System.out.println("Input text: '" + input + "'.");
        String output = "";
        TextAnnotation ta = getTextAnnotation(input);
        try {
            LinkingProblem prob = new LinkingProblem("serverinput", ta, null);
            inference.annotate(prob, null, false, false, 0);
            output = prob.wikificationString(false);
        } catch (Exception e) {
            e.printStackTrace();
            throw new AnnotationFailedException("Failed to annotate the text :\n" + input + "\nThe exception was: \n" + e.toString());
        }

        return output;
    }

    private TextAnnotation getTextAnnotation(String input) throws AnnotationFailedException {
        System.err.println("##IllinoisWikifierHandler.getTextAnnotation()...");
        String corpId = "wikifier_input";
        String spanId = "wikifier_span";
        TextAnnotation ta = new TextAnnotation(corpId, spanId, input, SentenceViewGenerators.LBJSentenceViewGenerator);

        if (client != null)
            try {
                client.addChunkView(ta, forceUpdate);
                client.addNamedEntityView(ta, forceUpdate);
                client.addPOSView(ta, forceUpdate);
            } catch (Exception e) {
                throw new AnnotationFailedException("IllinoisWikifierHandler.getTextAnnotation(): " + "Failed to annotate the text :\n"
                        + input + "\nThe exception was: \n" + e.toString());
            }

        else {
            throw new AnnotationFailedException("IllinoisWikifierHandler.getTextAnnotation(): "
                    + " this shouldn't be called when IllinoisWikifierHandler is a Curator component!!!");
        }

        return ta;
    }

    /**
     * assumes that TextAnnotation has already been created one way or another.
     * @throws Exception 
     */

    public Labeling tagText(TextAnnotation ta) throws Exception {

        String text = ta.getText();
        System.out.println("IllinoisWikifierHandler.tagText()...\n----------Input text: " + text);

        Labeling labeling = new Labeling();
        List<Span> labels = new ArrayList<Span>();
        Map<String, Span> knownSpans = new HashMap<String, Span>();

        System.err.println("## IllinoisWikifierHandler.tagText(): instantiating LinkingProblem... ");
        LinkingProblem prob = new LinkingProblem("serverinput", ta, null);
        System.err.println("## IllinoisWikifierHandler.tagText(): calling inference engine... ");
        inference.annotate(prob, null, false, false, 0);
        System.err.println("## IllinoisWikifierHandler.tagText(): done with calling inference engine... ");

        /*
         * This takes care of the Wikifier output. However, notice that I'll be assigning some
         * Wikipedia categories to non-Wikified expressions as well...
         */
        for (Mention e : prob.components) {
            
            if(e.topCandidate == null)
                continue;
            
            String title = e.topCandidate.titleName;
            if(e.finalCandidate != null)
                title = e.finalCandidate.titleName;
            
            String disambiguation = "http://en.wikipedia.org/wiki/" + title;
            Span span = new Span(e.charStart, e.charStart + e.charLength);
            knownSpans.put(span.getStart() + "-" + span.getEnding(), span);
            Map<String, String> attributes = new HashMap<String, String>();
            attributes.put("IsLinked", String.valueOf(e.finalCandidate != null && e.getLinkability() >0.05));
            attributes.put("LinkerScore", String.valueOf(e.linkerScore));
            attributes.put("RankerScore", String.valueOf(e.topCandidate.rankerScore));

            String titleWikiCats = "";
            if (GlobalParameters.hasTitle(title)) {
                titleWikiCats = StringUtils.join(GlobalParameters.getCategories(title), '\t');
            } else {
                attributes.put("TitleMismatchError", "Title:" + title + "does not appear in the titles to categories index."
                        + " The error is probably due to mismatch in Wikipedia versions");
            }
            attributes.put("TitleWikiCatAttribs", titleWikiCats);

            List<String> tokens = InFile.aggressiveTokenize(e.surfaceForm.toLowerCase());
            String surfaceFormsAttribs = "";
            for (String token : tokens) {
                String lemma = GlobalParameters.wordnet.getLemma(token.toLowerCase(), net.didion.jwnl.data.POS.NOUN);
                if (GlobalParameters.wikiAccess.getWikiSummaryData().normalizedCatTokenToId.containsKey(lemma))
                    surfaceFormsAttribs += "\t" + lemma;
            }
            attributes.put("SurfaceFormWikiCatAttribs", surfaceFormsAttribs);
            span.setAttributes(attributes);
            span.setAttributesIsSet(true);
            span.setLabel(disambiguation);
            span.setScore(e.linkerScore);
            labels.add(span);
//            System.out.println("*) Entity:" + e.surfaceForm + "; disambiguation: " + disambiguation);
        }

        int N = ta.getTokens().length;
        for (int tid = 0; tid < N; tid++) {
            String pos = ta.getView(POS).getConstituentsCoveringToken(tid).get(0).getLabel();
            // if the word is part of an NER, don't generate attributes. For example,
            // "NY Giants" is not a real giant.
            // also, use only the noun senses - not the adjectives or the verbs...
            if (ta.getView(NER).getConstituentsCoveringToken(tid).size() == 0 && (pos.startsWith("NN") || pos.startsWith("JJ"))) {
                Constituent c = new Constituent("", "", ta, tid, tid + 1);
                Span entity = new Span(c.getStartCharOffset(), c.getEndCharOffset());
                String key = entity.getStart() + "-" + entity.getEnding();
                if (!knownSpans.containsKey(key)) {
                    List<String> tokens = InFile.aggressiveTokenize(ta.getToken(tid).toLowerCase());
                    String surfaceFormsAttribs = "";
                    for (String token : tokens) {
                        String lemma = GlobalParameters.wordnet.getLemma(token.toLowerCase(), net.didion.jwnl.data.POS.NOUN);
                        if (GlobalParameters.wikiAccess.getWikiSummaryData().normalizedCatTokenToId.containsKey(lemma))
                            surfaceFormsAttribs += "\t" + lemma;
                    }
                    if (surfaceFormsAttribs.length() > 0) {
                        knownSpans.put(key, entity);
                        Map<String, String> attributes = new HashMap<String, String>();
                        attributes.put("IsLinked", "false");
                        attributes.put("LinkerScore", "-999.0");
                        attributes.put("RankerScore", "-999.0");
                        attributes.put("TitleWikiCatAttribs", "");
                        attributes.put("SurfaceFormWikiCatAttribs", surfaceFormsAttribs);
                        entity.setAttributes(attributes);
                        entity.setLabel("UNMAPPED");
                        entity.setScore(-999.0);
                        labels.add(entity);
                    }
                }
            }
        }
        labeling.setLabels(labels);

        labeling.setSource(getSourceIdentifier());
        return labeling;
    }

    public Labeling labelRecord(Record record) throws AnnotationFailedException, TException {
        String text = record.rawText;
        try {
            TextAnnotation ta;
            if (client == null) {
                ta = CuratorDataStructureInterface.getTextAnnotationViewsFromRecord("", "", record);

                if (!checkViews(ta))
                    throw new IllegalArgumentException("Required views not found. Existing views are: " + ta.getAvailableViews());

            } else {
                ta = GlobalParameters.curator.getTextAnnotation(text);
            }

            Labeling labeling = tagText(ta);
            return labeling;
        } catch (Exception e) {
            throw new AnnotationFailedException("Failed to annotate the text :\n" + record.rawText + "\nThe exception was: \n"
                    + ExceptionUtils.getStackTrace(e));
        }
    }

    private boolean checkViews(TextAnnotation ta) {
        return ta.getAvailableViews().containsAll(requiredViews);
    }
}