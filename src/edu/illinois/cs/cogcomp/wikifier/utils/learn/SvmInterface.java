package edu.illinois.cs.cogcomp.wikifier.utils.learn;

public class SvmInterface {
//	private static int nr_cosssvalidation_folds=5;
//	private static boolean scale_problems = true;
//
//	public static svm_parameter getNewParam(double cost, double gamma) throws Exception{
//		if(ParametersAndGlobalVariables.SVM_KERNEL==svm_parameter.RBF) 
//			return newRbfSvmParams(cost, gamma);
//		if(ParametersAndGlobalVariables.SVM_KERNEL==svm_parameter.LINEAR)
//			return newLinearSvmParams(cost, gamma);
//		throw new Exception("Unknown SVM kernel type: "+ParametersAndGlobalVariables.SVM_KERNEL);
//	}
//	private static svm_parameter newRbfSvmParams(double cost, double gamma) {
//		svm_parameter param = new svm_parameter();
//		// default values
//		param.svm_type = svm_parameter.C_SVC;
//		param.kernel_type = svm_parameter.RBF;
//		param.degree = 3;
//		param.gamma = gamma;	// 1/num_features
//		param.coef0 = 0;
//		param.nu = 0.5;
//		param.cache_size = 100;
//		param.C = cost;
//		param.eps = 1e-3;
//		param.p = 0.1;
//		param.shrinking = 1;
//		param.probability = 1;
//		param.nr_weight = 0;
//		param.weight_label = new int[0];
//		param.weight = new double[0];
//		return param;
//	}
//	
//	private static svm_parameter newLinearSvmParams(double cost, double gamma) {
//		svm_parameter param = new svm_parameter();
//		// default values
//		param.svm_type = svm_parameter.C_SVC;
//		param.kernel_type = svm_parameter.LINEAR;
//		param.degree = 3;
//		param.gamma = gamma;	// 1/num_features
//		param.coef0 = 0;
//		param.nu = 0.5;
//		param.cache_size = 100;
//		param.C = cost;
//		param.eps = 1e-3;
//		param.p = 0.1;
//		param.shrinking = 1;
//		param.probability = 1;
//		param.nr_weight = 0;
//		param.weight_label = new int[0];
//		param.weight = new double[0];
//		return param;
//	}
//
//	public static double make_prediction(double[] features, svm_model model) {
//		return libsvm.svm.svm_predict(model,featureVectorToSvmFormat(features));
//	}
//	
//	public static double get_weighted_value_of_binary_classification(double[] features, svm_model model) throws Exception {
//		if(libsvm.svm.svm_get_nr_class(model)!=2)
//			throw new Exception("Expecting only binary classification with +1/-1 target labels!");
//		double[] values = new double[2];
//		int[] labels = new int[2];
//		libsvm.svm.svm_predict_values(model,featureVectorToSvmFormat(features), values);
//		libsvm.svm.svm_get_labels(model,labels);
//		if(labels[0]+labels[1]!=0&&labels[0]*labels[1]==-1)
//			throw new Exception("Expecting only binary classification with +1/-1 target labels!");
//		return labels[0]*values[0]+labels[1]*values[1];
//	}
//	
//	public static double get_prediction_confidence(double[] features, svm_model model) throws Exception {
//		double[] prob_estimates = new double[libsvm.svm.svm_get_nr_class(model)];
//		libsvm.svm.svm_predict_probability(model,featureVectorToSvmFormat(features), prob_estimates);
//		double max=0;
//		for(int i=0;i<prob_estimates.length;i++) {
//			//System.out.println("conf="+prob_estimates[i]);
//			double d=prob_estimates[i];
//			if(d>1||d<0)
//				throw new Exception("What is supposed to be a probability is actually not a probability");
//			if(d>max)
//				max=d;
//		}
//		return max;
//	}
//	
//	public static svm_node[] featureVectorToSvmFormat(double[] features){
//		svm_node[] res = new svm_node[features.length];
//		for(int j=0;j<features.length;j++) {
//			res[j] = new svm_node();
//			res[j].index=j;
//			res[j].value=features[j];
//		}
//		return res;
//	}
//	
//	public static double[]  returnScaledVectorCopy(double[] feature_vector,ArrayList<Double> scaling) throws Exception {
//		if(scaling.size()!=feature_vector.length)
//			throw new Exception("The length of the scaling vector does not match the length of the feature vector");
//		double[] res=new double[feature_vector.length];
//		for(int i=0;i<feature_vector.length;i++){
//			if(scaling!=null&&i<scaling.size()&&scaling.get(i)>0)
//				res[i]=feature_vector[i]/scaling.get(i);
//			else
//				res[i]=feature_vector[i];
//		}
//		return res;
//	}
//	
//	public static double[] scale(svm_problem prob){
//		int max_index=0;
//		HashMap<Integer,Double> scaling_factors=new HashMap<Integer, Double>();
//		for(int i=0;i<prob.x.length;i++){
//			for(int j=0;j<prob.x[i].length;j++){
//				if(max_index<prob.x[i][j].index)
//					max_index = prob.x[i][j].index;
//				double max = 0;// since we do Math.abs, 0 is really the min we can have
//				if(scaling_factors.containsKey(prob.x[i][j].index))
//					max = scaling_factors.get(prob.x[i][j].index);
//				if(Math.abs(prob.x[i][j].value)>=max||!scaling_factors.containsKey(prob.x[i][j].index))
//					scaling_factors.put(prob.x[i][j].index,Math.abs(prob.x[i][j].value));
//			}
//		}
//		for(int i=0;i<prob.x.length;i++)
//			for(int j=0;j<prob.x[i].length;j++)
//				if(scaling_factors.get(prob.x[i][j].index)>0)
//					prob.x[i][j].value/=scaling_factors.get(prob.x[i][j].index);					
//		double[] scaling =new double[max_index+1];
//		for(int i=0;i<=max_index;i++){
//			if(scaling_factors.containsKey(i)&&scaling_factors.get(i)>0)
//				scaling[i]=scaling_factors.get(i);
//			else
//				scaling[i]=1.0;
//		}
//		return scaling;
//	}
//
//	public static svm_model train_and_save(svm_problem prob, svm_parameter param, String model_path) throws IOException {
//		svm_model model = svm.svm_train(prob,param);
//		svm.svm_save_model(model_path,model);
//		return model;
//	}
//	
//	public static double do_cross_validation(svm_problem prob, svm_parameter param) {
//		int i;
//		int total_correct = 0;
//		double total_error = 0;
//		double sumv = 0, sumy = 0, sumvv = 0, sumyy = 0, sumvy = 0;
//		double[] target = new double[prob.l];
//
//		svm.svm_cross_validation(prob,param,nr_cosssvalidation_folds,target);
//		if(param.svm_type == svm_parameter.EPSILON_SVR ||
//		   param.svm_type == svm_parameter.NU_SVR)
//		{
//			for(i=0;i<prob.l;i++)
//			{
//				double y = prob.y[i];
//				double v = target[i];
//				total_error += (v-y)*(v-y);
//				sumv += v;
//				sumy += y;
//				sumvv += v*v;
//				sumyy += y*y;
//				sumvy += v*y;
//			}
//			System.out.print("Cross Validation Mean squared error = "+total_error/prob.l+"\n");
//			System.out.print("Cross Validation Squared correlation coefficient = "+
//				((prob.l*sumvy-sumv*sumy)*(prob.l*sumvy-sumv*sumy))/
//				((prob.l*sumvv-sumv*sumv)*(prob.l*sumyy-sumy*sumy))+"\n"
//				);
//			return total_error/prob.l;
//		}
//		else
//		{
//			for(i=0;i<prob.l;i++)
//				if(target[i] == prob.y[i])
//					++total_correct;
//			System.out.print("Cross Validation Accuracy = "+100.0*total_correct/prob.l+"%\n");
//			return 100.0*total_correct/prob.l;
//		}
//	}
//	
//	public static double getOptimalCost(svm_problem problem) throws Exception {
//		double[] costs = null;
//		if(ParametersAndGlobalVariables.SVM_KERNEL==svm_parameter.RBF)
//			costs=new double[]{1.0, 5.0, 10.0, 20.0, 50.0, 100.0, 150.0, 200.0, 250.0, 300.0, 350.0, 400.0, 450.0, 500.0, 550.0, 600.0, 650.0, 700.0, 750.0, 800.0};
//		else {
//			if(ParametersAndGlobalVariables.SVM_KERNEL==svm_parameter.LINEAR)
//				costs=new double[]{2.0, 5.0, 10.0, 20.0, 50.0};
//			else
//				throw new Exception("Unknown SVM kernel: "+ParametersAndGlobalVariables.SVM_KERNEL);
//		}
//		double best_cost=costs[0];
//		double best_performance=0.0;
//		for(int i=0;i<costs.length;i++){ 
//			svm_parameter param = getNewParam(costs[i],getAppropriateGamma(problem));
//			double current=do_cross_validation(problem,param);
//			if(current>best_performance){
//				best_performance=current;
//				best_cost=costs[i];
//			}
//		}
//		System.out.println("-------- Best cost = "+best_cost+"  >>>>>>>>> Best perfomance="+best_performance);
//		return best_cost;
//	}
//	
//	public static void save_problem(svm_problem prob, String outfile){
//		OutFile out = new OutFile(outfile);
//		for(int i=0;i<prob.x.length;i++){
//			out.print(prob.y[i]+"\t");
//			for(int j=0;j<prob.x[i].length;j++)
//				out.print("\t"+prob.x[i][j].index+":"+prob.x[i][j].value);
//			out.println("");
//		}
//		out.close();
//	}
//	
//	public static svm_problem read_problem(String input_file_name) throws IOException
//	{
//		svm_problem prob= new svm_problem();
//		BufferedReader fp = new BufferedReader(new FileReader(input_file_name));
//		List<Double> vy = new ArrayList<Double>();
//		List<svm_node[]> vx = new ArrayList<svm_node[]>();
//
//		while(true)
//		{
//			String line = fp.readLine();
//			if(line == null) break;
//
//			StringTokenizer st = new StringTokenizer(line," \t\n\r\f:");
//
//			double y= atof(st.nextToken());
//			int m = st.countTokens()/2;
//			svm_node[] x = new svm_node[m];
//			for(int j=0;j<m;j++)
//			{
//				x[j] = new svm_node();
//				x[j].index = atoi(st.nextToken());
//				x[j].value = atof(st.nextToken());
//			}
//			vx.add(x);
//			vy.add(y);
//		}
//
//		prob = new svm_problem();
//		prob.l = vy.size();
//		prob.x = new svm_node[prob.l][];
//		for(int i=0;i<prob.l;i++)
//			prob.x[i] = vx.get(i);
//		prob.y = new double[prob.l];
//		for(int i=0;i<prob.l;i++)
//			prob.y[i] = vy.get(i);
//
//
//		if(scale_problems  )
//			scale(prob);
//		fp.close();
//		return prob;
//	}
//	
//	public static double getAppropriateGamma(svm_problem prob){
//		int max_index = 0;
//		for(int i=0;i<prob.x.length;i++)
//			for(int j=0;j<prob.x[i].length;j++)
//				if(max_index<prob.x[i][j].index)
//					max_index = prob.x[i][j].index; 
//		if(max_index>0)
//			return 1.0/max_index;
//		else
//			return 0;
//	
//	}
//
//	private static double atof(String s)
//	{
//		double d = Double.valueOf(s).doubleValue();
//		if (Double.isNaN(d) || Double.isInfinite(d))
//		{
//			System.err.print("NaN or Infinity in input\n");
//			System.exit(1);
//		}
//		return(d);
//	}
//
//	private static int atoi(String s)
//	{
//		return Integer.parseInt(s);
//	}
//
//	public static void saveScalingParams(double[] scaling,String pathToScalingData) {
//		String line="";
//		for(int i=0;i<scaling.length;i++)
//			line+=scaling[i]+"\t";
//		OutFile out=new OutFile(pathToScalingData);
//		out.println(line);
//		out.close();
//	}
//	public static List<Double> loadScalingParams(String pathToScalingData) {
//		InFile in=new InFile(pathToScalingData);
//		String line=in.readLine();
//		in.close();
//		StringTokenizer st = new StringTokenizer(line,"\n \t");
//		List<Double> v=new ArrayList<Double>();
//		while(st.hasMoreTokens())
//			v.add(Double.parseDouble(st.nextToken()));
//		return v;
//	}
//	
//	
//	public static void main(String argv[]) throws Exception {
//		/*svm_problem problem = read_problem("./WikiData/Models/linkerSvm.training.data.txt");
//		double best_cost = getOptimalCost(problem);
//		double bestGamma = getAppropriateGamma( problem);
//		svm_parameter param = getNewParam(best_cost, bestGamma);
//		System.out.println(" ????????????????  What's the actual 5-fold crossvalidation for that cost ?????????????????");
//		System.out.println("---------------------------------------------------------------->"+do_cross_validation(problem, param));
//		train_and_save(problem,param, "./WikiData/Models/linker.svm.model");*/
//		svm_problem problem = read_problem("./WikiData/Models/CoherenceRanker.training.data.txt");
//		double best_cost = getOptimalCost(problem);
//		double bestGamma = getAppropriateGamma(problem);
//		svm_parameter param = getNewParam(best_cost, bestGamma);
//		System.out.println(" ????????????????  What's the actual 5-fold crossvalidation for that cost ?????????????????");
//		System.out.println("-------------------------------------------------------------->"+do_cross_validation(problem, param));
//		svm_model model = train_and_save(problem,param, "./WikiData/Models/ranker.svm.model");
//		double[] test_feats = new double[]{-0.0035471476366600143,	0.0,	0.0,	-0.9522883083194368,	-0.9962201977127351,	3.0,	0.0,	0.0,	0.0,	0.0,	-0.5466465750879747,	0.0,	0.0,	0.0,	0.0,	0.0	, 0.0	, 0.0	, -0.017070436452921382	, 0.012523874791555152	, -0.031100182723529834	, -0.06702917107272459	, 0.0	,0.0	,0.025732839511422127	, 0.014929377735225506	,0.01201068346450321	,0.38519245012721076	,0.0	,0.0	,0.0	,0.0};
//		System.out.println("prediction -- "+ make_prediction(test_feats, model));
//		System.out.println("conf -- "+ get_prediction_confidence(test_feats, model));
//		System.out.println("Weighted decision score: " + get_weighted_value_of_binary_classification(test_feats, model));
//	}

}
