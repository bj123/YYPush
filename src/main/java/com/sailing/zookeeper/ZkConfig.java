package com.sailing.zookeeper;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.Watcher.Event.EventType;
import org.apache.zookeeper.Watcher.Event.KeeperState;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.sailing.Sailing;
import com.sailing.config.Config;
import com.sailing.model.ChangeNode;


public class ZkConfig implements Watcher {
	private static Logger log = Logger.getLogger(ZkConfig.class);
	public final static String zkBase = "/logpush";
	public ZooKeeper zk = null;
	public Stat stat = null;
	public Sailing sail;
	
	public ZkConfig(Sailing sail) {
		this.sail = sail;
	}

	@Override
	public void process(WatchedEvent event) {
		if(KeeperState.SyncConnected == event.getState()){
			if(EventType.None == event.getType() && null == event.getPath()){
				
			}else if (event.getType() == EventType.NodeChildrenChanged) {
				try {
					sail.lock.lock();
					List<String> ss = zk.getChildren(event.getPath(), true);
					if(ss == null){
						ss = Lists.newArrayList();
					}
					Map<String, String> map = Maps.newHashMap();
					log.info("reget config name:" + ss);
					for (String s : ss) {
						String son = event.getPath() + "/" + s;
						String configStr = new String(zk.getData(son, true, stat));
						log.info(son + " : " + configStr);
						map.put(son, configStr);
					}
					ChangeNode node = new ChangeNode();
					node.setChilds(ss);
					node.setUseMap(false);
					node.setMap(map);
					
					sail.changeStatus.add(node);
					sail.needReload = true;
				} catch (Exception e) {
					log.info("notify load config error!");
				}finally{
					sail.c.signalAll();
					sail.lock.unlock();
				}
			}else if (event.getType() == EventType.NodeDataChanged) {
				try {
					sail.lock.lock();
					String name = event.getPath();
					String configStr;
					configStr = new String(zk.getData(event.getPath(), true, stat));
					log.info("path data change:" + event.getPath());
					Map<String, String> map = Maps.newHashMap();
					map.put(name, configStr);
					ChangeNode node = new ChangeNode();
					node.setChilds(null);
					node.setUseMap(true);
					node.setMap(map);
					sail.changeStatus.add(node);
					sail.needReload = true;
				} catch (KeeperException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}finally{
					sail.c.signalAll();
					sail.lock.unlock();
				}
			}
		}
	}
	
	public void LoadingConfig() throws JsonParseException, JsonMappingException, IOException {
		ObjectMapper jsonMapper = new ObjectMapper();		
		Map<String, Config> configs = Maps.newHashMap();
	    this.stat = new Stat();
	    this.zk = new ZooKeeper("10.77.96.122:2181", 6000, this);
		List<String> cl = null;
		try {
			cl = zk.getChildren(zkBase, true);
		} catch (KeeperException | InterruptedException e1) {
			e1.printStackTrace();
		}
		if(cl == null || cl.size() == 0){
			log.info("load config from zookeeper null");
		}else {
			for(String k : cl){
				String son = zkBase + '/' + k;
				String confStr = null;;
				try {
					confStr = new String(zk.getData(son, true, stat));
				} catch (KeeperException | InterruptedException e) {
					e.printStackTrace();
				}
				if(confStr != null && !confStr.isEmpty()){
					Config config = jsonMapper.readValue(confStr, Config.class);
					config.name = son;
					configs.put(son, config);
					log.info("load config success: " + k);
				}
			}
		}
		log.info("finished init config");
		sail.configs = configs;
	}
}