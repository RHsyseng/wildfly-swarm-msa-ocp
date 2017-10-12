package com.redhat.refarch.wildfly.swarm.lambdaair.edge.mapping;

import com.redhat.refarch.wildfly.swarm.lambdaair.edge.mapping.impl.JavaScriptMapper;
import com.redhat.refarch.wildfly.swarm.lambdaair.edge.mapping.impl.PropertyMapper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.servlet.http.HttpServletRequest;

@ApplicationScoped
public class MappingConfiguration
{
	private static Logger logger = Logger.getLogger( MappingConfiguration.class.getName() );
	private List<Mapper> mapperChain = new ArrayList<>();

	public MappingConfiguration()
	{
		Mapper[] candidates = new Mapper[]{PropertyMapper.getInstance(), JavaScriptMapper.getInstance()};
		for( Mapper candidate : candidates )
		{
			if( candidate.initialize() )
			{
				mapperChain.add( candidate );
			}
		}
	}

	public String getHostAddress(HttpServletRequest request)
	{
		if( mapperChain.isEmpty() )
		{
			logger.severe( "No mapper configured, will return null" );
			return null;
		}
		else
		{
			Iterator<Mapper> mapperIterator = mapperChain.iterator();
			String hostAddress = mapperIterator.next().getHostAddress( request, null );
			logger.fine( "Default mapper returned " + hostAddress );
			while( mapperIterator.hasNext() )
			{
				Mapper mapper = mapperIterator.next();
				hostAddress = mapper.getHostAddress( request, hostAddress );
				logger.fine( "Mapper " + mapper + " returned " + hostAddress );
			}
			return hostAddress;
		}
	}
}
