package edu.cshl.schatz.jnomics.manager.client;

import edu.cshl.schatz.jnomics.manager.api.Authentication;
import edu.cshl.schatz.jnomics.manager.api.JnomicsCompute;
import edu.cshl.schatz.jnomics.manager.api.JnomicsData;
import edu.cshl.schatz.jnomics.manager.common.JnomicsApiConfig;
import org.apache.thrift.async.TAsyncClientManager;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Properties;

/**
 * User: james
 */
public class JnomicsThriftClient {

    public static Authentication getAuthentication() throws IOException {
        Properties properties = JnomicsApiConfig.getClientProperties();
        String username = properties.getProperty("username");
        String password = properties.getProperty("password");
        return new Authentication(username,password);
    }
    
    public static JnomicsData.Client getFsClient() throws IOException, TTransportException {

        Properties properties = JnomicsApiConfig.getClientProperties();
        String thriftDataHost = properties.getProperty("data-server-host");
        int thriftDataPort = Integer.parseInt(properties.getProperty("data-server-port"));
        TTransport transport = new TSocket(thriftDataHost, thriftDataPort);
        transport.open();
        TProtocol protocol = new TBinaryProtocol(transport);
        JnomicsData.Client client = new JnomicsData.Client(protocol);

        return client;
    }


    public static JnomicsCompute.Client getComputeClient() throws IOException, TTransportException{
        Properties properties = JnomicsApiConfig.getClientProperties();

        String thriftComputeHost = properties.getProperty("compute-server-host");
        int thriftComputePort = Integer.parseInt(properties.getProperty("compute-server-port"));
        TTransport thriftTransport = new TSocket(thriftComputeHost, thriftComputePort);
        thriftTransport.open();
        TProtocol protocol = new TBinaryProtocol(thriftTransport);
        JnomicsCompute.Client client = new JnomicsCompute.Client(protocol);

        return client;
    }
    

    public static JnomicsCompute.AsyncClient getAsyncComputeClient() throws IOException, TTransportException{
        Properties properties = JnomicsApiConfig.getClientProperties();

        String thriftComputeHost = properties.getProperty("compute-server-host");
        int thriftComputePort = Integer.parseInt(properties.getProperty("compute-server-port"));
        JnomicsCompute.AsyncClient client = new JnomicsCompute.AsyncClient(new TBinaryProtocol.Factory(),
                new TAsyncClientManager(), new TNonblockingSocket(thriftComputeHost,thriftComputePort));

        return client;
    }
    
}
