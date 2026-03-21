package com.staticanalysis.analyzer;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

public class MethodAnalyzer extends VoidVisitorAdapter<Void> {

    @Override
    public void visit(MethodDeclaration md, Void arg) {
        super.visit(md, arg);

        String returnType = md.getTypeAsString();
        int paramCount = md.getParameters().size();
        String visibility = md.getAccessSpecifier().asString();
        int bodyLines = md.getBody()
            .map(b -> b.toString().split("\n").length)
            .orElse(0);

        System.out.println("  Method: " + md.getName()
            + " | " + visibility
            + " | Returns: " + returnType
            + " | Params: " + paramCount
            + " | Lines: " + bodyLines);
    }
}
