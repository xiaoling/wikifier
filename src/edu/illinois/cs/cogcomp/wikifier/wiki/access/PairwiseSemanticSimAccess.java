package edu.illinois.cs.cogcomp.wikifier.wiki.access;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;

import edu.illinois.cs.cogcomp.wikifier.common.GlobalParameters;
import edu.illinois.cs.cogcomp.wikifier.models.WikipediaProtobuffers.PairwiseSemanticSim;
import edu.illinois.cs.cogcomp.wikifier.utils.lucene.Lucene;
import edu.illinois.cs.cogcomp.wikifier.wiki.importing.PrecomputeSemanicSimilarityBetweenArticles;


public class PairwiseSemanticSimAccess {
	
	IndexSearcher searcher = null;
	
    public PairwiseSemanticSimAccess() throws Exception {
        searcher = Lucene.searcher(GlobalParameters.paths.protobufferAccessDir,"PairwiseSemanticsSim");
    }

	/*
	 * returns null if the relatedness is not in index!
	 */
	public PairwiseSemanticSim getRelatednessProtobuffer(int tid1, int tid2) throws Exception {
		String key = PrecomputeSemanicSimilarityBetweenArticles.getKey(tid1, tid2);
        TopDocs hits = searcher.search(new TermQuery(new Term("MinTid_MaxTid", key)), 1);
		if(hits.totalHits>0){
			return PairwiseSemanticSim.parseFrom(searcher.doc(hits.scoreDocs[0].doc).getBinaryValue("PairwiseSemanticSim").bytes);
		}
		return null;
	}
	
	public static void main(String[] args) throws Exception {
		PairwiseSemanticSimAccess access = new PairwiseSemanticSimAccess();
		System.out.println(access.getRelatednessProtobuffer(6736746, 6858448));
		System.out.println(access.getRelatednessProtobuffer(34658, 2510772));
		System.out.println(access.getRelatednessProtobuffer(2510772, 34658));

		//double[] res = access.getRelatedness(719, 304883);
		//System.out.println("("+res[0]+","+res[1]+","+res[2]+","+res[3]+","+res[4]+","+res[5]+")");
	}
}
