/**
 * React component for performing calculations
 */

import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { CalculationService, CalculationResult } from '../services/CalculationService';
import { MathOperation, MathConfig, DEFAULT_MATH_CONFIG } from '../utils/mathUtils';

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
 * Creates a complete MathConfig from partial configuration
 */
const createMathConfig = (partialConfig?: Partial<MathConfig>): MathConfig => {
    return { ...DEFAULT_MATH_CONFIG, ...partialConfig };
};

/**
 * Calculator component for performing mathematical operations
 */
export const Calculator: React.FC<CalculatorProps> = ({
    initialConfig,
    onCalculation,
    showHistory = true
}) => {
    const completeConfig = useMemo(() => createMathConfig(initialConfig), [initialConfig]);
    const [calculationService] = useState(() => new CalculationService(completeConfig));
    
    const [calculatorState, setCalculatorState] = useState<CalculatorState>({
        firstNumber: '',
        secondNumber: '',
        operation: MathOperation.ADD,
        result: null,
        isLoading: false,
        error: null
    });
    
    const [calculationHistory, setCalculationHistory] = useState<CalculationResult[]>([]);

    const updateCalculationHistory = useCallback(() => {
        if (showHistory) {
            const recentHistory = calculationService.getHistory(10);
            setCalculationHistory(recentHistory);
        }
    }, [calculationService, showHistory]);

    useEffect(() => {
        updateCalculationHistory();
    }, [updateCalculationHistory]);

    const createNumberChangeHandler = useCallback((fieldName: 'firstNumber' | 'secondNumber') => 
        (event: React.ChangeEvent<HTMLInputElement>) => {
            const newValue = event.target.value;
            setCalculatorState(previousState => ({
                ...previousState,
                [fieldName]: newValue,
                error: null
            }));
        }, []
    );

    const handleOperationSelection = useCallback((event: React.ChangeEvent<HTMLSelectElement>) => {
        const selectedOperation = event.target.value as MathOperation;
        setCalculatorState(previousState => ({
            ...previousState,
            operation: selectedOperation,
            error: null
        }));
    }, []);

    const executeCalculation = useCallback(async () => {
        const { firstNumber, secondNumber, operation } = calculatorState;
        
        const firstOperand = parseFloat(firstNumber);
        const secondOperand = parseFloat(secondNumber);

        if (isNaN(firstOperand) || isNaN(secondOperand)) {
            setCalculatorState(previousState => ({
                ...previousState,
                error: 'Please enter valid numbers'
            }));
            return;
        }

        setCalculatorState(previousState => ({ 
            ...previousState, 
            isLoading: true, 
            error: null 
        }));

        try {
            const calculationResult = await calculationService.performCalculation(
                firstOperand, 
                secondOperand, 
                operation
            );
            
            setCalculatorState(previousState => ({
                ...previousState,
                result: calculationResult,
                isLoading: false
            }));
            
            if (onCalculation) {
                onCalculation(calculationResult);
            }
            
            updateCalculationHistory();
        } catch (calculationError) {
            const errorMessage = calculationError instanceof Error 
                ? calculationError.message 
                : 'Calculation failed';
                
            setCalculatorState(previousState => ({
                ...previousState,
                error: errorMessage,
                isLoading: false
            }));
        }
    }, [calculatorState, calculationService, onCalculation, updateCalculationHistory]);

    const executeSpecialOperation = useCallback(async (operationType: 'circle' | 'factorial') => {
        const { firstNumber } = calculatorState;
        const operand = parseFloat(firstNumber);

        if (isNaN(operand)) {
            setCalculatorState(previousState => ({
                ...previousState,
                error: 'Please enter a valid number'
            }));
            return;
        }

        setCalculatorState(previousState => ({ 
            ...previousState, 
            isLoading: true, 
            error: null 
        }));

        try {
            let operationResult: CalculationResult;
            
            if (operationType === 'circle') {
                operationResult = calculationService.calculateCircleArea(operand);
            } else {
                operationResult = calculationService.calculateFactorial(operand);
            }

            setCalculatorState(previousState => ({
                ...previousState,
                result: operationResult,
                isLoading: false
            }));

            if (onCalculation) {
                onCalculation(operationResult);
            }

            updateCalculationHistory();
        } catch (operationError) {
            const errorMessage = operationError instanceof Error 
                ? operationError.message 
                : 'Operation failed';
                
            setCalculatorState(previousState => ({
                ...previousState,
                error: errorMessage,
                isLoading: false
            }));
        }
    }, [calculatorState, calculationService, onCalculation, updateCalculationHistory]);

    const resetCalculator = useCallback(() => {
        setCalculatorState({
            firstNumber: '',
            secondNumber: '',
            operation: MathOperation.ADD,
            result: null,
            isLoading: false,
            error: null
        });
    }, []);

    const renderCalculationHistory = () => {
        if (!showHistory || calculationHistory.length === 0) {
            return null;
        }

        return (
            <div className="calculator-history">
                <h3>Recent Calculations</h3>
                <ul>
                    {calculationHistory.map((historyItem, index) => (
                        <li key={index}>
                            {historyItem.operation} = {historyItem.value}
                            <small> ({historyItem.timestamp.toLocaleTimeString()})</small>
                        </li>
                    ))}
                </ul>
            </div>
        );
    };

    const { firstNumber, secondNumber, operation, result, isLoading, error } = calculatorState;
    const hasValidFirstNumber = firstNumber && !isNaN(parseFloat(firstNumber));
    const hasValidSecondNumber = secondNumber && !isNaN(parseFloat(secondNumber));
    const canPerformBasicCalculation = hasValidFirstNumber && hasValidSecondNumber;
    const canPerformSpecialOperation = hasValidFirstNumber;

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
                        onChange={createNumberChangeHandler('firstNumber')}
                        disabled={isLoading}
                    />
                </div>

                <div className="input-group">
                    <label htmlFor="operation">Operation:</label>
                    <select
                        id="operation"
                        value={operation}
                        onChange={handleOperationSelection}
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
                        onChange={createNumberChangeHandler('secondNumber')}
                        disabled={isLoading}
                    />
                </div>

                <div className="button-group">
                    <button 
                        onClick={executeCalculation} 
                        disabled={isLoading || !canPerformBasicCalculation}
                    >
                        {isLoading ? 'Calculating...' : 'Calculate'}
                    </button>
                    
                    <button 
                        onClick={() => executeSpecialOperation('circle')}
                        disabled={isLoading || !canPerformSpecialOperation}
                        title="Calculate circle area using first number as radius"
                    >
                        Circle Area
                    </button>
                    
                    <button 
                        onClick={() => executeSpecialOperation('factorial')}
                        disabled={isLoading || !canPerformSpecialOperation}
                        title="Calculate factorial of first number"
                    >
                        Factorial
                    </button>
                    
                    <button onClick={resetCalculator} disabled={isLoading}>
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

            {renderCalculationHistory()}
        </div>
    );
};

export default Calculator;
