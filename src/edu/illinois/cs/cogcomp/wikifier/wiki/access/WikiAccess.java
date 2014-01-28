package edu.illinois.cs.cogcomp.wikifier.wiki.access;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.lucene.document.Document;

import com.google.protobuf.InvalidProtocolBufferException;

import edu.illinois.cs.cogcomp.wikifier.common.GlobalParameters;
import edu.illinois.cs.cogcomp.wikifier.inference.features.SimilarityMetrics.HashMapSemanticInfoRepresentation;
import edu.illinois.cs.cogcomp.wikifier.models.WikipediaProtobuffers.BasicTitleDataInfoProto;
import edu.illinois.cs.cogcomp.wikifier.models.WikipediaProtobuffers.LexicalTitleDataInfoProto;
import edu.illinois.cs.cogcomp.wikifier.models.WikipediaProtobuffers.SemanticTitleDataInfoProto;
import edu.illinois.cs.cogcomp.wikifier.models.WikipediaProtobuffers.SurfaceFormSummaryProto;
import edu.illinois.cs.cogcomp.wikifier.models.WikipediaProtobuffers.WikiDataSummaryProto;
import edu.illinois.cs.cogcomp.wikifier.utils.WikiTitleUtils;


/**
 * Interface allowing different underlying wiki data providers
 * @author cheng88
 *
 */
public abstract class WikiAccess implements Iterable<Integer>{

    /*
     * Estimates to avoid re-hashing
     */
    public static final int MAX_NUM_ARTICLES_TO_CACHE = 5000;
    public static final int MAX_NUM_SURFACE_FORMS_TO_CACHE = 5000; 
    public static final int MAX_WIKIPEDIA_ID_ESTIMATE = 18598374;
    public static final int TOTAL_WIKIPEDIA_TITLE_ESTIMATE = 2478573;
    public static final int ESTIMATED_UNLINKABLE_SURFACEFORMS = 130000;

    public static enum WikiDataFields{
        
        TitleID,TitleName,BasicInfo,SemanticInfo,LexicalInfo;
        public static Set<String> INITIAL_FIELD_SET = new HashSet<String>(Arrays.asList(BasicInfo.name()));
        
    }
    public static enum SurfaceFormFields{
        SurfaceForm,SurfaceFormSummaryProto
    }
    public static enum RedirectFields{
        From,To
    }
    
    // we need this to know the tokens feature map, the IDF scores, the normalized categories maps etc
    protected WikipediaSummaryData wikiDataSummary = null; 
    /**
     * Interface for holding wiki data structures
     * 
     * @author cheng88
     * 
     */
    public static class WikiData {

        private byte[] basicInfo;
        private byte[] lexInfo;
        private byte[] semanticInfo;
        private BasicTitleDataInfoProto basicProto = null;
        private LexicalTitleDataInfoProto lexProto = null;
        private SemanticTitleDataInfoProto semanticProto = null;

        public WikiData(byte[] basicInfo, byte[] lexInfo, byte[] semanticInfo) {
            this.basicInfo = basicInfo;
            this.lexInfo = lexInfo;
            this.semanticInfo = semanticInfo;
        }
        
        public WikiData(Document doc){
            this(doc.getBinaryValue(WikiDataFields.BasicInfo.name()).bytes,
                    doc.getBinaryValue(WikiDataFields.LexicalInfo.name()).bytes,
                    doc.getBinaryValue(WikiDataFields.SemanticInfo.name()).bytes);
        }

        public WikiData(byte[] data) throws IOException {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
            readFrom(in);
            in.close();
        }
        
        public WikiData(DataInput input) throws IOException {
            readFrom(input);
        }

        public byte[] serialize() {
            ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
            DataOutputStream oos = new DataOutputStream(byteArray);
            try {
                writeTo(oos);
                oos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return byteArray.toByteArray();
        }

        public void writeTo(DataOutput oos) throws IOException {
            oos.writeInt(basicInfo.length);
            oos.writeInt(lexInfo.length);
            oos.writeInt(semanticInfo.length);
            oos.write(basicInfo);
            oos.write(lexInfo);
            oos.write(semanticInfo);
        }
        
        public WikiData readFrom(DataInput in) throws IOException{
            basicInfo = new byte[in.readInt()];
            lexInfo = new byte[in.readInt()];
            semanticInfo = new byte[in.readInt()];
            in.readFully(basicInfo);
            in.readFully(lexInfo);
            in.readFully(semanticInfo);
            return this;
        }

        public byte[] basicInfo() {
            return basicInfo;
        }

        public byte[] lexInfo() {
            return lexInfo;
        }

        public byte[] semanticInfo() {
            return semanticInfo;
        }

        public BasicTitleDataInfoProto getBasicProto() throws InvalidProtocolBufferException {
            if (basicProto == null)
                basicProto = BasicTitleDataInfoProto.parseFrom(basicInfo);
            return basicProto;
        }

        public LexicalTitleDataInfoProto getLexProto() throws InvalidProtocolBufferException {
            if (lexProto == null)
                lexProto = LexicalTitleDataInfoProto.parseFrom(lexInfo);
            return lexProto;
        }

        public SemanticTitleDataInfoProto getSemanticProto() throws InvalidProtocolBufferException {
            if (semanticProto == null)
                semanticProto = SemanticTitleDataInfoProto.parseFrom(semanticInfo);
            return semanticProto;
        }

    }
    
    public static class SurfaceData{
        
        public static Charset defaultEncoding = StandardCharsets.UTF_8;
        
        private String surface;
        private SurfaceFormSummaryProto proto;
        private final byte[] surfaceInfo;
        public SurfaceData(String surface,byte[] surfaceInfo){
            this.surface = surface;
            this.surfaceInfo = surfaceInfo;
        }
        
        public SurfaceData(byte[] data) throws IOException{
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
            byte[] surfaceBytes = new byte[in.readInt()];
            surfaceInfo = new byte[in.readInt()];
            in.readFully(surfaceBytes);
            surface = new String(surfaceBytes,defaultEncoding);
            in.readFully(surfaceInfo);
            in.close();
        }
        
        public SurfaceFormSummaryProto getSurfaceProto() throws InvalidProtocolBufferException{
            if(proto==null)
                proto = SurfaceFormSummaryProto.parseFrom(surfaceInfo);
            return proto;
        }
        
        public String getSurface(){
            return surface;
        }
        
        public byte[] serialize(){
            byte[] surBytes = surface.getBytes(defaultEncoding);
            ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
            DataOutputStream oos = new DataOutputStream(byteArray);
            try {
                oos.writeInt(surBytes.length);
                oos.writeInt(surfaceInfo.length);
                oos.write(surBytes);
                oos.write(surfaceInfo);
                oos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return byteArray.toByteArray();
        }
    }

    
    /*
     * This class stores the core information about a disambiguation candidate and the surface form
     * the candidate disambiguates
     */
    public static class WikiMatchData implements Serializable {
        /**
    	 * 
    	 */
        private static final long serialVersionUID = -4354148618660083430L;
        // this part of the data is used to generate title match features
        public String matchedSurfaceForm = null;
        public double surfaceFormAmbiguity = 0;
        public int linkabilityAppearanceCountTotal = 0;
        public int linakabilityAppearanceCountLinked = 0;
        public double conditionalSurfaceFromProb = 0; // P(surface form|title)
        public double conditionalTitleProb = 0; // P(title|surface form)
        public double logProbOnWebGoogleEstimate = 0;
        public double logProbAppearaceInWikipedia = 0;
        public double expectedProbOutOfWikipediaEntityGoodTuring = 0;
        public int disambiguationRank = 100;// What's the position of this candidate in the
                                            // disambiguation list? Is it a top candidate? Second
                                            // best? (Here the lesser the value, the better)
        // this is the data for the advanced feature extraction
        public transient BasicTitleDataInfoProto basicTitleInfo = null;
        public transient LexicalTitleDataInfoProto lexInfo = null;
        public transient SemanticTitleDataInfoProto semanticInfo = null;
    
        public transient HashMapSemanticInfoRepresentation dataForSemanticRelatednessComputation = null;
    
        // this data stores the WikiMatchData in hash tables for fast computation of semantic relatedness
    
        public WikiMatchData() {
            
        }
    
        public WikiMatchData(String surfaceForm, String normalizedTitle, int rank) throws Exception {
            WikiAccess wiki = GlobalParameters.wikiAccess;
            int titleId = wiki.getTitleIdOf(normalizedTitle);
            if (titleId < 0) {
                System.out.println("Could not find WikiMatchData for title " + normalizedTitle);
            }
            lexInfo = wiki.getLexicalInfo(titleId);
            semanticInfo = wiki.getSemanticInfo(titleId);
            basicTitleInfo = wiki.getBasicInfo(titleId);
    
            disambiguationRank = rank;
    
            matchedSurfaceForm = surfaceForm;
            surfaceFormAmbiguity = 1.0;
            linakabilityAppearanceCountLinked = 0;
            linkabilityAppearanceCountTotal = basicTitleInfo.getTitleAppearanceCount();
            conditionalTitleProb = 0;
            conditionalSurfaceFromProb = 0;
            expectedProbOutOfWikipediaEntityGoodTuring = 0.00001;
            logProbOnWebGoogleEstimate = -5;
            logProbAppearaceInWikipedia = -10;
        }
    
    }


    protected WikiAccess(){
        
    }
    
    protected WikiAccess(String indexPath){
        String wikiDataSummaryFile = new File(indexPath,"WikiSummary.proto.save").getPath();
        try {
            InputStream is = new FileInputStream(wikiDataSummaryFile);
            wikiDataSummary = new WikipediaSummaryData(WikiDataSummaryProto.parseFrom(is));
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(0);
        }
    }
	/*
	 * NOTE THAT THIS FUNCTION ADDS SOME FEATURES TO THE DISAMBIGUATION
	 * CANDIDATES. THESE FEATURES ARE THE AMBIGUITY OF THE SURFACE FORM,
	 * P(TITLE|SURFACE FORM) ETC... I HAVE TO EXTRACT THEM HERE, BECAUSE
	 * THESE FEATURES CAPTURE THE RELATIONSHIP BETWEEN THE DISAMBIGUATION
	 * CANDIDATE TITLE AND THE SURFACE FORM IT DISAMBIGUATES.
	 * 
	 * This function is used not only to extract the disambiguation candidates, 
	 * but also to check if the surface forms may have any legitimate disambiguation
	 * candidates. Note that a disambiguation candidate is legitimate only if BOTH
	 *      P(title|surface form)>0 AND P(surface form|title)>0.2
	 *      
	 *  IF THIS FUNCTION RETURNS AN EMPTY VECTOR, IT MEANS THAT 
	 *  THE SURFACE FORM IS NOT LINKABLE!!!!
	 *  
	 *  maxCandidatesToGenerate is a parameter which specifies how many candidates
	 *  we can generate. Using maxCandidatesToGenerate=20 is generally a good idea...
	 *  	  
	 */
    public List<WikiMatchData> getDisambiguationCandidates(String surfaceForm, int maxCandidatesToGenerate) throws Exception {
        List<WikiMatchData> res = new ArrayList<WikiMatchData>();

        if (surfaceExists(surfaceForm)) {

            SurfaceFormSummaryProto surfaceInfo = getSurfaceFormInfo(surfaceForm);
            int rank = 1;
            double goodTuringNonWikipediaMass = 0;
            double numberOfTitlesWithSingleCounts = 0;
            double sumTitleCounts = 0;
            for (double appearance : surfaceInfo.getConditionalTitleAppearancesList()) {
                sumTitleCounts += appearance;
                if (appearance == 1)
                    numberOfTitlesWithSingleCounts++;
            }
            double logProbAppearaceInWikipedia = Math.log(surfaceInfo.getTotalAppearanceCount())
                    - Math.log(wikiDataSummary.numWikipediaArticles);
            if (sumTitleCounts > 5)
                goodTuringNonWikipediaMass = numberOfTitlesWithSingleCounts / sumTitleCounts;
            for (int i = 0; i < surfaceInfo.getConditionalTitleProbCount() && res.size() < maxCandidatesToGenerate; i++) {
                // if(surfaceInfo.getConditionalSurfaceFormProb(i)>=0.2) { // the if turns out to be
                // excessively strict!
                int titleId = surfaceInfo.getTitleIds(i);
                WikiMatchData c = new WikiMatchData();
                c.basicTitleInfo = getBasicInfo(titleId);

                // Filters certain bad pages
                if (WikiTitleUtils.filterTitle(c.basicTitleInfo.getTitleSurfaceForm()))
                    continue;

                // Filter disambiguation pages here

                c.disambiguationRank = rank;
                rank++;
                c.matchedSurfaceForm = surfaceForm;
                c.surfaceFormAmbiguity = surfaceInfo.getAmbiguity();
                c.lexInfo = getLexicalInfo(titleId);
                c.semanticInfo = getSemanticInfo(titleId);
                c.linakabilityAppearanceCountLinked = surfaceInfo.getLinkedAppearanceCount();
                c.linkabilityAppearanceCountTotal = surfaceInfo.getTotalAppearanceCount();
                c.conditionalTitleProb = surfaceInfo.getConditionalTitleProb(i);
                c.conditionalSurfaceFromProb = surfaceInfo.getConditionalSurfaceFormProb(i);
                c.expectedProbOutOfWikipediaEntityGoodTuring = goodTuringNonWikipediaMass;
                c.logProbOnWebGoogleEstimate = surfaceInfo.getLogProbOnWebGoogle();
                c.logProbAppearaceInWikipedia = logProbAppearaceInWikipedia;
                res.add(c);
                // } the if turns out to be excessively strict!
            }// for
        }// if the surface form is in the HashMap
        return res;
    }
	
    public int getTitleIdFromExternalLink(String wikiLink) {
        String title = TitleNameNormalizer.normalize(wikiLink);
        return getTitleIdOf(title);
    }
	
	/**
	 * Methods below are data heavy, override to use another implementation
	 */
	/*
	 * returns -1 if the title name is not in the index
	 */
	public abstract int getTitleIdOf(String titleName);
	
    public String getTitle(String tid){
        BasicTitleDataInfoProto proto = null;
        try {
            proto = getBasicInfo(Integer.parseInt(tid));
        } catch (Exception e) {
        }
        return proto == null ? null : proto.getTitleSurfaceForm();
    }

	public abstract SurfaceFormSummaryProto getSurfaceFormInfo(String surfaceForm);

	public abstract LexicalTitleDataInfoProto getLexicalInfo(int titleID);

	public abstract SemanticTitleDataInfoProto getSemanticInfo(int titleID);

	public abstract BasicTitleDataInfoProto getBasicInfo(int titleID);

	public int getTotalNumberOfWikipediaTitles(){
	    return wikiDataSummary.numWikipediaArticles;
	}

	/**
	 * Provides a way to iterate over all title IDs
	 */
	public abstract Iterator<Integer> iterator();

	/**
	 * Determines whether a surface form is unlinkable
	 * and we have its surface form data
	 * @param surface
	 * @return
	 */
    public abstract boolean isKnownUnlinkable(String surface);
    
    public int getNumberIngoingLinks(int tid) {
        BasicTitleDataInfoProto b = getBasicInfo(tid);
        if (b == null)
            return 0;
        return b.getNumberOfIngoingLinks();
    }

    public WikipediaSummaryData getWikiSummaryData(){
        return wikiDataSummary;
    }
    
    protected abstract boolean surfaceExists(String surface);
    
    public static boolean isUnlinkable(String surface,SurfaceFormSummaryProto proto){
        double linkability = ((double) proto.getLinkedAppearanceCount()) / proto.getTotalAppearanceCount();
        return surface.length() < GlobalParameters.params.minSurfaceLen || linkability < GlobalParameters.params.minLinkability;
    }


}