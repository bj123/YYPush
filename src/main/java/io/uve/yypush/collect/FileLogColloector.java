package io.uve.yypush.collect;

import io.uve.yypush.model.FileNode;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;

import org.apache.log4j.Logger;
import org.joda.time.DateTime;

/**
 * 
 * @author yangyang21@staff.weibo.com(yangyang)
 * 
 */
public class FileLogColloector extends Collector{
	private static Logger log = Logger.getLogger(FileLogColloector.class);
	private FileNode node;
	private AsynchronousFileChannel channel;

	public boolean load(boolean first) throws IOException {
	 	Path startingDir = Paths.get(config.basePath + "/" + config.suffix);
	 	boolean exist = false;
	 	while(!exist){
		 	exist = Files.exists(startingDir, LinkOption.NOFOLLOW_LINKS);
	 		if(!exist){
	 			log.info("dir not exist(sleeping):" + startingDir.toAbsolutePath());
		 		try {
					Thread.sleep(2000L);
				} catch (InterruptedException e) {
					log.info("recv interrunpt");
					return false;
				}
	 		}
	 	}
	 	
		this.channel = AsynchronousFileChannel.open(startingDir, StandardOpenOption.READ);
		this.node = new FileNode();
		
		node.setFileName(startingDir.toAbsolutePath().toString());
		node.setBf(ByteBuffer.allocate(1000000));
		node.getBf().clear();
		node.setCnt(null);
		node.setOffset(0);
		
		if(first && config.useFileCurrent){
			long size = this.channel.size();
			if(size > 1000){
				size = size - 1000;
			}
			node.setOffset(size);
		}
		
		node.setCurTime(System.currentTimeMillis());		
		log.info("load file successs:" + startingDir.toAbsolutePath()  + " with offset:" + node.getOffset());
		log.info("init successs!");
		return true;
	}
	
	public void process() throws IOException, ExecutionException, TimeoutException {
		while(true){
			int count = 0;
			while (true) {
				if(Thread.interrupted()){
					log.info("thread " + this.config.name + "has stop by main interupt");
					return;
				}

				Future<Integer> f = channel.read(node.getBf(), node.getOffset());
				node.setCnt(f);

				try {
					Integer cnt = 0;
					cnt = node.getCnt().get();
					if (cnt > 0) {
						handle(node);
						if(node.getBf().remaining() == 0){
							Thread.sleep(2000L);
						}
					} else if (!node.isHasReadEOF()) {
						if (config.fileType.check(node.getCurTime(), config)) {
							count = count + 1;
							node.setHasReadEOF(true);
						}
					}
				} catch (InterruptedException e) {
					log.info("recv interrunpt");
					Thread.currentThread().interrupt();
				}			
				
				if(count == 1){
					channel.close();
					log.info("close file: " + node.getFileName());
					break;
				}
			}
			
			if(!loadNext()){
				log.info("load next dir error!");
				break;
			}
		}
		
		log.info("exit the thread!");
	}
	
	private boolean loadNext() {
		try {
			load(false);
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	@Override
	public boolean load(DateTime dateTime, boolean first) throws IOException {
		return load(first);
	}
}
