package edu.illinois.cs.cogcomp.wikifier.apps.demo;

import java.net.InetSocketAddress;

import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpServer;

import edu.illinois.cs.cogcomp.wikifier.common.GlobalParameters;
import edu.illinois.cs.cogcomp.wikifier.common.GlobalParameters.SettingManager;
import edu.illinois.cs.cogcomp.wikifier.common.ParameterPresets;
import edu.illinois.cs.cogcomp.wikifier.common.SystemSettings.WikiAccessType;
import edu.illinois.cs.cogcomp.wikifier.inference.InferenceEngine;


/**
 * A simple HTML server
 * @author cheng88
 *
 */
public class Server{

    @SuppressWarnings("restriction")
    public static void main(String[] args) throws Exception{


        ParameterPresets.DEMO.apply();
        // It will block here
        SettingManager manager = new SettingManager();
        manager.settings.wikiAccessProvider = WikiAccessType.MongoDB;
        manager.paths.curatorCache = null;
        manager.paths.compressedRedirects = null;
        GlobalParameters.loadSettings(manager);
        ParameterPresets.DEMO.apply();

        InferenceEngine engine = new InferenceEngine(false);
        HttpServer server = HttpServer.create(new InetSocketAddress(8800), 0);
        HttpContext wikifyContext = server.createContext("/wikify", new WikificationHandler(engine));
        wikifyContext.getFilters().add(new ParameterFilter());
        server.start();
    }

}
