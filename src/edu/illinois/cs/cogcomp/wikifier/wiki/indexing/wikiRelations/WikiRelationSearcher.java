package edu.illinois.cs.cogcomp.wikifier.wiki.indexing.wikiRelations;

import static edu.illinois.cs.cogcomp.wikifier.wiki.indexing.wikiRelations.WikiRelationIndexUtils.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.lucene.document.Document;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;

import edu.illinois.cs.cogcomp.wikifier.common.GlobalParameters;
import edu.illinois.cs.cogcomp.wikifier.models.Mention;
import edu.illinois.cs.cogcomp.wikifier.utils.datastructure.Triple;
import edu.illinois.cs.cogcomp.wikifier.utils.lucene.Lucene;
import edu.illinois.cs.cogcomp.wikifier.wiki.access.MongoDBWikiAccess;
import edu.illinois.cs.cogcomp.wikifier.wiki.indexing.TitleNameIndexer;

public class WikiRelationSearcher {

	public static enum Fields {
		FROM, TO, FROM_ID, TO_ID, RELATION
	}

	private static IndexSearcher searcher;
	static{
        try {
            searcher = Lucene.searcher(GlobalParameters.paths.wikiRelationIndexDir);
        } catch (IOException e) {
            System.err.println("Unable to instantiate WikiRelationSearcher");
            e.printStackTrace();
            System.exit(0);
        }
	}

	private WikiRelationSearcher(){}
	
	/**
	 * Grounds the relations with strict entailment requirements
	 * @param e1
	 * @param e2
	 * @return
	 */
    public static List<Triple> searchGroundedRelations(final Mention e1, final Mention e2) {
        List<Triple> firstSearch = new ArrayList<>();
        if (e2.topCandidate != null)
            firstSearch.addAll(searchExactRelations(e2.topCandidate.titleName, e1.surfaceForm, true));
        if (e1.topCandidate != null) {
            firstSearch.addAll(searchExactRelations(e1.topCandidate.titleName, e2.surfaceForm));
        }

        return firstSearch;
    }

    public static List<Triple> searchExactRelations(final String exactMatch,final String context){
        return searchExactRelations(exactMatch, context, false);
    }

    
    public static List<Triple> searchExactRelations(final String exactMatch, final String context, boolean reverse) {
        String normalizedTitle = TitleNameIndexer.normalize(exactMatch);
        int normalizedTid = GlobalParameters.wikiAccess.getTitleIdOf(normalizedTitle);
        return searchExactRelations(normalizedTid, context,reverse);
    }

	/**
	 * @experimental
	 */
	private static List<Triple> searchExactRelations(final int id,final String context,boolean reverse){
	    
        List<Triple> returnTriples = new ArrayList<Triple>();
        if (id < 0)
            return returnTriples;
	    try {
            BooleanQuery part1 = new BooleanQuery();
            part1.add(idQuery(id, Fields.FROM_ID), Occur.MUST);
            part1.add(parse(context, Fields.TO), Occur.MUST);

            BooleanQuery part2 = new BooleanQuery();
            part2.add(idQuery(id, Fields.TO_ID), Occur.MUST);
            part2.add(parse(context, Fields.FROM), Occur.MUST);

            BooleanQuery combined = new BooleanQuery();
            combined.add(part1, Occur.SHOULD);
            combined.add(part2, Occur.SHOULD);
            // long start = System.currentTimeMillis();
            TopDocs docs = searcher.search(combined, GlobalParameters.params.maxCandidatesToGenerateInitially);
            // System.out.println(System.currentTimeMillis()-start + " ms");
            String strId = ""+id;
            for(ScoreDoc scoreDoc: docs.scoreDocs){
                Document doc = searcher.doc(scoreDoc.doc);

                Triple relation = readDocument(scoreDoc,doc);

                if(relation == null || relation.getArg1()==null || relation.getArg2() == null)
                    continue;

                if(doc.get(Fields.TO_ID.name()).equals(strId))
                    relation.swapArgs();
                
                returnTriples.add(relation);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (reverse)
            for (Triple t : returnTriples) {
                t.swapArgs();
            }
        return Triple.consolidate(returnTriples, true);
	}
	
	/**
	 * Searches the relatin lexically by matching the two titles
	 * @param fromTitle
	 * @param toTitle
	 * @return
	 */
	public static List<Triple> searchRaw(final String fromTitle,final String toTitle){
	    List<Triple> results = new ArrayList<Triple>();
        
        final List<ScoreDoc> reverseSearchResults = new ArrayList<ScoreDoc>();
        Thread reverseSearch = new Thread(){
            public void run(){
                reverseSearchResults.addAll(Arrays.asList(search(toTitle, fromTitle))) ;
            }
        };
        
        reverseSearch.start();
        
        for(ScoreDoc scoreDoc:search(fromTitle,toTitle)){
            Triple triple = readDocument(scoreDoc,null);
            if(triple == null || triple.getArg1()==null || triple.getArg2() == null)
                continue;
            results.add(triple);
        }
        
        try {
            reverseSearch.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        // Here we are doing unordered search, some predicates might not make sense
        for(ScoreDoc scoreDoc:reverseSearchResults){
            Triple triple = readDocument(scoreDoc,null);
            if(triple == null || triple.getArg1()==null || triple.getArg2() == null)
                continue;
            triple.swapArgs();
            results.add(triple);
        }
        return results;
	}
	
	/**
	 * @param fromTitle
	 * @param toTitle
	 * @return Aggregated relations between two entities matching the two arguments
	 */
	public static List<Triple> searchRelation(final String fromTitle,final String toTitle){
		return Triple.consolidate(searchRaw(fromTitle,toTitle),true);
	}
	
	/**
	 * Converts Lucene document to our relation triple
	 * @param scoreDoc
	 * @return
	 */
	private static Triple readDocument(ScoreDoc scoreDoc,Document doc){
		try {
            if (doc == null)
                doc = searcher.doc(scoreDoc.doc);
			return readRelationalDoc(scoreDoc.score,doc);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	
	protected static ScoreDoc[] search(String arg1,String arg2){
		try {			
			BooleanQuery booleanQuery = new BooleanQuery();
			booleanQuery.add(parse(arg1,Fields.FROM),Occur.MUST);
			booleanQuery.add(parse(arg2,Fields.TO), Occur.MUST);
//		    long start = System.currentTimeMillis();
			TopDocs docs = searcher.search(booleanQuery, GlobalParameters.params.maxCandidatesToGenerateInitially);
//			System.out.println(System.currentTimeMillis()-start + " ms");
			return docs.scoreDocs;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return new ScoreDoc[0];
	}

	public static void main(String[] args) throws Exception {

////		ParametersAndGlobalVariables.loadConfig("Config/XiaoConfig");
////		System.out.println(ParametersAndGlobalVariables.wikiAccess.getBasicInfo(2855836));
//	    long start = System.currentTimeMillis();
////	    for(int i = 0 ;i<1000;i++)
//	        WikiLinkIndexer.searchRelation("Robinson College", "Georgia State University");
//	    System.out.println(System.currentTimeMillis()-start + " ms");
//
//		System.out.println(searchRelation("Robinson College", "Georgia State University"));
//		System.out.println(searchRelation("John Weir", "Weir Group"));
//		System.out.println(searchRelation("Clinton", "Bill Clinton"));
//		System.out.println(searchRelation("Clinton", "Clinton"));
//
//
//	    GlobalParameters.wikiAccess = new MongoDBWikiAccess("../Data/WikiSummary.proto.save");

        System.out.println(WikiRelationSearcher.searchRelation("Glen Gilmore", "Hamilton"));
        System.out.println(WikiRelationSearcher.searchRelation("Waynesboro", "Coopersville"));
        System.out.println(WikiRelationSearcher.searchRelation("Gratton", "Florida"));
	    System.out.println(WikiRelationSearcher.searchExactRelations("John_Corbett","Sex and the City",true));
        

        System.out.println(WikiRelationSearcher.searchExactRelations("University_of_Florida", "College of Medicine"));
        System.out.println(WikiRelationSearcher.searchExactRelations("University_of_Florida_College_of_Medicine", "University"));
        System.out.println(WikiRelationSearcher.searchExactRelations("Georgia_State_University", "Robinson College"));
        System.out.println(WikiRelationSearcher.searchExactRelations("Sex_and_the_City","John Corbett"));
       System.out.println(WikiRelationSearcher.searchExactRelations("Slobodan_Milošević", "Socialist Party"));
        System.out.println(WikiRelationSearcher.searchExactRelations("Bosnia_and_Herzegovina", "Brcko"));
        System.exit(0);
        System.out.println(WikiRelationSearcher.searchRelation("European", "Japan"));
        System.out.println(WikiRelationSearcher.searchRelation("America", "Japan"));

        System.out.println(WikiRelationSearcher.searchExactRelations("Florida","Green Party"));
        System.out.println(WikiRelationSearcher.searchExactRelations("The_Guardian_(Nigeria)", "Lagos"));
        
        System.out.println(searchRelation("Minister of Defence", "India"));
        System.out.println(searchRelation("Shaquille O'Neal", "Nash"));
        System.out.println(searchRelation("Slobodan_Milošević", "Socialist Party"));
        System.out.println(searchRelation("South Carolina", "ABBEVILLE"));
        System.out.println(searchRelation("Slobodan Milosevic", "Socialist Party"));
		System.out.println(searchRelation("Hosni Mubarak", "Mubarak"));
//		System.out.println(WikiLinkIndexer.searchRelation("Dumfries", "Virginia"));

        System.out.println(searchRelation("World_championship", "PDC_World_Darts_Championship"));
		System.out.println(searchRelation("List_of_world_championships", "PDC_World_Darts_Championship"));
		System.out.println(searchRelation("Indiana", "United States"));
		System.out.println(searchRelation("LaGuardia_Airport", "New York"));
		System.out.println(searchRelation("Robinson_College", "Georgia State University"));
//		System.out.println(WikiLinkIndexer.searchRelation("Scotland", "Wales"));


        System.out.println(searchRelation("World Grand Prix", "World Championship"));
        System.out.println(searchRelation("World Grand Prix (darts)", "World Championship"));
		
		System.out.println();
		
        System.out.println(searchRelation("Sex and the City", "John Corbett"));

		System.out.println(searchRelation("President of the Palestinian National Authority", "Yasser Arafat"));
	    System.out.println(searchRelation("Mubarak","Hosni Mubarak"));
		System.out.println(searchRelation("Brokaw", "Gerald Ford"));
		System.out.println(searchRelation("Milošević", "Socialist Party"));
		System.out.println(searchRelation("Socialist Party", "Milošević"));
		System.out.println(searchRelation("Prague", "Czech"));
		System.out.println(searchRelation("Prime Minister", "Phan Van Khai"));
		System.out.println(searchRelation("Prime Minister", "Monarch"));
		System.out.println(searchRelation("United Kingdom", "Prime Minister"));
		System.out.println(searchRelation("Sandinista", "Contra"));
		System.out.println(searchRelation("John Glenn", "American"));
		System.out.println(searchRelation("Diaz", "Timberlake"));
		System.out.println(searchRelation("New York", "Dow industrials"));
		System.out.println(searchRelation("Dow", "Industrial"));
		System.out.println(searchRelation("Davie", "Florida"));
		System.out.println(searchRelation("Lindfield", "Gordon"));
		System.out.println(searchRelation("Boomer", "Vietnam"));
		System.out.println(searchRelation("Portsmouth", "Exeter"));
		System.out.println(searchRelation("Secretary of State", "New Hampshire"));
		System.out.println(searchRelation("Legislative Council", "Quebec"));
		System.out.println(searchRelation("Legislative Assembly", "Quebec"));
		System.out.println(searchRelation("Pointer Sisters", "Priority"));
		System.out.println(searchRelation("Department of Natural Resources", "Michigan"));
		System.out.println(searchRelation("Grossman", "Johns Hopkins University"));
		System.out.println(searchRelation("Washington", "George W. Bush"));
		System.out.println(searchRelation("Penacook", "Portsmouth"));
		System.out.println(searchRelation("Secretary of State", "New Hampshire"));
		System.out.println(searchRelation("Nader", "Portland"));
		System.out.println(searchRelation("NBC", "Washington"));
		System.out.println(searchRelation("Lawrence Tribe", "Harvard"));
		System.out.println(searchRelation("Washington", "D.C."));
		System.out.println(searchRelation("Florida Green Party", "Florida"));
		System.out.println(searchRelation("Sex and the City", "John Corbett"));
		System.out.println(searchRelation("LaGuardia_Airport", "New York"));
		System.out.println(searchRelation("Camp_Zeist", "Holland"));
		System.out.println(searchRelation("Iran", "Ministry of Defense"));
		System.out.println(searchRelation("Fox Sports", "Sun Microsystems"));
		System.out.println(WikiRelationSearcher.searchExactRelations("United_States", "navy"));
		System.out.println(searchRelation("Electoral College", "Al_Gore"));
		System.out.println(searchRelation("Ralph Nader", "Green Party"));
		System.out.println(searchRelation("Bob_Hope_Airport", "John Wayne Airport"));
		System.out.println(searchRelation("Supreme Court", "Florida"));
		System.out.println(searchRelation("Prime Minister", "Government_of_the_United_Kingdom"));
//		
//	      
//		
//		System.out.println(WikiLinkIndexer.searchExactRelations("Sex_and_the_City", "John Corbett"));
//		System.out.println(WikiLinkIndexer.searchExactRelations("Sex_and_the_City", "Corbett"));
//		System.out.println(WikiLinkIndexer.searchExactRelations("Time_Inc.", "Time"));
//		System.out.println(WikiLinkIndexer.searchExactRelations("Time_Warner", "Time"));
//		System.out.println(WikiLinkIndexer.searchExactRelations("Florida", "Green Party"));
//		System.out.println(WikiLinkIndexer.searchExactRelations("Catholic_Church", "Protestant"));

		
//		System.out.println(TitleNameIndexer.normalize("Washington D.C."));
//		System.out.println(TitleNameIndexer.normalize("Washington , D.C."));
//		System.out.println(TitleNameIndexer.normalize("Washington State"));
//		System.out.println(TitleNameIndexer.normalize("Florida Green Party"));
//		System.out.println(TitleNameIndexer.normalize("NPR news"));
//		System.out.println(TitleNameIndexer.normalize("HONG KONG"));
//		System.out.println(TitleNameIndexer.normalize("AIRPLANE"));
//		System.out.println(TitleNameIndexer.normalize("China"));
//		System.out.println(StringUtils.isAlphanumeric("Al_Maliki"));
//		System.out.println(TitleNameIndexer.normalize("area \"A\""));
		
	}

}
