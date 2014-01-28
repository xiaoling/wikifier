package edu.illinois.cs.cogcomp.wikifier.models.tfidf;

import edu.illinois.cs.cogcomp.wikifier.models.WikipediaProtobuffers.LexicalTitleDataInfoProto;
import edu.illinois.cs.cogcomp.wikifier.utils.datastructure.SortedObjects;
import gnu.trove.iterator.TIntDoubleIterator;
import gnu.trove.map.hash.TIntDoubleHashMap;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;


public class TFIDF {
    public long docsCount = 0;
    public FeatureMap map = null;
    public int[] IDF = null;

    public TFIDF(FeatureMap _map) {
        docsCount = 0;
        map = _map;
        IDF = new int[map.dim];
    }

    public void updateIDF(Document doc) {
        docsCount++;
        for (int fid : doc.getActiveFid(map))
            IDF[fid]++;
    }

    public void updateIDF(UnigramStatistics stat) {
        docsCount++;
        for (String w : stat.wordCounts.keySet()) {
            if (map.wordToFid.containsKey(w))
                IDF[map.wordToFid.get(w)]++;
        }
    }

    public TF_IDF_Doc getRepresentation(Document d, boolean disableIdfWeighting) {
        UnigramStatistics stat = new UnigramStatistics(true);
        for (String word : d.words)
            stat.addWord(word);
        return getRepresentation(stat, disableIdfWeighting);
    }

    public TF_IDF_Doc getRepresentation(UnigramStatistics stat, boolean disableIdfWeighting) {

        List<Integer> features = new ArrayList<Integer>();
        List<Double> weights = new ArrayList<Double>();

        for (String s : stat.wordCounts.keySet()) {

            if (map.wordToFid.containsKey(s)) {
                int fid = map.wordToFid.get(s);
                if (stat.wordCounts.containsKey(s) && IDF[fid] > 0) {
                    features.add(fid);
                    if (!disableIdfWeighting)
                        weights.add(stat.wordCounts.get(s) * Math.log(((double) docsCount) / ((double) IDF[fid])));
                    else
                        weights.add(stat.wordCounts.get(s).doubleValue());
                }

            }
        }
        
        TF_IDF_Doc doc = new TF_IDF_Doc(features.size(), false);

        for (int i = 0; i < features.size(); i++) {
            doc.activeFids[i] = features.get(i);
            doc.tfIdfWeight[i] = weights.get(i);
        }
        return doc;
    }

    public static int[] getIDF(DocumentCollection docCollection, FeatureMap map) {
        TFIDF tfidf = new TFIDF(map);
        for (int i = 0; i < docCollection.docs.size(); i++)
            tfidf.updateIDF(docCollection.docs.get(i));
        return tfidf.IDF;
    }

    public static List<TF_IDF_Doc> getRepresentation(DocumentCollection docCollection, FeatureMap map, boolean disableIdfWeighting) {
        System.out.println("Building TF IFD Representation");
        TFIDF tfidf = new TFIDF(map);
        for (Document doc:docCollection)
            tfidf.updateIDF(doc);
        List<TF_IDF_Doc> docs = new ArrayList<>();
        for (Document doc:docCollection)
            docs.add(tfidf.getRepresentation(doc, disableIdfWeighting));
        System.out.println("***Done*** Building TF IFD Representation");
        return docs;
    }

    public static double getCosineSim(TF_IDF_Doc d1, TF_IDF_Doc d2) {
        if (d1.featureMap != null && d2.featureMap != null)
            return getCosineSimHashMaps(d1, d2);
        return getCosineSimVectors(d1, d2);
    }
    


    public static double getCosineSimHashMaps(TF_IDF_Doc d1, TF_IDF_Doc d2) {
        double nominator = 0;
        TF_IDF_Doc shorter = d1;
        TF_IDF_Doc longer = d2;
        if (d1.featureMap.size() > d2.featureMap.size()) {
            shorter = d2;
            longer = d1;
        }
        
        for(TIntDoubleIterator it = shorter.featureMap.iterator();it.hasNext();){
            it.advance();
            int fid = it.key();
            if (longer.featureMap.containsKey(fid))
                nominator += shorter.featureMap.get(fid) * longer.featureMap.get(fid);
        }
        
//        for (Iterator<Entry<Integer, Double>> i = shorter.featureMap.entrySet().iterator(); i.hasNext();) {
//            Entry<Integer, Double> e = i.next();
//            int fid = e.getKey();
//            if (longer.featureMap.containsKey(fid))
//                nominator += shorter.featureMap.get(fid) * longer.featureMap.get(fid);
//        }
        if (nominator == 0)
            return 0;
        double norm1 = shorter.getWeightNorm();
        double norm2 = longer.getWeightNorm();
        return nominator / (norm1 * norm2);
    }

    public static double getCosineSimVectors(TF_IDF_Doc d1, TF_IDF_Doc d2) {
        double nominator = 0;
        TF_IDF_Doc shorter = d1;
        TF_IDF_Doc longer = d2;
        if (d1.activeFids.length > d2.activeFids.length) {
            shorter = d2;
            longer = d1;
        }
        
        TIntDoubleHashMap h = new TIntDoubleHashMap();
        for (int i = 0; i < longer.activeFids.length; i++)
            h.put(longer.activeFids[i], longer.tfIdfWeight[i]);
        for (int i = 0; i < shorter.activeFids.length; i++)
            if (h.containsKey(shorter.activeFids[i]))
                nominator += shorter.tfIdfWeight[i] * h.get(shorter.activeFids[i]);
        if (nominator == 0)
            return 0;
        double norm1 = 0;
        for (int i = 0; i < shorter.activeFids.length; i++)
            norm1 += shorter.tfIdfWeight[i] * shorter.tfIdfWeight[i];
        norm1 = Math.sqrt(norm1);
        double norm2 = 0;
        for (int i = 0; i < longer.activeFids.length; i++)
            norm2 += longer.tfIdfWeight[i] * longer.tfIdfWeight[i];
        norm2 = Math.sqrt(norm2);
        return nominator / (norm1 * norm2);
    }

    public static class TF_IDF_Doc implements Serializable {
        /**
		 * 
		 */
        private static final long serialVersionUID = 2338008209620619317L;
        
        public int[] activeFids = null;
        public double[] tfIdfWeight = null;
        // the hashmap reprsentation is only partially supported, and should be used onlt when the
        // same document is used many times
        // for TF-IDF similarity purposes, and we need to pre-compute the hashmaps!
        public TIntDoubleHashMap featureMap = null;
        // featureMap.get(activeFids[i])=tfIdfWeight[i].
        // This will speed things up, but will
        // require way more memory

        public TF_IDF_Doc(LexicalTitleDataInfoProto lexInfo) {
            featureMap = new TIntDoubleHashMap();
            for (int j = 0; j < lexInfo.getContextTokensFidsCount(); j++) {
                int fid = lexInfo.getContextTokensFids(j);
                featureMap.put(fid, lexInfo.getContextTokensFidsWeights(j));
            }

            for (int j = 0; j < lexInfo.getTextTokensFidsCount(); j++) {
                int fid = lexInfo.getTextTokensFids(j);
                double currentWeight = featureMap.containsKey(fid) ? featureMap.get(fid) : 0.0;
                featureMap.put(fid, currentWeight + lexInfo.getTextTokensFidsWeights(j));
            }
        }
        
        public double getWeightNorm(){
            double[] weights = tfIdfWeight==null? featureMap.values() : tfIdfWeight;
            double norm = 0;
            for(double w:weights){
                norm += w*w;
            }
            return Math.sqrt(norm);
        }
        
        /**
         * Creates a reweighted doc from another
         * @param original
         * @param weightedDocCount
         * @param weightedIdfCount
         */
        public TF_IDF_Doc(TF_IDF_Doc original,double weightedDocCount,TIntDoubleHashMap weightedIdfCount){
            this(original.featureMap.size(), true);
            for(TIntDoubleIterator it = original.featureMap.iterator();it.hasNext();){
                it.advance();
                int fid = it.key();
                double weight = it.value();
                featureMap.put(fid, weight * Math.log(weightedDocCount/weightedIdfCount.get(fid)));
            }
        }

        public TF_IDF_Doc(int numberOfActiveFeatures, boolean useHashMapRepresentation) {
            if (useHashMapRepresentation) {
                featureMap = new TIntDoubleHashMap();
            } else {
                activeFids = new int[numberOfActiveFeatures];
                tfIdfWeight = new double[numberOfActiveFeatures];
            }
        }

        // assigns weight 1 to all the features
        public TF_IDF_Doc(int[] activeFeatures) {
            activeFids = activeFeatures;
            tfIdfWeight = new double[activeFeatures.length];
            for (int i = 0; i < activeFeatures.length; i++)
                tfIdfWeight[i] = 1;
        }

        public TF_IDF_Doc filterTopWords(int wordsNum) {
            if (wordsNum >= activeFids.length)
                return this;
            SortedObjects<Integer> top = new SortedObjects<Integer>(wordsNum);
            TF_IDF_Doc res = new TF_IDF_Doc(wordsNum, false);
            for (int i = 0; i < activeFids.length; i++)
                top.add(new Integer(activeFids[i]), tfIdfWeight[i]);
            for (int i = 0; i < wordsNum; i++) {
                res.activeFids[i] = top.topObjects.get(i);
                res.tfIdfWeight[i] = top.topScores.get(i);
            }
            return res;
        }

        public TF_IDF_Doc filterFids(int[] remainingFids) {
            boolean[] mask = new boolean[activeFids.length];
            int count = 0;
            for (int i = 0; i < activeFids.length; i++) {
                for (int j = 0; j < remainingFids.length; j++)
                    if (activeFids[i] == remainingFids[j])
                        mask[i] = true;
                if (mask[i])
                    count++;
            }
            TF_IDF_Doc res = new TF_IDF_Doc(count, false);
            int pos = 0;
            for (int i = 0; i < activeFids.length; i++)
                if (mask[i]) {
                    res.activeFids[pos] = activeFids[i];
                    res.tfIdfWeight[pos] = tfIdfWeight[i];
                    pos++;
                }
            return res;
        }

        public String toString(FeatureMap map) {
            String res = "";
            for (int i = 0; i < activeFids.length; i++)
                if (map.fidToWord.containsKey(activeFids[i]))
                    res += map.fidToWord.get(activeFids[i]) + " ";
            return res;
        }

    }

    public static void main(String[] args) {
        // test this class!
        String[] d1 = { "a", "b", "c" };
        String[] d2 = { "a", "a", "a", "c" };
        String[] d3 = { "b", "b", "b", "c", "e" };
        Document dd1 = new Document(d1, 1, null);
        Document dd2 = new Document(d2, 1, null);
        Document dd3 = new Document(d3, 1, null);
        DocumentCollection docs = new DocumentCollection();
        docs.addDoc(dd1);
        docs.addDoc(dd2);
        docs.addDoc(dd3);
        FeatureMap map = new FeatureMap();
        map.addDocs(docs, 0, true);
        List<TF_IDF_Doc> docs2 = TFIDF.getRepresentation(docs, map, false);
        for (int i = 0; i < docs2.size(); i++) {
            for (int j = 0; j < docs2.get(i).activeFids.length; j++) {
                int fid = docs2.get(i).activeFids[j];
                System.out.print(map.fidToWord.get(fid) + "(" + docs2.get(i).tfIdfWeight[j] + ") ");
            }
            System.out.println("");
        }
        System.out.println("\n\nfiltered version:");
        for (int i = 0; i < docs2.size(); i++) {
            TF_IDF_Doc doc = docs2.get(i).filterTopWords(1);
            for (int j = 0; j < doc.activeFids.length; j++) {
                int fid = doc.activeFids[j];
                System.out.print(map.fidToWord.get(fid) + "(" + doc.tfIdfWeight[j] + ") ");
            }
            System.out.println("");
        }
    }
}
