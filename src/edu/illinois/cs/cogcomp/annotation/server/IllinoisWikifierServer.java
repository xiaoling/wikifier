package edu.illinois.cs.cogcomp.annotation.server;

import org.apache.commons.cli.Options;

import edu.illinois.cs.cogcomp.annotation.handler.IllinoisWikifierHandler;
import edu.illinois.cs.cogcomp.thrift.labeler.Labeler.Iface;
import edu.illinois.cs.cogcomp.thrift.labeler.Labeler.Processor;
import edu.illinois.cs.cogcomp.wikifier.common.GlobalParameters;

public class IllinoisWikifierServer extends IllinoisAbstractServer {

    public <T> IllinoisWikifierServer(Class<T> c) {
        super(c);
    }

    public <T> IllinoisWikifierServer(Class<T> c, int threads, int port, String configFile) {
        super(c, threads, port, configFile);
    }

    /*
     * Takes the config file as the last parameter!!!
     */
    public static void main(String[] args) throws Exception {

        Options options = createOptions();

        IllinoisWikifierServer s = new IllinoisWikifierServer(IllinoisWikifierServer.class, 2, 9173, "");
        s.parseCommandLine(options, args, "9173", "2", "configs/DEMO.xml");

        GlobalParameters.settings.curatorURL = s.line.getOptionValue("CuratorMachine", "localhost");
        GlobalParameters.settings.curatorPort = Integer.parseInt(s.line.getOptionValue("CuratorPort", "9999"));

        System.out.println("Starting Illinois Wikifier...");
        System.out.println("****Config file = " + s.configFile);
        s.runServer(new Processor<Iface>(new IllinoisWikifierHandler(s.configFile)));
        
    }
}