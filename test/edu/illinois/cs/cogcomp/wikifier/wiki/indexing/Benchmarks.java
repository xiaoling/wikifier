package edu.illinois.cs.cogcomp.wikifier.wiki.indexing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;

import edu.illinois.cs.cogcomp.wikifier.utils.Timer;


@SuppressWarnings("rawtypes")
public class Benchmarks {

    /**
     * @param args
     * @throws IllegalAccessException 
     * @throws InstantiationException 
     */
    public static void main(String[] args) 
            throws InstantiationException, IllegalAccessException {
        
        Thread arrayListTest = new TestingThread(ArrayList.class);
        Thread vectorTestingThread = new TestingThread(Vector.class);
        Thread hashSetThread = new TestingThread(HashSet.class);

        vectorTestingThread.start();
        arrayListTest.start();
        hashSetThread.start();
        
        
        Timer timer = new Timer("ArrayList_Prealloc"){
            @Override
            public void run() {
                List<Integer> list = new ArrayList<Integer>(1000000);
                for(int i = 0;i<1000000;i++)
                    list.add(i);
            }
        };
        timer.timedRun();
        
        timer = new Timer("ArrayList"){

            @Override
            public void run() {
                List<Integer> list = new ArrayList<Integer>();
                for(int i = 0;i<1000000;i++)
                    list.add(i);
            }
            
        };
        timer.timedRun();
        
        timer = new Timer("LinkedList") {
            
            @Override
            public void run() {
                List<Integer> list = new LinkedList<Integer>();
                for(int i = 0;i<1000000;i++)
                    list.add(i);
            }
        };
        timer.timedRun();
        

    }
    
    public static class TestingThread extends Thread{
        

        public Class<? extends Collection> testingClass;
        
        public TestingThread(Class<? extends Collection> testingClass){
            this.testingClass = testingClass;
        }
        
        @SuppressWarnings({ "unchecked" })
        public void run(){
            
            long iterations = Integer.MAX_VALUE/400;
            long start = System.currentTimeMillis();
            Collection<Long> testContainer = null;
            try {
                testContainer = testingClass.newInstance();
            } catch (Exception e) {}
            for(long i=0;i<iterations;i++){
                testContainer.add(i);
            }
            System.out.printf("%s performing %d inserts took %d ms\n",
                    testContainer.getClass().getName(),
                    iterations,
                    System.currentTimeMillis()-start);
            
        }
    }

}
