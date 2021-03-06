/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.lint.checks;

import static com.android.SdkConstants.APPCOMPAT_LIB_ARTIFACT;
import static com.android.SdkConstants.CLASS_ACTIVITY;
import static com.android.tools.lint.detector.api.TextFormat.RAW;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.JavaParser.ResolvedClass;
import com.android.tools.lint.client.api.JavaParser.ResolvedMethod;
import com.android.tools.lint.client.api.JavaParser.ResolvedNode;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Speed;
import com.android.tools.lint.detector.api.TextFormat;

import java.util.Arrays;
import java.util.List;

import lombok.ast.AstVisitor;
import lombok.ast.ClassDeclaration;
import lombok.ast.MethodInvocation;

public class AppCompatCallDetector extends Detector implements Detector.JavaScanner {
    public static final Issue ISSUE = Issue.create(
            "AppCompatMethod",
            "Using Wrong AppCompat Method",
            "When using the appcompat library, there are some methods you should be calling " +
            "instead of the normal ones; for example, `getSupportActionBar()` instead of " +
            "`getActionBar()`. This lint check looks for calls to the wrong method.",
            Category.CORRECTNESS, 6, Severity.WARNING,
            new Implementation(
                    AppCompatCallDetector.class,
                    Scope.JAVA_FILE_SCOPE)).
            addMoreInfo("http://developer.android.com/tools/support-library/index.html");

    private static final String GET_ACTION_BAR = "getActionBar";
    private static final String START_ACTION_MODE = "startActionMode";
    private static final String SET_PROGRESS_BAR_VIS = "setProgressBarVisibility";
    private static final String SET_PROGRESS_BAR_IN_VIS = "setProgressBarIndeterminateVisibility";
    private static final String SET_PROGRESS_BAR_INDETERMINATE = "setProgressBarIndeterminate";
    private static final String REQUEST_WINDOW_FEATURE = "requestWindowFeature";
    /** If you change number of parameters or order, update {@link #getMessagePart(String, int,TextFormat)} */
    private static final String ERROR_MESSAGE_FORMAT = "Should use `%1$s` instead of `%2$s` name";

    private boolean mDependsOnAppCompat;

    public AppCompatCallDetector() {
    }

    @NonNull
    @Override
    public Speed getSpeed() {
        return Speed.NORMAL;
    }

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        Boolean dependsOnAppCompat = context.getProject().dependsOn(APPCOMPAT_LIB_ARTIFACT);
        mDependsOnAppCompat = dependsOnAppCompat != null && dependsOnAppCompat;
    }

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList(
                GET_ACTION_BAR,
                START_ACTION_MODE,
                SET_PROGRESS_BAR_VIS,
                SET_PROGRESS_BAR_IN_VIS,
                SET_PROGRESS_BAR_INDETERMINATE,
                REQUEST_WINDOW_FEATURE);
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @Nullable AstVisitor visitor,
            @NonNull MethodInvocation node) {
        if (mDependsOnAppCompat && isAppBarActivityCall(context, node)) {
            String name = node.astName().astValue();
            String replace = null;
            if (GET_ACTION_BAR.equals(name)) {
                replace = "getSupportActionBar";
            } else if (START_ACTION_MODE.equals(name)) {
                replace = "startSupportActionMode";
            } else if (SET_PROGRESS_BAR_VIS.equals(name)) {
                replace = "setSupportProgressBarVisibility";
            } else if (SET_PROGRESS_BAR_IN_VIS.equals(name)) {
                replace = "setSupportProgressBarIndeterminateVisibility";
            } else if (SET_PROGRESS_BAR_INDETERMINATE.equals(name)) {
                replace = "setSupportProgressBarIndeterminate";
            } else if (REQUEST_WINDOW_FEATURE.equals(name)) {
                replace = "supportRequestWindowFeature";
            }

            if (replace != null) {
                String message = String.format(ERROR_MESSAGE_FORMAT, replace, name);
                context.report(ISSUE, node, context.getLocation(node), message);
            }
        }
    }

    private static boolean isAppBarActivityCall(@NonNull JavaContext context,
            @NonNull MethodInvocation node) {
        ResolvedNode resolved = context.resolve(node);
        if (resolved instanceof ResolvedMethod) {
            ResolvedMethod method = (ResolvedMethod) resolved;
            ResolvedClass containingClass = method.getContainingClass();
            if (containingClass.isSubclassOf(CLASS_ACTIVITY, false)) {
                // Make sure that the calling context is a subclass of ActionBarActivity;
                // we don't want to flag these calls if they are in non-appcompat activities
                // such as PreferenceActivity (see b.android.com/58512)
                ClassDeclaration surroundingClass = JavaContext.findSurroundingClass(node);
                if (surroundingClass != null) {
                    ResolvedNode clz = context.resolve(surroundingClass);
                    return clz instanceof ResolvedClass &&
                            ((ResolvedClass)clz).isSubclassOf(
                                    "android.support.v7.app.ActionBarActivity",
                                    false);
                }
            }
        }
        return false;
    }

    /**
     * Given an error message created by this lint check, return the corresponding old method name
     * that it suggests should be deleted. (Intended to support quickfix implementations
     * for this lint check.)
     *
     * @param errorMessage the error message originally produced by this detector
     * @param format the format of the error message
     * @return the corresponding old method name, or null if not recognized
     */
    @Nullable
    public static String getOldCall(@NonNull String errorMessage, @NonNull TextFormat format) {
        return getMessagePart(errorMessage, 2, format);
    }

    /**
     * Given an error message created by this lint check, return the corresponding new method name
     * that it suggests replace the old method name. (Intended to support quickfix implementations
     * for this lint check.)
     *
     * @param errorMessage the error message originally produced by this detector
     * @param format the format of the error message
     * @return the corresponding new method name, or null if not recognized
     */
    @Nullable
    public static String getNewCall(@NonNull String errorMessage, @NonNull TextFormat format) {
        return getMessagePart(errorMessage, 1, format);
    }

    @Nullable
    private static String getMessagePart(@NonNull String errorMessage, int group,
            @NonNull TextFormat format) {
        List<String> parameters = LintUtils.getFormattedParameters(
                RAW.convertTo(ERROR_MESSAGE_FORMAT, format),
                errorMessage);
        if (parameters.size() == 2 && group <= 2) {
            return parameters.get(group - 1);
        }

        return null;
    }
}
