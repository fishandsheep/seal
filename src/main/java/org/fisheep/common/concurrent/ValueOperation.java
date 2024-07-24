package org.fisheep.common.concurrent;

@FunctionalInterface
public interface ValueOperation<T>
{
	T execute();
}
