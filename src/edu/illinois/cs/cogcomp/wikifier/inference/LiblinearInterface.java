package edu.illinois.cs.cogcomp.wikifier.inference;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.StringTokenizer;

import liblinear.Linear;
import liblinear.Model;
import liblinear.Parameter;
import liblinear.Problem;
import liblinear.SolverType;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import edu.illinois.cs.cogcomp.wikifier.utils.io.InFile;
import edu.illinois.cs.cogcomp.wikifier.utils.io.OutFile;


public class LiblinearInterface {
	private static int nr_cosssvalidation_folds=5;
	private static boolean scale_problems = true;


	public static int make_prediction(double[] features, liblinear.Model model) {
		return liblinear.Linear.predict(model, featureVectorToSvmFormat(features));
	}
	
	//Only binary allowed, otherwise exception will be thrown
	public static double predictProbability(double[] features,Model model){
	    double[] binaryLabel = new double[2];
	    Linear.predictProbability(model, featureVectorToSvmFormat(features), binaryLabel);
	    return binaryLabel[1];
	}
	
	public static double weightedBinaryClassification(double[] features, liblinear.Model model) throws Exception {
		int[] labels = model.getLabels();
		if(labels[0]+labels[1]!=0||labels[0]*labels[1]!=-1||labels.length!=2)
			throw new Exception("Expecting only binary classification with +1/-1 target labels!");
		double[] values = new double[2];
		liblinear.Linear.predictValues(model,  featureVectorToSvmFormat(features), values);
		return labels[0]*values[0]+labels[1]*values[1];
	}
		
	public static liblinear.FeatureNode[] featureVectorToSvmFormat(double[] features){
		liblinear.FeatureNode[] res = new liblinear.FeatureNode[features.length];
		for(int j=0;j<features.length;j++) 
			res[j] = new liblinear.FeatureNode(j+1, features[j]);
		return res;
	}
	
	public static double[]  trimAndScale(double[] feature_vector, List<Double> scaling) throws Exception {
		if(scaling.size()>feature_vector.length)
			throw new Exception("The length of the scaling vector is larger than the size of the feature vector");
		double[] res=new double[scaling.size()];
        for (int i = 0; i < res.length; i++) {
            if (scaling != null && i < scaling.size() && scaling.get(i) > 0)
                res[i] = feature_vector[i] / scaling.get(i);
            else
                res[i] = feature_vector[i];
		}
		return res;
	}
	
	
	// 0 index is left out, index exactly corresponds to the FeatureNode index
	public static double[] scale(liblinear.Problem prob){
		int max_index=0;
		HashMap<Integer,Double> scaling_factors=new HashMap<Integer, Double>();
		for(int i=0;i<prob.x.length;i++){
			for(int j=0;j<prob.x[i].length;j++){
				int idx = prob.x[i][j].index;
				if(max_index < idx)
					max_index = idx;
				double max = 0;// since we do Math.abs, 0 is really the min we can have
				if(scaling_factors.containsKey(idx))
					max = scaling_factors.get(idx);
				if(Math.abs(prob.x[i][j].value)>=max||!scaling_factors.containsKey(idx))
					scaling_factors.put(idx,Math.abs(prob.x[i][j].value));
			}
		}
		for(int i=0;i<prob.x.length;i++)
			for(int j=0;j<prob.x[i].length;j++) {
				int idx = prob.x[i][j].index;
				if(scaling_factors.get(idx)>0) {
					prob.x[i][j].value = prob.x[i][j].value/scaling_factors.get(idx);
				}
			}
        double[] scaling = new double[max_index];
        for (int i = 1; i <= max_index; i++) {
            if (scaling_factors.containsKey(i) && scaling_factors.get(i) > 0)
                scaling[i-1] = scaling_factors.get(i);
            else
                scaling[i-1] = 1.0;
		}
		return scaling;
	}

	public static liblinear.Model train_and_save(liblinear.Problem prob, liblinear.Parameter param, String model_path) throws IOException {
		liblinear.Model model = liblinear.Linear.train(prob, param);
		File f = new File(model_path);
		liblinear.Linear.saveModel(f, model);
		//liblinear.svm_save_model(model_path,model);
		return model;
	}

	public static liblinear.Model loadModel(String model_path) throws Exception {
		return liblinear.Linear.loadModel(new File(model_path));
	}

	public static double do_cross_validation(liblinear.Problem prob, liblinear.Parameter param) {
		int[] target = new int[prob.y.length];
		liblinear.Linear.crossValidation(prob, param, nr_cosssvalidation_folds, target);
		double correct = 0;
		for(int i=0;i<prob.y.length;i++)
			if(prob.y[i]==target[i])
				correct ++;
		double acc = 100.0*correct/prob.y.length;
		System.out.print("Cross Validation Accuracy = "+acc+"%\n");
			return acc;
	}
	
	public static liblinear.Parameter getOptimalParam(liblinear.Problem problem /*, double weightOfPosInstance, double weightOfNegativeInstance*/) throws Exception {
//		double[] costs=new double[]{2.0, 5.0, 10.0, 20.0, 50.0, 100, 150, 200, 250,  300, 350,  400, 450, 500, 550,  600, 650, 700, 750, 800, 850, 900, 950, 1000};
		double[] costs=new double[]{150,180, 200, 220, 210};
//		double[] costs=new double[]{0.03125,0.0625,0.125,0.25, 5.0, 10.0,20.,50.,100.,200,500,550,  600, 650, 700, 750, 800, 850, 900, 950, 1000};
		double[] epss=new double[]{0.01,0.015,0.02};
		SolverType[] solvers =new SolverType[]{liblinear.SolverType.L2R_LR,SolverType.L2R_L2LOSS_SVC,SolverType.L2R_L2LOSS_SVC_DUAL};// {new SolverType[]{liblinear.SolverType.L2R_L2LOSS_SVC}};//; //;  // ;//new SolverType[]{SolverType.L1LOSS_SVM_DUAL}; 
		liblinear.Parameter bestParam = null;
		double best_performance=-1;
		for(int i=0;i<costs.length;i++)
			for(int j=0;j<solvers.length;j++)
				for(int k=0;k<epss.length;k++)
				{
					liblinear.Parameter param = new Parameter(solvers[j], costs[i], epss[k]); 
					// turns out that on my problem, this regularization beats the opposition hands down
					//param.setWeights(new double[]{weightOfPosInstance, weightOfNegativeInstance}, new int[]{+1,-1});
					double current=do_cross_validation(problem,param);
					if(current>best_performance){
						best_performance=current;
						bestParam=param;
					}
				}
		System.out.println("-------- Best parameter = "+paramString(bestParam)+"  >>>>>>>>> Best perfomance="+best_performance);
		return bestParam;
	}
	
	public static void save_problem(liblinear.Problem prob, String outfile){
		OutFile out = new OutFile(outfile);
		for(int i=0;i<prob.x.length;i++){
			out.print(prob.y[i]+"\t");
			for(int j=0;j<prob.x[i].length;j++)
				out.print("\t"+(prob.x[i][j].index)+":"+prob.x[i][j].value); 
			// remember that the indices in the problem start from 1 and not for 0 like i like, so everything's shifted
			out.println("");
		}
		out.close();
	}
	
	public static void save_problem_1index(liblinear.Problem prob, String outfile){
		OutFile out = new OutFile(outfile);
		for(int i=0;i<prob.x.length;i++){
			out.print(prob.y[i]+" ");
			for(int j=0;j<prob.x[i].length;j++)
				out.print(" "+(prob.x[i][j].index)+":"+prob.x[i][j].value); 
			out.println("");
		}
		out.close();
	}
	
	public static liblinear.Problem read_problem_noScaling(String input_file_name) throws IOException
	{
		liblinear.Problem prob= new liblinear.Problem();
		BufferedReader fp = new BufferedReader(new FileReader(input_file_name));
		List<Integer> vy = new ArrayList<Integer>();
		List<liblinear.FeatureNode[]> vx = new ArrayList<liblinear.FeatureNode[]>();

		while(true)
		{
			String line = fp.readLine();
			if(line == null) break;

			StringTokenizer st = new StringTokenizer(line," \t\n\r\f:");
			int y= (int)Double.parseDouble(st.nextToken());
			int m = st.countTokens()/2;
			liblinear.FeatureNode[] x = new liblinear.FeatureNode[m];
			for(int j=0;j<m;j++)
				x[j] = new liblinear.FeatureNode(atoi(st.nextToken()), atof(st.nextToken()));
			vx.add(x);
			vy.add(y);
		}

		prob = new liblinear.Problem();
		prob.l = vy.size();
		prob.x = new liblinear.FeatureNode[prob.l][];
		for(int i=0;i<prob.l;i++)
			prob.x[i] = vx.get(i);
		prob.y = new int[prob.l];
		for(int i=0;i<prob.l;i++)
			prob.y[i] = vy.get(i);

		fp.close();
		prob.bias = 0;
		prob.n = prob.x[0].length+1;
		return prob;
	}
	
	public static liblinear.Problem read_problem(String input_file_name) throws IOException
	{
		liblinear.Problem prob= new liblinear.Problem();
		BufferedReader fp = new BufferedReader(new FileReader(input_file_name));
		List<Integer> vy = new ArrayList<Integer>();
		List<liblinear.FeatureNode[]> vx = new ArrayList<liblinear.FeatureNode[]>();

		while(true)
		{
			String line = fp.readLine();
			if(line == null) break;

			StringTokenizer st = new StringTokenizer(line," \t\n\r\f:");
			int y= (int)Double.parseDouble(st.nextToken());
			int m = st.countTokens()/2;
			liblinear.FeatureNode[] x = new liblinear.FeatureNode[m];
			for(int j=0;j<m;j++)
				x[j] = new liblinear.FeatureNode(atoi(st.nextToken()), atof(st.nextToken()));
			vx.add(x);
			vy.add(y);
		}

		prob = new liblinear.Problem();
		prob.l = vy.size();
		prob.x = new liblinear.FeatureNode[prob.l][];
		for(int i=0;i<prob.l;i++)
			prob.x[i] = vx.get(i);
		prob.y = new int[prob.l];
		for(int i=0;i<prob.l;i++)
			prob.y[i] = vy.get(i);


		if(scale_problems  )
			scale(prob);
		fp.close();
		prob.bias = 0;
		prob.n = prob.x[0].length+1;
		return prob;
	}
	
	private static double atof(String s)
	{
		double d = Double.valueOf(s).doubleValue();
		if (Double.isNaN(d) || Double.isInfinite(d))
		{
			System.out.print("NaN or Infinity in input\n");
			System.exit(1);
		}
		return(d);
	}

	private static int atoi(String s)
	{
		return Integer.parseInt(s);
	}

	public static void saveScalingParams(double[] scaling,String pathToScalingData) {
		
		OutFile out=new OutFile(pathToScalingData);
		out.println(StringUtils.join(ArrayUtils.toObject(scaling), '\t'));
		out.close();
	}
	
	public static List<Double> loadScalingParams(String pathToScalingData) {
		InFile in=new InFile(pathToScalingData);
		String line=in.readLine();
		in.close();
		StringTokenizer st = new StringTokenizer(line,"\n \t");
		List<Double> v=new ArrayList<Double>();
		while(st.hasMoreTokens())
			v.add(Double.parseDouble(st.nextToken()));
		return v;
	}
	
	private static String paramString(Parameter param){
	    return String.format("C=%f,Eps=%f,SolverType=%s", param.getC(),param.getEps(),param.getSolverType().name());
	}
	
	public static void main(String argv[]) throws Exception {
		/*svm_problem problem = read_problem("./WikiData/Models/linkerSvm.training.data.txt");
		double best_cost = getOptimalCost(problem);
		double bestGamma = getAppropriateGamma( problem);
		svm_parameter param = getNewParam(best_cost, bestGamma);
		System.out.println(" ????????????????  What's the actual 5-fold crossvalidation for that cost ?????????????????");
		System.out.println("---------------------------------------------------------------->"+do_cross_validation(problem, param));
		train_and_save(problem,param, "./WikiData/Models/linker.svm.model");*/
		liblinear.Problem problem = read_problem("./WikiData/Models/CoherenceRanker.training.data.txt");
		double[] scaling = scale(problem);
		Parameter param = getOptimalParam(problem /*, 1, 1*/);
		System.out.println(" ????????????????  What's the actual 5-fold crossvalidation for that cost ?????????????????");
		System.out.println("-------------------------------------------------------------->"+do_cross_validation(problem, param));
		train_and_save(problem,param, "./WikiData/Models/ranker.svm.model");
		Model model2= loadModel("./WikiData/Models/ranker.svm.model");
		double[] test_feats = new double[]{-0.0035471476366600143,	0.0,	0.0,	-0.9522883083194368,	-0.9962201977127351,	3.0,	0.0,	0.0,	0.0,	0.0,	-0.5466465750879747,	0.0,	0.0,	0.0,	0.0,	0.0	, 0.0	, 0.0	, -0.017070436452921382	, 0.012523874791555152	, -0.031100182723529834	, -0.06702917107272459	, 0.0	,0.0	,0.025732839511422127	, 0.014929377735225506	,0.01201068346450321	,0.38519245012721076	,0.0	,0.0	,0.0	,0.0};
		System.out.println("Scaling features len = "+scaling.length+" ; vector len = "+test_feats.length);
		List<Double> feats_v = new ArrayList<Double>();
		for(int i=0;i<test_feats.length;i++)
			feats_v.add(test_feats[i]);
		System.out.println("prediction -- "+ make_prediction(trimAndScale(scaling, feats_v), model2));
		System.out.println("Weighted decision score: " + weightedBinaryClassification(test_feats, model2));
	}
	
	public static String printParam(Parameter p){
		String paramS = String.format("SolverType: %s\nC: %s\neps: %s\n", p.getSolverType(),p.getC(),p.getEps());		
		System.out.println(paramS);
		return paramS;
	}

	/**
	 * Sanity checks the consistency of problems
	 * @param p1
	 * @param p2
	 * @return
	 */
	public static boolean problemEquals(Problem p1,Problem p2){
		try{
			boolean consistent = Arrays.equals(p1.y, p2.y);
			for(int i = 0;i<p1.x.length;i++)
				consistent = consistent && Arrays.equals(p1.x[i], p2.x[i]);
			return consistent;
		}
		catch(Exception e){
			
		}
		return false;
	}

}
