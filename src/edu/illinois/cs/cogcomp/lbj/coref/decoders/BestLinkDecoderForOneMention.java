package edu.illinois.cs.cogcomp.lbj.coref.decoders;

import java.io.PrintStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

import LBJ2.classify.Classifier;
import LBJ2.learn.Learner;
import edu.illinois.cs.cogcomp.lbj.coref.constraints.Constraint;
import edu.illinois.cs.cogcomp.lbj.coref.constraints.GenderMismatchConstraints;
import edu.illinois.cs.cogcomp.lbj.coref.constraints.IdenticalDetNom;
import edu.illinois.cs.cogcomp.lbj.coref.constraints.IdenticalNonPronoun;
import edu.illinois.cs.cogcomp.lbj.coref.constraints.ListMismatchConstraints;
import edu.illinois.cs.cogcomp.lbj.coref.constraints.ModifierMismatchConstraints;
import edu.illinois.cs.cogcomp.lbj.coref.constraints.NestedPossessivePronoun;
import edu.illinois.cs.cogcomp.lbj.coref.constraints.PronounMismatchConstraints;
import edu.illinois.cs.cogcomp.lbj.coref.features.ContextFeatures;
import edu.illinois.cs.cogcomp.lbj.coref.features.PronounResolutionFeatures;
import edu.illinois.cs.cogcomp.lbj.coref.ir.Mention;
import edu.illinois.cs.cogcomp.lbj.coref.ir.docs.Doc;
import edu.illinois.cs.cogcomp.lbj.coref.ir.examples.CExample;
import edu.illinois.cs.cogcomp.lbj.coref.ir.solutions.ChainSolution;

/**
 * Translates classification decisions to a collection of coreference
 * equivalence classes in the form of a {@code ChainSolution} via the decode
 * method according to the best link decoding algorithm. The best link decoding
 * method specifies that for each mention {@code m} a link will be produced with
 * highest scoring preceding mention {@code a} only if
 * {@link #predictedCoreferential(CExample)} returns true for the example
 * {@code doc.getCExampleFor(a, m)}. Also, allows several options to be set that
 * modify the performance of the best link decoding algorithm. See the relevant
 * setter methods for details. This classifier allows to specify a seperate classifier
 * for pronoun resolution.
 * @author Kai-Wei Chang
 */
public class BestLinkDecoderForOneMention extends ScoredCorefDecoder implements
Serializable {
    private static final long serialVersionUID = 1L;
    public boolean skipProuns = false;
    public boolean noAllowProPro = false;
    public Learner pronounClassifer = null;
    public boolean linkallPersonal = false;
    public double pronounThreshold = 0.0;
    public static Map<String, Double> scoreCache;
    
    /* Option variables */

    /** Whether to allow cataphora. */
    protected boolean m_allowCataphora = false;

    /** Whether to prevent long distance pronoun reference. */
    protected boolean m_preventLongDistPRO = true;

    /** Currently does nothing. */
    protected boolean m_experimental = false;

    /** Holds the optional scores log. */
    protected PrintStream m_scoresLog = null;

    protected double idNamPenalty = 10000.0;
    protected double idPropNamePenalty = 10000.0;
    protected double perNonPerPenalty = 10000;
    protected double proSameSentNonPronPenalty = 10000;
    protected double nTNPenalty = 10000.0;
    protected double idNPPenalty = 10000;
    protected double idDetNomPenalty = 10000;
    protected double nPPPenalty = 10000;
    protected double sSpeakPenalty = 10000;
    protected double sEntTitPenalty = 10000;
    protected double sMentStruct = 10000;
    protected double penalty = 10000.0;
    protected boolean useConstraints = false;
    protected boolean notAllowNestedMention = false;
    private boolean m_filterPreSolution = false;
    protected int sentSpan=3;
    private ArrayList<Constraint> negConsList;
    private ArrayList<Constraint> PosConsList;

    public  void initConstraint(){
         
         try {
            PosConsList = new ArrayList<Constraint>();
            negConsList = new ArrayList<Constraint>();
            IdenticalNonPronoun idNP = new IdenticalNonPronoun();
            NestedPossessivePronoun nPP = new NestedPossessivePronoun();                
            IdenticalDetNom idDetNom = new IdenticalDetNom();
            GenderMismatchConstraints gMis= new GenderMismatchConstraints();
            ModifierMismatchConstraints mMis= new ModifierMismatchConstraints();
            PronounMismatchConstraints pMis= new PronounMismatchConstraints();
            ListMismatchConstraints lMis= new ListMismatchConstraints();
            PosConsList.add(idNP);
            PosConsList.add(nPP);
            PosConsList.add(idDetNom);
            negConsList.add(gMis);
            negConsList.add(mMis);
            negConsList.add(lMis);
            negConsList.add(pMis);
            nPP = new NestedPossessivePronoun();
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
    }

    static class LogFormatter extends Formatter {
        @Override
        public String format(LogRecord rec) {
            StringBuilder buf = new StringBuilder(1000);
            buf.append(formatMessage(rec));
            buf.append('\n');
            return buf.toString();
        }
    }

    /* Constructors */

    /**
     * Constructor for the case where a scoring classifier has had its threshold
     * set.
     * 
     * @param scorer
     *            A scoring classifier (specifically, a
     *            {@code LinearThresholdUnit}), whose threshold should be set
     *            using its {@code setThreshold} method. {@code scorer}'s
     *            {@code discreteValue} takes {@code CExample}s and returns
     *            "true" or "false". It also provides scores for the "true"
     *            value.
     */
    public BestLinkDecoderForOneMention(Learner scorer, Learner proClassifier) {
        super(scorer);
        if(useConstraints)
            initConstraint();
        pronounClassifer = proClassifier;
    }
    public BestLinkDecoderForOneMention(Learner scorer, Learner proClassifier, String solutionPath) {
        super(scorer);
        if(useConstraints)
            initConstraint();
        pronounClassifer = proClassifier;
    }

    /**
     * Constructor for use when the scoring classifier is not sufficient to
     * decide whether links should be made, such as when inference is being
     * applied. Both {@code scorer} and {@code decider} must return "true" for
     * an example to be considered coreferential.
     * 
     * @param scorer
     *            Determines the score or confidence. Takes {@code CExample}s
     *            and returns a score.
     * @param decider
     *            Final arbiter of linking decisions. Takes {@code CExample}s
     *            and returns "true" or "false".
     */
    public BestLinkDecoderForOneMention(Learner scorer, Classifier decider) {
        super(scorer, decider);
        
    }

    /* Main function */

    public void useConstraint(boolean USECONSTRAINTS) {
        useConstraints = USECONSTRAINTS;
    }

    public double getPositiveConstraintScore(CExample ex){
        Mention m = ex.getM2();
        Mention a = ex.getM1();
        double score = 0.0;
        for(Constraint cons : PosConsList){
            try {
                score += cons.checkConstraint(a, m, false);
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        return score*=100;
    }
    public double getNegativeConstraintScore(CExample ex) {
        double score = 0.0;
        Mention m = ex.getM2();
        Mention a = ex.getM1();
        for(Constraint cons : negConsList){
            try {
                score += cons.checkConstraint(a, m, false);
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        return score*=100;
    }
    
    public double constraintViolationScore(Mention m1, Mention m2) {
        try {
            CExample ex = m1.getDoc().getCExampleFor(m1, m2);
            return getNegativeConstraintScore(ex) + getPositiveConstraintScore(ex);
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }


    public boolean disregardLink(Mention m1, Mention m2){
        boolean mIsPro = m2.getType().equals("PRO");
        if (!m_allowCataphora) {
            if (m1.getType().equals("PRO") && !mIsPro){
                return true; // Prohibit pronoun cataphora.
            }
        }
        if (m_preventLongDistPRO) {
            if (mIsPro && m1.getSentNum() < m2.getSentNum() - sentSpan){
                return true; // too far to link.
            }
        }
        
        return false;
    }
    
    /**
     * Takes the mentions in the specified document and produces a collection of
     * coreference equivalence classes. The best link decoding method specifies
     * that for each mention {@code m} a link will be produced with highest
     * scoring preceding mention {@code a} only if
     * {@link #predictedCoreferential(CExample)} returns true for the example
     * {@code doc.getCExampleFor(a, m)}. Note: Several options ignore the
     * {@link #predictedCoreferential(CExample)} method; in these cases, a
     * decider may specify false and links may still be made, possibly
     * interfering with successful inference.
     * 
     * @param doc
     *            a document whose mentions will be place in coreference
     *            classes.
     * @return A {@code ChainSolution} representing the coreference equivalence
     *         classes as chains. LFor the experiment,inks established between
     *         mentions will also be given labels in the solution.
     */
    public ChainSolution<Mention> decode(Doc doc){
        return null;
    }
    
    public ChainSolution<Mention> decode(Doc doc, Mention target, Set<Mention> IgnoreSet) {
        ChainSolution<Mention> sol = new ChainSolution<Mention>();
        int linkNum = 0;

        List<Mention> allMents = doc.getMentions();
        for (Mention m : allMents) {
            sol.recordExistence(m);
        }
        
        double bestScore = Double.NEGATIVE_INFINITY;
        Mention bestA = null;
        boolean makeLink = false;
        
        // First, find the Bestlink of the target mention:
        for(int j =0 ; j < allMents.size(); ++j){
            Mention a = allMents.get(j);
            if(target.compareTo(a)<=0 || IgnoreSet.contains(a))
                continue;
            
            boolean aIsPro = PronounResolutionFeatures.isPersonAndReflectivePronoun(a.getExtent().getText());
            if(aIsPro)
                continue;
            
            CExample ex = doc.getCExampleFor(a, target);

            recordStatsFor(ex);
            double score = 0;
            if (m_scorer != null){
                if(useConstraints)
                    score = getTrueScore(ex) + constraintViolationScore(a, target);
                else
                    score  = getTrueScore(ex);
            }
            
            if (score >= bestScore) {
                bestScore = score;
                bestA = a;
            }
        }
        if(bestA !=null){
            makeLink = predictedCoreferential(doc.getCExampleFor(bestA, target));
            sol.recordBestLinkPrediction(bestA, target);
        }
        if (makeLink) {
            List<String> labels = new ArrayList<String>();
            labels.add("Pairwise Best First (link #" + linkNum + ").");
            labels.add("Score: " + bestScore);
            linkNum++;
            if (getBooleanOption("interactive")) {
                CExample ex = doc.getCExampleFor(bestA, target);
                labels.addAll(getEdgeLabels(ex)); // This uses the base
            }
            sol.recordEquivalence(bestA, target, labels);

            // Record linkages in mentions:
            bestA.addCorefMentsOf(target);
            target.addCorefMentsOf(bestA);
        }
        
        // For mentions after target
        for(int j =0 ; j < allMents.size(); ++j){
            Mention m = allMents.get(j);
            boolean mIsPro = PronounResolutionFeatures.isPersonAndReflectivePronoun(m.getExtent().getText());
            if(target.compareTo(m)>=0)
                continue;
            if(mIsPro)
                continue;
            
            makeLink = predictedCoreferential(doc.getCExampleFor(target,m));
            // If m possibly links to target
            // Then find m's bestlink 
            if(makeLink){
                bestScore = Double.NEGATIVE_INFINITY;
                bestA = null;
                for(int i =0 ; i < allMents.size(); ++i){
                    Mention a = allMents.get(i);
                    boolean aIsPro = PronounResolutionFeatures.isPersonAndReflectivePronoun(a.getExtent().getText());
                    if(a.compareTo(m)>=0)
                        continue;
                    if(aIsPro)
                        continue;
                    
                    CExample ex = doc.getCExampleFor(a, m);
                    
                    recordStatsFor(ex);
                    double score = 0;
                    if (m_scorer != null){
                        if(useConstraints)
                            score = getTrueScore(ex) + constraintViolationScore(a, target);
                        else
                            score  = getTrueScore(ex);
                    }
                    
                    if (score >= bestScore) {
                        bestScore = score;
                        bestA = a;
                    }
                }
                
                sol.recordBestLinkPrediction(bestA, target);
                if (bestA == target) {
                    List<String> labels = new ArrayList<String>();
                    labels.add("Pairwise Best First (link #" + linkNum + ").");
                    labels.add("Score: " + bestScore);
                    linkNum++;
                    if (getBooleanOption("interactive")) {
                        CExample ex = doc.getCExampleFor(bestA, target);
                        labels.addAll(getEdgeLabels(ex)); // This uses the base
                    }
                    sol.recordEquivalence(target, m, labels);

                    // Record linkages in mentions:
                    target.addCorefMentsOf(m);
                    m.addCorefMentsOf(target);
                }

            }
        }
        return sol;
    } // End decode method.

    private boolean FindSpeaker(Mention m, CExample ex) {
        if (PronounResolutionFeatures.proNumber(m) == 'S')
            if (ContextFeatures.ASpeakB(ex, 3)) {
                return true;
            }
        if (PronounResolutionFeatures.proNumber(m) == 'P')
            if (ContextFeatures.ASpeakB(ex, 5)) {
                return true;
            }
        return false;
    }

    /* Option methods */
    /**
     * Specifies whether to allow pronoun cataphora Specifically, if
     * {@code allow} is true, a pronoun cannot take an referent that appears
     * after the pronoun.
     * 
     * @param allow
     *            Whether to allow a pronoun to refer to mentions appearing
     *            after the pronoun.
     */
    public void setAllowPronounCataphora(boolean allow) {
        m_allowCataphora = allow;
    }

    /**
     * Specifies whether to limit pronoun reference to within a small number of
     * sentences. Specifically, if {@code prevent} is true, a pronoun cannot
     * take an antecedent from any sentence earlier than the previous sentence.
     * 
     * @param prevent
     *            Whether to prevent long-distance pronoun reference.
     */
    public void setPreventLongDistPRO(boolean prevent) {
        m_preventLongDistPRO = prevent;
    }

    /**
     * Processes the options by calling super and calling the dedicated methods
     * for setting specific options. Also, if {@literal scoreslog} is set,
     * constructs the {@code m_scoresLog} print stream.
     * 
     * @param option
     *            The name of the option, which is generally all lowercase.
     * @param value
     *            The value, which may be the string representation of a boolean
     *            or real value (In a format supported by by
     *            {@link Boolean#parseBoolean} or {@link Double#parseDouble}) or
     *            any arbitrary string.
     */
    @Override
    public void processOption(String option, String value) {
        super.processOption(option, value);
        boolean bVal = Boolean.parseBoolean(value);
        if (option.equals("allowprocata")) {
            setAllowPronounCataphora(bVal);
        } else if (option.equals("allownonprotopro")) { // backwards compatible.
            setAllowPronounCataphora(bVal);
        } else if (option.equals("preventlongdistpro")) {
            setPreventLongDistPRO(bVal);
        } else if (option.equals("experimental")) {
            m_experimental = bVal;
        } else if (option.equals("allowcataphora")) {
            setAllowPronounCataphora(bVal);
        } else if (option.equals("scoreslog")) {
            try {
                m_scoresLog = new PrintStream(value);
                // TODO: Close file somewhere?
            } catch (Exception e) {
                System.err.println("Cannot open scores log file " + value);
            }
        }
    }

    /* Statistics methods */

    /**
     * Enables the recording of data about coreference examples as they are used
     * in the decoding algorithm. Currently does nothing, but may be overridden
     * or revised to record any statistic. This method should be called in the
     * decode method whenever an example is examined (once per examination).
     * 
     * @param ex
     *            The example whose statistics should be recorded.
     */
    protected void recordStatsFor(CExample ex) {
        /*
         * boolean isPos = Boolean.parseBoolean((new
         * coLabel()).discreteValue(ex)); boolean apposOn =
         * Boolean.parseBoolean( (new soonAppositive()).discreteValue(ex));
         * boolean bothSpeakOn = Boolean.parseBoolean( (new
         * bothSpeak()).discreteValue(ex)); boolean relProOn =
         * Boolean.parseBoolean( (new relativePronounFor()).discreteValue(ex));
         * boolean eMatchOn = Boolean.parseBoolean( (new
         * wordNetETypeMatchBetter()).discreteValue(ex));
         * 
         * if (isPos) { posEx++; if (apposOn) apposPosEx++; if (bothSpeakOn)
         * bothSpeakPosEx++; if (relProOn) relProPosEx++; if (eMatchOn)
         * eMatchPosEx++; } else { //neg: negEx++; if (apposOn) apposNegEx++; if
         * (bothSpeakOn) bothSpeakNegEx++; if (relProOn) relProNegEx++; if
         * (eMatchOn) eMatchNegEx++; }msg
         */
    }

    /**
     * Enables recorded statistics to be returned. Currently does nothing, since
     * no statistics are being recorded, but may be overridden or revised to
     * enable statistics output.
     * 
     * @return The statistics string, which is currently empty.
     */
    @Override
    public String getStatsString() {
        String s = super.getStatsString();
        return s;
    }


    public void setPronounThreshold(double pronounThreshold) {
        this.pronounThreshold = pronounThreshold;
    }

    public double getPronounThreshold() {
        return pronounThreshold;
    }
    public void setM_filterPreSolution(boolean m_filterPreSolution) {
        this.m_filterPreSolution = m_filterPreSolution;
    }
    public boolean isM_filterPreSolution() {
        return m_filterPreSolution;
    }

}