package org.broadinstitute.ddp.cache;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Consumer;
import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.configuration.CacheEntryListenerConfiguration;
import javax.cache.configuration.Configuration;
import javax.cache.integration.CompletionListener;
import javax.cache.processor.EntryProcessor;
import javax.cache.processor.EntryProcessorException;
import javax.cache.processor.EntryProcessorResult;

public class NullCache<K, V> implements Cache<K, V> {
    private String name;
    private CacheManager cacheManager;
    private Configuration<K, V> configuration;
    private boolean isClosed = false;

    public <C extends Configuration<K, V>> NullCache(String cacheName, C configuration) {
        this.name = cacheName;
        this.configuration = configuration;

    }

    @Override
    public V get(K key) {
        return null;
    }

    @Override
    public Map<K, V> getAll(Set<? extends K> keys) {
        return null;
    }

    @Override
    public boolean containsKey(K key) {
        return false;
    }

    @Override
    public void loadAll(Set<? extends K> keys, boolean replaceExistingValues, CompletionListener completionListener) {

    }

    @Override
    public void put(K key, V value) {

    }

    @Override
    public V getAndPut(K key, V value) {
        return value;
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> map) {

    }

    @Override
    public boolean putIfAbsent(K key, V value) {
        return false;
    }

    @Override
    public boolean remove(K key) {
        return true;
    }

    @Override
    public boolean remove(K key, V oldValue) {
        return true;
    }

    @Override
    public V getAndRemove(K key) {
        return null;
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        return true;
    }

    @Override
    public boolean replace(K key, V value) {
        return true;
    }

    @Override
    public V getAndReplace(K key, V value) {
        return value;
    }

    @Override
    public void removeAll(Set<? extends K> keys) {

    }

    @Override
    public void removeAll() {

    }

    @Override
    public void clear() {

    }

    @Override
    public <C extends Configuration<K, V>> C getConfiguration(Class<C> clazz) {
        return (C) this.configuration;
    }

    @Override
    public <T> T invoke(K key, EntryProcessor<K, V, T> entryProcessor, Object... arguments) throws EntryProcessorException {
        return null;
    }

    @Override
    public <T> Map<K, EntryProcessorResult<T>> invokeAll(Set<? extends K> keys, EntryProcessor<K, V, T> entryProcessor,
                                                         Object... arguments) {
        return null;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public CacheManager getCacheManager() {
        return cacheManager;
    }

    @Override
    public void close() {
        isClosed = true;
    }

    @Override
    public boolean isClosed() {
        return isClosed;
    }

    @Override
    public <T> T unwrap(Class<T> clazz) {
        return null;
    }

    @Override
    public void registerCacheEntryListener(CacheEntryListenerConfiguration<K, V> cacheEntryListenerConfiguration) {

    }

    @Override
    public void deregisterCacheEntryListener(CacheEntryListenerConfiguration<K, V> cacheEntryListenerConfiguration) {

    }

    @Override
    public Iterator<Entry<K, V>> iterator() {
        return Collections.emptyIterator();
    }

    @Override
    public void forEach(Consumer<? super Entry<K, V>> action) {

    }

    @Override
    public Spliterator<Entry<K, V>> spliterator() {
        return null;
    }
}
