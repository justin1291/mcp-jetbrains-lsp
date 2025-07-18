/**
 * Service for performing complex calculations
 */

import { 
    MathCalculator, 
    MathConfig, 
    MathOperation, 
    calculateCircleArea, 
    factorial,
    DEFAULT_MATH_CONFIG 
} from '../utils/mathUtils';

/**
 * Result of a calculation operation
 */
export interface CalculationResult {
    value: number;
    operation: string;
    timestamp: Date;
    metadata?: Record<string, any>;
}

/**
 * Batch calculation request
 */
export type BatchCalculationRequest = {
    operands: number[];
    operation: MathOperation;
    config?: Partial<MathConfig>;
};

/**
 * Service class for handling various calculations
 */
export class CalculationService {
    private calculator: MathCalculator;
    private operationHistory: CalculationResult[] = [];

    constructor(config: MathConfig = DEFAULT_MATH_CONFIG) {
        this.calculator = new MathCalculator(config);
    }

    /**
     * Performs a simple calculation between two numbers
     * @param a - First number
     * @param b - Second number
     * @param operation - Operation to perform
     * @returns Calculation result
     */
    async performCalculation(
        a: number, 
        b: number, 
        operation: MathOperation
    ): Promise<CalculationResult> {
        this.calculator.updateConfig({ operation });
        
        const value = this.calculator.calculate(a, b);
        const result: CalculationResult = {
            value,
            operation: `${a} ${operation} ${b}`,
            timestamp: new Date(),
            metadata: {
                config: this.calculator.getConfig()
            }
        };

        this.operationHistory.push(result);
        return result;
    }

    /**
     * Calculates the area of a circle using the math utils
     * @param radius - Circle radius
     * @returns Calculation result with circle area
     */
    calculateCircleArea(radius: number): CalculationResult {
        const value = calculateCircleArea(radius);
        const result: CalculationResult = {
            value,
            operation: `circle_area(${radius})`,
            timestamp: new Date(),
            metadata: { type: 'geometry' }
        };

        this.operationHistory.push(result);
        return result;
    }

    /**
     * Calculates factorial using the math utils
     * @param n - Number to calculate factorial for
     * @returns Calculation result with factorial
     */
    calculateFactorial(n: number): CalculationResult {
        try {
            const value = factorial(n);
            const result: CalculationResult = {
                value,
                operation: `factorial(${n})`,
                timestamp: new Date(),
                metadata: { type: 'combinatorics' }
            };

            this.operationHistory.push(result);
            return result;
        } catch (error) {
            const errorMessage = error instanceof Error 
                ? error.message 
                : 'Factorial calculation failed';
            throw new Error(`Factorial calculation failed: ${errorMessage}`);
        }
    }

    /**
     * Performs batch calculations
     * @param request - Batch calculation request
     * @returns Array of calculation results
     */
    async performBatchCalculation(
        request: BatchCalculationRequest
    ): Promise<CalculationResult[]> {
        const { operands, operation, config } = request;
        
        if (operands.length < 2) {
            throw new Error('At least two operands are required for batch calculation');
        }

        if (config) {
            this.calculator.updateConfig(config);
        }

        const results: CalculationResult[] = [];
        
        // Perform operations in sequence
        for (let i = 0; i < operands.length - 1; i++) {
            const result = await this.performCalculation(
                operands[i], 
                operands[i + 1], 
                operation
            );
            results.push(result);
        }

        return results;
    }

    /**
     * Gets the calculation history
     * @param limit - Maximum number of results to return
     * @returns Array of calculation results
     */
    getHistory(limit?: number): CalculationResult[] {
        if (limit) {
            return this.operationHistory.slice(-limit);
        }
        return [...this.operationHistory];
    }

    /**
     * Clears the calculation history
     */
    clearHistory(): void {
        this.operationHistory = [];
    }

    /**
     * Gets statistics about the operations performed
     */
    getStatistics(): {
        totalOperations: number;
        operationTypes: Record<string, number>;
        averageValue: number;
    } {
        const totalOperations = this.operationHistory.length;
        const operationTypes: Record<string, number> = {};
        let totalValue = 0;

        this.operationHistory.forEach(result => {
            const opType = result.metadata?.type || 'arithmetic';
            operationTypes[opType] = (operationTypes[opType] || 0) + 1;
            totalValue += result.value;
        });

        return {
            totalOperations,
            operationTypes,
            averageValue: totalOperations > 0 ? totalValue / totalOperations : 0
        };
    }
}

// Export a default instance
export const defaultCalculationService = new CalculationService();
