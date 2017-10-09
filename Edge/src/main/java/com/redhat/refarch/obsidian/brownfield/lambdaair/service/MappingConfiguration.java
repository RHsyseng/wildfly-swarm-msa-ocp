package com.redhat.refarch.obsidian.brownfield.lambdaair.service;

import java.util.HashMap;
import java.util.Map;

import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class MappingConfiguration
{
	private Map<String, String> proxyMap;

	public MappingConfiguration()
	{
		proxyMap = new HashMap<>();
		proxyMap.put( "presentation", "http://presentation:8080" );
		proxyMap.put( "airports", "http://airports:8080" );
		proxyMap.put( "flights", "http://flights:8080" );
		proxyMap.put( "sales", "http://sales:8080" );
	}

	public String getHost(String context)
	{
		return proxyMap.get( context );
	}
}