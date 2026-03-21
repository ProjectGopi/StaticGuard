package com.staticanalysis.analyzer;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

public class ClassAnalyzer extends VoidVisitorAdapter<Void> {

    @Override
    public void visit(ClassOrInterfaceDeclaration cid, Void arg) {
        super.visit(cid, arg);

        System.out.println("  Class: " + cid.getName());
        System.out.println("     Methods: " + cid.getMethods().size()
            + " | Fields: " + cid.getFields().size()
            + " | Constructors: " + cid.getConstructors().size());

        if (!cid.getExtendedTypes().isEmpty()) {
            System.out.println("     Extends: " + cid.getExtendedTypes());
        }
        if (!cid.getImplementedTypes().isEmpty()) {
            System.out.println("     Implements: " + cid.getImplementedTypes());
        }

        System.out.println("     ---");
    }
}
