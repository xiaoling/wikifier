package edu.illinois.cs.cogcomp.wikifier.wiki.importing;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;

/**
 * Reads dbpedia page link format (ttl) to a list of ID pairs
 * (A,B) which indicates a link relation between A->B
 * @author cheng88
 *
 */
public class WikiPageLinkReader {

    
    private InputStream is;
    
    public WikiPageLinkReader(String path) throws IOException{
        is = new BufferedInputStream(new FileInputStream(path));
    }
        
    public void parseTo(String outputPath){
        Scanner scanner = new Scanner(is);
        int lineCount = 0;
        while(scanner.hasNext()){
            String line = scanner.nextLine();
            lineCount++;
            if(lineCount%10000==0)
                System.out.println(lineCount);
        }
        scanner.close();
        System.out.println("Total of "+lineCount + " lines");
    }
    
    
    /**
     * @param args
     * @throws FileNotFoundException 
     */
    public static void main(String[] args) throws Exception {
        WikiPageLinkReader reader = new WikiPageLinkReader("../../dbpedia/page_links_en.ttl");
        reader.parseTo("cache/pageLinks.txt");
    }

}
