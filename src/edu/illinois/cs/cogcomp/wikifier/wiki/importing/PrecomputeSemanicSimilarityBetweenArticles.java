package edu.illinois.cs.cogcomp.wikifier.wiki.importing;

import java.util.HashMap;
import java.util.Iterator;
import java.util.StringTokenizer;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexWriter;

import edu.illinois.cs.cogcomp.wikifier.inference.features.SimilarityMetrics;
import edu.illinois.cs.cogcomp.wikifier.models.WikipediaProtobuffers.PairwiseSemanticSim;
import edu.illinois.cs.cogcomp.wikifier.models.WikipediaProtobuffers.SemanticTitleDataInfoProto;
import edu.illinois.cs.cogcomp.wikifier.utils.io.InFile;
import edu.illinois.cs.cogcomp.wikifier.utils.io.OutFile;
import edu.illinois.cs.cogcomp.wikifier.utils.lucene.Lucene;
import edu.illinois.cs.cogcomp.wikifier.wiki.access.ProtobufferBasedWikipediaAccess;

public class PrecomputeSemanicSimilarityBetweenArticles {
	
	
	public static String outFolder = "./WikiData/PairwiseSemanticSim";
		
	public static void precomputeAndSavePairwiseSim(ProtobufferBasedWikipediaAccess wikiAccess) throws Exception {
		IndexWriter idx = Lucene.storeOnlyWriter(outFolder+"/Index");
		OutFile debugFile = new OutFile(outFolder+"/pairwiseSim.debug.txt");		
		Iterator<Integer> titleIds = wikiAccess.iterator();
		HashMap<String, PairwiseSemanticSim> simData = new HashMap<String, PairwiseSemanticSim>();
		int articlesProcessed =0;
		while(titleIds.hasNext() ){ //&&articlesProcessed<1000){
			articlesProcessed++;
			if(articlesProcessed%100==0)
				System.out.println(articlesProcessed+" articles processed");
			int tid1 = titleIds.next();
			SemanticTitleDataInfoProto seminfo1 = wikiAccess.getSemanticInfo(tid1);
			HashMap<Integer,Boolean> inlinksTid1 = new HashMap<Integer, Boolean>();
			for(int i=0;i<seminfo1.getIncomingLinksIdsCount();i++)
				inlinksTid1.put(seminfo1.getIncomingLinksIds(i), true);
			HashMap<Integer,Boolean> outlinksTid1 = new HashMap<Integer, Boolean>();
			for(int i=0;i<seminfo1.getOutgoingLinksIdsCount();i++)
				outlinksTid1.put(seminfo1.getOutgoingLinksIds(i), true);
			for(int i=0;i<seminfo1.getOutgoingLinksIdsCount();i++) {
				// inTid points to tid
				int tid2 = seminfo1.getOutgoingLinksIds(i);				
				String key = getKey(tid1, tid2);
				if(!simData.containsKey(key)){
					 PairwiseSemanticSim.Builder builder = PairwiseSemanticSim.newBuilder(); 
					 SemanticTitleDataInfoProto seminfo2 = wikiAccess.getSemanticInfo(tid2);					
					 int intersectionIn = 0;
					 for(int j=0;j<seminfo2.getIncomingLinksIdsCount();j++)
						 if(inlinksTid1.containsKey(seminfo2.getIncomingLinksIds(j)))
							 intersectionIn++;
					 int intersectionOut = 0;
					 boolean mutualLink = false;
					 for(int j=0;j<seminfo2.getOutgoingLinksIdsCount();j++) {
						 if(seminfo2.getOutgoingLinksIds(j)==tid1)
							 mutualLink = true;
						 if(outlinksTid1.containsKey(seminfo2.getOutgoingLinksIds(j)))
							 intersectionOut++;
					 }
					 builder.setIncomingLinksPmi(SimilarityMetrics.approxPMI(seminfo1.getIncomingLinksIdsCount(), seminfo2.getIncomingLinksIdsCount(), intersectionIn));
					 builder.setIncomingLinksNormalizedGoogleDistanceSim(
							 SimilarityMetrics.normalizedGoogleDistanceSimilarity(seminfo1.getIncomingLinksIdsCount(), seminfo2.getIncomingLinksIdsCount(), intersectionIn));
					 builder.setOutgoingLinksPmi(SimilarityMetrics.approxPMI(seminfo1.getOutgoingLinksIdsCount(), seminfo2.getOutgoingLinksIdsCount(), intersectionOut));
					 builder.setOutgoingLinksNormalizedGoogleDistanceSim(
							 SimilarityMetrics.normalizedGoogleDistanceSimilarity(seminfo1.getOutgoingLinksIdsCount(), seminfo2.getOutgoingLinksIdsCount(), intersectionOut));
					 builder.setOneWayLink(true);
					 builder.setTwoWayLink(mutualLink);
					 simData.put(key, builder.build());
				}
			}
		}
		
		for(Iterator<String> iter=simData.keySet().iterator();iter.hasNext();){
			String key = iter.next();
			PairwiseSemanticSim sim = simData.get(key);
			Document doc = new Document();
			doc.add(new StringField("MinTid_MaxTid", key, Store.YES));
			doc.add(new StoredField("PairwiseSemanticSim",sim.toByteArray()));
			idx.addDocument(doc);
			int tid1 = Integer.parseInt(key.substring(0,key.indexOf('_')));
			int tid2 = Integer.parseInt(key.substring(key.indexOf('_')+1,key.length()));
			debugFile.println(key+"\t"+wikiAccess.getBasicInfo(tid1).getTitleSurfaceForm()+"<->"+wikiAccess.getBasicInfo(tid2).getTitleSurfaceForm()+"\t"+ sim.toString());
		}
		idx.close();
		debugFile.close();
	}

	public static void buildIndexFromDegubLog(String debugLogFile) throws Exception{
		IndexWriter idx = Lucene.storeOnlyWriter(outFolder+"/Index");
		InFile in = new InFile(debugLogFile);
		int pairsCount = 0;
		String line =in.readLine();
		while(line!=null) {//&&pairsCount<100){
			pairsCount++;
			PairwiseSemanticSim.Builder sim = PairwiseSemanticSim.newBuilder();
			StringTokenizer st = new StringTokenizer(line,"\t ");
			String key = st.nextToken();
			st.nextToken(); st.nextToken();
			sim.setIncomingLinksPmi(Double.parseDouble(st.nextToken()));
			line = in.readLine();
			st = new StringTokenizer(line,"\t ");
			st.nextToken();
			sim.setIncomingLinksNormalizedGoogleDistanceSim(Double.parseDouble(st.nextToken()));
			line = in.readLine();
			st = new StringTokenizer(line,"\t ");
			st.nextToken();
			sim.setOutgoingLinksPmi(Double.parseDouble(st.nextToken()));
			line = in.readLine();
			st = new StringTokenizer(line,"\t ");
			st.nextToken();
			sim.setOutgoingLinksNormalizedGoogleDistanceSim(Double.parseDouble(st.nextToken()));
			line = in.readLine();
			st = new StringTokenizer(line,"\t ");
			st.nextToken();
			sim.setOneWayLink(Boolean.parseBoolean(st.nextToken()));
			line = in.readLine();
			st = new StringTokenizer(line,"\t ");
			st.nextToken();
			sim.setTwoWayLink(Boolean.parseBoolean(st.nextToken()));
			PairwiseSemanticSim build = sim.build();
			if(pairsCount%1000==0){
				System.out.println(pairsCount+" sim points read; last point:");
				System.out.println("Read pairwise sim point: for key "+key+" \n"+build.toString());
			}
			Document doc = new Document();
            doc.add(new StringField("MinTid_MaxTid", key, Store.YES));
            doc.add(new StoredField("PairwiseSemanticSim", build.toByteArray()));
			idx.addDocument(doc);
			line = in.readLine();
			line = in.readLine();
		}
		in.close();
		idx.close();
	}
	
	public static void main(String[] args) throws Exception {
		/*System.out.println("Loading the indices and data structures");
		ParametersAndGlobalVariables.pathToEvaluationRedirectsData="./WikiData/RedirectsForEvaluationData/RedirectsAug2010.txt";
		ProtobufferBasedWikipediaAccess wikiAccess=  new ProtobufferBasedWikipediaAccess(
				"./WikiData/ProtobuffersData/WikiSummary.proto.save", 
				"./WikiData/ProtobuffersData/Indices/CompleteWikipediaIndex", 
				"./WikiData/ProtobuffersData/Indices/SurfaceFromsInfo");
		ParametersAndGlobalVariables.wikiAccess = wikiAccess;
		precomputeAndSavePairwiseSim(wikiAccess);*/
		buildIndexFromDegubLog(outFolder+"/pairwiseSim.debug.txt");
	}
	
	public static String getKey(int tid1, int tid2){
		int min = tid1, max = tid2;
		if(tid1>tid2){
			min = tid2;
			max = tid1;
		}
		return min+"_"+max;
	}
}
