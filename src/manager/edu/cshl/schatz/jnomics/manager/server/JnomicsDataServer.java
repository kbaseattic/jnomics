package edu.cshl.schatz.jnomics.manager.server;

import edu.cshl.schatz.jnomics.manager.api.JnomicsData;
import edu.cshl.schatz.jnomics.manager.common.JnomicsApiConfig;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TServerTransport;
import org.apache.thrift.transport.TTransportException;

import java.io.IOException;
import java.util.Properties;

/**
 * User: james
 */
public class JnomicsDataServer {


    private static int DEFAULTPORT = 43345;

    public static void main(String []args) throws TTransportException, IOException, InterruptedException {

        Properties prop = JnomicsApiConfig.getServerProperties();

        int port = Integer.parseInt(prop.getProperty("data-server-port",Integer.toString(DEFAULTPORT)));

        JnomicsDataHandler handler = new JnomicsDataHandler(prop);
        Thread garbageCollectorThread = new Thread(new JnomicsHandleGarbageCollector(handler));
        JnomicsData.Processor processor = new JnomicsData.Processor(handler);

        TServerTransport serverTransport = new TServerSocket(port);
        TServer server = new TThreadPoolServer(new TThreadPoolServer.Args(serverTransport).processor(processor));

        System.out.println("Starting server port "+ port +"...");
        garbageCollectorThread.start();
        server.serve();
        garbageCollectorThread.interrupt();
    }
}