package edu.illinois.cs.cogcomp.wikifier.models;

import java.io.Closeable;
import java.io.Serializable;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.concurrent.BackgroundInitializer;
import org.apache.commons.lang3.concurrent.ConcurrentException;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import edu.illinois.cs.cogcomp.edison.sentences.Constituent;
import edu.illinois.cs.cogcomp.edison.sentences.CoreferenceView;
import edu.illinois.cs.cogcomp.edison.sentences.SpanLabelView;
import edu.illinois.cs.cogcomp.edison.sentences.TextAnnotation;
import edu.illinois.cs.cogcomp.edison.sentences.View;
import edu.illinois.cs.cogcomp.edison.sentences.ViewNames;
import edu.illinois.cs.cogcomp.wikifier.annotation.Coreference;
import edu.illinois.cs.cogcomp.wikifier.common.GlobalParameters;
import edu.illinois.cs.cogcomp.wikifier.inference.RelationSolver;
import edu.illinois.cs.cogcomp.wikifier.inference.RelationSolver.SolverType;
import edu.illinois.cs.cogcomp.wikifier.inference.coref.CorefClusters;
import edu.illinois.cs.cogcomp.wikifier.inference.coref.CorefResolver;
import edu.illinois.cs.cogcomp.wikifier.inference.relation.NPAnalysis;
import edu.illinois.cs.cogcomp.wikifier.models.Mention.SurfaceType;
import edu.illinois.cs.cogcomp.wikifier.models.extractors.MentionExtractor;
import edu.illinois.cs.cogcomp.wikifier.models.tfidf.TFIDF.TF_IDF_Doc;
import edu.illinois.cs.cogcomp.wikifier.train.TrainWikifierSVM.GoldAnnotation;
import edu.illinois.cs.cogcomp.wikifier.utils.Comparators;
import edu.illinois.cs.cogcomp.wikifier.utils.WordFeatures;
import edu.illinois.cs.cogcomp.wikifier.utils.datastructure.ConstituentMap;
import edu.illinois.cs.cogcomp.wikifier.utils.io.OutFile;
import gnu.trove.map.hash.TCustomHashMap;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;
/**
 * Please close this class after annotation as reference instances'
 * assignedEntity will leak memory through back reference
 * @modified cheng88
 *
 */
public class LinkingProblem implements Serializable,Closeable {
    /**
	 * context-free gtr
	 */
    private static final long serialVersionUID = -4261143260930991069L;

    public static volatile long timeConsumedConstructingProblems = 0;

    public String sourceFilename = null;
    public String text = null;
    // additional data
    public transient TF_IDF_Doc textVec = null;
    public List<Mention> components = null;
    public List<CoherenceRelation> relations = null;
    public TextAnnotation ta = null;

    public View entityView = null;
    private TCustomHashMap<Constituent, Mention> entityMap = null;
    
    private Map<Mention,Set<Mention>> corefExceptions = new HashMap<>();
    
    private BackgroundInitializer<View> corefView = new BackgroundInitializer<View>() {
        @Override
        protected View initialize() throws Exception {
            if(ta.hasView(ViewNames.COREF))
                return ta.getView(ViewNames.COREF);
            return Coreference.annotateTA(ta);
        }
    };
    
    private BackgroundInitializer<View> mentionView = new BackgroundInitializer<View>() {
        @Override
        protected View initialize() throws Exception {
            View mentionView = null;// problem.getCorefView();
            View cView = null;
            // When coref is required, check mention views in coref
            if (ta.hasView(ViewNames.COREF))
                cView = ta.getView(ViewNames.COREF);
            if (cView == null && corefView.isStarted())
                cView = corefView.get();
            
            if (cView != null && Coreference.isMentionView(cView))
                mentionView = cView;
            if (mentionView == null)
                mentionView = Coreference.getMentionView(ta);
            return mentionView;
        }
    };

    public LinkingProblem() {}

    public LinkingProblem(String sourceFilename, TextAnnotation ta, List<ReferenceInstance> referenceInstances) throws Exception {
        this.sourceFilename = sourceFilename;
        this.text = ta.getText();
        this.ta = ta;
        if(GlobalParameters.settings.annotateCOREF)
            corefView.start();
        if(GlobalParameters.params.USE_RELATIONAL_INFERENCE)
            mentionView.start();
        System.out.println("Constructing a problem for the following text: \n" + StringUtils.abbreviate(text, 200));
        long firstTime = System.currentTimeMillis();
        long afterTFIDFTime = firstTime;
        
        if (GlobalParameters.params.useLexicalFeaturesNaive || GlobalParameters.params.useLexicalFeaturesReweighted) {
            this.textVec = GlobalParameters.wikiAccess.getWikiSummaryData().getTextRepresentation(text, true);
            System.out.println(System.currentTimeMillis() - firstTime
                    + " milliseconds elapsed on constructing the TF-IDF representation of the input text..." + sourceFilename);
            afterTFIDFTime = System.currentTimeMillis();
        }
        System.out.println("Getting the wikifiable mentions candidates");
        
        components = MentionExtractor.extract(this, referenceInstances, GlobalParameters.wikiAccess);
        Collections.sort(components,Comparators.earlierEntityFirst);
        // Locking list to prevent tampering
        components = Collections.unmodifiableList(components);

        System.out.println("     ----  almost there....");
        if (referenceInstances != null) {
            GoldAnnotation gold = new GoldAnnotation(referenceInstances);
            for (Mention e : components) {
                e.goldCandidate = gold.getGoldDisambiguationCandidate(e, true);
            }
        }
        System.out.println(System.currentTimeMillis() - afterTFIDFTime
                + " milliseconds elapsed on constructing potentially wikifiable entitites in the input text..." + sourceFilename);
        timeConsumedConstructingProblems += (System.currentTimeMillis() - firstTime);
    }

    /*
     * Sometimes, we get entities that overlap, for example Florida Police Department. If we have
     * entity overlap, we want to take the non-overlapping subset with the highest score
     */

    public void resolveOverlapConflicts() throws Exception {
        System.out.println("-- Removing entities with overlapping mentions in text --");
        TIntSet takenTokens = new TIntHashSet();
        for (Mention c : getSortedMentions(Comparators.longerEntityFirst)) {
            boolean conflictsWithAbetterThing = false;

            for (int j = c.startTokenId; j < c.endTokenId; j++)
                if (takenTokens.contains(j))
                    conflictsWithAbetterThing = true;
            if (conflictsWithAbetterThing)
                c.finalCandidate = null;
            else {
                for (int j = c.startTokenId; j < c.endTokenId; j++)
                    takenTokens.add(j);
            }
        }
    }
    
    public Mention getComponent(Constituent c){
        return entityMap.get(c);
    }
    
    /**
     * Transforms the data structure for efficient inference. Must be called prior to performing
     * structural rerank. Otherwise there may be some necessary fields that are not filled.
     */
    public void prepareForRerank() {
        for (Mention e : components) {
            e.prepareForStructuralRerank(false);
        }
        entityView = new SpanLabelView(ViewNames.WIKIFIER, "WikifierVer3", ta, 1.0,true);
        ta.addView(ViewNames.WIKIFIER,entityView);

        entityMap = new ConstituentMap<Mention>();

        for (Mention e : components) {
            Constituent c = new Constituent("", ViewNames.WIKIFIER, ta, e.startTokenId, e.endTokenId);
            entityMap.put(c, e);
            e.constituent = c;
            entityView.addConstituent(c);
        }
    }

    public String wikificationString(boolean displayErrors) throws Exception {
        StringBuilder res = new StringBuilder();
        int lastEnd = 0;
        List<Mention> costarts = Lists.newArrayList();
        for (Mention m : components) {
            
            if (m.finalCandidate == null || m.getLinkability()<0.01)
                continue;

            if (costarts.size() == 0 || costarts.get(0).startTokenId == m.startTokenId) {
                costarts.add(m);
                continue;
            }
            
            // Dequeue and enqueue
            Collections.sort(costarts,Comparators.longerEntityFirst);
            
            for(Mention e:costarts)
                if (e.finalCandidate != null 
                        && (e.charStart > lastEnd || lastEnd == 0)) {
                    
                    String title = e.topCandidate.titleName;
                    if(e.finalCandidate != null)
                        title = e.finalCandidate.titleName; 
                    res.append(text.substring(lastEnd, e.charStart));
                    if(e.getLinkability()<0.05 || WordFeatures.containsNoUpperCase(e.surfaceForm))
                        res.append(" <b>")
                        .append(e.surfaceForm)
                        .append("</b>");
                    else
                        res.append(" <a class=\"wiki\" href=\"http://en.wikipedia.org/wiki/")
                        .append(title)
                        .append("\" cat=\"")
                        .append(StringUtils.join(GlobalParameters.getCategories(title), '\t'))
                        .append("\">")
                        .append(e.surfaceForm)
                        .append("</a> ");
                    
                    
                    if (displayErrors && e.isCorrectPrediction == Mention.WikificationCorrectness.Incorrect)
                        res.append(" <a href=\"http://en.wikipedia.org/wiki/" + e.goldAnnotation + "\"> [ERROR] </a>  ");
                    lastEnd = e.charStart + e.charLength;
                    break;
                }
            costarts.clear();
            costarts.add(m);
        }
        res.append(text.substring(lastEnd));
        return res.toString().replace("\n", "<br>");
    }

    public String mappedEntitiesAsString() throws Exception {
        StringBuffer res = new StringBuffer();
        for (int i = 0; i < components.size(); i++) {
            Mention e = components.get(i);
            String disambiguation = "*null*";
            if (e.finalCandidate != null) {

                disambiguation = "(tid=" + e.finalCandidate.getTid() + ", Title name="
                        + e.finalCandidate.titleName + ")";
            }
            res.append("*Entity:[" + e.surfaceForm + ",textSpan=(" + e.charStart + "-" + (e.charStart + e.charLength)
                    + ")]\t-Disambiguation:" + disambiguation + " gold wikification:=" + e.goldAnnotation + " isCorrectPrediction="
                    + e.isCorrectPrediction + "\n");
        }
        return res.toString();
    }

    /**
     * Generates a filtered and ordered entity list for further analysis
     * 
     * @param components
     * @return sorted entities
     */
    public List<Mention> getSortedMentions() {
        return components;
    }

    public List<Mention> getSortedMentions(Comparator<Mention> comparator) {
        List<Mention> sortedList = Lists.newArrayList(components);
        Collections.sort(sortedList, comparator);
        return sortedList;
    }
    
    public CoreferenceView getCorefView() {
        if(ta.hasView(ViewNames.COREF))
            return (CoreferenceView) ta.getView(ViewNames.COREF);
        try {
            return corefView.isStarted() ? (CoreferenceView) corefView.get() : null;
        } catch (ConcurrentException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void deepRelationalInference(){

        resolveCoherenceRelations();

        if (GlobalParameters.params.USE_RELATIONAL_INFERENCE || GlobalParameters.params.USE_COREF) {
            try {
                RelationSolver solver = new RelationSolver(this, SolverType.GUROBI);
                long start = System.currentTimeMillis();
                solver.solve();
                System.out.println("Relational inference took " + (System.currentTimeMillis() - start) + "ms");
                solver.explain();

            } catch (Exception e) {
                System.out.println("Relational inference failed: ");
                e.printStackTrace(System.out);
                System.exit(0);
            }
        }
        // We used a different data structure for structural inference
        // here we sync it back to the default candidate list
        syncPredictions();
    }

    

    /**
     * Generates new candidates and appending resolved relations to the problem
     * 
     * @param relational
     *            window size
     * @return
     * @throws Exception
     */
    private void resolveCoherenceRelations() {

        prepareForRerank();
        augmentNER();
        relations = Lists.newArrayList();

        // Inject new global candidates that matches exactly except for cases and punctuation
        if (GlobalParameters.params.USE_LEXICAL_SEARCH_HEURSTICS) {
            for (Mention e : components)
                e.cleanCurrentDecision();
        }

        if (GlobalParameters.params.USE_RELATIONAL_INFERENCE) {
            NPAnalysis.infer(this);
        }
        
        // We have to get this for both relational and coref
        // Heuristic co-reference, including acronyms, should be last step as it
        // depends on newly added entities
        CorefClusters clusters = null;
        if(GlobalParameters.params.USE_RELATIONAL_INFERENCE || GlobalParameters.params.USE_COREF){
            clusters = CorefResolver.generateClusters(this);
            CorefResolver.resolveCorefRelations(this, clusters);
        }
    }

    
    private static final Pattern cap = Pattern.compile("\\b[A-Z]");
    
    /**
     * Augments NER results by injecting Wikipedia entities
     */
    private void augmentNER() {
        View ner = ta.getView(ViewNames.NER);
        for(Mention e:components){
            if(e.topCandidate!=null 
                    && !e.isNamedEntity() 
                    && e.isTopLevelMention()
                    && cap.matcher(e.surfaceForm).find()){
                
                for(String cat:GlobalParameters.getCategories(e.topCandidate.titleName)){
                    switch (cat) {
                    case "person":
                        e.types = EnumSet.of(SurfaceType.NER,SurfaceType.PER);
                        break;
                    case "location":
                    case "place":
                        e.types = EnumSet.of(SurfaceType.NER,SurfaceType.LOC);
                        break;
                    case "organization":
                        e.types = EnumSet.of(SurfaceType.NER,SurfaceType.ORG);
                        break;
                    }
                }
                
                if(e.isNamedEntity()){
                    for (SurfaceType type : Sets.intersection(SurfaceType.getNERTypes(), e.types)) {
                        List<Constituent> currentNEs = ner.getConstituentsCoveringSpan(e.startTokenId,e.endTokenId);
                        if (currentNEs.size() > 0 && e.tokenLength() <= 1)
                            continue;
                        ner.addConstituent(new Constituent(type.name(), ViewNames.NER, ta, e.startTokenId, e.endTokenId));
                    }
                }
            }
        }
        
    }


    /**
     * Structural inference has a different data structure from the normal representation
     */
    private void syncPredictions() {
        for (Mention e : components) {
            e.syncCandidates();
        }
    }

    public void printFinalSolutions() {
        for (Mention e : getSortedMentions()) {
            if ("*unknown*".equals(e.goldAnnotation))
                continue;
            WikiCandidate sol = e.finalCandidate;
            String correctness = sol != null && e.goldAnnotation.equals(sol.titleName) ? "correct:" : "wrong:";
            String notes = correctness.startsWith("wrong") ? e.goldAnnotation : "";
            System.out.println(correctness + sol + e.getCurrentDecisionConfusion() + " " + notes);
        }
    }

    /*
     * This one saves the result of a tagging in the "reference problem" format
     */
    public void saveAsReferenceProblem(String outFilename) {
        String problem = "<ReferenceProblem>\n\t<ReferenceFileName>\n\t\t" + sourceFilename + "\n\t</ReferenceFileName>\n";
        for (Mention e : components) {
            if (e.finalCandidate != null) {
                String link = ("http://en.wikipedia.org/wiki/" + e.finalCandidate.titleName).replace(' ', '_');
                problem += "<ReferenceInstance>\n\t<SurfaceForm>\n\t\t" + e.surfaceForm + "\n\t</SurfaceForm>\n\t<Offset>\n\t\t"
                        + e.charStart + "\n\t</Offset>\n\t<Length>\n\t\t" + e.charLength + "\n\t</Length>\n\t<ChosenAnnotation>\n\t\t"
                        + link + "\n\t</ChosenAnnotation>\n\t<NumAnnotators>\n\t\t1\n\t</NumAnnotators>\n\t"
                        + "<AnnotatorId>\n\t\tCogcompWikifier\n\t</AnnotatorId>\n\t<Annotation>\n\t\t" + link
                        + "\n\t</Annotation>\n</ReferenceInstance>\n";
            }
        }
        problem += "</ReferenceProblem>\n";
        OutFile file = new OutFile(outFilename);
        file.println(problem);
        file.close();
    }
    
    public View getMentionView(){
        try {
            return mentionView.get();
        } catch (ConcurrentException e) {
            e.printStackTrace();
        }
        return null;
    }

    
    public List<Mention> getContext(int pos,int charWindow){
        int start = pos;
        int end = pos;
        int center = components.get(pos).charStart;
        for(int i=pos-1;i>=0;i--){
            if(center - components.get(i).charStart <charWindow)
                start = i;
            else
                break;
        }
        for(int i=pos+1;i<components.size();i++){
            if(components.get(i).charStart-center<charWindow)
                end = i;
            else
                break;
        }
        return Collections.unmodifiableList(components.subList(start, end + 1));
    }

    public void addCorefException(Mention m1,Mention m2){
        Set<Mention> nonCorefCluster = corefExceptions.get(m1);
        if(nonCorefCluster == null){
            nonCorefCluster = new HashSet<>();
            nonCorefCluster.add(m1);
            corefExceptions.put(m1, nonCorefCluster);
        }
        Set<Mention> nonCorefCluster2 = corefExceptions.get(m2);
        if(nonCorefCluster2 == null){
            nonCorefCluster.add(m2);
            corefExceptions.put(m2, nonCorefCluster);
            return;
        }
        nonCorefCluster.addAll(nonCorefCluster2);
        corefExceptions.put(m2, nonCorefCluster);
    }
    
    public boolean isCorefException(Mention m1,Mention m2){
        return corefExceptions.get(m1).contains(m2);
    }
    
    public Set<Mention> getCorefExceptions(Mention m) {
        Set<Mention> nonCoref = corefExceptions.get(m);
        if (nonCoref == null)
            return Collections.emptySet();
        return nonCoref;
    }

    @Override
    public void close(){
        for(Mention e:components){
            e.close();
        }
    }
    
    public String toString(){
        return text;
    }
    

}
