package com.extractor.analyzer;

import com.extractor.model.CodeIssue;
import com.extractor.model.Component;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.CtScanner;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Analyzes Java code patterns using Spoon to detect common issues.
 * Expanded with rules inspired by Google Error Prone.
 */
public class StaticCodeAnalyzer {

    private static final List<String> BOXED_PRIMITIVES = Arrays.asList(
            "java.lang.Integer", "java.lang.Long", "java.lang.Double",
            "java.lang.Float", "java.lang.Short", "java.lang.Byte", "java.lang.Boolean"
    );

    public StaticCodeAnalyzer() {
    }

    public void analyzeType(CtType<?> type, Component component) {
        // Check Package Naming Convention
        checkPackageNaming(type, component);

        type.accept(new CtScanner() {

            // REGLA 1: Comparación de Referencias (String y Wrappers)
            // Error Prone: ReferenceEquality / StringEquality
            @Override
            public <T> void visitCtBinaryOperator(CtBinaryOperator<T> operator) {
                super.visitCtBinaryOperator(operator);

                if (operator.getKind() == BinaryOperatorKind.EQ || operator.getKind() == BinaryOperatorKind.NE) {
                    CtExpression<?> left = operator.getLeftHandOperand();
                    CtExpression<?> right = operator.getRightHandOperand();

                    // String == String
                    if (isType(left, "java.lang.String") || isType(right, "java.lang.String")) {
                        addIssue(component, operator,
                                "Potential String comparison using ==/!=. Use .equals() instead.",
                                "string_comparison_operator", CodeIssue.Severity.WARNING);
                    }

                    // Integer == Integer (Boxed Primitive Equality)
                    if (isBoxedPrimitive(left) && isBoxedPrimitive(right)) {
                        addIssue(component, operator,
                                "Reference equality used on Boxed Primitives (e.g., Integer, Long). Values > 127 may compare false.",
                                "boxed_primitive_equality", CodeIssue.Severity.CRITICAL);
                    }
                }
            }

            // REGLA 2: BigDecimal(double)
            // Error Prone: BigDecimalDoubleConstructor
            @Override
            public <T> void visitCtConstructorCall(CtConstructorCall<T> ctConstructorCall) {
                super.visitCtConstructorCall(ctConstructorCall);

                if (isType(ctConstructorCall, "java.math.BigDecimal")) {
                    List<CtExpression<?>> args = ctConstructorCall.getArguments();
                    if (args.size() == 1 && isType(args.get(0), "double")) {
                        addIssue(component, ctConstructorCall,
                                "Avoid new BigDecimal(double). It creates unpredictable precision. Use new BigDecimal(String) or BigDecimal.valueOf(double).",
                                "big_decimal_double_constructor", CodeIssue.Severity.WARNING);
                    }
                }
            }

            // REGLA 3: Optional retornando null
            // Error Prone: ReturnNullFromOptional
            @Override
            public <T> void visitCtReturn(CtReturn<T> returnStatement) {
                super.visitCtReturn(returnStatement);

                CtExpression<?> returnedExpr = returnStatement.getReturnedExpression();
                if (returnedExpr instanceof CtLiteral && ((CtLiteral<?>) returnedExpr).getValue() == null) {
                    // Buscar el método padre para ver si retorna Optional
                    CtMethod<?> parentMethod = returnStatement.getParent(CtMethod.class);
                    if (parentMethod != null && isType(parentMethod.getType(), "java.util.Optional")) {
                        addIssue(component, returnStatement,
                                "Methods returning Optional should never return null. Return Optional.empty() instead.",
                                "optional_return_null", CodeIssue.Severity.WARNING);
                    }
                }
            }

            // REGLA 4: Switch sin default
            // Error Prone: MissingDefault
            @Override
            public <E> void visitCtSwitch(CtSwitch<E> switchStatement) {
                super.visitCtSwitch(switchStatement);

                boolean hasDefault = false;
                for (CtCase<? super E> caseStatement : switchStatement.getCases()) {
                    if (caseStatement.getCaseExpressions().isEmpty()) { // Spoon define default como un case sin expresión
                        hasDefault = true;
                        break;
                    }
                }

                if (!hasDefault) {
                    // Excepción: Si es un switch sobre un Enum, a veces se perdona, pero es buena práctica tenerlo
                    addIssue(component, switchStatement,
                            "Switch statement is missing a 'default' case.",
                            "missing_switch_default", CodeIssue.Severity.INFO);
                }
            }

            @Override
            public void visitCtCatch(CtCatch catchBlock) {
                super.visitCtCatch(catchBlock);

                CtBlock<?> body = catchBlock.getBody();
                // REGLA 5: Empty Catch
                if (body != null && body.getStatements().isEmpty()) {
                    addIssue(component, catchBlock,
                            "Empty catch block detected. Exceptions should not be swallowed.",
                            "empty_catch_block", CodeIssue.Severity.WARNING);
                }

                // REGLA 6: Catch genérico
                if (catchBlock.getParameter() != null && catchBlock.getParameter().getType() != null) {
                    String exceptionType = catchBlock.getParameter().getType().getQualifiedName();
                    if ("java.lang.Exception".equals(exceptionType) || "java.lang.Throwable".equals(exceptionType)) {
                        addIssue(component, catchBlock,
                                "Catching generic Exception or Throwable is generally discouraged.",
                                "generic_exception_catch", CodeIssue.Severity.INFO);
                    }
                }
            }

            @Override
            public <T> void visitCtInvocation(CtInvocation<T> invocation) {
                super.visitCtInvocation(invocation);

                if (invocation.getExecutable() == null) return;

                String method = invocation.getExecutable().getSimpleName();

                // REGLA 7: System.out/err
                if (invocation.getTarget() != null) {
                    String target = invocation.getTarget().toString();
                    if (("System.out".equals(target) || "System.err".equals(target)) && method.startsWith("print")) {
                        addIssue(component, invocation,
                                "Direct use of System.out/err. Use a logger instead.",
                                "system_out_println", CodeIssue.Severity.INFO);
                    }
                }

                // REGLA 8: Hardcoded strings en comparaciones
                if (("equals".equals(method) || "contains".equals(method) || "equalsIgnoreCase".equals(method)) &&
                        !invocation.getArguments().isEmpty()) {
                    CtExpression<?> arg = invocation.getArguments().get(0);
                    if (arg instanceof CtLiteral && ((CtLiteral<?>) arg).getValue() instanceof String) {
                        addIssue(component, invocation,
                                "Comparison with hardcoded String literal: \"" + ((CtLiteral<?>) arg).getValue() + "\". Consider using a constant.",
                                "hardcoded_string_comparison", CodeIssue.Severity.INFO);
                    }
                }

                // REGLA 9: Thread.stop() / Thread.suspend() (Deprecados y peligrosos)
                if (("stop".equals(method) || "suspend".equals(method) || "resume".equals(method)) &&
                        isType(invocation.getTarget(), "java.lang.Thread")) {
                    addIssue(component, invocation,
                            "Usage of deprecated Thread method (" + method + "). These methods are unsafe.",
                            "thread_unsafe_method", CodeIssue.Severity.CRITICAL);
                }
            }
        });
    }

    // --- Helper Methods ---

    private void addIssue(Component component, CtElement element, String message, String pattern, CodeIssue.Severity severity) {
        component.addCodeIssue(CodeIssue.builder()
                .type(CodeIssue.Type.BUG_PATTERN)
                .severity(severity)
                .message(message)
                .line(getSafeLine(element))
                .pattern(pattern)
                .build());
    }

    private void checkPackageNaming(CtType<?> type, Component component) {
        CtPackage pkg = type.getPackage();
        if (pkg == null) return;

        String fqn = pkg.getQualifiedName();
        if (fqn == null || fqn.isEmpty()) return;

        String[] parts = fqn.split("\\.");
        for (String part : parts) {
            if (!part.isEmpty() && Character.isUpperCase(part.charAt(0))) {
                addIssue(component, type,
                        "Package name component \"" + part + "\" is capitalized. Java convention recommends lowercase package names.",
                        "capitalized_package_name", CodeIssue.Severity.INFO);
                break;
            }
        }
    }

    private boolean isType(CtElement element, String expectedType) {
        if (element == null) return false;
        if (element instanceof CtExpression) {
            CtTypeReference<?> typeRef = ((CtExpression<?>) element).getType();
            return typeRef != null && expectedType.equals(typeRef.getQualifiedName());
        }
        if (element instanceof CtTypeReference) {
            return expectedType.equals(((CtTypeReference<?>) element).getQualifiedName());
        }
        return false;
    }

    private boolean isBoxedPrimitive(CtExpression<?> expression) {
        if (expression == null || expression.getType() == null) return false;
        return BOXED_PRIMITIVES.contains(expression.getType().getQualifiedName());
    }

    private int getSafeLine(CtElement element) {
        if (element != null && element.getPosition() != null && element.getPosition().isValidPosition()) {
            return element.getPosition().getLine();
        }
        return 0;
    }
}