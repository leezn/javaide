/*
 * Copyright (C) 2018 Tran Le Duy
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.duy.ide.javaide.editor.autocomplete.internal;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.duy.ide.javaide.editor.autocomplete.dex.IClass;
import com.duy.ide.javaide.editor.autocomplete.dex.JavaDexClassLoader;
import com.duy.ide.javaide.utils.DLog;
import com.sun.tools.javac.tree.JCTree;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static com.sun.tools.javac.tree.JCTree.JCBlock;
import static com.sun.tools.javac.tree.JCTree.JCClassDecl;
import static com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import static com.sun.tools.javac.tree.JCTree.JCExpression;
import static com.sun.tools.javac.tree.JCTree.JCFieldAccess;
import static com.sun.tools.javac.tree.JCTree.JCIdent;
import static com.sun.tools.javac.tree.JCTree.JCImport;
import static com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import static com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import static com.sun.tools.javac.tree.JCTree.JCStatement;
import static com.sun.tools.javac.tree.JCTree.JCVariableDecl;

public class TypeResolver {
    private static final String TAG = "TypeResolver";
    private JavaDexClassLoader mClassLoader;
    private JCCompilationUnit mUnit;

    public TypeResolver(JavaDexClassLoader classLoader, JCCompilationUnit unit) {
        mClassLoader = classLoader;
        this.mUnit = unit;
    }

    public void resolveType(@NonNull JCExpression expression) {

        resolveTypeImpl(expression);
    }

    @Nullable
    private IClass resolveTypeImpl(@NonNull JCExpression expression) {
        List<JCTree> list = extractExpression(expression);
        if (expression instanceof JCIdent) {
            JCIdent jcIdent = (JCIdent) expression;

            //variable declaration, static import or inner class
            //case: variableDecl
            JCVariableDecl variableDecl = getVariableDeclaration(mUnit, jcIdent);
            if (DLog.DEBUG) DLog.d(TAG, "variableDecl = " + variableDecl);
            if (variableDecl != null) {
                String className = variableDecl.getType().toString();
                List<JCImport> imports = mUnit.getImports();
                for (JCImport jcImport : imports) {
                    String fullClassName = jcImport.getQualifiedIdentifier().toString();
                    if (fullClassName.equals(className) ||
                            fullClassName.endsWith("." + className)) {
                        className = fullClassName;
                        break;
                    }
                }
                System.out.println("className = " + className);
                IClass classDesc =
                        mClassLoader.getClassReader().readClassByName(className, null);
                return classDesc;
            }

            //case: static import

            //case inner class
        }
        return null;
    }

    @Nullable
    private List<JCTree> extractExpression(JCExpression expression) {
        JCTree last = expression;
        LinkedList<JCTree> list = new LinkedList<>();
        while (last != null) {
            list.addFirst(last);
            if (last instanceof JCMethodInvocation) {
                JCExpression methodSelect = ((JCMethodInvocation) last).getMethodSelect();
                if (methodSelect instanceof JCIdent) {
                    //not need add to list because it belong to method
                    last = null;
                    break;
                } else if (methodSelect instanceof JCFieldAccess) {
                    last = ((JCFieldAccess) methodSelect).getExpression();
                } else {
                    if (DLog.DEBUG) {
                        DLog.w(TAG, "extractExpression: can not resolve type of expression "
                                + expression);
                    }
                    return null;
                }
            } else if (last instanceof JCFieldAccess) {
                last = ((JCFieldAccess) last).getExpression();
            } else if (last instanceof JCIdent) {
                last = null;
            } else {
                if (DLog.DEBUG) {
                    DLog.w(TAG, "extractExpression: can not resolve type of expression "
                            + expression);
                }
                //should not happen
                return null;
            }
        }
        return list;
    }

    @Nullable
    private JCVariableDecl getVariableDeclaration(JCCompilationUnit unit,
                                                  JCIdent jcIdent) {
        List<JCImport> imports = unit.getImports();
        List<JCTree> typeDecls = unit.getTypeDecls();
        for (JCTree typeDecl : typeDecls) {
            List<JCVariableDecl> variableDeclaration = getVariableDeclaration(typeDecl, jcIdent);
            if (!variableDeclaration.isEmpty()) {
                //ambiguous
                if (variableDeclaration.size() > 1) {
                    return null;
                } else {
                    return variableDeclaration.get(0);
                }
            }
        }
        return null;
    }

    @NonNull
    private List<JCVariableDecl> getVariableDeclaration(final JCTree parent,
                                                        final JCIdent jcIdent) {
        List<JCVariableDecl> result = new ArrayList<>();
        if (parent instanceof JCClassDecl) {
            List<JCTree> members = ((JCClassDecl) parent).getMembers();
            //all member equals scope
            for (JCTree member : members) {
                if (member instanceof JCVariableDecl) { //variable
                    JCVariableDecl variableDecl = (JCVariableDecl) member;
                    if (canBeSampleVariable(parent, variableDecl, jcIdent)) {
                        result.add(variableDecl);
                    }
                } else if (member instanceof JCMethodDecl) { //method
                    JCBlock body = ((JCMethodDecl) member).getBody();
                    List<JCVariableDecl> tmp = getVariableDeclaration(body, jcIdent);
                    //local variable
                    if (!tmp.isEmpty()) {
                        result.clear();
                        result.addAll(tmp);
                    }
                } else if (member instanceof JCBlock) {
                    List<JCVariableDecl> tmp = getVariableDeclaration(member, jcIdent);
                    //local variable
                    if (!tmp.isEmpty()) {
                        result.clear();
                        result.addAll(tmp);
                    }
                }
            }
        } else if (parent instanceof JCBlock) {
            List<JCStatement> statements = ((JCBlock) parent).getStatements();
            for (JCStatement statement : statements) {
                if (statement instanceof JCVariableDecl) {
                    JCVariableDecl variableDecl = (JCVariableDecl) statement;
                    if (canBeSampleVariable(parent, variableDecl, jcIdent)) {
                        result.add(variableDecl);
                    }
                }
            }
        }
        return result;
    }

    /**
     * @param parent - scope of variable
     */
    private boolean canBeSampleVariable(JCTree parent,
                                        JCVariableDecl variable,
                                        JCIdent ident) {
        if (!variable.getName().equals(ident.getName())) {
            return false;
        }

        //identifier inside or equal scope if variable o
        if (isChildOfParent(parent, ident)) {
            return true;
        }
        return false;
    }

    private boolean isChildOfParent(JCTree parent, JCTree child) {
        return parent.getStartPosition() <= child.getStartPosition()
                && getEndPosition(parent) >= getEndPosition(child);
    }

    private int getEndPosition(JCTree tree) {
        return tree.getEndPosition(mUnit.endPositions);
    }

    static class c {
        static void cd() {

        }
    }

}