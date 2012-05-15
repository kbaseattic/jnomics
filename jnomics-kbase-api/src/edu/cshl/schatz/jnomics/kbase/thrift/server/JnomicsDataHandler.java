package edu.cshl.schatz.jnomics.kbase.thrift.server;

import edu.cshl.schatz.jnomics.kbase.thrift.api.*;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.*;
import org.apache.thrift.TException;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.*;

/**
 * User: james
 * Modeled after HadoopThriftfs
 */

public class JnomicsDataHandler implements JnomicsData.Iface {

    private final org.slf4j.Logger log = LoggerFactory.getLogger(JnomicsComputeHandler.class);
    private final Map<UUID, JnomicsFsHandle> handleMap = Collections.synchronizedMap(new HashMap<UUID, JnomicsFsHandle>());
    private Properties properties;

    private final ThreadLocal bufferCache = new ThreadLocal(){
        @Override
        protected Object initialValue() {
            return new byte[2000000];
        }
    };

    public JnomicsDataHandler(Properties props){
        properties = props;
    }

    private UUID getUniqueUUID(){
        UUID b;
        while(handleMap.containsKey(b = UUID.randomUUID())){}
        return b;
    }

    private FileSystem getFileSystem(String username) throws JnomicsDataException {
        URI uri;
        String fsName = properties.getProperty("hdfs-default-name");
        try{
            uri = new URI(fsName);
        }catch(Exception e){
            log.error("probleming initializing hdfs");
            e.printStackTrace();
            throw new JnomicsDataException(e.toString());
        }
        FileSystem fs = null;
        try{
            fs = FileSystem.get(uri, new Configuration(), username);
        } catch(Exception e){
            log.error("Problem creating filesystem");
            e.printStackTrace();
            throw new JnomicsDataException(e.toString());
        }

        return fs;
    }
    
    private void closeFileSystem(FileSystem fs) throws JnomicsDataException{
        try{
            fs.close();
        }catch(Exception e){
            log.error("Problem closing fs");
            e.printStackTrace();
            throw new JnomicsDataException(e.toString());
        }
    }
    
    
    @Override
    public JnomicsThriftHandle create(String path, Authentication auth) throws TException, JnomicsDataException {
        log.info("Creating file: " + path + " for user: "+ auth.getUsername());

        FileSystem fs = getFileSystem(auth.getUsername());

        FSDataOutputStream stream = null;
        try {
            stream = fs.create(new Path(path));
        } catch (IOException e) {
            log.error("Problem creating file " + path);
            e.printStackTrace();
            throw new JnomicsDataException(e.toString());
        }
        
        UUID nxtUUID = getUniqueUUID();
        handleMap.put(nxtUUID,new JnomicsFsHandle(fs,stream));
        
        return new JnomicsThriftHandle(nxtUUID.toString());
    }

    @Override
    public JnomicsThriftHandle open(String path, Authentication auth) throws JnomicsDataException, TException {
        log.info("Opening file: " + path + " for user: " + auth.getUsername());
        
        FileSystem fs = getFileSystem(auth.getUsername());
        FSDataInputStream stream = null;
        try{
            stream = fs.open(new Path(path));
        }catch(Exception e){
            log.error("Problem opening file: " + path);
            e.printStackTrace();
            throw new JnomicsDataException(e.toString());
        }

        UUID nxtUUID = getUniqueUUID();
        handleMap.put(nxtUUID,new JnomicsFsHandle(fs,stream));
        return new JnomicsThriftHandle(nxtUUID.toString());
    }


    @Override
    public void write(JnomicsThriftHandle handle, ByteBuffer data, Authentication auth) throws TException, JnomicsDataException {
        JnomicsFsHandle jhandle = handleMap.get(UUID.fromString(handle.getUuid()));
        try {
            jhandle.getOutStream().write(data.array());
        } catch (IOException e) {
            log.error("Problem writing to file");
            e.printStackTrace();
            throw new JnomicsDataException(e.toString());
        }
        jhandle.updateLastUsed();
    }

    @Override
    public ByteBuffer read(JnomicsThriftHandle handle, Authentication auth) throws TException, JnomicsDataException {
        JnomicsFsHandle jhandle = handleMap.get(UUID.fromString(handle.getUuid()));

        byte[] buf = (byte[]) bufferCache.get();
        int bytesRead;
        try{
            bytesRead = jhandle.getInStream().read(buf);
        } catch (IOException e) {
            throw new JnomicsDataException(e.toString());
        }
        jhandle.updateLastUsed();
        if(-1 == bytesRead)
            return ByteBuffer.allocate(0);
        return ByteBuffer.wrap(buf,0,bytesRead);
    }

    @Override
    public void close(JnomicsThriftHandle handle, Authentication auth) throws TException, JnomicsDataException {
        UUID u = UUID.fromString(handle.getUuid());
        JnomicsFsHandle jhandle = handleMap.get(u);


        if(jhandle.getOutStream() != null){
            try {
                jhandle.getOutStream().close();
            } catch (IOException e) {
                log.error("Problem closing file");
                e.printStackTrace();
                throw new JnomicsDataException(e.toString());
            }
        }else if(jhandle.getInStream() != null){
            try {
                jhandle.getInStream().close();
            } catch (IOException e) {
                log.error("Problem closing file");
                e.printStackTrace();
                throw new JnomicsDataException(e.toString());
            }
        }

        try {
            closeFileSystem(jhandle.getFileSystem());
        }finally{
            handleMap.remove(u);
        }
    }

    @Override
    public List<JnomicsFileStatus> listStatus(String path, Authentication auth) throws TException, JnomicsDataException {
        FileSystem fs = getFileSystem(auth.getUsername());
        FileStatus[] stats;
        try{
            stats = fs.listStatus(new Path(path));
        }catch(Exception e){
            log.error("Could not open filesystem in listStatus");
            e.printStackTrace();
            throw new JnomicsDataException(e.toString());
        }finally{
            closeFileSystem(fs);
        }

        if(null == stats)
            return new ArrayList<JnomicsFileStatus>();

        JnomicsFileStatus[] thriftStatuses = new JnomicsFileStatus[stats.length];
        FileStatus c;
        for(int i=0; i< stats.length; ++i){
            c = stats[i];
            thriftStatuses[i] = new JnomicsFileStatus(c.isDir(),
                    c.getPath().getName(),
                    c.getOwner(),
                    c.getGroup(),
                    c.getPermission().toString(),
                    c.getReplication(),
                    c.getModificationTime(),
                    c.getBlockSize(),
                    c.getLen()
            );
        }
        
        return Arrays.asList(thriftStatuses);
    }

    @Override
    public boolean remove(String path, boolean recursive, Authentication auth) throws JnomicsDataException, TException {
        FileSystem fs = getFileSystem(auth.getUsername());
        boolean state = false;
        try{
            state = fs.delete(new Path(path), recursive);
        }catch(Exception e){
            log.error("Problem deleting " + path + " for user: " + auth.getUsername());
            e.printStackTrace();
            throw new JnomicsDataException(e.toString());
        }finally{
            closeFileSystem(fs);
        }
        return state;
    }

}