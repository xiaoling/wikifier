package edu.illinois.cs.cogcomp.wikifier.utils;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.apache.commons.lang3.StringUtils;

public class ErrorAnalysis {

    /**
     * @param args
     * @throws IOException 
     */
    public static void main(String[] args) throws IOException {
        String logPath = "./ResultsLogs/";
        for(File log:new File(logPath).listFiles(filter)){
            
            LineIterator iterator = FileUtils.lineIterator(log);
            boolean rightAfterError = false;
            int errorCounter = 0;
            int errorHighEntropy = 0;
            int entropyCount = 0;
            int noCandidateError = 0;
            int singleTokenForceLinkError = 0;
            while(iterator.hasNext()){
                String line = iterator.nextLine();
                if(line.startsWith("Final System Output:Correct Wikification of:")){
                    
                    rightAfterError = false;
                }
                
                if(line.startsWith("Final System Output:: Still Incorrect Wikification of:")){
                    errorCounter ++;
                    rightAfterError = true;
                    String errorToken = StringUtils.substringBetween(line, "Still Incorrect Wikification of: ", ";");
                    if(!errorToken.contains(" "))
                        singleTokenForceLinkError++;
                }
                
                if(line.startsWith("Candidates Entropy: 1")){
                    if(rightAfterError){
                        errorHighEntropy++;
                    }
                    entropyCount++;
                }
                
                if(line.equals("Candidates Entropy: 0.0")){
                    if(rightAfterError){
                        noCandidateError++;
                    }
                }

            }
            System.out.printf("%d errors for %s with %d entropy>1 \n%d error with >1 entropy\n" +
            		"%d error has no candidate\n%d singleTokenForceLinkError\n\n",
                    errorCounter,
                    log.getName(),
                    entropyCount,
                    errorHighEntropy,
                    noCandidateError,
                    singleTokenForceLinkError
                    );
        }

    }
    
    private static final FilenameFilter filter = new FilenameFilter() {

        @Override
        public boolean accept(File dir, String name) {
            return !name.startsWith("CandidatesGeneration") && name.endsWith("XiaoConfig");
        }
    };

}
