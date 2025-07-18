/**
 * Demonstration of TSX symbol extraction workaround
 */

// First, let's analyze the Calculator.tsx file content
const calculatorContent = `
import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { CalculationService, CalculationResult } from '../services/CalculationService';

interface CalculatorProps {
    initialConfig?: Partial<MathConfig>;
    onCalculation?: (result: CalculationResult) => void;
    showHistory?: boolean;
}

interface CalculatorState {
    firstNumber: string;
    secondNumber: string;
    operation: MathOperation;
    result: CalculationResult | null;
    isLoading: boolean;
    error: string | null;
}

export const Calculator: React.FC<CalculatorProps> = ({ 
    initialConfig, 
    onCalculation, 
    showHistory = true 
}) => {
    const [calculatorState, setCalculatorState] = useState<CalculatorState>({
        firstNumber: '',
        secondNumber: '',
        operation: MathOperation.ADD,
        result: null,
        isLoading: false,
        error: null
    });

    const handleCalculation = useCallback(async () => {
        // Implementation here
        return null;
    }, []);

    return (
        <div className="calculator">
            <h2>Calculator</h2>
            {/* JSX content here */}
        </div>
    );
};
`;

// Preprocessing function to extract TypeScript symbols
function preprocessTSXForSymbolExtraction(content: string): string {
    let processed = content;
    
    // Remove JSX return statements but preserve function signatures
    processed = processed.replace(
        /return\s*\(\s*<[\s\S]*?>\s*[\s\S]*?<\/[\s\S]*?>\s*\);?/g,
        'return null;'
    );
    
    // Remove simple JSX returns
    processed = processed.replace(
        /return\s*<[\s\S]*?>;?/g,
        'return null;'
    );
    
    // Remove JSX variable assignments
    processed = processed.replace(
        /=\s*<[\s\S]*?>;?/g,
        '= null;'
    );
    
    return processed;
}

// Analyze the content
export function analyzeTSXContent(content: string) {
    const hasJSX = /<[^>]+>/.test(content);
    const hasHooks = /use[A-Z][a-zA-Z]*\s*\(/.test(content);
    const hasInterfaces = /interface\s+[A-Z][a-zA-Z]*/.test(content);
    const hasComponents = /React\.FC|React\.Component/.test(content);
    
    return {
        hasJSX,
        hasHooks,
        hasInterfaces,
        hasComponents,
        symbolsExpected: hasInterfaces || hasComponents || hasHooks
    };
}

// Test the analysis
const analysis = analyzeTSXContent(calculatorContent);

/**
 * This demonstrates what we would find in a typical TSX file:
 * - Interfaces (CalculatorProps, CalculatorState)
 * - React components (Calculator)
 * - Hooks (useState, useCallback, etc.)
 * - TypeScript types and imports
 */

export { preprocessTSXForSymbolExtraction, analyzeTSXContent };
