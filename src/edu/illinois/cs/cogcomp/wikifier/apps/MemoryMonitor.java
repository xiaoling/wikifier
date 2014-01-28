package edu.illinois.cs.cogcomp.wikifier.apps;

public class MemoryMonitor {
    
	public static void printMemoryUsage(String announcement) throws Exception{
		Runtime r=Runtime.getRuntime();
		runGC(r,10);
		System.out.printf(announcement+'\n',usedMemory());
	}
	
	public static long usedMemory(){
	    Runtime r = Runtime.getRuntime();
	    return r.totalMemory() - r.freeMemory();
	}
	
	private static void runGC(Runtime r, int loops) throws Exception {
		System.out.println("running garbage collecting");
		for(int i=0; i<loops; i++) {
			r.gc();
			Thread.sleep(1000);
		}
		System.out.println("done running garbage collecting");
	}
}
