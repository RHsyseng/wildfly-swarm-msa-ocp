package com.redhat.refarch.wildfly.swarm.lambdaair.edge.mapping;

import javax.servlet.http.HttpServletRequest;

public interface Mapper
{
	String getHostAddress(HttpServletRequest request, String hostAddress);

	boolean initialize();
}
