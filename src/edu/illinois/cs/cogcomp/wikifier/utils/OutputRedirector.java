package edu.illinois.cs.cogcomp.wikifier.utils;

import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;

public class OutputRedirector {

    private static PrintStream originalOut = null;
    private static PrintStream pipe = null;

    public static synchronized void pipeTo(String path) throws FileNotFoundException {
        if (originalOut != null)
            return;
        originalOut = System.out;
        pipe = new PrintStream(new BufferedOutputStream(new FileOutputStream(path)));
        System.setOut(pipe);
    }

    public static synchronized void reset() {
        if (originalOut != null){
            pipe.close();
            System.setOut(originalOut);
        }
    }

}
