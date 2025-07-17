"""Data Processor - Python equivalent of DataProcessor.java.

This module demonstrates data processing patterns, caching,
batch operations, and Python-specific features like generators
and context managers.
"""

from typing import List, Dict, Any, Optional, Iterator, TypeVar, Generic, Protocol
from dataclasses import dataclass, field
from datetime import datetime, timedelta
from collections import defaultdict
from functools import lru_cache
import asyncio
from contextlib import contextmanager
from abc import ABC, abstractmethod
import time

# Type variable for generic processing
T = TypeVar('T')

# Constants (like Java static final)
MAX_CACHE_SIZE = 1000
DEFAULT_BATCH_SIZE = 100
CACHE_TTL_SECONDS = 3600  # 1 hour


@dataclass
class ProcessedData:
    """Data class representing processed data (like Java record)."""
    id: str
    original_data: Any
    processed_data: Any
    processing_time: float
    timestamp: datetime = field(default_factory=datetime.now)
    
    @property
    def age_seconds(self) -> float:
        """Get age of processed data in seconds."""
        return (datetime.now() - self.timestamp).total_seconds()
    
    def is_expired(self, ttl_seconds: float = CACHE_TTL_SECONDS) -> bool:
        """Check if data is expired."""
        return self.age_seconds > ttl_seconds


@dataclass
class CacheStats:
    """Statistics about cache usage."""
    hits: int = 0
    misses: int = 0
    evictions: int = 0
    size: int = 0
    
    @property
    def hit_rate(self) -> float:
        """Calculate cache hit rate."""
        total = self.hits + self.misses
        return self.hits / total if total > 0 else 0.0
    
    def __str__(self) -> str:
        """String representation."""
        return (f"CacheStats(hits={self.hits}, misses={self.misses}, "
                f"rate={self.hit_rate:.2%}, size={self.size})")


class DataValidator(Protocol):
    """Protocol for data validation (like Java interface)."""
    
    def validate(self, data: Any) -> bool:
        """Validate data."""
        ...
    
    def get_validation_errors(self, data: Any) -> List[str]:
        """Get validation errors."""
        ...


class BaseProcessor(ABC):
    """Abstract base processor class."""
    
    def __init__(self, name: str):
        self.name = name
        self._processing_count = 0
    
    @abstractmethod
    def process(self, data: Any) -> Any:
        """Abstract processing method."""
        pass
    
    def pre_process(self, data: Any) -> Any:
        """Pre-processing hook."""
        self._processing_count += 1
        return data
    
    def post_process(self, data: Any) -> Any:
        """Post-processing hook."""
        return data


class DataProcessor(BaseProcessor, Generic[T]):
    """Generic data processor with caching and batch operations.
    
    This class demonstrates various Python patterns including:
    - Generic types
    - Context managers
    - Generators
    - Async operations
    - Decorators
    - Properties
    """
    
    def __init__(self, 
                 validator: Optional[DataValidator] = None,
                 cache_size: int = MAX_CACHE_SIZE):
        """Initialize the data processor.
        
        Args:
            validator: Optional data validator
            cache_size: Maximum cache size
        """
        super().__init__("DataProcessor")
        self._cache: Dict[str, ProcessedData] = {}
        self._stats = CacheStats()
        self._validator = validator
        self._max_cache_size = cache_size
        self.__secret_key = "processor_secret"  # Private field
    
    def process(self, data: T) -> ProcessedData:
        """Process single data item.
        
        Args:
            data: Data to process
            
        Returns:
            Processed data result
        """
        data = self.pre_process(data)
        
        # Check cache
        cache_key = self._get_cache_key(data)
        if cache_key in self._cache:
            self._stats.hits += 1
            return self._cache[cache_key]
        
        self._stats.misses += 1
        
        # Validate if validator provided
        if self._validator and not self._validator.validate(data):
            raise ValueError(f"Invalid data: {self._validator.get_validation_errors(data)}")
        
        # Process data
        start_time = time.time()
        processed = self._do_processing(data)
        processing_time = time.time() - start_time
        
        result = ProcessedData(
            id=cache_key,
            original_data=data,
            processed_data=processed,
            processing_time=processing_time
        )
        
        # Cache result
        self._add_to_cache(cache_key, result)
        
        return self.post_process(result)
    
    def process_batch(self, 
                     data_list: List[T], 
                     batch_size: int = DEFAULT_BATCH_SIZE) -> List[ProcessedData]:
        """Process data in batches.
        
        Args:
            data_list: List of data to process
            batch_size: Size of each batch
            
        Returns:
            List of processed results
        """
        results = []
        
        for batch in self._create_batches(data_list, batch_size):
            batch_results = [self.process(item) for item in batch]
            results.extend(batch_results)
        
        return results
    
    async def process_async(self, data: T) -> ProcessedData:
        """Async data processing.
        
        Args:
            data: Data to process
            
        Returns:
            Processed data result
        """
        await asyncio.sleep(0.1)  # Simulate async work
        return self.process(data)
    
    async def process_batch_async(self, 
                                 data_list: List[T],
                                 concurrency: int = 10) -> List[ProcessedData]:
        """Process batch asynchronously with concurrency limit.
        
        Args:
            data_list: List of data to process
            concurrency: Maximum concurrent operations
            
        Returns:
            List of processed results
        """
        semaphore = asyncio.Semaphore(concurrency)
        
        async def process_with_semaphore(data):
            async with semaphore:
                return await self.process_async(data)
        
        tasks = [process_with_semaphore(data) for data in data_list]
        return await asyncio.gather(*tasks)
    
    @property
    def cache_stats(self) -> CacheStats:
        """Get cache statistics (property)."""
        self._stats.size = len(self._cache)
        return self._stats
    
    @property
    def cache_size(self) -> int:
        """Get current cache size."""
        return len(self._cache)
    
    @lru_cache(maxsize=128)
    def get_processing_info(self, data_type: str) -> Dict[str, Any]:
        """Get processing information for data type (cached method).
        
        Args:
            data_type: Type of data
            
        Returns:
            Processing information
        """
        return {
            'type': data_type,
            'processor': self.name,
            'cache_size': self.cache_size,
            'max_cache_size': self._max_cache_size
        }
    
    def clear_cache(self) -> None:
        """Clear the processing cache."""
        self._cache.clear()
        self._stats.evictions += self._stats.size
        self._stats.size = 0
    
    def remove_expired(self, ttl_seconds: float = CACHE_TTL_SECONDS) -> int:
        """Remove expired entries from cache.
        
        Args:
            ttl_seconds: Time to live in seconds
            
        Returns:
            Number of entries removed
        """
        expired_keys = [
            key for key, data in self._cache.items()
            if data.is_expired(ttl_seconds)
        ]
        
        for key in expired_keys:
            del self._cache[key]
            self._stats.evictions += 1
        
        return len(expired_keys)
    
    # Generator methods
    def iter_processed_data(self) -> Iterator[ProcessedData]:
        """Iterate over all processed data (generator)."""
        for data in self._cache.values():
            yield data
    
    def iter_valid_data(self, ttl_seconds: float = CACHE_TTL_SECONDS) -> Iterator[ProcessedData]:
        """Iterate over non-expired data (generator)."""
        for data in self._cache.values():
            if not data.is_expired(ttl_seconds):
                yield data
    
    # Context manager support
    @contextmanager
    def batch_processing(self, batch_name: str):
        """Context manager for batch processing.
        
        Args:
            batch_name: Name of the batch
            
        Yields:
            Self for processing
        """
        print(f"Starting batch: {batch_name}")
        start_time = time.time()
        
        try:
            yield self
        finally:
            elapsed = time.time() - start_time
            print(f"Batch {batch_name} completed in {elapsed:.2f} seconds")
    
    # Protected methods
    def _get_cache_key(self, data: Any) -> str:
        """Generate cache key for data (protected method).
        
        Args:
            data: Data to generate key for
            
        Returns:
            Cache key string
        """
        return f"data_{hash(str(data))}"
    
    def _do_processing(self, data: T) -> Any:
        """Actual processing logic (protected method).
        
        Args:
            data: Data to process
            
        Returns:
            Processed result
        """
        # Simulate processing
        import hashlib
        result = hashlib.md5(str(data).encode()).hexdigest()
        return f"processed_{result}"
    
    def _add_to_cache(self, key: str, data: ProcessedData) -> None:
        """Add data to cache with eviction (protected method).
        
        Args:
            key: Cache key
            data: Data to cache
        """
        # Evict oldest if cache is full
        if len(self._cache) >= self._max_cache_size:
            oldest_key = min(self._cache.keys(), 
                           key=lambda k: self._cache[k].timestamp)
            del self._cache[oldest_key]
            self._stats.evictions += 1
        
        self._cache[key] = data
    
    @staticmethod
    def _create_batches(data_list: List[T], batch_size: int) -> Iterator[List[T]]:
        """Create batches from list (static generator method).
        
        Args:
            data_list: List to batch
            batch_size: Size of each batch
            
        Yields:
            Batches of data
        """
        for i in range(0, len(data_list), batch_size):
            yield data_list[i:i + batch_size]
    
    # Private methods
    def __validate_internal_state(self) -> bool:
        """Validate internal processor state (private method).
        
        Returns:
            True if state is valid
        """
        return (
            self._max_cache_size > 0 and
            len(self._cache) <= self._max_cache_size and
            self._stats.size == len(self._cache)
        )
    
    def __str__(self) -> str:
        """String representation."""
        return f"DataProcessor(cache_size={self.cache_size}, stats={self._stats})"
    
    def __repr__(self) -> str:
        """Developer representation."""
        return (f"DataProcessor(validator={self._validator}, "
                f"cache_size={self._max_cache_size})")


# Specialized processor subclass
class JsonDataProcessor(DataProcessor[dict]):
    """Specialized processor for JSON data."""
    
    def __init__(self):
        """Initialize JSON processor."""
        super().__init__(validator=None, cache_size=500)
    
    def _do_processing(self, data: dict) -> dict:
        """Process JSON data.
        
        Args:
            data: JSON dictionary
            
        Returns:
            Processed JSON
        """
        import json
        # Simulate JSON transformation
        processed = {
            'original': data,
            'processed_at': datetime.now().isoformat(),
            'processor': 'JsonDataProcessor'
        }
        return processed


# Module-level functions
def create_processor(data_type: str = "generic") -> DataProcessor:
    """Factory function to create appropriate processor.
    
    Args:
        data_type: Type of data to process
        
    Returns:
        Configured processor instance
    """
    if data_type == "json":
        return JsonDataProcessor()
    else:
        return DataProcessor()


async def process_data_stream(data_stream: AsyncIterator[T], 
                            processor: DataProcessor[T]) -> int:
    """Process async data stream.
    
    Args:
        data_stream: Async iterator of data
        processor: Processor to use
        
    Returns:
        Number of items processed
    """
    count = 0
    async for data in data_stream:
        await processor.process_async(data)
        count += 1
    return count


def benchmark_processor(processor: DataProcessor, 
                       test_data: List[Any],
                       iterations: int = 3) -> Dict[str, float]:
    """Benchmark processor performance.
    
    Args:
        processor: Processor to benchmark
        test_data: Test data list
        iterations: Number of iterations
        
    Returns:
        Benchmark results
    """
    times = []
    
    for _ in range(iterations):
        processor.clear_cache()
        start = time.time()
        processor.process_batch(test_data)
        elapsed = time.time() - start
        times.append(elapsed)
    
    return {
        'avg_time': sum(times) / len(times),
        'min_time': min(times),
        'max_time': max(times),
        'items_per_second': len(test_data) / (sum(times) / len(times))
    }
