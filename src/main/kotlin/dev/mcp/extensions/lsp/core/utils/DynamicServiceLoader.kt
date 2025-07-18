package dev.mcp.extensions.lsp.core.utils

import com.intellij.openapi.diagnostic.Logger
import dev.mcp.extensions.lsp.core.interfaces.DefinitionFinder
import dev.mcp.extensions.lsp.core.interfaces.HoverInfoProvider
import dev.mcp.extensions.lsp.core.interfaces.ReferenceFinder
import dev.mcp.extensions.lsp.core.interfaces.SymbolExtractor
import java.util.*

/**
 * Dynamic service loader that uses Java's ServiceLoader mechanism to discover
 * language-specific implementations across plugin boundaries.
 *
 * This approach is compatible with IntelliJ's plugin class loader architecture
 * and properly handles optional dependencies.
 */
object DynamicServiceLoader {
    private val logger = Logger.getInstance(DynamicServiceLoader::class.java)

    // Cache for loaded services to improve performance
    private val serviceCache = mutableMapOf<Class<*>, List<Any>>()

    /**
     * Load all available implementations of a service interface using ServiceLoader.
     * This properly handles cross-plugin boundaries and optional dependencies.
     * 
     * IMPORTANT: This method gracefully handles cases where service implementations
     * depend on unavailable classes (e.g., Python PSI classes when Python plugin is not installed).
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> loadServices(serviceClass: Class<T>): List<T> {
        return serviceCache.getOrPut(serviceClass) {
            try {
                logger.debug("Loading services for: ${serviceClass.name}")

                // Use the current thread's context class loader first
                val contextLoader = Thread.currentThread().contextClassLoader
                val originalLoader = contextLoader

                val services = mutableListOf<T>()

                // Try with the service class's class loader
                try {
                    Thread.currentThread().contextClassLoader = serviceClass.classLoader
                    val serviceLoader = ServiceLoader.load(serviceClass, serviceClass.classLoader)
                    
                    // Iterate carefully to catch individual service loading failures
                    val iterator = serviceLoader.iterator()
                    while (iterator.hasNext()) {
                        try {
                            val service = iterator.next()
                            services.add(service)
                            logger.info("Successfully loaded service: ${service.javaClass.name}")
                        } catch (e: ServiceConfigurationError) {
                            // This happens when a service implementation can't be instantiated
                            // (e.g., Python classes when Python plugin is not installed)
                            logger.warn("Failed to load service implementation: ${e.message}")
                            logger.debug("ServiceConfigurationError details", e)
                        } catch (e: NoClassDefFoundError) {
                            // This happens when required classes (like Python PSI) are missing
                            logger.warn("Service implementation requires unavailable classes: ${e.message}")
                            logger.debug("NoClassDefFoundError details", e)
                        } catch (e: Exception) {
                            logger.warn("Unexpected error loading service: ${e.message}")
                            logger.debug("Exception details", e)
                        }
                    }
                } finally {
                    Thread.currentThread().contextClassLoader = originalLoader
                }

                // Also try with the context class loader if different
                if (contextLoader != serviceClass.classLoader) {
                    try {
                        val serviceLoader = ServiceLoader.load(serviceClass, contextLoader)
                        val iterator = serviceLoader.iterator()
                        while (iterator.hasNext()) {
                            try {
                                val service = iterator.next()
                                if (!services.any { it.javaClass == service.javaClass }) {
                                    services.add(service)
                                    logger.debug("Loaded service from context loader: ${service.javaClass.name}")
                                }
                            } catch (e: ServiceConfigurationError) {
                                logger.debug("Failed to load service from context loader: ${e.message}")
                            } catch (e: NoClassDefFoundError) {
                                logger.debug("Service requires unavailable classes: ${e.message}")
                            }
                        }
                    } catch (e: Exception) {
                        logger.debug("Error with context loader: ${e.message}")
                    }
                }

                logger.info("Loaded ${services.size} services for ${serviceClass.simpleName}")
                services
            } catch (e: Exception) {
                logger.error("Failed to load services for ${serviceClass.name}", e)
                emptyList<T>()
            }
        } as List<T>
    }

    /**
     * Get a specific service implementation by class name.
     * Returns the first matching implementation or null if not found.
     */
    fun <T : Any> loadService(serviceInterface: Class<T>, implementationClassName: String): T? {
        logger.debug("Loading service: $implementationClassName for interface: ${serviceInterface.name}")
        val services = loadServices(serviceInterface)
        logger.debug("Available services: ${services.map { it.javaClass.name }}")
        val result = services.firstOrNull { it.javaClass.name == implementationClassName }
        logger.debug("Service match result: $result")
        return result
    }

    /**
     * Load a SymbolExtractor implementation by class name.
     */
    fun loadSymbolExtractor(className: String): SymbolExtractor? {
        logger.debug("Attempting to load SymbolExtractor: $className")
        val result = loadService(SymbolExtractor::class.java, className)
        logger.debug("SymbolExtractor load result: $result")
        return result
    }

    /**
     * Load a DefinitionFinder implementation by class name.
     */
    fun loadDefinitionFinder(className: String): DefinitionFinder? {
        return loadService(DefinitionFinder::class.java, className)
    }

    /**
     * Load a HoverInfoProvider implementation by class name.
     */
    fun loadHoverInfoProvider(className: String): HoverInfoProvider? {
        return loadService(HoverInfoProvider::class.java, className)
    }

    /**
     * Load a ReferenceFinder implementation by class name.
     */
    fun loadReferenceFinder(className: String): ReferenceFinder? {
        return loadService(ReferenceFinder::class.java, className)
    }

    /**
     * Get all available SymbolExtractor implementations.
     */
    fun getAllSymbolExtractors(): List<SymbolExtractor> {
        return loadServices(SymbolExtractor::class.java)
    }

    /**
     * Check if a specific implementation class is available.
     */
    fun isServiceAvailable(serviceInterface: Class<*>, implementationClassName: String): Boolean {
        val services = loadServices(serviceInterface)
        return services.any { it.javaClass.name == implementationClassName }
    }

    /**
     * Check if a SymbolExtractor implementation is available.
     */
    fun isSymbolExtractorAvailable(className: String): Boolean {
        return isServiceAvailable(SymbolExtractor::class.java, className)
    }

    /**
     * Clear the service cache. Useful for testing or when plugins are reloaded.
     */
    fun clearCache() {
        serviceCache.clear()
        logger.debug("Cleared service cache")
    }

    /**
     * Get information about loaded services (for debugging).
     */
    fun getLoadedServices(): Map<String, List<String>> {
        return serviceCache.mapKeys { it.key.simpleName }
            .mapValues { entry ->
                entry.value.map { it.javaClass.name }
            }
    }
}
