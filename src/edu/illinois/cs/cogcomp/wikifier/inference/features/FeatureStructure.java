package edu.illinois.cs.cogcomp.wikifier.inference.features;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class FeatureStructure implements Serializable {

    static final Logger logger = Logger.getLogger(FeatureStructure.class);
    static {
        logger.setLevel(Level.OFF);
    }

    private static class FeatureManager {

        private Map<String, Map<String, Integer>> featureNameToIdByFeatureGroup = new HashMap<String, Map<String, Integer>>();
        private Map<String, Map<Integer, String>> featureIdToNameByFeatureGroup = new HashMap<String, Map<Integer, String>>();

        public Map<String, Integer> getFeatureNameMapByGroup(String featureGroup) {
            if (!featureNameToIdByFeatureGroup.containsKey(featureGroup)) {
                initializeFeatureGroup(featureGroup);
            }
            return featureNameToIdByFeatureGroup.get(featureGroup);
        }

        public Map<Integer, String> getFeatureIdMapByGroup(String featureGroup) {
            if (!featureIdToNameByFeatureGroup.containsKey(featureGroup)) {
                initializeFeatureGroup(featureGroup);
            }
            return featureIdToNameByFeatureGroup.get(featureGroup);
        }

        // Fast synchronized initialization, will only be blocked at most NUM_THREAD times
        private synchronized void initializeFeatureGroup(String featureGroup) {
            if (!featureIdToNameByFeatureGroup.containsKey(featureGroup)) {
                logger.debug("Creating feature group " + featureGroup);
                featureNameToIdByFeatureGroup.put(featureGroup, new HashMap<String, Integer>());
                featureIdToNameByFeatureGroup.put(featureGroup, new HashMap<Integer, String>());
            }
        }

    }

    /**
	 * 
	 */
    private static final long serialVersionUID = 6878797422238373348L;
    // the featuremap hashmap is shared among all instances of the features
    private static final FeatureManager featureManager = new FeatureManager();

    // keep these two fields private - they can contain repetitive feature instances, so
    // if you work with them without care, it'll end in tears.
    private String featureGroup = null;
    private List<Integer> featureIds = new ArrayList<Integer>();
    private List<Double> featureVals = new ArrayList<Double>();
    private Map<String, Integer> featureNameToId;
    private Map<Integer, String> featureIdToName;

    public FeatureStructure(String featureGroup) {
        this.featureGroup = featureGroup;
        featureNameToId = featureManager.getFeatureNameMapByGroup(featureGroup);
        featureIdToName = featureManager.getFeatureIdMapByGroup(featureGroup);
    }

    public FeatureStructure(FeatureStructure other) {
        featureGroup = other.featureGroup;
        featureNameToId = other.featureNameToId;
        featureIdToName = other.featureIdToName;
        featureIds = new ArrayList<Integer>(other.featureIds);
        featureVals = new ArrayList<Double>(other.featureVals);
    }

    // Fast synchronized initialization, will only be blocked at most NUM_THREAD*NUM_FEATURE times
    private void intializeFeature(String featureName) {
        synchronized (featureNameToId) {
            if (!featureNameToId.containsKey(featureName)) {
                int fid = featureNameToId.size();
                logger.debug("Mapped feature " + featureName + " in group " + featureGroup + " to id " + fid);
                featureNameToId.put(featureName, fid);
                featureIdToName.put(fid, featureName);
            }
        }
    }

    /*
     * Adds the features to the feature vector. If multiple features with the same name were added,
     * the value of the last addition is taken
     */
    public void addFeature(String name, double val) {

        if (!featureNameToId.containsKey(name)) {
            intializeFeature(name);
        }

        int fid = featureNameToId.get(name);

        featureIds.add(fid);
        featureVals.add(val);
    }

    public double[] getFeatureVector() {
        double[] res = new double[featureIdToName.size()];
        for (int i = 0; i < featureVals.size(); i++)
            res[featureIds.get(i)] = featureVals.get(i);
        return res;
    }

    public double getFeatureValue(String featureName) {
        double[] feats = getFeatureVector();
        return feats[featureNameToId.get(featureName)];
    }

    public String toString() {
        StringBuilder res = new StringBuilder(200);
        double[] feats = getFeatureVector();
        for (int i = 0; i < feats.length; i++)
            res.append("[(" + i + ") " + featureIdToName.get(i) + "," + feats[i] + "] ");
        return res.toString();
    }

    public static void test() throws Exception {
        Thread t1 = new Thread() {
            public void run() {
                for (int i = 0; i < 100000; i++) {
                    FeatureStructure type1instance1 = new FeatureStructure("type1");
                    type1instance1.addFeature("a", 1);
                    type1instance1.addFeature("b", 2);
                    Thread.yield();
                    FeatureStructure type2instance1 = new FeatureStructure("type2");
                    type2instance1.addFeature("a2", 1);
                    type2instance1.addFeature("b2", 2);
                }
            }
        };
        Thread t2 = new Thread() {
            public void run() {
                for (int i = 0; i < 100000; i++) {
                    FeatureStructure type1instance1 = new FeatureStructure("type1");
                    type1instance1.addFeature("a", 1);
                    type1instance1.addFeature("b", 2);
                    FeatureStructure type2instance1 = new FeatureStructure("type2");
                    type2instance1.addFeature("a2", 1);
                    type2instance1.addFeature("b2", 2);
                }
            }
        };
        t1.start();
        t2.start();
        // FeaturesDatastructure type1instance1 = new FeaturesDatastructure("type1");
        // type1instance1.addFeature("a", 1);
        // type1instance1.addFeature("b", 2);
        // FeaturesDatastructure type1instance2 = new FeaturesDatastructure("type1");
        // type1instance2.addFeature("c", 1);
        // type1instance2.addFeature("b", 2);
        // FeaturesDatastructure type2instance1 = new FeaturesDatastructure("type2");
        // type2instance1.addFeature("a2", 1);
        // type2instance1.addFeature("b2", 2);
        // FeaturesDatastructure type2instance2 = new FeaturesDatastructure("type2");
        // type2instance2.addFeature("c2", 1);
        // type2instance2.addFeature("b2", 2);
        // System.out.println(type2instance1.toString());
        // System.out.println(type1instance1.toString());
        // System.out.println(type1instance2.toString());
        // System.out.println(type2instance2.toString());

    }
}
