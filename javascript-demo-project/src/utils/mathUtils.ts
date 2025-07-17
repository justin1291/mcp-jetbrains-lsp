/**
 * Utility functions for mathematical operations
 * @author Test Developer
 * @since 1.0.0
 */

export const PI = 3.14159;

/**
 * Calculates the area of a circle
 * @param radius - The radius of the circle
 * @returns The area of the circle
 */
export function calculateCircleArea(radius: number): number {
    return PI * radius * radius;
}

/**
 * Calculates the factorial of a number
 * @param n - The number to calculate factorial for
 * @returns The factorial of n
 * @throws Error if n is negative
 */
export const factorial = (n: number): number => {
    if (n < 0) {
        throw new Error('Factorial is not defined for negative numbers');
    }
    if (n === 0 || n === 1) {
        return 1;
    }
    return n * factorial(n - 1);
};

/**
 * Mathematical operation types
 */
export enum MathOperation {
    ADD = 'add',
    SUBTRACT = 'subtract',
    MULTIPLY = 'multiply',
    DIVIDE = 'divide'
}

/**
 * Configuration for mathematical operations
 */
export interface MathConfig {
    precision: number;
    operation: MathOperation;
    useLogging?: boolean;
}

/**
 * A utility class for advanced mathematical operations
 */
export class MathCalculator {
    private config: MathConfig;

    constructor(config: MathConfig) {
        this.config = config;
    }

    /**
     * Performs the configured operation on two numbers
     * @param a - First operand
     * @param b - Second operand
     * @returns Result of the operation
     */
    calculate(a: number, b: number): number {
        if (this.config.useLogging) {
            console.log(`Performing ${this.config.operation} on ${a} and ${b}`);
        }

        let result: number;
        switch (this.config.operation) {
            case MathOperation.ADD:
                result = a + b;
                break;
            case MathOperation.SUBTRACT:
                result = a - b;
                break;
            case MathOperation.MULTIPLY:
                result = a * b;
                break;
            case MathOperation.DIVIDE:
                if (b === 0) {
                    throw new Error('Division by zero');
                }
                result = a / b;
                break;
            default:
                throw new Error(`Unsupported operation: ${this.config.operation}`);
        }

        return Number(result.toFixed(this.config.precision));
    }

    /**
     * Gets the current configuration
     */
    getConfig(): MathConfig {
        return { ...this.config };
    }

    /**
     * Updates the configuration
     */
    updateConfig(newConfig: Partial<MathConfig>): void {
        this.config = { ...this.config, ...newConfig };
    }
}

// Default configuration
export const DEFAULT_MATH_CONFIG: MathConfig = {
    precision: 2,
    operation: MathOperation.ADD,
    useLogging: false
};
