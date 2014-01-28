package edu.illinois.cs.cogcomp.wikifier.train;

import java.io.File;

import liblinear.Parameter;
import liblinear.Problem;
import edu.illinois.cs.cogcomp.wikifier.ReferenceAssistant;
import edu.illinois.cs.cogcomp.wikifier.common.GlobalParameters;
import edu.illinois.cs.cogcomp.wikifier.common.TrainingParameters;
import edu.illinois.cs.cogcomp.wikifier.inference.LiblinearInterface;
import edu.illinois.cs.cogcomp.wikifier.utils.learn.LearningProblemBuilder;

public abstract class TrainRelationInference {
    
    String[] args;
    
    private TrainRelationInference(String[] args){
        setup();
        this.args = args;
    }
    
    public void train() throws Exception{
        //TODO create a learning problem manager
        TrainingParameters.problemBuilder = new LearningProblemBuilder();
        ReferenceAssistant.main(args);
        
        Problem problem = TrainingParameters.problemBuilder.getProblem();
        problem.bias = 1;

        double[] scales = LiblinearInterface.scale(problem);
        LiblinearInterface.saveScalingParams(scales, getScalerPath());
        
        LiblinearInterface.save_problem(problem,getProblemPath());

        Parameter param = LiblinearInterface.getOptimalParam(problem);
        LiblinearInterface.train_and_save(problem, param, getModelPath());
    }
    
    protected abstract void setup();
    
    
    public static void trainCoref(String[] args) throws Exception{
        TrainRelationInference trainer = new TrainRelationInference(args){

            @Override
            protected void setup() {
                GlobalParameters.params.USE_RELATIONAL_INFERENCE = true;
                TrainingParameters.trainCoref = true;
            }
            
        };
        trainer.train();
    }
    
    public static void trainRelationScaler(String[] args) throws Exception{
        
        TrainRelationInference trainer = new TrainRelationInference(args){

            @Override
            protected void setup() {
                GlobalParameters.params.USE_RELATIONAL_INFERENCE = true;
                TrainingParameters.trainRelationRescale = true;
            }
            
        };
        trainer.train();
        
    }
    
    /**
     * This thing gives around 80% linker accuracy
     * @param args
     * @throws Exception
     */
    @Deprecated
    public static void trainLinker(String[] args) throws Exception{
        
        TrainRelationInference trainer = new TrainRelationInference(args){

            @Override
            protected void setup() {
                GlobalParameters.params.USE_RELATIONAL_INFERENCE = true;
                TrainingParameters.trainRelationLinker = true;
            }
            
        };
        trainer.train();
        
    }
    
    public String getModelPath(){
        return new File(GlobalParameters.paths.models,"Relation.model").getPath();
    }
    
    public String getScalerPath(){
        return new File(GlobalParameters.paths.models,"Relation.scaler").getPath();
    }
    
    public String getProblemPath(){
        return new File(GlobalParameters.paths.models,"Relation.problem").getPath();
    }
    
    public static void offlineTrain(String problemFile) throws Exception{
        Problem problem = LiblinearInterface.read_problem(problemFile);
        problem.bias = 1;
        Parameter param = LiblinearInterface.getOptimalParam(problem);
        LiblinearInterface.train_and_save(problem, param, "latestOfflineTraining.model");
    }
    /**
     * @param args
     */
    public static void main(String[] args) throws Exception{
        

        String[] trainingArgs = new String[]{
                "-referenceAssistant",
//                "coherenceTest/Problems",
//                "coherenceTest/RawTexts",
//                "coherenceTest/Output",
                "../Data/WikificationACL2011Data/MSNBC/Problems/",
                "../Data/WikificationACL2011Data/MSNBC/RawTextsSimpleChars/",
                "../Output/MSNBC/",
                "Config/XiaoConfigMongo"
        };
        
//        trainLinker(trainingArgs);
//        trainRelationScaler(trainingArgs);
        trainCoref(trainingArgs);
//        offlineTrain("../Data/Models/TitleMatchPlusLexicalPlusCoherence/Relation.problem");

    }

}
