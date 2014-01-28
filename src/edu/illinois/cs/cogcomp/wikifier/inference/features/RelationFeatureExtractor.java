package edu.illinois.cs.cogcomp.wikifier.inference.features;

//import java.util.ArrayList;
//import java.util.List;
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.Executors;
//import java.util.concurrent.TimeUnit;
//
//import edu.illinois.cs.cogcomp.wikifier.common.GlobalParameters;
//import edu.illinois.cs.cogcomp.wikifier.models.DisambiguationProblem;
//import edu.illinois.cs.cogcomp.wikifier.models.WikifiableEntity;
//
//
//public class RelationFeatureExtractor{
//
//    DisambiguationProblem problem;
//    
//    public RelationFeatureExtractor(DisambiguationProblem problem){
//        this.problem = problem;
//    }
//    
//    public void extractFeatures() throws Exception {
//        long lastTime = System.currentTimeMillis();
//
//        // setDisambiguationContext(problem.components); // the main purpose here is to set the weights of the context entities....
//        ExtractionThread[] threads = new ExtractionThread[GlobalParameters.NUM_THREADS];
//        for (int i = 0; i < threads.length; i++)
//            threads[i] = new ExtractionThread();
//
//        for (int componentId = 0; componentId < problem.components.size(); componentId++)
//            threads[componentId % GlobalParameters.NUM_THREADS].addExtractionTask(problem.components.get(componentId));
//        
//        ExecutorService execSvc = Executors.newFixedThreadPool(GlobalParameters.NUM_THREADS);
//        
//        for(Thread extractionThread:threads)
//            execSvc.execute(extractionThread);
//        
//        execSvc.shutdown();
//        execSvc.awaitTermination(300,TimeUnit.SECONDS);
//
//        System.out.println(System.currentTimeMillis()-lastTime+" milliseconds elapsed extracting features for the level: Relation inference");
//    }
//
//    private static class ExtractionThread extends Thread{
//        
//        private static List<WikifiableEntity> extractionCandidates = new ArrayList<WikifiableEntity>(); 
//
//        public void addExtractionTask(WikifiableEntity e){
//            extractionCandidates.add(e);
//        }
//        
//        public void run(){
//            for(WikifiableEntity e:extractionCandidates){
//                e.extractEntityFeatures();
//            }
//        }
//        
//    }
//    
//
//
//}
