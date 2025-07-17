/**
 * React component for performing calculations
 */

import React, { useState, useEffect, useCallback } from 'react';
import { CalculationService, CalculationResult } from '../services/CalculationService';
import { MathOperation, MathConfig } from '../utils/mathUtils';

/**
 * Props for the Calculator component
 */
interface CalculatorProps {
    /** Initial configuration for the calculator */
    initialConfig?: Partial<MathConfig>;
    /** Callback when calculation is performed */
    onCalculation?: (result: CalculationResult) => void;
    /** Whether to show calculation history */
    showHistory?: boolean;
}

/**
 * State for the calculator form
 */
interface CalculatorState {
    firstNumber: string;
    secondNumber: string;
    operation: MathOperation;
    result: CalculationResult | null;
    isLoading: boolean;
    error: string | null;
}

/**
 * Calculator component for performing mathematical operations
 */
export const Calculator: React.FC<CalculatorProps> = ({
    initialConfig,
    onCalculation,
    showHistory = true
}) => {
    const [calculationService] = useState(() => new CalculationService(initialConfig));
    const [state, setState] = useState<CalculatorState>({
        firstNumber: '',
        secondNumber: '',
        operation: MathOperation.ADD,
        result: null,
        isLoading: false,
        error: null
    });
    const [history, setHistory] = useState<CalculationResult[]>([]);

    /**
     * Updates the calculation history from the service
     */
    const updateHistory = useCallback(() => {
        if (showHistory) {
            setHistory(calculationService.getHistory(10)); // Show last 10 operations
        }
    }, [calculationService, showHistory]);

    useEffect(() => {
        updateHistory();
    }, [updateHistory]);

    /**
     * Handles input change for numbers
     */
    const handleNumberChange = useCallback((field: 'firstNumber' | 'secondNumber') => 
        (event: React.ChangeEvent<HTMLInputElement>) => {
            setState(prev => ({
                ...prev,
                [field]: event.target.value,
                error: null
            }));
        }, []
    );

    /**
     * Handles operation selection change
     */
    const handleOperationChange = useCallback((event: React.ChangeEvent<HTMLSelectElement>) => {
        setState(prev => ({
            ...prev,
            operation: event.target.value as MathOperation,
            error: null
        }));
    }, []);

    /**
     * Performs the calculation
     */
    const handleCalculate = useCallback(async () => {
        const { firstNumber, secondNumber, operation } = state;
        
        const a = parseFloat(firstNumber);
        const b = parseFloat(secondNumber);

        if (isNaN(a) || isNaN(b)) {
            setState(prev => ({
                ...prev,
                error: 'Please enter valid numbers'
            }));
            return;
        }

        setState(prev => ({ ...prev, isLoading: true, error: null }));

        try {
            const result = await calculationService.performCalculation(a, b, operation);
            setState(prev => ({
                ...prev,
                result,
                isLoading: false
            }));
            
            if (onCalculation) {
                onCalculation(result);
            }
            
            updateHistory();
        } catch (error) {
            setState(prev => ({
                ...prev,
                error: error instanceof Error ? error.message : 'Calculation failed',
                isLoading: false
            }));
        }
    }, [state, calculationService, onCalculation, updateHistory]);

    /**
     * Handles special operation buttons (circle area, factorial)
     */
    const handleSpecialOperation = useCallback(async (type: 'circle' | 'factorial') => {
        const { firstNumber } = state;
        const num = parseFloat(firstNumber);

        if (isNaN(num)) {
            setState(prev => ({
                ...prev,
                error: 'Please enter a valid number'
            }));
            return;
        }

        setState(prev => ({ ...prev, isLoading: true, error: null }));

        try {
            let result: CalculationResult;
            
            if (type === 'circle') {
                result = calculationService.calculateCircleArea(num);
            } else {
                result = calculationService.calculateFactorial(num);
            }

            setState(prev => ({
                ...prev,
                result,
                isLoading: false
            }));

            if (onCalculation) {
                onCalculation(result);
            }

            updateHistory();
        } catch (error) {
            setState(prev => ({
                ...prev,
                error: error instanceof Error ? error.message : 'Operation failed',
                isLoading: false
            }));
        }
    }, [state.firstNumber, calculationService, onCalculation, updateHistory]);

    /**
     * Clears the calculator
     */
    const handleClear = useCallback(() => {
        setState({
            firstNumber: '',
            secondNumber: '',
            operation: MathOperation.ADD,
            result: null,
            isLoading: false,
            error: null
        });
    }, []);

    /**
     * Renders the operation history
     */
    const renderHistory = () => {
        if (!showHistory || history.length === 0) {
            return null;
        }

        return (
            <div className="calculator-history">
                <h3>Recent Calculations</h3>
                <ul>
                    {history.map((item, index) => (
                        <li key={index}>
                            {item.operation} = {item.value}
                            <small> ({item.timestamp.toLocaleTimeString()})</small>
                        </li>
                    ))}
                </ul>
            </div>
        );
    };

    const { firstNumber, secondNumber, operation, result, isLoading, error } = state;

    return (
        <div className="calculator">
            <h2>Advanced Calculator</h2>
            
            <div className="calculator-form">
                <div className="input-group">
                    <label htmlFor="first-number">First Number:</label>
                    <input
                        id="first-number"
                        type="number"
                        value={firstNumber}
                        onChange={handleNumberChange('firstNumber')}
                        disabled={isLoading}
                    />
                </div>

                <div className="input-group">
                    <label htmlFor="operation">Operation:</label>
                    <select
                        id="operation"
                        value={operation}
                        onChange={handleOperationChange}
                        disabled={isLoading}
                    >
                        <option value={MathOperation.ADD}>Add (+)</option>
                        <option value={MathOperation.SUBTRACT}>Subtract (-)</option>
                        <option value={MathOperation.MULTIPLY}>Multiply (×)</option>
                        <option value={MathOperation.DIVIDE}>Divide (÷)</option>
                    </select>
                </div>

                <div className="input-group">
                    <label htmlFor="second-number">Second Number:</label>
                    <input
                        id="second-number"
                        type="number"
                        value={secondNumber}
                        onChange={handleNumberChange('secondNumber')}
                        disabled={isLoading}
                    />
                </div>

                <div className="button-group">
                    <button 
                        onClick={handleCalculate} 
                        disabled={isLoading || !firstNumber || !secondNumber}
                    >
                        {isLoading ? 'Calculating...' : 'Calculate'}
                    </button>
                    
                    <button 
                        onClick={() => handleSpecialOperation('circle')}
                        disabled={isLoading || !firstNumber}
                        title="Calculate circle area using first number as radius"
                    >
                        Circle Area
                    </button>
                    
                    <button 
                        onClick={() => handleSpecialOperation('factorial')}
                        disabled={isLoading || !firstNumber}
                        title="Calculate factorial of first number"
                    >
                        Factorial
                    </button>
                    
                    <button onClick={handleClear} disabled={isLoading}>
                        Clear
                    </button>
                </div>
            </div>

            {error && (
                <div className="calculator-error">
                    <strong>Error:</strong> {error}
                </div>
            )}

            {result && !error && (
                <div className="calculator-result">
                    <h3>Result: {result.value}</h3>
                    <p>Operation: {result.operation}</p>
                    <p>Calculated at: {result.timestamp.toLocaleString()}</p>
                </div>
            )}

            {renderHistory()}
        </div>
    );
};

/**
 * Higher-order component that provides calculation statistics
 */
export const withCalculationStats = <P extends object>(
    Component: React.ComponentType<P>
) => {
    return (props: P & { calculationService?: CalculationService }) => {
        const [stats, setStats] = useState({ totalOperations: 0, operationTypes: {}, averageValue: 0 });
        const service = props.calculationService || new CalculationService();

        useEffect(() => {
            const updateStats = () => {
                setStats(service.getStatistics());
            };

            // Update stats every 5 seconds
            const interval = setInterval(updateStats, 5000);
            updateStats(); // Initial update

            return () => clearInterval(interval);
        }, [service]);

        return (
            <div>
                <div className="calculation-stats">
                    <h4>Statistics</h4>
                    <p>Total Operations: {stats.totalOperations}</p>
                    <p>Average Value: {stats.averageValue.toFixed(2)}</p>
                </div>
                <Component {...props} />
            </div>
        );
    };
};

export default Calculator;
