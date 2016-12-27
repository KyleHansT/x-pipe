package com.ctrip.xpipe.redis.meta.server.keeper.keepermaster.impl;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.unidal.tuple.Pair;

import com.ctrip.xpipe.api.server.Server.SERVER_ROLE;
import com.ctrip.xpipe.netty.ByteBufUtils;
import com.ctrip.xpipe.redis.core.entity.RedisMeta;
import com.ctrip.xpipe.redis.core.protocal.MASTER_STATE;
import com.ctrip.xpipe.redis.core.protocal.pojo.SlaveRole;
import com.ctrip.xpipe.simpleserver.Server;

import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * @author wenchao.meng
 *
 * Nov 13, 2016
 */
public class PrimaryDcKeeperMasterChooserAlgorithmTest extends AbstractDcKeeperMasterChooserTest{
	
	private PrimaryDcKeeperMasterChooserAlgorithm primaryAlgorithm;
	
	private List<RedisMeta> redises;
	
	@Before
	public void befoePrimaryDcKeeperMasterChooserTest() throws Exception{
		
		primaryAlgorithm =  new PrimaryDcKeeperMasterChooserAlgorithm(clusterId, shardId, 
				dcMetaCache, currentMetaManager, getXpipeNettyClientKeyedObjectPool(), 1, scheduled);
		redises = new LinkedList<>();
		int port1 = randomPort();
		redises.add(new RedisMeta().setIp("localhost").setPort(port1));
		
		redises.add(new RedisMeta().setIp("localhost").setPort(randomPort(Arrays.asList(port1))));
		when(dcMetaCache.getShardRedises(clusterId, shardId)).thenReturn(redises);
		when(dcMetaCache.isCurrentDcPrimary(clusterId, shardId)).thenReturn(true);
	}
	
	@Test
	public void testNoneMaster(){
		
		Assert.assertNull(primaryAlgorithm.choose());
		
	}

	@Test
	public void testOneMaster() throws Exception{

		SlaveRole role = new SlaveRole(SERVER_ROLE.MASTER, "localhost", randomPort(), MASTER_STATE.REDIS_REPL_CONNECT, 0L);
		RedisMeta chosen = redises.get(0);
		startServer(chosen.getPort(), ByteBufUtils.readToString(role.format()));
		

		when(currentMetaManager.getKeeperMaster(clusterId, shardId)).thenReturn(null);
		Assert.assertEquals(new Pair<String, Integer>(chosen.getIp(), chosen.getPort()), primaryAlgorithm.choose());
		for(RedisMeta redisMeta : redises){
			
			when(currentMetaManager.getKeeperMaster(clusterId, shardId)).thenReturn(new Pair<String, Integer>(redisMeta.getIp(), redisMeta.getPort()));
			Assert.assertEquals(new Pair<String, Integer>(chosen.getIp(), chosen.getPort()), primaryAlgorithm.choose());
		}
	}
	
	@Test
	public void testLongConnection() throws Exception{

		SlaveRole role = new SlaveRole(SERVER_ROLE.MASTER, "localhost", randomPort(), MASTER_STATE.REDIS_REPL_CONNECT, 0L);
		RedisMeta chosen = redises.get(0);
		Server server = startServer(chosen.getPort(), ByteBufUtils.readToString(role.format()));
		
		Assert.assertEquals(0, server.getConnected());
		
		for(int i=0;i<10;i++){
			
			primaryAlgorithm.choose();
			Assert.assertEquals(1, server.getConnected());
		}

	}

	@Test
	public void testMultiMaster() throws Exception{

		SlaveRole role = new SlaveRole(SERVER_ROLE.MASTER, "localhost", randomPort(), MASTER_STATE.REDIS_REPL_CONNECT, 0L);
		
		for(RedisMeta redisMeta : redises){
			startServer(redisMeta.getPort(), ByteBufUtils.readToString(role.format()));
		}
		
		when(currentMetaManager.getKeeperMaster(clusterId, shardId)).thenReturn(null);
		Assert.assertEquals(new Pair<String, Integer>(redises.get(0).getIp(), redises.get(0).getPort()), primaryAlgorithm.choose());
		
		for(RedisMeta redisMeta : redises){
			
			Pair<String, Integer> currentMaster = new Pair<String, Integer>(redisMeta.getIp(), redisMeta.getPort());
			when(currentMetaManager.getKeeperMaster(clusterId, shardId)).thenReturn(currentMaster);
			Assert.assertEquals(currentMaster, primaryAlgorithm.choose());
		}
	}
}