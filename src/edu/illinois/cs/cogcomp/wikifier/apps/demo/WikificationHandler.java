package edu.illinois.cs.cogcomp.wikifier.apps.demo;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;


import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import edu.illinois.cs.cogcomp.edison.sentences.TextAnnotation;
import edu.illinois.cs.cogcomp.wikifier.common.GlobalParameters;
import edu.illinois.cs.cogcomp.wikifier.inference.InferenceEngine;
import edu.illinois.cs.cogcomp.wikifier.models.LinkingProblem;

@SuppressWarnings("restriction")
public class WikificationHandler implements HttpHandler {

    InferenceEngine inference;
    private static final String warmUp = "Houston, Monday, July 21 -- Men have landed and walked on the moon. " +
    		"Two Americans, astronauts of Apollo 11, steered their fragile four-legged lunar module safely and " +
    		"smoothly to the historic landing yesterday at 4:17:40 P.M., Eastern daylight time. Neil A. Armstrong," +
    		" the 38-year-old civilian commander, radioed to earth and the mission control room here: \"Houston, " +
    		"Tranquility Base here; the Eagle has landed.\"\n"
            + "\n"
            + "The first men to reach the moon -- Mr. Armstrong and his co-pilot, Col. Edwin E. Aldrin, Jr. of the " +
            "Air Force -- brought their ship to rest on a level, rock-strewn plain near the southwestern shore of the " +
            "arid Sea of Tranquility. About six and a half hours later, Mr. Armstrong opened the landing craft's hatch, " +
            "stepped slowly down the ladder and declared as he planted the first human footprint on the lunar crust: \"" +
            "That's one small step for man, one giant leap for mankind.\"";

    public WikificationHandler(InferenceEngine engine) throws Exception {
        inference = engine;
        annotate(warmUp);
    }

    private String annotate(String text) throws Exception {
        TextAnnotation ta = GlobalParameters.curator.getTextAnnotation(text);

        LinkingProblem problem = new LinkingProblem("FileName", ta, null);
        System.out.println("Done constructing the problem; running the inference");
        inference.annotate(problem, null, false, false, 0);

        String resp = problem.wikificationString(false);
        return resp;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {

            @SuppressWarnings("unchecked")
            Map<String, Object> params = (Map<String, Object>) exchange.getAttribute("parameters");
            String text = "" + params.get("text");

           

            byte[] responseBytes;
            if (text == null || text.length() < 5) {
                responseBytes = "Text too short!".getBytes();
                exchange.sendResponseHeaders(200, responseBytes.length);
            } else {
                
                responseBytes = annotate(text).getBytes();
                exchange.sendResponseHeaders(200, responseBytes.length);
            }

            OutputStream os = exchange.getResponseBody();
            os.write(responseBytes);
            os.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

}
