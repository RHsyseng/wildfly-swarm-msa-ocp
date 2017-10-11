package com.redhat.refarch.obsidian.brownfield.lambdaair.mapping;

import javax.servlet.http.HttpServletRequest;

public interface Mapper
{
	String getHostAddress(HttpServletRequest request, String hostAddress);
}
