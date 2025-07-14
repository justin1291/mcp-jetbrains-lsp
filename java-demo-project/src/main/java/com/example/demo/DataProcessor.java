package com.example.demo;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Data processing utility class
 */
public class DataProcessor {
    
    public static final int MAX_CACHE_SIZE = 1000;
    public static final int DEFAULT_BATCH_SIZE = 100;
    
    private final Map<String, ProcessedData> cache = new ConcurrentHashMap<>();
    
    /**
     * Processes raw data and returns processed result
     * @param rawData The raw data to process
     * @return Processed data
     */
    public ProcessedData processData(String rawData) {
        // Check cache first
        ProcessedData cached = cache.get(rawData);
        if (cached != null) {
            return cached;
        }
        
        // Process the data
        String processed = rawData.trim().toUpperCase();
        long timestamp = System.currentTimeMillis();
        ProcessedData result = new ProcessedData(rawData, processed, timestamp);
        
        // Cache the result if cache is not full
        if (cache.size() < MAX_CACHE_SIZE) {
            cache.put(rawData, result);
        }
        
        return result;
    }
    
    /**
     * Process multiple data items in batch
     * @param dataItems Items to process
     * @return List of processed data
     */
    public List<ProcessedData> processBatch(List<String> dataItems) {
        return dataItems.stream()
                .map(this::processData)
                .collect(Collectors.toList());
    }
    
    /**
     * Gets current cache statistics
     * @return Cache statistics
     */
    public CacheStats getCacheStats() {
        return new CacheStats(
            cache.size(),
            MAX_CACHE_SIZE,
            (double) cache.size() / MAX_CACHE_SIZE
        );
    }
    
    /**
     * Clears the processing cache
     */
    public void clearCache() {
        cache.clear();
    }
    
    /**
     * Represents processed data
     */
    public static class ProcessedData {
        private final String original;
        private final String processed;
        private final long timestamp;
        
        public ProcessedData(String original, String processed, long timestamp) {
            this.original = original;
            this.processed = processed;
            this.timestamp = timestamp;
        }
        
        public String getOriginal() {
            return original;
        }
        
        public String getProcessed() {
            return processed;
        }
        
        public long getTimestamp() {
            return timestamp;
        }
        
        @Override
        public String toString() {
            return "ProcessedData{" +
                    "original='" + original + '\'' +
                    ", processed='" + processed + '\'' +
                    ", timestamp=" + timestamp +
                    '}';
        }
    }
    
    /**
     * Cache statistics
     */
    public static class CacheStats {
        private final int size;
        private final int maxSize;
        private final double utilizationRate;
        
        public CacheStats(int size, int maxSize, double utilizationRate) {
            this.size = size;
            this.maxSize = maxSize;
            this.utilizationRate = utilizationRate;
        }
        
        public int getSize() {
            return size;
        }
        
        public int getMaxSize() {
            return maxSize;
        }
        
        public double getUtilizationRate() {
            return utilizationRate;
        }
    }
}
