package com.jc.starrocks.datagrip.completion

import com.jc.starrocks.datagrip.dialect.StarRocksDialect
import com.jc.starrocks.datagrip.lang.StarRocksElementTypes
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.project.DumbAware
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext

class StarRocksCompletionContributor : CompletionContributor(), DumbAware {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().withLanguage(StarRocksDialect.INSTANCE),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    result: CompletionResultSet
                ) {
                    val completionResult = result.caseInsensitive()
                    if (isPropertyCompletionContext(parameters)) {
                        addPropertyCompletions(completionResult)
                    }
                    addSnippetCompletions(completionResult)
                }
            }
        )
    }

    private fun addPropertyCompletions(result: CompletionResultSet) {
        StarRocksCompletionCatalog.PROPERTIES.forEach { property ->
            result.addElement(
                LookupElementBuilder.create(property)
                    .withTypeText("property", true)
            )
        }
    }

    private fun addSnippetCompletions(result: CompletionResultSet) {
        StarRocksCompletionCatalog.SNIPPETS.forEach { snippet ->
            result.addElement(
                LookupElementBuilder.create(snippet.lookup)
                    .withTypeText("snippet", true)
                    .withInsertHandler(snippetInsertHandler(snippet.insertText))
            )
        }
    }

    private fun isPropertyCompletionContext(parameters: CompletionParameters): Boolean {
        return generateSequence(parameters.position) { it.parent }
            .any { it.node?.elementType == StarRocksElementTypes.PROPERTIES_CLAUSE }
    }

    private fun snippetInsertHandler(insertText: String): InsertHandler<LookupElement> {
        return InsertHandler { context, _ ->
            context.document.replaceString(context.startOffset, context.tailOffset, insertText)
            context.editor.caretModel.moveToOffset(context.startOffset + insertText.length)
        }
    }

}
